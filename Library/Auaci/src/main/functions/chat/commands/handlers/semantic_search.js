// Lightweight semantic_search handler (optimized)
// - Parallel file processing with bounded concurrency
// - Early file filtering by extension and keyword precheck
// - Configurable size/depth limits and soft time budget
// - Maintains previous scoring behavior for accuracy

const fs = require('fs').promises;
const path = require('path');
const { appendLog } = require('../lib/utils');

function tokenize(text) {
  return String(text || '')
    .toLowerCase()
    .split(/[^a-z0-9_]+/)
    .filter(Boolean);
}

function unique(arr) {
  const s = new Set(arr);
  return Array.from(s);
}

module.exports = async function semanticSearchCmd(params) {
  const cwd = process.cwd();

  // Paths handling similar to context_search
  let pathArgs = [];
  if (Array.isArray(params && params.paths) && params.paths.length) pathArgs = params.paths.slice();
  else if (Array.isArray(params && params.file_paths) && params.file_paths.length) pathArgs = params.file_paths.slice();
  else if (params && params.path) pathArgs = [params.path];
  else pathArgs = [cwd];

  const query = (Array.isArray(params && params.queries) && params.queries.length) ? params.queries.join(' ') : (params && params.query ? String(params.query) : '');
  if (!query || !query.trim()) throw new Error('semantic_search: missing query parameter');

  const maxResults = Number(params && params.max_results) || 10;
  const contextLines = Number(params && params.context_lines) || 2;
  const includeSnippets = (params && params.include_snippets) !== false;

  // Performance-related knobs with safe defaults
  const maxFileSizeKB = Number(params && params.max_file_kb) || 200; // cap per file
  const maxDepth = Number(params && params.max_depth) || 8; // directory recursion depth
  const timeBudgetMs = Number(params && params.time_budget_ms) || 3500; // soft budget
  const concurrency = Math.min(Math.max(Number(params && params.concurrency) || 16, 2), 64);
  const includeExtensions = Array.isArray(params && params.file_types) && params.file_types.length
    ? params.file_types.map(e => e.startsWith('.') ? e.toLowerCase() : ('.' + e.toLowerCase()))
    : ['.js','.ts','.jsx','.tsx','.json','.md','.txt','.py','.java','.c','.cpp','.h','.cs','.rb','.go','.rs','.php'];

  const queryTokens = unique(tokenize(query));
  if (queryTokens.length === 0) throw new Error('semantic_search: query has no valid tokens');

  const EXCLUDED_DIRS = new Set(['.venv', 'venv', '__pycache__', 'node_modules', '.git']);
  const results = [];

  // Concurrency control
  const queue = [];
  let active = 0;
  let stopped = false;
  const startedAt = Date.now();
  const softTimedOut = () => (Date.now() - startedAt) > timeBudgetMs;

  function shouldStop() {
    return stopped || softTimedOut();
  }

  function pushTask(fn) {
    return new Promise((resolve) => {
      queue.push({ fn, resolve });
      drain();
    });
  }

  async function drain() {
    if (active >= concurrency) return;
    const task = queue.shift();
    if (!task) return;
    active++;
    try { await task.fn(); } catch (_) { /* ignore */ }
    active--;
    task.resolve();
    if (queue.length) drain();
  }

  function hasAllowedExtension(filePath) {
    const ext = path.extname(filePath).toLowerCase();
    return includeExtensions.includes(ext);
  }

  async function processFile(full) {
    if (shouldStop()) return;

    try {
      const st = await fs.stat(full);
      if (!st.isFile()) return;
      if (st.size > maxFileSizeKB * 1024) return;
    } catch (_) { return; }

    if (!hasAllowedExtension(full)) return;

    let content;
    try { content = await fs.readFile(full, 'utf8'); } catch (_) { return; }

    // Quick keyword precheck before tokenization
    const lower = content.toLowerCase();
    let quickHit = false;
    for (const t of queryTokens) {
      if (lower.includes(t)) { quickHit = true; break; }
    }
    if (!quickHit) return;

    const words = tokenize(content);
    if (words.length === 0) return;

    // Compute simple overlap score
    const freq = Object.create(null);
    for (const w of words) freq[w] = (freq[w] || 0) + 1;

    let score = 0;
    for (const t of queryTokens) {
      score += (freq[t] || 0);
    }

    if (score <= 0) return;

    // Normalize by file length
    const normalized = score / Math.sqrt(words.length);

    const entry = { file_path: full, raw_score: normalized };

    if (includeSnippets) {
      const lines = String(content).split(/\r?\n/);
      const snippets = [];
      for (let i = 0; i < lines.length; i++) {
        if (snippets.length >= 10) break;
        const line = lines[i];
        const low = line.toLowerCase();
        for (const t of queryTokens) {
          if (low.includes(t)) {
            const start = Math.max(0, i - contextLines);
            const end = Math.min(lines.length - 1, i + contextLines);
            const snippet = lines.slice(start, end + 1).join('\n');
            snippets.push({ line_number: i + 1, snippet });
            break;
          }
        }
      }
      entry.matched_snippets = snippets;
    }

    results.push(entry);
  }

  async function walk(dir, depth = 0) {
    if (shouldStop()) return;
    let entries;
    try { entries = await fs.readdir(dir, { withFileTypes: true }); } catch (_) { return; }
    for (const ent of entries) {
      if (shouldStop()) return;
      const full = path.join(dir, ent.name);
      if (ent.isDirectory()) {
        if (EXCLUDED_DIRS.has(ent.name) || ent.name.startsWith('.')) continue;
        if (depth >= maxDepth) continue;
        await walk(full, depth + 1);
        continue;
      }
      // Enqueue file processing with concurrency control
      await pushTask(() => processFile(full));
    }
  }

  for (const p of pathArgs) {
    const abs = path.isAbsolute(p) ? p : path.join(cwd, p);
    try {
      const stat = await fs.stat(abs);
      if (stat.isDirectory()) await walk(abs, 0);
      else if (stat.isFile()) await pushTask(() => processFile(abs));
    } catch (_) { /* skip */ }
  }

  // Wait for all queued tasks to complete or time budget to elapse
  async function waitForQueueToDrain() {
    while ((active > 0 || queue.length > 0) && !softTimedOut()) {
      // Small delay to yield
      await new Promise(r => setTimeout(r, 5));
      // Trigger more draining if needed
      drain();
    }
    if (softTimedOut()) {
      stopped = true;
    }
    // Flush remaining tasks best-effort
    while (active > 0 && (Date.now() - startedAt) < (timeBudgetMs + 250)) {
      await new Promise(r => setTimeout(r, 5));
    }
  }

  await waitForQueueToDrain();

  // Sort descending by raw_score
  results.sort((a, b) => b.raw_score - a.raw_score);

  const top = results.slice(0, maxResults).map(r => ({ file_path: r.file_path, score: Number(r.raw_score.toFixed(6)), matched_snippets: r.matched_snippets || [] }));

  const timedOut = softTimedOut();
  await appendLog(`[executor] semantic_search query="${String(query).slice(0,80)}" results=${top.length} timedOut=${timedOut}`);

  // Limit output size to prevent truncation
  const MAX_OUTPUT_SIZE = 10000; // 10KB
  const output = { query, results: top, timed_out: timedOut, success: true };
  let outputStr = JSON.stringify(output);
  
  if (outputStr.length > MAX_OUTPUT_SIZE) {
    // Reduce snippet content first
    for (const result of top) {
      if (result.matched_snippets && result.matched_snippets.length > 3) {
        result.matched_snippets = result.matched_snippets.slice(0, 3);
      }
      
      // Reduce snippet line length
      for (const snippet of result.matched_snippets) {
        if (snippet.snippet && snippet.snippet.length > 500) {
          snippet.snippet = snippet.snippet.substring(0, 500) + '... [truncated]';
        }
      }
    }
  }

  return output;
};
