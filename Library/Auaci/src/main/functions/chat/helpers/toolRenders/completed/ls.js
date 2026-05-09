// src/main/functions/chat/helpers/toolRenders/completed/ls.js
// Renderer for completed ls (directory listing) tool

const { escapeHtmlLite, okIconSvg, failIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const filePath = result?.path || result?.summary?.base_path || result?.summary?.path || input?.path || '';
  const hasError = !!(result?.error);
  
  // Get entries count from various possible locations
  const filesCount = result?.entriesCount 
    || result?.summary?.total_items 
    || (Array.isArray(result?.entries) ? result.entries.length : 0);
  
  const depth = result?.max_depth || result?.summary?.max_depth || input?.max_depth;
  
  const icon = hasError ? failIconSvg() : okIconSvg();
  
  const colParts = [buildFilePath(filePath)];
  
  if (hasError) {
    colParts.push(buildSubInfo(result.error, '#b00020'));
  } else {
    colParts.push(buildSubInfo(`Found ${filesCount} files`));
    if (depth != null && !isNaN(depth)) {
      colParts.push(buildSubInfo(`Search depth ${depth}`));
    }
  }
  
  lines.push(buildToolLine(icon, colParts));
  return lines.join('');
}

module.exports = { render };
