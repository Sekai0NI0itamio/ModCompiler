// Tab manager improvements:
//  - deleteSessionById will only prompt if the session contains messages (empty sessions delete silently)
//  - persisting per-session scroll positions (saved to ~/.auaci/chat/ via scrollCache)
//  - save scroll before switching sessions so cached position is kept
//  - topbar is sticky inside the chat container (so its Y is fixed but contained)

const fs = require('fs').promises;
const path = require('path');
const crypto = require('crypto');
const sessionManager = require('./sessionManager');
const chatTabOptions = require('./chatTabOptions');
const { scrollElementIntoView } = require('./helpers/smoothScroll');
const scrollCache = require('./helpers/scrollCache');

// New: rename UI module (opens a modal-like window for renaming)
const renameUI = require('./taboptions/rename');

// Concurrent session management
const { 
  getSessionState, 
  captureSessionState, 
  isSessionResponding, 
  isSessionAwaitingInput,
  isSessionError,
  SESSION_STATUS
} = require('./concurrent/sessionStateManager');
const { restoreSessionRender } = require('./concurrent/sessionRenderer');

const STYLE_ID = 'auaci-chat-tab-styles';
const TOPBAR_ID = 'chat-topbar';
const TABS_CONTAINER_CLASS = 'chat-tabs';
const ADD_BTN_ID = 'chat-add-btn';

let didInitialActiveScroll = false;

