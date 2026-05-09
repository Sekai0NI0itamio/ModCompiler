// Tool variant (used by the tools/ runtime) for context-search
// Returns same shape as the handler: { matched_files, pattern_stats, path } (NO contents)

const { fs, path, appendLog } = require('./_shared');

function isStringArray(a) {
  return Array.isArray(a) && a.every(x => typeof x === 'string');
}

module.exports = async function contextSearch(params) {
  const cwd = process.cwd();

  let pathArgs = [];
  if (isStringArray(params && params.paths)) pathArgs = params.paths.slice();
  else if (isStringArray(params && params.file_paths)) pathArgs = params.file_paths.slice();
  else if (params && typeof params.path === 'string') pathArgs = [params.path];
  else pathArgs = [cwd];

  const queries = Array.isArray(params && params.queries) ? params.queries
    : Array.isArray(params && params.patterns) ? params.patterns
    : [];

  if (queries.length === 0) {
    return { error: 'context_search: missing queries/patterns' };
  }

  const compiled = queries.map(q => {
    try { return { pattern: q, re: new RegExp(q) }; } catch (_) { return { pattern: q, re: null }; }
  });

  const EXCLUDED_DIRS = new Set(['.venv', 'venv', 'vnv', '__pycache__', 'node_modules', '.auaci', '.git', 'dradew']);
  const patternFound = new Map(queries.map(q => [q, false]));
  const results = [];

  async function processFile(full) {
    try { const st = await fs.stat(full); if (!st.isFile()) return; } catch (_) { return; }
    let content;
    try { content = await fs.readFile(full, 'utf8'); } catch (_) { return; }
    const lines = String(content).split(/\r?\n/);
    const matchedLines = new Set();
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      for (const c of compiled) {
        if (!c.re) continue;
        try {
          if (c.re.test(line)) {
            matchedLines.add(i + 1);
            patternFound.set(c.pattern, true);
          }
        } catch (_) {}
      }
    }
    if (matchedLines.size > 0) {
      results.push({ file_path: full, matched_lines: Array.from(matchedLines).sort((a,b)=>a-b) });
    }
  }

  async function walk(dir) {
    let ents;
    try { ents = await fs.readdir(dir, { withFileTypes: true }); } catch (_) { return; }
    for (const ent of ents) {
      const full = path.join(dir, ent.name);
      if (ent.isDirectory()) {
        if (EXCLUDED_DIRS.has(ent.name) || ent.name.startsWith('.')) continue;
        await walk(full);
        continue;
      }
      await processFile(full);
    }
  }

  for (const p of pathArgs) {
    const abs = path.isAbsolute(p) ? p : path.join(cwd, p);
    try {
      const st = await fs.stat(abs);
      if (st.isDirectory()) await walk(abs);
      else if (st.isFile()) await processFile(abs);
    } catch (_) {}
  }

  const matched_files = results.map(r => ({ file_path: r.file_path, matched_lines: r.matched_lines }));
  const pattern_stats = queries.map(q => ({ pattern: q, matched: !!patternFound.get(q) }));

  await appendLog(`[tools.context-search] base=${pathArgs.join(',')} matched=${matched_files.length}`);
  return { matched_files, pattern_stats, path: pathArgs.join(',') };
};