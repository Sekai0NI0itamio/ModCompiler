// src/main/functions/chat/modelSelector.js
// Renderer-side model/hoster selector that uses the centralized app config via IPC
// Uses ipcRenderer.invoke('get-config') and ipcRenderer.invoke('update-config').

const { ipcRenderer } = require('electron');

/**
 * Known fallback models (used if config has no models key).
 * Settings UI will receive models from the centralized config normally.
 */
const FALLBACK_MODELS = ['GPT-5-mini', 'GPT-5.1-codex', 'GPT-5-nano'];
const FALLBACK_DEFAULT = 'GPT-5-mini';
const CUSTOM_MODEL_VALUE = '__custom__';

// Available hosters
const HOSTERS = ['poe', 'openrouter', 'nvidia'];
const DEFAULT_HOSTER = 'poe';

// Track custom model input state
let isCustomModelInputActive = false;
let previousModelValue = null;

function getSelectedModel() {
  return window.selectedModel || FALLBACK_DEFAULT;
}

function getSelectedPrompt() {
  // Always return 'Auaci' as the default prompt
  return 'Auaci';
}

function getSelectedHoster() {
  return window.selectedHoster || DEFAULT_HOSTER;
}

async function setupModelSelector() {
  if (document.readyState === 'loading') {
    await new Promise((resolve) => document.addEventListener('DOMContentLoaded', resolve));
  }

  const inputContainer = document.getElementById('input-container');
  if (!inputContainer) {
    console.warn('Model/Prompt selector: #input-container not found');
    return;
  }

  // Ensure top bar exists
  let topbar = inputContainer.querySelector('.model-topbar');
  if (!topbar) {
    topbar = document.createElement('div');
    topbar.className = 'model-topbar';
    const inputScroll = inputContainer.querySelector('#input-scroll');
    if (inputScroll) inputContainer.insertBefore(topbar, inputScroll);
    else inputContainer.insertBefore(topbar, inputContainer.firstChild);
  }

  // If a model select already exists, don't create another; but we may still add hoster select
  let modelSelect = topbar.querySelector('select.model-select');
  let hosterSelect = topbar.querySelector('select.hoster-select');
  let reasoningSelect = topbar.querySelector('select.reasoning-select');
  let customModelInput = topbar.querySelector('input.custom-model-input');

  if (!modelSelect) {
    modelSelect = document.createElement('select');
    modelSelect.className = 'model-select';
    modelSelect.setAttribute('aria-label', 'Select model for chat');
    topbar.insertBefore(modelSelect, topbar.firstChild);
  }

  // Create custom model input (hidden by default)
  if (!customModelInput) {
    customModelInput = document.createElement('input');
    customModelInput.type = 'text';
    customModelInput.className = 'model-select custom-model-input';
    customModelInput.placeholder = 'Enter custom model name...';
    customModelInput.style.display = 'none';
    customModelInput.setAttribute('aria-label', 'Enter custom model name');
    topbar.insertBefore(customModelInput, modelSelect.nextSibling);
  }

  if (!hosterSelect) {
    hosterSelect = document.createElement('select');
    hosterSelect.className = 'model-select hoster-select'; // reuse styling
    hosterSelect.setAttribute('aria-label', 'Select hoster for chat');
    // place to the right of custom model input
    if (customModelInput.nextSibling) topbar.insertBefore(hosterSelect, customModelInput.nextSibling);
    else topbar.appendChild(hosterSelect);
  }

  if (!reasoningSelect) {
    reasoningSelect = document.createElement('select');
    reasoningSelect.className = 'model-select reasoning-select'; // reuse styling
    reasoningSelect.setAttribute('aria-label', 'Select reasoning effort');
    // place to the right of hoster select
    if (hosterSelect.nextSibling) topbar.insertBefore(reasoningSelect, hosterSelect.nextSibling);
    else topbar.appendChild(reasoningSelect);
  }

  // Populate selects with models and prompts and set selected values from config
  async function populateFromConfig() {
    try {
      const cfg = await ipcRenderer.invoke('get-config');
      const models = Array.isArray(cfg && cfg.models) && cfg.models.length > 0 ? cfg.models : FALLBACK_MODELS;
      const defaultModel = (cfg && cfg.defaultModel && typeof cfg.defaultModel === 'string') ? cfg.defaultModel : (models[0] || FALLBACK_DEFAULT);

      // Models - add Custom option at the top
      modelSelect.innerHTML = '';
      
      // Add "Custom..." option first
      const customOpt = document.createElement('option');
      customOpt.value = CUSTOM_MODEL_VALUE;
      customOpt.textContent = '+ Custom...';
      customOpt.style.fontStyle = 'italic';
      modelSelect.appendChild(customOpt);
      
      for (const m of models) {
        const opt = document.createElement('option');
        opt.value = m;
        opt.textContent = m;
        modelSelect.appendChild(opt);
      }
      if (defaultModel && models.includes(defaultModel)) modelSelect.value = defaultModel; else modelSelect.value = models[0] || FALLBACK_DEFAULT;
      window.selectedModel = modelSelect.value;

      // Hosters
      const defaultHoster = (cfg && typeof cfg.defaultHoster === 'string' && cfg.defaultHoster) ? cfg.defaultHoster : DEFAULT_HOSTER;
      hosterSelect.innerHTML = '';
      for (const h of HOSTERS) {
        const opt = document.createElement('option');
        opt.value = h;
        opt.textContent = h.charAt(0).toUpperCase() + h.slice(1); // Capitalize first letter
        hosterSelect.appendChild(opt);
      }
      hosterSelect.value = HOSTERS.includes(defaultHoster) ? defaultHoster : DEFAULT_HOSTER;
      window.selectedHoster = hosterSelect.value;

      // Set default prompt to always be 'Auaci'
      window.selectedPrompt = 'Auaci';

      // Reasoning effort (fixed list; default comes from config, overrideable per-session)
      const efforts = [
        { value: 'none', label: 'None' },
        { value: 'low', label: 'Low' },
        { value: 'medium', label: 'Medium' },
        { value: 'high', label: 'High' }
      ];
      reasoningSelect.innerHTML = '';
      const allowedEfforts = new Set();
      for (const e of efforts) {
        const opt = document.createElement('option');
        opt.value = e.value;
        opt.textContent = e.label;
        reasoningSelect.appendChild(opt);
        allowedEfforts.add(e.value);
      }
      let defaultEffort = 'none';
      if (cfg && typeof cfg.defaultReasoningEffort === 'string') {
        const cand = cfg.defaultReasoningEffort.toLowerCase();
        if (allowedEfforts.has(cand)) defaultEffort = cand;
      }
      const prevEffort = (typeof window.selectedReasoningEffort === 'string') ? window.selectedReasoningEffort : defaultEffort;
      const chosenEffort = allowedEfforts.has(prevEffort) ? prevEffort : defaultEffort;
      reasoningSelect.value = chosenEffort;
      window.selectedReasoningEffort = chosenEffort;
    } catch (err) {
      console.error('Model/Prompt selector: failed to load config:', err);
      // fallback
      modelSelect.innerHTML = '';
      
      // Add "Custom..." option first even in fallback
      const customOpt = document.createElement('option');
      customOpt.value = CUSTOM_MODEL_VALUE;
      customOpt.textContent = '+ Custom...';
      customOpt.style.fontStyle = 'italic';
      modelSelect.appendChild(customOpt);
      
      for (const m of FALLBACK_MODELS) {
        const opt = document.createElement('option');
        opt.value = m;
        opt.textContent = m;
        modelSelect.appendChild(opt);
      }
      modelSelect.value = FALLBACK_DEFAULT;
      window.selectedModel = modelSelect.value;

      // Hosters fallback
      hosterSelect.innerHTML = '';
      for (const h of HOSTERS) {
        const opt = document.createElement('option');
        opt.value = h;
        opt.textContent = h.charAt(0).toUpperCase() + h.slice(1);
        hosterSelect.appendChild(opt);
      }
      hosterSelect.value = DEFAULT_HOSTER;
      window.selectedHoster = hosterSelect.value;

      // Set default prompt to always be 'Auaci'
      window.selectedPrompt = 'Auaci';

      // Reasoning effort fallback
      reasoningSelect.innerHTML = '';
      for (const [value,label] of [['none','None'], ['low','Low'], ['medium','Medium'], ['high','High']]) {
        const opt = document.createElement('option');
        opt.value = value;
        opt.textContent = label;
        reasoningSelect.appendChild(opt);
      }
      reasoningSelect.value = 'none';
      window.selectedReasoningEffort = 'none';
    }
  }

  // Handle config updates pushed from main
  ipcRenderer.on('config-loaded', (event, cfg) => {
    try {
      const models = Array.isArray(cfg && cfg.models) && cfg.models.length > 0 ? cfg.models : FALLBACK_MODELS;
      const defaultModel = (cfg && cfg.defaultModel && typeof cfg.defaultModel === 'string') ? cfg.defaultModel : (models[0] || FALLBACK_DEFAULT);
      const defaultHoster = (cfg && typeof cfg.defaultHoster === 'string' && cfg.defaultHoster) ? cfg.defaultHoster : DEFAULT_HOSTER;

      // Models - preserve Custom option at top
      modelSelect.innerHTML = '';
      
      const customOpt = document.createElement('option');
      customOpt.value = CUSTOM_MODEL_VALUE;
      customOpt.textContent = '+ Custom...';
      customOpt.style.fontStyle = 'italic';
      modelSelect.appendChild(customOpt);
      
      for (const m of models) {
        const opt = document.createElement('option');
        opt.value = m;
        opt.textContent = m;
        modelSelect.appendChild(opt);
      }
      modelSelect.value = (defaultModel && models.includes(defaultModel)) ? defaultModel : (models[0] || FALLBACK_DEFAULT);
      window.selectedModel = modelSelect.value;

      // Hosters
      hosterSelect.innerHTML = '';
      for (const h of HOSTERS) {
        const opt = document.createElement('option');
        opt.value = h;
        opt.textContent = h.charAt(0).toUpperCase() + h.slice(1);
        hosterSelect.appendChild(opt);
      }
      hosterSelect.value = HOSTERS.includes(defaultHoster) ? defaultHoster : DEFAULT_HOSTER;
      window.selectedHoster = hosterSelect.value;

      // Set default prompt to always be 'Auaci'
      window.selectedPrompt = 'Auaci';

      // Reasoning default from config
      const allowedEfforts = ['none', 'low', 'medium', 'high'];
      let defaultEffort = 'none';
      if (cfg && typeof cfg.defaultReasoningEffort === 'string') {
        const cand = cfg.defaultReasoningEffort.toLowerCase();
        if (allowedEfforts.includes(cand)) defaultEffort = cand;
      }
      reasoningSelect.value = defaultEffort;
      window.selectedReasoningEffort = defaultEffort;
    } catch (e) {
      // ignore
    }
  });

  /**
   * Show custom model input mode
   */
  function showCustomModelInput() {
    isCustomModelInputActive = true;
    previousModelValue = modelSelect.value !== CUSTOM_MODEL_VALUE ? modelSelect.value : (window.selectedModel || FALLBACK_DEFAULT);
    
    modelSelect.style.display = 'none';
    customModelInput.style.display = '';
    customModelInput.value = '';
    customModelInput.focus();
  }

  /**
   * Hide custom model input and restore select
   */
  function hideCustomModelInput(restorePrevious = true) {
    isCustomModelInputActive = false;
    
    customModelInput.style.display = 'none';
    modelSelect.style.display = '';
    
    if (restorePrevious && previousModelValue) {
      modelSelect.value = previousModelValue;
      window.selectedModel = previousModelValue;
    }
  }

  /**
   * Save custom model to config and select it
   */
  async function saveCustomModel(modelName) {
    const trimmedName = modelName.trim();
    if (!trimmedName) {
      hideCustomModelInput(true);
      return;
    }

    try {
      // Get current config
      const cfg = await ipcRenderer.invoke('get-config');
      const models = Array.isArray(cfg && cfg.models) ? [...cfg.models] : [...FALLBACK_MODELS];
      
      // Check if model already exists
      if (!models.includes(trimmedName)) {
        // Add new model to the list
        models.push(trimmedName);
        
        // Update config with new models list and set as default
        await ipcRenderer.invoke('update-config', { 
          models: models,
          defaultModel: trimmedName 
        });
        
        // Add new option to select
        const newOpt = document.createElement('option');
        newOpt.value = trimmedName;
        newOpt.textContent = trimmedName;
        modelSelect.appendChild(newOpt);
      } else {
        // Model exists, just set as default
        await ipcRenderer.invoke('update-config', { defaultModel: trimmedName });
      }
      
      // Select the new/existing model
      modelSelect.value = trimmedName;
      window.selectedModel = trimmedName;
      
      // Hide input and show select
      hideCustomModelInput(false);
      
      // Visual feedback
      modelSelect.classList.add('model-saved');
      setTimeout(() => modelSelect.classList.remove('model-saved'), 700);
      
      try { 
        window.dispatchEvent(new CustomEvent('model-selected', { detail: { model: trimmedName } })); 
      } catch (_) {}
      
      console.log(`[modelSelector] Added custom model: ${trimmedName}`);
      
    } catch (err) {
      console.error('Model selector: failed to save custom model:', err);
      hideCustomModelInput(true);
    }
  }

  // Custom model input event handlers
  customModelInput.addEventListener('keydown', async (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      await saveCustomModel(customModelInput.value);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      hideCustomModelInput(true);
    }
  });

  customModelInput.addEventListener('blur', () => {
    // Small delay to allow click events to process first
    setTimeout(() => {
      if (isCustomModelInputActive) {
        hideCustomModelInput(true);
      }
    }, 150);
  });

  // change handler -- persist via update-config IPC and flash saved state
  modelSelect.addEventListener('change', async (e) => {
    const newModel = e.target.value;
    if (!newModel) return;
    
    // Handle custom model selection
    if (newModel === CUSTOM_MODEL_VALUE) {
      showCustomModelInput();
      return;
    }
    
    window.selectedModel = newModel;
    try {
      await ipcRenderer.invoke('update-config', { defaultModel: newModel });
      modelSelect.classList.add('model-saved');
      setTimeout(() => modelSelect.classList.remove('model-saved'), 700);
      try { window.dispatchEvent(new CustomEvent('model-selected', { detail: { model: newModel } })); } catch (_) {}
    } catch (err) {
      console.error('Model selector: failed to persist selected model via update-config:', err);
    }
  });

  hosterSelect.addEventListener('change', async (e) => {
    const newHoster = e.target.value;
    if (!newHoster) return;
    window.selectedHoster = newHoster;
    try {
      await ipcRenderer.invoke('update-config', { defaultHoster: newHoster });
      hosterSelect.classList.add('model-saved');
      setTimeout(() => hosterSelect.classList.remove('model-saved'), 700);
      try { window.dispatchEvent(new CustomEvent('hoster-selected', { detail: { hoster: newHoster } })); } catch (_) {}
    } catch (err) {
      console.error('Hoster selector: failed to persist selected hoster via update-config:', err);
    }
  });

  reasoningSelect.addEventListener('change', (e) => {
    const v = String(e.target.value || 'none').toLowerCase();
    const allowed = new Set(['none','low','medium','high']);
    window.selectedReasoningEffort = allowed.has(v) ? v : 'none';
    // Visual flash like others (no persistence)
    reasoningSelect.classList.add('model-saved');
    setTimeout(() => reasoningSelect.classList.remove('model-saved'), 700);
  });

  // Initial population
  await populateFromConfig();
}

module.exports = { setupModelSelector, getSelectedModel, getSelectedPrompt, getSelectedHoster };
