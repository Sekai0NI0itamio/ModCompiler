// src/main/functions/chat/helpers/toolRenders/completed/str_replace.js
// Renderer for completed str_replace tool

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const filePath = input?.path || result?.path || '';
  const hasError = !!(result?.error);
  const success = result?.success !== false && !hasError;
  const replacementsMade = result?.replacements_made;
  
  const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
  
  const colParts = [buildFilePath(filePath)];
  
  if (hasError) {
    colParts.push(buildSubInfo(result.error, '#b00020'));
  } else if (success) {
    if (replacementsMade !== undefined) {
      colParts.push(buildSubInfo(`${replacementsMade} replacement${replacementsMade !== 1 ? 's' : ''} made`, '#1a7f37'));
    } else {
      colParts.push(buildSubInfo('Replaced', '#1a7f37'));
    }
  } else {
    colParts.push(buildSubInfo('Replacing...'));
  }
  
  lines.push(buildToolLine(icon, colParts));
  return lines.join('');
}

module.exports = { render };
