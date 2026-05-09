// src/main/functions/file_info/main.js
// Main-process: File Info window and IPC handlers (live size calculation, metadata)

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const { execFile } = require('child_process');

let infoWindow = null;
const scanners = new Map(); // webContentsId -> { cancel: () => void }

function formatPerms(mode) {
  try {
    const m = mode & 0o7777; // keep perms + specials
    const rwx = ['---','--x','-w-','-wx','r--','r-x','rw-','rwx'];
    const u = rwx[(m >> 6) & 7];
    const g = rwx[(m >> 3) & 7];
    const o = rwx[m & 7];
    return `${u}${g}${o}`;
  } catch (_) { return ''; }
}

async function statPath(p, deref = false) {
  try {
    return deref ? await fsp.stat(p) : await fsp.lstat(p);
  } catch (e) {
    return null;
  }
}

async function getWhereFroms(p) {
  // macOS: try mdls for kMDItemWhereFroms (more readable than raw xattr plist)
  return await new Promise((resolve) => {
    try {
      execFile('mdls', ['-name', 'kMDItemWhereFroms', '-raw', p], { timeout: 2500 }, (err, stdout) => {
        if (err) return resolve(null);
        const out = String(stdout || '').trim();
        if (!out || out === '(null)') return resolve(null);
        // mdls prints (
        //   "https://example"
        // ) or an URL directly
        const cleaned = out.replace(/^\(\s*|\s*\)$/g, '').replace(/^"|"$/g, '');
        resolve(cleaned);
      });
    } catch (e) { resolve(null); }
  });
}

async function getQuarantine(p) {
  return await new Promise((resolve) => {
    try {
      execFile('xattr', ['-p', 'com.apple.quarantine', p], { timeout: 1500 }, (err, stdout) => {
        if (err) return resolve(null);
        resolve(String(stdout || '').trim());
      });
    } catch (e) { resolve(null); }
  });
}

function createInfoWindow({ BrowserWindow, screen }, filePath) {
  const disp = screen.getPrimaryDisplay();
  const ref = disp.workArea;
  const width = Math.max(420, Math.round(ref.width * 0.32));
  const height = Math.max(360, Math.round(ref.height * 0.42));
  const x = Math.round(ref.x + (ref.width - width) / 2);
  const y = Math.round(ref.y + (ref.height - height) / 2);

  if (infoWindow && !infoWindow.isDestroyed()) {
    try {
      infoWindow.setBounds({ x, y, width, height });
      infoWindow.focus();
      infoWindow.webContents.send('file-info-init', { filePath });
      return infoWindow;
    } catch (_) {}
  }

  infoWindow = new BrowserWindow({
    x, y, width, height,
    minWidth: 380,
    minHeight: 280,
    title: 'File Info',
    resizable: true,
    frame: true,
    webPreferences: { nodeIntegration: true, contextIsolation: false }
  });
  const html = path.join(__dirname, 'index.html');
  infoWindow.loadFile(html);

  infoWindow.on('closed', () => {
    // cancel any running scanner for this webContents id
    try {
      const id = infoWindow.webContents.id;
      const sc = scanners.get(id);
      if (sc && typeof sc.cancel === 'function') sc.cancel();
    } catch (_) {}
    infoWindow = null;
  });

  infoWindow.webContents.once('did-finish-load', () => {
    try { infoWindow.webContents.send('file-info-init', { filePath }); } catch (_) {}
  });

  return infoWindow;
}

async function getBasicInfo(fullPath) {
  try {
    const st = await statPath(fullPath, true);
    const lst = await statPath(fullPath, false);
    if (!st && !lst) return { error: 'Path not found' };
    const isSymlink = !!(lst && !lst.isDirectory() && lst.isSymbolicLink && lst.isSymbolicLink());
    const type = st && st.isDirectory() ? 'directory' : (st && st.isFile() ? 'file' : 'other');
    const perms = st ? formatPerms(st.mode) : '';
    const whereFroms = await getWhereFroms(fullPath);
    const quarantine = await getQuarantine(fullPath);
    return {
      ok: true,
      path: fullPath,
      base: path.basename(fullPath),
      type,
      isSymlink,
      size: st ? st.size : 0,
      atimeMs: st ? st.atimeMs : undefined,
      mtimeMs: st ? st.mtimeMs : undefined,
      ctimeMs: st ? st.ctimeMs : undefined,
      birthtimeMs: st ? st.birthtimeMs : undefined,
      mode: st ? st.mode : undefined,
      perms,
      uid: st ? st.uid : undefined,
      gid: st ? st.gid : undefined,
      whereFroms,
      quarantine
    };
  } catch (e) {
    return { error: String(e && e.message ? e.message : e) };
  }
}

function startSizeScanFor(win, fullPath, sendProgress) {
  let cancelled = false;
  const cancel = () => { cancelled = true; };

  async function scan() {
    try {
      const st = await statPath(fullPath, true);
      if (!st) { sendProgress({ done: true, totalBytes: 0 }); return; }
      if (st.isFile()) { sendProgress({ done: true, totalBytes: st.size }); return; }
      // Directory: walk recursively
      let total = 0;
      let files = 0, dirs = 0;

      async function walk(dir) {
        if (cancelled) return;
        let entries;
        try { entries = await fsp.readdir(dir, { withFileTypes: true }); }
        catch (_) { return; }
        for (const ent of entries) {
          if (cancelled) return;
          const p = path.join(dir, ent.name);
          try {
            if (ent.isDirectory()) {
              dirs++;
              await walk(p);
            } else if (ent.isFile()) {
              const s = await fsp.stat(p);
              total += s.size;
              files++;
              if (!cancelled) sendProgress({ totalBytes: total, files, dirs });
            } else {
              // ignore other types
            }
          } catch (_) {}
        }
      }

      if (st.isDirectory()) {
        await walk(fullPath);
        sendProgress({ done: true, totalBytes: total, files, dirs });
      }
    } catch (e) {
      sendProgress({ done: true, error: String(e && e.message ? e.message : e) });
    }
  }

  scan();
  return cancel;
}

function register({ ipcMain, BrowserWindow, screen }) {
  if (!ipcMain) throw new Error('ipcMain required');

  ipcMain.on('file-info-open-window', (event, fullPath) => {
    try { createInfoWindow({ BrowserWindow, screen }, fullPath); } catch (e) { console.error('file-info-open-window', e); }
  });

  ipcMain.handle('file-info-get-basic', async (_e, fullPath) => {
    return await getBasicInfo(fullPath);
  });

  ipcMain.on('file-info-start-size', (event, fullPath) => {
    const wc = event.sender;
    const id = wc.id;
    try {
      const cancel = startSizeScanFor(wc, fullPath, (data) => {
        try { wc.send('file-info-progress', data); } catch (_) {}
      });
      scanners.set(id, { cancel });
    } catch (e) {
      try { wc.send('file-info-progress', { done: true, error: String(e && e.message ? e.message : e) }); } catch (_) {}
    }
  });

  ipcMain.on('file-info-stop-size', (event) => {
    try {
      const wc = event.sender;
      const id = wc.id;
      const sc = scanners.get(id);
      if (sc && typeof sc.cancel === 'function') sc.cancel();
      scanners.delete(id);
    } catch (_) {}
  });
}

module.exports = { register };
