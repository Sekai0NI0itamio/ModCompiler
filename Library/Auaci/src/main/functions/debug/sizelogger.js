// src/main/functions/debug/sizelogger.js
const fs = require('fs').promises;

/**
 * Helper to read widths and format a single debug line.
 * Exports startSizeLogger which appends size snapshots to a file at a fixed interval.
 */

function nowISO() {
  return new Date().toISOString();
}

function readWidth(el) {
  if (!el) return null;
  // Use getBoundingClientRect for a reliable layout width (float)
  const rect = el.getBoundingClientRect();
  return Math.round(rect.width);
}

function buildSizesObject(selectors) {
  const sizes = {};
  for (const key in selectors) {
    const sel = selectors[key];
    const el = document.querySelector(sel);
    sizes[key] = readWidth(el);
  }
  // Add window width
  sizes.window = Math.round(window.innerWidth);
  return sizes;
}

function formatSizesLine(sizes) {
  // Example: [SizeLogger] 2025-08-08T12:00:00.000Z — directory: 240, editor: 720, chat: 320, app: 1280, window: 1400
  const parts = [];
  for (const k of Object.keys(sizes)) {
    parts.push(`${k}: ${sizes[k] === null ? 'null' : sizes[k]}`);
  }
  return `[SizeLogger] ${nowISO()} — ${parts.join(', ')}`;
}

/**
 * Start the size logger which appends a timestamped sizes line to filePath every intervalMs.
 *
 * selectors: map of logicalName => selector (defaults to main panels)
 * options:
 *   - intervalMs (default 1000)
 *   - filePath (default '/tmp/size.log')
 *   - appendNewline (default true)
 *
 * Returns an object with stop() method to cancel logging.
 */
function startSizeLogger(selectors = {
  directory: '#directory-viewer',
  editor: '#editor',
  chat: '#chat',
  app: '#app-container'
}, options = {}) {
  const {
    intervalMs = 1000,
    filePath = '/tmp/size.log',
    appendNewline = true
  } = options;

  let timer = null;
  let stopped = false;

  async function captureAndAppend() {
    try {
      const sizes = buildSizesObject(selectors);
      const line = formatSizesLine(sizes);
      const payload = appendNewline ? (line + '\n') : line;
      await fs.appendFile(filePath, payload);
    } catch (err) {
      // If writing fails, attempt to write an error line to console (do not throw)
      // Writing to file may fail if permissions or path issues exist.
      console.error('[SizeLogger] Failed to append to file:', err && err.message ? err.message : err);
    }
  }

  // Start immediate write then schedule interval
  (async () => {
    if (stopped) return;
    await captureAndAppend();
    if (stopped) return;
    timer = setInterval(() => {
      captureAndAppend().catch(() => { /* errors already handled inside */ });
    }, intervalMs);
  })();

  return {
    stop: () => {
      stopped = true;
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    }
  };
}

module.exports = { startSizeLogger, buildSizesObject, formatSizesLine };