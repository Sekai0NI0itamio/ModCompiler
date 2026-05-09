// main.js
const { app, BrowserWindow, screen, ipcMain, clipboard, Menu, dialog } = require('electron');
const { execFile } = require('child_process');
const path = require('path');
const fs = require('fs');
// Debug logging for file drop / navigation behavior
const MAIN_DROP_LOG = '/tmp/electron_drop_debug.log';
function logDropDebug(message, extra) {
  try {
    const line = `[${new Date().toISOString()}] ${message}` + (extra ? ' ' + JSON.stringify(extra) : '') + '\n';
    fs.appendFileSync(MAIN_DROP_LOG, line);
  } catch (_) {}
}

// Config handler (new)
const configHandler = require(path.join(__dirname, 'src', 'main', 'functions', 'configHandler'));

// Backup main module (registers IPC and the backup window creation)
const backupMain = require(path.join(__dirname, 'src', 'main', 'functions', 'backup', 'main'));

let mainWindow = null;
let settingsWindow = null;

// Internal HTML files that are allowed to navigate via file:// without being treated as
// external file drops. Any file:// navigation to a path outside this allowlist will be
// intercepted and routed to the main renderer as an external file drop.
const INTERNAL_HTML_PATHS = new Set([
  path.join(__dirname, 'src', 'main', 'index.html'),
  path.join(__dirname, 'src', 'main', 'functions', 'settings', 'index.html'),
  path.join(__dirname, 'src', 'main', 'functions', 'file_info', 'index.html'),
  path.join(__dirname, 'src', 'main', 'functions', 'backup', 'index.html')
].map(p => {
  try { return path.normalize(p); } catch (_) { return p; }
}));

