// Dispatcher: delegates each command to its own handler module for better organization.

const { tryParseJson, extractJsonObject, safeStringify, appendLog } = require('./lib/utils');
const { wrapToolResult } = require('./lib/toolResult');
const { toolService, ToolValidationError } = require('../ai-logic/tool-instructions/toolInvocationService');

// Handlers
const createFileCmd = require('./handlers/create_file');
const runCommandCmd = require('./handlers/run_command');
const findFilesCmd = require('./handlers/find_files');
const grepCmd = require('./handlers/grep');
const createTodoListCmd = require('./handlers/create_todo_list');
const readTodosCmd = require('./handlers/read_todos');
const markTodoAsDoneCmd = require('./handlers/mark_todo_as_done');
const addTodosCmd = require('./handlers/add_todos');
const removeTodosCmd = require('./handlers/remove_todos');
const updateTodoStatusCmd = require('./handlers/update_todo_status');
const deleteFileFolderWithPermissionCmd = require('./handlers/delete_file_folder_with_permission');
// Agentic tools
const writeToFileCmd = require('./handlers/write_to_file');
const lsCmd = require('./handlers/ls');
const globCmd = require('./handlers/glob');
const viewCmd = require('./handlers/view');
const bashCmd = require('./handlers/bash');
const txtlizeCmd = require('./handlers/txtlize');
const contextSearchCmd = require('./handlers/context_search');
const semanticSearchCmd = require('./handlers/semantic_search');
const applyPatchCmd = require('./handlers/apply_patch');
const askCmd = require('./handlers/ask');
const diagnosticsCmd = require('./handlers/diagnostics');
// New tools
const strReplaceCmd = require('./handlers/str_replace');
const fsAppendCmd = require('./handlers/fs_append');
const webFetchCmd = require('./handlers/web_fetch');
const webSearchCmd = require('./handlers/web_search');
// AST-based and precise editing tools
// Error recovery tool
const errorRecovererCmd = require('./handlers/error_recoverer');
const getFunctionCmd = require('./handlers/get_function');
const editFunctionCmd = require('./handlers/edit_function');
const editLinesCmd = require('./handlers/edit_lines');

async function executeCommand(cmd) {
  if (!cmd) throw new Error('Invalid command');
  const type = (cmd.type || cmd.name || '').trim();
  if (!type) throw new Error('Invalid command/tool type');
  const params = normalizeParams(cmd);
  if (cmd && cmd.context && typeof cmd.context === 'object') {
    params.__context = cmd.context;
  }

  await appendLog(`[executor] start ${type} params=${safeStringify(params).slice(0,500)}`);
  const startTime = Date.now();
  const meta = () => ({ duration_ms: Date.now() - startTime, started_at: startTime });

  // Validate against tool schema if available
  try {
    const validation = toolService.validateToolInput(type, params);
    if (validation instanceof ToolValidationError) {
      const err = new Error(validation.error);
      err.code = 'ERR_VALIDATION';
      return wrapToolResult(type, null, err, meta());
    }
  } catch (_) {
    // If validation fails internally, proceed with execution
  }

  try {
    switch (type) {
      // legacy commands
      case 'create_file':
        return wrapToolResult(type, await createFileCmd(params), null, meta());
      case 'run_command':
        return wrapToolResult(type, await runCommandCmd(params), null, meta());
      case 'find_files':
        return wrapToolResult(type, await findFilesCmd(params), null, meta());
      case 'grep':
        return wrapToolResult(type, await grepCmd(params), null, meta());
      case 'create_todo_list':
        return wrapToolResult(type, await createTodoListCmd(params), null, meta());
      case 'read_todos':
        return wrapToolResult(type, await readTodosCmd(), null, meta());
      case 'mark_todo_as_done':
        return wrapToolResult(type, await markTodoAsDoneCmd(params), null, meta());
      case 'add_todos':
        return wrapToolResult(type, await addTodosCmd(params), null, meta());
      case 'remove_todos':
        return wrapToolResult(type, await removeTodosCmd(params), null, meta());
      case 'delete_file_folder_with_permission':
        return wrapToolResult(type, await deleteFileFolderWithPermissionCmd(params), null, meta());

      // agentic tools
      case 'write_to_file':
        return wrapToolResult(type, await writeToFileCmd(params), null, meta());
      case 'ls':
        return wrapToolResult(type, await lsCmd(params), null, meta());
      case 'glob':
        return wrapToolResult(type, await globCmd(params), null, meta());
      case 'view':
        return wrapToolResult(type, await viewCmd(params), null, meta());
      case 'bash':
        return wrapToolResult(type, await bashCmd(params), null, meta());
      case 'txtlize':
        return wrapToolResult(type, await txtlizeCmd(params), null, meta());
      case 'update_todo_status':
        return wrapToolResult(type, await updateTodoStatusCmd(params), null, meta());
      case 'attempt_completion':
        return wrapToolResult(type, { completed: true, result: params && params.result ? String(params.result) : '' }, null, meta());
      case 'context_search':
      case 'context-search':
        return wrapToolResult(type, await contextSearchCmd(params), null, meta());
      case 'apply_patch':
        return wrapToolResult(type, await applyPatchCmd(params), null, meta());
      case 'ask':
        return wrapToolResult(type, await askCmd(params), null, meta());
      case 'diagnostics':
        return wrapToolResult(type, await diagnosticsCmd(params), null, meta());
      
      // New tools
      case 'str_replace':
        return wrapToolResult(type, await strReplaceCmd(params), null, meta());
      case 'fs_append':
        return wrapToolResult(type, await fsAppendCmd(params), null, meta());
      case 'web_fetch':
        return wrapToolResult(type, await webFetchCmd(params), null, meta());
      case 'web_search':
        return wrapToolResult(type, await webSearchCmd(params), null, meta());
      case 'semantic_search':
        return wrapToolResult(type, await semanticSearchCmd(params), null, meta());
      // AST-based and precise editing tools
      case 'get_function':
        return wrapToolResult(type, await getFunctionCmd(params), null, meta());
      case 'edit_function':
        return wrapToolResult(type, await editFunctionCmd(params), null, meta());
      case 'error_recoverer':
        return wrapToolResult(type, await errorRecovererCmd(params), null, meta());
      case 'edit_lines':
        return wrapToolResult(type, await editLinesCmd(params), null, meta());

      default:
        throw new Error(`Unsupported command/tool: ${type}`);
    }
  } catch (err) {
    return wrapToolResult(type, null, err, meta());
  }
}

function normalizeSingleEdit(p) {
  return {
    file_path: p.file_path || p.path,
    search: p.search,
    replace: p.replace !== undefined ? p.replace : '',
    search_start_line_number: p.search_start_line || p.search_start_line_number || 1,
  };
}

function normalizeParams(cmd) {
  if (cmd && cmd.parameters && typeof cmd.parameters === 'object') return cmd.parameters;
  if (cmd && cmd.input && typeof cmd.input === 'object') return cmd.input;
  const raw = (cmd && (cmd.rawParameters || cmd.raw)) ? String(cmd.rawParameters || cmd.raw) : '';
  const parsed = tryParseJson(raw) || tryParseJson(extractJsonObject(raw));
  return parsed || {};
}

module.exports = { executeCommand };
