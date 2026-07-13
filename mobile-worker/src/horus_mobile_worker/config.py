from ipaddress import AddressValueError, IPv4Address
import os
import re
from dataclasses import asdict, dataclass
from urllib.parse import urlsplit


def _required(env: dict[str, str], name: str) -> str:
    value = env.get(name, "").strip()
    if not value:
        raise ValueError(f"{name} is required")
    return value


def _http_url(value: str, name: str, *, local_only: bool = False) -> str:
    parsed = urlsplit(value)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError(f"{name} must be an HTTP URL without credentials")
    if local_only and (parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}):
        raise ValueError(f"{name} must use local HTTP Ollama")
    return value.rstrip("/")


def _float(env: dict[str, str], name: str, default: float, minimum: float) -> float:
    try:
        value = float(env.get(name, str(default)))
    except ValueError as error:
        raise ValueError(f"{name} must be numeric") from error
    if value < minimum:
        raise ValueError(f"{name} must be at least {minimum}")
    return value


def _adb_server_host(env: dict[str, str]) -> str:
    name = "MOBILE_WORKER_ADB_SERVER_HOST"
    value = env.get(name, "127.0.0.1")
    dotted_decimal = re.fullmatch(r"[0-9]+(?:\.[0-9]+){3}", value)
    if dotted_decimal:
        try:
            IPv4Address(value)
        except AddressValueError:
            raise ValueError(f"{name} must be a hostname or IPv4 literal without URLs, paths, or credentials") from None
        return value
    labels = value.split(".")
    if not value or value != value.strip() or len(value) > 253 or any(character in value for character in ":/@\\") or not all(re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", label) for label in labels):
        raise ValueError(f"{name} must be a hostname or IPv4 literal without URLs, paths, or credentials")
    return value


def _adb_server_port(env: dict[str, str]) -> int:
    name = "MOBILE_WORKER_ADB_SERVER_PORT"
    value = env.get(name, "5037")
    if not re.fullmatch(r"[0-9]+", value):
        raise ValueError(f"{name} must be a numeric port")
    port = int(value)
    if not 1 <= port <= 65535:
        raise ValueError(f"{name} must be between 1 and 65535")
    return port


@dataclass(frozen=True)
class WorkerSettings:
    gateway_url: str
    worker_token: str
    device_id: str
    ollama_url: str
    ollama_model: str
    max_steps: int
    status_poll_seconds: float
    execution_timeout_seconds: float
    readiness_timeout_seconds: float = 15.0
    adb_server_host: str = "127.0.0.1"
    adb_server_port: int = 5037

    @classmethod
    def from_env(cls, env: dict[str, str] | None = None) -> "WorkerSettings":
        values = dict(os.environ if env is None else env)
        device_id = _required(values, "MOBILE_WORKER_DEVICE_ID")
        if device_id != "emulator-5554":
            raise ValueError("MOBILE_WORKER_DEVICE_ID must be emulator-5554")
        try:
            max_steps = int(values.get("MOBILE_WORKER_MAX_STEPS", "8"))
        except ValueError as error:
            raise ValueError("MOBILE_WORKER_MAX_STEPS must be an integer") from error
        return cls(
            gateway_url=_http_url(_required(values, "HORUS_GATEWAY_URL"), "HORUS_GATEWAY_URL"),
            worker_token=_required(values, "MOBILE_WORKER_TOKEN"),
            device_id=device_id,
            ollama_url=_http_url(values.get("MOBILE_WORKER_OLLAMA_URL", "http://127.0.0.1:11434").strip(), "MOBILE_WORKER_OLLAMA_URL", local_only=True),
            ollama_model=_required(values, "MOBILE_WORKER_OLLAMA_MODEL"),
            max_steps=max(1, min(max_steps, 8)),
            status_poll_seconds=_float(values, "MOBILE_WORKER_STATUS_POLL_SECONDS", 1.0, 0.01),
            execution_timeout_seconds=_float(values, "MOBILE_WORKER_EXECUTION_TIMEOUT_SECONDS", 120.0, 1.0),
            readiness_timeout_seconds=_float(values, "MOBILE_WORKER_READINESS_TIMEOUT_SECONDS", 15.0, 1.0),
            adb_server_host=_adb_server_host(values),
            adb_server_port=_adb_server_port(values),
        )

    def redacted(self) -> dict[str, object]:
        result = asdict(self)
        result["worker_token"] = "<redacted>"
        return result
