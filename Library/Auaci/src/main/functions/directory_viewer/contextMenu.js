const fs = require('fs').promises;
const path = require('path');
const { scanDirectoryTree } = require('./scan');
const { renderDirectoryTree } = require('./render');
const { revealInFinder } = require('./buttons/revealInFinder');
const { copyPath } = require('./buttons/copyPath');
const { createFile } = require('./buttons/createFile');
const { createFolder } = require('./buttons/createFolder');
const { createTxtfile } = require('./buttons/txtfilelize');
const { copyProjectTree } = require('./buttons/copyProjectTree'); // NEW
const { openTerminal } = require('./buttons/openTerminal'); // NEW
const { copyRelativePath } = require('./relativePath');
const { showAutoEdit } = require('./buttons/autoEdit'); // NEW
const { searchFile } = require('./buttons/searchFile'); // NEW
const { markdownRender } = require('./buttons/markdownRender'); // NEW
const { createPopupWithTolerance } = require('./popupUtils'); // NEW

// DELETE helper functions moved to a dedicated module to enable Trash behavior on macOS
const {
  moveToTrash,
  deleteFileToTrash,
  deleteFolderToTrash,
  emptyFolderContentsToTrash
} = require('./buttons/DeleteFunction');
const { showDeleteListWindow } = require('./buttons/deleteList');

/**
 * If a collapsed directory row (compressed path) is right-clicked, show an initial
 * disambiguation menu listing the progressive folders (e.g. folder1, folder1/folder2, ...).
 * Returns a Promise that resolves with the full selected absolute path, or null if cancelled.
 */
