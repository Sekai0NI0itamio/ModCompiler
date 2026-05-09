// src/main/functions/chat/gpt-api.js
// GPT HTTP streaming client for local server
// Uses proper API format: user_prompt, system_prompt, tools, tool_choice

const fs = require('fs').promises;
const path = require('path');
const os = require('os');

const DEFAULT_URL = 'http://localhost:8129/chat';
const DEFAULT_MODEL = 'GPT-5-nano';
const AUACI_MODEL_FILE = path.join(os.homedir(), '.auaci', 'selected_model.json');

let cachedStoredModel = null;
let cachedModelLoadAttempted = false;

/**
 * loadStoredModel()
 * Reads model from Electron config or fallback file.
 */
async function loadStoredModel() {
  if (cachedModelLoadAttempted) return cachedStoredModel;
  cachedModelLoadAttempted = true;

  // Try Electron config
  try {
    const maybeElectron = require('electron');
    if (maybeElectron && maybeElectron.ipcRenderer && typeof maybeElectron.ipcRenderer.invoke === 'function') {
      const cfg = await maybeElectron.ipcRenderer.invoke('get-config');
      if (cfg && typeof cfg.defaultModel === 'string' && cfg.defaultModel.trim()) {
        cachedStoredModel = cfg.defaultModel.trim();
        return cachedStoredModel;
      }
    }
  } catch (_) {}

  // Fallback to file
  try {
    const raw = await fs.readFile(AUACI_MODEL_FILE, 'utf8');
    if (raw) {
      const obj = JSON.parse(raw);
      if (obj && typeof obj.model === 'string' && obj.model.trim()) {
        cachedStoredModel = obj.model.trim();
        return cachedStoredModel;
      }
    }
  } catch (_) {}

  return null;
}

async function ensureFetchAvailable() {
  if (typeof fetch === 'undefined') {
    throw new Error('Global fetch is not available in this environment.');
  }
}

/**
 * streamChat(payload, opts)
 * 
 * Sends a chat request to the local GPT server.
 * 
 * Payload format (matching c04 API):
 * {
 *   user_prompt: string,        // User message
 *   system_prompt: string,      // System instructions
 *   model: string,              // Model name
 *   session_id: string,         // Session ID
 *   app_id: string,             // App identifier
 *   include_history: boolean,   // Whether to include session history
 *   tools: array,               // Tool definitions
 *   tool_choice: string,        // "auto" | "none" | specific tool
 *   history: array              // Additional history (tool results)
 * }
 * 
 * Response format (NDJSON):
 * {"status": "start", "session_id": "...", "request_id": "..."}
 * {"event": "content", "content": "..."}
 * {"event": "tool_calls", "tool_calls": [...]}  // Streamed as tool builds up
 * {"status": "end", "finish_reason": "stop|tool_calls"}
 */
