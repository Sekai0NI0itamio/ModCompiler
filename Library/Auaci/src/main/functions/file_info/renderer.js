// src/main/functions/file_info/renderer.js
const { ipcRenderer } = require('electron');

function fmtBytes(n) {
  const b = Number(n||0);
  if (b < 1024) return `${b} B`;
  const kb = b/1024; if (kb < 1024) return `${kb.toFixed(1)} KB`;
  const mb = kb/1024; if (mb < 1024) return `${mb.toFixed(1)} MB`;
  const gb = mb/1024; return `${gb.toFixed(2)} GB`;
}

function fmtDate(ms) {
  if (!ms && ms !== 0) return '';
  try { return new Date(ms).toLocaleString(); } catch (_) { return '';
  }
}

let currentPath = null;

async function loadBasic(p) {
  const res = await ipcRenderer.invoke('file-info-get-basic', p);
  if (!res || res.error) {
    document.getElementById('fi-name').textContent = 'Error';
    document.getElementById('fi-path').textContent = res && res.error ? res.error : '';
    return;
  }
  document.getElementById('fi-name').textContent = res.base || '';
  document.getElementById('fi-path').textContent = res.path || '';
  document.getElementById('fi-type').textContent = `${res.type}${res.isSymlink ? ' (symlink)' : ''}`;
  document.getElementById('fi-mtime').textContent = fmtDate(res.mtimeMs);
  document.getElementById('fi-btime').textContent = fmtDate(res.birthtimeMs || res.ctimeMs);
  document.getElementById('fi-perms').textContent = res.perms || '';
  document.getElementById('fi-ids').textContent = `${res.uid ?? ''} / ${res.gid ?? ''}`;
  document.getElementById('fi-where').textContent = res.whereFroms || '—';
  document.getElementById('fi-quar').textContent = res.quarantine || '—';

  // If it's a plain file, we already have size
  if (res.type === 'file') {
    document.getElementById('fi-size').textContent = fmtBytes(res.size);
  } else {
    // Start live size
    document.getElementById('fi-size').textContent = 'Calculating…';
    ipcRenderer.send('file-info-start-size', p);
  }
}

ipcRenderer.on('file-info-init', (_e, { filePath }) => {
  currentPath = filePath;
  loadBasic(filePath);
});

ipcRenderer.on('file-info-progress', (_e, data) => {
  try {
    if (data && data.error) {
      document.getElementById('fi-size').textContent = `Error: ${data.error}`;
      return;
    }
    if (typeof data.totalBytes === 'number') {
      document.getElementById('fi-size').textContent = `${fmtBytes(data.totalBytes)}${(data.files || data.dirs) ? `  (${data.files||0} files, ${data.dirs||0} folders)` : ''}`;
    }
    // When done, we keep the final number; no progress bar needed.
  } catch (_) {}
});

window.addEventListener('beforeunload', () => {
  try { ipcRenderer.send('file-info-stop-size'); } catch (_) {}
});
