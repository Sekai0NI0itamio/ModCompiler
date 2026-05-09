// src/main/functions/editor/fileTabOptions.js
// Shows a lightweight context menu for file tabs (right-click).
// Options: Copy File Path, Copy Relative Path, Reveal in Finder, GPT code applier, Wrap text On/Off.

function _createMenu() {
  const m = document.createElement('div');
  m.id = 'file-tab-options-menu';
  m.style.position = 'fixed';
  m.style.zIndex = 99999;
  m.style.minWidth = '220px';
  m.style.background = '#fff';
  m.style.boxShadow = '0 6px 18px rgba(0,0,0,0.12)';
  m.style.borderRadius = '6px';
  m.style.padding = '6px';
  m.style.fontFamily = 'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial';
  m.style.fontSize = '13px';
  m.style.color = '#111';
  m.style.display = 'none';
  m.style.cursor = 'default';

  m.addEventListener('contextmenu', (ev) => {
    ev.preventDefault();
    ev.stopPropagation();
  });

  document.body.appendChild(m);
  return m;
}

function _closeMenu(menu) {
  if (!menu) return;
  menu.style.display = 'none';
  menu.innerHTML = '';
  document.removeEventListener('click', _globalClickHandler);
}

function _globalClickHandler(e) {
  const menu = document.getElementById('file-tab-options-menu');
  if (!menu) return;
  if (!menu.contains(e.target)) {
    _closeMenu(menu);
  }
}

