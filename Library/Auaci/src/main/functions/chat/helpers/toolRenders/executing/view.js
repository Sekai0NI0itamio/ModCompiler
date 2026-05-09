// Executing state render for view tool
const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

module.exports = function renderViewExecuting(input) {
  const path = (input && input.path) || '';
  const paths = input && Array.isArray(input.paths) ? input.paths : (path ? [path] : []);
  const ranges = input && Array.isArray(input.ranges) ? input.ranges : [];
  
  const lines = [];
  
  if (paths.length === 0) {
    lines.push(buildToolLine(spinnerIconSvg(), [buildSubInfo('Reading file...')]));
  } else if (paths.length === 1) {
    const colParts = [
      buildFilePath(paths[0]),
      buildSubInfo(ranges.length > 0 ? `Reading lines ${ranges.join(', ')}...` : 'Reading file...')
    ];
    lines.push(buildToolLine(spinnerIconSvg(), colParts));
  } else {
    for (const p of paths) {
      lines.push(buildToolLine(spinnerIconSvg(), [
        buildFilePath(p),
        buildSubInfo('Reading...')
      ]));
    }
  }
  
  return lines.join('');
};
