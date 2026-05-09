// src/main/functions/chat/ui/multiView.js
// Multi-View Sessions - Split view for multiple concurrent sessions

const fs = require('fs').promises;
const path = require('path');
const { ipcRenderer } = require('electron');

const STYLE_ID = 'auaci-multiview-styles';

// Track multi-viewed sessions
const multiViewSessions = new Set();

// Store session containers for multi-view
const sessionContainers = new Map();

// Autocomplete state per panel
const panelAutocompleteState = new Map();

// File index (shared with terminalInput)
let fileIndex = [];
let fileIndexReady = false;
let projectRoot = null;

const CSS = `
/* Multi-View Styles */
#chat-container.multi-view-active {
  display: flex !important;
  flex-direction: column !important;
  overflow: hidden !important;
  height: 100% !important;
}

#chat-container.multi-view-active > #chat-topbar {
  flex-shrink: 0;
}

#chat-container.multi-view-active > #chat-messages {
  display: none !important;
}

#chat-container.multi-view-active > #input-container {
  display: none !important;
}

#multi-view-container {
  display: none;
  flex-direction: row;
  overflow: hidden;
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
}

#chat-container.multi-view-active #multi-view-container {
  display: flex !important;
}

.multi-view-panel {
  flex: 1 1 50%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 200px;
  height: 100%;
  background: #ffffff;
}

.multi-view-panel:not(:last-child) {
  border-right: 2px solid #e5e7eb;
}

.multi-view-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  flex-shrink: 0;
}

.multi-view-panel-header .session-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.multi-view-panel-header .close-btn {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  border-radius: 4px;
  font-size: 14px;
  margin-left: 8px;
}

.multi-view-panel-header .close-btn:hover {
  background: #e5e7eb;
  color: #374151;
}

.multi-view-messages {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px;
  min-height: 0;
}

.multi-view-input-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
  border-top: 1px solid #ccc;
  background: white;
  padding: 8px;
  position: relative;
  flex-shrink: 0;
}

.multi-view-topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 8px;
  height: 36px;
  background: rgba(255,255,255,0.98);
  border-radius: 6px 6px 0 0;
  margin-bottom: 8px;
  box-sizing: border-box;
}

.multi-view-topbar .model-select {
  font-size: 13px;
  padding: 6px 8px;
  border-radius: 6px;
  border: 1px solid #d0d0d0;
  background: #fff;
  min-width: 120px;
  color: #2d2d2d;
  box-shadow: 0 3px 8px rgba(0,0,0,0.02);
}

.multi-view-input-wrapper {
  position: relative;
  width: 100%;
}

.multi-view-input {
  width: 100%;
  box-sizing: border-box;
  margin: 0;
  min-height: 60px;
  max-height: 150px;
  height: auto;
  border: none;
  outline: none;
  font-family: 'Inter', system-ui, -apple-system, Arial, sans-serif;
  font-size: 14px;
  line-height: 1.6;
  background: transparent;
  padding: 6px 0;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  resize: none;
}

.multi-view-autocomplete-ghost {
  position: absolute;
  pointer-events: none;
  color: #9ca3af;
  opacity: 0.7;
  white-space: pre;
  z-index: 1;
}

.multi-view-autocomplete-hint {
  position: absolute;
  right: 8px;
  bottom: 8px;
  font-size: 10px;
  color: #9ca3af;
  background: #f3f4f6;
  padding: 2px 6px;
  border-radius: 4px;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.15s;
  z-index: 10;
}

.multi-view-autocomplete-hint.visible {
  opacity: 1;
}

.multi-view-input:focus {
  outline: none;
}

.multi-view-input::placeholder {
  color: #666;
}

.multi-view-panel.responding .multi-view-panel-header {
  background: #fef3c7;
}

.multi-view-panel.responding .session-name {
  color: #b45309;
}

/* Resize handle between panels */
.multi-view-resize-handle {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  position: relative;
  flex-shrink: 0;
}

.multi-view-resize-handle:hover,
.multi-view-resize-handle.dragging {
  background: #3b82f6;
}

/* Tab indicator for multi-viewed sessions */
.auaci-chat-tab.multi-viewed {
  border-left: 3px solid #3b82f6;
}
`;

/**
 * Initialize multi-view system
 */
async function initMultiView() {
  injectStyles();
  createMultiViewContainer();
  setupContextMenuExtension();
  
  // Get project root for file indexing
  try {
    projectRoot = await ipcRenderer.invoke('get-project-root');
  } catch (_) {}
  if (!projectRoot) {
    try { projectRoot = process.cwd(); } catch (_) {}
  }
  
  // Build file index for autocomplete
  await buildFileIndex();
  
  console.log('[MultiView] Initialized');
}

/**
 * Inject styles
 */
function injectStyles() {
  if (document.getElementById(STYLE_ID)) return;
  const style = document.createElement('style');
  style.id = STYLE_ID;
  style.textContent = CSS;
  document.head.appendChild(style);
}

/**
 * Create the multi-view container
 */
