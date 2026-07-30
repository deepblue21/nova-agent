"""Standart kütüphane worker test süitini tek komutla koşar.

`src/` dizinini yola ekleyerek `PYTHONPATH` gerektirmeden çalışır:
    python3 mobile-worker/run_tests.py
"""

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "src"))

suite = unittest.defaultTestLoader.discover(str(ROOT / "tests"))
result = unittest.TextTestRunner(verbosity=1).run(suite)
raise SystemExit(0 if result.wasSuccessful() else 1)
