// txtfilelize.js
const fs = require('fs').promises;
const path = require('path');
const { revealInFinder } = require('./revealInFinder');
const { showFileSelector } = require('./helperfunctions/fileselect');

/**
 * createTxtfile(projectRoot, relativePath)
 *
 * - Collects files under the target folder (excluding .DS_Store, hidden files, images, and likely binary files)
 * - Shows a reusable file/folder selection UI (implemented in helperfunctions/fileselect.js)
 *   (fileselect now auto-hides deselected files and excludes node_modules from the folder list)
 * - On Create, writes projectRoot/.auaci/tmp/<chosen_filename>.txt with the assembled content
 * - Calls revealInFinder(outPath) after writing.
 */
async function createTxtfile(projectRoot, relativePath = '') {
  const baseFolder = path.join(projectRoot, relativePath || '');
  const displayBase = relativePath && relativePath !== '' ? relativePath.replace(/\\/g, '/') : '.';

  // --- helpers for file inspection / collection ---
  const IMAGE_EXTS = new Set([
    '.png', '.jpg', '.jpeg', '.gif', '.bmp', '.webp', '.svg', '.ico',
    '.tif', '.tiff', '.heic', '.heif', '.avif', '.psd', '.raw',
    '.nef', '.cr2', '.arw', '.dng'
  ]);

  async function readChunk(filePath, maxLen = 4096) {
    const fh = await fs.open(filePath, 'r');
    try {
      const st = await fh.stat();
      const toRead = Math.min(maxLen, st.size || maxLen);
      if (toRead <= 0) return Buffer.alloc(0);
      const buffer = Buffer.alloc(toRead);
      const { bytesRead } = await fh.read(buffer, 0, toRead, 0);
      return buffer.slice(0, bytesRead);
    } finally {
      try { await fh.close(); } catch (e) {}
    }
  }

  function isImageByExt(filePath) {
    const ext = path.extname(filePath).toLowerCase();
    return IMAGE_EXTS.has(ext);
  }

  function isImageByMagic(buf) {
    if (!buf || !buf.length) return false;
    // PNG
    if (buf.length >= 8 &&
      buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4E && buf[3] === 0x47 &&
      buf[4] === 0x0D && buf[5] === 0x0A && buf[6] === 0x1A && buf[7] === 0x0A) return true;
    // JPEG
    if (buf.length >= 3 && buf[0] === 0xFF && buf[1] === 0xD8 && buf[2] === 0xFF) return true;
    // GIF
    if (buf.length >= 6 && buf.toString('ascii', 0, 6).startsWith('GIF8')) return true;
    // BMP ("BM")
    if (buf.length >= 2 && buf[0] === 0x42 && buf[1] === 0x4D) return true;
    // WebP ("RIFF....WEBP")
    if (buf.length >= 12 && buf.toString('ascii', 0, 4) === 'RIFF' && buf.toString('ascii', 8, 12) === 'WEBP') return true;
    // ICO (00 00 01 00)
    if (buf.length >= 4 && buf[0] === 0x00 && buf[1] === 0x00 && buf[2] === 0x01 && buf[3] === 0x00) return true;
    return false;
  }

  function isLikelyBinary(buf) {
    if (!buf || !buf.length) return false;
    for (let i = 0; i < Math.min(buf.length, 512); i++) {
      if (buf[i] === 0) return true;
    }
    let nonText = 0;
    for (let i = 0; i < buf.length; i++) {
      const b = buf[i];
      if (b === 9 || b === 10 || b === 13) continue;
      if (b >= 32 && b <= 126) continue;
      if (b >= 128) continue;
      nonText++;
    }
    const ratio = nonText / buf.length;
    return ratio > 0.30;
  }

  // --- collect supported text files under baseFolder ---
  let files = []; // supported files: { fullPath, relPath, relNormalized, relProject }
  const excludedBinary = [];
  const excludedImage = [];
  const excludedUnreadable = [];

  async function collect(dir) {
    let list;
    try {
      list = await fs.readdir(dir, { withFileTypes: true });
    } catch (err) {
      // In the original UI this would set a status area; here we throw so caller can handle.
      throw new Error(`Unable to read folder: ${err.message || String(err)}`);
    }

    for (const entry of list) {
      const name = entry.name;
      if (name === '.DS_Store') continue;
      if (name.startsWith('.')) continue;

      const fp = path.join(dir, name);
      try {
        const isDir = (typeof entry.isDirectory === 'function') ? entry.isDirectory() : !!entry.isDirectory;
        const isFile = (typeof entry.isFile === 'function') ? entry.isFile() : !!entry.isFile;

        if (isDir) {
          try {
            const relDir = path.relative(baseFolder, fp).replace(/\\/g, '/');
            // skip node_modules (avoid deep scanning); fileselect also omits node_modules display
            if (relDir && relDir.split('/').includes('node_modules')) {
              continue;
            }
          } catch (e) {}
          await collect(fp);
          continue;
        }

        if (!isFile) {
          try {
            const s = await fs.stat(fp);
            if (s.isDirectory()) {
              try {
                const relDir = path.relative(baseFolder, fp).replace(/\\/g, '/');
                if (relDir && relDir.split('/').includes('node_modules')) {
                  continue;
                }
              } catch (e) {}
              await collect(fp);
              continue;
            } else if (!s.isFile()) {
              continue;
            }
          } catch (err) {
            excludedUnreadable.push(path.relative(baseFolder, fp).replace(/\\/g, '/') || name);
            continue;
          }
        }

        let buf;
        try {
          buf = await readChunk(fp, 8192);
        } catch (err) {
          excludedUnreadable.push(path.relative(baseFolder, fp).replace(/\\/g, '/') || name);
          continue;
        }

        const rel = path.relative(baseFolder, fp) || name;
        const relNormalized = rel.replace(/\\/g, '/');
        const relProject = path.relative(projectRoot, fp).replace(/\\/g, '/');

        if (isImageByExt(fp) || isImageByMagic(buf)) {
          excludedImage.push(relNormalized);
          continue;
        }

        if (isLikelyBinary(buf)) {
          excludedBinary.push(relNormalized);
          continue;
        }

        files.push({ fullPath: fp, relPath: rel, relNormalized, relProject });
      } catch (err) {
        // ignore individual file errors
      }
    }
  }

  try {
    await collect(baseFolder);
  } catch (err) {
    // bubble up error (can't render selector if folder unreadable)
    console.error(err);
    return;
  }

  files.sort((a, b) => a.relPath.localeCompare(b.relPath, undefined, { numeric: true }));

  // Show the reusable selector UI (helper handles persistence and UI interactions)
  let result;
  try {
    // Note: fileselect now auto-enables hide-deselected and omits node_modules by default,
    // so we don't need to (and shouldn't) override that behavior here.
    result = await showFileSelector({
      projectRoot,
      baseFolder,
      displayBase,
      files,
      excludedBinary,
      excludedImage,
      excludedUnreadable,
      defaultFilename: 'projectinfo.txt',
      // defaultHideDeselected omitted so fileselect's default (true) applies
      defaultUseRelative: true,
      defaultIncludeTree: false
    });
  } catch (err) {
    console.error('Failed to show file selector:', err);
    return;
  }

  if (!result || result.action !== 'create') {
    // user cancelled or closed the dialog
    return;
  }

  // bring over some of the original creation logic (revalidate, read and assemble)
  const { outFilename, includeTree, useRelative, selectedFullPaths, selectedRelPaths } = result;

  const outDir = path.join(projectRoot, '.auaci', 'tmp');
  try { await fs.mkdir(outDir, { recursive: true }); } catch (err) { /* ignore */ }
  const outPath = path.join(outDir, outFilename);

  let assembled = '';

  // Build ASCII tree if requested
  function buildAsciiTreeFromSelectedFiles(selectedRelPathsLocal, rootLabel) {
    const rootMap = new Map();

    for (const rel of selectedRelPathsLocal) {
      const parts = String(rel).split('/').filter(Boolean);
      let currentMap = rootMap;
      for (let i = 0; i < parts.length; i++) {
        const part = parts[i];
        const isLast = i === parts.length - 1;
        if (isLast) {
          if (!currentMap.has(part)) {
            currentMap.set(part, { name: part, isDir: false, children: [] });
          }
        } else {
          if (!currentMap.has(part)) {
            currentMap.set(part, { name: part, isDir: true, children: new Map() });
          }
          const node = currentMap.get(part);
          if (!node.children || !(node.children instanceof Map)) node.children = new Map();
          currentMap = node.children;
        }
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

    function renderAsciiTree(rootLabelLocal, childrenLocal) {
      const lines = [];
      const rl = (rootLabelLocal === '.' ? '.' : rootLabelLocal.replace(/\/$/, '')) + '/';
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

      walk(childrenLocal, '    ');
      return lines.join('\n');
    }

    return renderAsciiTree(rootLabel, childrenNodes);
  }

  if (includeTree) {
    try {
      const treeText = buildAsciiTreeFromSelectedFiles(selectedRelPaths, displayBase);
      assembled += `File structure tree (selected files only):\n${treeText}\n\n`;
    } catch (err) {
      console.error('Failed to build tree:', err);
    }
  }

  const skipped = [];
  for (const fp of selectedFullPaths) {
    let buf;
    try {
      buf = await readChunk(fp, 8192);
    } catch (err) {
      skipped.push({ fp, reason: 'unreadable' });
      continue;
    }
    if (isImageByExt(fp) || isImageByMagic(buf) || isLikelyBinary(buf)) {
      skipped.push({ fp, reason: 'binary_or_image' });
      continue;
    }
    try {
      const content = await fs.readFile(fp, 'utf8');
      const writtenPath = useRelative ? path.relative(projectRoot, fp).replace(/\\/g, '/') : fp;
      assembled += `File name: ${path.basename(fp)}\n`;
      assembled += `File path: ${writtenPath}\n`;
      assembled += `File Content:\n${content}\n\n`;
    } catch (err) {
      skipped.push({ fp, reason: 'unreadable_at_read' });
      continue;
    }
  }

  if (!assembled) {
    console.warn('No files could be included (all selected files were skipped).');
    return;
  }

  try {
    await fs.writeFile(outPath, assembled, 'utf8');
    console.log(`Created: ${outPath}`);
    if (skipped.length) {
      console.log(`${skipped.length} file(s) skipped during creation.`);
    }
    try {
      await revealInFinder(outPath);
    } catch (err) {
      console.error('Failed to reveal file in Finder:', err);
    }
  } catch (err) {
    console.error('Failed to write output file:', err);
  }
}

async function createTxtFromPaths(projectRoot, outFilename, filePaths = [], options = {}) {
  const outDir = path.join(projectRoot, '.auaci', 'tmp');
  try { await fs.mkdir(outDir, { recursive: true }); } catch (_) {}
  const outPath = path.join(outDir, outFilename);

  let assembled = '';
  let included = 0;
  for (const p of filePaths) {
    try {
      const content = await fs.readFile(p, 'utf8');
      assembled += `File name: ${path.basename(p)}\n`;
      const rel = path.relative(projectRoot, p).replace(/\\/g, '/');
      assembled += `File path: ${rel.startsWith('..') ? p : rel}\n`;
      assembled += `File Content:\n${content}\n\n`;
      included++;
    } catch (_) {
      // skip unreadable
    }
  }
  if (!assembled) throw new Error('No readable text files provided');
  await fs.writeFile(outPath, assembled, 'utf8');
  try { await revealInFinder(outPath); } catch (_) {}
  return { output_path: outPath, file_count: included };
}

module.exports = { createTxtfile, createTxtFromPaths };
