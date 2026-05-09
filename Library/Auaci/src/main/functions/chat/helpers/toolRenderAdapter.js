// src/main/functions/chat/helpers/toolRenderAdapter.js
// Adapter that provides a clean interface to the tool rendering system
// Wraps both the legacy toolRender.js and the new modular renderers

const { safeRenderToolBox, batchRenderToolBoxes, ensureToolStyles } = require('./robustToolRender');
const { isToolExecuting, getToolHeaderTitle, renderToolCompleted, renderToolExecuting } = require('./toolRenders');
const { renderGrouped, isViewTool } = require('./toolRenders/completed/view_group');

/**
 * Check if two DOM elements are consecutive siblings (allowing whitespace and empty elements between them)
 * @param {HTMLElement} el1 - First element
 * @param {HTMLElement} el2 - Second element
 * @returns {boolean}
 */
function areConsecutiveSiblings(el1, el2) {
  if (!el1 || !el2) return false;
  if (el1.parentNode !== el2.parentNode) return false;
  
  let next = el1.nextSibling;
  while (next && next !== el2) {
    // Skip whitespace-only text nodes
    if (next.nodeType === Node.TEXT_NODE) {
      if (!next.textContent.trim()) {
        next = next.nextSibling;
        continue;
      }
      // Non-whitespace text between them - not consecutive
      return false;
    }
    
    // Skip empty elements (like empty <p> tags from markdown)
    if (next.nodeType === Node.ELEMENT_NODE) {
      const el = next;
      // Check if it's an empty or whitespace-only element
      if (!el.textContent.trim() && !el.querySelector('.auaci-tool-box')) {
        next = next.nextSibling;
        continue;
      }
      // Non-empty element between them - not consecutive
      return false;
    }
    
    next = next.nextSibling;
  }
  
  return next === el2;
}

/**
 * Find groups of consecutive view tool boxes
 * @param {Array} boxes - Array of tool box elements
 * @param {Array} runs - Array of normalized run data
 * @returns {Array} - Array of { startIndex, endIndex, boxes, runs } for each group
 */
function findViewToolGroups(boxes, runs) {
  const groups = [];
  let i = 0;
  
  while (i < boxes.length) {
    const box = boxes[i];
    const run = runs[i] || {};
    const toolName = run.name || box.getAttribute('data-tool') || '';
    
    // Check if this is a view tool
    if (isViewTool(toolName)) {
      // Start a potential group
      const groupStart = i;
      const groupBoxes = [box];
      const groupRuns = [run];
      
      // Look for consecutive view tools
      let j = i + 1;
      while (j < boxes.length) {
        const nextBox = boxes[j];
        const nextRun = runs[j] || {};
        const nextToolName = nextRun.name || nextBox.getAttribute('data-tool') || '';
        
        // Check if next tool is also a view tool AND is a consecutive sibling
        if (isViewTool(nextToolName) && areConsecutiveSiblings(groupBoxes[groupBoxes.length - 1], nextBox)) {
          groupBoxes.push(nextBox);
          groupRuns.push(nextRun);
          j++;
        } else {
          break;
        }
      }
      
      // Only create a group if we have 2+ consecutive view tools
      if (groupBoxes.length >= 2) {
        groups.push({
          startIndex: groupStart,
          endIndex: j - 1,
          boxes: groupBoxes,
          runs: groupRuns
        });
      }
      
      i = j;
    } else {
      i++;
    }
  }
  
  return groups;
}

/**
 * Create a grouped view box element
 * @param {Array} viewTools - Array of { input, result } objects
 * @returns {HTMLElement}
 */
function createGroupedViewBoxElement(viewTools) {
  const box = document.createElement('div');
  box.className = 'auaci-tool-box view-group-box';
  box.setAttribute('data-tool', 'view_group');
  box.setAttribute('data-tool-count', String(viewTools.length));
  
  // Header
  const header = document.createElement('div');
  header.className = 'tool-header';
  
  const nameSpan = document.createElement('span');
  nameSpan.className = 'auaci-tool-name';
  nameSpan.textContent = `View (${viewTools.length} files)`;
  header.appendChild(nameSpan);
  
  // Body
  const body = document.createElement('div');
  body.className = 'tool-body';
  body.innerHTML = renderGrouped(viewTools);
  
  box.appendChild(header);
  box.appendChild(body);
  
  return box;
}

