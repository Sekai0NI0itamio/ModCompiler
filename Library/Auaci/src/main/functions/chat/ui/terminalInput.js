// src/main/functions/chat/ui/terminalInput.js
// Dynamic Terminal Chat - CD navigation and file path autocomplete in chat input

const fs = require('fs').promises;
const path = require('path');

const STYLE_ID = 'auaci-terminal-input-styles';

// Current working directory for the chat (starts at project root)
let currentCwd = null;
let projectRoot = null;

// Session directory - the directory where the current session file is stored
let sessionDirectory = null;

// Autocomplete state
let autocompleteText = '';
let autocompleteVisible = false;
let lastInputText = '';

// File index for fast autocomplete (now only stores current directory entries)
let fileIndex = [];
let fileIndexReady = false;

const CSS = `
/* Autocomplete ghost text overlay - positioned inline with text */
#input-wrapper {
  position: relative;
  display: block;
}

#autocomplete-ghost {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
  color: #9ca3af;
  opacity: 0.7;
  white-space: pre;
  z-index: 1;
}

/* CD command result message */
.cd-result-message {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 14px;
  margin: 8px 0;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  font-family: 'SF Mono', Consolas, 'Courier New', monospace;
  font-size: 13px;
  color: #0369a1;
}

.cd-result-message.error {
  background: #fef2f2;
  border-color: #fecaca;
  color: #dc2626;
}

.cd-result-message .cd-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cd-result-message .cd-label {
  font-weight: 600;
  color: #0c4a6e;
}

.cd-result-message .cd-command {
  color: #0c4a6e;
}

.cd-result-message .cd-current-path {
  color: #6b7280;
  font-size: 12px;
}

/* Autocomplete hint badge */
.autocomplete-hint {
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

.autocomplete-hint.visible {
  opacity: 1;
}
`;

/**
 * Initialize the terminal input system
 */
