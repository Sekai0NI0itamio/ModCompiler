// src/main/functions/directory_viewer/smartRefresh.js
const fs = require('fs').promises;
const path = require('path');
const { getFolderState, removeFolderState } = require('./state');
const { renderDirectoryTree } = require('./render');
const { scanDirectoryTree, flattenTree } = require('./scan');
const clipboardMonitor = require('./clipboardMonitor');

/**
 * Verify that a path actually exists on disk
 */
async function pathExists(p) {
  try {
    await fs.access(p);
    return true;
  } catch {
    return false;
  }
}

async function smartRefresh(projectRoot) {
  const folderStates = window.folderStates || new Map();

  // Extract current rendered items (compute depth relative to projectRoot robustly)
  // Skip invisible root fillers so they don't get treated as removed items.
  const currentElements = Array.from(document.querySelectorAll('.tree-item:not(.invisible-root-filler)'));
  const currentItems = currentElements.map(el => {
    const p = el.dataset.path;
    const isDirectory = el.classList.contains('directory');

    // Compute depth based on path.relative to projectRoot so it's robust and not dependent on CSS.
    let depth = 0;
    try {
      let rel = path.relative(projectRoot, p) || '';
      // Normalize empty or '.' to empty
      if (rel === '.' || rel === '') {
        depth = 0;
      } else {
        const parts = rel.split(path.sep).filter(Boolean);
        depth = Math.max(0, parts.length - 1);
      }
    } catch (e) {
      // fallback to zero if anything weird
      depth = 0;
    }

    const state = isDirectory ? getFolderState(p) : undefined;
    return {
      path: p,
      isDirectory,
      depth,
      state
    };
  });

  // Verify all current items still exist on disk
  // This catches cases where a folder was deleted but items with same names exist elsewhere
  const existenceChecks = await Promise.all(
    currentItems.map(async (item) => ({
      ...item,
      exists: await pathExists(item.path)
    }))
  );

  // Find items that no longer exist
  const staleItems = existenceChecks.filter(item => !item.exists);

  // If we have stale items, do a full re-render to ensure accuracy
  if (staleItems.length > 0) {
    console.log(`[smartRefresh] Found ${staleItems.length} stale items, doing full re-render`);
    
    // Clean up folder states for removed directories
    for (const item of staleItems) {
      if (item.isDirectory && item.path !== projectRoot) {
        await removeFolderState(item.path);
      }
    }

    // Do a full re-scan and re-render
    const tree = await scanDirectoryTree(projectRoot, 0, true);
    renderDirectoryTree(tree);

    // Save updated folder states
    const { saveFolderStates } = require('./memsave');
    await saveFolderStates(folderStates, projectRoot);

    // Re-run clipboard poll
    try {
      if (clipboardMonitor && typeof clipboardMonitor.pollOnce === 'function') {
        await clipboardMonitor.pollOnce();
      }
    } catch (err) {
      console.error('smartRefresh: clipboardMonitor.pollOnce failed', err);
    }

    return;
  }

  // Scan project root as a tree
  const tree = await scanDirectoryTree(projectRoot, 0, true);
  const scannedItems = flattenTree(tree);

  // Filter scanned items to only include those whose parent folders are open
  const filteredScannedItems = scannedItems.filter(item => {
    if (item.depth === 0) return true;
    const parentPath = path.dirname(item.path);
    return getFolderState(parentPath) === 1;
  });

  // Compare scanned and current items to find added/removed
  const scannedPaths = new Set(filteredScannedItems.map(item => item.path));
  const currentPaths = new Set(currentItems.map(item => item.path));

  const addedItems = filteredScannedItems.filter(item => !currentPaths.has(item.path));
  const removedItems = currentItems.filter(item => !scannedPaths.has(item.path));

  // If there are significant changes (more than just a few items), do a full re-render
  // This ensures consistency when there are complex structural changes
  const totalChanges = addedItems.length + removedItems.length;
  if (totalChanges > 10 || (removedItems.length > 0 && addedItems.length > 0)) {
    console.log(`[smartRefresh] Significant changes detected (${totalChanges}), doing full re-render`);
    
    // Clean up folder states for removed directories
    for (const item of removedItems) {
      if (item.isDirectory && item.path !== projectRoot) {
        await removeFolderState(item.path);
      }
    }

    renderDirectoryTree(tree);

    // Save updated folder states
    const { saveFolderStates } = require('./memsave');
    await saveFolderStates(folderStates, projectRoot);

    // Re-run clipboard poll
    try {
      if (clipboardMonitor && typeof clipboardMonitor.pollOnce === 'function') {
        await clipboardMonitor.pollOnce();
      }
    } catch (err) {
      console.error('smartRefresh: clipboardMonitor.pollOnce failed', err);
    }

    return;
  }

  // Clean up folder states for removed directories
  for (const item of removedItems) {
    // Ignore any filler entries (they were not included by the querySelector above),
    // but as an extra guard ensure we never remove the project root from folder states here.
    if (item.isDirectory && item.path !== projectRoot) {
      await removeFolderState(item.path);
    }
  }

  // Update the render with minimal changes
  const { updateDirectoryTree } = require('./render');
  updateDirectoryTree(addedItems, removedItems);

  // Save updated folder states
  const { saveFolderStates } = require('./memsave');
  await saveFolderStates(folderStates, projectRoot);

  // Immediately re-run clipboard poll so highlights are applied/updated for the newly changed DOM.
  try {
    if (clipboardMonitor && typeof clipboardMonitor.pollOnce === 'function') {
      await clipboardMonitor.pollOnce();
    }
  } catch (err) {
    console.error('smartRefresh: clipboardMonitor.pollOnce failed', err);
  }
}

function startSmartRefresh(projectRoot) {
  if (window.smartRefreshInterval) {
    clearInterval(window.smartRefreshInterval);
  }
  smartRefresh(projectRoot);
  window.smartRefreshInterval = setInterval(() => smartRefresh(projectRoot), 1000);
}

module.exports = { smartRefresh, startSmartRefresh };