function createMultiViewContainer() {
  const chatContainer = document.getElementById('chat-container');
  if (!chatContainer) return;
  
  // Check if already exists
  if (document.getElementById('multi-view-container')) return;
  
  const container = document.createElement('div');
  container.id = 'multi-view-container';
  
  // Insert after chat-messages
  const chatMessages = document.getElementById('chat-messages');
  if (chatMessages && chatMessages.nextSibling) {
    chatContainer.insertBefore(container, chatMessages.nextSibling);
  } else if (chatMessages) {
    chatContainer.insertBefore(container, chatMessages.nextSibling);
  } else {
    chatContainer.appendChild(container);
  }
}

/**
 * Setup context menu extension for Multi-View option
 * NOTE: Multi-View menu item is already added in chatTabOptions.js
 * This function is kept for potential future extensions but does NOT add duplicate menu items
 */
function setupContextMenuExtension() {
  // Multi-View menu item is handled directly in chatTabOptions.js
  // No need to add it again via event listener to avoid duplicates
}

/**
 * Add Multi-View menu item to context menu
 */
function addMultiViewMenuItem(menu, sessionId) {
  const isMultiViewed = multiViewSessions.has(sessionId);
  
  const item = document.createElement('div');
  item.className = 'ct-item';
  item.textContent = isMultiViewed ? 'Close Multi-View' : 'Multi-View';
  
  item.addEventListener('click', (ev) => {
    ev.stopPropagation();
    // Hide the menu
    try {
      const { hideMenu } = require('../chatTabOptions');
      hideMenu();
    } catch (_) {}
    
    if (isMultiViewed) {
      removeFromMultiView(sessionId);
    } else {
      addToMultiView(sessionId);
    }
  });
  
  // Insert before delete option
  const deleteItem = menu.querySelector('.ct-item.danger');
  if (deleteItem) {
    menu.insertBefore(item, deleteItem);
  } else {
    menu.appendChild(item);
  }
}

/**
 * Add a session to multi-view
 * When first session is added, also add the current active session
 */
async function addToMultiView(sessionId) {
  if (multiViewSessions.has(sessionId)) return;
  
  // If this is the first session being added to multi-view,
  // also add the current active session so we have two panels
  if (multiViewSessions.size === 0) {
    try {
      const sessionManager = require('../sessionManager');
      const currentSessionId = await sessionManager.getSessionId();
      
      // If the session being added is different from current, add current first
      if (currentSessionId && currentSessionId !== sessionId) {
        await addSessionPanel(currentSessionId);
      }
    } catch (e) {
      console.warn('[MultiView] Could not get current session:', e);
    }
  }
  
  // Add the requested session
  await addSessionPanel(sessionId);
  
  // Activate multi-view mode
  updateMultiViewMode();
  
  console.log('[MultiView] Added session:', sessionId, 'Total sessions:', multiViewSessions.size);
}

/**
 * Internal function to add a session panel
 */
async function addSessionPanel(sessionId) {
  if (multiViewSessions.has(sessionId)) return;
  
  multiViewSessions.add(sessionId);
  
  // Get session info
  let sessionName = sessionId.slice(0, 8);
  try {
    const tabManager = require('../tabManager');
    const sessions = await tabManager.getSessions();
    const session = sessions.find(s => s.id === sessionId);
    if (session) sessionName = session.name || sessionName;
  } catch (_) {}
  
  // Create panel for this session
  const panel = createMultiViewPanel(sessionId, sessionName);
  
  const container = document.getElementById('multi-view-container');
  if (container) {
    // Add resize handle if not first panel
    if (container.children.length > 0) {
      const handle = createResizeHandle();
      container.appendChild(handle);
    }
    container.appendChild(panel);
  }
  
  // Load and render history for this session
  await renderSessionInPanel(sessionId, panel);
  
  // Update tab indicator
  updateTabMultiViewIndicator(sessionId, true);
}

/**
 * Remove a session from multi-view
 * If only one session remains after removal, auto-disable multi-view entirely
 */
function removeFromMultiView(sessionId) {
  if (!multiViewSessions.has(sessionId)) return;
  
  multiViewSessions.delete(sessionId);
  sessionContainers.delete(sessionId);
  panelAutocompleteState.delete(sessionId);
  
  // Remove panel
  const container = document.getElementById('multi-view-container');
  if (container) {
    const panel = container.querySelector(`.multi-view-panel[data-session-id="${sessionId}"]`);
    if (panel) {
      // Also remove adjacent resize handle
      const prevSibling = panel.previousElementSibling;
      const nextSibling = panel.nextElementSibling;
      
      if (prevSibling && prevSibling.classList.contains('multi-view-resize-handle')) {
        prevSibling.remove();
      } else if (nextSibling && nextSibling.classList.contains('multi-view-resize-handle')) {
        nextSibling.remove();
      }
      
      panel.remove();
    }
  }
  
  // Update tab indicator
  updateTabMultiViewIndicator(sessionId, false);
  
  // If only one session remains, auto-disable multi-view entirely
  if (multiViewSessions.size === 1) {
    const remainingSessionId = Array.from(multiViewSessions)[0];
    console.log('[MultiView] Only one session remaining, auto-disabling multi-view');
    
    // Remove the remaining session
    multiViewSessions.delete(remainingSessionId);
    sessionContainers.delete(remainingSessionId);
    panelAutocompleteState.delete(remainingSessionId);
    
    // Remove remaining panel
    if (container) {
      const remainingPanel = container.querySelector(`.multi-view-panel[data-session-id="${remainingSessionId}"]`);
      if (remainingPanel) remainingPanel.remove();
      
      // Remove any remaining resize handles
      const handles = container.querySelectorAll('.multi-view-resize-handle');
      handles.forEach(h => h.remove());
    }
    
    // Update tab indicator for remaining session
    updateTabMultiViewIndicator(remainingSessionId, false);
  }
  
  // Update multi-view mode (will disable if no sessions left)
  updateMultiViewMode();
  
  console.log('[MultiView] Removed session:', sessionId, 'Remaining:', multiViewSessions.size);
}

