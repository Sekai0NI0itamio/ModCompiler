// src/main/functions/editor/tabManagement.js
const path = require('path');
const fs = require('fs'); // used to detect if files still exist on disk
const monacoManager = require('./monacoManager');
const fileTabOptions = require('./fileTabOptions');

const editorCacheQueue = require('./editorCacheQueue');
const tabUtils = require('./tabUtils');
const tabFileIO = require('./tabFileIO');
const editorCache = require('./editorCache');

let tabs = [];
let activeTab = null;
// When true, suppress animations/transitions during tab render.
let _suppressTabAnimations = false;

// Debounced, expensive model->string snapshots (used for cache persistence and save-all).
let _contentSnapshotTimer = null;

// Large file mode thresholds (tuned for UI responsiveness).
const LARGE_FILE_BYTES = 1 * 1024 * 1024;      // 1 MB
const VERY_LARGE_FILE_BYTES = 5 * 1024 * 1024; // 5 MB

// When we enter "large file mode" we override a handful of editor options.
// Capture/restore so small files don't inherit the degraded settings.
let _largeModeSnapshot = null;
let _largeModeActive = false;

/**
 * Small wrappers around the editorCacheQueue so the rest of this module
 * can remain readable.
 */
function _queueSaveCache() {
  try {
    editorCacheQueue.queueSave(tabs, activeTab ? activeTab.path : null);
  } catch (err) {
    // best-effort
    console.warn('queueSaveCache failed:', err);
  }
}
function _saveCacheImmediate() {
  try {
    return editorCacheQueue.saveImmediate(tabs, activeTab ? activeTab.path : null);
  } catch (err) {
    console.warn('saveCacheImmediate failed:', err);
    return Promise.resolve();
  }
}

function _isMonacoReady() {
  const editor = monacoManager.getEditor();
  const monaco = monacoManager.getMonaco();
  return !!editor && !!monaco;
}

function _estimateBytesFromString(s) {
  try {
    if (s == null) return 0;
    // JS strings are UTF-16; this is a rough-but-fast estimate for thresholds.
    return String(s).length;
  } catch (_) {
    return 0;
  }
}

function _updateLargeFileFlags(tab) {
  if (!tab) return;
  const bytes = (typeof tab.size === 'number' && tab.size >= 0)
    ? tab.size
    : _estimateBytesFromString(tab.content != null ? tab.content : (tab.savedContent != null ? tab.savedContent : ''));
  tab.isLarge = bytes >= LARGE_FILE_BYTES;
  tab.isVeryLarge = bytes >= VERY_LARGE_FILE_BYTES;
}

function _saveActiveTabViewState() {
  try {
    if (!_isMonacoReady()) return;
    if (!activeTab || activeTab.mediaType) return;
    const editor = monacoManager.getEditor();
    if (!editor) return;
    try { activeTab.viewState = editor.saveViewState(); } catch (_) {}
    try {
      const sel = editor.getSelection && editor.getSelection();
      if (sel) {
        activeTab.selection = {
          startLineNumber: sel.startLineNumber,
          startColumn: sel.startColumn,
          endLineNumber: sel.endLineNumber,
          endColumn: sel.endColumn
        };
      }
    } catch (_) {}
    try { activeTab.scrollTop = editor.getScrollTop(); } catch (_) {}
    try { activeTab.scrollLeft = editor.getScrollLeft(); } catch (_) {}
  } catch (_) {}
}