const CSS = `
/* Tab manager styles (self-contained) */

/* The topbar is sticky INSIDE the chat container so it stays fixed in Y while staying confined to the container */
#${TOPBAR_ID} {
  position: sticky;
  top: 0;
  left: 0;
  right: 0;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: #fafbfc;
  box-sizing: border-box;
  z-index: 900;
  margin: 0;
  border-bottom: none;
  box-shadow: 0 1px 0 rgba(0,0,0,0.03);
  position: sticky;
}

/* Scrollable tab strip */
#${TOPBAR_ID} .${TABS_CONTAINER_CLASS} {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  overflow-y: hidden;
  -webkit-overflow-scrolling: touch;
  flex: 1 1 auto;
  align-items: center;
  padding: 4px 6px;
  box-sizing: border-box;
  white-space: nowrap;
  scroll-behavior: smooth;
}

/* thin scrollbar (auto-hide) */
#${TOPBAR_ID} .${TABS_CONTAINER_CLASS}::-webkit-scrollbar { height: 8px; }
#${TOPBAR_ID} .${TABS_CONTAINER_CLASS}::-webkit-scrollbar-thumb { background: rgba(0,0,0,0); border-radius: 6px; transition: background-color 160ms ease; }
#${TOPBAR_ID} .${TABS_CONTAINER_CLASS}.show-scrollbar::-webkit-scrollbar-thumb { background: #e6e9ee; }
#${TOPBAR_ID} .${TABS_CONTAINER_CLASS} { scrollbar-width: thin; scrollbar-color: transparent transparent; }
#${TOPBAR_ID} .${TABS_CONTAINER_CLASS}.show-scrollbar { scrollbar-color: #e6e9ee transparent; }

/* Tab item: rectangular, no roundness */
.auaci-chat-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  background: #f4f6f8;
  color: #0f172a;
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
  border-radius: 0;
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
  line-height: 1;
  flex: 0 0 auto;
  min-height: 36px;
  box-sizing: border-box;
  transition: background 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}
/* Responding state: make the label red */
.auaci-chat-tab.auaci-responding .auaci-tab-label {
  color: #b91c1c;
  font-weight: 700;
}

/* Awaiting input state: yellow outline */
.auaci-chat-tab.auaci-awaiting-input {
  border-color: #f59e0b;
  box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.25);
}
.auaci-chat-tab.auaci-awaiting-input .auaci-tab-label {
  color: #b45309;
  font-weight: 600;
}

/* Error state: red indicator */
.auaci-chat-tab.auaci-error {
  border-color: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.15);
}
.auaci-chat-tab.auaci-error .auaci-tab-label {
  color: #dc2626;
}

/* Active: blue outline and visually connected (bookmark look) */
.auaci-chat-tab.auaci-active {
  background: #ffffff;
  border-color: #1e90ff;
  box-shadow: 0 0 0 2px rgba(30,144,255,0.08);
  position: relative;
  z-index: 910;
  margin-bottom: -8px;
  border-bottom-color: transparent;
}

/* label */
.auaci-chat-tab .auaci-tab-label {
  padding: 0;
  margin: 0;
  display: inline-block;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* larger X, no chrome */
.auaci-chat-tab .auaci-tab-close {
  font-family: Consolas, "Courier New", monospace;
  font-size: 18px;
  font-weight: 700;
  color: #374151;
  margin-left: 8px;
  opacity: 0;
  cursor: pointer;
  padding: 0;
  line-height: 1;
  user-select: none;
  background: transparent;
  border: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
}
.auaci-chat-tab:hover .auaci-tab-close { opacity: 1; }
.auaci-chat-tab .auaci-tab-close:hover { color: #b91c1c; }

/* Options button at right */
#${ADD_BTN_ID} {
  width: 44px;
  min-width: 44px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #eaf2ff;
  border: 1px solid #d3e3ff;
  cursor: pointer;
  user-select: none;
  font-weight: 700;
  font-size: 18px;
  border-radius: 0;
  color: #0b4db1;
  flex: 0 0 auto;
  position: relative;
}
#${ADD_BTN_ID}:hover { background: #e1f0ff; }

/* Dropdown menu for options */
#chat-options-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  min-width: 180px;
  background: #ffffff;
  border: 1px solid #d1d5db;
  box-shadow: 0 6px 18px rgba(0,0,0,0.08);
  border-radius: 6px;
  padding: 6px 0;
  z-index: 950;
  display: none;
}
#chat-options-menu.open { display: block; }
.chat-options-item {
  padding: 8px 12px;
  font-size: 13px;
  color: #111827;
  cursor: pointer;
  white-space: nowrap;
  font-weight: 400; /* unbold menu text */
}
.chat-options-item:hover {
  background: #f3f4f6;
}

/* Ensure chat area connects visually to topbar */
#chat-messages {
  margin-top: 0 !important;
  border-top: 1px solid #cbd5e1 !important;
  padding-top: 18px !important;
}

/* empty states */
.auaci-empty-project { color: #9ca3af; text-align: center; margin: 64px auto; font-size: 14px; }
.auaci-empty-session { color: #9ca3af; text-align: center; margin: 40px auto; font-size: 14px; }

/* rename input */
.auaci-rename-input { font-family: inherit; font-size: 13px; padding: 4px 6px; border: 1px solid #cbd5e1; background: #ffffff; color: #0f172a; outline: none; box-sizing: border-box; min-width: 80px; }

@media (max-width: 480px) {
  #${TOPBAR_ID} { padding: 6px; gap: 6px; }
  #${ADD_BTN_ID} { width: 38px; min-width: 38px; height: 34px; font-size: 16px; }
  .auaci-chat-tab { padding: 6px 10px; font-size: 12px; min-height: 32px; }
}
`;

/* Inject styles once */
function ensureStyles() {
  if (typeof document === 'undefined') return;
  if (document.getElementById(STYLE_ID)) return;
  const s = document.createElement('style');
  s.id = STYLE_ID;
  s.textContent = CSS;
  document.head.appendChild(s);
}

/* Wire an element to auto-show scrollbar while scrolling/hovering, then hide after inactivity */
function wireAutoHideScrollbarLocal(el, options = {}) {
  if (!el || el.__auaciScrollbarWired) return;
  el.__auaciScrollbarWired = true;
  const hideDelay = Number(options.hideDelay || 900);
  const leaveDelay = Number(options.leaveDelay || 300);

  function clearTimer() {
    if (el.__auaciHideTimer) {
      clearTimeout(el.__auaciHideTimer);
      el.__auaciHideTimer = null;
    }
  }

  function scheduleHide(delay) {
    clearTimer();
    el.__auaciHideTimer = setTimeout(() => {
      try { el.classList.remove('show-scrollbar'); } catch(_) {}
      el.__auaciHideTimer = null;
    }, typeof delay === 'number' ? delay : hideDelay);
  }

  function showAndSchedule() {
    try { el.classList.add('show-scrollbar'); } catch(_) {}
    scheduleHide();
  }

  el.addEventListener('scroll', () => { showAndSchedule(); }, { passive: true });
  el.addEventListener('mouseenter', () => { showAndSchedule(); });
  el.addEventListener('mouseleave', () => { clearTimer(); scheduleHide(leaveDelay); });
}

