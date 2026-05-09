// Minimal preload script for Electron
// This script runs in a privileged context and prepares the renderer environment

const { ipcRenderer } = require('electron');

// Expose ipcRenderer to the renderer process
window.ipcRenderer = ipcRenderer;

console.log('[preload] Electron preload script loaded');
