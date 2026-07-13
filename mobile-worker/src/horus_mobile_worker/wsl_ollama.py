from ipaddress import AddressValueError, IPv4Address, IPv4Network
import re
import subprocess
from collections.abc import Callable


_WSL_DISTRO = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
_SRC_IPV4 = re.compile(r"\bsrc[ \t]+([0-9]+(?:\.[0-9]+){3})\b")
_WSL_PRIVATE_NETWORK = IPv4Network("172.16.0.0/12")
_WSL_ROUTE_COMMAND = [
    "wsl.exe",
    "--distribution",
    "{distro}",
    "--exec",
    "ip",
    "-4",
    "route",
    "get",
    "1.1.1.1",
]


def resolve_wsl_ollama_url(
    distro: str,
    *,
    run: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
    platform_name: str,
) -> str:
    if platform_name != "win32":
        raise ValueError("MOBILE_WORKER_OLLAMA_WSL_DISTRO is supported only on Windows")
    if not isinstance(distro, str) or not _WSL_DISTRO.fullmatch(distro):
        raise ValueError("MOBILE_WORKER_OLLAMA_WSL_DISTRO must be a valid distro identifier")

    command = [part.format(distro=distro) for part in _WSL_ROUTE_COMMAND]
    try:
        result = run(command, capture_output=True, text=True, timeout=5)
    except subprocess.TimeoutExpired as error:
        raise ValueError("WSL route lookup timed out") from error
    except OSError as error:
        raise ValueError("WSL route lookup could not run") from error
    if result.returncode != 0:
        raise ValueError("WSL route lookup failed")

    source_values = _SRC_IPV4.findall(result.stdout or "")
    if len(source_values) != 1:
        raise ValueError("WSL route lookup must return exactly one src IPv4 address")
    try:
        source = IPv4Address(source_values[0])
    except AddressValueError as error:
        raise ValueError("WSL route lookup returned an invalid src IPv4 address") from error
    if source not in _WSL_PRIVATE_NETWORK:
        raise ValueError("WSL route lookup src IPv4 address must be in 172.16.0.0/12")
    return f"http://{source}:11434"
