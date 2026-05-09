// src/main/functions/chat/historyFormat.js
// New robust history format for chat entries
// Uses SEGMENTED format to clearly separate GPT text from tool calls
// This allows proper rendering where GPT messages are continuous

/**
 * NEW SEGMENTED HISTORY FORMAT:
 * 
 * gpt: {
 *   segments: [
 *     { type: 'text', content: 'GPT text before first tool' },
 *     { type: 'tool', id: 'call_123', name: 'view', input: {...}, system_response: {...} },
 *     { type: 'text', content: 'GPT text after first tool' },
 *     { type: 'tool', id: 'call_456', name: 'bash', input: {...}, system_response: {...} },
 *     { type: 'text', content: 'Final GPT text' }
 *   ],
 *   // For backward compatibility, also store serialized version
 *   _serialized: 'full text with <tool_use> blocks'
 * }
 * 
 * RENDERING:
 * - All segments are rendered in order
 * - Text segments are rendered as markdown
 * - Tool segments are rendered as tool boxes
 * - The entire sequence appears as one continuous GPT message
 */

/**
 * createGptEntry()
 * Creates a new GPT entry with segmented structure
 */
function createGptEntry() {
  return {
    segments: [],
    _serialized: ''
  };
}

/**
 * addTextSegment(gptEntry, text)
 * Adds a text segment to the GPT entry
 */
function addTextSegment(gptEntry, text) {
  if (!gptEntry || !gptEntry.segments) {
    console.log('[historyFormat] addTextSegment: invalid gptEntry');
    return;
  }
  if (!text || typeof text !== 'string' || !text.trim()) {
    console.log('[historyFormat] addTextSegment: empty text');
    return;
  }
  
  // Clean the text - remove any <tool_use> blocks that might be embedded
  const cleanText = removeToolUseBlocks(text).trim();
  if (!cleanText) {
    console.log('[historyFormat] addTextSegment: text was all tool_use blocks');
    return;
  }
  
  console.log(`[historyFormat] addTextSegment: adding "${cleanText.slice(0, 50)}..." (${cleanText.length} chars)`);
  
  // If the last segment is also text, append to it
  const lastSeg = gptEntry.segments[gptEntry.segments.length - 1];
  if (lastSeg && lastSeg.type === 'text') {
    lastSeg.content = (lastSeg.content || '') + '\n\n' + cleanText;
    console.log(`[historyFormat] addTextSegment: appended to existing text segment`);
  } else {
    gptEntry.segments.push({
      type: 'text',
      content: cleanText
    });
    console.log(`[historyFormat] addTextSegment: created new text segment (total: ${gptEntry.segments.length})`);
  }
}

/**
 * addToolSegment(gptEntry, id, name, input, systemResponse)
 * Adds a tool segment to the GPT entry
 */
