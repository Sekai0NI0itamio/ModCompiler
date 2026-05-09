// src/main/functions/backup/main.js
// Main-process code for the Backup popup: create window, cache handling, scan, create and restore backups.

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const archiver = require('archiver');
const extract = require('extract-zip');

let backupWindow = null;

let restoreState = {
  state: 'idle',
  message: '',
  startedAt: 0,
  finishedAt: 0,
  error: null,
  backupFullPath: null
};

function sendRestoreState() {
  try {
    if (backupWindow && !backupWindow.isDestroyed()) {
      backupWindow.webContents.send('backup-restore-state', Object.assign({}, restoreState));
    }
  } catch (err) {
    console.warn('Failed to send restore state:', err && err.message ? err.message : err);
  }
}

function setRestoreState(next) {
  const now = Date.now();
  restoreState = Object.assign({}, restoreState, next || {});
  if (restoreState.state === 'running' && !restoreState.startedAt) {
    restoreState.startedAt = now;
    restoreState.finishedAt = 0;
  }
  if ((restoreState.state === 'done' || restoreState.state === 'error') && !restoreState.finishedAt) {
    restoreState.finishedAt = now;
  }
  sendRestoreState();
}

// Active scanning processes that can be cancelled
let activeScanProcesses = new Map();

// Unique token generator for scan processes
let scanTokenCounter = 0;

function generateScanToken() {
  return ++scanTokenCounter;
}

/**
 * Default cache. These defaults are used when no config exists.
 * The `list` here includes common folders that are usually excluded from backups by default.
 */
function defaultCache() {
  return {
    version: 1,
    mode: 'blacklist', // 'blacklist' or 'whitelist'
    list: [
      '.auaci',
      '.venv',
      'venv',
      'node_modules',
      '.git',
      'dist',
      'build',
      'backup',        // ensure default "backup" folder in workspace is excluded by default
      '__pycache__'    // blacklist Python bytecode cache folders by default
    ],
    selections: {}, // relPath -> boolean
    backupFolder: '../backup', // default outside workspace
    preserveOnRestore: ['.auaci', '.venv', 'venv', 'node_modules', 'backup'],
    // Paths under which blacklist/whitelist rules should be ignored when
    // scanning and building the tree. Used when the user explicitly chooses
    // to "enable" a previously skipped folder and load its contents.
    ignoreListPrefixes: []
  };
}

function configFilePath(projectRoot) {
  return path.join(projectRoot, '.auaci', 'backup', 'config.json');
}

async function readCache(projectRoot) {
  const cfgPath = configFilePath(projectRoot);
  const base = defaultCache();
  try {
    const txt = await fsp.readFile(cfgPath, 'utf8');
    const obj = JSON.parse(txt || '{}');

    // If file exists but has no list/preserve fields, keep defaults for those arrays.
    if (obj.list === undefined) obj.list = base.list.slice();
    if (obj.preserveOnRestore === undefined) obj.preserveOnRestore = base.preserveOnRestore.slice();
    if (!Array.isArray(obj.ignoreListPrefixes)) obj.ignoreListPrefixes = [];

    // Merge shallowly
    const merged = Object.assign({}, base, obj);

    // Ensure key defaults are present in the runtime list so UI shows them by default.
    // (We don't auto-write the file here; let the renderer persist if desired.)
    if (!Array.isArray(merged.list)) merged.list = base.list.slice();
    for (const must of ['__pycache__','backup']) {
      if (!merged.list.includes(must)) merged.list.push(must);
    }
    if (!Array.isArray(merged.preserveOnRestore)) merged.preserveOnRestore = base.preserveOnRestore.slice();
    if (!merged.preserveOnRestore.includes('backup')) merged.preserveOnRestore.push('backup');
    if (!Array.isArray(merged.ignoreListPrefixes)) merged.ignoreListPrefixes = [];

    return merged;
  } catch (err) {
    // if file missing or parse error -> return defaults
    return base;
  }
}

// Normalise relative paths to a forward-slash format without trailing slashes
function normalizeRelPath(p) {
  if (!p) return '';
  return String(p).replace(/\\/g, '/').replace(/\/+$/, '');
}

