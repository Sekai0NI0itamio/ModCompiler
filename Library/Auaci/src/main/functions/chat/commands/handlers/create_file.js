// src/main/functions/chat/commands/handlers/create_file.js
const path = require('path');
const fs = require('fs').promises;
const { ensureDirSync, withLineNumbers, readFileText, appendLog } = require('../lib/utils');
const { applyPatchForTool, buildAddFilePatch, buildFullReplacePatch } = require('../lib/unified-edit');
const { recordFileOperation } = require('../../fileOperationTracker');

/**
 * Get the current working directory for the chat session
 * Falls back to process.cwd() if not available
 */
function getChatCwd() {
  try {
    const { getCurrentCwd } = require('../../ui/terminalInput');
    const cwd = getCurrentCwd();
    if (cwd) return cwd;
  } catch (_) {}
  return process.cwd();
}

module.exports = async function createFileCmd(params) {
  const filePath = params.path || params.file_path;
  const content = params.content !== undefined ? params.content : params.contents;
  if (!filePath) throw new Error('create_file: missing path');
  const abs = path.isAbsolute(filePath) ? filePath : path.join(getChatCwd(), filePath);
  let originalContent = null;
  let fileExisted = true;
  try {
    originalContent = await fs.readFile(abs, 'utf8');
  } catch (err) {
    if (err && err.code === 'ENOENT') {
      fileExisted = false;
      originalContent = null;
    }
  }
  ensureDirSync(path.dirname(abs));
  const finalContent = String(content || '');
  const patchText = fileExisted
    ? buildFullReplacePatch(filePath, originalContent || '', finalContent)
    : buildAddFilePatch(filePath, finalContent);
  const summary = await applyPatchForTool(patchText, params);
  if (!summary || summary.success === false) {
    const errMsg = (summary && summary.errors && summary.errors[0] && summary.errors[0].error) || 'apply_patch failed';
    throw new Error(`create_file: ${errMsg}`);
  }
  const after = await readFileText(abs);
  const lines = after.split('\n').length;
  const result = {
    file_content_after_create: {
      path: abs,
      content: withLineNumbers(after, 1),
      line_range_start: 1,
      line_range_end: lines,
    },
    success: true,
    path: filePath,
    bytes_written: String(content || '').length,
    file_existed: fileExisted
  };
  const ctx = params && params.__context ? params.__context : {};
  if (ctx && ctx.sessionId != null && ctx.entryIndex != null) {
    recordFileOperation(ctx.sessionId, ctx.entryIndex, 'create_file', {
      filePath: abs,
      originalContent,
      fileExisted,
      operationSubtype: fileExisted ? 'file_edit' : 'file_creation',
      operationDetails: { bytes: String(content || '').length }
    }).catch(() => {});
  }
  await appendLog(`[executor] create_file ok path=${abs}`);
  return result;
};
