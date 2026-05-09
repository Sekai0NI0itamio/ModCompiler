/**
 * Tool Instructions Loader
 * Loads and manages tool definitions from JSON files
 * Integrated with tool capability detection system
 */

const fs = require('fs');
const path = require('path');
const { detectToolCapabilities, buildToolContext, ToolName } = require('./toolCapabilities');
const { toolService } = require('./toolInvocationService');

// Load enhanced navigation tools
require('./registerEnhancedTools');

const TOOL_DIR = __dirname;

/**
 * loadAllTools()
 * Loads all tool JSON files from this directory and returns an array
 * of tool definitions in the format expected by the API.
 * 
 * @returns {Array} Array of tool definitions with 'type' and 'function' properties
 */
function loadAllTools() {
  return toolService.getTools();
}

/**
 * loadTool(name)
 * Loads a specific tool by name.
 * 
 * @param {String} name - Tool name (without .json extension)
 * @returns {Object|null} Tool definition or null if not found
 */
function loadTool(name) {
  return toolService.getTool(name);
}

// Cache tools on first load
let _cachedTools = null;

/**
 * getTools()
 * Returns all available tools (cached after first load)
 * 
 * @returns {Array} Array of tool definitions
 */
function getTools() {
  if (!_cachedTools) {
    _cachedTools = loadAllTools();
  }
  return _cachedTools;
}

/**
 * getToolCapabilities(tools)
 * Detects which capabilities are available in the tool set
 * 
 * @param {Array} tools - Array of tool definitions
 * @returns {Object} Tool capabilities object
 */
function getToolCapabilities(tools = null) {
  const toolList = tools || getTools();
  return detectToolCapabilities(toolList);
}

/**
 * getToolContext(modelFamily)
 * Builds a complete tool execution context
 * 
 * @param {String} modelFamily - Model family name
 * @returns {Object} Tool context with capabilities and helper methods
 */
function getToolContext(modelFamily = 'default') {
  const tools = getTools();
  return buildToolContext(tools, modelFamily);
}

/**
 * filterToolsByCapability(capability)
 * Filters tools by a specific capability
 * 
 * @param {String} capability - Tool name or capability key
 * @returns {Array} Filtered tools
 */
function filterToolsByCapability(capability) {
  const tools = getTools();
  return tools.filter(tool => tool.function.name === capability || tool.function.name.includes(capability));
}

/**
 * validateToolInput(toolName, input)
 * Validates tool input against its JSON schema
 * 
 * @param {String} toolName - Tool name
 * @param {*} input - Input to validate
 * @returns {Object} Validation result with inputObj or error
 */
function validateToolInput(toolName, input) {
  return toolService.validateToolInput(toolName, input);
}

/**
 * executeTool(toolName, input, executor)
 * Executes a tool with the given input
 * 
 * @param {String} toolName - Tool name
 * @param {*} input - Tool input
 * @param {Function} executor - Tool executor function (optional)
 * @returns {Promise<Object>} Execution result
 */
async function executeTool(toolName, input, executor = null) {
  if (executor) {
    toolService.registerExecutor(toolName, executor);
  }
  return await toolService.executeTool(toolName, input);
}

/**
 * clearCache()
 * Clears the tool cache (useful for testing)
 */
function clearCache() {
  _cachedTools = null;
}

/**
 * Get tool service for advanced operations
 */
function getToolService() {
  return toolService;
}

module.exports = {
  loadAllTools,
  loadTool,
  getTools,
  getToolCapabilities,
  getToolContext,
  filterToolsByCapability,
  validateToolInput,
  executeTool,
  clearCache,
  getToolService,
  ToolName,
};
