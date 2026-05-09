// Executing state render for find_files tool
const { spinnerIconSvg, escapeHtmlLite } = require('../shared');

module.exports = function renderFindFilesExecuting(input) {
  const patterns = input && Array.isArray(input.patterns) ? input.patterns : [];
  
  const lines = [];
  lines.push(`<div class="tool-line" style="font-weight:600;">Looking for files</div>`);
  
  if (patterns.length > 0) {
    for (const pat of patterns) {
      lines.push(`<div class="tool-line">${spinnerIconSvg()}<span class="tool-file" style="margin-left:6px;">${escapeHtmlLite(String(pat))}</span></div>`);
    }
  } else {
    lines.push(`<div class="tool-line">${spinnerIconSvg()}<span style="margin-left:6px;color:#6b7280;">Searching...</span></div>`);
  }
  
  return lines.join('');
};