/* Read selected session id file (project or homedir) */
async function readSelectedSessionIdFile() {
  for (const baseDir of sessionManager.CANDIDATE_BASE_DIRS) {
    const p = path.join(baseDir, 'session_id.txt');
    try {
      const raw = await fs.readFile(p, 'utf8');
      const sid = String(raw || '').trim();
      if (sid) return sid;
    } catch (e) { /* ignore */ }
  }
  return null;
}

/* Read the active session id from disk, fallback to session manager if missing. */
async function getActiveSessionId() {
  try {
    const sid = await readSelectedSessionIdFile();
    if (sid) return sid;
  } catch (_) {}
  try {
    return await sessionManager.getSessionId();
  } catch (_) {
    return null;
  }
}

/* Find sessions (preserve creation order) */
async function getSessions() {
  const sessionsMap = new Map();
  for (const baseDir of sessionManager.CANDIDATE_BASE_DIRS) {
    const sessionsDir = path.join(baseDir, 'sessions');
    try {
      const exists = await fs.access(sessionsDir).then(() => true).catch(() => false);
      if (!exists) continue;
      const files = await fs.readdir(sessionsDir);
      for (const f of files) {
        if (!f.endsWith('.json')) continue;
        const full = path.join(sessionsDir, f);
        try {
          const txt = await fs.readFile(full, 'utf8');
          const obj = JSON.parse(txt || '{}');
          const sid = obj.session_id || f.replace(/\.json$/, '');
          if (sessionsMap.has(sid)) continue;
          const st = await fs.stat(full).catch(() => ({}));
          let created = 0;
          if (obj && (obj.creationdate || obj.creationDate || obj.created)) {
            const parsed = Date.parse(obj.creationdate || obj.creationDate || obj.created);
            created = Number.isNaN(parsed) ? (st.ctimeMs || st.mtimeMs || 0) : parsed;
          } else {
            created = st.ctimeMs || st.mtimeMs || 0;
          }
          const name = (obj && obj.name) ? obj.name : `chat-${sid.slice(0, 6)}`;
          sessionsMap.set(sid, { id: sid, name, path: full, created });
        } catch (e) { /* ignore parse error */ }
      }
    } catch (e) { /* ignore */ }
  }
  return Array.from(sessionsMap.values()).sort((a, b) => (a.created || 0) - (b.created || 0));
}

/* Generate default chat name */
function generateDefaultName(existingSessions) {
  const names = (existingSessions || []).map(s => (s.name || '').toLowerCase());
  let max = 0;
  let hasPlain = false;
  for (const n of names) {
    if (n.trim() === 'default chat') hasPlain = true;
    const m = n.match(/^default\s*chat\s*(\d+)$/i);
    if (m) {
      const v = parseInt(m[1], 10);
      if (!Number.isNaN(v)) max = Math.max(max, v);
    }
  }
  if (!hasPlain && max === 0) return 'default chat';
  if (hasPlain && max === 0) return 'default chat2';
  return `default chat${max + 1}`;
}

/* Ensure directories exist for creating sessions */
async function ensureDirsForCreation() {
  const preferred = sessionManager.CANDIDATE_BASE_DIRS[0];
  try { await fs.mkdir(path.join(preferred, 'sessions'), { recursive: true }); } catch (_) {}
}

/* Create a new session file */
async function createSessionWithName(name = null, makeActive = true) {
  try {
    await ensureDirsForCreation();
    const sid = (crypto && typeof crypto.randomUUID === 'function') ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const preferred = sessionManager.CANDIDATE_BASE_DIRS[0];
    const sessionsDir = path.join(preferred, 'sessions');
    await fs.mkdir(sessionsDir, { recursive: true }).catch(() => {});
    const filePath = path.join(sessionsDir, `${sid}.json`);
    const existing = await getSessions();
    const finalName = name || generateDefaultName(existing);
    const init = { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), session_id: sid, name: finalName, chat: [] };
    await fs.writeFile(filePath, JSON.stringify(init, null, 2), 'utf8');
    if (makeActive) {
      try { await sessionManager.setSessionId(sid); } catch (_) {}
      dispatchSessionChanged();
    }
    await refreshTabs();
    return { id: sid, name: finalName, path: filePath };
  } catch (e) {
    console.error('[tabManager] createSession failed:', e && e.message ? e.message : e);
    return null;
  }
}

