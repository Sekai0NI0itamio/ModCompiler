// src/main/functions/directory_viewer/render.js
const path = require('path');
const { setFolderState, getFolderState } = require('./state');
const { flattenTree } = require('./scan');

// consult clipboardMonitor to re-apply highlights for re-created elements
const clipboardMonitor = require('./clipboardMonitor');

function assetFileUrl(filename) {
  // Build a file:// URL pointing to src/main/assets/directory_viewer/<filename>
  // __dirname is src/main/functions/directory_viewer
  const assetPath = path.join(__dirname, '..', '..', 'assets', 'directory_viewer', filename);
  // Normalize for Windows paths
  const normalized = process.platform === 'win32' ? assetPath.replace(/\\/g, '/') : assetPath;
  return `file://${normalized}`;
}

/**
 * Create an invisible filler element that represents the root folder.
 * These fillers are appended at the very end of the container and:
 *  - have dataset.path = window.projectRoot (so they represent the root)
 *  - do NOT have a toggle chevron and cannot be opened
 *  - are visually hidden (CSS .invisible-root-filler) but still accept pointer events
 */
function createRootFiller(index) {
  const rootPath = window.projectRoot || '';
  const div = document.createElement('div');
  div.className = 'tree-item directory invisible-root-filler';
  // Keep root-level padding (depth 0)
  div.style.paddingLeft = `0px`;
  div.dataset.path = rootPath;
  // mark filler index for debugging if needed
  div.setAttribute('data-filler-index', String(index));
  // Do not make the filler draggable (not a source)
  div.draggable = false;
  // No toggle, no name span added (CSS hides any text if present)
  return div;
}

function ensureRootFillers(container) {
  if (!container) return;
  // Remove any existing fillers (so we always have exactly 3 at the end)
  Array.from(container.querySelectorAll('.invisible-root-filler')).forEach(el => el.remove());
  // Append exactly three fillers
  for (let i = 0; i < 3; i++) {
    container.appendChild(createRootFiller(i));
  }
}

/**
 * Normalize row widths so every .tree-item spans the full horizontal content width.
 * This ensures .tree-item.selected backgrounds cover the whole scrollable width,
 * even when some filenames are much wider than others.
 *
 * The function clears inline widths, lets layout settle, then measures the maximum
 * scrollWidth among the items and sets that width on every .tree-item.
 */
function normalizeRowWidths(container) {
  if (!container) return;
  const items = Array.from(container.querySelectorAll('.tree-item'));
  if (!items.length) return;

  // Clear previously-applied inline widths so we can measure natural widths.
  items.forEach(el => {
    el.style.width = '';
  });

  // Wait for the browser to lay out natural sizes, then measure and apply uniform width.
  window.requestAnimationFrame(() => {
    // Determine the maximum needed width among items (use their scrollWidth)
    const maxItemScroll = items.reduce((max, el) => Math.max(max, el.scrollWidth || 0), 0);
    // At minimum use the container's visible width
    const fullWidth = Math.max(container.clientWidth || 0, maxItemScroll || 0);

    // Apply the measured full width to every row (box-sizing ensured by CSS).
    items.forEach(el => {
      el.style.width = fullWidth + 'px';
      el.style.boxSizing = 'border-box';
    });
  });
}

