// Load and render chat history with scroll-caching integration.
// PERFORMANCE OPTIMIZED VERSION

const fs = require('fs').promises;
const marked = require('marked');
const { createRenderer } = require('./renderer');
const { loadChatHistory } = require('./historyStorage');
const { escapeHTML, setupCodeBlockListeners } = require('./helpers/dom');
const scrollCache = require('./helpers/scrollCache');
const sessionManager = require('./sessionManager');
const { extractToolUsesAndSanitize } = require('./commands/parser');
const { enhanceToolPlaceholders } = require('./helpers/toolRender');
const { enhanceToolPlaceholdersV2, mergeViewToolsAcrossMessages } = require('./helpers/toolRenderAdapter');
const { embedToolResultsForRendering, embedToolResultsInGptResponse } = require('./send/sendMessage');

// Performance optimization: Cache for history rendering
let historyCache = null;
let historyCacheTime = 0;
const HISTORY_CACHE_TTL = 30000; // 30 seconds

// Performance optimization: DOM fragment for batch rendering
let renderQueue = [];
let renderTimer = null;
const RENDER_BATCH_DEBOUNCE = 100; // 100ms batching

// Load context gatherer popup handler (registers window.showContextGathererSummary)
try { require('./helpers/contextGathererPopup'); } catch (_) {}

// Concurrent session management
const { 
  getSessionState, 
  isSessionResponding, 
  isSessionAwaitingInput,
  SESSION_STATUS 
} = require('./concurrent/sessionStateManager');
const { restoreSessionRender } = require('./concurrent/sessionRenderer');

const logPath = '/tmp/events.log';

/**
 * Performance optimized markdown parsing with caching
 */
function parseMarkdownWithCache(text) {
  if (!text || typeof text !== 'string') return '';
  
  // Use the same cache as responseRenderer
  const { parseMarkdownWithCache: responseParseMarkdownWithCache } = require('./send/modules/responseRenderer');
  return responseParseMarkdownWithCache(text);
}

/**
 * Efficiently batch DOM updates using requestAnimationFrame
 */
function batchDomUpdate(elementCreator, callback) {
  renderQueue.push({ creator: elementCreator, callback });
  
  if (!renderTimer) {
    renderTimer = setTimeout(() => {
      flushRenderQueue();
    }, RENDER_BATCH_DEBOUNCE);
  }
}

/**
 * Flush the render queue to DOM
 */
function flushRenderQueue() {
  if (renderTimer) {
    clearTimeout(renderTimer);
    renderTimer = null;
  }
  
  if (renderQueue.length === 0) return;
  
  const chatMessages = document.getElementById('chat-messages');
  if (!chatMessages) {
    renderQueue = [];
    return;
  }
  
  // Use document fragment for batch insertion
  const fragment = document.createDocumentFragment();
  
  for (const item of renderQueue) {
    const element = item.creator();
    if (element) {
      fragment.appendChild(element);
    }
  }
  
  chatMessages.appendChild(fragment);
  
  // Execute callbacks
  renderQueue.forEach(item => {
    if (item.callback) {
      try { item.callback(); } catch (_) {}
    }
  });
  
  renderQueue = [];
}

/**
 * Clear batched rendering
 */
function clearBatchedRendering() {
  if (renderTimer) {
    clearTimeout(renderTimer);
    renderTimer = null;
  }
  renderQueue = [];
}

/**
 * Efficiently clear chat messages
 */
function clearChatMessages() {
  const chatMessages = document.getElementById('chat-messages');
  if (!chatMessages) return;
  
  // Performance optimization: Use requestAnimationFrame for clearing
  requestAnimationFrame(() => {
    chatMessages.innerHTML = '';
  });
}

/**
 * Create file box element efficiently
 */
