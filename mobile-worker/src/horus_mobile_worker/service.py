import asyncio
import json
from contextlib import suppress
from collections.abc import Awaitable, Callable
from typing import TypeVar

from .config import WorkerSettings
from .gateway_client import GatewayError, LeaseLost
from .models import ClaimedTask, RunOutcome, TaskRunner, WorkerPhase
from .runner import ComputeUnavailable, DeviceUnavailable, StepLimitExceeded

ACTIVE_STATUSES = {"observing", "executing"}
T = TypeVar("T")


class _WorkStopped(RuntimeError):
    pass


def _cap_summary(value: str, limit: int = 200) -> str:
    clean = " ".join(value.split())
    return clean[:limit]


def _log(claimed: ClaimedTask, phase: WorkerPhase, summary: str) -> None:
    print(json.dumps({
        "task_id": claimed.task.id,
        "lease_id": claimed.lease.id,
        "phase": phase,
        "summary": _cap_summary(summary),
    }, separators=(",", ":")), flush=True)


class WorkerService:
    def __init__(self, gateway: object, runner: TaskRunner, settings: WorkerSettings) -> None:
        self._gateway = gateway
        self._runner = runner
        self._settings = settings

    async def _report(self, claimed: ClaimedTask, *, phase: WorkerPhase, summary: str, steps: int | None = None, error_code: str | None = None) -> None:
        await self._assert_active(claimed)
        summary = _cap_summary(summary)
        await self._gateway.report(claimed, phase=phase, summary=summary, steps=steps, error_code=error_code)
        _log(claimed, phase, summary)

    async def _active(self, claimed: ClaimedTask) -> bool:
        return await self._gateway.get_active_status(claimed) in ACTIVE_STATUSES

    async def _assert_active(self, claimed: ClaimedTask) -> None:
        if not await self._active(claimed):
            raise _WorkStopped

    async def _stop_task(self, task: asyncio.Task[T], on_cancel: Callable[[], Awaitable[None]] | None) -> None:
        if on_cancel is not None:
            await on_cancel()
        if not task.done():
            task.cancel()
        with suppress(asyncio.CancelledError, Exception):
            await task

    async def _run_monitored(
        self,
        claimed: ClaimedTask,
        operation: Callable[[], Awaitable[T]],
        *,
        timeout_seconds: float,
        timeout_error: Callable[[], Exception],
        on_cancel: Callable[[], Awaitable[None]] | None = None,
    ) -> T:
        await self._assert_active(claimed)
        task = asyncio.create_task(operation())
        loop = asyncio.get_running_loop()
        deadline = loop.time() + timeout_seconds
        while not task.done():
            remaining = deadline - loop.time()
            if remaining <= 0:
                await self._stop_task(task, on_cancel)
                raise timeout_error()
            await asyncio.wait({task}, timeout=min(self._settings.status_poll_seconds, remaining))
            if task.done():
                break
            try:
                await self._assert_active(claimed)
            except (LeaseLost, _WorkStopped):
                await self._stop_task(task, on_cancel)
                raise _WorkStopped
        return await task

    async def _safe_terminal(self, claimed: ClaimedTask, phase: WorkerPhase, summary: str, error_code: str, steps: int = 0) -> None:
        with suppress(LeaseLost, _WorkStopped):
            await self._report(claimed, phase=phase, summary=summary, steps=steps, error_code=error_code)

    async def run_once(self) -> bool:
        claimed = await self._gateway.claim(self._settings.device_id)
        if claimed is None:
            return False
        try:
            await self._report(claimed, phase="observing", summary="Emulator and Portal readiness check")
            await self._run_monitored(
                claimed,
                lambda: self._runner.readiness(claimed.task.device_id),
                timeout_seconds=self._settings.readiness_timeout_seconds,
                timeout_error=lambda: DeviceUnavailable("emulator readiness check timed out"),
            )
            await self._report(claimed, phase="running", summary="Safe Settings workflow started")
            outcome = await self._run_monitored(
                claimed,
                lambda: self._runner.run(task_id=claimed.task.id, prompt=claimed.task.prompt, device_id=claimed.task.device_id),
                timeout_seconds=self._settings.execution_timeout_seconds,
                timeout_error=asyncio.TimeoutError,
                on_cancel=lambda: self._runner.cancel(claimed.task.id),
            )
            await self._report(
                claimed,
                phase="completed" if outcome.success else "failed",
                summary=outcome.summary,
                steps=min(max(outcome.steps, 0), self._settings.max_steps),
                error_code=outcome.error_code,
            )
        except (LeaseLost, _WorkStopped):
            return True
        except DeviceUnavailable:
            await self._safe_terminal(claimed, "waiting_for_device", "Emulator or Portal is unavailable", "device_unavailable")
        except ComputeUnavailable:
            await self._safe_terminal(claimed, "waiting_for_compute", "Local Ollama is unavailable", "compute_unavailable")
        except (asyncio.TimeoutError, StepLimitExceeded):
            await self._safe_terminal(claimed, "failed", "Worker execution limit reached", "execution_limit", self._settings.max_steps)
        except GatewayError:
            raise
        except Exception:
            await self._safe_terminal(claimed, "failed", "Safe emulator workflow failed", "execution_failed")
        return True
