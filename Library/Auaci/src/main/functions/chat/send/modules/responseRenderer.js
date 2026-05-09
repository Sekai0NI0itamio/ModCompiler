// src/main/functions/chat/send/modules/responseRenderer.js
// Clean module for rendering GPT responses with tools
// PERFORMANCE OPTIMIZED VERSION

const marked = require('marked');
const { createRenderer } = require('../../renderer');
const { setupCodeBlockListeners } = require('../../helpers/dom');
const { enhanceToolPlaceholders } = require('../../helpers/toolRender');
const { enhanceToolPlaceholdersV2 } = require('../../helpers/toolRenderAdapter');
const { hasClaudeToolCalls, convertClaudeToOpenAI } = require('../../helpers/claudeToolConverter');

// Performance optimization: Cache for markdown parsing results
const markdownCache = new Map();
const MARKDOWN_CACHE_MAX_SIZE = 200; // Increased from 100
const MARKDOWN_CACHE_TTL = 120000; // 120 seconds (increased from 30s)

// Performance optimization: Debounced rendering
let renderDebounceTimer = null;
const RENDER_DEBOUNCE_MS = 50; // Batch DOM updates every 50ms

// Performance optimization: Throttled DOM operations
let lastRenderTime = 0;
const MIN_RENDER_INTERVAL = 100; // Minimum 100ms between renders

// Performance optimization: Request animation frame for DOM updates
let pendingRender = null;
let animationFrameId = null;

/**
 * OPTIMIZED: Better hash function to prevent collisions
 * Uses content-based hashing instead of length-based
 */
function hashContent(text) {
  if (!text) return '0';
  
  // Use multiple character samples to avoid collisions
  // Sample first 20 chars, last 20 chars, and middle section
  const len = text.length;
  const sample1 = text.substring(0, Math.min(20, len));
  const sample2 = len > 40 ? text.substring(len - 20) : '';
  const sample3 = len > 100 ? text.substring(Math.floor(len / 2), Math.floor(len / 2) + 20) : '';
  
  // Create hash from combined samples
  const combined = sample1 + sample2 + sample3;
  let hash = 0;
  for (let i = 0; i < combined.length; i++) {
    const char = combined.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash; // Convert to 32bit integer
  }
  
  // Include length to distinguish truly different content
  return hash.toString(36) + ':' + len;
}

/**
 * Performance optimized markdown parsing with caching
 * OPTIMIZED: Better cache hashing to prevent collisions
 */
function parseMarkdownWithCache(text) {
  if (!text || typeof text !== 'string') return '';
  
  // Use content-based hash instead of length-based
  const hash = hashContent(text);
  
  // Check cache
  const cached = markdownCache.get(hash);
  if (cached && Date.now() - cached.timestamp < MARKDOWN_CACHE_TTL) {
    return cached.html;
  }
  
  // Parse and cache
  const html = marked.parse(text);
  
  // Manage cache size (FIFO)
  if (markdownCache.size >= MARKDOWN_CACHE_MAX_SIZE) {
    const firstKey = markdownCache.keys().next().value;
    markdownCache.delete(firstKey);
  }
  
  markdownCache.set(hash, { html, timestamp: Date.now() });
  return html;
}

/**
 * OPTIMIZED: Schedule render with proper RAF batching
 * Yields to browser for reflow/repaint between updates
 */
function scheduleRender(containerEl, gptText, toolRuns = []) {
  const now = Date.now();
  
  // Throttle to MIN_RENDER_INTERVAL but use RAF for batching
  if (now - lastRenderTime < MIN_RENDER_INTERVAL) {
    // Queue for later rendering
    pendingRender = { containerEl, gptText, toolRuns };
    
    if (!animationFrameId) {
      // Use RAF to batch with browser refresh cycle
      animationFrameId = requestAnimationFrame(() => {
        if (pendingRender) {
          renderGptResponseOptimized(pendingRender.containerEl, pendingRender.gptText, pendingRender.toolRuns);
          pendingRender = null;
          lastRenderTime = Date.now();
        }
        animationFrameId = null;
      });
    }
    return;
  }
  
  // Clear any pending render
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  }
  pendingRender = null;
  
  // Perform render immediately (within throttle window)
  renderGptResponseOptimized(containerEl, gptText, toolRuns);
  lastRenderTime = now;
}

