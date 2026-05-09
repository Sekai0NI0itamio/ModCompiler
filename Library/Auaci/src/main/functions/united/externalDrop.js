// src/main/functions/united/externalDrop.js
// Shared helper to spawn the native filedroplistener helper and process its JSON output.
// Exports spawnFileDrop(...) which returns { process, resultPromise }.
// resultPromise resolves to an array of file objects: { name, size, path, content } (content may be null).

const { spawn } = require('child_process');
const fs = require('fs').promises;
const path = require('path');
const { readFileContent } = require('./fsUtils');
const { appendLog } = require('./logger');
const { ipcRenderer } = require('electron');

async function resolveAppPath(provided) {
  if (provided) return provided;
  try {
    return await ipcRenderer.invoke('get-app-path');
  } catch (err) {
    return process.cwd();
  }
}

/**
 * spawnFileDrop(options)
 * options:
 *   - appPath (optional)
 *   - binName (default 'filedroplistener')
 *   - outputPath (default '/tmp/dropped_files.json')
 *   - logPath (default '/tmp/events.log')
 *   - cwd
 *
 * Returns { process, resultPromise }
 * - process is the spawned child (or null if spawn failed)
 * - resultPromise resolves to an array of file objects (with content when available)
 */
async function spawnFileDrop(options = {}) {
  const appPath = await resolveAppPath(options.appPath);
  const binName = options.binName || 'filedroplistener';
  const outputPath = options.outputPath || '/tmp/dropped_files.json';
  const logPath = options.logPath || '/tmp/events.log';
  const cwd = options.cwd || appPath;
  const binaryPath = path.join(appPath, 'bin', binName);

  try { await appendLog(logPath, `Launching filedroplistener: ${binaryPath}`); } catch (_) {}

  let child = null;
  try {
    child = spawn(binaryPath, [], { cwd });
  } catch (err) {
    await appendLog(logPath, `spawn filedroplistener failed: ${err && err.message ? err.message : String(err)}`);
    return { process: null, resultPromise: Promise.resolve([]) };
  }

  const resultPromise = new Promise((resolve) => {
    child.on('error', async (err) => {
      await appendLog(logPath, `Filedroplistener error: ${err && err.message ? err.message : String(err)}`);
      resolve([]);
    });

    child.on('exit', async (code) => {
      try { await appendLog(logPath, `Filedroplistener exited with code ${code}`); } catch(_) {}
      if (code !== 0) { resolve([]); return; }

      // Ensure output file exists
      try {
        await fs.access(outputPath);
      } catch (err) {
        await appendLog(logPath, `No output file at ${outputPath} after filedroplistener exit`);
        resolve([]);
        return;
      }

      try {
        const raw = await fs.readFile(outputPath, 'utf8');
        await appendLog(logPath, `Read ${outputPath}: ${String(raw).slice(0, 1000)}`);
        let items;
        try { items = JSON.parse(raw); } catch (parseErr) {
          await appendLog(logPath, `Failed to parse ${outputPath}: ${parseErr && parseErr.message ? parseErr.message : String(parseErr)}`);
          resolve([]);
          return;
        }

        if (!Array.isArray(items) || items.length === 0) {
          resolve([]);
          return;
        }

        const processed = await Promise.all(items.map(async (it) => {
          try {
            // Try to include file content if possible (unified reader)
            const full = await readFileContent(it.path, { appPath, logPath });
            if (full) return full;
            // fallback minimal object
            const st = await fs.stat(it.path).catch(() => null);
            return { name: it.name || (st ? path.basename(it.path) : ''), size: it.size || (st ? st.size : 0), path: it.path, content: null };
          } catch (err) {
            await appendLog(logPath, `Error processing dropped file ${it.path}: ${err && err.message ? err.message : String(err)}`);
            return null;
          }
        }));

        // Try to cleanup the output path
        await fs.unlink(outputPath).catch(() => {});

        resolve(processed.filter(Boolean));
      } catch (err) {
        await appendLog(logPath, `Error handling dropped files: ${err && err.message ? err.message : String(err)}`);
        resolve([]);
      }
    });
  });

  return { process: child, resultPromise };
}

module.exports = { spawnFileDrop };