// src/main/functions/chat/commands/handlers/read_multiple_files.js
// Read multiple files in a single call for efficiency

const fs = require('fs').promises;
const path = require('path');
const { appendLog } = require('../lib/utils');

/**
 * Add line numbers to content
 */
function withLineNumbers(content, startLine = 1) {
  const lines = content.split('\n');
  return lines.map((line, i) => `${startLine + i}|${line}`).join('\n');
}

/**
 * Read multiple files command handler
 * @param {Object} params - { paths, start_line?, end_line? }
 */
module.exports = async function readMultipleFilesCmd(params) {
  const paths = params.paths;
  const startLine = params.start_line || 1;
  const endLine = params.end_line || -1; // -1 means read to end
  
  // Validate parameters
  if (!paths || !Array.isArray(paths) || paths.length === 0) {
    return {
      success: false,
      error: 'Missing required parameter: paths (array of file paths)',
      error_code: 'ERR_INVALID_INPUT',
      files: []
    };
  }
  
  await appendLog(`[tools.read_multiple_files] Reading ${paths.length} file(s)`);
  
  const results = [];
  const displayParts = [];
  
  for (const filePath of paths) {
    const abs = path.isAbsolute(filePath) ? filePath : path.join(process.cwd(), filePath);
    
    try {
      // Check if file exists
      const stat = await fs.stat(abs);
      
      if (stat.isDirectory()) {
        results.push({
          path: filePath,
          success: false,
          error: 'Path is a directory',
          error_code: 'ERR_INVALID_INPUT'
        });
        displayParts.push(`--- ${filePath} ---\nError: Path is a directory`);
        continue;
      }
      
      // Read file content
      const content = await fs.readFile(abs, 'utf8');
      const allLines = content.split('\n');
      const totalLines = allLines.length;
      
      // Apply line range
      let selectedLines;
      let actualStart = startLine;
      let actualEnd = endLine;
      let wasTruncated = false;
      let truncationMessage = null;
      
      if (startLine < 0) {
        // Negative start means from end
        actualStart = Math.max(1, totalLines + startLine + 1);
      }
      
      if (endLine < 0 || endLine > totalLines) {
        actualEnd = totalLines;
      }
      
      // Clamp values
      actualStart = Math.max(1, Math.min(actualStart, totalLines));
      actualEnd = Math.max(actualStart, Math.min(actualEnd, totalLines));
      
      // Apply 2000 line limit
      const MAX_LINES = 2000;
      const requestedLines = actualEnd - actualStart + 1;
      
      if (requestedLines > MAX_LINES) {
        wasTruncated = true;
        actualEnd = actualStart + MAX_LINES - 1;
        const cutoffLine = actualEnd + 1;
        const remainingLines = totalLines - actualEnd;
        truncationMessage = `⚠️ Requested range is too large (${requestedLines} lines). Only showing lines ${actualStart}-${actualEnd}. The remaining ${remainingLines} lines (${cutoffLine}-${totalLines}) were not sent. To read the rest, call read_multiple_files again with start_line: ${cutoffLine}, end_line: ${totalLines}`;
      }
      
      selectedLines = allLines.slice(actualStart - 1, actualEnd);
      const contentWithNumbers = selectedLines.map((line, i) => `${actualStart + i}|${line}`).join('\n');
      
      const fileResult = {
        path: filePath,
        success: true,
        content: contentWithNumbers,
        total_lines: totalLines,
        displayed_lines: selectedLines.length,
        range: `${actualStart}-${actualEnd}`,
        is_truncated: wasTruncated
      };
      
      if (truncationMessage) {
        fileResult.truncation_message = truncationMessage;
      }
      
      results.push(fileResult);
      
      let displayHeader = `--- ${filePath} (${actualStart}-${actualEnd} of ${totalLines})`;
      if (wasTruncated) {
        displayHeader += ` [TRUNCATED]`;
      }
      displayHeader += ` ---`;
      
      let displayContent = displayHeader + '\n' + contentWithNumbers;
      if (truncationMessage) {
        displayContent += '\n\n' + truncationMessage;
      }
      
      displayParts.push(displayContent);
      
    } catch (err) {
      const errorMsg = err.code === 'ENOENT' ? 'File not found' : err.message;
      const errorCode = err.code === 'ENOENT' ? 'ERR_NOT_FOUND' : (err.code || 'ERR_IO');
      results.push({
        path: filePath,
        success: false,
        error: errorMsg,
        error_code: errorCode
      });
      displayParts.push(`--- ${filePath} ---\nError: ${errorMsg}`);
    }
  }
  
  const successCount = results.filter(r => r.success).length;
  const failCount = results.length - successCount;
  
  await appendLog(`[tools.read_multiple_files] Read ${successCount}/${results.length} files successfully`);
  
  return {
    success: failCount === 0,
    files: results,
    summary: {
      total: results.length,
      success: successCount,
      failed: failCount
    },
    display_content: displayParts.join('\n\n')
  };
};
