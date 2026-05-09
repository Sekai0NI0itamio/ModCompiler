/**
 * Tool Calling Loop - Tool Execution Framework
 * Handles execution of tools, collecting results, and feeding back to the model
 * Inspired by vscode-copilot-chat's ToolCallingLoop pattern
 */

/**
 * ToolResult
 * Represents the result of a tool execution
 */
class ToolResult {
  constructor(toolName, input, output, error = null) {
    this.toolName = toolName;
    this.input = input;
    this.output = output;
    this.error = error;
    this.timestamp = Date.now();
    this.success = !error;
  }
}

/**
 * ToolCall
 * Represents a tool call request from the model
 */
class ToolCall {
  constructor(id, toolName, input) {
    this.id = id;
    this.toolName = toolName;
    this.input = input;
    this.timestamp = Date.now();
  }
}

/**
 * ToolCallingLoop
 * Manages iterative tool execution for agent tasks
 * Collects tool results and builds feedback for the model
 */
class ToolCallingLoop {
  constructor(options = {}) {
    this.options = options;
    this.toolCalls = [];
    this.toolResults = [];
    this.conversationHistory = [];
    this.maxIterations = options.maxIterations || 10;
    this.currentIteration = 0;
  }

  /**
   * Execute a tool and collect the result
   * 
   * @param {ToolCall} toolCall - The tool call to execute
   * @param {Function} executor - Function that executes the tool
   * @returns {Promise<ToolResult>} The result of the tool execution
   */
  async executeToolCall(toolCall, executor) {
    try {
      if (!executor) {
        throw new Error(`No executor provided for tool: ${toolCall.toolName}`);
      }

      // Execute the tool
      const output = await executor(toolCall.toolName, toolCall.input);
      
      // Create result
      const result = new ToolResult(
        toolCall.toolName,
        toolCall.input,
        output,
        null
      );

      // Record the result
      this.toolResults.push(result);
      this.toolCalls.push(toolCall);

      return result;
    } catch (err) {
      const result = new ToolResult(
        toolCall.toolName,
        toolCall.input,
        null,
        err
      );

      this.toolResults.push(result);
      this.toolCalls.push(toolCall);

      return result;
    }
  }

  /**
   * Process tool call round
   * Executes multiple tool calls and returns results
   * 
   * @param {Array} toolCalls - Array of ToolCall objects
   * @param {Function} executor - Tool executor function
   * @returns {Promise<Array>} Array of ToolResult objects
   */
  async processToolCallRound(toolCalls, executor) {
    const results = [];
    
    for (const toolCall of toolCalls) {
      const result = await this.executeToolCall(toolCall, executor);
      results.push(result);
    }

    this.currentIteration++;
    return results;
  }

  /**
   * Build context for model feedback
   * Formats tool results for inclusion in model context
   * 
   * @returns {Object} Tool execution context
   */
  buildFeedbackContext() {
    return {
      totalCalls: this.toolCalls.length,
      totalResults: this.toolResults.length,
      successCount: this.toolResults.filter(r => r.success).length,
      errorCount: this.toolResults.filter(r => !r.success).length,
      currentIteration: this.currentIteration,
      maxIterations: this.maxIterations,
      toolResults: this.toolResults,
      toolCalls: this.toolCalls,
    };
  }

  /**
   * Format tool results for model context
   * Creates a readable format for including in the next prompt
   * 
   * @returns {String} Formatted tool results
   */
  formatToolResults() {
    let formatted = '';

    for (const result of this.toolResults) {
      formatted += `\n<tool_result tool="${result.toolName}">\n`;
      
      if (result.success) {
        if (typeof result.output === 'string') {
          formatted += result.output;
        } else {
          formatted += JSON.stringify(result.output, null, 2);
        }
      } else {
        formatted += `Error: ${result.error?.message || String(result.error)}`;
        
        // Add error recovery suggestion for common errors
        if (this.shouldSuggestErrorRecovery(result.error)) {
          formatted += `\n\nUse tool Error Recoverer to apply the missing parameter to the tool you just used, or modify any content of the tool you just used. You may respond with a diff to modify the content of your previous tool you've called as well through Error Recoverer.`;
        }
      }
      
      formatted += `\n</tool_result>\n`;
    }

    return formatted;
  }

  /**
   * Determine if error recovery should be suggested
   * 
   * @param {Error} error - The error that occurred
   * @returns {Boolean} True if error recovery should be suggested
   */
  shouldSuggestErrorRecovery(error) {
    const errorStr = String(error).toLowerCase();
    
    // Common errors that can be fixed with error_recoverer
    const recoverableErrors = [
      'missing required',
      'required property',
      'validation failed',
      'invalid',
      'not found',
      'file not found',
      'path not found',
      'no such file',
      'permission denied'
    ];

    return recoverableErrors.some(pattern => errorStr.includes(pattern));
  }

  /**
   * Should continue looping
   * Determines if we should continue executing tool calls
   * 
   * @returns {Boolean} True if should continue
   */
  shouldContinue() {
    return this.currentIteration < this.maxIterations;
  }

  /**
   * Reset state
   * Clears history for a new conversation
   */
  reset() {
    this.toolCalls = [];
    this.toolResults = [];
    this.conversationHistory = [];
    this.currentIteration = 0;
  }

  /**
   * Get execution summary
   * Returns a summary of tool execution
   * 
   * @returns {String} Summary text
   */
  getExecutionSummary() {
    const context = this.buildFeedbackContext();
    return `Tool execution summary: ${context.successCount} successful, ${context.errorCount} failed out of ${context.totalCalls} total calls.`;
  }
}

module.exports = {
  ToolCall,
  ToolResult,
  ToolCallingLoop,
};