/**
 * Create a multi-view panel for a session
 */
function createMultiViewPanel(sessionId, sessionName) {
  const panel = document.createElement('div');
  panel.className = 'multi-view-panel';
  panel.setAttribute('data-session-id', sessionId);
  
  // Header
  const header = document.createElement('div');
  header.className = 'multi-view-panel-header';
  
  const nameSpan = document.createElement('span');
  nameSpan.className = 'session-name';
  nameSpan.textContent = sessionName;
  nameSpan.title = sessionName;
  
  const closeBtn = document.createElement('button');
  closeBtn.className = 'close-btn';
  closeBtn.innerHTML = '×';
  closeBtn.title = 'Close Multi-View';
  closeBtn.addEventListener('click', () => removeFromMultiView(sessionId));
  
  header.appendChild(nameSpan);
  header.appendChild(closeBtn);
  
  // Messages container
  const messages = document.createElement('div');
  messages.className = 'multi-view-messages';
  messages.setAttribute('data-session-id', sessionId);
  
  // Input container with full functionality
  const inputContainer = document.createElement('div');
  inputContainer.className = 'multi-view-input-container';
  
  // Top bar with model/hoster/reasoning selectors
  const topbar = document.createElement('div');
  topbar.className = 'multi-view-topbar';
  
  // Model selector
  const modelSelect = document.createElement('select');
  modelSelect.className = 'model-select';
  modelSelect.setAttribute('aria-label', 'Select model');
  
  // Hoster selector
  const hosterSelect = document.createElement('select');
  hosterSelect.className = 'model-select hoster-select';
  hosterSelect.setAttribute('aria-label', 'Select hoster');
  
  // Reasoning selector
  const reasoningSelect = document.createElement('select');
  reasoningSelect.className = 'model-select reasoning-select';
  reasoningSelect.setAttribute('aria-label', 'Select reasoning effort');
  
  topbar.appendChild(modelSelect);
  topbar.appendChild(hosterSelect);
  topbar.appendChild(reasoningSelect);
  
  // Populate selectors
  populatePanelSelectors(modelSelect, hosterSelect, reasoningSelect);
  
  // Input wrapper for autocomplete positioning
  const inputWrapper = document.createElement('div');
  inputWrapper.className = 'multi-view-input-wrapper';
  
  const input = document.createElement('textarea');
  input.className = 'multi-view-input';
  input.placeholder = 'Type a message...';
  input.setAttribute('data-session-id', sessionId);
  
  // Autocomplete hint
  const autocompleteHint = document.createElement('div');
  autocompleteHint.className = 'multi-view-autocomplete-hint';
  autocompleteHint.textContent = 'Tab ↹';
  
  inputWrapper.appendChild(input);
  inputWrapper.appendChild(autocompleteHint);
  
  inputContainer.appendChild(topbar);
  inputContainer.appendChild(inputWrapper);
  
  // Initialize autocomplete state for this panel
  panelAutocompleteState.set(sessionId, {
    text: '',
    visible: false,
    lastInputText: '',
    cwd: projectRoot // Each panel can have its own CWD
  });
  
  // Setup input event handlers
  setupPanelInputHandlers(sessionId, input, inputWrapper, autocompleteHint, modelSelect, hosterSelect, reasoningSelect);
  
  panel.appendChild(header);
  panel.appendChild(messages);
  panel.appendChild(inputContainer);
  
  // Store reference
  sessionContainers.set(sessionId, { panel, messages, input, modelSelect, hosterSelect, reasoningSelect });
  
  return panel;
}

/**
 * Populate panel selectors with models, hosters, and reasoning options
 */
