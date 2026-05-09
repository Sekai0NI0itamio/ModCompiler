// src/main/functions/chat/rawHistoryStorage.js
// Manages raw conversation history in OpenAI-compatible format
// This history is used to provide context to GPT requests without relying on server-side caching
//
// Format: Array of messages with role, content, tool_calls, tool_call_id, name
// - User messages: { role: "user", content: "..." }
// - Assistant messages: { role: "assistant", content: "...", tool_calls?: [...] }
// - Tool results: { role: "tool", tool_call_id: "...", name: "...", content: "..." }

const fs = require('fs').promises;
const path = require('path');
const { getSessionId } = require('./sessionManager');

const RAW_HISTORY_DIR = path.join(process.cwd(), '.auaci', 'chathistory', 'rawhistory');
const MAX_HISTORY_MESSAGES = 50; // Keep last N messages to avoid token overflow

/**
 * Ensure the raw history directory exists
 */
async function ensureRawHistoryDir() {
  try {
    await fs.mkdir(RAW_HISTORY_DIR, { recursive: true });
  } catch (err) {
    if (err.code !== 'EEXIST') {
      console.error('[rawHistoryStorage] Failed to create directory:', err);
    }
  }
}

/**
 * Get the file path for a session's raw history
 */
function getRawHistoryPath(sessionId) {
  return path.join(RAW_HISTORY_DIR, `${sessionId}.json`);
}

/**
 * Load raw history for a session
 * @param {string} sessionId - Session ID (optional, uses current session if not provided)
 * @returns {Promise<{session_id: string, updated_at: string, messages: Array}>}
 */
async function loadRawHistory(sessionId = null) {
  await ensureRawHistoryDir();
  
  const sid = sessionId || await getSessionId();
  const filePath = getRawHistoryPath(sid);
  
  try {
    const data = await fs.readFile(filePath, 'utf8');
    const parsed = JSON.parse(data);
    return {
      session_id: parsed.session_id || sid,
      updated_at: parsed.updated_at || new Date().toISOString(),
      messages: Array.isArray(parsed.messages) ? parsed.messages : []
    };
  } catch (err) {
    if (err.code === 'ENOENT') {
      // File doesn't exist, return empty history
      return {
        session_id: sid,
        updated_at: new Date().toISOString(),
        messages: []
      };
    }
    console.error('[rawHistoryStorage] Failed to load raw history:', err);
    return {
      session_id: sid,
      updated_at: new Date().toISOString(),
      messages: []
    };
  }
}

/**
 * Save raw history for a session
 * @param {string} sessionId - Session ID
 * @param {Array} messages - Array of messages
 */
async function saveRawHistory(sessionId, messages) {
  await ensureRawHistoryDir();
  
  const filePath = getRawHistoryPath(sessionId);
  const data = {
    session_id: sessionId,
    updated_at: new Date().toISOString(),
    messages: messages
  };
  
  try {
    await fs.writeFile(filePath, JSON.stringify(data, null, 2), 'utf8');
  } catch (err) {
    console.error('[rawHistoryStorage] Failed to save raw history:', err);
  }
}

/**
 * Append a user message to raw history
 * @param {string} sessionId - Session ID
 * @param {string} content - User message content (full prompt with file context)
 */
async function appendUserMessage(sessionId, content) {
  const history = await loadRawHistory(sessionId);
  
  history.messages.push({
    role: 'user',
    content: content
  });
  
  // Trim to max messages
  if (history.messages.length > MAX_HISTORY_MESSAGES) {
    history.messages = history.messages.slice(-MAX_HISTORY_MESSAGES);
  }
  
  await saveRawHistory(sessionId, history.messages);
  console.log(`[rawHistoryStorage] Appended user message (${content.length} chars)`);
}

/**
 * Append an assistant message to raw history
 * @param {string} sessionId - Session ID
 * @param {string} content - Assistant response text
 * @param {Array|null} toolCalls - Tool calls array (OpenAI format)
 */
async function appendAssistantMessage(sessionId, content, toolCalls = null) {
  const history = await loadRawHistory(sessionId);
  
  const message = {
    role: 'assistant',
    content: content || ''
  };
  
  if (toolCalls && Array.isArray(toolCalls) && toolCalls.length > 0) {
    message.tool_calls = toolCalls;
  }
  
  history.messages.push(message);
  
  // Trim to max messages
  if (history.messages.length > MAX_HISTORY_MESSAGES) {
    history.messages = history.messages.slice(-MAX_HISTORY_MESSAGES);
  }
  
  await saveRawHistory(sessionId, history.messages);
  console.log(`[rawHistoryStorage] Appended assistant message (${content.length} chars, ${toolCalls?.length || 0} tools)`);
}