async function showFolderPickMenu(projectRoot, clickedElement, x, y) {
  try {
    if (!clickedElement || !clickedElement.dataset || clickedElement.dataset.collapsed !== '1') return null;
    const nameEl = clickedElement.querySelector('.name');
    const label = (nameEl && (nameEl.textContent || '').trim()) || '';
    if (!label || !label.includes('/')) return null; // not a compressed chain with multiple segments

    const baseFirstPath = clickedElement.dataset.path || '';
    const baseParent = path.dirname(baseFirstPath);

    const parts = label.split('/').filter(Boolean);
    if (parts.length < 2) return null;

    // Build progressive display labels and absolute paths
    const displayLabels = [];
    const absolutePaths = [];
    for (let i = 0; i < parts.length; i++) {
      const sub = parts.slice(0, i + 1).join('/');
      displayLabels.push(sub);
      absolutePaths.push(path.join(baseParent, ...parts.slice(0, i + 1)));
    }

    // Try to determine which part of the path the user clicked on based on mouse position
    const nameElRect = nameEl.getBoundingClientRect();
    const clickXRelative = x - nameElRect.left;
    
    // Calculate approximate character width and position of each segment
    const textContent = nameEl.textContent || '';
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    const computedStyle = window.getComputedStyle(nameEl);
    ctx.font = `${computedStyle.fontSize} ${computedStyle.fontFamily}`;
    
    let cumulativeX = 0;
    let detectedIndex = -1;
    
    for (let i = 0; i < parts.length; i++) {
      const segmentText = parts.slice(0, i + 1).join('/');
      const segmentWidth = ctx.measureText(segmentText).width;
      
      // Check if click is within this segment's range
      if (clickXRelative >= cumulativeX && clickXRelative <= segmentWidth) {
        // Check if we're not clicking on a "/" separator
        const prevSegmentText = i > 0 ? parts.slice(0, i).join('/') : '';
        const prevSegmentWidth = i > 0 ? ctx.measureText(prevSegmentText).width : 0;
        const separatorStart = prevSegmentWidth;
        const separatorEnd = prevSegmentWidth + ctx.measureText('/').width;
        
        // If click is on the separator, show the menu
        if (clickXRelative >= separatorStart && clickXRelative <= separatorEnd) {
          detectedIndex = -1;
          break;
        }
        
        detectedIndex = i;
        break;
      }
      cumulativeX = segmentWidth;
    }
    
    // If we detected a specific segment, return it directly without showing the menu
    if (detectedIndex >= 0) {
      return absolutePaths[detectedIndex];
    }

    // Create overlay and menu similar to the main context menu for consistent UX
    const overlay = document.createElement('div');
    Object.assign(overlay.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      right: '0',
      bottom: '0',
      width: '100%',
      height: '100%',
      background: 'transparent',
      zIndex: '2147483646',
      pointerEvents: 'auto'
    });

    const menu = document.createElement('div');
    Object.assign(menu.style, {
      position: 'fixed',
      left: `${x}px`,
      top: `${y}px`,
      background: '#fff',
      border: '1px solid #ccc',
      borderRadius: '4px',
      boxShadow: '0 6px 24px rgba(0, 0, 0, 0.30)',
      padding: '5px 0',
      zIndex: '2147483647',
      maxHeight: 'calc(100vh - 12px)',
      overflowY: 'auto',
      minWidth: '160px',
      boxSizing: 'border-box'
    });

    function positionMenu(menuEl, clickX, clickY) {
      const padding = 8;
      const vw = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
      const vh = Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0);
      const rect = menuEl.getBoundingClientRect();
      const mw = rect.width;
      const mh = rect.height;
      let left = clickX;
      let top = clickY;
      if (left + mw + padding > vw) left = Math.max(padding, vw - mw - padding);
      if (top + mh + padding > vh) {
        const above = clickY - mh;
        if (above >= padding) top = Math.max(padding, clickY - mh);
        else top = Math.max(padding, vh - mh - padding);
      }
      left = Math.max(padding, left);
      top = Math.max(padding, top);
      menuEl.style.left = `${left}px`;
      menuEl.style.top = `${top}px`;
    }

    return await new Promise((resolve) => {
      displayLabels.forEach((labelText, idx) => {
        const item = document.createElement('div');
        Object.assign(item.style, {
          padding: '8px 16px',
          cursor: 'pointer',
          color: '#000',
          fontSize: '14px',
          whiteSpace: 'nowrap'
        });
        item.textContent = labelText;
        item.addEventListener('click', (e) => {
          e.stopPropagation();
          const sel = absolutePaths[idx];
          try { document.body.removeChild(overlay); } catch (_) {}
          resolve(sel);
        });
        item.addEventListener('mouseover', () => item.style.background = '#f0f0f0');
        item.addEventListener('mouseout', () => item.style.background = '');
        menu.appendChild(item);
      });

      overlay.appendChild(menu);
      document.body.appendChild(overlay);
      requestAnimationFrame(() => {
        try { positionMenu(menu, x, y); } catch (_) {}
      });

      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) {
          try { document.body.removeChild(overlay); } catch (_) {}
          resolve(null);
        }
      });
    });
  } catch (err) {
    console.error('showFolderPickMenu error:', err);
    return null;
  }
}

/**
 * Show a custom HTML context menu. This implementation:
 *  - appends a full-viewport fixed overlay to document.body (so it's above the app content)
 *  - places the menu with position:fixed and a very large z-index
 *  - measures the menu after insertion and repositions it (flip/clamp) so it stays fully visible
 *
 * clickedElement (optional): the actual DOM element that was right-clicked.
 * If provided we'll highlight that element directly rather than doing a document.querySelector.
 */
