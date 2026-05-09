// src/main/functions/chat/helpers/claudeToolConverter.js
// Converts Claude Code XML tool calls to OpenAI/standard format
// Handles various Claude XML formats:
// 1. <tool_call><function=name><parameter=key>value</parameter></function></tool_call>
// 2. <function=name><parameter=key>value</parameter></function>
// 3. <function_calls><invoke name="...">...</invoke>
// 4. <function=command "args"</parameter></function> (malformed shorthand)
// 5. <function=cat "/path/to/file"</parameter></function> (shell command as function)


/**
 * Shell commands that should be converted to run_command
 */
const SHELL_COMMANDS = new Set([
  'cat', 'ls', 'cd', 'pwd', 'echo', 'grep', 'find', 'head', 'tail', 'wc',
  'mkdir', 'rm', 'cp', 'mv', 'touch', 'chmod', 'chown', 'curl', 'wget',
  'npm', 'npx', 'yarn', 'pnpm', 'node', 'python', 'python3', 'pip', 'pip3',
  'git', 'docker', 'make', 'cargo', 'go', 'java', 'javac', 'ruby', 'gem',
  'brew', 'apt', 'apt-get', 'yum', 'dnf', 'pacman', 'snap',
  'sed', 'awk', 'sort', 'uniq', 'cut', 'tr', 'xargs', 'tee',
  'tar', 'zip', 'unzip', 'gzip', 'gunzip', 'bzip2',
  'ssh', 'scp', 'rsync', 'ftp', 'sftp',
  'ps', 'top', 'htop', 'kill', 'killall', 'pkill',
  'df', 'du', 'free', 'uname', 'whoami', 'which', 'whereis',
  'less', 'more', 'vi', 'vim', 'nano', 'emacs',
  'diff', 'patch', 'file', 'stat', 'readlink', 'realpath',
  'env', 'export', 'source', 'alias', 'history',
  'test', 'true', 'false', 'exit', 'return',
  'open', 'xdg-open', 'start', // OS-specific open commands
  'code', 'subl', 'atom', // Editor commands
  'tsc', 'eslint', 'prettier', 'jest', 'mocha', 'vitest', // JS tools
  'rustc', 'rustup', 'clippy', // Rust tools
  'swift', 'swiftc', 'xcodebuild', // Swift/iOS tools
  'flutter', 'dart', // Flutter/Dart
  'dotnet', 'nuget', // .NET
  'composer', 'php', 'artisan', // PHP
  'bundle', 'rails', 'rake', // Ruby
  'gradle', 'mvn', 'ant', // Java build tools
  'terraform', 'kubectl', 'helm', 'aws', 'gcloud', 'az', // Cloud/DevOps
]);

/**
 * Claude Code tool name mappings to our standard tool names
 */
const CLAUDE_TOOL_MAPPINGS = {
  // Editor tools
  'editor': 'view',
  'str_replace_editor': 'str_replace',
  'read_file': 'view',
  'read': 'view',
  'view': 'view',
  'write_file': 'write_to_file',
  'write': 'write_to_file',
  'create_file': 'create_file',
  'create': 'create_file',
  
  // Command tools
  'execute_command': 'run_command',
  'bash': 'run_command',
  'terminal': 'run_command',
  'shell': 'run_command',
  'run': 'run_command',
  'exec': 'run_command',
  'execute': 'run_command',
  
  // File tools
  'list_files': 'ls',
  'list_directory': 'ls',
  'list': 'ls',
  'dir': 'ls',
  'search_files': 'grep',
  'search': 'grep',
  'find_files': 'find_files',
  'glob_files': 'glob',
  'glob': 'glob',
  'delete_file': 'delete_file_folder_with_permission',
  'delete': 'delete_file_folder_with_permission',
  'remove': 'delete_file_folder_with_permission',
  
  // Web tools
  'web_search': 'web_search',
  'fetch_url': 'web_fetch',
  'web_fetch': 'web_fetch',
  'fetch': 'web_fetch',
  
  // Other
  'attempt_completion': 'attempt_completion',
  'complete': 'attempt_completion',
  'done': 'attempt_completion',
  'ask_followup_question': 'ask',
  'ask': 'ask',
  'question': 'ask'
};

/**
 * Claude editor command mappings
 */