async function initTerminalInput() {
  injectStyles();
  
  // Get project root
  try {
    const { ipcRenderer } = require('electron');
    projectRoot = await ipcRenderer.invoke('get-project-root');
  } catch (_) {}
  if (!projectRoot) {
    try { projectRoot = process.cwd(); } catch (_) {}
  }
  
  currentCwd = projectRoot;
  
  // Initialize session directory based on current session
  await updateSessionDirectory();
  
  // Create UI elements
  createCwdIndicator();
  createAutocompleteOverlay();
  
  // Setup input listeners
  setupInputListeners();
  
  // Build file index for current directory only (single layer)
  buildFileIndex();
  
  // Listen for session changes to update session directory
  window.addEventListener('session-changed', async () => {
    await updateSessionDirectory();
    console.log('[TerminalInput] Session changed, updated session directory:', sessionDirectory);
  });
  
  console.log('[TerminalInput] Initialized with root:', projectRoot);
  console.log('[TerminalInput] Session directory:', sessionDirectory);
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
 * Create the autocomplete hint element
 */
function createCwdIndicator() {
  const inputContainer = document.getElementById('input-container');
  if (!inputContainer) return;
  
  // Create autocomplete hint badge
  if (!document.querySelector('.autocomplete-hint')) {
    const hint = document.createElement('div');
    hint.className = 'autocomplete-hint';
    hint.textContent = 'Tab ↹';
    inputContainer.style.position = 'relative';
    inputContainer.appendChild(hint);
  }
}

/**
 * Create autocomplete ghost text element
 */
function createAutocompleteOverlay() {
  const userInput = document.getElementById('user-input');
  if (!userInput) return;
  
  // Wrap input in a relative container if not already
  let wrapper = userInput.parentElement;
  if (!wrapper || wrapper.id !== 'input-wrapper') {
    wrapper = document.createElement('div');
    wrapper.id = 'input-wrapper';
    wrapper.style.position = 'relative';
    wrapper.style.display = 'inline-block';
    wrapper.style.width = '100%';
    userInput.parentNode.insertBefore(wrapper, userInput);
    wrapper.appendChild(userInput);
  }
  
  // We'll create the ghost element dynamically when needed
}

/**
 * No longer showing CWD indicator - removed per user request
 */
function updateCwdIndicator() {
  // CWD is now shown only in CD command results
}

/**
 * Update session directory based on current active session
 */
async function updateSessionDirectory() {
  try {
    const { getSessionId, getSessionFilePath } = require('../sessionManager');
    const sessionId = await getSessionId();
    if (sessionId) {
      const sessionFilePath = await getSessionFilePath(sessionId);
      if (sessionFilePath) {
        // Session directory is the parent of the session file (the 'sessions' folder)
        sessionDirectory = path.dirname(sessionFilePath);
        console.log('[TerminalInput] Updated session directory:', sessionDirectory);
      }
    }
  } catch (err) {
    console.warn('[TerminalInput] Failed to get session directory:', err);
    sessionDirectory = null;
  }
}

/**
 * Build file index for fast autocomplete
 * Now only indexes the current directory (single layer, no recursion)
 */
async function buildFileIndex() {
  fileIndex = [];
  fileIndexReady = false;
  
  try {
    await indexCurrentDirectory(currentCwd || projectRoot);
    fileIndexReady = true;
    console.log(`[TerminalInput] Indexed ${fileIndex.length} files in current directory`);
  } catch (err) {
    console.warn('[TerminalInput] Failed to build file index:', err);
  }
}

/**
 * Index only the current directory (single layer, no recursion)
 */
async function indexCurrentDirectory(dirPath) {
  try {
    const entries = await fs.readdir(dirPath, { withFileTypes: true });
    
    for (const entry of entries) {
      // Skip hidden files and common ignore patterns
      if (entry.name.startsWith('.') || 
          entry.name === 'node_modules' || 
          entry.name === '__pycache__' ||
          entry.name === 'dist' ||
          entry.name === 'build') {
        continue;
      }
      
      fileIndex.push({
        name: entry.name,
        path: entry.name, // Just the name since we're only indexing current directory
        isDir: entry.isDirectory()
      });
    }
  } catch (_) {}
}

/**
 * Setup input listeners for CD commands and autocomplete
 */
function setupInputListeners() {
  const userInput = document.getElementById('user-input');
  if (!userInput) return;
  
  // Handle Tab key for autocomplete - ALWAYS prevent default to avoid inserting tab character
  userInput.addEventListener('keydown', async (e) => {
    if (e.key === 'Tab') {
      e.preventDefault(); // Always prevent tab character insertion
      if (autocompleteText && autocompleteVisible) {
        applyAutocomplete(userInput);
      }
      // If no autocomplete available, do nothing (don't insert tab)
      return;
    }
  });
  
  // Handle Enter for CD commands (capture phase to run first)
  userInput.addEventListener('keydown', async (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      const text = getInputText(userInput).trim();
      
      // Check if it's a CD command
      if (text.toLowerCase().startsWith('cd ') || text.toLowerCase() === 'cd') {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        await handleCdCommand(text, userInput);
        return;
      }
    }
  }, true);
  
  // Handle input changes for autocomplete preview
  userInput.addEventListener('input', async () => {
    const text = getInputText(userInput);
    if (text !== lastInputText) {
      lastInputText = text;
      await updateAutocompletePreview(text, userInput);
    }
  });
}

/**
 * Get text from input element
 */
function getInputText(el) {
  if (el.value !== undefined && el.value !== null) {
    return el.value;
  }
  return el.innerText || '';
}

/**
 * Set text in input element
 */
function setInputText(el, text) {
  if (el.value !== undefined) {
    el.value = text;
  } else {
    el.innerText = text;
  }
  try {
    el.dispatchEvent(new Event('input', { bubbles: true }));
  } catch (_) {}
}

/**
 * Handle CD command
 */
