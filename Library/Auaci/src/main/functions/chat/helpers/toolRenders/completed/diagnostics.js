// src/main/functions/chat/helpers/toolRenders/completed/diagnostics.js
// Renderer for completed diagnostics tool

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const paths = input?.paths || [];
  const diagnostics = Array.isArray(result?.diagnostics) ? result.diagnostics : [];
  const totalIssues = result?.total_issues || diagnostics.length;
  const hasError = !!(result?.error);
  
  // Header
  const pathsStr = paths.length > 0 ? paths.slice(0, 3).join(', ') + (paths.length > 3 ? '...' : '') : 'files';
  lines.push(`<div class="tool-line" style="font-weight:600;">Diagnostics for ${escapeHtmlLite(pathsStr)}</div>`);
  
  if (hasError) {
    lines.push(`<div class="tool-line">${failIconSvg()}<span class="tool-sub" style="margin-left:6px;color:#b00020;">${escapeHtmlLite(result.error)}</span></div>`);
    return lines.join('');
  }
  
  if (totalIssues === 0) {
    lines.push(`<div class="tool-line">${okIconSvg()}<span class="tool-sub" style="margin-left:6px;color:#1a7f37;">No issues found</span></div>`);
    return lines.join('');
  }
  
  // Group by severity
  const errors = diagnostics.filter(d => d?.severity === 'error');
  const warnings = diagnostics.filter(d => d?.severity === 'warning');
  const others = diagnostics.filter(d => d?.severity !== 'error' && d?.severity !== 'warning');
  
  // Summary line
  const summaryParts = [];
  if (errors.length > 0) summaryParts.push(`${errors.length} error${errors.length > 1 ? 's' : ''}`);
  if (warnings.length > 0) summaryParts.push(`${warnings.length} warning${warnings.length > 1 ? 's' : ''}`);
  if (others.length > 0) summaryParts.push(`${others.length} other`);
  
  const icon = errors.length > 0 ? failIconSvg() : (warnings.length > 0 ? pendingIconSvg() : okIconSvg());
  lines.push(`<div class="tool-line">${icon}<span class="tool-sub" style="margin-left:6px;">${summaryParts.join(', ')}</span></div>`);
  
  // Show first few issues
  const displayDiagnostics = diagnostics.slice(0, 5);
  for (const d of displayDiagnostics) {
    const file = d?.file || '';
    const line = d?.line || 0;
    const message = d?.message || '';
    const severity = d?.severity || 'info';
    
    const color = severity === 'error' ? '#b00020' : (severity === 'warning' ? '#d97706' : '#6b7280');
    const location = file ? `${file}:${line}` : '';
    
    lines.push(
      `<div class="tool-line" style="margin-left:20px;">` +
      `<div class="tool-col" style="display:flex;flex-direction:column;gap:1px;">` +
        `<div class="tool-sub" style="color:${color};font-size:11px;">${escapeHtmlLite(location)}</div>` +
        `<div class="tool-file" style="font-size:12px;">${escapeHtmlLite(String(message).slice(0, 100))}</div>` +
      `</div>` +
      `</div>`
    );
  }
  
  if (totalIssues > 5) {
    lines.push(`<div class="tool-line"><span class="tool-sub" style="color:#6b7280;">...and ${totalIssues - 5} more issues</span></div>`);
  }
  
  return lines.join('');
}

module.exports = { render };