function _applyEditorOptionsForTab(tab) {
  try {
    if (!_isMonacoReady()) return;
    if (!tab || tab.mediaType) return;

    const editor = monacoManager.getEditor();
    const monaco = monacoManager.getMonaco();

    // Capture/restore editor settings when toggling large-file mode so small files
    // don't get stuck with degraded options.
    if (tab.isLarge) {
      if (!_largeModeActive) {
        const snap = {};
        try {
          const EO = monaco && monaco.editor && monaco.editor.EditorOption;
          const getOpt = (name) => {
            try {
              if (!EO || EO[name] == null) return undefined;
              if (!editor || typeof editor.getOption !== 'function') return undefined;
              return editor.getOption(EO[name]);
            } catch (_) {
              return undefined;
            }
          };

          snap.codeLens = getOpt('codeLens');
          snap.folding = getOpt('folding');
          snap.lightbulb = getOpt('lightbulb');
          snap.hover = getOpt('hover');
          snap.links = getOpt('links');
          snap.matchBrackets = getOpt('matchBrackets');
          snap.renderLineHighlight = getOpt('renderLineHighlight');
          snap.bracketPairColorization = getOpt('bracketPairColorization');
          snap.guides = getOpt('guides');
          snap.maxTokenizationLineLength = getOpt('maxTokenizationLineLength');
          snap.stopRenderingLineAfter = getOpt('stopRenderingLineAfter');
          snap.wordBasedSuggestions = getOpt('wordBasedSuggestions');
          snap.quickSuggestions = getOpt('quickSuggestions');
          snap.suggestOnTriggerCharacters = getOpt('suggestOnTriggerCharacters');
          snap.colorDecorators = getOpt('colorDecorators');
          snap.renderValidationDecorations = getOpt('renderValidationDecorations');
          snap.scrollBeyondLastLine = getOpt('scrollBeyondLastLine');
        } catch (_) {}

        _largeModeSnapshot = snap;
        _largeModeActive = true;
      }
    } else if (_largeModeActive) {
      try {
        const restore = {};
        const snap = _largeModeSnapshot || {};
        for (const k of Object.keys(snap)) {
          if (typeof snap[k] !== 'undefined') restore[k] = snap[k];
        }
        if (Object.keys(restore).length) monacoManager.updateEditorOptions(restore);
      } catch (_) {}
      _largeModeSnapshot = null;
      _largeModeActive = false;
    }

    const opts = {
      readOnly: false,
      wordWrap: tab.wrap ? 'on' : 'off'
    };

    if (tab.isLarge) {
      Object.assign(opts, {
        // Keep the renderer + language services lightweight for huge buffers.
        renderValidationDecorations: 'off',
        scrollBeyondLastLine: false,

        codeLens: false,
        folding: false,
        lightbulb: { enabled: false },
        hover: { enabled: false },
        links: false,
        matchBrackets: 'never',
        renderLineHighlight: 'none',
        bracketPairColorization: { enabled: false },
        guides: { bracketPairs: false, indentation: false },
        colorDecorators: false,
        maxTokenizationLineLength: 20000,
        stopRenderingLineAfter: 10000,
        wordBasedSuggestions: false,
        quickSuggestions: false,
        suggestOnTriggerCharacters: false
      });
    }

    monacoManager.updateEditorOptions(opts);

    // Keep horizontal overflow calm when wrap is enabled
    try {
      const ed = monacoManager.getEditor();
      if (ed && typeof ed.getDomNode === 'function') {
        const dom = ed.getDomNode();
        if (dom) dom.style.overflowX = tab.wrap ? 'hidden' : '';
      }
    } catch (_) {}
  } catch (_) {}
}

function _ensureModelForTab(tab) {
  if (!tab) return null;
  if (!_isMonacoReady()) return null;
  if (tab.mediaType) return null;
  if (tab.model) return tab.model;

  const lang = tab.isVeryLarge ? 'plaintext' : monacoManager.detectLanguageFromPath(tab.path);
  const initial = (tab.content != null) ? String(tab.content)
    : (tab.savedContent != null) ? String(tab.savedContent)
    : '';

  const model = monacoManager.createModelForPath(tab.path || ('untitled://' + tab.id), initial, lang, { update: true });
  tab.model = model;

  // Establish "saved" baseline for version tracking.
  try {
    if (model && typeof model.getAlternativeVersionId === 'function' && (typeof tab.savedVersionId !== 'number')) {
      tab.savedVersionId = model.getAlternativeVersionId();
    }
  } catch (_) {}

  // Free duplicated strings for clean tabs to reduce memory/GC churn.
  try {
    if (!tab.unsaved) {
      tab.content = null;
      tab.savedContent = null;
    }
  } catch (_) {}

  return model;
}

/* Utilities moved out of this file:
   - id creation and formatting -> tabUtils
   - disk-cache debounce -> editorCacheQueue
   - file read -> tabFileIO
*/

/**
 * Best-effort lookup of cached wrap preference for a given file path.
 * Returns true/false when a cached value exists, or null when unknown.
 */
async function _getCachedWrapPreference(filePath) {
  try {
    if (!filePath) return null;
    const cache = await editorCache.loadCache();
    if (!cache || !Array.isArray(cache.tabs)) return null;
    const entry = cache.tabs.find(t => t && t.path === filePath);
    if (!entry || typeof entry.wrap !== 'boolean') return null;
    return !!entry.wrap;
  } catch (err) {
    return null;
  }
}

/**
 * Ensure the active tab element is scrolled into view in the horizontal tab strip.
 * Uses scrollIntoView with options where available, falls back to a manual scroll adjustment.
 */
function ensureActiveTabVisible() {
  try {
    const tabsContainer = document.getElementById('editor-tabs');
    if (!tabsContainer) return;
    const activeEl = tabsContainer.querySelector('.tab.active');
    if (!activeEl) return;

    try {
      // Prefer smooth centering in the inline (horizontal) direction; disable during silent refresh
      const behavior = _suppressTabAnimations ? 'auto' : 'smooth';
      activeEl.scrollIntoView({ behavior, inline: 'center', block: 'nearest' });
    } catch (err) {
      // Fallback manual scroll calculation for older browsers
      const wrapper = activeEl.closest('.tab-wrapper') || activeEl;
      if (wrapper) {
        const wrapperRect = wrapper.getBoundingClientRect();
        const containerRect = tabsContainer.getBoundingClientRect();
        // Compute offset to center the wrapper
        const offset = (wrapperRect.left - containerRect.left) - (containerRect.width / 2) + (wrapperRect.width / 2);
        tabsContainer.scrollLeft += offset;
      }
    }
  } catch (err) {
    // ignore scrolling failures
  }
}

