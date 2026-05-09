// Generic executing state render for unknown tools
const { spinnerIconSvg, escapeHtmlLite } = require('../shared');

module.exports = function renderGenericExecuting(input, toolName) {
  const name = toolName || 'tool';
  return `<div class="tool-line">${spinnerIconSvg()}<span class="tool-cmd" style="margin-left:6px;">${escapeHtmlLite(name)}</span><span style="margin-left:8px;color:#6b7280;">Executing...</span></div>`;
};
