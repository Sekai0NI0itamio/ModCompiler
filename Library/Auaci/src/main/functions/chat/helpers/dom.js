// src/main/functions/chat/helpers/dom.js
function escapeHTML(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function setupCodeBlockListeners() {
  document.querySelectorAll('.copy-button').forEach(btn => {
    const clone = btn.cloneNode(true);
    btn.parentNode.replaceChild(clone, btn);
  });

  document.querySelectorAll('.copy-button').forEach(btn => {
    btn.addEventListener('click', handleCopyClick);
  });

  // Also install link click handlers (idempotent)
  setupLinkMenuListeners();
}

const LINK_MENU_STYLE_ID = 'auaci-link-menu-styles';
const LINK_MENU_ID = 'auaci-link-menu';

function injectLinkMenuStyles() {
  if (document.getElementById(LINK_MENU_STYLE_ID)) return;
  const css = `
    #${LINK_MENU_ID} {
      position: fixed;
      z-index: 2147483647;
      min-width: 200px;
      background: #ffffff;
      border: 1px solid #dfe3e6;
      box-shadow: 0 8px 22px rgba(0,0,0,0.10);
      border-radius: 8px;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      color: #111827;
      padding: 6px 0;
      user-select: none;
    }
    #${LINK_MENU_ID} .li-item {
      padding: 9px 14px;
      font-size: 13px;
      cursor: pointer;
      white-space: nowrap;
    }
    #${LINK_MENU_ID} .li-item:hover {
      background: #f3f4f6;
    }
    #${LINK_MENU_ID} .li-url {
      padding: 8px 14px;
      font-size: 12px;
      color: #6b7280;
      border-bottom: 1px solid #eef0f2;
      max-width: 320px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  `;
  const s = document.createElement('style');
  s.id = LINK_MENU_STYLE_ID;
  s.textContent = css;
  document.head.appendChild(s);
}

function hideLinkMenu() {
  const m = document.getElementById(LINK_MENU_ID);
  if (m) m.remove();
  window.removeEventListener('mousedown', onLinkMenuWindowMouse, true);
  window.removeEventListener('keydown', onLinkMenuKeyDown, true);
}

function onLinkMenuWindowMouse(e) {
  const m = document.getElementById(LINK_MENU_ID);
  if (!m) return;
  if (!m.contains(e.target)) hideLinkMenu();
}

function onLinkMenuKeyDown(e) {
  if (e.key === 'Escape') hideLinkMenu();
}

async function copyText(text) {
  if (typeof text !== 'string') text = String(text ?? '');
  try {
    await navigator.clipboard.writeText(text);
    return;
  } catch (_) {
    // Fallback to Electron clipboard if available
    try {
      const { clipboard } = require('electron');
      clipboard.writeText(text);
      return;
    } catch (_) {}

    // Final fallback: execCommand
    try {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.left = '-1000px';
      ta.style.top = '-1000px';
      document.body.appendChild(ta);
      ta.focus();
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    } catch (_) {}
  }
}

function safeHref(href) {
  const h = String(href || '').trim();
  if (!h) return null;
  // Never allow javascript: URLs
  if (/^\s*javascript:/i.test(h)) return null;
  return h;
}

function showLinkMenu(x, y, href) {
  injectLinkMenuStyles();
  hideLinkMenu();

  const safe = safeHref(href);
  if (!safe) return;

  const menu = document.createElement('div');
  menu.id = LINK_MENU_ID;

  const urlRow = document.createElement('div');
  urlRow.className = 'li-url';
  urlRow.textContent = safe;
  menu.appendChild(urlRow);

  function addItem(label, onClick) {
    const el = document.createElement('div');
    el.className = 'li-item';
    el.textContent = label;
    el.addEventListener('click', async (ev) => {
      ev.preventDefault();
      ev.stopPropagation();
      try { await onClick(); } catch (_) {}
      hideLinkMenu();
    });
    menu.appendChild(el);
  }

  addItem('Copy link address', async () => {
    await copyText(safe);
  });

  addItem('Open in default browser', async () => {
    try {
      const { shell } = require('electron');
      await shell.openExternal(safe);
    } catch (_) {
      // As a fallback, attempt window.open (may be blocked in Electron)
      try { window.open(safe, '_blank', 'noopener'); } catch (_) {}
    }
  });

  document.body.appendChild(menu);

  // Position with viewport clamping
  const vw = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
  const vh = Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0);
  const rect = menu.getBoundingClientRect();
  let left = x;
  let top = y;
  if (left + rect.width > vw) left = Math.max(8, vw - rect.width - 8);
  if (top + rect.height > vh) top = Math.max(8, vh - rect.height - 8);
  menu.style.left = `${left}px`;
  menu.style.top = `${top}px`;

  window.addEventListener('mousedown', onLinkMenuWindowMouse, true);
  window.addEventListener('keydown', onLinkMenuKeyDown, true);
}

function setupLinkMenuListeners() {
  if (window.__auaciLinkMenuAttached) return;
  window.__auaciLinkMenuAttached = true;

  // Use event delegation so we don't have to rebind for every render.
  const container = document.getElementById('chat-messages');
  if (!container) return;

  container.addEventListener('click', (e) => {
    // Only handle left-clicks.
    if (typeof e.button === 'number' && e.button !== 0) return;

    const target = e.target;
    const link = target && typeof target.closest === 'function' ? target.closest('a[href]') : null;
    if (!link) return;

    // Do not hijack clicks inside the input box.
    try { if (link.closest && link.closest('#user-input')) return; } catch (_) {}

    // Show our menu instead of navigating.
    e.preventDefault();
    e.stopPropagation();

    const href = link.getAttribute('href') || link.href || '';
    showLinkMenu(e.clientX, e.clientY, href);
  }, true);
}

function handleCopyClick(e) {
  e.preventDefault();
  const btn = e.currentTarget;
  const code = decodeURIComponent(btn.getAttribute('data-code'));

  navigator.clipboard.writeText(code).then(() => {
    btn.classList.add('copied');
    setTimeout(() => btn.classList.remove('copied'), 2000);
  }).catch(() => {
    const ta = document.createElement('textarea');
    ta.value = code;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    btn.classList.add('copied');
    setTimeout(() => btn.classList.remove('copied'), 2000);
  });
}

module.exports = { escapeHTML, setupCodeBlockListeners };