// src/main/functions/chat/session-orchestrator.js
// Manages message-level session isolation:
// - Each user message gets a unique GPT session ID (separate from chat UI session)
// - Maintains mapping of GPT session IDs to chat session IDs
// - Provides utilities to build memory messages from raw history
// - Manages per-session cancellation and state tracking

const fs = require('fs').promises;
const path = require('path');
const crypto = require('crypto');
const { compileMemoryMessages } = require('./rawHistoryStorage');
const { getSystemPrompt, buildChatMessages } = require('./ai-logic/gpt-msg-sender');
const { getTools, getToolContext } = require('./ai-logic/tool-instructions');

const LOG_PATH = '/tmp/events.log';

// Global tracking maps
// sessionMap[gptSessionId] = { chatSessionId, createdAt, requestId, status }
const _sessionMap = {};

// cancelTokens[gptSessionId] = { cancelled: boolean, reason: string }
const _cancelTokens = {};

// requestIds[gptSessionId] = requestId (backend request ID)
const _requestIds = {};

async function appendLog(msg) {
  try {
    await fs.appendFile(LOG_PATH, `[${new Date().toISOString()}] ${msg}\n`);
  } catch (_) { /* ignore */ }
}

/**
 * generateGptSessionId()
 * Creates a unique session ID for a new GPT conversation
 */
function generateGptSessionId() {
  return `gpt-${Date.now()}-${crypto.randomBytes(8).toString('hex')}`;
}

/**
 * createMessageSession(chatSessionId, requestId)
 * Creates a new GPT session for an individual message within a chat session
 * Returns: { gptSessionId, chatSessionId, createdAt, requestId }
 */
function createMessageSession(chatSessionId, requestId) {
  const gptSessionId = generateGptSessionId();
  const session = {
    chatSessionId,
    createdAt: new Date().toISOString(),
    requestId: requestId || null,
    status: 'active'
  };
  _sessionMap[gptSessionId] = session;
  _cancelTokens[gptSessionId] = { cancelled: false, reason: '' };
  if (requestId) _requestIds[gptSessionId] = requestId;
  
  appendLog(`Created message session: ${gptSessionId} (chat: ${chatSessionId}, request: ${requestId})`);
  return { gptSessionId, ...session };
}

/**
 * getSessionInfo(gptSessionId)
 * Retrieves session info for a given GPT session ID
 */
function getSessionInfo(gptSessionId) {
  return _sessionMap[gptSessionId] || null;
}

/**
 * getChatSessionIdForGptSession(gptSessionId)
 * Gets the chat session ID associated with a GPT session
 */
function getChatSessionIdForGptSession(gptSessionId) {
  const info = getSessionInfo(gptSessionId);
  return info ? info.chatSessionId : null;
}

/**
 * getRequestIdForGptSession(gptSessionId)
 * Gets the backend request ID for a GPT session
 */
function getRequestIdForGptSession(gptSessionId) {
  return _requestIds[gptSessionId] || null;
}

/**
 * cancelGptSession(gptSessionId, reason)
 * Marks a GPT session as cancelled
 */
function cancelGptSession(gptSessionId, reason = 'user-cancelled') {
  if (!_cancelTokens[gptSessionId]) {
    _cancelTokens[gptSessionId] = { cancelled: false, reason: '' };
  }
  _cancelTokens[gptSessionId].cancelled = true;
  _cancelTokens[gptSessionId].reason = reason;
  if (_sessionMap[gptSessionId]) {
    _sessionMap[gptSessionId].status = 'cancelled';
  }
  appendLog(`Cancelled GPT session: ${gptSessionId} (reason: ${reason})`);
}

/**
 * isCancelledGptSession(gptSessionId)
 * Checks if a GPT session has been marked as cancelled
 */
function isCancelledGptSession(gptSessionId) {
  const token = _cancelTokens[gptSessionId];
  return token && token.cancelled === true;
}

/**
 * getCancelReason(gptSessionId)
 * Gets the cancellation reason for a GPT session
 */
function getCancelReason(gptSessionId) {
  const token = _cancelTokens[gptSessionId];
  return (token && token.reason) || null;
}

/**
 * markSessionComplete(gptSessionId, status = 'completed')
 * Marks a GPT session as complete
 */
function markSessionComplete(gptSessionId, status = 'completed') {
  if (_sessionMap[gptSessionId]) {
    _sessionMap[gptSessionId].status = status;
  }
  appendLog(`Marked GPT session complete: ${gptSessionId} (status: ${status})`);
}

/**
 * cleanupSession(gptSessionId)
 * Removes all tracking data for a GPT session (call after response is fully processed)
 */
function cleanupSession(gptSessionId) {
  delete _sessionMap[gptSessionId];
  delete _cancelTokens[gptSessionId];
  delete _requestIds[gptSessionId];
  appendLog(`Cleaned up GPT session: ${gptSessionId}`);
}

/**
 * buildGptPayload(chatSessionId, userPrompt, systemPrompt, gptSessionId, extraOptions = {})
 * Builds the complete payload to send to the GPT API for a fresh session with memory
 * This ensures each message gets a fresh GPT session with conversation history
 */
