// src/main/functions/chat/send/modules/gptCycle.js
// Main orchestrator for GPT request/response cycle
// 
// FLOW:
// 1. Send user message to GPT
// 2. Wait for FULL response
// 3. Save response to history (async, debounced)
// 4. Render response (text + tools, debounced)
// 5. Execute tools one by one
// 6. After each tool: save system response (async), re-render (debounced)
// 7. Send tool results back to GPT
// 8. Repeat from step 2 until no more tools or attempt_completion

const { streamChat, stopRequest } = require('../../gpt-api');
const { getTools } = require('../../ai-logic/tool-instructions/index');
const { buildPrompt } = require('../../ai-logic/gpt-msg-sender');
const { 
  saveGptResponse, 
  initializeEntry,
  appendToolToGptResponse,
  appendTextToGptResponse,
  buildToolBlock
} = require('./historyManager');
const {
  renderGptResponse,
  getOrCreateGptContainer,
  showThinkingIndicator,
  hideThinkingIndicator,
  parseToolsFromResponse
} = require('./responseRenderer');
const {
  executeTool,
  sanitizeResultForHistory,
  isFinalizationTool,
  requiresContinuation,
  normalizeToolName
} = require('./toolExecutor');

/**
 * Debounced rendering system
 * OPTIMIZATION: Batches DOM updates to reduce repaints
 */
class RenderQueue {
  constructor(containerEl, delayMs = 100) {
    this.containerEl = containerEl;
    this.delayMs = delayMs;
    this.pendingRender = null;
    this.timeoutId = null;
    this.lastRenderContent = null;
    this.lastToolRuns = [];
  }
  
  queue(gptContent, toolRuns = []) {
    this.pendingRender = { gptContent, toolRuns };
    
    // Clear existing timeout
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }
    
    // Schedule render
    this.timeoutId = setTimeout(() => {
      this.flush();
    }, this.delayMs);
  }
  
  flush() {
    if (!this.pendingRender) return;
    
    const { gptContent, toolRuns } = this.pendingRender;
    
    // Only render if content changed
    if (this.lastRenderContent !== gptContent) {
      renderGptResponse(this.containerEl, gptContent, toolRuns);
      this.lastRenderContent = gptContent;
      this.lastToolRuns = toolRuns;
    }
    
    this.pendingRender = null;
    this.timeoutId = null;
  }
  
  cancel() {
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
      this.timeoutId = null;
    }
    this.pendingRender = null;
  }
}

/**
 * Debounced file save system
 * OPTIMIZATION: Batches and delays file writes to reduce I/O
 */
class SaveQueue {
  constructor(delayMs = 200) {
    this.delayMs = delayMs;
    this.pendingSaves = new Map();
    this.timeoutId = null;
  }
  
  queue(entryIndex, gptContent, sessionId, isFinal) {
    this.pendingSaves.set(entryIndex, { gptContent, sessionId, isFinal });
    
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }
    
    // For final saves, do it immediately
    if (isFinal) {
      this.flush();
    } else {
      this.timeoutId = setTimeout(() => this.flush(), this.delayMs);
    }
  }
  
  async flush() {
    if (this.pendingSaves.size === 0) return;
    
    const saves = Array.from(this.pendingSaves.entries());
    this.pendingSaves.clear();
    
    // Execute all pending saves in parallel
    const promises = saves.map(([entryIndex, { gptContent, sessionId, isFinal }]) =>
      saveGptResponse(entryIndex, gptContent, sessionId, isFinal).catch(err => {
        console.error('[gptCycle] Save error:', err);
      })
    );
    
    await Promise.all(promises);
  }
  
  cancel() {
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
      this.timeoutId = null;
    }
    this.pendingSaves.clear();
  }
}

/**
 * Main function to handle a complete GPT conversation cycle
 */
