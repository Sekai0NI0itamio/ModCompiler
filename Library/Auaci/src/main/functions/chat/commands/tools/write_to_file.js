// src/main/functions/chat/commands/tools/write_to_file.js
const { fs, path, ensureDirSync, appendLog } = require('./_shared');

module.exports = async function write_to_file(params) {
  const filePath = params && params.path;
  const content = params && params.the_content;
  if (!filePath || typeof content !== 'string') throw new Error('write_to_file: path and the_content required');
  const abs = path.isAbsolute(filePath) ? filePath : path.join(process.cwd(), filePath);
  ensureDirSync(path.dirname(abs));
  await fs.writeFile(abs, content, 'utf8');
  await appendLog(`[tools.write_to_file] wrote ${abs}`);
  return { success: true };
};
