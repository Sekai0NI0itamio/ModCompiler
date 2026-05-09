// Executing state render for web_fetch tool
const { spinnerIconSvg, buildToolLine, buildSubInfo, escapeHtmlLite } = require('../shared');

module.exports = function renderWebFetchExecuting(input) {
  const url = (input && input.url) || '';
  const mode = (input && input.mode) || 'truncated';
  
  // Truncate URL for display
  const displayUrl = url.length > 60 ? url.substring(0, 57) + '...' : url;
  
  const colParts = [
    `<div class="tool-file" style="white-space:normal;word-break:break-all;overflow-wrap:anywhere;" title="${escapeHtmlLite(url)}">${escapeHtmlLite(displayUrl)}</div>`,
    buildSubInfo(`Fetching (${mode})...`)
  ];
  
  return buildToolLine(spinnerIconSvg(), colParts);
};