async function populatePanelSelectors(modelSelect, hosterSelect, reasoningSelect) {
  try {
    const cfg = await ipcRenderer.invoke('get-config');
    const models = Array.isArray(cfg?.models) && cfg.models.length > 0 ? cfg.models : ['GPT-5-mini', 'GPT-5.1-codex', 'GPT-5-nano'];
    const defaultModel = cfg?.defaultModel || models[0];
    
    // Models
    modelSelect.innerHTML = '';
    for (const m of models) {
      const opt = document.createElement('option');
      opt.value = m;
      opt.textContent = m;
      modelSelect.appendChild(opt);
    }
    modelSelect.value = models.includes(defaultModel) ? defaultModel : models[0];
    
    // Hosters
    const hosters = ['poe', 'openrouter'];
    const defaultHoster = cfg?.defaultHoster || 'poe';
    hosterSelect.innerHTML = '';
    for (const h of hosters) {
      const opt = document.createElement('option');
      opt.value = h;
      opt.textContent = h.charAt(0).toUpperCase() + h.slice(1); // Capitalize first letter
      hosterSelect.appendChild(opt);
    }
    hosterSelect.value = hosters.includes(defaultHoster) ? defaultHoster : 'poe';
    
    // Reasoning
    const efforts = [
      { value: 'none', label: 'None' },
      { value: 'low', label: 'Low' },
      { value: 'medium', label: 'Medium' },
      { value: 'high', label: 'High' }
    ];
    reasoningSelect.innerHTML = '';
    for (const e of efforts) {
      const opt = document.createElement('option');
      opt.value = e.value;
      opt.textContent = e.label;
      reasoningSelect.appendChild(opt);
    }
    const defaultEffort = cfg?.defaultReasoningEffort?.toLowerCase() || 'none';
    reasoningSelect.value = ['none', 'low', 'medium', 'high'].includes(defaultEffort) ? defaultEffort : 'none';
  } catch (err) {
    console.warn('[MultiView] Failed to populate selectors:', err);
  }
}

/**
 * Setup input event handlers for a panel
 */
function setupPanelInputHandlers(sessionId, input, inputWrapper, autocompleteHint, modelSelect, hosterSelect, reasoningSelect) {
  const state = panelAutocompleteState.get(sessionId);
  
  // Handle Tab key for autocomplete - ALWAYS prevent default
  input.addEventListener('keydown', async (e) => {
    if (e.key === 'Tab') {
      e.preventDefault();
      if (state.text && state.visible) {
        applyPanelAutocomplete(sessionId, input, inputWrapper);
      }
      return;
    }
  });
  
  // Handle Enter for CD commands and sending messages
  input.addEventListener('keydown', async (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      const text = input.value.trim();
      
      // Check if it's a CD command
      if (text.toLowerCase().startsWith('cd ') || text.toLowerCase() === 'cd') {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        await handlePanelCdCommand(sessionId, text, input);
        return;
      }
      
      // Regular message send
      e.preventDefault();
      if (text) {
        await sendMessageInPanel(sessionId, text, input, modelSelect.value, hosterSelect.value, reasoningSelect.value);
      }
    }
  }, true);
  
  // Handle input changes for autocomplete preview
  input.addEventListener('input', async () => {
    const text = input.value;
    if (text !== state.lastInputText) {
      state.lastInputText = text;
      await updatePanelAutocompletePreview(sessionId, text, input, inputWrapper, autocompleteHint);
    }
  });
}

/**
 * Get autocomplete suggestion for panel input
 */
async function getPanelAutocompleteSuggestion(text) {
  if (!text || !fileIndexReady) return null;
  
  const words = text.split(/\s+/);
  const lastWord = words[words.length - 1];
  
  if (!lastWord || lastWord.length < 1) return null;
  
  const searchTerm = lastWord.toLowerCase();
  
  let matches = fileIndex.filter(f => 
    f.name.toLowerCase().startsWith(searchTerm)
  );
  
  if (matches.length === 0) return null;
  
  matches.sort((a, b) => {
    if (a.name.toLowerCase() === searchTerm && b.name.toLowerCase() !== searchTerm) return -1;
    if (b.name.toLowerCase() === searchTerm && a.name.toLowerCase() !== searchTerm) return 1;
    return a.name.length - b.name.length;
  });
  
  const bestMatch = matches[0];
  return bestMatch.name.slice(lastWord.length);
}

/**
 * Update autocomplete preview for panel
 */
async function updatePanelAutocompletePreview(sessionId, text, input, inputWrapper, autocompleteHint) {
  const state = panelAutocompleteState.get(sessionId);
  const suggestion = await getPanelAutocompleteSuggestion(text);
  
  if (suggestion) {
    state.text = suggestion;
    state.visible = true;
    autocompleteHint.classList.add('visible');
    showPanelGhostText(sessionId, input, inputWrapper, text, suggestion);
  } else {
    clearPanelAutocomplete(sessionId, inputWrapper, autocompleteHint);
  }
}

/**
 * Show ghost text for panel autocomplete
 */