/**
 * Debounced rendering for streaming updates
 */
function debouncedRender(containerEl, gptText, toolRuns = []) {
  if (renderDebounceTimer) {
    clearTimeout(renderDebounceTimer);
  }
  
  renderDebounceTimer = setTimeout(() => {
    scheduleRender(containerEl, gptText, toolRuns);
    renderDebounceTimer = null;
  }, RENDER_DEBOUNCE_MS);
}

/**
 * Cache for protected ranges to avoid re-parsing the same text
 * WeakMap would be ideal but strings aren't suitable for WeakMap
 * Instead we use a simple cache with text hash
 */
const protectedRangesCache = new Map();
const CACHE_MAX_SIZE = 50;

function getTextHash(text) {
  // Simple hash for caching - using length + first/last char combo
  return text.length + ':' + text.charCodeAt(0) + ':' + text.charCodeAt(text.length - 1);
}

/**
 * Find all "protected" ranges in text where tool tags should NOT be parsed:
 * - Fenced code blocks (```...```)
 * - Inline code (`...`)
 * 
 * NOTE: We intentionally do NOT protect quoted strings ("..." or '...') because
 * the JSON inside <tool_use> blocks contains many quoted strings, and protecting
 * them would cause legitimate tools to be skipped.
 * 
 * Returns array of { start, end } objects
 * OPTIMIZED: Uses caching and faster binary-search lookup
 */
