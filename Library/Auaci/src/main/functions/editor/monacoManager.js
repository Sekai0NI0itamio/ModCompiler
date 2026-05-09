// src/main/functions/editor/monacoManager.js
// Improved Monaco loader for Electron renderer
// - percent-encodes file:// URLs (handles spaces)
// - temporarily hides Node/Electron globals (module, exports, process) so the AMD loader
//   initializes in browser/AMD mode and modules call define()
// - robust onload/onerror handling and clearer console logging
// - provides helpers for model creation and suppression of change events so programmatic
//   updates don't mark tabs unsaved.
// - includes editor options to disable occurrence/selection highlights and overview ruler markers.
// - disables Monaco's "sticky scroll" (function header shown at top) completely

const { ipcRenderer } = require('electron');
const path = require('path');

let _monaco = null;
let _editor = null; // primary editor instance (main code editor)
let _loading = null;
let _suspendChange = false;

// Track multiple editors keyed by container id so ephemeral editors (e.g., preview/search overlays)
// do not clobber the primary editor.
const _editors = new Map(); // containerId -> editor
let _primaryContainerId = 'editor-content';

function fileUrlForLocalPath(p) {
  // Use path.resolve then encodeURI the file:// URL so spaces and special chars are safe
  const resolved = path.resolve(p);
  // On macOS / POSIX: add leading slash and encode
  const fileUrl = `file://${resolved}`;
  return encodeURI(fileUrl);
}

async function loadMonacoFromNodeModules() {
  if (_loading) return _loading;

  _loading = (async () => {
    const appPath = await ipcRenderer.invoke('get-app-path');
    const vsDir = path.join(appPath, 'node_modules', 'monaco-editor', 'min', 'vs');
    const loaderPath = path.join(vsDir, 'loader.js');

    const loaderUrl = fileUrlForLocalPath(loaderPath);
    const vsPathUrl = fileUrlForLocalPath(vsDir);

    console.info('[monacoManager] loaderUrl=', loaderUrl);
    console.info('[monacoManager] vsPathUrl=', vsPathUrl);

    if (window.monaco) {
      _monaco = window.monaco;
      return _monaco;
    }

    return new Promise((resolve, reject) => {
      // Save originals
      const origModule = window.module;
      const origExports = window.exports;
      const origProcess = window.process;
      const origGlobal = window.global;

      // Hide Node/Electron globals so AMD loader runs in browser mode
      try {
        try { delete window.module; } catch (e) { window.module = undefined; }
        try { delete window.exports; } catch (e) { window.exports = undefined; }
        try { delete window.process; } catch (e) { window.process = undefined; }
        // ensure global refers to window for scripts that expect it
        window.global = window;
      } catch (e) { /* ignore */ }

      const existing = document.querySelector('script[data-monaco-loader-src]');
      if (!existing) {
        const s = document.createElement('script');
        s.setAttribute('data-monaco-loader-src', loaderUrl);
        s.type = 'text/javascript';
        s.src = loaderUrl;

        s.onload = () => {
          // restore originals quickly (after loader created window.require)
          setTimeout(() => {
            try {
              if (origModule !== undefined) window.module = origModule; else try { delete window.module; } catch (_) {}
              if (origExports !== undefined) window.exports = origExports; else try { delete window.exports; } catch (_) {}
              if (origProcess !== undefined) window.process = origProcess; else try { delete window.process; } catch (_) {}
              if (origGlobal !== undefined) window.global = origGlobal;
            } catch (e) { /* ignore */ }

            try {
              if (typeof window.require !== 'function') {
                const msg = '[monacoManager] loader loaded but window.require is not defined.';
                console.error(msg);
                reject(new Error(msg));
                return;
              }

              // Configure AMD loader base for 'vs'
              try {
                window.require.config({ paths: { vs: vsPathUrl } });
              } catch (cfgErr) {
                console.warn('[monacoManager] require.config failed:', cfgErr && cfgErr.message ? cfgErr.message : cfgErr);
                // still try to require editor.main
              }

              // Load main editor module
              window.require(['vs/editor/editor.main'], () => {
                _monaco = window.monaco;
                if (!_monaco) {
                  const msg = '[monacoManager] window.monaco is not available after require.';
                  console.error(msg);
                  reject(new Error(msg));
                  return;
                }
                console.info('[monacoManager] Monaco loaded successfully.');
                resolve(_monaco);
              }, (err) => {
                const detail = (err && err.message) ? err.message : JSON.stringify(err);
                const msg = `[monacoManager] require(['vs/editor/editor.main']) failed: ${detail}`;
                console.error(msg, err);
                reject(new Error(msg));
              });
            } catch (err) {
              console.error('[monacoManager] Error after loader onload:', err && err.message ? err.message : err);
              reject(err);
            }
          }, 0);
        };

        s.onerror = (e) => {
          // restore originals
          try {
            if (origModule !== undefined) window.module = origModule; else try { delete window.module; } catch (_) {}
            if (origExports !== undefined) window.exports = origExports; else try { delete window.exports; } catch (_) {}
            if (origProcess !== undefined) window.process = origProcess; else try { delete window.process; } catch (_) {}
            if (origGlobal !== undefined) window.global = origGlobal;
          } catch (ee) { /* ignore */ }

          const msg = `[monacoManager] Failed to load loader script: ${loaderUrl}`;
          console.error(msg, e);
          reject(new Error(msg + ' ' + String(e)));
        };

        document.body.appendChild(s);
      } else {
        // Loader script already present. Restore globals then attempt require.
        try {
          if (origModule !== undefined) window.module = origModule; else try { delete window.module; } catch (_) {}
          if (origExports !== undefined) window.exports = origExports; else try { delete window.exports; } catch (_) {}
          if (origProcess !== undefined) window.process = origProcess; else try { delete window.process; } catch (_) {}
          if (origGlobal !== undefined) window.global = origGlobal;
        } catch (e) { /* ignore */ }

        try {
          if (typeof window.require !== 'function') {
            const msg = '[monacoManager] loader already present but window.require is not defined.';
            console.error(msg);
            reject(new Error(msg));
            return;
          }
          window.require.config({ paths: { vs: vsPathUrl } });
          window.require(['vs/editor/editor.main'], () => {
            _monaco = window.monaco;
            if (!_monaco) {
              const msg = '[monacoManager] window.monaco is not available after require.';
              console.error(msg);
              reject(new Error(msg));
              return;
            }
            console.info('[monacoManager] Monaco loaded successfully (existing loader).');
            resolve(_monaco);
          }, (err) => {
            const detail = (err && err.message) ? err.message : JSON.stringify(err);
            const msg = `[monacoManager] require(['vs/editor/editor.main']) failed (existing loader): ${detail}`;
            console.error(msg, err);
            reject(new Error(msg));
          });
        } catch (err) {
          console.error('[monacoManager] Error using existing loader:', err && err.message ? err.message : err);
          reject(err);
        }
      }
    });
  })();

  return _loading;
}