function createFileBoxElement(file) {
  try {
    const fileUtils = require('./fileUtils');
    const html = fileUtils.createHistoryFileBox(file);
    if (!html) return null;
    
    // Parse HTML string efficiently
    const tempDiv = document.createElement('div');
    tempDiv.innerHTML = html;
    return tempDiv.firstChild;
  } catch (_) {
    return null;
  }
}

/**
 * Run a synchronous callback while forcing `container`'s inline
 * scroll-behavior to 'auto' so that programmatic scrollTop changes
 * happen immediately (no CSS smooth animation). Restores previous inline
 * value afterwards.
 */
function withImmediateScroll(container, fn) {
  if (!container || typeof fn !== 'function') {
    if (typeof fn === 'function') fn();
    return;
  }
  const prevInline = container.style.scrollBehavior || '';
  try {
    container.style.scrollBehavior = 'auto';
    fn();
  } finally {
    container.style.scrollBehavior = prevInline;
  }
}

/**
 * Attach a scroll-to-cache saver on the container for the current session.
 * Saves immediately on first scroll, and then debounced saves while user scrolls.
 * Ensures only one handler is attached.
 */
function isNearBottom(el, threshold = 24) {
  try {
    return (el.scrollHeight - (el.scrollTop + el.clientHeight)) <= threshold;
  } catch (_) {
    return true;
  }
}

function attachScrollSaver(container, sessionId) {
  if (!container || !sessionId) return;
  // remove previous handler if present
  try {
    if (container.__auaci_scroll_saver) {
      container.removeEventListener('scroll', container.__auaci_scroll_saver);
      container.__auaci_scroll_saver = null;
    }
  } catch (_) {}
  let firstSaved = false;
  let timer = null;
  const handler = () => {
    // record manual scroll time if user is not near bottom
    try {
      if (!isNearBottom(container)) {
        window.chatUserLastManualScrollAt = Date.now();
      }
    } catch (_) {}
    // immediate save the first time we detect a user scroll
    if (!firstSaved) {
      firstSaved = true;
      scrollCache.saveScrollFromContainer(sessionId, container).catch(() => {});
    }
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      scrollCache.saveScrollFromContainer(sessionId, container).catch(() => {});
      timer = null;
    }, 900);
  };
  container.addEventListener('scroll', handler, { passive: true });
  container.__auaci_scroll_saver = handler;
}

/**
 * Render chat history for the currently active session.
 */