function pathIsUnder(nodeRel, listEntry) {
  if (!listEntry) return false;
  const n = normalizeRelPath(nodeRel);
  const e = normalizeRelPath(listEntry);
  if (!e) return false;
  if (n === e) return true;
  return n.startsWith(e + '/');
}

function isMatchedByList(nodeRel, listArray) {
  if (!listArray || !listArray.length) return false;
  for (const entry of listArray) {
    if (pathIsUnder(nodeRel, entry)) return true;
  }
  return false;
}

// Return true if any list entry is inside the given directory (used for
// whitelist mode so we only scan folders that might contain whitelisted
// descendants).
function hasListDescendant(dirRel, listArray) {
  const dir = normalizeRelPath(dirRel);
  if (!dir) return false;
  for (const entry of listArray || []) {
    const e = normalizeRelPath(entry);
    if (!e) continue;
    if (e === dir) return true;
    if (e.startsWith(dir + '/')) return true;
  }
  return false;
}

async function writeCache(projectRoot, config) {
  const p = configFilePath(projectRoot);
  await fsp.mkdir(path.dirname(p), { recursive: true });
  await fsp.writeFile(p, JSON.stringify(config, null, 2), 'utf8');
  return config;
}

/* Scan the project directory and produce a nested tree:
   { rootName, tree: [ { name, relPath, type: 'dir'|'file', children? } ] }
*/
async function scanProjectTree(projectRoot) {
  // Load backup cache so we can respect blacklist/whitelist rules while
  // scanning. This lets us avoid descending into folders that are excluded
  // by the current configuration, which keeps large folders like
  // node_modules or .git from being fully scanned.
  let cache;
  try {
    cache = await readCache(projectRoot);
  } catch (err) {
    cache = defaultCache();
  }
  const mode = cache.mode || 'blacklist';
  const listConfig = Array.isArray(cache.list) ? cache.list.slice() : [];
  const ignorePrefixes = Array.isArray(cache.ignoreListPrefixes)
    ? cache.ignoreListPrefixes.map(normalizeRelPath)
    : [];

  async function walk(dir) {
    const list = [];
    let entries;
    try {
      entries = await fsp.readdir(dir, { withFileTypes: true });
    } catch (err) {
      return list;
    }

    entries.sort((a, b) => {
      if (a.isDirectory() && !b.isDirectory()) return -1;
      if (!a.isDirectory() && b.isDirectory()) return 1;
      return a.name.localeCompare(b.name);
    });

    for (const ent of entries) {
      const full = path.join(dir, ent.name);
      const rel = path.relative(projectRoot, full);

      if (ent.isDirectory()) {
        const relNorm = normalizeRelPath(rel);
        const underOverride = ignorePrefixes.some((p) => p && (relNorm === p || relNorm.startsWith(p + '/')));
        let shouldScanChildren = true;

        if (listConfig.length && !underOverride) {
          if (mode === 'blacklist') {
            // In blacklist mode, if this folder is under a blacklisted
            // prefix, do not descend into it.
            if (isMatchedByList(relNorm, listConfig)) {
              shouldScanChildren = false;
            }
          } else if (mode === 'whitelist') {
            // In whitelist mode we only descend into folders that are either
            // themselves under a whitelisted prefix or could contain a
            // whitelisted descendant.
            const includedSelf = isMatchedByList(relNorm, listConfig);
            const hasDesc = hasListDescendant(relNorm, listConfig);
            if (!includedSelf && !hasDesc) {
              shouldScanChildren = false;
            }
          }
        }

        let children = null;
        let lazy = false;
        if (shouldScanChildren) {
          children = await walk(full);
        } else {
          // Mark this directory as lazily-unscanned so the renderer can
          // offer an option to load it on demand.
          children = null;
          lazy = true;
        }
        list.push({ name: ent.name, relPath: rel, type: 'dir', children, lazy });
      } else {
        list.push({ name: ent.name, relPath: rel, type: 'file' });
      }
    }
    return list;
  }

  const rootName = path.basename(projectRoot);
  const tree = await walk(projectRoot);
  return { rootName, tree };
}

