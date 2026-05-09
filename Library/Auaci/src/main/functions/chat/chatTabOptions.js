// src/main/functions/chat/chatTabOptions.js
// Lightweight context menu for chat tabs. Self-contained CSS injected into <head>.
// Emits window events:
//   - 'chat-tab-action' with detail { action: 'rename'|'delete', sessionId }

const CONTEXT_STYLE_ID = 'auaci-chat-tab-context-styles';
const MENU_ID = 'auaci-chat-tab-contextmenu';

function injectStyles() {
  if (document.getElementById(CONTEXT_STYLE_ID)) return;
  const css = `
    /* Context menu for chat tabs (self-contained) */
    #${MENU_ID} {
      position: fixed;
      z-index: 2147483647;
      min-width: 160px;
      background: #ffffff;
      border: 1px solid #dfe3e6;
      box-shadow: 0 6px 18px rgba(0,0,0,0.08);
      border-radius: 6px;
      font-family: "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      color: #111827;
      padding: 6px 0;
      user-select: none;
    }
    #${MENU_ID} .ct-item {
      padding: 8px 14px;
      font-size: 13px;
      cursor: pointer;
      white-space: nowrap;
    }
    #${MENU_ID} .ct-item:hover {
      background: #f3f4f6;
    }
    #${MENU_ID} .ct-item.danger {
      color: #b91c1c;
    }
  `;
  const s = document.createElement('style');
  s.id = CONTEXT_STYLE_ID;
  s.textContent = css;
  document.head.appendChild(s);
}

function hideMenu() {
  const existing = document.getElementById(MENU_ID);
  if (existing) existing.remove();
  window.removeEventListener('mousedown', onWindowMouse);
  window.removeEventListener('keydown', onKeyDown);
}

function onWindowMouse(e) {
  const menu = document.getElementById(MENU_ID);
  if (!menu) return;
  if (!menu.contains(e.target)) hideMenu();
}

function onKeyDown(e) {
  if (e.key === 'Escape') hideMenu();
}

/**
 * showMenu(x, y, sessionId)
 * Positions an in-page context menu at (x, y) and wires actions.
 */
function showMenu(x, y, sessionId) {
  injectStyles();
  hideMenu();

  const menu = document.createElement('div');
  menu.id = MENU_ID;

  const rename = document.createElement('div');
  rename.className = 'ct-item';
  rename.textContent = 'Rename';
  rename.addEventListener('click', (ev) => {
    ev.stopPropagation();
    hideMenu();
    window.dispatchEvent(new CustomEvent('chat-tab-action', { detail: { action: 'rename', sessionId } }));
  });

  // Multi-View option
  let isMultiViewed = false;
  try {
    const { isInMultiView } = require('./ui/multiView');
    isMultiViewed = isInMultiView(sessionId);
  } catch (_) {}

  const multiView = document.createElement('div');
  multiView.className = 'ct-item';
  multiView.textContent = isMultiViewed ? 'Close Multi-View' : 'Multi-View';
  multiView.addEventListener('click', (ev) => {
    ev.stopPropagation();
    hideMenu();
    try {
      const { addToMultiView, removeFromMultiView } = require('./ui/multiView');
      if (isMultiViewed) {
        removeFromMultiView(sessionId);
      } else {
        addToMultiView(sessionId);
      }
    } catch (e) {
      console.error('[chatTabOptions] Multi-view action failed:', e);
    }
  });

  const del = document.createElement('div');
  del.className = 'ct-item danger';
  del.textContent = 'Delete';
  del.addEventListener('click', (ev) => {
    ev.stopPropagation();
    hideMenu();
    window.dispatchEvent(new CustomEvent('chat-tab-action', { detail: { action: 'delete', sessionId } }));
  });

  menu.appendChild(rename);
  menu.appendChild(multiView);
  menu.appendChild(del);

  // Dispatch event for extensions to add more items
  try {
    window.dispatchEvent(new CustomEvent('chat-tab-contextmenu-extend', { detail: { menu, sessionId } }));
  } catch (_) {}

  document.body.appendChild(menu);

  // Basic position clamping to viewport
  const vw = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
  const vh = Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0);
  const rect = menu.getBoundingClientRect();
  let left = x;
  let top = y;
  if (left + rect.width > vw) left = Math.max(8, vw - rect.width - 8);
  if (top + rect.height > vh) top = Math.max(8, vh - rect.height - 8);

  menu.style.left = `${left}px`;
  menu.style.top = `${top}px`;

  // Dismiss handlers
  window.addEventListener('mousedown', onWindowMouse);
  window.addEventListener('keydown', onKeyDown);
}

module.exports = { showMenu, hideMenu };