function renderTabs() {
  const tabsContainer = document.getElementById('editor-tabs');
  if (!tabsContainer) return;
  tabsContainer.innerHTML = '';

  // Compute basename counts so we can show relative paths when basenames collide.
  const nameCounts = {};
  tabs.forEach((t) => {
    if (!t) return;
    const p = t.path || '';
    if (p && !p.startsWith('untitled://')) {
      const base = path.basename(p);
      nameCounts[base] = (nameCounts[base] || 0) + 1;
    } else {
      const name = t.name || 'Untitled';
      nameCounts[name] = (nameCounts[name] || 0) + 1;
    }
  });

  tabs.forEach((tab) => {
    const wrapper = document.createElement('div');
    wrapper.className = 'tab-wrapper';
    // mark with tab id to allow future querying if needed
    wrapper.dataset.tabId = tab.id || '';
    if (_suppressTabAnimations) {
      try { wrapper.style.animation = 'none'; } catch (_) {}
      try { wrapper.style.transition = 'none'; } catch (_) {}
    }

    const tabEl = document.createElement('div');
    tabEl.className = `tab ${tab === activeTab ? 'active' : ''} ${tab.unsaved ? 'unsaved' : ''}`;
    if (_suppressTabAnimations) {
      try { tabEl.style.animation = 'none'; } catch (_) {}
      try { tabEl.style.transition = 'none'; } catch (_) {}
    }

    // Detect missing files (only for real filesystem paths, not untitled://)
    let isMissing = false;
    if (tab && tab.path && !String(tab.path).startsWith('untitled://')) {
      try {
        isMissing = !fs.existsSync(tab.path);
      } catch (_) {
        isMissing = false;
      }
    }
    if (isMissing) {
      tabEl.classList.add('missing');
      // keep a property if other code wants to inspect
      tab.missing = true;
    } else {
      tab.missing = false;
    }

    // External change hint (set by smartSync). Styling is optional; class is a hook.
    const hasExternalChange = !!(tab && tab.externalChange);
    if (hasExternalChange) {
      try { tabEl.classList.add('external-change'); } catch (_) {}
    }

    // Choose display name; when multiple tabs share the same basename, show a relative path to disambiguate
    let displayName = tab.name || (tab.path ? path.basename(tab.path) : 'Untitled');
    if (tab && tab.path && !String(tab.path).startsWith('untitled://')) {
      const base = path.basename(tab.path);
      if (nameCounts[base] > 1) {
        // Show path relative to cwd to help differentiate
        try {
          const rel = path.relative(process.cwd(), tab.path) || tab.path;
          displayName = rel;
        } catch (_) {
          displayName = tab.path;
        }
      } else {
        displayName = tab.name || base;
      }
    } else {
      displayName = tab.name || (tab.path ? path.basename(tab.path) : 'Untitled');
    }

    const nameSpan = document.createElement('span');
    nameSpan.className = 'tab-name';
    nameSpan.textContent = displayName;
    // title shows full path when available, and indicates missing state
    if (tab && tab.path) {
      nameSpan.title = tab.path
        + (isMissing ? ' — File not found' : '')
        + (hasExternalChange ? ' — Changed on disk' : '');
    } else {
      nameSpan.title = displayName;
    }
    tabEl.appendChild(nameSpan);

    tabEl.addEventListener('click', () => {
      const idx = tabs.findIndex(t => t && t.id === tab.id);
      if (idx !== -1) switchTab(idx);
    });

    tabEl.addEventListener('contextmenu', (e) => {
      e.preventDefault();
      e.stopPropagation();
      try {
        fileTabOptions.showForTab(e, tab);
      } catch (err) {
        console.warn('fileTabOptions.showForTab error:', err);
      }
    });

    const closeBtn = document.createElement('button');
    closeBtn.className = 'close-btn tab-close';
    closeBtn.type = 'button';
    closeBtn.textContent = '×';
    if (_suppressTabAnimations) {
      try { closeBtn.style.animation = 'none'; } catch (_) {}
      try { closeBtn.style.transition = 'none'; } catch (_) {}
    }
    closeBtn.setAttribute('aria-label', `Close ${tab.name || tab.path || 'tab'}`);
    closeBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const idx = tabs.findIndex(t => t && t.id === tab.id);
      if (idx !== -1) {
        closeTab(idx);
      }
    });

    tabEl.appendChild(closeBtn);
    wrapper.appendChild(tabEl);
    tabsContainer.appendChild(wrapper);
  });
}

/**
 * Ensure a media container exists (sibling to the editor-content container).
 * The container is created on demand and reused.
 */
function _ensureMediaContainer() {
  const editorRoot = document.getElementById('editor') || document.body;
  let mediaEl = document.getElementById('editor-media');
  if (!mediaEl) {
    mediaEl = document.createElement('div');
    mediaEl.id = 'editor-media';
    mediaEl.style.position = 'relative';
    mediaEl.style.zIndex = 1;
    mediaEl.style.width = '100%';
    mediaEl.style.height = '100%';
    mediaEl.style.overflow = 'auto';
    mediaEl.style.display = 'none';
    mediaEl.style.alignItems = 'center';
    mediaEl.style.justifyContent = 'center';
    mediaEl.style.background = '#fff';
    editorRoot.appendChild(mediaEl);
  }
  return mediaEl;
}

function _showMediaElement() {
  const mediaEl = _ensureMediaContainer();
  // Hide the regular editor content container if present
  const contentContainer = document.getElementById('editor-content');
  if (contentContainer) contentContainer.style.display = 'none';
  mediaEl.style.display = 'flex';
  return mediaEl;
}