// Progressive scan that only loads the first layer initially
async function scanProjectTreeFirstLayer(projectRoot, scanToken) {
  // Load backup cache so we can respect blacklist/whitelist rules while
  // scanning. This lets us avoid descending into folders that are excluded
  // by the current configuration, which keeps large folders like
  // node_modules or .git from being fully scanned.
  let cache;
  try {
    cache = await readCache(projectRoot);
  } catch (err) {
    cache = defaultCache();
  }
  const mode = cache.mode || 'blacklist';
  const listConfig = Array.isArray(cache.list) ? cache.list.slice() : [];
  const ignorePrefixes = Array.isArray(cache.ignoreListPrefixes)
    ? cache.ignoreListPrefixes.map(normalizeRelPath)
    : [];

  async function walkFirstLayer(dir) {
    const list = [];
    let entries;
    try {
      entries = await fsp.readdir(dir, { withFileTypes: true });
    } catch (err) {
      return list;
    }

    entries.sort((a, b) => {
      if (a.isDirectory() && !b.isDirectory()) return -1;
      if (!a.isDirectory() && b.isDirectory()) return 1;
      return a.name.localeCompare(b.name);
    });

    for (const ent of entries) {
      // Check if scan was cancelled
      if (scanToken && activeScanProcesses.get(scanToken) === 'cancelled') {
        throw new Error('Scan cancelled');
      }

      const full = path.join(dir, ent.name);
      const rel = path.relative(projectRoot, full);

      if (ent.isDirectory()) {
        const relNorm = normalizeRelPath(rel);
        const underOverride = ignorePrefixes.some((p) => p && (relNorm === p || relNorm.startsWith(p + '/')));
        
        // Always mark directories as lazy for progressive loading
        list.push({ name: ent.name, relPath: rel, type: 'dir', children: null, lazy: true });
      } else {
        list.push({ name: ent.name, relPath: rel, type: 'file' });
      }
    }
    return list;
  }

  const rootName = path.basename(projectRoot);
  const tree = await walkFirstLayer(projectRoot);
  return { rootName, tree };
}

// Background scan to fill in directory contents progressively
async function scanDirectoryContents(projectRoot, relPath, scanToken) {
  const relNorm = normalizeRelPath(relPath || '');
  const absRoot = path.resolve(projectRoot, relNorm || '.');

  let st;
  try {
    st = await fsp.stat(absRoot);
  } catch (err) {
    return { error: 'Path not found' };
  }

  async function walk(dir) {
    const list = [];
    let entries;
    try {
      entries = await fsp.readdir(dir, { withFileTypes: true });
    } catch (err) {
      return list;
    }

    entries.sort((a, b) => {
      if (a.isDirectory() && !b.isDirectory()) return -1;
      if (!a.isDirectory() && b.isDirectory()) return 1;
      return a.name.localeCompare(b.name);
    });

    for (const ent of entries) {
      // Check if scan was cancelled
      if (scanToken && activeScanProcesses.get(scanToken) === 'cancelled') {
        throw new Error('Scan cancelled');
      }

      const full = path.join(dir, ent.name);
      const rel = path.relative(projectRoot, full);
      
      if (ent.isDirectory()) {
        const children = await walk(full);
        list.push({ name: ent.name, relPath: rel, type: 'dir', children, lazy: false });
      } else {
        list.push({ name: ent.name, relPath: rel, type: 'file' });
      }
    }
    return list;
  }

  if (st.isDirectory()) {
    const children = await walk(absRoot);
    return { children };
  }

  // For a file, there is no subtree to scan.
  return { children: [] };
}

