from __future__ import annotations

import hashlib
import json
import os
import random
import threading
import time
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

DEFAULT_PRIMARY_MODEL = "openrouter/hunter-alpha"
DEFAULT_FALLBACK_MODEL = "stepfun/step-3.5-flash:free"
UNAVAILABLE_THRESHOLD = 20
WINDOW_SECONDS = 60


@dataclass
class KeyState:
    key: str
    request_timestamps: list[float] = field(default_factory=list)
    cooldown_until: float = 0.0
    _lock: threading.Lock = field(default_factory=threading.Lock)

    def add_request(self) -> None:
        with self._lock:
            now = time.time()
            self.request_timestamps = [ts for ts in self.request_timestamps if now - ts < WINDOW_SECONDS]
            self.request_timestamps.append(now)

    def get_request_count(self) -> int:
        with self._lock:
            now = time.time()
            return sum(1 for ts in self.request_timestamps if now - ts < WINDOW_SECONDS)

    def is_available(self) -> bool:
        return self.get_request_count() < UNAVAILABLE_THRESHOLD and not self.is_in_cooldown()

    def is_in_cooldown(self) -> bool:
        return time.time() < self.cooldown_until

    def mark_cooldown(self, seconds: float) -> None:
        with self._lock:
            until = time.time() + max(0.0, seconds)
            if until > self.cooldown_until:
                self.cooldown_until = until