/**
 * Append a tool result to raw history
 * @param {string} sessionId - Session ID
 * @param {string} toolCallId - Tool call ID
 * @param {string} toolName - Tool name
 * @param {object|string} result - Tool result (will be JSON stringified if object)
 */
async function appendToolResult(sessionId, toolCallId, toolName, result) {
  const history = await loadRawHistory(sessionId);
  
  let content;
  try {
    // First, ensure result is not undefined
    if (result === undefined || result === null) {
      content = JSON.stringify({ error: 'Tool result was undefined or null' });
    } else if (typeof result === 'string') {
      content = result;
    } else {
      // Try to stringify, but handle circular references
      const seen = new WeakSet();
      content = JSON.stringify(result, (key, value) => {
        if (typeof value === 'object' && value !== null) {
          if (seen.has(value)) {
            return '[Circular Reference]';
          }
          seen.add(value);
        }
        return value;
      });
    }
  } catch (jsonError) {
    console.warn(`[rawHistoryStorage] JSON stringify failed for ${toolName}: ${jsonError.message}`);
    content = JSON.stringify({ error: 'Failed to serialize tool result', originalResult: String(result) });
  }
  
  // Apply 15KB truncation to tool results to prevent token overflow
  if (content && content.length > 15 * 1024) {
    const { truncateToolContent } = require('./send/modules/gptCycle');
    const truncatedContent = truncateToolContent(content, 15 * 1024);
    const originalLength = content.length;
    content = truncatedContent;
    console.log(`[rawHistoryStorage] Tool result truncated from ${originalLength} to ${content.length} chars for ${toolName}`);
  }
  
  history.messages.push({
    role: 'tool',
    tool_call_id: toolCallId,
    name: toolName,
    content: content
  });
  
  // Trim to max messages
  if (history.messages.length > MAX_HISTORY_MESSAGES) {
    history.messages = history.messages.slice(-MAX_HISTORY_MESSAGES);
  }
  
  await saveRawHistory(sessionId, history.messages);
  console.log(`[rawHistoryStorage] Appended tool result for ${toolName} (${content.length} chars)`);
}

/**
 * Get messages formatted for GPT API request
 * Returns the messages array ready to be sent as history
 * Validates that all tool_calls have corresponding tool results
 * @param {string} sessionId - Session ID
 * @param {number} maxMessages - Maximum number of messages to return
 * @returns {Promise<Array>} Array of messages in OpenAI format
 */
async function getMessagesForGpt(sessionId, maxMessages = MAX_HISTORY_MESSAGES) {
  const history = await loadRawHistory(sessionId);
  
  // Return last N messages
  let messages = history.messages.slice(-maxMessages);
  
  // Validate and sanitize: ensure all tool_calls have corresponding tool results
  messages = sanitizeMessagesForGpt(messages);
  
  console.log(`[rawHistoryStorage] Returning ${messages.length} messages for GPT`);
  return messages;
}

/**
 * Sanitize messages to ensure valid OpenAI format
 * - Removes orphaned tool results at the beginning (no preceding tool_calls)
 * - Removes tool_calls from assistant messages if no corresponding tool results exist
 * - Removes orphaned tool results in the middle
 * - Ensures all messages have valid content (uses empty string if missing to prevent API errors)
 * - Ensures content is always a valid string (not undefined, null, or object)
 * @param {Array} messages - Array of messages
 * @returns {Array} Sanitized messages
 */