async function displayChatHistory() {
  const chatMessages = document.getElementById('chat-messages');
  if (!chatMessages) return;
  
  // Setup smart scroll tracking for chat messages
  try {
    const { setupScrollTracking } = require('./helpers/smartScroll');
    setupScrollTracking(chatMessages);
  } catch (err) {
    console.warn('[historyDisplay] Failed to setup scroll tracking:', err);
  }
  
  clearChatMessages();
  try {
    // Ask tab manager for sessions count (dynamically require to avoid circular require at module load)
    let sessions = [];
    try {
      const tabManager = require('./tabManager');
      if (typeof tabManager.getSessions === 'function') {
        sessions = await tabManager.getSessions();
      }
    } catch (_) {
      // ignore if tab manager not available
    }

    if (!Array.isArray(sessions) || sessions.length === 0) {
      // No sessions at all in this project directory -> show project-level empty state
      const fragment = document.createDocumentFragment();
      const emptyDiv = document.createElement('div');
      emptyDiv.className = 'auaci-empty-project';
      emptyDiv.textContent = 'You have no chat session in this project directory yet, maybe start a new one?';
      fragment.appendChild(emptyDiv);
      chatMessages.appendChild(fragment);
      
      try { if (require('./tabManager').refreshTabs) await require('./tabManager').refreshTabs(); } catch (_) {}
      return;
    }

    // During history render we keep any raw terminal-like text so we don't over-strip older logs
    try { window.AUACI_KEEP_TERMINAL_TEXT = true; } catch (_) {}

    // Resolve the current session id first and then load history for that explicit session.
    // This avoids a race where loadChatHistory() might implicitly create or select a different
    // session file if the global session pointer changes concurrently during a switch.
    let sessionId = null;
    try { sessionId = await sessionManager.getSessionId(); } catch (_) { sessionId = null; }

    const history = await loadChatHistory(sessionId);
    window.allChatEntries = history.chat || [];
    marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });

    // Determine cached scroll for current session (if any)
    // (sessionId already resolved above)

    const cached = sessionId ? await scrollCache.getScroll(sessionId) : null;

    // If the current session has no messages, show "this chat session is empty" message
    if (!Array.isArray(window.allChatEntries) || window.allChatEntries.length === 0) {
      // determine session name (prefer history.name)
      let sessionName = history && history.name ? history.name : null;
      if (!sessionName) {
        try {
          const tm = require('./tabManager');
          const sm = require('./sessionManager');
          const sid = await sm.getSessionId();
          const sessionsList = await tm.getSessions();
          const found = sessionsList.find(s => s.id === sid);
          if (found) sessionName = found.name;
        } catch (_) { /* ignore */ }
      }
      if (!sessionName) sessionName = 'Chat';

      // Performance optimization: Use document fragment
      const fragment = document.createDocumentFragment();
      const emptyDiv = document.createElement('div');
      emptyDiv.className = 'auaci-empty-session';
      emptyDiv.style.textAlign = 'center';
      
      const titleDiv = document.createElement('div');
      titleDiv.style.fontWeight = '600';
      titleDiv.style.marginBottom = '8px';
      titleDiv.style.fontSize = '16px';
      titleDiv.textContent = escapeHTML(sessionName);
      
      const subtitleDiv = document.createElement('div');
      subtitleDiv.style.color = '#9ca3af';
      subtitleDiv.textContent = 'This chat has no messages yet, start sending one :)' ;
      
      emptyDiv.appendChild(titleDiv);
      emptyDiv.appendChild(subtitleDiv);
      fragment.appendChild(emptyDiv);
      chatMessages.appendChild(fragment);
      
      try { if (require('./tabManager').refreshTabs) await require('./tabManager').refreshTabs(); } catch (_) {}
      // attach saver so if user scrolls after messages are added we'll save
      if (sessionId) attachScrollSaver(chatMessages, sessionId);
      return;
    }

    // Render the entire chat thread (no last-N truncation)
    const defaultStartIndex = 0;

    // If we have a cached visible-top entry, keep it in view by starting a bit earlier
    const BUFFER_BEFORE = 5;
    let computedStartIndex = defaultStartIndex;
    if (cached && typeof cached.topEntryIndex === 'number' && !Number.isNaN(cached.topEntryIndex)) {
      const wanted = Math.max(0, Math.floor(cached.topEntryIndex) - BUFFER_BEFORE);
      computedStartIndex = Math.min(defaultStartIndex, wanted); // remains 0 unless cache suggests earlier (still 0)
    }

    window.renderedStartIndex = Math.max(0, computedStartIndex);
    window.renderedEndIndex = window.allChatEntries.length - 1;

    // Render entries starting from renderedStartIndex. Add data-entry-index attributes
    const initialEntries = window.allChatEntries.slice(window.renderedStartIndex);
    let entryIndex = window.renderedStartIndex;
    
    // Performance optimization: Use a document fragment for batch DOM insertion
    const fragment = document.createDocumentFragment();
    
    for (const entry of initialEntries) {
      // Handle CD command entries
      if (entry.type === 'cd_command') {
        try {
          const { renderCdCommandFromHistory } = require('./ui/terminalInput');
          renderCdCommandFromHistory(entry);
        } catch (e) {
          console.warn('[historyDisplay] Failed to render CD command:', e);
        }
        entryIndex++;
        continue;
      }
      
      const userDiv = document.createElement('div');
      userDiv.className = 'message user-message';
      userDiv.setAttribute('data-entry-index', String(entryIndex));
      const userContent = document.createElement('div');
      userContent.className = 'message-content';
      if (entry.user && entry.user.text) {
        const textDiv = document.createElement('div');
        textDiv.textContent = entry.user.text;
        userContent.appendChild(textDiv);
      }
      if (entry.user && entry.user.files) {
        // Performance optimization: Create file box elements instead of string concatenation
        entry.user.files.forEach(file => {
          const fileBox = createFileBoxElement(file);
          if (fileBox) userContent.appendChild(fileBox);
        });
      }
      userDiv.appendChild(userContent);
      fragment.appendChild(userDiv);

      if (entry.gpt) {
        // Skip rendering if GPT response is just "empty" placeholder or whitespace
        const gptText = String(entry.gpt || '').trim();
        const isEmptyPlaceholder = gptText === 'empty' || gptText === '' || gptText === '(empty)' || gptText === '[empty]';
        
        if (!isEmptyPlaceholder) {
          const gptDiv = document.createElement('div');
          gptDiv.className = 'message gpt-message';
          gptDiv.setAttribute('data-entry-index', String(entryIndex));
          const gptContent = document.createElement('div');
          gptContent.className = 'message-content';
          // For backward compatibility, embed any legacy tool_runs into the gpt text for rendering
          const legacyRuns = Array.isArray(entry.tool_runs) ? entry.tool_runs : (Array.isArray(entry.gpt_tools) ? entry.gpt_tools : []);
          // Clean saved text by embedding and stripping any orphan result JSON before rendering
          const cleanedText = embedToolResultsInGptResponse(entry.gpt || '', legacyRuns);
          const textForRender = embedToolResultsForRendering(cleanedText || '', legacyRuns);
          let parsed = extractToolUsesAndSanitize(textForRender || '');
          let sanitizedText = parsed.sanitizedText;
          // Do not attempt to strip attempt_completion text here; the renderer appends it safely at the end.
          gptContent.innerHTML = parseMarkdownWithCache(sanitizedText);
          // Use the new robust tool rendering system with fallback
          try { 
            enhanceToolPlaceholdersV2(gptContent, parsed.tools, []); 
          } catch (e) {
            console.warn('[historyDisplay] V2 render failed, falling back:', e);
            try { enhanceToolPlaceholders(gptContent, parsed.tools, []); } catch (_) {}
          }
          gptDiv.appendChild(gptContent);
          fragment.appendChild(gptDiv);
        }
      } else if (entryIndex === (window.allChatEntries.length - 1)) {
        // If last entry has no GPT yet and this session is responding, render a placeholder
        try {
          // Check both legacy map and new concurrent session state
          const legacyMap = window.__auaciRespondingSessions || {};
          const isResponding = isSessionResponding(sessionId) || (sessionId && legacyMap && legacyMap[sessionId]);
          if (isResponding) {
            const gptDiv = document.createElement('div');
            gptDiv.className = 'message gpt-message';
            gptDiv.setAttribute('data-entry-index', String(entryIndex));
            const gptContent = document.createElement('div');
            gptContent.className = 'message-content';
            gptDiv.appendChild(gptContent);
            fragment.appendChild(gptDiv);
          }
        } catch (_) {}
      }
      entryIndex++;
    }
    
    // Single DOM insertion for all history items
    chatMessages.appendChild(fragment);

    // If session is responding, ensure inline thinking shows after render
    try {
      // Check both legacy map and new concurrent session state
      const legacyMap = window.__auaciRespondingSessions || {};
      const isResponding = isSessionResponding(sessionId) || (sessionId && legacyMap && legacyMap[sessionId]);
      if (isResponding) {
        const ind = require('./ui/indicator');
        ind.showGptIndicator(sessionId);
        ind.showInlineThinking(sessionId);
      } else {
        try { require('./ui/indicator').hideInlineThinking(sessionId); } catch (_) {}
      }
    } catch (_) {}

    // Restore cached view if present (immediate scroll); otherwise scroll to bottom
    if (cached && typeof cached.topEntryIndex === 'number') {
      const targetIndex = Math.min(window.allChatEntries.length - 1, Math.max(0, Math.floor(cached.topEntryIndex)));
      const el = chatMessages.querySelector(`[data-entry-index="${targetIndex}"]`);
      if (el) {
        try {
          const containerRect = chatMessages.getBoundingClientRect();
          const elRect = el.getBoundingClientRect();
          const relativeTop = (elRect.top - containerRect.top) + (chatMessages.scrollTop || 0);
          const offset = (typeof cached.offset === 'number') ? cached.offset : 0;
          withImmediateScroll(chatMessages, () => {
            chatMessages.scrollTop = Math.max(0, Math.round(relativeTop + offset));
          });
        } catch (_) {
          withImmediateScroll(chatMessages, () => { chatMessages.scrollTop = chatMessages.scrollHeight; });
        }
      } else {
        withImmediateScroll(chatMessages, () => { chatMessages.scrollTop = chatMessages.scrollHeight; });
      }
    } else {
      withImmediateScroll(chatMessages, () => { chatMessages.scrollTop = chatMessages.scrollHeight; });
    }

    // If we have more messages above, add a "Load more" loader
    if (window.renderedStartIndex > 0) {
      const cacheLoader = document.createElement('div');
      cacheLoader.className = 'cache-loader';
      cacheLoader.innerHTML = `
        <div class="cache-loader-content">
          <span class="cache-loader-text">Previous messages have been cached</span>
          <button class="cache-loader-button">Load More Messages</button>
        </div>
      `;
      chatMessages.insertBefore(cacheLoader, chatMessages.firstChild);
      const button = cacheLoader.querySelector('.cache-loader-button');
      button.addEventListener('click', () => require('./messageCache').loadCachedMessages());
    }

    // Attach scroll saver for this session
    if (sessionId) attachScrollSaver(chatMessages, sessionId);

    setupCodeBlockListeners();
    
    // Merge consecutive view tools across GPT messages for compact rendering
    try {
      mergeViewToolsAcrossMessages(chatMessages);
    } catch (e) {
      console.warn('[historyDisplay] Failed to merge view tools:', e);
    }
    
    // After history render, check if this session needs concurrent state restoration
    // This handles the case where user switches to a session that's still responding
    try {
      const sessionState = getSessionState(sessionId);
      // Only restore for truly active sessions (streaming, executing tool, or awaiting input)
      // Don't restore for completed/idle sessions as history display already rendered them
      if (sessionState && sessionState.isResponding && 
          (sessionState.status === SESSION_STATUS.STREAMING || 
           sessionState.status === SESSION_STATUS.EXECUTING_TOOL ||
           sessionState.status === SESSION_STATUS.AWAITING_INPUT)) {
        console.log(`[historyDisplay] Session ${sessionId} is active (${sessionState.status}), restoring concurrent state`);
        const result = await restoreSessionRender(sessionId, chatMessages);
        if (result.success) {
          console.log(`[historyDisplay] Restored session state:`, {
            status: result.status,
            isResponding: result.isResponding,
            thinkingSeconds: result.thinkingSeconds,
            restoredThinking: result.restoredThinking,
            restoredToolAnimation: result.restoredToolAnimation,
            restoredAskTool: result.restoredAskTool
          });
        }
      }
    } catch (e) {
      console.warn('[historyDisplay] Failed to restore concurrent session state:', e);
    }
  } catch (err) {
    await fs.appendFile(logPath, `[${new Date().toISOString()}] Error loading chat history: ${String(err && err.message ? err.message : err)}\n`);
    console.error('displayChatHistory error:', err);
  } finally {
    // Restore default behavior for future (streaming) renders
    try { delete window.AUACI_KEEP_TERMINAL_TEXT; } catch (_) {}
  }
}

