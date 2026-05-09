// src/main/functions/titlebar/init.js
// Injects a custom titlebar (whitish-gray) into the renderer, wires buttons to main,
// and keeps macOS native traffic-light behavior when available.

const { ipcRenderer } = require('electron');

function normalizeTitle(appTitle, fallback = 'Auaci') {
  if (appTitle == null) return fallback;
  if (typeof appTitle === 'string') return appTitle;
  if (typeof appTitle === 'object') {
    if (typeof appTitle.title === 'string' && appTitle.title.trim() !== '') return appTitle.title;
    if (typeof appTitle.name === 'string' && appTitle.name.trim() !== '') return appTitle.name;
    try {
      const s = appTitle.toString();
      if (s && s !== '[object Object]') return s;
    } catch (e) {}
    try {
      return JSON.stringify(appTitle);
    } catch (e) {
      return fallback;
    }
  }
  try {
    return String(appTitle);
  } catch (e) {
    return fallback;
  }
}

function escapeHtml(s = '') {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function createTitlebarDOM(appTitle = 'Auaci') {
  if (document.getElementById('app-titlebar')) return;

  const isMac = process.platform === 'darwin';
  const safeTitle = escapeHtml(normalizeTitle(appTitle, 'Auaci'));

  const bar = document.createElement('div');
  bar.id = 'app-titlebar';
  bar.className = 'custom-titlebar';
  bar.setAttribute('role', 'toolbar');
  bar.innerHTML = `
    <div class="titlebar-left">
      <div class="traffic-spacer" aria-hidden="true"></div>
      <div class="app-title">${safeTitle}</div>
    </div>
    <div class="titlebar-right" aria-hidden="${isMac ? 'true' : 'false'}">
      <div class="window-controls">
        <button class="tb-btn tb-minimize" title="Minimize" aria-label="Minimize">▁</button>
        <button class="tb-btn tb-maximize" title="Maximize" aria-label="Maximize">▢</button>
        <button class="tb-btn tb-close" title="Close" aria-label="Close">✕</button>
      </div>
      <!-- settings button or other actions will be appended here by the buttons module -->
    </div>
  `;

  document.body.prepend(bar);
}

function injectTitlebarStyles() {
  if (document.querySelector('style[data-generated-by="custom-titlebar"]')) return;

  const css = `
    /* Custom app titlebar */
    .custom-titlebar {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      height: 36px;
      background: #f5f6f7;
      color: #111827;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 10px;
      box-sizing: border-box;
      z-index: 999999;
      -webkit-user-select: none;
      -webkit-app-region: drag;
      border-bottom: 1px solid rgba(0,0,0,0.06);
      backdrop-filter: blur(2px);
    }
    .custom-titlebar .titlebar-left {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .custom-titlebar .traffic-spacer {
      width: 84px;
      height: 100%;
      display: inline-block;
    }
    .custom-titlebar .app-title {
      font-size: 13px;
      font-weight: 600;
      color: #111827;
      line-height: 36px;
      -webkit-app-region: drag;
    }
    .custom-titlebar .titlebar-right {
      display: flex;
      gap: 8px;
      align-items: center;
      margin-left: auto;
    }

    /* window control group (min/max/close) */
    .custom-titlebar .window-controls {
      display: flex;
      gap: 6px;
      align-items: center;
    }

    .custom-titlebar .tb-btn {
      -webkit-app-region: no-drag;
      background: transparent;
      border: none;
      color: #111827;
      padding: 6px 8px;
      border-radius: 6px;
      cursor: pointer;
      font-size: 12px;
      line-height: 12px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .custom-titlebar .tb-btn:focus { outline: none; box-shadow: 0 0 0 2px rgba(59,130,246,0.12); }
    .custom-titlebar .tb-minimize:hover { background: rgba(0,0,0,0.04); }
    .custom-titlebar .tb-maximize:hover { background: rgba(0,0,0,0.04); }
    .custom-titlebar .tb-close:hover { background: rgba(239,68,68,0.12); color: #ef4444; }

    /* Settings / Backup button specific styling (both share the same style) */
    .custom-titlebar .tb-settings,
    .custom-titlebar .tb-backup {
      -webkit-app-region: no-drag;
      width: 34px;
      height: 34px;
      padding: 4px;
      border-radius: 6px;
      background: transparent;
      border: none;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }
    .custom-titlebar .tb-settings img,
    .custom-titlebar .tb-backup img {
      width: 18px;
      height: 18px;
      display: block;
    }
    .custom-titlebar .tb-settings:focus,
    .custom-titlebar .tb-backup:focus { outline: none; box-shadow: 0 0 0 2px rgba(59,130,246,0.12); }
    .custom-titlebar .tb-settings:hover,
    .custom-titlebar .tb-backup:hover { background: rgba(0,0,0,0.04); }

    /* Ensure content is not covered by the fixed titlebar */
    body { padding-top: 36px !important; }

    /* On macOS, hide only the window control buttons (we keep settings visible) */
    :root[data-platform="darwin"] .custom-titlebar .window-controls { display: none; }
    :root:not([data-platform="darwin"]) .custom-titlebar .traffic-spacer { width: 12px; }
  `;

  const style = document.createElement('style');
  style.setAttribute('data-generated-by', 'custom-titlebar');
  style.appendChild(document.createTextNode(css));
  document.head.appendChild(style);
}

function wireTitlebarButtons() {
  const bar = document.getElementById('app-titlebar');
  if (!bar) return;

  const btnMin = bar.querySelector('.tb-minimize');
  const btnMax = bar.querySelector('.tb-maximize');
  const btnClose = bar.querySelector('.tb-close');

  const sendControl = (action) => {
    try {
      ipcRenderer.send('window-control', action);
    } catch (err) {}
  };

  if (btnMin) btnMin.addEventListener('click', (e) => { e.stopPropagation(); sendControl('minimize'); });
  if (btnMax) btnMax.addEventListener('click', (e) => { e.stopPropagation(); sendControl('toggle-maximize'); });
  if (btnClose) btnClose.addEventListener('click', (e) => { e.stopPropagation(); sendControl('close'); });

  bar.addEventListener('dblclick', (e) => {
    e.stopPropagation();
    sendControl('toggle-maximize');
  });

  ipcRenderer.on('window-is-maximized', () => {
    if (btnMax) btnMax.textContent = '❐';
  });
  ipcRenderer.on('window-is-unmaximized', () => {
    if (btnMax) btnMax.textContent = '▢';
  });
}

/**
 * Initialize the custom titlebar.
 */
function initTitleBar(appTitle = 'Auaci') {
  const start = () => {
    try {
      const isMac = process.platform === 'darwin';
      document.documentElement.setAttribute('data-platform', isMac ? 'darwin' : 'not-darwin');

      injectTitlebarStyles();
      createTitlebarDOM(appTitle);
      wireTitlebarButtons();

      // Load and initialize extra titlebar buttons (settings etc.)
      try {
        const { initTitlebarButtons } = require('./buttons');
        if (typeof initTitlebarButtons === 'function') initTitlebarButtons();
      } catch (e) {
        // ignore if buttons module missing
        // console.warn('Titlebar buttons module not available:', e);
      }
    } catch (err) {
      console.error('initTitleBar error:', err && err.message ? err.message : err);
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, { once: true });
  } else {
    start();
  }
}

module.exports = { initTitleBar };