// src/main/functions/chat/helpers/toolRenders/completed/get_function.js
// Renderer for completed get_function tool

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const filePath = input?.path || result?.path || '';
  const functionName = input?.function_name || result?.function_name || '';
  const hasError = !!(result?.error);
  const success = result?.success !== false && !hasError;
  const startLine = result?.start_line;
  const endLine = result?.end_line;
  const lineCount = result?.line_count;
  
  const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
  
  const colParts = [buildFilePath(filePath)];
  
  if (functionName) {
    colParts.push(buildSubInfo(`Function: ${functionName}`));
  }
  
  if (hasError) {
    colParts.push(buildSubInfo(result.error, '#b00020'));
  } else if (success) {
    if (startLine !== undefined && endLine !== undefined) {
      colParts.push(buildSubInfo(`Lines ${startLine}-${endLine} (${lineCount || (endLine - startLine + 1)} lines)`));
    } else if (lineCount !== undefined) {
      colParts.push(buildSubInfo(`${lineCount} lines extracted`));
    } else {
      colParts.push(buildSubInfo('Function extracted', '#1a7f37'));
    }
  } else {
    colParts.push(buildSubInfo('Extracting...'));
  }
  
  lines.push(buildToolLine(icon, colParts));
  return lines.join('');
}

module.exports = { render };
