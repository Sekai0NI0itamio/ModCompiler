// src/main/functions/chat/helpers/toolRenders/completed/index.js
// Entry point for completed tool state renderers

const shared = require('../shared');

// Import individual completed renderers
const viewRenderer = require('./view');
const viewGroupRenderer = require('./view_group');
const lsRenderer = require('./ls');
const grepRenderer = require('./grep');
const bashRenderer = require('./bash');
const patchRenderer = require('./apply_patch');
const writeRenderer = require('./write_to_file');
const findFilesRenderer = require('./find_files');
const globRenderer = require('./glob');
const askRenderer = require('./ask');
const deleteRenderer = require('./delete');
const todoRenderer = require('./todo');
const attemptCompletionRenderer = require('./attempt_completion');
const webFetchRenderer = require('./web_fetch');
const webSearchRenderer = require('./web_search');
const diagnosticsRenderer = require('./diagnostics');
const strReplaceRenderer = require('./str_replace');
const fsAppendRenderer = require('./fs_append');
const editFunctionRenderer = require('./edit_function');
const editLinesRenderer = require('./edit_lines');
const getFunctionRenderer = require('./get_function');
const semanticSearchRenderer = require('./semantic_search');
const genericRenderer = require('./generic');

/**
 * Render a completed tool
 * @param {string} toolName - The tool name
 * @param {object} input - The tool input
 * @param {object} result - The tool result
 * @param {object} options - Additional options (toolIndex, etc.)
 * @returns {string} - HTML string for the tool body
 */
function renderCompleted(toolName, input, result, options = {}) {
  const name = String(toolName || '').toLowerCase().trim();
  
  try {
    switch (name) {
      case 'view':
        return viewRenderer.render(input, result, options);
      case 'view_group':
        // View group is handled specially by the adapter, but provide fallback
        return viewGroupRenderer.renderGrouped(Array.isArray(input) ? input : [{ input, result }], options);
      case 'read_any_files':
      case 'read_multiple_files':
        return viewRenderer.renderMultiple(input, result, options);
      case 'ls':
        return lsRenderer.render(input, result, options);
      case 'grep':
      case 'context_search':
      case 'context-search':
        return grepRenderer.render(input, result, options);
      case 'semantic_search':
        return semanticSearchRenderer.render(input, result, options);
      case 'bash':
      case 'run_command':
        return bashRenderer.render(input, result, options);
      case 'apply_patch':
        return patchRenderer.render(input, result, options);
      case 'write_to_file':
        return writeRenderer.render(input, result, options);
      case 'create_file':
        return writeRenderer.render(input, result, { ...options, isCreate: true });
      case 'find_files':
        return findFilesRenderer.render(input, result, options);
      case 'glob':
        return globRenderer.render(input, result, options);
      case 'ask':
        return askRenderer.render(input, result, options);
      case 'delete_file_folder_with_permission':
        return deleteRenderer.render(input, result, options);
      case 'create_todo_list':
      case 'read_todos':
      case 'add_todos':
      case 'remove_todos':
      case 'mark_todo_as_done':
      case 'update_todo_status':
        return todoRenderer.render(input, result, options);
      case 'attempt_completion':
        return attemptCompletionRenderer.render(input, result, options);
      case 'web_fetch':
        return webFetchRenderer.render(input, result, options);
      case 'web_search':
        return webSearchRenderer.render(input, result, options);
      case 'diagnostics':
        return diagnosticsRenderer.render(input, result, options);
      case 'str_replace':
        return strReplaceRenderer.render(input, result, options);
      case 'fs_append':
        return fsAppendRenderer.render(input, result, options);
      case 'edit_function':
        return editFunctionRenderer.render(input, result, options);
      case 'edit_lines':
        return editLinesRenderer.render(input, result, options);
      case 'get_function':
        return getFunctionRenderer.render(input, result, options);
      case 'txtlize':
        return genericRenderer.render(input, result, { ...options, toolName: 'txtlize' });
      default:
        return genericRenderer.render(input, result, { ...options, toolName: name });
    }
  } catch (err) {
    console.warn(`[completedRenderers] Error rendering ${name}:`, err);
    return genericRenderer.renderError(name, err);
  }
}

module.exports = {
  renderCompleted,
  viewGroupRenderer
};
