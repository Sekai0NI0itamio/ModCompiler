// src/main/functions/chat/send/sendMessage.js
// Clean, modular implementation of the chat message sending flow
//
// FLOW:
// 1. User sends message
// 2. Save user message to history (both display and raw)
// 3. Render user message
// 4. Send to GPT with raw history attached (not using server-side cache)
// 5. Save GPT response to both display history and raw history
// 6. Render GPT response (text + tool placeholders)
// 7. Execute tools one by one
// 8. After each tool: save tool result to raw history, update display history, re-render
// 9. Send tool result to GPT with raw history, repeat from step 4
// 10. Finalize when no more tools or attempt_completion

const marked = require('marked');
const { createRenderer } = require('../renderer');
const { initiateChatEntry, updateGptResponse, finalizeChatEntry } = require('../incrementalHistoryStorage');
// Lazy-load gpt-api to avoid syntax parsing issues on startup
let streamChat = null;
function getStreamChat() {
  if (!streamChat) {
    streamChat = require('../gpt-api').streamChat;
  }
  return streamChat;
}
const { executeCommand } = require('../commands/executor');
const { buildPrompt, buildChatMessages, getSystemPrompt } = require('../ai-logic/gpt-msg-sender');
const { getTools, getToolContext, getToolCapabilities, ToolName } = require('../ai-logic/tool-instructions/index');
const { ToolCallingLoop, ToolCall } = require('../ai-logic/tool-instructions/toolCallingLoop');
const { detectToolCapabilities } = require('../ai-logic/tool-instructions/toolCapabilities');
const { writeUserRequest } = require('../ai-logic/aicoder/showwhatssent');
const { showGptIndicator, hideGptIndicator } = require('../ui/indicator');
const { escapeHTML, setupCodeBlockListeners } = require('../helpers/dom');
const { getSessionId } = require('../sessionManager');
const tabManager = require('../tabManager');
const { smoothScrollTo } = require('../helpers/smoothScroll');
const { enhanceToolPlaceholders } = require('../helpers/toolRender');
const { sanitizeForToolHistory } = require('../helpers/toolRenderSanitizer');
const { enhanceToolPlaceholdersV2 } = require('../helpers/toolRenderAdapter');
const { shouldRenderForSession, setActiveSessionId } = require('../helpers/renderGate');
const { executeTool, normalizeToolName } = require('./modules/toolExecutor');
const { truncateToolContent } = require('./modules/gptCycle');

// Load context gatherer popup handler (registers window.showContextGathererSummary)
try { require('../helpers/contextGathererPopup'); } catch (_) {}
const { 
  appendUserMessage, 
  appendAssistantMessage, 
  appendToolResult,
  getMessagesForGpt 
} = require('../rawHistoryStorage');
const { 
  startMonitoring, 
  markRequestStart, 
  markRequestActivity, 
  markRequestEnd,
  markToolStart,
  markToolEnd
} = require('../connectionMonitor');

// Concurrent session management
const {
  getSessionState,
  SESSION_STATUS,
  scheduleCleanup
} = require('../concurrent/sessionStateManager');

// Multi-view support
const {
  isInMultiView,
  updateMultiViewPanelContent
} = require('../ui/multiView');

// ============================================================================
// HISTORY MANAGEMENT
// ============================================================================

/**
 * Build a <tool_use> block string
 * @param {string} toolName - Name of the tool
 * @param {object} toolInput - Tool input parameters
 * @param {object|null} systemResponse - System response (null if not yet executed)
 * @param {string|null} toolId - Unique ID for tracking (used to match response to correct tool)
 */
function buildToolBlock(toolName, toolInput, systemResponse = null, toolId = null) {
  const toolObj = {
    name: toolName,
    input: toolInput || {}
  };
  
  // Add tracking ID if provided (will be removed when response is added)
  if (toolId && systemResponse === null) {
    toolObj._tool_id = toolId;
  }
  
  if (systemResponse !== null && systemResponse !== undefined) {
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
 * Update a tool block in content with system response
 * Uses toolId to uniquely identify which tool block to update (prevents duplicate issues)
 */
function updateToolBlockWithResponse(content, toolName, systemResponse, toolId = null) {
  const toolRe = /<tool_use>\s*([\s\S]*?)\s*<\/tool_use>/gi;
  let result = content;
  let match;
  let foundMatch = false;
  
  toolRe.lastIndex = 0;
  
  while ((match = toolRe.exec(content)) !== null) {
    let parsed = null;
    try {
      parsed = JSON.parse(match[1]);
    } catch (_) {
      const start = match[1].indexOf('{');
      const end = match[1].lastIndexOf('}');
      if (start !== -1 && end > start) {
        try { parsed = JSON.parse(match[1].slice(start, end + 1)); } catch (_) {}
      }
    }
    
    if (parsed && parsed.name === toolName) {
      const hasResponse = parsed.tool_system_response || parsed.tool_system_results;
      
      // Match logic:
      // 1. If toolId is provided AND block has _tool_id, match by id
      // 2. If toolId is provided but block has no _tool_id (text-parsed), match first without response
      // 3. If no toolId provided, match first without response
      // 4. Special case: ask tool - allow updating even if it has a response (to update from waiting to answered)
      const blockHasId = !!parsed._tool_id;
      const idMatches = toolId && blockHasId ? (parsed._tool_id === toolId) : true;
      
      // For ask tool, allow updating if the existing response is in waiting state
      const isAskWaiting = toolName === 'ask' && hasResponse && 
        (parsed.tool_system_results?.waiting === true || parsed.tool_system_response?.waiting === true);
      const isAskAnswerUpdate = toolName === 'ask' && systemResponse && 
        (systemResponse.answered === true || systemResponse.waiting === false);
      
      // Debug logging for ask tool
      if (toolName === 'ask') {
        console.log('[updateToolBlockWithResponse] Ask tool update:', {
          hasResponse: !!hasResponse,
          isAskWaiting,
          isAskAnswerUpdate,
          idMatches,
          existingWaiting: parsed.tool_system_results?.waiting,
          newAnswered: systemResponse?.answered,
          newWaiting: systemResponse?.waiting,
          willUpdate: (!hasResponse || (isAskWaiting && isAskAnswerUpdate)) && idMatches
        });
      }
      
      if ((!hasResponse || (isAskWaiting && isAskAnswerUpdate)) && idMatches) {
        foundMatch = true;
        const lname = String(toolName).toLowerCase();
        const useResultsKey = ['view', 'grep', 'context_search', 'find_files', 'ls',
                              'add_todos', 'create_todo_list', 'read_todos',
                              'mark_todo_as_done', 'remove_todos', 'ask'].includes(lname);
        
        if (useResultsKey) {
          parsed.tool_system_results = systemResponse;
        } else {
          parsed.tool_system_response = systemResponse;
        }
        
        // Remove internal tracking id before saving
        delete parsed._tool_id;
        
        const newBlock = `<tool_use>\n${JSON.stringify(parsed, null, 2)}\n</tool_use>`;
        result = content.slice(0, match.index) + newBlock + content.slice(match.index + match[0].length);
        
        // Debug logging for ask tool
        if (toolName === 'ask') {
          console.log('[updateToolBlockWithResponse] Ask tool block updated successfully');
        }
        
        break;
      }
    }
  }
  
  // Debug logging if no match found for ask tool
  if (toolName === 'ask' && !foundMatch) {
    console.log('[updateToolBlockWithResponse] WARNING: No matching ask tool block found to update!');
  }
  
  return result;
}

// ============================================================================
// RENDERING
// ============================================================================

/**
 * Parse GPT response into ordered segments
 */
function parseResponseSegments(gptText) {
  if (!gptText || typeof gptText !== 'string') return [];
  
  const segments = [];
  const toolRe = /<tool_use>\s*([\s\S]*?)\s*<\/tool_use>/gi;
  let lastIndex = 0;
  let match;
  
  while ((match = toolRe.exec(gptText)) !== null) {
    const textBefore = gptText.slice(lastIndex, match.index).trim();
    if (textBefore) {
      segments.push({ type: 'text', content: textBefore });
    }
    
    let parsed = null;
    try {
      parsed = JSON.parse(match[1]);
    } catch (_) {
      const start = match[1].indexOf('{');
      const end = match[1].lastIndexOf('}');
      if (start !== -1 && end > start) {
        try { parsed = JSON.parse(match[1].slice(start, end + 1)); } catch (_) {}
      }
    }
    
    if (parsed) {
      segments.push({
        type: 'tool',
        name: parsed.name || '',
        input: parsed.input || {},
        systemResponse: parsed.tool_system_response || parsed.tool_system_results || null
      });
    }
    
    lastIndex = toolRe.lastIndex;
  }
  
  const textAfter = gptText.slice(lastIndex).trim();
  if (textAfter) {
    segments.push({ type: 'text', content: textAfter });
  }
  
  return segments;
}

/**
 * Render complete GPT response to container
 * Uses the same rendering logic as history display for consistency
 */
function renderGptResponse(containerEl, gptText, toolRuns = []) {
  if (!containerEl) return;
  
  containerEl.innerHTML = '';
  
  try {
    marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });
  } catch (_) {}
  
  // Use the same parsing as history display
  const { extractToolUsesAndSanitize } = require('../commands/parser');
  const parsed = extractToolUsesAndSanitize(gptText || '');
  const sanitizedText = parsed.sanitizedText || '';
  const tools = parsed.tools || [];
  
  // Render the sanitized text (which has tool placeholders already inserted)
  containerEl.innerHTML = marked.parse(sanitizedText);
  
  // Enhance tool placeholders with the parsed tool data
  // Use V2 renderer which supports view tool grouping
  try {
    enhanceToolPlaceholdersV2(containerEl, tools, toolRuns);
  } catch (e) {
    console.warn('[renderGptResponse] enhanceToolPlaceholdersV2 failed, falling back:', e);
    try {
      enhanceToolPlaceholders(containerEl, tools, toolRuns);
    } catch (_) {}
  }
  
  try { setupCodeBlockListeners(); } catch (_) {}
}

