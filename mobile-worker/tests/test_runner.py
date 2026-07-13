import asyncio
import os
import unittest
from dataclasses import replace
from unittest.mock import AsyncMock, Mock, patch

import httpx

from horus_mobile_worker.config import WorkerSettings
from horus_mobile_worker.runner import ComputeUnavailable, DeviceUnavailable, MobilerunTaskRunner


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
        self.terminate = Mock()
        self.kill = Mock()
        self.wait = AsyncMock()


class MobilerunTaskRunnerTests(unittest.IsolatedAsyncioTestCase):
    async def test_readiness_passes_configured_adb_endpoint_to_ping(self) -> None:
        process = FakeProcess(b"Portal is installed and accessible.\n")
        with patch(
            "horus_mobile_worker.runner.asyncio.create_subprocess_exec",
            new=AsyncMock(return_value=process),
        ) as create_process:
            await MobilerunTaskRunner(settings()).readiness("emulator-5554")
        child_env = create_process.await_args.kwargs["env"]
        self.assertEqual(child_env["ANDROID_ADB_SERVER_HOST"], "127.0.0.1")
        self.assertEqual(child_env["ANDROID_ADB_SERVER_PORT"], "5037")

    async def test_readiness_rejects_private_ping_error_even_with_zero_exit(self) -> None:
        process = FakeProcess(b"Error: Could not find device emulator-5554\n")
        with patch("horus_mobile_worker.runner.asyncio.create_subprocess_exec", new=AsyncMock(return_value=process)):
            with self.assertRaises(DeviceUnavailable):
                await MobilerunTaskRunner(settings()).readiness("emulator-5554")

    async def test_ping_timeout_kills_unreaped_private_subprocess_and_maps_to_device_unavailable(self) -> None:
        process = FakeProcess(b"")
        process.communicate.side_effect = asyncio.TimeoutError
        async def wait_forever() -> None:
            await asyncio.Event().wait()
        process.wait.side_effect = wait_forever
        with patch("horus_mobile_worker.runner.PROCESS_TERMINATE_GRACE_SECONDS", 0.001), patch(
            "horus_mobile_worker.runner.asyncio.create_subprocess_exec",
            new=AsyncMock(return_value=process),
        ):
            with self.assertRaises(DeviceUnavailable):
                await asyncio.wait_for(MobilerunTaskRunner(settings()).readiness("emulator-5554"), timeout=0.1)
        process.terminate.assert_called_once_with()
        process.kill.assert_called_once_with()
        self.assertEqual(process.wait.await_count, 2)

    async def test_ping_cancellation_kills_unreaped_private_subprocess(self) -> None:
        process = FakeProcess(b"")
        process.communicate.side_effect = asyncio.CancelledError
        async def wait_forever() -> None:
            await asyncio.Event().wait()
        process.wait.side_effect = wait_forever
        with patch("horus_mobile_worker.runner.PROCESS_TERMINATE_GRACE_SECONDS", 0.001), patch(
            "horus_mobile_worker.runner.asyncio.create_subprocess_exec",
            new=AsyncMock(return_value=process),
        ):
            with self.assertRaises(asyncio.CancelledError):
                await asyncio.wait_for(MobilerunTaskRunner(settings()).readiness("emulator-5554"), timeout=0.1)
        process.terminate.assert_called_once_with()
        process.kill.assert_called_once_with()
        self.assertEqual(process.wait.await_count, 2)

    async def test_screenshot_streaming_flags_are_forced_false(self) -> None:
        process = FakeProcess(b"Portal is installed and accessible.\n")
        import_env = {}
        inherited = {
            "MOBILERUN_STREAM_SCREENSHOTS": "true",
            "DROIDRUN_STREAM_SCREENSHOTS": "true",
        }
        def stop_import(*_args, **_kwargs):
            import_env.update({
                "MOBILERUN_STREAM_SCREENSHOTS": os.environ.get("MOBILERUN_STREAM_SCREENSHOTS"),
                "DROIDRUN_STREAM_SCREENSHOTS": os.environ.get("DROIDRUN_STREAM_SCREENSHOTS"),
            })
            raise ImportError("stop after env setup")
        with patch.dict(os.environ, inherited, clear=False), patch(
            "horus_mobile_worker.runner.asyncio.create_subprocess_exec",
            new=AsyncMock(return_value=process),
        ) as create_process:
            runner = MobilerunTaskRunner(settings())
            with patch("builtins.__import__", side_effect=stop_import):
                with self.assertRaises(ImportError):
                    runner._mobilerun_types()
            await runner.readiness("emulator-5554")
        self.assertEqual(import_env["MOBILERUN_STREAM_SCREENSHOTS"], "false")
        self.assertEqual(import_env["DROIDRUN_STREAM_SCREENSHOTS"], "false")
        child_env = create_process.await_args.kwargs["env"]
        self.assertEqual(child_env["MOBILERUN_STREAM_SCREENSHOTS"], "false")
        self.assertEqual(child_env["DROIDRUN_STREAM_SCREENSHOTS"], "false")

    async def test_httpx_timeout_maps_to_compute_unavailable(self) -> None:
        class Agent:
            def __init__(self, **_kwargs): pass
            async def run(self): raise httpx.ReadTimeout("timed out")
        runner = MobilerunTaskRunner(settings())
        with patch.object(runner, "_config_for", return_value=(Agent, object())):
            with self.assertRaises(ComputeUnavailable):
                await runner.run(task_id="task-1", prompt="safe", device_id="emulator-5554")

    async def test_cancel_targets_tracked_task_without_sdk_cancel_method(self) -> None:
        runner = MobilerunTaskRunner(settings())
        started = asyncio.Event()
        async def blocked():
            started.set()
            await asyncio.Event().wait()
        tracked = asyncio.create_task(blocked())
        runner._tasks["task-1"] = tracked
        sdk_handler = Mock(cancel_run=AsyncMock())
        runner._handlers = {"task-1": sdk_handler}
        await started.wait()
        await runner.cancel("task-1")
        self.assertTrue(tracked.cancelled())
        sdk_handler.cancel_run.assert_not_awaited()


if __name__ == "__main__":
    unittest.main()
