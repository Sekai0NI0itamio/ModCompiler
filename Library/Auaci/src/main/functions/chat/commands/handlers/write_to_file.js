// src/main/functions/chat/commands/handlers/write_to_file.js
const path = require('path');
const fs = require('fs').promises;
const { ensureDirSync } = require('../lib/utils');
const { applyPatchForTool, buildAddFilePatch, buildFullReplacePatch } = require('../lib/unified-edit');
const { recordFileOperation } = require('../../fileOperationTracker');

/**
 * Get the current working directory for the chat session
 */
function getChatCwd() {
  try {
    const { getCurrentCwd } = require('../../ui/terminalInput');
    const cwd = getCurrentCwd();
    if (cwd) return cwd;
  } catch (_) {}
  return process.cwd();
}

module.exports = async function writeToFileCmd(params) {
  const filePath = params.path;
  const content = params.the_content;
  if (!filePath || typeof content !== 'string') throw new Error('write_to_file: path and the_content required');
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
  const patchText = fileExisted
    ? buildFullReplacePatch(filePath, originalContent || '', content)
    : buildAddFilePatch(filePath, content);
  const summary = await applyPatchForTool(patchText, params);
  if (!summary || summary.success === false) {
    const errMsg = (summary && summary.errors && summary.errors[0] && summary.errors[0].error) || 'apply_patch failed';
    throw new Error(`write_to_file: ${errMsg}`);
  }

  const ctx = params && params.__context ? params.__context : {};
  if (ctx && ctx.sessionId != null && ctx.entryIndex != null) {
    recordFileOperation(ctx.sessionId, ctx.entryIndex, 'write_to_file', {
      filePath: abs,
      originalContent,
      fileExisted,
      operationSubtype: fileExisted ? 'file_edit' : 'file_creation',
      operationDetails: { bytes: content.length }
    }).catch(() => {});
  }
  return { success: true, path: filePath, bytes_written: content.length, file_existed: fileExisted };
};