async function loadMoreMessages() {
  if (window.renderedStartIndex <= 0 || window.isLoadingMore) return;
  window.isLoadingMore = true;

  const chatMessages = document.getElementById('chat-messages');
  if (!chatMessages) {
    window.isLoadingMore = false;
    return;
  }

  const oldScrollHeight = chatMessages.scrollHeight;
  const oldScrollTop = chatMessages.scrollTop;

  let gptCount = 0;
  let loadCount = 0;
  let newStartIndex = Math.max(0, window.renderedStartIndex - 1);

  for (let i = window.renderedStartIndex - 1; i >= 0 && gptCount < 10; i--) {
    if (window.allChatEntries[i].gpt) {
      gptCount++;
    }
    loadCount++;
    newStartIndex = i;
  }

  const newEntries = window.allChatEntries.slice(newStartIndex, window.renderedStartIndex);
  const fragment = document.createDocumentFragment();

  // During load-more render we keep any raw terminal-like text so we don't over-strip older logs
  try { window.AUACI_KEEP_TERMINAL_TEXT = true; } catch (_) {}

  // Insert in chronological order (older -> newer) so slice(newStartIndex, startIndex) is correct
  let entryIndex = newStartIndex;
  for (const entry of newEntries) {
    // Handle CD command entries
    if (entry.type === 'cd_command') {
      const cdDiv = document.createElement('div');
      cdDiv.className = 'message user-message';
      cdDiv.style.marginBottom = '8px';
      
      const resultDiv = document.createElement('div');
      resultDiv.className = `cd-result-message${entry.error ? ' error' : ''}`;
      
      if (entry.error) {
        resultDiv.innerHTML = `
          <div class="cd-line">
            <span class="cd-label">cd:</span>
            <span class="cd-command">${escapeHTML(entry.command)}</span>
          </div>
          <div class="cd-current-path" style="color:#dc2626">${escapeHTML(entry.error)}</div>
        `;
      } else {
        resultDiv.innerHTML = `
          <div class="cd-line">
            <span class="cd-label">cd:</span>
            <span class="cd-command">${escapeHTML(entry.command)}</span>
          </div>
          <div class="cd-current-path">${escapeHTML(entry.result)}</div>
        `;
      }
      
      cdDiv.appendChild(resultDiv);
      fragment.appendChild(cdDiv);
      entryIndex++;
      continue;
    }
    
    const userDiv = document.createElement('div');
    userDiv.className = 'message user-message';
    userDiv.setAttribute('data-entry-index', String(entryIndex));
    const userContent = document.createElement('div');
    userContent.className = 'message-content';
    if (entry.user.text) {
      const textDiv = document.createElement('div');
      textDiv.textContent = entry.user.text;
      userContent.appendChild(textDiv);
    }
    if (entry.user.files) {
      entry.user.files.forEach(file => {
        userContent.innerHTML += require('./fileUtils').createHistoryFileBox(file);
      });
    }
    userDiv.appendChild(userContent);
    fragment.appendChild(userDiv);

    if (entry.gpt) {
      // Skip rendering if GPT response is just "empty" placeholder or whitespace
      const gptText = String(entry.gpt || '').trim();
      const isEmptyPlaceholder = gptText === 'empty' || gptText === '' || gptText === '(empty)' || gptText === '[empty]';
      
      if (!isEmptyPlaceholder) {
        const gptDiv = document.createElement('div');
        gptDiv.className = 'message gpt-message';
        gptDiv.setAttribute('data-entry-index', String(entryIndex));
        const gptContent = document.createElement('div');
        gptContent.className = 'message-content';
        // For backward compatibility, embed any legacy tool_runs into the gpt text for rendering
        const legacyRuns = Array.isArray(entry.tool_runs) ? entry.tool_runs : (Array.isArray(entry.gpt_tools) ? entry.gpt_tools : []);
        const textForRender = embedToolResultsForRendering(entry.gpt || '', legacyRuns);
        let parsed = extractToolUsesAndSanitize(textForRender || '');
        let cleanedText = parsed.sanitizedText;
        // Do not attempt to strip attempt_completion text here; the renderer appends it safely at the end.
        gptContent.innerHTML = marked.parse(cleanedText);
        // Use the new robust tool rendering system with fallback
        try { 
          enhanceToolPlaceholdersV2(gptContent, parsed.tools, []); 
        } catch (e) {
          console.warn('[historyDisplay] V2 render failed, falling back:', e);
          try { enhanceToolPlaceholders(gptContent, parsed.tools, []); } catch (_) {}
        }
        gptDiv.appendChild(gptContent);
        fragment.appendChild(gptDiv);
      }
    }
    entryIndex++;
  }

  chatMessages.insertBefore(fragment, chatMessages.firstChild);
  window.renderedStartIndex = newStartIndex;

  const newScrollHeight = chatMessages.scrollHeight;
  // Preserve user's viewport: shift scroll by the difference in height, immediately
  // Performance optimization: Use requestAnimationFrame for smooth scrolling
  requestAnimationFrame(() => {
    withImmediateScroll(chatMessages, () => {
      chatMessages.scrollTop = oldScrollTop + (newScrollHeight - oldScrollHeight);
    });
  });

  require('./messaging').setupCodeBlockListeners();
  window.isLoadingMore = false;
  try {
    await fs.appendFile(logPath, `[${new Date().toISOString()}] Loaded ${loadCount} more messages\n`);
  } catch (err) {
    // ignore logging failures
  } finally {
    try { delete window.AUACI_KEEP_TERMINAL_TEXT; } catch (_) {}
  }
}

