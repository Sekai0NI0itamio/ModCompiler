// src/main/functions/chat/helpers/toolRenders/completed/generic.js
// Generic renderer for unknown/unhandled tools

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildToolLine, buildSubInfo } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  const toolName = options?.toolName || 'Tool';
  
  const hasError = !!(result?.error);
  const success = result?.success !== false && !hasError;
  const message = result?.message;
  const path = result?.path || input?.path;
  
  const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
  
  const colParts = [];
  
  if (path) {
    colParts.push(`<div class="tool-file" style="white-space:normal;word-break:break-word;overflow-wrap:anywhere;">${escapeHtmlLite(String(path))}</div>`);
  }
  
  if (hasError) {
    colParts.push(buildSubInfo(result.error, '#b00020'));
  } else if (message) {
    colParts.push(buildSubInfo(String(message).slice(0, 200)));
  } else if (success) {
    colParts.push(buildSubInfo('Completed', '#1a7f37'));
  } else {
    colParts.push(buildSubInfo('Processing...'));
  }
  
  if (colParts.length === 0) {
    colParts.push(buildSubInfo(success ? 'Completed' : 'Processing...'));
  }
  
  lines.push(buildToolLine(icon, colParts));
  return lines.join('');
}

function renderError(toolName, error) {
  const errorMsg = error?.message || String(error);
  return `<div class="tool-line">${failIconSvg()}<span class="tool-sub" style="margin-left:6px;color:#b00020;">Render error: ${escapeHtmlLite(errorMsg.slice(0, 100))}</span></div>`;
}

module.exports = { render, renderError };
