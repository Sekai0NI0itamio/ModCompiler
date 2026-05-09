// Main Process Background Processing Support
// Ensures Electron app continues processing when window is minimized or unfocused

const { app, powerSaveBlocker } = require('electron');

class MainProcessBackgroundManager {
  constructor() {
    this.powerSaveBlockerId = null;
    this.isBackgroundMode = false;
    this.init();
  }

  init() {
    // Prevent system from entering power save mode when app is active
    this.startPowerSaveBlocker();
    
    // Set up app event handlers
    this.setupAppEventHandlers();
    
    console.log('[MainProcessBackgroundManager] Initialized');
  }

  startPowerSaveBlocker() {
    try {
      // Prevent system from sleeping while app is running
      this.powerSaveBlockerId = powerSaveBlocker.start('prevent-app-suspension');
      console.log(`[MainProcessBackgroundManager] Power save blocker started: ${this.powerSaveBlockerId}`);
    } catch (err) {
      console.warn('[MainProcessBackgroundManager] Failed to start power save blocker:', err);
    }
  }

  stopPowerSaveBlocker() {
    if (this.powerSaveBlockerId !== null) {
      try {
        powerSaveBlocker.stop(this.powerSaveBlockerId);
        this.powerSaveBlockerId = null;
        console.log('[MainProcessBackgroundManager] Power save blocker stopped');
      } catch (err) {
        console.warn('[MainProcessBackgroundManager] Failed to stop power save blocker:', err);
      }
    }
  }

  setupAppEventHandlers() {
    // Handle app activation/deactivation
    app.on('activate', () => {
      this.handleAppActivation(true);
    });

    app.on('window-all-closed', () => {
      // Don't quit on macOS when all windows are closed
      if (process.platform !== 'darwin') {
        this.handleAppActivation(false);
      }
    });

    // Handle app hiding/showing (macOS specific)
    if (process.platform === 'darwin') {
      app.on('hide', () => {
        this.handleAppActivation(false);
      });

      app.on('show', () => {
        this.handleAppActivation(true);
      });
    }
  }

  handleAppActivation(isActive) {
    const wasBackgroundMode = this.isBackgroundMode;
    this.isBackgroundMode = !isActive;
    
    if (wasBackgroundMode !== this.isBackgroundMode) {
      console.log(`[MainProcessBackgroundManager] App ${isActive ? 'activated' : 'deactivated'}`);
      this.notifyBackgroundModeChange();
    }
  }

  notifyBackgroundModeChange() {
    // Notify all windows about background mode change
    const { BrowserWindow } = require('electron');
    const windows = BrowserWindow.getAllWindows();
    
    windows.forEach(win => {
      try {
        win.webContents.send('main-process-background-mode-changed', {
          isBackgroundMode: this.isBackgroundMode,
          timestamp: Date.now()
        });
      } catch (err) {
        console.debug('[MainProcessBackgroundManager] Failed to send IPC to window:', err.message);
      }
    });
  }

  // Force background mode for testing
  setBackgroundMode(enable) {
    this.isBackgroundMode = enable;
    this.notifyBackgroundModeChange();
  }

  // Get current background mode status
  getBackgroundMode() {
    return this.isBackgroundMode;
  }

  // Cleanup
  destroy() {
    this.stopPowerSaveBlocker();
    console.log('[MainProcessBackgroundManager] Destroyed');
  }
}

// Singleton instance
let mainProcessBackgroundManager = null;

function getMainProcessBackgroundManager() {
  if (!mainProcessBackgroundManager) {
    mainProcessBackgroundManager = new MainProcessBackgroundManager();
  }
  return mainProcessBackgroundManager;
}

// Register with main process
function registerMainProcessBackground(ipcMain) {
  if (!ipcMain) {
    console.warn('[MainProcessBackgroundManager] No ipcMain provided');
    return;
  }

  // Handle requests from renderer
  ipcMain.handle('get-main-process-background-mode', () => {
    const manager = getMainProcessBackgroundManager();
    return manager.getBackgroundMode();
  });

  ipcMain.handle('set-main-process-background-mode', (event, enable) => {
    const manager = getMainProcessBackgroundManager();
    manager.setBackgroundMode(enable);
    return { success: true };
  });

  console.log('[MainProcessBackgroundManager] Registered with main process');
}

module.exports = {
  MainProcessBackgroundManager,
  getMainProcessBackgroundManager,
  registerMainProcessBackground
};