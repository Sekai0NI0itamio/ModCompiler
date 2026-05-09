// src/main/functions/chat/helpers/toolRenders/completed/glob.js
// Renderer for completed glob tool

const { escapeHtmlLite, okIconSvg, failIconSvg } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const basePath = input?.path || '.';
  const pattern = input?.pattern || '*';
  const matchesCount = result?.matches_count || (Array.isArray(result?.matches) ? result.matches.length : 0);
  const found = matchesCount > 0;
  
  const icon = found ? okIconSvg() : failIconSvg();
  
  lines.push(`<div class="tool-line" style="font-weight:600;">Searched in "${escapeHtmlLite(String(basePath))}"</div>`);
  lines.push(`<div class="tool-line">${icon}<span class="tool-file">${escapeHtmlLite(String(pattern))}</span></div>`);
  
  if (found) {
    lines.push(`<div class="tool-line"><span class="tool-sub" style="color:#6b7280;">${matchesCount} matches found</span></div>`);
  }
  
  return lines.join('');
}

module.exports = { render };
