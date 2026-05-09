(function () {
  function init() {
    try { console.log('[checkpoint.js] init() called'); } catch (e) {}

    const ipcGet = (function () {
      try {
        if (typeof require === 'function') {
          return require('electron').ipcRenderer;
        }
      } catch (e) {
        // ignore
      }
      if (typeof window !== 'undefined' && window.ipcRenderer) {
        return window.ipcRenderer;
      }
      return null;
    })();

    if (!ipcGet) {
      console.warn('[checkpoint.js] ipcRenderer not available; settings may be read-only in this context.');
    }

    const modeGroup = document.getElementById('checkpoint-mode-group');
    const radios = Array.from(document.querySelectorAll('input[name="checkpoint-mode"]'));
    const saveBtn = document.getElementById('save-checkpoint-settings');
    const resetBtn = document.getElementById('reset-checkpoint-settings');
    const banner = document.getElementById('settings-banner');
    const bannerText = document.getElementById('settings-banner-text');
    const saveStatus = document.getElementById('save-status');
    const saveError = document.getElementById('save-error');

    if (!modeGroup || !radios.length || !saveBtn || !resetBtn) {
      console.warn('[checkpoint.js] One or more DOM elements not found. Aborting init.');
      return;
    }

    let lastLoaded = null;

    function normalizeMode(value) {
      if (!value || typeof value !== 'string') return 'lite';
      const v = value.toLowerCase();
      return (v === 'lite' || v === 'git') ? v : 'lite';
    }

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
      markFieldUnsaved(modeGroup, false);
    }

    function getSelectedMode() {
      const checked = radios.find(r => r.checked);
      return normalizeMode(checked ? checked.value : 'lite');
    }

    function setSelectedMode(mode) {
      const value = normalizeMode(mode);
      radios.forEach(r => { r.checked = (r.value === value); });
    }

    function configsEqual(a, b) {
      return a && b && a.checkpointMode === b.checkpointMode;
    }

    function evaluateUnsaved() {
      try {
        if (!lastLoaded) {
          clearUnsaved();
          return;
        }
        const cur = { checkpointMode: getSelectedMode() };
        markFieldUnsaved(modeGroup, !configsEqual(lastLoaded, cur));
      } catch (e) {
        console.warn('evaluateUnsaved error:', e);
      }
    }

    async function loadAndPopulate() {
      try {
        if (!ipcGet) {
          const effective = { checkpointMode: 'lite' };
          setSelectedMode(effective.checkpointMode);
          lastLoaded = effective;
          clearUnsaved();
          return;
        }

        const cfg = await ipcGet.invoke('get-config');
        const mode = normalizeMode(cfg && cfg.checkpointMode);
        const effective = { checkpointMode: mode };
        setSelectedMode(effective.checkpointMode);
        lastLoaded = effective;
        clearUnsaved();
      } catch (err) {
        console.error('[checkpoint.js] loadAndPopulate error:', err);
        const effective = { checkpointMode: 'lite' };
        setSelectedMode(effective.checkpointMode);
        lastLoaded = effective;
        clearUnsaved();
      }
    }

    loadAndPopulate().catch(e => console.error('[checkpoint.js] initial load failed', e));

    if (ipcGet) {
      ipcGet.on && ipcGet.on('config-loaded', (event, cfg) => {
        try {
          const effective = { checkpointMode: normalizeMode(cfg && cfg.checkpointMode) };
          setSelectedMode(effective.checkpointMode);
          lastLoaded = effective;
          clearUnsaved();
        } catch (e) {
          console.error('[checkpoint.js] error applying broadcasted config', e);
        }
      });
    }

    radios.forEach(r => r.addEventListener('change', evaluateUnsaved));

    saveBtn.addEventListener('click', async () => {
      animateButtonPress(saveBtn);
      try {
        const mode = getSelectedMode();
        if (saveStatus) { saveStatus.style.display = 'inline'; saveStatus.textContent = 'Saving...'; }
        if (saveError) { saveError.style.display = 'none'; saveError.textContent = ''; }

        if (!ipcGet) {
          lastLoaded = { checkpointMode: mode };
          clearUnsaved();
          if (saveStatus) { saveStatus.textContent = 'Saved'; setTimeout(() => { saveStatus.style.display = 'none'; }, 900); }
          showBanner('Settings updated (local only)', 'success', 2400);
          return;
        }

        const updated = await ipcGet.invoke('update-config', { checkpointMode: mode });
        lastLoaded = { checkpointMode: normalizeMode(updated && updated.checkpointMode) };
        clearUnsaved();

        if (saveStatus) { saveStatus.textContent = 'Saved'; setTimeout(() => { saveStatus.style.display = 'none'; }, 900); }
        showBanner('Settings saved', 'success', 2800);
      } catch (e) {
        console.error('[checkpoint.js] save handler error', e);
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
          console.error('[checkpoint.js] Failed to obtain defaults:', defaults && defaults.error ? defaults.error : defaults);
          showBanner('Failed to load defaults', 'error', 3000);
          return;
        }

        await ipcGet.invoke('set-config', defaults);
        await loadAndPopulate();
        showBanner('Settings reset to defaults', 'info', 2400);
      } catch (e) {
        console.error('[checkpoint.js] reset handler error', e);
        showBanner('Failed to reset settings', 'error', 3600);
      }
    });

    [saveBtn, resetBtn].forEach((btn) => {
      btn.addEventListener('keyup', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          btn.click();
        }
      });
    });
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { init };
  }
  if (typeof window !== 'undefined') {
    try {
      window.initCheckpointSettings = init;
    } catch (e) {
      // ignore
    }
  }
})();