// Scan a single subtree rooted at the given relative path. This ignores
// blacklist/whitelist rules so that when the user explicitly enables a
// previously skipped folder we show everything beneath it.
async function scanSubtree(projectRoot, rootRelPath) {
  const relNorm = normalizeRelPath(rootRelPath || '');
  const absRoot = path.resolve(projectRoot, relNorm || '.');

  let st;
  try {
    st = await fsp.stat(absRoot);
  } catch (err) {
    return { error: 'Path not found' };
  }

  async function walk(dir) {
    const list = [];
    let entries;
    try {
      entries = await fsp.readdir(dir, { withFileTypes: true });
    } catch (err) {
      return list;
    }

    entries.sort((a, b) => {
      if (a.isDirectory() && !b.isDirectory()) return -1;
      if (!a.isDirectory() && b.isDirectory()) return 1;
      return a.name.localeCompare(b.name);
    });

    for (const ent of entries) {
      const full = path.join(dir, ent.name);
      const rel = path.relative(projectRoot, full);
      if (ent.isDirectory()) {
        const children = await walk(full);
        list.push({ name: ent.name, relPath: rel, type: 'dir', children, lazy: false });
      } else {
        list.push({ name: ent.name, relPath: rel, type: 'file' });
      }
    }
    return list;
  }

  if (st.isDirectory()) {
    const children = await walk(absRoot);
    return { children };
  }

  // For a file, there is no subtree to scan.
  return { children: [] };
}

// Collect all files (absolute paths + relative path inside zip) from selected relative paths
function collectFilesForPaths(projectRoot, selectedRelPaths, deselectedRelPaths = []) {
  const items = [];
  const deselected = new Set((deselectedRelPaths || []).map((p) => String(p || '').replace(/\\/g, '/')));

  function isExcluded(relPath) {
    const norm = String(relPath || '').replace(/\\/g, '/');
    for (const pfx of deselected) {
      if (!pfx) continue;
      if (norm === pfx || norm.startsWith(pfx + '/')) return true;
    }
    return false;
  }

  function walkDir(absDir, relBase) {
    const entNames = fs.readdirSync(absDir, { withFileTypes: true });
    for (const ent of entNames) {
      const abs = path.join(absDir, ent.name);
      const rel = path.join(relBase, ent.name);
      if (isExcluded(rel)) continue;
      if (ent.isDirectory()) {
        walkDir(abs, rel);
      } else {
        items.push({ absPath: abs, relPathInZip: rel });
      }
    }
  }

  for (const rel of selectedRelPaths) {
    if (isExcluded(rel)) continue;
    const abs = path.resolve(projectRoot, rel);
    if (!fs.existsSync(abs)) continue;
    const stat = fs.statSync(abs);
    if (stat.isDirectory()) {
      walkDir(abs, rel);
    } else {
      items.push({ absPath: abs, relPathInZip: rel });
    }
  }

  return items;
}

function computeNextVersion(folderPath) {
  try {
    if (!fs.existsSync(folderPath)) return 1;
    const files = fs.readdirSync(folderPath);
    let max = 0;
    for (const f of files) {
      const m = /^Version (\d+) -/.exec(f);
      if (m) {
        const n = parseInt(m[1], 10);
        if (!Number.isNaN(n) && n > max) max = n;
      }
    }
    return max + 1;
  } catch (err) {
    return 1;
  }
}

