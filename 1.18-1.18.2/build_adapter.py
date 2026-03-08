#!/usr/bin/env python3

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from modcompiler.adapters import run_range_adapter


if __name__ == "__main__":
    raise SystemExit(run_range_adapter("1.18-1.18.2"))
