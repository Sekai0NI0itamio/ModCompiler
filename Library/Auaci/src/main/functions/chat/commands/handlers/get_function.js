// src/main/functions/chat/commands/handlers/get_function.js
// Extract function/method/class blocks from code files using AST-like parsing

const path = require('path');
const fs = require('fs').promises;
const { appendLog } = require('../lib/utils');

/**
 * Get the current working directory for the chat session
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
 * Language-specific patterns for detecting code blocks
 */
const LANGUAGE_PATTERNS = {
  javascript: {
    extensions: ['.js', '.jsx', '.mjs', '.cjs'],
    patterns: [
      // Named function declaration: function name(...)
      { type: 'function', regex: /^(\s*)(async\s+)?function\s+(\w+)\s*\([^)]*\)\s*\{/gm, nameGroup: 3 },
      // Arrow function assigned to const/let/var: const name = (...) =>
      { type: 'function', regex: /^(\s*)(export\s+)?(const|let|var)\s+(\w+)\s*=\s*(async\s+)?\([^)]*\)\s*=>/gm, nameGroup: 4 },
      // Arrow function assigned (no parens): const name = async arg =>
      { type: 'function', regex: /^(\s*)(export\s+)?(const|let|var)\s+(\w+)\s*=\s*(async\s+)?\w+\s*=>/gm, nameGroup: 4 },
      // Method in object/class: name(...) { or async name(...) {
      { type: 'method', regex: /^(\s*)(async\s+)?(\w+)\s*\([^)]*\)\s*\{/gm, nameGroup: 3 },
      // Class declaration
      { type: 'class', regex: /^(\s*)(export\s+)?(default\s+)?class\s+(\w+)/gm, nameGroup: 4 },
      // Export function
      { type: 'function', regex: /^(\s*)export\s+(async\s+)?function\s+(\w+)\s*\([^)]*\)\s*\{/gm, nameGroup: 3 },
      // Export default function
      { type: 'function', regex: /^(\s*)export\s+default\s+(async\s+)?function\s+(\w+)?\s*\([^)]*\)\s*\{/gm, nameGroup: 3 },
    ]
  },
  typescript: {
    extensions: ['.ts', '.tsx'],
    patterns: [
      // Same as JS plus type annotations
      { type: 'function', regex: /^(\s*)(async\s+)?function\s+(\w+)\s*[<(]/gm, nameGroup: 3 },
      { type: 'function', regex: /^(\s*)(export\s+)?(const|let|var)\s+(\w+)\s*[:<]\s*/gm, nameGroup: 4 },
      { type: 'method', regex: /^(\s*)(async\s+)?(public|private|protected)?\s*(\w+)\s*[<(]/gm, nameGroup: 4 },
      { type: 'class', regex: /^(\s*)(export\s+)?(default\s+)?class\s+(\w+)/gm, nameGroup: 4 },
      { type: 'interface', regex: /^(\s*)(export\s+)?interface\s+(\w+)/gm, nameGroup: 3 },
      { type: 'type', regex: /^(\s*)(export\s+)?type\s+(\w+)\s*=/gm, nameGroup: 3 },
    ]
  },
  python: {
    extensions: ['.py'],
    patterns: [
      // Function: def name(...)
      { type: 'function', regex: /^(\s*)(async\s+)?def\s+(\w+)\s*\(/gm, nameGroup: 3 },
      // Class: class Name
      { type: 'class', regex: /^(\s*)class\s+(\w+)/gm, nameGroup: 2 },
    ]
  },
  java: {
    extensions: ['.java'],
    patterns: [
      { type: 'method', regex: /^(\s*)(public|private|protected)?\s*(static)?\s*[\w<>\[\]]+\s+(\w+)\s*\(/gm, nameGroup: 4 },
      { type: 'class', regex: /^(\s*)(public|private)?\s*(abstract)?\s*class\s+(\w+)/gm, nameGroup: 4 },
    ]
  },
  go: {
    extensions: ['.go'],
    patterns: [
      { type: 'function', regex: /^func\s+(\w+)\s*\(/gm, nameGroup: 1 },
      { type: 'method', regex: /^func\s+\([^)]+\)\s+(\w+)\s*\(/gm, nameGroup: 1 },
      { type: 'type', regex: /^type\s+(\w+)\s+(struct|interface)/gm, nameGroup: 1 },
    ]
  },
  rust: {
    extensions: ['.rs'],
    patterns: [
      { type: 'function', regex: /^(\s*)(pub\s+)?(async\s+)?fn\s+(\w+)/gm, nameGroup: 4 },
      { type: 'struct', regex: /^(\s*)(pub\s+)?struct\s+(\w+)/gm, nameGroup: 3 },
      { type: 'impl', regex: /^(\s*)impl\s+(\w+)/gm, nameGroup: 2 },
    ]
  }
};

/**
 * Detect language from file extension
 */
function detectLanguage(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  for (const [lang, config] of Object.entries(LANGUAGE_PATTERNS)) {
    if (config.extensions.includes(ext)) return lang;
  }
  return 'javascript'; // Default fallback
}

/**
 * Find matching closing brace/bracket considering nesting
 */
function findBlockEnd(content, startIndex, openChar = '{', closeChar = '}') {
  let depth = 0;
  let inString = false;
  let stringChar = '';
  let inComment = false;
  let inLineComment = false;
  
  for (let i = startIndex; i < content.length; i++) {
    const char = content[i];
    const nextChar = content[i + 1];
    const prevChar = content[i - 1];
    
    // Handle line comments
    if (!inString && !inComment && char === '/' && nextChar === '/') {
      inLineComment = true;
      continue;
    }
    if (inLineComment && char === '\n') {
      inLineComment = false;
      continue;
    }
    if (inLineComment) continue;
    
    // Handle block comments
    if (!inString && !inComment && char === '/' && nextChar === '*') {
      inComment = true;
      i++;
      continue;
    }
    if (inComment && char === '*' && nextChar === '/') {
      inComment = false;
      i++;
      continue;
    }
    if (inComment) continue;
    
    // Handle strings
    if (!inString && (char === '"' || char === "'" || char === '`')) {
      inString = true;
      stringChar = char;
      continue;
    }
    if (inString && char === stringChar && prevChar !== '\\') {
      inString = false;
      continue;
    }
    if (inString) continue;
    
    // Count braces
    if (char === openChar) depth++;
    if (char === closeChar) {
      depth--;
      if (depth === 0) return i;
    }
  }
  
  return -1; // Not found
}

/**
 * Find Python block end using indentation
 */
function findPythonBlockEnd(lines, startLineIndex) {
  if (startLineIndex >= lines.length) return startLineIndex;
  
  const startLine = lines[startLineIndex];
  const baseIndent = startLine.match(/^(\s*)/)[1].length;
  
  // Find the colon that starts the block
  let blockStarted = startLine.includes(':');
  
  for (let i = startLineIndex + 1; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();
    
    // Skip empty lines and comments
    if (!trimmed || trimmed.startsWith('#')) continue;
    
    const currentIndent = line.match(/^(\s*)/)[1].length;
    
    // If we find a line with same or less indentation, block ended
    if (currentIndent <= baseIndent) {
      return i - 1;
    }
  }
  
  return lines.length - 1;
}

/**
 * Extract all code symbols from a file
 */
function extractSymbols(content, language) {
  const config = LANGUAGE_PATTERNS[language] || LANGUAGE_PATTERNS.javascript;
  const lines = content.split('\n');
  const symbols = [];
  
  for (const pattern of config.patterns) {
    const regex = new RegExp(pattern.regex.source, pattern.regex.flags);
    let match;
    
    while ((match = regex.exec(content)) !== null) {
      const name = match[pattern.nameGroup];
      if (!name) continue;
      
      const startIndex = match.index;
      const lineNumber = content.substring(0, startIndex).split('\n').length;
      
      // Find block boundaries
      let endLineNumber;
      let blockContent;
      
      if (language === 'python') {
        endLineNumber = findPythonBlockEnd(lines, lineNumber - 1) + 1;
        blockContent = lines.slice(lineNumber - 1, endLineNumber).join('\n');
      } else {
        // Find opening brace
        const braceIndex = content.indexOf('{', startIndex);
        if (braceIndex === -1) continue;
        
        const endIndex = findBlockEnd(content, braceIndex, '{', '}');
        if (endIndex === -1) continue;
        
        endLineNumber = content.substring(0, endIndex + 1).split('\n').length;
        blockContent = content.substring(startIndex, endIndex + 1);
      }
      
      symbols.push({
        name,
        type: pattern.type,
        start_line: lineNumber,
        end_line: endLineNumber,
        line_count: endLineNumber - lineNumber + 1,
        content: blockContent
      });
    }
  }
  
  // Remove duplicates and sort by line number
  const seen = new Set();
  return symbols
    .filter(s => {
      const key = `${s.name}:${s.start_line}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => a.start_line - b.start_line);
}

/**
 * Get function/symbol from file
 * Params: { path: string, name?: string, list_only?: boolean }
 */
module.exports = async function getFunctionCmd(params) {
  const filePath = params.path;
  if (!filePath) {
    return { success: false, error: 'get_function: missing path', error_code: 'ERR_INVALID_INPUT' };
  }
  
  const abs = path.isAbsolute(filePath) ? filePath : path.join(getChatCwd(), filePath);
  
  try {
    await fs.stat(abs);
  } catch (e) {
    if (e.code === 'ENOENT') {
      return { success: false, error: `get_function: file not found: ${filePath}`, error_code: 'ERR_NOT_FOUND', path: filePath };
    }
    return { success: false, error: `get_function: ${e.message}`, error_code: e.code || 'ERR_IO', path: filePath };
  }
  
  let content;
  try {
    content = await fs.readFile(abs, 'utf8');
  } catch (e) {
    return { success: false, error: `get_function: failed to read file: ${e.message}`, error_code: e.code || 'ERR_IO', path: filePath };
  }
  const language = detectLanguage(filePath);
  const symbols = extractSymbols(content, language);
  
  // List mode - just return symbol names and locations
  if (params.list_only || params.list) {
    await appendLog(`[tools.get_function] list mode for ${filePath}: found ${symbols.length} symbols`);
    return {
      success: true,
      path: filePath,
      language,
      symbols: symbols.map(s => ({
        name: s.name,
        type: s.type,
        start_line: s.start_line,
        end_line: s.end_line,
        line_count: s.line_count
      })),
      total_symbols: symbols.length
    };
  }
  
  // Get specific symbol by name
  const targetName = params.name || params.function_name || params.symbol;
  if (!targetName) {
    // No name specified, return list
    await appendLog(`[tools.get_function] no name specified, returning list for ${filePath}`);
    return {
      success: true,
      path: filePath,
      language,
      symbols: symbols.map(s => ({
        name: s.name,
        type: s.type,
        start_line: s.start_line,
        end_line: s.end_line,
        line_count: s.line_count
      })),
      total_symbols: symbols.length,
      hint: 'Specify "name" parameter to get the full content of a specific symbol'
    };
  }
  
  // Find the symbol
  const symbol = symbols.find(s => s.name === targetName);
  if (!symbol) {
    const available = symbols.map(s => s.name).join(', ');
    return {
      success: false,
      error: `get_function: symbol "${targetName}" not found in ${filePath}. Available: ${available || 'none'}`,
      error_code: 'ERR_NOT_FOUND',
      path: filePath,
      available: available ? available.split(', ') : []
    };
  }
  
  // Add line numbers to content
  const contentLines = symbol.content.split('\n');
  const numberedContent = contentLines
    .map((line, i) => `${symbol.start_line + i}|${line}`)
    .join('\n');
  
  await appendLog(`[tools.get_function] extracted ${symbol.type} "${targetName}" from ${filePath} (lines ${symbol.start_line}-${symbol.end_line})`);
  
  return {
    success: true,
    path: filePath,
    language,
    symbol_name: symbol.name,
    function_name: symbol.name,
    start_line: symbol.start_line,
    end_line: symbol.end_line,
    line_count: symbol.line_count,
    code: numberedContent,
    symbol: {
      name: symbol.name,
      type: symbol.type,
      start_line: symbol.start_line,
      end_line: symbol.end_line,
      line_count: symbol.line_count,
      content: numberedContent
    }
  };
};
