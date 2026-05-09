const { initEvents } = require('./functions/chat/events');
const { sendMessage } = require('./functions/chat/messaging');
const { getBackgroundProcessor } = require('./functions/chat/backgroundProcessing');
const { initDirectoryViewer, setupEventListeners } = require('./functions/directory_viewer/init');
const { initEditor } = require('./functions/editor/init');
// const { initSizeLogger } = require('./functions/debug/init');
const { initProportionalLayout, reapplyProportions } = require('./functions/layout/init');
const { initTitleBar } = require('./functions/titlebar/init');
const { displayChatHistory } = require('./functions/chat/historyDisplay');
const { initTerminal } = require('./functions/terminal/init');
const { ipcRenderer } = require('electron');

window.chat = { sendMessage };

//
// Section visibility management
//
function applySectionVisibility(visibility) {
  const appContainer = document.getElementById('app-container');
  const directoryViewer = document.getElementById('directory-viewer');
  const editor = document.getElementById('editor');
  const chat = document.getElementById('chat');

  if (!appContainer || !directoryViewer || !editor || !chat) return;

  // Apply hidden class to sections
  if (visibility.directoryViewer) {
    directoryViewer.classList.remove('section-hidden');
  } else {
    directoryViewer.classList.add('section-hidden');
  }

  if (visibility.editor) {
    editor.classList.remove('section-hidden');
  } else {
    editor.classList.add('section-hidden');
  }

  if (visibility.chat) {
    chat.classList.remove('section-hidden');
  } else {
    chat.classList.add('section-hidden');
  }

  // Set data attributes on container for CSS expansion rules
  appContainer.setAttribute('data-directory-hidden', !visibility.directoryViewer);
  appContainer.setAttribute('data-editor-hidden', !visibility.editor);
  appContainer.setAttribute('data-chat-hidden', !visibility.chat);

  // Reapply layout proportions to handle the visibility change
  reapplyProportions();

  // Trigger Monaco editor resize if it exists
  if (window.monacoEditor && typeof window.monacoEditor.layout === 'function') {
    setTimeout(() => {
      try { window.monacoEditor.layout(); } catch (_) {}
    }, 50);
  }
}

// Listen for section visibility changes from main process
ipcRenderer.on('section-visibility-changed', (event, visibility) => {
  applySectionVisibility(visibility);
});

// Initialize section visibility on load
async function initSectionVisibility() {
  try {
    const visibility = await ipcRenderer.invoke('get-section-visibility');
    applySectionVisibility(visibility);
  } catch (err) {
    console.warn('Failed to get initial section visibility:', err);
  }
}

// Initialize background processing system
function initBackgroundProcessing() {
  try {
    const processor = getBackgroundProcessor();
    console.log('[index] Background processing system initialized');
  } catch (err) {
    console.warn('[index] Failed to initialize background processing:', err);
  }
}

//
// Integrate editor helpers (monacoFocusFix + pasteHandler) into this preload/renderer script.
// This attempts to require and attach them immediately and again on DOMContentLoaded as a fallback.
// It also exposes a tiny window.auaci debug API (safe here because nodeIntegration: true, contextIsolation: false).
//
let _editorHelpers = {
  monacoFocusFix: null,
  pasteHandler: null,
  attached: false
};

