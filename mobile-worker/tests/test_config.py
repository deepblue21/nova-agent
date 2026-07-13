import os
import subprocess
import sys
import unittest
from unittest.mock import patch

from horus_mobile_worker.config import WorkerSettings
from horus_mobile_worker.wsl_ollama import resolve_wsl_ollama_url


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

    def test_accepts_a_host_docker_internal_adb_endpoint(self) -> None:
        settings = WorkerSettings.from_env(self.env(
            MOBILE_WORKER_ADB_SERVER_HOST="host.docker.internal",
            MOBILE_WORKER_ADB_SERVER_PORT="5037",
        ))
        self.assertEqual(settings.adb_server_host, "host.docker.internal")
        self.assertEqual(settings.adb_server_port, 5037)
        self.assertEqual(settings.redacted()["adb_server_host"], "host.docker.internal")
        self.assertEqual(settings.redacted()["adb_server_port"], 5037)

    def test_rejects_malformed_dns_adb_host(self) -> None:
        with self.assertRaisesRegex(ValueError, "MOBILE_WORKER_ADB_SERVER_HOST"):
            WorkerSettings.from_env(self.env(MOBILE_WORKER_ADB_SERVER_HOST="a..b"))

    def test_rejects_invalid_adb_endpoint_values(self) -> None:
        invalid_values = (
            {"MOBILE_WORKER_ADB_SERVER_HOST": ""},
            {"MOBILE_WORKER_ADB_SERVER_HOST": "http://adb.example"},
            {"MOBILE_WORKER_ADB_SERVER_HOST": "adb.example/path"},
            {"MOBILE_WORKER_ADB_SERVER_HOST": "adb host"},
            {"MOBILE_WORKER_ADB_SERVER_HOST": "user:pass@adb"},
            {"MOBILE_WORKER_ADB_SERVER_HOST": "adb.example:5037"},
            {"MOBILE_WORKER_ADB_SERVER_PORT": "abc"},
            {"MOBILE_WORKER_ADB_SERVER_PORT": "0"},
            {"MOBILE_WORKER_ADB_SERVER_PORT": "65536"},
        )
        for overrides in invalid_values:
            with self.subTest(overrides=overrides), self.assertRaisesRegex(ValueError, "MOBILE_WORKER_ADB_SERVER"):
                WorkerSettings.from_env(self.env(**overrides))

    def test_resolves_wsl_ollama_url_from_one_private_src_address(self) -> None:
        command: list[str] | None = None

        def run(args: list[str], **kwargs: object) -> subprocess.CompletedProcess[str]:
            nonlocal command
            command = args
            self.assertNotIn("shell", kwargs)
            return subprocess.CompletedProcess(args, 0, stdout="1.1.1.1 via 172.19.96.1 dev eth0 src 172.19.99.210 uid 0\n")

        url = resolve_wsl_ollama_url("Ubuntu-24.04", run=run, platform_name="win32")

        self.assertEqual(url, "http://172.19.99.210:11434")
        self.assertEqual(command, ["wsl.exe", "--distribution", "Ubuntu-24.04", "--exec", "ip", "-4", "route", "get", "1.1.1.1"])

    def test_rejects_invalid_wsl_distro_identifier(self) -> None:
        with self.assertRaisesRegex(ValueError, "distro"):
            resolve_wsl_ollama_url("Ubuntu; whoami", run=lambda *_args, **_kwargs: None, platform_name="win32")

    def test_rejects_raw_ollama_url_with_wsl_mode(self) -> None:
        with self.assertRaisesRegex(ValueError, "MOBILE_WORKER_OLLAMA_URL"):
            WorkerSettings.from_env(self.env(MOBILE_WORKER_OLLAMA_WSL_DISTRO="Ubuntu-24.04"))

    def test_uses_derived_wsl_ollama_url_when_raw_url_is_empty(self) -> None:
        with patch("horus_mobile_worker.config.resolve_wsl_ollama_url", return_value="http://172.19.99.210:11434") as resolve:
            settings = WorkerSettings.from_env(self.env(
                MOBILE_WORKER_OLLAMA_URL="",
                MOBILE_WORKER_OLLAMA_WSL_DISTRO="Ubuntu-24.04",
            ))

        self.assertEqual(settings.ollama_url, "http://172.19.99.210:11434")
        resolve.assert_called_once_with("Ubuntu-24.04", run=subprocess.run, platform_name=sys.platform)

    def test_rejects_wsl_mode_outside_windows(self) -> None:
        with self.assertRaisesRegex(ValueError, "Windows"):
            resolve_wsl_ollama_url("Ubuntu-24.04", run=lambda *_args, **_kwargs: None, platform_name="linux")

    def test_rejects_wsl_lookup_timeout_and_nonzero_exit(self) -> None:
        def timed_out(*_args: object, **_kwargs: object) -> None:
            raise subprocess.TimeoutExpired("wsl.exe", 5)

        with self.subTest(result="timeout"), self.assertRaisesRegex(ValueError, "timed out"):
            resolve_wsl_ollama_url("Ubuntu-24.04", run=timed_out, platform_name="win32")
        with self.subTest(result="nonzero"), self.assertRaisesRegex(ValueError, "failed"):
            resolve_wsl_ollama_url(
                "Ubuntu-24.04",
                run=lambda args, **_kwargs: subprocess.CompletedProcess(args, 1, stdout="", stderr="not running"),
                platform_name="win32",
            )

    def test_rejects_missing_or_multiple_wsl_src_addresses(self) -> None:
        for output in (
            "1.1.1.1 via 172.19.96.1 dev eth0\n",
            "1.1.1.1 via 172.19.96.1 dev eth0 src 172.19.99.210 src 172.19.99.211\n",
        ):
            with self.subTest(output=output), self.assertRaisesRegex(ValueError, "exactly one"):
                resolve_wsl_ollama_url(
                    "Ubuntu-24.04",
                    run=lambda args, **_kwargs: subprocess.CompletedProcess(args, 0, stdout=output),
                    platform_name="win32",
                )

    def test_rejects_wsl_src_outside_172_16_range(self) -> None:
        with self.assertRaisesRegex(ValueError, "172.16.0.0/12"):
            resolve_wsl_ollama_url(
                "Ubuntu-24.04",
                run=lambda args, **_kwargs: subprocess.CompletedProcess(args, 0, stdout="1.1.1.1 via 10.0.0.1 dev eth0 src 10.0.0.2\n"),
                platform_name="win32",
            )


if __name__ == "__main__": unittest.main()
