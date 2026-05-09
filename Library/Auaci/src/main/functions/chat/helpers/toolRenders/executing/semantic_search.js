// Executing state render for semantic_search tool
const { spinnerIconSvg, escapeHtmlLite } = require('../shared');

module.exports = function renderSemanticSearchExecuting(input) {
  const path = (input && input.path) || (input && input.paths && input.paths[0]) || '.';
  const query = (input && input.query) || '';
  const queries = (input && input.queries) || [];
  const maxResults = (input && input.max_results) || 10;
  
  const searchQueries = query ? [query] : (queries.length > 0 ? queries : []);
  
  const lines = [];
  lines.push(`<div class="tool-line" style="font-weight:600;">Semantic searching in "${escapeHtmlLite(String(path))}"</div>`);
  
  if (searchQueries.length > 0) {
    for (const q of searchQueries) {
      lines.push(`<div class="tool-line">${spinnerIconSvg()}<span class="tool-file" style="margin-left:6px;">${escapeHtmlLite(String(q))}</span></div>`);
    }
  } else {
    lines.push(`<div class="tool-line">${spinnerIconSvg()}<span style="margin-left:6px;color:#6b7280;">Semantic searching...</span></div>`);
  }
  
  if (maxResults !== undefined) {
    lines.push(`<div class="tool-line" style="color:#6b7280;">Max results: ${escapeHtmlLite(String(maxResults))}</div>`);
  }
  
  return lines.join('');
};