// Handler for "context_search" command - searches files for regex patterns and returns
// for each matched file: file_path and matched_lines (numbers only). No line contents are returned.

const fs = require('fs').promises;
const path = require('path');
const { appendLog } = require('../lib/utils');

// Maximum output size in characters (5KB)
const MAX_OUTPUT_CHARS = 5000;

module.exports = async function contextSearchCmd(params) {
  const cwd = process.cwd();
  // Accept either a single path or array of paths
  let pathArgs = [];
  if (Array.isArray(params && params.paths) && params.paths.length) pathArgs = params.paths.slice();
  else if (Array.isArray(params && params.file_paths) && params.file_paths.length) pathArgs = params.file_paths.slice();
  else if (params && params.path) pathArgs = [params.path];
  else pathArgs = [cwd];

  // Accept queries/patterns
  const queries = Array.isArray(params && params.queries) ? params.queries
    : Array.isArray(params && params.patterns) ? params.patterns
    : [];

  if (queries.length === 0) {
    return {
      success: false,
      error: 'context_search: missing queries (provide queries or patterns array)',
      error_code: 'ERR_INVALID_INPUT',
      matched_files: [],
      pattern_stats: []
    };
  }

  // Compile regexes (tolerant)
  const compiled = queries.map(q => {
    try {
      // If caller passes an object with { pattern, flags } this won't be handled; assume string regex
      return { pattern: q, re: new RegExp(q) };
    } catch (_) {
      return { pattern: q, re: null };
    }
  });

  const EXCLUDED_DIRS = new Set(['.venv', 'venv', 'vnv', '__pycache__', 'node_modules', '.auaci', '.git', 'dradew']);

  // Map to track whether each pattern matched anywhere
  const patternFound = new Map(queries.map(q => [q, false]));
  const results = [];

  async function processFile(full) {
    // Only regular files
    try {
      const st = await fs.stat(full);
      if (!st.isFile()) return;
    } catch (_) {
      return;
    }

    // Try to read as utf8; skip unreadable/binary files
    let content;
    try {
      content = await fs.readFile(full, 'utf8');
    } catch (_) {
      return;
    }

    const lines = String(content).split(/\r?\n/);
    const matchedLineNums = new Set();

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      for (const c of compiled) {
        if (!c.re) continue;
        try {
          if (c.re.test(line)) {
            matchedLineNums.add(i + 1);
            patternFound.set(c.pattern, true);
          }
        } catch (_) {
          // ignore regex runtime errors for a given line
        }
      }
    }

    if (matchedLineNums.size > 0) {
      results.push({
        file_path: full,
        matched_lines: Array.from(matchedLineNums).sort((a, b) => a - b)
      });
    }
  }

  async function walk(dir) {
    let entries;
    try {
      entries = await fs.readdir(dir, { withFileTypes: true });
    } catch (_) {
      return;
    }
    for (const ent of entries) {
      const full = path.join(dir, ent.name);
      if (ent.isDirectory()) {
        if (EXCLUDED_DIRS.has(ent.name) || ent.name.startsWith('.')) continue;
        await walk(full);
        continue;
      }
      await processFile(full);
    }
  }

  // Process each provided path (file or directory). Normalize relative to cwd.
  for (const p of pathArgs) {
    const abs = path.isAbsolute(p) ? p : path.join(cwd, p);
    try {
      const stat = await fs.stat(abs);
      if (stat.isDirectory()) {
        await walk(abs);
      } else if (stat.isFile()) {
        await processFile(abs);
      }
    } catch (_) {
      // skip paths that don't exist or are unreadable
    }
  }

  const matched_files = results.map(r => ({
    file_path: r.file_path,
    matched_lines: r.matched_lines
  }));

  const pattern_stats = queries.map(q => ({ pattern: q, matched: !!patternFound.get(q) }));
  await appendLog(`[executor] context_search matched=${matched_files.length}`);

  let result = {
    success: true,
    matched_files,
    pattern_stats,
    path: (Array.isArray(pathArgs) ? pathArgs.join(',') : String(pathArgs))
  };

  // Truncate entire result to 5KB
  let resultStr = JSON.stringify(result);
  if (resultStr.length > MAX_OUTPUT_CHARS) {
    // Reduce matched_files array until under limit
    let truncatedFiles = [];
    for (const file of matched_files) {
      truncatedFiles.push(file);
      const testResult = {
        matched_files: truncatedFiles,
        pattern_stats,
        path: result.path,
        truncated: true,
        truncation_message: 'The rest is truncated.'
      };
      if (JSON.stringify(testResult).length > MAX_OUTPUT_CHARS) {
        truncatedFiles.pop();
        break;
      }
    }
    result = {
      success: true,
      matched_files: truncatedFiles,
      pattern_stats,
      path: result.path,
      truncated: true,
      truncation_message: 'The rest is truncated.'
    };
  }
  
  return result;
};