function streamChat(payload = {}, opts = {}) {
  return {
    [Symbol.asyncIterator]: async function* () {
      await ensureFetchAvailable();

      // Resolve model
      const modelFromOpts = opts.model;
      const storedModel = await loadStoredModel();
      const resolvedModel = modelFromOpts || payload.model || storedModel || DEFAULT_MODEL;

      // Build request body
      const url = opts.url || DEFAULT_URL;
      const bodyObj = {
        model: resolvedModel,
        app_id: payload.app_id || 'editor-v1',
        include_history: payload.include_history !== false,
      };

      // Hoster (poe or openrouter)
      if (payload.hoster) {
        bodyObj.hoster = payload.hoster;
      } else if (opts.hoster) {
        bodyObj.hoster = opts.hoster;
      }

      // User prompt
      if (payload.user_prompt) {
        bodyObj.user_prompt = payload.user_prompt;
      }

      // System prompt
      if (payload.system_prompt) {
        bodyObj.system_prompt = payload.system_prompt;
      }

      // Session ID
      if (payload.session_id) {
        bodyObj.session_id = payload.session_id;
      }

      // Tools
      if (Array.isArray(opts.tools) && opts.tools.length > 0) {
        bodyObj.tools = opts.tools;
      } else if (Array.isArray(payload.tools) && payload.tools.length > 0) {
        bodyObj.tools = payload.tools;
      }

      // Tool choice
      if (opts.toolChoice !== undefined) {
        bodyObj.tool_choice = opts.toolChoice;
      } else if (payload.tool_choice !== undefined) {
        bodyObj.tool_choice = payload.tool_choice;
      } else if (bodyObj.tools && bodyObj.tools.length > 0) {
        bodyObj.tool_choice = 'auto';
      }

      // History (for tool results)
      if (Array.isArray(payload.history) && payload.history.length > 0) {
        bodyObj.history = payload.history;
      }

      const controller = !opts.signal ? new AbortController() : null;
      const signal = opts.signal || (controller && controller.signal);

      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(bodyObj),
        signal,
      });

      if (!res.ok) {
        let txt;
        try {
          txt = await res.text();
          txt = txt.trim() ? txt : `${res.status} ${res.statusText}`;
        } catch (_) {
          txt = `${res.status} ${res.statusText}`;
        }
        throw new Error(`GPT API network error: ${txt}`);
      }

      if (!res.body) {
        const text = await res.text();
        try {
          yield JSON.parse(text);
        } catch {
          yield { raw: text };
        }
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          const parts = buffer.split('\n');
          buffer = parts.pop();

          for (const line of parts) {
            if (!line) continue;
            try {
              const obj = JSON.parse(line);
              
              // Call onStart callback if provided
              if (obj && obj.status === 'start' && typeof opts.onStart === 'function') {
                try { await opts.onStart(obj); } catch (_) {}
              }
              
              yield obj;
            } catch (err) {
              yield { raw: line };
            }
          }
        }

        // Flush remainder
        if (buffer && buffer.trim()) {
          try {
            yield JSON.parse(buffer);
          } catch {
            yield { raw: buffer };
          }
        }
      } finally {
        try { await reader.cancel(); } catch (_) {}
      }
    }
  };
}

/**
 * streamToString(payload, opts)
 * Convenience helper that collects content chunks into a string.
 * Also returns tool_calls if present.
 */
async function streamToString(payload = {}, opts = {}) {
  let out = '';
  let toolCalls = null;
  let sessionId = null;
  let requestId = null;
  
  for await (const chunk of streamChat(payload, opts)) {
    // Handle start event
    if (chunk && chunk.status === 'start') {
      sessionId = chunk.session_id;
      requestId = chunk.request_id;
    }
    
    // Handle content event
    if (chunk && chunk.event === 'content' && typeof chunk.content === 'string') {
      out += chunk.content;
    }
    
    // Handle legacy content format
    if (chunk && typeof chunk.content === 'string' && !chunk.event) {
      out += chunk.content;
    }
    
    // Handle raw text
    if (chunk && typeof chunk.raw === 'string') {
      out += chunk.raw;
    }
    
    // Handle tool_calls event
    if (chunk && chunk.event === 'tool_calls' && Array.isArray(chunk.tool_calls)) {
      toolCalls = chunk.tool_calls;
    }
    
    // Handle error
    if (chunk && chunk.error) {
      throw new Error(String(chunk.error));
    }
  }
  
  return { text: out, tool_calls: toolCalls, session_id: sessionId, request_id: requestId };
}

/**
 * stopRequest(requestId, opts)
 * Cancels an ongoing request.
 */
async function stopRequest(requestId, opts = {}) {
  await ensureFetchAvailable();
  const url = (opts.url || DEFAULT_URL).replace(/\/chat$/, '/stop');
  try {
    await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ request_id: requestId })
    });
  } catch (_) {}
}

module.exports = {
  streamChat,
  streamToString,
  stopRequest,
  DEFAULT_URL,
  DEFAULT_MODEL,
  _loadStoredModel: loadStoredModel,
};