/* Delete session: if the session contains messages, prompt the user.
   If it's empty, delete silently.
   @param {string} sessionId - The ID of the session to delete
   @param {boolean} skipConfirmation - If true, skip the confirmation dialog
*/
async function deleteSessionById(sessionId, skipConfirmation = false) {
  try {
    if (!sessionId) return false;

    // Gather existing session file paths (by id-based filename)
    const foundPaths = [];
    for (const baseDir of sessionManager.CANDIDATE_BASE_DIRS) {
      const p = path.join(baseDir, 'sessions', `${sessionId}.json`);
      try {
        await fs.access(p);
        foundPaths.push(p);
      } catch (_) {}
    }

    // Also locate the actual path via session listing (in case filename != `${id}.json`)
    let actualPath = null;
    try {
      const sessions = await getSessions();
      const found = sessions.find(s => s.id === sessionId);
      if (found && found.path) actualPath = found.path;
    } catch (_) {}

    // If any found path (including actualPath) contains messages, prompt the user
    let hasMessages = false;
    const pathsToInspect = [...foundPaths];
    if (actualPath && !pathsToInspect.includes(actualPath)) pathsToInspect.push(actualPath);
    for (const p of pathsToInspect) {
      try {
        const txt = await fs.readFile(p, 'utf8');
        const obj = JSON.parse(txt || '{}');
        if (Array.isArray(obj.chat) && obj.chat.length > 0) {
          hasMessages = true;
          break;
        }
      } catch (_) {}
    }

    if (hasMessages && !skipConfirmation) {
      const ok = confirm('Delete this chat session and its messages? This cannot be undone.');
      if (!ok) return false;
    }

    // Proceed to remove session files from all candidate dirs + the actual path (best-effort)
    let removedAny = false;
    const pathsToDeleteSet = new Set();
    for (const baseDir of sessionManager.CANDIDATE_BASE_DIRS) {
      pathsToDeleteSet.add(path.join(baseDir, 'sessions', `${sessionId}.json`));
    }
    if (actualPath) pathsToDeleteSet.add(actualPath);

    for (const p of pathsToDeleteSet) {
      try {
        await fs.unlink(p);
        removedAny = true;
      } catch (_) {}
    }

    // Also delete the raw history file for this session
    try {
      const { clearRawHistory } = require('./rawHistoryStorage');
      await clearRawHistory(sessionId);
      console.log(`[tabManager] Deleted raw history for session ${sessionId}`);
    } catch (e) {
      console.warn('[tabManager] Failed to delete raw history:', e?.message || e);
    }

    // Remove scroll cache for this session
    try { await scrollCache.deleteScroll(sessionId); } catch (_) {}

    // If deleted the active session, pick another or clear selection
    const active = await readSelectedSessionIdFile();
    if (active === sessionId) {
      const others = (await getSessions()).filter(s => s.id !== sessionId);
      if (others.length > 0) {
        try { await sessionManager.setSessionId(others[0].id); } catch (_) {}
        dispatchSessionChanged();
      } else {
        // No sessions left -> immediately create and select a new default chat
        try { await createSessionWithName(null, true); } catch (_) {}
        // createSessionWithName will set the active session, dispatch, and refresh tabs
      }
    }

    // Re-render tabs (safe even if createSessionWithName already refreshed)
    await refreshTabs();
    return removedAny;
  } catch (e) {
    console.error('[tabManager] deleteSession failed:', e && e.message ? e.message : e);
    return false;
  }
}

/* Rename a session (save name into JSON) */
async function renameSession(sessionId, newName) {
  if (!newName || typeof newName !== 'string') return false;
  try {
    const sessions = await getSessions();
    const found = sessions.find(s => s.id === sessionId);
    let filePath;
    if (found) filePath = found.path;
    else filePath = await sessionManager.getSessionFilePath(sessionId);

    let obj = {};
    try {
      const txt = await fs.readFile(filePath, 'utf8');
      obj = JSON.parse(txt || '{}');
    } catch (_) { obj = {}; }
    obj.name = newName;
    obj.lastused = new Date().toISOString();
    await fs.writeFile(filePath, JSON.stringify(obj, null, 2), 'utf8');
    await refreshTabs();
    return true;
  } catch (e) {
    console.error('[tabManager] renameSession failed:', e && e.message ? e.message : e);
    return false;
  }
}

