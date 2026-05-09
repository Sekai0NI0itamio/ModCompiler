// src/main/functions/chat/helpers/toolRenders/completed/find_files.js
// Renderer for completed find_files tool

const { escapeHtmlLite, okIconSvg, failIconSvg } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const patterns = result?.patterns || input?.patterns || [];
  const files = Array.isArray(result?.files) ? result.files : [];
  
  const hasWildcards = (p) => /[\*\?\[]/.test(String(p || ''));
  const basename = (p) => {
    try { return String(p).split(/\\|\//).pop(); } catch (_) { return String(p || ''); }
  };
  
  // Build mapping from pattern -> found file names
  const matchedByPattern = new Map();
  for (const pat of patterns) matchedByPattern.set(pat, []);
  
  if (patterns.length === 0) {
    // No patterns provided, just list found files
    lines.push(`<div class="tool-line" style="font-weight:600;">Looked for Files:</div>`);
    for (const f of files) {
      const label = f?.fileName || f?.fullPath || '';
      lines.push(`<div class="tool-line">${okIconSvg()}<span class="tool-file">${escapeHtmlLite(String(label))}</span></div>`);
    }
    if (files.length === 0) {
      lines.push(`<div class="tool-line">${failIconSvg()}<span class="tool-file">No files found</span></div>`);
    }
  } else {
    // Match files to patterns
    for (const f of files) {
      const fn = String(f?.fileName || '');
      for (const pat of patterns) {
        if (hasWildcards(pat)) {
          const b = basename(pat);
          if (b === fn || (b && fn.includes(b.replace(/[\*\?\[\]]/g, '')))) {
            matchedByPattern.get(pat)?.push(fn);
          }
        } else {
          if (basename(pat) === fn) matchedByPattern.get(pat)?.push(fn);
        }
      }
    }
    
    lines.push(`<div class="tool-line" style="font-weight:600;">Looked for Files:</div>`);
    
    for (const pat of patterns) {
      const matches = matchedByPattern.get(pat) || [];
      if (hasWildcards(pat)) {
        if (matches.length > 0) {
          for (const m of matches) {
            lines.push(`<div class="tool-line">${okIconSvg()}<span class="tool-file">${escapeHtmlLite(String(m))}</span></div>`);
          }
        } else {
          lines.push(`<div class="tool-line">${failIconSvg()}<span class="tool-file">${escapeHtmlLite(String(pat))}</span></div>`);
        }
      } else {
        const label = basename(pat);
        const found = matches.length > 0;
        const icon = found ? okIconSvg() : failIconSvg();
        lines.push(`<div class="tool-line">${icon}<span class="tool-file">${escapeHtmlLite(String(label))}</span></div>`);
      }
    }
  }
  
  return lines.join('');
}

module.exports = { render };
