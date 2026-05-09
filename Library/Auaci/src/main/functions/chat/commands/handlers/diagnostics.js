// src/main/functions/chat/commands/handlers/diagnostics.js
// Checks files for syntax errors, linting issues, and other problems

const fs = require('fs').promises;
const path = require('path');
const { execFile } = require('child_process');
const { promisify } = require('util');
const { appendLog } = require('../lib/utils');

const execFileAsync = promisify(execFile);

/**
 * Get file extension
 */
function getExtension(filePath) {
  return path.extname(filePath).toLowerCase();
}

/**
 * Check JavaScript/TypeScript syntax using Node.js
 */
async function checkJavaScript(filePath, content) {
  const issues = [];
  
  try {
    // Try to parse as JavaScript
    new Function(content);
  } catch (err) {
    // Extract line number from error if possible
    const match = err.message.match(/at.*:(\d+):(\d+)/);
    const line = match ? parseInt(match[1], 10) : 1;
    const col = match ? parseInt(match[2], 10) : 1;
    
    issues.push({
      line,
      column: col,
      severity: 'error',
      message: err.message.split('\n')[0]
    });
  }
  
  return issues;
}

/**
 * Check JSON syntax
 */
async function checkJSON(filePath, content) {
  const issues = [];
  
  try {
    JSON.parse(content);
  } catch (err) {
    // Try to extract position from error
    const posMatch = err.message.match(/position (\d+)/i);
    let line = 1;
    let column = 1;
    
    if (posMatch) {
      const pos = parseInt(posMatch[1], 10);
      const lines = content.substring(0, pos).split('\n');
      line = lines.length;
      column = lines[lines.length - 1].length + 1;
    }
    
    issues.push({
      line,
      column,
      severity: 'error',
      message: err.message
    });
  }
  
  return issues;
}

/**
 * Check Python syntax using python3 -m py_compile
 */
async function checkPython(filePath) {
  const issues = [];
  
  try {
    await execFileAsync('python3', ['-m', 'py_compile', filePath], {
      timeout: 10000
    });
  } catch (err) {
    // Parse Python syntax error output
    const stderr = err.stderr || err.message || '';
    
    // Python syntax errors look like:
    // File "path", line X
    //   code
    //       ^
    // SyntaxError: message
    const lineMatch = stderr.match(/line (\d+)/i);
    const line = lineMatch ? parseInt(lineMatch[1], 10) : 1;
    
    // Extract the actual error message
    const errorMatch = stderr.match(/(SyntaxError|IndentationError|TabError):\s*(.+)/i);
    const message = errorMatch 
      ? `${errorMatch[1]}: ${errorMatch[2]}`
      : stderr.split('\n').filter(l => l.trim()).pop() || 'Syntax error';
    
    issues.push({
      line,
      column: 1,
      severity: 'error',
      message
    });
  }
  
  return issues;
}

/**
 * Check HTML for basic issues
 */
async function checkHTML(filePath, content) {
  const issues = [];
  
  // Check for unclosed tags (basic check)
  const openTags = [];
  const tagRegex = /<\/?([a-zA-Z][a-zA-Z0-9]*)[^>]*\/?>/g;
  const selfClosing = new Set(['br', 'hr', 'img', 'input', 'meta', 'link', 'area', 'base', 'col', 'embed', 'param', 'source', 'track', 'wbr']);
  
  let match;
  const lines = content.split('\n');
  
  while ((match = tagRegex.exec(content)) !== null) {
    const fullTag = match[0];
    const tagName = match[1].toLowerCase();
    
    // Skip self-closing tags
    if (selfClosing.has(tagName) || fullTag.endsWith('/>')) continue;
    
    // Calculate line number
    const pos = match.index;
    const beforeMatch = content.substring(0, pos);
    const line = beforeMatch.split('\n').length;
    
    if (fullTag.startsWith('</')) {
      // Closing tag
      if (openTags.length === 0 || openTags[openTags.length - 1].name !== tagName) {
        issues.push({
          line,
          column: 1,
          severity: 'warning',
          message: `Unexpected closing tag </${tagName}>`
        });
      } else {
        openTags.pop();
      }
    } else {
      // Opening tag
      openTags.push({ name: tagName, line });
    }
  }
  
  // Report unclosed tags
  for (const tag of openTags) {
    issues.push({
      line: tag.line,
      column: 1,
      severity: 'warning',
      message: `Unclosed tag <${tag.name}>`
    });
  }
  
  return issues;
}

/**
 * Check CSS for basic syntax issues
 */
