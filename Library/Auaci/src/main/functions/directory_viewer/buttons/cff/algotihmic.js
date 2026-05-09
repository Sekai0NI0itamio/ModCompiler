// src/main/functions/directory_viewer/buttons/cff/algotihmic.js
// Algorithmic parser + creator for the textual tree input.
// parseTree(treeText) => returns array of relative paths (folders and files).
// createFromPaths(basePath, relativePaths) => creates the directories/files under basePath.

const fs = require('fs').promises;
const path = require('path');

function cleanFileName(name) {
  const match = name.match(/^([a-zA-Z0-9._\-\s]*?)(?:[^a-zA-Z0-9._\-\s]|$)/);
  if (match) return match[1].trimEnd();
  return name.trim();
}

function getDepth(prefix) {
  const indentGroups = prefix.match(/(│\s{3}| {4})/g);
  return indentGroups ? indentGroups.length : 0;
}

/**
 * Parse a tree-like text into an array of relative paths.
 * Example accepted text:
 * myproject/
 * ├── src
 * │   └── index.js
 * └── README.md
 *
 * Returns:
 * [ 'myproject', 'myproject/src', 'myproject/src/index.js', 'myproject/README.md' ]
 *
 * If the first line is a standalone top-level folder line (no prefix and ends with '/'),
 * that name is used as a root prefix for returned relative paths.
 */
function parseTree(treeText) {
  if (!treeText || typeof treeText !== 'string') return [];

  const lines = treeText.split('\n').filter(l => l.trim());
  let currentPath = [];
  const relPaths = [];
  let basePrefix = '';

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // First-line top-level folder (e.g. "myproject/")
    if (i === 0 && line.match(/^[^\s├└│].*\/$/)) {
      basePrefix = line.replace(/\/$/, '').trim();
      continue;
    }

    const match = line.match(/^(\s*(?:[│ ]*?)[├└]──\s*)(.+)$/);
    if (!match) {
      // skip malformed lines
      continue;
    }

    const prefix = match[1];
    const nameAndComment = match[2];
    const cleanedName = cleanFileName(nameAndComment);
    if (!cleanedName) {
      continue;
    }

    const depth = getDepth(prefix);
    const isDir = cleanedName.endsWith('/') || !cleanedName.includes('.');
    const finalName = cleanedName.replace(/\/$/, '');

    if (depth === 0) {
      currentPath = [];
    } else {
      currentPath = currentPath.slice(0, depth);
    }
    currentPath.push(finalName);

    const rel = (basePrefix ? basePrefix + '/' : '') + currentPath.join('/');
    relPaths.push(rel);

    if (!isDir) {
      // pop file from currentPath so siblings are at correct level
      currentPath.pop();
    }
  }

  return relPaths;
}

/**
 * Create directories/files under basePath for the provided relative paths.
 * Files are created as empty files. Heuristic: path contains a dot (.) => file, otherwise folder.
 * Returns an array of successfully created full paths.
 */
async function createFromPaths(basePath, relativePaths = []) {
  if (!basePath || typeof basePath !== 'string') {
    throw new Error('basePath is required');
  }
  if (!Array.isArray(relativePaths)) relativePaths = [];

  const created = [];

  for (const rel of relativePaths) {
    if (!rel || typeof rel !== 'string') continue;
    const normalizedRel = rel.replace(/^\.\//, '').replace(/\\/g, '/');
    const fullPath = path.join(basePath, normalizedRel);

    // Determine if this should be a directory
    const lastSegment = path.basename(normalizedRel);
    const isDir = lastSegment.endsWith('/') || !lastSegment.includes('.');

    try {
      if (isDir) {
        await fs.mkdir(fullPath, { recursive: true });
        created.push(fullPath);
      } else {
        await fs.mkdir(path.dirname(fullPath), { recursive: true });
        await fs.writeFile(fullPath, '');
        created.push(fullPath);
      }
    } catch (err) {
      console.error(`Failed to create ${isDir ? 'folder' : 'file'} ${fullPath}:`, err);
      // keep going for other paths; surface errors by throwing aggregated or rely on caller to inspect created array
      throw new Error(`Failed to create ${fullPath}: ${err.message || err}`);
    }
  }

  return created;
}

module.exports = {
  parseTree,
  createFromPaths,
};