// src/main/functions/chat/helpers/toolRenders/completed/edit_lines.js
// Renderer for completed edit_lines tool

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const filePath = input?.path || result?.path || '';
  const startLine = input?.start_line;
  const endLine = input?.end_line;
  const hasError = !!(result?.error);
  const success = result?.success !== false && !hasError;
  const linesChanged = result?.lines_changed;
  
  const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
  
  const colParts = [buildFilePath(filePath)];
  
  if (startLine !== undefined && endLine !== undefined) {
    colParts.push(buildSubInfo(`Lines ${startLine}-${endLine}`));
  }
  
  if (hasError) {
    colParts.push(buildSubInfo(result.error, '#b00020'));
  } else if (success) {
    if (linesChanged !== undefined) {
      colParts.push(buildSubInfo(`${linesChanged} lines changed`, '#1a7f37'));
    } else {
      colParts.push(buildSubInfo('Lines edited', '#1a7f37'));
    }
  } else {
    colParts.push(buildSubInfo('Editing...'));
  }
  
  lines.push(buildToolLine(icon, colParts));
  return lines.join('');
}

module.exports = { render };
