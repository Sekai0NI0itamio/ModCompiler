"""
key_scheduler.py — NVIDIA API Key Stress Scheduler
===================================================
Ported and extended from C05 Local AI proxy's KeyRing + routes.py error handling.

Key behaviours (matching C05 nvidia hoster config):
  - 60-second sliding window per key
  - 40 requests/minute cap per key
  - Always pick the key with fewest recent requests (least-stressed)
  - Wait if all keys are at capacity (never exceed the cap)
  - Permanent exhaustion on auth errors (HTTP 401/403, invalid_api_key)
  - Temporary cooldown on transient rate limits (HTTP 429 with retry-after)
  - Retry with backoff on 503 ResourceExhausted (server overload, not key fault)
  - Connection errors: retry without retiring the key
  - Request-too-large errors: never retire the key
"""

from __future__ import annotations

import asyncio
import math
import re
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone, timedelta
from email.utils import parsedate_to_datetime
from typing import Dict, List, Optional, Tuple

MAX_REQUESTS_PER_MINUTE = 40
TIME_WINDOW_SECONDS = 60

# 503 retry config (ResourceExhausted — server overload, not key fault)
MAX_503_RETRIES = 5
_503_BASE_BACKOFF = 2.0   # seconds
_503_MAX_BACKOFF  = 60.0  # seconds

DEFAULT_COOLDOWN_SECONDS = 60
MAX_COOLDOWN_SECONDS = 3600


# ---------------------------------------------------------------------------
# Error classification (ported from C05 routes.py)
# ---------------------------------------------------------------------------

def _extract_http_status(err_text: str) -> Optional[int]:
    m = re.search(r"(?:error code|status code|http)\s*[:=]?\s*(\d{3})", err_text, re.IGNORECASE)
    if m:
        v = int(m.group(1))
        if 100 <= v <= 599:
            return v
    # Also match bare "HTTP 503" style
    m2 = re.search(r"\b(4\d{2}|5\d{2})\b", err_text)
    if m2:
        return int(m2.group(1))
    return None


def _coerce_cooldown(value: float) -> int:
    return max(1, min(MAX_COOLDOWN_SECONDS, int(math.ceil(value))))


def _parse_retry_after(value: str) -> Optional[int]:
    raw = str(value or "").strip()
    if not raw:
        return None
    try:
        numeric = float(raw)
        now_ts = datetime.now(timezone.utc).timestamp()
        if numeric >= 1_000_000_000_000:
            return _coerce_cooldown((numeric / 1000.0) - now_ts)
        if numeric >= 1_000_000_000:
            return _coerce_cooldown(numeric - now_ts)
        return _coerce_cooldown(numeric)
    except ValueError:
        pass
    try:
        dt = parsedate_to_datetime(raw)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return _coerce_cooldown((dt - datetime.now(timezone.utc)).total_seconds())
    except Exception:
        pass
    # Parse "retry in Xs" / "wait Xm Ys" patterns
    units = {"ms": 0.001, "s": 1, "sec": 1, "secs": 1, "second": 1, "seconds": 1,
             "m": 60, "min": 60, "mins": 60, "minute": 60, "minutes": 60,
             "h": 3600, "hr": 3600, "hour": 3600, "hours": 3600}
    m = re.search(
        r"(?:retry|wait|reset)[^0-9]{0,20}(\d+(?:\.\d+)?)\s*(ms|milliseconds?|secs?|seconds?|mins?|minutes?|hrs?|hours?|[smh])\b",
        raw.lower(),
    )
    if m:
        return _coerce_cooldown(float(m.group(1)) * units.get(m.group(2), 1))
    return None