function showForTab(evt, tab) {
  if (!evt || !tab) return;
  evt.preventDefault();
  evt.stopPropagation();

  // Try to require revealInFinder, copyPath and relative helper (best-effort; fail gracefully)
  let revealInFinder = null;
  let copyPathFn = null;
  let copyRelativeFn = null;
  try {
    revealInFinder = require('../directory_viewer/buttons/revealInFinder').revealInFinder;
  } catch (e) {
    revealInFinder = null;
  }
  try {
    copyPathFn = require('../directory_viewer/buttons/copyPath').copyPath;
  } catch (e) {
    copyPathFn = null;
  }
  try {
    // relativePath module exports copyRelativePath(projectRoot, targetPath, x, y)
    copyRelativeFn = require('../directory_viewer/relativePath').copyRelativePath;
  } catch (e) {
    copyRelativeFn = null;
  }

  let menu = document.getElementById('file-tab-options-menu');
  if (!menu) menu = _createMenu();

  menu.innerHTML = "";

  // Helper create item
  function makeBtn(text, onClick) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = text;
    btn.style.display = 'block';
    btn.style.width = '100%';
    btn.style.padding = '8px 10px';
    btn.style.border = 'none';
    btn.style.background = 'transparent';
    btn.style.textAlign = 'left';
    btn.style.borderRadius = '4px';
    btn.style.cursor = 'pointer';
    btn.addEventListener('mouseover', () => { btn.style.background = '#f5f5f5'; });
    btn.addEventListener('mouseout', () => { btn.style.background = 'transparent'; });
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      try { onClick(); } catch (err) { console.error('Tab option action failed:', err); }
      _closeMenu(menu);
    });
    return btn;
  }

  // Copy file path
  const copyBtn = makeBtn('Copy File Path', () => {
    try {
      if (copyPathFn) {
        // Some implementations accept coordinates; best-effort: call with only path (no coords)
        try { copyPathFn(tab.path); return; } catch (e) { /* fallthrough to clipboard fallback */ }
      }
      if (navigator && navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(tab.path).catch((err) => {
          console.warn('navigator.clipboard failed:', err);
          // fallback to textarea copy
          try {
            const ta = document.createElement('textarea');
            ta.value = tab.path;
            ta.style.position = 'fixed';
            ta.style.opacity = '0';
            document.body.appendChild(ta);
            ta.select();
            document.execCommand('copy');
            document.body.removeChild(ta);
          } catch (err2) {
            console.error('Copy fallback failed:', err2);
          }
        });
      } else {
        // textarea fallback
        try {
          const ta = document.createElement('textarea');
          ta.value = tab.path;
          ta.style.position = 'fixed';
          ta.style.opacity = '0';
          document.body.appendChild(ta);
          ta.select();
          document.execCommand('copy');
          document.body.removeChild(ta);
        } catch (err) {
          console.error('Copy to clipboard failed:', err);
        }
      }
    } catch (err) {
      console.error('Copy file path error:', err);
    }
  });
  menu.appendChild(copyBtn);

  // Copy relative path
  const copyRelBtn = makeBtn('Copy Relative Path', () => {
    try {
      // Prefer the specialized helper that can compute relative to project root
      if (copyRelativeFn) {
        // We don't always have a projectRoot in the editor context; pass null so the helper falls back to process.cwd()
        try { copyRelativeFn(null, tab.path, evt.clientX, evt.clientY); return; } catch (e) { /* fallthrough */ }
      }

      // Fallback: compute relative to process.cwd() and copy (best-effort)
      let rel = tab.path;
      try {
        const path = require('path');
        rel = path.relative(process.cwd(), tab.path) || '.';
      } catch (e) {
        rel = tab.path;
      }

      // If we have copyPathFn we can use it to copy + show notification
      if (copyPathFn) {
        try { copyPathFn(rel, evt.clientX, evt.clientY); return; } catch (e) { /* fallthrough */ }
      }

      // navigator.clipboard fallback
      if (navigator && navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(rel).catch((err) => {
          console.warn('navigator.clipboard failed:', err);
          // fallback to textarea copy
          try {
            const ta = document.createElement('textarea');
            ta.value = rel;
            ta.style.position = 'fixed';
            ta.style.opacity = '0';
            document.body.appendChild(ta);
            ta.select();
            document.execCommand('copy');
            document.body.removeChild(ta);
          } catch (err2) {
            console.error('Copy fallback failed:', err2);
          }
        });
      } else {
        // textarea fallback
        try {
          const ta = document.createElement('textarea');
          ta.value = rel;
          ta.style.position = 'fixed';
          ta.style.opacity = '0';
          document.body.appendChild(ta);
          ta.select();
          document.execCommand('copy');
          document.body.removeChild(ta);
        } catch (err) {
          console.error('Copy relative path failed:', err);
        }
      }
    } catch (err) {
      console.error('Copy relative path error:', err);
    }
  });
  menu.appendChild(copyRelBtn);

  // Reveal in Finder (if available)
  const revealBtn = makeBtn('Reveal in Finder', () => {
    try {
      if (revealInFinder) {
        revealInFinder(tab.path);
      } else {
        alert('Reveal in Finder not available.');
      }
    } catch (err) {
      console.error('Reveal in Finder failed:', err);
    }
  });
  menu.appendChild(revealBtn);


  // Wrap text toggle
  const isWrapped = !!tab.wrap;
  const wrapBtn = makeBtn(`Wrap text: ${isWrapped ? 'On' : 'Off'}`, () => {
    try {
      const newWrap = !isWrapped;
      // Dispatch an event the tab manager listens to so we avoid circular requires.
      document.dispatchEvent(new CustomEvent('tab-wrap-toggled', {
        detail: { path: tab.path, id: tab.id, wrap: newWrap }
      }));
    } catch (err) {
      console.error('Failed to toggle wrap setting:', err);
    }
  });
  menu.appendChild(wrapBtn);

  // Position menu at mouse coordinates and ensure on-screen
  const pad = 8;
  let left = evt.clientX;
  let top = evt.clientY;
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  menu.style.display = 'block';
  menu.style.visibility = 'hidden'; // show hidden to measure size
  menu.style.left = '0px';
  menu.style.top = '0px';
  menu.style.maxWidth = (vw - 32) + 'px';

  // give the browser a tick to render and compute sizes
  requestAnimationFrame(() => {
    const rect = menu.getBoundingClientRect();
    if (left + rect.width > vw - pad) left = Math.max(pad, vw - rect.width - pad);
    if (top + rect.height > vh - pad) top = Math.max(pad, vh - rect.height - pad);
    menu.style.left = `${Math.round(left)}px`;
    menu.style.top = `${Math.round(top)}px`;
    menu.style.visibility = '';
  });

  // close when clicking outside
  document.addEventListener('click', _globalClickHandler);
}

function initFileTabOptions() {
  // Ensure the menu element exists (optional)
  if (!document.getElementById('file-tab-options-menu')) _createMenu();
}

module.exports = {
  showForTab,
  initFileTabOptions,
};