function renderDirectoryTree(tree, modifiedPaths = []) {
  const container = document.getElementById('directory-viewer-content');
  if (!container) return;
  container.innerHTML = ''; // Clear existing content

  const orderedItems = flattenTree(tree);
  const frag = document.createDocumentFragment();

  orderedItems.forEach(item => {
    const div = document.createElement('div');
    div.className = `tree-item ${item.isDirectory ? 'directory' : 'file'}`;
    div.style.paddingLeft = `${item.depth * 20 + (item.isDirectory ? 0 : 20)}px`;
    div.dataset.path = item.path;
    if (item.finalPath) div.dataset.finalPath = item.finalPath;
    if (item.isCollapsed) div.dataset.collapsed = '1';
    div.draggable = true;

    // Re-apply clipboard highlight if this path is tracked by the clipboard monitor.
    try {
      if (clipboardMonitor && typeof clipboardMonitor.isPathHighlighted === 'function' && clipboardMonitor.isPathHighlighted(item.path)) {
        div.classList.add('clipboard-highlight');
      }
    } catch (err) {
      // ignore if clipboard monitor not available or errors
    }

    if (item.isDirectory) {
      const toggle = document.createElement('span');
      toggle.className = 'toggle';

      const img = document.createElement('img');
      img.alt = '';
      img.draggable = false;
      // Choose image based on folder state (1 = open)
      img.src = item.state === 1
        ? assetFileUrl('opened-arrow.png')
        : assetFileUrl('closed-arrow.png');

      toggle.appendChild(img);

      toggle.addEventListener('click', async (e) => {
        e.stopPropagation();
        const newState = item.state === 1 ? 0 : 1;
        const targetPath = item.finalPath || item.path;
        
        // For collapsed chains, mirror the state across all chain paths for consistency
        if (item.isCollapsed && item.chainPaths && item.chainPaths.length) {
          for (const chainPath of item.chainPaths) {
            await setFolderState(chainPath, newState);
          }
        }
        await setFolderState(targetPath, newState);
        
        // After toggling state we dispatch event to re-scan & re-render using the final target path
        document.dispatchEvent(new CustomEvent('toggle-folder', {
          detail: { path: targetPath, state: newState }
        }));
      });

      div.appendChild(toggle);
      // Insert a single non-breaking space between the toggle and the name to ensure exactly one space
      div.appendChild(document.createTextNode('\u00A0'));
    }

    const name = document.createElement('span');
    name.className = 'name';
    // Use collapsed display name if available, otherwise use regular name
    name.textContent = item.isCollapsed ? item.collapsedDisplayName : item.name;
    div.appendChild(name);

    div.addEventListener('click', (e) => {
      document.querySelectorAll('.tree-item.selected').forEach(el => el.classList.remove('selected'));
      div.classList.add('selected');

      // Single-click preview behavior: dispatch preview-file for files
      if (!item.isDirectory) {
        try {
          document.dispatchEvent(new CustomEvent('preview-file', {
            detail: { path: item.path }
          }));
        } catch (err) {
          console.error('Failed to dispatch preview-file event:', err);
        }
      }
    });

    frag.appendChild(div);
  });

  container.appendChild(frag);

  // Ensure three invisible root fillers are present at the end
  ensureRootFillers(container);

  // Immediately re-run the clipboard poll so highlights are freshly computed/applied
  // (this updates internal highlightedPaths and will apply to any items just rendered).
  try {
    if (clipboardMonitor && typeof clipboardMonitor.pollOnce === 'function') {
      // don't await to avoid blocking UI; run asynchronously
      clipboardMonitor.pollOnce().catch(err => {
        console.error('clipboardMonitor.pollOnce failed:', err);
      });
    }
  } catch (err) {
    // ignore
  }

  // Normalize row widths so selection backgrounds cover the full horizontal scroll width
  normalizeRowWidths(container);
}

