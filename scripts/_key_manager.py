"""
_key_manager.py — Thread-safe API key pool with stress tracking.

Reads all keys from a file (one per line, # for comments).
Tracks which keys are in use ("stressed") and distributes load
across available keys. Thread-safe for parallel AI requests.
"""

from __future__ import annotations

import threading
from pathlib import Path
from typing import Optional


class KeyManager:
    """Manages a pool of API keys with stress-based load balancing.

    Keys are read from a file (one per line, # comments ignored).
    acquire() returns the least-stressed key.
    release(key) decreases stress after use.
    """

    def __init__(self, key_path: str | Path, env_vars: tuple[str, ...] = ()) -> None:
        self._lock = threading.Lock()
        self._keys: list[str] = []
        self._stress: dict[str, int] = {}  # key -> current usage count

        p = Path(key_path)
        if p.exists():
            for line in p.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#"):
                    self._keys.append(line)

        # Fallback to env vars
        if not self._keys:
            for var in env_vars:
                import os
                val = os.environ.get(var, "").strip()
                if val:
                    self._keys.append(val)
                    break

        if not self._keys:
            raise RuntimeError(
                f"No API keys found in {key_path} or env vars {env_vars}"
            )

        # Initialize stress to 0 for all keys
        for k in self._keys:
            self._stress[k] = 0

    def acquire(self) -> str:
        """Get the least-stressed key and mark it as in use."""
        with self._lock:
            # Find key with lowest stress
            best_key = min(self._keys, key=lambda k: self._stress[k])
            self._stress[best_key] += 1
            return best_key

    def release(self, key: str) -> None:
        """Mark a key as no longer in use."""
        with self._lock:
            if key in self._stress and self._stress[key] > 0:
                self._stress[key] -= 1

    @property
    def key_count(self) -> int:
        return len(self._keys)

    @property
    def stress_summary(self) -> str:
        with self._lock:
            parts = [f"{k[:12]}...:{v}" for k, v in sorted(self._stress.items())]
            return ", ".join(parts)
