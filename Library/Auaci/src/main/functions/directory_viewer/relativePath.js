// src/main/functions/directory_viewer/relativePath.js
// Helper utilities to compute a path relative to a project root and copy it (with notification).
// Exports:
//   - computeRelative(projectRoot, targetPath) => string
//   - copyRelativePath(projectRoot, targetPath, x, y) => uses copyPath(...) to copy + show notification

const path = require('path');
const { copyPath } = require('./buttons/copyPath');

/**
 * Compute a path for copying relative to projectRoot.
 * If projectRoot is falsy, fall back to process.cwd().
 * If the targetPath is the same as projectRoot, return '.'.
 * If the computed relative path would escape the projectRoot (starts with '..'), fallback to absolute targetPath.
 *
 * @param {string|null} projectRoot
 * @param {string} targetPath
 * @returns {string}
 */
function computeRelative(projectRoot, targetPath) {
  try {
    const absTarget = path.resolve(targetPath);

    if (projectRoot && typeof projectRoot === 'string') {
      const absProject = path.resolve(projectRoot);
      let rel = path.relative(absProject, absTarget);
      if (rel === '') return '.';
      // If not inside project root, fallback to absolute target
      if (rel.startsWith('..')) {
        return absTarget;
      }
      return rel;
    }

    // Fallback to cwd
    const cwd = process.cwd ? process.cwd() : '';
    try {
      const rel = path.relative(cwd, absTarget);
      return rel === '' ? '.' : rel;
    } catch (err) {
      return absTarget;
    }
  } catch (err) {
    console.error('computeRelative error:', err);
    return targetPath;
  }
}

/**
 * Copy the relative path (computed via computeRelative) to the clipboard and show notification at (x,y).
 * If copyPath fails, it will attempt a best-effort fallback to write to the clipboard directly.
 *
 * @param {string|null} projectRoot
 * @param {string} targetPath
 * @param {number} x
 * @param {number} y
 */
async function copyRelativePath(projectRoot, targetPath, x, y) {
  try {
    const rel = computeRelative(projectRoot, targetPath);
    // Reuse copyPath which writes to clipboard and shows notification
    await copyPath(rel, x, y);
  } catch (err) {
    console.error('copyRelativePath failed:', err);
    // fallback: try direct copy via clipboard if available
    try {
      const { clipboard } = require('electron');
      clipboard.writeText(targetPath);
      // try to give a minimal visual feedback if x/y present
      if (typeof x === 'number' && typeof y === 'number') {
        const notification = document.createElement('div');
        Object.assign(notification.style, {
          position: 'absolute',
          left: `${x}px`,
          top: `${y + 20}px`,
          background: '#fff',
          color: '#000',
          padding: '5px 10px',
          borderRadius: '4px',
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.15)',
          fontSize: '12px',
          fontFamily: 'Arial, sans-serif',
          zIndex: '1002'
        });
        notification.textContent = targetPath;
        document.body.appendChild(notification);
        setTimeout(() => {
          try { document.body.removeChild(notification); } catch (e) { /* ignore */ }
        }, 2500);
      }
    } catch (e) {
      console.error('fallback copy failed:', e);
    }
  }
}

module.exports = {
  computeRelative,
  copyRelativePath
};