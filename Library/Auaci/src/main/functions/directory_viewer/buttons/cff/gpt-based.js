// src/main/functions/directory_viewer/buttons/cff/gpt-based.js
// GPT-based parser that asks the local proxy to return relative paths in a code block.
// Caches last input -> parsed result. Supports being aborted via AbortSignal.
//
// Usage:
// const gpt = require('./cff/gpt-based');
// const result = await gpt.parseWithCache(treeText, { signal, timeout });
// result.paths -> array of relative paths
// result.fromCache -> boolean

const { sendToGpt } = require('../../../united/api'); // relative to this file's location
const sanitize = (s) => (s || '').replace(/\r/g, '').trim();

let lastInput = null;
let lastParsed = null;
let lastRaw = null;
let currentController = null;
let currentPromise = null;

/**
 * Extract paths from assistant text:
 * - Prefer a ```projectstructure ... ``` code block
 * - Fallback to any triple-backtick block
 * - Fallback to extracting lines that look like paths
 */
function extractPathsFromAssistantText(text) {
  if (!text || typeof text !== 'string') return [];

  // Try labelled block first
  let blockMatch = text.match(/```projectstructure\s*([\s\S]*?)\s*```/i);
  if (!blockMatch) {
    // Any code block
    blockMatch = text.match(/```(?:\w+)?\s*([\s\S]*?)\s*```/);
  }

  let content = blockMatch ? blockMatch[1] : text;

  const lines = content.split('\n').map(l => l.trim()).filter(Boolean);

  const paths = [];
  for (const l of lines) {
    // ignore lines that are not plausible paths (but keep lines that contain '/' OR look like file names)
    if (l.startsWith('//') || l.startsWith('#')) continue;
    let cleaned = l.replace(/^\.\//, '').replace(/^\/+/, '').trim();
    // Remove bullets or leading characters like '-', '*'
    cleaned = cleaned.replace(/^[\-\*\•\s>]+/, '').trim();
    if (!cleaned) continue;
    // Only accept lines with at least one letter/number
    if (!/[a-zA-Z0-9]/.test(cleaned)) continue;
    paths.push(cleaned);
  }

  // Deduplicate while preserving order
  const seen = new Set();
  const dedup = [];
  for (const p of paths) {
    if (!seen.has(p)) {
      seen.add(p);
      dedup.push(p);
    }
  }
  return dedup;
}

/**
 * Parse with caching. If the provided treeText equals the lastInput we will return cached result.
 * If not cached, we call sendToGpt with a clear prompt.
 *
 * Options:
 * - signal: AbortSignal to cancel the request
 * - timeout: ms
 */
async function parseWithCache(treeText, { signal = null, timeout = 120000 } = {}) {
  const text = sanitize(treeText || '');
  if (!text) {
    // Nothing to parse
    lastInput = null;
    lastParsed = null;
    lastRaw = null;
    return { paths: [], fromCache: false, raw: '' };
  }

  if (text === lastInput && Array.isArray(lastParsed)) {
    return { paths: lastParsed.slice(), fromCache: true, raw: lastRaw || '' };
  }

  // If there is a running request, abort it
  if (currentController) {
    try {
      currentController.abort();
    } catch (e) {}
    currentController = null;
    currentPromise = null;
  }

  currentController = new AbortController();
  const internalSignal = currentController.signal;

  if (signal) {
    if (signal.aborted) {
      currentController.abort();
    } else {
      const onAbort = () => currentController.abort();
      signal.addEventListener('abort', onAbort);
      // ensure removal after promise resolves/rejects
      const cleanupListener = () => {
        try { signal.removeEventListener('abort', onAbort); } catch (e) {}
      };
      // We'll call cleanup later when promise completes
      // store cleanup for later (simple way: attach to internal controller)
      internalSignal._cleanupFromParent = cleanupListener;
    }
  }

  // Build robust prompt instructing GPT to output only a code block labeled projectstructure
  const promptParts = [
    "You are given a textual representation of a project tree. Respond ONLY with a single code block labeled",
    "`projectstructure` that contains a newline-separated list of relative file and folder paths",
    "needed to create the project. Use forward slashes '/', no extra explanatory text.",
    "Folders may be listed as just the folder path (no trailing slash required). Files should include their file name.",
    "Example output:",
    "```projectstructure",
    "src/main/example.js",
    "README.md",
    "```",
    "",
    "Here is the project tree to parse:",
    "```",
    text,
    "```",
    "",
    "Return only the code block with the relative paths."
  ];

  const prompt = promptParts.join('\n');

  // Perform the request
  currentPromise = (async () => {
    try {
      const assistantText = await sendToGpt({
        prompt,
        model: "GPT-5.1-codex",
        timeout,
        signal: internalSignal
      });

      // cleanup parent abort listener if present
      if (internalSignal && internalSignal._cleanupFromParent) {
        try { internalSignal._cleanupFromParent(); } catch (e) {}
      }

      const paths = extractPathsFromAssistantText(assistantText);
      lastInput = text;
      lastParsed = paths.slice();
      lastRaw = assistantText;
      currentController = null;
      currentPromise = null;
      return { paths: paths.slice(), fromCache: false, raw: assistantText };
    } catch (err) {
      // cleanup parent abort listener if present
      if (internalSignal && internalSignal._cleanupFromParent) {
        try { internalSignal._cleanupFromParent(); } catch (e) {}
      }
      currentController = null;
      currentPromise = null;
      // normalize abort message
      if (err && err.message && err.message.includes('aborted')) {
        throw new Error('aborted');
      }
      throw err;
    }
  })();

  return currentPromise;
}

function abortCurrent() {
  if (currentController) {
    try {
      currentController.abort();
    } catch (e) {}
    currentController = null;
    currentPromise = null;
  }
}

module.exports = {
  parseWithCache,
  abortCurrent,
};