class ErrorKind:
    """Classify an API error into one of several handling categories."""

    AUTH          = "auth"           # 401/403 / invalid key → mark exhausted
    RATE_LIMIT    = "rate_limit"     # 429 quota exceeded → mark exhausted
    TEMP_RATE     = "temp_rate"      # 429 temporary upstream → cooldown
    SERVER_BUSY   = "server_busy"    # 503 ResourceExhausted → retry with backoff
    TOO_LARGE     = "too_large"      # 413 / context too long → don't retire key
    CONNECTION    = "connection"     # network error → retry without retiring
    UNKNOWN       = "unknown"        # anything else

    @staticmethod
    def classify(err_text: str) -> Tuple[str, int]:
        """
        Returns (kind, cooldown_seconds).
        cooldown_seconds is only meaningful for TEMP_RATE.
        """
        t = err_text.lower()
        status = _extract_http_status(t)

        # Too-large — never retire the key
        if status == 413 or any(m in t for m in (
            "request too large", "payload too large", "context length exceeded",
            "maximum context length", "too many tokens", "input is too long",
        )):
            return ErrorKind.TOO_LARGE, 0

        # Auth errors — permanent exhaustion
        if status == 401 or (status == 403 and any(m in t for m in (
            "authentication_error", "invalid_api_key", "invalid api key",
            "incorrect api key", "authentication failed",
        ))):
            return ErrorKind.AUTH, 0
        if any(m in t for m in (
            "authentication_error", "invalid_api_key", "invalid api key",
            "incorrect api key", "authentication failed", "invalid authentication",
        )):
            return ErrorKind.AUTH, 0

        # 503 server busy (ResourceExhausted) — retry with backoff, don't retire
        if status == 503 or status == 502 or "resourceexhausted" in t or "all workers are busy" in t or "bad gateway" in t:
            return ErrorKind.SERVER_BUSY, 0

        # 429 — distinguish temporary upstream vs permanent quota
        if status == 429:
            temp_markers = (
                "temporarily rate-limited upstream", "temporarily rate limited",
                "retry shortly", "retry again shortly", "please retry shortly",
                "temporary rate limit",
            )
            if any(m in t for m in temp_markers):
                cooldown = _parse_retry_after(err_text) or DEFAULT_COOLDOWN_SECONDS
                return ErrorKind.TEMP_RATE, cooldown
            # Permanent quota exhaustion
            if any(m in t for m in (
                "quota exceeded", "insufficient_quota", "exceeded your current quota",
                "rate_limit_exceeded",
            )):
                return ErrorKind.RATE_LIMIT, 0
            # Generic 429 — treat as temporary cooldown
            cooldown = _parse_retry_after(err_text) or DEFAULT_COOLDOWN_SECONDS
            return ErrorKind.TEMP_RATE, cooldown

        # Permanent rate/quota markers without status code
        if any(m in t for m in (
            "rate_limit_exceeded", "quota exceeded", "insufficient_quota",
            "exceeded your current quota", "too many requests",
        )):
            return ErrorKind.RATE_LIMIT, 0

        # Connection errors — retry without retiring
        conn_markers = (
            "connection error", "connection reset", "connection aborted",
            "connection refused", "connection closed", "server disconnected",
            "network is unreachable", "timed out", "timeout",
            "eof occurred in violation of protocol", "name or service not known",
        )
        if any(m in t for m in conn_markers):
            return ErrorKind.CONNECTION, 0

        return ErrorKind.UNKNOWN, 0


# ---------------------------------------------------------------------------
# Per-key state
# ---------------------------------------------------------------------------

@dataclass
class _KeyState:
    key: str
    request_times: List[float] = field(default_factory=list)
    active_requests: int = 0
    exhausted: bool = False
    cooldown_until: Optional[float] = None   # monotonic time

    def cleanup_window(self, now: float) -> None:
        cutoff = now - TIME_WINDOW_SECONDS
        self.request_times = [t for t in self.request_times if t > cutoff]

    def recent_count(self, now: float) -> int:
        self.cleanup_window(now)
        return len(self.request_times)

    def capacity_left(self, now: float) -> int:
        return max(0, MAX_REQUESTS_PER_MINUTE - self.recent_count(now) - self.active_requests)

    def is_cooled_down(self, now: float) -> bool:
        if self.cooldown_until is None:
            return True
        if now >= self.cooldown_until:
            self.cooldown_until = None
            return True
        return False

    def is_usable(self, now: float) -> bool:
        if self.exhausted:
            return False
        if not self.is_cooled_down(now):
            return False
        return self.capacity_left(now) > 0

    def rank(self, now: float) -> Tuple[int, int]:
        """Lower is better: (recent_requests, active_requests)."""
        return (self.recent_count(now), self.active_requests)

    def seconds_until_slot(self, now: float) -> float:
        """How many seconds until this key has capacity (ignoring exhaustion/cooldown)."""
        if self.cooldown_until and now < self.cooldown_until:
            return self.cooldown_until - now
        if not self.request_times:
            return 0.0
        oldest = min(self.request_times)
        return max(0.0, oldest + TIME_WINDOW_SECONDS - now)


