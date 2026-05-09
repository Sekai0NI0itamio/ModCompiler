// src/main/functions/chat/commands/handlers/edit_lines.js
// Precise line-based editing tool - forces small, targeted edits

const path = require('path');
const fs = require('fs').promises;
const { appendLog, detectEOL } = require('../lib/utils');
const { applyPatchForTool, buildFullReplacePatch } = require('../lib/unified-edit');

const MAX_EDIT_LINES = null; // No line limit

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
 * Edit specific lines in a file
 * Params: {
 *   path: string,
 *   start_line: number,      // 1-indexed
 *   end_line?: number,       // 1-indexed, inclusive (default: start_line)
 *   new_content: string,     // Replacement content (can be empty to delete)
 *   action?: 'replace' | 'insert_before' | 'insert_after' | 'delete'
 * }
 */
module.exports = async function editLinesCmd(params) {
  const filePath = params.path || params.file_path;
  const startLine = Number(params.start_line);
  const endLine = Number(params.end_line || params.start_line);
  const newContent = typeof params.new_content === 'string' ? params.new_content : (params.content || '');
  const action = params.action || 'replace';

  function buildError(message, code, extra = {}) {
    return { success: false, error: message, error_code: code || 'ERR_UNKNOWN', ...extra };
  }

  if (!filePath) return buildError('edit_lines: missing path', 'ERR_INVALID_INPUT');
  if (!startLine || startLine < 1) return buildError('edit_lines: invalid start_line (must be >= 1)', 'ERR_INVALID_INPUT', { path: filePath });
  if (endLine < startLine) return buildError('edit_lines: end_line must be >= start_line', 'ERR_INVALID_INPUT', { path: filePath });

  const lineCount = endLine - startLine + 1;
  const newLineCount = newContent ? newContent.split(/\r?\n/).length : 0;

  const abs = path.isAbsolute(filePath) ? filePath : path.join(getChatCwd(), filePath);

  let raw;
  try {
    raw = await fs.readFile(abs, 'utf8');
  } catch (e) {
    const code = e.code === 'ENOENT' ? 'ERR_NOT_FOUND' : (e.code || 'ERR_IO');
    return buildError(`edit_lines: failed to read file: ${e.message}`, code, { path: filePath });
  }
  const eol = detectEOL(raw);
  const lines = raw.split(/\r?\n/);

  if (startLine > lines.length + 1) {
    return buildError(`edit_lines: start_line ${startLine} exceeds file length (${lines.length} lines)`, 'ERR_INVALID_INPUT', { path: filePath });
  }

  const newLines = newContent ? newContent.split(/\r?\n/) : [];

  let before, after, removedLines, resultMessage;

  switch (action) {
    case 'delete':
      before = lines.slice(0, startLine - 1);
      after = lines.slice(endLine);
      removedLines = lines.slice(startLine - 1, endLine);
      resultMessage = `Deleted lines ${startLine}-${endLine} (${lineCount} lines)`;
      break;

    case 'insert_before':
      before = lines.slice(0, startLine - 1);
      after = lines.slice(startLine - 1);
      removedLines = [];
      resultMessage = `Inserted ${newLineCount} lines before line ${startLine}`;
      break;

    case 'insert_after':
      before = lines.slice(0, endLine);
      after = lines.slice(endLine);
      removedLines = [];
      resultMessage = `Inserted ${newLineCount} lines after line ${endLine}`;
      break;

    case 'replace':
    default:
      before = lines.slice(0, startLine - 1);
      after = lines.slice(endLine);
      removedLines = lines.slice(startLine - 1, endLine);
      if (lineCount === newLineCount) {
        resultMessage = `Modified lines ${startLine}-${endLine} (${lineCount} lines)`;
      } else {
        resultMessage = `Replaced lines ${startLine}-${endLine} (${lineCount} → ${newLineCount} lines)`;
      }
      break;
  }

  const finalLines = action === 'delete' 
    ? [...before, ...after]
    : [...before, ...newLines, ...after];

  const updatedContent = finalLines.join(eol);
  const patchText = buildFullReplacePatch(filePath, raw, updatedContent);
  const summary = await applyPatchForTool(patchText, params);
  if (!summary || summary.success === false) {
    const errMsg = (summary && summary.errors && summary.errors[0] && summary.errors[0].error) || 'apply_patch failed';
    return buildError(`edit_lines: ${errMsg}`, 'ERR_APPLY_PATCH', { path: filePath });
  }

  await appendLog(`[tools.edit_lines] ${action} lines ${startLine}-${endLine} in ${filePath}`);

  const oldLineCount = removedLines.length;
  const newLineCountFinal = action === 'delete' ? 0 : newLineCount;
  const linesChanged = action === 'delete' ? oldLineCount : newLineCountFinal;

  return {
    success: true,
    message: resultMessage,
    path: filePath,
    action,
    start_line: startLine,
    end_line: endLine,
    old_lines: oldLineCount,
    new_lines: newLineCountFinal,
    lines_changed: linesChanged,
    changes: {
      start_line: startLine,
      end_line: endLine,
      old_line_count: oldLineCount,
      new_line_count: newLineCountFinal
    },
    total_lines: finalLines.length,
    diff_hunk: {
      header: `@@ -${startLine},${removedLines.length} +${startLine},${action === 'delete' ? 0 : newLineCount} @@`,
      lines: [
        ...removedLines.map(l => ({ type: 'del', text: l })),
        ...(action !== 'delete' ? newLines.map(l => ({ type: 'add', text: l })) : [])
      ],
      stats: { added: action === 'delete' ? 0 : newLineCount, removed: removedLines.length }
    }
  };
};