function decodeFileUrlToPath(url) {
  if (!url || typeof url !== 'string') return null;
  if (!url.toLowerCase().startsWith('file://')) return null;
  try {
    const u = new URL(url);
    return decodeURI(u.pathname || '');
  } catch (_) {
    try { return decodeURI(url.replace(/^file:\/\//i, '')); } catch (_) { return null; }
  }
}

function isInternalAppHtml(filePath) {
  if (!filePath) return false;
  try {
    const norm = path.normalize(filePath);
    return INTERNAL_HTML_PATHS.has(norm);
  } catch (_) {
    return false;
  }
}

function sendExternalFileToRenderer(filePath, source) {
  if (!filePath) return;
  try {
    logDropDebug('sendExternalFileToRenderer called', { filePath, source });
    // Prefer primary mainWindow, but fall back to any existing window if needed.
    if (!mainWindow || mainWindow.isDestroyed()) {
      const candidates = BrowserWindow.getAllWindows().filter(w => !w.isDestroyed());
      if (candidates.length > 0) {
        mainWindow = candidates[0];
      }
    }

    if (!mainWindow || mainWindow.isDestroyed()) {
      logDropDebug('sendExternalFileToRenderer: no available mainWindow to send to', { filePath, source });
      return;
    }

    const wc = mainWindow.webContents;
    const doSend = () => {
      try {
        logDropDebug('sendExternalFileToRenderer: sending to renderer', { filePath, source, webContentsId: wc.id });
        wc.send('external-file-drop-paths', [filePath]);
      } catch (e) {
        logDropDebug('sendExternalFileToRenderer: send failed', { error: e && e.message ? e.message : String(e) });
      }
    };
    if (wc.isLoading && wc.isLoading()) {
      logDropDebug('sendExternalFileToRenderer: webContents still loading, waiting for did-finish-load', { filePath, source, webContentsId: wc.id });
      wc.once('did-finish-load', doSend);
    } else {
      doSend();
    }
  } catch (err) {
    console.warn('sendExternalFileToRenderer error:', err && err.message ? err.message : err);
    logDropDebug('sendExternalFileToRenderer threw', { error: err && err.message ? err.message : String(err), filePath, source });
  }
}

// macOS: handle files opened via Finder / Dock (including drag-and-drop onto the app icon)
// so we do not spawn a separate window that simply shows the raw file contents. Instead,
// we route the file path into the main renderer via 'external-file-drop-paths'.
if (process.platform === 'darwin') {
  app.on('open-file', (event, filePath) => {
    event.preventDefault();
    if (!filePath) return;

    logDropDebug('app.open-file event', { filePath });

    const sendPathToRenderer = () => {
      try {
        sendExternalFileToRenderer(filePath, 'open-file');
      } catch (err) {
        console.warn('open-file handler error:', err && err.message ? err.message : err);
        logDropDebug('open-file handler error', { error: err && err.message ? err.message : String(err) });
      }
    };

    if (app.isReady()) {
      sendPathToRenderer();
    } else {
      app.once('ready', sendPathToRenderer);
    }
  });
}

async function createWindow() {
  const { width, height } = screen.getPrimaryDisplay().workAreaSize;
  const margin = 20;

  // Use macOS hiddenInset so traffic lights remain available while the titlebar is hidden.
  // On other platforms use frameless window so we can draw our own controls.
  const isMac = process.platform === 'darwin';

  const win = new BrowserWindow({
    width: width - 2 * margin,
    height: height - 2 * margin,
    x: margin,
    y: margin,
    // macOS: keep native traffic lights but hide the titlebar inset
    // Other platforms: create frameless window so we can render our own controls
    ...(isMac ? { titleBarStyle: 'hiddenInset', frame: true } : { frame: false }),
    webPreferences: {
      preload: path.join(__dirname, 'src', 'main', 'preload.js'),
      nodeIntegration: true,
      contextIsolation: false,
      backgroundThrottling: false
    }
  });

  win.loadFile(path.join(__dirname, 'src', 'main', 'index.html'));

  // Ensure Cmd/Ctrl+V always performs paste in the focused element (editor/chat/etc.)
  try {
    win.webContents.on('before-input-event', (event, input) => {
      try {
        // Only handle keyDown for V with Cmd/Ctrl
        const k = (input.key || '').toLowerCase();
        if (input && input.type === 'keyDown') {
          if ((input.control || input.meta) && k === 'v') {
            // Let the renderer handle its own paste listeners; we just trigger the native paste command
            try { win.webContents.paste(); } catch (_) {}
            event.preventDefault();
            return;
          }
        }
      } catch (_) {}
    });
  } catch (err) {
    console.warn('Failed to bind before-input-event paste handler:', err && err.message ? err.message : err);
  }

  // Forward maximize/unmaximize events so renderer can update controls (titlebar)
  win.on('maximize', () => {
    try {
      win.webContents.send('window-is-maximized');
    } catch (e) { /* ignore */ }
  });
  win.on('unmaximize', () => {
    try {
      win.webContents.send('window-is-unmaximized');
    } catch (e) { /* ignore */ }
  });

  // Keep mainWindow reference and clear on close
  mainWindow = win;
  win.on('closed', () => {
    if (mainWindow === win) mainWindow = null;
  });

  // After content loads, send the current config to renderer so it can apply immediately.
  win.webContents.once('did-finish-load', () => {
    try {
      const cfg = configHandler.getConfig();
      win.webContents.send('config-loaded', cfg);
    } catch (err) {
      console.warn('Failed to send config-loaded to main window:', err);
    }
  });

  return win;
}

/**
 * Compute centered bounds for the settings window relative to the main app window.
 * Width = 4/5 of reference width, Height = 3/4 of reference height.
 * Reference is mainWindow bounds if available, otherwise primary display workArea.
 */
function computeSettingsBounds() {
  let ref;
  if (mainWindow && !mainWindow.isDestroyed()) {
    ref = mainWindow.getBounds(); // { x, y, width, height }
  } else {
    // fallback to primary display workArea which includes x/y
    const disp = screen.getPrimaryDisplay();
    ref = disp.workArea; // { x, y, width, height }
  }

  const width = Math.max(320, Math.round(ref.width * 4 / 5)); // min width safety
  const height = Math.max(240, Math.round(ref.height * 3 / 4)); // min height safety
  const x = Math.round(ref.x + (ref.width - width) / 2);
  const y = Math.round(ref.y + (ref.height - height) / 2);

  return { x, y, width, height };
}

/* ------------------------
   IPC: window control
   renderer will send 'window-control' with actions:
     'minimize', 'maximize', 'unmaximize', 'toggle-maximize', 'close'
   we look up the sender window and perform the action.
   ------------------------ */
ipcMain.on('window-control', (event, action) => {
  const win = BrowserWindow.fromWebContents(event.sender);
  if (!win) return;
  try {
    switch (action) {
      case 'minimize':
        win.minimize();
        break;
      case 'maximize':
        win.maximize();
        break;
      case 'unmaximize':
        win.unmaximize();
        break;
      case 'toggle-maximize':
        if (win.isMaximized()) win.unmaximize();
        else win.maximize();
        break;
      case 'close':
        win.close();
        break;
      default:
        console.warn('Unknown window-control action:', action);
    }
  } catch (err) {
    console.error('window-control error:', err);
  }
});

/* ------------------------
   Settings window creation & IPC
   ------------------------ */
function createSettingsWindow() {
  const bounds = computeSettingsBounds();

  if (settingsWindow && !settingsWindow.isDestroyed()) {
    // Reposition / resize and focus existing settings window
    try {
      settingsWindow.setBounds(bounds);
      if (settingsWindow.isMinimized()) settingsWindow.restore();
      settingsWindow.focus();
    } catch (e) {
      console.error('Failed to reposition existing settings window:', e);
    }
    return;
  }

  settingsWindow = new BrowserWindow({
    x: bounds.x,
    y: bounds.y,
    width: bounds.width,
    height: bounds.height,
    minWidth: 480,
    minHeight: 320,
    title: 'Settings',
    resizable: true,
    // Keep native frame so macOS traffic lights are present
    frame: true,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
      // If your app uses preload scripts instead, set preload here accordingly.
    }
  });

  const settingsHtml = path.join(__dirname, 'src', 'main', 'functions', 'settings', 'index.html');
  settingsWindow.loadFile(settingsHtml);

  // When settings window finishes load send current config too
  settingsWindow.webContents.once('did-finish-load', () => {
    try {
      const cfg = configHandler.getConfig();
      settingsWindow.webContents.send('config-loaded', cfg);
    } catch (err) { /* ignore */ }
  });

  settingsWindow.on('closed', () => {
    settingsWindow = null;
  });
}

ipcMain.on('open-settings-window', (event) => {
  try {
    createSettingsWindow();
  } catch (err) {
    console.error('Failed to open settings window:', err && err.message ? err.message : err);
  }
});

/* ------------------------
   Register backup module (main-process)
   ------------------------ */
try {
  // Pass required runtime things to the backup module so it can create its own window and handlers
  backupMain.register({
    ipcMain,
    BrowserWindow,
    screen,
    execFile,
    path,
    computeSettingsBounds
  });
} catch (err) {
  console.error('Failed to initialize backup main module:', err);
}

// Register terminal IPC (main-side PTY)
try {
  const terminalMain = require(path.join(__dirname, 'src', 'main', 'functions', 'terminal', 'main'));
  terminalMain.register({ ipcMain, BrowserWindow });
} catch (err) {
  console.error('Failed to initialize terminal main module:', err);
}

// Register main process background processing support
try {
  const { registerMainProcessBackground } = require(path.join(__dirname, 'src', 'main', 'functions', 'chat', 'mainProcessBackground'));
  registerMainProcessBackground(ipcMain);
} catch (err) {
  console.error('Failed to initialize main process background support:', err);
}

// Register File Info module (details popup)
try {
  const fileInfoMain = require(path.join(__dirname, 'src', 'main', 'functions', 'file_info', 'main'));
  fileInfoMain.register({ ipcMain, BrowserWindow, screen });
} catch (err) {
  console.error('Failed to initialize file info main module:', err);
}

/* ------------------------
   Config IPC handlers (expose to renderers)
   - get-config: returns current config (in-memory)
   - set-config: overwrite config (persist)
   - update-config: shallow-merge patch into current config and persist
   ------------------------ */
ipcMain.handle('get-config', async () => {
  try {
    return configHandler.getConfig();
  } catch (err) {
    return { error: String(err) };
  }
});

ipcMain.handle('set-config', async (event, newConfig) => {
  try {
    const result = await configHandler.setConfig(newConfig);
    // Broadcast new config to all windows
    BrowserWindow.getAllWindows().forEach(w => {
      try { w.webContents.send('config-loaded', result); } catch(_) {}
    });
    return result;
  } catch (err) {
    console.error('set-config failed:', err);
    throw err;
  }
});

ipcMain.handle('update-config', async (event, patch) => {
  try {
    const result = await configHandler.updateConfig(patch);
    // Broadcast new config to all windows
    BrowserWindow.getAllWindows().forEach(w => {
      try { w.webContents.send('config-loaded', result); } catch(_) {}
    });
    return result;
  } catch (err) {
    console.error('update-config failed:', err);
    throw err;
  }
});

// NEW: expose the internal default config via IPC so renderers can reset to defaults
ipcMain.handle('get-default-config', async () => {
  try {
    return configHandler._DEFAULT;
  } catch (err) {
    return { error: String(err) };
  }
});

/* ------------------------
   Excluded Folders IPC handlers
   These manage the excluded folders config for AI tools (ls, grep, find_files)
   ------------------------ */
const excludedFoldersModule = require('./src/main/functions/chat/commands/lib/excludedFolders');

ipcMain.handle('get-excluded-folders', async () => {
  try {
    const folders = await excludedFoldersModule.getExcludedFolders();
    return { folders };
  } catch (err) {
    console.error('get-excluded-folders failed:', err);
    return { error: String(err), folders: [] };
  }
});

ipcMain.handle('save-excluded-folders', async (event, folders) => {
  try {
    await excludedFoldersModule.saveExcludedFolders(folders);
    return { success: true, folders };
  } catch (err) {
    console.error('save-excluded-folders failed:', err);
    throw err;
  }
});

ipcMain.handle('reset-excluded-folders', async () => {
  try {
    const folders = await excludedFoldersModule.resetToDefaults();
    return { folders };
  } catch (err) {
    console.error('reset-excluded-folders failed:', err);
    throw err;
  }
});

// Git checkpoint handler
const gitCheckpointManager = require(path.join(__dirname, 'src', 'main', 'functions', 'chat', 'gitCheckpointManager'));

ipcMain.handle('git-restore-checkpoint', async (event, sessionId, entryIndex) => {
  try {
    const result = await gitCheckpointManager.restoreToCheckpoint(sessionId, entryIndex);
    return result;
  } catch (err) {
    console.error('[main] git-restore-checkpoint failed:', err);
    return false;
  }
});

ipcMain.handle('git-create-checkpoint', async (event, sessionId, entryIndex, userMessage) => {
  try {
    const result = await gitCheckpointManager.createCheckpoint(sessionId, entryIndex, userMessage);
    return result;
  } catch (err) {
    console.error('[main] git-create-checkpoint failed:', err);
    return false;
  }
});

ipcMain.handle('git-undo-restore', async (event, sessionId) => {
  try {
    const result = await gitCheckpointManager.undoLastRestore(sessionId);
    return result;
  } catch (err) {
    console.error('[main] git-undo-restore failed:', err);
    return false;
  }
});

ipcMain.handle('git-invalidate-restore-undo', async (event, sessionId) => {
  try {
    const result = await gitCheckpointManager.invalidateRestoreUndo(sessionId);
    return result;
  } catch (err) {
    console.error('[main] git-invalidate-restore-undo failed:', err);
    return false;
  }
});

/* ------------------------
   Existing IPC handlers (unchanged)...
   ------------------------ */
ipcMain.handle('get-pasted-files', async () => {
  try {
    const fileUrlData = clipboard.readBuffer('public.file-url').toString('utf8');
    const utf8Text = clipboard.readBuffer('public.utf8-plain-text').toString('utf8');
    const plainText = clipboard.readText('text/plain');
    const availableFormats = clipboard.availableFormats();

    // NOTE: binary moved to bin/ — please ensure process_paste_files binary exists in the project's bin/
    const binaryPath = path.join(__dirname, 'bin', 'process_paste_files');
    const { stdout, stderr } = await new Promise((resolve, reject) => {
      execFile(binaryPath, ['--paste'], (error, stdout, stderr) => {
        if (error) reject(error);
        resolve({ stdout, stderr });
      });
    });

    if (stderr) {
      return { error: `Paste C program error: ${stderr}`, clipboardData: {
        'public.file-url': fileUrlData,
        'public.utf8-plain-text': utf8Text,
        'text/plain': plainText,
        availableTypes: availableFormats
      }};
    }

    let files;
    try {
      files = JSON.parse(stdout);
    } catch (err) {
      return { error: `Failed to parse paste C program output: ${err.message}`, clipboardData: {
        'public.file-url': fileUrlData,
        'public.utf8-plain-text': utf8Text,
        'text/plain': plainText,
        availableTypes: availableFormats
      }};
    }

    if (!Array.isArray(files) || files.length === 0) {
      return { error: 'No valid files found in paste', clipboardData: {
        'public.file-url': fileUrlData,
        'public.utf8-plain-text': utf8Text,
        'text/plain': plainText,
        availableTypes: availableFormats
      }};
    }

    return { files };
  } catch (err) {
    return { error: err.message, clipboardData: {
      'public.file-url': clipboard.readBuffer('public.file-url').toString('utf8'),
      'public.utf8-plain-text': clipboard.readBuffer('public.utf8-plain-text').toString('utf8'),
      'text/plain': clipboard.readText('text/plain'),
      availableTypes: clipboard.availableFormats()
    }};
  }
});

ipcMain.handle('get-project-root', () => {
  return process.cwd();
});

ipcMain.handle('get-app-path', () => {
  return app.getAppPath();
});

// Web fetch with JavaScript rendering - creates a hidden BrowserWindow to render JS-heavy pages
ipcMain.handle('web-fetch-with-js', async (event, options = {}) => {
  const { url, timeout = 20000 } = options;
  
  return new Promise((resolve, reject) => {
    let win = null;
    let timeoutId = null;
    let resolved = false;
    
    const cleanup = () => {
      if (timeoutId) clearTimeout(timeoutId);
      if (win && !win.isDestroyed()) {
        try { win.close(); } catch (_) {}
      }
    };
    
    const finish = (result) => {
      if (resolved) return;
      resolved = true;
      cleanup();
      resolve(result);
    };
    
    const fail = (err) => {
      if (resolved) return;
      resolved = true;
      cleanup();
      reject(err);
    };
    
    try {
      win = new BrowserWindow({
        width: 1280,
        height: 800,
        show: false,
        webPreferences: {
          nodeIntegration: false,
          contextIsolation: true,
          sandbox: true,
          javascript: true
        }
      });
      
      timeoutId = setTimeout(() => fail(new Error('Page load timeout')), timeout);
      
      win.webContents.on('did-finish-load', async () => {
        try {
          // Wait for JS to execute
          await new Promise(r => setTimeout(r, 2000));
          const html = await win.webContents.executeJavaScript('document.documentElement.outerHTML');
          finish({ url, statusCode: 200, body: html, jsRendered: true });
        } catch (err) {
          fail(err);
        }
      });
      
      win.webContents.on('did-fail-load', (event, errorCode, errorDescription) => {
        fail(new Error(`Page load failed: ${errorDescription}`));
      });
      
      win.loadURL(url);
    } catch (err) {
      fail(err);
    }
  });
});

// Open system file selector for Auto Edit (files, folders, multi-select)
ipcMain.handle('autoedit-open-file-dialog', async (event, options = {}) => {
  try {
    const { defaultPath } = options || {};
    const win = BrowserWindow.fromWebContents(event.sender);

    const dialogOptions = {
      title: 'Select files or folders',
      properties: ['openFile', 'openDirectory', 'multiSelections'],
    };

    if (typeof defaultPath === 'string' && defaultPath.trim()) {
      dialogOptions.defaultPath = defaultPath;
    }

    const result = await dialog.showOpenDialog(win || null, dialogOptions);
    return {
      canceled: !!result.canceled,
      paths: Array.isArray(result.filePaths) ? result.filePaths : [],
    };
  } catch (err) {
    console.error('autoedit-open-file-dialog failed:', err && err.message ? err.message : err);
    return { canceled: true, paths: [], error: String(err && err.message ? err.message : err) };
  }
});

// Project indexing handler
ipcMain.handle('index-project', async () => {
  try {
    const projectIndexer = require(path.join(__dirname, 'src', 'main', 'functions', 'projectIndexer', 'index'));
    const projectRoot = process.cwd(); // Use current working directory as project root
    
    console.log('Starting project indexing for:', projectRoot);
    const result = await projectIndexer.indexProject(projectRoot);
    
    if (result.success) {
      console.log('Project indexing completed successfully');
      return result;
    } else {
      console.error('Project indexing failed:', result.error);
      return { error: result.error };
    }
  } catch (error) {
    console.error('IPC index-project handler error:', error);
    return { error: error.message };
  }
});

/* ------------------------
   Global interception of unintended file:// navigations (external file drops)
   ------------------------ */
try {
  app.on('web-contents-created', (_event, contents) => {
    try {
      const wcId = contents.id;
      logDropDebug('web-contents-created', { webContentsId: wcId });
      contents.on('will-navigate', (event, url) => {
        try {
          const filePath = decodeFileUrlToPath(url);
          logDropDebug('will-navigate', { webContentsId: wcId, url, filePath });
          if (!filePath) return; // not a file:// URL

          // Allow internal HTML files for our own windows.
          if (isInternalAppHtml(filePath)) {
            logDropDebug('will-navigate: allowed internal HTML', { webContentsId: wcId, filePath });
            return;
          }

          // Any other file:// navigation is treated as an external file drop.
          event.preventDefault();
          logDropDebug('will-navigate: treating as external file drop', { webContentsId: wcId, filePath });
          sendExternalFileToRenderer(filePath, 'will-navigate');
        } catch (err) {
          console.warn('global will-navigate handler error:', err && err.message ? err.message : err);
          logDropDebug('global will-navigate handler error', { error: err && err.message ? err.message : String(err), url });
        }
      });
    } catch (err) {
      console.warn('Failed to attach will-navigate to webContents:', err && err.message ? err.message : err);
      logDropDebug('Failed to attach will-navigate to webContents', { error: err && err.message ? err.message : String(err) });
    }
  });
} catch (err) {
  console.warn('Failed to register web-contents-created handler:', err && err.message ? err.message : err);
  logDropDebug('Failed to register web-contents-created handler', { error: err && err.message ? err.message : String(err) });
}

/* ------------------------
   App lifecycle
   ------------------------ */
  app.on('browser-window-created', (_event, win) => {
  try {
    logDropDebug('browser-window-created', { id: win.id });

    // Make every new window fully transparent by default so it never visibly
    // flashes on screen. We'll only make it opaque and show it later if we
    // determine that it is one of our internal HTML windows.
    try {
      if (typeof win.setOpacity === 'function') {
        win.setOpacity(0);
        logDropDebug('browser-window-created: set opacity 0', { id: win.id });
      }
    } catch (_) {}

    win.webContents.once('did-finish-load', () => {
      try {
        const url = win.webContents.getURL();
        logDropDebug('window did-finish-load', { id: win.id, url });

        const filePath = decodeFileUrlToPath(url);
        if (filePath && isInternalAppHtml(filePath)) {
          // Our own UI window: make it opaque and show it now that content is ready.
          logDropDebug('window did-finish-load: internal app HTML, showing window', {
            id: win.id,
            filePath
          });
          try {
            if (typeof win.setOpacity === 'function') win.setOpacity(1);
            win.show();
          } catch (_) {}
          return;
        }

        if (filePath && !isInternalAppHtml(filePath)) {
          // This window is displaying a raw file (e.g. from a Finder drop) rather than
          // one of our internal HTML entry points. Treat it as an external file drop,
          // route the path into the main window, and immediately close/minimize the raw window.
          logDropDebug('window did-finish-load: detected external file window, routing and closing', {
            id: win.id,
            filePath
          });

          // Shrink the external window to a tiny size in a corner so any brief display is
          // minimally visible.
          try {
            win.setBounds({ x: 0, y: 0, width: 1, height: 1 });
          } catch (_) {}

          try {
            sendExternalFileToRenderer(filePath, 'browser-window-created');
          } catch (err) {
            logDropDebug('browser-window-created handler: sendExternalFileToRenderer error', {
              id: win.id,
              error: err && err.message ? err.message : String(err)
            });
          }

          // Close the raw window shortly after load and refocus the main app window so any
          // popup remains effectively hidden behind the IDE.
          setTimeout(() => {
            try {
              logDropDebug('closing external file window', { id: win.id });
              win.close();
            } catch (_) {}
            try {
              if (mainWindow && !mainWindow.isDestroyed()) {
                logDropDebug('focusing mainWindow after external file window close', { mainWindowId: mainWindow.id });
                mainWindow.show();
                mainWindow.focus();
              }
            } catch (_) {}
          }, 10);
        }
      } catch (_) {}
    });

    win.on('page-title-updated', (_e, title) => {
      logDropDebug('page-title-updated', { id: win.id, title });
    });
  } catch (_) {}
});

// Track section visibility state for menu checkmarks
const sectionVisibility = {
  directoryViewer: true,
  editor: true,
  chat: true
};

// Build and set the application menu
function buildApplicationMenu() {
  const isMac = process.platform === 'darwin';
  
  const template = [
    // Auaci menu (macOS app menu)
    ...(isMac ? [{
      label: 'Auaci',
      submenu: [
        {
          label: 'Settings',
          accelerator: 'CmdOrCtrl+,',
          click: () => {
            createSettingsWindow();
          }
        },
        {
          label: 'Backup',
          click: () => {
            try {
              backupMain.openBackupWindow();
            } catch (err) {
              console.error('Failed to open backup window:', err);
            }
          }
        },
        { type: 'separator' },
        { role: 'quit', label: 'Quit Auaci' }
      ]
    }] : []),
    // Edit menu
    {
      label: 'Edit',
      submenu: [
        { role: 'undo' },
        { role: 'redo' },
        { type: 'separator' },
        { role: 'cut' },
        { role: 'copy' },
        { role: 'paste' },
        { role: 'pasteAndMatchStyle' },
        { role: 'delete' },
        { type: 'separator' },
        { role: 'selectAll' }
      ]
    },
    // View menu
    {
      label: 'View',
      submenu: [
        {
          label: 'Directory Viewer',
          type: 'checkbox',
          checked: sectionVisibility.directoryViewer,
          click: (menuItem) => {
            sectionVisibility.directoryViewer = menuItem.checked;
            broadcastSectionVisibility();
          }
        },
        {
          label: 'Editor',
          type: 'checkbox',
          checked: sectionVisibility.editor,
          click: (menuItem) => {
            sectionVisibility.editor = menuItem.checked;
            broadcastSectionVisibility();
          }
        },
        {
          label: 'Chat',
          type: 'checkbox',
          checked: sectionVisibility.chat,
          click: (menuItem) => {
            sectionVisibility.chat = menuItem.checked;
            broadcastSectionVisibility();
          }
        },
        { type: 'separator' },
        { role: 'resetZoom', label: 'Actual Size' },
        { role: 'zoomIn', label: 'Zoom In' },
        { role: 'zoomOut', label: 'Zoom Out' },
        { type: 'separator' },
        {
          label: 'Show Terminal',
          accelerator: 'CmdOrCtrl+`',
          click: (_item, browserWindow) => {
            try { browserWindow && browserWindow.webContents.send('show-terminal'); } catch (_) {}
          }
        },
        {
          label: 'Hide Terminal',
          accelerator: 'CmdOrCtrl+Shift+`',
          click: (_item, browserWindow) => {
            try { browserWindow && browserWindow.webContents.send('hide-terminal'); } catch (_) {}
          }
        },
        { type: 'separator' },
        { role: 'togglefullscreen', label: 'Toggle Full Screen' }
      ]
    },
    // Developer menu
    {
      label: 'Developer',
      submenu: [
        { role: 'reload', label: 'Reload' },
        { role: 'forceReload', label: 'Force Reload' },
        { role: 'toggleDevTools', label: 'Toggle Developer Tools' }
      ]
    }
  ];

  const menu = Menu.buildFromTemplate(template);
  Menu.setApplicationMenu(menu);
}

// Broadcast section visibility to all windows
function broadcastSectionVisibility() {
  BrowserWindow.getAllWindows().forEach(w => {
    try {
      w.webContents.send('section-visibility-changed', sectionVisibility);
    } catch (_) {}
  });
  // Rebuild menu to update checkmarks
  buildApplicationMenu();
}

// IPC handler for getting current section visibility
ipcMain.handle('get-section-visibility', () => {
  return sectionVisibility;
});

// IPC handler for setting section visibility from renderer
ipcMain.on('set-section-visibility', (event, newVisibility) => {
  if (newVisibility.hasOwnProperty('directoryViewer')) {
    sectionVisibility.directoryViewer = newVisibility.directoryViewer;
  }
  if (newVisibility.hasOwnProperty('editor')) {
    sectionVisibility.editor = newVisibility.editor;
  }
  if (newVisibility.hasOwnProperty('chat')) {
    sectionVisibility.chat = newVisibility.chat;
  }
  broadcastSectionVisibility();
});

app.whenReady().then(async () => {
  // Set the app name to Auaci
  app.name = 'Auaci';
  
  // Initialize config handler before creating windows so config is ready
  try {
    await configHandler.init(app);
  } catch (err) {
    console.error('Failed to initialize config handler:', err && err.message ? err.message : err);
  }

  // Build and set the custom application menu
  try {
    buildApplicationMenu();
  } catch (err) {
    console.warn('Failed to set application menu:', err && err.message ? err.message : err);
  }

  createWindow();

  // On macOS it's common to re-create a window in the app when the dock icon is clicked
  // and there are no other windows open.
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

// Keep this last (optional)
app.disableHardwareAcceleration();