class OpenRouterClient:
    def __init__(
        self,
        keys: list[str] | None = None,
        primary_model: str = DEFAULT_PRIMARY_MODEL,
        fallback_model: str = DEFAULT_FALLBACK_MODEL,
    ):
        if keys is None:
            keys = self._load_keys_from_env()
        self.key_states = [KeyState(key=key) for key in keys if key.strip()]
        self.primary_model = os.environ.get("OPENROUTER_PRIMARY_MODEL", "").strip() or primary_model
        fallback_models = self._load_fallback_models()
        self.fallback_models = fallback_models or [fallback_model]
        self._lock = threading.Lock()
        self._last_key_state: KeyState | None = None
        self._assigned_key_state: KeyState | None = None
        self._assign_key_from_env()

    def _load_keys_from_env(self) -> list[str]:
        raw_keys = os.environ.get("OPENROUTER_API_KEY", "")
        if not raw_keys:
            return []
        keys = []
        for line in raw_keys.splitlines():
            key = line.strip()
            if key:
                keys.append(key)
        return keys

    def _load_fallback_models(self) -> list[str]:
        raw = os.environ.get("OPENROUTER_FALLBACK_MODELS", "").strip()
        if not raw:
            return []
        parts: list[str] = []
        for line in raw.replace(",", "\n").splitlines():
            model = line.strip()
            if model:
                parts.append(model)
        return parts

    def select_key(self) -> KeyState | None:
        with self._lock:
            if not self.key_states:
                return None
            if self._assigned_key_state and self._assigned_key_state in self.key_states:
                if self._assigned_key_state.is_available():
                    return self._assigned_key_state
            available = [ks for ks in self.key_states if ks.is_available()]
            pool = available
            if not pool:
                pool = [ks for ks in self.key_states if not ks.is_in_cooldown()]
            if not pool:
                pool = self.key_states
            counts = [(ks, ks.get_request_count()) for ks in pool]
            min_count = min(count for _ks, count in counts)
            least_used = [ks for ks, count in counts if count == min_count]
            chosen = random.choice(least_used)
            if self._assigned_key_state is None or not self._assigned_key_state.is_available():
                self._assigned_key_state = chosen
            return chosen

    def _disable_key(self, key: str) -> None:
        with self._lock:
            self.key_states = [ks for ks in self.key_states if ks.key != key]
            if self._assigned_key_state and self._assigned_key_state.key == key:
                self._assigned_key_state = None

    def _assign_key_from_env(self) -> None:
        if not self.key_states:
            return
        index_raw = os.environ.get("OPENROUTER_KEY_INDEX", "").strip()
        if index_raw:
            try:
                index = int(index_raw)
            except ValueError:
                index = None
            if index is not None:
                self._assigned_key_state = self.key_states[index % len(self.key_states)]
                return
        seed = os.environ.get("OPENROUTER_KEY_SEED", "").strip()
        if not seed:
            seed = _default_job_seed()
        if seed:
            index = _stable_index(seed, len(self.key_states))
            self._assigned_key_state = self.key_states[index]

    def set_job_seed(self, seed: str) -> None:
        if not seed or not self.key_states:
            return
        with self._lock:
            index = _stable_index(seed, len(self.key_states))
            self._assigned_key_state = self.key_states[index]

    def _mark_last_key_cooldown(self, seconds: float) -> None:
        if self._last_key_state is None:
            return
        self._last_key_state.mark_cooldown(seconds)
        if self._assigned_key_state and self._assigned_key_state.key == self._last_key_state.key:
            self._assigned_key_state = None

    def chat_completion(
        self,
        messages: list[dict[str, str]],
        model: str | None = None,
        temperature: float = 0.7,
        max_tokens: int | None = None,
        tools: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        key_state = self.select_key()
        if key_state is None:
            raise ModCompilerError("No API keys available")

        key_state.add_request()
        api_key = key_state.key
        self._last_key_state = key_state
        selected_model = model or self.primary_model

        headers = {
            "Authorization": f"Bearer {api_key}",
            "HTTP-Referer": os.environ.get("GITHUB_SERVER_URL", "https://github.com"),
            "X-Title": "ModCompiler Auto Update",
        }

        payload: dict[str, Any] = {
            "model": selected_model,
            "messages": messages,
            "temperature": temperature,
        }
        if max_tokens:
            payload["max_tokens"] = max_tokens
        if tools:
            payload["tools"] = tools

        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

        request = urllib.request.Request(
            "https://openrouter.ai/api/v1/chat/completions",
            data=data,
            headers=headers,
            method="POST",
        )

        try:
            proxy = _load_socks_proxy()
            if proxy:
                with _open_with_proxy(request, timeout=120, proxy=proxy) as response:
                    body = response.read().decode("utf-8", errors="replace")
            else:
                with urllib.request.urlopen(request, timeout=120) as response:
                    body = response.read().decode("utf-8", errors="replace")
            try:
                result = json.loads(body)
            except json.JSONDecodeError:
                if _is_provider_error(body):
                    self._mark_last_key_cooldown(30.0)
                else:
                    self._mark_last_key_cooldown(5.0)
                raise ModCompilerError(f"Invalid OpenRouter response (non-JSON): {body[:500]}")
        except urllib.error.HTTPError as error:
            error_body = error.read().decode("utf-8", errors="replace")
            if error.code == 429:
                self._mark_last_key_cooldown(WINDOW_SECONDS)
                raise ModCompilerError(f"Rate limited on current key. Retrying with another key. Details: {error_body[:500]}")
            if error.code == 401:
                if self._last_key_state is not None:
                    self._disable_key(self._last_key_state.key)
                raise ModCompilerError(f"Invalid API key. Details: {error_body[:500]}")
            if _is_provider_error(error_body):
                self._mark_last_key_cooldown(30.0)
            raise ModCompilerError(f"OpenRouter API error {error.code}: {error_body[:500]}")
        except urllib.error.URLError as error:
            self._mark_last_key_cooldown(5.0)
            raise ModCompilerError(f"Failed to connect to OpenRouter: {error.reason}")

        if "choices" not in result:
            if isinstance(result, dict):
                err = json.dumps(result)
                if _is_provider_error(err):
                    self._mark_last_key_cooldown(30.0)
            raise ModCompilerError(f"Invalid OpenRouter response: {json.dumps(result)[:500]}")

        return result

    def chat_completion_with_fallback(
        self,
        messages: list[dict[str, str]],
        temperature: float = 0.7,
        max_tokens: int | None = None,
        tools: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        last_error: Exception | None = None
        max_attempts = _max_attempts()

        for attempt in range(1, max_attempts + 1):
            try:
                return self.chat_completion(
                    messages,
                    model=None,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    tools=tools,
                )
            except ModCompilerError as e:
                last_error = e
                if "PySocks is required to use OPENROUTER_SOCKS_PROXY" in str(e):
                    raise
                print(
                    f"OpenRouter primary attempt {attempt}/{max_attempts} failed: {e}",
                    file=sys.stderr,
                )
                time.sleep(_retry_delay(attempt))

        for model in self.fallback_models:
            for attempt in range(1, max_attempts + 1):
                try:
                    return self.chat_completion(
                        messages,
                        model=model,
                        temperature=temperature,
                        max_tokens=max_tokens,
                        tools=tools,
                    )
                except ModCompilerError as e:
                    last_error = e
                    if "PySocks is required to use OPENROUTER_SOCKS_PROXY" in str(e):
                        raise
                    print(
                        f"OpenRouter fallback attempt {attempt}/{max_attempts} failed: {e}",
                        file=sys.stderr,
                    )
                    time.sleep(_retry_delay(attempt))

        if last_error is not None:
            raise last_error
        raise ModCompilerError("OpenRouter request failed after retries.")


def _is_provider_error(message: str) -> bool:
    lower = message.lower()
    return "provider returned error" in lower or "provider_name" in lower or "provider error" in lower


def _is_rate_limited(message: str) -> bool:
    lower = message.lower()
    return "rate limit" in lower or "rate limited" in lower or "429" in lower


def _retry_delay(attempt: int) -> float:
    base = min(2 ** (attempt - 1), 30)
    jitter = random.uniform(0, 0.5 * base)
    return base + jitter


def _max_attempts() -> int:
    raw = os.environ.get("OPENROUTER_MAX_ATTEMPTS", "").strip()
    if not raw:
        return 5
    try:
        return max(1, min(50, int(raw)))
    except ValueError:
        return 5


def _stable_index(seed: str, count: int) -> int:
    if count <= 0:
        return 0
    digest = hashlib.sha256(seed.encode("utf-8")).hexdigest()
    return int(digest, 16) % count


def _default_job_seed() -> str:
    parts = []
    for key in (
        "OPENROUTER_JOB_SEED",
        "MODCOMPILER_TARGET",
        "MODCOMPILER_VERSION",
        "MODCOMPILER_LOADER",
        "GITHUB_JOB",
        "GITHUB_RUN_ID",
        "GITHUB_RUN_ATTEMPT",
        "GITHUB_WORKFLOW",
        "GITHUB_SHA",
    ):
        value = os.environ.get(key, "").strip()
        if value:
            parts.append(value)
    return "|".join(parts)


def _load_socks_proxy() -> tuple[str, str, int] | None:
    raw = os.environ.get("OPENROUTER_SOCKS_PROXY", "").strip()
    if not raw:
        return None
    if "://" not in raw:
        raw = f"socks5h://{raw}"
    parsed = urllib.parse.urlparse(raw)
    scheme = (parsed.scheme or "socks5h").lower()
    if scheme not in {"socks5", "socks5h", "socks4", "socks4a"}:
        raise ModCompilerError(
            f"Unsupported SOCKS proxy scheme '{scheme}'. Use socks5h://host:port."
        )
    host = parsed.hostname
    if not host:
        raise ModCompilerError("OPENROUTER_SOCKS_PROXY must include a hostname.")
    port = parsed.port or 9050
    return scheme, host, port


def _open_with_proxy(
    request: urllib.request.Request,
    *,
    timeout: int,
    proxy: tuple[str, str, int],
):
    try:
        import socks  # type: ignore
        from sockshandler import SocksiPyHandler  # type: ignore
    except Exception as exc:  # pragma: no cover - environment-specific
        raise ModCompilerError(
            "PySocks is required to use OPENROUTER_SOCKS_PROXY. Install with: pip install pysocks"
        ) from exc

    scheme, host, port = proxy
    if scheme in {"socks5", "socks5h"}:
        proxy_type = socks.SOCKS5
        rdns = scheme == "socks5h"
    else:
        proxy_type = socks.SOCKS4
        rdns = scheme == "socks4a"

    handler = SocksiPyHandler(proxy_type, host, port, rdns=rdns)
    opener = urllib.request.build_opener(handler)
    return opener.open(request, timeout=timeout)


class ModCompilerError(Exception):
    pass


def load_openrouter_client() -> OpenRouterClient:
    return OpenRouterClient()
