// src/main/functions/directory_viewer/state.js
const { saveFolderStates, loadFolderStates } = require('./memsave');
const fs = require('fs').promises;
const path = require('path');

let folderStates = new Map();

async function initFolderStates(projectRoot) {
  folderStates = await loadFolderStates(projectRoot);

  // Validate and clean up stale folder states
  const validStates = new Map();
  for (const [folderPath, state] of folderStates) {
    try {
      const stats = await fs.stat(folderPath);
      if (stats.isDirectory()) {
        validStates.set(folderPath, state);
      }
    } catch (err) {
      // Remove stale or inaccessible paths
      console.warn(`Removing stale folder state for ${folderPath}: ${err.message}`);
    }
  }
  folderStates = validStates;

  // Ensure project root is always open
  folderStates.set(projectRoot, 1);
  window.folderStates = folderStates; // Expose for smartRefresh
  await saveFolderStates(folderStates, projectRoot); // Save cleaned-up states
}

function getFolderState(path) {
  if (!folderStates.has(path)) {
    folderStates.set(path, path === window.projectRoot ? 1 : 0);
  }
  return folderStates.get(path);
}

async function setFolderState(path, state) {
  folderStates.set(path, state);
  await saveFolderStates(folderStates, window.projectRoot);
}

async function removeFolderState(path) {
  folderStates.delete(path);
  await saveFolderStates(folderStates, window.projectRoot);
}

module.exports = { initFolderStates, getFolderState, setFolderState, removeFolderState };