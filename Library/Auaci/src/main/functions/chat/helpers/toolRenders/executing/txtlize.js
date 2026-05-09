// Executing state render for txtlize tool
const { spinnerIconSvg, escapeHtmlLite } = require('../shared');

module.exports = function renderTxtlizeExecuting(input) {
  const name = (input && input.name) || '';
  
  return `
<div class="tool-line" style="font-weight:600;">Txtlizing${name ? ` - "${escapeHtmlLite(name)}"` : ''}</div>
<div class="tool-line">${spinnerIconSvg()}<span style="margin-left:6px;color:#6b7280;">Processing files...</span></div>`;
};
