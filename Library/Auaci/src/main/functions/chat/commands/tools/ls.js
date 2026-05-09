// src/main/functions/chat/commands/tools/ls.js
const { fs, path, appendLog } = require('./_shared');

module.exports = async function ls(params) {
  const rel = params && params.path ? params.path : '.';
  const maxDepth = typeof params?.max_depth === 'number' ? params.max_depth : Infinity;
  const base = path.isAbsolute(rel) ? rel : path.join(process.cwd(), rel);
  const entries = [];

  async function walk(dir, depth) {
    if (depth > maxDepth) return;
    let ents;
    try { ents = await fs.readdir(dir, { withFileTypes: true }); } catch (e) { return; }
    for (const ent of ents) {
      if (ent.name.startsWith('.')) continue; // basic ignore for dotfiles
      const full = path.join(dir, ent.name);
      entries.push(full);
      if (ent.isDirectory()) {
        await walk(full, depth + 1);
      }
    }
  }

  await walk(base, 0);
  await appendLog(`[tools.ls] path=${base} entries=${entries.length}`);
  return { entries };
};
