import asyncio
import json
from contextlib import suppress

from .config import WorkerSettings
from .gateway_client import GatewayError, LeaseLost
from .models import ClaimedTask, RunOutcome, TaskRunner, WorkerPhase
from .runner import ComputeUnavailable, DeviceUnavailable, StepLimitExceeded

ACTIVE_STATUSES = {"observing", "executing"}


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
        summary = _cap_summary(summary)
        await self._gateway.report(claimed, phase=phase, summary=summary, steps=steps, error_code=error_code)
        _log(claimed, phase, summary)

    async def _active(self, claimed: ClaimedTask) -> bool:
        return await self._gateway.get_active_status(claimed) in ACTIVE_STATUSES

    async def _stop_adapter(self, claimed: ClaimedTask, adapter: asyncio.Task[RunOutcome]) -> None:
        await self._runner.cancel(claimed.task.id)
        if not adapter.done():
            adapter.cancel()
        with suppress(asyncio.CancelledError, Exception):
            await adapter

    async def _monitor(self, claimed: ClaimedTask, adapter: asyncio.Task[RunOutcome]) -> RunOutcome | None:
        loop = asyncio.get_running_loop()
        deadline = loop.time() + self._settings.execution_timeout_seconds
        while not adapter.done():
            remaining = deadline - loop.time()
            if remaining <= 0:
                await self._stop_adapter(claimed, adapter)
                raise asyncio.TimeoutError
            await asyncio.wait({adapter}, timeout=min(self._settings.status_poll_seconds, remaining))
            if adapter.done():
                break
            try:
                active = await self._active(claimed)
            except LeaseLost:
                await self._stop_adapter(claimed, adapter)
                return None
            if not active:
                await self._stop_adapter(claimed, adapter)
                return None
        outcome = await adapter
        try:
            active = await self._active(claimed)
        except LeaseLost:
            await self._runner.cancel(claimed.task.id)
            return None
        if not active:
            await self._runner.cancel(claimed.task.id)
            return None
        return outcome

    async def _safe_terminal(self, claimed: ClaimedTask, phase: WorkerPhase, summary: str, error_code: str, steps: int = 0) -> None:
        with suppress(LeaseLost):
            await self._report(claimed, phase=phase, summary=summary, steps=steps, error_code=error_code)

    async def run_once(self) -> bool:
        claimed = await self._gateway.claim(self._settings.device_id)
        if claimed is None:
            return False
        try:
            await self._report(claimed, phase="observing", summary="Emulator and Portal readiness check")
            await self._runner.readiness(claimed.task.device_id)
            if not await self._active(claimed):
                return True
            await self._report(claimed, phase="running", summary="Safe Settings workflow started")
            adapter = asyncio.create_task(self._runner.run(task_id=claimed.task.id, prompt=claimed.task.prompt, device_id=claimed.task.device_id))
            outcome = await self._monitor(claimed, adapter)
            if outcome is None:
                return True
            await self._report(
                claimed,
                phase="completed" if outcome.success else "failed",
                summary=outcome.summary,
                steps=min(max(outcome.steps, 0), self._settings.max_steps),
                error_code=outcome.error_code,
            )
        except LeaseLost:
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
