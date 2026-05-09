// src/main/functions/chat/commands/handlers/fs_append.js
// Append text to the end of an existing file

const fs = require('fs').promises;
const path = require('path');
const { appendLog } = require('../lib/utils');
const { applyPatchForTool, buildFullReplacePatch } = require('../lib/unified-edit');
const { recordFileOperation } = require('../../fileOperationTracker');

/**
 * File append command handler
 * @param {Object} params - { path, text }
 */
module.exports = async function fsAppendCmd(params) {
  const filePath = params.path;
  const text = params.text;
  
  // Validate parameters
  if (!filePath) {
    return {
      success: false,
      error: 'Missing required parameter: path',
      error_code: 'ERR_INVALID_INPUT',
      path: filePath
    };
  }
  
  if (typeof text !== 'string') {
    return {
      success: false,
      error: 'Missing required parameter: text',
      error_code: 'ERR_INVALID_INPUT',
      path: filePath
    };
  }
  
  const abs = path.isAbsolute(filePath) ? filePath : path.join(process.cwd(), filePath);
  
  await appendLog(`[tools.fs_append] Appending to ${filePath}`);
  
  // Check if file exists (fsAppend only works on existing files)
  try {
    await fs.access(abs);
  } catch {
    return {
      success: false,
      error: `File not found: ${filePath}. Use create_file or write_to_file for new files.`,
      error_code: 'ERR_NOT_FOUND',
      path: filePath
    };
  }
  
  // Read existing content to check if we need a newline
  let existingContent;
  try {
    existingContent = await fs.readFile(abs, 'utf8');
  } catch (err) {
    return {
      success: false,
      error: `Failed to read file: ${err.message}`,
      error_code: 'ERR_IO',
      path: filePath
    };
  }
  
  // Determine if we need to add a newline before the new content
  let contentToAppend = text;
  if (existingContent.length > 0 && !existingContent.endsWith('\n')) {
    contentToAppend = '\n' + text;
  }
  
  // Append via unified patch engine
  const updatedContent = existingContent + contentToAppend;
  const patchText = buildFullReplacePatch(filePath, existingContent, updatedContent);
  const summary = await applyPatchForTool(patchText, params);
  if (!summary || summary.success === false) {
    return {
      success: false,
      error: (summary && summary.errors && summary.errors[0] && summary.errors[0].error) || 'apply_patch failed',
      error_code: 'ERR_APPLY_PATCH',
      path: filePath
    };
  }
  
  const linesAdded = text.split('\n').length;
  const totalLines = (existingContent + contentToAppend).split('\n').length;
  
  await appendLog(`[tools.fs_append] Appended ${linesAdded} lines to ${filePath}`);

  const ctx = params && params.__context ? params.__context : {};
  if (ctx && ctx.sessionId != null && ctx.entryIndex != null) {
    recordFileOperation(ctx.sessionId, ctx.entryIndex, 'fs_append', {
      filePath: abs,
      originalContent: existingContent,
      fileExisted: true,
      operationSubtype: 'file_edit',
      operationDetails: { linesAdded }
    }).catch(() => {});
  }
  
  return {
    success: true,
    path: filePath,
    lines_added: linesAdded,
    total_lines: totalLines,
    display_content: `Appended to ${filePath}\n+${linesAdded} line(s), total: ${totalLines} lines`
  };
};
