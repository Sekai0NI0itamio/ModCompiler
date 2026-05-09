// src/main/functions/chat/helpers/fileTokens.js
// Inline file token management for the chat input (contenteditable #user-input)

const { saveCurrentInputDraft } = require('../incrementalHistoryStorage');

function formatFileSize(size) {
  const units = ['B', 'KB', 'MB', 'GB'];
  let n = size || 0, i = 0;
  while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
  return `${n.toFixed(1)} ${units[i]}`;
}

function createTokenSpan(file) {
  const span = document.createElement('span');
  const name = file.name || 'file';
  const sz = typeof file.size === 'number' ? formatFileSize(file.size) : '';
  const path = file.path || '';
  const label = path || name;
  span.className = 'file-token';
  span.textContent = `@${label}${sz ? `(${sz})` : ''}`;
  span.setAttribute('data-name', name);
  if (path) span.setAttribute('data-path', path);
  if (typeof file.size === 'number') span.setAttribute('data-size', String(file.size));
  span.contentEditable = 'false';
  return span;
}

function insertNodeAtCaret(node) {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) {
    const input = document.getElementById('user-input');
    input && input.appendChild(node);
    return;
  }
  const range = sel.getRangeAt(0);
  range.deleteContents();
  range.insertNode(node);
  // place caret after node
  range.setStartAfter(node);
  range.setEndAfter(node);
  sel.removeAllRanges();
  sel.addRange(range);
}

function insertTextAtCaret(text) {
  try { document.execCommand('insertText', false, text); } catch (_) {
    const tn = document.createTextNode(text);
    insertNodeAtCaret(tn);
  }
}

async function addFilesToStateAndDraft(files) {
  window.droppedFiles = (window.droppedFiles || []);
  const existing = new Set(window.droppedFiles.map(f => (f.path || (f.name + '|' + f.size))));
  for (const f of files) {
    const key = f.path || (f.name + '|' + f.size);
    if (!existing.has(key)) window.droppedFiles.push(f);
  }
  try {
    const text = document.getElementById('user-input')?.textContent || '';
    await saveCurrentInputDraft(text, window.droppedFiles);
  } catch (_) {}
}

function removeFileFromStateByPathOrName(pathOrName) {
  const before = Array.isArray(window.droppedFiles) ? window.droppedFiles : [];
  window.droppedFiles = before.filter(f => {
    const matchPath = (f && f.path && f.path === pathOrName);
    const matchName = (f && f.name && f.name === pathOrName);
    return !(matchPath || matchName);
  });
}

function showTip(span) {
  hideTip();
  const tip = document.createElement('div');
  tip.className = 'file-token-tip';
  const path = span.getAttribute('data-path') || '';
  tip.textContent = path || span.getAttribute('data-name') || '';
  document.body.appendChild(tip);
  const rect = span.getBoundingClientRect();
  // Position to the left by default; clamp within viewport; if not enough space, place to the right
  const tw = tip.offsetWidth;
  let left = rect.left - tw - 8;
  if (left < 8) left = rect.right + 8;
  const top = Math.max(8, rect.top);
  tip.style.left = Math.round(left) + 'px';
  tip.style.top = Math.round(top) + 'px';
  window.__fileTokenTip = tip;
}

function hideTip() {
  const tip = window.__fileTokenTip;
  if (tip && tip.parentNode) tip.parentNode.removeChild(tip);
  window.__fileTokenTip = null;
}