function showPanelGhostText(sessionId, input, inputWrapper, currentText, suggestion) {
  // Remove existing ghost
  const existingGhost = inputWrapper.querySelector('.multi-view-autocomplete-ghost');
  if (existingGhost) existingGhost.remove();
  
  if (!suggestion) return;
  
  const inputStyle = getComputedStyle(input);
  const paddingLeft = parseFloat(inputStyle.paddingLeft) || 0;
  const paddingTop = parseFloat(inputStyle.paddingTop) || 0;
  const fontSize = parseFloat(inputStyle.fontSize) || 14;
  const lineHeight = parseFloat(inputStyle.lineHeight) || fontSize * 1.2 || 20;
  
  // Get cursor position to determine which line we're on
  const cursorPos = input.selectionStart || currentText.length;
  const textBeforeCursor = currentText.substring(0, cursorPos);
  
  // Split text to find current line based on cursor position
  const linesBeforeCursor = textBeforeCursor.split('\n');
  const currentLineIndex = linesBeforeCursor.length - 1;
  const currentLineText = linesBeforeCursor[currentLineIndex] || '';
  
  // Create measuring span to get width of current line text
  const measureSpan = document.createElement('span');
  measureSpan.style.cssText = `position:absolute;visibility:hidden;white-space:pre;font:${inputStyle.font};`;
  measureSpan.textContent = currentLineText;
  document.body.appendChild(measureSpan);
  const textWidth = measureSpan.offsetWidth;
  document.body.removeChild(measureSpan);
  
  // Calculate vertical position based on current line, accounting for scroll
  const scrollTop = input.scrollTop || 0;
  const topPosition = paddingTop + (currentLineIndex * lineHeight) - scrollTop;
  
  const ghost = document.createElement('span');
  ghost.className = 'multi-view-autocomplete-ghost';
  ghost.textContent = suggestion;
  ghost.style.cssText = `
    position:absolute;
    top:${topPosition}px;
    left:${paddingLeft + textWidth}px;
    pointer-events:none;
    color:#9ca3af;
    opacity:0.7;
    white-space:pre;
    font:${inputStyle.font};
    line-height:${inputStyle.lineHeight};
    z-index:1;
  `;
  
  inputWrapper.appendChild(ghost);
}

/**
 * Clear panel autocomplete
 */
function clearPanelAutocomplete(sessionId, inputWrapper, autocompleteHint) {
  const state = panelAutocompleteState.get(sessionId);
  if (state) {
    state.text = '';
    state.visible = false;
  }
  
  if (autocompleteHint) autocompleteHint.classList.remove('visible');
  
  const ghost = inputWrapper?.querySelector('.multi-view-autocomplete-ghost');
  if (ghost) ghost.remove();
}

/**
 * Apply panel autocomplete
 */
function applyPanelAutocomplete(sessionId, input, inputWrapper) {
  const state = panelAutocompleteState.get(sessionId);
  const text = input.value;
  
  if (state.text) {
    const newText = text + state.text;
    
    // Clear autocomplete first
    const autocompleteHint = inputWrapper.querySelector('.multi-view-autocomplete-hint');
    clearPanelAutocomplete(sessionId, inputWrapper, autocompleteHint);
    state.lastInputText = newText;
    
    input.value = newText;
    
    // Move cursor to end
    input.focus();
    const endPos = newText.length;
    setTimeout(() => {
      input.setSelectionRange(endPos, endPos);
    }, 0);
  }
}

/**
 * Handle CD command in panel
 */
async function handlePanelCdCommand(sessionId, text, input) {
  const state = panelAutocompleteState.get(sessionId);
  const parts = text.trim().split(/\s+/);
  const targetPath = parts.length > 1 ? parts.slice(1).join(' ') : '';
  
  let newCwd;
  let errorMsg = null;
  const currentCwd = state.cwd || projectRoot;
  
  if (!targetPath || targetPath === '~') {
    newCwd = projectRoot;
  } else if (targetPath === '..') {
    newCwd = path.dirname(currentCwd);
  } else if (targetPath === '-') {
    newCwd = currentCwd;
  } else if (path.isAbsolute(targetPath)) {
    newCwd = targetPath;
  } else {
    newCwd = path.resolve(currentCwd, targetPath);
  }
  
  // Verify directory exists
  if (!errorMsg) {
    try {
      const stat = await fs.stat(newCwd);
      if (!stat.isDirectory()) {
        errorMsg = 'Not a directory';
      }
    } catch (err) {
      errorMsg = 'Directory not found';
    }
  }
  
  // Clear input
  input.value = '';
  const inputWrapper = input.parentElement;
  const autocompleteHint = inputWrapper?.querySelector('.multi-view-autocomplete-hint');
  clearPanelAutocomplete(sessionId, inputWrapper, autocompleteHint);
  
  // Render CD result in panel
  await renderPanelCdResult(sessionId, text, errorMsg ? currentCwd : newCwd, errorMsg);
  
  if (!errorMsg) {
    state.cwd = newCwd;
    console.log('[MultiView] Panel', sessionId, 'changed directory to:', newCwd);
  }
}

/**
 * Render CD result in panel
 */
