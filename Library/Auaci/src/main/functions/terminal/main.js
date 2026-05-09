// src/main/functions/terminal/main.js
// Main-process terminal manager: prefers node-pty; falls back to macOS `script` shim if unavailable.

let _ptyLib = null;
try { _ptyLib = require('node-pty'); } catch (_) { _ptyLib = null; }
const { spawn } = require('child_process');

function register({ ipcMain, BrowserWindow }) {
  // Keyed by `${webContentsId}:${sessionId}` -> { pty | child }
  const sessions = new Map();

  const getSenderWC = (event) => {
    try { return event && (BrowserWindow.fromWebContents(event.sender)?.webContents || event.sender); } catch (_) { return null; }
  };
  const keyFor = (wc, sessionId) => `${wc && wc.id}:${sessionId || 'default'}`;

  const killSession = (key) => {
    const s = sessions.get(key);
    if (!s) return;
    try { s.child && s.child.kill('SIGKILL'); } catch (_) {}
    try { s.pty && s.pty.kill && s.pty.kill(); } catch (_) {}
    sessions.delete(key);
  };
  const killAllForWC = (wc) => {
    if (!wc) return;
    const prefix = `${wc.id}:`;
    for (const k of Array.from(sessions.keys())) {
      if (k.startsWith(prefix)) killSession(k);
    }
  };

  ipcMain.handle('terminal-start', (event, { cwd, cols = 80, rows = 24, sessionId = 'default' } = {}) => {
    const wc = getSenderWC(event);
    if (!wc) return false;

    const key = keyFor(wc, sessionId);
    // Clean existing session with same id
    killSession(key);

    const shell = process.env.SHELL || '/bin/zsh';
    const env = Object.assign({}, process.env);

    const send = (channel, payload) => {
      try {
        wc && wc.send(channel, payload);
      } catch (_) {}
    };

    if (_ptyLib) {
      // Preferred: real PTY
      try {
        const proc = _ptyLib.spawn(shell, ['-l'], {
          name: 'xterm-color',
          cols, rows,
          cwd: cwd || process.cwd(),
          env,
        });
        sessions.set(key, { pty: proc });
        proc.onData((d) => send('terminal-data', { sessionId, chunk: d }));
        proc.onExit((e) => { send('terminal-exit', { sessionId, ...e }); killSession(key); });
        return true;
      } catch (e) {
        // fallthrough to script shim
      }
    }

    // Fallback: plain shell via pipes (no real PTY). Limited features but allows basic commands.
    const envWithTerm = Object.assign({}, env, { TERM: env.TERM || 'xterm-256color' });
    const child = spawn(shell, ['-l', '-i'], {
      cwd: cwd || process.cwd(),
      env: envWithTerm,
      stdio: ['pipe', 'pipe', 'pipe']
    });

    sessions.set(key, { child });

    child.stdout.on('data', (buf) => send('terminal-data', { sessionId, chunk: buf.toString('utf8') }));
    child.stderr.on('data', (buf) => send('terminal-data', { sessionId, chunk: buf.toString('utf8') }));
    child.on('exit', (code, signal) => { send('terminal-exit', { sessionId, code, signal }); killSession(key); });

    return true;
  });

  ipcMain.on('terminal-input', (event, { sessionId = 'default', data } = {}) => {
    const wc = getSenderWC(event);
    if (!wc) return;
    const key = keyFor(wc, sessionId);
    const s = sessions.get(key);
    if (!s) return;
    if (s.pty) {
      try { s.pty.write(data); } catch (_) {}
      return;
    }
    if (s.child && !s.child.killed) {
      // Map common control keys to signals for non-pty fallback
      if (data === '\u0003') { // Ctrl+C
        try { s.child.kill('SIGINT'); } catch (_) {}
        return;
      }
      if (data === '\u001a') { // Ctrl+Z
        try { s.child.kill('SIGTSTP'); } catch (_) {}
        return;
      }
      if (s.child.stdin) {
        try { s.child.stdin.write(data); } catch (_) {}
      }
    }
  });

  ipcMain.on('terminal-resize', (event, { sessionId = 'default', cols, rows } = {}) => {
    const wc = getSenderWC(event);
    if (!wc) return;
    const key = keyFor(wc, sessionId);
    const s = sessions.get(key);
    if (s && s.pty && cols && rows) {
      try { s.pty.resize(cols, rows); } catch (_) {}
    }
    // script shim: ignore
  });

  ipcMain.on('terminal-stop', (event, { sessionId = 'default' } = {}) => {
    const wc = getSenderWC(event);
    if (!wc) return;
    const key = keyFor(wc, sessionId);
    killSession(key);
  });

  ipcMain.on('terminal-stop-all', (event) => {
    const wc = getSenderWC(event);
    killAllForWC(wc);
  });
}

module.exports = { register };
