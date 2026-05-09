// src/main/functions/chat/helpers/toolRenders/completed/fs_append.js
// Renderer for completed fs_append tool

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const filePath = input?.path || result?.path || '';
  const hasError = !!(result?.error);
  const success = result?.success !== false && !hasError;
  const bytesAppended = result?.bytes_appended;
  
  const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
  
  const colParts = [buildFilePath(filePath)];
  
  if (hasError) {
    colParts.push(buildSubInfo(result.error, '#b00020'));
  } else if (success) {
    if (bytesAppended !== undefined) {
      colParts.push(buildSubInfo(`${bytesAppended} bytes appended`, '#1a7f37'));
    } else {
      colParts.push(buildSubInfo('Content appended', '#1a7f37'));
    }
  } else {
    colParts.push(buildSubInfo('Appending...'));
  }
  
  lines.push(buildToolLine(icon, colParts));
  return lines.join('');
}

module.exports = { render };