async function initEditor(containerId, options = {}) {
  const monaco = await loadMonacoFromNodeModules();
  if (!monaco) throw new Error('Monaco failed to load');

  const container = document.getElementById(containerId);
  if (!container) throw new Error('Editor container not found: ' + containerId);

  // If there is already an editor bound to this container id, dispose it cleanly
  const existing = _editors.get(containerId);
  if (existing) {
    try { existing.dispose && existing.dispose(); } catch (_) {}
    _editors.delete(containerId);
    if (containerId === _primaryContainerId) {
      _editor = null;
    }
  }

  const editorOptions = Object.assign({
    value: options.value || '',
    language: options.language || 'plaintext',
    theme: options.theme || 'vs-light',
    automaticLayout: true,
    fontSize: options.fontSize || 13,
    minimap: { enabled: false },

    // Preferred scrollbar behavior: show vertical overlay scrollbar, hide horizontal visuals by default.
    // Monaco accepts nested 'scrollbar' options; if a given Monaco version doesn't use them they will be ignored.
    scrollbar: {
      vertical: 'visible',
      horizontal: 'auto',
      verticalScrollbarSize: 12,
      horizontalScrollbarSize: 12,
      handleMouseWheel: true
    },

    // Turn off typical "auto-correct / suggestion" behaviors:
    quickSuggestions: false,
    suggestOnTriggerCharacters: false,
    wordBasedSuggestions: false,
    // Accept suggestions on Enter shouldn't cause surprising replacements:
    acceptSuggestionOnEnter: 'off',

    // Disable Monaco's occurrence/selection highlights and overview ruler markers:
    occurrencesHighlight: false,
    selectionHighlight: false,
    overviewRulerLanes: 0,
    overviewRulerBorder: false,

    // Disable sticky scroll / "current function" header (newer Monaco uses editor.stickyScroll)
    // Provide both legacy and new-style options to ensure it is turned off across versions.
    stickyScroll: { enabled: false },
    // Some Monaco versions accept a boolean or `editor.stickyScroll.enabled`
    // also include top-level flag for safety:
    stickyScrollEnabled: false,
    // Optional: hide the left glyph margin if you don't need it
    glyphMargin: false
  }, options);

  const created = monaco.editor.create(container, editorOptions);
  _monaco = monaco;

  // Track editor by container id and choose whether it becomes primary
  _editors.set(containerId, created);
  try {
    created.onDidDispose?.(() => {
      try { _editors.delete(containerId); } catch (_) {}
      if (containerId === _primaryContainerId) {
        _editor = null;
      }
    });
  } catch (_) {}

  // Choose primary editor: default to the main content container id
  if (!_primaryContainerId) _primaryContainerId = 'editor-content';
  if (containerId === _primaryContainerId) {
    _editor = created;
  } else if (!_editor) {
    // If primary not yet established, fall back to first created
    _editor = created;
    _primaryContainerId = containerId;
  }

  // Also ensure the runtime options are applied in case some platform/theme altered them
  try {
    created.updateOptions({
      occurrencesHighlight: false,
      selectionHighlight: false,
      overviewRulerLanes: 0,
      overviewRulerBorder: false,
      glyphMargin: false,
      // Ensure sticky scroll is off at runtime:
      stickyScroll: { enabled: false },
      // also attempt boolean style option for other versions:
      stickyScrollEnabled: false,
      // Ensure vertical scrollbar is visible at runtime if Monaco accepts it:
      scrollbar: {
        vertical: 'visible',
        horizontal: 'auto',
        verticalScrollbarSize: 12,
        horizontalScrollbarSize: 12,
        handleMouseWheel: true
      }
    });
  } catch (e) {
    // ignore if updateOptions fails for any reason
  }

  setTimeout(() => {
    try { _editor.layout(); } catch (_) {}
  }, 50);

  return { monaco: _monaco, editor: created };
}

