// src/main/functions/chat/commands/handlers/read_any_files.js
const path = require('path');
const fs = require('fs').promises;
const { withLineNumbers, readFileText, appendLog } = require('../lib/utils');

module.exports = async function readAnyFilesCmd(params) {
  const files = Array.isArray(params.files) ? params.files : [];
  if (files.length === 0) {
    return { success: false, error: 'Missing required parameter: files', error_code: 'ERR_INVALID_INPUT', files: [] };
  }
  const out = [];
  let successCount = 0;
  let failCount = 0;
  for (const f of files) {
    const p = f.path;
    if (!p) {
      out.push({ path: '', success: false, error: 'Missing file path', error_code: 'ERR_INVALID_INPUT' });
      failCount++;
      continue;
    }
    const abs = path.isAbsolute(p) ? p : path.join(process.cwd(), p);
    try {
      const stat = await fs.stat(abs);
      const content = await readFileText(abs);
      const lineCount = String(content || '').split('\n').length;
      out.push({
        path: abs,
        success: true,
        content: withLineNumbers(content, 1),
        size_bytes: stat.size,
        is_empty: stat.size === 0,
        total_line_count: lineCount,
        displayed_lines: lineCount,
        is_truncated: false
      });
      successCount++;
    } catch (e) {
      const code = e && e.code === 'ENOENT' ? 'ERR_NOT_FOUND' : (e && e.code === 'EACCES' ? 'ERR_PERMISSION' : (e && e.code ? String(e.code) : 'ERR_IO'));
      out.push({ path: abs, success: false, error: String(e && e.message ? e.message : e), error_code: code });
      failCount++;
    }
  }
  await appendLog(`[executor] read_any_files count=${out.length}`);
  return {
    success: failCount === 0,
    files: out,
    summary: { total: out.length, success: successCount, failed: failCount }
  };
};
