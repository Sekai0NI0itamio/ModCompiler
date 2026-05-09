// src/main/functions/chat/helpers/toolRenders/completed/web_search.js
// Renderer for completed web_search tool

const { escapeHtmlLite, okIconSvg, failIconSvg, buildToolLine, buildSubInfo } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const query = input?.query || result?.query || '';
  const results = Array.isArray(result?.results) ? result.results : [];
  const totalResults = result?.total_results || results.length;
  const hasError = !!(result?.error);
  
  // Header with query
  lines.push(`<div class="tool-line" style="font-weight:600;">Search: "${escapeHtmlLite(String(query))}"</div>`);
  
  if (hasError) {
    lines.push(`<div class="tool-line">${failIconSvg()}<span class="tool-sub" style="margin-left:6px;color:#b00020;">${escapeHtmlLite(result.error)}</span></div>`);
    return lines.join('');
  }
  
  if (results.length === 0) {
    lines.push(`<div class="tool-line">${failIconSvg()}<span class="tool-sub" style="margin-left:6px;color:#6b7280;">No results found</span></div>`);
    return lines.join('');
  }
  
  // Show results (limited to 5 for display)
  const displayResults = results.slice(0, 5);
  for (const r of displayResults) {
    const title = r?.title || 'Untitled';
    const url = r?.url || '';
    
    lines.push(
      `<div class="tool-line">${okIconSvg()}` +
      `<div class="tool-col" style="display:flex;flex-direction:column;gap:2px;margin-left:6px;">` +
        `<div class="tool-file" style="white-space:normal;word-break:break-word;overflow-wrap:anywhere;">${escapeHtmlLite(String(title))}</div>` +
        `<div class="tool-sub" style="color:#6b7280;font-size:11px;">${escapeHtmlLite(String(url))}</div>` +
      `</div>` +
      `</div>`
    );
  }
  
  if (totalResults > 5) {
    lines.push(`<div class="tool-line"><span class="tool-sub" style="color:#6b7280;">...and ${totalResults - 5} more results</span></div>`);
  }
  
  return lines.join('');
}

module.exports = { render };