function showContextMenu(projectRoot, targetPath, isDirectory, x, y, clickedElement) {
    const overlay = document.createElement('div');
    Object.assign(overlay.style, {
        position: 'fixed',
        top: '0',
        left: '0',
        right: '0',
        bottom: '0',
        width: '100%',
        height: '100%',
        background: 'transparent',
        zIndex: '2147483646', // very high so it's above app content
        pointerEvents: 'auto'
    });

    const menu = document.createElement('div');
    Object.assign(menu.style, {
        position: 'fixed', // fixed so coordinates are viewport-relative and won't be affected by parent transforms
        left: `${x}px`,
        top: `${y}px`,
        background: '#fff',
        border: '1px solid #ccc',
        borderRadius: '4px',
        boxShadow: '0 6px 24px rgba(0, 0, 0, 0.30)',
        padding: '5px 0',
        zIndex: '2147483647', // above overlay
        maxHeight: 'calc(100vh - 12px)', // leave a little breathing room
        overflowY: 'auto',
        minWidth: '140px',
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'row'
    });

    // Create vertical path display on the left
    const pathDisplay = document.createElement('div');
    Object.assign(pathDisplay.style, {
        background: '#f9f9f9',
        borderRight: '1px solid #e0e0e0',
        padding: '8px 6px',
        fontSize: '11px',
        color: '#666',
        fontFamily: 'monospace',
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-all',
        maxWidth: '120px',
        overflowWrap: 'break-word',
        lineHeight: '1.2',
        writingMode: 'vertical-rl',
        textOrientation: 'mixed',
        transform: 'rotate(180deg)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center'
    });
    
    // Get relative path for display
    let displayPath = targetPath;
    try {
        displayPath = path.relative(projectRoot, targetPath);
        if (displayPath === '') displayPath = '.';
    } catch (_) {
        displayPath = targetPath;
    }
    
    pathDisplay.textContent = displayPath;
    menu.appendChild(pathDisplay);

    // Create menu items container
    const menuItemsContainer = document.createElement('div');
    Object.assign(menuItemsContainer.style, {
        flex: '1',
        display: 'flex',
        flexDirection: 'column'
    });
    menu.appendChild(menuItemsContainer);

    // Helper to keep the menu inside the viewport (flip/clamp logic).
    function positionMenu(menuEl, clickX, clickY) {
      const padding = 8; // distance from viewport edges
      const vw = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
      const vh = Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0);

      // Read size after layout
      const rect = menuEl.getBoundingClientRect();
      const mw = rect.width;
      const mh = rect.height;

      let left = clickX;
      let top = clickY;

      // If menu would overflow to the right, clamp/shift left
      if (left + mw + padding > vw) {
        left = Math.max(padding, vw - mw - padding);
      }

      // If menu would overflow to the bottom, prefer to render above the click point if possible,
      // otherwise clamp to remain fully visible.
      if (top + mh + padding > vh) {
        const above = clickY - mh;
        if (above >= padding) {
          top = Math.max(padding, clickY - mh);
        } else {
          top = Math.max(padding, vh - mh - padding);
        }
      }

      // Ensure not negative
      left = Math.max(padding, left);
      top = Math.max(padding, top);

      menuEl.style.left = `${left}px`;
      menuEl.style.top = `${top}px`;
    }

    // Compute a safe relative path under projectRoot for createFile/createFolder wrappers.
    function computeSafeRelative(target) {
      let rel = '';
      try {
        rel = path.relative(projectRoot, target);
      } catch (err) {
        rel = '';
      }
      // If rel starts with '..' or is empty/invalid, use '' so create functions will use projectRoot.
      return (!rel || rel === '' || rel.startsWith('..')) ? '' : rel;
    }

    // Wrapper for creating a new file inside the directory viewer.
    // Computes a safe relative path under projectRoot and calls createFile(projectRoot, relativePath).
    function handleNewFile(projectRoot, targetPath) {
      try {
        const safeRel = computeSafeRelative(targetPath);
        createFile(projectRoot, safeRel);
      } catch (err) {
        console.error('handleNewFile failed:', err);
        // fallback: still try to open createFile with project root
        try { createFile(projectRoot, ''); } catch (e) { console.error('fallback createFile failed:', e); }
      }
    }

    // Wrapper for creating a new folder using the shared createFolder UI.
    function handleNewFolder(projectRoot, targetPath) {
      try {
        const safeRel = computeSafeRelative(targetPath);
        createFolder(projectRoot, safeRel);
      } catch (err) {
        console.error('handleNewFolder failed:', err);
        try { createFolder(projectRoot, ''); } catch (e) { console.error('fallback createFolder failed:', e); }
      }
    }

    // Wrapper for Txtfilelize (existing).
    function handleTxtfilelize(projectRoot, targetPath) {
      try {
        const safeRel = computeSafeRelative(targetPath);
        createTxtfile(projectRoot, safeRel);
      } catch (err) {
        console.error('handleTxtfilelize failed:', err);
        try { createTxtfile(projectRoot, ''); } catch (e) { console.error('fallback createTxtfile failed:', e); }
      }
    }

    // Wrapper for Copy Project Tree (NEW).
    function handleCopyProjectTree(projectRoot, targetPath) {
      try {
        const safeRel = computeSafeRelative(targetPath);
        copyProjectTree(projectRoot, safeRel);
      } catch (err) {
        console.error('handleCopyProjectTree failed:', err);
        try { copyProjectTree(projectRoot, ''); } catch (e) { console.error('fallback copyProjectTree failed:', e); }
      }
    }

    // Wrapper for Open Terminal (NEW).
    function handleOpenTerminal(projectRoot, targetPath) {
      try {
        const safeRel = computeSafeRelative(targetPath);
        openTerminal(projectRoot, safeRel);
      } catch (err) {
        console.error('handleOpenTerminal failed:', err);
        try { openTerminal(projectRoot, ''); } catch (e) { console.error('fallback openTerminal failed:', e); }
      }
    }

    // Wrapper for Search File (NEW).
    function handleSearchFile(projectRoot, targetPath) {
      try {
        const safeRel = computeSafeRelative(targetPath);
        searchFile(projectRoot, safeRel);
      } catch (err) {
        console.error('handleSearchFile failed:', err);
        try { searchFile(projectRoot, ''); } catch (e) { console.error('fallback searchFile failed:', e); }
      }
    }

    // Rename, Delete, Reveal, Copy actions.
    async function handleRename(projectRoot, targetPath, isDirectory) {
      const overlayEl = document.createElement('div');
      Object.assign(overlayEl.style, {
        position: 'fixed',
        top: '0',
        left: '0',
        width: '100vw',
        height: '100vh',
        background: 'rgba(0, 0, 0, 0.5)',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        zIndex: '1000'
      });

      const input = document.createElement('input');
      input.type = 'text';
      input.placeholder = 'Enter new name';
      input.value = path.basename(targetPath);
      Object.assign(input.style, {
        padding: '10px',
        width: '300px',
        fontSize: '16px',
        borderRadius: '5px',
        border: '1px solid #ccc',
        outline: 'none'
      });

      overlayEl.appendChild(input);
      document.body.appendChild(overlayEl);
      input.focus();
      
      // Auto-select filename (excluding extension) similar to VS Code behavior
      setTimeout(() => {
        const filename = path.basename(targetPath);
        if (!isDirectory && filename.includes('.')) {
          // For files, select everything except the extension
          const lastDotIndex = filename.lastIndexOf('.');
          if (lastDotIndex > 0) { // Don't select if file starts with dot (hidden files)
            input.setSelectionRange(0, lastDotIndex);
          } else {
            input.select(); // Select all if no proper extension found
          }
        } else {
          // For directories or files without extensions, select all
          input.select();
        }
      }, 10); // Small delay to ensure input is fully rendered

      // Set up tolerance zone for the rename popup
      const closeRenamePopup = () => {
        if (overlayEl.parentNode) document.body.removeChild(overlayEl);
      };
      
      const cleanupTolerance = createPopupWithTolerance(overlayEl, input, closeRenamePopup, 30);

      input.addEventListener('keydown', async (e) => {
        if (e.key === 'Enter') {
          const newName = input.value.trim();
          if (newName) {
            try {
              const newPath = path.join(path.dirname(targetPath), newName);
              
              // Use macOS terminal command for rename operation
              const { execSync } = require('child_process');
              
              // Escape paths for shell command - handle spaces and special characters
              const escapedOldPath = `'${targetPath.replace(/'/g, "'\\''")}'`;
              const escapedNewPath = `'${newPath.replace(/'/g, "'\\''")}'`;
              
              // Use mv command for macOS
              const command = `mv ${escapedOldPath} ${escapedNewPath}`;
              
              // Execute the terminal command
              execSync(command, { stdio: 'pipe' });
              
              // Trigger render update after successful rename
              const tree = await scanDirectoryTree(projectRoot);
              document.getElementById('directory-viewer-content').innerHTML = '';
              renderDirectoryTree(tree, [newPath]);
            } catch (err) {
              console.error('Failed to rename using terminal command:', err);
              alert(`Failed to rename item: ${err.message}`);
            }
          }
          if (cleanupTolerance) cleanupTolerance();
          closeRenamePopup();
        }
      });
    }

    /**
     * New delete flow uses the dedicated DeleteFunction module that attempts to move
     * items to the user's Trash folder on macOS (and Linux where appropriate).
     *
     * For directories: we still present the same UI choices ("Delete folder and all contents" or
     * "Delete everything inside (keep folder)") but both actions will try to move content to Trash
     * first. If moving to Trash fails, we fallback to the aggressive removal behavior.
     */
    async function handleDelete(projectRoot, targetPath, isDirectory) {
      const baseName = path.basename(targetPath);

      // Directories: show the existing overlay with two delete options, but call
      // into the new DeleteFunction module for the actual removal/move-to-trash operation.
      if (isDirectory) {
        const overlayEl = document.createElement('div');
        Object.assign(overlayEl.style, {
          position: 'fixed',
          top: '0',
          left: '0',
          width: '100vw',
          height: '100vh',
          background: 'rgba(0, 0, 0, 0.5)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: '1000'
        });

        const card = document.createElement('div');
        Object.assign(card.style, {
          background: '#fff',
          borderRadius: '8px',
          padding: '16px',
          minWidth: '320px',
          maxWidth: '90vw',
          boxShadow: '0 10px 30px rgba(0,0,0,0.25)'
        });

        const title = document.createElement('div');
        title.textContent = `Delete folder "${baseName}"`;
        Object.assign(title.style, { fontSize: '16px', fontWeight: '600', marginBottom: '8px' });

        const subtitle = document.createElement('div');
        subtitle.textContent = 'Choose what to delete:';
        Object.assign(subtitle.style, { fontSize: '13px', color: '#555', marginBottom: '12px' });

        const actions = document.createElement('div');
        Object.assign(actions.style, { display: 'flex', flexDirection: 'column', gap: '8px' });

        const close = () => {
          if (overlayEl.parentNode) document.body.removeChild(overlayEl);
        };

        // Option 1: move the folder (and contents) to Trash (preferred on macOS)
        const btnDeleteAll = document.createElement('button');
        btnDeleteAll.textContent = 'Delete folder and all contents (move to Trash when possible)';
        Object.assign(btnDeleteAll.style, {
          padding: '8px 10px',
          background: '#dc2626',
          color: '#fff',
          border: 'none',
          borderRadius: '6px',
          cursor: 'pointer',
          textAlign: 'left'
        });
        btnDeleteAll.addEventListener('click', async (e) => {
          e.stopPropagation();
          try {
            // Attempt move to Trash, fallback to removal if necessary
            await deleteFolderToTrash(targetPath);
            const tree = await scanDirectoryTree(projectRoot);
            document.getElementById('directory-viewer-content').innerHTML = '';
            // Focus parent directory after deletion
            renderDirectoryTree(tree, [path.dirname(targetPath)]);
          } catch (err) {
            console.error('Failed to delete folder:', err);
            alert('Failed to delete folder: ' + (err && err.message ? err.message : String(err)));
          }
          close();
        });

        // Option 2: move contents to Trash individually, keep the folder
        const btnEmpty = document.createElement('button');
        btnEmpty.textContent = 'Delete everything inside (move contents to Trash; keep folder)';
        Object.assign(btnEmpty.style, {
          padding: '8px 10px',
          background: '#f59e0b',
          color: '#111',
          border: 'none',
          borderRadius: '6px',
          cursor: 'pointer',
          textAlign: 'left'
        });
        btnEmpty.addEventListener('click', async (e) => {
          e.stopPropagation();
          try {
            const res = await emptyFolderContentsToTrash(targetPath);
            if (res && res.errors && res.errors.length) {
              console.warn('Some items could not be moved to Trash:', res.errors);
              alert(`Some items could not be removed. See console for details.`);
            }
            const tree = await scanDirectoryTree(projectRoot);
            document.getElementById('directory-viewer-content').innerHTML = '';
            renderDirectoryTree(tree, [targetPath]);
          } catch (err) {
            console.error('Failed to empty folder contents:', err);
            alert('Failed to empty folder contents: ' + (err && err.message ? err.message : String(err)));
          }
          close();
        });

        // Cancel
        const btnCancel = document.createElement('button');
        btnCancel.textContent = 'Cancel';
        Object.assign(btnCancel.style, {
          padding: '8px 10px',
          background: '#e5e7eb',
          color: '#111',
          border: 'none',
          borderRadius: '6px',
          cursor: 'pointer',
          textAlign: 'left'
        });
        btnCancel.addEventListener('click', (e) => {
          e.stopPropagation();
          close();
        });

        actions.appendChild(btnDeleteAll);
        actions.appendChild(btnEmpty);
        actions.appendChild(btnCancel);

        card.appendChild(title);
        card.appendChild(subtitle);
        card.appendChild(actions);

        overlayEl.appendChild(card);
        document.body.appendChild(overlayEl);

        // Use the existing tolerance helper so accidental near-clicks don't close the dialog
        try { createPopupWithTolerance(overlayEl, card, close, 30); } catch (_) {
          overlayEl.addEventListener('click', (e) => { if (e.target === overlayEl) close(); });
        }

        try { btnDeleteAll.focus(); } catch (_) {}
        return;
      }

      // Files: show confirmation and attempt move to Trash (macOS) or fallback removal.
      if (confirm(`Are you sure you want to delete ${baseName}?`)) {
        try {
          await deleteFileToTrash(targetPath);
          const tree = await scanDirectoryTree(projectRoot);
          document.getElementById('directory-viewer-content').innerHTML = '';
          renderDirectoryTree(tree);
        } catch (err) {
          console.error('Failed to delete:', err);
          alert('Failed to delete item: ' + (err && err.message ? err.message : String(err)));
        }
      }
    }

    // DFPF: Delete Folder Preserve Files
    async function handleDFPF(projectRoot, folderPath) {
      try {
        // Ensure target is a directory
        const stat = await fs.lstat(folderPath);
        if (!stat.isDirectory()) {
          alert('DFPF is only available for folders.');
          return;
        }

        const folderName = path.basename(folderPath);
        const parentDir = path.dirname(folderPath);

        const msg = `Are you sure you want to delete the folder ${folderName} but have the files and all contents inside be moved out of the folder into the folder the old folder was in?`;
        const proceed = confirm(msg);
        if (!proceed) return;

        // Read items inside the folder
        const entries = await fs.readdir(folderPath);

        // Detect name conflicts in parent
        const conflicts = [];
        for (const name of entries) {
          const destPath = path.join(parentDir, name);
          try {
            await fs.lstat(destPath);
            conflicts.push(name);
          } catch (_) {
            // dest doesn't exist -> ok
          }
        }

        if (conflicts.length > 0) {
          const warn = `The following items already exist in the destination and will be replaced if you continue:\n\n${conflicts.map(n => '- ' + n).join('\n')}\n\nProceed and replace them?`;
          const replaceAll = confirm(warn);
          if (!replaceAll) return; // Abort entire operation
        }

        // Move each entry up one level. If a destination exists, remove it first (replace semantics).
        for (const name of entries) {
          const src = path.join(folderPath, name);
          const dest = path.join(parentDir, name);
          try {
            // Remove destination if it exists so rename won't fail on directories
            await fs.rm(dest, { recursive: true, force: true }).catch(() => {});
          } catch (_) {}
          await fs.rename(src, dest);
        }

        // Remove the now-empty folder (force in case any hidden files were missed)
        await fs.rm(folderPath, { recursive: true, force: true });

        // Refresh tree and focus parent
        const tree = await scanDirectoryTree(projectRoot);
        document.getElementById('directory-viewer-content').innerHTML = '';
        renderDirectoryTree(tree, [parentDir]);
      } catch (err) {
        console.error('DFPF failed:', err);
        alert(`Failed to delete folder while preserving files: ${err && err.message ? err.message : err}`);
      }
    }

    // Open File Info window (details + live size)
    function handleFileInfo(targetPath) {
      try {
        const { ipcRenderer } = require('electron');
        ipcRenderer.send('file-info-open-window', targetPath);
      } catch (err) {
        console.error('Failed to open File Info window:', err);
      }
    }

    const allOptions = [
        { text: 'Auto Edit', enabled: isDirectory, action: () => {
            const safeRel = computeSafeRelative(targetPath);
            try {
              showAutoEdit(projectRoot, safeRel);
            } catch (err) {
              console.error('Failed to open Auto Edit:', err);
              // fallback: open with project root
              try { showAutoEdit(projectRoot, ''); } catch (e) { console.error('fallback showAutoEdit failed:', e); }
            }
          } },
        { text: 'Copy Path', enabled: true, action: () => copyPath(targetPath, x, y) },
        { text: 'Copy Project Tree', enabled: isDirectory, action: () => handleCopyProjectTree(projectRoot, targetPath) },
        { text: 'Copy Relative Path', enabled: true, action: () => copyRelativePath(projectRoot, targetPath, x, y) },
        { text: 'Delete', enabled: true, action: () => handleDelete(projectRoot, targetPath, isDirectory) },
        { text: 'Delete List', enabled: isDirectory, action: () => showDeleteListWindow(targetPath) },
        { text: 'DFPF', enabled: isDirectory, action: () => handleDFPF(projectRoot, targetPath) },
        { text: 'Get Info', enabled: true, action: () => handleFileInfo(targetPath) },
        { text: 'Markdown render', enabled: !isDirectory, action: () => markdownRender(projectRoot, targetPath) },
        { text: 'New File', enabled: isDirectory, action: () => handleNewFile(projectRoot, targetPath) },
        { text: 'New Folder', enabled: isDirectory, action: () => handleNewFolder(projectRoot, targetPath) },
        { text: 'Open Terminal', enabled: true, action: () => handleOpenTerminal(projectRoot, targetPath) },
        { text: 'Rename', enabled: true, action: () => handleRename(projectRoot, targetPath, isDirectory) },
        { text: 'Reveal in Finder', enabled: true, action: () => revealInFinder(targetPath) },
        { text: 'Search File', enabled: isDirectory, action: () => handleSearchFile(projectRoot, targetPath) },
        { text: 'Txtfilelize', enabled: isDirectory, action: () => handleTxtfilelize(projectRoot, targetPath) }
    ];

    // Separate enabled and disabled options, then sort each group alphabetically
    const enabledOptions = allOptions.filter(opt => opt.enabled).sort((a, b) => a.text.localeCompare(b.text));
    const disabledOptions = allOptions.filter(opt => !opt.enabled).sort((a, b) => a.text.localeCompare(b.text));
    
    // Combine: enabled first (alphabetically), then disabled (alphabetically)
    const options = [...enabledOptions, ...disabledOptions];

    // Highlight the right-clicked file or directory
    // If clickedElement is provided, prefer that (it may be one of the invisible fillers).
    const targetElement = clickedElement || Array.from(document.querySelectorAll('.tree-item')).find(el => el.dataset && el.dataset.path === targetPath);
    let clearNameHighlight = () => {};
    if (targetElement) {
        targetElement.classList.add('context-highlighted');
        // Apply name highlight for both directories and files
        clearNameHighlight = applyFolderNameHighlight(targetElement, targetPath, isDirectory);
    }

    function cleanupHighlight() {
        if (targetElement) {
            targetElement.classList.remove('context-highlighted');
        }
        try {
            clearNameHighlight();
        } catch (_) {
            // ignore
        }
    }

    options.forEach(option => {
        const item = document.createElement('div');
        Object.assign(item.style, {
            padding: '8px 16px',
            cursor: option.enabled ? 'pointer' : 'not-allowed',
            color: option.enabled ? '#000' : '#999',
            fontSize: '14px',
            whiteSpace: 'nowrap'
        });
        item.textContent = option.text;
        if (option.enabled) {
            item.addEventListener('click', (e) => {
                e.stopPropagation();
                try {
                  option.action();
                } catch (err) {
                  console.error('Context menu action failed:', err);
                }
                if (overlay.parentNode) document.body.removeChild(overlay);
                // Remove highlight when menu closes
                cleanupHighlight();
            });
            item.addEventListener('mouseover', () => item.style.background = '#f0f0f0');
            item.addEventListener('mouseout', () => item.style.background = '');
        }
        menuItemsContainer.appendChild(item);
    });

    overlay.appendChild(menu);
    document.body.appendChild(overlay);

    // position the menu after it has been inserted and laid out.
    // use requestAnimationFrame to ensure layout is ready for measurement.
    requestAnimationFrame(() => {
      try {
        positionMenu(menu, x, y);
      } catch (err) {
        // if measurement fails for any reason, leave default position
        console.error('Failed to position context menu:', err);
      }
    });

    // Clicking outside the menu closes it.
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) {
            if (overlay.parentNode) document.body.removeChild(overlay);
            // Remove highlight when menu closes
            cleanupHighlight();
        }
    });

    // If the window resizes while menu is open, reposition so it stays visible
    const onResize = () => {
      try {
        // Try to use the last requested x/y (click coords). If they are outside viewport, clamp.
        const lastX = Math.min(Math.max(0, x), window.innerWidth || document.documentElement.clientWidth);
        const lastY = Math.min(Math.max(0, y), window.innerHeight || document.documentElement.clientHeight);
        positionMenu(menu, lastX, lastY);
      } catch (err) {
        // ignore
      }
    };
    window.addEventListener('resize', onResize);

    // Clean up resize listener when overlay is removed (hook into overlay click removal).
    const observer = new MutationObserver(() => {
      if (!document.body.contains(overlay)) {
        window.removeEventListener('resize', onResize);
        observer.disconnect();
      }
    });
    observer.observe(document.body, { childList: true });
}