async function handleCdCommand(text, inputEl) {
  const parts = text.trim().split(/\s+/);
  const targetPath = parts.length > 1 ? parts.slice(1).join(' ') : '';
  
  // Ensure we have a valid current directory
  const baseCwd = currentCwd || projectRoot || process.cwd();
  
  let newCwd;
  let errorMsg = null;
  
  if (!targetPath || targetPath === '~') {
    newCwd = projectRoot || process.cwd();
  } else if (targetPath === '..') {
    // Go up one directory - no restrictions
    newCwd = path.dirname(baseCwd);
  } else if (targetPath === '-') {
    newCwd = baseCwd;
  } else if (path.isAbsolute(targetPath)) {
    // Allow any absolute path
    newCwd = targetPath;
  } else {
    // Resolve relative path from current directory (handles ../ paths)
    newCwd = path.resolve(baseCwd, targetPath);
  }
  
  console.log('[TerminalInput] CD command:', { targetPath, baseCwd, newCwd });
  
  // Verify the directory exists using multiple methods for robustness
  try {
    // First try fs.stat
    const stat = await fs.stat(newCwd);
    if (!stat.isDirectory()) {
      errorMsg = 'Not a directory';
      console.log('[TerminalInput] CD error: path exists but is not a directory:', newCwd);
    }
  } catch (err) {
    // fs.stat failed - try fs.access as a fallback
    console.log('[TerminalInput] CD stat failed, trying access:', err.code, err.message);
    
    try {
      await fs.access(newCwd);
      // If access succeeds, the directory exists - stat might have failed for other reasons
      // Try readdir to confirm it's a directory
      await fs.readdir(newCwd);
      // If we get here, it's a valid directory
      console.log('[TerminalInput] CD: directory confirmed via readdir:', newCwd);
      errorMsg = null; // Clear any error
    } catch (accessErr) {
      console.log('[TerminalInput] CD access/readdir also failed:', accessErr.code, accessErr.message);
      
      // Check if it's a permission error vs not found
      if (accessErr.code === 'ENOENT' || err.code === 'ENOENT') {
        errorMsg = `Directory not found: ${targetPath}`;
      } else if (accessErr.code === 'EACCES' || err.code === 'EACCES') {
        errorMsg = `Permission denied: ${targetPath}`;
      } else if (accessErr.code === 'ENOTDIR' || err.code === 'ENOTDIR') {
        errorMsg = `Not a directory: ${targetPath}`;
      } else {
        errorMsg = `Cannot access: ${targetPath} (${accessErr.code || err.code || err.message})`;
      }
    }
  }
  
  // Clear input
  setInputText(inputEl, '');
  clearAutocomplete();
  
  // Render the CD result as a user message
  await renderCdResult(text, errorMsg ? baseCwd : newCwd, errorMsg);
  
  // IMPORTANT: Only update currentCwd if there was no error
  if (!errorMsg) {
    currentCwd = newCwd;
    updateCwdIndicator();
    // Rebuild file index for the new directory (single layer only)
    await buildFileIndex();
    console.log('[TerminalInput] Changed directory to:', currentCwd);
  } else {
    console.log('[TerminalInput] CD failed, staying in:', baseCwd);
  }
}

/**
 * Render CD command result as a message in chat
 */
async function renderCdResult(command, resultPath, error = null) {
  const chatMessages = document.getElementById('chat-messages');
  if (!chatMessages) return;
  
  const messageDiv = document.createElement('div');
  messageDiv.className = 'message user-message';
  messageDiv.style.marginBottom = '8px';
  
  const resultDiv = document.createElement('div');
  resultDiv.className = `cd-result-message${error ? ' error' : ''}`;
  
  if (error) {
    resultDiv.innerHTML = `
      <div class="cd-line">
        <span class="cd-label">cd:</span>
        <span class="cd-command">${escapeHtml(command)}</span>
      </div>
      <div class="cd-current-path" style="color:#dc2626">${escapeHtml(error)}</div>
    `;
  } else {
    resultDiv.innerHTML = `
      <div class="cd-line">
        <span class="cd-label">cd:</span>
        <span class="cd-command">${escapeHtml(command)}</span>
      </div>
      <div class="cd-current-path">${escapeHtml(resultPath)}</div>
    `;
  }
  
  messageDiv.appendChild(resultDiv);
  chatMessages.appendChild(messageDiv);
  
  // Scroll to bottom
  chatMessages.scrollTop = chatMessages.scrollHeight;
  
  // Save CD command to session history so it persists on reload
  try {
    const { getSessionId } = require('../sessionManager');
    const sessionId = await getSessionId();
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
    console.warn('[TerminalInput] Failed to save CD command to history:', e);
  }
}

