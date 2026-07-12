import asyncio
import logging
import os
import re
from contextlib import suppress
from typing import Any

import httpx

from .config import WorkerSettings
from .models import RunOutcome


class DeviceUnavailable(RuntimeError):
    pass


class ComputeUnavailable(RuntimeError):
    pass


class StepLimitExceeded(RuntimeError):
    pass


SAFE_MOBILERUN_ENV = {
    "DROIDRUN_TELEMETRY_ENABLED": "false",
    "MOBILERUN_TELEMETRY_ENABLED": "false",
    "DROIDRUN_STREAM_SCREENSHOTS": "false",
    "MOBILERUN_STREAM_SCREENSHOTS": "false",
}


def _safe_result_summary(reason: object) -> str:
    match = re.search(r"\bAndroid\s+(\d+(?:\.\d+)*)\b", str(reason), re.IGNORECASE)
    return f"Android {match.group(1)}" if match else "Android version workflow completed"


class MobilerunTaskRunner:
    def __init__(self, settings: WorkerSettings) -> None:
        self._settings = settings
        self._tasks: dict[str, asyncio.Task[RunOutcome]] = {}

    def _mobilerun_types(self) -> tuple[Any, ...]:
        os.environ.update(SAFE_MOBILERUN_ENV)
        logging.getLogger("mobilerun").disabled = True
        from mobilerun import (
            AgentConfig,
            CredentialsConfig,
            DeviceConfig,
            LLMProfile,
            LoggingConfig,
            MobileAgent,
            MobileConfig,
            TelemetryConfig,
            ToolsConfig,
            TracingConfig,
        )
        logging.getLogger("mobilerun").disabled = True
        return (AgentConfig, CredentialsConfig, DeviceConfig, LLMProfile, LoggingConfig, MobileAgent, MobileConfig, TelemetryConfig, ToolsConfig, TracingConfig)

    def _config_for(self, device_id: str) -> tuple[Any, Any]:
        if device_id != "emulator-5554":
            raise DeviceUnavailable("unsupported device")
        (AgentConfig, CredentialsConfig, DeviceConfig, LLMProfile, LoggingConfig, MobileAgent, MobileConfig, TelemetryConfig, ToolsConfig, TracingConfig) = self._mobilerun_types()
        profile = lambda: LLMProfile(
            provider="Ollama",
            model=self._settings.ollama_model,
            temperature=0.0,
            base_url=self._settings.ollama_url,
            kwargs={"max_tokens": 2048, "context_window": 32768},
        )
        config = MobileConfig(
            agent=AgentConfig(reasoning=False, max_steps=self._settings.max_steps, streaming=False),
            device=DeviceConfig(serial=device_id, platform="android", use_tcp=False, auto_setup=False),
            llm_profiles={name: profile() for name in ("manager", "executor", "fast_agent", "app_opener", "structured_output")},
            tools=ToolsConfig(disabled_tools=["click_at", "click_area", "long_press_at"]),
            telemetry=TelemetryConfig(enabled=False),
            tracing=TracingConfig(enabled=False, langfuse_screenshots=False),
            logging=LoggingConfig(debug=False, save_trajectory="none", rich_text=False, trajectory_gifs=False),
            credentials=CredentialsConfig(enabled=False),
        )
        return MobileAgent, config

    async def readiness(self, device_id: str) -> None:
        if device_id != "emulator-5554":
            raise DeviceUnavailable("unsupported device")
        process = await asyncio.create_subprocess_exec(
            "mobilerun", "ping", "--device", device_id,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            env={**os.environ, **SAFE_MOBILERUN_ENV},
        )
        try:
            stdout, _stderr = await asyncio.wait_for(
                process.communicate(),
                timeout=self._settings.readiness_timeout_seconds,
            )
        except asyncio.TimeoutError as error:
            with suppress(ProcessLookupError):
                process.terminate()
            await process.wait()
            raise DeviceUnavailable("emulator readiness check timed out") from error
        except asyncio.CancelledError:
            with suppress(ProcessLookupError):
                process.terminate()
            await process.wait()
            raise
        if process.returncode != 0 or b"Portal is installed and accessible." not in stdout:
            raise DeviceUnavailable("emulator readiness check failed")

    async def _run_agent(self, task_id: str, prompt: str, device_id: str) -> RunOutcome:
        MobileAgent, config = self._config_for(device_id)
        try:
            result = await MobileAgent(goal=prompt, config=config, credentials=None, timeout=int(self._settings.execution_timeout_seconds)).run()
        except asyncio.CancelledError:
            raise
        except Exception as error:
            module = type(error).__module__.lower()
            if isinstance(error, (httpx.TimeoutException, httpx.NetworkError)) or any(name in module for name in ("ollama", "openai", "llm")):
                raise ComputeUnavailable("local Ollama is unavailable") from error
            raise
        steps = min(max(int(result.steps or 0), 0), self._settings.max_steps)
        if not bool(result.success) and steps >= self._settings.max_steps:
            raise StepLimitExceeded("worker step limit reached")
        return RunOutcome(
            success=bool(result.success),
            summary=_safe_result_summary(result.reason) if result.success else "The safe emulator workflow did not complete",
            steps=steps,
            error_code=None if result.success else "execution_failed",
        )

    async def run(self, *, task_id: str, prompt: str, device_id: str) -> RunOutcome:
        task = asyncio.create_task(self._run_agent(task_id, prompt, device_id))
        self._tasks[task_id] = task
        try:
            return await task
        finally:
            self._tasks.pop(task_id, None)

    async def cancel(self, task_id: str) -> None:
        task = self._tasks.get(task_id)
        if task is not None and not task.done():
            task.cancel()
            with suppress(asyncio.CancelledError):
                await task