function sanitizeMessagesForGpt(messages) {
  if (!Array.isArray(messages) || messages.length === 0) {
    return [];
  }
  
  /**
   * Ensure content is a valid string
   */
  function ensureValidContent(content) {
    if (content === null || content === undefined) {
      return '';
    }
    if (typeof content === 'string') {
      // Safety check: truncate extremely large content to prevent token overflow
      if (content.length > 15 * 1024) {
        const { truncateToolContent } = require('./send/modules/gptCycle');
        const truncatedContent = truncateToolContent(content, 15 * 1024);
        console.warn('[rawHistoryStorage] Large content truncated during sanitization:', content.length, '->', truncatedContent.length, 'chars');
        return truncatedContent;
      }
      return content;
    }
    if (typeof content === 'object') {
      try {
        const strContent = JSON.stringify(content);
        // Safety check for object content too
        if (strContent.length > 15 * 1024) {
          const { truncateToolContent } = require('./send/modules/gptCycle');
          const truncatedContent = truncateToolContent(strContent, 15 * 1024);
          console.warn('[rawHistoryStorage] Large object content truncated during sanitization:', strContent.length, '->', truncatedContent.length, 'chars');
          return truncatedContent;
        }
        return strContent;
      } catch (e) {
        console.warn('[rawHistoryStorage] Failed to stringify content object:', e);
        return '';
      }
    }
    return String(content);
  }

  /**
   * Ensure tool call arguments are a valid JSON string
   */
  function sanitizeToolArguments(args) {
    if (typeof args === 'string') {
      try {
        JSON.parse(args);
        return args;
      } catch (err) {
        console.warn('[rawHistoryStorage] Invalid tool arguments JSON, replacing with {}:', err?.message || err);
        return '{}';
      }
    }
    try {
      return JSON.stringify(args || {});
    } catch (err) {
      console.warn('[rawHistoryStorage] Failed to stringify tool arguments, replacing with {}:', err?.message || err);
      return '{}';
    }
  }
  
  const result = [];
  
  // First pass: skip any tool results at the very beginning (they have no preceding tool_calls)
  let startIdx = 0;
  while (startIdx < messages.length && messages[startIdx].role === 'tool') {
    console.log(`[rawHistoryStorage] Skipping orphaned tool result at start: ${messages[startIdx].name || 'unknown'} (id: ${messages[startIdx].tool_call_id})`);
    startIdx++;
  }
  
  // Process messages from startIdx
  for (let i = startIdx; i < messages.length; i++) {
    const msg = messages[i];
    
    // Ensure message has valid content
    if (msg.role === 'user') {
      const content = ensureValidContent(msg.content);
      result.push({ role: 'user', content: content });
      continue;
    }
    
    if (msg.role === 'assistant') {
      // Ensure assistant message has content
      let sanitizedMsg = { ...msg };
      sanitizedMsg.content = ensureValidContent(sanitizedMsg.content);
      
      if (msg.tool_calls && Array.isArray(msg.tool_calls) && msg.tool_calls.length > 0) {
        // Validate tool_calls structure
        const validToolCalls = msg.tool_calls.filter(tc => {
          if (!tc || typeof tc !== 'object') return false;
          if (!tc.id || typeof tc.id !== 'string') return false;
          if (!tc.function || typeof tc.function !== 'object') return false;
          if (!tc.function.name || typeof tc.function.name !== 'string') return false;
          return true;
        }).map(tc => ({
          id: tc.id,
          type: tc.type || 'function',
          function: {
            name: tc.function.name,
            arguments: sanitizeToolArguments(tc.function.arguments)
          }
        }));
        
        if (validToolCalls.length === 0) {
          // No valid tool_calls, remove them
          delete sanitizedMsg.tool_calls;
          result.push(sanitizedMsg);
          continue;
        }
        
        // Check if ALL tool_calls have corresponding tool results following this message
        const toolCallIds = new Set(validToolCalls.map(tc => tc.id));
        const foundToolResults = new Set();
        
        // Look ahead for tool results
        for (let j = i + 1; j < messages.length; j++) {
          const nextMsg = messages[j];
          if (nextMsg.role === 'tool' && nextMsg.tool_call_id) {
            if (toolCallIds.has(nextMsg.tool_call_id)) {
              foundToolResults.add(nextMsg.tool_call_id);
            }
          } else if (nextMsg.role === 'user' || nextMsg.role === 'assistant') {
            // Stop looking when we hit another user or assistant message
            break;
          }
        }
        
        // Check if all tool_calls have results
        const allToolsHaveResults = toolCallIds.size === foundToolResults.size;
        
        if (allToolsHaveResults) {
          // All tool_calls have results, keep the message with validated tool_calls
          sanitizedMsg.tool_calls = validToolCalls;
          result.push(sanitizedMsg);
        } else {
          // Some tool_calls are missing results - remove tool_calls from this message
          console.log(`[rawHistoryStorage] Removing orphaned tool_calls from assistant message (${toolCallIds.size} calls, ${foundToolResults.size} results)`);
          delete sanitizedMsg.tool_calls;
          result.push(sanitizedMsg);
        }
      } else {
        // Regular assistant message without tool_calls
        result.push(sanitizedMsg);
      }
      continue;
    }
    
    if (msg.role === 'tool') {
      // Check if this tool result has a corresponding tool_call in a previous assistant message
      const toolCallId = msg.tool_call_id;
      let hasMatchingToolCall = false;
      
      // Look back for the assistant message with this tool_call
      for (let j = result.length - 1; j >= 0; j--) {
        const prevMsg = result[j];
        if (prevMsg.role === 'assistant' && prevMsg.tool_calls && Array.isArray(prevMsg.tool_calls)) {
          if (prevMsg.tool_calls.some(tc => tc.id === toolCallId)) {
            hasMatchingToolCall = true;
            break;
          }
        } else if (prevMsg.role === 'user') {
          // Stop looking when we hit a user message
          break;
        }
      }
      
      if (hasMatchingToolCall) {
        // Ensure tool result has valid content
        const content = ensureValidContent(msg.content);
        result.push({
          role: 'tool',
          tool_call_id: msg.tool_call_id,
          name: msg.name || 'unknown',
          content: content
        });
      } else {
        // Orphaned tool result - skip it
        console.log(`[rawHistoryStorage] Skipping orphaned tool result for ${msg.name || 'unknown'} (id: ${toolCallId})`);
      }
      continue;
    }
    
    // System or other messages - pass through with content check
    if (msg.role === 'system') {
      const content = ensureValidContent(msg.content);
      result.push({ role: 'system', content: content });
    } else {
      // Unknown role - skip or pass through with content validation
      const sanitizedMsg = { ...msg };
      if ('content' in sanitizedMsg) {
        sanitizedMsg.content = ensureValidContent(sanitizedMsg.content);
      }
      result.push(sanitizedMsg);
    }
  }
  
  return result;
}

