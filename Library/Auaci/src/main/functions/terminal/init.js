function deleteSession(sessionId) {
  const s = sessions.get(sessionId);
  try { ipcRenderer.send('terminal-stop', { sessionId }); } catch (_) {}
  sessions.delete(sessionId);
  // Remove tab
  const el = document.querySelector(`#terminal-tabs .term-tab[data-session-id="${sessionId}"]`);
  if (el && el.parentNode) el.parentNode.removeChild(el);
  // If active was deleted, switch to another or hide terminal if none
  if (activeSessionId === sessionId) {
    const nextId = Array.from(sessions.keys())[0];
    if (nextId) {
      switchSession(nextId);
    } else {
      hideTerminal();
    }
  }
  persistSessionsStateDebounced();
}

let termTabMenuEl = null;
function openTermTabMenu(event, sessionId) {
  event.preventDefault();
  closeTermTabMenu();
  termTabMenuEl = document.createElement('div');
  termTabMenuEl.id = 'term-tab-menu';
  termTabMenuEl.style.position = 'fixed';
  termTabMenuEl.style.left = `${event.clientX}px`;
  termTabMenuEl.style.top = `${event.clientY}px`;
  termTabMenuEl.style.background = '#ffffff';
  termTabMenuEl.style.border = '1px solid #e5e7eb';
  termTabMenuEl.style.borderRadius = '6px';
  termTabMenuEl.style.boxShadow = '0 12px 36px rgba(0,0,0,0.18)';
  termTabMenuEl.style.padding = '6px 4px';
  termTabMenuEl.style.zIndex = '2147483647';
  termTabMenuEl.style.minWidth = '180px';

  const mkItem = (label, onClick) => {
    const b = document.createElement('button');
    b.textContent = label;
    b.style.display = 'block';
    b.style.width = '100%';
    b.style.textAlign = 'left';
    b.style.background = 'transparent';
    b.style.border = 'none';
    b.style.padding = '10px 12px';
    b.style.cursor = 'pointer';
    b.addEventListener('click', () => { onClick(); closeTermTabMenu(); });
    b.addEventListener('mouseover', () => { b.style.background = 'rgba(59,130,246,0.06)'; });
    b.addEventListener('mouseout', () => { b.style.background = 'transparent'; });
    return b;
  };

  termTabMenuEl.appendChild(mkItem('Rename session', () => {
    const s = sessions.get(sessionId);
    const current = (s && s.title) || '';
    const next = prompt('Rename session to:', current);
    if (next && next.trim()) {
      if (s) s.title = next.trim();
      const labelEl = document.querySelector(`#terminal-tabs .term-tab[data-session-id="${sessionId}"] .term-tab-label`);
      if (labelEl) labelEl.textContent = next.trim();
      persistSessionsStateDebounced();
    }
  }));

  termTabMenuEl.appendChild(mkItem('Force stop process', () => {
    try { ipcRenderer.send('terminal-stop', { sessionId }); } catch (_) {}
    const s = sessions.get(sessionId);
    if (s) { s.backendStarted = false; s.runningCommand = null; updateTabRunningUI(sessionId); }
  }));

  document.body.appendChild(termTabMenuEl);
  setTimeout(() => {
    window.addEventListener('click', closeTermTabMenu, { once: true });
    window.addEventListener('contextmenu', closeTermTabMenu, { once: true });
  }, 0);
}
function closeTermTabMenu() {
  if (termTabMenuEl && termTabMenuEl.parentNode) termTabMenuEl.parentNode.removeChild(termTabMenuEl);
  termTabMenuEl = null;
}
function updateTabRunningUI(sessionId) {
  const s = sessions.get(sessionId);
  const el = document.querySelector(`#terminal-tabs .term-tab[data-session-id="${sessionId}"]`);
  if (!el) return;
  const running = !!(s && s.runningCommand);
  const eligible = running && (!s.runningSince || (Date.now() - s.runningSince) >= 1500);
  el.classList.toggle('running', eligible);
  el.title = eligible ? `Running: ${s.runningCommand}` : '';
}

function escapeForShell(val = '') {
  // naive escaping: wrap in single quotes and escape inner single quotes
  const s = String(val);
  if (s === '') return "''";
  return `'${s.replace(/'/g, "'\\''")}'`;
}

/**
 * Clean terminal escape sequences and control characters from a string
 * This is important for sanitizing user input before storing/using it
 */