function _hideMediaElement() {
  const mediaEl = document.getElementById('editor-media');
  const contentContainer = document.getElementById('editor-content');
  if (mediaEl) {
    mediaEl.style.display = 'none';
    mediaEl.innerHTML = '';
  }
  if (contentContainer) contentContainer.style.display = '';
}

/**
 * Switches to the given tab index and restores view state. Supports media-type tabs:
 * - image: shows an <img> in the media container
 * - audio: shows "format unsupported" message
 * - binary/text: normal text rendering (binary now treated as text)
 */
function switchTab(index) {
  if (index < 0 || index >= tabs.length) return;
  const editor = monacoManager.getEditor();
  const monaco = monacoManager.getMonaco();

  // Persist view state for the current active tab before switching away.
  try { _saveActiveTabViewState(); } catch (_) {}

  activeTab = tabs[index];

  // If the tab is a media-only tab, handle that first.
  if (activeTab && activeTab.mediaType) {
    // For image / audio we render into a separate media container and hide the editor.
    if (activeTab.mediaType === 'image') {
      try {
        if (editor) {
          try { monacoManager.setModel(null); } catch (_) {}
          try { monacoManager.updateEditorOptions({ readOnly: true }); } catch (_) {}
        }
      } catch (_) {}

      const mediaEl = _showMediaElement();
      mediaEl.innerHTML = '';
      const img = document.createElement('img');
      img.alt = activeTab.name || '';
      img.style.maxWidth = '100%';
      img.style.maxHeight = '100%';
      img.style.objectFit = 'contain';
      // Prefer explicit dataUrl field, fallback to content
      img.src = activeTab.dataUrl || activeTab.content || activeTab.path || '';
      img.addEventListener('error', () => {
        // If image fails to render, show a message
        mediaEl.innerHTML = `<div style="padding:16px;color:#333">Unable to display image.</div>`;
      });
      mediaEl.appendChild(img);

      renderTabs();
      // ensure the active tab is visible in the horizontal scroll
      try { ensureActiveTabVisible(); } catch (_) {}
      _queueSaveCache();
      return;
    }

    if (activeTab.mediaType === 'audio') {
      // Not supported for now as requested.
      const mediaEl = _showMediaElement();
      mediaEl.innerHTML = '';
      const msg = document.createElement('div');
      msg.style.padding = '16px';
      msg.style.color = '#333';
      msg.textContent = 'Audio preview is not supported in this editor view.';
      mediaEl.appendChild(msg);

      renderTabs();
      try { ensureActiveTabVisible(); } catch (_) {}
      _queueSaveCache();
      return;
    }

    // For other media types fall through to default text rendering
  }

  // Textual handling (Monaco path)
  if (editor && monaco) {
    // Ensure any media UI is hidden
    try { _hideMediaElement(); } catch (_) {}

    // Large-file heuristics + editor option throttling
    try { _updateLargeFileFlags(activeTab); } catch (_) {}
    try { _applyEditorOptionsForTab(activeTab); } catch (_) {}

    // Ensure we reuse per-tab Monaco models (no setModel(null) detach, no model.getValue compares).
    const model = _ensureModelForTab(activeTab);
    if (model) {
      monacoManager.ignoreModelContentChanges(() => {
        try { editor.setModel(model); } catch (_) { try { monacoManager.setModel(model); } catch (_) {} }
      });
    }

    // Restore view state (preferred) or fallback to legacy selection/scroll fields.
    try {
      if (activeTab.viewState) {
        try { editor.restoreViewState(activeTab.viewState); } catch (_) {}
      } else {
        if (activeTab.selection && activeTab.selection.startLineNumber != null) {
          try {
            editor.setSelection(activeTab.selection);
            editor.revealRangeInCenter(activeTab.selection);
          } catch (e) {
            try {
              const pos = {
                lineNumber: activeTab.selection.startLineNumber || 1,
                column: activeTab.selection.startColumn || 1
              };
              editor.setPosition(pos);
              editor.revealPositionInCenter(pos);
            } catch (_) {}
          }
        } else if (activeTab.cursorIndex != null) {
          try {
            const m = editor.getModel();
            if (m) {
              const offset = Math.max(0, Math.floor(activeTab.cursorIndex));
              const pos = m.getPositionAt(offset);
              editor.setPosition(pos);
              editor.revealPositionInCenter(pos);
            }
          } catch (_) {}
        }

        if (typeof activeTab.scrollTop === 'number') {
          try { editor.setScrollTop(activeTab.scrollTop); } catch (_) {}
        }
        if (typeof activeTab.scrollLeft === 'number') {
          try { editor.setScrollLeft(activeTab.scrollLeft); } catch (_) {}
        }
      }
    } catch (_) {}

    try {
      monacoManager.focus();
      // Layout can be expensive; defer so the tab switch paints first.
      requestAnimationFrame(() => {
        try { monacoManager.layout(); } catch (_) {}
      });
    } catch (_) {}
  } else {
    // Fallback textarea handling
    try { _hideMediaElement(); } catch (_) {}

    const contentArea = document.getElementById('editor-content');
    if (contentArea && contentArea.tagName === 'TEXTAREA') {
      // Enable the textarea when a tab is active
      try { contentArea.removeAttribute('disabled'); } catch (_) {}
      window.__editor_suppress_input = true;
      try {
        const val = (activeTab.content != null) ? String(activeTab.content)
          : (activeTab.savedContent != null) ? String(activeTab.savedContent)
          : '';
        contentArea.value = val;
        contentArea.placeholder = val === '' ? 'File is empty' : '';

        // Apply wrap styles for textarea fallback
        if (activeTab.wrap) {
          contentArea.style.whiteSpace = 'pre-wrap';
          contentArea.style.wordWrap = 'break-word';
          contentArea.style.overflowX = 'hidden';
          try { contentArea.setAttribute('wrap', 'soft'); } catch (_) {}
        } else {
          contentArea.style.whiteSpace = 'pre';
          contentArea.style.wordWrap = 'normal';
          contentArea.style.overflowX = '';
          try { contentArea.removeAttribute('wrap'); } catch (_) {}
        }

        if (typeof activeTab.cursorIndex === 'number') {
          try { contentArea.selectionStart = activeTab.cursorIndex; contentArea.selectionEnd = activeTab.cursorIndex; } catch (_) {}
        }
        if (typeof activeTab.scrollTop === 'number') {
          try { contentArea.scrollTop = activeTab.scrollTop; } catch (_) {}
        }
      } finally {
        setTimeout(() => { window.__editor_suppress_input = false; }, 0);
      }
    }
  }

  renderTabs();
  // Ensure the active tab is visible after we re-render the tabs
  try { ensureActiveTabVisible(); } catch (_) {}
  _queueSaveCache();
}

