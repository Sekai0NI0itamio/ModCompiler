// src/main/functions/united/fsUtils.js
// Robust file content reader used by multiple places in the renderer.
// Strategy:
//  - Try to use the native helper binary (bin/read_file_content) if present.
//  - If binary fails/unavailable, fall back to fs.readFile('utf8').
//  - Returns an object { name, size, path, content } or null on failure.

const fs = require('fs').promises;
const path = require('path');
const { execFile } = require('child_process');
const { ipcRenderer } = require('electron');
const { appendLog } = require('./logger');

function execFilePromise(file, args = []) {
  return new Promise((resolve, reject) => {
    execFile(file, args, { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 }, (err, stdout, stderr) => {
      if (err) return reject({ err, stdout, stderr });
      resolve({ stdout: stdout || '', stderr: stderr || '' });
    });
  });
}

/**
 * readFileContent(filePath, opts)
 * - opts.appPath (optional) - path to app bundle (used to find bin/read_file_content)
 * - opts.logPath (optional) - path to append logs
 *
 * Returns: { name, size, path, content } or null
 */
async function readFileContent(filePath, opts = {}) {
  const logPath = opts.logPath || '/tmp/events.log';
  if (!filePath) {
    await appendLog(logPath, `readFileContent called without filePath`);
    return null;
  }

  // Validate file
  let stats;
  try {
    stats = await fs.stat(filePath);
    if (!stats.isFile()) {
      await appendLog(logPath, `readFileContent: not a regular file: ${filePath}`);
      return null;
    }
  } catch (err) {
    await appendLog(logPath, `readFileContent: stat failed for ${filePath}: ${err && err.message ? err.message : String(err)}`);
    return null;
  }

  // Try native binary first (if available)
  try {
    const appPath = opts.appPath || (await (async () => {
      try { return await ipcRenderer.invoke('get-app-path'); } catch (_) { return null; }
    })());

    if (appPath) {
      const bin = path.join(appPath, 'bin', 'read_file_content');
      try {
        const { stdout, stderr } = await execFilePromise(bin, [filePath]);
        if (stderr && stderr.trim()) {
          await appendLog(logPath, `read_file_content stderr for ${filePath}: ${stderr.trim().slice(0, 200)}`);
        }
        if (stdout && stdout.trim()) {
          try {
            const parsed = JSON.parse(stdout);
            // ensure shape (fallback to ensure name/size/path)
            parsed.name = parsed.name || path.basename(filePath);
            parsed.path = parsed.path || filePath;
            parsed.size = parsed.size || stats.size;
            return parsed;
          } catch (parseErr) {
            await appendLog(logPath, `read_file_content parse failed for ${filePath}: ${parseErr && parseErr.message ? parseErr.message : String(parseErr)}`);
            // fall through to fs fallback
          }
        }
      } catch (execErr) {
        await appendLog(logPath, `read_file_content exec failed for ${filePath}: ${(execErr && execErr.err && execErr.err.message) ? execErr.err.message : String(execErr)}`);
        // fall through
      }
    }
  } catch (err) {
    await appendLog(logPath, `readFileContent: native attempt error for ${filePath}: ${err && err.message ? err.message : String(err)}`);
  }

  // Fallback to Node fs readFile
  try {
    const content = await fs.readFile(filePath, 'utf8').catch(() => null);
    return { name: path.basename(filePath), size: stats.size, path: filePath, content };
  } catch (err) {
    await appendLog(logPath, `readFileContent: fallback fs.readFile failed for ${filePath}: ${err && err.message ? err.message : String(err)}`);
    return null;
  }
}

module.exports = { readFileContent };