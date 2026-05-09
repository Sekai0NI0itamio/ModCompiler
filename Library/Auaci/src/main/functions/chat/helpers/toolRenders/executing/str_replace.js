// Executing state render for str_replace tool
const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

module.exports = function renderStrReplaceExecuting(input) {
  const path = (input && input.path) || '';
  const oldStr = (input && input.oldStr) || '';
  const newStr = (input && input.newStr) || '';
  
  const oldLines = oldStr ? oldStr.split('\n').length : 0;
  const newLines = newStr ? newStr.split('\n').length : 0;
  
  const colParts = [
    buildFilePath(path),
    buildSubInfo('Replacing...')
  ];
  if (oldLines > 0 || newLines > 0) {
    colParts.push(buildSubInfo(`${oldLines} → ${newLines} lines`));
  }
  
  return buildToolLine(spinnerIconSvg(), colParts);
};
