// src/main/functions/chat/helpers/toolRenders/completed/view.js
// Renderer for completed view/read file tools

const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

/**
 * Render a single file view result
 */
function render(input, result, options = {}) {
  const lines = [];
  // Extract file path - handle both single file (path) and multiple files (paths) cases
  let filePath = input?.path || result?.path || '';
  
  // If no path found, check if we have paths array (for multiple files mode)
  if (!filePath && input?.paths && Array.isArray(input.paths) && input.paths.length > 0) {
    // Take the first path from the paths array
    const firstPath = input.paths[0];
    filePath = typeof firstPath === 'string' ? firstPath : (firstPath?.path || '');
  }
  
  const hasError = !!(result?.error);
  const success = !hasError && result?.success !== false;
  
  const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
  
  const colParts = [buildFilePath(filePath)];
  
  if (hasError) {
    colParts.push(buildSubInfo(result.error, '#b00020'));
  } else if (result?.displayed_lines !== undefined) {
    const rangeInfo = result?.range_info || `${result.displayed_lines} lines`;
    colParts.push(buildSubInfo(`Lines Read: ${rangeInfo}`));
  } else if (result?.total_line_count !== undefined) {
    colParts.push(buildSubInfo(`${result.total_line_count} lines total`));
  }
  
  lines.push(buildToolLine(icon, colParts));
  return lines.join('');
}

/**
 * Render multiple file view results
 */
function renderMultiple(input, result, options = {}) {
  const lines = [];
  const files = Array.isArray(result?.files) ? result.files : [];
  
  if (files.length === 0) {
    // Check if we have input paths but no results yet
    const inputPaths = input?.paths || [];
    if (inputPaths.length > 0) {
      for (const pathItem of inputPaths) {
        const p = typeof pathItem === 'string' ? pathItem : (pathItem?.path || '');
        if (p) {
          const colParts = [buildFilePath(p), buildSubInfo('Reading...')];
          lines.push(buildToolLine(pendingIconSvg(), colParts));
        }
      }
    } else {
      lines.push(buildToolLine(pendingIconSvg(), [buildSubInfo('No files')]));
    }
    return lines.join('');
  }
  
  for (const f of files) {
    const p = f?.path || '';
    const hasError = !!(f?.error) || f?.success === false;
    const icon = hasError ? failIconSvg() : okIconSvg();
    
    const colParts = [buildFilePath(p)];
    
    if (hasError && f?.error) {
      colParts.push(buildSubInfo(f.error, '#b00020'));
    } else if (f?.displayed_lines !== undefined) {
      colParts.push(buildSubInfo(`Lines Read: ${f.displayed_lines}`));
    }
    
    lines.push(buildToolLine(icon, colParts));
  }
  
  return lines.join('');
}

module.exports = { render, renderMultiple };
