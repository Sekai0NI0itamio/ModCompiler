// src/main/functions/chat/commands/handlers/find_files.js
const fs = require('fs').promises;
const path = require('path');
const { appendLog } = require('../lib/utils');
const { getExcludedFoldersSet } = require('../lib/excludedFolders');

// Maximum output size in characters (5KB)
const MAX_OUTPUT_CHARS = 5000;

/**
 * Truncate output to maximum size (5KB)
 */
function truncateOutput(output, maxChars = MAX_OUTPUT_CHARS) {
  if (!output || output.length <= maxChars) {
    return output;
  }
  const truncated = output.substring(0, maxChars);
  return truncated + '\n\nThe rest is truncated.';
}

/**
 * Convert a glob pattern to a regex matcher function.
 * Supports:
 *   - ** for matching any path segments (including nested directories)
 *   - * for matching any characters within a single path segment
 *   - ? for matching a single character
 *   - {a,b,c} for alternation
 *   - [abc] for character classes
 * 
 * @param {string} pattern - The glob pattern
 * @returns {function(string): boolean} - A function that tests if a path matches
 */
function globToMatcher(pattern) {
  if (!pattern || pattern === '*' || pattern === '**' || pattern === '**/*') {
    // Match all files
    return () => true;
  }
  
  // Normalize path separators
  let pat = pattern.replace(/\\/g, '/');
  
  // Check if pattern has directory components
  const hasPathSeparator = pat.includes('/');
  
  // Build regex from glob pattern
  let regexStr = '';
  let i = 0;
  
  while (i < pat.length) {
    const char = pat[i];
    const next = pat[i + 1];
    
    if (char === '*' && next === '*') {
      // ** matches any path segments
      if (pat[i + 2] === '/') {
        // **/ at start or middle - match any directories
        regexStr += '(?:.*\\/)?';
        i += 3;
      } else if (i + 2 === pat.length) {
        // ** at end - match anything
        regexStr += '.*';
        i += 2;
      } else {
        // ** followed by something else
        regexStr += '.*';
        i += 2;
      }
    } else if (char === '*') {
      // * matches any characters except /
      regexStr += '[^\\/]*';
      i++;
    } else if (char === '?') {
      // ? matches single character except /
      regexStr += '[^\\/]';
      i++;
    } else if (char === '[') {
      // Character class - find closing ]
      const closeIdx = pat.indexOf(']', i + 1);
      if (closeIdx !== -1) {
        regexStr += pat.slice(i, closeIdx + 1);
        i = closeIdx + 1;
      } else {
        regexStr += '\\[';
        i++;
      }
    } else if (char === '{') {
      // Alternation {a,b,c}
      const closeIdx = pat.indexOf('}', i + 1);
      if (closeIdx !== -1) {
        const alternatives = pat.slice(i + 1, closeIdx).split(',');
        regexStr += '(?:' + alternatives.map(a => a.replace(/[.+^${}()|[\]\\]/g, '\\$&')).join('|') + ')';
        i = closeIdx + 1;
      } else {
        regexStr += '\\{';
        i++;
      }
    } else if (char === '/') {
      regexStr += '\\/';
      i++;
    } else if ('.+^${}()|[]\\'.includes(char)) {
      // Escape regex special characters
      regexStr += '\\' + char;
      i++;
    } else {
      regexStr += char;
      i++;
    }
  }
  
  // If pattern doesn't have path separators, match against filename only
  // If pattern has path separators, match against full relative path
  const regex = new RegExp('^' + regexStr + '$', 'i');
  
  if (hasPathSeparator) {
    // Match against full relative path
    return (relativePath) => regex.test(relativePath.replace(/\\/g, '/'));
  } else {
    // Match against filename only
    return (relativePath) => {
      const fileName = path.basename(relativePath);
      return regex.test(fileName);
    };
  }
}

/**
 * Render clean file search results
 */
function renderFindResults(matches, patterns, searchDir, limits) {
  const lines = [];
  const relativePath = path.relative(process.cwd(), searchDir) || '.';
  
  lines.push(`File Search in ${relativePath}`);
  lines.push(`Pattern: ${patterns.map(p => `"${p}"`).join(', ')}`);
  lines.push('');
  
  if (matches.length === 0) {
    lines.push('No files found');
    return lines.join('\n');
  }
  
  const truncated = matches.length >= limits.maxMatches;
  lines.push(`Found ${matches.length}${truncated ? '+' : ''} files:`);
  lines.push('');
  
  // List file paths, one per line
  for (const match of matches) {
    const relPath = path.relative(searchDir, match.fullPath);
    lines.push(relPath);
  }
  
  if (truncated) {
    lines.push('');
    lines.push(`(Results limited to ${limits.maxMatches})`);
  }
  
  return lines.join('\n');
}

