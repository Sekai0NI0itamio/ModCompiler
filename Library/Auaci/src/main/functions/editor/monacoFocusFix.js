/**
 * Helpers to ensure Monaco receives focus and paste when an overlay or DOM layering
 * prevents Monaco's native handlers from getting the events.
 *
 * - attachMonacoFocusFix(): install pointer + paste capture listeners
 * - detachMonacoFocusFix(): remove them
 *
 * This file is intended to sit next to your existing monacoManager and pasteHandler:
 *   src/main/functions/editor/monacoManager.js
 *   src/main/functions/editor/pasteHandler.js
 *
 * It uses monacoManager.getEditor() to access the editor instance and will call
 * pasteHandler.applyClipboardTextToMonaco(...) as a robust fallback insertion path.
 */
const monacoManager = require('./monacoManager');
let pasteHandler = null;
try { pasteHandler = require('./pasteHandler'); } catch (_) { /* optional */ }

function safeLog(...args) {
  try { console.debug('[monacoFocusFix]', ...args); } catch (_) {}
}

/**
 * Lightweight interactive element check: don't override paste for real inputs
 */
function isInteractiveElement(el) {
  if (!el) return false;
  try {
    const tag = (el.tagName || '').toUpperCase();
    if (tag === 'INPUT' || tag === 'TEXTAREA') return true;
    if (el.isContentEditable === true) return true;
    if (el.getAttribute && el.getAttribute('role') === 'textbox') return true;
    // If any ancestor is an input/textarea/contenteditable, treat as interactive
    if (el.closest && (el.closest('input') || el.closest('textarea') || el.closest('[contenteditable="true"]'))) return true;
  } catch (_) {}
  return false;
}

/**
 * Read plain text from available clipboard sources (event, navigator, electron)
 */
async function readClipboardText(e) {
  try {
    if (e && e.clipboardData && typeof e.clipboardData.getData === 'function') {
      const t = e.clipboardData.getData('text/plain') || e.clipboardData.getData('text') || '';
      if (t) return t;
    }
  } catch (_) {}

  try {
    if (navigator.clipboard && typeof navigator.clipboard.readText === 'function') {
      const t = await navigator.clipboard.readText();
      if (t) return t;
    }
  } catch (_) {}

  try {
    // may work in preload / node-enabled renderer
    // Guard with try/catch in case require('electron') is not available
    // eslint-disable-next-line global-require
    const { clipboard } = require('electron');
    if (clipboard && typeof clipboard.readText === 'function') {
      return clipboard.readText();
    }
  } catch (_) {}

  return '';
}

let _attached = false;
let _listeners = {};

/**
 * Decide whether the event (pointer/paste) is visually inside the editor area.
 * We check either DOM containment or position within the editor bounding rect so
 * overlay widgets that are separate DOM siblings still count.
 */
function eventIsInsideEditorVisualArea(e, editorDom) {
  if (!editorDom) return false;

  const target = e && (e.target || (typeof e.composedPath === 'function' && e.composedPath()[0])) || null;
  try {
    if (target && editorDom.contains && editorDom.contains(target)) return true;
  } catch (_) {}

  // fallback: compare coordinates to editor bounding rect (covers overlay widgets)
  try {
    const rect = editorDom.getBoundingClientRect && editorDom.getBoundingClientRect();
    if (!rect) return false;
    const x = typeof e.clientX === 'number' ? e.clientX : null;
    const y = typeof e.clientY === 'number' ? e.clientY : null;
    if (x === null || y === null) return false;
    if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) return true;
  } catch (_) {}
  return false;
}

/**
 * Pointer-down/mouse-down handler:
 * - If click is in editor visual area and not on an interactive input, ensure editor.focus()
 * - Skip if this is a right-click (context menu trigger)
 */
function pointerDownHandler(e) {
  try {
    // Skip right-clicks to allow context menu to work properly
    if (e && (e.button === 2 || e.which === 3)) {
      return;
    }
    
    const target = e && (e.target || (typeof e.composedPath === 'function' && e.composedPath()[0])) || null;
    const targetEl = (target && target.nodeType === 3) ? target.parentElement : target;

    // Skip if this event originated inside a modal/overlay that opts out of Monaco focus stealing
    try {
      if (targetEl && targetEl.closest && targetEl.closest('[data-prevent-monaco-focus="true"]')) {
        return;
      }
    } catch (_) {}
    if (isInteractiveElement(targetEl || target)) return; // don't steal focus from inputs
    
    // Skip if target is inside a context menu or menu overlay
    if (targetEl && targetEl.closest && (
      targetEl.closest('.monaco-menu') || 
      targetEl.closest('.context-view') ||
      targetEl.closest('.monaco-action-bar') ||
      targetEl.closest('[role="menu"]') ||
      targetEl.closest('[role="menuitem"]')
    )) {
      return;
    }

    const editor = monacoManager.getEditorFromEvent(e) || monacoManager.getEditor();
    if (!editor) return;

    const editorDom = (editor && typeof editor.getDomNode === 'function') ? editor.getDomNode() : null;
    if (!editorDom) return;

    if (!eventIsInsideEditorVisualArea(e, editorDom)) return;

    // Defer focus slightly to allow other handlers (e.g. overlay click) to complete.
    setTimeout(() => {
      try {
        const ed = monacoManager.getEditorFromEvent(e) || monacoManager.getEditor();
        if (!ed) return;
        try { ed.focus && ed.focus(); } catch (_) {}
        // Some Monaco versions accept DOM focus on the container as well
        try {
          const dom = ed.getDomNode && ed.getDomNode();
          if (dom && typeof dom.focus === 'function') dom.focus();
        } catch (_) {}
        safeLog('forced focus on Monaco (pointer down inside editor area)');
      } catch (err) {
        safeLog('pointerDown deferred error', err && err.message ? err.message : err);
      }
    }, 0);
  } catch (err) {
    safeLog('pointerDown handler error', err && err.message ? err.message : err);
  }
}

