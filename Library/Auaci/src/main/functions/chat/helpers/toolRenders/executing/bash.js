// Executing state render for bash/run_command tool
const { spinnerIconSvg, escapeHtmlLite } = require('../shared');

module.exports = function renderBashExecuting(input) {
  const command = (input && (input.command || input.cmd)) || '';
  
  // Truncate long commands
  const displayCmd = command.length > 80 ? command.substring(0, 77) + '...' : command;
  
  return `
<div class="tool-line">
  <span class="tool-cmd" style="font-family:ui-monospace,monospace;background:#1e1e1e;color:#d4d4d4;padding:4px 8px;border-radius:4px;">${escapeHtmlLite(displayCmd)}</span>
</div>
<div class="tool-line" style="margin-top:4px;">
  ${spinnerIconSvg()}
  <span style="margin-left:6px;color:#6b7280;">Running command...</span>
</div>`;
};
