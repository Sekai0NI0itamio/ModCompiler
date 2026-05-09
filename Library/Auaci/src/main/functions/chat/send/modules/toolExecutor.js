// src/main/functions/chat/send/modules/toolExecutor.js
// Clean module for executing tools

const { executeCommand } = require('../../commands/executor');
const { sanitizeToolResultForRender } = require('../../helpers/toolRenderSanitizer');
const { wrapToolResult } = require('../../commands/lib/toolResult');
const { getBackgroundProcessor, executeToolWithBackgroundSupport } = require('../../backgroundProcessing');

/**
 * Execute a single tool and return the result
 * Handles both streaming (run_command/bash) and non-streaming tools
 */
async function executeTool(toolName, toolInput, options = {}) {
  const { sessionId, onStreamOutput, entryIndex, skipBackground } = options;
  
  const normalizedName = normalizeToolName(toolName);
  console.log(`[toolExecutor] Executing tool: ${normalizedName}`);
  const startTime = Date.now();
  const meta = () => ({ duration_ms: Date.now() - startTime, started_at: startTime });
  
  // Use background processing if available
  const processor = getBackgroundProcessor();
  if (!skipBackground && processor.getBackgroundMode()) {
    console.log('[toolExecutor] Running in background mode');
    return executeToolWithBackgroundSupport(normalizedName, toolInput, options);
  }
  
  // Continue with normal execution
  
  try {
    if (normalizedName === 'run_command' || normalizedName === 'bash') {
      // Streaming command execution
      const raw = await executeStreamingCommand(normalizedName, toolInput, onStreamOutput);
      return wrapToolResult(normalizedName, raw, null, meta());
    } else if (normalizedName === 'ask') {
      // Interactive ask - needs UI interaction
      const raw = await executeAskTool(toolInput, options);
      return wrapToolResult(normalizedName, raw, null, meta());
    } else if (normalizedName === 'delete_file_folder_with_permission') {
      // Interactive delete - needs UI confirmation
      const raw = await executeDeleteTool(toolInput, options);
      return wrapToolResult(normalizedName, raw, null, meta());
    } else {
      // Standard non-streaming tool
      const result = await executeCommand({
        name: normalizedName,
        input: toolInput,
        raw: '',
        context: { sessionId, entryIndex }
      });
      console.log(`[toolExecutor] Tool ${normalizedName} completed`);
      return wrapToolResult(normalizedName, result, null, meta());
    }
  } catch (err) {
    console.error(`[toolExecutor] Tool ${normalizedName} failed:`, err);
    return wrapToolResult(normalizedName, null, err, meta());
  }
}

/**
 * Track file operation before execution to enable restore functionality

/**
 * Normalize tool name to standard format
 */
/**
 * Normalize tool name to standard format
 */
function normalizeToolName(name) {
  if (!name || typeof name !== 'string') return '';
  const s = name.trim();
  if (/^attempt[-_]?completion$/i.test(s)) return 'attempt_completion';
  if (/^context[-_]?search$/i.test(s)) return 'context_search';
  if (/^apply[-_]?patch$/i.test(s)) return 'apply_patch';
  if (/^run[-_]?command$/i.test(s)) return 'run_command';
  if (/^error[-_]?recoverer?$/i.test(s)) return 'error_recoverer';
  return s;
}

/**
 * Execute streaming command (run_command/bash) via IDE terminal panel
 */
async function executeStreamingCommand(toolName, input, onStreamOutput) {
  try {
    // Use the run_command handler which executes in the terminal panel
    const runCommandHandler = require('../../commands/handlers/run_command');
    
    const cmd = input && typeof input.command === 'string' ? input.command : '';
    if (!cmd) {
      return { error: 'No command provided' };
    }
    
    console.log(`[toolExecutor] Executing command in terminal: ${cmd}`);
    
    // Execute via terminal panel
    const result = await runCommandHandler({ command: cmd });
    
    // Call stream callback with final output if provided
    if (onStreamOutput && result?.finished?.output) {
      try { onStreamOutput(result.finished.output); } catch (_) {}
    }
    
    return result;
    
  } catch (err) {
    console.error('[toolExecutor] Terminal command execution failed:', err);
    return { error: String(err.message || err) };
  }
}

/**
 * Execute ask tool (interactive)
 * For now, returns a placeholder - actual UI interaction handled elsewhere
 */
async function executeAskTool(input, options) {
  // This would need UI integration
  // For now, return waiting state
  return {
    question: input.question || '',
    mode: input.mode || 'free',
    options: input.options || [],
    waiting: true
  };
}

/**
 * Execute delete tool (interactive)
 * For now, returns a placeholder - actual UI interaction handled elsewhere
 */
async function executeDeleteTool(input, options) {
  // This would need UI integration
  // For now, execute directly via command executor
  return await executeCommand({
    name: 'delete_file_folder_with_permission',
    input: input,
    raw: ''
  });
}

/**
 * Sanitize tool result for history storage
 * Removes large content, keeps metadata
 * Uses the centralized sanitizer for consistency
 */
function sanitizeResultForHistory(toolName, input, result) {
  return sanitizeToolResultForRender(toolName, input, result);
}

/**
 * Check if tool is a finalization tool (attempt_completion)
 */
function isFinalizationTool(toolName) {
  return normalizeToolName(toolName) === 'attempt_completion';
}

/**
 * Check if tool requires continuation (needs system response sent back to GPT)
 */
function requiresContinuation(toolName) {
  const name = normalizeToolName(toolName);
  // attempt_completion doesn't need continuation
  return name !== 'attempt_completion';
}

module.exports = {
  executeTool,
  normalizeToolName,
  sanitizeResultForHistory,
  isFinalizationTool,
  requiresContinuation
};