function updateDirectoryTree(addedItemsTree, removedItems) {
  const container = document.getElementById('directory-viewer-content');
  if (!container) return;

  // Helper to find an element by dataset.path without using querySelector (safer)
  // Skip filler elements as they are special and should not be treated as "real" items here.
  function findElByDataPath(p) {
    const children = Array.from(container.children);
    return children.find(ch => ch.dataset && ch.dataset.path === p && !ch.classList.contains('invisible-root-filler')) || null;
  }

  // Remove items
  removedItems.forEach(item => {
    const element = findElByDataPath(item.path);
    if (element) element.remove();
  });

  // Flatten the added items tree (handles both nested and flat inputs)
  const filteredAddedItems = flattenTree(addedItemsTree).filter(item => {
    if (item.depth === 0) return true;
    return getFolderState(path.dirname(item.path)) === 1;
  });

  // Add new items in sorted order
  // When determining insertion points, ignore filler elements so new items go before fillers.
  const existingItems = Array.from(container.children).filter(ch => !ch.classList.contains('invisible-root-filler'));

  filteredAddedItems.forEach(item => {
    const div = document.createElement('div');
    div.className = `tree-item ${item.isDirectory ? 'directory' : 'file'}`;
    div.style.paddingLeft = `${item.depth * 20 + (item.isDirectory ? 0 : 20)}px`;
    div.dataset.path = item.path;
    if (item.finalPath) div.dataset.finalPath = item.finalPath;
    if (item.isCollapsed) div.dataset.collapsed = '1';
    div.draggable = true;

    // Re-apply clipboard highlight if this path is tracked by the clipboard monitor.
    try {
      if (clipboardMonitor && typeof clipboardMonitor.isPathHighlighted === 'function' && clipboardMonitor.isPathHighlighted(item.path)) {
        div.classList.add('clipboard-highlight');
      }
    } catch (err) {
      // ignore if clipboard monitor not available or errors
    }

    if (item.isDirectory) {
      const toggle = document.createElement('span');
      toggle.className = 'toggle';

      const img = document.createElement('img');
      img.alt = '';
      img.draggable = false;
      img.src = item.state === 1
        ? assetFileUrl('opened-arrow.png')
        : assetFileUrl('closed-arrow.png');

      toggle.appendChild(img);

      toggle.addEventListener('click', async (e) => {
        e.stopPropagation();
        const newState = item.state === 1 ? 0 : 1;
        const targetPath = item.finalPath || item.path;
        
        // For collapsed chains, mirror the state across all chain paths for consistency
        if (item.isCollapsed && item.chainPaths && item.chainPaths.length) {
          for (const chainPath of item.chainPaths) {
            await setFolderState(chainPath, newState);
          }
        }
        await setFolderState(targetPath, newState);
        
        document.dispatchEvent(new CustomEvent('toggle-folder', {
          detail: { path: targetPath, state: newState }
        }));
      });

      div.appendChild(toggle);
      // Insert a single non-breaking space between the toggle and the name to ensure exactly one space
      div.appendChild(document.createTextNode('\u00A0'));
    }

    const name = document.createElement('span');
    name.className = 'name';
    // Use collapsed display name if available, otherwise use regular name
    name.textContent = item.isCollapsed ? item.collapsedDisplayName : item.name;
    div.appendChild(name);

    div.addEventListener('click', (e) => {
      document.querySelectorAll('.tree-item.selected').forEach(el => el.classList.remove('selected'));
      div.classList.add('selected');

      // Single-click preview behavior: dispatch preview-file for files
      if (!item.isDirectory) {
        try {
          document.dispatchEvent(new CustomEvent('preview-file', {
            detail: { path: item.path }
          }));
        } catch (err) {
          console.error('Failed to dispatch preview-file event:', err);
        }
      }
    });

    // Find the correct insertion point by comparing against current DOM items.
    let insertBefore = null;
    for (const el of existingItems) {
      if (compareNewItemWithElement(item, el, container) < 0) {
        insertBefore = el;
        break;
      }
    }

    container.insertBefore(div, insertBefore);
  });

  // After the minimal DOM changes, ensure root fillers are present at the end
  ensureRootFillers(container);

  // After the minimal DOM changes, immediately re-run clipboard poll so highlights
  // are up-to-date and applied to any newly inserted elements.
  try {
    if (clipboardMonitor && typeof clipboardMonitor.pollOnce === 'function') {
      clipboardMonitor.pollOnce().catch(err => {
        console.error('clipboardMonitor.pollOnce failed:', err);
      });
    }
  } catch (err) {
    // ignore
  }

  // Normalize row widths after the incremental updates
  normalizeRowWidths(container);
}

function compareNewItemWithElement(item, el, container) {
  const aParts = item.path.split(path.sep);
  const bParts = el.dataset.path.split(path.sep);
  const minLen = Math.min(aParts.length, bParts.length);

  for (let i = 0; i < minLen; i++) {
    if (aParts[i] !== bParts[i]) {
      // Direct siblings (same parent and same depth)
      if (i === minLen - 1 && aParts.length === bParts.length) {
        const bIsDir = el.classList.contains('directory');
        // files-first ordering (like scan.js)
        if (!item.isDirectory && bIsDir) return -1;
        if (item.isDirectory && !bIsDir) return 1;
        return aParts[i].localeCompare(bParts[i]);
      }

      // Not direct siblings: attempt to consult DOM entries for the differing prefixes
      const prefixA = aParts.slice(0, i + 1).join(path.sep);
      const prefixB = bParts.slice(0, i + 1).join(path.sep);

      // Ignore filler elements when looking up prefix elements
      const children = Array.from(container.children).filter(ch => !ch.classList.contains('invisible-root-filler'));
      const prefixAEl = children.find(ch => ch.dataset.path === prefixA);
      const prefixBEl = children.find(ch => ch.dataset.path === prefixB);

      if (prefixAEl && prefixBEl) {
        const aIsDir = prefixAEl.classList.contains('directory');
        const bIsDir = prefixBEl.classList.contains('directory');
        if (!aIsDir && bIsDir) return -1;
        if (aIsDir && !bIsDir) return 1;
      }

      // Fallback: lexical comparison of the differing segment
      return aParts[i].localeCompare(bParts[i]);
    }
  }

  // One path is a prefix of the other (ancestor vs descendant) — ancestor should come first.
  return aParts.length - bParts.length;
}

module.exports = { renderDirectoryTree, updateDirectoryTree };