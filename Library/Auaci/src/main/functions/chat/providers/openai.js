// src/main/functions/chat/providers/openai.js
// Streaming wrapper for OpenAI Chat Completions SSE
// Produces the same chunk shape as the local NDJSON stream: { status: 'start'|'end' } and { content } pieces

function streamOpenAI(payload = {}, opts = {}) {
  return {
    [Symbol.asyncIterator]: async function* () {
      if (typeof fetch === 'undefined') {
        throw new Error('Global fetch is not available in this environment.');
      }

      const cfg = await tryGetAppConfig();

      // Determine API key and base URL
      const apiKey = (opts.openaiApiKey) || (cfg && cfg.openaiApiKey) || process.env.OPENAI_API_KEY;
      if (!apiKey || typeof apiKey !== 'string') {
        throw new Error('OpenAI API key is missing. Set it in app config or OPENAI_API_KEY env.');
      }
      const baseUrl = (opts.openaiBaseUrl) || (cfg && cfg.openaiBaseUrl) || 'https://api.openai.com/v1';
      const isPoe = /poe\.com\b/i.test(String(baseUrl || ''));

      // Model selection
      const model = opts.model || payload.model || (cfg && cfg.defaultModel) || 'gpt-4o-mini';

      // Build messages from payload
      const systemPrompt = payload.system_prompt || '';
      const userPrompt = payload.user_prompt || '';
      const messages = [];
      if (systemPrompt) messages.push({ role: 'system', content: systemPrompt });
      messages.push({ role: 'user', content: userPrompt });

      const body = {
        model,
        stream: true,
        messages,
      };

      // Advanced options: extra_body (Poe-style vendor extensions), tools, and tool_choice.
      // Only send extra_body when talking to Poe; the official OpenAI API does not accept it.
      if (isPoe && opts.extraBody && typeof opts.extraBody === 'object') {
        body.extra_body = opts.extraBody;
      }
      if (Array.isArray(opts.tools) && opts.tools.length > 0) {
        body.tools = opts.tools;
      }
      if (typeof opts.toolChoice !== 'undefined') {
        body.tool_choice = opts.toolChoice;
      }

      const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey}`,
      };
      if (cfg && cfg.openaiOrganization) headers['OpenAI-Organization'] = cfg.openaiOrganization;

      const url = `${baseUrl.replace(/\/$/, '')}/chat/completions`;
      const res = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: opts.signal,
      });

      if (!res.ok) {
        let txt;
        try { txt = await res.text(); } catch { txt = `${res.status} ${res.statusText}`; }
        throw new Error(`OpenAI request failed: ${txt}`);
      }

      if (!res.body) {
        const text = await res.text();
        yield { content: text };
        return;
      }

      yield { status: 'start' };

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          let idx;
          while ((idx = buffer.indexOf('\n')) !== -1) {
            const line = buffer.slice(0, idx).trim();
            buffer = buffer.slice(idx + 1);
            if (!line) continue;
            if (!line.startsWith('data:')) continue;
            const dataStr = line.slice(5).trim();
            if (dataStr === '[DONE]') {
              yield { status: 'end' };
              return;
            }
            try {
              const evt = JSON.parse(dataStr);
              const choice = evt.choices && evt.choices[0];
              if (choice && choice.delta && typeof choice.delta.content === 'string') {
                yield { content: choice.delta.content };
              }
            } catch (_) {
              // ignore malformed chunks
            }
          }
        }
      } finally {
        try { await reader.cancel(); } catch (_) {}
      }

      yield { status: 'end' };
    }
  };
}

async function tryGetAppConfig() {
  try {
    const maybeElectron = require('electron');
    if (maybeElectron && maybeElectron.ipcRenderer && typeof maybeElectron.ipcRenderer.invoke === 'function') {
      const cfg = await maybeElectron.ipcRenderer.invoke('get-config');
      return cfg || null;
    }
  } catch (_) {}
  return null;
}

module.exports = { streamOpenAI };