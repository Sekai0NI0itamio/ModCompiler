// src/main/functions/editor/editorCache.js
// Simple cache for editor tabs and active path, stored under .auaci/editor/cache.txt
// NOTE: Only persist actual file content when the tab is actively edited (unsaved === true).
//       Previously this persisted content for temporary/view-only tabs which caused large
//       cache files and memory usage when users just inspected files. This change avoids
//       storing heavy content unless the user has made unsaved edits.

const fs = require('fs').promises;
const path = require('path');
const { ipcRenderer } = require('electron');

async function _getProjectRoot() {
  if (window.projectRoot) return window.projectRoot;
  try {
    const pr = await ipcRenderer.invoke('get-project-root');
    if (pr) window.projectRoot = pr;
    return pr;
  } catch (err) {
    console.warn('editorCache: failed to get project root:', err);
    return null;
  }
}

/**
 * Save cache:
 * - persist metadata for all open tabs (name, path, id, selection, view state, wrap, temp, unsaved)
 * - persist content ONLY for tabs that are currently unsaved (user edited).
 *   This prevents storing large file blobs for tabs the user only previewed or opened read-only,
 *   and avoids doubling cache size by storing both content + savedContent.
 * - always persist activePath (if provided)
 */
async function saveCache(tabs, activePath) {
  try {
    const projectRoot = await _getProjectRoot();
    if (!projectRoot) return;
    const dir = path.join(projectRoot, '.auaci', 'editor');
    await fs.mkdir(dir, { recursive: true });
    const filePath = path.join(dir, 'cache.txt');

    const savedAt = new Date().toISOString();

    const serialTabs = (tabs || []).map(t => {
      const item = {
        name: t.name,
        path: t.path,
        id: t.id,
        size: (typeof t.size === 'number') ? t.size : null,
        diskSize: (typeof t.diskSize === 'number') ? t.diskSize : null,
        diskMtimeMs: (typeof t.diskMtimeMs === 'number') ? t.diskMtimeMs : null,
        unsaved: !!t.unsaved,
        temp: !!t.temp,
        wrap: !!t.wrap, // persist wrap preference
        // view state
        selection: t.selection || null,
        cursorIndex: (typeof t.cursorIndex === 'number') ? t.cursorIndex : null,
        scrollTop: (typeof t.scrollTop === 'number') ? t.scrollTop : null,
        scrollLeft: (typeof t.scrollLeft === 'number') ? t.scrollLeft : null
      };

      // Persist actual content only for tabs that are unsaved (user edited).
      // Do NOT persist content merely because a tab is temporary or was previewed.
      if (t.unsaved) {
        // Monaco is the source of truth; `t.content` is a best-effort snapshot we keep for persistence.
        // If it's missing we still write an empty string instead of duplicating `savedContent`.
        item.content = t.content == null ? '' : String(t.content);
      }

      // preserve media metadata if present
      if (t.mediaType) item.mediaType = t.mediaType;
      if (t.mime) item.mime = t.mime;
      if (t.dataUrl) item.dataUrl = t.dataUrl;

      return item;
    });

    const data = { tabs: serialTabs, activePath: activePath || null, savedAt };
    await fs.writeFile(filePath, JSON.stringify(data, null, 2), 'utf8');
  } catch (err) {
    console.error('editorCache.saveCache failed:', err);
  }
}

async function loadCache() {
  try {
    const projectRoot = await _getProjectRoot();
    if (!projectRoot) return null;
    const filePath = path.join(projectRoot, '.auaci', 'editor', 'cache.txt');
    const data = await fs.readFile(filePath, 'utf8');

    // If file is empty, treat as an empty cache (don't throw).
    if (!data || String(data).trim() === '') {
      return { tabs: [], activePath: null, savedAt: null };
    }

    return JSON.parse(data);
  } catch (err) {
    if (err && err.code === 'ENOENT') return null;
    console.error('editorCache.loadCache failed:', err);
    return null;
  }
}

module.exports = { saveCache, loadCache };
