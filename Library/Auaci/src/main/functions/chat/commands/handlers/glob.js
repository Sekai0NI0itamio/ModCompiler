// src/main/functions/chat/commands/handlers/glob.js
const path = require('path');
const findFilesCmd = require('./find_files');

// Maximum output size in characters (5KB)
const MAX_OUTPUT_CHARS = 5000;



module.exports = async function globCmd(params) {
  const base = path.isAbsolute(params.path || '.') ? (params.path || '.') : path.join(process.cwd(), params.path || '.');
  const pattern = String(params.pattern || '*');
  const max_matches = typeof params.max_results === 'number' && params.max_results > 0 ? params.max_results : 1000;
  const res = await findFilesCmd({ search_dir: base, patterns: [pattern], max_matches, max_depth: Infinity, min_depth: 0 });
  if (res && res.success === false) {
    return {
      success: false,
      error: res.error || 'glob: search failed',
      error_code: res.error_code || 'ERR_IO',
      matches: []
    };
  }
  
  // Use the full paths from the find_files result instead of reconstructing from filenames
  // This avoids duplicates when files have the same name in different directories
  let matches = [];
  
  if (res.files && res.files.length > 0) {
    // Use the files array which contains full paths
    matches = res.files.map(file => file.fullPath);
  } else if (res.matched_files) {
    // Fallback: reconstruct paths from matched_files
    const lines = String(res.matched_files).split('\n').filter(Boolean);
    matches = lines.map(relativePath => {
      // If the path is already absolute, use it directly
      if (path.isAbsolute(relativePath)) {
        return relativePath;
      }
      // Otherwise, join with base directory
      return path.join(base, relativePath);
    });
  }
  
  // Remove duplicates just in case
  matches = [...new Set(matches)];
  
  // Truncate entire result to 5KB
  let result = { matches };
  let resultStr = JSON.stringify(result);
  
  if (resultStr.length > MAX_OUTPUT_CHARS) {
    // Reduce matches array until under limit
    let truncatedMatches = [];
    for (const match of matches) {
      truncatedMatches.push(match);
      const testResult = {
        matches: truncatedMatches,
        truncated: true,
        truncation_message: 'The rest is truncated.'
      };
      if (JSON.stringify(testResult).length > MAX_OUTPUT_CHARS) {
        truncatedMatches.pop();
        break;
      }
    }
    return { 
      success: true,
      matches: truncatedMatches,
      truncated: true,
      truncation_message: 'The rest is truncated.'
    };
  }
  
  return { success: true, ...result };
};