/**
 * Remove orphan tool-result JSON blocks introduced by older histories
 * where raw tool JSON was appended outside the <tool_use> block.
 * We detect standalone JSON objects likely produced by tools
 * (keys like content, entries, total_line_count, is_truncated, finished/exit_code)
 * and strip them when they appear outside code fences.
 */
function removeOrphanToolResultJson(text) {
  if (!text || typeof text !== 'string') return text;

  // Quick path: if there's no '{', nothing to do
  if (text.indexOf('{') === -1) return text;

  const fences = findCodeFences(text);
  const isInsideFence = (idx) => {
    for (const f of fences) if (idx >= f.start && idx < f.end) return true;
    return false;
  };

  let out = '';
  let i = 0;
  while (i < text.length) {
    const ch = text[i];
    if (ch === '{' && !isInsideFence(i)) {
      const end = findMatchingJsonEnd(text, i);
      if (end !== -1) {
        const candidate = text.slice(i, end + 1);
        if (looksLikeToolResult(candidate)) {
          // Skip this JSON object entirely and also skip a following "Note:"-style annotation if present
          let j = end + 1;
          j = skipAnnotationLines(text, j);
          i = j;
          continue;
        }
      }
    }
    out += ch;
    i++;
  }
  return out;
}

function looksLikeToolResult(jsonStr) {
  try {
    const obj = JSON.parse(jsonStr);
    if (!obj || typeof obj !== 'object') return false;
    const keys = Object.keys(obj);
    // Heuristics tuned for our tools
    const hasViewKeys = ('content' in obj) || ('total_line_count' in obj) || ('is_truncated' in obj);
    const hasLsKeys = Array.isArray(obj.entries);
    const hasRunKeys = (obj.finished && typeof obj.finished.exit_code !== 'undefined');
    const hasGenericError = ('error' in obj) && keys.length <= 3;
    // Grep detection
    const hasGrepKeys = ('pattern_stats' in obj) || ('matched_files' in obj) || ('matched_files_count' in obj) || (Array.isArray(obj.pattern_stats));
    return hasViewKeys || hasLsKeys || hasRunKeys || hasGenericError || hasGrepKeys;
  } catch (_) {
    return false;
  }
}