/**
 * Escape HTML
 */
function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/**
 * Get autocomplete suggestion for current input
 * Matches by the LAST WORD (separated by spaces), not the full sentence
 * Takes into account the current working directory for relative path matching
 */
async function getAutocompleteSuggestion(text) {
  if (!text) return null;
  
  // Get the last word being typed (words are separated by spaces)
  const words = text.split(/\s+/);
  const lastWord = words[words.length - 1];
  
  if (!lastWord || lastWord.length < 1) return null;
  
  // Check if the last word contains a path separator (user is typing a path)
  const hasPathSeparator = lastWord.includes('/') || lastWord.includes('\\');
  
  if (hasPathSeparator) {
    // Path-based autocomplete - search the actual filesystem relative to currentCwd
    return await getPathBasedSuggestion(lastWord);
  } else {
    // Simple name-based autocomplete from file index
    // But filter to show files relative to currentCwd
    return await getNameBasedSuggestion(lastWord);
  }
}

/**
 * Get suggestion for path-based input (e.g., "src/comp" -> "src/components")
 * Searches relative to the current working directory
 */
async function getPathBasedSuggestion(partialPath) {
  try {
    // Normalize path separators
    const normalizedPath = partialPath.replace(/\\/g, '/');
    const lastSlashIdx = normalizedPath.lastIndexOf('/');
    
    // Split into directory part and filename part
    const dirPart = lastSlashIdx >= 0 ? normalizedPath.slice(0, lastSlashIdx) : '';
    const namePart = lastSlashIdx >= 0 ? normalizedPath.slice(lastSlashIdx + 1) : normalizedPath;
    
    // Resolve the directory to search in (relative to currentCwd)
    const searchDir = dirPart 
      ? path.resolve(currentCwd || projectRoot, dirPart)
      : (currentCwd || projectRoot);
    
    // Read directory entries
    let entries;
    try {
      entries = await fs.readdir(searchDir, { withFileTypes: true });
    } catch {
      return null; // Directory doesn't exist
    }
    
    // Filter entries that start with the name part
    const namePartLower = namePart.toLowerCase();
    let matches = entries.filter(e => {
      // Skip hidden files
      if (e.name.startsWith('.')) return false;
      // Skip common ignore patterns
      if (e.name === 'node_modules' || e.name === '__pycache__' || e.name === 'dist' || e.name === 'build') return false;
      // Match by prefix
      return e.name.toLowerCase().startsWith(namePartLower);
    });
    
    if (matches.length === 0) return null;
    
    // Sort by relevance
    matches.sort((a, b) => {
      // Prefer exact matches
      if (a.name.toLowerCase() === namePartLower && b.name.toLowerCase() !== namePartLower) return -1;
      if (b.name.toLowerCase() === namePartLower && a.name.toLowerCase() !== namePartLower) return 1;
      // Prefer directories (for path completion)
      if (a.isDirectory() && !b.isDirectory()) return -1;
      if (!a.isDirectory() && b.isDirectory()) return 1;
      // Then by name length
      return a.name.length - b.name.length;
    });
    
    const bestMatch = matches[0];
    
    // Return the completion (rest of the name, plus / if it's a directory)
    const completion = bestMatch.name.slice(namePart.length);
    const suffix = bestMatch.isDirectory() ? '/' : '';
    
    return completion + suffix || null;
  } catch (err) {
    console.warn('[TerminalInput] Path-based suggestion error:', err);
    return null;
  }
}

/**
 * Get suggestion for simple name-based input
 * Only searches files in the current working directory (single layer, no recursion)
 */
