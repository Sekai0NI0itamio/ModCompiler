// src/main/functions/chat/commands/handlers/grep.js
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
 * Render clean grep results
 */
function renderGrepResults(matchedFiles, patterns, patternFound, searchPath, totalMatches) {
  const lines = [];
  const relativePath = path.relative(process.cwd(), searchPath) || '.';
  
  lines.push(`Search Results in ${relativePath}`);
  lines.push('');
  
  if (matchedFiles.length === 0) {
    lines.push('No matches found');
    lines.push('');
    lines.push(`Patterns searched: ${patterns.map(p => `"${p}"`).join(', ')}`);
    return lines.join('\n');
  }
  
  lines.push(`Found ${totalMatches} matches in ${matchedFiles.length} files:`);
  lines.push('');
  
  // List matched files with line numbers
  for (const match of matchedFiles) {
    const relPath = path.relative(searchPath, match.file_path);
    const lineRanges = formatLineRanges(match.matched_lines);
    lines.push(`${relPath}`);
    lines.push(`  Lines: ${lineRanges}`);
  }
  
  lines.push('');
  
  // Pattern stats
  const matched = patterns.filter(p => patternFound.get(p));
  const unmatched = patterns.filter(p => !patternFound.get(p));
  
  if (matched.length > 0) {
    lines.push(`Found: ${matched.map(p => `"${p}"`).join(', ')}`);
  }
  if (unmatched.length > 0) {
    lines.push(`Not found: ${unmatched.map(p => `"${p}"`).join(', ')}`);
  }
  
  return lines.join('\n');
}

/**
 * Format line numbers into ranges
 */
function formatLineRanges(lineNumbers) {
  if (lineNumbers.length === 0) return 'none';
  if (lineNumbers.length === 1) return String(lineNumbers[0]);
  
  const ranges = [];
  let start = lineNumbers[0];
  let end = lineNumbers[0];
  
  for (let i = 1; i < lineNumbers.length; i++) {
    if (lineNumbers[i] === end + 1) {
      end = lineNumbers[i];
    } else {
      ranges.push(start === end ? String(start) : `${start}-${end}`);
      start = end = lineNumbers[i];
    }
  }
  ranges.push(start === end ? String(start) : `${start}-${end}`);
  
  return ranges.join(', ');
}

module.exports = async function grepCmd(params) {
  const base = params.path || process.cwd();
  const queries = Array.isArray(params.queries) ? params.queries : [];
  
  if (queries.length === 0) {
    await appendLog('[tools.grep] No search patterns provided');
    return {
      display_content: 'Error: No search patterns provided',
      matched_files: [],
      pattern_stats: [],
      summary: { total_matches: 0, files_matched: 0 },
      success: false,
      error: 'No search patterns provided',
      error_code: 'ERR_INVALID_INPUT'
    };
  }
  
  await appendLog(`[tools.grep] Searching for ${queries.length} pattern(s) in ${base}`);
  
  // Load excluded folders from config
  const excludedFolders = await getExcludedFoldersSet();
  
  const result = [];
  let totalMatches = 0;

  // Pre-compile regexes safely
  const compiled = queries.map(q => {
    try { 
      return { pattern: q, re: new RegExp(q, 'i') };
    } catch { 
      return { pattern: q, re: null }; 
    }
  });

  // Track per-pattern match status
  const patternFound = new Map(queries.map(q => [q, false]));

  async function walk(dir) {
    let entries;
    try {
      entries = await fs.readdir(dir, { withFileTypes: true });
    } catch {
      return;
    }
    
    for (const ent of entries) {
      const full = path.join(dir, ent.name);
      
      if (ent.isDirectory()) {
        // Skip excluded folders and hidden directories
        if (excludedFolders.has(ent.name) || ent.name.startsWith('.')) continue;
        await walk(full);
        continue;
      }
      
      // Skip binary files
      const ext = path.extname(ent.name).toLowerCase();
      const binaryExts = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.ico', '.webp',
                         '.pdf', '.zip', '.tar', '.gz', '.rar', '.7z',
                         '.exe', '.dll', '.so', '.dylib', '.bin',
                         '.mp3', '.mp4', '.wav', '.avi', '.mov',
                         '.woff', '.woff2', '.ttf', '.eot'];
      if (binaryExts.includes(ext)) continue;
      
      let content = '';
      try { 
        content = await fs.readFile(full, 'utf8'); 
      } catch { 
        continue;
      }
      
      const lines = content.split('\n');
      let matchedLines = new Set();
      
      for (const { pattern, re } of compiled) {
        if (!re) continue;
        
        let matched = false;
        for (let idx = 0; idx < lines.length; idx++) {
          if (re.test(lines[idx])) {
            matchedLines.add(idx + 1);
            matched = true;
            totalMatches++;
          }
        }
        if (matched) patternFound.set(pattern, true);
      }
      
      if (matchedLines.size > 0) {
        result.push({ 
          file_path: full, 
          matched_lines: Array.from(matchedLines).sort((a, b) => a - b),
          match_count: matchedLines.size
        });
      }
    }
  }

  await walk(base);
  
  const rawDisplayContent = renderGrepResults(result, queries, patternFound, base, totalMatches);
  const displayContent = truncateOutput(rawDisplayContent);
  const pattern_stats = queries.map(q => ({ pattern: q, matched: !!patternFound.get(q) }));
  
  let grepResult = {
    display_content: displayContent,
    matched_files: result,
    pattern_stats,
    summary: {
      total_matches: totalMatches,
      files_matched: result.length,
      patterns_searched: queries.length,
      patterns_found: pattern_stats.filter(p => p.matched).length,
      search_path: base
    },
    path: base,
    success: true
  };
  
  // Truncate entire result to 5KB
  let resultStr = JSON.stringify(grepResult);
  if (resultStr.length > MAX_OUTPUT_CHARS) {
    // Reduce matched_files array until under limit
    let truncatedFiles = [];
    for (const file of result) {
      truncatedFiles.push(file);
      const testResult = {
        ...grepResult,
        matched_files: truncatedFiles,
        truncated: true,
        truncation_message: 'The rest is truncated.'
      };
      if (JSON.stringify(testResult).length > MAX_OUTPUT_CHARS) {
        truncatedFiles.pop();
        break;
      }
    }
    grepResult = {
      display_content: truncateOutput(rawDisplayContent),
      matched_files: truncatedFiles,
      pattern_stats,
      summary: grepResult.summary,
      path: base,
      truncated: true,
      truncation_message: 'The rest is truncated.',
      success: true
    };
  }
  
  await appendLog(`[tools.grep] Found ${totalMatches} matches in ${result.length} files`);
  return grepResult;
};
