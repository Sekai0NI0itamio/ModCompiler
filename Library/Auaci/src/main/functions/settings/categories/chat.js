// src/main/functions/settings/categories/chat.js
(function () {
  function init() {
    // Very obvious startup log so we can find it in DevTools console
    try { console.log('[chat.js] init() called'); } catch (e) {}

    const { ipcRenderer } = require('electron');
    try { console.log('[chat.js] require ipcRenderer OK'); } catch (e) {}

    const modelsTa = document.getElementById('models-list');
    const defaultSel = document.getElementById('default-model');
    const promptSel = document.getElementById('default-prompt');
    const gptSystemLogicSel = document.getElementById('gpt-system-logic');
    const apiPortInput = document.getElementById('api-port');

    const saveBtn = document.getElementById('save-chat-settings');
    const resetBtn = document.getElementById('reset-chat-settings');
    const banner = document.getElementById('settings-banner');
    const bannerText = document.getElementById('settings-banner-text');
    const saveStatus = document.getElementById('save-status');
    const saveError = document.getElementById('save-error');
    const defaultVerbositySel = document.getElementById('default-verbosity');
    const defaultTempInput = document.getElementById('default-temperature');
    const defaultReasoningSel = document.getElementById('default-reasoning-effort');

    // Excluded folders elements
    const excludedFoldersTa = document.getElementById('excluded-folders-list');
    const saveExcludedBtn = document.getElementById('save-excluded-folders');
    const resetExcludedBtn = document.getElementById('reset-excluded-folders');
    const excludedCountEl = document.getElementById('excluded-folders-count');

    if (!modelsTa || !defaultSel || !promptSel || !gptSystemLogicSel || !apiPortInput || !defaultVerbositySel || !defaultTempInput || !defaultReasoningSel) {
      console.warn('[chat.js] One or more DOM elements not found. Aborting init.');
      return;
    }

    // Keep last-loaded config so we can detect unsaved edits.
    let lastLoadedConfig = null;

    function populateDefaultSelect(models, defaultModel) {
      // populate primary default
      defaultSel.innerHTML = '';
      if (!Array.isArray(models) || models.length === 0) {
        const opt = document.createElement('option');
        opt.value = '';
        opt.textContent = '-- no models defined --';
        defaultSel.appendChild(opt);
        defaultSel.disabled = true;
      } else {
        for (const m of models) {
          const opt = document.createElement('option');
          opt.value = m;
          opt.textContent = m;
          defaultSel.appendChild(opt);
        }
        defaultSel.disabled = false;
        if (defaultModel && models.includes(defaultModel)) defaultSel.value = defaultModel;
        else defaultSel.value = models[0];
      }
    }

    // Reusable function to show a temporary banner message at bottom
    function showBanner(message, type = 'info', durationMs = 3000) {
      try {
        if (!banner || !bannerText) return;
        bannerText.textContent = message || '';
        banner.classList.remove('success', 'info', 'error');
        banner.classList.add(type || 'info');
        banner.hidden = false;
        // Force reflow for animation restart
        void banner.offsetWidth;
        banner.classList.add('visible');

        // Remove previous timer if any
        if (banner._hideTimer) clearTimeout(banner._hideTimer);
        banner._hideTimer = setTimeout(() => {
          banner.classList.remove('visible');
          // hide after transition
          setTimeout(() => { banner.hidden = true; }, 280);
        }, durationMs);
      } catch (e) {
        console.warn('showBanner failed:', e);
      }
    }

    // Small utility to animate a button press visually
    function animateButtonPress(btn, pressMs = 220) {
      try {
        if (!btn) return;
        btn.classList.add('pressed');
        setTimeout(() => btn.classList.remove('pressed'), pressMs);
      } catch (e) {
        // ignore
      }
    }

    // Utility helpers to mark clean/unsaved states on fields
    function markFieldUnsaved(el, unsaved) {
      try {
        if (!el) return;
        if (unsaved) el.classList.add('unsaved-input');
        else el.classList.remove('unsaved-input');
      } catch (e) {}
    }

    function clearAllUnsavedMarks() {
      [
        modelsTa,
        defaultSel,
        promptSel,
        gptSystemLogicSel,
        apiPortInput,
        defaultVerbositySel,
        defaultTempInput,
        defaultReasoningSel,
      ].forEach(el => el && el.classList.remove('unsaved-input'));
    }

    function currentFormValues() {
      const models = modelsTa.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
      const defaultModel = defaultSel.value || '';
      const defaultPrompt = promptSel.value || 'Auaci';
      const apiPort = apiPortInput.value ? parseInt(apiPortInput.value, 10) : null;
      const apiProvider = 'local';
      const gptSystemLogic = gptSystemLogicSel ? (gptSystemLogicSel.value || 'tool-openai') : 'tool-openai';
      const defaultVerbosity = defaultVerbositySel ? (defaultVerbositySel.value || 'high') : 'high';
      let defaultTemperature = defaultTempInput && defaultTempInput.value !== '' ? parseFloat(defaultTempInput.value) : 0;
      if (!Number.isFinite(defaultTemperature)) defaultTemperature = 0;
      const defaultReasoningEffort = defaultReasoningSel ? (defaultReasoningSel.value || 'none') : 'none';
      return {
        models,
        defaultModel,
        defaultPrompt,
        apiPort,
        apiProvider,
        gptSystemLogic,
        defaultVerbosity,
        defaultTemperature,
        defaultReasoningEffort,
      };
    }

    function configsEqual(a, b) {
      if (!a || !b) return false;
      // compare models arrays
      const am = Array.isArray(a.models) ? a.models : [];
      const bm = Array.isArray(b.models) ? b.models : [];
      if (am.length !== bm.length) return false;
      for (let i = 0; i < am.length; i++) if (am[i] !== bm[i]) return false;
      if ((a.defaultModel || '') !== (b.defaultModel || '')) return false;
      if ((a.defaultPrompt || 'Auaci') !== (b.defaultPrompt || 'Auaci')) return false;
      // apiPort
      const ap = typeof a.apiPort === 'number' ? a.apiPort : (a.apiPort ? parseInt(a.apiPort, 10) : null);
      const bp = typeof b.apiPort === 'number' ? b.apiPort : (b.apiPort ? parseInt(b.apiPort, 10) : null);
      if (ap !== bp) return false;
      if ((a.gptSystemLogic || 'tool-openai') !== (b.gptSystemLogic || 'tool-openai')) return false;
      return true;
    }

    // Called on any input/change to update unsaved highlighting
    function evaluateUnsavedStates() {
      try {
        if (!lastLoadedConfig) {
          // if we don't have loaded baseline just mark nothing unsaved
          clearAllUnsavedMarks();
          return;
        }
        const cur = currentFormValues();

        // models
        const modelsEqual = Array.isArray(lastLoadedConfig.models)
          && lastLoadedConfig.models.length === cur.models.length
          && lastLoadedConfig.models.every((v, idx) => v === cur.models[idx]);
        markFieldUnsaved(modelsTa, !modelsEqual);

        // default model
        markFieldUnsaved(defaultSel, String(lastLoadedConfig.defaultModel || '') !== String(cur.defaultModel || ''));

        // default prompt
        markFieldUnsaved(promptSel, String(lastLoadedConfig.defaultPrompt || 'Auaci') !== String(cur.defaultPrompt || 'Auaci'));

        // api port
        const lp = typeof lastLoadedConfig.apiPort === 'number' ? lastLoadedConfig.apiPort : (lastLoadedConfig.apiPort ? parseInt(lastLoadedConfig.apiPort, 10) : null);
        const cp = cur.apiPort;
        markFieldUnsaved(apiPortInput, lp !== cp);

        // GPT system logic
        if (gptSystemLogicSel) {
          markFieldUnsaved(gptSystemLogicSel, String(lastLoadedConfig.gptSystemLogic || 'tool-openai') !== String(cur.gptSystemLogic || 'tool-openai'));
        }

        // provider
        markFieldUnsaved(providerSel, String(lastLoadedConfig.apiProvider || 'local') !== String(cur.apiProvider || 'local'));
        if (defaultVerbositySel) {
          const lv = String(lastLoadedConfig.defaultVerbosity || 'high');
          const cv = String(cur.defaultVerbosity || 'high');
          markFieldUnsaved(defaultVerbositySel, lv !== cv);
        }
        if (defaultTempInput) {
          const lt = typeof lastLoadedConfig.defaultTemperature === 'number'
            ? lastLoadedConfig.defaultTemperature
            : (lastLoadedConfig.defaultTemperature ? parseFloat(lastLoadedConfig.defaultTemperature) : 0);
          const ct = cur.defaultTemperature;
          const ltn = Number.isFinite(lt) ? lt : 0;
          const ctn = Number.isFinite(ct) ? ct : 0;
          markFieldUnsaved(defaultTempInput, ltn !== ctn);
        }
        if (defaultReasoningSel) {
          const lr = String(lastLoadedConfig.defaultReasoningEffort || 'none');
          const cr = String(cur.defaultReasoningEffort || 'none');
          markFieldUnsaved(defaultReasoningSel, lr !== cr);
        }
      } catch (e) {
        console.warn('evaluateUnsavedStates error:', e);
      }
    }

    // loadAndPopulate is now reusable so Reset can call it too
    async function loadAndPopulate() {
      try {
        console.log('[chat.js] invoking ipcRenderer.invoke("get-config")');
        const cfg = await ipcRenderer.invoke('get-config');
        // Avoid logging full config to prevent accidental credential exposure
        console.log('[chat.js] get-config returned keys:', cfg && Object.keys(cfg || {}));

        let effective = null;
        if (!cfg || cfg.error) {
          // fallback default values displayed in UI
          effective = {
            models: FALLBACK_MODELS.slice(),
            defaultModel: FALLBACK_DEFAULT_MODEL,
            defaultPrompt: 'Auaci',
            apiPort: 8129,
            apiProvider: 'local',
            gptSystemLogic: 'tool-openai',
            defaultVerbosity: 'high',
            defaultTemperature: 0,
            defaultReasoningEffort: 'none',
          };
        } else {
          effective = {
            models: Array.isArray(cfg.models) ? cfg.models.slice() : [],
            defaultModel: cfg.defaultModel || '',
            defaultPrompt: cfg.defaultPrompt || 'Auaci',
            apiPort: typeof cfg.apiPort === 'number'
              ? cfg.apiPort
              : (typeof cfg.apiPort === 'string' && cfg.apiPort ? parseInt(cfg.apiPort, 10) : cfg.apiPort),
            apiProvider: 'local',
            gptSystemLogic: cfg.gptSystemLogic || 'tool-openai',
            defaultVerbosity: (typeof cfg.defaultVerbosity === 'string' && cfg.defaultVerbosity)
              ? cfg.defaultVerbosity
              : 'high',
            defaultTemperature: (typeof cfg.defaultTemperature === 'number')
              ? cfg.defaultTemperature
              : (typeof cfg.defaultTemperature === 'string' && cfg.defaultTemperature
                  ? parseFloat(cfg.defaultTemperature)
                  : 0),
            defaultReasoningEffort: (typeof cfg.defaultReasoningEffort === 'string' && cfg.defaultReasoningEffort)
              ? cfg.defaultReasoningEffort
              : 'none',
          };

          // Ensure we never show an empty models list in the UI; fall back to built-in defaults.
          if (!Array.isArray(effective.models) || effective.models.length === 0) {
            effective.models = FALLBACK_MODELS.slice();
          }
          if (!effective.defaultModel) {
            effective.defaultModel = effective.models[0] || FALLBACK_DEFAULT_MODEL;
          }
        }

        // Normalize advanced defaults
        try {
          const vRaw = String(effective.defaultVerbosity || 'high').toLowerCase();
          effective.defaultVerbosity = (vRaw === 'low' || vRaw === 'medium' || vRaw === 'high') ? vRaw : 'high';

          let tRaw = (typeof effective.defaultTemperature === 'number')
            ? effective.defaultTemperature
            : (typeof effective.defaultTemperature === 'string' && effective.defaultTemperature
                ? parseFloat(effective.defaultTemperature)
                : 0);
          if (!Number.isFinite(tRaw)) tRaw = 0;
          if (tRaw < 0) tRaw = 0;
          if (tRaw > 2) tRaw = 2;
          effective.defaultTemperature = tRaw;

          const rRaw = String(effective.defaultReasoningEffort || 'none').toLowerCase();
          effective.defaultReasoningEffort = ['none', 'low', 'medium', 'high'].includes(rRaw) ? rRaw : 'none';
        } catch (_) {}

        // Apply to UI controls
        modelsTa.value = effective.models.join('\n');
        populateDefaultSelect(effective.models, effective.defaultModel);
        if (promptSel) promptSel.value = effective.defaultPrompt || 'Auaci';
        apiPortInput.value = String(effective.apiPort || '');
        if (gptSystemLogicSel) gptSystemLogicSel.value = effective.gptSystemLogic || 'tool-openai';

        if (defaultVerbositySel) defaultVerbositySel.value = effective.defaultVerbosity || 'high';
        if (defaultTempInput) {
          const t = effective.defaultTemperature != null ? effective.defaultTemperature : 0;
          defaultTempInput.value = String(t);
        }
        if (defaultReasoningSel) defaultReasoningSel.value = effective.defaultReasoningEffort || 'none';

        // Save baseline and clear unsaved markers
        lastLoadedConfig = effective;
        clearAllUnsavedMarks();

        console.log('[chat.js] UI populated from config');
      } catch (err) {
        console.error('[chat.js] loadAndPopulate error:', err);
        const effective = {
          models: FALLBACK_MODELS.slice(),
          defaultModel: FALLBACK_DEFAULT_MODEL,
          defaultPrompt: 'Auaci',
          apiPort: 8129,
          apiProvider: 'local',
          gptSystemLogic: 'tool-openai',
          defaultVerbosity: 'high',
          defaultTemperature: 0,
          defaultReasoningEffort: 'none',
        };

        modelsTa.value = effective.models.join('\n');
        populateDefaultSelect(effective.models, effective.defaultModel);
        if (promptSel) promptSel.value = effective.defaultPrompt || 'Auaci';
        apiPortInput.value = '8129';
        if (gptSystemLogicSel) gptSystemLogicSel.value = effective.gptSystemLogic || 'tool-openai';

        if (defaultVerbositySel) defaultVerbositySel.value = effective.defaultVerbosity || 'high';
        if (defaultTempInput) defaultTempInput.value = String(effective.defaultTemperature != null ? effective.defaultTemperature : 0);
        if (defaultReasoningSel) defaultReasoningSel.value = effective.defaultReasoningEffort || 'none';

        lastLoadedConfig = effective;
        clearAllUnsavedMarks();
      }
    }

    // Initial population
    loadAndPopulate().catch(err => console.error('[chat.js] initial load error:', err));

    // Listen for config updates broadcast from main
    ipcRenderer.on('config-loaded', (event, cfg) => {
      try {
        console.log('[chat.js] received config-loaded broadcast keys:', cfg && Object.keys(cfg || {}));
        // Reuse main loader so all fields stay in sync with central config.
        loadAndPopulate().catch(e => console.error('[chat.js] loadAndPopulate error after broadcast:', e));
      } catch (e) {
        console.error('[chat.js] error applying broadcasted config', e);
      }
    });

    // Attach listeners to detect edits
    modelsTa.addEventListener('input', evaluateUnsavedStates);
    apiPortInput.addEventListener('input', evaluateUnsavedStates);
    defaultSel.addEventListener('change', evaluateUnsavedStates);
    if (promptSel) promptSel.addEventListener('change', evaluateUnsavedStates);
    if (defaultVerbositySel) defaultVerbositySel.addEventListener('change', evaluateUnsavedStates);
    if (defaultTempInput) defaultTempInput.addEventListener('input', evaluateUnsavedStates);
    if (defaultReasoningSel) defaultReasoningSel.addEventListener('change', evaluateUnsavedStates);

    // Save handler
    saveBtn.addEventListener('click', async () => {
      animateButtonPress(saveBtn);
      // Compose new config patch
      try {
        const models = modelsTa.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
        const defaultModel = defaultSel.value || models[0] || '';
        const defaultPrompt = (promptSel && promptSel.value) ? promptSel.value : 'Auaci';
        const apiPort = parseInt(apiPortInput.value, 10) || 8129;
        const apiProvider = 'local';
        const gptSystemLogic = gptSystemLogicSel ? (gptSystemLogicSel.value || 'tool-openai') : 'tool-openai';

        let defaultVerbosity = defaultVerbositySel ? (defaultVerbositySel.value || 'high') : 'high';
        defaultVerbosity = String(defaultVerbosity).toLowerCase();
        if (!['low', 'medium', 'high'].includes(defaultVerbosity)) defaultVerbosity = 'high';

        let defaultTemperature = defaultTempInput && defaultTempInput.value !== '' ? parseFloat(defaultTempInput.value) : 0;
        if (!Number.isFinite(defaultTemperature)) defaultTemperature = 0;
        if (defaultTemperature < 0) defaultTemperature = 0;
        if (defaultTemperature > 2) defaultTemperature = 2;

        let defaultReasoningEffort = defaultReasoningSel ? (defaultReasoningSel.value || 'none') : 'none';
        defaultReasoningEffort = String(defaultReasoningEffort).toLowerCase();
        if (!['none', 'low', 'medium', 'high'].includes(defaultReasoningEffort)) defaultReasoningEffort = 'none';

        // Show small inline status while saving
        if (saveStatus) { saveStatus.style.display = 'inline'; saveStatus.textContent = 'Saving...'; }
        if (saveError) { saveError.style.display = 'none'; saveError.textContent = ''; }

        const patch = {
          models,
          defaultModel,
          defaultPrompt,
          apiPort,
          apiProvider,
          gptSystemLogic,
          defaultVerbosity,
          defaultTemperature,
          defaultReasoningEffort,
        };

        // Capture the returned updated config so we can mark fields as saved.
        const updated = await ipcRenderer.invoke('update-config', patch);

        // update baseline and clear markers
        lastLoadedConfig = {
          models: Array.isArray(updated.models) ? updated.models.slice() : models,
          defaultModel: updated.defaultModel || defaultModel,
          defaultPrompt: updated.defaultPrompt || defaultPrompt,
          apiPort: typeof updated.apiPort === 'number' ? updated.apiPort : apiPort,
          apiProvider: 'local',
          gptSystemLogic: updated.gptSystemLogic || gptSystemLogic,
          defaultVerbosity: updated.defaultVerbosity || defaultVerbosity,
          defaultTemperature: typeof updated.defaultTemperature === 'number' ? updated.defaultTemperature : defaultTemperature,
          defaultReasoningEffort: updated.defaultReasoningEffort || defaultReasoningEffort,
        };
        clearAllUnsavedMarks();

        if (saveStatus) { saveStatus.textContent = 'Saved'; setTimeout(() => { saveStatus.style.display = 'none'; }, 900); }

        // show bottom banner success
        showBanner('Settings saved', 'success', 2800);
        console.log('[chat.js] update-config invoked');
      } catch (e) {
        console.error('[chat.js] save handler error', e);
        if (saveStatus) saveStatus.style.display = 'none';
        if (saveError) { saveError.style.display = 'inline'; saveError.textContent = String(e && e.message ? e.message : e); }
        showBanner('Failed to save settings', 'error', 4000);
      }
    });

    // Reset handler - restore defaults via IPC to main
    resetBtn.addEventListener('click', async () => {
      animateButtonPress(resetBtn);
      try {
        // Request defaults from main
        const defaults = await ipcRenderer.invoke('get-default-config');
        if (!defaults || defaults.error) {
          console.error('[chat.js] Failed to obtain defaults:', defaults && defaults.error ? defaults.error : defaults);
          showBanner('Failed to load defaults', 'error', 3000);
          return;
        }

        // Overwrite config with defaults (set-config replaces entire config)
        const setResult = await ipcRenderer.invoke('set-config', defaults);

        // Reload UI from the newly-set config (should be same as defaults)
        await loadAndPopulate();

        showBanner('Settings reset to defaults', 'info', 2400);
      } catch (e) {
        console.error('[chat.js] reset handler error', e);
        showBanner('Failed to reset settings', 'error', 3600);
      }
    });

    // ========== EXCLUDED FOLDERS SECTION ==========
    
    // Update the count display
    function updateExcludedCount() {
      if (!excludedFoldersTa || !excludedCountEl) return;
      const folders = excludedFoldersTa.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
      excludedCountEl.textContent = String(folders.length);
    }

    // Load excluded folders from the config file
    async function loadExcludedFolders() {
      if (!excludedFoldersTa) return;
      try {
        const result = await ipcRenderer.invoke('get-excluded-folders');
        if (result && Array.isArray(result.folders)) {
          excludedFoldersTa.value = result.folders.join('\n');
          updateExcludedCount();
          console.log('[chat.js] Loaded', result.folders.length, 'excluded folders');
        }
      } catch (e) {
        console.error('[chat.js] Failed to load excluded folders:', e);
      }
    }

    // Save excluded folders
    async function saveExcludedFolders() {
      if (!excludedFoldersTa) return;
      const folders = excludedFoldersTa.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
      try {
        await ipcRenderer.invoke('save-excluded-folders', folders);
        updateExcludedCount();
        showBanner('Exclusions saved', 'success', 2400);
        console.log('[chat.js] Saved', folders.length, 'excluded folders');
      } catch (e) {
        console.error('[chat.js] Failed to save excluded folders:', e);
        showBanner('Failed to save exclusions', 'error', 3600);
      }
    }

    // Reset excluded folders to defaults
    async function resetExcludedFolders() {
      try {
        const result = await ipcRenderer.invoke('reset-excluded-folders');
        if (result && Array.isArray(result.folders)) {
          if (excludedFoldersTa) excludedFoldersTa.value = result.folders.join('\n');
          updateExcludedCount();
          showBanner('Exclusions reset to defaults', 'info', 2400);
          console.log('[chat.js] Reset to', result.folders.length, 'default excluded folders');
        }
      } catch (e) {
        console.error('[chat.js] Failed to reset excluded folders:', e);
        showBanner('Failed to reset exclusions', 'error', 3600);
      }
    }

    // Initial load of excluded folders
    loadExcludedFolders().catch(err => console.error('[chat.js] initial excluded folders load error:', err));

    // Attach event listeners for excluded folders
    if (excludedFoldersTa) {
      excludedFoldersTa.addEventListener('input', updateExcludedCount);
    }
    if (saveExcludedBtn) {
      saveExcludedBtn.addEventListener('click', () => {
        animateButtonPress(saveExcludedBtn);
        saveExcludedFolders();
      });
    }
    if (resetExcludedBtn) {
      resetExcludedBtn.addEventListener('click', () => {
        animateButtonPress(resetExcludedBtn);
        resetExcludedFolders();
      });
    }

    // Optional: keyboard accessibility (Enter/Space to activate)
    [saveBtn, resetBtn, saveExcludedBtn, resetExcludedBtn].filter(Boolean).forEach((btn) => {
      btn.addEventListener('keyup', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          btn.click();
        }
      });
    });
  }

  // expose the init function for the loader to call
  if (typeof window !== 'undefined') {
    window.initChatSettings = init;
  } else if (typeof module !== 'undefined') {
    module.exports = { init };
  }
})();