function findCodeBlockRanges(text) {
  const hash = getTextHash(text);
  if (protectedRangesCache.has(hash)) {
    return protectedRangesCache.get(hash);
  }
  
  const ranges = [];
  
  // Find fenced code blocks (```...```) - highest priority
  const fencedRe = /```[\s\S]*?```/g;
  let match;
  while ((match = fencedRe.exec(text)) !== null) {
    ranges.push({ start: match.index, end: match.index + match[0].length });
  }
  
  // Optimize: Sort ranges for binary search later
  ranges.sort((a, b) => a.start - b.start);
  
  // Find inline code (`...`) - but not inside fenced blocks using sorted ranges
  const inlineRe = /`[^`\n]+`/g;
  while ((match = inlineRe.exec(text)) !== null) {
    if (!isInRange(match.index, ranges)) {
      ranges.push({ start: match.index, end: match.index + match[0].length });
    }
  }
  
  // Maintain cache size limit
  if (protectedRangesCache.size >= CACHE_MAX_SIZE) {
    const firstKey = protectedRangesCache.keys().next().value;
    protectedRangesCache.delete(firstKey);
  }
  
  protectedRangesCache.set(hash, ranges);
  return ranges;
}

/**
 * Helper to check if position is already in a range using binary search
 * Assumes ranges are sorted by start position
 */
function isInRange(pos, ranges) {
  let left = 0, right = ranges.length - 1;
  while (left <= right) {
    const mid = Math.floor((left + right) / 2);
    const r = ranges[mid];
    if (pos >= r.start && pos < r.end) return true;
    if (pos < r.start) right = mid - 1;
    else left = mid + 1;
  }
  return false;
}

/**
 * Check if a position is inside any protected range (code blocks)
 * OPTIMIZED: Uses binary search instead of linear scan
 */
function isInsideProtectedRange(pos, protectedRanges) {
  return isInRange(pos, protectedRanges);
}

/**
 * Normalize Claude-style tool names to our tool names
 * Claude models trained for VSCode/editor use different naming conventions
 */
function normalizeClaudeToolName(name) {
  if (!name) return name;
  const n = name.toLowerCase().trim();
  
  // Claude editor tool mappings
  const mappings = {
    'editor': 'view',           // Claude's editor tool -> our view tool
    'str_replace_editor': 'str_replace',
    'execute_command': 'run_command',
    'bash': 'run_command',
    'terminal': 'run_command',
    'read_file': 'view',
    'write_file': 'write_to_file',
    'list_files': 'ls',
    'search_files': 'grep',
    'find_files': 'find_files',
  };
  
  return mappings[n] || name;
}

/**
 * Check if a string looks like a valid Claude tool call
 * Must have proper structure with function and parameter tags
 */
function isValidClaudeToolCall(xmlBlock) {
  if (!xmlBlock) return false;
  
  // Must have a function tag with = syntax (Claude style): <function=name>
  if (!/<function\s*=\s*[a-zA-Z_][a-zA-Z0-9_]*\s*>/i.test(xmlBlock)) return false;
  
  // Must have at least one parameter tag with = syntax: <parameter=key>
  if (!/<parameter\s*=\s*[a-zA-Z_][a-zA-Z0-9_]*\s*>/i.test(xmlBlock)) return false;
  
  // Must have closing </function> tag
  if (!/<\/function>/i.test(xmlBlock)) return false;
  
  // Must have closing </parameter> tag
  if (!/<\/parameter>/i.test(xmlBlock)) return false;
  
  return true;
}

/**
 * Parse Claude-style tool_call XML format
 * Format: <tool_call>\n<function=name>\n<parameter=key>value</parameter>\n</function>\n</tool_call>
 * Also handles: <function=name>...</function> without outer tool_call wrapper
 */
function parseClaudeToolCall(xmlBlock) {
  try {
    // Validate this is actually a Claude tool call (not random text)
    if (!isValidClaudeToolCall(xmlBlock)) return null;
    
    // Extract function name: <function=name> (Claude uses = syntax)
    const funcMatch = xmlBlock.match(/<function\s*=\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*>/i);
    if (!funcMatch) return null;
    
    let name = funcMatch[1];
    const input = {};
    
    // Extract parameters: <parameter=key>value</parameter> (Claude uses = syntax)
    const paramRe = /<parameter\s*=\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*>([\s\S]*?)<\/parameter>/gi;
    let paramMatch;
    while ((paramMatch = paramRe.exec(xmlBlock)) !== null) {
      const key = paramMatch[1];
      let value = paramMatch[2];
      
      // Try to parse as JSON if it looks like JSON
      if (value.trim().startsWith('{') || value.trim().startsWith('[') || value.trim().startsWith('"')) {
        try {
          value = JSON.parse(value.trim());
        } catch (_) {
          // Keep as string
        }
      }
      
      input[key] = value;
    }
    
    // Normalize Claude tool names to our tool names
    name = normalizeClaudeToolName(name);
    
    // Also normalize parameter names for Claude's editor tool
    // Claude uses: editor_mode, path, lines, etc.
    // We use: path, ranges, etc.
    if (input.editor_mode === 'view' && !input.ranges && input.lines) {
      // Convert lines to ranges format if needed
      input.ranges = [input.lines];
      delete input.lines;
    }
    
    return { name, input };
  } catch (_) {
    return null;
  }
}

/**
 * Cache for compiled regex patterns
 */
const regexCache = {
  toolUse: /<tool_use>\s*([\s\S]*?)\s*<\/tool_use>/gi,
  toolCall: /<tool_call>\s*([\s\S]*?)\s*<\/tool_call>/gi,
  claudeFunc: /<function\s*=\s*[a-zA-Z_][a-zA-Z0-9_]*\s*>[\s\S]*?<parameter\s*=[\s\S]*?<\/parameter>[\s\S]*?<\/function>/gi
};

/**
 * Parse <tool_use> blocks AND Claude-style <tool_call>/<function=> blocks from GPT response text
 * Returns { cleanText, tools }
 * IMPORTANT: Skips tool-like patterns inside protected ranges (code blocks, quotes)
 * OPTIMIZED: Uses streaming parser with reduced regex overhead
 */
function parseToolsFromResponse(gptText) {
  if (!gptText || typeof gptText !== 'string') {
    return { cleanText: '', tools: [] };
  }
  
  // First, convert any Claude Code XML tool calls to OpenAI format
  let processedText = gptText;
  if (hasClaudeToolCalls(gptText)) {
    console.log('[responseRenderer] Detected Claude Code XML tool calls, converting');
    processedText = convertClaudeToOpenAI(gptText);
  }
  
  const tools = [];
  const textParts = [];
  
  // Find all protected ranges to skip (code blocks, quotes)
  const protectedRanges = findCodeBlockRanges(processedText);
  
  // Use optimized streaming parser
  const segments = parseSegmentsOptimized(processedText, protectedRanges);
  
  for (const seg of segments) {
    if (seg.type === 'text' && seg.content) {
      textParts.push(seg.content);
    } else if (seg.type === 'tool' && seg.parsed) {
      tools.push({
        name: seg.parsed.name,
        input: seg.parsed.input || {},
        systemResponse: seg.parsed.tool_system_response || seg.parsed.tool_system_results || null
      });
    }
  }
  
  return {
    cleanText: textParts.join('\n\n'),
    tools
  };
}

/**
 * Optimized streaming parser that extracts tools and text in order
 * Uses a single pass with character-based state machine
 */
function parseSegmentsOptimized(text, protectedRanges) {
  const segments = [];
  let lastIndex = 0;
  let i = 0;
  
  while (i < text.length) {
    // Look for tool start markers
    const toolStart = text.indexOf('<tool_use>', i);
    const callStart = text.indexOf('<tool_call>', i);
    const funcStart = text.indexOf('<function=', i);
    
    // Find the nearest tool marker
    const markers = [
      { pos: toolStart, type: 'tool_use' },
      { pos: callStart, type: 'tool_call' },
      { pos: funcStart, type: 'function' }
    ].filter(m => m.pos !== -1).sort((a, b) => a.pos - b.pos);
    
    if (markers.length === 0) {
      // No more tools
      const remaining = text.slice(lastIndex).trim();
      if (remaining) {
        segments.push({ type: 'text', content: remaining });
      }
      break;
    }
    
    const marker = markers[0];
    
    // Check if this marker is in a protected range
    if (isInsideProtectedRange(marker.pos, protectedRanges)) {
      i = marker.pos + 1;
      continue;
    }
    
    // Extract and parse the tool
    let parsed = null;
    let toolEndPos = -1;
    
    if (marker.type === 'tool_use') {
      const endPos = text.indexOf('</tool_use>', marker.pos);
      if (endPos !== -1) {
        toolEndPos = endPos + '</tool_use>'.length;
        const jsonRaw = text.slice(marker.pos + '<tool_use>'.length, endPos);
        parsed = tryParseJSON(jsonRaw);
      }
    } else if (marker.type === 'tool_call') {
      const endPos = text.indexOf('</tool_call>', marker.pos);
      if (endPos !== -1) {
        toolEndPos = endPos + '</tool_call>'.length;
        const xmlBlock = text.slice(marker.pos + '<tool_call>'.length, endPos);
        parsed = parseClaudeToolCall(xmlBlock);
      }
    } else if (marker.type === 'function') {
      const endPos = text.indexOf('</function>', marker.pos);
      if (endPos !== -1) {
        toolEndPos = endPos + '</function>'.length;
        const xmlBlock = text.slice(marker.pos, toolEndPos);
        parsed = parseClaudeToolCall(xmlBlock);
      }
    }
    
    if (toolEndPos !== -1 && parsed && parsed.name) {
      // Add text before this tool
      const textBefore = text.slice(lastIndex, marker.pos).trim();
      if (textBefore) {
        segments.push({ type: 'text', content: textBefore });
      }
      
      // Add tool
      segments.push({ type: 'tool', parsed });
      
      lastIndex = toolEndPos;
      i = toolEndPos;
    } else {
      i = marker.pos + 1;
    }
  }
  
  return segments;
}

/**
 * Try to parse JSON with fallback
 */
function tryParseJSON(text) {
  try {
    return JSON.parse(text);
  } catch (_) {
    const start = text.indexOf('{');
    const end = text.lastIndexOf('}');
    if (start !== -1 && end > start) {
      try {
        return JSON.parse(text.slice(start, end + 1));
      } catch (_) {}
    }
  }
  return null;
}

/**
 * Performance optimized GPT response rendering
 * Uses incremental DOM updates instead of complete resets
 */
function renderGptResponseOptimized(containerEl, gptText, toolRuns = []) {
  if (!containerEl) {
    console.warn('[responseRenderer] No container element provided');
    return;
  }
  
  // Parse the response into segments
  const segments = parseSegmentsInOrder(gptText);
  
  // Setup marked renderer
  try {
    marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });
  } catch (_) {}
  
  // Get existing segments for diffing
  const existingSegments = Array.from(containerEl.children);
  
  // Performance optimization: Only update changed segments
  for (let i = 0; i < Math.max(segments.length, existingSegments.length); i++) {
    const segment = segments[i];
    const existingElement = existingSegments[i];
    
    if (!segment) {
      // Remove extra elements
      if (existingElement) {
        existingElement.remove();
      }
      continue;
    }
    
    if (!existingElement) {
      // Add new element
      if (segment.type === 'text' && segment.content) {
        const textDiv = document.createElement('div');
        textDiv.className = 'gpt-text-segment';
        textDiv.innerHTML = parseMarkdownWithCache(segment.content);
        containerEl.appendChild(textDiv);
      } else if (segment.type === 'tool') {
        const toolBox = createToolBox(segment);
        containerEl.appendChild(toolBox);
        enhanceToolBox(toolBox, segment, toolRuns);
      }
      continue;
    }
    
    // Update existing element if content changed
    if (segment.type === 'text' && segment.content) {
      const expectedContent = parseMarkdownWithCache(segment.content);
      if (existingElement.innerHTML !== expectedContent) {
        existingElement.innerHTML = expectedContent;
      }
    } else if (segment.type === 'tool') {
      // Check if tool needs update
      const toolName = existingElement.getAttribute('data-tool') || '';
      if (toolName !== segment.name) {
        existingElement.replaceWith(createToolBox(segment));
        enhanceToolBox(existingElement, segment, toolRuns);
      }
    }
  }
  
  // Setup code block listeners
  try {
    setupCodeBlockListeners();
  } catch (_) {}
}

/**
 * Create a tool box element
 */
function createToolBox(segment) {
  const toolBox = document.createElement('div');
  toolBox.className = 'auaci-tool-box';
  toolBox.setAttribute('data-tool', segment.name || 'tool');
  
  const header = document.createElement('div');
  header.className = 'tool-header';
  const nameSpan = document.createElement('span');
  nameSpan.className = 'auaci-tool-name';
  nameSpan.textContent = segment.name || 'tool';
  header.appendChild(nameSpan);
  
  const body = document.createElement('div');
  body.className = 'tool-body';
  
  toolBox.appendChild(header);
  toolBox.appendChild(body);
  
  return toolBox;
}

/**
 * Enhance tool box with tool data
 */
function enhanceToolBox(toolBox, segment, toolRuns) {
  const toolData = [{
    name: segment.name,
    input: segment.input,
    result: segment.systemResponse,
    tool_system_response: segment.systemResponse,
    tool_system_results: segment.systemResponse
  }];
  
  const matchingRun = toolRuns.find(r => r.name === segment.name);
  const runsForEnhance = matchingRun ? [matchingRun] : [];
  
  try {
    try {
      enhanceToolPlaceholdersV2(toolBox, toolData, runsForEnhance);
    } catch (e) {
      console.warn('[responseRenderer] V2 render failed, falling back:', e);
      enhanceToolPlaceholders(toolBox, toolData, runsForEnhance);
    }
  } catch (e) {
    console.warn('[responseRenderer] Failed to enhance tool placeholder:', e);
  }
}

/**
 * Original render function now uses optimized version
 * Maintains backward compatibility
 */
function renderGptResponse(containerEl, gptText, toolRuns = []) {
  debouncedRender(containerEl, gptText, toolRuns);
}

/**
 * Parse GPT response into ordered segments (text and tools in order)
 * Handles both <tool_use> JSON format and Claude-style <tool_call>/<function=> XML format
 * IMPORTANT: Skips tool-like patterns inside protected ranges (code blocks, quotes)
 * OPTIMIZED: Uses optimized streaming parser
 */
function parseSegmentsInOrder(gptText) {
  if (!gptText || typeof gptText !== 'string') {
    return [];
  }
  
  // First, convert any Claude Code XML tool calls to OpenAI format
  let processedText = gptText;
  if (hasClaudeToolCalls(gptText)) {
    processedText = convertClaudeToOpenAI(gptText);
  }
  
  // Find all protected ranges to skip (code blocks, quotes)
  const protectedRanges = findCodeBlockRanges(processedText);
  
  // Use optimized streaming parser
  const segments = parseSegmentsOptimized(processedText, protectedRanges);
  
  // Convert parsed segments to the expected format
  return segments.map(seg => {
    if (seg.type === 'tool' && seg.parsed) {
      return {
        type: 'tool',
        name: seg.parsed.name || '',
        input: seg.parsed.input || {},
        systemResponse: seg.parsed.tool_system_response || seg.parsed.tool_system_results || null
      };
    }
    return seg;
  });
}

/**
 * Get or create the GPT message container for an entry
 */
function getOrCreateGptContainer(chatMessagesEl, entryIndex) {
  // Try to find existing
  let gptDiv = chatMessagesEl.querySelector(`.message.gpt-message[data-entry-index="${entryIndex}"]`);
  
  if (!gptDiv) {
    gptDiv = document.createElement('div');
    gptDiv.className = 'message gpt-message';
    gptDiv.setAttribute('data-entry-index', String(entryIndex));
    
    const contentEl = document.createElement('div');
    contentEl.className = 'message-content';
    gptDiv.appendChild(contentEl);
    
    chatMessagesEl.appendChild(gptDiv);
  }
  
  return gptDiv.querySelector('.message-content');
}

/**
 * Show thinking indicator in container
 */
function showThinkingIndicator(containerEl) {
  if (!containerEl) return null;
  
  // Remove any existing
  const existing = containerEl.querySelector('.thinking-inline');
  if (existing) existing.remove();
  
  const thinkingDiv = document.createElement('div');
  thinkingDiv.className = 'thinking-inline';
  thinkingDiv.innerHTML = `
    <span class="typing-text">GPT is thinking (<span class="thinking-seconds">0</span>s)</span>
    <span class="gpt-indicator-dots">
      <span class="dot"></span>
      <span class="dot"></span>
      <span class="dot"></span>
    </span>
  `;
  containerEl.appendChild(thinkingDiv);
  
  // Start timer
  const startTime = Date.now();
  const secondsEl = thinkingDiv.querySelector('.thinking-seconds');
  const timer = setInterval(() => {
    const secs = Math.floor((Date.now() - startTime) / 1000);
    if (secondsEl) secondsEl.textContent = String(secs);
  }, 1000);
  
  return { element: thinkingDiv, timer };
}

/**
 * Hide thinking indicator
 */
function hideThinkingIndicator(thinkingRef) {
  if (!thinkingRef) return;
  
  if (thinkingRef.timer) {
    clearInterval(thinkingRef.timer);
  }
  if (thinkingRef.element && thinkingRef.element.parentNode) {
    thinkingRef.element.remove();
  }
}

module.exports = {
  parseToolsFromResponse,
  parseSegmentsInOrder,
  parseClaudeToolCall,
  normalizeClaudeToolName,
  isValidClaudeToolCall,
  findCodeBlockRanges,
  isInsideProtectedRange,
  renderGptResponse,
  renderGptResponseOptimized,
  debouncedRender,
  scheduleRender,
  parseMarkdownWithCache,
  getOrCreateGptContainer,
  showThinkingIndicator,
  hideThinkingIndicator
};