async function checkCSS(filePath, content) {
  const issues = [];
  
  // Check for unbalanced braces
  let braceCount = 0;
  let lastOpenLine = 0;
  const lines = content.split('\n');
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    for (const char of line) {
      if (char === '{') {
        braceCount++;
        lastOpenLine = i + 1;
      } else if (char === '}') {
        braceCount--;
        if (braceCount < 0) {
          issues.push({
            line: i + 1,
            column: 1,
            severity: 'error',
            message: 'Unexpected closing brace'
          });
          braceCount = 0;
        }
      }
    }
  }
  
  if (braceCount > 0) {
    issues.push({
      line: lastOpenLine,
      column: 1,
      severity: 'error',
      message: `Unclosed brace (${braceCount} unclosed)`
    });
  }
  
  return issues;
}

/**
 * Run diagnostics on a single file
 */
async function diagnoseFile(filePath) {
  const abs = path.isAbsolute(filePath) ? filePath : path.join(process.cwd(), filePath);
  const ext = getExtension(abs);
  
  // Check if file exists
  try {
    await fs.access(abs);
  } catch {
    return {
      path: filePath,
      exists: false,
      issues: [{
        line: 0,
        column: 0,
        severity: 'error',
        message: 'File not found'
      }]
    };
  }
  
  // Read file content
  let content;
  try {
    content = await fs.readFile(abs, 'utf8');
  } catch (err) {
    return {
      path: filePath,
      exists: true,
      issues: [{
        line: 0,
        column: 0,
        severity: 'error',
        message: `Failed to read file: ${err.message}`
      }]
    };
  }
  
  let issues = [];
  
  // Run appropriate checker based on file type
  switch (ext) {
    case '.js':
    case '.mjs':
    case '.cjs':
      issues = await checkJavaScript(abs, content);
      break;
    
    case '.ts':
    case '.tsx':
    case '.jsx':
      // For TypeScript/JSX, just do basic JS check
      // Full TS checking would require tsconfig
      issues = await checkJavaScript(abs, content);
      break;
    
    case '.json':
      issues = await checkJSON(abs, content);
      break;
    
    case '.py':
      issues = await checkPython(abs);
      break;
    
    case '.html':
    case '.htm':
      issues = await checkHTML(abs, content);
      break;
    
    case '.css':
      issues = await checkCSS(abs, content);
      break;
    
    default:
      // For unknown types, just verify the file is readable
      issues = [];
  }
  
  return {
    path: filePath,
    exists: true,
    issues
  };
}

/**
 * Format issues for display
 */
function formatIssues(results) {
  const lines = [];
  let totalIssues = 0;
  let filesWithIssues = 0;
  
  for (const result of results) {
    if (!result.exists) {
      lines.push(`${result.path}: File not found`);
      totalIssues++;
      filesWithIssues++;
      continue;
    }
    
    if (result.issues.length === 0) {
      lines.push(`${result.path}: No issues found`);
      continue;
    }
    
    filesWithIssues++;
    lines.push(`${result.path}:`);
    
    for (const issue of result.issues) {
      totalIssues++;
      const loc = issue.line > 0 ? `  Line ${issue.line}` : '  ';
      const sev = issue.severity === 'error' ? 'ERROR' : 'WARNING';
      lines.push(`${loc}: [${sev}] ${issue.message}`);
    }
    
    lines.push('');
  }
  
  // Summary
  lines.push('---');
  if (totalIssues === 0) {
    lines.push(`Checked ${results.length} file(s): No issues found`);
  } else {
    lines.push(`Checked ${results.length} file(s): ${totalIssues} issue(s) in ${filesWithIssues} file(s)`);
  }
  
  return lines.join('\n');
}

/**
 * Main diagnostics command handler
 */
module.exports = async function diagnosticsCmd(params) {
  const paths = params.paths;
  
  if (!paths || !Array.isArray(paths) || paths.length === 0) {
    return {
      success: false,
      error: 'No file paths provided',
      error_code: 'ERR_INVALID_INPUT',
      display_content: 'Error: No file paths provided',
      results: [],
      summary: { total_files: 0, files_with_issues: 0, total_issues: 0 }
    };
  }
  
  await appendLog(`[tools.diagnostics] Checking ${paths.length} file(s)`);
  
  // Run diagnostics on all files
  const results = [];
  for (const filePath of paths) {
    const result = await diagnoseFile(filePath);
    results.push(result);
  }
  
  // Calculate summary
  const totalIssues = results.reduce((sum, r) => sum + r.issues.length, 0);
  const filesWithIssues = results.filter(r => r.issues.length > 0).length;
  
  const displayContent = formatIssues(results);
  
  await appendLog(`[tools.diagnostics] Found ${totalIssues} issue(s) in ${filesWithIssues}/${results.length} file(s)`);
  
  return {
    success: true,
    display_content: displayContent,
    results,
    summary: {
      total_files: results.length,
      files_with_issues: filesWithIssues,
      total_issues: totalIssues
    }
  };
};
