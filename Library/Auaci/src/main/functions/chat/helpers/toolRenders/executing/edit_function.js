// Executing state render for edit_function tool

const { spinnerIconSvg, buildToolLine, buildFilePath } = require('../shared');

/**
 * Render edit_function tool in executing state
 * @param {object} input - Tool input parameters
 * @returns {string} - HTML string
 */
function renderEditFunction(input) {
  const filePath = input?.path || input?.file_path || 'file';
  const symbolName = input?.name || input?.function_name || 'symbol';
  const action = input?.action || 'replace';
  
  let actionText;
  switch (action) {
    case 'delete':
      actionText = `Deleting "${symbolName}" from`;
      break;
    case 'insert_before':
      actionText = `Inserting before "${symbolName}" in`;
      break;
    case 'insert_after':
      actionText = `Inserting after "${symbolName}" in`;
      break;
    default:
      actionText = `Editing "${symbolName}" in`;
  }
  
  return buildToolLine(
    spinnerIconSvg,
    `${actionText} ${buildFilePath(filePath)}`
  );
}

module.exports = renderEditFunction;
