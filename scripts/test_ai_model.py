#!/usr/bin/env python3
"""Test script for the AI model via C05LocalAI."""

import json
import urllib.request
import urllib.error

C05_BASE = "http://localhost:8129"
HOSTER = "nvidia"
MODEL = "stepfun-ai/step-3.5-flash"

SYSTEM_PROMPT = """You are a code generator that outputs source files in a strict format.
Do NOT explain, plan, or reason. Output ONLY the file blocks."""

USER_PROMPT = """Write a simple Java class for a Minecraft Fabric mod.

Output format MUST be exactly:

```filepath
src/main/java/testmod/TestMod.java
```
```java
package testmod;
public class TestMod {
    // implementation
}
```

Write the file now. No explanation."""


def test_model():
    url = f"{C05_BASE}/chat"
    payload = {
        "hoster": HOSTER,
        "model": MODEL,
        "system_prompt": SYSTEM_PROMPT,
        "user_prompt": USER_PROMPT,
        "extra_body": {
            "temperature": 0.2,
        },
    }

    body_bytes = json.dumps(payload).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
    }

    req = urllib.request.Request(url, data=body_bytes, headers=headers, method="POST")

    print(f"Testing: {HOSTER} / {MODEL}")
    print(f"URL: {url}")
    print("-" * 60)
    print("Response:")
    print("-" * 60)

    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            if resp.headers.get("content-type", "").startswith("text/event-stream"):
                # Streaming response
                full_content = ""
                for line in resp:
                    line = line.decode("utf-8", errors="replace")
                    if line.startswith("data: "):
                        data = line[6:]
                        if data.strip() == "[DONE]":
                            break
                        try:
                            chunk = json.loads(data)
                            delta = chunk.get("choices", [{}])[0].get("delta", {})
                            content = delta.get("content", "")
                            if content:
                                print(content, end="", flush=True)
                                full_content += content
                        except json.JSONDecodeError:
                            pass
                print()
                print("-" * 60)
                print(f"Total: {len(full_content)} chars")
                has_java = "```java" in full_content
                has_filepath = "```filepath" in full_content
                print(f"Has ```java blocks: {has_java}")
                print(f"Has ```filepath blocks: {has_filepath}")
            else:
                # Direct JSON response
                result = json.loads(resp.read().decode("utf-8"))
                print(json.dumps(result, indent=2))

    except urllib.error.HTTPError as e:
        print(f"HTTP Error {e.code}: {e.read().decode('utf-8', errors='replace')[:500]}")
    except Exception as e:
        print(f"Error: {e}")


if __name__ == "__main__":
    test_model()