async function closeTab(index) {
  if (!tabs[index]) return;
  if (tabs[index].unsaved) {
    if (!confirm(`Unsaved changes in ${tabs[index].name || path.basename(tabs[index].path)}. Close anyway?`)) return;
  }

  tabs.splice(index, 1);

  if (tabs.length === 0) {
    activeTab = null;
    const editor = monacoManager.getEditor();
    if (editor) {
      // Remove model and make editor read-only when no tabs are open
      try {
        monacoManager.setModel(null);
      } catch (_) {}
      try {
        monacoManager.updateEditorOptions({ readOnly: true });
      } catch (_) {}
    } else {
      const contentArea = document.getElementById('editor-content');
      if (contentArea && contentArea.tagName === 'TEXTAREA') {
        contentArea.value = '';
        contentArea.placeholder = 'Drag or drop a file here';
        try { contentArea.setAttribute('disabled', 'disabled'); } catch (_) {}
      }
    }
    // Hide any media UI
    try { _hideMediaElement(); } catch (_) {}
  } else {
    const nextIndex = Math.min(index, tabs.length - 1);
    switchTab(nextIndex);
  }
  renderTabs();
  _queueSaveCache();
}

function addFiles(files) {
  if (!Array.isArray(files) || files.length === 0) return;
  files.forEach(file => {
    const incomingPath = file && file.path ? String(file.path) : null;
    const incomingName = file && file.name ? String(file.name) : null;
    const existingTab = incomingPath ? tabs.find(tab => tab.path === incomingPath) : null;

    if (!existingTab) {
      const id = file && file.id ? file.id : tabUtils.createTabId();
      const content = (file && file.content != null) ? String(file.content) :
                      (file && file.savedContent != null) ? String(file.savedContent) : '';
      const savedC = (file && file.savedContent != null) ? String(file.savedContent) : content;
      const resolvedPath = incomingPath || `untitled://${id}`;
      const name = incomingName || (incomingPath ? path.basename(incomingPath) : 'Untitled');

      const fileObj = {
        name,
        size: (file && typeof file.size === 'number') ? file.size : 0,
        diskSize: (file && typeof file.diskSize === 'number') ? file.diskSize : ((file && typeof file.size === 'number') ? file.size : null),
        diskMtimeMs: (file && typeof file.diskMtimeMs === 'number') ? file.diskMtimeMs : null,
        path: resolvedPath,
        content: content,
        unsaved: !!file.unsaved,
        savedContent: savedC,
        id
      };

      if (file && file.temp) fileObj.temp = true;

      if (file && file.selection) fileObj.selection = file.selection;
      if (file && typeof file.cursorIndex === 'number') fileObj.cursorIndex = file.cursorIndex;
      if (file && typeof file.scrollTop === 'number') fileObj.scrollTop = file.scrollTop;
      if (file && typeof file.scrollLeft === 'number') fileObj.scrollLeft = file.scrollLeft;

      // copy media-specific metadata if provided (image/audio)
      if (file && file.mediaType) fileObj.mediaType = file.mediaType;
      if (file && file.mime) fileObj.mime = file.mime;
      if (file && file.dataUrl) fileObj.dataUrl = file.dataUrl;

      // copy wrap preference (default off)
      if (file && typeof file.wrap === 'boolean') fileObj.wrap = !!file.wrap;
      else fileObj.wrap = false;

      tabs.push(fileObj);
      activeTab = tabs[tabs.length - 1];
    } else {
      if (file && file.content != null) existingTab.content = String(file.content);
      if (file && file.savedContent != null) existingTab.savedContent = String(file.savedContent);
      if (file && typeof file.unsaved === 'boolean') existingTab.unsaved = !!file.unsaved;
      if (file && typeof file.size === 'number') existingTab.size = file.size;
      if (file && typeof file.diskSize === 'number') existingTab.diskSize = file.diskSize;
      if (file && typeof file.diskMtimeMs === 'number') existingTab.diskMtimeMs = file.diskMtimeMs;
      if (file && file.selection) existingTab.selection = file.selection;
      if (file && typeof file.cursorIndex === 'number') existingTab.cursorIndex = file.cursorIndex;
      if (file && typeof file.scrollTop === 'number') existingTab.scrollTop = file.scrollTop;
      if (file && typeof file.scrollLeft === 'number') existingTab.scrollLeft = file.scrollLeft;

      // update media metadata if provided
      if (file && file.mediaType) existingTab.mediaType = file.mediaType;
      if (file && file.mime) existingTab.mime = file.mime;
      if (file && file.dataUrl) existingTab.dataUrl = file.dataUrl;

      // update wrap if provided
      if (file && typeof file.wrap === 'boolean') existingTab.wrap = !!file.wrap;

      activeTab = existingTab;
    }
  });
  switchTab(tabs.indexOf(activeTab));
  _queueSaveCache();
}

