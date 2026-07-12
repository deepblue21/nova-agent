import os
import unittest
from unittest.mock import patch

from horus_mobile_worker.config import WorkerSettings


class WorkerSettingsTests(unittest.TestCase):
    def env(self, **overrides: str) -> dict[str, str]:
        values = {"HORUS_GATEWAY_URL": "http://127.0.0.1:8080", "MOBILE_WORKER_TOKEN": "worker-secret", "MOBILE_WORKER_DEVICE_ID": "emulator-5554", "MOBILE_WORKER_OLLAMA_URL": "http://127.0.0.1:11434", "MOBILE_WORKER_OLLAMA_MODEL": "qwen2.5:7b"}
        values.update(overrides)
        return values

    def test_requires_gateway_token_and_exact_emulator(self) -> None:
        for missing in ("HORUS_GATEWAY_URL", "MOBILE_WORKER_TOKEN", "MOBILE_WORKER_DEVICE_ID"):
            env = self.env(); del env[missing]
            with self.subTest(missing=missing), patch.dict(os.environ, env, clear=True), self.assertRaises(ValueError):
                WorkerSettings.from_env()
        with patch.dict(os.environ, self.env(MOBILE_WORKER_DEVICE_ID="physical-device"), clear=True), self.assertRaisesRegex(ValueError, "emulator-5554"):
            WorkerSettings.from_env()

    def test_rejects_non_local_ollama_and_invalid_urls(self) -> None:
        for values in ({"MOBILE_WORKER_OLLAMA_URL": "https://ollama.example.com"}, {"MOBILE_WORKER_OLLAMA_URL": "http://user:pass@127.0.0.1:11434"}, {"HORUS_GATEWAY_URL": "not-a-url"}):
            with self.subTest(values=values), patch.dict(os.environ, self.env(**values), clear=True), self.assertRaises(ValueError):
                WorkerSettings.from_env()

    def test_max_steps_is_clamped_and_redacted_hides_token(self) -> None:
        with patch.dict(os.environ, self.env(MOBILE_WORKER_MAX_STEPS="99"), clear=True):
            settings = WorkerSettings.from_env()
        self.assertEqual(settings.max_steps, 8)
        self.assertEqual(settings.redacted()["worker_token"], "<redacted>")
        self.assertNotIn("worker-secret", repr(settings.redacted()))


if __name__ == "__main__": unittest.main()