/**
 * Enhanced tool placeholder rendering
 * Uses the new modular system with fallback to legacy
 * Supports grouping consecutive view tools into a single compact box
 * OPTIMIZED: Caches DOM queries and uses batch operations
 * @param {HTMLElement} container - Container with tool boxes
 * @param {Array} tools - Parsed tool entries
 * @param {Array} toolRuns - Tool run records
 * @param {object} options - Render options
 */
function enhanceToolPlaceholdersV2(container, tools, toolRuns = [], options = {}) {
  if (!container) return;
  
  // Ensure toolRuns is always a valid array to prevent undefined length errors
  const safeToolRuns = Array.isArray(toolRuns) ? toolRuns : [];
  
  // OPTIMIZED: Cache the initial query result
  let boxes = Array.from(container.querySelectorAll('.auaci-tool-box'));
  if (boxes.length === 0) return;
  
  // Ensure styles are injected
  ensureToolStyles();
  ensureViewGroupStyles();
  
  // Build normalized runs array
  const runs = buildNormalizedRuns(boxes, tools, safeToolRuns);
  
  // Find groups of consecutive view tools
  const viewGroups = findViewToolGroups(boxes, runs);
  
  // Track which boxes are part of a group (to skip individual rendering)
  const groupedBoxIndices = new Set();
  for (const group of viewGroups) {
    for (let idx = group.startIndex; idx <= group.endIndex; idx++) {
      groupedBoxIndices.add(idx);
    }
  }
  
  // OPTIMIZED: Batch DOM modifications - collect all operations first
  const modificationQueue = [];
  
  for (const group of viewGroups) {
    const viewTools = group.runs.map((run, idx) => ({
      input: run.input || {},
      result: run.result || null
    }));
    
    // Create the grouped box
    const groupedBox = createGroupedViewBoxElement(viewTools);
    
    // Queue modifications instead of doing them immediately
    modificationQueue.push({
      type: 'insert',
      element: groupedBox,
      referenceElement: group.boxes[0],
      parent: group.boxes[0].parentNode
    });
    
    for (const box of group.boxes) {
      modificationQueue.push({
        type: 'remove',
        element: box
      });
    }
  }
  
  // Execute all modifications
  for (const op of modificationQueue) {
    if (op.type === 'insert' && op.parent && op.referenceElement) {
      op.parent.insertBefore(op.element, op.referenceElement);
    } else if (op.type === 'remove' && op.element && op.element.parentNode) {
      op.element.remove();
    }
  }
  
  // OPTIMIZED: Re-query only the remaining boxes (not grouped)
  const remainingBoxes = Array.from(container.querySelectorAll('.auaci-tool-box:not(.view-group-box)'));
  if (!Array.isArray(runs) || runs.length === 0) return;
  
  // Render remaining non-grouped boxes
  let runIndex = 0;
  for (let i = 0; i < boxes.length; i++) {
    if (groupedBoxIndices.has(i)) continue;
    
    const run = (Array.isArray(runs) && runs[i]) ? runs[i] : {};
    const remainingBox = remainingBoxes[runIndex];
    
    if (remainingBox) {
      const toolData = {
        name: run.name || remainingBox.getAttribute('data-tool') || '',
        input: run.input || {},
        result: run.result || null
      };
      
      safeRenderToolBox(remainingBox, toolData, { toolIndex: String(i) });
    }
    runIndex++;
  }
}

/**
 * Ensure view group CSS styles are injected
 */
