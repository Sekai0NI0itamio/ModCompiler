(function () {
  function init() {
    try { console.log('[hidden-files.js] init() called'); } catch (e) {}

    // Use require when available; otherwise attempt to use window.ipcRenderer (if a preload exposed it)
    const ipcGet = (function () {
      try {
        if (typeof require === 'function') {
          return require('electron').ipcRenderer;
        }
      } catch (e) {
        // ignore
      }
      // fallback to global if provided by preload
      if (typeof window !== 'undefined' && window.ipcRenderer) {
        return window.ipcRenderer;
      }
      return null;
    })();

    if (!ipcGet) {
      console.warn('[hidden-files.js] ipcRenderer not available; settings may be read-only in this context.');
    }

    const listTa = document.getElementById('hidden-files-list');
    const saveBtn = document.getElementById('save-hidden-files');
    const resetBtn = document.getElementById('reset-hidden-files');
    const banner = document.getElementById('settings-banner');
    const bannerText = document.getElementById('settings-banner-text');
    const saveStatus = document.getElementById('save-status');
    const saveError = document.getElementById('save-error');

    if (!listTa || !saveBtn || !resetBtn) {
      console.warn('[hidden-files.js] One or more DOM elements not found. Aborting init.');
      return;
    }

    let lastLoaded = null;

    function showBanner(message, type = 'info', durationMs = 3000) {
      try {
        if (!banner || !bannerText) return;
        bannerText.textContent = message || '';
        banner.classList.remove('success', 'info', 'error');
        banner.classList.add(type || 'info');
        banner.hidden = false;
        void banner.offsetWidth;
        banner.classList.add('visible');
        if (banner._hideTimer) clearTimeout(banner._hideTimer);
        banner._hideTimer = setTimeout(() => {
          banner.classList.remove('visible');
          setTimeout(() => { banner.hidden = true; }, 280);
        }, durationMs);
      } catch (e) {
        console.warn('showBanner failed:', e);
      }
    }

    function animateButtonPress(btn, pressMs = 220) {
      try {
        if (!btn) return;
        btn.classList.add('pressed');
        setTimeout(() => btn.classList.remove('pressed'), pressMs);
      } catch (e) {}
    }

    function markFieldUnsaved(el, unsaved) {
      try {
        if (!el) return;
        if (unsaved) el.classList.add('unsaved-input');
        else el.classList.remove('unsaved-input');
      } catch (e) {}
    }

    function clearUnsaved() {
      markFieldUnsaved(listTa, false);
    }

    function currentValues() {
      const files = listTa.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
      return { hiddenFiles: files };
    }

    function configsEqual(a, b) {
      if (!a || !b) return false;
      const am = Array.isArray(a.hiddenFiles) ? a.hiddenFiles : [];
      const bm = Array.isArray(b.hiddenFiles) ? b.hiddenFiles : [];
      if (am.length !== bm.length) return false;
      for (let i = 0; i < am.length; i++) if (am[i] !== bm[i]) return false;
      return true;
    }

    function evaluateUnsaved() {
      try {
        if (!lastLoaded) {
          clearUnsaved();
          return;
        }
        const cur = currentValues();
        markFieldUnsaved(listTa, !configsEqual({ hiddenFiles: lastLoaded.hiddenFiles || [] }, cur));
      } catch (e) {
        console.warn('evaluateUnsaved error:', e);
      }
    }

    async function loadAndPopulate() {
      try {
        if (!ipcGet) {
          // No ipc: default fallback
          const effective = { hiddenFiles: ['.DS_Store'] };
          listTa.value = effective.hiddenFiles.join('\n');
          lastLoaded = effective;
          clearUnsaved();
          return;
        }

        const cfg = await ipcGet.invoke('get-config');
        let effective = null;
        if (!cfg || cfg.error) {
          effective = { hiddenFiles: ['.DS_Store'] };
          listTa.value = effective.hiddenFiles.join('\n');
        } else {
          effective = { hiddenFiles: Array.isArray(cfg.hiddenFiles) ? cfg.hiddenFiles.slice() : ['.DS_Store'] };
          listTa.value = effective.hiddenFiles.join('\n');
        }
        lastLoaded = effective;
        clearUnsaved();
      } catch (err) {
        console.error('[hidden-files.js] loadAndPopulate error:', err);
        const effective = { hiddenFiles: ['.DS_Store'] };
        listTa.value = effective.hiddenFiles.join('\n');
        lastLoaded = effective;
        clearUnsaved();
      }
    }

    loadAndPopulate().catch(e => console.error('[hidden-files.js] initial load failed', e));

    // Listen for config updates broadcast from main
    if (ipcGet) {
      ipcGet.on && ipcGet.on('config-loaded', (event, cfg) => {
        try {
          const effective = { hiddenFiles: Array.isArray(cfg && cfg.hiddenFiles) ? cfg.hiddenFiles.slice() : ['.DS_Store'] };
          listTa.value = effective.hiddenFiles.join('\n');
          lastLoaded = effective;
          clearUnsaved();
        } catch (e) {
          console.error('[hidden-files.js] error applying broadcasted config', e);
        }
      });
    }

    listTa.addEventListener('input', evaluateUnsaved);

    saveBtn.addEventListener('click', async () => {
      animateButtonPress(saveBtn);
      try {
        const files = listTa.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
        if (saveStatus) { saveStatus.style.display = 'inline'; saveStatus.textContent = 'Saving...'; }
        if (saveError) { saveError.style.display = 'none'; saveError.textContent = ''; }

        if (!ipcGet) {
          // no ipc: we cannot persist, but update lastLoaded so fields appear saved in UI
          lastLoaded = { hiddenFiles: files };
          clearUnsaved();
          if (saveStatus) { saveStatus.textContent = 'Saved'; setTimeout(() => { saveStatus.style.display = 'none'; }, 900); }
          showBanner('Settings updated (local only)', 'success', 2400);
          return;
        }

        const updated = await ipcGet.invoke('update-config', { hiddenFiles: files });

        lastLoaded = {
          hiddenFiles: Array.isArray(updated.hiddenFiles) ? updated.hiddenFiles.slice() : files
        };
        clearUnsaved();

        if (saveStatus) { saveStatus.textContent = 'Saved'; setTimeout(() => { saveStatus.style.display = 'none'; }, 900); }
        showBanner('Settings saved', 'success', 2800);
        console.log('[hidden-files.js] update-config invoked');
      } catch (e) {
        console.error('[hidden-files.js] save handler error', e);
        if (saveStatus) saveStatus.style.display = 'none';
        if (saveError) { saveError.style.display = 'inline'; saveError.textContent = String(e && e.message ? e.message : e); }
        showBanner('Failed to save settings', 'error', 4000);
      }
    });

    resetBtn.addEventListener('click', async () => {
      animateButtonPress(resetBtn);
      try {
        if (!ipcGet) {
          showBanner('Cannot reset without IPC available', 'error', 2400);
          return;
        }
        const defaults = await ipcGet.invoke('get-default-config');
        if (!defaults || defaults.error) {
          console.error('[hidden-files.js] Failed to obtain defaults:', defaults && defaults.error ? defaults.error : defaults);
          showBanner('Failed to load defaults', 'error', 3000);
          return;
        }

        await ipcGet.invoke('set-config', defaults);
        await loadAndPopulate();
        showBanner('Settings reset to defaults', 'info', 2400);
      } catch (e) {
        console.error('[hidden-files.js] reset handler error', e);
        showBanner('Failed to reset settings', 'error', 3600);
      }
    });

    // Accessibility: Enter/Space to activate
    [saveBtn, resetBtn].forEach((btn) => {
      btn.addEventListener('keyup', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          btn.click();
        }
      });
    });
  }

  // Expose initializer for both require() and script-tag fallback.
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { init };
  }
  if (typeof window !== 'undefined') {
    // settings/init.js expects a global function name constructed from the category name.
    // For "hidden-files" that becomes "initHidden-filesSettings" (string). We cannot declare an identifier
    // with a hyphen, so we set the property using bracket notation so the loader can call it.
    try {
      window['initHidden-filesSettings'] = init;
    } catch (e) {
      // ignore
    }
  }
})();