function _scheduleContentSnapshotForTab(tab) {
  try {
    if (!tab || !tab.unsaved) return;
    if (!_isMonacoReady()) return;
    const model = tab.model;
    if (!model || typeof model.getValue !== 'function') return;

    // Debounce full-string snapshots; large files need a gentler cadence.
    const delay = tab.isVeryLarge ? 700 : tab.isLarge ? 600 : 250;
    if (_contentSnapshotTimer) clearTimeout(_contentSnapshotTimer);
    _contentSnapshotTimer = setTimeout(() => {
      try {
        if (!tab || !tab.unsaved) return;
        if (!tab.model || typeof tab.model.getValue !== 'function') return;
        tab.content = tab.model.getValue();
        // best-effort size update for unsaved buffers
        try { tab.size = Math.max(tab.size || 0, _estimateBytesFromString(tab.content)); } catch (_) {}
        // Now that we have a fresh snapshot, persist it on the next cache tick.
        try { _queueSaveCache(); } catch (_) {}
      } catch (_) {
        // ignore snapshot failures
      }
    }, delay);
  } catch (_) {}
}

/**
 * Monaco path: mark dirty/clean based on model alternative version id (no getValue()).
 * This keeps typing fast even for large files.
 */
function markActiveTabDirtyFromModel() {
  try {
    const tab = activeTab;
    if (!tab || tab.mediaType) return;
    if (!_isMonacoReady()) return;
    const editor = monacoManager.getEditor();
    const model = editor && editor.getModel && editor.getModel();
    if (!model) return;

    // Ensure tab has a stable model reference.
    if (!tab.model) tab.model = model;

    // Compute dirty state without reading the full value.
    const wasUnsaved = !!tab.unsaved;
    const alt = (typeof model.getAlternativeVersionId === 'function') ? model.getAlternativeVersionId() : null;
    const saved = (typeof tab.savedVersionId === 'number') ? tab.savedVersionId : null;
    const nowUnsaved = (alt == null || saved == null) ? true : (alt !== saved);

    tab.unsaved = nowUnsaved;
    if (nowUnsaved && tab.temp) tab.temp = false;

    // For clean tabs, drop duplicated string snapshots to reduce memory pressure.
    if (!nowUnsaved) {
      tab.content = null;
      tab.savedContent = null;
    } else {
      // Keep a debounced snapshot for cache persistence and save-all.
      try { _updateLargeFileFlags(tab); } catch (_) {}
      _scheduleContentSnapshotForTab(tab);
    }

    if (nowUnsaved !== wasUnsaved) {
      renderTabs();
    }
    _queueSaveCache();
  } catch (_) {
    // ignore
  }
}

function markTabUnsaved(content) {
  if (activeTab) {
    const newContent = content == null ? '' : String(content);
    const wasUnsaved = activeTab.unsaved;
    if (activeTab.savedContent == null) activeTab.savedContent = '';

    if (newContent !== activeTab.savedContent) {
      activeTab.content = newContent;
      activeTab.unsaved = true;
      if (activeTab.temp) activeTab.temp = false;
    } else {
      activeTab.content = newContent;
      activeTab.unsaved = false;
    }
    if (activeTab.unsaved !== wasUnsaved) {
      renderTabs();
    }
    _queueSaveCache();
  }
}