function cleanTerminalEscapes(str) {
  if (!str || typeof str !== 'string') return str;
  return str
    // Remove bracketed paste mode sequences (most common issue)
    .replace(/\[200~/g, '')
    .replace(/\[201~/g, '')
    .replace(/\x1b\[\?2004[hl]/g, '')
    // Remove standard ANSI escape sequences
    .replace(/\x1b\[[0-9;]*[a-zA-Z]/g, '')
    // Remove OSC sequences
    .replace(/\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)/g, '')
    // Remove other escape sequences
    .replace(/\x1b[PX^_].*?\x1b\\/g, '')
    // Remove carriage returns
    .replace(/\r/g, '')
    // Remove null bytes and other control characters (except newline/tab)
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '')
    // Clean up any remaining escape character
    .replace(/\x1b/g, '')
    .trim();
}

/**
 * Validate and clean a path for storage/restoration
 * Returns null if the path is invalid or suspicious
 */
function sanitizePath(pathStr) {
  if (!pathStr || typeof pathStr !== 'string') return null;
  
  // Clean escape sequences first
  let cleaned = cleanTerminalEscapes(pathStr);
  
  // Remove surrounding quotes if present
  if ((cleaned.startsWith('"') && cleaned.endsWith('"')) ||
      (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
    cleaned = cleaned.slice(1, -1);
  }
  
  // Check for suspicious patterns that indicate corrupted data
  if (cleaned.includes('[200~') || cleaned.includes('[201~') ||
      cleaned.includes('\x1b') || cleaned.includes('\x00') ||
      cleaned.length > 1000) {
    return null;
  }
  
  // Basic path validation - should start with / or ~ or be relative
  if (!cleaned.match(/^[\/~.]/) && !cleaned.match(/^[a-zA-Z0-9_-]/)) {
    return null;
  }
  
  return cleaned || null;
}

/**
 * Validate and clean an environment variable value
 */
function sanitizeEnvValue(val) {
  if (val === undefined || val === null) return null;
  const cleaned = cleanTerminalEscapes(String(val));
  // Don't store if it looks corrupted
  if (cleaned.includes('[200~') || cleaned.includes('[201~')) {
    return null;
  }
  return cleaned;
}

function parseAndRecordSessionState(session, line) {
  try {
    // Clean the line first to remove any escape sequences
    const cleanLine = cleanTerminalEscapes(line);
    if (!cleanLine) return;
    
    // cd handling
    let m = cleanLine.match(/^\s*cd\s+(.+)$/);
    if (m) {
      const arg = sanitizePath(m[1].trim());
      if (arg) {
        session.cwd = arg;
      }
      return;
    }
    // export VAR=VALUE or VAR=VALUE
    m = cleanLine.match(/^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.+)\s*$/);
    if (m) {
      const key = m[1];
      let val = sanitizeEnvValue(m[2]);
      if (val !== null) {
        session.env = session.env || {};
        session.env[key] = val;
      }
      return;
    }
    // unset VAR
    m = cleanLine.match(/^\s*unset\s+([A-Za-z_][A-Za-z0-9_]*)\s*$/);
    if (m) {
      const key = m[1];
      if (session.env) delete session.env[key];
      return;
    }
    // source file or . file
    m = cleanLine.match(/^\s*(?:source|\.)\s+(.+)$/);
    if (m) {
      const file = sanitizePath(m[1].trim());
      if (file) {
        session.sources = session.sources || [];
        if (!session.sources.includes(file)) session.sources.push(file);
      }
      return;
    }
  } catch (_) {}
}

// ---------- Sessions persistence ----------
const fs = require('fs');
const path = require('path');
let saveTimer = null;

function sessionStorePaths() {
  const root = defaultCwd || process.cwd();
  const dir = path.join(root, '.auaci', 'terminal');
  const file = path.join(dir, 'sessions.json');
  return { dir, file };
}

function ensureStoreDir() {
  const { dir } = sessionStorePaths();
  try { fs.mkdirSync(dir, { recursive: true }); } catch (_) {}
}

function getSavedState() {
  try {
    const { file } = sessionStorePaths();
    const raw = fs.readFileSync(file, 'utf8');
    return JSON.parse(raw);
  } catch (_) { return { sessions: [], selectedId: null, terminalVisible: false }; }
}

/**
 * Serialize terminal buffer content for a session
 * Returns the serialized string or null if serialization fails
 */
function serializeTerminalBuffer(session) {
  try {
    if (!session || !session.serializeAddon || !session.term) {
      return null;
    }
    // Serialize the terminal buffer content
    const content = session.serializeAddon.serialize();
    // Limit buffer size to prevent huge files (max 100KB)
    if (content && content.length > 100000) {
      // Keep only the last 100KB
      return content.slice(-100000);
    }
    return content || null;
  } catch (e) {
    console.warn('[Terminal] Failed to serialize buffer:', e);
    return null;
  }
}

function persistSessionsStateSync() {
  ensureStoreDir();
  const { file } = sessionStorePaths();
  
  // Sanitize all data before saving
  const sanitizedSessions = Array.from(sessions.entries()).map(([id, s]) => {
    // Sanitize cwd
    const sanitizedCwd = s.cwd ? sanitizePath(s.cwd) : null;
    
    // Sanitize env vars
    const sanitizedEnv = {};
    if (s.env && typeof s.env === 'object') {
      for (const [k, v] of Object.entries(s.env)) {
        const sanitizedVal = sanitizeEnvValue(v);
        if (sanitizedVal !== null && k.match(/^[A-Za-z_][A-Za-z0-9_]*$/)) {
          sanitizedEnv[k] = sanitizedVal;
        }
      }
    }
    
    // Sanitize sources
    const sanitizedSources = [];
    if (Array.isArray(s.sources)) {
      for (const src of s.sources) {
        const sanitizedSrc = sanitizePath(src);
        if (sanitizedSrc) {
          sanitizedSources.push(sanitizedSrc);
        }
      }
    }
    
    // Serialize terminal buffer content for restoration
    const bufferContent = serializeTerminalBuffer(s);
    
    return {
      id,
      title: s.title,
      cwd: sanitizedCwd,
      env: sanitizedEnv,
      sources: sanitizedSources,
      wasOpen: visible && id === activeSessionId,
      bufferContent: bufferContent,  // Serialized terminal buffer
    };
  });
  
  const payload = {
    selectedId: activeSessionId,
    terminalVisible: visible,
    sessions: sanitizedSessions,
  };
  try { fs.writeFileSync(file, JSON.stringify(payload, null, 2), 'utf8'); } catch (_) {}
}

function persistSessionsStateDebounced() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(persistSessionsStateSync, 250);
}

async function ensureSessionsLoaded() {
  await ensureDefaultCwd();
  const state = getSavedState();
  // Build tabs and session objects (do not start backends until shown)
  const container = ensureContainers();
  if (!container) return;
  const tabsWrap = container.querySelector('#terminal-tabs');
  if (tabsWrap) tabsWrap.innerHTML = '';

  if (!state.sessions || state.sessions.length === 0) return; // none saved

  for (const s of state.sessions) {
    await createSession(s.id, s.title);
    const sess = sessions.get(s.id);
    
    // Sanitize loaded values to prevent corrupted data from causing issues
    sess.cwd = s.cwd ? sanitizePath(s.cwd) : null;
    
    // Sanitize env vars
    sess.env = {};
    if (s.env && typeof s.env === 'object') {
      for (const [k, v] of Object.entries(s.env)) {
        const sanitizedVal = sanitizeEnvValue(v);
        if (sanitizedVal !== null && k.match(/^[A-Za-z_][A-Za-z0-9_]*$/)) {
          sess.env[k] = sanitizedVal;
        }
      }
    }
    
    // Sanitize source files
    sess.sources = [];
    if (Array.isArray(s.sources)) {
      for (const src of s.sources) {
        const sanitizedSrc = sanitizePath(src);
        if (sanitizedSrc) {
          sess.sources.push(sanitizedSrc);
        }
      }
    }
    
    // Load saved buffer content for restoration
    sess.bufferContent = s.bufferContent || null;
    
    sess.wasOpen = !!s.wasOpen;
    addSessionTabToUI(s.id, s.title);
  }

  // Select previously selected
  if (state.selectedId && sessions.has(state.selectedId)) {
    activeSessionId = state.selectedId;
  }
}

function generateSessionId() {
  return Math.random().toString(36).slice(2, 10);
}

function setupTopbarUI() {
  const container = ensureContainers();
  if (!container) return;
  const tabsWrap = container.querySelector('#terminal-tabs');
  const addBtn = container.querySelector('#terminal-add-btn');
  if (!addBtn || !tabsWrap) return;
  if (!addBtn._wired) {
    addBtn._wired = true;
    addBtn.addEventListener('click', async () => {
      const id = generateSessionId();
      await createSession(id, `Session ${sessions.size + 1}`);
      await startBackendForSession(id);
      addSessionTabToUI(id, sessions.get(id).title);
      switchSession(id);
      persistSessionsStateDebounced();
    });
  }
  // Robust context menu: delegate at document level and also react to right-button mousedown
  if (!window._terminalTabMenuWired) {
    window._terminalTabMenuWired = true;
    const findTab = (el) => el && (el.closest && el.closest('#terminal-tabs .term-tab'));
    document.addEventListener('contextmenu', (e) => {
      const tab = findTab(e.target);
      if (tab) {
        e.preventDefault();
        e.stopPropagation();
        openTermTabMenu(e, tab.dataset.sessionId);
      }
    }, true);
    const openIfTab = (e) => {
      const tab = findTab(e.target);
      if (tab) {
        e.preventDefault();
        e.stopPropagation();
        openTermTabMenu(e, tab.dataset.sessionId);
      }
    };
    document.addEventListener('mousedown', (e) => { if (e.button === 2) openIfTab(e); }, true);
    document.addEventListener('mouseup', (e) => { if (e.button === 2) openIfTab(e); }, true);
    document.addEventListener('pointerdown', (e) => { if (e.button === 2) openIfTab(e); }, true);
  }
}

function addSessionTabToUI(sessionId, title) {
  const tabsWrap = document.getElementById('terminal-tabs');
  if (!tabsWrap) return;
  const btn = document.createElement('button');
  btn.className = 'term-tab';
  btn.dataset.sessionId = sessionId;
  btn.setAttribute('role', 'tab');

  const label = document.createElement('span');
  label.className = 'term-tab-label';
  label.textContent = title || sessionId;
  btn.appendChild(label);

  const close = document.createElement('button');
  close.className = 'term-tab-close';
  close.setAttribute('aria-label', 'Close session');
  close.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18.3 5.71a1 1 0 0 0-1.41 0L12 10.59 7.11 5.7a1 1 0 0 0-1.41 1.42L10.59 12l-4.9 4.89a1 1 0 1 0 1.41 1.42L12 13.41l4.89 4.9a1 1 0 0 0 1.42-1.41L13.41 12l4.9-4.89a1 1 0 0 0-.01-1.4z"/></svg>';
  close.addEventListener('click', (e) => {
    e.stopPropagation();
    const s = sessions.get(sessionId);
    const name = (s && s.title) || sessionId;
    if (confirm(`Close terminal session "${name}"?`)) {
      deleteSession(sessionId);
    }
  });
  btn.appendChild(close);

  btn.addEventListener('click', () => switchSession(sessionId));
  btn.addEventListener('contextmenu', (e) => openTermTabMenu(e, sessionId));

  tabsWrap.appendChild(btn);
}

// src/main/functions/terminal/init.js
// Adds an embedded terminal under the editor, toggleable via View > Show/Hide Terminal.
// Uses xterm + node-pty. Default CWD = current project root (via IPC get-project-root).

const { ipcRenderer } = require('electron');
const { Terminal } = require('xterm');
const { FitAddon } = require('xterm-addon-fit');

// Try to load serialize addon - use new package name first, fall back to old
let SerializeAddon;
try {
  SerializeAddon = require('@xterm/addon-serialize').SerializeAddon;
} catch (e) {
  try {
    SerializeAddon = require('xterm-addon-serialize').SerializeAddon;
  } catch (e2) {
    console.warn('[Terminal] SerializeAddon not available, buffer persistence disabled');
    SerializeAddon = null;
  }
}

let visible = false;
let defaultCwd = null;

// Multi-session management
// Each session now includes serializeAddon for buffer persistence
const sessions = new Map(); // id -> { term, fitAddon, serializeAddon, title, inputBuffer, loaded, backendStarted, wasOpen, cwd, env, sources, bufferContent }
let activeSessionId = null;

function ensureContainers() {
  let editor = document.getElementById('editor');
  if (!editor) return null;

  let container = document.getElementById('terminal-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'terminal-container';
    container.innerHTML = `
      <div id="terminal-topbar" role="tablist" aria-label="Terminal Sessions">
        <div id="terminal-tabs"></div>
        <button id="terminal-add-btn" title="New Session" aria-label="New Terminal Session">+</button>
      </div>
      <div id="terminal"></div>`;
    editor.appendChild(container);
  } else {
    if (!container.querySelector('#terminal-topbar')) {
      const top = document.createElement('div');
      top.id = 'terminal-topbar';
      top.setAttribute('role', 'tablist');
      top.setAttribute('aria-label', 'Terminal Sessions');
      top.innerHTML = '<div id="terminal-tabs"></div><button id="terminal-add-btn" title="New Session" aria-label="New Terminal Session">+</button>';
      const termEl = container.querySelector('#terminal');
      container.insertBefore(top, termEl || null);
    }
  }
  return container;
}

async function ensureDefaultCwd() {
  if (defaultCwd) return defaultCwd;
  try {
    defaultCwd = await ipcRenderer.invoke('get-project-root');
  } catch (_) {
    defaultCwd = process.cwd();
  }
  return defaultCwd;
}

async function createSession(sessionId, title) {
  const container = ensureContainers();
  if (!container) return;
  const termEl = container.querySelector('#terminal');
  if (!termEl) return;

  if (sessions.has(sessionId)) return; // already exists

  const term = new Terminal({
    convertEol: true,
    fontFamily: 'Menlo, Monaco, Consolas, "Liberation Mono", monospace',
    fontSize: 13,
    cursorBlink: true,
    theme: {
      background: '#ffffff',
      foreground: '#000000',
      cursor: '#111111',
      cursorAccent: '#ffffff',
      // Make selection clearly visible on a light background
      selectionBackground: 'rgba(59, 130, 246, 0.35)',    // blue-ish highlight
      selectionInactiveBackground: 'rgba(148, 163, 184, 0.25)', // slate when unfocused
      selectionForeground: '#111111'
    },
    scrollback: 5000,
  });
  
  // Load addons
  const fitAddon = new FitAddon();
  term.loadAddon(fitAddon);
  
  // Load serialize addon if available
  let serializeAddon = null;
  if (SerializeAddon) {
    try {
      serializeAddon = new SerializeAddon();
      term.loadAddon(serializeAddon);
    } catch (e) {
      console.warn('[Terminal] Failed to load SerializeAddon:', e);
    }
  }

  // Wire input capturing for history
  const session = { 
    id: sessionId, 
    term, 
    fitAddon, 
    serializeAddon,
    title: title || `Session ${sessionId}`, 
    inputBuffer: '', 
    loaded: false, 
    backendStarted: false, 
    wasOpen: false, 
    cwd: null, 
    env: {}, 
    sources: [], 
    runningCommand: null, 
    runningSince: null, 
    runningTimer: null, 
    runningEndTimer: null, 
    lastDataAt: null, 
    hadOutputForCurrentCmd: false,
    bufferContent: null,  // Serialized terminal buffer for restoration
    bufferRestored: false // Track if buffer has been restored
  };
  sessions.set(sessionId, session);

  term.onData(data => {
    // send keystrokes to main for this session
    try { ipcRenderer.send('terminal-input', { sessionId, data }); } catch (_) {}

    // capture command line to detect cd/export/source and env assignments
    for (const ch of data) {
      if (ch === '\u0003') { // Ctrl+C
        session.runningCommand = null;
        session.runningSince = null;
        session.commandOutput = '';
        if (session.runningTimer) { clearTimeout(session.runningTimer); session.runningTimer = null; }
        if (session.runningEndTimer) { clearTimeout(session.runningEndTimer); session.runningEndTimer = null; }
        updateTabRunningUI(sessionId);
      }
      if (ch === '\r' || ch === '\n') {
        const line = (session.inputBuffer || '').trim();
        if (line) {
          // Record state changes
          parseAndRecordSessionState(session, line);
          // Heuristic: non-pure state command -> mark as potentially running
          // Set running state IMMEDIATELY (not with delay) for better tracking
          if (!/^\s*(?:cd\s+|export\s+|unset\s+|[A-Za-z_][A-Za-z0-9_]*=|(?:source|\.)\s+)/.test(line)) {
            session.runningCommand = line;
            session.runningSince = Date.now();
            session.hadOutputForCurrentCmd = false;
            session.commandOutput = '';
            session.lastDataAt = Date.now();
            if (session.runningTimer) { clearTimeout(session.runningTimer); }
            if (session.runningEndTimer) { clearTimeout(session.runningEndTimer); session.runningEndTimer = null; }
            // Update UI immediately to show running state
            updateTabRunningUI(sessionId);
            // Also set a delayed update for the visual indicator (1.5s threshold)
            session.runningTimer = setTimeout(() => {
              updateTabRunningUI(sessionId);
            }, 1500);
          } else {
            // Pure state command: do not set running
            // no-op
          }
        }
        session.inputBuffer = '';
        persistSessionsStateDebounced();
      } else if (ch === '\u0008' || ch === '\b') { // backspace
        session.inputBuffer = session.inputBuffer.slice(0, -1);
      } else if (ch >= ' ') {
        session.inputBuffer += ch;
      }
    }
  });

  // Route incoming data to the correct term
  const onData = (_e, payload) => {
    const { sessionId: sid, chunk } = payload || {};
    if (sid === sessionId) {
      try { term.write(chunk); } catch (_) {}
      const s = sessions.get(sessionId);
      if (s) {
        s.lastDataAt = Date.now();
        if (s.runningCommand) {
          // mark that this command produced output (non-whitespace)
          const hasNonWs = /[^\s]/.test(chunk);
          if (hasNonWs) s.hadOutputForCurrentCmd = true;
          
          // Track total output for this command
          s.commandOutput = (s.commandOutput || '') + chunk;
          
          // Limit output buffer size to prevent memory issues
          if (s.commandOutput.length > 50000) {
            s.commandOutput = s.commandOutput.slice(-30000);
          }
          
          // Check if this chunk contains a shell prompt (indicates command finished)
          // Clean ANSI codes first for reliable detection
          const cleanChunk = chunk
            .replace(/\x1b\[[0-9;]*[a-zA-Z]/g, '')
            .replace(/\x1b\[\?[0-9]+[hl]/g, '')
            .replace(/\r/g, '');
          
          // Shell prompt patterns - must be at end of output
          const hasPrompt = /[\$\%\>]\s*$/.test(cleanChunk) && 
                           // Prompt line should be relatively short
                           cleanChunk.split('\n').pop().length < 150;
          
          // Also check if we see user@host pattern
          const hasUserHostPrompt = /[a-zA-Z0-9_-]+@[a-zA-Z0-9_.-]+.*[\$\%\>]\s*$/.test(cleanChunk);
          
          // debounce completion detection after output calms down
          if (s.runningEndTimer) clearTimeout(s.runningEndTimer);
          
          // Only consider command finished if:
          // 1. We see a shell prompt pattern, AND
          // 2. Output has been idle for a bit, AND
          // 3. Command has been running for at least 1 second (avoid false positives)
          const timeSinceStart = s.runningSince ? (Date.now() - s.runningSince) : 0;
          
          if ((hasPrompt || hasUserHostPrompt) && timeSinceStart > 1000) {
            s.runningEndTimer = setTimeout(() => {
              const now = Date.now();
              const idle = !s.lastDataAt || (now - s.lastDataAt) >= 500; // Increased from 300ms
              if (s.runningCommand && s.hadOutputForCurrentCmd && idle) {
                // Double-check: look at the last part of accumulated output for prompt
                const recentOutput = (s.commandOutput || '').slice(-500)
                  .replace(/\x1b\[[0-9;]*[a-zA-Z]/g, '')
                  .replace(/\x1b\[\?[0-9]+[hl]/g, '')
                  .replace(/\r/g, '');
                const lastLines = recentOutput.split('\n').slice(-3).join('\n');
                const confirmedPrompt = /[\$\%\>]\s*$/.test(lastLines);
                
                if (confirmedPrompt) {
                  s.runningCommand = null;
                  s.runningSince = null;
                  s.commandOutput = '';
                  if (s.runningTimer) { clearTimeout(s.runningTimer); s.runningTimer = null; }
                  updateTabRunningUI(sessionId);
                }
              }
            }, 600); // Increased from 400ms - more reliable for slow commands
          } else {
            // No prompt seen - use longer timeout for interactive commands
            // Only mark as done after extended silence
            s.runningEndTimer = setTimeout(() => {
              const now = Date.now();
              const longIdle = !s.lastDataAt || (now - s.lastDataAt) >= 8000; // Increased from 5000ms
              if (s.runningCommand && longIdle) {
                // Check accumulated output for prompt before clearing
                const recentOutput = (s.commandOutput || '').slice(-500)
                  .replace(/\x1b\[[0-9;]*[a-zA-Z]/g, '')
                  .replace(/\x1b\[\?[0-9]+[hl]/g, '')
                  .replace(/\r/g, '');
                const lastLines = recentOutput.split('\n').slice(-3).join('\n');
                const hasPromptNow = /[\$\%\>]\s*$/.test(lastLines);
                
                if (hasPromptNow) {
                  s.runningCommand = null;
                  s.runningSince = null;
                  s.commandOutput = '';
                  if (s.runningTimer) { clearTimeout(s.runningTimer); s.runningTimer = null; }
                  updateTabRunningUI(sessionId);
                }
              }
            }, 8500); // Increased from 5500ms - don't finish too early for slow commands
          }
        }
      }
    }
  };
  ipcRenderer.on('terminal-data', onData);

  const onExit = (_e, payload) => {
    const { sessionId: sid } = payload || {};
    if (sid === sessionId) {
      const s = sessions.get(sessionId);
      if (s) {
        s.backendStarted = false;
        s.runningCommand = null;
        s.runningSince = null;
        s.hadOutputForCurrentCmd = false;
        if (s.runningTimer) { clearTimeout(s.runningTimer); s.runningTimer = null; }
        if (s.runningEndTimer) { clearTimeout(s.runningEndTimer); s.runningEndTimer = null; }
        updateTabRunningUI(sessionId);
      }
    }
  };
  ipcRenderer.on('terminal-exit', onExit);

  // Open into container if first/active selection will mount
  session.loaded = true;
}

function mountSession(sessionId) {
  const s = sessions.get(sessionId);
  const container = document.getElementById('terminal');
  if (!s || !container) {
    console.warn('[Terminal] mountSession failed - session or container missing', { sessionId, hasSession: !!s, hasContainer: !!container });
    return;
  }
  
  // Check if xterm instance exists
  if (!s.term) {
    console.warn('[Terminal] mountSession failed - no xterm instance for session', sessionId);
    return;
  }
  
  // Clear previous term DOM
  try { 
    while (container.firstChild) {
      container.removeChild(container.firstChild);
    }
  } catch (e) {
    console.warn('[Terminal] Error clearing container:', e);
  }
  
  // Open xterm into container
  try { 
    s.term.open(container);
    console.log('[Terminal] Mounted xterm for session', sessionId);
  } catch (e) {
    console.error('[Terminal] Error opening xterm:', e);
    return;
  }
  
  // Restore buffer content if available (from saved session)
  if (s.bufferContent && !s.bufferRestored) {
    try {
      console.log('[Terminal] Restoring buffer content for session', sessionId, 'length:', s.bufferContent.length);
      s.term.write(s.bufferContent);
      s.bufferRestored = true;  // Mark as restored to avoid re-writing on subsequent mounts
    } catch (e) {
      console.warn('[Terminal] Error restoring buffer content:', e);
    }
  }
  
  // Focus and fit
  const doFit = () => {
    try {
      if (s.fitAddon) {
        s.fitAddon.fit();
      }
      const cols = s.term.cols || 80;
      const rows = s.term.rows || 24;
      try { ipcRenderer.send('terminal-resize', { sessionId, cols, rows }); } catch (_) {}
    } catch (e) {
      console.warn('[Terminal] Error fitting terminal:', e);
    }
  };
  
  // Fit immediately and after a short delay to handle layout changes
  doFit();
  setTimeout(doFit, 50);
  setTimeout(doFit, 150);
  
  // Make container focusable and wire up focus handling
  try { container.setAttribute('tabindex', '0'); } catch (_) {}
  
  // Remove old mousedown listener to avoid duplicates, then add new one
  if (container._termMouseHandler) {
    container.removeEventListener('mousedown', container._termMouseHandler);
  }
  container._termMouseHandler = () => { 
    try { s.term && s.term.focus(); } catch (_) {} 
  };
  container.addEventListener('mousedown', container._termMouseHandler);
}

function fitActiveSession() {
  if (!visible || !activeSessionId) return;
  const s = sessions.get(activeSessionId);
  if (!s) return;
  try {
    s.fitAddon && s.fitAddon.fit();
    const cols = s.term.cols || 80;
    const rows = s.term.rows || 24;
    try { ipcRenderer.send('terminal-resize', { sessionId: activeSessionId, cols, rows }); } catch (_) {}
  } catch (_) {}
}

async function startBackendForSession(sessionId) {
  const rootCwd = await ensureDefaultCwd();
  const s = sessions.get(sessionId);
  if (!s) return;
  
  // Sanitize stored cwd before using it
  const sanitizedCwd = s.cwd ? sanitizePath(s.cwd) : null;
  
  // Estimate size via a temporary fit using current mount (if any)
  let cols = 80, rows = 24;
  try { s.fitAddon && s.fitAddon.fit(); cols = s.term.cols || 80; rows = s.term.rows || 24; } catch (_) {}
  
  // Start terminal with sanitized cwd (or root if invalid)
  const startCwd = sanitizedCwd || rootCwd;
  await ipcRenderer.invoke('terminal-start', { sessionId, cwd: startCwd, cols, rows });
  s.backendStarted = true;
  
  // Re-apply minimal state: env exports, sources
  // Note: We don't need to cd again since we started with the correct cwd
  const send = (cmd) => {
    try { ipcRenderer.send('terminal-input', { sessionId, data: cmd + '\n' }); } catch (_) {}
  };
  
  // Only apply env vars that are valid
  if (s.env && typeof s.env === 'object') {
    for (const [k, v] of Object.entries(s.env)) {
      const sanitizedVal = sanitizeEnvValue(v);
      if (sanitizedVal !== null && k.match(/^[A-Za-z_][A-Za-z0-9_]*$/)) {
        send(`export ${k}=${escapeForShell(sanitizedVal)}`);
      }
    }
  }
  
  // Only source files that look valid
  if (Array.isArray(s.sources)) {
    for (const file of s.sources) {
      const sanitizedFile = sanitizePath(file);
      if (sanitizedFile) {
        send(`. ${escapeForShell(sanitizedFile)}`);
      }
    }
  }
  
  // Update the session's cwd to the sanitized version
  if (sanitizedCwd !== s.cwd) {
    s.cwd = sanitizedCwd;
    persistSessionsStateDebounced();
  }
}

function switchSession(sessionId) {
  const s = sessions.get(sessionId);
  if (!s) return;
  
  const isSameSession = activeSessionId === sessionId;
  activeSessionId = sessionId;
  
  // Start backend if not started
  if (!s.backendStarted) {
    startBackendForSession(sessionId).catch(() => {});
  }
  
  // Always mount the session to ensure xterm is rendered
  // This is important when restoring from saved state
  mountSession(sessionId);
  
  // Update tab highlighting
  const tabs = document.querySelectorAll('#terminal-tabs .term-tab');
  tabs.forEach(el => el.classList.toggle('active', el.dataset.sessionId === sessionId));
  
  // Save selected (only if actually changed)
  if (!isSameSession) {
    persistSessionsStateDebounced();
  }
}

function showTerminal() {
  const editor = document.getElementById('editor');
  const container = ensureContainers();
  if (!editor || !container) return;

  editor.classList.add('with-terminal');
  container.style.display = 'block';
  visible = true;
  persistSessionsStateDebounced();

  // Ensure UI wired
  setupTopbarUI();

  ensureSessionsLoaded().then(async () => {
    // If no sessions, create initial one
    if (sessions.size === 0) {
      const id = generateSessionId();
      await createSession(id, `Session ${sessions.size + 1}`);
      await startBackendForSession(id);
      addSessionTabToUI(id, sessions.get(id).title);
      switchSession(id);
    } else {
      // Determine which session to show
      let selected = activeSessionId && sessions.has(activeSessionId) 
        ? activeSessionId 
        : Array.from(sessions.keys())[0];
      
      // Start backend for selected session if needed
      const s = sessions.get(selected);
      if (s && !s.backendStarted) {
        await startBackendForSession(selected);
      }
      
      // Switch to the selected session (this will mount and update tabs)
      switchSession(selected);
      
      // Fit after layout settles
      setTimeout(() => { try { fitActiveSession(); } catch (_) {} }, 100);
    }

    // Relayout monaco when terminal becomes visible
    try {
      const monacoManager = require('../editor/monacoManager');
      monacoManager && monacoManager.layout && monacoManager.layout();
    } catch (_) {}
  });
}

function hideTerminal() {
  const editor = document.getElementById('editor');
  const container = document.getElementById('terminal-container');
  if (!editor || !container) return;

  editor.classList.remove('with-terminal');
  container.style.display = 'none';
  visible = false;

  // Relayout monaco when terminal hidden
  try {
    const monacoManager = require('../editor/monacoManager');
    monacoManager && monacoManager.layout && monacoManager.layout();
  } catch (_) {}

  // Mark sessions as not open
  for (const s of sessions.values()) s.wasOpen = false;
  persistSessionsStateDebounced();
}

/**
 * Async version of showTerminal that waits for initialization
 */
async function showTerminalAsync() {
  const editor = document.getElementById('editor');
  const container = ensureContainers();
  if (!editor || !container) return false;

  editor.classList.add('with-terminal');
  container.style.display = 'block';
  visible = true;
  persistSessionsStateDebounced();

  // Ensure UI wired
  setupTopbarUI();

  // Wait for sessions to load
  await ensureSessionsLoaded();
  
  // If no sessions exist, create one
  if (sessions.size === 0) {
    const id = generateSessionId();
    await createSession(id, `Session ${sessions.size + 1}`);
    await startBackendForSession(id);
    addSessionTabToUI(id, sessions.get(id).title);
    switchSession(id);
  }

  // Relayout monaco
  try {
    const monacoManager = require('../editor/monacoManager');
    monacoManager && monacoManager.layout && monacoManager.layout();
  } catch (_) {}

  return true;
}

function initTerminal() {
  // IPC bindings
  ipcRenderer.on('show-terminal', () => showTerminal());
  ipcRenderer.on('hide-terminal', () => hideTerminal());

  // Fit on resize for active session
  window.addEventListener('resize', () => { fitActiveSession(); });

  // Periodic safety refresh to ensure long-running commands show indicator even if timers were throttled
  if (!window._termRunningPoll) {
    window._termRunningPoll = setInterval(() => {
      try {
        for (const [id, s] of sessions.entries()) {
          if (s && s.runningCommand) updateTabRunningUI(id);
        }
      } catch (e) {}
    }, 1000);
  }
  
  // Periodic buffer save to ensure terminal content is persisted
  // Save every 30 seconds if terminal is visible
  if (!window._termBufferSaveInterval) {
    window._termBufferSaveInterval = setInterval(() => {
      try {
        if (visible && sessions.size > 0) {
          persistSessionsStateDebounced();
        }
      } catch (e) {
        console.warn('[Terminal] Error in periodic buffer save:', e);
      }
    }, 30000);
  }

  // Cleanup on unload: stop all backends and persist state
  window.addEventListener('beforeunload', () => {
    try { ipcRenderer.send('terminal-stop-all'); } catch (_) {}
    try { persistSessionsStateSync(); } catch (_) {}
    try { if (window._termRunningPoll) { clearInterval(window._termRunningPoll); window._termRunningPoll = null; } } catch (_) {}
    try { if (window._termBufferSaveInterval) { clearInterval(window._termBufferSaveInterval); window._termBufferSaveInterval = null; } } catch (_) {}
  });
}

/**
 * Get the active terminal session ID
 */
function getActiveSessionId() {
  return activeSessionId;
}

/**
 * Check if terminal is visible
 */
function isTerminalVisible() {
  return visible;
}

/**
 * Send input to a specific terminal session
 */
function sendToTerminal(sessionId, data) {
  if (!sessionId) sessionId = activeSessionId;
  if (!sessionId) return false;
  try {
    ipcRenderer.send('terminal-input', { sessionId, data });
    return true;
  } catch (_) {
    return false;
  }
}

/**
 * Get all session IDs
 */
function getSessionIds() {
  return Array.from(sessions.keys());
}

// AI Agent session constants
const AI_AGENT_SESSION_ID = 'ai-agent-terminal';
const AI_AGENT_SESSION_TITLE = 'AI Agent';

/**
 * Create or get the AI Agent terminal session
 * This is a dedicated session for AI-executed commands
 * Must be called after terminal is shown/initialized
 */
async function ensureAiAgentSession() {
  // First ensure default CWD is set
  await ensureDefaultCwd();
  
  // Ensure containers exist
  const container = ensureContainers();
  if (!container) {
    console.error('[Terminal] Cannot create AI Agent session - no container');
    return null;
  }
  
  // Ensure topbar UI is set up
  setupTopbarUI();
  
  // Check if AI agent session already exists
  if (sessions.has(AI_AGENT_SESSION_ID)) {
    const s = sessions.get(AI_AGENT_SESSION_ID);
    console.log('[Terminal] AI Agent session exists, backendStarted:', s.backendStarted);
    // Ensure backend is started
    if (!s.backendStarted) {
      console.log('[Terminal] Starting backend for existing AI Agent session');
      await startBackendForSession(AI_AGENT_SESSION_ID);
    }
    // Mount and switch to it
    mountSession(AI_AGENT_SESSION_ID);
    activeSessionId = AI_AGENT_SESSION_ID;
    // Update tab UI
    const tabs = document.querySelectorAll('#terminal-tabs .term-tab');
    tabs.forEach(el => el.classList.toggle('active', el.dataset.sessionId === AI_AGENT_SESSION_ID));
    setTimeout(() => { try { fitActiveSession(); } catch (_) {} }, 50);
    return AI_AGENT_SESSION_ID;
  }
  
  console.log('[Terminal] Creating new AI Agent session');
  
  // Create the AI agent session
  await createSession(AI_AGENT_SESSION_ID, AI_AGENT_SESSION_TITLE);
  
  // Verify session was created
  if (!sessions.has(AI_AGENT_SESSION_ID)) {
    console.error('[Terminal] Failed to create AI Agent session');
    return null;
  }
  
  // Start the backend (PTY process)
  console.log('[Terminal] Starting backend for AI Agent session');
  await startBackendForSession(AI_AGENT_SESSION_ID);
  
  // Add tab to UI
  addSessionTabToUI(AI_AGENT_SESSION_ID, AI_AGENT_SESSION_TITLE);
  
  // Mount the terminal (renders xterm)
  mountSession(AI_AGENT_SESSION_ID);
  
  // Set as active
  activeSessionId = AI_AGENT_SESSION_ID;
  
  // Update tab UI
  const tabs = document.querySelectorAll('#terminal-tabs .term-tab');
  tabs.forEach(el => el.classList.toggle('active', el.dataset.sessionId === AI_AGENT_SESSION_ID));
  
  // Fit the terminal
  setTimeout(() => { try { fitActiveSession(); } catch (_) {} }, 50);
  
  persistSessionsStateDebounced();
  
  console.log('[Terminal] AI Agent session created and ready');
  return AI_AGENT_SESSION_ID;
}

/**
 * Get the AI Agent session ID
 */
function getAiAgentSessionId() {
  return AI_AGENT_SESSION_ID;
}

/**
 * Check if AI Agent session exists and is ready
 */
function isAiAgentSessionReady() {
  const s = sessions.get(AI_AGENT_SESSION_ID);
  const ready = !!(s && s.backendStarted && s.loaded);
  console.log('[Terminal] isAiAgentSessionReady:', ready, 'session exists:', !!s, 'backendStarted:', s?.backendStarted, 'loaded:', s?.loaded);
  return ready;
}

/**
 * Set the current working directory for a session
 * This should be called before startBackendForSession
 */
function setSessionCwd(sessionId, cwd) {
  const s = sessions.get(sessionId);
  if (s && cwd) {
    s.cwd = cwd;
    console.log(`[Terminal] Set cwd for session ${sessionId}: ${cwd}`);
  }
}

/**
 * Check if a command is currently running in a session
 */
function isCommandRunning(sessionId) {
  const s = sessions.get(sessionId || AI_AGENT_SESSION_ID);
  return !!(s && s.runningCommand);
}

/**
 * Get the running command for a session
 */
function getRunningCommand(sessionId) {
  const s = sessions.get(sessionId || AI_AGENT_SESSION_ID);
  return s ? s.runningCommand : null;
}

/**
 * Set the running command state for a session (used by run_command handler)
 */
function setRunningCommand(sessionId, command) {
  const s = sessions.get(sessionId || AI_AGENT_SESSION_ID);
  if (s) {
    s.runningCommand = command;
    s.runningSince = command ? Date.now() : null;
    s.hadOutputForCurrentCmd = false;
    s.commandOutput = ''; // Reset output buffer
    s.lastDataAt = Date.now(); // Initialize last data time
    // Clear any existing timers
    if (s.runningTimer) { clearTimeout(s.runningTimer); s.runningTimer = null; }
    if (s.runningEndTimer) { clearTimeout(s.runningEndTimer); s.runningEndTimer = null; }
    updateTabRunningUI(sessionId || AI_AGENT_SESSION_ID);
  }
}

/**
 * Clear the running command state for a session
 */
function clearRunningCommand(sessionId) {
  const s = sessions.get(sessionId || AI_AGENT_SESSION_ID);
  if (s) {
    s.runningCommand = null;
    s.runningSince = null;
    s.hadOutputForCurrentCmd = false;
    s.commandOutput = '';
    if (s.runningTimer) { clearTimeout(s.runningTimer); s.runningTimer = null; }
    if (s.runningEndTimer) { clearTimeout(s.runningEndTimer); s.runningEndTimer = null; }
    updateTabRunningUI(sessionId || AI_AGENT_SESSION_ID);
  }
}

module.exports = { 
  initTerminal, 
  showTerminal,
  showTerminalAsync,
  hideTerminal,
  getActiveSessionId,
  isTerminalVisible,
  sendToTerminal,
  getSessionIds,
  ensureAiAgentSession,
  getAiAgentSessionId,
  isAiAgentSessionReady,
  isCommandRunning,
  getRunningCommand,
  setRunningCommand,
  clearRunningCommand,
  createSession,
  switchSession,
  startBackendForSession,
  addSessionTabToUI,
  setSessionCwd,
  AI_AGENT_SESSION_ID
};