const EDITOR_COMMAND_MAPPINGS = {
  'view': 'view',
  'read': 'view',
  'write': 'write_to_file',
  'create': 'create_file',
  'str_replace': 'str_replace',
  'insert': 'fs_append',
  'undo_edit': null // Not supported
};

/**
 * Cache for compiled regex patterns
 * OPTIMIZED: Pre-compile patterns to avoid recompilation
 */
const regexPatterns = {
  toolCall: /<tool_call>/i,
  function: /<function\s*=\s*[a-zA-Z_]/i,
  functionCalls: /<function_calls>/i,
  invoke: /<invoke\s+name\s*=/i,
  shellCmd: /<function\s*=\s*(?:cat|ls|grep|find|head|tail|npm|node|python|git|docker|curl|wget)\s/i
};

/**
 * Check if text contains Claude Code XML tool calls
 * @param {string} text - Text to check
 * @returns {boolean}
 * OPTIMIZED: Early exit, no multiple regex tests
 */
function hasClaudeToolCalls(text) {
  if (!text || typeof text !== 'string') return false;
  
  // Quick checks with pre-compiled patterns
  if (regexPatterns.toolCall.test(text)) return true;
  if (regexPatterns.function.test(text)) return true;
  if (regexPatterns.functionCalls.test(text)) return true;
  if (regexPatterns.invoke.test(text)) return true;
  if (regexPatterns.shellCmd.test(text)) return true;
  
  return false;
}

/**
 * Parse a single Claude Code XML tool call
 * Handles multiple formats:
 * 1. <function=name><parameter=key>value</parameter>...</function>
 * 2. <function=command "args"</parameter></function> (malformed shorthand)
 * 3. <function=cat "/path/to/file"</parameter></function> (shell command as function)
 * @param {string} xmlBlock - The XML block to parse
 * @returns {object|null} - Parsed tool { name, input } or null
 */