async function renderPanelCdResult(sessionId, command, resultPath, error = null) {
  const containerInfo = sessionContainers.get(sessionId);
  if (!containerInfo) return;
  
  const { messages: messagesContainer } = containerInfo;
  
  const messageDiv = document.createElement('div');
  messageDiv.className = 'message user-message';
  messageDiv.style.marginBottom = '8px';
  
  const resultDiv = document.createElement('div');
  resultDiv.className = `cd-result-message${error ? ' error' : ''}`;
  resultDiv.style.cssText = `
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 10px 14px;
    background: ${error ? '#fef2f2' : '#f0f9ff'};
    border: 1px solid ${error ? '#fecaca' : '#bae6fd'};
    border-radius: 8px;
    font-family: 'SF Mono', Consolas, 'Courier New', monospace;
    font-size: 13px;
    color: ${error ? '#dc2626' : '#0369a1'};
  `;
  
  const escapeHtml = (s) => {
    const div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
  };
  
  if (error) {
    resultDiv.innerHTML = `
      <div style="display:flex;align-items:center;gap:8px;">
        <span style="font-weight:600;color:#0c4a6e;">cd:</span>
        <span>${escapeHtml(command)}</span>
      </div>
      <div style="color:#dc2626;font-size:12px;">${escapeHtml(error)}</div>
    `;
  } else {
    resultDiv.innerHTML = `
      <div style="display:flex;align-items:center;gap:8px;">
        <span style="font-weight:600;color:#0c4a6e;">cd:</span>
        <span>${escapeHtml(command)}</span>
      </div>
      <div style="color:#6b7280;font-size:12px;">${escapeHtml(resultPath)}</div>
    `;
  }
  
  messageDiv.appendChild(resultDiv);
  messagesContainer.appendChild(messageDiv);
  messagesContainer.scrollTop = messagesContainer.scrollHeight;
  
  // Save CD command to session history
  try {
    const { ensureSessionFile, saveSessionData } = require('../incrementalHistoryStorage');
    const { filePath, sessionData } = await ensureSessionFile(sessionId);
    sessionData.chat.push({
      timestamp: new Date().toISOString(),
      type: 'cd_command',
      command: command,
      result: error ? null : resultPath,
      error: error || null
    });
    await saveSessionData(sessionData, filePath);
  } catch (e) {
    console.warn('[MultiView] Failed to save CD command to history:', e);
  }
}

/**
 * Build file index for autocomplete
 */
async function buildFileIndex() {
  fileIndex = [];
  fileIndexReady = false;
  
  try {
    await indexDirectory(projectRoot, '', 0, 4);
    fileIndexReady = true;
    console.log(`[MultiView] Indexed ${fileIndex.length} files`);
  } catch (err) {
    console.warn('[MultiView] Failed to build file index:', err);
  }
}

/**
 * Recursively index directory
 */
async function indexDirectory(dirPath, relativePath, depth, maxDepth) {
  if (depth > maxDepth) return;
  
  try {
    const entries = await fs.readdir(dirPath, { withFileTypes: true });
    
    for (const entry of entries) {
      if (entry.name.startsWith('.') || 
          entry.name === 'node_modules' || 
          entry.name === '__pycache__' ||
          entry.name === 'dist' ||
          entry.name === 'build') {
        continue;
      }
      
      const entryRelPath = relativePath ? `${relativePath}/${entry.name}` : entry.name;
      
      fileIndex.push({
        name: entry.name,
        path: entryRelPath,
        isDir: entry.isDirectory()
      });
      
      if (entry.isDirectory()) {
        await indexDirectory(
          path.join(dirPath, entry.name),
          entryRelPath,
          depth + 1,
          maxDepth
        );
      }
    }
  } catch (_) {}
}

/**
 * Create resize handle between panels
 */
function createResizeHandle() {
  const handle = document.createElement('div');
  handle.className = 'multi-view-resize-handle';
  
  let startX = 0;
  let startWidths = [];
  let panels = [];
  
  handle.addEventListener('mousedown', (e) => {
    e.preventDefault();
    handle.classList.add('dragging');
    startX = e.clientX;
    
    // Get adjacent panels
    const prevPanel = handle.previousElementSibling;
    const nextPanel = handle.nextElementSibling;
    
    if (prevPanel && nextPanel) {
      panels = [prevPanel, nextPanel];
      startWidths = [prevPanel.offsetWidth, nextPanel.offsetWidth];
      
      const onMouseMove = (e) => {
        const delta = e.clientX - startX;
        const newWidth1 = Math.max(200, startWidths[0] + delta);
        const newWidth2 = Math.max(200, startWidths[1] - delta);
        
        panels[0].style.flex = `0 0 ${newWidth1}px`;
        panels[1].style.flex = `0 0 ${newWidth2}px`;
      };
      
      const onMouseUp = () => {
        handle.classList.remove('dragging');
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
        
        // Reset to flex: 1 for equal distribution
        panels.forEach(p => p.style.flex = '');
      };
      
      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
    }
  });
  
  return handle;
}

/**
 * Render session history in a panel
 */