/**
 * Get the current GPT content element for a given entry index
 * This handles the case where the user switched sessions and displayChatHistory
 * created new DOM elements - we need to render to the CURRENT element
 */
function getCurrentGptContent(entryIndex, fallbackElement) {
  try {
    const currentChatMessages = document.getElementById('chat-messages');
    if (currentChatMessages && entryIndex !== null) {
      const currentGptDiv = currentChatMessages.querySelector(`.message.gpt-message[data-entry-index="${entryIndex}"]`);
      if (currentGptDiv) {
        const foundContent = currentGptDiv.querySelector('.message-content');
        if (foundContent) {
          return { element: foundContent, chatMessages: currentChatMessages };
        }
      }
    }
  } catch (_) {}
  return { element: fallbackElement, chatMessages: document.getElementById('chat-messages') };
}

/**
 * Show thinking indicator
 */
function showThinking(containerEl) {
  if (!containerEl) return null;
  
  const existing = containerEl.querySelector('.thinking-inline');
  if (existing) existing.remove();
  
  const div = document.createElement('div');
  div.className = 'thinking-inline';
  div.innerHTML = `
    <span class="typing-text">GPT is thinking (<span class="thinking-seconds">0</span>s)</span>
    <span class="gpt-indicator-dots">
      <span class="dot"></span><span class="dot"></span><span class="dot"></span>
    </span>
  `;
  containerEl.appendChild(div);
  
  const startTime = Date.now();
  const secondsEl = div.querySelector('.thinking-seconds');
  const timer = setInterval(() => {
    if (secondsEl) secondsEl.textContent = String(Math.floor((Date.now() - startTime) / 1000));
  }, 1000);
  
  return { element: div, timer };
}

function hideThinking(ref) {
  if (!ref) return;
  if (ref.timer) clearInterval(ref.timer);
  if (ref.element?.parentNode) ref.element.remove();
}

// ============================================================================
// TOOL EXECUTION
// ============================================================================



/**
 * Execute ask tool - waits for user interaction via UI
 * Returns a promise that resolves when user answers
 */
async function executeAskTool(input, options = {}) {
  const question = input?.question || '';
  const mode = input?.mode || 'free';
  const opts = Array.isArray(input?.options) ? input.options : [];
  const toolIndex = options.toolIndex;
  
  // Return initial waiting state - the UI will render this and show input controls
  // The actual answer will be captured when user clicks "Answer" button
  // which triggers 'auaci:ask-answered' event
  return {
    question,
    mode,
    options: opts,
    waiting: true
  };
}

/**
 * Wait for user to answer an ask tool
 * Returns a promise that resolves when user submits their answer
 */
function waitForAskAnswer(toolIndex) {
  return new Promise((resolve) => {
    const onAnswered = (event) => {
      try {
        const detail = event.detail || {};
        // Check if this is for our tool
        if (String(detail.tool_index) === String(toolIndex)) {
          const ansText = detail.user_input || '';
          const selectedValues = Array.isArray(detail.selected_values) ? detail.selected_values : [];
          
          window.removeEventListener('auaci:ask-answered', onAnswered);
          
          resolve({
            question: detail.question || '',
            mode: detail.mode || 'free',
            options: detail.options || [],
            answer_text: ansText,
            selected: selectedValues,
            waiting: false,
            answered: true
          });
        }
      } catch (e) {
        window.removeEventListener('auaci:ask-answered', onAnswered);
        resolve({
          question: '',
          mode: 'free',
          options: [],
          answer_text: '',
          selected: [],
          waiting: false,
          answered: true,
          error: String(e.message || e)
        });
      }
    };
    
    window.addEventListener('auaci:ask-answered', onAnswered);
  });
}

async function executeStreamingCommand(toolName, input) {
  try {
    // Use the run_command handler which executes in the terminal panel
    const runCommandHandler = require('../commands/handlers/run_command');
    
    const cmd = input?.command || '';
    if (!cmd) {
      return { error: 'No command provided' };
    }
    
    console.log(`[sendMessage] Executing command in terminal: ${cmd}`);
    
    // Execute via terminal panel
    const result = await runCommandHandler({ command: cmd });
    
    return result;
    
  } catch (err) {
    console.error('[sendMessage] Terminal command execution failed:', err);
    return { error: String(err.message || err) };
  }
}

function sanitizeResult(toolName, input, result) {
  return sanitizeForToolHistory(toolName, input, result).result;
}

function sanitizeHistoryForSend(messages) {
  if (!Array.isArray(messages) || messages.length === 0) return messages;
  return messages.map(msg => {
    if (!msg || typeof msg !== 'object') return msg;

    const sanitized = { ...msg };
    if (sanitized.content !== undefined && typeof sanitized.content !== 'string') {
      try {
        sanitized.content = JSON.stringify(sanitized.content);
      } catch (_) {
        sanitized.content = String(sanitized.content);
      }
    }

    if (sanitized.role === 'assistant' && Array.isArray(sanitized.tool_calls)) {
      sanitized.tool_calls = sanitized.tool_calls.map(tc => {
        if (!tc || !tc.function) return tc;
        const fn = { ...tc.function };
        if (typeof fn.arguments === 'string') {
          try {
            JSON.parse(fn.arguments);
          } catch (_) {
            fn.arguments = '{}';
          }
        } else {
          try {
            fn.arguments = JSON.stringify(fn.arguments || {});
          } catch (_) {
            fn.arguments = '{}';
          }
        }
        return { ...tc, function: fn };
      });
    }

    return sanitized;
  });
}

// ============================================================================
// GPT API - WITH STREAMING SUPPORT
// ============================================================================

/**
 * Get GPT response with streaming support
 * @param {Object} options - Request options
 * @param {Function} onChunk - Callback for each text chunk: (fullText, newChunk) => void
 * @param {Function} onToolChunk - Callback for streaming tool updates: (toolCalls) => void
 * @returns {Promise<{text: string, tool_calls: array}>}
 */
async function getGptResponse(options, onChunk = null, onToolChunk = null) {
  const { prompt, systemPrompt, sessionId, toolResultHistory, rawHistory, signal } = options;
  
  // Check if already stopped before starting
  if (isStopRequested(sessionId)) {
    return { text: '', tool_calls: [], stopped: true };
  }
  
  // Detect model family for model-aware prompt and tool context
  let modelFamily = 'openai'; // default
  try {
    const selectedHoster = require('../modelSelector').getSelectedHoster();
    if (selectedHoster) {
      const hosterStr = String(selectedHoster).toLowerCase();
      if (hosterStr.includes('claude') || hosterStr.includes('anthropic')) {
        modelFamily = 'anthropic';
      } else if (hosterStr.includes('gpt5')) {
        modelFamily = 'gpt5plus';
      }
    }
  } catch (_) {}
  
  const payload = { app_id: 'editor-v1' };
  if (prompt) payload.user_prompt = prompt;
  
  // Use model-aware system prompt from new system
  if (!systemPrompt) {
    try {
      payload.system_prompt = getSystemPrompt(modelFamily);
    } catch (_) {
      // Fallback to old system
      try {
        payload.system_prompt = require('../ai-logic/instructions/logicalizer').getSystemPrompt();
      } catch (_) {}
    }
  } else {
    payload.system_prompt = systemPrompt;
  }
  
  if (sessionId) payload.session_id = sessionId;
  
  // IMPORTANT: Disable server-side history caching - we manage history ourselves
  payload.include_history = false;
  
  // Get tools with capability detection
  const toolDefs = getTools();
  const toolCapabilities = detectToolCapabilities(toolDefs);
  
  if (toolDefs?.length > 0) {
    payload.tools = toolDefs;
    payload.tool_choice = 'auto';
    payload.tool_capabilities = toolCapabilities; // Add capability metadata
  }
  
  // Attach raw history messages for context
  // This replaces server-side caching with client-managed history
  if (rawHistory && Array.isArray(rawHistory) && rawHistory.length > 0) {
    payload.history = sanitizeHistoryForSend(rawHistory);
    console.log(`[getGptResponse] Attaching ${rawHistory.length} raw history messages`);
  } else if (toolResultHistory) {
    // Fallback: Support both single tool result and array of tool results
    payload.history = Array.isArray(toolResultHistory) ? toolResultHistory : [toolResultHistory];
  }
  
  let selectedModel = null;
  try { selectedModel = require('../modelSelector').getSelectedModel(); } catch (_) {}
  
  let selectedHoster = null;
  try { selectedHoster = require('../modelSelector').getSelectedHoster(); } catch (_) {}
  
  let text = '';
  let toolCalls = null;
  
  try {
    // Pass abort signal and hoster to streamChat
    const streamOpts = { model: selectedModel || undefined };
    if (selectedHoster) streamOpts.hoster = selectedHoster;
    if (signal) streamOpts.signal = signal;
    
    for await (const chunk of getStreamChat()(payload, streamOpts)) {
      // Check for stop request on each chunk
      if (isStopRequested(sessionId)) {
        console.log('[getGptResponse] Stop requested, aborting stream');
        return { text, tool_calls: toolCalls || [], stopped: true };
      }
      
      if (chunk?.event === 'content' && typeof chunk.content === 'string') {
        const newContent = chunk.content;
        text += newContent;
        // Call streaming callback if provided
        if (onChunk) {
          try { onChunk(text, newContent); } catch (_) {}
        }
      } else if (chunk?.content && !chunk.event) {
        const newContent = chunk.content;
        text += newContent;
        if (onChunk) {
          try { onChunk(text, newContent); } catch (_) {}
        }
      } else if (chunk?.event === 'tool_calls' && Array.isArray(chunk.tool_calls)) {
        // Tool calls are streamed as they build up - update with latest state
        toolCalls = chunk.tool_calls;
        // Call tool streaming callback if provided
        if (onToolChunk) {
          try { onToolChunk(toolCalls); } catch (_) {}
        }
      } else if (chunk?.error) {
        return { error: String(chunk.error) };
      }
    }
  } catch (err) {
    return { error: String(err.message || err) };
  }
  
  return { text, tool_calls: toolCalls || [] };
}

