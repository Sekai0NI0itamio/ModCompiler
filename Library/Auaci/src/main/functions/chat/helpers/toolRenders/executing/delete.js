// Executing state render for delete_file_folder_with_permission tool
const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

module.exports = function renderDeleteExecuting(input) {
  const items = (input && Array.isArray(input.items)) ? input.items : [];
  const path = (input && input.path) || '';
  
  // Handle both array of items and single path
  const paths = items.length > 0 ? items.map(i => i.path || i) : (path ? [path] : []);
  
  if (paths.length === 0) {
    return buildToolLine(spinnerIconSvg(), [buildSubInfo('Preparing delete...')]);
  }
  
  const lines = [];
  for (const p of paths.slice(0, 5)) { // Show max 5 items
    lines.push(buildToolLine(spinnerIconSvg(), [
      buildFilePath(typeof p === 'string' ? p : (p.path || '')),
      buildSubInfo('Pending confirmation...')
    ]));
  }
  
  if (paths.length > 5) {
    lines.push(`<div class="tool-line" style="color:#6b7280;margin-left:22px;">...and ${paths.length - 5} more</div>`);
  }
  
  return lines.join('');
};
