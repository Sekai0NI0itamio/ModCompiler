"""
mod_wizard/ai_client.py — Send prompts to local freedeepseek browser automation server.

The server returns NDJSON (one JSON event per line). The chat URL
is embedded inside content events as [CHAT_URL:...] markers.

Session lifecycle (FreeDeepSeek — browser-based, tabs auto-close):
  Create:   send_prompt()                     →  extract [CHAT_URL:...] from response
  Continue: send_prompt(resume_url=url)        →  re-opens same chat via URL
  No explicit close needed — tabs auto-close after each response.

Supports response_mode "direct" (recommended for accuracy) and "stream"
(real-time preview).  Direct mode copies via clipboard for exact raw markdown.
"""

from __future__ import annotations

import json
import re
import urllib.request
import urllib.error
from typing import Any


SERVER_URL = "http://localhost:8129"
REQUEST_TIMEOUT = 600


def _post(endpoint: str, payload: dict[str, Any]) -> dict[str, Any]:
    """Send a POST request and parse NDJSON response.

    Returns:
        {"error": bool, "message": str, "response": str,
         "resume_url": str|None}

    resume_url is extracted from [CHAT_URL:...] markers in content events.
    """
    url = f"{SERVER_URL}{endpoint}"
    data = json.dumps(payload).encode("utf-8")

    req = urllib.request.Request(
        url, data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.URLError as e:
        return _err(f"Cannot reach server at {SERVER_URL}. Is it running? ({e.reason})")
    except Exception as e:
        return _err(str(e))

    chat_url = None
    content_parts: list[str] = []

    _chat_url_re = re.compile(
        r'\[CHAT_URL:`(https://chat\.deepseek\.com/a/chat/s/[^\]]+)`\]'
        r'|\[CHAT_URL:([^\]]+)\]'
    )

    def _extract_url(m: re.Match) -> str | None:
        return m.group(1) or m.group(2)

    def _strip_url(m: re.Match) -> str:
        return ''

    for line in raw.strip().splitlines():
        line = line.strip()
        if not line:
            continue

        m = _chat_url_re.search(line)
        if m:
            raw_url = _extract_url(m)
            if raw_url:
                chat_url = raw_url.strip('`')

        if not line.startswith("{"):
            cleaned = _chat_url_re.sub(_strip_url, line)
            if cleaned.strip():
                content_parts.append(cleaned)
            continue

        try:
            evt = json.loads(line)
        except json.JSONDecodeError:
            cleaned = _chat_url_re.sub(_strip_url, line)
            if cleaned.strip():
                content_parts.append(cleaned)
            continue

        if evt.get("chat_url"):
            chat_url = evt["chat_url"]

        evt_content = evt.get("content", "")

        if evt.get("content") and evt.get("event") == "content":
            cleaned = _chat_url_re.sub(_strip_url, evt_content)
            if cleaned.strip():
                content_parts.append(cleaned)

    return {
        "error": False,
        "message": "",
        "response": "".join(content_parts).strip(),
        "resume_url": chat_url,
    }


def _err(msg: str) -> dict[str, Any]:
    return {"error": True, "message": msg, "response": "", "resume_url": None}


def send_prompt(
    prompt: str,
    *,
    model: str = "Instant",
    thinking: bool = False,
    web_search: bool = False,
    resume_url: str | None = None,
    response_mode: str = "direct",
) -> dict[str, Any]:
    """Send a prompt to DeepSeek via browser automation.

    Args:
        prompt: The prompt text.
        model: "Instant" (fast) or "Expert".
        thinking: Enable DeepThink.
        web_search: Enable web search.
        resume_url: Continue an existing chat by re-opening its DeepSeek URL.
        response_mode: "direct" (clipboard copy, exact raw markdown — recommended)
                       or "stream" (real-time DOM polling preview).

    Returns:
        {"error", "message", "response", "resume_url"}
    """
    extra_body: dict[str, Any] = {}

    if resume_url:
        extra_body["resume_url"] = resume_url

    if thinking:
        extra_body["thinking"] = True
    if web_search:
        extra_body["web_search"] = True

    payload = {
        "hoster": "freedeepseek",
        "model": model,
        "user_prompt": prompt,
        "response_mode": response_mode,
        "extra_body": extra_body,
    }

    return _post("/chat", payload)


def check_server() -> bool:
    """Check if the local freedeepseek server is running."""
    try:
        req = urllib.request.Request(
            f"{SERVER_URL}/hosters/freedeepseek/models",
            method="GET",
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            return resp.status == 200
    except Exception:
        return False