async function getNameBasedSuggestion(searchTerm) {
  const searchTermLower = searchTerm.toLowerCase();
  
  // Only search in the current working directory (single layer)
  try {
    const cwdEntries = await fs.readdir(currentCwd || projectRoot, { withFileTypes: true });
    const cwdMatches = cwdEntries.filter(e => {
      if (e.name.startsWith('.')) return false;
      if (e.name === 'node_modules' || e.name === '__pycache__' || e.name === 'dist' || e.name === 'build') return false;
      return e.name.toLowerCase().startsWith(searchTermLower);
    });
    
    if (cwdMatches.length > 0) {
      // Sort matches
      cwdMatches.sort((a, b) => {
        if (a.name.toLowerCase() === searchTermLower && b.name.toLowerCase() !== searchTermLower) return -1;
        if (b.name.toLowerCase() === searchTermLower && a.name.toLowerCase() !== searchTermLower) return 1;
        return a.name.length - b.name.length;
      });
      
      const bestMatch = cwdMatches[0];
      const completion = bestMatch.name.slice(searchTerm.length);
      const suffix = bestMatch.isDirectory() ? '/' : '';
      return completion + suffix || null;
    }
  } catch (err) {
    console.warn('[TerminalInput] Name-based suggestion error:', err);
  }
  
  // No matches found in current directory - don't fall back to other directories
  return null;
}

/**
 * Update autocomplete preview (ghost text) - renders inline after cursor
 */
async function updateAutocompletePreview(text, inputEl) {
  const hint = document.querySelector('.autocomplete-hint');
  
  const suggestion = await getAutocompleteSuggestion(text);
  
  if (suggestion) {
    autocompleteText = suggestion;
    autocompleteVisible = true;
    if (hint) hint.classList.add('visible');
    
    // Create or update ghost text element
    showGhostText(inputEl, text, suggestion);
  } else {
    clearAutocomplete();
  }
}

/**
 * Show ghost text after the current input text
 * Handles multi-line input by positioning at the cursor's current line
 */
function showGhostText(inputEl, currentText, suggestion) {
  // Remove existing ghost
  const existingGhost = document.getElementById('autocomplete-ghost');
  if (existingGhost) existingGhost.remove();
  
  if (!suggestion) return;
  
  const wrapper = inputEl.parentElement;
  if (!wrapper) return;
  
  const inputStyle = getComputedStyle(inputEl);
  const paddingLeft = parseFloat(inputStyle.paddingLeft) || 0;
  const paddingTop = parseFloat(inputStyle.paddingTop) || 0;
  const fontSize = parseFloat(inputStyle.fontSize) || 14;
  const lineHeight = parseFloat(inputStyle.lineHeight) || fontSize * 1.2 || 20;
  
  // Get cursor position to determine which line we're on
  const cursorPos = inputEl.selectionStart || currentText.length;
  const textBeforeCursor = currentText.substring(0, cursorPos);
  
  // Split text to find current line based on cursor position
  const linesBeforeCursor = textBeforeCursor.split('\n');
  const currentLineIndex = linesBeforeCursor.length - 1;
  const currentLineText = linesBeforeCursor[currentLineIndex] || '';
  
  // Create a measuring span to get the width of the current line's text
  const measureSpan = document.createElement('span');
  measureSpan.style.cssText = `
    position: absolute;
    visibility: hidden;
    white-space: pre;
    font: ${inputStyle.font};
  `;
  measureSpan.textContent = currentLineText;
  document.body.appendChild(measureSpan);
  const textWidth = measureSpan.offsetWidth;
  document.body.removeChild(measureSpan);
  
  // Calculate vertical position based on current line, accounting for scroll
  const scrollTop = inputEl.scrollTop || 0;
  const topPosition = paddingTop + (currentLineIndex * lineHeight) - scrollTop;
  
  // Create ghost element
  const ghost = document.createElement('span');
  ghost.id = 'autocomplete-ghost';
  ghost.textContent = suggestion;
  ghost.style.cssText = `
    position: absolute;
    top: ${topPosition}px;
    left: ${paddingLeft + textWidth}px;
    pointer-events: none;
    color: #9ca3af;
    opacity: 0.7;
    white-space: pre;
    font: ${inputStyle.font};
    line-height: ${inputStyle.lineHeight};
    z-index: 1;
  `;
  
  wrapper.appendChild(ghost);
}

