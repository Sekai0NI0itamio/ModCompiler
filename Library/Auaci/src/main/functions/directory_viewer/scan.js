const fs = require('fs').promises;
const path = require('path');
const { getFolderState } = require('./state');

const DEFAULT_HIDDEN = ['.DS_Store'];

/**
 * Checks if a directory should be collapsed into a chain.
 * Returns the full collapsed path chain if collapsible, null otherwise.
 * A directory is collapsible if it contains only one subdirectory and no files.
 */
async function getCollapsibleChain(dirPath) {
  try {
    const hiddenSet = getHiddenSet();
    const entries = await fs.readdir(dirPath, { withFileTypes: true });
    
    // Filter out hidden files
    const visibleEntries = entries.filter(entry => !hiddenSet.has(entry.name));
    
    // Check if there's exactly one entry and it's a directory
    if (visibleEntries.length !== 1 || !visibleEntries[0].isDirectory()) {
      return null;
    }
    
    const singleChild = visibleEntries[0];
    const childPath = path.join(dirPath, singleChild.name);
    
    // Recursively check if the child can also be collapsed
    const childChain = await getCollapsibleChain(childPath);
    
    if (childChain) {
      // Child has a chain, extend it
      return {
        displayName: singleChild.name + '/' + childChain.displayName,
        finalPath: childChain.finalPath,
        chainPaths: [dirPath, ...childChain.chainPaths]
      };
    } else {
      // Child doesn't have a chain, but we can still collapse this level
      return {
        displayName: singleChild.name,
        finalPath: childPath,
        chainPaths: [dirPath]
      };
    }
  } catch (err) {
    return null;
  }
}

function getHiddenSet() {
  try {
    if (Array.isArray(window && window.hiddenFiles)) {
      return new Set(window.hiddenFiles.map(n => String(n)));
    }
    if (window && window.appConfig && Array.isArray(window.appConfig.hiddenFiles)) {
      return new Set(window.appConfig.hiddenFiles.map(n => String(n)));
    }
  } catch (e) {
    // ignore and fall back
  }
  return new Set(DEFAULT_HIDDEN);
}

async function scanDirectoryTree(dirPath, depth = 0, parentOpen = true) {
  if (!parentOpen) return []; // Skip if parent is closed

  let entries;
  try {
    entries = await fs.readdir(dirPath, { withFileTypes: true });
  } catch (err) {
    console.error(`Error scanning directory ${dirPath}:`, err);
    return [];
  }

  // Apply hidden-files filter (global app setting). This hides both files and folders by name.
  const hiddenSet = getHiddenSet();
  if (hiddenSet && hiddenSet.size > 0) {
    entries = entries.filter(entry => !hiddenSet.has(entry.name));
  }

  const items = [];
  for (const entry of entries) {
    const fullPath = path.join(dirPath, entry.name);
    const isDirectory = entry.isDirectory();

    // Determine collapse info first
    let collapsibleChain = null;
    if (isDirectory) {
      try { collapsibleChain = await getCollapsibleChain(fullPath); } catch (_) { collapsibleChain = null; }
    }

    // Effective state is always based on the final target when collapsed, otherwise the folder itself
    const effectiveState = isDirectory
      ? getFolderState(collapsibleChain ? collapsibleChain.finalPath : fullPath)
      : undefined;

    let item = {
      name: entry.name,
      path: fullPath,           // Keep original path for dataset stability
      isDirectory,
      depth,
      state: effectiveState
    };

    // If collapsible, attach metadata and the collapsed display name (label)
    if (isDirectory && collapsibleChain) {
      item.isCollapsed = true;
      item.collapsedDisplayName = entry.name + '/' + collapsibleChain.displayName;
      item.finalPath = collapsibleChain.finalPath;
      item.chainPaths = [fullPath, ...collapsibleChain.chainPaths];
    }

    if (isDirectory && effectiveState === 1) {
      // If expanded, scan children from the final path when collapsed, else the directory itself
      const pathToScan = item.isCollapsed ? item.finalPath : fullPath;
      item.children = await scanDirectoryTree(pathToScan, depth + 1, true);
    }

    items.push(item);
  }

  // Sort: files first, then folders, alphabetically
  items.sort((a, b) => {
    if (a.isDirectory && !b.isDirectory) return 1;
    if (!a.isDirectory && b.isDirectory) return -1;
    return a.name.localeCompare(b.name);
  });

  return items;
}

function flattenTree(tree) {
  const result = [];
  function traverse(items) {
    items.forEach(item => {
      // Create a new item without the children property to avoid recursive structures
      const { children, ...itemWithoutChildren } = item;
      result.push(itemWithoutChildren);
      if (item.children) {
        traverse(item.children);
      }
    });
  }
  traverse(tree);
  return result;
}

module.exports = { scanDirectoryTree, flattenTree, getCollapsibleChain };
