// Executing state render for diagnostics tool
const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

module.exports = function renderDiagnosticsExecuting(input) {
  const paths = (input && Array.isArray(input.paths)) ? input.paths : [];
  
  if (paths.length === 0) {
    return buildToolLine(spinnerIconSvg(), [buildSubInfo('Running diagnostics...')]);
  }
  
  const lines = [];
  for (const p of paths) {
    lines.push(buildToolLine(spinnerIconSvg(), [
      buildFilePath(p),
      buildSubInfo('Checking...')
    ]));
  }
  
  return lines.join('');
};
