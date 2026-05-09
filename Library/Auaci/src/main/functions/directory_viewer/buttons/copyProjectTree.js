// copyProjectTree.js
const fs = require('fs').promises;
const path = require('path');
const { clipboard } = require('electron');
const { showFileSelector } = require('./helperfunctions/fileselect');

/**
 * copyProjectTree(projectRoot, relativePath)
 * - Uses showFileSelector(...) to present the file/folder selection UI
 * - Builds an ASCII tree from the selected files and shows a small preview modal
 *   with a Copy button (copies to clipboard).
 */
async function copyProjectTree(projectRoot, relativePath = '') {
  const baseFolder = path.join(projectRoot, relativePath || '');
  const displayBase = relativePath && relativePath !== '' ? relativePath.replace(/\\/g, '/') : '.';

  // --- scanning / data collection (same logic as original) ---
  let files = []; // { fullPath, relPath, relNormalized, name }
  const foldersSet = new Set();
  const excludedHidden = []; // .DS_Store and hidden
  const excludedUnreadable = []; // unreadable files/dirs encountered

  foldersSet.add('.');

  async function collect(dir) {
    let list;
    try {
      list = await fs.readdir(dir, { withFileTypes: true });
    } catch (err) {
      excludedUnreadable.push(path.relative(baseFolder, dir).replace(/\\/g, '/') || '.');
      return;
    }

    // keep stable order
    list.sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));

    for (const entry of list) {
      const name = entry.name;

      // skip hidden and .DS_Store
      if (name === '.DS_Store') {
        excludedHidden.push(path.relative(baseFolder, path.join(dir, name)).replace(/\\/g, '/'));
        continue;
      }
      if (name.startsWith('.')) {
        excludedHidden.push(path.relative(baseFolder, path.join(dir, name)).replace(/\\/g, '/'));
        continue;
      }

      const fp = path.join(dir, name);
      try {
        let isDir = (typeof entry.isDirectory === 'function') ? entry.isDirectory() : !!entry.isDirectory;
        if (isDir) {
          const rel = path.relative(baseFolder, fp) || '.';
          foldersSet.add(rel.replace(/\\/g, '/'));
          await collect(fp);
          continue;
        }

        // file
        const rel = path.relative(baseFolder, fp) || name;
        const relNormalized = rel.replace(/\\/g, '/');
        files.push({ fullPath: fp, relPath: rel, relNormalized, name });

        // add ancestor folders
        const parts = relNormalized.split('/');
        if (parts.length === 1) {
          foldersSet.add('.');
        } else {
          for (let i = 0; i < parts.length - 1; i++) {
            const p = parts.slice(0, i + 1).join('/') || '.';
            foldersSet.add(p === '' ? '.' : p);
          }
        }
      } catch (err) {
        excludedUnreadable.push(path.relative(baseFolder, fp).replace(/\\/g, '/'));
        continue;
      }
    }
  }

  try {
    await collect(baseFolder);
  } catch (err) {
    console.error('Error scanning folder:', err);
    try { window.alert(`Error scanning folder: ${err.message || String(err)}`); } catch (e) {}
    return;
  }

  files.sort((a, b) => a.relPath.localeCompare(b.relPath, undefined, { numeric: true }));
  const folders = Array.from(foldersSet).sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));

  // Prepare files array for the fileselect helper
  const filesForSelector = files.map(f => ({
    fullPath: f.fullPath,
    relPath: f.relPath,
    relNormalized: f.relNormalized,
    relProject: f.relNormalized // identifier used by fileselect for persistence
  }));

  // Use fileselect to let the user choose files/folders
  let selectorResult;
  try {
    // Use selection mode so the file-selection dialog doesn't show filename UI or give the impression
    // it's for creating a combined text file.
    selectorResult = await showFileSelector({
      projectRoot,
      baseFolder,
      displayBase,
      files: filesForSelector,
      excludedBinary: [],
      excludedImage: [],
      excludedUnreadable: [...excludedHidden, ...excludedUnreadable],
      defaultUseRelative: true,
      defaultIncludeTree: true,
      dialogTitle: 'Copy Project Tree — select files to include',
      mode: 'select',
      createButtonText: 'Preview selection'
    });
  } catch (err) {
    console.error('showFileSelector failed:', err);
    return;
  }

  if (!selectorResult || selectorResult.action !== 'create') {
    // user cancelled
    return;
  }

  // Determine selected files (prefer selectedRelPaths)
  const selectedRelPaths = (Array.isArray(selectorResult.selectedRelPaths) && selectorResult.selectedRelPaths.length)
    ? selectorResult.selectedRelPaths.map(s => String(s).replace(/\\/g, '/'))
    : (Array.isArray(selectorResult.selectedRelProjects) ? selectorResult.selectedRelProjects.map(s => String(s).replace(/\\/g, '/')) : []);

  // --- ascii tree builder from selected folders + files ---
  function renderAsciiTree(rootLabel, children) {
    const lines = [];
    const rl = (rootLabel === '.' ? '.' : rootLabel.replace(/\/$/, '')) + '/';
    lines.push(`└── ${rl}`);

    function walk(nodes, prefix) {
      for (let i = 0; i < nodes.length; i++) {
        const node = nodes[i];
        const isLast = (i === nodes.length - 1);
        const branch = isLast ? '└── ' : '├── ';
        lines.push(prefix + branch + (node.isDir ? node.name + '/' : node.name));
        if (node.isDir && node.children && node.children.length) {
          const newPrefix = prefix + (isLast ? '    ' : '│   ');
          walk(node.children, newPrefix);
        }
      }
    }

    walk(children, '    ');
    return lines.join('\n');
  }

  function buildAsciiTreeFromSelections(selectedFiles, selectedFolders, rootLabel) {
    // Build nested map of selected directories/files
    const rootMap = new Map();

    function ensureDirParts(parts) {
      let current = rootMap;
      for (let i = 0; i < parts.length; i++) {
        const part = parts[i];
        if (!current.has(part)) {
          current.set(part, { name: part, isDir: true, children: new Map() });
        }
        const node = current.get(part);
        current = node.children;
      }
      return current; // map for children of last directory
    }

    // Add selected folders (skip '.' here; root handled by render)
    for (const folder of selectedFolders) {
      if (!folder || folder === '.') continue;
      const parts = folder.split('/').filter(Boolean);
      ensureDirParts(parts);
    }

    // Add selected files
    for (const file of selectedFiles) {
      const parts = file.split('/').filter(Boolean);
      if (!parts.length) continue;
      const fileName = parts.pop();
      const parentMap = ensureDirParts(parts);
      // file node stored as Map entry with isDir=false and children=empty array sentinel
      if (!parentMap.has(fileName)) {
        parentMap.set(fileName, { name: fileName, isDir: false, children: [] });
      }
    }

    function mapToNodes(map) {
      const arr = [];
      for (const [key, val] of map.entries()) {
        if (val.isDir) {
          arr.push({ name: val.name, isDir: true, children: mapToNodes(val.children) });
        } else {
          arr.push({ name: val.name, isDir: false, children: [] });
        }
      }
      arr.sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
      return arr;
    }

    const childrenNodes = mapToNodes(rootMap);
    return renderAsciiTree(rootLabel, childrenNodes);
  }

  // Build tree text from the selected files
  const treeText = buildAsciiTreeFromSelections(selectedRelPaths, [], displayBase);

  // --- show a simple preview modal with the tree + copy/close ---
  const overlay = document.createElement('div');
  Object.assign(overlay.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    width: '100vw',
    height: '100vh',
    background: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: '1000',
    padding: '6vh 12px'
  });

  const card = document.createElement('div');
  Object.assign(card.style, {
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
    background: '#fff',
    padding: '12px',
    borderRadius: '8px',
    boxShadow: '0 8px 28px rgba(0,0,0,0.12)',
    width: 'min(96vw, 900px)',
    maxWidth: '900px',
    boxSizing: 'border-box',
    maxHeight: '80vh',
    overflow: 'hidden'
  });

  const title = document.createElement('div');
  title.textContent = 'Project Tree — preview';
  Object.assign(title.style, { fontSize: '15px', fontWeight: '600', color: '#111' });

  const textarea = document.createElement('textarea');
  textarea.readOnly = true;
  textarea.value = treeText;
  Object.assign(textarea.style, {
    padding: '10px',
    width: '100%',
    height: '60vh',
    minHeight: '200px',
    fontSize: '13px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    outline: 'none',
    resize: 'vertical',
    fontFamily: 'monospace',
    background: '#fff',
    boxSizing: 'border-box',
    whiteSpace: 'pre',
    overflow: 'auto'
  });

  const footerRow = document.createElement('div');
  Object.assign(footerRow.style, { display: 'flex', gap: '8px', alignItems: 'center' });

  const copyBtn = document.createElement('button');
  copyBtn.textContent = 'Copy to clipboard';
  Object.assign(copyBtn.style, {
    padding: '10px',
    borderRadius: '6px',
    border: '1px solid #0b66ff',
    background: 'linear-gradient(180deg, #0b66ff, #075fe6)',
    color: '#fff',
    cursor: 'pointer'
  });
  copyBtn.disabled = !(treeText && treeText.trim() !== '');

  const closeBtn = document.createElement('button');
  closeBtn.textContent = 'Close';
  Object.assign(closeBtn.style, {
    padding: '8px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    background: '#f7f9fb',
    cursor: 'pointer'
  });

  const statusDiv = document.createElement('div');
  Object.assign(statusDiv.style, { fontSize: '13px', color: '#333', marginLeft: '8px' });

  footerRow.appendChild(copyBtn);
  footerRow.appendChild(closeBtn);
  footerRow.appendChild(statusDiv);

  card.appendChild(title);
  card.appendChild(textarea);
  card.appendChild(footerRow);
  overlay.appendChild(card);
  document.body.appendChild(overlay);

  let copyTimer = null;
  copyBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const txt = textarea.value || '';
    try {
      clipboard.writeText(txt);
      statusDiv.textContent = 'Copied to clipboard';
      const prevText = copyBtn.textContent;
      copyBtn.textContent = 'Copied ✓';
      copyBtn.disabled = true;
      if (copyTimer) clearTimeout(copyTimer);
      copyTimer = setTimeout(() => {
        copyBtn.textContent = prevText;
        copyBtn.disabled = false;
        statusDiv.textContent = '';
      }, 1400);
    } catch (err) {
      console.error('Clipboard write failed:', err);
      statusDiv.textContent = 'Failed to copy to clipboard.';
    }
  });

  closeBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    try { if (document.body.contains(overlay)) document.body.removeChild(overlay); } catch (err) {}
  });

  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) {
      try { if (document.body.contains(overlay)) document.body.removeChild(overlay); } catch (err) {}
    }
  });
  card.addEventListener('click', (e) => e.stopPropagation());

  // Escape closes
  function onKey(e) {
    if (e.key === 'Escape') {
      try { if (document.body.contains(overlay)) document.body.removeChild(overlay); } catch (err) {}
      window.removeEventListener('keydown', onKey);
    }
  }
  window.addEventListener('keydown', onKey);
}

module.exports = { copyProjectTree };