function escapeHtml(text) {
  if (text == null) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Temporarily highlight the folder name inside the directory viewer when the
 * context menu is open.
 *
 * For collapsed chains like "folder1/folder2/folder3", only the last segment
 * of the SELECTED folder is highlighted. For example:
 * - If user clicked on "src" in "src/main/functions", highlight "src"
 * - If user clicked on "main" in "src/main/functions", highlight "main"
 * - If user clicked on "functions" in "src/main/functions", highlight "functions"
 *
 * Returns a cleanup function that restores the original label.
 */
function applyFolderNameHighlight(targetElement, targetPath, isDirectory) {
  if (!targetElement) return () => {};

  const nameEl = targetElement.querySelector('.name');
  if (!nameEl) return () => {};

  const originalHtml = nameEl.innerHTML;

  try {
    const isCollapsed = targetElement.dataset && targetElement.dataset.collapsed === '1';
    const labelText = (nameEl.textContent || '').trim();
    if (!labelText) return () => {};

    const labelParts = labelText.split('/');

    if (!isCollapsed || labelParts.length === 1) {
      // Non-collapsed folder or a file: colour the entire name.
      nameEl.innerHTML = `<span class="context-name-highlight">${escapeHtml(labelText)}</span>`;
    } else {
      // Collapsed chain: determine which segment of the display corresponds to the selected targetPath
      const baseFirstPath = targetElement.dataset.path || '';
      const baseParent = path.dirname(baseFirstPath);

      // Calculate which segment of the collapsed path the targetPath represents
      let selectedSegmentIndex = -1;
      try {
        const rel = path.relative(baseParent, targetPath || '');
        if (rel && rel !== '.') {
          const relParts = rel.split(path.sep);
          // Find how many parts of the collapsed label match the selected path
          selectedSegmentIndex = Math.min(relParts.length - 1, labelParts.length - 1);
        }
      } catch (_) {
        selectedSegmentIndex = labelParts.length - 1; // fallback to last
      }

      if (selectedSegmentIndex < 0) selectedSegmentIndex = 0;

      // Build HTML: highlight only the last segment of the selected folder
      let html = '';
      for (let i = 0; i < labelParts.length; i++) {
        if (i > 0) html += '/';
        
        if (i === selectedSegmentIndex) {
          // This is the selected segment - highlight only its last part
          html += `<span class="context-name-highlight">${escapeHtml(labelParts[i])}</span>`;
        } else {
          html += escapeHtml(labelParts[i]);
        }
      }

      nameEl.innerHTML = html;
    }
  } catch (err) {
    console.error('applyFolderNameHighlight failed:', err);
    // On failure, restore original HTML immediately
    nameEl.innerHTML = originalHtml;
    return () => {};
  }

  // Cleanup restores the original label.
  return () => {
    try {
      nameEl.innerHTML = originalHtml;
    } catch (_) {
      // ignore
    }
  };
}

module.exports = { showContextMenu, showFolderPickMenu };