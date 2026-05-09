// Executing state render for get_function tool

const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

/**
 * Render get_function tool in executing state
 * @param {object} input - Tool input parameters
 * @returns {string} - HTML string
 */
function renderGetFunction(input) {
  const filePath = input?.path || 'file';
  const symbolName = input?.name;
  const listOnly = input?.list_only;
  
  let action = 'Extracting symbols from';
  if (symbolName) {
    action = `Extracting "${symbolName}" from`;
  } else if (listOnly) {
    action = 'Listing symbols in';
  }
  
  return buildToolLine(
    spinnerIconSvg,
    `${action} ${buildFilePath(filePath)}`
  );
}

module.exports = renderGetFunction;
