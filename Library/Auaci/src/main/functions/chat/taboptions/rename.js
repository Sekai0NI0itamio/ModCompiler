// Lightweight modal for renaming a chat tab. Accepts a commit callback so the caller
// (tabManager) can perform the actual persistence/refresh. Returns a Promise that
// resolves when the dialog closes: { committed: boolean, name?: string }.

const STYLE_ID = 'auaci-chat-rename-styles';

function injectStyles() {
  if (typeof document === 'undefined') return;
  if (document.getElementById(STYLE_ID)) return;
  const css = `
    /* Rename dialog styles */
    .auaci-rename-overlay {
      position: fixed;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0,0,0,0.48);
      z-index: 2147483646;
    }
    .auaci-rename-card {
      width: min(92vw, 520px);
      background: #fff;
      border-radius: 10px;
      box-shadow: 0 8px 30px rgba(0,0,0,0.18);
      padding: 16px;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      gap: 10px;
      font-family: "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      color: #0f172a;
    }
    .auaci-rename-title {
      font-size: 16px;
      font-weight: 600;
    }
    .auaci-rename-desc {
      font-size: 13px;
      color: #475569;
    }
    .auaci-rename-input {
      padding: 10px 12px;
      font-size: 14px;
      border-radius: 8px;
      border: 1px solid #d1d5db;
      outline: none;
      box-sizing: border-box;
      width: 100%;
    }
    .auaci-rename-hint {
      font-size: 12px;
      color: #6b7280;
    }
    .auaci-rename-buttons {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
      margin-top: 6px;
    }
    .auaci-rename-btn {
      padding: 8px 12px;
      border-radius: 8px;
      border: 1px solid #d1d5db;
      background: #f8fafc;
      cursor: pointer;
      font-size: 14px;
    }
    .auaci-rename-btn.primary {
      background: linear-gradient(180deg,#0b66ff,#075fe6);
      color: #fff;
      border-color: rgba(11,102,255,0.9);
      font-weight: 600;
    }
    @media (max-width: 480px) {
      .auaci-rename-card { width: calc(100vw - 28px); padding: 12px; }
      .auaci-rename-input { font-size: 14px; padding: 9px; }
    }
  `;
  const s = document.createElement('style');
  s.id = STYLE_ID;
  s.textContent = css;
  document.head.appendChild(s);
}

/**
 * openRenameWindow(sessionId, currentName, onCommit) -> Promise<{committed, name?}>
 * - sessionId: identifier (passed through, not used by this UI directly)
 * - currentName: string to prefill the input with
 * - onCommit: async function(newName) called when user presses Enter or clicks Save
 *
 * Promise resolves when dialog closes. If committed is true, name contains the final name.
 */
function openRenameWindow(sessionId, currentName = '', onCommit) {
  return new Promise((resolve) => {
    if (typeof document === 'undefined') return resolve({ committed: false });

    injectStyles();

    // Avoid opening multiple dialogs for the same session id - best-effort check
    const existing = document.querySelector(`.auaci-rename-overlay[data-session-id="${sessionId}"]`);
    if (existing) {
      // focus the existing input if possible
      const inp = existing.querySelector('.auaci-rename-input');
      if (inp) {
        inp.focus();
        inp.select();
      }
      return resolve({ committed: false });
    }

    const overlay = document.createElement('div');
    overlay.className = 'auaci-rename-overlay';
    overlay.setAttribute('data-session-id', String(sessionId || ''));

    const card = document.createElement('div');
    card.className = 'auaci-rename-card';

    const title = document.createElement('div');
    title.className = 'auaci-rename-title';
    title.textContent = 'Rename chat';

    const desc = document.createElement('div');
    desc.className = 'auaci-rename-desc';
    desc.textContent = 'Change the display name for this chat session. Press Enter to save, or Esc to cancel.';

    const input = document.createElement('input');
    input.className = 'auaci-rename-input';
    input.type = 'text';
    input.setAttribute('aria-label', 'Rename chat session');
    input.value = String(currentName || '');

    const hint = document.createElement('div');
    hint.className = 'auaci-rename-hint';
    hint.textContent = 'Tip: keep names short and descriptive.';

    const buttons = document.createElement('div');
    buttons.className = 'auaci-rename-buttons';

    const cancelBtn = document.createElement('button');
    cancelBtn.className = 'auaci-rename-btn';
    cancelBtn.type = 'button';
    cancelBtn.textContent = 'Cancel';

    const saveBtn = document.createElement('button');
    saveBtn.className = 'auaci-rename-btn primary';
    saveBtn.type = 'button';
    saveBtn.textContent = 'Save';

    buttons.appendChild(cancelBtn);
    buttons.appendChild(saveBtn);

    card.appendChild(title);
    card.appendChild(desc);
    card.appendChild(input);
    card.appendChild(hint);
    card.appendChild(buttons);
    overlay.appendChild(card);
    document.body.appendChild(overlay);

    // Pointer-based overlay close control (to avoid accidental close on drags)
    let pointerDownOnOverlay = false;
    let pointerUpOnOverlay = false;

    overlay.addEventListener('pointerdown', (e) => {
      pointerDownOnOverlay = (e.target === overlay);
      pointerUpOnOverlay = false;
    });
    overlay.addEventListener('pointerup', (e) => {
      pointerUpOnOverlay = (e.target === overlay);
    });
    overlay.addEventListener('pointercancel', () => {
      pointerDownOnOverlay = false;
      pointerUpOnOverlay = false;
    });

    overlay.addEventListener('mousedown', (e) => {
      pointerDownOnOverlay = (e.target === overlay);
      pointerUpOnOverlay = false;
    });
    overlay.addEventListener('mouseup', (e) => {
      pointerUpOnOverlay = (e.target === overlay);
    });

    overlay.addEventListener('click', (e) => {
      if (e.target === overlay && pointerDownOnOverlay && pointerUpOnOverlay) {
        close(false);
      }
      pointerDownOnOverlay = false;
      pointerUpOnOverlay = false;
    });

    // Stop clicks inside the card from bubbling to overlay
    card.addEventListener('click', (e) => { e.stopPropagation(); });

    // Keyboard handling
    function onKeyDown(e) {
      if (e.key === 'Escape') {
        e.preventDefault();
        close(false);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        commit();
      }
    }
    window.addEventListener('keydown', onKeyDown);

    cancelBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      close(false);
    });

    saveBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      commit();
    });

    // commit flow
    let closed = false;
    async function commit() {
      if (closed) return;
      const v = String(input.value || '').trim();
      if (!v) {
        // Don't allow empty names; simply focus the input and don't close.
        input.focus();
        return;
      }
      try {
        if (typeof onCommit === 'function') {
          // allow the caller to persist changes (may be async)
          await onCommit(v);
        }
      } catch (err) {
        // swallow errors from commit; caller will log if needed
      } finally {
        close(true, v);
      }
    }

    function close(committed = false, name) {
      if (closed) return;
      closed = true;
      try {
        if (document.body.contains(overlay)) document.body.removeChild(overlay);
      } catch (_) {}
      window.removeEventListener('keydown', onKeyDown);
      resolve(committed ? { committed: true, name } : { committed: false });
    }

    // Mutation observer to detect external removals
    const observer = new MutationObserver(() => {
      if (!document.body.contains(overlay)) {
        window.removeEventListener('keydown', onKeyDown);
        observer.disconnect();
        resolve({ committed: false });
      }
    });
    observer.observe(document.body, { childList: true });

    // initial focus
    setTimeout(() => {
      input.focus();
      input.select();
    }, 20);
  });
}

module.exports = { openRenameWindow };