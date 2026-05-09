// src/main/functions/united/api.js
// Sends a single one-off request to the local FastAPI proxy (no session history).
// Returns the assistant text (concatenated 'content' fields returned by the local server).
// Uses model "GPT-5-mini" by default.
// Accepts an optional external AbortSignal via { signal } to allow callers to abort.

const DEFAULT_URL = "http://localhost:8129/chat";

async function sendToGpt({ prompt, model = "GPT-5-mini", url = DEFAULT_URL, timeout = 120000, signal: externalSignal, onResponseStart, onChunk } = {}) {
  console.log("[api] Sending request to local GPT proxy...");
  if (!prompt || typeof prompt !== "string") {
    throw new Error("[api] prompt must be a non-empty string");
  }

  const payload = {
    user_prompt: prompt,
    model,
    include_history: false, // one-time request (no session history)
  };

  // Use a local AbortController that we can abort either by timeout or by an external signal.
  const controller = new AbortController();
  const combinedSignal = controller.signal;

  // If an external signal is provided, forward its abort to our controller.
  let externalAbortHandler = null;
  if (externalSignal) {
    if (externalSignal.aborted) {
      controller.abort();
    } else {
      externalAbortHandler = () => controller.abort();
      externalSignal.addEventListener('abort', externalAbortHandler);
    }
  }

  const timer = setTimeout(() => controller.abort(), timeout);

  try {
    const res = await fetch(url, {
      method: "POST",
      body: JSON.stringify(payload),
      headers: { "Content-Type": "application/json" },
      signal: combinedSignal,
    });

    if (!res.ok) {
      const txt = await res.text().catch(() => "<no-text>");
      throw new Error(`[api] Local proxy returned ${res.status}: ${txt}`);
    }

    // Notify that the response (headers) has started
    try { if (typeof onResponseStart === 'function') onResponseStart(); } catch (_) {}

    let assistant = "";

    // If the runtime supports streaming, prefer to read incrementally so callers can track "response time".
    const body = res.body;
    if (body && typeof body.getReader === 'function') {
      const reader = body.getReader();
      const decoder = new TextDecoder();
      let buf = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const chunk = decoder.decode(value, { stream: true });
        buf += chunk;
        // Process complete lines
        const parts = buf.split(/\r?\n/);
        buf = parts.pop() || ""; // keep the last partial line in buffer
        for (const line of parts) {
          if (!line) continue;
          try {
            const obj = JSON.parse(line);
            if (obj.content) {
              assistant += obj.content;
              try { if (typeof onChunk === 'function') onChunk(String(obj.content)); } catch (_) {}
            }
            if (obj.error) throw new Error(`[api] server error: ${obj.error}`);
          } catch (e) {
            console.warn("[api] skipping unparsable line from stream:", line.slice(0, 200));
          }
        }
      }
      // flush any remaining buffered line
      const rem = (buf || "").trim();
      if (rem) {
        try {
          const obj = JSON.parse(rem);
          if (obj.content) {
            assistant += obj.content;
            try { if (typeof onChunk === 'function') onChunk(String(obj.content)); } catch (_) {}
          }
          if (obj.error) throw new Error(`[api] server error: ${obj.error}`);
        } catch (e) {
          // ignore leftover
        }
      }
      clearTimeout(timer);
      if (externalSignal && externalAbortHandler) {
        try { externalSignal.removeEventListener('abort', externalAbortHandler); } catch (e) {}
      }
      console.log("[api] Received assistant response (length:", assistant.length + ")");
      return assistant;
    }

    // Fallback: read full text and parse NDJSON afterward
    const raw = await res.text();
    clearTimeout(timer);

    // detach external handler
    if (externalSignal && externalAbortHandler) {
      try { externalSignal.removeEventListener('abort', externalAbortHandler); } catch (e) {}
    }

    // The server emits JSON objects separated by newline characters
    const lines = raw.split(/\r?\n/).filter(Boolean);
    for (const l of lines) {
      try {
        const obj = JSON.parse(l);
        if (obj.content) assistant += obj.content;
        if (obj.error) {
          throw new Error(`[api] server error: ${obj.error}`);
        }
      } catch (err) {
        console.warn("[api] skipping unparsable line from stream:", l.slice(0, 200));
      }
    }
    console.log("[api] Received assistant response (length:", assistant.length + ")");
    return assistant;
  } catch (err) {
    clearTimeout(timer);
    if (externalSignal && externalAbortHandler) {
      try { externalSignal.removeEventListener('abort', externalAbortHandler); } catch (e) {}
    }
    if (err.name === "AbortError" || (err.message && err.message.includes('aborted'))) {
      throw new Error("[api] Request aborted");
    }
    if (err.name === "TypeError" && err.message === "Failed to fetch") {
      throw new Error("[api] Fetch failed: " + err.message);
    }
    throw err;
  }
}

module.exports = {
  sendToGpt,
};