/* Ensure there is an active session and its session file exists (create if needed) */
async function ensureActiveSession() {
  try {
    const sid = await sessionManager.getSessionId();
    try {
      const filePath = await sessionManager.getSessionFilePath(sid);
      const exists = await fs.access(filePath).then(() => true).catch(() => false);
      if (!exists) {
        const sessions = await getSessions();
        const name = generateDefaultName(sessions);
        const init = { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), session_id: sid, name, chat: [] };
        await fs.writeFile(filePath, JSON.stringify(init, null, 2), 'utf8');
      }
    } catch (e) {
      await createSessionWithName(null, true);
      return await readSelectedSessionIdFile();
    }
    await refreshTabs();
    return sid;
  } catch (e) {
    console.warn('[tabManager] ensureActiveSession failed:', e && e.message ? e.message : e);
    return null;
  }
}

/* Switch to a session (set as active). Save scroll for previous session first. */
async function switchToSession(sessionId) {
  try {
    if (!sessionId) return;

    // Get previous session ID before switching
    let prevSid = null;
    try { prevSid = await sessionManager.getSessionId(); } catch (_) {}

    // Save current session state before switching (if any)
    if (prevSid && prevSid !== sessionId && typeof document !== 'undefined') {
      const chatMessages = document.getElementById('chat-messages');
      if (chatMessages) {
        // Save scroll position
        try { await scrollCache.saveScrollFromContainer(prevSid, chatMessages); } catch (_) {}
        
        // Capture full session state for concurrent execution
        try { captureSessionState(prevSid, chatMessages); } catch (_) {}
      }
    }

    await sessionManager.setSessionId(sessionId);
    
    // Update render gate to point to new session
    try {
      const { setActiveSessionId } = require('./helpers/renderGate');
      setActiveSessionId(sessionId);
    } catch (_) {}
    
    // Immediately hide global + inline indicators to avoid leak during switch
    try { const ind = require('./ui/indicator'); ind.hideGptIndicator(); ind.hideInlineThinking(null); } catch (_) {}
    
    dispatchSessionChanged();
    await refreshTabs();
    
    // Note: Session state restoration is handled by historyDisplay.js after it renders
    // the chat history. We don't call restoreSessionRender here to avoid race conditions
    // where restoration happens before displayChatHistory completes.
    
  } catch (e) {
    console.warn('[tabManager] switchToSession failed:', e && e.message ? e.message : e);
  }
}

/* Dispatch global event to notify renderer parts */
function dispatchSessionChanged() {
  try {
    window.dispatchEvent(new CustomEvent('session-changed', { detail: { ts: Date.now() } }));
  } catch (e) { /* ignore */ }
}

/**
 * Update tab status classes based on concurrent session state
 * @param {HTMLElement} tabNode - The tab DOM element
 * @param {string} sessionId - The session ID
 */
function updateTabStatusClasses(tabNode, sessionId) {
  if (!tabNode || !sessionId) return;
  
  // Remove all status classes first
  tabNode.classList.remove('auaci-responding', 'auaci-awaiting-input', 'auaci-error');
  
  try {
    // Check concurrent session state first (preferred)
    if (isSessionAwaitingInput(sessionId)) {
      tabNode.classList.add('auaci-awaiting-input');
    } else if (isSessionResponding(sessionId)) {
      tabNode.classList.add('auaci-responding');
    } else if (isSessionError(sessionId)) {
      tabNode.classList.add('auaci-error');
    } else {
      // Fallback to legacy window map for backward compatibility
      const map = window.__auaciRespondingSessions || {};
      if (map && map[sessionId]) {
        tabNode.classList.add('auaci-responding');
      }
    }
  } catch (e) {
    // Fallback to legacy check
    try {
      const map = window.__auaciRespondingSessions || {};
      if (map && map[sessionId]) {
        tabNode.classList.add('auaci-responding');
      }
    } catch (_) {}
  }
}

