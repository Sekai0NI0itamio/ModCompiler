// src/main/functions/chat/ai-logic/instructions/logicalizer.js
// Centralized system-prompt loader for the *main* GPT agent.
// Uses a single unified prompt file (AUACIprompt.txt).

const fs = require('fs').promises;
const path = require('path');

// Cache the unified prompt
let _promptCache = null;

/**
/**
 * loadMainPrompt()
 * Loads AUACIprompt.txt with caching and robust fallbacks.
 */
async function loadMainPrompt() {
  if (_promptCache) return _promptCache;
  const fileName = 'AUACIprompt.txt';
  try {
    const filePath = path.join(__dirname, fileName);
    const data = await fs.readFile(filePath, 'utf8');
    let txt = String(data || '').trim();
    if (!txt) txt = 'You are a helpful assistant.';
    _promptCache = txt;
    return _promptCache;
  } catch (_) {
    const minimal = 'You are a helpful assistant.';
    _promptCache = minimal;
    return _promptCache;
  }
}

/**
 * getSystemPrompt()
 * Returns the unified system prompt text (AUACI).
 */
async function getSystemPrompt() {
  return await loadMainPrompt();
}

/**
 * logicalize(userMessage)
 * Returns only the user message portion to send as role=user content.
 */
async function logicalize(userMessage) {
  const safeUserMessage = (userMessage || '').trim();
  return `${safeUserMessage}`;
}

module.exports = { logicalize, getSystemPrompt };
