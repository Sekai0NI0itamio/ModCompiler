// Executing state render for todo tools
const { spinnerIconSvg, buildSubInfo } = require('../shared');

module.exports = function renderTodoExecuting(input) {
  return `<div class="tool-line">${spinnerIconSvg()}<span style="margin-left:6px;color:#6b7280;">Loading todos...</span></div>`;
};
