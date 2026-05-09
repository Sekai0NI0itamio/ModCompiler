// src/main/functions/directory_viewer/collapseAll.js
const fs = require('fs').promises;
const path = require('path');
const { scanDirectoryTree } = require('../scan');
const { renderDirectoryTree } = require('../render');
const { setFolderState } = require('../state');
const { saveFolderStates, loadFolderStates } = require('../memsave');

async function collapseAll(projectRoot) {
  try {
    // Load current folder states
    let folderStates = await loadFolderStates(projectRoot);

    // Set all folder states to closed (0) except the project root
    const newStates = new Map();
    newStates.set(projectRoot, 1); // Keep project root open
    
    // Update all folder states to closed
    for (const [folderPath] of folderStates) {
      if (folderPath !== projectRoot) {
        await setFolderState(folderPath, 0); // Use setFolderState to update individual states
      }
    }

    // Update global folder states
    window.folderStates = newStates;
    await saveFolderStates(newStates, projectRoot);

    // Refresh the directory tree with updated states
    const tree = await scanDirectoryTree(projectRoot);
    document.getElementById('directory-viewer-content').innerHTML = '';
    renderDirectoryTree(tree);
    
    console.log('All directories collapsed successfully');
  } catch (err) {
    console.error('Failed to collapse all directories:', err);
    // Don't show alert, just log the error
  }
}

module.exports = { collapseAll };