function ensureViewGroupStyles() {
  if (typeof document === 'undefined') return;
  if (document.getElementById('auaci-view-group-styles')) return;
  
  const style = document.createElement('style');
  style.id = 'auaci-view-group-styles';
  style.textContent = `
    .view-group-box {
      padding: 8px 12px;
    }
    .view-group-box .tool-header {
      margin-bottom: 8px;
    }
    .view-group-body {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .view-group-line {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      padding: 4px 0;
      border-bottom: 1px solid #f3f4f6;
    }
    .view-group-line:last-child {
      border-bottom: none;
      padding-bottom: 0;
    }
    .view-group-line:first-child {
      padding-top: 0;
    }
    .view-group-icon {
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 14px;
      height: 14px;
    }
    .view-group-icon svg {
      width: 14px;
      height: 14px;
    }
    .view-group-path {
      flex: 1;
      color: #1f2937;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      min-width: 0;
    }
    .view-group-info {
      flex-shrink: 0;
      color: #6b7280;
      font-size: 11px;
    }
    .view-group-info .tool-sub {
      color: #6b7280;
    }
  `;
  document.head.appendChild(style);
}

/**
 * Build normalized runs array from various input sources
 */
function buildNormalizedRuns(boxes, tools, toolRuns) {
  const runs = [];
  
  // Ensure toolRuns is a valid array to prevent "Cannot read properties of undefined (reading 'length')" error
  // toolRuns can be undefined when a tool completes while the app is unfocused and no runs cache was restored yet.
  const safeToolRuns = Array.isArray(toolRuns) ? toolRuns : [];
  
  // Build a map of runs by uid for robust matching
  const runsByUid = new Map();
  for (let i = 0; i < (safeToolRuns.length || 0); i++) {
    const run = safeToolRuns[i];
    if (run && run.__uid) {
      runsByUid.set(String(run.__uid), { run, index: i });
    }
  }
  
  for (let i = 0; i < boxes.length; i++) {
    const box = boxes[i];
    const uid = box.getAttribute('data-tool-uid');
    const t = (Array.isArray(tools) && tools[i]) ? tools[i] : null;
    const savedRun = (Array.isArray(safeToolRuns) && safeToolRuns[i]) ? safeToolRuns[i] : null;
    
    // Try uid-based matching first
    if (uid && runsByUid.has(uid)) {
      const matched = runsByUid.get(uid);
      runs.push(normalizeRun(matched.run, t));
    } else if (savedRun) {
      runs.push(normalizeRun(savedRun, t));
    } else if (t) {
      runs.push({
        name: t.name || '',
        input: t.input || {},
        result: t.result || t.tool_system_response || null,
        summary: (t.result && t.result.summary) ? t.result.summary : {},
        success: t.result && typeof t.result.error !== 'undefined' ? !t.result.error : null
      });
    } else {
      runs.push({ name: '', input: {}, result: null, summary: {}, success: null });
    }
  }
  
  return runs;
}

/**
 * Normalize a run record
 */
function normalizeRun(savedRun, tool) {
  return {
    name: savedRun.name || (tool && tool.name) || '',
    input: savedRun.input || (tool && tool.input) || {},
    result: savedRun.result !== undefined ? savedRun.result : (tool && tool.result) || null,
    summary: savedRun.summary || {},
    success: typeof savedRun.success === 'boolean' 
      ? savedRun.success 
      : (savedRun.result && !savedRun.result.error ? true : !!(tool && tool.result && !tool.result.error))
  };
}

/**
 * Render a single tool inline (for streaming updates)
 * @param {HTMLElement} box - The tool box element
 * @param {object} toolData - Tool data
 * @param {object} options - Options
 */
function renderToolInline(box, toolData, options = {}) {
  safeRenderToolBox(box, toolData, options);
}

/**
 * Create a tool box element
 * @param {string} toolName - Tool name
 * @param {object} input - Tool input
 * @param {string} toolIndex - Tool index
 * @returns {HTMLElement}
 */
