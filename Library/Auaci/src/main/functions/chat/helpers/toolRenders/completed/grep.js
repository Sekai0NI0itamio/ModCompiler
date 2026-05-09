// src/main/functions/chat/helpers/toolRenders/completed/grep.js
// Renderer for completed grep/context_search tools

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildToolLine } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const basePath = result?.path || result?.summary?.path || input?.path || '.';
  const patterns = result?.patterns || result?.summary?.patterns || input?.queries || [];
  const patternStats = result?.pattern_stats || result?.summary?.pattern_stats || [];
  const foundMap = result?.summary?.found_by_pattern || {};
  
  // Header line
  lines.push(`<div class="tool-line" style="font-weight:600;">Searched in "${escapeHtmlLite(String(basePath))}"</div>`);
  
  if (patterns.length > 0) {
    for (const pat of patterns) {
      let status = null;
      
      // Try to determine status from various sources
      if (foundMap && typeof foundMap === 'object' && Object.prototype.hasOwnProperty.call(foundMap, pat)) {
        status = coerceBool(foundMap[pat]);
      } else if (patternStats.length > 0) {
        const stat = patternStats.find(s => s && String(s.pattern) === String(pat));
        if (stat && typeof stat.matched !== 'undefined') {
          status = coerceBool(stat.matched);
        }
      }
      
      const icon = status === true ? okIconSvg() : (status === false ? failIconSvg() : pendingIconSvg());
      lines.push(`<div class="tool-line">${icon}<span class="tool-file">${escapeHtmlLite(String(pat))}</span></div>`);
    }
  } else if (result?.matched_files_count !== undefined) {
    // Fallback: show count if no patterns
    const count = result.matched_files_count;
    const icon = count > 0 ? okIconSvg() : failIconSvg();
    lines.push(`<div class="tool-line">${icon}<span class="tool-file">${count} files matched</span></div>`);
  }
  
  return lines.join('');
}

function coerceBool(v) {
  if (v === null || typeof v === 'undefined') return null;
  if (typeof v === 'boolean') return v;
  if (typeof v === 'number') return v === 1 ? true : (v === 0 ? false : null);
  if (typeof v === 'string') {
    const s = v.trim().toLowerCase();
    if (s === 'true' || s === '1' || s === 'yes') return true;
    if (s === 'false' || s === '0' || s === 'no') return false;
  }
  return null;
}

module.exports = { render };
