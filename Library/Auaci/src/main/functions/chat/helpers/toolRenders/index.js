// src/main/functions/chat/helpers/toolRenders/index.js
// Main entry point for tool rendering

const shared = require('./shared');
const executing = require('./executing');
const completed = require('./completed');

/**
 * Render a tool in its executing (before completion) state
 * @param {string} toolName - The tool name
 * @param {object} input - The tool input parameters
 * @param {string} toolIndex - The tool index for interactive tools
 * @returns {string} - HTML string for the tool body
 */
function renderToolExecuting(toolName, input, toolIndex) {
  return executing.renderExecuting(toolName, input, toolIndex);
}

/**
 * Render a tool in its completed state
 * @param {string} toolName - The tool name
 * @param {object} input - The tool input parameters
 * @param {object} result - The tool result
 * @param {object} options - Additional options
 * @returns {string} - HTML string for the tool body
 */
function renderToolCompleted(toolName, input, result, options = {}) {
  return completed.renderCompleted(toolName, input, result, options);
}

/**
 * Check if a tool result indicates it's still executing
 * @param {object} result - The tool result
 * @returns {boolean}
 */
function isToolExecuting(result) {
  // No result at all - definitely executing
  if (result === null || result === undefined) return true;
  
  // Explicit waiting flag
  if (result.waiting === true) return true;
  
  // Explicit executing flag
  if (result._executing === true) return true;
  
  // Empty object with no meaningful keys - likely still executing
  if (typeof result === 'object') {
    const keys = Object.keys(result);
    // If the only keys are internal tracking keys, still executing
    if (keys.length === 0) return true;
    if (keys.length === 1 && keys[0] === '_tool_id') return true;
  }
  
  return false;
}

/**
 * Get the header title for a tool based on its state
 * @param {string} toolName - The tool name
 * @param {boolean} isExecuting - Whether the tool is still executing
 * @param {object} result - The tool result (if completed)
 * @returns {string} - The header title
 */
function getToolHeaderTitle(toolName, isExecuting, result) {
  const name = String(toolName || '').toLowerCase();
  const hasError = result && result.error;
  
  const titles = {
    'ls': isExecuting ? 'Scanning...' : 'ls',
    'view': isExecuting ? 'Reading...' : 'View',
    'read_any_files': isExecuting ? 'Reading...' : 'View',
    'grep': isExecuting ? 'Searching...' : 'Grep',
    'find_files': isExecuting ? 'Finding...' : 'Find files',
    'glob': isExecuting ? 'Matching...' : 'Glob',
    'bash': isExecuting ? 'Running...' : 'Terminal',
    'run_command': isExecuting ? 'Running...' : 'Terminal',
    'write_to_file': isExecuting ? 'Writing...' : (hasError ? 'Write failed' : 'Wrote to file'),
    'create_file': isExecuting ? 'Creating...' : (hasError ? 'Create failed' : 'Created file'),
    'apply_patch': isExecuting ? 'Patching...' : (hasError ? 'Patch failed' : 'Applied patch'),
    'str_replace': isExecuting ? 'Replacing...' : (hasError ? 'Replace failed' : 'Replaced'),
    'fs_append': isExecuting ? 'Appending...' : (hasError ? 'Append failed' : 'Appended'),
    'web_fetch': isExecuting ? 'Fetching...' : (hasError ? 'Fetch failed' : 'Fetched'),
    'web_search': isExecuting ? 'Searching...' : (hasError ? 'Search failed' : 'Web Search'),
    'diagnostics': isExecuting ? 'Checking...' : 'Diagnostics',
    'ask': isExecuting ? 'Waiting...' : 'Ask',
    'delete_file_folder_with_permission': isExecuting ? 'Preparing...' : 'Delete files/folders',
    'create_todo_list': isExecuting ? 'Creating...' : 'Todos',
    'read_todos': isExecuting ? 'Loading...' : 'Todos',
    'add_todos': isExecuting ? 'Adding...' : 'Todos',
    'remove_todos': isExecuting ? 'Removing...' : 'Todos',
    'mark_todo_as_done': isExecuting ? 'Updating...' : 'Todos',
    'update_todo_status': isExecuting ? 'Updating...' : 'Todos',
    'context_search': isExecuting ? 'Searching...' : 'Context search',
    'context-search': isExecuting ? 'Searching...' : 'Context search',
    'semantic_search': isExecuting ? 'Semantic searching...' : 'Semantic search',
    'txtlize': isExecuting ? 'Processing...' : 'Txtlize',
    'attempt_completion': 'Summary',
    // New AST-based and precise editing tools
    'get_function': isExecuting ? 'Extracting...' : 'Get function',
    'edit_function': isExecuting ? 'Editing...' : (hasError ? 'Edit failed' : 'Edited function'),
    'edit_lines': isExecuting ? 'Editing...' : (hasError ? 'Edit failed' : 'Edited lines')
  };
  
  return titles[name] || (isExecuting ? 'Executing...' : (name.charAt(0).toUpperCase() + name.slice(1)));
}

module.exports = {
  ...shared,
  renderToolExecuting,
  renderToolCompleted,
  isToolExecuting,
  getToolHeaderTitle,
  executing,
  completed
};
