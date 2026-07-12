import asyncio

from .config import WorkerSettings
from .gateway_client import GatewayClient, GatewayError
from .runner import MobilerunTaskRunner
from .service import WorkerService


async def _run() -> int:
    try:
        settings = WorkerSettings.from_env()
        async with GatewayClient(settings.gateway_url, settings.worker_token) as gateway:
            await WorkerService(gateway, MobilerunTaskRunner(settings), settings).run_once()
        return 0
    except (ValueError, GatewayError):
        return 1


def main() -> None:
    raise SystemExit(asyncio.run(_run()))


if __name__ == "__main__":
    main()