function initFileTokens() {
  function attach() {
    const input = document.getElementById('user-input');
    if (!input) return false;

    // Hover tooltip (use closest to be robust)
    input.addEventListener('mouseover', (e) => {
      const t = e.target && e.target.closest ? e.target.closest('.file-token') : null;
      if (t && t.classList && t.classList.contains('file-token')) {
        showTip(t);
      }
    });
    input.addEventListener('mouseout', (e) => {
      const t = e.target && e.target.closest ? e.target.closest('.file-token') : null;
      if (t && t.classList && t.classList.contains('file-token')) {
        hideTip();
      }
    });
    window.addEventListener('scroll', hideTip, true);

    // Keep droppedFiles in sync with tokens present in DOM.
    // IMPORTANT: avoid scanning the whole contenteditable on every keystroke (can lag badly
    // for large pasted/typed content). Only reconcile when the edit type might affect tokens.
    let reconcileTimer = null;
    function scheduleReconcile() {
      try { if (reconcileTimer) clearTimeout(reconcileTimer); } catch (_) {}
      reconcileTimer = setTimeout(() => {
        reconcileTimer = null;
        try {
          // Fast-path: if there are no dropped files and there are no token elements, skip.
          const hasAnyDropped = Array.isArray(window.droppedFiles) && window.droppedFiles.length > 0;
          const hasAnyTokenEl = !!input.querySelector('.file-token');
          if (!hasAnyDropped && !hasAnyTokenEl) return;

          const tokens = input.querySelectorAll('.file-token');
          const keys = new Set();
          tokens.forEach(span => {
            const key = span.getAttribute('data-path') || (span.getAttribute('data-name') + '|' + (span.getAttribute('data-size') || ''));
            if (key) keys.add(key);
          });
          const before = Array.isArray(window.droppedFiles) ? window.droppedFiles : [];
          const after = before.filter(f => {
            const k = (f && (f.path || (f.name + '|' + f.size)));
            return keys.has(k);
          });
          window.droppedFiles = after;

          // Persist draft with updated files (best-effort)
          const text = input.textContent || '';
          saveCurrentInputDraft(text, window.droppedFiles).catch(() => {});
        } catch (_) {}
      }, 250);
    }

    input.addEventListener('input', (e) => {
      // Reconcile only for edits that can reasonably affect file tokens.
      // For normal typing ('insertText'), do nothing.
      const it = e && typeof e.inputType === 'string' ? e.inputType : '';
      const should =
        it.startsWith('delete') ||
        it === 'insertFromPaste' ||
        it === 'insertFromDrop' ||
        it === 'insertFromYank' ||
        it === 'insertReplacementText';
      if (should) scheduleReconcile();
    });

    // Delete whole token on Backspace/Delete when caret adjacent to it
    input.addEventListener('keydown', (e) => {
      const sel = window.getSelection();
      if (!sel || !sel.isCollapsed) return;
      const range = sel.rangeCount ? sel.getRangeAt(0) : null;
      if (!range) return;

      if (e.key === 'Backspace') {
        // If at start of a text node and previousSibling is token
        if (range.startContainer && range.startOffset === 0) {
          const prev = (range.startContainer.nodeType === 3 ? range.startContainer.previousSibling : range.startContainer.childNodes[range.startOffset - 1]) || range.startContainer.previousSibling;
          if (prev && prev.classList && prev.classList.contains('file-token')) {
            e.preventDefault();
            const pathOrName = prev.getAttribute('data-path') || (prev.getAttribute('data-name'));
            prev.remove();
            removeFileFromStateByPathOrName(pathOrName);
            try { input.dispatchEvent(new Event('input', { bubbles: true })); } catch (_) {}
          }
        }
      } else if (e.key === 'Delete') {
        // If at end of a text node and nextSibling is token
        if (range.startContainer && range.startContainer.nodeType === 3) {
          const textNode = range.startContainer;
          if (range.startOffset === textNode.length) {
            const next = textNode.nextSibling;
            if (next && next.classList && next.classList.contains('file-token')) {
              e.preventDefault();
              const pathOrName = next.getAttribute('data-path') || (next.getAttribute('data-name'));
              next.remove();
              removeFileFromStateByPathOrName(pathOrName);
              try { input.dispatchEvent(new Event('input', { bubbles: true })); } catch (_) {}
            }
          }
        }
      }
    });

    return true;
  }

  if (!attach()) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', attach, { once: true });
    } else {
      // If element appears later (dynamic), observe briefly
      const mo = new MutationObserver(() => {
        if (attach()) mo.disconnect();
      });
      mo.observe(document.documentElement, { childList: true, subtree: true });
      setTimeout(() => mo.disconnect(), 3000);
    }
  }
}

function insertFileTokens(files) {
  if (!Array.isArray(files) || files.length === 0) return;
  for (const f of files) {
    const span = createTokenSpan(f);
    insertNodeAtCaret(span);
    insertTextAtCaret(' ');
  }
  // Trigger resize
  try { document.getElementById('user-input').dispatchEvent(new Event('input', { bubbles: true })); } catch (_) {}
}

module.exports = { initFileTokens, insertFileTokens, formatFileSize };
