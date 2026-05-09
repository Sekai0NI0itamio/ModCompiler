// src/main/functions/chat/commands/tools/glob.js
const { fs, path, appendLog } = require('./_shared');

function globToRegex(pat) {
  const esc = String(pat || '*').replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*\*/g, '§§DOUBLESTAR§§').replace(/\*/g, '[^/]*').replace(/§§DOUBLESTAR§§/g, '.*').replace(/\?/g, '.');
  return new RegExp(`^${esc}$`);
}

module.exports = async function glob(params) {
  const baseRel = params && params.path ? params.path : '.';
  const base = path.isAbsolute(baseRel) ? baseRel : path.join(process.cwd(), baseRel);
  const pattern = params && params.pattern ? String(params.pattern) : '*';
  const re = globToRegex(pattern);
  const matches = [];
  const seenPaths = new Set(); // Track seen paths to avoid duplicates
  const EXCLUDED_DIRS = new Set(['.venv', 'venv', 'vnv', '__pycache__', 'node_modules', '.auaci', '.git', 'dradew']);

  async function walk(dir) {
    let ents;
    try { ents = await fs.readdir(dir, { withFileTypes: true }); } catch (e) { return; }
    for (const ent of ents) {
      if (EXCLUDED_DIRS.has(ent.name) || ent.name.startsWith('.')) continue;
      const full = path.join(dir, ent.name);
      if (ent.isDirectory()) {
        await walk(full);
      }
      const rel = full.startsWith(base) ? full.slice(base.length + (base.endsWith(path.sep) ? 0 : 1)) : full;
      if (re.test(rel) && !seenPaths.has(full)) {
        seenPaths.add(full);
        matches.push(full);
      }
    }
  }

  await walk(base);
  await appendLog(`[tools.glob] base=${base} pattern=${pattern} matches=${matches.length}`);
  return { matches };
};
