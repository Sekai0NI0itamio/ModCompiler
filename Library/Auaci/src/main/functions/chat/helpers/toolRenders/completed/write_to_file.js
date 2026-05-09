// src/main/functions/chat/helpers/toolRenders/completed/write_to_file.js
// Renderer for completed write_to_file and create_file tools

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildToolLine } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  const isCreate = options?.isCreate;
  
  const filePath = input?.path || result?.path || '';
  const hasError = !!(result?.error);
  const success = result?.success !== false && !hasError;
  
  const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
  
  const colParts = [];
  colParts.push(`<div class="tool-file" style="white-space:normal;word-break:break-word;overflow-wrap:anywhere;">${escapeHtmlLite(String(filePath))}</div>`);
  
  if (success) {
    colParts.push(`<div class="tool-sub" style="color:#1a7f37;">${isCreate ? 'File created' : 'File written'}</div>`);
  } else if (hasError) {
    colParts.push(`<div class="tool-sub" style="color:#b00020;">${escapeHtmlLite(String(result.error || 'Failed'))}</div>`);
  } else {
    colParts.push(`<div class="tool-sub" style="color:#6b7280;">${isCreate ? 'Creating...' : 'Writing...'}</div>`);
  }
  
  lines.push(buildToolLine(icon, colParts));
  return lines.join('');
}

module.exports = { render };