function addToolSegment(gptEntry, id, name, input, systemResponse = null) {
  if (!gptEntry || !gptEntry.segments) {
    console.log('[historyFormat] addToolSegment: invalid gptEntry');
    return;
  }
  if (!name) {
    console.log('[historyFormat] addToolSegment: no name provided');
    return;
  }
  
  console.log(`[historyFormat] addToolSegment: adding tool "${name}"`);
  
  const toolSeg = {
    type: 'tool',
    id: id || `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    name: name,
    input: input || {}
  };
  
  if (systemResponse !== null && systemResponse !== undefined) {
    toolSeg.system_response = systemResponse;
  }
  
  gptEntry.segments.push(toolSeg);
  console.log(`[historyFormat] addToolSegment: added tool segment (total: ${gptEntry.segments.length})`);
}

/**
 * updateToolSystemResponse(gptEntry, toolIdOrName, systemResponse)
 * Updates the system response for a tool segment
 * Can match by ID or by name (finds the last matching tool without a response)
 */
function updateToolSystemResponse(gptEntry, toolIdOrName, systemResponse) {
  if (!gptEntry || !gptEntry.segments) return;
  
  // Find the tool segment by ID first
  let targetSeg = null;
  
  if (toolIdOrName) {
    // Try to find by ID
    targetSeg = gptEntry.segments.find(s => s.type === 'tool' && s.id === toolIdOrName);
    
    // If not found by ID, try to find by name (last one without system_response)
    if (!targetSeg) {
      const toolName = String(toolIdOrName).toLowerCase();
      for (let i = gptEntry.segments.length - 1; i >= 0; i--) {
        const seg = gptEntry.segments[i];
        if (seg.type === 'tool' && !seg.system_response) {
          // Match by name (case-insensitive)
          if (String(seg.name || '').toLowerCase() === toolName) {
            targetSeg = seg;
            break;
          }
        }
      }
    }
  }
  
  // If still not found, find the last tool segment without a system_response
  if (!targetSeg) {
    for (let i = gptEntry.segments.length - 1; i >= 0; i--) {
      const seg = gptEntry.segments[i];
      if (seg.type === 'tool' && !seg.system_response) {
        targetSeg = seg;
        break;
      }
    }
  }
  
  if (targetSeg) {
    targetSeg.system_response = systemResponse;
  }
}

/**
 * removeToolUseBlocks(text)
 * Removes <tool_use>...</tool_use> blocks from text
 * Also removes incomplete/orphan <tool_use> tags
 */
function removeToolUseBlocks(text) {
  if (!text || typeof text !== 'string') return '';
  
  // First remove complete blocks
  let result = text.replace(/<tool_use>[\s\S]*?<\/tool_use>/gi, '');
  
  // Then remove any orphan opening tags (incomplete tool_use blocks)
  result = result.replace(/<tool_use>\s*$/gi, '');
  result = result.replace(/<tool_use>(?![^<]*<\/tool_use>)/gi, '');
  
  // Remove orphan closing tags
  result = result.replace(/<\/tool_use>/gi, '');
  
  return result.trim();
}

/**
 * serializeGptEntry(gptEntry)
 * Serializes GPT entry to a string for storage (backward compatible)
 * Always rebuilds from segments to ensure fresh serialization
 */
function serializeGptEntry(gptEntry) {
  if (!gptEntry) {
    console.log('[historyFormat] serializeGptEntry: null gptEntry');
    return '';
  }
  if (typeof gptEntry === 'string') {
    console.log('[historyFormat] serializeGptEntry: gptEntry is already a string');
    return gptEntry;
  }
  
  // Build serialized string from segments (always rebuild, don't use cached _serialized)
  if (!Array.isArray(gptEntry.segments) || gptEntry.segments.length === 0) {
    console.log('[historyFormat] serializeGptEntry: no segments, returning _serialized');
    return gptEntry._serialized || '';
  }
  
  console.log(`[historyFormat] serializeGptEntry: serializing ${gptEntry.segments.length} segments`);
  
  const parts = [];
  
  for (const seg of gptEntry.segments) {
    if (seg.type === 'text' && seg.content) {
      console.log(`[historyFormat] serializeGptEntry: text segment "${seg.content.slice(0, 30)}..."`);
      parts.push(seg.content);
    } else if (seg.type === 'tool') {
      console.log(`[historyFormat] serializeGptEntry: tool segment "${seg.name}" (has response: ${!!seg.system_response})`);
      const toolObj = {
        name: seg.name || '',
        input: seg.input || {}
      };
      
      if (seg.system_response !== null && seg.system_response !== undefined) {
        // Use tool_system_response for most tools, tool_system_results for view/grep/etc
        const lname = String(seg.name || '').toLowerCase();
        const useNewKey = (lname === 'view' || lname === 'grep' || lname === 'context_search' || 
                          lname === 'context-search' || lname === 'find_files' || lname === 'ls' || 
                          lname === 'add_todos' || lname === 'create_todo_list' || lname === 'read_todos' || 
                          lname === 'mark_todo_as_done' || lname === 'remove_todos' || lname === 'ask');
        
        if (useNewKey) {
          toolObj.tool_system_results = seg.system_response;
        } else {
          toolObj.tool_system_response = seg.system_response;
        }
      }
      
      parts.push(`<tool_use>\n${JSON.stringify(toolObj, null, 2)}\n</tool_use>`);
    }
  }
  
  const serialized = parts.join('\n\n');
  gptEntry._serialized = serialized;
  console.log(`[historyFormat] serializeGptEntry: final length ${serialized.length}`);
  return serialized;
}

/**
 * deserializeGptEntry(gptString)
 * Deserializes a GPT string back to segmented format
 */
function deserializeGptEntry(gptString) {
  if (!gptString) return createGptEntry();
  
  // If already in new format, return as-is
  if (typeof gptString === 'object' && Array.isArray(gptString.segments)) {
    return gptString;
  }
  
  const entry = createGptEntry();
  entry._serialized = typeof gptString === 'string' ? gptString : '';
  
  if (typeof gptString !== 'string') return entry;
  
  // Parse the string into segments
  const toolRe = /<tool_use>\s*([\s\S]*?)\s*<\/tool_use>/gi;
  let lastIndex = 0;
  let match;
  
  while ((match = toolRe.exec(gptString)) !== null) {
    // Add text before this tool
    const textBefore = gptString.slice(lastIndex, match.index).trim();
    if (textBefore) {
      addTextSegment(entry, textBefore);
    }
    
    // Parse the tool JSON
    const jsonRaw = match[1];
    let parsed = null;
    
    try {
      parsed = JSON.parse(jsonRaw);
    } catch (_) {
      // Try to extract JSON object
      const start = jsonRaw.indexOf('{');
      const end = jsonRaw.lastIndexOf('}');
      if (start !== -1 && end > start) {
        try {
          parsed = JSON.parse(jsonRaw.slice(start, end + 1));
        } catch (_) {}
      }
    }
    
    if (parsed) {
      const sysResp = parsed.tool_system_response || parsed.tool_system_results || null;
      addToolSegment(entry, parsed.id, parsed.name, parsed.input, sysResp);
    }
    
    lastIndex = toolRe.lastIndex;
  }
  
  // Add any remaining text after the last tool
  const textAfter = gptString.slice(lastIndex).trim();
  if (textAfter) {
    addTextSegment(entry, textAfter);
  }
  
  return entry;
}

/**
 * buildRenderableContent(gptEntry)
 * Builds content suitable for rendering
 * Returns the serialized format which the existing renderer can handle
 */
function buildRenderableContent(gptEntry) {
  if (!gptEntry) return '';
  if (typeof gptEntry === 'string') return gptEntry;
  return serializeGptEntry(gptEntry);
}

/**
 * convertApiToolCalls(apiToolCalls)
 * Converts API tool_calls format to our internal format
 */
function convertApiToolCalls(apiToolCalls) {
  if (!Array.isArray(apiToolCalls)) return [];
  return apiToolCalls.map(tc => {
    const fn = tc.function || {};
    let args = {};
    try {
      args = typeof fn.arguments === 'string' ? JSON.parse(fn.arguments) : (fn.arguments || {});
    } catch (_) {
      args = {};
    }
    return {
      id: tc.id || `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      name: fn.name || '',
      input: args
    };
  });
}

