/**
 * Tool Invocation Service
 * Handles validation and execution of tools with proper error handling
 * Inspired by vscode-copilot-chat's ToolsService
 * Enhanced with retry logic, token estimation, and error recovery (Phase 1)
 */

const fs = require('fs');
const path = require('path');
const { ToolName } = require('./toolCapabilities');

/**
 * Tool validation result types
 */
class ValidatedToolInput {
  constructor(inputObj) {
    this.inputObj = inputObj;
  }
}

class ToolValidationError {
  constructor(error) {
    this.error = error;
  }
}

/**
 * Tool Execution Result with enhanced error handling
 */
class ToolExecutionResult {
  constructor(success, output, toolName, error = null, retriedCount = 0, estimatedTokens = 0, metadata = {}) {
    this.success = success;
    this.output = output;
    this.toolName = toolName;
    this.error = error;
    this.retriedCount = retriedCount;
    this.estimatedTokens = estimatedTokens;
    this.metadata = metadata;
    this.timestamp = Date.now();
  }
}

/**
 * Error Recovery Result
 */
class ErrorRecoveryResult {
  constructor(recovered, toolName, correctedInput, originalError, recoveryAction) {
    this.recovered = recovered;
    this.toolName = toolName;
    this.correctedInput = correctedInput;
    this.originalError = originalError;
    this.recoveryAction = recoveryAction;
    this.timestamp = Date.now();
  }
}

/**
 * Retry configuration
 */
class RetryConfig {
  constructor(maxRetries = 0, backoffMs = 0) {
    this.maxRetries = maxRetries;
    this.backoffMs = backoffMs;
  }

  static fromErrorHandling(errorHandling) {
    if (!errorHandling || !errorHandling.retryable) {
      return new RetryConfig(0, 0);
    }
    const backoff = errorHandling.retryBackoff || {};
    const initial = backoff.initial || 1000;
    return new RetryConfig(errorHandling.maxRetries || 1, initial);
  }
}

/**
 * Tool Invocation Service
 * Manages tool validation, execution, and error recovery
 */
class ToolInvocationService {
  constructor() {
    this.tools = [];
    this.validators = new Map();
    this.executors = new Map();
    this.toolMetadata = new Map();
    this.lastFailedTool = null;
    this.recoveryEnabled = true;
    this.loadTools();
  }

  /**
   * Load tools from JSON definitions
   */
  loadTools() {
    // Tools are in the same directory as this file
    const toolsDir = __dirname;
    
    try {
      const files = fs.readdirSync(toolsDir);
      
      for (const file of files) {
        if (!file.endsWith('.json')) continue;
        
        try {
          const filePath = path.join(toolsDir, file);
          const content = fs.readFileSync(filePath, 'utf8');
          const toolDef = JSON.parse(content);
          
          if (toolDef && toolDef.type === 'function' && toolDef.function) {
            // Store tool definition
            this.tools.push(toolDef);
            
            // Store schema for validation reference (no need to compile with AJV)
            if (toolDef.function.parameters) {
              this.validators.set(toolDef.function.name, toolDef.function.parameters);
            }
            
            // Store metadata (version, executionMode, errorHandling, tokenEstimate)
            this.toolMetadata.set(toolDef.function.name, {
              version: toolDef.function.version || '1.0.0',
              executionMode: toolDef.function.executionMode || 'optional',
              errorHandling: toolDef.errorHandling || { retryable: false },
              tokenEstimate: toolDef.tokenEstimate || { invocation: 50, parameterPerChar: 0.1, outputPerChar: 0.05 }
            });
          }
        } catch (err) {
          console.warn(`[ToolService] Failed to load tool ${file}:`, err.message);
        }
      }
      
      console.log(`[ToolService] Loaded ${this.tools.length} tools with metadata`);
    } catch (err) {
      console.error('[ToolService] Failed to read tools directory:', err.message);
    }
  }

  /**
   * Estimate token cost of a tool invocation
   * @param {string} toolName - Name of the tool
   * @param {object} params - Tool parameters
   * @returns {number} Estimated tokens
   */
  estimateToolTokens(toolName, params) {
    const metadata = this.toolMetadata.get(toolName);
    if (!metadata || !metadata.tokenEstimate) {
      return 50; // Default estimate
    }

    const estimate = metadata.tokenEstimate;
    const invocationCost = estimate.invocation || 50;
    const paramStr = JSON.stringify(params || {});
    const paramCost = paramStr.length * (estimate.parameterPerChar || 0.1);
    
    return Math.round(invocationCost + paramCost);
  }

