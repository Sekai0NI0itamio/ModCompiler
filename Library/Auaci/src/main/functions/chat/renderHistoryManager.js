// src/main/functions/chat/renderHistoryManager.js
// Manages render history - optimized storage for UI display
// Strips large content from tool results while preserving metadata needed for rendering

const { sanitizeToolResultForRender, sanitizeToolInputForRender, createRenderToolEntry } = require('./helpers/toolRenderSanitizer');

/**
 * Create a render-optimized GPT entry from a full GPT response
 * Strips file contents and large outputs while keeping display metadata
 * @param {string} gptText - The raw GPT response text
 * @param {Array} toolRuns - Array of tool run records
 * @returns {object} - Render-optimized entry
 */
function createRenderEntry(gptText, toolRuns = []) {
  // Sanitize tool runs for render storage
  const sanitizedToolRuns = toolRuns.map(run => {
    if (!run) return null;
    return createRenderToolEntry(run.name, run.input, run.result);
  }).filter(Boolean);
  
  // Strip tool_system_response content from the GPT text
  // Keep the <tool_use> structure but remove large embedded results
  const sanitizedText = sanitizeGptTextForRender(gptText);
  
  return {
    gpt: sanitizedText,
    tool_runs: sanitizedToolRuns,
    _render_optimized: true,
    _created_at: new Date().toISOString()
  };
}

/**
 * Sanitize GPT text for render storage
 * Removes large embedded tool results while keeping tool structure
 * @param {string} gptText - Raw GPT response text
 * @returns {string} - Sanitized text
 */
function sanitizeGptTextForRender(gptText) {
  if (!gptText || typeof gptText !== 'string') return gptText || '';
  
  // Find and sanitize <tool_use> blocks
  const toolUseRe = /<tool_use>\s*([\s\S]*?)\s*<\/tool_use>/gi;
  
  return gptText.replace(toolUseRe, (match, jsonContent) => {
    try {
      // Try to parse the JSON content
      let parsed = null;
      try {
        parsed = JSON.parse(jsonContent);
      } catch (_) {
        // Try to extract JSON object
        const start = jsonContent.indexOf('{');
        const end = jsonContent.lastIndexOf('}');
        if (start !== -1 && end > start) {
          try {
            parsed = JSON.parse(jsonContent.slice(start, end + 1));
          } catch (_) {}
        }
      }
      
      if (!parsed || !parsed.name) {
        // Can't parse, return as-is
        return match;
      }
      
      // Sanitize the tool data
      const sanitizedInput = sanitizeToolInputForRender(parsed.name, parsed.input);
      const sanitizedResult = parsed.tool_system_response 
        ? sanitizeToolResultForRender(parsed.name, parsed.input, parsed.tool_system_response)
        : null;
      
      const sanitizedTool = {
        name: parsed.name,
        input: sanitizedInput
      };
      
      if (sanitizedResult) {
        sanitizedTool.tool_system_response = sanitizedResult;
      }
      
      return `<tool_use>\n${JSON.stringify(sanitizedTool, null, 2)}\n</tool_use>`;
    } catch (err) {
      // On error, return original
      console.warn('[renderHistoryManager] Error sanitizing tool_use block:', err);
      return match;
    }
  });
}

/**
 * Update a render entry with new tool result
 * @param {object} entry - Existing render entry
 * @param {number} toolIndex - Index of the tool to update
 * @param {object} result - New tool result
 * @returns {object} - Updated entry
 */
function updateRenderEntryToolResult(entry, toolIndex, result) {
  if (!entry || !entry.tool_runs) return entry;
  
  const updated = { ...entry };
  updated.tool_runs = [...entry.tool_runs];
  
  if (toolIndex >= 0 && toolIndex < updated.tool_runs.length) {
    const run = updated.tool_runs[toolIndex];
    if (run) {
      updated.tool_runs[toolIndex] = {
        ...run,
        result: sanitizeToolResultForRender(run.name, run.input, result)
      };
    }
  }
  
  updated._updated_at = new Date().toISOString();
  return updated;
}

/**
 * Check if an entry is render-optimized
 * @param {object} entry - Chat entry
 * @returns {boolean}
 */
function isRenderOptimized(entry) {
  return !!(entry && entry._render_optimized);
}

/**
 * Estimate the size of a render entry
 * @param {object} entry - Render entry
 * @returns {number} - Approximate size in bytes
 */
function estimateRenderEntrySize(entry) {
  try {
    return JSON.stringify(entry).length;
  } catch (_) {
    return 0;
  }
}

/**
 * Validate that a render entry has all required data for display
 * @param {object} entry - Render entry
 * @returns {object} - { valid: boolean, issues: string[] }
 */
function validateRenderEntry(entry) {
  const issues = [];
  
  if (!entry) {
    return { valid: false, issues: ['Entry is null or undefined'] };
  }
  
  if (typeof entry.gpt !== 'string') {
    issues.push('Missing or invalid gpt text');
  }
  
  if (entry.tool_runs && !Array.isArray(entry.tool_runs)) {
    issues.push('tool_runs is not an array');
  }
  
  if (entry.tool_runs) {
    for (let i = 0; i < entry.tool_runs.length; i++) {
      const run = entry.tool_runs[i];
      if (!run) {
        issues.push(`tool_runs[${i}] is null`);
        continue;
      }
      if (!run.name) {
        issues.push(`tool_runs[${i}] missing name`);
      }
    }
  }
  
  return {
    valid: issues.length === 0,
    issues
  };
}

/**
 * Repair a render entry if possible
 * @param {object} entry - Potentially broken render entry
 * @returns {object} - Repaired entry
 */
function repairRenderEntry(entry) {
  if (!entry) return { gpt: '', tool_runs: [], _repaired: true };
  
  const repaired = { ...entry };
  
  if (typeof repaired.gpt !== 'string') {
    repaired.gpt = '';
  }
  
  if (!Array.isArray(repaired.tool_runs)) {
    repaired.tool_runs = [];
  }
  
  // Filter out invalid tool runs
  repaired.tool_runs = repaired.tool_runs.filter(run => run && run.name);
  
  // Ensure each tool run has required fields
  repaired.tool_runs = repaired.tool_runs.map(run => ({
    name: run.name || 'unknown',
    input: run.input || {},
    result: run.result || null,
    _render_only: true
  }));
  
  repaired._repaired = true;
  return repaired;
}

module.exports = {
  createRenderEntry,
  sanitizeGptTextForRender,
  updateRenderEntryToolResult,
  isRenderOptimized,
  estimateRenderEntrySize,
  validateRenderEntry,
  repairRenderEntry
};
