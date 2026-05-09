// Executing state render for ls tool
const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

module.exports = function renderLsExecuting(input) {
  const path = (input && input.path) || '.';
  const maxDepth = input && typeof input.max_depth === 'number' ? input.max_depth : null;
  
  const colParts = [
    buildFilePath(path),
    buildSubInfo('Scanning directory...')
  ];
  if (maxDepth !== null) {
    colParts.push(buildSubInfo(`Depth: ${maxDepth}`));
  }
  
  return buildToolLine(spinnerIconSvg(), colParts);
};
