// src/main/functions/chat/helpers/toolRenders/completed/view_group.js
// Renderer for grouped view tools - renders multiple view tools in a single compact box

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildFilePath, buildSubInfo } = require('../shared');

/**
 * Render a grouped view box containing multiple view tool results
 * Format:
 * tick | file name\path | Lines read
 * tick | file name\path | Lines read
 * ...
 * 
 * @param {Array} viewTools - Array of { input, result } objects for each view tool
 * @param {object} options - Additional options
 * @returns {string} - HTML string for the grouped view box
 */
function renderGrouped(viewTools, options = {}) {
  if (!Array.isArray(viewTools) || viewTools.length === 0) {
    return '';
  }
  
  const lines = [];
  
  for (const tool of viewTools) {
    const { input, result } = tool;
    const filePath = input?.path || result?.path || '';
    const hasError = !!(result?.error);
    const success = !hasError && result?.success !== false;
    
    const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
    
    // Build the line info
    let lineInfo = '';
    if (hasError) {
      lineInfo = `<span class="tool-sub" style="color:#b00020;">${escapeHtmlLite(result.error)}</span>`;
    } else if (result?.displayed_lines !== undefined) {
      const rangeInfo = result?.range_info || `${result.displayed_lines} lines`;
      lineInfo = `<span class="tool-sub">Lines Read: ${escapeHtmlLite(rangeInfo)}</span>`;
    } else if (result?.total_line_count !== undefined) {
      lineInfo = `<span class="tool-sub">${result.total_line_count} lines total</span>`;
    } else {
      lineInfo = `<span class="tool-sub">Read</span>`;
    }
    
    lines.push(`
      <div class="view-group-line">
        <span class="view-group-icon">${icon}</span>
        <span class="view-group-path">${escapeHtmlLite(filePath)}</span>
        <span class="view-group-info">${lineInfo}</span>
      </div>
    `);
  }
  
  return `<div class="view-group-body">${lines.join('')}</div>`;
}

/**
 * Create a complete grouped view box element
 * @param {Array} viewTools - Array of { name, input, result } objects
 * @returns {HTMLElement} - The grouped view box element
 */
function createGroupedViewBox(viewTools) {
  const box = document.createElement('div');
  box.className = 'auaci-tool-box view-group-box';
  box.setAttribute('data-tool', 'view_group');
  box.setAttribute('data-tool-count', String(viewTools.length));
  
  // Header
  const header = document.createElement('div');
  header.className = 'tool-header';
  
  const nameSpan = document.createElement('span');
  nameSpan.className = 'auaci-tool-name';
  nameSpan.textContent = `View (${viewTools.length} files)`;
  header.appendChild(nameSpan);
  
  // Body
  const body = document.createElement('div');
  body.className = 'tool-body';
  body.innerHTML = renderGrouped(viewTools);
  
  box.appendChild(header);
  box.appendChild(body);
  
  return box;
}

/**
 * Check if a tool is a view-type tool that can be grouped
 * @param {string} toolName - The tool name
 * @returns {boolean}
 */
function isViewTool(toolName) {
  const name = String(toolName || '').toLowerCase().trim();
  return name === 'view' || name === 'read_file' || name === 'editor';
}

module.exports = {
  renderGrouped,
  createGroupedViewBox,
  isViewTool
};