/**
 * parseToolCallsFromText(text)
 * Parses <tool_use> blocks from text and extracts tool calls
 * Returns { cleanText, toolCalls }
 */
function parseToolCallsFromText(text) {
  if (!text || typeof text !== 'string') {
    return { cleanText: '', toolCalls: [] };
  }

  const toolCalls = [];
  const toolRe = /<tool_use>\s*([\s\S]*?)\s*<\/tool_use>/gi;
  let match;
  let index = 0;

  while ((match = toolRe.exec(text)) !== null) {
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

    if (parsed) {
      toolCalls.push({
        id: parsed.id || `call_${index}`,
        name: parsed.name || '',
        input: parsed.input || {},
        system_response: parsed.tool_system_response || parsed.tool_system_results || null
      });
      index++;
    }
  }

  const cleanText = removeToolUseBlocks(text);
  return { cleanText, toolCalls };
}

// Legacy exports for backward compatibility
function createToolCall(id, name, input = {}) {
  return {
    id: id || `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    name: name || '',
    input: input || {},
    system_response: null
  };
}

function addSystemResponse(toolCall, response) {
  if (!toolCall) return toolCall;
  return { ...toolCall, system_response: response };
}

module.exports = {
  createGptEntry,
  addTextSegment,
  addToolSegment,
  updateToolSystemResponse,
  serializeGptEntry,
  deserializeGptEntry,
  buildRenderableContent,
  convertApiToolCalls,
  parseToolCallsFromText,
  removeToolUseBlocks,
  // Legacy exports
  createToolCall,
  addSystemResponse
};