/**
 * Clear raw history for a session
 * @param {string} sessionId - Session ID
 */
async function clearRawHistory(sessionId) {
  await ensureRawHistoryDir();
  
  const filePath = getRawHistoryPath(sessionId);
  
  try {
    await fs.unlink(filePath);
    console.log(`[rawHistoryStorage] Cleared raw history for session ${sessionId}`);
  } catch (err) {
    if (err.code !== 'ENOENT') {
      console.error('[rawHistoryStorage] Failed to clear raw history:', err);
    }
  }
}

/**
 * Update the last assistant message (for streaming updates)
 * @param {string} sessionId - Session ID
 * @param {string} content - Updated content
 * @param {Array|null} toolCalls - Updated tool calls
 */
async function updateLastAssistantMessage(sessionId, content, toolCalls = null) {
  const history = await loadRawHistory(sessionId);
  
  // Find the last assistant message
  for (let i = history.messages.length - 1; i >= 0; i--) {
    if (history.messages[i].role === 'assistant') {
      history.messages[i].content = content || '';
      if (toolCalls && Array.isArray(toolCalls) && toolCalls.length > 0) {
        history.messages[i].tool_calls = toolCalls;
      } else {
        delete history.messages[i].tool_calls;
      }
      break;
    }
  }
  
  await saveRawHistory(sessionId, history.messages);
}

/**
 * Delete messages from raw history by entry index
 * This removes the user message and all subsequent assistant/tool messages until the next user message
 * @param {string} sessionId - Session ID
 * @param {number} entryIndex - The display history entry index (maps to user message position)
 */
async function deleteMessagesByEntryIndex(sessionId, entryIndex) {
  const history = await loadRawHistory(sessionId);
  
  if (!Array.isArray(history.messages) || history.messages.length === 0) {
    return;
  }
  
  // Find the Nth user message (entryIndex corresponds to display history index)
  let userMsgCount = -1;
  let startIdx = -1;
  let endIdx = history.messages.length;
  
  for (let i = 0; i < history.messages.length; i++) {
    if (history.messages[i].role === 'user') {
      userMsgCount++;
      if (userMsgCount === entryIndex) {
        startIdx = i;
      } else if (userMsgCount === entryIndex + 1) {
        endIdx = i;
        break;
      }
    }
  }
  
  if (startIdx === -1) {
    console.log(`[rawHistoryStorage] Entry index ${entryIndex} not found in raw history`);
    return;
  }
  
  // Remove messages from startIdx to endIdx (exclusive)
  const removeCount = endIdx - startIdx;
  history.messages.splice(startIdx, removeCount);
  
  await saveRawHistory(sessionId, history.messages);
  console.log(`[rawHistoryStorage] Deleted ${removeCount} messages for entry ${entryIndex}`);
}