/**
 * Clear autocomplete state
 */
function clearAutocomplete() {
  autocompleteText = '';
  autocompleteVisible = false;
  
  const hint = document.querySelector('.autocomplete-hint');
  if (hint) hint.classList.remove('visible');
  
  const ghost = document.getElementById('autocomplete-ghost');
  if (ghost) ghost.remove();
}

/**
 * Apply autocomplete suggestion
 */
function applyAutocomplete(inputEl) {
  const text = getInputText(inputEl);
  
  if (autocompleteText) {
    const newText = text + autocompleteText;
    
    // Clear autocomplete first to prevent re-triggering
    const savedAutocomplete = autocompleteText;
    clearAutocomplete();
    lastInputText = newText;
    
    // Set the text
    if (inputEl.value !== undefined) {
      inputEl.value = newText;
    } else {
      inputEl.innerText = newText;
    }
    
    // Move cursor to the END of the text
    inputEl.focus();
    const endPos = newText.length;
    
    // Use setTimeout to ensure cursor positioning happens after DOM update
    setTimeout(() => {
      if (inputEl.setSelectionRange) {
        inputEl.setSelectionRange(endPos, endPos);
      } else if (inputEl.selectionStart !== undefined) {
        inputEl.selectionStart = endPos;
        inputEl.selectionEnd = endPos;
      }
      // For contenteditable elements
      if (inputEl.isContentEditable) {
        const range = document.createRange();
        const sel = window.getSelection();
        if (inputEl.childNodes.length > 0) {
          const textNode = inputEl.childNodes[0];
          range.setStart(textNode, textNode.length);
          range.collapse(true);
          sel.removeAllRanges();
          sel.addRange(range);
        }
      }
    }, 0);
  }
}

/**
 * Get current working directory
 */
function getCurrentCwd() {
  return currentCwd;
}

/**
 * Set current working directory (for external use)
 */
function setCurrentCwd(newCwd) {
  if (newCwd) {
    currentCwd = newCwd;
    // Rebuild file index for the new directory
    buildFileIndex();
  }
}

/**
 * Get session directory
 */
function getSessionDirectory() {
  return sessionDirectory;
}

/**
 * Refresh file index (call after file operations or directory change)
 */
async function refreshFileIndex() {
  await buildFileIndex();
}

/**
 * Refresh session directory (call when session changes)
 */
async function refreshSessionDirectory() {
  await updateSessionDirectory();
}

/**
 * Render a CD command from history (used when loading session)
 */
function renderCdCommandFromHistory(entry) {
  const chatMessages = document.getElementById('chat-messages');
  if (!chatMessages) return;
  
  const messageDiv = document.createElement('div');
  messageDiv.className = 'message user-message';
  messageDiv.style.marginBottom = '8px';
  
  const resultDiv = document.createElement('div');
  resultDiv.className = `cd-result-message${entry.error ? ' error' : ''}`;
  
  if (entry.error) {
    resultDiv.innerHTML = `
      <div class="cd-line">
        <span class="cd-label">cd:</span>
        <span class="cd-command">${escapeHtml(entry.command)}</span>
      </div>
      <div class="cd-current-path" style="color:#dc2626">${escapeHtml(entry.error)}</div>
    `;
  } else {
    resultDiv.innerHTML = `
      <div class="cd-line">
        <span class="cd-label">cd:</span>
        <span class="cd-command">${escapeHtml(entry.command)}</span>
      </div>
      <div class="cd-current-path">${escapeHtml(entry.result)}</div>
    `;
    // Update current CWD to the last successful CD
    if (entry.result) {
      currentCwd = entry.result;
    }
  }
  
  messageDiv.appendChild(resultDiv);
  chatMessages.appendChild(messageDiv);
}

module.exports = {
  initTerminalInput,
  getCurrentCwd,
  setCurrentCwd,
  getSessionDirectory,
  refreshFileIndex,
  refreshSessionDirectory,
  renderCdCommandFromHistory
};
