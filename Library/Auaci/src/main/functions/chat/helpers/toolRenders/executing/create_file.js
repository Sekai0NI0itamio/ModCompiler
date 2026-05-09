// Executing state render for create_file tool
const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

module.exports = function renderCreateFileExecuting(input) {
  const path = (input && input.path) || '';
  
  const colParts = [
    buildFilePath(path),
    buildSubInfo('Creating file...')
  ];
  
  return buildToolLine(spinnerIconSvg(), colParts);
};