function createToolBox(toolName, input, toolIndex) {
  const box = document.createElement('div');
  box.className = 'auaci-tool-box';
  box.setAttribute('data-tool', toolName);
  box.setAttribute('data-tool-index', String(toolIndex));
  
  const header = document.createElement('div');
  header.className = 'tool-header';
  
  const nameSpan = document.createElement('span');
  nameSpan.className = 'auaci-tool-name';
  nameSpan.textContent = getToolHeaderTitle(toolName, true, null);
  header.appendChild(nameSpan);
  
  const body = document.createElement('div');
  body.className = 'tool-body';
  body.innerHTML = renderToolExecuting(toolName, input || {}, String(toolIndex));
  
  box.appendChild(header);
  box.appendChild(body);
  box.classList.add('tool-executing');
  
  return box;
}

/**
 * Update a tool box with completed result
 * @param {HTMLElement} box - The tool box element
 * @param {object} result - Tool result
 */
function updateToolBoxWithResult(box, result) {
  if (!box) return;
  
  const toolName = box.getAttribute('data-tool') || '';
  const toolIndex = box.getAttribute('data-tool-index') || '0';
  
  // Get input from existing data if possible
  let input = {};
  try {
    const existingData = box.__toolData;
    if (existingData && existingData.input) {
      input = existingData.input;
    }
  } catch (_) {}
  
  safeRenderToolBox(box, { name: toolName, input, result }, { toolIndex });
}

/**
 * Check if a tool is in executing state
 */
function isExecuting(result) {
  return isToolExecuting(result);
}

/**
 * Get display title for a tool
 */
function getDisplayTitle(toolName, isExecuting, result) {
  return getToolHeaderTitle(toolName, isExecuting, result);
}

/**
 * Merge view tool boxes across consecutive GPT messages
 * This handles the case where GPT returns view tools in consecutive responses
 * without any text message in between.
 * 
 * @param {HTMLElement} chatContainer - The chat messages container
 */
function mergeViewToolsAcrossMessages(chatContainer) {
  if (!chatContainer) return;
  
  ensureViewGroupStyles();
  
  // Get all GPT messages
  const gptMessages = Array.from(chatContainer.querySelectorAll('.gpt-message'));
  if (gptMessages.length < 2) return;
  
  // Find consecutive GPT messages that only contain view tools (no text)
  let i = 0;
  while (i < gptMessages.length) {
    const msg = gptMessages[i];
    const content = msg.querySelector('.message-content');
    if (!content) {
      i++;
      continue;
    }
    
    // Check if this message only contains view tools (no text content)
    if (!isViewToolOnlyMessage(content)) {
      i++;
      continue;
    }
    
    // Start collecting consecutive view-only messages
    const messagesToMerge = [{ msg, content }];
    let j = i + 1;
    
    while (j < gptMessages.length) {
      const nextMsg = gptMessages[j];
      const nextContent = nextMsg.querySelector('.message-content');
      
      if (!nextContent || !isViewToolOnlyMessage(nextContent)) {
        break;
      }
      
      // Check if messages are consecutive (no user message in between)
      if (areConsecutiveGptMessages(msg, nextMsg, chatContainer)) {
        messagesToMerge.push({ msg: nextMsg, content: nextContent });
        j++;
      } else {
        break;
      }
    }
    
    // If we have multiple consecutive view-only messages, merge them
    if (messagesToMerge.length >= 2) {
      mergeViewMessages(messagesToMerge);
      // Skip the merged messages
      i = j;
    } else {
      i++;
    }
  }
}

/**
 * Check if a message content only contains view tools (no text)
 * @param {HTMLElement} content - The message content element
 * @returns {boolean}
 */
function isViewToolOnlyMessage(content) {
  // Get all child nodes
  const children = Array.from(content.childNodes);
  
  let hasViewTool = false;
  let hasNonViewContent = false;
  
  for (const child of children) {
    // Skip whitespace-only text nodes
    if (child.nodeType === Node.TEXT_NODE) {
      if (child.textContent.trim()) {
        hasNonViewContent = true;
      }
      continue;
    }
    
    // Check if it's a tool box
    if (child.nodeType === Node.ELEMENT_NODE) {
      const el = child;
      
      // Check for view tool box or view group box
      if (el.classList.contains('auaci-tool-box')) {
        const toolName = el.getAttribute('data-tool') || '';
        if (isViewTool(toolName) || toolName === 'view_group') {
          hasViewTool = true;
        } else {
          hasNonViewContent = true;
        }
      } else if (el.classList.contains('view-group-box')) {
        hasViewTool = true;
      } else {
        // Any other element (text div, etc.) counts as non-view content
        // But skip empty divs
        if (el.textContent.trim()) {
          hasNonViewContent = true;
        }
      }
    }
  }
  
  return hasViewTool && !hasNonViewContent;
}

