// src/main/functions/chat/helpers/toolRenders/completed/apply_patch.js
// Renderer for completed apply_patch tool

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const applied = Array.isArray(result?.applied) ? result.applied : [];
  const errorCount = Number(result?.error_count || 0);
  const hasErrorFlag = !!(result?.error);
  
  if (applied.length > 0) {
    for (const ap of applied) {
      const f = ap?.file_path ? String(ap.file_path) : '';
      
      let added = 0;
      let removed = 0;
      
      // Get line counts from various sources
      if (ap?.inserted_line_count !== undefined) added = Number(ap.inserted_line_count);
      if (ap?.deleted_line_count !== undefined) removed = Number(ap.deleted_line_count);
      
      // Fallback: parse from replaced object
      if (added === 0 && ap?.replaced?.inserted_line_count !== undefined) {
        added = Number(ap.replaced.inserted_line_count);
      }
      if (removed === 0 && ap?.replaced?.deleted_line_count !== undefined) {
        removed = Number(ap.replaced.deleted_line_count);
      }
      
      // Fallback: parse from message
      if (added === 0 && removed === 0 && ap?.message) {
        const msg = String(ap.message);
        const deletedMatch = msg.match(/deleted (\d+) lines?/);
        const insertedMatch = msg.match(/inserted (\d+) lines?/);
        const replacedMatch = msg.match(/replaced (\d+) lines? with (\d+)/);
        if (deletedMatch) removed = Number(deletedMatch[1]);
        if (insertedMatch) added = Number(insertedMatch[1]);
        if (replacedMatch) {
          removed = Number(replacedMatch[1]);
          added = Number(replacedMatch[2]);
        }
      }
      
      const autoAligned = (typeof ap?.auto_aligned_to_line === 'number' && ap.auto_aligned_to_line > 0) 
        ? ap.auto_aligned_to_line 
        : null;
      
      lines.push(`<div class="tool-line"><span class="tool-file">File: ${escapeHtmlLite(f)}</span>${autoAligned ? `<span class="tool-sub" style="margin-left:8px;color:#6b7280;">auto-aligned to line ${autoAligned}</span>` : ''}</div>`);
      lines.push(`<div class="tool-line" style="color:#1a7f37;">+ ${added}</div>`);
      lines.push(`<div class="tool-line" style="color:#b00020;">- ${removed}</div>`);

      if (ap?.diff_preview) {
        lines.push(
          `<details class="tool-line" style="margin-left:4px;">` +
          `<summary style="cursor:pointer;color:#2563eb;">View diff preview</summary>` +
          `<pre style="white-space:pre-wrap;background:#0b1020;color:#e5e7eb;padding:8px;border-radius:6px;margin-top:6px;">` +
          `${escapeHtmlLite(String(ap.diff_preview))}` +
          `</pre>` +
          `</details>`
        );
      }
    }
  } else if (errorCount > 0 || hasErrorFlag || result?.success === false) {
    lines.push(`<div class="tool-line" style="font-weight:600; color:#b00020;">Patch failed</div>`);
    
    if (Array.isArray(result?.errors) && result.errors.length > 0) {
      const e0 = result.errors[0] || {};
      const ef = e0.file_path ? ` (${escapeHtmlLite(String(e0.file_path))})` : '';
      if (e0.error) {
        lines.push(`<div class="tool-line">${escapeHtmlLite(String(e0.error))}${ef}</div>`);
      }
    } else if (typeof result?.error === 'string') {
      lines.push(`<div class="tool-line">${escapeHtmlLite(result.error)}</div>`);
    }
  } else {
    lines.push(`<div class="tool-line">${pendingIconSvg()}<span style="margin-left:6px;">Patch pending</span></div>`);
  }
  
  return lines.join('');
}

module.exports = { render };