async function runGptCycle(options) {
  // Use background processing if available
  const processor = getBackgroundProcessor();
  if (processor.getBackgroundMode()) {
    console.log('[gptCycle] Running in background mode');
    return executeGptCycleWithBackgroundSupport(options);
  }
  
  // Continue with normal execution
  const {
    userInput,
    files = [],
    sessionId,
    systemPrompt,
    chatMessagesEl,
    entryIndex,
    onComplete,
    onError
  } = options;
  
  console.log('[gptCycle] Starting GPT cycle');
  
  // State
  let currentGptContent = '';
  let loopCount = 0;
  const MAX_LOOPS = 50;
  let thinkingRef = null;
  
  // Get container for rendering
  const containerEl = getOrCreateGptContainer(chatMessagesEl, entryIndex);
  
  // Create optimization queues
  const renderQueue = new RenderQueue(containerEl, 100);
  const saveQueue = new SaveQueue(200);
  
  try {
    // Build initial prompt
    const prompt = await buildPrompt(userInput, files, { recentCount: 2 });
    
    // Start the cycle
    let currentPrompt = prompt;
    let toolResultHistory = null;
    
    while (loopCount < MAX_LOOPS) {
      loopCount++;
      console.log(`[gptCycle] Loop ${loopCount}`);
      
      // Show thinking indicator
      thinkingRef = showThinkingIndicator(containerEl);
      
      // Step 1: Get FULL GPT response
      const response = await getFullGptResponse({
        prompt: currentPrompt,
        systemPrompt,
        sessionId,
        toolResultHistory
      });
      
      // Hide thinking
      hideThinkingIndicator(thinkingRef);
      thinkingRef = null;
      
      if (!response || response.error) {
        throw new Error(response?.error || 'No response from GPT');
      }
      
      const gptText = response.text || '';
      const apiToolCalls = response.tool_calls || [];
      
      console.log(`[gptCycle] Got response: ${gptText.length} chars, ${apiToolCalls.length} API tool calls`);
      
      // Step 2: Build complete response content
      let responseContent = gptText;
      
      const toolsToExecute = [];
      if (apiToolCalls.length > 0) {
        for (const tc of apiToolCalls) {
          const fn = tc.function || {};
          let args = {};
          try {
            args = typeof fn.arguments === 'string' ? JSON.parse(fn.arguments) : (fn.arguments || {});
          } catch (_) {
            args = {};
          }
          
          const tool = {
            id: tc.id,
            name: fn.name || '',
            input: args
          };
          toolsToExecute.push(tool);
          responseContent = appendToolToGptResponse(responseContent, tool.name, tool.input, null);
        }
      } else {
        // Parse tools from text
        const { tools } = parseToolsFromResponse(gptText);
        for (const t of tools) {
          toolsToExecute.push({
            id: `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
            name: t.name,
            input: t.input
          });
        }
      }
      
      // Step 3: Accumulate to current content
      currentGptContent = appendTextToGptResponse(currentGptContent, responseContent);
      
      // Step 4: Queue save (debounced, non-blocking)
      saveQueue.queue(entryIndex, currentGptContent, sessionId, false);
      
      // Step 5: Queue render (debounced)
      renderQueue.queue(currentGptContent, []);
      scrollToBottom(chatMessagesEl);
      
      // Step 6: Check if we have tools to execute
      if (toolsToExecute.length === 0) {
        console.log('[gptCycle] No tools to execute, cycle complete');
        break;
      }
      
      // Step 7: Execute tools one by one (with batched updates)
      let shouldContinue = false;
      let lastToolResult = null;
      let lastToolName = null;
      let lastToolId = null;
      
      for (const tool of toolsToExecute) {
        const toolName = normalizeToolName(tool.name);
        console.log(`[gptCycle] Executing tool: ${toolName}`);
        
        // Check for finalization
        if (isFinalizationTool(toolName)) {
          console.log('[gptCycle] Found attempt_completion, finalizing');
          break;
        }
        
        // Execute the tool
        const result = await executeTool(toolName, tool.input, { sessionId, entryIndex });
        
        // Sanitize result for storage
        const sanitizedResult = sanitizeResultForHistory(toolName, tool.input, result);
        
        // Step 8: Update the tool block in content with system response
        currentGptContent = updateToolWithSystemResponse(
          currentGptContent, 
          toolName, 
          tool.input, 
          sanitizedResult
        );
        
        // Step 9: Queue save (non-blocking, debounced)
        saveQueue.queue(entryIndex, currentGptContent, sessionId, false);
        
        // Step 10: Queue render with updated tool results
        const toolRun = {
          name: toolName,
          input: tool.input,
          result: sanitizedResult,
          success: !sanitizedResult?.error
        };
        renderQueue.queue(currentGptContent, [toolRun]);
        scrollToBottom(chatMessagesEl);
        
        // Track for continuation
        if (requiresContinuation(toolName)) {
          shouldContinue = true;
          lastToolResult = result;
          lastToolName = toolName;
          lastToolId = tool.id;
        }
      }
      
      // Step 11: If we need to continue, send tool result back to GPT
      if (shouldContinue && lastToolResult) {
        console.log(`[gptCycle] Sending ${lastToolName} result back to GPT`);
        
        // Build tool result for history with truncation logic
        const fullToolContent = JSON.stringify(lastToolResult);
        const truncatedContent = truncateToolContent(fullToolContent, 15 * 1024); // 15KB limit
        
        toolResultHistory = {
          role: 'tool',
          tool_call_id: lastToolId,
          name: lastToolName,
          content: truncatedContent
        };
        
        // Clear prompt for next iteration
        currentPrompt = null;
      } else {
        // No continuation needed
        break;
      }
    }
    
    // Flush all pending operations
    renderQueue.flush();
    await saveQueue.flush();
    
    // Final save
    await saveGptResponse(entryIndex, currentGptContent, sessionId, true);
    console.log('[gptCycle] Cycle complete');
    
    if (onComplete) {
      onComplete(currentGptContent);
    }
    
  } catch (err) {
    console.error('[gptCycle] Error:', err);
    hideThinkingIndicator(thinkingRef);
    
    // Cleanup queues
    renderQueue.cancel();
    saveQueue.cancel();
    
    if (onError) {
      onError(err);
    }
  }
}

/**
 * Get full GPT response (wait for complete response)
 */
async function getFullGptResponse(options) {
  const { prompt, systemPrompt, sessionId, toolResultHistory } = options;
  
  const payload = { app_id: 'editor-v1' };
  
  if (prompt) {
    payload.user_prompt = prompt;
  }
  if (systemPrompt) {
    payload.system_prompt = systemPrompt;
  }
  if (sessionId) {
    payload.session_id = sessionId;
  }
  
  payload.include_history = true;
  
  // Add tools
  const toolDefs = getTools();
  if (Array.isArray(toolDefs) && toolDefs.length > 0) {
    payload.tools = toolDefs;
    payload.tool_choice = 'auto';
  }
  
  // Add tool result history if present
  if (toolResultHistory) {
    payload.history = [toolResultHistory];
  }
  
  // Get model
  let selectedModel = null;
  try {
    selectedModel = require('../../modelSelector').getSelectedModel();
  } catch (_) {}
  
  // Get hoster
  let selectedHoster = null;
  try {
    selectedHoster = require('../../modelSelector').getSelectedHoster();
  } catch (_) {}
  
  // Stream and collect full response
  let textOut = '';
  let toolCalls = null;
  
  try {
    const streamOpts = { model: selectedModel || undefined };
    if (selectedHoster) streamOpts.hoster = selectedHoster;
    
    for await (const chunk of streamChat(payload, streamOpts)) {
      if (chunk?.event === 'content' && typeof chunk.content === 'string') {
        textOut += chunk.content;
      } else if (chunk && typeof chunk.content === 'string' && !chunk.event) {
        textOut += chunk.content;
      } else if (chunk?.event === 'tool_calls' && Array.isArray(chunk.tool_calls)) {
        toolCalls = chunk.tool_calls;
      } else if (chunk?.error) {
        return { error: String(chunk.error) };
      }
    }
  } catch (err) {
    return { error: String(err.message || err) };
  }
  
  return {
    text: textOut,
    tool_calls: toolCalls || []
  };
}

/**
 * Update a tool block in the content with its system response
 */
function updateToolWithSystemResponse(content, toolName, toolInput, systemResponse) {
  // Find the tool block without system response and update it
  const toolRe = /<tool_use>\s*([\s\S]*?)\s*<\/tool_use>/gi;
  let result = content;
  let match;
  
  // Reset regex
  toolRe.lastIndex = 0;
  
  while ((match = toolRe.exec(content)) !== null) {
    const jsonRaw = match[1];
    let parsed = null;
    
    try {
      parsed = JSON.parse(jsonRaw);
    } catch (_) {
      const start = jsonRaw.indexOf('{');
      const end = jsonRaw.lastIndexOf('}');
      if (start !== -1 && end > start) {
        try {
          parsed = JSON.parse(jsonRaw.slice(start, end + 1));
        } catch (_) {}
      }
    }
    
    if (parsed && parsed.name === toolName) {
      // Check if this block already has a system response
      const hasResponse = parsed.tool_system_response || parsed.tool_system_results;
      
      if (!hasResponse) {
        // This is the block to update
        const lname = String(toolName).toLowerCase();
        const useResultsKey = ['view', 'grep', 'context_search', 'find_files', 'ls',
                              'add_todos', 'create_todo_list', 'read_todos',
                              'mark_todo_as_done', 'remove_todos', 'ask'].includes(lname);
        
        if (useResultsKey) {
          parsed.tool_system_results = systemResponse;
        } else {
          parsed.tool_system_response = systemResponse;
        }
        
        const newBlock = `<tool_use>\n${JSON.stringify(parsed, null, 2)}\n</tool_use>`;
        result = content.slice(0, match.index) + newBlock + content.slice(match.index + match[0].length);
        break; // Only update first matching block without response
      }
    }
  }
  
  return result;
}

const { smartScrollToBottom, setupScrollTracking } = require('../../helpers/smartScroll');
const { getBackgroundProcessor, executeGptCycleWithBackgroundSupport } = require('../../backgroundProcessing');

/**
 * Scroll chat container to bottom
 */
function scrollToBottom(containerEl) {
  if (!containerEl) return;
  // Use smart scrolling instead of forcing to bottom
  smartScrollToBottom(containerEl, { smooth: false });
}

/**
 * Truncate tool content to specified size limit (in bytes)
 * Keeps the LAST X bytes (bottom-to-top) so most recent results are shown
 * Adds truncation message AT THE TOP
 */
function truncateToolContent(content, maxBytes) {
  if (!content || typeof content !== 'string') return content;
  
  const contentBytes = Buffer.byteLength(content, 'utf8');
  
  // If content is already under limit, return as-is
  if (contentBytes <= maxBytes) {
    return content;
  }
  
  // Keep the LAST maxBytes (bottom-to-top approach)
  const bytesToKeep = maxBytes;
  const bytesTruncated = contentBytes - bytesToKeep;
  
  // Extract last X bytes that are valid UTF-8
  const buffer = Buffer.from(content, 'utf8');
  const startIndex = contentBytes - bytesToKeep;
  const truncatedBuffer = buffer.slice(startIndex);
  
  // Convert back to string, ensuring valid UTF-8
  let truncatedContent = truncatedBuffer.toString('utf8');
  
  // Handle case where we might have cut in the middle of a multi-byte character
  // Try progressively earlier byte positions to find a valid UTF-8 boundary
  if (truncatedContent.length === 0 && content.length > 0) {
    for (let offset = 1; offset < Math.min(4, bytesToKeep); offset++) {
      const testBuffer = buffer.slice(startIndex + offset);
      const testContent = testBuffer.toString('utf8');
      if (testContent.length > 0) {
        truncatedContent = testContent;
        break;
      }
    }
  }
  
  // Add truncation message AT THE TOP with important info upfront
  const kbKept = Math.round(bytesToKeep / 1024);
  const kbTruncated = Math.round(bytesTruncated / 1024);
  
  const truncationMessage = `[Output Truncated - Large Command Output] Showing last ${kbKept}KB of results (${kbTruncated}KB skipped from beginning) to stay within 15KB token limit. This preserves the most recent/important output at the top.\n\n`;
  
  return truncationMessage + truncatedContent;
}

module.exports = {
  runGptCycle,
  getFullGptResponse,
  truncateToolContent
};
