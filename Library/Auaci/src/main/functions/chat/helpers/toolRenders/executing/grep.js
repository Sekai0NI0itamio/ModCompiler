// Executing state render for grep tool
const { spinnerIconSvg, escapeHtmlLite } = require('../shared');

module.exports = function renderGrepExecuting(input) {
  const path = (input && input.path) || '.';
  const queries = input && Array.isArray(input.queries) ? input.queries : [];
  const pattern = input && input.pattern ? input.pattern : '';
  
  const patterns = queries.length > 0 ? queries : (pattern ? [pattern] : []);
  
  const lines = [];
  lines.push(`<div class="tool-line" style="font-weight:600;">Searching in "${escapeHtmlLite(String(path))}"</div>`);
  
  if (patterns.length > 0) {
    for (const pat of patterns) {
      lines.push(`<div class="tool-line">${spinnerIconSvg()}<span class="tool-file" style="margin-left:6px;">${escapeHtmlLite(String(pat))}</span></div>`);
    }
  } else {
    lines.push(`<div class="tool-line">${spinnerIconSvg()}<span style="margin-left:6px;color:#6b7280;">Searching...</span></div>`);
  }
  
  return lines.join('');
};