function parseClaudeFunctionCall(xmlBlock) {
  if (!xmlBlock) return null;
  
  try {
    // First, try to detect malformed/shorthand format
    // Pattern: <function=command "arg1" arg2</parameter></function>
    // or: <function=cat "/path/to/file"</parameter></function>
    const malformedMatch = xmlBlock.match(/<function\s*=\s*([a-zA-Z_][a-zA-Z0-9_-]*)\s+([^>]*?)(?:<\/parameter>|>)/i);
    
    if (malformedMatch) {
      const commandOrName = malformedMatch[1].trim();
      const argsString = malformedMatch[2].trim();
      
      // Check if this is a shell command being used as function name
      if (isShellCommand(commandOrName)) {
        // This is a shell command like: <function=cat "/path/to/file"
        // Convert to run_command with the full command
        const fullCommand = `${commandOrName} ${cleanArgString(argsString)}`;
        return {
          name: 'run_command',
          input: { command: fullCommand }
        };
      }
      
      // Check if it's a known tool with inline argument
      const mappedName = mapClaudeToolName(commandOrName);
      if (mappedName !== commandOrName || CLAUDE_TOOL_MAPPINGS[commandOrName.toLowerCase()]) {
        // It's a known tool, try to parse the argument
        const input = parseInlineArgs(mappedName, argsString);
        return { name: mappedName, input };
      }
    }
    
    // Standard format: <function=name>
    const funcMatch = xmlBlock.match(/<function\s*=\s*([a-zA-Z_][a-zA-Z0-9_-]*)\s*>/i);
    if (!funcMatch) {
      // Try alternate format without closing >
      const altMatch = xmlBlock.match(/<function\s*=\s*([a-zA-Z_][a-zA-Z0-9_-]*)/i);
      if (!altMatch) return null;
      
      // This might be a malformed tag, try to extract what we can
      const rawName = altMatch[1];
      if (isShellCommand(rawName)) {
        // Extract everything after the command name as the argument
        const afterName = xmlBlock.slice(altMatch.index + altMatch[0].length);
        const argMatch = afterName.match(/^\s*["']?([^<"']+)["']?/);
        const arg = argMatch ? argMatch[1].trim() : '';
        return {
          name: 'run_command',
          input: { command: arg ? `${rawName} ${arg}` : rawName }
        };
      }
    }
    
    let rawName = funcMatch ? funcMatch[1] : '';
    const input = {};
    
    // Check if this is a shell command used as function name
    if (isShellCommand(rawName)) {
      // Look for the argument in the rest of the block
      const afterFunc = xmlBlock.slice(funcMatch ? funcMatch.index + funcMatch[0].length : 0);
      
      // Try to find parameter content
      const paramMatch = afterFunc.match(/<parameter[^>]*>([\s\S]*?)<\/parameter>/i);
      if (paramMatch) {
        const arg = paramMatch[1].trim();
        return {
          name: 'run_command',
          input: { command: `${rawName} ${arg}` }
        };
      }
      
      // Try to find inline argument
      const inlineMatch = afterFunc.match(/^\s*["']?([^<]+?)["']?\s*(?:<|$)/);
      if (inlineMatch && inlineMatch[1].trim()) {
        return {
          name: 'run_command',
          input: { command: `${rawName} ${inlineMatch[1].trim()}` }
        };
      }
      
      // Just the command itself
      return {
        name: 'run_command',
        input: { command: rawName }
      };
    }
    
    // Extract all parameters: <parameter=key>value</parameter>
    const paramRe = /<parameter\s*=\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*>([\s\S]*?)<\/parameter>/gi;
    let paramMatch;
    
    while ((paramMatch = paramRe.exec(xmlBlock)) !== null) {
      const key = paramMatch[1];
      let value = paramMatch[2];
      
      // Try to parse value as JSON if it looks like JSON
      value = tryParseValue(value);
      input[key] = value;
    }
    
    // Also try alternate parameter format: <parameter name="key">value</parameter>
    const paramRe2 = /<parameter\s+name\s*=\s*["']([^"']+)["']\s*>([\s\S]*?)<\/parameter>/gi;
    while ((paramMatch = paramRe2.exec(xmlBlock)) !== null) {
      const key = paramMatch[1];
      let value = paramMatch[2];
      value = tryParseValue(value);
      if (!input[key]) input[key] = value;
    }
    
    // Handle special case: editor tool with command parameter
    if (rawName.toLowerCase() === 'editor' && input.command) {
      const editorCommand = String(input.command).toLowerCase();
      const mappedTool = EDITOR_COMMAND_MAPPINGS[editorCommand];
      if (mappedTool) {
        rawName = mappedTool;
        // Remap parameters for editor tool
        if (input.file_path && !input.path) {
          input.path = input.file_path;
          delete input.file_path;
        }
        delete input.command;
      }
    }
    
    // Map Claude tool name to our standard name
    const name = mapClaudeToolName(rawName);
    
    // Normalize input parameters
    const normalizedInput = normalizeToolInput(name, input);
    
    return { name, input: normalizedInput };
  } catch (err) {
    console.warn('[claudeToolConverter] Error parsing function call:', err);
    return null;
  }
}

/**
 * Check if a string is a known shell command
 */
function isShellCommand(str) {
  if (!str) return false;
  return SHELL_COMMANDS.has(str.toLowerCase().trim());
}

/**
 * Clean argument string from quotes and extra whitespace
 */
function cleanArgString(str) {
  if (!str) return '';
  // Remove surrounding quotes if present
  let cleaned = str.trim();
  if ((cleaned.startsWith('"') && cleaned.endsWith('"')) ||
      (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
    cleaned = cleaned.slice(1, -1);
  }
  // Also handle case where closing quote is missing (malformed)
  else if (cleaned.startsWith('"') && !cleaned.endsWith('"')) {
    cleaned = cleaned.slice(1);
  }
  else if (cleaned.startsWith("'") && !cleaned.endsWith("'")) {
    cleaned = cleaned.slice(1);
  }
  return cleaned.trim();
}

/**
 * Parse inline arguments for a tool
 */
function parseInlineArgs(toolName, argsString) {
  const input = {};
  const cleaned = cleanArgString(argsString);
  
  const name = toolName.toLowerCase();
  
  // Map inline arg to appropriate parameter based on tool
  if (name === 'view' || name === 'read_file' || name === 'editor') {
    input.path = cleaned;
  } else if (name === 'run_command' || name === 'bash' || name === 'shell') {
    input.command = cleaned;
  } else if (name === 'ls' || name === 'list_files') {
    input.path = cleaned || '.';
  } else if (name === 'grep' || name === 'search_files') {
    input.queries = [cleaned];
  } else if (name === 'write_to_file' || name === 'create_file') {
    input.path = cleaned;
  } else {
    // Generic: use as first positional argument
    input.arg = cleaned;
  }
  
  return input;
}

/**
 * Parse Claude <invoke> format
 * Format: <invoke name="tool_name"><parameter name="key">value</parameter></invoke>
 * @param {string} xmlBlock - The XML block to parse
 * @returns {object|null} - Parsed tool { name, input } or null
 */
function parseClaudeInvokeCall(xmlBlock) {
  if (!xmlBlock) return null;
  
  try {
    // Extract tool name: <invoke name="tool_name">
    const invokeMatch = xmlBlock.match(/<invoke\s+name\s*=\s*["']([^"']+)["']\s*>/i);
    if (!invokeMatch) return null;
    
    let rawName = invokeMatch[1];
    const input = {};
    
    // Extract parameters: <parameter name="key">value</parameter>
    const paramRe = /<parameter\s+name\s*=\s*["']([^"']+)["']\s*>([\s\S]*?)<\/parameter>/gi;
    let paramMatch;
    
    while ((paramMatch = paramRe.exec(xmlBlock)) !== null) {
      const key = paramMatch[1];
      let value = paramMatch[2];
      value = tryParseValue(value);
      input[key] = value;
    }
    
    const name = mapClaudeToolName(rawName);
    const normalizedInput = normalizeToolInput(name, input);
    
    return { name, input: normalizedInput };
  } catch (err) {
    console.warn('[claudeToolConverter] Error parsing invoke call:', err);
    return null;
  }
}

/**
 * Extract all Claude tool calls from text
 * @param {string} text - Text containing Claude XML tool calls
 * @returns {Array} - Array of { name, input, originalXml, startIndex, endIndex }
 */
function extractClaudeToolCalls(text) {
  if (!text || typeof text !== 'string') return [];
  
  const tools = [];
  const protectedRanges = findCodeFences(text);
  
  // Pattern 1: <tool_call>...</tool_call> (may contain various inner formats)
  const toolCallRe = /<tool_call>\s*([\s\S]*?)\s*<\/tool_call>/gi;
  let match;
  
  while ((match = toolCallRe.exec(text)) !== null) {
    if (isInsideProtectedRange(match.index, protectedRanges)) continue;
    
    const innerContent = match[1];
    const parsed = parseClaudeFunctionCall(innerContent);
    if (parsed && parsed.name) {
      tools.push({
        ...parsed,
        originalXml: match[0],
        startIndex: match.index,
        endIndex: match.index + match[0].length
      });
    }
  }
  
  // Pattern 2: Standalone <function=...>...</function>
  // More permissive regex to catch malformed tags
  const funcRe = /<function\s*=\s*([^\s>]+)[^>]*>[\s\S]*?<\/function>/gi;
  
  while ((match = funcRe.exec(text)) !== null) {
    if (isInsideProtectedRange(match.index, protectedRanges)) continue;
    
    // Skip if already captured by tool_call pattern
    const alreadyCaptured = tools.some(t => 
      match.index >= t.startIndex && match.index < t.endIndex
    );
    if (alreadyCaptured) continue;
    
    const parsed = parseClaudeFunctionCall(match[0]);
    if (parsed && parsed.name) {
      tools.push({
        ...parsed,
        originalXml: match[0],
        startIndex: match.index,
        endIndex: match.index + match[0].length
      });
    }
  }
  
  // Pattern 3: Malformed <function=command "args"</parameter></function>
  // This catches cases like: <function=cat "/path"</parameter></function>
  const malformedRe = /<function\s*=\s*([a-zA-Z_][a-zA-Z0-9_-]*)\s+[^>]*<\/parameter>\s*<\/function>/gi;
  
  while ((match = malformedRe.exec(text)) !== null) {
    if (isInsideProtectedRange(match.index, protectedRanges)) continue;
    
    const alreadyCaptured = tools.some(t => 
      match.index >= t.startIndex && match.index < t.endIndex
    );
    if (alreadyCaptured) continue;
    
    const parsed = parseClaudeFunctionCall(match[0]);
    if (parsed && parsed.name) {
      tools.push({
        ...parsed,
        originalXml: match[0],
        startIndex: match.index,
        endIndex: match.index + match[0].length
      });
    }
  }
  
  // Pattern 4: <function_calls><invoke name="...">...</invoke></function_calls>
  const funcCallsRe = /<function_calls>\s*([\s\S]*?)\s*<\/function_calls>/gi;
  
  while ((match = funcCallsRe.exec(text)) !== null) {
    if (isInsideProtectedRange(match.index, protectedRanges)) continue;
    
    const innerContent = match[1];
    const invokeRe = /<invoke\s+name\s*=\s*["'][^"']+["']\s*>[\s\S]*?<\/invoke>/gi;
    let invokeMatch;
    
    while ((invokeMatch = invokeRe.exec(innerContent)) !== null) {
      const parsed = parseClaudeInvokeCall(invokeMatch[0]);
      if (parsed && parsed.name) {
        tools.push({
          ...parsed,
          originalXml: invokeMatch[0],
          startIndex: match.index,
          endIndex: match.index + match[0].length,
          isPartOfFunctionCalls: true
        });
      }
    }
  }
  
  // Pattern 5: Standalone <invoke name="...">...</invoke>
  const standaloneInvokeRe = /<invoke\s+name\s*=\s*["']([^"']+)["']\s*>[\s\S]*?<\/invoke>/gi;
  
  while ((match = standaloneInvokeRe.exec(text)) !== null) {
    if (isInsideProtectedRange(match.index, protectedRanges)) continue;
    
    const alreadyCaptured = tools.some(t => 
      match.index >= t.startIndex && match.index < t.endIndex
    );
    if (alreadyCaptured) continue;
    
    const parsed = parseClaudeInvokeCall(match[0]);
    if (parsed && parsed.name) {
      tools.push({
        ...parsed,
        originalXml: match[0],
        startIndex: match.index,
        endIndex: match.index + match[0].length
      });
    }
  }
  
  // Sort by start index
  tools.sort((a, b) => a.startIndex - b.startIndex);
  
  return tools;
}

/**
 * Convert Claude tool calls in text to OpenAI <tool_use> format
 * @param {string} text - Text with Claude XML tool calls
 * @returns {string} - Text with converted <tool_use> blocks
 */
function convertClaudeToOpenAI(text) {
  if (!text || typeof text !== 'string') return text;
  if (!hasClaudeToolCalls(text)) return text;
  
  const tools = extractClaudeToolCalls(text);
  if (tools.length === 0) return text;
  
  // Replace from end to start to preserve indices
  let result = text;
  for (let i = tools.length - 1; i >= 0; i--) {
    const tool = tools[i];
    
    // Create OpenAI format tool_use block
    const openAIBlock = `<tool_use>
${JSON.stringify({ name: tool.name, input: tool.input }, null, 2)}
</tool_use>`;
    
    result = result.slice(0, tool.startIndex) + openAIBlock + result.slice(tool.endIndex);
  }
  
  return result;
}

/**
 * Map Claude tool name to our standard tool name
 */
function mapClaudeToolName(name) {
  if (!name) return name;
  const lower = name.toLowerCase().trim();
  return CLAUDE_TOOL_MAPPINGS[lower] || name;
}

/**
 * Normalize tool input parameters
 */
function normalizeToolInput(toolName, input) {
  if (!input || typeof input !== 'object') return input || {};
  
  const normalized = { ...input };
  const name = String(toolName).toLowerCase();
  
  // Fix common malformed parameter patterns
  // Pattern: <parameter=editor>command>view</parameter> should be <parameter=command>view</parameter>
  // This happens when AI makes a typo like "editor>command" instead of "command"
  for (const [key, value] of Object.entries(normalized)) {
    if (typeof value === 'string') {
      // Check for pattern like "command>view" or "command>str_replace"
      const malformedMatch = value.match(/^([a-zA-Z_]+)>(.+)$/);
      if (malformedMatch) {
        const actualKey = malformedMatch[1]; // e.g., "command"
        const actualValue = malformedMatch[2]; // e.g., "view"
        
        // If the extracted key is a known parameter name, use it
        const knownParams = ['command', 'path', 'content', 'old_str', 'new_str', 'query', 'mode'];
        if (knownParams.includes(actualKey.toLowerCase())) {
          normalized[actualKey] = actualValue;
          delete normalized[key];
        }
      }
    }
  }
  
  // Common parameter renames
  if (normalized.file_path && !normalized.path) {
    normalized.path = normalized.file_path;
    delete normalized.file_path;
  }
  
  if (normalized.directory && !normalized.path && (name === 'ls' || name === 'list_files')) {
    normalized.path = normalized.directory;
    delete normalized.directory;
  }
  
  if (normalized.cmd && !normalized.command && (name === 'run_command' || name === 'bash')) {
    normalized.command = normalized.cmd;
    delete normalized.cmd;
  }
  
  if (normalized.search_term && !normalized.query && (name === 'grep' || name === 'search_files')) {
    normalized.queries = [normalized.search_term];
    delete normalized.search_term;
  }
  
  if (normalized.pattern && !normalized.queries && (name === 'grep' || name === 'search_files')) {
    normalized.queries = [normalized.pattern];
    delete normalized.pattern;
  }
  
  // Handle view_range for view tool
  if (normalized.view_range && !normalized.ranges && name === 'view') {
    // view_range might be "1-100" or [1, 100]
    const range = normalized.view_range;
    if (typeof range === 'string' && range.includes('-')) {
      const [start, end] = range.split('-').map(s => parseInt(s.trim(), 10));
      if (!isNaN(start) && !isNaN(end)) {
        normalized.ranges = [[start, end]];
      }
    } else if (Array.isArray(range) && range.length === 2) {
      normalized.ranges = [range];
    }
    delete normalized.view_range;
  }
  
  // Handle editor tool command parameter - map to correct tool
  // If we have a command parameter with value like "view", "str_replace", etc.
  // and the tool is "editor" or "view", we should use the command value as the tool action
  if ((normalized.command || normalized.cmd) && (name === 'view' || name === 'editor')) {
    const cmd = String(normalized.command || normalized.cmd).toLowerCase();
    // If command is an editor action, it's metadata not an actual command to run
    if (['view', 'str_replace', 'write', 'create', 'insert'].includes(cmd)) {
      delete normalized.command;
      delete normalized.cmd;
    }
  }
  
  return normalized;
}

/**
 * Try to parse a value as JSON or return as string
 */
function tryParseValue(value) {
  if (value === null || value === undefined) return value;
  
  const trimmed = String(value).trim();
  
  // Try JSON parse for objects/arrays
  if (trimmed.startsWith('{') || trimmed.startsWith('[') || trimmed.startsWith('"')) {
    try {
      return JSON.parse(trimmed);
    } catch (_) {}
  }
  
  // Try to parse as number
  if (/^-?\d+$/.test(trimmed)) {
    return parseInt(trimmed, 10);
  }
  if (/^-?\d+\.\d+$/.test(trimmed)) {
    return parseFloat(trimmed);
  }
  
  // Boolean
  if (trimmed.toLowerCase() === 'true') return true;
  if (trimmed.toLowerCase() === 'false') return false;
  
  // Return as string
  return value;
}

/**
 * Find code fence ranges in text
 */
function findCodeFences(text) {
  const ranges = [];
  const re = /```[\s\S]*?```/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    ranges.push({ start: m.index, end: re.lastIndex });
  }
  return ranges;
}

/**
 * Check if position is inside a protected range
 */
function isInsideProtectedRange(pos, ranges) {
  return ranges.some(r => pos >= r.start && pos < r.end);
}

/**
 * Convert Claude tool calls to executable tool objects
 * Returns array of tools ready for execution
 * @param {string} text - Text with Claude XML tool calls
 * @returns {Array} - Array of { name, input, id } ready for execution
 */
function extractExecutableTools(text) {
  const claudeTools = extractClaudeToolCalls(text);
  
  return claudeTools.map((tool, index) => ({
    id: `claude_tool_${Date.now()}_${index}`,
    name: tool.name,
    input: tool.input,
    type: 'function',
    function: {
      name: tool.name,
      arguments: JSON.stringify(tool.input)
    }
  }));
}

module.exports = {
  hasClaudeToolCalls,
  extractClaudeToolCalls,
  convertClaudeToOpenAI,
  parseClaudeFunctionCall,
  parseClaudeInvokeCall,
  extractExecutableTools,
  mapClaudeToolName,
  normalizeToolInput,
  isShellCommand,
  cleanArgString,
  parseInlineArgs,
  CLAUDE_TOOL_MAPPINGS,
  EDITOR_COMMAND_MAPPINGS,
  SHELL_COMMANDS
};
