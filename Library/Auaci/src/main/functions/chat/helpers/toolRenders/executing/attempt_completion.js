// Executing state render for attempt_completion tool
const { spinnerIconSvg } = require('../shared');

module.exports = function renderAttemptCompletionExecuting(input) {
  return `
<div class="tool-line">
  ${spinnerIconSvg()}
  <span style="margin-left:6px;color:#6b7280;">Generating summary...</span>
</div>`;
};