module.exports = async function findFilesCmd(params) {
  const searchDir = params.search_dir || process.cwd();
  const maxMatches = typeof params.max_matches === 'number' && params.max_matches > 0 ? params.max_matches : 1000;
  const maxDepth = typeof params.max_depth === 'number' ? params.max_depth : Infinity;
  const minDepth = typeof params.min_depth === 'number' ? params.min_depth : 0;
  const patterns = Array.isArray(params.patterns) && params.patterns.length > 0 ? params.patterns : ['*'];

  await appendLog(`[tools.find_files] Searching for ${patterns.length} pattern(s) in ${searchDir}`);
  
  // Load excluded folders from config
  const excludedFolders = await getExcludedFoldersSet();
  
  // Validate search directory
  try {
    const stat = await fs.stat(searchDir);
    if (!stat.isDirectory()) {
      return {
        display_content: `Error: Not a directory: ${searchDir}`,
        matched_files: '',
        summary: { total_matches: 0, patterns_searched: patterns.length },
        success: false,
        error: `Not a directory: ${searchDir}`,
        error_code: 'ERR_INVALID_INPUT'
      };
    }
  } catch (e) {
    return {
      display_content: `Error: Directory not found: ${searchDir}\n${e.message}`,
      matched_files: '',
      summary: { total_matches: 0, patterns_searched: patterns.length },
      success: false,
      error: `Directory not found: ${searchDir}`,
      error_code: 'ERR_NOT_FOUND'
    };
  }

  // Create matcher functions for each pattern
  const matcherFns = patterns.map(globToMatcher);
  let matches = [];

  async function walk(dir, depth, relativePath = '') {
    if (depth > maxDepth || matches.length >= maxMatches) return;
    
    let entries;
    try {
      entries = await fs.readdir(dir, { withFileTypes: true });
    } catch {
      return;
    }
    
    for (const ent of entries) {
      if (matches.length >= maxMatches) break;
      
      const full = path.join(dir, ent.name);
      const relPath = relativePath ? `${relativePath}/${ent.name}` : ent.name;
      
      if (ent.isDirectory()) {
        // Skip excluded folders and hidden directories
        if (!excludedFolders.has(ent.name) && !ent.name.startsWith('.')) {
          await walk(full, depth + 1, relPath);
        }
        continue;
      }
      
      if (depth >= minDepth) {
        // Test against relative path for patterns with path separators
        if (matcherFns.some(fn => fn(relPath))) {
          matches.push({
            fileName: ent.name,
            fullPath: full,
            relativePath: relPath,
            depth: depth
          });
        }
      }
    }
  }

  await walk(searchDir, 0, '');
  
  // Remove duplicates from matches based on fullPath
  const uniqueMatches = [];
  const seenPaths = new Set();
  for (const match of matches) {
    if (!seenPaths.has(match.fullPath)) {
      seenPaths.add(match.fullPath);
      uniqueMatches.push(match);
    }
  }
  matches = uniqueMatches;
  
  const limits = { maxMatches, maxDepth, minDepth };
  const rawDisplayContent = renderFindResults(matches, patterns, searchDir, limits);
  const displayContent = truncateOutput(rawDisplayContent);
  
  // Convert to old format for compatibility
  // Use relative paths from searchDir instead of just filenames to avoid duplicates
  const filePaths = matches.map(m => path.relative(searchDir, m.fullPath));
  
  let result = {
    display_content: displayContent,
    matched_files: filePaths.join('\n'),
    summary: {
      total_matches: matches.length,
      patterns_searched: patterns.length,
      search_directory: searchDir,
      limits: limits,
      truncated: matches.length >= maxMatches
    },
    files: matches
    ,
    success: true
  };
  
  // Truncate entire result to 5KB, but be smarter about it
  let resultStr = JSON.stringify(result);
  if (resultStr.length > MAX_OUTPUT_CHARS) {
    // Try to preserve more results by removing less important data first
    // First, remove the files array (most verbose)
    const { files, ...resultWithoutFiles } = result;
    let testResult = { ...resultWithoutFiles, truncated: true, truncation_message: 'The rest is truncated.' };
    
    // If still too big, truncate the display_content
    if (JSON.stringify(testResult).length > MAX_OUTPUT_CHARS) {
      testResult.display_content = truncateOutput(testResult.display_content, MAX_OUTPUT_CHARS / 2);
    }
    
    // If still too big, reduce matched_files
    if (JSON.stringify(testResult).length > MAX_OUTPUT_CHARS) {
      const lines = testResult.matched_files.split('\n');
      const truncatedLines = [];
      for (const line of lines) {
        truncatedLines.push(line);
        testResult.matched_files = truncatedLines.join('\n');
        if (JSON.stringify(testResult).length > MAX_OUTPUT_CHARS) {
          truncatedLines.pop();
          break;
        }
      }
      testResult.matched_files = truncatedLines.join('\n');
    }
    
    // Update the files array to match the truncated matched_files
    const truncatedFilePaths = testResult.matched_files.split('\n').filter(Boolean);
    const truncatedFiles = matches.filter(m => truncatedFilePaths.includes(path.relative(searchDir, m.fullPath)));
    testResult.files = truncatedFiles;
    
    result = testResult;
  }
  if (!result.success) result.success = true;
  
  await appendLog(`[tools.find_files] Found ${matches.length} files`);
  return result;
};
