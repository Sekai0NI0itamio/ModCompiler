// Centralized paste handling for Monaco editor renderer.
// - Attaches document capture paste listener and editor DOM paste listener.
// - Provides Ctrl/Cmd+V keydown fallback that reads clipboard and forces insertion.
// - Writes detailed debug logs to /tmp/editor.log and also to console + in-memory buffer.

const fs = require('fs').promises;
const monacoManager = require('./monacoManager'); // relative to same directory
const tabs = (() => { try { return require('./tabManagement'); } catch (_) { return null; } })();
const LOG_PATH = '/tmp/editor.log';

function safeString(v) {
  try { return String(v); } catch (_) { return '';}
}

/**
 * Append a debug line: write to /tmp, console, and store a short in-memory buffer
 * on window._auaciPasteLogs so you can inspect logs from DevTools even if file
 * writes fail.
 */
async function appendLog(line) {
  const ts = new Date().toISOString();
  const out = `[${ts}] ${safeString(line)}`;
  try {
    // console first so devtools show real-time events
    try { console.debug('[pasteHandler]', out); } catch (_) {}
    try {
      if (typeof window !== 'undefined') {
        window._auaciPasteLogs = window._auaciPasteLogs || [];
        window._auaciPasteLogs.push(out);
        // keep buffer length bounded
        if (window._auaciPasteLogs.length > 200) window._auaciPasteLogs.shift();
      }
    } catch (_) {}
    // attempt file write (best-effort)
    await fs.appendFile(LOG_PATH, out + '\n').catch(() => {});
  } catch (err) {
    // swallow - we already logged to console
    try { console.warn('[pasteHandler] appendLog failed:', err && err.message ? err.message : err); } catch (_) {}
  }
}

// Detect if an element is an interactive text input where we should never intercept paste
function isInteractiveElement(el) {
  if (!el || typeof el.closest !== 'function') return false;
  const tag = (el.tagName || '').toUpperCase();
  if (tag === 'INPUT' || tag === 'TEXTAREA' || (el.isContentEditable === true)) return true;
  // aria textboxes should be treated as inputs
  if (el.getAttribute && el.getAttribute('role') === 'textbox') return true;
  // Monaco and VS Code style input boxes
  if (el.closest('.monaco-inputbox') || el.closest('.monaco-findInput') || el.closest('.find-widget')) return true;
  return false;
}

function isInsideMonacoFindWidget(el) {
  if (!el || typeof el.closest !== 'function') return false;
  if (el.closest('.find-widget') || el.closest('.monaco-findInput')) return true;
  try {
    const label = (el.getAttribute && el.getAttribute('aria-label')) || '';
    if (label && /^(find|replace)$/i.test(label)) return true;
  } catch (_) {}
  return false;
}

/**
 * Heuristic: check whether an event target (or the first entry in composedPath)
 * is inside the Monaco editor DOM. This covers cases where editor.hasTextFocus()
 * may be false but the paste actually targets the Monaco DOM.
 */
function isEventInsideMonacoEditor(e) {
  try {
    const ed = monacoManager.getEditorFromEvent(e);
    return !!ed;
  } catch (_) {
    return false;
  }
}

/**
 * Insert provided text into the active Monaco editor instance.
 * Strategy:
 *  1) Ensure editor has focus
 *  2) Try editor.trigger('keyboard','type',{text}) to emulate typing (best)
 *  3) Fallback to editor.executeEdits with a computed Range
 *  4) Last-resort: replace model value (append or full replace)
 *
 * Returns true when insertion seemed to succeed.
 */