try {
  // Load helper modules relative to this file (src/main)
  try {
    _editorHelpers.monacoFocusFix = require('./functions/editor/monacoFocusFix');
  } catch (e) {
    console.debug('[index] monacoFocusFix require failed', e && e.message ? e.message : e);
  }

  // Skip custom pasteHandler entirely; rely on Monaco's native paste.
  const attachHelpers = () => {
    try {
      if (_editorHelpers.monacoFocusFix && typeof _editorHelpers.monacoFocusFix.attachMonacoFocusFix === 'function') {
        try {
          _editorHelpers.monacoFocusFix.attachMonacoFocusFix();
          console.debug('[index] monacoFocusFix.attachMonacoFocusFix() called');
        } catch (err) {
          console.warn('[index] monacoFocusFix.attachMonacoFocusFix() threw', err && err.message ? err.message : err);
        }
      }
    } catch (err) {
      console.debug('[index] monacoFocusFix attach check failed', err && err.message ? err.message : err);
    }

    _editorHelpers.attached = true;
  };

  // Try to attach immediately (pre-DOM). This is safe: listeners can be attached early.
  try { attachHelpers(); } catch (e) { console.debug('[index] initial attachHelpers failed', e && e.message ? e.message : e); }

  // Fallback: ensure attachment after DOM is ready (in case helpers need the DOM)
  document.addEventListener('DOMContentLoaded', () => {
    if (!_editorHelpers.attached) {
      try { attachHelpers(); } catch (e) { console.debug('[index] DOM attachHelpers failed', e && e.message ? e.message : e); }
    }
  });

  // Expose tiny debug/test helpers on window (nodeIntegration: true, contextIsolation: false)
  try {
    window.auaci = window.auaci || {};
    window.auaci.ensureHelpersAttached = () => {
      try { attachHelpers(); } catch (_) {}
      return true;
    };
  } catch (err) {
    console.debug('[index] could not expose window.auaci helpers', err && err.message ? err.message : err);
  }
} catch (err) {
  console.debug('[index] editor helpers integration failed', err && err.message ? err.message : err);
}

/* ------------------------------------------------------------------
   Initialize layout proportions first so panels get initial widths
   before other components render. This runs on DOMContentLoaded.
------------------------------------------------------------------ */
if (!window.layoutInitialized) {
  window.layoutInitialized = true;
  document.addEventListener('DOMContentLoaded', () => {
    initProportionalLayout();
  });
}

document.addEventListener('DOMContentLoaded', async () => {
  try {
    // Initialize tab manager FIRST to ensure tabs are rendered before history display
    try {
      const { setupTabManager } = require('./functions/chat/tabManager');
      await setupTabManager();
    } catch (e) {
      console.warn('[DEBUG] setupTabManager failed:', e);
    }
    
    // Initialize active session id for render gating before initial render
    try {
      const sid = await require('./functions/chat/sessionManager').getSessionId();
      const gate = require('./functions/chat/helpers/renderGate');
      gate.setActiveSessionId(sid);
      gate.enableRenderForSession(sid, true);
    } catch (_) {}
    await displayChatHistory();
  } catch (err) {
    console.error('[DEBUG] displayChatHistory failed:', err);
  }
});

if (!window.chatInitialized) {
  window.chatInitialized = true;
  document.addEventListener('DOMContentLoaded', initEvents);
}

if (!window.directoryViewerInitialized) {
  window.directoryViewerInitialized = true;
  document.addEventListener('DOMContentLoaded', () => {
    initDirectoryViewer();
    setupEventListeners();
  });
}

if (!window.editorInitialized) {
  window.editorInitialized = true;
  document.addEventListener('DOMContentLoaded', initEditor);
}

// Initialize terminal (IPC listeners + lazy creation on first open)
if (!window.terminalInitialized) {
  window.terminalInitialized = true;
  document.addEventListener('DOMContentLoaded', () => {
    initTerminal();
    // Auto show/hide terminal based on saved state
    try {
      const { getSavedTerminalState } = require('./functions/terminal/state-bridge');
      const st = getSavedTerminalState();
      if (st && st.terminalVisible) {
        const { showTerminal } = require('./functions/terminal/init');
        showTerminal();
      }
    } catch (_) {}
  });
}

// Initialize section visibility after DOM is ready
if (!window.sectionVisibilityInitialized) {
  window.sectionVisibilityInitialized = true;
  document.addEventListener('DOMContentLoaded', initSectionVisibility);
}

// Initialize background processing after DOM is ready
if (!window.backgroundProcessingInitialized) {
  window.backgroundProcessingInitialized = true;
  document.addEventListener('DOMContentLoaded', initBackgroundProcessing);
}

// Initialize size debug logger (if present)
/*
if (!window.sizeLoggerInitialized) {
  window.sizeLoggerInitialized = true;
  document.addEventListener('DOMContentLoaded', initSizeLogger);
}
*/

// Initialize titlebar (do NOT pass the DOM event object as the title)
if (!window.titlebarInitialized) {
  window.titlebarInitialized = true;
  // Wrap the call so DOMContentLoaded event is not forwarded into initTitleBar
  document.addEventListener('DOMContentLoaded', () => {
    initTitleBar('Auaci');
  });
}