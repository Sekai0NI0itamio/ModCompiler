// src/main/functions/chat/commands/handlers/txtlize.js
const fs = require('fs').promises;
const fssync = require('fs');
const path = require('path');
const { ensureDirSync, appendLog } = require('../lib/utils');

function isStringArray(a) {
  return Array.isArray(a) && a.every(x => typeof x === 'string' && x.trim() !== '');
}

function uniqueStrings(arr) {
  const seen = new Set();
  const out = [];
  for (const s of arr) {
    const k = String(s);
    if (!seen.has(k)) { seen.add(k); out.push(k); }
  }
  return out;
}

function formatError(missing, extraMsg) {
  let msg = 'txtlize: malformed or missing arguments.';
  if (missing && missing.length) msg += ` Missing: ${missing.join(', ')}.`;
  if (extraMsg) msg += ` ${extraMsg}`;
  msg += ' Provide input as {"name":"output_name","file_paths":["/abs/or/relative/path1","path2",...] }';
  return msg;
}

async function resolveReadableTextFiles(paths, cwd) {
  const out = [];
  for (const p of paths) {
    const abs = path.isAbsolute(p) ? p : path.join(cwd, p);
    try {
      const st = await fs.stat(abs);
      if (!st.isFile()) continue;
      // quick binary heuristic: read small chunk and check for zeros
      const fd = await fs.open(abs, 'r');
      try {
        const toRead = Math.min(8192, st.size || 8192);
        const buf = Buffer.alloc(toRead);
        const { bytesRead } = await fd.read(buf, 0, toRead, 0);
        const slice = buf.slice(0, bytesRead);
        let hasZero = false;
        for (let i = 0; i < Math.min(slice.length, 512); i++) { if (slice[i] === 0) { hasZero = true; break; } }
        if (hasZero) continue; // likely binary
      } finally {
        try { await fd.close(); } catch (_) {}
      }
      out.push({ abs, size: st.size });
    } catch (_) {
      // skip unreadable
    }
  }
  return out;
}

module.exports = async function txtlize(params) {
  const cwd = process.cwd();
  const name = params && typeof params.name === 'string' ? params.name.trim() : '';
  let filePaths = [];
  if (params && Array.isArray(params.file_paths)) filePaths = params.file_paths;
  else if (params && Array.isArray(params.files)) filePaths = params.files;

  const missing = [];
  if (!name) missing.push('name');
  if (!Array.isArray(filePaths) || filePaths.length === 0) missing.push('file_paths');

  if (missing.length > 0) {
    return {
      success: false,
      error: formatError(missing),
      error_code: 'ERR_INVALID_INPUT',
      missing,
      example: { name: 'bundle', file_paths: ['src/index.ts', 'README.md'] }
    };
  }

  // Normalize and dedupe
  filePaths = uniqueStrings(filePaths.map(String));

  // Resolve and filter readable text files
  const readable = await resolveReadableTextFiles(filePaths, cwd);
  if (readable.length === 0) {
    return {
      success: false,
      error: 'txtlize: none of the provided file_paths are readable text files.',
      error_code: 'ERR_NOT_FOUND',
      provided: filePaths
    };
  }

  // Assemble contents
  let assembled = '';
  let includedCount = 0;
  for (const f of readable) {
    try {
      const content = await fs.readFile(f.abs, 'utf8');
      assembled += `File name: ${path.basename(f.abs)}\n`;
      const rel = path.relative(cwd, f.abs).replace(/\\/g, '/');
      assembled += `File path: ${rel.startsWith('..') ? f.abs : rel}\n`;
      assembled += `File Content:\n${content}\n\n`;
      includedCount++;
    } catch (_) {
      // skip file on read error
    }
  }

  if (!assembled) {
    return {
      success: false,
      error: 'txtlize: failed to read any of the provided file_paths as text.',
      error_code: 'ERR_IO'
    };
  }

  // Write to project .auaci/tmp/<name>.txt
  const outDir = path.join(cwd, '.auaci', 'tmp');
  ensureDirSync(outDir);
  const safeName = name.replace(/[^\w\-.]+/g, '_');
  const outPath = path.join(outDir, `${safeName}.txt`);
  await fs.writeFile(outPath, assembled, 'utf8');
  await appendLog(`[tools.txtlize] created ${outPath} with ${includedCount} file(s)`);

  let sizeBytes = 0;
  try { sizeBytes = fssync.statSync(outPath).size || 0; } catch (_) { sizeBytes = 0; }

  return {
    success: true,
    output_path: outPath,
    file_count: includedCount,
    total_size_bytes: sizeBytes,
  };
};
