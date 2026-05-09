/**
 * Tool Capability Detection and Utilities
 * Inspired by vscode-copilot-chat's detectToolCapabilities pattern
 */

/**
 * Standard tool names used throughout the system
 * Matches tool definition names in tool JSON files
 */
const ToolName = {
  // File operations
  READ_FILE: 'view',
  CREATE_FILE: 'create_file',
  REPLACE_STRING: 'str_replace',
  MULTI_REPLACE_STRING: 'batch_str_replace',
  EDIT_FILE: 'edit_file',
  DELETE_FILE: 'delete_file_folder_with_permission',
  
  // Search and discovery
  FILE_SEARCH: 'find_files',
  GREP_SEARCH: 'grep',
  SEMANTIC_SEARCH: 'semantic_search',
  GLOB: 'glob',
  
  // Terminal/Shell
  RUN_IN_TERMINAL: 'run_command',
  BASH: 'bash',
  
  // Web
  WEB_SEARCH: 'web_search',
  WEB_FETCH: 'web_fetch',
  
  // Context and search
  CONTEXT_SEARCH: 'context_search',
  
  // Code editing
  APPLY_PATCH: 'apply_patch',
  EDIT_LINES: 'edit_lines',
  EDIT_FUNCTION: 'edit_function',
  GET_FUNCTION: 'get_function',
  
  // System
  VIEW: 'view',
  LS: 'ls',
  TXTLIZE: 'txtlize',
  DIAGNOSTICS: 'diagnostics',
  
  // User interaction
  ASK: 'ask',
  ATTEMPT_COMPLETION: 'attempt_completion',
  
  // Error recovery
  ERROR_RECOVERER: 'error_recoverer',
  
  // Todo management
  MANAGE_TODO_LIST: 'manage_todo_list',
  CREATE_TODO_LIST: 'create_todo_list',
  READ_TODOS: 'read_todos',
  ADD_TODOS: 'add_todos',
  MARK_TODO_DONE: 'mark_todo_as_done',
  UPDATE_TODO_STATUS: 'update_todo_status',
  REMOVE_TODOS: 'remove_todos',
  
  // File system operations
  APPEND_FILE: 'fs_append',
  WRITE_FILE: 'write_to_file',
};

/**
 * Detect which tools are available
 * Returns a capabilities object with boolean flags for each tool
 * 
 * @param {Array} availableTools - Array of tool objects with 'name' property
 * @returns {Object} Tool capabilities object
 */
function detectToolCapabilities(availableTools = []) {
  const capabilities = {
    // Initialize all tools as disabled
    ...Object.values(ToolName).reduce((acc, name) => {
      acc[name] = false;
      return acc;
    }, {}),
    // Derived capabilities
    hasEditTool: false,
    hasSearchTool: false,
    hasFileReadTool: false,
  };

  // Mark available tools as enabled
  if (Array.isArray(availableTools)) {
    const availableNames = new Set(availableTools.map(t => t.name || t.function?.name));
    for (const name of Object.values(ToolName)) {
      if (availableNames.has(name)) {
        capabilities[name] = true;
      }
    }
  }

  // Compute derived capabilities
  capabilities.hasEditTool = !!(
    capabilities[ToolName.REPLACE_STRING] ||
    capabilities[ToolName.MULTI_REPLACE_STRING] ||
    capabilities[ToolName.EDIT_FILE]
  );

  capabilities.hasSearchTool = !!(
    capabilities[ToolName.SEMANTIC_SEARCH] ||
    capabilities[ToolName.GREP_SEARCH] ||
    capabilities[ToolName.FILE_SEARCH]
  );

  capabilities.hasFileReadTool = !!capabilities[ToolName.READ_FILE];

  return capabilities;
}

/**
 * Validate tool parameters against a schema
 * Ensures required fields are present
 * 
 * @param {Object} params - Tool parameters to validate
 * @param {Object} schema - JSON Schema for the tool
 * @returns {Array} Array of validation error messages (empty if valid)
 */
function validateToolParams(params, schema) {
  const errors = [];

  if (!schema || !schema.properties) {
    return errors;
  }

  // Check required fields
  if (schema.required) {
    for (const required of schema.required) {
      if (params[required] === undefined || params[required] === null) {
        errors.push(`Missing required parameter: ${required}`);
      }
    }
  }

  // Check parameter types
  for (const [paramName, paramValue] of Object.entries(params)) {
    const paramSchema = schema.properties[paramName];
    if (!paramSchema) {
      errors.push(`Unknown parameter: ${paramName}`);
      continue;
    }

    const expectedType = paramSchema.type;
    const actualType = Array.isArray(paramValue) ? 'array' : typeof paramValue;

    if (expectedType && expectedType !== actualType && paramValue !== null) {
      errors.push(`Parameter ${paramName} should be ${expectedType}, got ${actualType}`);
    }
  }

  return errors;
}

/**
 * Build tool execution context with capabilities information
 * 
 * @param {Array} availableTools - Available tools
 * @param {String} modelFamily - Model family (for model-specific tool hints)
 * @returns {Object} Tool execution context
 */
function buildToolContext(availableTools = [], modelFamily = 'default') {
  return {
    capabilities: detectToolCapabilities(availableTools),
    availableTools: availableTools,
    modelFamily: String(modelFamily).toLowerCase(),
    toolsByName: availableTools.reduce((acc, tool) => {
      acc[tool.name] = tool;
      return acc;
    }, {}),
  };
}

/**
 * Get best tool for a specific task
 * Returns the preferred tool for common operations
 * 
 * @param {Object} context - Tool context from buildToolContext()
 * @param {String} taskType - Type of task ('read', 'write', 'search', etc.)
 * @returns {String|null} Tool name or null if not available
 */
function getBestToolForTask(context, taskType) {
  const { capabilities } = context;

  switch (taskType) {
    case 'read':
      return capabilities.hasFileReadTool ? ToolName.READ_FILE : null;

    case 'write':
      if (capabilities[ToolName.REPLACE_STRING]) return ToolName.REPLACE_STRING;
      if (capabilities[ToolName.EDIT_FILE]) return ToolName.EDIT_FILE;
      if (capabilities[ToolName.CREATE_FILE]) return ToolName.CREATE_FILE;
      return null;

    case 'multi-write':
      if (capabilities[ToolName.MULTI_REPLACE_STRING]) return ToolName.MULTI_REPLACE_STRING;
      return null;

    case 'search':
      if (capabilities[ToolName.SEMANTIC_SEARCH]) return ToolName.SEMANTIC_SEARCH;
      if (capabilities[ToolName.GREP_SEARCH]) return ToolName.GREP_SEARCH;
      if (capabilities[ToolName.FILE_SEARCH]) return ToolName.FILE_SEARCH;
      return null;

    case 'terminal':
      return capabilities[ToolName.RUN_IN_TERMINAL] ? ToolName.RUN_IN_TERMINAL : null;

    default:
      return null;
  }
}

module.exports = {
  ToolName,
  detectToolCapabilities,
  validateToolParams,
  buildToolContext,
  getBestToolForTask,
};