async function applyClipboardTextToMonaco(editor, text) {
  try {
    await appendLog(`applyClipboardTextToMonaco called, textLen=${safeString((text || '')).length}`);
    if (!editor) {
      await appendLog('applyClipboardTextToMonaco: no editor instance');
      return false;
    }

    // Focus first (some Monaco internal handlers only accept typing when focused)
    try { editor.focus(); } catch (e) { /* ignore */ }

    // Prefer simulating typing which integrates with Monaco's composition/undo stack.
    try {
      if (typeof editor.trigger === 'function') {
        try {
          editor.trigger('keyboard', 'type', { text });
          try { editor.focus(); } catch (_) {}
          await appendLog('applyClipboardTextToMonaco: editor.trigger type succeeded');
          return true;
        } catch (triggerErr) {
          await appendLog(`applyClipboardTextToMonaco: editor.trigger threw: ${triggerErr && triggerErr.message ? triggerErr.message : String(triggerErr)}`);
          // fall through to executeEdits
        }
      }
    } catch (e) {
      await appendLog(`applyClipboardTextToMonaco: trigger attempt failed: ${e && e.message ? e.message : String(e)}`);
    }

    const monaco = monacoManager.getMonaco();
    const model = (typeof editor.getModel === 'function') ? editor.getModel() : null;

    // Try to compute current selection (if any)
    let selection = null;
    try {
      if (typeof editor.getSelection === 'function') selection = editor.getSelection();
    } catch (_) { selection = null; }

    // Build a Range; if selection missing, use end-of-model
    let range = null;
    try {
      if (selection && typeof selection.getStartPosition === 'function' && typeof selection.getEndPosition === 'function' && monaco) {
        const s = selection.getStartPosition();
        const e = selection.getEndPosition();
        range = new monaco.Range(s.lineNumber, s.column, e.lineNumber, e.column);
      } else if (model && monaco) {
        const offset = model.getValueLength ? model.getValueLength() : (model.getValue ? model.getValue().length : 0);
        const pos = model.getPositionAt ? model.getPositionAt(offset) : { lineNumber: 1, column: 1 };
        range = new monaco.Range(pos.lineNumber, pos.column, pos.lineNumber, pos.column);
      }
    } catch (err) {
      await appendLog(`applyClipboardTextToMonaco: failed to build range: ${err && err.message ? err.message : String(err)}`);
    }

    // Try executeEdits
    try {
      if (typeof editor.executeEdits === 'function' && range) {
        editor.executeEdits('clipboardPaste', [{ range, text, forceMoveMarkers: true }]);
        try { editor.focus(); } catch (_) {}
        await appendLog('applyClipboardTextToMonaco: executeEdits succeeded');
        return true;
      }
    } catch (execErr) {
      await appendLog(`applyClipboardTextToMonaco: executeEdits threw: ${execErr && execErr.message ? execErr.message : String(execErr)}`);
    }

    // Fallback: set model.value (append or full replace)
    try {
      if (model && typeof model.setValue === 'function') {
        // Try to insert at selection offset when possible
        try {
          if (selection && typeof model.getOffsetAt === 'function') {
            const startOff = model.getOffsetAt(selection.getStartPosition());
            const endOff = model.getOffsetAt(selection.getEndPosition());
            const cur = model.getValue();
            const newVal = cur.slice(0, startOff) + text + cur.slice(endOff);
            monacoManager.ignoreModelContentChanges(() => model.setValue(newVal));
            await appendLog('applyClipboardTextToMonaco: fallback setValue with selection succeeded');
            return true;
          }
        } catch (e) {
          // fallback to appending at end
        }

        try {
          const cur = model.getValue ? model.getValue() : '';
          monacoManager.ignoreModelContentChanges(() => model.setValue(cur + text));
          await appendLog('applyClipboardTextToMonaco: fallback append setValue succeeded');
          return true;
        } catch (e) {
          await appendLog(`applyClipboardTextToMonaco: model setValue fallback failed: ${e && e.message ? e.message : String(e)}`);
        }
      }
    } catch (err) {
      await appendLog(`applyClipboardTextToMonaco: final fallback error: ${err && err.message ? err.message : String(err)}`);
    }

    await appendLog('applyClipboardTextToMonaco: all strategies failed');
    return false;
  } catch (outerErr) {
    await appendLog(`applyClipboardTextToMonaco: unexpected error: ${outerErr && outerErr.message ? outerErr.message : String(outerErr)}`);
    return false;
  }
}

/**
 * Common paste handler used by DOM listeners and keydown fallback.
 * Accepts either a DOM event (with clipboardData) or a synthetic call.
 *
 * sourceHint: 'document-capture' | 'editor-dom-capture' | 'beforeinput' | 'force' | other
 */