function getEditor() { return _editor; }
function getMonaco() { return _monaco; }

/**
 * Return the editor instance for a given container id (if any).
 */
function getEditorByContainerId(id) {
  return _editors.get(id) || null;
}

/**
 * Iterate known editors and try to find one that visually contains the event target or coordinates.
 */
function getEditorFromEvent(e) {
  try {
    if (!_editors.size) return _editor;
    let target = null;
    try { target = e && (e.target || (typeof e.composedPath === 'function' && e.composedPath()[0])) || null; } catch (_) {}
    let x = null, y = null;
    try { x = (typeof e.clientX === 'number') ? e.clientX : null; } catch (_) {}
    try { y = (typeof e.clientY === 'number') ? e.clientY : null; } catch (_) {}

    for (const [id, ed] of _editors.entries()) {
      if (!ed || typeof ed.getDomNode !== 'function') continue;
      let dom = null;
      try { dom = ed.getDomNode(); } catch (_) { dom = null; }
      if (!dom) continue;
      try {
        if (target && dom.contains && dom.contains(target)) return ed;
      } catch (_) {}
      try {
        if (x != null && y != null) {
          const rect = dom.getBoundingClientRect && dom.getBoundingClientRect();
          if (rect && x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) return ed;
        }
      } catch (_) {}
    }
    return _editor;
  } catch (_) {
    return _editor;
  }
}

/**
 * Return all tracked editors.
 */
function getAllEditors() {
  return Array.from(_editors.values());
}

/**
 * Create or retrieve a Monaco model for a given path.
 *
 * Performance note:
 * Avoid calling model.getValue() just to compare content. For large files that
 * becomes very expensive and can cause tab-switch jank. Callers should tell us
 * when they *know* content must be updated (opts.update === true).
 *
 * @param {string} filePath
 * @param {string} content
 * @param {string} language
 * @param {{ update?: boolean, setLanguage?: boolean }} opts
 */
function createModelForPath(filePath, content, language, opts = {}) {
  if (!_monaco) return null;

  let uri;
  try {
    if (filePath && (filePath.indexOf('://') !== -1)) {
      uri = _monaco.Uri.parse(filePath);
    } else {
      // Use file URI for real file paths and a "untitled" scheme for untitled
      if (filePath && filePath.startsWith('untitled://')) {
        uri = _monaco.Uri.parse(filePath);
      } else {
        uri = _monaco.Uri.file(filePath || ('untitled://' + Date.now()));
      }
    }
  } catch (err) {
    // last-resort: create an in-memory URI
    uri = _monaco.Uri.parse('untitled://' + (filePath || Date.now()));
  }

  let model = _monaco.editor.getModel(uri);
  if (!model) {
    try {
      model = _monaco.editor.createModel(content == null ? '' : String(content), language || undefined, uri);
    } catch (e) {
      // fallback: create model without a uri
      model = _monaco.editor.createModel(content == null ? '' : String(content), language || undefined);
    }
  } else {
    // Language can be expensive for large models; only apply when requested.
    // Apply BEFORE setValue so "very large file mode" can switch to plaintext and avoid heavy tokenization.
    if (language && (opts && opts.setLanguage)) {
      try {
        _monaco.editor.setModelLanguage(model, language);
      } catch (_) {}
    }

    // Only update model content when the caller explicitly asks us to.
    if (opts && opts.update) {
      ignoreModelContentChanges(() => {
        try {
          model.setValue(content == null ? '' : String(content));
        } catch (_) {}
      });
    }
  }
  return model;
}