async function renderSessionInPanel(sessionId, panel) {
  const messagesContainer = panel.querySelector('.multi-view-messages');
  if (!messagesContainer) return;
  
  try {
    const { loadChatHistory } = require('../historyStorage');
    const history = await loadChatHistory(sessionId);
    const entries = history.chat || [];
    
    const marked = require('marked');
    const { createRenderer } = require('../renderer');
    const { extractToolUsesAndSanitize } = require('../commands/parser');
    const { enhanceToolPlaceholders } = require('../helpers/toolRender');
    const { enhanceToolPlaceholdersV2 } = require('../helpers/toolRenderAdapter');
    
    marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });
    
    messagesContainer.innerHTML = '';
    
    for (let i = 0; i < entries.length; i++) {
      const entry = entries[i];
      
      // Handle CD command entries
      if (entry.type === 'cd_command') {
        const cdDiv = document.createElement('div');
        cdDiv.className = 'message user-message';
        cdDiv.style.marginBottom = '8px';
        
        const resultDiv = document.createElement('div');
        resultDiv.className = `cd-result-message${entry.error ? ' error' : ''}`;
        resultDiv.style.cssText = `
          display: flex;
          flex-direction: column;
          gap: 4px;
          padding: 10px 14px;
          background: ${entry.error ? '#fef2f2' : '#f0f9ff'};
          border: 1px solid ${entry.error ? '#fecaca' : '#bae6fd'};
          border-radius: 8px;
          font-family: 'SF Mono', Consolas, 'Courier New', monospace;
          font-size: 13px;
          color: ${entry.error ? '#dc2626' : '#0369a1'};
        `;
        
        if (entry.error) {
          resultDiv.innerHTML = `
            <div style="display:flex;align-items:center;gap:8px;">
              <span style="font-weight:600;color:#0c4a6e;">cd:</span>
              <span>${entry.command}</span>
            </div>
            <div style="color:#dc2626;font-size:12px;">${entry.error}</div>
          `;
        } else {
          resultDiv.innerHTML = `
            <div style="display:flex;align-items:center;gap:8px;">
              <span style="font-weight:600;color:#0c4a6e;">cd:</span>
              <span>${entry.command}</span>
            </div>
            <div style="color:#6b7280;font-size:12px;">${entry.result}</div>
          `;
        }
        
        cdDiv.appendChild(resultDiv);
        messagesContainer.appendChild(cdDiv);
        continue;
      }
      
      // User message
      if (entry.user && entry.user.text) {
        const userDiv = document.createElement('div');
        userDiv.className = 'message user-message';
        userDiv.setAttribute('data-entry-index', String(i));
        const userContent = document.createElement('div');
        userContent.className = 'message-content';
        userContent.textContent = entry.user.text;
        userDiv.appendChild(userContent);
        messagesContainer.appendChild(userDiv);
      }
      
      // GPT message
      if (entry.gpt) {
        // Skip rendering if GPT response is just "empty" placeholder or whitespace
        const gptText = String(entry.gpt || '').trim();
        const isEmptyPlaceholder = gptText === 'empty' || gptText === '' || gptText === '(empty)' || gptText === '[empty]';
        
        if (!isEmptyPlaceholder) {
          const gptDiv = document.createElement('div');
          gptDiv.className = 'message gpt-message';
          gptDiv.setAttribute('data-entry-index', String(i));
          const gptContent = document.createElement('div');
          gptContent.className = 'message-content';
          
          const parsed = extractToolUsesAndSanitize(entry.gpt || '');
          gptContent.innerHTML = marked.parse(parsed.sanitizedText || '');
          try { 
            enhanceToolPlaceholdersV2(gptContent, parsed.tools, []); 
          } catch (e) {
            console.warn('[multiView] V2 render failed, falling back:', e);
            try { enhanceToolPlaceholders(gptContent, parsed.tools, []); } catch (_) {}
          }
          
          gptDiv.appendChild(gptContent);
          messagesContainer.appendChild(gptDiv);
        }
      }
    }
    
    // Scroll to bottom
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
    
    // Check if session is responding and restore state
    const { getSessionState, isSessionResponding } = require('../concurrent/sessionStateManager');
    if (isSessionResponding(sessionId)) {
      panel.classList.add('responding');
      
      // Try to restore current streaming state
      const state = getSessionState(sessionId);
      if (state && (state.accumulatedContent || state.streamBuffer)) {
        // Check if there's already a GPT message to update
        let lastGpt = messagesContainer.querySelector('.message.gpt-message:last-child .message-content');
        
        // If no GPT message exists yet, create a placeholder
        if (!lastGpt) {
          const gptDiv = document.createElement('div');
          gptDiv.className = 'message gpt-message';
          const gptContent = document.createElement('div');
          gptContent.className = 'message-content';
          gptDiv.appendChild(gptContent);
          messagesContainer.appendChild(gptDiv);
          lastGpt = gptContent;
        }
        
        const { renderGptResponse } = require('../send/sendMessage');
        const content = state.accumulatedContent || state.streamBuffer || '';
        renderGptResponse(lastGpt, content, state.toolRuns || []);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
      } else {
        // Session is responding but no content yet - show thinking placeholder
        let lastGpt = messagesContainer.querySelector('.message.gpt-message:last-child .message-content');
        if (!lastGpt) {
          const gptDiv = document.createElement('div');
          gptDiv.className = 'message gpt-message';
          const gptContent = document.createElement('div');
          gptContent.className = 'message-content';
          gptContent.innerHTML = '<div class="thinking-inline"><span class="typing-text">GPT is thinking...</span></div>';
          gptDiv.appendChild(gptContent);
          messagesContainer.appendChild(gptDiv);
          messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }
      }
    }
    
  } catch (e) {
    console.error('[MultiView] Error rendering session:', e);
    messagesContainer.innerHTML = '<div class="error">Failed to load session</div>';
  }
}

/**
 * Send a message in a multi-view panel
 * This sends a message to a specific session without switching the main view
 */
