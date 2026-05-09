// Executing state render for fs_append tool
const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

module.exports = function renderFsAppendExecuting(input) {
  const path = (input && input.path) || '';
  const text = (input && input.text) || '';
  const lineCount = text ? text.split('\n').length : 0;
  
  const colParts = [
    buildFilePath(path),
    buildSubInfo('Appending...')
  ];
  if (lineCount > 0) {
    colParts.push(buildSubInfo(`+${lineCount} lines`));
  }
  
  return buildToolLine(spinnerIconSvg(), colParts);
};
