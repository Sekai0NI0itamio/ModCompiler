// Executing state render for web_search tool
const { spinnerIconSvg, buildToolLine, buildSubInfo, escapeHtmlLite } = require('../shared');

module.exports = function renderWebSearchExecuting(input) {
  const query = (input && input.query) || '';
  const numResults = (input && input.num_results) || 10;
  
  // Search icon with spinner effect
  const searchSpinnerSvg = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style="animation: tool-spin 1s linear infinite;"><circle cx="11" cy="11" r="7" stroke="#3b82f6" stroke-width="2"/><path d="M16 16l4 4" stroke="#3b82f6" stroke-width="2" stroke-linecap="round"/></svg>';
  
  const colParts = [
    `<div class="tool-file" style="white-space:normal;word-break:break-word;overflow-wrap:anywhere;font-weight:500;">"${escapeHtmlLite(query)}"</div>`,
    buildSubInfo(`Searching for ${numResults} results...`)
  ];
  
  return buildToolLine(searchSpinnerSvg, colParts);
};