async function handlePasteEvent(e, sourceHint = 'unknown') {
  try {
    const startTs = Date.now();
    await appendLog(`paste handler entered (sourceHint=${sourceHint})`);

    // Determine target early
    const target = (e && (e.target || (typeof e.composedPath === 'function' && e.composedPath()[0]))) || null;
    try { await appendLog(`paste handler target: ${target && target.nodeName ? target.nodeName : String(target)}`); } catch (_) {}

    // If the event target is an interactive text input (including Monaco find widget), do NOT intercept.
    try {
      if (isInteractiveElement(target) || isInsideMonacoFindWidget(target)) {
        await appendLog('paste handler: target is interactive/find-widget input; allowing native paste');
        return; // let default paste happen
      }
    } catch (_) { /* ignore */ }

    // Log event types if available
    try {
      const types = (e && e.clipboardData && e.clipboardData.types) ? Array.from(e.clipboardData.types)
                 : (e && e.dataTransfer && e.dataTransfer.types) ? Array.from(e.dataTransfer.types) : [];
      await appendLog(`paste event types: ${JSON.stringify(types)}`);
    } catch (_) {}

    // Choose the appropriate editor: try event-targeted editor first, then fall back to primary
    let editor = monacoManager.getEditorFromEvent(e) || monacoManager.getEditor();
    if (!editor) {
      await appendLog('paste handler: no editor instance');
      return;
    }

    // Ensure there is a writable model; if none/tabless or media active, create an untitled text tab
    try {
      if (!editor.getModel || !editor.getModel()) {
        if (tabs && typeof tabs.createUntitledTab === 'function') {
          await appendLog('paste handler: no model; creating untitled tab');
          try { tabs.createUntitledTab('Untitled'); } catch (_) {}
          // Give the UI a tick to bind the model
          try { editor = monacoManager.getEditor(); } catch (_) {}
        }
      }
    } catch (_) {}

    // Determine focus: prefer Monaco's hasTextFocus, but also accept events that are inside some editor DOM.
    let hasFocus = false;
    try {
      hasFocus = !!(editor && typeof editor.hasTextFocus === 'function' && editor.hasTextFocus());
    } catch (_) { hasFocus = false; }
    const eventInsideEditor = isEventInsideMonacoEditor(e);
    await appendLog(`paste handler: editor present, hasTextFocus=${hasFocus}, eventInsideEditor=${eventInsideEditor}`);

    // If editor doesn't appear focused and this is not a forced invocation, try to focus it when event targets nothing
    if (!hasFocus && !eventInsideEditor && sourceHint !== 'force') {
      try { editor.focus && editor.focus(); } catch (_) {}
      // proceed anyway to ensure paste works even when focus is slightly off
    }

    if (e && (e.preventDefault)) { try { e.preventDefault(); } catch (_) {} }
    if (e && (e.stopPropagation)) { try { e.stopPropagation(); } catch (_) {} }

    // Prefer clipboard text provided on the event (useful for synthetic/force calls)
    let clipboardText = '';
    if (e && e._clipboardText) {
      try { clipboardText = String(e._clipboardText); await appendLog(`paste handler: used provided _clipboardText length=${clipboardText.length}`); } catch (_) {}
    }

    // Prefer synchronous event.clipboardData
    try {
      if (!clipboardText && e && e.clipboardData && typeof e.clipboardData.getData === 'function') {
        clipboardText = e.clipboardData.getData('text/plain') || e.clipboardData.getData('text') || '';
        await appendLog(`paste handler: read from event.clipboardData length=${clipboardText.length}`);
      }
    } catch (err) {
      await appendLog(`paste handler: event.clipboardData read error: ${err && err.message ? err.message : String(err)}`);
    }

    // Fallback to navigator.clipboard.readText()
    if (!clipboardText) {
      try {
        if (navigator.clipboard && typeof navigator.clipboard.readText === 'function') {
          clipboardText = await navigator.clipboard.readText();
          await appendLog(`paste handler: read from navigator.clipboard length=${clipboardText.length}`);
        }
      } catch (err) {
        await appendLog(`paste handler: navigator.clipboard.readText error: ${err && err.message ? err.message : String(err)}`);
      }
    }

    // Final fallback to Electron clipboard
    if (!clipboardText) {
      try {
        const { clipboard } = require('electron');
        if (clipboard && typeof clipboard.readText === 'function') {
          clipboardText = clipboard.readText();
          await appendLog(`paste handler: read from electron.clipboard length=${clipboardText.length}`);
        }
      } catch (err) {
        await appendLog(`paste handler: electron.clipboard error: ${err && err.message ? err.message : String(err)}`);
      }
    }

    await appendLog(`paste handler: clipboardText length=${clipboardText ? clipboardText.length : 0}`);

    if (!clipboardText) {
      await appendLog('paste handler: no clipboard text found');
      return;
    }

    // As a final fallback, if Monaco still lacks a model, try textarea fallback
    let applied = false;
    try {
      if (!editor.getModel || !editor.getModel()) {
        const ta = document.getElementById('editor-content');
        if (ta && ta.tagName === 'TEXTAREA') {
          ta.removeAttribute('disabled');
          const start = ta.selectionStart || 0;
          const end = ta.selectionEnd || 0;
          const v = ta.value || '';
          ta.value = v.slice(0, start) + clipboardText + v.slice(end);
          ta.selectionStart = ta.selectionEnd = start + clipboardText.length;
          applied = true;
          await appendLog('paste handler: applied to fallback textarea');
        }
      }
    } catch (_) {}

    if (!applied) {
      applied = await applyClipboardTextToMonaco(editor, clipboardText);
    }

    await appendLog(`paste handler: applied=${applied} (duration=${Date.now() - startTs}ms)`);
  } catch (err) {
    await appendLog(`paste handler outer error: ${err && err.message ? err.message : String(err)}`);
    console.error('[pasteHandler] paste handler failed:', err);
  }
}

