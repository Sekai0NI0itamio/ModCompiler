// Executing state render for edit_lines tool

const { spinnerIconSvg, buildToolLine, buildFilePath } = require('../shared');

/**
 * Render edit_lines tool in executing state
 * @param {object} input - Tool input parameters
 * @returns {string} - HTML string
 */
function renderEditLines(input) {
  const filePath = input?.path || input?.file_path || 'file';
  const startLine = input?.start_line || '?';
  const endLine = input?.end_line || startLine;
  const action = input?.action || 'replace';
  
  let actionText;
  const lineRange = startLine === endLine ? `line ${startLine}` : `lines ${startLine}-${endLine}`;
  
  switch (action) {
    case 'delete':
      actionText = `Deleting ${lineRange} in`;
      break;
    case 'insert_before':
      actionText = `Inserting before ${lineRange} in`;
      break;
    case 'insert_after':
      actionText = `Inserting after ${lineRange} in`;
      break;
    default:
      actionText = `Editing ${lineRange} in`;
  }
  
  return buildToolLine(
    spinnerIconSvg,
    `${actionText} ${buildFilePath(filePath)}`
  );
}

module.exports = renderEditLines;
