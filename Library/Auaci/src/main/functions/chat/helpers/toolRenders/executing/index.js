// src/main/functions/chat/helpers/toolRenders/executing/index.js
// Index file for executing (before completion) tool renders

const shared = require('../shared');

// Import individual tool renderers
const renderLs = require('./ls');
const renderView = require('./view');
const renderGrep = require('./grep');
const renderFindFiles = require('./find_files');
const renderGlob = require('./glob');
const renderBash = require('./bash');
const renderWriteToFile = require('./write_to_file');
const renderCreateFile = require('./create_file');
const renderApplyPatch = require('./apply_patch');
const renderStrReplace = require('./str_replace');
const renderFsAppend = require('./fs_append');
const renderWebFetch = require('./web_fetch');
const renderWebSearch = require('./web_search');
const renderDiagnostics = require('./diagnostics');
const renderAsk = require('./ask');
const renderDelete = require('./delete');
const renderTodo = require('./todo');
const renderContextSearch = require('./context_search');
const renderSemanticSearch = require('./semantic_search');
const renderTxtlize = require('./txtlize');
const renderGeneric = require('./generic');
const renderAttemptCompletion = require('./attempt_completion');
// New AST-based and precise editing tools
const renderGetFunction = require('./get_function');
const renderEditFunction = require('./edit_function');
const renderEditLines = require('./edit_lines');

/**
 * Get the executing state renderer for a tool
 * @param {string} toolName - The tool name (lowercase)
 * @returns {function} - The render function
 */
function getExecutingRenderer(toolName) {
  const name = String(toolName || '').toLowerCase();
  
  const renderers = {
    'ls': renderLs,
    'view': renderView,
    'read_any_files': renderView,
    'grep': renderGrep,
    'find_files': renderFindFiles,
    'glob': renderGlob,
    'bash': renderBash,
    'run_command': renderBash,
    'write_to_file': renderWriteToFile,
    'create_file': renderCreateFile,
    'apply_patch': renderApplyPatch,
    'str_replace': renderStrReplace,
    'fs_append': renderFsAppend,
    'web_fetch': renderWebFetch,
    'web_search': renderWebSearch,
    'diagnostics': renderDiagnostics,
    'ask': renderAsk,
    'delete_file_folder_with_permission': renderDelete,
    'create_todo_list': renderTodo,
    'read_todos': renderTodo,
    'add_todos': renderTodo,
    'remove_todos': renderTodo,
    'mark_todo_as_done': renderTodo,
    'update_todo_status': renderTodo,
    'context_search': renderContextSearch,
    'context-search': renderContextSearch,
    'semantic_search': renderSemanticSearch,
    'txtlize': renderTxtlize,
    'attempt_completion': renderAttemptCompletion,
    // New AST-based and precise editing tools
    'get_function': renderGetFunction,
    'edit_function': renderEditFunction,
    'edit_lines': renderEditLines
  };
  
  return renderers[name] || renderGeneric;
}

/**
 * Render a tool in executing state
 * @param {string} toolName - The tool name
 * @param {object} input - The tool input parameters
 * @param {string} toolIndex - The tool index for interactive tools
 * @returns {string} - HTML string for the tool body
 */
function renderExecuting(toolName, input, toolIndex) {
  shared.ensureSpinnerStyles();
  const renderer = getExecutingRenderer(toolName);
  // Pass toolIndex for interactive tools like ask
  return renderer(input, toolIndex);
}

module.exports = {
  getExecutingRenderer,
  renderExecuting
};