/**
 * Attach listeners:
 * - document capture paste (to catch context-menu or default paste flow)
 * - editor DOM node capture paste (stronger)
 * - document capture keydown fallback for Ctrl/Cmd+V (immediate electron.clipboard read)
 * - beforeinput (insertFromPaste) as an incremental signal
 */
async function attachPasteHandlers() {
  try {
    // document-level capture listener
    document.addEventListener('paste', (e) => {
      handlePasteEvent(e, 'document-capture');
    }, { capture: true, passive: false });
    await appendLog('Attached document paste listener (capture)');

    // beforeinput listener to catch insertFromPaste where supported
    document.addEventListener('beforeinput', (e) => {
      try {
        if (e && e.inputType === 'insertFromPaste') {
          // Some browsers provide clipboard data on beforeinput; forward
          handlePasteEvent(e, 'beforeinput');
        }
      } catch (_) {}
    }, { capture: true, passive: false });
    await appendLog('Attached document beforeinput listener (capture)');

    // Attach to editor DOM node if available, else poll for it briefly
    const tryAttachToEditorDom = async () => {
      try {
        const editor = monacoManager.getEditor();
        const domNode = editor && typeof editor.getDomNode === 'function' ? editor.getDomNode() : null;
        if (domNode && domNode.addEventListener) {
          domNode.addEventListener('paste', (e) => handlePasteEvent(e, 'editor-dom-capture'), { capture: true, passive: false });
          domNode.addEventListener('beforeinput', (e) => {
            try { if (e && e.inputType === 'insertFromPaste') handlePasteEvent(e, 'editor-dom-beforeinput'); } catch (_) {}
          }, { capture: true, passive: false });
          await appendLog('Attached paste/beforeinput listeners to editor DOM node (capture)');
          return true;
        }
      } catch (err) {
        await appendLog(`Error trying to attach to editor DOM: ${err && err.message ? err.message : String(err)}`);
      }
      return false;
    };

    let attached = await tryAttachToEditorDom();
    if (!attached) {
      // Poll for a short while (e.g., 2 seconds)
      const start = Date.now();
      const timeoutMs = 2000;
      while (!attached && (Date.now() - start) < timeoutMs) {
        // small delay
        await new Promise(r => setTimeout(r, 150));
        attached = await tryAttachToEditorDom();
      }
      if (!attached) {
        await appendLog('Editor DOM node not available to attach paste listener (will rely on document-level listener)');
      }
    }

    // Keydown fallback: capture Ctrl/Cmd+V and attempt to read clipboard and force-apply if necessary
    document.addEventListener('keydown', (e) => {
      try {
        const isPasteKey = ((e.ctrlKey || e.metaKey) && e.key && e.key.toLowerCase() === 'v');
        if (!isPasteKey) return;

        // If focused element is an interactive input (including Monaco find/replace inputs), do not interfere
        const ae = document.activeElement;
        if (isInteractiveElement(ae) || isInsideMonacoFindWidget(ae)) {
          return; // allow native paste
        }

        // Try immediate synchronous electron.clipboard read to avoid navigator permissions
        appendLog('Keydown detected Cmd/Ctrl+V (immediate fallback)').catch(() => {});
        let text = '';
        try {
          const { clipboard } = require('electron');
          if (clipboard && typeof clipboard.readText === 'function') {
            text = clipboard.readText();
            appendLog(`keydown immediate: electron.clipboard.readText len=${text.length}`).catch(() => {});
          }
        } catch (err) {
          appendLog(`keydown immediate: electron.clipboard error: ${err && err.message ? err.message : String(err)}`).catch(() => {});
        }

        const targetEditor = monacoManager.getEditorFromEvent(e) || monacoManager.getEditor();
        if (text) {
          try { e.preventDefault(); e.stopPropagation(); } catch (_) {}
          if (!targetEditor) {
            appendLog('keydown fallback: no editor instance to apply clipboard text').catch(() => {});
            return;
          }
          // ensure a writable model; create untitled if needed
          try {
            if (!targetEditor.getModel || !targetEditor.getModel()) {
              if (tabs && typeof tabs.createUntitledTab === 'function') {
                tabs.createUntitledTab('Untitled');
              }
            }
          } catch (_) {}
          // ensure focus then apply
          try { targetEditor.focus(); } catch (_) {}
          applyClipboardTextToMonaco(targetEditor, text).catch(() => {});
          return;
        }

        // If immediate read failed, schedule a short delayed fallback (gives browser a chance to fire paste event)
        appendLog('keydown fallback: immediate clipboard empty, scheduling delayed check').catch(() => {});
        setTimeout(async () => {
          try {
            const editor = monacoManager.getEditorFromEvent(e) || monacoManager.getEditor();
            if (!editor) {
              appendLog('keydown delayed fallback: no editor present').catch(() => {});
              return;
            }
            let text2 = '';
            // Try navigator.clipboard first (may require permissions)
            try {
              if (navigator.clipboard && typeof navigator.clipboard.readText === 'function') {
                text2 = await navigator.clipboard.readText();
                appendLog(`keydown delayed: navigator.clipboard read len=${text2.length}`).catch(() => {});
              }
            } catch (err) {
              appendLog(`keydown delayed: navigator.clipboard error: ${err && err.message ? err.message : String(err)}`).catch(() => {});
            }
            if (!text2) {
              try {
                const { clipboard } = require('electron');
                text2 = clipboard.readText();
                appendLog(`keydown delayed: electron.clipboard read len=${text2.length}`).catch(() => {});
              } catch (err) {
                appendLog(`keydown delayed: electron.clipboard error: ${err && err.message ? err.message : String(err)}`).catch(() => {});
              }
            }
            if (text2) {
              try { e.preventDefault(); e.stopPropagation(); } catch (_) {}
              try {
                if (!editor.getModel || !editor.getModel()) {
                  if (tabs && typeof tabs.createUntitledTab === 'function') {
                    tabs.createUntitledTab('Untitled');
                  }
                }
              } catch (_) {}
              try { editor.focus(); } catch (_) {}
              applyClipboardTextToMonaco(editor, text2).catch(() => {});
            } else {
              appendLog('keydown delayed: no clipboard text to apply').catch(() => {});
            }
          } catch (err) {
            appendLog(`keydown delayed outer error: ${err && err.message ? err.message : String(err)}`).catch(() => {});
          }
        }, 40);
      } catch (err) {
        // swallow
      }
    }, { capture: true });
    await appendLog('Attached Ctrl/Cmd+V keydown fallback (capture)');
  } catch (err) {
    await appendLog(`Error attaching paste handlers: ${err && err.message ? err.message : String(err)}`);
  }
}

module.exports = {
  attachPasteHandlers,
  // expose handler for manual invocation/testing
  handlePasteEvent,
  applyClipboardTextToMonaco
};