// src/main/functions/chat/commands/handlers/str_replace.js
// String replace tool - unified find-and-replace in files
// Uses fuzzy matching and applies edits through apply_patch

const fs = require('fs').promises;
const path = require('path');
const { appendLog } = require('../lib/utils');
const {
  applyPatchForTool,
  buildFullReplacePatch,
  replaceWithMatch,
} = require('../lib/unified-edit');

/**
 * String replace command handler
 * @param {Object} params - { path, oldStr, newStr }
 */
module.exports = async function strReplaceCmd(params) {
  const filePath = params.path;
  const oldStr = params.oldStr;
  const newStr = params.newStr;
  
  // Validate parameters
  if (!filePath) {
    return {
      success: false,
      error: 'Missing required parameter: path',
      error_code: 'ERR_INVALID_INPUT',
      path: filePath
    };
  }
  
  if (typeof oldStr !== 'string') {
    return {
      success: false,
      error: 'Missing required parameter: oldStr',
      error_code: 'ERR_INVALID_INPUT',
      path: filePath
    };
  }
  
  if (typeof newStr !== 'string') {
    return {
      success: false,
      error: 'Missing required parameter: newStr',
      error_code: 'ERR_INVALID_INPUT',
      path: filePath
    };
  }
  
  // oldStr and newStr must be different
  if (oldStr === newStr) {
    return {
      success: false,
      error: 'oldStr and newStr are identical - no replacement needed',
      error_code: 'ERR_NOOP',
      path: filePath
    };
  }
  
  const abs = path.isAbsolute(filePath) ? filePath : path.join(process.cwd(), filePath);
  
  await appendLog(`[tools.str_replace] Replacing in ${filePath}`);
  
  // Check if file exists
  try {
    await fs.access(abs);
  } catch {
    return {
      success: false,
      error: `File not found: ${filePath}`,
      error_code: 'ERR_NOT_FOUND',
      path: filePath
    };
  }
  
  // Read file content
  let content;
  try {
    content = await fs.readFile(abs, 'utf8');
  } catch (err) {
    return {
      success: false,
      error: `Failed to read file: ${err.message}`,
      error_code: 'ERR_IO',
      path: filePath
    };
  }

  const match = replaceWithMatch(content, oldStr, newStr);
  if (match.type === 'none') {
    return {
      success: false,
      error: `String not found in ${filePath}. ${match.suggestion || 'The oldStr must match exactly or be very similar.'}`,
      error_code: 'ERR_NOT_FOUND',
      path: filePath
    };
  }

  if (match.type === 'multiple') {
    return {
      success: false,
      error: `String found multiple times in ${filePath}. The oldStr must uniquely identify a single location.`,
      error_code: 'ERR_AMBIGUOUS',
      path: filePath
    };
  }

  const newContent = match.updated;
  const patchText = buildFullReplacePatch(filePath, content, newContent);
  const summary = await applyPatchForTool(patchText, params);
  if (!summary || summary.success === false) {
    return {
      success: false,
      error: (summary && summary.errors && summary.errors[0] && summary.errors[0].error) || 'apply_patch failed',
      error_code: 'ERR_APPLY_PATCH',
      path: filePath
    };
  }

  const beforeLines = match.line || 1;
  const oldLines = oldStr.split('\n').length;
  const newLines = newStr.split('\n').length;
  
  await appendLog(`[tools.str_replace] Replaced at line ${beforeLines}, ${oldLines} lines -> ${newLines} lines`);
  
  return {
    success: true,
    path: filePath,
    line: beforeLines,
    old_lines: oldLines,
    new_lines: newLines,
    display_content: `Replaced in ${filePath} at line ${beforeLines}\n${oldLines} line(s) → ${newLines} line(s)`
  };
};
