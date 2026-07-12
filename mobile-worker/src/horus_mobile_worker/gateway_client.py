from typing import Any
from uuid import uuid4

import httpx

from .models import ClaimedTask, Lease, Task, WorkerPhase


class GatewayError(RuntimeError):
    pass


class LeaseLost(GatewayError):
    pass


class GatewayClient:
    def __init__(self, gateway_url: str, worker_token: str, *, http_client: httpx.AsyncClient | None = None) -> None:
        self._base_url = gateway_url.rstrip("/")
        if self._base_url.endswith("/v1"):
            self._base_url = self._base_url[:-3]
        self._worker_token = worker_token
        self._http = http_client or httpx.AsyncClient(timeout=15.0)
        self._owns_http = http_client is None

    async def __aenter__(self) -> "GatewayClient":
        return self

    async def __aexit__(self, *_args: object) -> None:
        if self._owns_http:
            await self._http.aclose()

    def _headers(self, lease_token: str | None = None) -> dict[str, str]:
        headers = {"Authorization": f"Bearer {self._worker_token}"}
        if lease_token is not None:
            headers["X-Horus-Lease-Token"] = lease_token
        return headers

    async def _request(self, method: str, path: str, *, lease_token: str | None = None, json: dict[str, Any] | None = None) -> httpx.Response:
        try:
            response = await self._http.request(method, f"{self._base_url}{path}", headers=self._headers(lease_token), json=json)
        except httpx.HTTPError as error:
            raise GatewayError("gateway request failed") from error
        if response.status_code == 409:
            raise LeaseLost("worker lease is unavailable")
        if response.status_code >= 400:
            raise GatewayError(f"gateway request failed with status {response.status_code}")
        return response

    async def claim(self, device_id: str) -> ClaimedTask | None:
        response = await self._request("POST", "/v1/internal/mobile-worker/claims", json={"device_id": device_id})
        if response.status_code == 204:
            return None
        try:
            body = response.json()
            task = Task(**{key: body["task"][key] for key in ("id", "status", "device_id", "prompt")})
            lease = Lease(**{key: body["lease"][key] for key in ("id", "task_id", "token", "state", "expires_at")})
        except (KeyError, TypeError, ValueError) as error:
            raise GatewayError("gateway returned an invalid claim") from error
        return ClaimedTask(task=task, lease=lease)

    async def get_active_status(self, claimed: ClaimedTask) -> str:
        response = await self._request("GET", f"/v1/internal/mobile-worker/tasks/{claimed.task.id}/status", lease_token=claimed.lease.token)
        try:
            status = response.json()["status"]
        except (KeyError, TypeError, ValueError) as error:
            raise GatewayError("gateway returned an invalid status") from error
        if not isinstance(status, str):
            raise GatewayError("gateway returned an invalid status")
        return status

    async def report(self, claimed: ClaimedTask, *, phase: WorkerPhase, summary: str | None = None, steps: int | None = None, error_code: str | None = None) -> None:
        body: dict[str, Any] = {"lease_id": claimed.lease.id, "report_id": str(uuid4()), "phase": phase}
        for key, value in (("summary", summary), ("steps", steps), ("error_code", error_code)):
            if value is not None:
                body[key] = value
        await self._request("POST", f"/v1/internal/mobile-worker/tasks/{claimed.task.id}/reports", lease_token=claimed.lease.token, json=body)