/* Create tab DOM node */
function makeTabNode(s) {
  const tab = document.createElement('div');
  tab.className = 'auaci-chat-tab';
  tab.setAttribute('data-session-id', s.id);

  const label = document.createElement('div');
  label.className = 'auaci-tab-label';
  label.title = s.name || s.id;
  label.textContent = s.name || s.id;

  const close = document.createElement('div');
  close.className = 'auaci-tab-close';
  close.setAttribute('aria-hidden', 'true');
  close.textContent = '×';

  tab.appendChild(label);
  tab.appendChild(close);

  // Click to focus
  tab.addEventListener('click', async (ev) => {
    ev.preventDefault();
    await switchToSession(s.id);
  });

  // Right-click context menu
  tab.addEventListener('contextmenu', (ev) => {
    ev.preventDefault();
    chatTabOptions.showMenu(ev.clientX, ev.clientY, s.id);
  });

  // Close click: do NOT prompt here; central logic in deleteSessionById will prompt only when necessary
  close.addEventListener('click', async (ev) => {
    ev.stopPropagation();
    await deleteSessionById(s.id);
  });

  return tab;
}

/* Refresh tabs with minimal DOM churn */
async function refreshTabs() {
  if (typeof document === 'undefined') return;
  ensureStyles();

  const root = document.getElementById('chat-container');
  if (!root) return;

  let topbar = document.getElementById(TOPBAR_ID);
  if (!topbar) {
    topbar = document.createElement('div');
    topbar.id = TOPBAR_ID;

    const tabs = document.createElement('div');
    tabs.className = TABS_CONTAINER_CLASS;

    const addBtn = document.createElement('div');
    addBtn.id = ADD_BTN_ID;
    addBtn.title = 'Options';
    addBtn.textContent = '...';

    // Build dropdown menu
    const menu = document.createElement('div');
    menu.id = 'chat-options-menu';
    const itemNew = document.createElement('div');
    itemNew.className = 'chat-options-item';
    itemNew.textContent = '+ New Chat';
    const itemDeleteAll = document.createElement('div');
    itemDeleteAll.className = 'chat-options-item';
    itemDeleteAll.textContent = 'Delete All Chat';
    menu.appendChild(itemNew);
    menu.appendChild(itemDeleteAll);

    addBtn.appendChild(menu);

    function closeMenu() { menu.classList.remove('open'); }
    function toggleMenu() { menu.classList.toggle('open'); }

    addBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleMenu();
    });

    // Actions
    itemNew.addEventListener('click', async (e) => {
      e.stopPropagation();
      closeMenu();
      await createSessionWithName(null, true);
    });

    itemDeleteAll.addEventListener('click', async (e) => {
      e.stopPropagation();
      closeMenu();
      await deleteAllSessions();
    });

    // Close when clicking outside
    document.addEventListener('click', () => closeMenu());

    topbar.appendChild(tabs);
    topbar.appendChild(addBtn);

    const chatMessages = document.getElementById('chat-messages');
    if (chatMessages) root.insertBefore(topbar, chatMessages);
    else root.appendChild(topbar);
  }

  const tabsContainer = topbar.querySelector(`.${TABS_CONTAINER_CLASS}`);
  if (!tabsContainer) return;

  // Wire auto-hide scrollbar for the horizontal tab strip
  try { wireAutoHideScrollbarLocal(tabsContainer, { hideDelay: 900, leaveDelay: 300 }); } catch (_) {}

  // preserve previous scroll and active
  const prevScrollLeft = tabsContainer.scrollLeft;
  const prevActiveEl = tabsContainer.querySelector('.auaci-chat-tab.auaci-active');
  const prevActiveSid = prevActiveEl ? prevActiveEl.getAttribute('data-session-id') : null;

  const sessions = await getSessions();
  let activeSid = await readSelectedSessionIdFile();
  if (!activeSid && sessions.length > 0) {
    activeSid = sessions[0].id;
    try { await sessionManager.setSessionId(activeSid); } catch (_) {}
    dispatchSessionChanged();
  }

  // Build desired set
  const desiredIds = new Set(sessions.map(s => s.id));

  // Iterate sessions and ensure DOM order / minimal updates
  let insertIndex = 0;
  for (const s of sessions) {
    const selector = `.auaci-chat-tab[data-session-id="${s.id}"]`;
    let node = tabsContainer.querySelector(selector);

    if (node) {
      const currentIndex = Array.prototype.indexOf.call(tabsContainer.children, node);
      if (currentIndex !== insertIndex) {
        tabsContainer.insertBefore(node, tabsContainer.children[insertIndex] || null);
      }
      const label = node.querySelector('.auaci-tab-label');
      if (label && label.textContent !== (s.name || s.id)) {
        label.textContent = s.name || s.id;
        label.title = s.name || s.id;
      }
      // Update tab status classes based on concurrent session state
      updateTabStatusClasses(node, s.id);
      if (activeSid && activeSid === s.id) node.classList.add('auaci-active');
      else node.classList.remove('auaci-active');
    } else {
      // create and insert
      const newNode = makeTabNode(s);
      // Update tab status classes based on concurrent session state
      updateTabStatusClasses(newNode, s.id);
      if (activeSid && activeSid === s.id) newNode.classList.add('auaci-active');
      tabsContainer.insertBefore(newNode, tabsContainer.children[insertIndex] || null);
    }

    insertIndex++;
  }

  // Remove stale tabs
  const existingChildren = Array.from(tabsContainer.querySelectorAll('.auaci-chat-tab'));
  for (const ch of existingChildren) {
    const sid = ch.getAttribute('data-session-id');
    if (!desiredIds.has(sid)) ch.remove();
  }

  // Decide scrolling behaviour
  const activeEl = tabsContainer.querySelector('.auaci-chat-tab.auaci-active');
  if (!didInitialActiveScroll) {
    if (activeEl) {
      try { scrollElementIntoView(tabsContainer, activeEl, { axis: 'x', align: 'center', duration: 260 }); } catch (_) {}
    }
    didInitialActiveScroll = true;
  } else {
    if (prevActiveSid && prevActiveSid === activeSid) {
      try { tabsContainer.scrollLeft = prevScrollLeft; } catch (_) {}
    } else if (activeEl) {
      try { scrollElementIntoView(tabsContainer, activeEl, { axis: 'x', align: 'center', duration: 200 }); } catch (_) {}
    }
  }

  // If no sessions show project empty message
  const chatMessages = document.getElementById('chat-messages');
  if (sessions.length === 0 && chatMessages) {
    chatMessages.innerHTML = `<div class="auaci-empty-project">You have no chat session in this project directory yet, maybe start a new one?</div>`;
  }

  return true;
}

