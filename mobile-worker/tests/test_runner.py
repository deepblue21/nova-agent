import unittest
from dataclasses import replace
from unittest.mock import AsyncMock, patch

from horus_mobile_worker.config import WorkerSettings
from horus_mobile_worker.runner import DeviceUnavailable, MobilerunTaskRunner


def settings() -> WorkerSettings:
    return WorkerSettings(
        "http://gateway.test",
        "worker-secret",
        "emulator-5554",
        "http://127.0.0.1:11434",
        "qwen2.5:7b",
        8,
        1.0,
        30.0,
    )


class FakeProcess:
    def __init__(self, stdout: bytes, stderr: bytes = b"", returncode: int = 0) -> None:
        self.returncode = returncode
        self.communicate = AsyncMock(return_value=(stdout, stderr))


class MobilerunTaskRunnerTests(unittest.IsolatedAsyncioTestCase):
    async def test_readiness_rejects_private_ping_error_even_with_zero_exit(self) -> None:
        process = FakeProcess(b"Error: Could not find device emulator-5554\n")
        with patch("horus_mobile_worker.runner.asyncio.create_subprocess_exec", new=AsyncMock(return_value=process)):
            with self.assertRaises(DeviceUnavailable):
                await MobilerunTaskRunner(settings()).readiness("emulator-5554")


if __name__ == "__main__":
    unittest.main()