function setModel(model) {
  if (!_editor) return;
  _editor.setModel(model);
}

/**
 * Set model on a specific editor instance (or primary when not provided).
 */
function setModelForPath(filePath, content, language, targetEditor, opts = {}) {
  const model = createModelForPath(filePath, content, language, opts);
  const ed = targetEditor || _editor;
  if (ed && model) ed.setModel(model);
  return model;
}

function setValue(val) { if (!_editor) return; _editor.setValue(val == null ? '' : String(val)); }
function getValue() { if (!_editor) return ''; return _editor.getValue(); }
function focus() { if (!_editor) return; _editor.focus(); }

function onDidChangeModelContent(fn) {
  if (!_editor) return () => {};
  return _editor.onDidChangeModelContent((e) => {
    if (_suspendChange) return;
    try { fn(e); } catch (e) { /* ignore user handler errors */ }
  });
}

function layout() { if (!_editor) return; try { _editor.layout(); } catch (_) {} }

/**
 * Run a synchronous callback while suppressing the onDidChangeModelContent handlers.
 * Useful when programmatically setting model or value.
 */
function ignoreModelContentChanges(callback) {
  const prev = _suspendChange;
  _suspendChange = true;
  try {
    callback && callback();
  } finally {
    _suspendChange = !!prev;
  }
}

/**
 * Basic language detection from extension (fallback to plaintext).
 * Extend this map as needed.
 */
function detectLanguageFromPath(filePath) {
  if (!filePath) return 'plaintext';
  const ext = String(filePath).split('.').pop().toLowerCase();
  const map = {
    'js': 'javascript',
    'mjs': 'javascript',
    'cjs': 'javascript',
    'ts': 'typescript',
    'tsx': 'typescript',
    'jsx': 'javascript',
    'py': 'python',
    'rb': 'ruby',
    'java': 'java',
    'html': 'html',
    'htm': 'html',
    'css': 'css',
    'scss': 'scss',
    'json': 'json',
    'md': 'markdown',
    'sh': 'shell',
    'bash': 'shell',
    'yml': 'yaml',
    'yaml': 'yaml',
    'go': 'go',
    'rs': 'rust',
    'php': 'php',
    'xml': 'xml',
    'sql': 'sql'
  };
  return map[ext] || 'plaintext';
}

function pathNormalize(p) {
  // Ensure we return something usable for file URIs; on Windows, monaco.Uri.file will handle paths.
  return path.resolve(p || '');
}

/**
 * Update options on the currently running editor instance.
 * Use this to toggle visual behaviors at runtime.
 */
function updateEditorOptions(opts = {}) {
  if (!_editor) return;
  try {
    // Ensure that sticky scroll is not re-enabled inadvertently by consumers:
    const merged = Object.assign({}, opts);
    // If caller does not explicitly enable stickyScroll, force-disable it
    if (merged.stickyScroll === undefined && merged.stickyScrollEnabled === undefined) {
      merged.stickyScroll = { enabled: false };
      merged.stickyScrollEnabled = false;
    }

    // If caller does not explicitly set scrollbar options, prefer a visible vertical scrollbar
    if (merged.scrollbar === undefined) {
      merged.scrollbar = {
        vertical: 'visible',
        horizontal: 'auto',
        verticalScrollbarSize: 12,
        horizontalScrollbarSize: 12,
        handleMouseWheel: true
      };
    }

    _editor.updateOptions(merged);
  } catch (e) {
    console.warn('[monacoManager] updateEditorOptions failed:', e && e.message ? e.message : e);
  }
}

module.exports = {
  initEditor,
  getEditor,
  getMonaco,
  getEditorByContainerId,
  getEditorFromEvent,
  getAllEditors,
  createModelForPath,
  setModel,
  setModelForPath,
  setValue,
  getValue,
  onDidChangeModelContent,
  focus,
  layout,
  ignoreModelContentChanges,
  detectLanguageFromPath,
  updateEditorOptions
};
