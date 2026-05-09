// src/main/functions/chat/helpers/toolRenders/completed/semantic_search.js
// Renderer for completed semantic_search tool

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildToolLine } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const path = result?.path || result?.summary?.path || input?.path || '.';
  const query = result?.query || result?.summary?.query || input?.query || '';
  const queries = result?.queries || result?.summary?.queries || input?.queries || [];
  const maxResults = result?.max_results || result?.summary?.max_results || input?.max_results || 10;
  const results = result?.results || result?.summary?.results || [];
  
  const searchQueries = query ? [query] : (queries.length > 0 ? queries : []);
  
  // Header line
  lines.push(`<div class="tool-line" style="font-weight:600;">Semantic searched in "${escapeHtmlLite(String(path))}"</div>`);
  
  if (searchQueries.length > 0) {
    for (const q of searchQueries) {
      const icon = results && results.length > 0 ? okIconSvg() : failIconSvg();
      lines.push(`<div class="tool-line">${icon}<span class="tool-file">${escapeHtmlLite(String(q))}</span></div>`);
    }
  }
  
  // Show result count
  if (results && Array.isArray(results)) {
    const resultCount = results.length;
    const icon = resultCount > 0 ? okIconSvg() : failIconSvg();
    lines.push(`<div class="tool-line">${icon}<span class="tool-file">${resultCount} results found</span></div>`);
    
    // Show top results (up to 3)
    if (resultCount > 0) {
      const topResults = results.slice(0, 3);
      for (const res of topResults) {
        const filePath = res.file_path || res.path || '';
        const score = res.score !== undefined ? ` (score: ${res.score.toFixed(2)})` : '';
        lines.push(`<div class="tool-line" style="margin-left:20px;color:#6b7280;">${escapeHtmlLite(filePath)}${escapeHtmlLite(score)}</div>`);
      }
      if (resultCount > 3) {
        lines.push(`<div class="tool-line" style="margin-left:20px;color:#6b7280;">... and ${resultCount - 3} more</div>`);
      }
    }
  } else if (result?.error) {
    lines.push(`<div class="tool-line">${failIconSvg()}<span class="tool-file">Error: ${escapeHtmlLite(String(result.error))}</span></div>`);
  }
  
  return lines.join('');
}

module.exports = { render };