function findCodeFences(text) {
  const ranges = [];
  const re = /```[\s\S]*?```/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    ranges.push({ start: m.index, end: re.lastIndex });
  }
  return ranges;
}

function removeDuplicateAttemptCompletionText(text, tools) {
  if (!text || typeof text !== 'string') return text;
  if (!Array.isArray(tools) || tools.length === 0) return text;
  let out = text;

  // Only remove duplicates that appear AFTER the attempt_completion placeholder.
  // This prevents deleting earlier conversational text that may be similar.
  for (const t of tools) {
    if (!t || t.name !== 'attempt_completion') continue;
    const snippet = (typeof t.result === 'string') ? t.result : (t.input && typeof t.input.result === 'string' ? t.input.result : null);
    if (typeof snippet !== 'string' || !snippet.trim()) continue;
    if (snippet.trim().length < 24) continue; // avoid removing very short phrases

    // Find the specific placeholder for this tool index in the sanitized text; don't rely on name spelling
    const needle = `data-tool-index=\\\"${t.index}\\\"`;
    const phIdx = out.indexOf(needle);
    if (phIdx === -1) {
      // If we can't locate the placeholder reliably, avoid stripping to prevent data loss
      continue;
    }

    // Build a fence map to avoid removing content inside code blocks
    const fences = findCodeFences(out);
    const isInsideFence = (idx) => {
      for (const f of fences) if (idx >= f.start && idx < f.end) return true;
      return false;
    };

    // Look for the first occurrence AFTER the placeholder
    let pos = out.indexOf(snippet, phIdx);
    while (pos !== -1 && isInsideFence(pos)) {
      pos = out.indexOf(snippet, pos + 1);
    }
    if (pos !== -1) {
      out = out.slice(0, pos) + out.slice(pos + snippet.length);
    }
  }
  return out;
}