/**
 * Paste capture handler:
 * - When a paste happens inside the editor visual area but Monaco doesn't have text focus,
 *   read the clipboard and insert it programmatically.
 */
async function pasteCaptureHandler(e) {
  try {
    const editor = monacoManager.getEditorFromEvent(e) || monacoManager.getEditor();
    if (!editor) return;

    const target = e && (e.target || (typeof e.composedPath === 'function' && e.composedPath()[0])) || null;
    if (isInteractiveElement(target)) return; // let native inputs handle their paste

    const editorDom = (editor && typeof editor.getDomNode === 'function') ? editor.getDomNode() : null;
    if (!editorDom) return;
    if (!eventIsInsideEditorVisualArea(e, editorDom)) return;

    // If Monaco already has text focus, let native behavior proceed so Monaco handles it
    try {
      if (typeof editor.hasTextFocus === 'function' && editor.hasTextFocus()) {
        return;
      }
    } catch (_) {}

    // Intercept and handle paste ourselves
    try { e.preventDefault && e.preventDefault(); } catch (_) {}
    try { e.stopPropagation && e.stopPropagation(); } catch (_) {}

    // First try to use the event clipboardData synchronously
    let text = '';
    try {
      if (e && e.clipboardData && typeof e.clipboardData.getData === 'function') {
        text = e.clipboardData.getData('text/plain') || e.clipboardData.getData('text') || '';
      }
    } catch (_) { text = ''; }

    if (!text) {
      text = await readClipboardText(e);
    }

    if (!text) {
      safeLog('pasteCaptureHandler: no clipboard text available');
      return;
    }

    // Try to use pasteHandler.applyClipboardTextToMonaco if available (preferred)
    if (pasteHandler && typeof pasteHandler.applyClipboardTextToMonaco === 'function') {
      try {
        await pasteHandler.applyClipboardTextToMonaco(editor, text);
        safeLog('pasteCaptureHandler: applied clipboard text via pasteHandler');
        return;
      } catch (err) {
        safeLog('pasteCaptureHandler: pasteHandler.apply failed, falling back', err && err.message ? err.message : err);
      }
    }

    // Fallback insertion: try editor.trigger('keyboard','type')
    try {
      try { editor.focus && editor.focus(); } catch (_) {}
      if (typeof editor.trigger === 'function') {
        editor.trigger('keyboard', 'type', { text });
        safeLog('pasteCaptureHandler: inserted via editor.trigger keyboard.type');
        return;
      }
    } catch (err) {
      safeLog('pasteCaptureHandler: editor.trigger failed', err && err.message ? err.message : err);
    }

    // Final fallback: executeEdits at current position
    try {
      const monaco = monacoManager.getMonaco();
      const model = editor.getModel && editor.getModel();
      if (model && monaco && typeof editor.executeEdits === 'function') {
        const pos = editor.getPosition && editor.getPosition();
        if (pos) {
          const range = new monaco.Range(pos.lineNumber, pos.column, pos.lineNumber, pos.column);
          editor.executeEdits('monacoFocusFixPaste', [{ range, text, forceMoveMarkers: true }]);
          safeLog('pasteCaptureHandler: inserted via executeEdits');
          return;
        }
      }
      // last-last resort: replace model value (append)
      if (model && typeof model.setValue === 'function') {
        const cur = model.getValue ? model.getValue() : '';
        model.setValue(cur + text);
        safeLog('pasteCaptureHandler: inserted via model.setValue append fallback');
        return;
      }
    } catch (err) {
      safeLog('pasteCaptureHandler final fallback error', err && err.message ? err.message : err);
    }
  } catch (err) {
    safeLog('pasteCaptureHandler error', err && err.message ? err.message : err);
  }
}

function attachMonacoFocusFix() {
  if (typeof window === 'undefined' || _attached) return;
  _attached = true;

  _listeners.pointerDown = pointerDownHandler.bind(null);

  document.addEventListener('pointerdown', _listeners.pointerDown, { capture: true });
  // pointerdown is not present in all environments; add mousedown to be safe
  document.addEventListener('mousedown', _listeners.pointerDown, { capture: true });

  safeLog('attachMonacoFocusFix: listeners attached (pointerdown/mousedown)');
}

function detachMonacoFocusFix() {
  if (!_attached) return;
  try {
    if (_listeners.pointerDown) {
      document.removeEventListener('pointerdown', _listeners.pointerDown, { capture: true });
      document.removeEventListener('mousedown', _listeners.pointerDown, { capture: true });
    }
  } catch (err) {
    safeLog('detach error', err && err.message ? err.message : err);
  } finally {
    _attached = false;
    _listeners = {};
    safeLog('detachMonacoFocusFix: listeners removed');
  }
}

module.exports = {
  attachMonacoFocusFix,
  detachMonacoFocusFix
};
