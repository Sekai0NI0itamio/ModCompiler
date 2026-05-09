// src/main/functions/chat/helpers/toolRenders/completed/bash.js
// Renderer for completed bash/run_command tools

const { escapeHtmlLite } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  const finished = result?.finished || {};
  
  const cmd = finished.command || input?.command || '';
  const cwd = finished.new_pwd || '';
  const rawOutput = finished.output || result?.output_preview || '';
  
  // Get base directory name for prompt
  const baseDir = (() => {
    try {
      const parts = String(cwd || '').split('/').filter(Boolean);
      return parts.length ? parts[parts.length - 1] : cwd || '.';
    } catch (_) {
      return cwd || '.';
    }
  })();
  
  const prompt = `${escapeHtmlLite(baseDir)} % ${escapeHtmlLite(String(cmd))}`;
  
  // Handle output display
  // No truncation here - gptCycle.js handles 15KB byte-based truncation
  // Display the full output that was returned by the tool
  let displayOutput = rawOutput;
  let totalLines = finished.total_lines;
  let displayedLines = finished.displayed_lines;
  let truncated = finished.truncated;
  
  if (typeof displayOutput === 'string') {
    const linesArr = displayOutput.split(/\r?\n/);
    totalLines = Number.isFinite(totalLines) ? totalLines : linesArr.length;
    displayedLines = Number.isFinite(displayedLines) ? displayedLines : linesArr.length; // Show ALL lines received
    truncated = (typeof truncated === 'boolean') ? truncated : false; // Trust the tool's truncation flag
    displayOutput = linesArr.join('\n'); // Don't slice - show everything
  }
  
  // Only show truncation note if the tool itself marked it as truncated (byte-level in gptCycle.js)
  const truncNote = (rawOutput && rawOutput.includes('[Output Truncated]'))
    ? `<div class="terminal-line" style="white-space:pre; background:#000; color:#fbbf24; margin-top:6px;">⚠️ Output truncated to 15KB - showing most recent results at the top</div>` 
    : '';
  
  const floodNote = (result?.terminated_by_system_flood && result?.flood_notice)
    ? `<div class="terminal-line" style="white-space:pre; background:#000; color:#fbbf24; margin-top:6px;">${escapeHtmlLite(String(result.flood_notice))}</div>` 
    : '';
  
  lines.push(`
<div class="tool-terminal" style="background:#000; color:#fff; margin:0; padding:8px; border-radius:8px; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace; line-height:1.35;">
  <div class="terminal-scroll" style="background:#000; max-height: calc(1.35em * 8); overflow: auto;">
    <div class="terminal-line" style="white-space:pre; background:#000; color:#fff;">${prompt}</div>
    ${displayOutput ? `<pre class="terminal-output" style="white-space:pre; margin:6px 0 0; background:#000; color:#fff;">${escapeHtmlLite(displayOutput)}</pre>` : ''}
    ${truncNote}${floodNote}
  </div>
</div>`);
  
  return lines.join('');
}

module.exports = { render };