function stripPlainTextOnce(text, snippet) {
  const fences = findCodeFences(text);
  const isInsideFence = (idx) => {
    for (const f of fences) if (idx >= f.start && idx < f.end) return true;
    return false;
  };
  const idx = text.indexOf(snippet);
  if (idx === -1 || isInsideFence(idx)) return text;
  return text.slice(0, idx) + text.slice(idx + snippet.length);
}

function findMatchingJsonEnd(text, startIndex) {
  let i = startIndex;
  const len = text.length;
  if (text[i] !== '{') return -1;
  let depth = 0;
  let inString = false;
  let stringChar = null;
  let escape = false;

  for (; i < len; i++) {
    const ch = text[i];
    if (inString) {
      if (escape) { escape = false; continue; }
      if (ch === '\\') { escape = true; continue; }
      if (ch === stringChar) { inString = false; stringChar = null; continue; }
      continue;
    } else {
      if (ch === '"' || ch === "'") { inString = true; stringChar = ch; continue; }
      if (ch === '{') { depth++; continue; }
      if (ch === '}') { depth--; if (depth === 0) return i; continue; }
    }
  }
  return -1;
}

/**
 * Skip annotation lines that commonly follow results (e.g. "Note: Content truncated...").
 * Helps avoid leaving these annotations as stray text below rendered tool-boxes.
 */
function skipAnnotationLines(text, pos) {
  let i = pos;
  while (i < text.length && /\s/.test(text[i])) i++;
  let advanced = false;
  const annotRe = /^\s*(?:Note\b|Note:|Warning\b|Warning:|Info:|Info\b|Hint:|Hint\b)/i;
  while (i < text.length) {
    const nextNl = text.indexOf('\n', i);
    const line = nextNl === -1 ? text.slice(i) : text.slice(i, nextNl);
    if (annotRe.test(line)) {
      i = nextNl === -1 ? text.length : nextNl + 1;
      advanced = true;
      continue;
    }
    if (line.trim() === '' && advanced) {
      i = nextNl === -1 ? text.length : nextNl + 1;
      break;
    }
    break;
  }
  return i;
}

module.exports = { displayChatHistory, loadMoreMessages };