# ---------------------------------------------------------------------------
# Scheduler
# ---------------------------------------------------------------------------

class KeyStressScheduler:
    """
    Async stress-based key scheduler for the NVIDIA API.
    Mirrors C05's KeyRing STRESS_BASED logic with full error classification.
    """

    def __init__(self, keys: List[str]) -> None:
        if not keys:
            raise ValueError("Key pool is empty — provide at least one NVIDIA API key.")
        self._states = [_KeyState(key=k) for k in keys]
        self._lock = asyncio.Lock()

    @classmethod
    def from_secret(cls, secret_value: str) -> "KeyStressScheduler":
        """Parse a newline-delimited key list (ignores blank lines and # comments)."""
        keys = [
            line.strip()
            for line in secret_value.splitlines()
            if line.strip() and not line.strip().startswith("#")
        ]
        if not keys:
            raise ValueError(
                "NVIDIA_API_KEYS contains no valid keys. Add one key per line."
            )
        return cls(keys)

    async def acquire(self, timeout: float = 120.0) -> str:
        """
        Return the least-stressed usable key, reserving it.
        Waits up to `timeout` seconds if all keys are at capacity.
        Raises RuntimeError if all keys are exhausted or timeout expires.
        """
        deadline = time.monotonic() + timeout
        while True:
            async with self._lock:
                now = time.monotonic()
                usable = [s for s in self._states if s.is_usable(now)]
                if usable:
                    best = min(usable, key=lambda s: s.rank(now))
                    best.active_requests += 1
                    return best.key

                # Check if any keys are still alive
                alive = [s for s in self._states if not s.exhausted]
                if not alive:
                    raise RuntimeError(
                        "All NVIDIA API keys are exhausted. "
                        "Add more keys to the NVIDIA_API_KEYS secret."
                    )

                # Calculate wait time until next slot opens
                wait = min(
                    (s.seconds_until_slot(now) for s in alive),
                    default=1.0,
                )
                wait = max(0.05, min(wait, 1.0))

            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise RuntimeError(
                    f"Timed out waiting for an available NVIDIA API key after {timeout}s."
                )
            await asyncio.sleep(min(wait, remaining))

    async def release(self, key: str) -> None:
        """Record a completed request and release the active slot."""
        async with self._lock:
            now = time.monotonic()
            for s in self._states:
                if s.key == key:
                    s.active_requests = max(0, s.active_requests - 1)
                    s.request_times.append(now)
                    break

    async def rollback(self, key: str) -> None:
        """
        Roll back the last request timestamp for a key (used when the request
        failed before the server processed it — e.g. connection error).
        Mirrors C05's rollback_last_request.
        """
        async with self._lock:
            for s in self._states:
                if s.key == key:
                    s.active_requests = max(0, s.active_requests - 1)
                    if s.request_times:
                        s.request_times.pop()
                    break

    async def mark_exhausted(self, key: str) -> None:
        """Permanently retire a key (auth error or permanent quota exhaustion)."""
        async with self._lock:
            for s in self._states:
                if s.key == key:
                    s.exhausted = True
                    s.active_requests = 0
                    break

    async def mark_cooldown(self, key: str, seconds: int) -> None:
        """
        Temporarily cool a key for `seconds` seconds (transient 429).
        The key is not permanently retired — it becomes available again after the cooldown.
        """
        async with self._lock:
            now = time.monotonic()
            for s in self._states:
                if s.key == key:
                    s.active_requests = max(0, s.active_requests - 1)
                    until = now + max(1, seconds)
                    # Extend existing cooldown if longer
                    if s.cooldown_until and s.cooldown_until > until:
                        until = s.cooldown_until
                    s.cooldown_until = until
                    break

    def stats(self) -> List[dict]:
        """Return current stats for all keys (for logging)."""
        now = time.monotonic()
        result = []
        for i, s in enumerate(self._states):
            result.append({
                "key_index": i,
                "key_prefix": s.key[:8] + "...",
                "recent_requests": s.recent_count(now),
                "active_requests": s.active_requests,
                "capacity_left": s.capacity_left(now),
                "exhausted": s.exhausted,
                "cooldown_remaining_s": max(0, round(s.cooldown_until - now, 1)) if s.cooldown_until else 0,
            })
        return result

    def all_exhausted(self) -> bool:
        return all(s.exhausted for s in self._states)
