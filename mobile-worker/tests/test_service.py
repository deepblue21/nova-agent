import asyncio
import unittest
from dataclasses import replace

from horus_mobile_worker.config import WorkerSettings
from horus_mobile_worker.gateway_client import LeaseLost
from horus_mobile_worker.models import ClaimedTask, Lease, RunOutcome, Task, WorkerReport
from horus_mobile_worker.runner import ComputeUnavailable, DeviceUnavailable, StepLimitExceeded
from horus_mobile_worker.service import WorkerService


def task() -> Task:
    return Task("11111111-1111-4111-8111-111111111111", "executing", "emulator-5554", "open settings and tell me the android version")


def lease() -> Lease:
    return Lease("22222222-2222-4222-8222-222222222222", task().id, "lease-secret", "active", "2026-07-12T12:00:00.000Z")


def settings(**overrides: object) -> WorkerSettings:
    value = WorkerSettings("http://gateway.test", "worker-secret", "emulator-5554", "http://127.0.0.1:11434", "qwen2.5:7b", 8, 0.01, 30.0)
    return replace(value, **overrides)


class FakeGatewayClient:
    def __init__(self, *, claim: ClaimedTask | None, statuses: list[object] | None = None) -> None:
        self.claimed, self.statuses, self.reports = claim, list(statuses or ["executing", "executing"]), []
    async def claim(self, _device_id): return self.claimed
    async def get_active_status(self, _claimed):
        value = self.statuses.pop(0) if self.statuses else "executing"
        if isinstance(value, BaseException): raise value
        return value
    async def report(self, _claimed, **values): self.reports.append(WorkerReport(**values))


class FakeRunner:
    def __init__(self, outcome): self.outcome, self.calls, self.cancelled_task_ids = outcome, [], []
    async def readiness(self, device_id):
        if isinstance(self.outcome, DeviceUnavailable): raise self.outcome
        self.calls.append(f"ready:{device_id}")
    async def run(self, *, task_id, prompt, device_id):
        self.calls.append(task_id)
        if isinstance(self.outcome, BaseException): raise self.outcome
        return self.outcome
    async def cancel(self, task_id): self.cancelled_task_ids.append(task_id)


class BlockingFakeRunner(FakeRunner):
    def __init__(self): super().__init__(RunOutcome(True, "unexpected", 1))
    async def run(self, *, task_id, prompt, device_id):
        self.calls.append(task_id)
        await asyncio.Event().wait()


class BlockingReadinessRunner(FakeRunner):
    def __init__(self, outcome=None):
        super().__init__(outcome or RunOutcome(True, "unexpected", 1))
        self.readiness_started = asyncio.Event()
        self.readiness_cancelled = False
    async def readiness(self, device_id):
        self.calls.append(f"ready:{device_id}")
        self.readiness_started.set()
        try:
            await asyncio.Event().wait()
        except asyncio.CancelledError:
            self.readiness_cancelled = True
            raise


class WorkerServiceTests(unittest.IsolatedAsyncioTestCase):
    async def test_safe_claim_runs_and_reports_sanitized_completion(self) -> None:
        client = FakeGatewayClient(claim=ClaimedTask(task(), lease()))
        runner = FakeRunner(RunOutcome(True, "Android 17", 3))
        self.assertTrue(await WorkerService(client, runner, settings()).run_once())
        self.assertEqual([r.phase for r in client.reports], ["observing", "running", "completed"])
        self.assertEqual(client.reports[-1].summary, "Android 17")

    async def test_paused_or_cancelled_before_work_prevents_runner_invocation(self) -> None:
        for status in ("cancelled", "paused"):
            client = FakeGatewayClient(claim=ClaimedTask(task(), lease()), statuses=[status])
            runner = FakeRunner(RunOutcome(True, "unexpected", 1))
            await WorkerService(client, runner, settings()).run_once()
            self.assertEqual(runner.calls, [])
            self.assertEqual(client.reports, [])

    async def test_inactive_transition_during_readiness_cancels_it_without_terminal_report(self) -> None:
        client = FakeGatewayClient(claim=ClaimedTask(task(), lease()), statuses=["executing", "executing", "paused"])
        runner = BlockingReadinessRunner()
        await WorkerService(client, runner, settings()).run_once()
        self.assertTrue(runner.readiness_cancelled)
        self.assertEqual([r.phase for r in client.reports], ["observing"])

    async def test_readiness_failure_after_lease_loss_emits_no_terminal_report(self) -> None:
        client = FakeGatewayClient(claim=ClaimedTask(task(), lease()), statuses=["executing", "executing", LeaseLost("safe")])
        runner = FakeRunner(DeviceUnavailable("raw"))
        await WorkerService(client, runner, settings()).run_once()
        self.assertEqual([r.phase for r in client.reports], ["observing"])

    async def test_pause_during_execution_cancels_runner_without_terminal_report(self) -> None:
        client = FakeGatewayClient(claim=ClaimedTask(task(), lease()), statuses=["executing"] * 4 + ["paused"])
        runner = BlockingFakeRunner()
        await WorkerService(client, runner, settings()).run_once()
        self.assertEqual(runner.cancelled_task_ids, [task().id])
        self.assertEqual([r.phase for r in client.reports], ["observing", "running"])

    async def test_lease_loss_cancels_runner_without_terminal_report(self) -> None:
        client = FakeGatewayClient(claim=ClaimedTask(task(), lease()), statuses=["executing"] * 4 + [LeaseLost("safe")])
        runner = BlockingFakeRunner()
        await WorkerService(client, runner, settings()).run_once()
        self.assertEqual(runner.cancelled_task_ids, [task().id])
        self.assertEqual([r.phase for r in client.reports], ["observing", "running"])

    async def test_failures_map_to_allowed_safe_reports(self) -> None:
        cases = ((DeviceUnavailable("raw"), "waiting_for_device", "device_unavailable"), (ComputeUnavailable("raw"), "waiting_for_compute", "compute_unavailable"), (asyncio.TimeoutError("raw"), "failed", "execution_limit"), (StepLimitExceeded("raw"), "failed", "execution_limit"), (RuntimeError("raw"), "failed", "execution_failed"))
        for error, phase, code in cases:
            client = FakeGatewayClient(claim=ClaimedTask(task(), lease()))
            await WorkerService(client, FakeRunner(error), settings()).run_once()
            terminal = client.reports[-1]
            self.assertEqual((terminal.phase, terminal.error_code), (phase, code))
            self.assertNotIn("raw", terminal.summary)

    async def test_httpx_timeout_maps_to_waiting_for_compute(self) -> None:
        client = FakeGatewayClient(claim=ClaimedTask(task(), lease()))
        await WorkerService(client, FakeRunner(ComputeUnavailable("timeout")), settings()).run_once()
        terminal = client.reports[-1]
        self.assertEqual((terminal.phase, terminal.error_code), ("waiting_for_compute", "compute_unavailable"))

    async def test_unsuccessful_outcome_maps_to_safe_failed_report(self) -> None:
        client = FakeGatewayClient(claim=ClaimedTask(task(), lease()))
        await WorkerService(client, FakeRunner(RunOutcome(False, "Safe failure", 8, "execution_failed")), settings()).run_once()
        terminal = client.reports[-1]
        self.assertEqual((terminal.phase, terminal.error_code, terminal.summary), ("failed", "execution_failed", "Safe failure"))


if __name__ == "__main__": unittest.main()