function makeTimestamp() {
  const d = new Date();
  const pad = (s) => String(s).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}_${pad(d.getHours())}-${pad(d.getMinutes())}`;
}

module.exports.register = function (opts = {}) {
  // opts: { ipcMain, BrowserWindow, screen, execFile, computeSettingsBounds }
  const { ipcMain, BrowserWindow, screen, execFile, computeSettingsBounds } = opts;
  if (!ipcMain) throw new Error('ipcMain required');

  function computeBounds() {
    if (typeof computeSettingsBounds === 'function') {
      try {
        return computeSettingsBounds();
      } catch (e) {}
    }
    // Fallback centered window: width = 2/3 of workArea width, height = 3/5 of workArea height
    const disp = screen.getPrimaryDisplay();
    const ref = disp.workArea;
    const width = Math.max(640, Math.round(ref.width * 2 / 3));     // 2/3 of screen width
    const height = Math.max(360, Math.round(ref.height * 3 / 5));   // 3/5 of screen height
    const x = Math.round(ref.x + (ref.width - width) / 2);
    const y = Math.round(ref.y + (ref.height - height) / 2);
    return { x, y, width, height };
  }

  function createBackupWindow() {
    const bounds = computeBounds();
    if (backupWindow && !backupWindow.isDestroyed()) {
      try {
        backupWindow.setBounds(bounds);
        if (backupWindow.isMinimized()) backupWindow.restore();
        backupWindow.focus();
      } catch (e) {
        console.error('Failed to reposition existing backup window:', e);
      }
      return;
    }

    backupWindow = new BrowserWindow({
      x: bounds.x,
      y: bounds.y,
      width: bounds.width,
      height: bounds.height,
      minWidth: 640,
      // Ensure minHeight does not exceed the requested height on small screens.
      minHeight: Math.min(460, bounds.height),
      title: 'Create / Restore Backup',
      resizable: true,
      frame: true,
      webPreferences: {
        nodeIntegration: true,
        contextIsolation: false,
        backgroundThrottling: false
      }
    });

    const html = path.join(__dirname, 'index.html');
    backupWindow.loadFile(html);

    backupWindow.webContents.once('did-finish-load', () => {
      sendRestoreState();
    });

    backupWindow.on('closed', () => {
      backupWindow = null;
    });
  }

  // Expose the createBackupWindow function for external use (e.g., from menu)
  module.exports.openBackupWindow = createBackupWindow;

  ipcMain.on('open-backup-window', () => {
    try {
      createBackupWindow();
    } catch (err) {
      console.error('open-backup-window failed:', err);
    }
  });

  // Return defaults (useful for renderer to initialize if needed)
  ipcMain.handle('backup-get-defaults', async () => {
    return defaultCache();
  });

  ipcMain.handle('backup-resolve-path', async (event, projectRootArg, inputPath) => {
    try {
      const projectRoot = projectRootArg || process.cwd();
      return path.resolve(projectRoot, inputPath || '../backup');
    } catch (err) {
      return { error: String(err) };
    }
  });

  ipcMain.handle('backup-load-cache', async (event, projectRootArg) => {
    try {
      const projectRoot = projectRootArg || process.cwd();
      return await readCache(projectRoot);
    } catch (err) {
      console.error('backup-load-cache error:', err);
      return defaultCache();
    }
  });

  ipcMain.handle('backup-save-cache', async (event, projectRootArg, config) => {
    try {
      const projectRoot = projectRootArg || process.cwd();
      await writeCache(projectRoot, config);
      return { ok: true };
    } catch (err) {
      console.error('backup-save-cache error:', err);
      return { error: String(err) };
    }
  });

  ipcMain.handle('backup-scan-project', async (event, projectRootArg) => {
    try {
      const projectRoot = projectRootArg || process.cwd();
      return await scanProjectTree(projectRoot);
    } catch (err) {
      console.error('backup-scan-project error:', err);
      return { error: String(err) };
    }
  });

  ipcMain.handle('backup-scan-project-first-layer', async (event, projectRootArg) => {
    const scanToken = generateScanToken();
    activeScanProcesses.set(scanToken, 'scanning');
    
    try {
      const projectRoot = projectRootArg || process.cwd();
      const result = await scanProjectTreeFirstLayer(projectRoot, scanToken);
      activeScanProcesses.delete(scanToken);
      return result;
    } catch (err) {
      activeScanProcesses.delete(scanToken);
      if (err.message === 'Scan cancelled') {
        return { cancelled: true };
      }
      console.error('backup-scan-project-first-layer error:', err);
      return { error: String(err) };
    }
  });

  ipcMain.handle('backup-scan-directory-contents', async (event, projectRootArg, relPath) => {
    const scanToken = generateScanToken();
    activeScanProcesses.set(scanToken, 'scanning');
    
    try {
      const projectRoot = projectRootArg || process.cwd();
      const result = await scanDirectoryContents(projectRoot, relPath || '', scanToken);
      activeScanProcesses.delete(scanToken);
      return result;
    } catch (err) {
      activeScanProcesses.delete(scanToken);
      if (err.message === 'Scan cancelled') {
        return { cancelled: true };
      }
      console.error('backup-scan-directory-contents error:', err);
      return { error: String(err) };
    }
  });

  ipcMain.handle('backup-cancel-scan', async (event, scanToken) => {
    if (activeScanProcesses.has(scanToken)) {
      activeScanProcesses.set(scanToken, 'cancelled');
      return { cancelled: true };
    }
    return { error: 'Scan token not found' };
  });

  ipcMain.handle('backup-scan-subtree', async (event, projectRootArg, relPath) => {
    try {
      const projectRoot = projectRootArg || process.cwd();
      return await scanSubtree(projectRoot, relPath || '');
    } catch (err) {
      console.error('backup-scan-subtree error:', err);
      return { error: String(err) };
    }
  });

  ipcMain.handle('backup-list-backups', async (event, projectRootArg, backupFolderInput) => {
    try {
      const projectRoot = projectRootArg || process.cwd();
      const folder = path.resolve(projectRoot, backupFolderInput || '../backup');
      if (!fs.existsSync(folder)) return { backups: [], folder };
      const files = await fsp.readdir(folder);
      const list = [];
      for (const f of files) {
        const full = path.join(folder, f);
        const stat = await fsp.stat(full);
        if (stat.isFile() && /\.(zip|tar|gz|tgz)$/i.test(f)) {
          list.push({ name: f, fullPath: full, size: stat.size, mtime: stat.mtimeMs });
        }
      }
      list.sort((a, b) => b.mtime - a.mtime);
      return { backups: list, folder };
    } catch (err) {
      console.error('backup-list-backups error:', err);
      return { error: String(err) };
    }
  });

  ipcMain.handle('backup-create', async (event, projectRootArg, options = {}) => {
    try {
      const projectRoot = projectRootArg || process.cwd();
      const { selectedRelPaths = [], backupFolderInput = '../backup', backupName = '' } = options;

      if (!selectedRelPaths || !selectedRelPaths.length) {
        return { error: 'No files selected' };
      }

      const resolvedBackupFolder = path.resolve(projectRoot, backupFolderInput || '../backup');
      await fsp.mkdir(resolvedBackupFolder, { recursive: true });

      // If backup folder is inside the project, ensure it is excluded from selection (defensive)
      const relBackupInside = (function(){
        try {
          const rel = path.relative(projectRoot, resolvedBackupFolder);
          // Only treat as inside if it doesn't traverse up
          if (rel && !rel.startsWith('..') && !path.isAbsolute(rel)) return rel.replace(/\\/g,'/');
          return null;
        } catch (_) { return null; }
      })();
      const exclusionPrefixes = new Set(['backup']);
      if (relBackupInside) exclusionPrefixes.add(relBackupInside.split(path.sep).join('/'));

      // Filter selected paths to drop any that are (or are under) the excluded prefixes
      const filteredRelPaths = selectedRelPaths.filter(rel => {
        const norm = String(rel || '').replace(/\\/g,'/');
        for (const pfx of exclusionPrefixes) {
          if (!pfx) continue;
          if (norm === pfx || norm.startsWith(pfx + '/')) return false;
        }
        return true;
      });
      const deselectedRelPaths = Array.isArray(options.deselectedRelPaths) ? options.deselectedRelPaths : [];
      const itemsUnfiltered = collectFilesForPaths(projectRoot, filteredRelPaths, deselectedRelPaths);

      // Extra guard: drop any files that would land under the excluded prefixes
      const items = itemsUnfiltered.filter(it => {
        const norm = String(it.relPathInZip || '').replace(/\\/g,'/');
        for (const pfx of exclusionPrefixes) {
          if (norm === pfx || norm.startsWith(pfx + '/')) return false;
        }
        return true;
      });
      if (!items.length) return { error: 'No files to archive (maybe selected entries are empty)' };

      let nameToUse = (backupName && String(backupName).trim()) || '';
      if (!nameToUse) {
        const next = computeNextVersion(resolvedBackupFolder);
        const stamp = makeTimestamp();
        nameToUse = `Version ${next} - ${stamp}`;
      }

      const zipFileName = `${nameToUse}.zip`;
      const outPath = path.join(resolvedBackupFolder, zipFileName);

      const totalFiles = items.length;
      let processedFiles = 0;

      await new Promise((resolve, reject) => {
        const output = fs.createWriteStream(outPath);
        const archive = archiver('zip', { zlib: { level: 9 } });

        output.on('close', () => {
          try {
            if (backupWindow && !backupWindow.isDestroyed()) {
              backupWindow.webContents.send('backup-progress', { percent: 100, processed: totalFiles, total: totalFiles });
            }
          } catch (e) {}
          resolve();
        });

        archive.on('error', (err) => reject(err));

        archive.on('entry', () => {
          processedFiles += 1;
          const percent = totalFiles ? Math.round((processedFiles / totalFiles) * 100) : 0;
          try {
            if (backupWindow && !backupWindow.isDestroyed()) {
              backupWindow.webContents.send('backup-progress', { percent, processed: processedFiles, total: totalFiles });
            }
          } catch (e) {}
        });

        archive.pipe(output);

        for (const it of items) {
          archive.file(it.absPath, { name: it.relPathInZip });
        }

        archive.finalize();
      });

      // Reveal in file manager (use bin/reveal_in_finder inside the project root if available)
      try {
        const revealBin = path.join(projectRoot, 'bin', 'reveal_in_finder');
        if (fs.existsSync(revealBin)) {
          execFile(revealBin, [outPath], (err) => {
            if (err) console.warn('reveal_in_finder failed:', err);
          });
        }
      } catch (err) {
        console.warn('Failed to run reveal_in_finder:', err);
      }

      return { ok: true, path: outPath, name: zipFileName };
    } catch (err) {
      console.error('backup-create error:', err);
      return { error: String(err) };
    }
  });

  ipcMain.handle('backup-reveal-in-finder', async (event, fullPath) => {
    try {
      // Try reveal binary inside project root first, then fallback to a relative bin near src
      const projectRoot = process.cwd();
      const candidates = [
        path.join(projectRoot, 'bin', 'reveal_in_finder'),
        path.join(__dirname, '..', '..', '..', '..', 'bin', 'reveal_in_finder')
      ];
      let found = null;
      for (const c of candidates) {
        if (fs.existsSync(c)) { found = c; break; }
      }
      if (!found) return { error: 'reveal binary not found' };
      await new Promise((resolve, reject) => {
        execFile(found, [fullPath], (err) => {
          if (err) reject(err);
          else resolve();
        });
      });
      return { ok: true };
    } catch (err) {
      console.error('backup-reveal-in-finder error:', err);
      return { error: String(err) };
    }
  });

  ipcMain.handle('backup-restore-status', async () => {
    return Object.assign({}, restoreState);
  });

  ipcMain.handle('backup-restore', async (event, projectRootArg, options = {}) => {
    try {
      const projectRoot = projectRootArg || process.cwd();
      const { backupFullPath, preserveOnRestore = ['.auaci', '.venv', 'venv', 'node_modules'], restoreMode = 'full' } = options;
      if (!backupFullPath || !fs.existsSync(backupFullPath)) {
        setRestoreState({ state: 'error', message: 'Backup file not found', error: 'Backup file not found', backupFullPath: backupFullPath || null });
        return { error: 'Backup file not found' };
      }

      setRestoreState({
        state: 'running',
        message: 'Restoring backup...',
        error: null,
        backupFullPath
      });

      if (restoreMode === 'full') {
        // Full restore: Remove top-level entries except preserveOnRestore and except the backup folder itself (if inside project)
        const backupDir = path.dirname(backupFullPath);
        const entries = await fsp.readdir(projectRoot);
        for (const name of entries) {
          if (preserveOnRestore.includes(name)) continue;
          const full = path.join(projectRoot, name);
          if (path.resolve(full) === path.resolve(backupDir)) continue;
          try {
            await fsp.rm(full, { recursive: true, force: true });
          } catch (e) {
            console.warn('Failed to remove during restore:', full, e);
          }
        }
      }
      // For 'merge' mode, don't delete anything - just extract over existing files

      // Extract zip into project root
      await extract(backupFullPath, { dir: projectRoot });

      setRestoreState({ state: 'done', message: 'Restore complete.', error: null });
      return { ok: true };
    } catch (err) {
      console.error('backup-restore error:', err);
      setRestoreState({ state: 'error', message: 'Restore failed.', error: String(err) });
      return { error: String(err) };
    }
  });
};