  /**
   * Simple schema validation - checks required fields and basic types
   */
  validateAgainstSchema(input, schema) {
    if (!schema || !schema.properties) {
      // No schema, accept anything
      return { valid: true, errors: [] };
    }

    const errors = [];
    const required = schema.required || [];

    // Check required properties
    for (const prop of required) {
      if (!(prop in input)) {
        errors.push(`Missing required property: ${prop}`);
      }
    }

    // Basic type checking if specified
    if (schema.properties) {
      for (const [key, value] of Object.entries(input)) {
        const propSchema = schema.properties[key];
        if (propSchema && propSchema.type) {
          const actualType = typeof value;
          const expectedType = propSchema.type;

          if (expectedType === 'string' && actualType !== 'string') {
            errors.push(`Property '${key}' should be a string, got ${actualType}`);
          } else if (expectedType === 'number' && actualType !== 'number') {
            errors.push(`Property '${key}' should be a number, got ${actualType}`);
          } else if (expectedType === 'boolean' && actualType !== 'boolean') {
            errors.push(`Property '${key}' should be a boolean, got ${actualType}`);
          } else if (expectedType === 'object' && actualType !== 'object') {
            errors.push(`Property '${key}' should be an object, got ${actualType}`);
          }
        }
      }
    }

    return { valid: errors.length === 0, errors };
  }

  /**
   * Validate tool input against its schema
   */
  validateToolInput(toolName, input) {
    try {
      // Find the tool definition
      const tool = this.tools.find(t => t.function.name === toolName);
      if (!tool) {
        return new ToolValidationError(`Unknown tool: ${toolName}`);
      }

      // Parse input if it's a string
      let inputObj = input;
      if (typeof input === 'string') {
        try {
          inputObj = JSON.parse(input);
        } catch (err) {
          return new ToolValidationError(`Invalid JSON input: ${err.message}`);
        }
      }

      // Get the schema
      const schema = tool.function.parameters;
      if (!schema) {
        // No schema to validate against
        return new ValidatedToolInput(inputObj);
      }

      // Validate against schema
      const validation = this.validateAgainstSchema(inputObj ?? {}, schema);
      
      if (validation.valid) {
        return new ValidatedToolInput(inputObj);
      }

      // Format validation errors
      const errorMessages = validation.errors.join('; ');
      return new ToolValidationError(`Validation failed: ${errorMessages}`);
    } catch (err) {
      return new ToolValidationError(`Validation error: ${err.message}`);
    }
  }

  /**
   * Get tool definition by name
   */
  getTool(name) {
    return this.tools.find(t => t.function.name === name);
  }

  /**
   * Get tool metadata (version, executionMode, errorHandling, tokenEstimate)
   */
  getToolMetadata(name) {
    return this.toolMetadata.get(name) || { version: '1.0.0', executionMode: 'optional', errorHandling: { retryable: false } };
  }

  /**
   * Get all tools
   */
  getTools() {
    return this.tools;
  }

  /**
   * Register a custom executor for a tool
   */
  registerExecutor(toolName, executor) {
    this.executors.set(toolName, executor);
  }

  /**
   * Sleep for a given number of milliseconds
   */
  static sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Attempt error recovery for a failed tool execution
   */
  async attemptErrorRecovery(failedToolName, failedInput, error, recoveryData) {
    try {
      console.log(`[ToolService] Attempting error recovery for ${failedToolName}`);
      
      if (!this.recoveryEnabled) {
        return new ErrorRecoveryResult(false, failedToolName, null, error, 'recovery_disabled');
      }

      // Store the last failed tool for potential recovery
      this.lastFailedTool = {
        toolName: failedToolName,
        input: failedInput,
        error: error,
        timestamp: Date.now()
      };

      // Check if this is a validation error that can be fixed
      const errorStr = String(error).toLowerCase();
      
      // Common fixable errors
      if (errorStr.includes('missing required') || errorStr.includes('required property')) {
        return await this.fixMissingParameters(failedToolName, failedInput, error, recoveryData);
      } else if (errorStr.includes('validation failed') || errorStr.includes('invalid')) {
        return await this.fixValidationErrors(failedToolName, failedInput, error, recoveryData);
      } else if (errorStr.includes('not found') || errorStr.includes('file not found')) {
        return await this.fixPathErrors(failedToolName, failedInput, error, recoveryData);
      }

      return new ErrorRecoveryResult(false, failedToolName, null, error, 'unrecoverable_error');
    } catch (recoveryErr) {
      console.error(`[ToolService] Error recovery failed:`, recoveryErr);
      return new ErrorRecoveryResult(false, failedToolName, null, error, 'recovery_failed');
    }
  }

