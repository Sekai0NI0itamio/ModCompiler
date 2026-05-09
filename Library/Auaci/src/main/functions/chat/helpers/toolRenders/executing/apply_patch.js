// Executing state render for apply_patch tool
const { spinnerIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

module.exports = function renderApplyPatchExecuting(input) {
  const diff = (input && (input.input || input.diff)) || '';
  
  // Extract file paths from the patch text
  const filePathsSet = new Set();
  let addCount = 0, removeCount = 0;
  if (diff) {
    const lines = diff.split('\n');
    for (const line of lines) {
      if (line.startsWith('*** Update File: ') || line.startsWith('*** Add File: ') || line.startsWith('*** Delete File: ')) {
        const fp = line.replace(/^\*\*\* (?:Update|Add|Delete) File:\s*/, '').trim();
        if (fp) filePathsSet.add(fp);
      }
      if (line.startsWith('+') && !line.startsWith('+++') && !line.startsWith('*** ')) addCount++;
      else if (line.startsWith('-') && !line.startsWith('---') && !line.startsWith('*** ')) removeCount++;
    }
  }
  
  const filePaths = Array.from(filePathsSet);
  const displayPath = filePaths.length === 1 ? filePaths[0] : (filePaths.length > 1 ? `${filePaths.length} files` : '');
  
  const colParts = [
    buildFilePath(displayPath),
    buildSubInfo('Applying patch...')
  ];
  if (addCount > 0 || removeCount > 0) {
    colParts.push(buildSubInfo(`+${addCount} / -${removeCount} lines`));
  }
  
  return buildToolLine(spinnerIconSvg(), colParts);
};
