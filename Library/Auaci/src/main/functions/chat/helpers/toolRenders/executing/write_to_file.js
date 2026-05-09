// Executing state render for write_to_file tool
const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

module.exports = function renderWriteToFileExecuting(input) {
  const path = (input && input.path) || '';
  const content = (input && input.content) || '';
  const lineCount = content ? content.split('\n').length : 0;
  
  const colParts = [
    buildFilePath(path),
    buildSubInfo('Writing file...')
  ];
  if (lineCount > 0) {
    colParts.push(buildSubInfo(`${lineCount} lines`));
  }
  
  return buildToolLine(spinnerIconSvg(), colParts);
};