// ============================================================================
// STREAMING RENDERER
// ============================================================================

/**
 * OPTIMIZED: Render streaming text with "Crafting tool" animation
 * KEY OPTIMIZATION: Uses incremental DOM appending instead of full re-renders
 * Only parses NEW content, not accumulated content
 * Prevents event listener duplication
 */
class StreamingRenderer {
  constructor(containerEl, previousContentHtml = '') {
    this.container = containerEl;
    this.previousContentHtml = previousContentHtml;
    this.currentText = '';
    this.lastRenderedText = ''; // Track what we've already rendered (KEY OPTIMIZATION)
    this.lastFormattedText = ''; // Track what we've formatted as markdown
    this.rawTextBuffer = ''; // Buffer for unformatted text (current incomplete paragraph)
    this.isInToolBlock = false;
    this.toolChars = 0;
    this.detectedToolName = 'Unknown';
    this.streamingToolCalls = null;
    
    // DOM elements - reuse instead of recreating
    this.textContentElement = null;
    this.rawTextElement = null; // For displaying raw unformatted text
    this.toolBoxElement = null;
    this.codeBlocksAttached = new Set(); // Track which elements have listeners (prevents duplication)
  }
  
  /**
   * Set the previous content HTML (rendered content from previous loops)
   */
  setPreviousContent(html) {
    this.previousContentHtml = html || '';
  }
  
  /**
   * Update with streaming tool calls from API
   */
  updateToolCalls(toolCalls) {
    if (!Array.isArray(toolCalls) || toolCalls.length === 0) return;
    
    this.streamingToolCalls = toolCalls;
    const tc = toolCalls[0];
    const fn = tc?.function || {};
    const toolName = fn.name || 'Unknown';
    const toolArgs = fn.arguments || '';
    
    this.detectedToolName = toolName;
    this.toolChars = toolArgs.length;
    this.isInToolBlock = true;
    
    this.renderWithCraftingTool(this.currentText);
  }
  
  /**
   * OPTIMIZED: Update with new streaming content
   * KEY CHANGE: Only parse the NEW part, not accumulated content
   */
  update(currentResponseText) {
    this.currentText = currentResponseText || '';
    
    if (this.streamingToolCalls && this.streamingToolCalls.length > 0) {
      this.renderWithCraftingTool(this.currentText);
      return;
    }
    
    const toolStartIdx = this.currentText.lastIndexOf('<tool_use>');
    const toolEndIdx = this.currentText.lastIndexOf('</tool_use>');
    
    if (toolStartIdx !== -1 && (toolEndIdx === -1 || toolEndIdx < toolStartIdx)) {
      this.isInToolBlock = true;
      const toolContent = this.currentText.slice(toolStartIdx + '<tool_use>'.length);
      this.toolChars = toolContent.length;
      this.detectedToolName = this.detectToolName(toolContent);
      const textBeforeTool = this.currentText.slice(0, toolStartIdx);
      this.renderWithCraftingTool(textBeforeTool);
    } else {
      this.isInToolBlock = false;
      this.toolChars = 0;
      this.detectedToolName = 'Unknown';
      this.renderStreamingText(this.currentText);
    }
  }
  
