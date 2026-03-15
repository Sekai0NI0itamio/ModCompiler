from __future__ import annotations

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
        return self.get_request_count() < UNAVAILABLE_THRESHOLD


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
        self.primary_model = primary_model
        self.fallback_model = fallback_model
        self._lock = threading.Lock()
        self._last_key_state: KeyState | None = None

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

    def select_key(self) -> KeyState | None:
        with self._lock:
            if not self.key_states:
                return None
            available = [ks for ks in self.key_states if ks.is_available()]
            pool = available or self.key_states
            counts = [(ks, ks.get_request_count()) for ks in pool]
            min_count = min(count for _ks, count in counts)
            least_used = [ks for ks, count in counts if count == min_count]
            return random.choice(least_used)

    def _disable_key(self, key: str) -> None:
        with self._lock:
            self.key_states = [ks for ks in self.key_states if ks.key != key]

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
            with urllib.request.urlopen(request, timeout=120) as response:
                result = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            error_body = error.read().decode("utf-8", errors="replace")
            if error.code == 429:
                raise ModCompilerError(f"Rate limited on all keys. Try again later. Details: {error_body[:500]}")
            if error.code == 401:
                if self._last_key_state is not None:
                    self._disable_key(self._last_key_state.key)
                raise ModCompilerError(f"Invalid API key. Details: {error_body[:500]}")
            raise ModCompilerError(f"OpenRouter API error {error.code}: {error_body[:500]}")
        except urllib.error.URLError as error:
            raise ModCompilerError(f"Failed to connect to OpenRouter: {error.reason}")

        if "choices" not in result:
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
        max_attempts = 5

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
                print(
                    f"OpenRouter primary attempt {attempt}/{max_attempts} failed: {e}",
                    file=sys.stderr,
                )
                time.sleep(_retry_delay(attempt))

        for attempt in range(1, max_attempts + 1):
            try:
                return self.chat_completion(
                    messages,
                    model=self.fallback_model,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    tools=tools,
                )
            except ModCompilerError as e:
                last_error = e
                print(
                    f"OpenRouter fallback attempt {attempt}/{max_attempts} failed: {e}",
                    file=sys.stderr,
                )
                time.sleep(_retry_delay(attempt))

        if last_error is not None:
            raise last_error
        raise ModCompilerError("OpenRouter request failed after retries.")


def _retry_delay(attempt: int) -> float:
    base = min(2 ** (attempt - 1), 30)
    jitter = random.uniform(0, 0.5 * base)
    return base + jitter


class ModCompilerError(Exception):
    pass


def load_openrouter_client() -> OpenRouterClient:
    return OpenRouterClient()
