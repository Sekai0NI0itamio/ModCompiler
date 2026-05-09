// src/main/functions/chat/events.js
const { ipcRenderer } = require('electron');
const fs = require('fs').promises;
const path = require('path');
const os = require('os');
const { setupDragDrop } = require('./dragDrop');
const { setupPaste } = require('./paste');
const { startCacheManager } = require('./messageCache');
const { displayChatHistory } = require('./historyDisplay');
const { sendMessage, setupCodeBlockListeners } = require('./messaging');
const { setupModelSelector } = require('./modelSelector'); // NEW
const { setupTabManager } = require('./tabManager'); // NEW - topbar tabs
require('./resize-textarea'); // Ensure input auto-resize behavior is active
require('./helpers/fileTokens').initFileTokens(); // Enable inline file tokens in input
const { loadCurrentInputDraft, saveCurrentInputDraft } = require('./incrementalHistoryStorage');
const { setupMessageContextMenu } = require('./messageContextMenu');
const { startMonitoring } = require('./connectionMonitor');
const { initTerminalInput } = require('./ui/terminalInput');
const { initMultiView } = require('./ui/multiView');

async function initEvents() {
  // Initialize globals only if they are not already set (don't clobber if displayChatHistory already ran)
  window.droppedFiles = window.droppedFiles || [];
  window.isGptResponding = typeof window.isGptResponding === 'boolean' ? window.isGptResponding : false;
  window.allChatEntries = Array.isArray(window.allChatEntries) ? window.allChatEntries : [];
  window.renderedStartIndex = (typeof window.renderedStartIndex === 'number') ? window.renderedStartIndex : 0;
  window.renderedEndIndex = (typeof window.renderedEndIndex === 'number') ? window.renderedEndIndex : 0;
  window.__restoreUndoAvailable = typeof window.__restoreUndoAvailable === 'boolean' ? window.__restoreUndoAvailable : false;
  // Track stopped sessions for immediate UI response
  window.__auaciStoppedSessions = window.__auaciStoppedSessions || {};

  // Ensure log file is writable
  const logPath = '/tmp/events.log';
  await fs.mkdir(path.dirname(logPath), { recursive: true }).catch(() => {});
  await fs.appendFile(logPath, `[${new Date().toISOString()}] Initialized events\n`);

  // NOTE: index.js already calls displayChatHistory() on DOMContentLoaded.
  // To avoid duplicate/conflicting calls and ordering issues, do NOT call displayChatHistory() again here.
  // If you *must* call it from here instead, remove the call in index.js and ensure initEvents runs first.

  // Initialize ancillary modules
  startCacheManager();
  setupDragDrop();
  setupPaste();
  setupMessageContextMenu();
  
  // Start connection monitoring
  startMonitoring();
  
  // Listen for tool completion in background mode
  window.addEventListener('tool-completed-in-background', async (event) => {
    try {
      const detail = event.detail || {};
      console.log('[events] Tool completed in background:', detail.toolName, 'for session:', detail.sessionId);
      
      // Store the background completion for later processing when focus is regained
      if (!window._backgroundToolCompletions) {
        window._backgroundToolCompletions = [];
      }
      window._backgroundToolCompletions.push({
        sessionId: detail.sessionId,
        entryIndex: detail.entryIndex,
        toolName: detail.toolName,
        toolId: detail.toolId,
        timestamp: Date.now()
      });
      
      // Also trigger immediate refresh if the session is currently active
      if (detail.sessionId && window.currentSessionId === detail.sessionId) {
        const { displayChatHistory } = require('./historyDisplay');
        await displayChatHistory();
      }
    } catch (err) {
      console.error('[events] Failed to handle background tool completion:', err);
    }
  });

  // Listen for retry requests from connection monitor
  window.addEventListener('auaci:retry-request', async (event) => {
    try {
      const detail = event.detail || {};
      const sessionId = detail.sessionId;
      if (!sessionId) return;
      
      // Get the last user message from history and resend
      const { getCurrentChatHistory } = require('./incrementalHistoryStorage');
      const history = await getCurrentChatHistory();
      if (!history || !Array.isArray(history.chat) || history.chat.length === 0) return;
      
      // Find the last entry that was streaming/incomplete
      const lastEntry = history.chat[history.chat.length - 1];
      if (!lastEntry || !lastEntry.user) return;
      
      // Put the user's text back in the input and trigger send
      const userInput = document.getElementById('user-input');
      if (userInput) {
        const text = lastEntry.user.text || '';
        if (userInput.value !== undefined) userInput.value = text;
        else userInput.innerText = text;
        
        // Restore files if any
        window.droppedFiles = Array.isArray(lastEntry.user.files) ? lastEntry.user.files : [];
        
        // Remove the incomplete entry from history before resending
        const { deleteChatEntry } = require('./incrementalHistoryStorage');
        const { deleteMessagesByEntryIndex } = require('./rawHistoryStorage');
        const entryIndex = history.chat.length - 1;
        await deleteChatEntry(entryIndex, sessionId);
        await deleteMessagesByEntryIndex(sessionId, entryIndex);
        
        // Remove from DOM
        const container = document.getElementById('chat-messages');
        if (container) {
          const messages = container.querySelectorAll(`[data-entry-index="${entryIndex}"]`);
          messages.forEach(msg => msg.remove());
        }
        
        // Trigger send
        setTimeout(() => {
          try { sendMessage(); } catch (_) {}
        }, 100);
      }
    } catch (err) {
      console.error('[events] Retry request failed:', err);
    }
  });

  // NEW: initialize model selector UI + persistence
  try {
    await setupModelSelector();
  } catch (err) {
    console.error('Failed to initialize model selector:', err);
  }

  // NEW: initialize tab manager (topbar with chat session tabs)
  // Note: setupTabManager is also called in index.js before displayChatHistory
  // This call ensures it's initialized if events.js runs first or independently
  try {
    await setupTabManager();
  } catch (err) {
    // Ignore if already initialized
    if (!err.message?.includes('already')) {
      console.error('Failed to initialize tab manager:', err);
    }
  }

  // Initialize terminal input (CD navigation + autocomplete)
  try {
    await initTerminalInput();
  } catch (err) {
    console.error('Failed to initialize terminal input:', err);
  }

  // Initialize multi-view system
  try {
    initMultiView();
  } catch (err) {
    console.error('Failed to initialize multi-view:', err);
  }

  // Wire up stop button on initial load
  try {
    const stopBtn = document.getElementById('stop-response-btn');
    if (stopBtn) {
      stopBtn.disabled = true; // Initially disabled
      stopBtn.onclick = async () => {
        try {
          const sid = await require('./sessionManager').getSessionId();
          if (!sid) return;
          
          // IMMEDIATE STOP: Set stopped state immediately before attempting backend stop
          window.__auaciStoppedSessions = window.__auaciStoppedSessions || {}; 
          window.__auaciStoppedSessions[sid] = true;
          
          // Also set legacy stop requested flag for backward compatibility
          window.__auaciStopRequested = window.__auaciStopRequested || {}; 
          window.__auaciStopRequested[sid] = true;
          
          // Attempt backend stop (best-effort, may already be finishing)
          try { 
            if (window.__auaciRequestIds && window.__auaciRequestIds[sid]) 
              await require('./gpt-api').stopRequest(window.__auaciRequestIds[sid]); 
          } catch (_) {}
          
          // Abort any remaining network calls
          try { 
            const ac = window.__auaciAbortControllers && window.__auaciAbortControllers[sid]; 
            if (ac) ac.abort(); 
          } catch (_) {}
          
          // Disable button immediately to prevent multiple clicks
          stopBtn.disabled = true;
        } catch (err) {
          console.error('[events] Stop button error:', err);
        }
      };
    }
  } catch (err) {
    console.error('Failed to wire up stop button:', err);
  }

  // Listen for session-changed events and refresh chat history
  window.addEventListener('session-changed', async () => {
    try {
      // Proactively hide any indicators before render to avoid leaks across sessions
      try {
        const ind = require('./ui/indicator');
        ind.hideGptIndicator();
        ind.hideInlineThinking(null);
      } catch (_) {}
      // Track active session for render gating
      let sid = null;
      try {
        sid = await require('./sessionManager').getSessionId();
        const { setActiveSessionId, enableRenderForSession } = require('./helpers/renderGate');
        setActiveSessionId(sid);
        enableRenderForSession(sid, true);
      } catch (_) {}
      await displayChatHistory();
      // Toggle indicator visibility for the newly active session based on per-session responding map and rebind Stop button
      try {
        const { showGptIndicator, hideGptIndicator, showInlineThinking, hideInlineThinking } = require('./ui/indicator');
        const map = window.__auaciRespondingSessions || {};
        if (sid && map && map[sid]) {
          showGptIndicator(sid); showInlineThinking(sid);
          const btn = document.getElementById('stop-response-btn');
          if (btn) {
            btn.disabled = false;
            btn.onclick = async () => {
              // IMMEDIATE STOP: Set stopped state immediately before attempting backend stop
              try { 
                window.__auaciStoppedSessions = window.__auaciStoppedSessions || {}; 
                window.__auaciStoppedSessions[sid] = true; 
              } catch (_) {}
              // Also set legacy stop requested flag for backward compatibility
              try { 
                window.__auaciStopRequested = window.__auaciStopRequested || {}; 
                window.__auaciStopRequested[sid] = true; 
              } catch (_) {}
              // Attempt backend stop (best-effort, may already be finishing)
              try { 
                if (window.__auaciRequestIds && window.__auaciRequestIds[sid]) 
                  await require('./gpt-api').stopRequest(window.__auaciRequestIds[sid]); 
              } catch (_) {}
              // Abort any remaining network calls
              try { 
                const ac = window.__auaciAbortControllers && window.__auaciAbortControllers[sid]; 
                if (ac) ac.abort(); 
              } catch (_) {}
              // Disable button immediately to prevent multiple clicks
              try { btn.disabled = true; } catch (_) {}
            };
          }
        } else {
          hideGptIndicator(); hideInlineThinking(null);
          const btn = document.getElementById('stop-response-btn');
          if (btn) { btn.disabled = true; btn.onclick = null; }
        }
      } catch (_) {}
      // Load per-session draft into the input box
      const ta = document.getElementById('user-input');
      const filePreviews = document.getElementById('file-previews');
      if (ta) {
        const draft = await loadCurrentInputDraft();
        const currentText = (ta.value != null) ? ta.value : (ta.innerText || '');
        // Only overwrite the input if it is currently empty (avoid clobbering user typing during session switch)
        const newText = (draft && typeof draft.text === 'string') ? draft.text : '';
        if (!currentText || currentText.trim().length === 0) {
          if (ta.value != null) ta.value = newText; else ta.innerText = newText;
        }
        // Update files only when we did not detect any existing tokens/content indicating active typing
        window.droppedFiles = Array.isArray(draft && draft.files) ? draft.files : [];
        if (filePreviews) {
          try {
            // Do not render legacy file preview boxes in the input; rely on inline tokens only.
            filePreviews.innerHTML = '';
            filePreviews.style.display = 'none';
          } catch (_) {}
        }
        // trigger auto-resize logic bound to 'input'
        try {
          const evt = new Event('input', { bubbles: true });
          ta.dispatchEvent(evt);
        } catch (_) {}
        // ensure focus so typing goes to the input for new sessions
        try { ta.focus(); } catch (_) {}
      }
    } catch (e) {
      console.warn('session-changed handler failed:', e);
    }
  });

  // Keydown event listener for sending messages + draft persistence
  const userInput = document.getElementById('user-input');
  if (userInput) {
    // Debounced saving of input drafts
    let draftTimer = null;
    const DEBOUNCE_MS = 350;
    function queueSave() {
      try { if (draftTimer) clearTimeout(draftTimer); } catch (_) {}
      draftTimer = setTimeout(async () => {
        try {
          const text = (userInput.value != null) ? userInput.value : (userInput.innerText || '');
          await saveCurrentInputDraft(text, Array.isArray(window.droppedFiles) ? window.droppedFiles : []);
        } catch (_) {}
      }, DEBOUNCE_MS);
    }

    // Track IME composition state to avoid sending while composing
    let isComposing = false;
    userInput.addEventListener('compositionstart', () => { isComposing = true; });
    userInput.addEventListener('compositionend', () => { isComposing = false; });

    userInput.addEventListener('input', queueSave);

    userInput.addEventListener('keydown', async (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        // Allow send only if the ACTIVE session is not responding
        let allow = true;
        try {
          const sid = await require('./sessionManager').getSessionId();
          const map = window.__auaciRespondingSessions || {};
          if (sid && map && map[sid]) allow = false;
        } catch (_) {}
        if (!allow) return;
        // Ignore Enter while IME composition is active (prevents empty send)
        if (e.isComposing || isComposing || e.keyCode === 229) {
          return;
        }
        e.preventDefault();
        // Defer send to end of event loop so DOM value includes any final changes
        setTimeout(() => { try { sendMessage(); } catch (_) {} }, 0);
      }
    });

    // Load current session's draft on init
    try {
      (async () => {
        const draft = await loadCurrentInputDraft();
        const text = (draft && typeof draft.text === 'string') ? draft.text : '';
        if (userInput.value != null) userInput.value = text; else userInput.innerText = text;
        window.droppedFiles = Array.isArray(draft && draft.files) ? draft.files : [];
        // Render tokens for any files in draft
        try { if (window.droppedFiles.length) require('./helpers/fileTokens').insertFileTokens(window.droppedFiles); } catch (_) {}
        try { userInput.dispatchEvent(new Event('input', { bubbles: true })); } catch (_) {}
      })();
    } catch (_) {}
  } else {
    console.warn('initEvents: #user-input not found');
  }
}

module.exports = { initEvents };
