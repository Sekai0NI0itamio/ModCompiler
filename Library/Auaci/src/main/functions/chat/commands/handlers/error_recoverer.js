/**
 * Error Recoverer Handler
 * Handles error recovery operations for failed tool executions
 */

// Import individual tool handlers directly to avoid circular dependency
const strReplaceCmd = require('./str_replace');
const editLinesCmd = require('./edit_lines');
const applyPatchCmd = require('./apply_patch');
const fsAppendCmd = require('./fs_append');
const createFileCmd = require('./create_file');
const writeToFileCmd = require('./write_to_file');

// Map tool names to their handlers
const toolHandlers = {
  'str_replace': strReplaceCmd,
  'edit_lines': editLinesCmd,
  'apply_patch': applyPatchCmd,
  'fs_append': fsAppendCmd,
  'create_file': createFileCmd,
  'write_to_file': writeToFileCmd
};

async function errorRecoverer(params) {
  console.log('[error_recoverer] NEW VERSION - Starting error recovery with params:', params);

  const {
    previous_tool_name,
    previous_tool_input,
    error_message,
    corrections,
    action
  } = params;

  // Validate required parameters
  if (!previous_tool_name) {
    return { success: false, error: 'Missing required parameter: previous_tool_name', error_code: 'ERR_INVALID_INPUT' };
  }

  if (!error_message) {
    return { success: false, error: 'Missing required parameter: error_message', error_code: 'ERR_INVALID_INPUT' };
  }

  if (!action) {
    return { success: false, error: 'Missing required parameter: action', error_code: 'ERR_INVALID_INPUT' };
  }

  // Check if we have a handler for this tool
  const toolHandler = toolHandlers[previous_tool_name];
  if (!toolHandler) {
    return {
      success: false,
      status: "Recovery Failed",
      error: `No handler available for tool: ${previous_tool_name}`,
      error_code: 'ERR_UNSUPPORTED',
      message: `No handler available for tool: ${previous_tool_name}. Available tools: ${Object.keys(toolHandlers).join(', ')}`
    };
  }

  // Apply corrections to the original input
  const correctedInput = { ...previous_tool_input, ...corrections };

  try {
    console.log(`[error_recoverer] Retrying ${previous_tool_name} with corrected input:`, correctedInput);
    
    // Execute the tool handler directly
    const result = await toolHandler(correctedInput);
    
    return {
      success: true,
      status: "Recovery Successful",
      previous_tool: previous_tool_name,
      action: action,
      result: result,
      message: `Successfully recovered and re-executed ${previous_tool_name}`
    };
  } catch (error) {
    return {
      success: false,
      status: "Recovery Failed",
      previous_tool: previous_tool_name,
      action: action,
      error: error.message,
      error_code: 'ERR_RECOVERY_FAILED',
      message: `Failed to recover ${previous_tool_name}: ${error.message}`
    };
  }
}

module.exports = errorRecoverer;
