// src/main/functions/chat/commands/handlers/view.js
const path = require('path');
const fs = require('fs').promises;
const { appendLog } = require('../lib/utils');

// Maximum lines per file to prevent context overload
const MAX_LINES = 600;

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
 * Process a single file and return its content with line numbers
 */
async function processFile(filePath, ranges) {
  const baseCwd = getChatCwd();
  const abs = path.isAbsolute(filePath) ? filePath : path.join(baseCwd, filePath);
  
  try {
    const stat = await fs.stat(abs);
    if (stat.isDirectory()) {
      return { path: filePath, success: false, error: 'Path is a directory', error_code: 'ERR_INVALID_TARGET' };
    }
  } catch (e) {
    if (e.code === 'ENOENT') {
      return { path: filePath, success: false, error: `File not found: ${filePath}`, error_code: 'ERR_NOT_FOUND' };
    }
    return { path: filePath, success: false, error: e.message, error_code: 'ERR_IO' };
  }
  
  const rawContent = await fs.readFile(abs, 'utf8');
  const allLines = rawContent.split(/\r?\n/);
  const totalLineCount = allLines.length;
  
  let selectedLines = [];
  let contentWithNumbers = '';
  let isTruncated = false;
  let rangeInfo = null;
  
  if (ranges && Array.isArray(ranges) && ranges.length > 0) {
    // Process each range and collect all selected lines
    const lineNumbers = new Set();
    
    for (const range of ranges) {
      if (typeof range === 'string') {
        if (range.includes('-')) {
          const [startStr, endStr] = range.split('-', 2);
          const start = parseInt(startStr, 10);
          const end = parseInt(endStr, 10);
          
          if (isNaN(start) || isNaN(end)) continue;
          if (start < 1 || end < start) continue;
          
          const actualStart = Math.max(1, start);
          const actualEnd = Math.min(totalLineCount, end);
          
          for (let i = actualStart; i <= actualEnd; i++) {
            lineNumbers.add(i);
          }
        } else {
          const lineNum = parseInt(range, 10);
          if (!isNaN(lineNum) && lineNum >= 1 && lineNum <= totalLineCount) {
            lineNumbers.add(lineNum);
          }
        }
      }
    }
    
    selectedLines = Array.from(lineNumbers)
      .sort((a, b) => a - b)
      .map(lineNum => ({ lineNum, content: allLines[lineNum - 1] || '' }));
    
    // Apply MAX_LINES limit even for ranges
    if (selectedLines.length > MAX_LINES) {
      isTruncated = true;
      selectedLines = selectedLines.slice(0, MAX_LINES);
    }
    
    contentWithNumbers = selectedLines.map(l => `${l.lineNum}|${l.content}`).join('\n');
    
    if (selectedLines.length > 0) {
      const firstLine = selectedLines[0].lineNum;
      const lastLine = selectedLines[selectedLines.length - 1].lineNum;
      rangeInfo = firstLine === lastLine ? `(${firstLine})` : `(${firstLine} - ${lastLine})`;
    }
  } else {
    // Return entire file with line numbers, capped at MAX_LINES
    if (totalLineCount > MAX_LINES) {
      isTruncated = true;
      selectedLines = allLines.slice(0, MAX_LINES).map((content, idx) => ({ lineNum: idx + 1, content }));
      contentWithNumbers = selectedLines.map(l => `${l.lineNum}|${l.content}`).join('\n');
      rangeInfo = `(1 - ${MAX_LINES})`;
    } else {
      selectedLines = allLines.map((content, idx) => ({ lineNum: idx + 1, content }));
      contentWithNumbers = selectedLines.map(l => `${l.lineNum}|${l.content}`).join('\n');
      rangeInfo = `(1 - ${totalLineCount})`;
    }
  }
  
  const result = {
    path: filePath,
    success: true,
    content: contentWithNumbers,
    total_line_count: totalLineCount,
    is_truncated: isTruncated,
    displayed_lines: selectedLines.length,
    range_info: rangeInfo
  };
  
  if (isTruncated) {
    const cutoffLine = selectedLines[selectedLines.length - 1].lineNum + 1;
    result.truncation_message = `⚠️ File is large (${totalLineCount} lines). Only showing ${selectedLines.length} lines. To read more, use ranges: ["${cutoffLine}-${Math.min(cutoffLine + MAX_LINES - 1, totalLineCount)}"]`;
  }
  
  return result;
}

/**
 * Enhanced view command - supports single file or multiple files
 * Single file: { path: string, ranges?: [string, ...] }
 * Multiple files: { paths: [string, ...] | [{path: string, ranges?: [string, ...]}] }
 */
module.exports = async function viewCmd(params) {
  // Handle multiple files mode
  if (params.paths && Array.isArray(params.paths)) {
    const results = [];
    const displayParts = [];
    
    for (const item of params.paths) {
      let filePath, ranges;
      
      if (typeof item === 'string') {
        filePath = item;
        ranges = null;
      } else if (item && typeof item === 'object') {
        filePath = item.path;
        ranges = item.ranges;
      } else {
        continue;
      }
      
      const fileResult = await processFile(filePath, ranges);
      results.push(fileResult);
      
      if (fileResult.success) {
        let header = `--- ${filePath} ${fileResult.range_info} (${fileResult.total_line_count} total)`;
        if (fileResult.is_truncated) header += ' [TRUNCATED]';
        header += ' ---';
        displayParts.push(header + '\n' + fileResult.content);
        if (fileResult.truncation_message) {
          displayParts[displayParts.length - 1] += '\n' + fileResult.truncation_message;
        }
      } else {
        displayParts.push(`--- ${filePath} ---\nError: ${fileResult.error}`);
      }
    }
    
    const successCount = results.filter(r => r.success).length;
    await appendLog(`[tools.view] multi-file mode: ${successCount}/${results.length} files read`);
    
    return {
      mode: 'multiple',
      files: results,
      summary: {
        total: results.length,
        success: successCount,
        failed: results.length - successCount
      },
      display_content: displayParts.join('\n\n'),
      success: successCount === results.length
    };
  }
  
  // Single file mode (original behavior)
  const p = params.path;
  if (!p) throw new Error('view: missing path or paths');
  
  const result = await processFile(p, params.ranges);
  
  if (!result.success) {
    throw new Error(result.error);
  }
  
  await appendLog(`[tools.view] path=${p} range=${result.range_info || 'full'} lines=${result.displayed_lines}/${result.total_line_count}`);
  
  // Return in original single-file format for backward compatibility
  return {
    path: p,
    content: result.content,
    total_line_count: result.total_line_count,
    is_truncated: result.is_truncated,
    displayed_lines: result.displayed_lines,
    range_info: result.range_info,
    truncation_message: result.truncation_message,
    success: true
  };
};