async function buildGptPayload(chatSessionId, userPrompt, systemPrompt, gptSessionId, extraOptions = {}) {
  try {
    // Compile memory messages from raw history
    const memoryMessages = await compileMemoryMessages(chatSessionId, systemPrompt);
    
    // Remove the system prompt from memory if it matches what we're sending
    const filteredMemory = memoryMessages.filter(m => !(m.role === 'system' && m.content === systemPrompt));
    
    const payload = {
      user_prompt: userPrompt || '',
      session_id: gptSessionId,
      app_id: chatSessionId, // track which chat session this GPT session belongs to
      include_history: false, // we're providing history explicitly via memory_messages
      ...extraOptions
    };
    
    // Attach system prompt if provided
    if (systemPrompt && typeof systemPrompt === 'string' && systemPrompt.trim()) {
      payload.system_prompt = systemPrompt.trim();
    }
    
    // Attach memory messages if available
    if (filteredMemory.length > 0) {
      payload.history = filteredMemory;
    }
    
    appendLog(`Built GPT payload for ${gptSessionId}: memory=${filteredMemory.length} messages`);
    return payload;
  } catch (err) {
    console.error(`[session-orchestrator] Error building GPT payload:`, err?.message || err);
    appendLog(`Error building GPT payload for ${gptSessionId}: ${String(err?.message || err)}`);
    
    // Fallback: return minimal payload
    return {
      user_prompt: userPrompt || '',
      session_id: gptSessionId,
      app_id: chatSessionId,
      include_history: false,
      system_prompt: systemPrompt || '',
      ...extraOptions
    };
  }
}

/**
 * getAllActiveSessions()
 * Returns all currently active GPT sessions (for debugging/monitoring)
 */
function getAllActiveSessions() {
  return Object.entries(_sessionMap)
    .filter(([_, session]) => session.status === 'active')
    .map(([gptSessionId, session]) => ({ gptSessionId, ...session }));
}

/**
 * getSessionStats()
 * Returns statistics about tracked sessions
 */
function getSessionStats() {
  const sessions = Object.values(_sessionMap);
  return {
    total: Object.keys(_sessionMap).length,
    active: sessions.filter(s => s.status === 'active').length,
    completed: sessions.filter(s => s.status === 'completed').length,
    cancelled: sessions.filter(s => s.status === 'cancelled').length,
    failed: sessions.filter(s => s.status === 'failed').length
  };
}

/**
 * buildEnhancedGptPayload(chatSessionId, userPrompt, gptSessionId, modelFamily, extraOptions)
 * Builds GPT payload using the new prompt and tool system
 * Automatically detects model and loads appropriate prompt
 */
async function buildEnhancedGptPayload(chatSessionId, userPrompt, gptSessionId, modelFamily = 'default', extraOptions = {}) {
  try {
    // Get system prompt for the model
    const systemPrompt = await getSystemPrompt(modelFamily);
    
    // Get available tools
    const tools = getTools();
    
    // Get tool context for model awareness
    const toolContext = getToolContext(modelFamily);
    
    // Build messages using new system
    const messages = await buildChatMessages(
      userPrompt,
      [], // files would be attached here
      systemPrompt,
      tools,
      modelFamily
    );

    // Compile memory from history
    const memoryMessages = await compileMemoryMessages(chatSessionId);
    
    // Build payload
    const payload = {
      user_prompt: userPrompt || '',
      session_id: gptSessionId,
      app_id: chatSessionId,
      include_history: false,
      model_family: modelFamily,
      ...extraOptions
    };
    
    // Add system prompt and messages
    if (messages.length > 0 && messages[0].role === 'system') {
      payload.system_prompt = messages[0].content;
    }
    
    // Add memory messages (excluding system prompt)
    const filteredMemory = memoryMessages.filter(m => !(m.role === 'system'));
    if (filteredMemory.length > 0) {
      payload.history = filteredMemory;
    }
    
    // Add tool context metadata
    payload.tool_capabilities = toolContext.capabilities;
    payload.available_tools = tools.length;
    
    appendLog(`Built enhanced GPT payload for ${gptSessionId}: model=${modelFamily}, tools=${tools.length}, memory=${filteredMemory.length}`);
    return payload;
  } catch (err) {
    console.error('[session-orchestrator] Error building enhanced payload:', err?.message || err);
    appendLog(`Error building enhanced payload for ${gptSessionId}: ${String(err?.message || err)}`);
    
    // Fallback to basic payload
    return {
      user_prompt: userPrompt || '',
      session_id: gptSessionId,
      app_id: chatSessionId,
      include_history: false,
      ...extraOptions
    };
  }
}

/**
 * getSessionToolContext(gptSessionId)
 * Gets tool context for a specific session
 */
function getSessionToolContext(gptSessionId, modelFamily = 'default') {
  return getToolContext(modelFamily);
}

module.exports = {
  generateGptSessionId,
  createMessageSession,
  getSessionInfo,
  getChatSessionIdForGptSession,
  getRequestIdForGptSession,
  cancelGptSession,
  isCancelledGptSession,
  getCancelReason,
  markSessionComplete,
  cleanupSession,
  buildGptPayload,
  buildEnhancedGptPayload,
  getSessionToolContext,
  getAllActiveSessions,
  getSessionStats
};
