// src/main/functions/chat/helpers/toolRenders/completed/edit_function.js
// Renderer for completed edit_function tool

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const filePath = input?.path || result?.path || '';
  const functionName = input?.function_name || result?.function_name || '';
  const hasError = !!(result?.error);
  const success = result?.success !== false && !hasError;
  const linesChanged = result?.lines_changed;
  
  const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
  
  const colParts = [buildFilePath(filePath)];
  
  if (functionName) {
    colParts.push(buildSubInfo(`Function: ${functionName}`));
  }
  
  if (hasError) {
    colParts.push(buildSubInfo(result.error, '#b00020'));
  } else if (success) {
    if (linesChanged !== undefined) {
      colParts.push(buildSubInfo(`${linesChanged} lines changed`, '#1a7f37'));
    } else {
      colParts.push(buildSubInfo('Function edited', '#1a7f37'));
    }
  } else {
    colParts.push(buildSubInfo('Editing...'));
  }
  
  lines.push(buildToolLine(icon, colParts));
  return lines.join('');
}

module.exports = { render };