/**
 * Check if two GPT messages are consecutive (no user message in between)
 * @param {HTMLElement} msg1 - First GPT message
 * @param {HTMLElement} msg2 - Second GPT message
 * @param {HTMLElement} container - Chat container
 * @returns {boolean}
 */
function areConsecutiveGptMessages(msg1, msg2, container) {
  let current = msg1.nextElementSibling;
  
  while (current && current !== msg2) {
    // If we find a user message, they're not consecutive
    if (current.classList.contains('user-message')) {
      return false;
    }
    current = current.nextElementSibling;
  }
  
  return current === msg2;
}

/**
 * Merge multiple view-only messages into a single grouped view box
 * @param {Array} messages - Array of { msg, content } objects
 */
function mergeViewMessages(messages) {
  if (messages.length < 2) return;
  
  // Collect all view tools from all messages
  const allViewTools = [];
  
  for (const { content } of messages) {
    // Get view tool boxes (both individual and grouped)
    const toolBoxes = content.querySelectorAll('.auaci-tool-box');
    
    for (const box of toolBoxes) {
      const toolName = box.getAttribute('data-tool') || '';
      
      if (toolName === 'view_group') {
        // Extract individual tools from grouped box
        const lines = box.querySelectorAll('.view-group-line');
        for (const line of lines) {
          const pathEl = line.querySelector('.view-group-path');
          const infoEl = line.querySelector('.view-group-info');
          const iconEl = line.querySelector('.view-group-icon');
          
          const path = pathEl ? pathEl.textContent : '';
          const hasError = iconEl ? iconEl.innerHTML.includes('fail') : false;
          
          // Try to extract line info
          let lineInfo = '';
          if (infoEl) {
            const subEl = infoEl.querySelector('.tool-sub');
            lineInfo = subEl ? subEl.textContent : infoEl.textContent;
          }
          
          allViewTools.push({
            input: { path },
            result: hasError ? { error: 'Error' } : { displayed_lines: lineInfo }
          });
        }
      } else if (isViewTool(toolName)) {
        // Extract data from individual view box
        const body = box.querySelector('.tool-body');
        const toolLine = body ? body.querySelector('.tool-line') : null;
        
        let path = '';
        let result = {};
        
        if (toolLine) {
          const fileEl = toolLine.querySelector('.tool-file');
          const subEl = toolLine.querySelector('.tool-sub');
          const iconEl = toolLine.querySelector('.tool-icon');
          
          path = fileEl ? fileEl.textContent : '';
          const hasError = iconEl ? iconEl.classList.contains('fail') : false;
          
          if (hasError && subEl) {
            result = { error: subEl.textContent };
          } else if (subEl) {
            result = { displayed_lines: subEl.textContent };
          }
        }
        
        allViewTools.push({
          input: { path },
          result
        });
      }
    }
  }
  
  if (allViewTools.length === 0) return;
  
  // Create the merged grouped box
  const groupedBox = createGroupedViewBoxElement(allViewTools);
  
  // Insert the grouped box in place of the first message's content
  const firstContent = messages[0].content;
  firstContent.innerHTML = '';
  firstContent.appendChild(groupedBox);
  
  // Remove the other messages
  for (let i = 1; i < messages.length; i++) {
    messages[i].msg.remove();
  }
}

module.exports = {
  enhanceToolPlaceholdersV2,
  renderToolInline,
  createToolBox,
  updateToolBoxWithResult,
  isExecuting,
  getDisplayTitle,
  ensureToolStyles,
  mergeViewToolsAcrossMessages,
  isViewTool
};
