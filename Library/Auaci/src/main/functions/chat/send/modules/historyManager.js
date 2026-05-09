// src/main/functions/chat/send/modules/historyManager.js
// Clean module for managing chat history - save and load operations

const { getSessionId } = require('../../sessionManager');

/**
 * History entry format:
 * {
 *   timestamp: ISO string,
 *   user: { text: string, files: array },
 *   gpt: string (contains text + <tool_use> blocks with system responses),
 *   status: 'complete' | 'in-progress'
 * }
 */

/**
 * Create a new GPT response string with a tool and its system response
 */
function buildToolBlock(toolName, toolInput, systemResponse = null) {
  const toolObj = {
    name: toolName,
    input: toolInput || {}
  };
  
  if (systemResponse !== null && systemResponse !== undefined) {
    // Use appropriate key based on tool type
    const lname = String(toolName || '').toLowerCase();
    const useResultsKey = ['view', 'grep', 'context_search', 'find_files', 'ls', 
                          'add_todos', 'create_todo_list', 'read_todos', 
                          'mark_todo_as_done', 'remove_todos', 'ask'].includes(lname);
    
    if (useResultsKey) {
      toolObj.tool_system_results = systemResponse;
    } else {
      toolObj.tool_system_response = systemResponse;
    }
  }
  
  return `<tool_use>\n${JSON.stringify(toolObj, null, 2)}\n</tool_use>`;
}

/**
 * Build complete GPT response string from text and tools
 */
function buildGptResponse(textContent, toolsWithResponses = []) {
  const parts = [];
  
  if (textContent && textContent.trim()) {
    parts.push(textContent.trim());
  }
  
  for (const tool of toolsWithResponses) {
    const block = buildToolBlock(tool.name, tool.input, tool.systemResponse);
    parts.push(block);
  }
  
  return parts.join('\n\n');
}

/**
 * Append a tool with system response to existing GPT content
 */
function appendToolToGptResponse(existingGpt, toolName, toolInput, systemResponse) {
  const block = buildToolBlock(toolName, toolInput, systemResponse);
  if (!existingGpt || !existingGpt.trim()) {
    return block;
  }
  return existingGpt.trim() + '\n\n' + block;
}

/**
 * Append text to existing GPT content
 */
function appendTextToGptResponse(existingGpt, newText) {
  if (!newText || !newText.trim()) {
    return existingGpt || '';
  }
  if (!existingGpt || !existingGpt.trim()) {
    return newText.trim();
  }
  return existingGpt.trim() + '\n\n' + newText.trim();
}

/**
 * Save GPT response to history at given entry index
 */
async function saveGptResponse(entryIndex, gptContent, sessionId, isComplete = false) {
  try {
    const { updateGptResponse, finalizeChatEntry } = require('../../incrementalHistoryStorage');
    
    if (isComplete) {
      await finalizeChatEntry(entryIndex, gptContent, sessionId);
    } else {
      await updateGptResponse(entryIndex, gptContent, false, sessionId);
    }
    
    console.log(`[historyManager] Saved GPT response at index ${entryIndex} (complete: ${isComplete})`);
    return true;
  } catch (err) {
    console.error('[historyManager] Failed to save GPT response:', err);
    return false;
  }
}

/**
 * Initialize a new chat entry with user input
 */
async function initializeEntry(userText, files, sessionId) {
  try {
    const { initiateChatEntry } = require('../../incrementalHistoryStorage');
    const entryIndex = await initiateChatEntry(userText, files, sessionId);
    console.log(`[historyManager] Initialized entry at index ${entryIndex}`);
    return entryIndex;
  } catch (err) {
    console.error('[historyManager] Failed to initialize entry:', err);
    return null;
  }
}

module.exports = {
  buildToolBlock,
  buildGptResponse,
  appendToolToGptResponse,
  appendTextToGptResponse,
  saveGptResponse,
  initializeEntry
};