  /**
   * Detect tool name from partial JSON
   */
  detectToolName(partialJson) {
    const nameMatch = partialJson.match(/"name"\s*:\s*"([^"]*)/i);
    return (nameMatch && nameMatch[1]) ? nameMatch[1] : 'Unknown';
  }
  
  /**
   * OPTIMIZED: Render streaming text with incremental updates
   * Instead of: innerHTML = previous + current
   * Now uses: Append/update only changed content
   */
  renderStreamingText(text) {
    if (!this.container) return;
    
    try {
      marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });
    } catch (_) {}
    
    // PROGRESSIVE MARKDOWN: Get new text since last render
    const newText = text.slice(this.lastRenderedText.length);
    
    if (newText) {
      // Add new text to raw buffer
      this.rawTextBuffer += newText;
      
      // Check for paragraph breaks (double newline or single newline followed by content)
      // This indicates a complete sentence/paragraph that should be formatted
      const paragraphs = this.rawTextBuffer.split(/\n\n/);
      
      // Keep the last incomplete paragraph in the buffer
      // (it might be continued with more text)
      const lastParagraph = paragraphs[paragraphs.length - 1];
      const completedParagraphs = paragraphs.slice(0, -1);
      
      // Render completed paragraphs with markdown
      if (completedParagraphs.length > 0) {
        const completedText = completedParagraphs.join('\n\n');
        
        // Parse and format completed paragraphs
        const formattedHtml = marked.parse(completedText);
        
        // Find or create text content container
        if (!this.textContentElement) {
          this.textContentElement = document.createElement('div');
          this.textContentElement.className = 'gpt-streaming-content';
          this.container.appendChild(this.textContentElement);
        }
        
        // Append formatted HTML
        this.textContentElement.innerHTML += formattedHtml;
        
        // Remove raw text element if it exists (we're replacing it)
        if (this.rawTextElement && this.rawTextElement.parentNode) {
          this.rawTextElement.remove();
          this.rawTextElement = null;
        }
        
        // Update formatted tracking
        this.lastFormattedText += completedText + '\n\n';
      }
      
      // Show remaining raw text buffer (unformatted, for instant display)
      if (lastParagraph.trim()) {
        if (!this.rawTextElement) {
          this.rawTextElement = document.createElement('pre');
          this.rawTextElement.className = 'gpt-raw-text-buffer';
          this.rawTextElement.style.whiteSpace = 'pre-wrap';
          this.rawTextElement.style.wordWrap = 'break-word';
          this.rawTextElement.style.fontFamily = 'inherit';
          this.rawTextElement.style.margin = '0';
          
          // Find or create text content container
          if (!this.textContentElement) {
            this.textContentElement = document.createElement('div');
            this.textContentElement.className = 'gpt-streaming-content';
            this.container.appendChild(this.textContentElement);
          }
          
          this.textContentElement.appendChild(this.rawTextElement);
        }
        
        // Update raw text display
        this.rawTextElement.textContent = lastParagraph;
      }
      
      this.rawTextBuffer = lastParagraph;
    }
    
    // Remove tool box if it exists (we're not in a tool anymore)
    if (this.toolBoxElement && this.toolBoxElement.parentNode) {
      this.toolBoxElement.remove();
      this.toolBoxElement = null;
    }
    
    // OPTIMIZED: Only attach listeners to new code blocks
    this._attachListenersToNewCodeBlocks();
    
    this.lastRenderedText = text;
  }
  
  /**
   * Finalize streaming text - format any remaining raw text as markdown
   */
  finalizeStreamingText() {
    if (!this.rawTextBuffer.trim()) return;
    
    try {
      marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });
    } catch (_) {}
    
    // Format the remaining raw text buffer
    const formattedHtml = marked.parse(this.rawTextBuffer);
    
    if (!this.textContentElement) {
      this.textContentElement = document.createElement('div');
      this.textContentElement.className = 'gpt-streaming-content';
      this.container.appendChild(this.textContentElement);
    }
    
    // Append formatted HTML
    this.textContentElement.innerHTML += formattedHtml;
    
    // Remove raw text element
    if (this.rawTextElement && this.rawTextElement.parentNode) {
      this.rawTextElement.remove();
      this.rawTextElement = null;
    }
    
    // Clear the buffer
    this.rawTextBuffer = '';
    this.lastFormattedText = this.lastRenderedText;
  }
  
  /**
   * OPTIMIZED: Render text + crafting tool animation
   * Uses incremental updates instead of full re-renders
   */
  renderWithCraftingTool(textBeforeTool) {
    if (!this.container) return;
    
    try {
      marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });
    } catch (_) {}
    
    // PROGRESSIVE MARKDOWN: Get new text since last render
    const newText = textBeforeTool.slice(this.lastRenderedText.length);
    
    if (newText) {
      // Add new text to raw buffer
      this.rawTextBuffer += newText;
      
      // Check for paragraph breaks
      const paragraphs = this.rawTextBuffer.split(/\n\n/);
      const lastParagraph = paragraphs[paragraphs.length - 1];
      const completedParagraphs = paragraphs.slice(0, -1);
      
      // Render completed paragraphs with markdown
      if (completedParagraphs.length > 0) {
        const completedText = completedParagraphs.join('\n\n');
        const formattedHtml = marked.parse(completedText);
        
        if (!this.textContentElement) {
          this.textContentElement = document.createElement('div');
          this.textContentElement.className = 'gpt-streaming-content';
          this.container.appendChild(this.textContentElement);
        }
        
        this.textContentElement.innerHTML += formattedHtml;
        
        if (this.rawTextElement && this.rawTextElement.parentNode) {
          this.rawTextElement.remove();
          this.rawTextElement = null;
        }
        
        this.lastFormattedText += completedText + '\n\n';
      }
      
      // Show remaining raw text (incomplete paragraph)
      if (lastParagraph.trim()) {
        if (!this.rawTextElement) {
          this.rawTextElement = document.createElement('pre');
          this.rawTextElement.className = 'gpt-raw-text-buffer';
          this.rawTextElement.style.whiteSpace = 'pre-wrap';
          this.rawTextElement.style.wordWrap = 'break-word';
          this.rawTextElement.style.fontFamily = 'inherit';
          this.rawTextElement.style.margin = '0';
          
          if (!this.textContentElement) {
            this.textContentElement = document.createElement('div');
            this.textContentElement.className = 'gpt-streaming-content';
            this.container.appendChild(this.textContentElement);
          }
          
          this.textContentElement.appendChild(this.rawTextElement);
        }
        
        this.rawTextElement.textContent = lastParagraph;
      }
      
      this.rawTextBuffer = lastParagraph;
    }
    
    // Update or create tool box
    if (!this.toolBoxElement) {
      this.toolBoxElement = this._createToolBox();
      this.container.appendChild(this.toolBoxElement);
    } else {
      // Update existing tool box
      this._updateToolBox(this.toolBoxElement);
    }
    
    // OPTIMIZED: Only attach listeners to new code blocks
    this._attachListenersToNewCodeBlocks();
    
    this.lastRenderedText = textBeforeTool;
  }
  
  /**
   * Create tool box element once
   */
  _createToolBox() {
    const toolNameDisplay = this.detectedToolName !== 'Unknown' 
      ? `<span class="tool-detected">${escapeHTML(this.detectedToolName)}</span>`
      : '<span class="tool-unknown">Detecting...</span>';
    
    const toolBox = document.createElement('div');
    toolBox.className = 'auaci-tool-box crafting-tool';
    toolBox.innerHTML = `
      <div class="tool-header">
        <span class="auaci-tool-name crafting-animation">
          Crafting a tool
        </span>
        <span class="crafting-stats">
          (${this.toolChars} chars)
        </span>
      </div>
      <div class="tool-body crafting-body">
        <div class="crafting-info">
          <span class="crafting-label">Tool Type:</span>
          ${toolNameDisplay}
        </div>
        <div class="crafting-progress">
          <div class="crafting-bar"></div>
        </div>
      </div>
    `;
    return toolBox;
  }
  
  /**
   * Update tool box stats only (not HTML replacement)
   */
  _updateToolBox(toolBox) {
    const statsSpan = toolBox.querySelector('.crafting-stats');
    if (statsSpan) {
      statsSpan.textContent = `(${this.toolChars} chars)`;
    }
    
    const nameSpan = toolBox.querySelector('.crafting-info');
    if (nameSpan && this.detectedToolName !== 'Unknown') {
      const toolNameDisplay = nameSpan.querySelector('.tool-detected');
      if (toolNameDisplay) {
        toolNameDisplay.textContent = this.detectedToolName;
      }
    }
  }
  
  /**
   * OPTIMIZED: Only attach listeners to NEW code blocks
   * Tracks which blocks already have listeners to prevent duplication
   */
  _attachListenersToNewCodeBlocks() {
    if (!this.container) return;
    
    const codeBlocks = this.container.querySelectorAll('.copy-button');
    codeBlocks.forEach((btn) => {
      const btnId = btn.id;
      if (btnId && !this.codeBlocksAttached.has(btnId)) {
        // This is a new button - attach listener
        try {
          btn.addEventListener('click', (e) => {
            e.preventDefault();
            const code = decodeURIComponent(btn.dataset.code || '');
            if (code) {
              navigator.clipboard.writeText(code).then(() => {
                const checkIcon = btn.querySelector('.check-icon');
                const copyIcon = btn.querySelector('.copy-icon');
                if (checkIcon && copyIcon) {
                  copyIcon.style.display = 'none';
                  checkIcon.style.display = 'inline';
                  setTimeout(() => {
                    copyIcon.style.display = 'inline';
                    checkIcon.style.display = 'none';
                  }, 2000);
                }
              });
            }
          });
          this.codeBlocksAttached.add(btnId);
        } catch (_) {}
      }
    });
  }
  
  /**
   * Reset for new response (keeps previous content)
   */
  reset() {
    this.currentText = '';
    this.lastRenderedText = '';
    this.lastFormattedText = '';
    this.rawTextBuffer = '';
    this.isInToolBlock = false;
    this.toolChars = 0;
    this.detectedToolName = 'Unknown';
    this.streamingToolCalls = null;
    this.textContentElement = null;
    if (this.rawTextElement && this.rawTextElement.parentNode) {
      this.rawTextElement.remove();
    }
    this.rawTextElement = null;
    if (this.toolBoxElement && this.toolBoxElement.parentNode) {
      this.toolBoxElement.remove();
    }
    this.toolBoxElement = null;
    this.codeBlocksAttached.clear();
  }
  
  /**
   * Clear everything (including previous content)
   */
  clear() {
    this.previousContentHtml = '';
    this.currentText = '';
    this.lastRenderedText = '';
    this.lastFormattedText = '';
    this.rawTextBuffer = '';
    this.isInToolBlock = false;
    this.toolChars = 0;
    this.detectedToolName = 'Unknown';
    this.streamingToolCalls = null;
    this.textContentElement = null;
    this.rawTextElement = null;
    this.toolBoxElement = null;
    this.codeBlocksAttached.clear();
    if (this.container) {
      this.container.innerHTML = '';
    }
  }
}

// ============================================================================
// STOP HANDLING
// ============================================================================

/**
 * Check if stop was requested for a session
 */
function isStopRequested(sessionId) {
  try {
    const stopped = window.__auaciStoppedSessions || {};
    if (stopped[sessionId]) return true;
    const stopReq = window.__auaciStopRequested || {};
    if (stopReq[sessionId]) return true;
  } catch (_) {}
  return false;
}

/**
 * Clear stop state for a session
 */
function clearStopState(sessionId) {
  try {
    if (window.__auaciStoppedSessions) window.__auaciStoppedSessions[sessionId] = false;
    if (window.__auaciStopRequested) window.__auaciStopRequested[sessionId] = false;
  } catch (_) {}
}

// ============================================================================
// MAIN ASSISTANT FUNCTION
// ============================================================================

