// src/main/functions/editor/revert.js
// Per-tab undo/redo manager that records minimal diffs for each change.
// - recordChange(tabId, prevContent, newContent, prevCursor, newCursor)
// - undo(tabOrTabId) / redo(tabOrTabId) apply the previous/next operation.
// Designed to be used by tabManagement and init.js (which supplies the active tab).
//
// Implementation notes:
// - Each operation stores a minimal diff (start, deleteText, insertText) AND
//   a prevContent/newContent snapshot as a reliable fallback when applying edits.
// - When applying, we try a minimal edit (using Monaco model.applyEdits or string splice).
//   If that is not possible (content diverged), we fallback to replacing the whole content.
// - The module does not require tabManagement (to avoid circular requires). Consumers
//   should pass the tab object (or tab.id) and handle updating tab metadata/UI after apply.

const monacoManager = require('./monacoManager');

const MAX_STACK_ENTRIES = 500;

const _stacks = new Map(); // tabId -> { undo: [], redo: [] }

function _ensureStack(tabId) {
  if (!tabId) return null;
  if (!_stacks.has(tabId)) _stacks.set(tabId, { undo: [], redo: [] });
  return _stacks.get(tabId);
}

function _computeDiff(a, b) {
  const as = a == null ? '' : String(a);
  const bs = b == null ? '' : String(b);
  const lenA = as.length;
  const lenB = bs.length;
  let start = 0;
  while (start < lenA && start < lenB && as[start] === bs[start]) start++;
  let endA = lenA - 1;
  let endB = lenB - 1;
  while (endA >= start && endB >= start && as[endA] === bs[endB]) {
    endA--;
    endB--;
  }
  const deleteText = as.substring(start, endA + 1);
  const insertText = bs.substring(start, endB + 1);
  return { start, deleteText, insertText };
}

/**
 * Record a change for a tab.
 * - tabId: identifier for a tab (string)
 * - prevContent: content before the change
 * - newContent: content after the change
 * - prevCursor/newCursor: optional cursor indices (number)
 */
function recordChange(tabId, prevContent, newContent, prevCursor = null, newCursor = null) {
  if (!tabId) return;
  const prev = prevContent == null ? '' : String(prevContent);
  const neu = newContent == null ? '' : String(newContent);
  if (prev === neu) return;

  try {
    const diff = _computeDiff(prev, neu);
    const op = {
      // minimal diff
      start: diff.start,
      deleteText: diff.deleteText,
      insertText: diff.insertText,

      // snapshots for robust fallback
      prevContent: prev,
      newContent: neu,

      // cursor hints
      prevCursor: typeof prevCursor === 'number' ? prevCursor : null,
      newCursor: typeof newCursor === 'number' ? newCursor : null,

      ts: Date.now()
    };

    const stacks = _ensureStack(tabId);
    if (!stacks) return;
    stacks.undo.push(op);
    // clear redo on new user change
    stacks.redo.length = 0;

    // limit stack size
    if (stacks.undo.length > MAX_STACK_ENTRIES) stacks.undo.shift();
  } catch (err) {
    // Best-effort: do not throw
    console.warn('[revert] recordChange failed:', err);
  }
}

/**
 * Apply a minimal or full update to the editor surface for the given tab.
 * Returns the applied operation (the op object) or null on no-op/failure.
 *
 * Note: caller should set window.__revert_applying = true while calling undo/redo
 * to avoid recording programmatic changes through markTabUnsaved.
 *
 * Accepts either the tab object or a tab id. If a tab object is given, it can
 * be used to compute language/model path; otherwise the function focuses on
 * applying to the current editor model / textarea.
 */
function undo(tabOrId) {
  try {
    const tabId = (tabOrId && tabOrId.id) ? tabOrId.id : tabOrId;
    if (!tabId) return null;
    const stacks = _ensureStack(tabId);
    if (!stacks || stacks.undo.length === 0) return null;
    const op = stacks.undo.pop();
    if (!op) return null;
    stacks.redo.push(op);

    _applyOpContent(op.prevContent, op, tabOrId, /*isUndo*/ true);
    return op;
  } catch (err) {
    console.warn('[revert] undo failed:', err);
    return null;
  }
}

function redo(tabOrId) {
  try {
    const tabId = (tabOrId && tabOrId.id) ? tabOrId.id : tabOrId;
    if (!tabId) return null;
    const stacks = _ensureStack(tabId);
    if (!stacks || stacks.redo.length === 0) return null;
    const op = stacks.redo.pop();
    if (!op) return null;
    stacks.undo.push(op);

    _applyOpContent(op.newContent, op, tabOrId, /*isUndo*/ false);
    return op;
  } catch (err) {
    console.warn('[revert] redo failed:', err);
    return null;
  }
}

/**
 * Try to apply a value (targetContent) using the minimal op when possible,
 * otherwise fall back to replacing the whole content.
 *
 * op is the operation object that contains both diff and snapshots.
 * tabOrId is optional; if it's an object with .path we may use it for model creation.
 */
