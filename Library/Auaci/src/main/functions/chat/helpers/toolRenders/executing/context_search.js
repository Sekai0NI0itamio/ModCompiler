// Executing state render for context_search tool
const { spinnerIconSvg, escapeHtmlLite } = require('../shared');

module.exports = function renderContextSearchExecuting(input) {
  const queries = (input && Array.isArray(input.queries)) ? input.queries : [];
  
  const lines = [];
  
  if (queries.length > 0) {
    for (const q of queries) {
      lines.push(`<div class="tool-line">${spinnerIconSvg()}<span class="tool-file" style="margin-left:6px;">${escapeHtmlLite(String(q))}</span></div>`);
    }
  } else {
    lines.push(`<div class="tool-line">${spinnerIconSvg()}<span style="margin-left:6px;color:#6b7280;">Searching context...</span></div>`);
  }
  
  return lines.join('');
};