  /**
   * Fix missing parameters error
   */
  async fixMissingParameters(toolName, input, error, recoveryData) {
    const tool = this.getTool(toolName);
    if (!tool || !tool.function.parameters) {
      return new ErrorRecoveryResult(false, toolName, null, error, 'no_schema_available');
    }

    const schema = tool.function.parameters;
    const required = schema.required || [];
    const correctedInput = { ...input };
    
    // Apply corrections from recovery data
    if (recoveryData && recoveryData.corrections) {
      Object.assign(correctedInput, recoveryData.corrections);
    }

    // Validate the corrected input
    const validation = this.validateAgainstSchema(correctedInput, schema);
    if (validation.valid) {
      // Re-execute the tool with corrected input
      const executor = this.executors.get(toolName);
      if (executor) {
        try {
          const result = await executor(toolName, correctedInput);
          return new ErrorRecoveryResult(true, toolName, correctedInput, error, 'missing_params_fixed');
        } catch (execErr) {
          return new ErrorRecoveryResult(false, toolName, null, error, 're_execution_failed');
        }
      }
    }

    return new ErrorRecoveryResult(false, toolName, null, error, 'correction_invalid');
  }

  /**
   * Fix validation errors
   */
  async fixValidationErrors(toolName, input, error, recoveryData) {
    // Similar to missing parameters but for type/format errors
    return await this.fixMissingParameters(toolName, input, error, recoveryData);
  }

  /**
   * Fix path/file not found errors
   */
  async fixPathErrors(toolName, input, error, recoveryData) {
    if (!recoveryData || !recoveryData.corrections) {
      return new ErrorRecoveryResult(false, toolName, null, error, 'no_path_corrections');
    }

    const correctedInput = { ...input, ...recoveryData.corrections };
    
    // Re-execute the tool with corrected paths
    const executor = this.executors.get(toolName);
    if (executor) {
      try {
        const result = await executor(toolName, correctedInput);
        return new ErrorRecoveryResult(true, toolName, correctedInput, error, 'path_corrected');
      } catch (execErr) {
        return new ErrorRecoveryResult(false, toolName, null, error, 're_execution_failed');
      }
    }

    return new ErrorRecoveryResult(false, toolName, null, error, 'no_executor');
  }

  /**
   * Execute error_recoverer tool
   */
  async executeErrorRecoverer(input) {
    const { previous_tool_name, error_message, corrections, action } = input;
    
    console.log(`[ToolService] Executing error_recoverer for ${previous_tool_name}`);
    
    // Show "Correcting Error..." status
    const statusMessage = "Correcting Error...";
    
    try {
      // Attempt recovery based on the action
      let recoveryResult;
      
      switch (action) {
        case 'fix_missing_params':
        case 'modify_content':
        case 'retry_with_fix':
          recoveryResult = await this.attemptErrorRecovery(
            previous_tool_name, 
            input.previous_tool_input || {}, 
            new Error(error_message), 
            { corrections }
          );
          break;
        default:
          recoveryResult = new ErrorRecoveryResult(false, previous_tool_name, null, new Error(error_message), 'invalid_action');
      }

      if (recoveryResult.recovered) {
        return {
          status: "Corrected Tool",
          recoveryResult: recoveryResult,
          message: "Error recovery successful. The tool has been re-executed with corrected parameters."
        };
      } else {
        return {
          status: "Failed to correct error",
          recoveryResult: recoveryResult,
          message: `Failed to correct error: ${recoveryResult.recoveryAction}`
        };
      }
    } catch (err) {
      return {
        status: "Failed to correct error",
        error: err.message,
        message: "Error recovery failed due to system error"
      };
    }
  }

