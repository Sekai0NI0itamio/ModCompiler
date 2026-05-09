// src/main/functions/chat/commands/handlers/ls.js
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
 * Get the current working directory for the chat session
 * Falls back to process.cwd() if not available
 */
function getChatCwd() {
  try {
    const { getCurrentCwd } = require('../../ui/terminalInput');
    const cwd = getCurrentCwd();
    if (cwd) return cwd;
  } catch (_) {}
  return process.cwd();
}

/**
 * Render a clean list of file paths, one per line
 */
function renderFileList(entries, basePath) {
  const lines = [];
  const relativeTo = basePath;
  
  // Header
  const relPath = path.relative(getChatCwd(), basePath) || '.';
  lines.push(`Directory: ${relPath}`);
  lines.push('');
  
  if (entries.length === 0) {
    lines.push('(empty)');
    return lines.join('\n');
  }
  
  // Just list file paths, one per line
  for (const entry of entries) {
    const rel = path.relative(relativeTo, entry.fullPath);
    lines.push(rel);
  }
  
  // Summary
  const dirCount = entries.filter(e => e.isDirectory).length;
  const fileCount = entries.filter(e => !e.isDirectory).length;
  lines.push('');
  lines.push(`Total: ${entries.length} items (${dirCount} directories, ${fileCount} files)`);
  
  return lines.join('\n');
}

module.exports = async function lsCmd(params) {
  const rel = params.path || '.';
  const maxDepth = typeof params.max_depth === 'number' ? params.max_depth : Infinity;
  const base = path.isAbsolute(rel) ? rel : path.join(getChatCwd(), rel);
  
  await appendLog(`[tools.ls] Listing directory ${base} with max depth ${maxDepth}`);
  
  // Load excluded folders from config
  const excludedFolders = await getExcludedFoldersSet();
  
  // Validate directory
  try {
    const stat = await fs.stat(base);
    if (!stat.isDirectory()) {
      return {
        display_content: `Error: Not a directory: ${rel}`,
        entries: [],
        summary: { total_items: 0, directories: 0, files: 0 },
        success: false,
        error: `Not a directory: ${rel}`,
        error_code: 'ERR_INVALID_INPUT'
      };
    }
  } catch (e) {
    return {
      display_content: `Error: Directory not found: ${rel}\n${e.message}`,
      entries: [],
      summary: { total_items: 0, directories: 0, files: 0 },
      success: false,
      error: `Directory not found: ${rel}`,
      error_code: 'ERR_NOT_FOUND'
    };
  }
  
  const entries = [];
  const entryDetails = [];
  
  async function walk(dir, depth) {
    if (depth > maxDepth) return;
    
    let ents;
    try {
      ents = await fs.readdir(dir, { withFileTypes: true });
    } catch {
      return; // Skip directories we can't read
    }
    
    // Sort entries: directories first, then files, both alphabetically
    ents.sort((a, b) => {
      if (a.isDirectory() && !b.isDirectory()) return -1;
      if (!a.isDirectory() && b.isDirectory()) return 1;
      return a.name.localeCompare(b.name);
    });
    
    for (const ent of ents) {
      // Skip hidden files/folders
      if (ent.name.startsWith('.')) continue;
      
      // Skip excluded folders (from config)
      if (ent.isDirectory() && excludedFolders.has(ent.name)) {
        continue;
      }
      
      const fullPath = path.join(dir, ent.name);
      entries.push(fullPath); // For backward compatibility
      
      entryDetails.push({
        name: ent.name,
        fullPath: fullPath,
        isDirectory: ent.isDirectory(),
        depth: depth
      });
      
      if (ent.isDirectory()) {
        await walk(fullPath, depth + 1);
      }
    }
  }
  
  await walk(base, 0);
  
  const rawDisplayContent = renderFileList(entryDetails, base);
  const displayContent = truncateOutput(rawDisplayContent);
  const dirCount = entryDetails.filter(e => e.isDirectory).length;
  const fileCount = entryDetails.filter(e => !e.isDirectory).length;
  
  let result = {
    display_content: displayContent,
    entries: entries,
    summary: {
      total_items: entries.length,
      directories: dirCount,
      files: fileCount,
      max_depth: maxDepth,
      base_path: base
    },
    items: entryDetails,
    success: true
  };
  
  // Truncate entire result to 5KB
  let resultStr = JSON.stringify(result);
  if (resultStr.length > MAX_OUTPUT_CHARS) {
    // Reduce entries and items arrays until under limit
    let truncatedEntries = [];
    let truncatedItems = [];
    for (let i = 0; i < entryDetails.length; i++) {
      truncatedEntries.push(entries[i]);
      truncatedItems.push(entryDetails[i]);
      const testResult = {
        display_content: truncateOutput(rawDisplayContent),
        entries: truncatedEntries,
        summary: result.summary,
        items: truncatedItems,
        truncated: true,
        truncation_message: 'The rest is truncated.'
      };
      if (JSON.stringify(testResult).length > MAX_OUTPUT_CHARS) {
        truncatedEntries.pop();
        truncatedItems.pop();
        break;
      }
    }
    result = {
      display_content: truncateOutput(rawDisplayContent),
      entries: truncatedEntries,
      summary: result.summary,
      items: truncatedItems,
      truncated: true,
      truncation_message: 'The rest is truncated.',
      success: true
    };
  }
  
  await appendLog(`[tools.ls] Found ${entries.length} items (${dirCount} directories, ${fileCount} files)`);
  return result;
};