function clearUnsaved() {
  if (activeTab) {
    const wasUnsaved = !!activeTab.unsaved;
    activeTab.unsaved = false;
    try {
      activeTab.externalChange = false;
      activeTab.externalDiskMtimeMs = null;
      activeTab.externalDiskSize = null;
    } catch (_) {}

    // Monaco path: establish saved baseline via version id (no string duplication).
    try {
      if (_isMonacoReady()) {
        const editor = monacoManager.getEditor();
        const model = activeTab.model || (editor && editor.getModel && editor.getModel());
        if (model && typeof model.getAlternativeVersionId === 'function') {
          activeTab.model = model;
          activeTab.savedVersionId = model.getAlternativeVersionId();
          activeTab.content = null;
          activeTab.savedContent = null;
        } else {
          // textarea fallback
          activeTab.savedContent = activeTab.content == null ? '' : String(activeTab.content);
        }
      } else {
        // textarea fallback
        activeTab.savedContent = activeTab.content == null ? '' : String(activeTab.content);
      }
    } catch (_) {
      activeTab.savedContent = activeTab.content == null ? '' : String(activeTab.content);
    }

    if (wasUnsaved) renderTabs();
    _queueSaveCache();
  }
}

function getActiveTab() {
  return activeTab;
}

/* New helpers for menu actions */

function getTabByPath(filePath) {
  return tabs.find(t => t.path === filePath) || null;
}

function getAllTabs() {
  return tabs;
}

function setTabSaved(filePath) {
  const t = tabs.find(tab => tab.path === filePath);
  if (t) {
    t.unsaved = false;
    try {
      t.externalChange = false;
      t.externalDiskMtimeMs = null;
      t.externalDiskSize = null;
    } catch (_) {}
    try {
      if (_isMonacoReady() && t.model && typeof t.model.getAlternativeVersionId === 'function') {
        t.savedVersionId = t.model.getAlternativeVersionId();
        t.content = null;
        t.savedContent = null;
      } else {
        t.savedContent = t.content == null ? '' : String(t.content);
      }
    } catch (_) {
      t.savedContent = t.content == null ? '' : String(t.content);
    }
    renderTabs();
    _queueSaveCache();
  }
}

/**
 * updateActiveTabViewState(state)
 * state may contain: selection, cursorIndex, scrollTop, scrollLeft
 */
function updateActiveTabViewState(state = {}) {
  if (!activeTab) return;
  try {
    if (state.selection) activeTab.selection = state.selection;
    if (typeof state.cursorIndex === 'number') activeTab.cursorIndex = state.cursorIndex;
    if (typeof state.scrollTop === 'number') activeTab.scrollTop = state.scrollTop;
    if (typeof state.scrollLeft === 'number') activeTab.scrollLeft = state.scrollLeft;
    _queueSaveCache();
  } catch (err) {
    // ignore
  }
}

/* previewFile remains tab-related; it uses tabUtils for ids */
function getTempTab() {
  return tabs.find(t => t && t.temp);
}

function previewFile(filePath, content, size, opts = {}) {
  if (!filePath) return;

  const existing = getTabByPath(filePath);
  if (existing) {
    activeTab = existing;
    switchTab(tabs.indexOf(activeTab));
    return;
  }

  const tempTab = getTempTab();
  const diskMtimeMs = (opts && typeof opts.mtimeMs === 'number') ? opts.mtimeMs : null;

  if (tempTab) {
    if (tempTab.unsaved) {
      const id = tabUtils.createTabId();
      const savedC = content == null ? '' : String(content);
      const fileObj = {
        name: path.basename(filePath),
        size: size || 0,
        diskSize: size || 0,
        diskMtimeMs,
        path: filePath,
        content: savedC,
        unsaved: false,
        savedContent: savedC,
        id,
        temp: true
      };
      if (opts.mediaType) fileObj.mediaType = opts.mediaType;
      if (opts.mime) fileObj.mime = opts.mime;
      if (opts.dataUrl) fileObj.dataUrl = opts.dataUrl;
      if (typeof opts.wrap === 'boolean') fileObj.wrap = !!opts.wrap;
      tabs.push(fileObj);
      activeTab = tabs[tabs.length - 1];
      switchTab(tabs.indexOf(activeTab));
    } else {
      // Reusing the preview tab for a different file: dispose the previous model to free memory.
      try { if (tempTab.model && typeof tempTab.model.dispose === 'function') tempTab.model.dispose(); } catch (_) {}
      tempTab.model = null;
      tempTab.savedVersionId = null;
      tempTab.viewState = null;

      tempTab.name = path.basename(filePath);
      tempTab.size = size || 0;
      tempTab.diskSize = size || 0;
      tempTab.diskMtimeMs = diskMtimeMs;
      tempTab.path = filePath;
      tempTab.content = content == null ? '' : String(content);
      tempTab.savedContent = tempTab.content;
      tempTab.unsaved = false;
      tempTab.externalChange = false;
      tempTab.externalDiskMtimeMs = null;
      tempTab.externalDiskSize = null;
      // Clear mediaType unless explicitly setting a new one
      if (opts.mediaType) tempTab.mediaType = opts.mediaType;
      else delete tempTab.mediaType;
      if (opts.mime) tempTab.mime = opts.mime;
      if (opts.dataUrl) tempTab.dataUrl = opts.dataUrl;
      if (typeof opts.wrap === 'boolean') tempTab.wrap = !!opts.wrap;
      activeTab = tempTab;
      switchTab(tabs.indexOf(tempTab));
    }
  } else {
    const id = tabUtils.createTabId();
    const savedC = content == null ? '' : String(content);
    const fileObj = {
      name: path.basename(filePath),
      size: size || 0,
      diskSize: size || 0,
      diskMtimeMs,
      path: filePath,
      content: savedC,
      unsaved: false,
      savedContent: savedC,
      id,
      temp: true
    };
    if (opts.mediaType) fileObj.mediaType = opts.mediaType;
    if (opts.mime) fileObj.mime = opts.mime;
    if (opts.dataUrl) fileObj.dataUrl = opts.dataUrl;
    if (typeof opts.wrap === 'boolean') fileObj.wrap = !!opts.wrap;
    tabs.push(fileObj);
    activeTab = tabs[tabs.length - 1];
    switchTab(tabs.indexOf(activeTab));
  }

  _queueSaveCache();
}