  /**
   * Execute a tool with retry logic and error recovery
   */
  async executeTool(toolName, input, options = {}) {
    try {
      // Handle error_recoverer tool specially
      if (toolName === 'error_recoverer') {
        return await this.executeErrorRecoverer(input);
      }

      // Validate input
      const validation = this.validateToolInput(toolName, input);
      if (validation instanceof ToolValidationError) {
        return new ToolExecutionResult(
          false, 
          null, 
          toolName, 
          validation.error, 
          0, 
          this.estimateToolTokens(toolName, input)
        );
      }

      const validatedInput = validation.inputObj;
      const metadata = this.getToolMetadata(toolName);
      const retryConfig = RetryConfig.fromErrorHandling(metadata.errorHandling);

      // Estimate token cost
      const estimatedTokens = this.estimateToolTokens(toolName, validatedInput);
      
      // Check if custom executor exists
      const executor = this.executors.get(toolName);
      if (!executor) {
        return new ToolExecutionResult(
          false, 
          null, 
          toolName, 
          `No executor registered for tool: ${toolName}`, 
          0, 
          estimatedTokens
        );
      }

      // Execute with retry logic
      let lastError = null;
      let retriedCount = 0;

      for (let attempt = 0; attempt <= retryConfig.maxRetries; attempt++) {
        try {
          const result = await executor(toolName, validatedInput);
          
          return new ToolExecutionResult(
            true, 
            result, 
            toolName, 
            null, 
            retriedCount, 
            estimatedTokens,
            {
              version: metadata.version,
              executionMode: metadata.executionMode
            }
          );
        } catch (err) {
          lastError = err;
          
          // Check if error is retryable
          const isRetryable = this.isRetryableError(err, metadata.errorHandling);
          
          if (isRetryable && attempt < retryConfig.maxRetries) {
            retriedCount++;
            // Exponential backoff
            const backoffMs = retryConfig.backoffMs * Math.pow(2, attempt);
            console.log(`[ToolService] Retrying ${toolName} after ${backoffMs}ms (attempt ${attempt + 1}/${retryConfig.maxRetries})`);
            await ToolInvocationService.sleep(backoffMs);
          } else {
            // Not retryable or no more retries
            break;
          }
        }
      }

      // All retries exhausted
      return new ToolExecutionResult(
        false, 
        null, 
        toolName, 
        lastError?.message || String(lastError), 
        retriedCount, 
        estimatedTokens
      );
    } catch (err) {
      return new ToolExecutionResult(
        false, 
        null, 
        toolName, 
        err.message || String(err), 
        0
      );
    }
  }

  /**
   * Determine if an error is retryable based on error handling configuration
   */
  isRetryableError(err, errorHandling) {
    if (!errorHandling || !errorHandling.retryable) {
      return false;
    }

    const errStr = String(err).toLowerCase();
    const retriableCodes = errorHandling.retriableCodes || [408, 500, 502, 503];
    const commonErrors = errorHandling.commonErrors || [];

    // Check for common error patterns
    for (const commonError of commonErrors) {
      if (commonError.pattern && errStr.includes(commonError.pattern.toLowerCase())) {
        // Found a matching pattern - check if it should be retried
        // For now, only retry timeout/connection errors
        if (commonError.pattern.toLowerCase().includes('timeout') || 
            commonError.pattern.toLowerCase().includes('connection')) {
          return true;
        }
      }
    }

    // Check for HTTP status codes
    const codeMatch = errStr.match(/\b(\d{3})\b/);
    if (codeMatch && retriableCodes.includes(parseInt(codeMatch[1]))) {
      return true;
    }

    return false;
  }

  /**
   * Get enabled tools based on capabilities
   * Filters tools based on available tool set
   */
  getEnabledTools(availableTools = []) {
    const availableNames = new Set(availableTools.map(t => t.name || t.function?.name));
    return this.tools.filter(tool => availableNames.has(tool.function.name));
  }

  /**
   * Enable/disable error recovery
   */
  setRecoveryEnabled(enabled) {
    this.recoveryEnabled = enabled;
  }

  /**
   * Get last failed tool info
   */
  getLastFailedTool() {
    return this.lastFailedTool;
  }
}

// Create singleton instance
const toolService = new ToolInvocationService();

module.exports = {
  ToolInvocationService,
  toolService,
  ValidatedToolInput,
  ToolValidationError,
  ToolExecutionResult,
  ErrorRecoveryResult,
  RetryConfig
};