async function MainAssistant() {
  // Handle background tool completion events
  if (typeof window !== 'undefined') {
    window.addEventListener('tool-completed-in-background', (event) => {
      console.log('[MainAssistant] Tool completed in background:', event.detail);
      const { sessionId, entryIndex, toolName, toolId } = event.detail;
      
      // Store the background tool completion for later processing
      if (!window._backgroundToolCompletions) {
        window._backgroundToolCompletions = [];
      }
      window._backgroundToolCompletions.push({
        sessionId,
        entryIndex,
        toolName,
        toolId,
        timestamp: Date.now()
      });
      
      console.log(`[MainAssistant] Queued background tool ${toolName} for session ${sessionId}`);
    });
    
    // NOTE: Removed focus event listener that triggered displayChatHistory() on refocus.
    // The app now maintains continuous rendering even when unfocused, so there is no need
    // to re-render the entire message history when the app regains focus.
    // Background tool completions are handled through the streaming update mechanism.
  }

  const userInputEl = document.getElementById('user-input');
  let userInput = '';
  if (userInputEl) {
    // Handle both input/textarea (value) and contenteditable (innerText preserves line breaks)
    if (userInputEl.value !== undefined && userInputEl.value !== null) {
      userInput = String(userInputEl.value).trim();
    } else {
      // Use innerText for contenteditable to preserve line breaks
      userInput = String(userInputEl.innerText || '').trim();
    }
  }
  const chatMessages = document.getElementById('chat-messages');
  let droppedFiles = window.droppedFiles || [];
  
  // Setup smart scroll tracking for chat messages
  if (chatMessages) {
    try {
      const { setupScrollTracking } = require('../helpers/smartScroll');
      setupScrollTracking(chatMessages);
    } catch (err) {
      console.warn('[MainAssistant] Failed to setup scroll tracking:', err);
    }
  }
  
  // Validate input
  if (!userInput && !droppedFiles.length) {
    showEmptyWarning();
    return;
  }
  
  // Check for pending edit (user clicked "edit" on a previous message)
  try {
    const { handlePendingEdit } = require('../messageContextMenu');
    const shouldCancel = await handlePendingEdit();
    if (shouldCancel) {
      return; // User cancelled the edit resend
    }
  } catch (_) {}
  
  // Ensure session
  try { await tabManager.ensureActiveSession(); } catch (_) {}
  if (!chatMessages) return;
  
  // Remove placeholders
  try {
    chatMessages.querySelectorAll('.auaci-empty-session, .auaci-empty-project').forEach(p => p.remove());
  } catch (_) {}
  
  // Get session ID
  let sessionId = null;
  try {
    const { getActiveSessionId } = require('../tabManager');
    sessionId = await getActiveSessionId();
    if (!sessionId) {
      sessionId = await getSessionId();
    }
  } catch (_) {
    try { sessionId = await getSessionId(); } catch (_) {}
  }
  
  // Clear any previous stop state for this session
  clearStopState(sessionId);
  
  // Initialize concurrent session state
  const sessionState = getSessionState(sessionId);
  if (sessionState) {
    sessionState.reset(); // Clear any previous state
    sessionState.startResponding();
    sessionState.currentEntryIndex = null; // Will be set after initiateChatEntry
  }
  
  // Set this as the active session for rendering
  try { setActiveSessionId(sessionId); } catch (_) {}
  
  // Create abort controller for this session
  const abortController = new AbortController();
  try {
    window.__auaciAbortControllers = window.__auaciAbortControllers || {};
    window.__auaciAbortControllers[sessionId] = abortController;
  } catch (_) {}
  
  // Store abort controller in session state for concurrent management
  if (sessionState) {
    sessionState.abortController = abortController;
  }
  
  // Track this session as responding (legacy + new)
  try {
    window.__auaciRespondingSessions = window.__auaciRespondingSessions || {};
    window.__auaciRespondingSessions[sessionId] = true;
  } catch (_) {}
  
  // Wire up stop button for immediate termination
  try {
    const stopBtn = document.getElementById('stop-response-btn');
    if (stopBtn) {
      stopBtn.disabled = false;
      stopBtn.onclick = () => {
        console.log('[MainAssistant] Stop button clicked - immediate termination');
        // Set stop flags immediately
        try { 
          window.__auaciStoppedSessions = window.__auaciStoppedSessions || {}; 
          window.__auaciStoppedSessions[sessionId] = true; 
        } catch (_) {}
        try { 
          window.__auaciStopRequested = window.__auaciStopRequested || {}; 
          window.__auaciStopRequested[sessionId] = true; 
        } catch (_) {}
        // Update concurrent session state
        if (sessionState) {
          sessionState.stopResponding();
          sessionState.complete();
        }
        // Abort the fetch immediately
        try { abortController.abort(); } catch (_) {}
        // Disable button
        try { stopBtn.disabled = true; } catch (_) {}
        // Hide indicator immediately
        try { hideGptIndicator(sessionId); } catch (_) {}
        // Mark session as not responding
        try { 
          if (window.__auaciRespondingSessions) window.__auaciRespondingSessions[sessionId] = false; 
        } catch (_) {}
        window.isGptResponding = false;
        // End connection monitoring
        markRequestEnd();
        // Refresh tabs to update indicators
        try { tabManager.refreshTabs(); } catch (_) {}
      };
    }
  } catch (_) {}
  
  // Get system prompt
  let systemPrompt = '';
  try {
    systemPrompt = await require('../ai-logic/instructions/logicalizer').getSystemPrompt();
    
    // Get the current working directory from terminalInput (respects cd commands)
    let projectPath = null;
    try {
      const { getCurrentCwd } = require('../ui/terminalInput');
      projectPath = getCurrentCwd();
    } catch (_) {}
    
    // Fallback to project root if terminalInput cwd not available
    if (!projectPath) {
      try {
        const { ipcRenderer } = require('electron');
        projectPath = await ipcRenderer.invoke('get-project-root');
      } catch (_) {}
    }
    if (!projectPath) try { projectPath = process.cwd(); } catch (_) {}
    
    if (projectPath) systemPrompt += `\n\nProject Details\n===============\nCurrent Working Directory: ${projectPath}`;
    
    // Add current date/time context
    const now = new Date();
    const dateStr = now.toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
    const timeStr = now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
    systemPrompt += `\n\nCurrent Date/Time\n=================\nDate: ${dateStr}\nTime: ${timeStr}\nUse this for any queries involving dates, times, or scheduling.`;
    
    // Add system information
    const os = require('os');
    const platform = os.platform();
    const platformName = platform === 'darwin' ? 'macOS' : platform === 'win32' ? 'Windows' : platform === 'linux' ? 'Linux' : platform;
    const shell = process.env.SHELL || (platform === 'win32' ? 'cmd/powershell' : 'unknown');
    systemPrompt += `\n\nSystem Information\n==================\nOperating System: ${platformName}\nShell: ${shell}`;
  } catch (_) {}
  
  // Prepare files for history
  const filesForHistory = droppedFiles.map(f => ({
    name: f.name,
    size: f.size,
    path: f.path || f.filePath || f.filepath,
    content: f.content
  }));
  
  // Initialize history entry
  let entryIndex = null;
  try {
    entryIndex = await initiateChatEntry(userInput, filesForHistory, sessionId);
    console.log(`[MainAssistant] Entry index: ${entryIndex}`);
    
    // Invalidate any restore-undo state once a new message is sent
    try {
      const { ipcRenderer } = require('electron');
      await ipcRenderer.invoke('git-invalidate-restore-undo', sessionId);
      window.__restoreUndoAvailable = false;
    } catch (_) {}

    // Create git checkpoint before processing this message (main process)
    try {
      const { ipcRenderer } = require('electron');
      await ipcRenderer.invoke('git-create-checkpoint', sessionId, entryIndex, userInput);
    } catch (err) {
      console.warn('[MainAssistant] Failed to create checkpoint:', err);
    }
    
    // Update session state with entry index
    if (sessionState) {
      sessionState.currentEntryIndex = entryIndex;
    }
    
    // Sync window.allChatEntries with the new entry for edit functionality
    // This ensures edit works on live-streamed messages without needing a refresh
    if (!Array.isArray(window.allChatEntries)) {
      window.allChatEntries = [];
    }
    // Ensure the array is large enough
    while (window.allChatEntries.length < entryIndex) {
      window.allChatEntries.push({ user: { text: '', files: [] }, gpt: '' });
    }
    // Add or update the entry at the correct index
    window.allChatEntries[entryIndex] = {
      user: { text: userInput, files: filesForHistory },
      gpt: '',
      status: 'streaming'
    };
  } catch (err) {
    console.error('[MainAssistant] Failed to init entry:', err);
  }
  
  // Render user message
  renderUserMessage(chatMessages, userInput, droppedFiles, entryIndex);
  scrollToBottom(chatMessages);
  
  // Clear input
  if (userInputEl) {
    if (userInputEl.value !== undefined) userInputEl.value = '';
    else userInputEl.textContent = '';
  }
  window.droppedFiles = [];
  
  // Create GPT message container
  const gptDiv = document.createElement('div');
  gptDiv.className = 'message gpt-message';
  gptDiv.setAttribute('data-entry-index', String(entryIndex));
  const gptContent = document.createElement('div');
  gptContent.className = 'message-content';
  gptDiv.appendChild(gptContent);
  chatMessages.appendChild(gptDiv);
  
  // Show indicator
  window.isGptResponding = true;
  if (shouldRenderForSession(sessionId)) showGptIndicator(sessionId);
  
  // Start connection monitoring
  startMonitoring();
  markRequestStart(sessionId);
  
  // Create streaming renderer for live updates
  const streamingRenderer = new StreamingRenderer(gptContent);
  
  try {
    // Build prompt (full prompt with file context)
    const prompt = await buildPrompt(userInput, droppedFiles, { recentCount: 2 });
    await writeUserRequest(prompt);
    
    // Save user message to raw history (full prompt for GPT context)
    await appendUserMessage(sessionId, prompt);
    console.log(`[MainAssistant] Saved user message to raw history`);
    
    // Main GPT cycle
    let currentGptContent = '';
    let currentPrompt = prompt;
    let loopCount = 0;
    const MAX_LOOPS = 50;
    const toolRuns = [];
    
    // Track rendered HTML from previous loops for continuity
    let previousRenderedHtml = '';
    
    while (loopCount < MAX_LOOPS) {
      // Check for stop request at start of each loop
      if (isStopRequested(sessionId)) {
        console.log('[MainAssistant] Stop requested, breaking loop');
        break;
      }
      
      loopCount++;
      console.log(`[MainAssistant] Loop ${loopCount}`);
      
      // Reset streaming renderer for new response, but keep previous content
      streamingRenderer.reset();
      streamingRenderer.setPreviousContent(previousRenderedHtml);
      
      // Show "GPT is thinking" message while waiting for first chunk
      // Append it to existing content, don't replace
      let thinkingRef = null;
      let hasReceivedContent = false;
      if (shouldRenderForSession(sessionId)) {
        // First render previous content, then append thinking indicator
        gptContent.innerHTML = previousRenderedHtml;
        thinkingRef = showThinking(gptContent);
        scrollToBottom(chatMessages);
        
        // Capture DOM state during thinking phase for concurrent session restoration
        if (sessionState) {
          sessionState.captureDomState(chatMessages);
        }
      }
      
      // Load raw history for GPT context (replaces server-side caching)
      const rawHistory = await getMessagesForGpt(sessionId);
      console.log(`[MainAssistant] Loaded ${rawHistory.length} raw history messages for GPT`);
      
      // Get GPT response WITH STREAMING and raw history
      const response = await getGptResponse({
        prompt: loopCount === 1 ? currentPrompt : null, // Only send prompt on first loop
        systemPrompt,
        sessionId,
        rawHistory, // Attach full conversation history
        signal: abortController.signal // Pass abort signal for immediate cancellation
      }, (fullText, newChunk) => {
        // Check for stop on each chunk
        if (isStopRequested(sessionId)) return;
        
        // Mark activity to reset timeout warning
        markRequestActivity();
        
        // Update session state with stream buffer (for concurrent session restoration)
        if (sessionState) {
          sessionState.updateStreamBuffer(fullText, newChunk);
        }
        
        // Hide thinking indicator on first content
        if (!hasReceivedContent && thinkingRef) {
          hideThinking(thinkingRef);
          thinkingRef = null;
          hasReceivedContent = true;
        }
        
        // OPTIMIZATION: Throttle streaming updates to reduce lag
        // Only update every 100ms instead of on every chunk
        const now = Date.now();
        if (!streamingRenderer._lastUpdateTime) streamingRenderer._lastUpdateTime = 0;
        const timeSinceLastUpdate = now - streamingRenderer._lastUpdateTime;
        
        // Update if: 1) enough time passed, 2) first chunk, or 3) text is short (near end)
        const shouldUpdate = timeSinceLastUpdate >= 100 || fullText.length < 50;
        
        if (shouldUpdate && shouldRenderForSession(sessionId)) {
          // IMPORTANT: Dynamically find the current GPT content element
          // This handles the case where user switched sessions and displayChatHistory
          // created new DOM elements - we need to render to the CURRENT element, not the stale one
          const currentChatMessages = document.getElementById('chat-messages');
          let currentGptContent = gptContent;
          try {
            if (currentChatMessages && entryIndex !== null) {
              const currentGptDiv = currentChatMessages.querySelector(`.message.gpt-message[data-entry-index="${entryIndex}"]`);
              if (currentGptDiv) {
                const foundContent = currentGptDiv.querySelector('.message-content');
                if (foundContent && foundContent !== streamingRenderer.container) {
                  // Container changed - user switched sessions and came back
                  // Update the streaming renderer's container reference
                  currentGptContent = foundContent;
                  streamingRenderer.container = currentGptContent;
                  
                  // Sync previousContentHtml from session state
                  if (sessionState && sessionState.previousRenderedHtml) {
                    streamingRenderer.setPreviousContent(sessionState.previousRenderedHtml);
                  }
                }
              }
            }
          } catch (_) {}
          
          streamingRenderer.update(fullText);
          scrollToBottom(currentChatMessages || chatMessages);
          streamingRenderer._lastUpdateTime = now;
          
          // Update multi-view panel if this session is in multi-view
          try {
            if (isInMultiView(sessionId)) {
              updateMultiViewPanelContent(sessionId, fullText, toolRuns);
            }
          } catch (_) {}
          
          // Capture DOM state for concurrent session restoration
          // This allows switching to this session and seeing the current streaming state
          if (sessionState && currentChatMessages) {
            sessionState.captureDomState(currentChatMessages);
          }
        }
      }, (toolCalls) => {
        // Check for stop on tool streaming
        if (isStopRequested(sessionId)) return;
        
        // Mark activity to reset timeout warning
        markRequestActivity();
        
        // Tool streaming callback - update crafting animation with tool data
        if (!hasReceivedContent && thinkingRef) {
          hideThinking(thinkingRef);
          thinkingRef = null;
          hasReceivedContent = true;
        }
        
        // OPTIMIZATION: Throttle tool call updates to reduce lag
        const now = Date.now();
        if (!streamingRenderer._lastToolUpdateTime) streamingRenderer._lastToolUpdateTime = 0;
        const timeSinceLastUpdate = now - streamingRenderer._lastToolUpdateTime;
        
        // Update every 150ms for tool calls (less frequent than text)
        if (timeSinceLastUpdate >= 150 && shouldRenderForSession(sessionId)) {
          // IMPORTANT: Dynamically find the current GPT content element
          const currentChatMessages = document.getElementById('chat-messages');
          try {
            if (currentChatMessages && entryIndex !== null) {
              const currentGptDiv = currentChatMessages.querySelector(`.message.gpt-message[data-entry-index="${entryIndex}"]`);
              if (currentGptDiv) {
                const foundContent = currentGptDiv.querySelector('.message-content');
                if (foundContent) {
                  streamingRenderer.container = foundContent;
                }
              }
            }
          } catch (_) {}
          
          streamingRenderer.updateToolCalls(toolCalls);
          scrollToBottom(currentChatMessages || chatMessages);
          streamingRenderer._lastToolUpdateTime = now;
        }
      });
      
      // Ensure thinking indicator is hidden after response completes
      if (thinkingRef) {
        hideThinking(thinkingRef);
        thinkingRef = null;
      }
      
      // Finalize any remaining raw text buffer as markdown
      // (formats the last incomplete paragraph when streaming completes)
      streamingRenderer.finalizeStreamingText();
      
      // Check if stopped during response
      if (response.stopped || isStopRequested(sessionId)) {
        console.log('[MainAssistant] Stopped during GPT response');
        break;
      }
      
      if (response.error) {
        throw new Error(response.error);
      }
      
      const gptText = response.text || '';
      const apiToolCalls = response.tool_calls || [];
      
      console.log(`[MainAssistant] Response: ${gptText.length} chars, ${apiToolCalls.length} API tools`);
      
      // Save assistant response to raw history (with tool_calls if any)
      // Format tool_calls in OpenAI format for raw history
      const toolCallsForHistory = apiToolCalls.length > 0 ? apiToolCalls.map(tc => ({
        id: tc.id,
        type: 'function',
        function: {
          name: tc.function?.name || '',
          arguments: typeof tc.function?.arguments === 'string' 
            ? tc.function.arguments 
            : JSON.stringify(tc.function?.arguments || {})
        }
      })) : null;
      
      await appendAssistantMessage(sessionId, gptText, toolCallsForHistory);
      console.log(`[MainAssistant] Saved assistant message to raw history`);
      
      // Build response content - DON'T add tool blocks yet for API tools
      // We'll add them as we execute each tool to avoid orphaned _tool_id
      let responseContent = gptText;
      const toolsToExecute = [];
      
      if (apiToolCalls.length > 0) {
        for (const tc of apiToolCalls) {
          const fn = tc.function || {};
          let args = {};
          try { args = typeof fn.arguments === 'string' ? JSON.parse(fn.arguments) : (fn.arguments || {}); } catch (_) {}
          
          const tool = { id: tc.id, name: fn.name || '', input: args };
          toolsToExecute.push(tool);
          // Don't add tool block here - we'll add it when executing
        }
      } else {
        // Parse tools from text - these already exist in gptText
        const segments = parseResponseSegments(gptText);
        for (const seg of segments) {
          if (seg.type === 'tool') {
            toolsToExecute.push({
              id: `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
              name: seg.name,
              input: seg.input
            });
          }
        }
      }
      
      // Accumulate content
      if (currentGptContent) {
        currentGptContent = currentGptContent.trim() + '\n\n' + responseContent.trim();
      } else {
        currentGptContent = responseContent;
      }
      
      // Save to display history and wait for it to complete
      if (entryIndex !== null) {
        await updateGptResponse(entryIndex, currentGptContent, false, sessionId);
        // Small delay to ensure file system write completes before rendering
        await new Promise(resolve => setTimeout(resolve, 50));
      }
      
      // Final render using history render (proper tool display)
      if (shouldRenderForSession(sessionId)) {
        // Get current GPT content element (handles session switches)
        const { element: currentEl, chatMessages: currentChat } = getCurrentGptContent(entryIndex, gptContent);
        renderGptResponse(currentEl, currentGptContent, toolRuns);
        scrollToBottom(currentChat);
        // Capture rendered HTML for next loop's previous content
        previousRenderedHtml = currentEl.innerHTML;
        
        // Update multi-view panel if this session is in multi-view
        try {
          if (isInMultiView(sessionId)) {
            updateMultiViewPanelContent(sessionId, currentGptContent, toolRuns);
          }
        } catch (_) {}
        
        // Capture DOM state for concurrent session restoration
        if (sessionState) {
          sessionState.captureDomState(currentChat);
          // Also update accumulated content and tool runs for restoration
          sessionState.setAccumulatedContent(currentGptContent);
          sessionState.toolRuns = [...toolRuns];
        }
      }
      
      // No tools? Done
      if (toolsToExecute.length === 0) {
        console.log('[MainAssistant] No tools, done');
        break;
      }
      
      // Execute tools
      let shouldContinue = false;
      const toolResults = []; // Collect ALL tool results for sending back to GPT
      const isApiToolCalls = apiToolCalls.length > 0; // Track if tools came from API
      
      for (let toolIdx = 0; toolIdx < toolsToExecute.length; toolIdx++) {
        // Check for stop request before each tool
        if (isStopRequested(sessionId)) {
          console.log('[MainAssistant] Stop requested, breaking tool loop');
          break;
        }
        
        const tool = toolsToExecute[toolIdx];
        const toolName = normalizeToolName(tool.name);
        
        // Check for finalization - but still save the attempt_completion to history
        if (toolName === 'attempt_completion') {
          console.log('[MainAssistant] attempt_completion, saving and finalizing');
          
          // The attempt_completion result is in the input (result field)
          const attemptResult = {
            result: tool.input?.result || tool.input?.text || tool.input?.message || '',
            success: true
          };
          
          // Add to toolRuns for rendering
          toolRuns.push({
            name: toolName,
            input: tool.input,
            result: attemptResult,
            success: true
          });
          
          // For API tools, add the tool block with the result
          if (isApiToolCalls) {
            const attemptBlock = buildToolBlock(toolName, tool.input, attemptResult);
            currentGptContent = currentGptContent.trim() + '\n\n' + attemptBlock;
          } else {
            // For text-parsed tools, update existing block
            currentGptContent = updateToolBlockWithResponse(currentGptContent, toolName, attemptResult, tool.id);
          }
          
          // Save to display history
          if (entryIndex !== null) {
            await updateGptResponse(entryIndex, currentGptContent, false, sessionId);
          }
          
          // Re-render
          if (shouldRenderForSession(sessionId)) {
            const { element: currentEl, chatMessages: currentChat } = getCurrentGptContent(entryIndex, gptContent);
            renderGptResponse(currentEl, currentGptContent, toolRuns);
            scrollToBottom(currentChat);
            previousRenderedHtml = currentEl.innerHTML;
            
            // Update multi-view panel if this session is in multi-view
            try {
              if (isInMultiView(sessionId)) {
                updateMultiViewPanelContent(sessionId, currentGptContent, toolRuns);
              }
            } catch (_) {}
            
            // Update session state for restoration
            if (sessionState) {
              sessionState.captureDomState(currentChat);
              sessionState.setAccumulatedContent(currentGptContent);
              sessionState.toolRuns = [...toolRuns];
            }
          }
          
          break;
        }
        
        // Add pending entry to toolRuns BEFORE execution so executing state renders
        const toolRunIndex = toolRuns.length;
        toolRuns.push({
          name: toolName,
          input: tool.input,
          result: null, // null triggers executing state render
          success: undefined
        });
        
        // Update session state for tool execution
        if (sessionState) {
          sessionState.startToolExecution(toolRunIndex, toolName);
          sessionState.toolRuns = [...toolRuns]; // Copy for state tracking
        }
        
        // For API tools, add the tool block with null result to show executing state
        if (isApiToolCalls) {
          const pendingBlock = buildToolBlock(toolName, tool.input, null);
          currentGptContent = currentGptContent.trim() + '\n\n' + pendingBlock;
          
          // Save and re-render to show executing state
          if (entryIndex !== null) {
            await updateGptResponse(entryIndex, currentGptContent, false, sessionId);
          }
          if (shouldRenderForSession(sessionId)) {
            const { element: currentEl, chatMessages: currentChat } = getCurrentGptContent(entryIndex, gptContent);
            renderGptResponse(currentEl, currentGptContent, toolRuns);
            scrollToBottom(currentChat);
            previousRenderedHtml = currentEl.innerHTML;
            
            // Update multi-view panel if this session is in multi-view
            try {
              if (isInMultiView(sessionId)) {
                updateMultiViewPanelContent(sessionId, currentGptContent, toolRuns);
              }
            } catch (_) {}
            
            // Update session state for restoration
            if (sessionState) {
              sessionState.captureDomState(currentChat);
            }
          }
        }
        
        // Update session state with accumulated content
        if (sessionState) {
          sessionState.setAccumulatedContent(currentGptContent);
          sessionState.setPreviousRenderedHtml(previousRenderedHtml);
          sessionState.toolRuns = [...toolRuns];
        }
        
        // Execute tool
        markToolStart(); // Pause API timeout tracking during tool execution
        let result = await executeTool(toolName, tool.input, { sessionId, toolIndex: toolRunIndex, entryIndex });
        markToolEnd(); // Resume API timeout tracking
        
        // Check for stop after tool execution
        if (isStopRequested(sessionId)) {
          console.log('[MainAssistant] Stop requested after tool execution');
          break;
        }
        
        // Debug: log if view tool has content
        if (toolName === 'view') {
          console.log(`[MainAssistant] View result has content: ${!!result?.content}, length: ${result?.content?.length || 0}`);
        }
        
        let sanitized = sanitizeResult(toolName, tool.input, result);
        
        // Handle interactive tools (ask) - wait for user input
        if (toolName === 'ask' && result?.waiting === true) {
          console.log('[MainAssistant] Ask tool waiting for user input...');
          
          // Update session state to awaiting input
          if (sessionState) {
            sessionState.setAwaitingInput(toolRunIndex, toolName);
          }
          
          // Refresh tabs to show yellow indicator
          try { tabManager.refreshTabs(); } catch (_) {}
          
          // Pause API timeout tracking while waiting for user input
          markToolStart();
          
          // Update the tool block with waiting state
          if (isApiToolCalls) {
            // For API tools, update the pending block we added earlier
            currentGptContent = updateToolBlockWithResponse(currentGptContent, toolName, sanitized, null);
          } else {
            // For text-parsed tools, update existing block
            currentGptContent = updateToolBlockWithResponse(currentGptContent, toolName, sanitized, tool.id);
          }
          
          if (entryIndex !== null) {
            await updateGptResponse(entryIndex, currentGptContent, false, sessionId);
            await new Promise(resolve => setTimeout(resolve, 50));
          }
          
          // Update the existing toolRuns entry with waiting state
          toolRuns[toolRunIndex].result = sanitized;
          toolRuns[toolRunIndex].success = undefined; // pending
          
          // Re-render to show the ask UI
          if (shouldRenderForSession(sessionId)) {
            const { element: currentEl, chatMessages: currentChat } = getCurrentGptContent(entryIndex, gptContent);
            renderGptResponse(currentEl, currentGptContent, toolRuns);
            scrollToBottom(currentChat);
            previousRenderedHtml = currentEl.innerHTML;
            
            // Update multi-view panel if this session is in multi-view
            try {
              if (isInMultiView(sessionId)) {
                updateMultiViewPanelContent(sessionId, currentGptContent, toolRuns);
              }
            } catch (_) {}
            
            // Update session state for restoration
            if (sessionState) {
              sessionState.captureDomState(currentChat);
              sessionState.setAccumulatedContent(currentGptContent);
              sessionState.toolRuns = [...toolRuns];
            }
          }
          
          // Wait for user to answer
          const answer = await waitForAskAnswer(toolRunIndex);
          console.log('[MainAssistant] Ask tool answered:', answer);
          
          // Clear awaiting input state
          if (sessionState) {
            sessionState.clearAwaitingInput();
          }
          
          // Refresh tabs to remove yellow indicator
          try { tabManager.refreshTabs(); } catch (_) {}
          // Resume API timeout tracking after user answers
          markToolEnd();
          
          // Update result with the answer
          result = answer;
          sanitized = sanitizeResult(toolName, tool.input, result);
          
          // Debug logging for ask tool answer
          console.log('[MainAssistant] Ask tool sanitized answer:', {
            waiting: sanitized?.waiting,
            answered: sanitized?.answered,
            answer_text: sanitized?.answer_text,
            selected: sanitized?.selected
          });
          
          // Update the tool run with the answer
          toolRuns[toolRunIndex].result = sanitized;
          toolRuns[toolRunIndex].success = !sanitized?.error;
          
          // Update content with the answered state
          const contentBefore = currentGptContent;
          currentGptContent = updateToolBlockWithResponse(currentGptContent, toolName, sanitized, null);
          
          // Debug: check if content was actually updated
          if (contentBefore === currentGptContent) {
            console.log('[MainAssistant] WARNING: Ask tool content was NOT updated!');
          } else {
            console.log('[MainAssistant] Ask tool content updated successfully');
            // Log a snippet of the updated content to verify
            const toolMatch = currentGptContent.match(/<tool_use>[\s\S]*?ask[\s\S]*?<\/tool_use>/i);
            if (toolMatch) {
              console.log('[MainAssistant] Updated ask tool block:', toolMatch[0].substring(0, 500));
            }
          }
          
          // Complete tool execution in session state (for ask tool)
          if (sessionState) {
            sessionState.completeToolExecution(toolRunIndex, sanitized);
          }
        } else {
          // Non-interactive tool - update the pending block with result
          if (isApiToolCalls) {
            // For API tools, update the pending block we added earlier
            currentGptContent = updateToolBlockWithResponse(currentGptContent, toolName, sanitized, null);
          } else {
            // For text-parsed tools, update existing block
            currentGptContent = updateToolBlockWithResponse(currentGptContent, toolName, sanitized, tool.id);
          }
          
          // Update the existing toolRuns entry with result
          toolRuns[toolRunIndex].result = sanitized;
          toolRuns[toolRunIndex].success = !sanitized?.error;
          
          // Complete tool execution in session state
          if (sessionState) {
            sessionState.completeToolExecution(toolRunIndex, sanitized);
          }
        }
        
        // Save to display history and wait for completion
        if (entryIndex !== null) {
          await updateGptResponse(entryIndex, currentGptContent, false, sessionId);
          await new Promise(resolve => setTimeout(resolve, 50));
        }
        
        // Re-render
        if (shouldRenderForSession(sessionId)) {
          const { element: currentEl, chatMessages: currentChat } = getCurrentGptContent(entryIndex, gptContent);
          renderGptResponse(currentEl, currentGptContent, toolRuns);
          scrollToBottom(currentChat);
          previousRenderedHtml = currentEl.innerHTML;
          
          // Update multi-view panel if this session is in multi-view
          try {
            if (isInMultiView(sessionId)) {
              updateMultiViewPanelContent(sessionId, currentGptContent, toolRuns);
            }
          } catch (_) {}
          
          // Update session state for restoration
          if (sessionState) {
            sessionState.captureDomState(currentChat);
            sessionState.setAccumulatedContent(currentGptContent);
            sessionState.toolRuns = [...toolRuns];
          }
        }
        
        // Save tool result to raw history and collect for GPT
        if (toolName !== 'attempt_completion') {
          shouldContinue = true;
let resultJson = '';
try {
  // Handle undefined/null results safely
  if (result === undefined || result === null) {
    resultJson = JSON.stringify({ error: 'Tool result was undefined or null' });
  } else {
    // Use circular reference-safe JSON.stringify
    const seen = new WeakSet();
    resultJson = JSON.stringify(result, (key, value) => {
      if (typeof value === 'object' && value !== null) {
        if (seen.has(value)) {
          return '[Circular Reference]';
        }
        seen.add(value);
      }
      return value;
    });
  }
  console.log(`[MainAssistant] Tool ${toolName} result size: ${resultJson.length} chars`);
} catch (jsonError) {
  console.warn(`[MainAssistant] JSON stringify failed for ${toolName}: ${jsonError.message}`);
  resultJson = JSON.stringify({ error: 'Failed to serialize tool result', originalResult: String(result) });
}
          
          // Save to raw history for future context
          await appendToolResult(sessionId, tool.id, toolName, result);
          console.log(`[MainAssistant] Saved tool result to raw history`);
          
          // Apply truncation logic to tool result content
          const truncatedContent = truncateToolContent(resultJson || '', 15 * 1024); // 15KB limit
          
          toolResults.push({
            role: 'tool',
            tool_call_id: tool.id,
            name: toolName,
            content: truncatedContent
          });
        }
      }
      
      // Continue? Raw history already has all messages, just loop back
      // But first check if stopped
      if (isStopRequested(sessionId)) {
        console.log('[MainAssistant] Stop requested, not continuing');
        break;
      }
      
      if (shouldContinue && toolResults.length > 0) {
        console.log(`[MainAssistant] Continuing with ${toolResults.length} tool result(s), raw history will be loaded`);
        // No need to set toolResultHistory - raw history has everything
        currentPrompt = null;
      } else {
        break;
      }
    }
    
    // Finalize
    if (entryIndex !== null) {
      await finalizeChatEntry(entryIndex, currentGptContent, sessionId);
      
      // Update window.allChatEntries with the final content
      if (Array.isArray(window.allChatEntries) && entryIndex < window.allChatEntries.length) {
        window.allChatEntries[entryIndex].gpt = currentGptContent;
        window.allChatEntries[entryIndex].status = 'complete';
      }
    }
    
    console.log('[MainAssistant] Complete');
    
  } catch (err) {
    // Don't show error if it was just an abort
    if (err.name === 'AbortError' || isStopRequested(sessionId)) {
      console.log('[MainAssistant] Stopped by user');
    } else {
      console.error('[MainAssistant] Error:', err);
      gptContent.innerHTML = `<div class="error">Error: ${escapeHTML(err.message || String(err))}</div>`;
      // Update session state with error
      if (sessionState) {
        sessionState.setError(err);
      }
    }
  } finally {
    window.isGptResponding = false;
    // End connection monitoring
    markRequestEnd();
    // Clean up session state
    try {
      if (window.__auaciRespondingSessions) window.__auaciRespondingSessions[sessionId] = false;
      if (window.__auaciAbortControllers) delete window.__auaciAbortControllers[sessionId];
    } catch (_) {}
    // Mark concurrent session as completed and schedule cleanup
    if (sessionState) {
      sessionState.complete();
      scheduleCleanup(sessionId);
    }
    // Clear stop state
    clearStopState(sessionId);
    // Disable stop button
    try {
      const stopBtn = document.getElementById('stop-response-btn');
      if (stopBtn) { stopBtn.disabled = true; stopBtn.onclick = null; }
    } catch (_) {}
    if (shouldRenderForSession(sessionId)) hideGptIndicator(sessionId);
    try { require('../tabManager').refreshTabs(); } catch (_) {}
  }
}

function renderUserMessage(container, text, files, entryIndex) {
  const div = document.createElement('div');
  div.className = 'message user-message';
  div.setAttribute('data-entry-index', String(entryIndex));
  
  const content = document.createElement('div');
  content.className = 'message-content';
  
  if (text) {
    const textDiv = document.createElement('div');
    textDiv.textContent = text;
    content.appendChild(textDiv);
  }
  
  files.forEach(file => {
    try {
      content.innerHTML += require('../fileUtils').createHistoryFileBox(file);
    } catch (_) {}
  });
  
  div.appendChild(content);
  container.appendChild(div);
}

const { smartScrollToBottom, setupScrollTracking } = require('../helpers/smartScroll');

function scrollToBottom(el) {
  if (!el) return;
  // Use smart scrolling instead of forcing to bottom
  smartScrollToBottom(el, { smooth: false });
}

function showEmptyWarning() {
  try {
    const indicator = document.getElementById('gpt-response-indicator');
    if (indicator) {
      indicator.innerHTML = `<span class="gpt-indicator-text">Hmm your message is empty, might want to type something first...</span>`;
      indicator.classList.add('visible');
      setTimeout(() => {
        indicator.classList.remove('visible');
        indicator.innerHTML = `
          <span class="gpt-indicator-text">GPT is responding</span>
          <span class="gpt-indicator-dots"><span class="dot"></span><span class="dot"></span><span class="dot"></span></span>
        `;
      }, 2000);
    }
  } catch (_) {}
}

// ============================================================================
// LEGACY COMPATIBILITY EXPORTS
// ============================================================================

// Alias for backward compatibility
const sendMessage = MainAssistant;

/**
 * embedToolResultsForRendering - for historyDisplay.js compatibility
 * Takes GPT response text and tool runs, returns text suitable for rendering
 */
function embedToolResultsForRendering(gptText, toolRuns = [], options = {}) {
  // The new format already has tool results embedded in <tool_use> blocks
  // Just return the text as-is since it's already in the correct format
  return gptText || '';
}

/**
 * embedToolResultsInGptResponse - for historyDisplay.js compatibility
 * Takes GPT response text and tool runs, returns text for storage
 */
function embedToolResultsInGptResponse(gptText, toolRuns = []) {
  // The new format already has tool results embedded in <tool_use> blocks
  // Just return the text as-is since it's already in the correct format
  return gptText || '';
}

module.exports = { 
  MainAssistant,
  sendMessage,
  setupCodeBlockListeners,
  embedToolResultsForRendering,
  embedToolResultsInGptResponse,
  // Also export rendering helpers for potential use elsewhere
  parseResponseSegments,
  renderGptResponse,
  buildToolBlock
};
