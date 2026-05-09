// src/main/functions/chat/commands/handlers/edit_function.js
// Edit or delete function/method/class blocks by name - AST-aware editing

const path = require('path');
const fs = require('fs').promises;
const { appendLog, detectEOL } = require('../lib/utils');
const { applyPatchForTool, buildFullReplacePatch } = require('../lib/unified-edit');
const getFunctionCmd = require('./get_function');

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

/**
 * Edit a function/symbol by name
 * Params: {
 *   path: string,
 *   name: string,              // Symbol name to edit
 *   new_content?: string,      // New content (omit to delete)
 *   action?: 'replace' | 'delete' | 'insert_before' | 'insert_after'
 * }
 */
module.exports = async function editFunctionCmd(params) {
  const filePath = params.path || params.file_path;
  const symbolName = params.name || params.function_name || params.symbol;
  const action = params.action || (params.new_content === undefined || params.new_content === '' ? 'delete' : 'replace');
  const newContent = params.new_content || params.content || '';
  
  function buildError(message, code, extra = {}) {
    return { success: false, error: message, error_code: code || 'ERR_UNKNOWN', ...extra };
  }
  
  if (!filePath) return buildError('edit_function: missing path', 'ERR_INVALID_INPUT');
  if (!symbolName) return buildError('edit_function: missing name (function/symbol name to edit)', 'ERR_INVALID_INPUT', { path: filePath });
  
  const abs = path.isAbsolute(filePath) ? filePath : path.join(getChatCwd(), filePath);
  
  // Get the symbol info
  let symbolInfo;
  try {
    symbolInfo = await getFunctionCmd({ path: filePath, name: symbolName });
  } catch (e) {
    return buildError(`edit_function: ${e.message}`, e.code || 'ERR_NOT_FOUND', { path: filePath, symbol: symbolName });
  }
  
  if (symbolInfo && symbolInfo.success === false) {
    return buildError(`edit_function: ${symbolInfo.error || 'symbol lookup failed'}`, symbolInfo.error_code || 'ERR_NOT_FOUND', { path: filePath, symbol: symbolName });
  }
  
  if (!symbolInfo.symbol) {
    return buildError(`edit_function: symbol "${symbolName}" not found`, 'ERR_NOT_FOUND', { path: filePath, symbol: symbolName });
  }
  
  const symbol = symbolInfo.symbol;
  
  // Read the file
  const rawContent = await fs.readFile(abs, 'utf8');
  const eol = detectEOL(rawContent);
  const lines = rawContent.split(/\r?\n/);
  
  // Validate line numbers
  if (symbol.start_line < 1 || symbol.end_line > lines.length) {
    throw new Error(`edit_function: invalid line range ${symbol.start_line}-${symbol.end_line} for file with ${lines.length} lines`);
  }
  
  // Build the new file content
  const before = lines.slice(0, symbol.start_line - 1);
  const after = lines.slice(symbol.end_line);
  const oldContent = lines.slice(symbol.start_line - 1, symbol.end_line).join(eol);
  
  let newLines;
  let resultMessage;
  
  switch (action) {
    case 'delete':
      newLines = [...before, ...after];
      resultMessage = `Deleted ${symbol.type} "${symbolName}" (${symbol.line_count} lines removed)`;
      break;
      
    case 'replace':
      const replacementLines = newContent.split(/\r?\n/);
      newLines = [...before, ...replacementLines, ...after];
      resultMessage = `Replaced ${symbol.type} "${symbolName}" (${symbol.line_count} lines → ${replacementLines.length} lines)`;
      break;
      
    case 'insert_before':
      const insertBeforeLines = newContent.split(/\r?\n/);
      const originalLines = lines.slice(symbol.start_line - 1, symbol.end_line);
      newLines = [...before, ...insertBeforeLines, ...originalLines, ...after];
      resultMessage = `Inserted ${insertBeforeLines.length} lines before ${symbol.type} "${symbolName}"`;
      break;
      
    case 'insert_after':
      const insertAfterLines = newContent.split(/\r?\n/);
      const origLines = lines.slice(symbol.start_line - 1, symbol.end_line);
      newLines = [...before, ...origLines, ...insertAfterLines, ...after];
      resultMessage = `Inserted ${insertAfterLines.length} lines after ${symbol.type} "${symbolName}"`;
      break;
      
    default:
      return buildError(`edit_function: unknown action "${action}". Use: replace, delete, insert_before, insert_after`, 'ERR_INVALID_INPUT', { path: filePath, symbol: symbolName });
  }
  
  // Apply through unified patch engine
  const newFileContent = newLines.join(eol);
  const patchText = buildFullReplacePatch(filePath, rawContent, newFileContent);
  const summary = await applyPatchForTool(patchText, params);
  if (!summary || summary.success === false) {
    const errMsg = (summary && summary.errors && summary.errors[0] && summary.errors[0].error) || 'apply_patch failed';
    return buildError(`edit_function: ${errMsg}`, 'ERR_APPLY_PATCH', { path: filePath, symbol: symbolName });
  }
  
  // Build diff info for UI
  const oldLines = oldContent.split(/\r?\n/);
  const newContentLines = action === 'delete' ? [] : newContent.split(/\r?\n/);
  const oldLineCount = symbol.line_count;
  const newLineCount = action === 'delete' ? 0 : newContentLines.length;
  const linesChanged = action === 'delete' ? oldLineCount : newLineCount;
  
  await appendLog(`[tools.edit_function] ${action} "${symbolName}" in ${filePath}: ${resultMessage}`);
  
  return {
    success: true,
    message: resultMessage,
    path: filePath,
    symbol: symbolName,
    action,
    start_line: symbol.start_line,
    end_line: symbol.end_line,
    old_lines: oldLineCount,
    new_lines: newLineCount,
    lines_changed: linesChanged,
    changes: {
      start_line: symbol.start_line,
      old_line_count: oldLineCount,
      new_line_count: newLineCount
    },
    total_lines: newLines.length,
    diff_hunk: {
      header: `@@ -${symbol.start_line},${symbol.line_count} +${symbol.start_line},${newContentLines.length} @@`,
      lines: [
        ...oldLines.map(l => ({ type: 'del', text: l })),
        ...newContentLines.map(l => ({ type: 'add', text: l }))
      ],
      stats: { added: newContentLines.length, removed: oldLines.length }
    }
  };
};
