from dataclasses import dataclass
from typing import Literal, Protocol

WorkerPhase = Literal["observing", "running", "completed", "failed", "waiting_for_device", "waiting_for_compute"]
ErrorCode = Literal["device_unavailable", "compute_unavailable", "execution_limit", "execution_failed"]


@dataclass(frozen=True)
class Task:
    id: str
    status: str
    device_id: str
    prompt: str


@dataclass(frozen=True)
class Lease:
    id: str
    task_id: str
    token: str
    state: str
    expires_at: str


@dataclass(frozen=True)
class ClaimedTask:
    task: Task
    lease: Lease


@dataclass(frozen=True)
class WorkerReport:
    phase: WorkerPhase
    summary: str | None = None
    steps: int | None = None
    error_code: ErrorCode | None = None


@dataclass(frozen=True)
class RunOutcome:
    success: bool
    summary: str
    steps: int
    error_code: ErrorCode | None = None


class TaskRunner(Protocol):
    async def readiness(self, device_id: str) -> None: ...
    async def run(self, *, task_id: str, prompt: str, device_id: str) -> RunOutcome: ...
    async def cancel(self, task_id: str) -> None: ...
