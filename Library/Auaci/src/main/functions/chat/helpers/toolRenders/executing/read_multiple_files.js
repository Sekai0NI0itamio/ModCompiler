// Executing state render for read_multiple_files tool
const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

module.exports = function renderReadMultipleFilesExecuting(input) {
  const paths = (input && Array.isArray(input.paths)) ? input.paths : [];
  
  if (paths.length === 0) {
    return buildToolLine(spinnerIconSvg(), [buildSubInfo('Reading files...')]);
  }
  
  const lines = [];
  for (const p of paths) {
    lines.push(buildToolLine(spinnerIconSvg(), [
      buildFilePath(p),
      buildSubInfo('Reading...')
    ]));
  }
  
  return lines.join('');
};
