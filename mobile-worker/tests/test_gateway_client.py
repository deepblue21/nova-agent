import json
import unittest
import httpx

from horus_mobile_worker.gateway_client import GatewayClient, GatewayError, LeaseLost
from horus_mobile_worker.models import ClaimedTask, Lease, Task

TASK_ID = "11111111-1111-4111-8111-111111111111"
LEASE_ID = "22222222-2222-4222-8222-222222222222"
LEASE_TOKEN = "lease-secret"


def claimed() -> ClaimedTask:
    return ClaimedTask(Task(TASK_ID, "executing", "emulator-5554", "open settings and tell me the android version"), Lease(LEASE_ID, TASK_ID, LEASE_TOKEN, "active", "2026-07-12T12:00:00.000Z"))


class GatewayClientTests(unittest.IsolatedAsyncioTestCase):
    async def test_report_rejects_invalid_phase_and_error_code_before_request(self) -> None:
        requests = []
        async def handler(request): requests.append(request); return httpx.Response(200)
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
            client = GatewayClient("http://gateway.test", "worker-secret", http_client=http)
            with self.assertRaisesRegex(ValueError, "phase"):
                await client.report(claimed(), phase="arbitrary")
            with self.assertRaisesRegex(ValueError, "error_code"):
                await client.report(claimed(), phase="failed", error_code="arbitrary")
        self.assertEqual(requests, [])

    async def test_versioned_gateway_url_does_not_duplicate_v1_prefix(self) -> None:
        paths = []
        async def handler(request): paths.append(request.url.path); return httpx.Response(204)
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
            await GatewayClient("http://gateway.test/v1", "worker-secret", http_client=http).claim("emulator-5554")
        self.assertEqual(paths, ["/v1/internal/mobile-worker/claims"])

    async def test_claim_sends_only_worker_bearer_and_safe_body(self) -> None:
        requests = []
        async def handler(request): requests.append(request); return httpx.Response(204)
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
            self.assertIsNone(await GatewayClient("http://gateway.test", "worker-secret", http_client=http).claim("emulator-5554"))
        request = requests[0]
        self.assertEqual(request.headers["Authorization"], "Bearer worker-secret")
        self.assertNotIn("X-Horus-Lease-Token", request.headers)
        self.assertEqual(json.loads(request.content), {"device_id": "emulator-5554"})

    async def test_status_and_report_keep_tokens_out_of_bodies(self) -> None:
        requests = []
        async def handler(request):
            requests.append(request)
            return httpx.Response(200, json={"status": "executing", "lease_expires_at": "x"} if request.method == "GET" else {"id": TASK_ID, "status": "completed"})
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
            client = GatewayClient("http://gateway.test", "worker-secret", http_client=http)
            self.assertEqual(await client.get_active_status(claimed()), "executing")
            await client.report(claimed(), phase="completed", summary="Android 17", steps=3)
        for request in requests:
            self.assertEqual(request.headers["X-Horus-Lease-Token"], LEASE_TOKEN)
            self.assertNotIn(LEASE_TOKEN.encode(), request.content)
            self.assertNotIn(b"worker-secret", request.content)
        body = json.loads(requests[-1].content)
        self.assertEqual(set(body), {"lease_id", "report_id", "phase", "summary", "steps"})

    async def test_safe_error_mapping_hides_response_and_secret(self) -> None:
        async def conflict(_request): return httpx.Response(409, text=f"private {LEASE_TOKEN}")
        async with httpx.AsyncClient(transport=httpx.MockTransport(conflict)) as http:
            with self.assertRaises(LeaseLost) as caught: await GatewayClient("http://gateway.test", "worker-secret", http_client=http).get_active_status(claimed())
        self.assertNotIn(LEASE_TOKEN, str(caught.exception))
        async def failure(_request): return httpx.Response(500, text="private stack trace")
        async with httpx.AsyncClient(transport=httpx.MockTransport(failure)) as http:
            with self.assertRaises(GatewayError) as caught: await GatewayClient("http://gateway.test", "worker-secret", http_client=http).claim("emulator-5554")
        self.assertNotIn("private stack trace", str(caught.exception))


if __name__ == "__main__": unittest.main()