function _applyOpContent(targetContent, op, tabOrId, isUndo) {
  // targetContent is either op.prevContent (undo) or op.newContent (redo)
  const monaco = monacoManager.getMonaco ? monacoManager.getMonaco() : null;
  const editor = monacoManager.getEditor();
  const useMonaco = !!editor && !!monaco;

  // If caller didn't set the global revert applying flag, we still use ignoreModelContentChanges
  // to avoid emitting model-change events to the rest of the app.
  try {
    if (useMonaco) {
      monacoManager.ignoreModelContentChanges(() => {
        try {
          const model = editor.getModel();
          if (!model) {
            // try to ensure model exists for path if available
            try {
              const path = (tabOrId && tabOrId.path) ? tabOrId.path : null;
              const lang = path ? monacoManager.detectLanguageFromPath(path) : undefined;
              monacoManager.setModelForPath(path || ('untitled://' + (tabOrId && tabOrId.id ? tabOrId.id : Date.now())), targetContent, lang);
              return;
            } catch (_) {
              // fallback to nothing
            }
            return;
          }

          const current = model.getValue() || '';

          // Attempt to apply minimal inverse edit if the current content matches expectations.
          // We expect to find op.insertText at op.start if undoing (replace insertText with deleteText).
          const expectedSegment = isUndo ? (op.insertText || '') : (op.deleteText || '');
          const expectedLength = expectedSegment.length;
          const sliceAt = typeof op.start === 'number' ? op.start : null;
          let appliedMinimally = false;

          if (sliceAt != null && sliceAt >= 0 && current.length >= sliceAt && expectedLength <= (current.length - sliceAt)) {
            // verify that the expected segment matches the current state at the offset
            const currentSeg = current.substr(sliceAt, expectedLength);
            if (currentSeg === expectedSegment) {
              try {
                // compute positions and apply edit using Monaco Range
                const startPos = model.getPositionAt(sliceAt);
                const endPos = model.getPositionAt(sliceAt + expectedLength);
                const Range = monaco.Range;
                const range = new Range(startPos.lineNumber, startPos.column, endPos.lineNumber, endPos.column);
                const replacementText = isUndo ? (op.deleteText || '') : (op.insertText || '');
                // applyEdits will record internal Monaco undo; we are suppressing model-change callbacks
                model.applyEdits([{ range, text: replacementText }]);
                appliedMinimally = true;
              } catch (e) {
                // fall through to full replace
                appliedMinimally = false;
              }
            }
          }

          if (!appliedMinimally) {
            // fallback: replace entire model value
            try {
              model.setValue(targetContent == null ? '' : String(targetContent));
            } catch (e) {
              // nothing else to do
            }
          }

          // Try to restore cursor position if provided
          if (typeof op.prevCursor === 'number' && isUndo) {
            try {
              const pos = model.getPositionAt(Math.max(0, Math.floor(op.prevCursor)));
              const ed = monacoManager.getEditor();
              if (ed && typeof ed.setPosition === 'function') {
                ed.setPosition(pos);
                ed.revealPositionInCenter(pos);
              }
            } catch (_) {}
          } else if (typeof op.newCursor === 'number' && !isUndo) {
            try {
              const pos = model.getPositionAt(Math.max(0, Math.floor(op.newCursor)));
              const ed = monacoManager.getEditor();
              if (ed && typeof ed.setPosition === 'function') {
                ed.setPosition(pos);
                ed.revealPositionInCenter(pos);
              }
            } catch (_) {}
          }
        } catch (e) {
          // ignore
        }
      });
      return;
    }

    // Textarea fallback
    const contentArea = document.getElementById('editor-content');
    if (contentArea && contentArea.tagName === 'TEXTAREA') {
      // suppress input handlers while updating
      window.__editor_suppress_input = true;
      try {
        const current = contentArea.value || '';
        const sliceAt = typeof op.start === 'number' ? op.start : null;
        const expectedSegment = isUndo ? (op.insertText || '') : (op.deleteText || '');
        const expectedLength = expectedSegment.length;
        let appliedMinimally = false;

        if (sliceAt != null && sliceAt >= 0 && current.length >= sliceAt && expectedLength <= (current.length - sliceAt)) {
          const currentSeg = current.substr(sliceAt, expectedLength);
          if (currentSeg === expectedSegment) {
            const replacement = isUndo ? (op.deleteText || '') : (op.insertText || '');
            const newVal = current.substr(0, sliceAt) + replacement + current.substr(sliceAt + expectedLength);
            contentArea.value = newVal;
            appliedMinimally = true;
          }
        }

        if (!appliedMinimally) {
          contentArea.value = targetContent == null ? '' : String(targetContent);
        }

        // restore caret if available
        const caret = isUndo ? op.prevCursor : op.newCursor;
        if (typeof caret === 'number') {
          try { contentArea.selectionStart = contentArea.selectionEnd = caret; } catch (_) {}
        }
      } finally {
        // small delay so any input listeners that check __editor_suppress_input run after update
        setTimeout(() => { window.__editor_suppress_input = false; }, 0);
      }
    }
  } catch (err) {
    console.warn('[revert] _applyOpContent failed:', err);
  }
}

/**
 * Clear undo/redo stacks for a tab (call when closing a tab to free memory).
 */
function clearStacksForTab(tabId) {
  try {
    if (!tabId) return;
    _stacks.delete(tabId);
  } catch (err) {
    // ignore
  }
}

function getUndoCount(tabId) {
  const s = _stacks.get(tabId);
  return s ? s.undo.length : 0;
}
function getRedoCount(tabId) {
  const s = _stacks.get(tabId);
  return s ? s.redo.length : 0;
}

module.exports = {
  recordChange,
  undo,
  redo,
  clearStacksForTab,
  getUndoCount,
  getRedoCount
};