/* Inline rename */
function startInlineRename(tabEl, sessionId) {
  if (!tabEl) return;
  const labelEl = tabEl.querySelector('.auaci-tab-label');
  if (!labelEl) return;
  const prev = labelEl.textContent || '';
  const input = document.createElement('input');
  input.className = 'auaci-rename-input';
  input.value = prev;
  input.setAttribute('aria-label', 'Rename chat session');
  tabEl.replaceChild(input, labelEl);
  input.focus();
  input.select();

  function commit() {
    const v = String(input.value || '').trim();
    if (v && v !== prev) {
      renameSession(sessionId, v).catch(() => {});
    } else {
      refreshTabs();
    }
  }
  input.addEventListener('blur', () => { commit(); });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') input.blur();
    else if (e.key === 'Escape') refreshTabs();
  });
}

/* Setup entrypoint */
async function setupTabManager() {
  if (typeof document === 'undefined') return;
  if (document.readyState === 'loading') {
    await new Promise(resolve => document.addEventListener('DOMContentLoaded', resolve));
  }
  ensureStyles();
  await refreshTabs();

  window.addEventListener('session-changed', async () => {
    setTimeout(async () => {
      try { await refreshTabs(); } catch (_) {}
    }, 40);
  });

  // Save scroll for current session when the window is unloading (best-effort)
  try {
    window.addEventListener('beforeunload', () => {
      try {
        (async () => {
          try {
            const sid = await sessionManager.getSessionId();
            const chatMessages = document.getElementById('chat-messages');
            if (sid && chatMessages) await scrollCache.saveScrollFromContainer(sid, chatMessages);
          } catch (_) {}
        })();
      } catch (_) {}
    });
  } catch (_) {}

  // central handler for context-menu actions (rename/delete)
  window.addEventListener('chat-tab-action', async (e) => {
    const { action, sessionId } = (e && e.detail) ? e.detail : {};
    if (!action || !sessionId) return;
    if (action === 'rename') {
      // Use the dedicated rename UI (modal-like) instead of inline rename.
      // Try to obtain the current display name for nicer UX.
      let currentName = '';
      try {
        const topbar = document.getElementById(TOPBAR_ID);
        if (topbar) {
          const tabEl = topbar.querySelector(`.auaci-chat-tab[data-session-id="${sessionId}"]`);
          if (tabEl) {
            const label = tabEl.querySelector('.auaci-tab-label');
            if (label) currentName = String(label.textContent || '');
          }
        }
      } catch (_) {}

      // Fallback to reading from session files if we couldn't get a DOM label
      if (!currentName) {
        try {
          const sessions = await getSessions();
          const found = sessions.find(s => s.id === sessionId);
          if (found) currentName = found.name || '';
        } catch (_) {}
      }

      // Open rename UI. When user commits, call renameSession to persist and refresh.
      try {
        await renameUI.openRenameWindow(sessionId, currentName || '', async (newName) => {
          if (newName && typeof newName === 'string') {
            try { await renameSession(sessionId, newName); } catch (_) {}
          }
        });
      } catch (_) {
        // ignore
      }
    } else if (action === 'delete') {
      // let deleteSessionById decide whether a prompt is necessary
      await deleteSessionById(sessionId);
    }
  });
}

