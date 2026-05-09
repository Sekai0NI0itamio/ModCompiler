// Executing state render for glob tool
const { spinnerIconSvg, escapeHtmlLite } = require('../shared');

module.exports = function renderGlobExecuting(input) {
  const pattern = (input && input.pattern) || '';
  
  const lines = [];
  lines.push(`<div class="tool-line" style="font-weight:600;">Matching files</div>`);
  
  if (pattern) {
    lines.push(`<div class="tool-line">${spinnerIconSvg()}<span class="tool-file" style="margin-left:6px;">${escapeHtmlLite(String(pattern))}</span></div>`);
  } else {
    lines.push(`<div class="tool-line">${spinnerIconSvg()}<span style="margin-left:6px;color:#6b7280;">Searching...</span></div>`);
  }
  
  return lines.join('');
};