async function sendMessageInPanel(sessionId, text, inputEl, model, hoster, reasoning) {
  // Clear input
  inputEl.value = '';
  
  // Clear autocomplete
  const inputWrapper = inputEl.parentElement;
  const autocompleteHint = inputWrapper?.querySelector('.multi-view-autocomplete-hint');
  clearPanelAutocomplete(sessionId, inputWrapper, autocompleteHint);
  
  // Get the panel's messages container
  const containerInfo = sessionContainers.get(sessionId);
  if (!containerInfo) return;
  
  const { messages: messagesContainer, panel } = containerInfo;
  
  // Add user message to panel
  const userDiv = document.createElement('div');
  userDiv.className = 'message user-message';
  const userContent = document.createElement('div');
  userContent.className = 'message-content';
  userContent.textContent = text;
  userDiv.appendChild(userContent);
  messagesContainer.appendChild(userDiv);
  
  // Add GPT placeholder
  const gptDiv = document.createElement('div');
  gptDiv.className = 'message gpt-message';
  const gptContent = document.createElement('div');
  gptContent.className = 'message-content';
  gptContent.innerHTML = '<div class="thinking-inline"><span class="typing-text">GPT is thinking...</span></div>';
  gptDiv.appendChild(gptContent);
  messagesContainer.appendChild(gptDiv);
  
  // Scroll to bottom
  messagesContainer.scrollTop = messagesContainer.scrollHeight;
  
  // Mark panel as responding
  panel.classList.add('responding');
  
  // Send the message using the session's context
  // We need to temporarily switch sessions, send, then switch back
  // The concurrent session system will handle the actual rendering
  try {
    const sessionManager = require('../sessionManager');
    const currentSession = await sessionManager.getSessionId();
    const wasOnDifferentSession = currentSession !== sessionId;
    
    // Switch to target session
    if (wasOnDifferentSession) {
      await sessionManager.setSessionId(sessionId);
    }
    
    // Set the input value for MainAssistant to pick up
    const mainInput = document.getElementById('user-input');
    const originalValue = mainInput ? (mainInput.value || mainInput.innerText) : '';
    const originalFiles = window.droppedFiles || [];
    
    if (mainInput) {
      if (mainInput.value !== undefined) mainInput.value = text;
      else mainInput.innerText = text;
    }
    window.droppedFiles = [];
    
    // Call MainAssistant - it will handle everything including multi-view updates
    const { MainAssistant } = require('../send/sendMessage');
    
    // Don't await - let it run in background so we can switch back immediately
    MainAssistant().catch(e => {
      console.error('[MultiView] MainAssistant error:', e);
      gptContent.innerHTML = `<div class="error">Error: ${e.message}</div>`;
      panel.classList.remove('responding');
    }).finally(() => {
      // Remove responding state when done
      panel.classList.remove('responding');
    });
    
    // Small delay to let MainAssistant start processing
    await new Promise(resolve => setTimeout(resolve, 100));
    
    // Switch back to original session if we were on a different one
    if (wasOnDifferentSession) {
      await sessionManager.setSessionId(currentSession);
      // Restore original input
      if (mainInput) {
        if (mainInput.value !== undefined) mainInput.value = originalValue;
        else mainInput.innerText = originalValue;
      }
      window.droppedFiles = originalFiles;
    }
    
  } catch (e) {
    console.error('[MultiView] Error sending message:', e);
    gptContent.innerHTML = `<div class="error">Error: ${e.message}</div>`;
    panel.classList.remove('responding');
  }
}

/**
 * Update multi-view mode based on active sessions
 */
function updateMultiViewMode() {
  const chatContainer = document.getElementById('chat-container');
  if (!chatContainer) return;
  
  if (multiViewSessions.size > 0) {
    chatContainer.classList.add('multi-view-active');
  } else {
    chatContainer.classList.remove('multi-view-active');
  }
}

/**
 * Update tab indicator for multi-viewed session
 */
function updateTabMultiViewIndicator(sessionId, isMultiViewed) {
  const tab = document.querySelector(`.auaci-chat-tab[data-session-id="${sessionId}"]`);
  if (tab) {
    if (isMultiViewed) {
      tab.classList.add('multi-viewed');
    } else {
      tab.classList.remove('multi-viewed');
    }
  }
}

/**
 * Get multi-view panel for a session
 */
function getMultiViewPanel(sessionId) {
  return sessionContainers.get(sessionId);
}

/**
 * Check if a session is in multi-view
 */
function isInMultiView(sessionId) {
  return multiViewSessions.has(sessionId);
}

/**
 * Get all multi-viewed session IDs
 */
function getMultiViewSessions() {
  return Array.from(multiViewSessions);
}

/**
 * Update a multi-view panel's content (for live streaming)
 */
function updateMultiViewPanelContent(sessionId, content, toolRuns = []) {
  const containerInfo = sessionContainers.get(sessionId);
  if (!containerInfo) return;
  
  const { messages: messagesContainer } = containerInfo;
  const lastGpt = messagesContainer.querySelector('.message.gpt-message:last-child .message-content');
  
  if (lastGpt) {
    try {
      const { renderGptResponse } = require('../send/sendMessage');
      renderGptResponse(lastGpt, content, toolRuns);
      messagesContainer.scrollTop = messagesContainer.scrollHeight;
    } catch (e) {
      console.warn('[MultiView] Error updating panel content:', e);
    }
  }
}

module.exports = {
  initMultiView,
  addToMultiView,
  removeFromMultiView,
  isInMultiView,
  getMultiViewSessions,
  getMultiViewPanel,
  updateMultiViewPanelContent
};