async function deleteAllSessions() {
  try {
    const sessions = await getSessions();
    // We will clear the sessions folders even if getSessions() returns empty, so don't early-return here.

    // Show single confirmation dialog for all sessions
    const ok = confirm('Do you wish to delete all chats? This cannot be undone.');
    if (!ok) return false;

    // Collect IDs for cache cleanup (from current listing)
    const ids = Array.isArray(sessions) ? sessions.map(s => s.id) : [];

    // 1) Best-effort delete every known session file (covers non-standard filenames)
    if (Array.isArray(sessions)) {
      for (const s of sessions) {
        try { if (s.path) await fs.unlink(s.path).catch(() => {}); } catch (_) {}
      }
    }

    // 2) Clear the sessions directories by removing all .json files
    for (const baseDir of sessionManager.CANDIDATE_BASE_DIRS) {
      const sessionsDir = path.join(baseDir, 'sessions');
      try {
        const entries = await fs.readdir(sessionsDir).catch(() => []);
        for (const f of entries) {
          const full = path.join(sessionsDir, f);
          // Only remove JSON files to avoid nuking unrelated artifacts
          if (f.endsWith('.json')) {
            try { await fs.unlink(full); } catch (_) {}
          }
        }
      } catch (_) {}
    }

    // 2.5) Clear all raw history files
    try {
      const rawHistoryDir = path.join(process.cwd(), '.auaci', 'chathistory', 'rawhistory');
      const rawEntries = await fs.readdir(rawHistoryDir).catch(() => []);
      for (const f of rawEntries) {
        if (f.endsWith('.json')) {
          try { await fs.unlink(path.join(rawHistoryDir, f)); } catch (_) {}
        }
      }
      console.log('[tabManager] Cleared all raw history files');
    } catch (e) {
      console.warn('[tabManager] Failed to clear raw history:', e?.message || e);
    }

    // 3) Remove active session pointers
    for (const baseDir of sessionManager.CANDIDATE_BASE_DIRS) {
      const sidFile = path.join(baseDir, 'session_id.txt');
      try { await fs.unlink(sidFile); } catch (_) {}
    }

    // 4) Clear per-session scroll cache entries (best-effort)
    for (const id of ids) {
      try { await scrollCache.deleteScroll(id); } catch (_) {}
    }

    // Immediately create and select a fresh default chat so the app never has zero sessions
    try { await createSessionWithName(null, true); } catch (_) {}
    return true;
  } catch (e) {
    console.error('[tabManager] deleteAllSessions failed:', e && e.message ? e.message : e);
    return false;
  }
}

module.exports = {
  setupTabManager,
  refreshTabs,
  getSessions,
  getActiveSessionId,
  ensureActiveSession,
  createSessionWithName,
  switchToSession,
  renameSession,
  deleteSessionById,
  deleteAllSessions
};