/* openFileInNewTab now delegates file reading to tabFileIO for clarity */
async function openFileInNewTab(filePath, content) {
  const existing = getTabByPath(filePath);
  if (existing) {
    activeTab = existing;
    switchTab(tabs.indexOf(activeTab));
    return;
  }

  try {
    // Best-effort: see if we have a cached wrap preference for this path.
    let cachedWrap = null;
    try {
      cachedWrap = await _getCachedWrapPreference(filePath);
    } catch (_) {
      cachedWrap = null;
    }

    const fileObj = await tabFileIO.createFileTabFromPath(filePath, content);

    // If we have a cached preference (user explicitly set wrap before), honor it.
    if (typeof cachedWrap === 'boolean') {
      fileObj.wrap = cachedWrap;
    } else {
      // No cached preference for this file; apply extension-based default.
      // Currently we auto-wrap .txt, .md, and .log files on first open.
      const ext = (filePath && typeof filePath === 'string') ? path.extname(filePath).toLowerCase() : '';
      const shouldAutoWrap = ext === '.txt' || ext === '.md' || ext === '.log';
      fileObj.wrap = !!shouldAutoWrap;
    }

    tabs.push(fileObj);
    activeTab = tabs[tabs.length - 1];
    switchTab(tabs.indexOf(activeTab));
    _queueSaveCache();
  } catch (err) {
    console.error('openFileInNewTab: failed to open', filePath, err);
  }
}

function createUntitledTab(name = 'Untitled') {
  const id = tabUtils.createTabId();
  const pathLike = `untitled://${id}`;
  const fileObj = {
    name: name,
    size: 0,
    path: pathLike,
    content: '',
    unsaved: false,
    savedContent: '',
    id,
    wrap: false
  };
  tabs.push(fileObj);
  activeTab = tabs[tabs.length - 1];
  switchTab(tabs.indexOf(activeTab));
  _queueSaveCache();
  return activeTab;
}

/**
 * Listen for wrap toggle events dispatched by the context menu UI.
 * We avoid a circular require by using DOM events.
 */
if (typeof document !== 'undefined' && document.addEventListener) {
  document.addEventListener('tab-wrap-toggled', (e) => {
    try {
      const detail = e && e.detail ? e.detail : {};
      const filePath = detail.path;
      const id = detail.id;
      const wrap = !!detail.wrap;

      const t = (filePath ? tabs.find(x => x.path === filePath) : tabs.find(x => x.id === id));
      if (!t) return;

      t.wrap = wrap;
      renderTabs();

      // If this is the active tab, re-apply view options immediately
      if (t === activeTab) {
        try {
          const editor = monacoManager.getEditor();
          if (editor && monacoManager.getMonaco()) {
            monacoManager.updateEditorOptions({ wordWrap: t.wrap ? 'on' : 'off' });
            const dom = editor.getDomNode && editor.getDomNode();
            if (dom) dom.style.overflowX = t.wrap ? 'hidden' : '';
          } else {
            const contentArea = document.getElementById('editor-content');
            if (contentArea && contentArea.tagName === 'TEXTAREA') {
              if (t.wrap) {
                contentArea.style.whiteSpace = 'pre-wrap';
                contentArea.style.wordWrap = 'break-word';
                contentArea.style.overflowX = 'hidden';
                try { contentArea.setAttribute('wrap', 'soft'); } catch (_) {}
              } else {
                contentArea.style.whiteSpace = 'pre';
                contentArea.style.wordWrap = 'normal';
                contentArea.style.overflowX = '';
                try { contentArea.removeAttribute('wrap'); } catch (_) {}
              }
            }
          }
        } catch (_) {}
      }

      _queueSaveCache();
    } catch (err) {
      console.warn('tab-wrap-toggled handler error:', err);
    }
  });
}

function refreshTabsRender() {
  try {
    _suppressTabAnimations = true;
    renderTabs();
  } catch (err) {
    // ignore
  } finally {
    _suppressTabAnimations = false;
  }
}

function refreshActiveTabEditorMode() {
  try {
    if (!activeTab) return;
    _updateLargeFileFlags(activeTab);
    _applyEditorOptionsForTab(activeTab);
  } catch (_) {
    // ignore
  }
}

module.exports = {
  addFiles,
  switchTab,
  closeTab,
  markTabUnsaved,
  markActiveTabDirtyFromModel,
  clearUnsaved,
  getActiveTab,
  getTabByPath,
  openFileInNewTab,
  getAllTabs,
  setTabSaved,
  createUntitledTab,
  previewFile,
  getTempTab,
  updateActiveTabViewState,
  refreshTabsRender,
  refreshActiveTabEditorMode
};