/**
 * Delete only the GPT (assistant/tool) messages for an entry, keeping the user message
 * @param {string} sessionId - Session ID
 * @param {number} entryIndex - The display history entry index
 */
async function deleteGptMessagesByEntryIndex(sessionId, entryIndex) {
  const history = await loadRawHistory(sessionId);
  
  if (!Array.isArray(history.messages) || history.messages.length === 0) {
    return;
  }
  
  // Find the Nth user message
  let userMsgCount = -1;
  let userMsgIdx = -1;
  let endIdx = history.messages.length;
  
  for (let i = 0; i < history.messages.length; i++) {
    if (history.messages[i].role === 'user') {
      userMsgCount++;
      if (userMsgCount === entryIndex) {
        userMsgIdx = i;
      } else if (userMsgCount === entryIndex + 1) {
        endIdx = i;
        break;
      }
    }
  }
  
  if (userMsgIdx === -1) {
    console.log(`[rawHistoryStorage] Entry index ${entryIndex} not found in raw history`);
    return;
  }
  
  // Remove messages from userMsgIdx+1 to endIdx (keep the user message)
  const startIdx = userMsgIdx + 1;
  if (startIdx < endIdx) {
    const removeCount = endIdx - startIdx;
    history.messages.splice(startIdx, removeCount);
    await saveRawHistory(sessionId, history.messages);
    console.log(`[rawHistoryStorage] Deleted ${removeCount} GPT messages for entry ${entryIndex}`);
  }
}

/**
 * Delete all messages from a given entry index onwards
 * @param {string} sessionId - Session ID
 * @param {number} entryIndex - The display history entry index to start deleting from
 */
async function deleteMessagesFromEntryIndex(sessionId, entryIndex) {
  const history = await loadRawHistory(sessionId);
  
  if (!Array.isArray(history.messages) || history.messages.length === 0) {
    return;
  }
  
  // Find the Nth user message
  let userMsgCount = -1;
  let startIdx = -1;
  
  for (let i = 0; i < history.messages.length; i++) {
    if (history.messages[i].role === 'user') {
      userMsgCount++;
      if (userMsgCount === entryIndex) {
        startIdx = i;
        break;
      }
    }
  }
  
  if (startIdx === -1) {
    console.log(`[rawHistoryStorage] Entry index ${entryIndex} not found in raw history`);
    return;
  }
  
  // Remove all messages from startIdx onwards
  const removeCount = history.messages.length - startIdx;
  history.messages.splice(startIdx);
  
  await saveRawHistory(sessionId, history.messages);
  console.log(`[rawHistoryStorage] Deleted ${removeCount} messages from entry ${entryIndex} onwards`);
}

/**
 * Compile memory messages for GPT API request
 * This is an alias for getMessagesForGpt with optional system prompt prepending
 * @param {string} sessionId - Session ID
 * @param {string} systemPrompt - Optional system prompt to prepend
 * @returns {Promise<Array>} Array of messages in OpenAI format
 */
async function compileMemoryMessages(sessionId, systemPrompt = null) {
  const messages = await getMessagesForGpt(sessionId);
  
  // If system prompt provided and not already in messages, prepend it
  if (systemPrompt && typeof systemPrompt === 'string' && systemPrompt.trim()) {
    const hasSystemPrompt = messages.some(m => m.role === 'system');
    if (!hasSystemPrompt) {
      return [{ role: 'system', content: systemPrompt.trim() }, ...messages];
    }
  }
  
  return messages;
}

module.exports = {
  loadRawHistory,
  saveRawHistory,
  appendUserMessage,
  appendAssistantMessage,
  appendToolResult,
  getMessagesForGpt,
  compileMemoryMessages,
  clearRawHistory,
  updateLastAssistantMessage,
  deleteMessagesByEntryIndex,
  deleteGptMessagesByEntryIndex,
  deleteMessagesFromEntryIndex,
  MAX_HISTORY_MESSAGES
};
