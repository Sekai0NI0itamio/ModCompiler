// src/main/functions/chat/helpers/toolRenders/completed/delete.js
// Renderer for completed delete_file_folder_with_permission tool

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const items = Array.isArray(result?.items) ? result.items : [];
  const mode = String(result?.mode || input?.mode || 'preview').toLowerCase();
  const waiting = !!(result?.waiting === true || mode === 'preview');
  const baseDir = result?.summary?.base_dir || input?.base_dir || '';
  const reason = result?.summary?.reason || input?.reason || '';
  
  const headerLine = waiting ? 'Select files and folders to delete' : 'Delete results';
  lines.push(`<div class="tool-line" style="font-weight:600;">${escapeHtmlLite(headerLine)}</div>`);
  
  if (reason) {
    lines.push(`<div class="tool-line"><span class="tool-sub">Reason:</span> <span class="tool-file">${escapeHtmlLite(String(reason))}</span></div>`);
  }
  if (baseDir) {
    lines.push(`<div class="tool-line"><span class="tool-sub">Base dir:</span> <span class="tool-file">${escapeHtmlLite(String(baseDir))}</span></div>`);
  }
  
  if (!items.length) {
    lines.push('<div class="tool-line"><span class="tool-sub">No files or folders were provided.</span></div>');
    return lines.join('');
  }
  
  for (const it of items) {
    const pathLabel = it?.requested_path || it?.full_path || '';
    const kind = it?.kind === 'directory' ? 'Folder' : (it?.kind === 'file' ? 'File' : 'Item');
    const missing = it?.status === 'missing';
    const selected = (typeof it?.selected === 'boolean') ? !!it.selected : true;
    
    let statusLabel = '';
    let icon = pendingIconSvg();
    
    if (waiting) {
      if (missing) {
        statusLabel = 'Missing (cannot delete)';
        icon = failIconSvg();
      } else if (!selected) {
        statusLabel = 'Will be kept';
        icon = pendingIconSvg();
      } else {
        statusLabel = 'Selected for deletion';
        icon = pendingIconSvg();
      }
    } else {
      if (it?.status === 'deleted') {
        statusLabel = 'Deleted';
        icon = okIconSvg();
      } else if (it?.status === 'skipped' || !selected) {
        statusLabel = 'Kept';
        icon = pendingIconSvg();
      } else if (missing) {
        statusLabel = 'Missing';
        icon = failIconSvg();
      } else if (it?.status === 'error') {
        statusLabel = it?.error ? `Error: ${it.error}` : 'Error';
        icon = failIconSvg();
      } else {
        statusLabel = 'Pending';
        icon = pendingIconSvg();
      }
    }
    
    lines.push(
      `<div class="tool-line">` +
      `${icon}` +
      `<div class="tool-col" style="display:flex;flex-direction:column;gap:2px;margin-left:6px;">` +
        `<div class="tool-file" style="white-space:normal;word-break:break-word;overflow-wrap:anywhere;">${escapeHtmlLite(String(pathLabel))}</div>` +
        `<div class="tool-sub" style="color:#6b7280;">${escapeHtmlLite(kind)} • ${escapeHtmlLite(statusLabel)}</div>` +
      `</div>` +
      `</div>`
    );
  }
  
  return lines.join('');
}

module.exports = { render };
