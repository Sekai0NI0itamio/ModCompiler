"""
_ai_client.py — AI provider client module.

Handles all AI API interaction: sending prompts, streaming responses,
listing models, and response size limits.
"""

from __future__ import annotations

import json
import urllib.request
import urllib.error
import time
from typing import Any, Callable


# Response size limit (100KB)
MAX_RESPONSE_BYTES = 100000


class ResponseTooLarge(Exception):
    """Response exceeded the maximum allowed size."""
    pass


def send_prompt(
    *,
    model: str,
    base_url: str,
    api_key: str,
    messages: list[dict],
    temperature: float = 0.2,
    stream: bool = True,
    size_limit: int = MAX_RESPONSE_BYTES,
    status_callback: Callable[[str, str, str], None] | None = None,
    target_name: str = "",
    reasoning_effort: str | None = None,
) -> str:
    """Send a prompt to the AI API and return the full response text.

    Supports streaming with reasoning_content fallback.
    Raises ResponseTooLarge if response exceeds size_limit.
    """
    url = f"{base_url}/chat/completions"

    payload: dict[str, Any] = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "stream": stream,
    }

    if reasoning_effort:
        payload["reasoning_effort"] = reasoning_effort

    body_bytes = json.dumps(payload).encode("utf-8")
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
    }

    if status_callback:
        status_callback(target_name, "in_progress")

    req = urllib.request.Request(url, data=body_bytes, headers=headers, method="POST")
    full_response = ""
    accumulated = 0
    buffer = ""

    try:
        with urllib.request.urlopen(req, timeout=600) as resp:
            while True:
                chunk = resp.read(4096)
                if not chunk:
                    break
                buffer += chunk.decode("utf-8", errors="replace")
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    line = line.strip()
                    if not line:
                        continue
                    if line.startswith("data: "):
                        data_str = line[6:]
                        if data_str.strip() == "[DONE]":
                            break
                        try:
                            data = json.loads(data_str)
                            for c in data.get("choices", []):
                                delta = c.get("delta", {})
                                text = delta.get("content", "") or ""
                                if text:
                                    full_response += text
                                    accumulated += len(text)
                                    if accumulated > size_limit:
                                        raise ResponseTooLarge(
                                            f"Response exceeded {size_limit:,} bytes ({accumulated:,})"
                                        )
                                    if status_callback and accumulated % 500 < 100:
                                        status_callback(
                                            target_name, "streaming",
                                            f"{accumulated:,} bytes received"
                                        )
                        except json.JSONDecodeError:
                            pass
    except urllib.error.HTTPError as e:
        error_code = e.code
        error_detail = e.read().decode("utf-8", errors="replace")[:500]
        raise RuntimeError(f"HTTP {error_code}: {error_detail}")
    except ResponseTooLarge:
        raise
    except Exception as e:
        raise RuntimeError(str(e))

    if not full_response:
        raise RuntimeError("Empty response from AI — no content generated.")

    if status_callback:
        status_callback(target_name, "streaming", f"{accumulated:,} bytes received")
    return full_response


def list_models(api_key: str, base_url: str) -> list[dict]:
    """List available models from the AI provider."""
    url = f"{base_url}/models"
    headers = {"Authorization": f"Bearer {api_key}"}
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read())
            return data.get("data", [])
    except Exception as e:
        raise RuntimeError(f"Failed to list models: {e}")


def test_model_basic(
    model: str,
    base_url: str,
    api_key: str,
    timeout: int = 30,
) -> dict:
    """Quick test if a model responds. Returns status and timing."""
    start = time.time()
    try:
        response = send_prompt(
            model=model,
            base_url=base_url,
            api_key=api_key,
            messages=[{"role": "user", "content": "Say OK"}],
            temperature=0.2,
            stream=True,
            size_limit=5000,
        )
        elapsed = time.time() - start
        return {
            "model": model,
            "status": "ok",
            "time": round(elapsed, 2),
            "response": response[:100],
        }
    except Exception as e:
        elapsed = time.time() - start
        return {
            "model": model,
            "status": "failed",
            "time": round(elapsed, 2),
            "error": str(e)[:100],
        }


def benchmark_model(
    model: str,
    base_url: str,
    api_key: str,
    prompt: str = "Write a simple Java class that prints hello world. Just the code.",
    expected_keywords: list[str] | None = None,
) -> dict:
    """Benchmark a model on speed and content accuracy.

    Tests:
      - Response time
      - Response size
      - Contains expected keywords (e.g. 'class', 'main')
    """
    if expected_keywords is None:
        expected_keywords = ["class", "public"]

    start = time.time()
    reasoning_chunks = 0
    result = {"model": model, "status": "unknown", "time": 0, "chars": 0, "accuracy": 0.0}

    try:
        url = f"{base_url}/chat/completions"
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.2,
            "stream": True,
        }
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        }
        req = urllib.request.Request(url, data=json.dumps(payload).encode(), headers=headers, method="POST")

        full = ""
        buffer = ""
        with urllib.request.urlopen(req, timeout=120) as resp:
            while True:
                chunk = resp.read(4096)
                if not chunk:
                    break
                buffer += chunk.decode("utf-8", errors="replace")
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    line = line.strip()
                    if not line:
                        continue
                    if line.startswith("data: "):
                        ds = line[6:]
                        if ds.strip() == "[DONE]":
                            break
                        try:
                            data = json.loads(ds)
                            for c in data.get("choices", []):
                                delta = c.get("delta", {})
                                if delta.get("reasoning_content"):
                                    reasoning_chunks += 1
                                text = delta.get("content", "") or ""
                                if text:
                                    full += text
                                    if len(full) > 100000:
                                        raise ResponseTooLarge("Response too large")
                        except json.JSONDecodeError:
                            pass
                if ds and ds.strip() == "[DONE]":
                    break

        elapsed = time.time() - start

        # Accuracy: check how many expected keywords appear
        hits = sum(1 for kw in expected_keywords if kw.lower() in full.lower())
        accuracy = hits / len(expected_keywords) if expected_keywords else 0.0

        result.update({
            "status": "ok",
            "time": round(elapsed, 2),
            "chars": len(full),
            "reasoning_chunks": reasoning_chunks,
            "accuracy": round(accuracy, 2),
            "response_preview": full[:200],
        })
    except ResponseTooLarge:
        result.update({"status": "too_large", "time": round(time.time() - start, 2)})
    except Exception as e:
        result.update({"status": "failed", "error": str(e)[:100], "time": round(time.time() - start, 2)})

    return result
