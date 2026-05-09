// src/main/functions/directory_viewer/buttons/autoEditAi.js
// Dedicated AI client for Auto Edit. Talks to the local FastAPI proxy described by the user.
// - Builds the required Prompt/Message structure
// - Streams NDJSON and exposes callbacks for UI timers and optional partial updates

/**
 * @typedef {Object} Attachment
 * @property {string} name
 * @property {string} content
 * @property {string} [path]
 */

/**
 * Default system prompt text used by Auto Edit when not supplied explicitly.
 * Mirrors the instruction format shown in the Auto Edit UI.
 */
const defaultSystemPrompt = [
  'For each file, include the full modified/newly-created file contents in a code block (dont show code for unmodified code files). The first line of a code block must be a single-line comment that is the file path. Example:',
  '```javascript',
  '// src/main/app.js',
  'console.log("hello")',
  '```',
].join('\n');

/**
 * Send the Auto Edit idea + attachments to the local FastAPI proxy.
 *
 * The request body maps to ChatRequest in the proxy:
 *   {
 *     user_prompt: 'Message:\n<idea+attachments>',
 *     model,
 *     system_prompt: 'Prompt:\n<systemPrompt>',
 *     include_history,
 *     app_id,
 *     session_id
 *   }
 *
 * The proxy streams NDJSON lines, including:
 *   {"status":"start", ...}\n  (first)
 *   {"content":"..."}\n      (zero or more)
 *   {"warning":"..."}\n     (zero or more)
 *   {"error":"..."}\n       (if something failed)
 *   {"status":"end", ...}\n  (last)
 *
 * @param {Object} params
 * @param {string} params.idea                         // User's freeform idea
 * @param {Attachment[]} params.attachments           // Files to include (name + content)
 * @param {string} [params.systemPrompt]              // If omitted, defaultSystemPrompt is used
 * @param {string} [params.model="GPT-5.1-codex"]
 * @param {string} [params.url="http://localhost:8129/chat"]
 * @param {boolean} [params.includeHistory=false]
 * @param {string} [params.appId="auto_edit"]
 * @param {string|null} [params.sessionId=null]
 * @param {AbortSignal} [params.signal]
 * @param {Function} [params.onStart]                 // Called when streaming starts (upon first NDJSON start line)
 * @param {Function} [params.onChunk]                 // Called with incremental text chunks
 * @param {Function} [params.onEnd]                   // Called when the stream ends
 * @returns {Promise<string>}                         // Resolves with the full assistant response
 */
async function sendAutoEditIdea({
  idea,
  attachments,
  systemPrompt = defaultSystemPrompt,
  model = 'GPT-5.1-codex',
  url = 'http://localhost:8129/chat',
  includeHistory = false,
  appId = 'auto_edit',
  sessionId = null,
  signal,
  onStart,
  onChunk,
  onEnd,
  onRequestId,
} = {}) {
  if (!idea || typeof idea !== 'string') throw new Error('idea must be a non-empty string');
  const atts = Array.isArray(attachments) ? attachments : [];

  // Build Message body
  const msgParts = [];
  msgParts.push(idea.trim());
  msgParts.push('');
  msgParts.push('Files Attached:');
  if (!atts.length) {
    msgParts.push('(none)');
  } else {
    for (const a of atts) {
      const name = String(a && a.name ? a.name : '').trim();
      const content = String(a && a.content ? a.content : '').toString();
      msgParts.push(`Name: ${name}`);
      msgParts.push(`Content: ${content}`);
      msgParts.push('');
    }
  }
  const messageText = msgParts.join('\n');

  // Determine reasoning effort from the main chat selector (if available); default to 'none'.
  let reasoningEffort = 'none';
  try {
    if (typeof window !== 'undefined') {
      reasoningEffort = String(window.selectedReasoningEffort || 'none').toLowerCase();
    }
  } catch (_) {
    reasoningEffort = 'none';
  }

  const payload = {
    user_prompt: 'Message:\n' + messageText,
    model,
    system_prompt: 'Prompt:\n' + systemPrompt,
    include_history: !!includeHistory,
    app_id: appId,
  };
  if (sessionId) payload.session_id = sessionId;

  // Attach extra_body so the proxy/Poe can honor reasoning_effort and related options.
  const extraBody = {};
  if (reasoningEffort && reasoningEffort !== 'none') {
    extraBody.reasoning_effort = reasoningEffort;
  }
  extraBody.verbosity = 'high';
  extraBody.temperature = 0;
  extraBody.seed = 42;
  extraBody.max_completion_tokens = 200000;
  extraBody.parallel_tool_calls = false;
  extraBody.response_format = { type: 'text' };
  payload.extra_body = extraBody;

  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal,
  });

  if (!res.ok) {
    const txt = await res.text().catch(() => '<no-text>');
    throw new Error(`AutoEdit AI request failed: ${res.status} ${res.statusText}: ${txt}`);
  }

  // Stream NDJSON if possible
  let full = '';
  const body = res.body;
  if (body && typeof body.getReader === 'function') {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buf = '';
    let started = false;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = decoder.decode(value, { stream: true });
      buf += chunk;
      const lines = buf.split(/\r?\n/);
      buf = lines.pop() || '';
      for (const line of lines) {
        if (!line) continue;
        let obj = null;
        try { obj = JSON.parse(line); } catch (_) { continue; }

        if (obj.status === 'start' && !started) {
          started = true;
          try { if (typeof onRequestId === 'function') onRequestId(obj && obj.request_id, obj); } catch (e) {}
          try { if (typeof onStart === 'function') onStart(obj); } catch (e) {}
          continue;
        }
        if (obj.error) {
          throw new Error(obj.error);
        }
        if (typeof obj.content === 'string' && obj.content) {
          full += obj.content;
          try { if (typeof onChunk === 'function') onChunk(obj.content); } catch (e) {}
          continue;
        }
        if (obj.status === 'end') {
          try { if (typeof onEnd === 'function') onEnd(obj); } catch (e) {}
          // Do not break here; allow reader loop to exhaust
        }
      }
    }

    return full;
  }

  // Fallback: read as text and parse lines
  const raw = await res.text();
  const lines = raw.split(/\r?\n/).filter(Boolean);
  let started = false;
  for (const l of lines) {
    let obj = null;
    try { obj = JSON.parse(l); } catch (_) { continue; }
    if (obj.status === 'start' && !started) {
      started = true;
      try { if (typeof onRequestId === 'function') onRequestId(obj && obj.request_id, obj); } catch (e) {}
      try { if (typeof onStart === 'function') onStart(obj); } catch (e) {}
      continue;
    }
    if (obj.error) throw new Error(obj.error);
    if (typeof obj.content === 'string' && obj.content) {
      full += obj.content;
      try { if (typeof onChunk === 'function') onChunk(obj.content); } catch (e) {}
      continue;
    }
    if (obj.status === 'end') {
      try { if (typeof onEnd === 'function') onEnd(obj); } catch (e) {}
    }
  }
  return full;
}

async function stopAutoEditRequest({ url = 'http://localhost:8129/chat', requestId }) {
  if (!requestId) return;
  const base = String(url || '').replace(/\/?chat$/, '');
  try {
    await fetch(base + '/stop', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ request_id: requestId })
    });
  } catch (_) {}
}

module.exports = {
  sendAutoEditIdea,
  defaultSystemPrompt,
  stopAutoEditRequest,
};
