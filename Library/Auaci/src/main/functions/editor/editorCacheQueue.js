// src/main/functions/editor/editorCacheQueue.js
// Debounced wrapper around editorCache.saveCache to reduce disk churn.
const editorCache = require('./editorCache');

let _timer = null;
const SAVE_DEBOUNCE_MS = 800;

function _saveImmediate(tabs, activePath) {
  try {
    return editorCache.saveCache(tabs, activePath);
  } catch (err) {
    console.warn('editorCache.saveCache error:', err);
    return Promise.resolve();
  }
}

function queueSave(tabs, activePath) {
  try {
    if (_timer) clearTimeout(_timer);
    _timer = setTimeout(() => {
      _saveImmediate(tabs, activePath).catch(err => {
        console.warn('editorCache.saveCache (deferred) failed:', err);
      });
    }, SAVE_DEBOUNCE_MS);
  } catch (err) {
    // fallback: immediate
    _saveImmediate(tabs, activePath).catch(e => console.warn('editorCache.saveCache fallback failed:', e));
  }
}

module.exports = {
  queueSave,
  saveImmediate: _saveImmediate
};