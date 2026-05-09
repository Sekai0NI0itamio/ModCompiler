// Per-session scroll position cache stored at ~/.auaci/chat/scroll_positions.json
// Exports:
//   - getScroll(sessionId) -> { topEntryIndex, offset, scrollTop, ts } | null
//   - setScroll(sessionId, data) -> saved object
//   - deleteScroll(sessionId)
//   - saveScrollFromContainer(sessionId, container) -> computes topEntryIndex & offset from DOM and saves

const fs = require('fs').promises;
const path = require('path');
const os = require('os');

const CACHE_DIR = path.join(os.homedir(), '.auaci', 'chat');
const CACHE_FILE = path.join(CACHE_DIR, 'scroll_positions.json');
const TMP_FILE = CACHE_FILE + '.tmp';

let _cache = null;
let _writePromise = null;

async function _ensureDir() {
  try { await fs.mkdir(CACHE_DIR, { recursive: true }); } catch (_) {}
}

async function _loadCache() {
  if (_cache !== null) return;
  try {
    const txt = await fs.readFile(CACHE_FILE, 'utf8');
    _cache = JSON.parse(txt || '{}');
  } catch (e) {
    _cache = {};
  }
}

async function _writeCacheAtomic() {
  if (_writePromise) return _writePromise;
  await _ensureDir();
  try {
    _writePromise = fs.writeFile(TMP_FILE, JSON.stringify(_cache || {}, null, 2), 'utf8')
      .then(() => fs.rename(TMP_FILE, CACHE_FILE).catch(() => {}))
      .catch(() => {});
    await _writePromise;
  } catch (_) {
    // swallow write errors
  } finally {
    _writePromise = null;
  }
}

async function getScroll(sessionId) {
  if (!sessionId) return null;
  await _loadCache();
  return _cache[sessionId] || null;
}

async function setScroll(sessionId, data = {}) {
  if (!sessionId) return null;
  await _loadCache();
  // Keep a small shape: topEntryIndex (int), offset (int), scrollTop (int), ts (ms)
  const entry = {
    topEntryIndex: (typeof data.topEntryIndex === 'number') ? Math.max(0, Math.floor(data.topEntryIndex)) : (data.topEntryIndex != null ? Number(data.topEntryIndex) : undefined),
    offset: (typeof data.offset === 'number') ? Math.round(data.offset) : (typeof data.offset === 'string' ? Math.round(Number(data.offset) || 0) : 0),
    scrollTop: (typeof data.scrollTop === 'number') ? Math.round(data.scrollTop) : undefined,
    ts: Date.now()
  };
  // Avoid writing if nothing meaningful changed
  const prev = _cache[sessionId] || {};
  const prevStr = JSON.stringify(prev);
  const newStr = JSON.stringify(entry);
  if (prevStr === newStr) return entry; // no-op
  _cache[sessionId] = entry;
  await _writeCacheAtomic().catch(() => {});
  return entry;
}

async function deleteScroll(sessionId) {
  if (!sessionId) return;
  await _loadCache();
  if (_cache && _cache[sessionId]) {
    delete _cache[sessionId];
    await _writeCacheAtomic().catch(() => {});
  }
}

/**
 * Compute the top visible entry (data-entry-index on message nodes)
 * and offset within that node, then save.
 * container: DOM element (chat-messages)
 */
async function saveScrollFromContainer(sessionId, container) {
  if (!sessionId) return null;
  if (!container || typeof container.querySelectorAll !== 'function') {
    // fallback to saving raw scrollTop
    return await setScroll(sessionId, { scrollTop: (container && container.scrollTop) || 0 });
  }

  try {
    const nodes = Array.from(container.querySelectorAll('[data-entry-index]'));
    if (nodes.length === 0) {
      // nothing mapped yet; save scrollTop only
      return await setScroll(sessionId, { scrollTop: container.scrollTop || 0 });
    }

    const containerRect = container.getBoundingClientRect();
    const scTop = container.scrollTop || 0;
    // Find the last node whose relative top <= scTop (+ small tolerance)
    let topNode = null;
    let topNodeRelTop = 0;
    for (let i = 0; i < nodes.length; i++) {
      const n = nodes[i];
      const nRect = n.getBoundingClientRect();
      const relTop = (nRect.top - containerRect.top) + scTop;
      if (relTop <= scTop + 1) {
        topNode = n;
        topNodeRelTop = relTop;
      } else {
        // once a node is below the current scrollTop, break (DOM is ordered)
        break;
      }
    }
    // if none found, use first node
    if (!topNode) {
      topNode = nodes[0];
      const rect = topNode.getBoundingClientRect();
      topNodeRelTop = (rect.top - containerRect.top) + scTop;
    }

    const topEntryIndexStr = topNode.getAttribute('data-entry-index');
    const topEntryIndex = topEntryIndexStr ? parseInt(topEntryIndexStr, 10) : undefined;
    const offset = Math.max(0, Math.round(scTop - topNodeRelTop));

    return await setScroll(sessionId, { topEntryIndex, offset, scrollTop: scTop });
  } catch (err) {
    // on errors, fallback to saving raw scrollTop
    return await setScroll(sessionId, { scrollTop: (container && container.scrollTop) || 0 });
  }
}

module.exports = {
  getScroll,
  setScroll,
  deleteScroll,
  saveScrollFromContainer
};