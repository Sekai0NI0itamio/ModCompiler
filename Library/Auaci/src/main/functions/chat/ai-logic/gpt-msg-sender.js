/**
 * GPT Message Sender - Enhanced with Prompt Registry
 * Handles system prompt selection, user message building, and tool context
 * Follows vscode-copilot-chat's pattern for prompt management
 */

const fs = require('fs').promises;
const path = require('path');
const { registry, PROMPT_TYPES } = require('./instructions/PROMPT_REGISTRY');
const { detectToolCapabilities, buildToolContext } = require('./tool-instructions/toolCapabilities');

// Cache for system prompts
let _systemPromptCache = {};

/**
 * getPromptForModel(modelFamily)
 * Determines which prompt to use based on model family
 * 
 * @param {String} modelFamily - Model family name (e.g., 'claude-opus-4.5', 'gpt-5')
 * @returns {String} Prompt type key
 */
function getPromptForModel(modelFamily = 'default') {
  return registry.getPromptForModel(modelFamily);
}

/**
 * getSystemPrompt(modelFamily)
 * Loads the appropriate system prompt based on model
 * Uses prompt registry with model-specific variants
 * 
 * @param {String} modelFamily - Model family name
 * @returns {Promise<String>} System prompt text
 */
async function getSystemPrompt(modelFamily = 'default') {
  const cacheKey = String(modelFamily).toLowerCase();
  
  if (_systemPromptCache[cacheKey]) {
    return _systemPromptCache[cacheKey];
  }

  try {
    // Get the appropriate prompt type for this model
    const promptType = getPromptForModel(modelFamily);
    
    // Load the prompt from registry
    const prompt = await registry.loadPrompt(promptType);
    
    // Also append editing instructions
    let fullPrompt = prompt;
    try {
      const editingInstructions = await registry.loadPrompt(PROMPT_TYPES.EDITING);
      fullPrompt = prompt + '\n\n' + editingInstructions;
    } catch (err) {
      // If editing instructions fail to load, continue with base prompt
      console.warn('Failed to load editing instructions:', err.message);
    }
    
    _systemPromptCache[cacheKey] = fullPrompt.trim();
    return _systemPromptCache[cacheKey];
  } catch (err) {
    console.error('Failed to load system prompt:', err.message);
    // Return fallback prompt
    return 'You are a helpful AI assistant. Assist the user with their request.';
  }
}

/**
 * buildUserPrompt(userMessage, files)
 * Builds the user prompt with message and file attachments
 * Includes proper formatting for file context
 * 
 * Format:
 *   <user message>
 *   
 *   <file path="...">content</file>
 *   <file path="...">content</file>
 * 
 * @param {String} userMessage - User's input message
 * @param {Array} files - Array of file objects with 'path' and 'content'
 * @returns {Promise<String>} Formatted user prompt
 */
async function buildUserPrompt(userMessage, files = []) {
  let prompt = String(userMessage || '').trim();
  
  // Append files using <file> tags for context
  if (Array.isArray(files) && files.length > 0) {
    for (const file of files) {
      try {
        const content = (typeof file.content !== 'undefined' && file.content !== null)
          ? file.content
          : '[Binary file - content not displayed]';
        const filePath = file.path || file.filePath || file.filepath || file.name;
        const safePath = String(filePath || file.name || 'attached');
        prompt += (prompt ? '\n\n' : '') + `<file path="${safePath}">\n${content}\n</file>`;
      } catch (err) {
        const label = file && file.name ? `${file.name}` : 'attachment';
        prompt += `\n\n<file path="${label}">[Could not include file content: ${err && err.message ? err.message : err}]</file>`;
      }
    }
  }
  
  return prompt;
}

/**
 * buildPrompt(userMessage, files, opts)
 * Wrapper for backward compatibility
 * 
 * @param {String} userMessage - User message
 * @param {Array} files - Attached files
 * @param {Object} opts - Options
 * @returns {Promise<String>} User prompt
 */
async function buildPrompt(userMessage, files = [], opts = {}) {
  return await buildUserPrompt(userMessage, files);
}

/**
 * buildChatMessages(userMessage, files, systemPrompt, tools, modelFamily)
 * Builds a complete message array for API submission
 * Includes system prompt, user message, and tool context
 * 
 * @param {String} userMessage - User's message
 * @param {Array} files - File attachments
 * @param {String} systemPrompt - System prompt (optional, will load if not provided)
 * @param {Array} tools - Available tools
 * @param {String} modelFamily - Model family for context
 * @returns {Promise<Array>} Array of message objects
 */
async function buildChatMessages(userMessage, files = [], systemPrompt = null, tools = [], modelFamily = 'default') {
  // Load system prompt if not provided
  if (!systemPrompt) {
    systemPrompt = await getSystemPrompt(modelFamily);
  }

  // Build user message with attachments
  const userPrompt = await buildUserPrompt(userMessage, files);

  // Build tool context for prompt instructions
  const toolContext = buildToolContext(tools, modelFamily);

  // Return message array
  const messages = [
    {
      role: 'system',
      content: systemPrompt,
    },
    {
      role: 'user',
      content: userPrompt,
    },
  ];

  return messages;
}

/**
 * clearPromptCache()
 * Clears cached prompts (useful for testing or configuration changes)
 */
function clearPromptCache() {
  _systemPromptCache = {};
  registry.clearCache();
}

/**
 * getAvailablePrompts()
 * Returns list of available prompt types
 * 
 * @returns {Array} Array of prompt type keys
 */
function getAvailablePrompts() {
  return Object.values(PROMPT_TYPES);
}

module.exports = { 
  buildPrompt, 
  buildUserPrompt,
  buildChatMessages,
  getSystemPrompt,
  getPromptForModel,
  clearPromptCache,
  getAvailablePrompts,
};
