// Updated to assign data-entry-index attributes to newly appended user/gpt nodes
// so scroll-cache can map DOM -> entry index.
// New robust history format: saves raw GPT text + tool calls + system responses separately

const marked = require('marked');
const { createRenderer } = require('../renderer');
const { saveChatHistory } = require('../historyStorage');
const { spawn } = require('child_process');
const fs = require('fs').promises;
const path = require('path');
const {
  initiateChatEntry,
  updateGptResponse,
  // addToolRun, // deprecated in new format
  finalizeChatEntry
} = require('../incrementalHistoryStorage');
const { streamToString, streamChat, stopRequest } = require('../gpt-api');
const { extractToolUsesAndSanitize } = require('../commands/parser');
const { executeCommand } = require('../commands/executor');
const { buildPrompt, getSystemPrompt } = require('../ai-logic/gpt-msg-sender');
const { getTools } = require('../ai-logic/tool-instructions/index');
const { writeUserRequest } = require('../ai-logic/aicoder/showwhatssent');
const { showGptIndicator, hideGptIndicator } = require('../ui/indicator');
const { escapeHTML, setupCodeBlockListeners } = require('../helpers/dom');
const { getSessionId } = require('../sessionManager');
const tabManager = require('../tabManager');
const { smoothScrollTo } = require('../helpers/smoothScroll');
const scrollCache = require('../helpers/scrollCache');
const { enhanceToolPlaceholders } = require('../helpers/toolRender');
const { appendRawHistoryEntry, updateRawHistoryEntry } = require('../rawHistoryStorage');
const { createMessageSession, buildGptPayload, cancelGptSession } = require('../session-orchestrator');
const { truncateToolContent } = require('./modules/gptCycle');
const {
  createGptEntry,
  addTextSegment,
  addToolSegment,
  updateToolSystemResponse,
  serializeGptEntry,
  deserializeGptEntry,
  buildRenderableContent,
  convertApiToolCalls: convertApiToolCallsFormat,
  parseToolCallsFromText,
  removeToolUseBlocks
} = require('../historyFormat');

// Decide if a todo list is recommended for this user request using a short, out-of-band classification
async function shouldSuggestTodosFor(userText, files) {
  try {
    const { streamToString } = require('../gpt-api');
    let selectedModel = null;
    try { selectedModel = require('../modelSelector').getSelectedModel(); } catch (_) { selectedModel = null; }
    const fileHints = Array.isArray(files) && files.length
      ? `Attached files: ${files.map(f => f && (f.name || f.path || f.filePath || f.filepath) || '').filter(Boolean).slice(0,6).join(', ')}`
      : '';
    const classifierPrompt = [
      'You are a classifier deciding if a TODO list is helpful for the following user request.',
      'Reply with exactly one word: yes or no. Any other response is treated as no.',
      '',
      'User request:',
      (userText || '').trim(),
      fileHints ? `\n${fileHints}` : ''
    ].join('\n');
    const tmpSessionId = `todo-suggest-${Date.now()}-${Math.random().toString(36).slice(2,8)}`;
    const reply = await streamToString({ user_prompt: classifierPrompt, session_id: tmpSessionId }, { model: selectedModel || undefined });
    const ans = String(reply || '').trim().toLowerCase();
    return ans === 'yes';
  } catch (_) {
    return false;
  }
}

// Attach current session's todo snapshot to tool system response sent back to the model (not rendered)
async function attachSessionTodoIfActive(resultObj, sessionId) {
  try {
    const state = await getSessionTodoState(sessionId);
    if (!state.hasAny) return JSON.stringify(resultObj);
    const snap = {
      pending_todos: state.pending,
      completed_todos: state.completed.length ? state.completed : null
    };
    const augmented = Object.assign({}, resultObj, { session_todo_list: snap });
    // If the list is fully complete (no pending items), include a gentle notice so the model knows it may finalize.
    if (!state.hasIncomplete) {
      const notice = await buildTodoCompletionNoticeMessage(sessionId);
      if (notice && typeof notice === 'string' && notice.trim()) {
        augmented.todo_completion_notice = notice;
      }
    }
    return JSON.stringify(augmented);
  } catch (_) {
    try { return JSON.stringify(resultObj); } catch { return '{}'; }
  }
}

// Compute current todo state for this session (pending vs completed).
async function getSessionTodoState(sessionId) {
  try {
    const { loadTodos } = require('../commands/lib/todos');
    const { pending, completed } = await loadTodos(sessionId);
    const p = Array.isArray(pending) ? pending : [];
    const c = Array.isArray(completed) ? completed : [];
    return {
      pending: p,
      completed: c,
      hasAny: p.length > 0 || c.length > 0,
      hasIncomplete: p.length > 0
    };
  } catch (_) {
    return { pending: [], completed: [], hasAny: false, hasIncomplete: false };
  }
}

// Render the full todo list (pending + completed) using the same formatting concept as the read_todos tool.
async function renderSessionTodoListText(sessionId, existingState) {
  let state = existingState;
  if (!state) state = await getSessionTodoState(sessionId);
  try {
    const readTodosCmd = require('../commands/handlers/read_todos');
    const res = await readTodosCmd();
    if (res && typeof res.display_content === 'string' && res.display_content.trim()) {
      const tl = res.todo_list || {};
      const pending = Array.isArray(tl.pending_todos) ? tl.pending_todos : state.pending || [];
      const completed = Array.isArray(tl.completed_todos) ? tl.completed_todos : state.completed || [];
      return { text: String(res.display_content), pending, completed };
    }
  } catch (_) {}
  // Fallback: minimal plain-text rendering.
  const pending = Array.isArray(state.pending) ? state.pending : [];
  const completed = Array.isArray(state.completed) ? state.completed : [];
  const lines = [];
  if (!pending.length && !completed.length) {
    lines.push('Todo List');
    lines.push('-'.repeat(40));
    lines.push('No todos found. Use create_todo_list or add_todos to get started.');
  } else {
    if (pending.length) {
      lines.push(`Pending Tasks (${pending.length})`);
      lines.push('-'.repeat(40));
      pending.forEach((todo, idx) => {
        const statusIcon = '[ ]';
        const indexStr = String(idx + 1).padStart(2, ' ');
        const title = String(todo && todo.title || '').trim();
        const details = String(todo && todo.details || '').trim();
        lines.push(`${indexStr}. ${statusIcon} ${title}${details ? `\n     ${details}` : ''}`);
      });
    }
    if (completed.length) {
      if (pending.length) lines.push('');
      lines.push(`Completed Tasks (${completed.length})`);
      lines.push('-'.repeat(40));
      completed.forEach((todo, idx) => {
        const statusIcon = '[x]';
        const indexStr = String(idx + 1).padStart(2, ' ');
        const title = String(todo && todo.title || '').trim();
        const details = String(todo && todo.details || '').trim();
        lines.push(`${indexStr}. ${statusIcon} ${title}${details ? `\n     ${details}` : ''}`);
      });
    }
  }
  return { text: lines.join('\n'), pending, completed };
}

// Build a guard message when the model tries to finalize with attempt_completion but there are still pending todos.
async function buildTodoGuardMessageForAttemptCompletion(sessionId) {
  const state = await getSessionTodoState(sessionId);
  if (!state.hasAny || !state.hasIncomplete) return null; // nothing to guard
  const rendered = await renderSessionTodoListText(sessionId, state);
  const header = 'System Response: You attempted to finish using attempt_completion, but there is still an active TODO list with uncompleted items for this session.';
  const instructions = 'You must finish working through your TODO list before finalizing. Review the list below (pending and completed items), continue using tools and updating todos as you progress, and only when there are zero pending todos, call attempt_completion again with a final summary. Your previous attempt_completion summary has NOT been saved.';
  const messageParts = [
    header,
    '',
    instructions,
    '',
    'CURRENT TODO LIST',
    '-----------------',
    rendered.text
  ];
  return messageParts.join('\n');
}

// Build a notice message when the session's todo list is now fully complete (no pending todos, but at least one completed).
async function buildTodoCompletionNoticeMessage(sessionId) {
  const state = await getSessionTodoState(sessionId);
  if (!state.hasAny || state.hasIncomplete) return null;
  const rendered = await renderSessionTodoListText(sessionId, state);
  const header = 'System Response: Your session TODO list is now fully marked complete (no pending items remain).';
  const instructions = 'You can now summarize what you have done and call attempt_completion with a concise, self-contained final result to end your response.';
  const messageParts = [
    header,
    '',
    instructions,
    '',
    'FINAL TODO LIST',
    '----------------',
    rendered.text
  ];
  return messageParts.join('\n');
}

async function MainAssistant() {
  const userInputEl = document.getElementById('user-input');
  const userInput = userInputEl ? ((userInputEl.value != null ? userInputEl.value : (userInputEl.textContent || '')).trim()) : '';
  const chatMessages = document.getElementById('chat-messages');
  let droppedFiles = window.droppedFiles || [];
  let sessionId = null; // declare early to avoid TDZ and allow early gating/indicators
  let gptSessionId = null; // unique GPT session for this message (separate from chat session)

  // Prevent sending when input is empty and there are no attachments; show warning bar
  if ((!userInput || userInput.length === 0) && (!Array.isArray(droppedFiles) || droppedFiles.length === 0)) {
    try {
      const indicator = document.getElementById('gpt-response-indicator');
      if (indicator) {
        const prevHTML = indicator.innerHTML;
        indicator.innerHTML = `
          <span class="gpt-indicator-text">Hmm your message is empty, might want to type something first...</span>
        `;
        indicator.classList.add('visible');
        setTimeout(() => {
          try {
            indicator.classList.remove('visible');
            // Restore default indicator content
            indicator.innerHTML = `
              <span class="gpt-indicator-text">GPT is responding</span>
              <span class="gpt-indicator-dots">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </span>
            `;
          } catch (_) {}
        }, 2000);
      }
    } catch (_) {}
    return; // Block send
  }

  // Ensure there is an active session with a backing session file.
  try {
    await tabManager.ensureActiveSession();
  } catch (e) {
    console.warn('[MainAssistant] ensureActiveSession failed:', e && e.message ? e.message : e);
  }

  if (!chatMessages) {
    console.warn('[MainAssistant] #chat-messages not found');
    return;
  }

  // Remove placeholders immediately (empty-session message)
  try {
    const placeholders = chatMessages.querySelectorAll('.auaci-empty-session, .auaci-empty-project');
    placeholders.forEach(p => p.remove());
  } catch (_) {}

  // Resolve session id as early as possible (used for gating and indicators)
  try {
    sessionId = await getSessionId();
  } catch (e) {
    console.warn('[MainAssistant] early getSessionId failed:', e && e.message ? e.message : e);
    sessionId = null;
  }

  // Resolve GPT system logic mode from centralized config. Defaults to 'tool-openai'.
  let gptSystemLogic = 'tool-openai';
  let defaultVerbosity = 'high';
  let defaultTemperature = 0;
  let defaultReasoningEffort = 'none';
  try {
    const { ipcRenderer } = require('electron');
    if (ipcRenderer && typeof ipcRenderer.invoke === 'function') {
      const cfg = await ipcRenderer.invoke('get-config');
      const raw = cfg && typeof cfg.gptSystemLogic === 'string' ? cfg.gptSystemLogic.trim() : '';
      if (raw === 'tool-openai' || raw === 'tool-message-self') {
        gptSystemLogic = raw;
      }
      // Advanced defaults
      try {
        const vRaw = cfg && typeof cfg.defaultVerbosity === 'string' ? cfg.defaultVerbosity.toLowerCase().trim() : '';
        if (vRaw === 'low' || vRaw === 'medium' || vRaw === 'high') {
          defaultVerbosity = vRaw;
        }
        let tRaw;
        if (cfg && typeof cfg.defaultTemperature === 'number') tRaw = cfg.defaultTemperature;
        else if (cfg && typeof cfg.defaultTemperature === 'string' && cfg.defaultTemperature.trim()) tRaw = parseFloat(cfg.defaultTemperature);
        if (Number.isFinite(tRaw)) {
          if (tRaw < 0) tRaw = 0;
          if (tRaw > 2) tRaw = 2;
          defaultTemperature = tRaw;
        }
        const rRaw = cfg && typeof cfg.defaultReasoningEffort === 'string' ? cfg.defaultReasoningEffort.toLowerCase().trim() : '';
        if (['none', 'low', 'medium', 'high'].includes(rRaw)) {
          defaultReasoningEffort = rRaw;
        }
      } catch (_) {}
    }
  } catch (_) {}
  const useOpenAiSystemLogic = (gptSystemLogic === 'tool-openai');
  // Track reasoning effort separately so we can forward it via extra_body when using OpenAI system logic.
  let reasoningEffortLevel = 'none';

  // Build a chatEntry for storage (no memory generation)
  const filesForHistory = droppedFiles.map(f => {
    const fileObj = { name: f.name, size: f.size };
    if (f.path) fileObj.path = f.path; else if (f.filePath) fileObj.path = f.filePath; else if (f.filepath) fileObj.path = f.filepath;
    if (typeof f.content === 'string' && f.content.length > 0) fileObj.content = f.content;
    return fileObj;
  });

  // IMMEDIATE SAVE: Save user input immediately to get the correct entry index before rendering
  let currentEntryIndex;
  try {
    currentEntryIndex = await initiateChatEntry(userInput, filesForHistory, sessionId);
    console.log(`[DEBUG] Immediately saved user input at entry index: ${currentEntryIndex}`);
  } catch (err) {
    console.error('[DEBUG] Failed to immediately save user input:', err);
    // Fallback: estimate index from current render state (should be rare)
    currentEntryIndex = Array.isArray(window.allChatEntries) ? window.allChatEntries.length : 0;
  }

  // Render user's message immediately (using the correct entry index)
  const userDiv = document.createElement('div');
  userDiv.className = 'message user-message';
  userDiv.setAttribute('data-entry-index', String(currentEntryIndex));
  const userContent = document.createElement('div');
  userContent.className = 'message-content';
  if (userInput) {
    const textDiv = document.createElement('div');
    textDiv.textContent = userInput;
    userContent.appendChild(textDiv);
  }
  droppedFiles.forEach(file => { userContent.innerHTML += require('../fileUtils').createHistoryFileBox(file); });
  userDiv.appendChild(userContent);
  chatMessages.appendChild(userDiv);

  // Smoothly scroll to bottom so the new user message is visible (gentle)
  try { smoothScrollTo(chatMessages, chatMessages.scrollHeight, { axis: 'y', duration: 140 }); } catch (_) {}

  // Keep legacy chatEntry for backward compatibility with existing code
  const chatEntry = {
    timestamp: new Date().toISOString(),
    user: { text: userInput, files: filesForHistory },
    gpt: ''
  };

  // Clear input and previews
  if (userInputEl) {
    if (userInputEl.value != null) userInputEl.value = '';
    else userInputEl.textContent = '';
    try { userInputEl.dispatchEvent(new Event('input', { bubbles: true })); } catch (_) {}
  }
  window.droppedFiles = [];
  // Clear per-session draft now that message was sent
  try { await require('../incrementalHistoryStorage').clearCurrentInputDraft(); } catch (_) {}

// Thinking indicator helpers: inline inside the GPT message box
  let thinkingStartTs = Date.now();
  let thinkingTimer = null;
  let thinkingDivRef = null;

  function getGptContentElForEntry(entryIndex) {
    if (!shouldRenderForSession(sessionId)) return null;
    const container = document.getElementById('chat-messages');
    if (!container) return null;
    const msg = container.querySelector(`.message.gpt-message[data-entry-index="${String(entryIndex)}"]`);
    if (!msg) return null;
    const contentEl = msg.querySelector('.message-content');
    return contentEl || null;
  }

  const { shouldRenderForSession } = require('../helpers/renderGate');

  function injectThinkingIndicator(targetEl) {
    if (!targetEl) return null;
    if (!shouldRenderForSession(sessionId)) return null;
    // Remove any existing thinking indicators in this target
    try {
      const olds = targetEl.querySelectorAll('.thinking-inline');
      olds.forEach(n => n.remove());
    } catch (_) {}
    try { if (thinkingTimer) clearInterval(thinkingTimer); } catch (_) {}
    const secs = Math.max(0, Math.floor((Date.now() - thinkingStartTs) / 1000));
    const thinkingInline = document.createElement('div');
    thinkingInline.className = 'thinking-inline';
    thinkingInline.innerHTML = `
      <span class=\"typing-text\">GPT is thinking (<span class=\"thinking-seconds\">${secs}</span>s)</span>
      <span class=\"gpt-indicator-dots\">\n        <span class=\"dot\"></span>\n        <span class=\"dot\"></span>\n        <span class=\"dot\"></span>\n      </span>\n    `;
    targetEl.appendChild(thinkingInline);
const thinkingSecondsEl = thinkingInline.querySelector('.thinking-seconds');
    thinkingTimer = setInterval(() => {
      try {
        const s = Math.max(0, Math.floor((Date.now() - thinkingStartTs) / 1000));
        if (thinkingSecondsEl) thinkingSecondsEl.textContent = String(s);
      } catch (_) {}
    }, 1000);
    thinkingDivRef = thinkingInline;
    return thinkingInline;
  }

  function attachThinkingIndicator() {
    if (!shouldRenderForSession(sessionId)) return;
    const content = getGptContentElForEntry(currentEntryIndex);
    if (content) {
      injectThinkingIndicator(content);
      try {
        const container = document.getElementById('chat-messages');
        if (container) smoothScrollTo(container, container.scrollHeight, { axis: 'y', duration: 160 });
      } catch (_) {}
    }
  }

  // Append-only rendering helpers (never wipe previous segments)
  function appendGptHtmlSegment(html) {
    if (!shouldRenderForSession(sessionId)) return;
    const contentEl = getGptContentElForEntry(currentEntryIndex);
    if (!contentEl) return;
    const seg = document.createElement('div');
    seg.className = 'gpt-segment';
    seg.innerHTML = html;
    contentEl.appendChild(seg);
    // Remove any lingering inline thinking indicator when new GPT text arrives
    try { contentEl.querySelectorAll('.thinking-inline').forEach(n => n.remove()); } catch (_) {}
  }

  function appendGptTextSegment(text, toolRuns) {
    if (!shouldRenderForSession(sessionId)) return;
    const parsed = extractToolUsesAndSanitize(text || '');
    const cleanedText = parsed.sanitizedText || '';
    try { marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() }); } catch (_) {}
    const html = marked.parse(cleanedText);
    appendGptHtmlSegment(html);
    try {
      const contentEl = getGptContentElForEntry(currentEntryIndex);
      if (contentEl) enhanceToolPlaceholders(contentEl, parsed.tools, Array.isArray(toolRuns) ? toolRuns : []);
    } catch (_) {}
    try { setupCodeBlockListeners(); } catch (_) {}
  }

  // Render a tool placeholder box directly (for API tool_calls that aren't in text)
  function appendToolPlaceholder(toolName, toolInput, toolRuns) {
    if (!shouldRenderForSession(sessionId)) return;
    const contentEl = getGptContentElForEntry(currentEntryIndex);
    if (!contentEl) return;
    
    // Create a tool placeholder div
    const toolBox = document.createElement('div');
    toolBox.className = 'auaci-tool-box';
    toolBox.setAttribute('data-tool', toolName || 'tool');
    
    const header = document.createElement('div');
    header.className = 'tool-header';
    const nameSpan = document.createElement('span');
    nameSpan.className = 'auaci-tool-name';
    nameSpan.textContent = toolName || 'tool';
    header.appendChild(nameSpan);
    
    const body = document.createElement('div');
    body.className = 'tool-body';
    
    toolBox.appendChild(header);
    toolBox.appendChild(body);
    contentEl.appendChild(toolBox);
    
    // Enhance the placeholder with the tool data
    const toolData = [{ name: toolName, input: toolInput || {} }];
    try {
      enhanceToolPlaceholders(contentEl, toolData, Array.isArray(toolRuns) ? toolRuns : []);
    } catch (_) {}
  }

  function appendGptToolSegment(name, input, resultObj) {
    // Build a minimal <tool_use> block with embedded system response/results
    const lname = String(name || '').toLowerCase();
    const useNewKey = (lname === 'view' || lname === 'grep' || lname === 'context_search' || lname === 'context-search' ||
                        lname === 'find_files' || lname === 'ls' || lname === 'add_todos' || lname === 'create_todo_list' ||
                        lname === 'read_todos' || lname === 'mark_todo_as_done' || lname === 'remove_todos' || lname === 'ask');
    const outObj = { name: name || '', input: input || {} };
    if (useNewKey) outObj.tool_system_results = sanitizeResultForHistory(lname, input || {}, resultObj || {});
    else outObj.tool_system_response = buildToolSystemResponse({ name, input, result: sanitizeResultForHistory(lname, input || {}, resultObj || {}) }, { name, input }, name);
    const block = `<tool_use>${JSON.stringify(outObj)}</tool_use>`;
    appendGptTextSegment(block, []);
  }

  // Create an initial GPT message box placeholder so the thinking indicator is inside it from the start
  const pendingGptDiv = document.createElement('div');
  pendingGptDiv.className = 'message gpt-message';
  pendingGptDiv.setAttribute('data-entry-index', String(currentEntryIndex));
  const pendingGptContent = document.createElement('div');
  pendingGptContent.className = 'message-content';
  pendingGptDiv.appendChild(pendingGptContent);
  chatMessages.appendChild(pendingGptDiv);
  try {
    // record start time for inline indicator
    window.__auaciRespondingStartTime = window.__auaciRespondingStartTime || {};
    if (sessionId) window.__auaciRespondingStartTime[sessionId] = Date.now();
    require('../ui/indicator').showInlineThinking(sessionId);
  } catch (_) {}
  try { smoothScrollTo(chatMessages, chatMessages.scrollHeight, { axis: 'y', duration: 160 }); } catch (_) {}

  // Persist an empty GPT placeholder immediately so session switches can re-render it
  try {
    if (currentEntryIndex !== null) {
      await updateGptResponse(currentEntryIndex, '', false, sessionId);
    }
  } catch (_) {}

  // Build prompt (user content) and system prompt
  let prompt;
  let systemPrompt = '';
  try {
    prompt = await buildPrompt(userInput, droppedFiles, { currentTimestamp: chatEntry.timestamp, recentCount: 2 });

    // 1) Decide if we should encourage using the todo list tool (out-of-band; unrelated session)
    let shouldAddTodoDirective = false;
    try { shouldAddTodoDirective = await shouldSuggestTodosFor(userInput, droppedFiles); } catch (_) { shouldAddTodoDirective = false; }
    if (shouldAddTodoDirective) {
      const directive = 'Please use the todo list tool to track your progress and create a clear todo plan.';
      prompt = `${prompt}\n\n${directive}`;
    }

    // 2) Capture reasoning effort level from the top-bar selector.
    try {
      const src = (typeof window !== 'undefined' && window.selectedReasoningEffort)
        ? window.selectedReasoningEffort
        : (defaultReasoningEffort || 'none');
      const lvl = String(src || 'none').toLowerCase();
      reasoningEffortLevel = lvl || 'none';
    } catch (_) {
      reasoningEffortLevel = defaultReasoningEffort || 'none';
    }

    try {
      systemPrompt = await require('../ai-logic/instructions/logicalizer').getSystemPrompt();
      // Append Project Details section with full project directory path
      let projectPath = null;
      try {
        const { ipcRenderer } = require('electron');
        if (ipcRenderer && typeof ipcRenderer.invoke === 'function') {
          projectPath = await ipcRenderer.invoke('get-project-root');
        }
      } catch (_) {}
      if (!projectPath) {
        try { projectPath = process.cwd(); } catch (_) { projectPath = null; }
      }
      if (projectPath && typeof projectPath === 'string') {
        systemPrompt += `\n\nProject Details\n===============\nProject Directory Path: ${projectPath}`;
      }
    } catch (_) { systemPrompt = ''; }
  } catch (err) {
    console.error('[DEBUG] Error building prompt via ai-logic:', err);
    prompt = userInput;
    for (const file of droppedFiles) {
      const contentSnippet = (typeof file.content === 'string' && file.content.length > 0)
        ? file.content
        : (file.path ? `[file at path: ${file.path}]` : `[no inline content available for ${file.name}]`);
      prompt += (prompt ? '\n\n' : '') +
        `File: ${file.name} (${file.size} bytes)\n\n\`\`\`\n${contentSnippet}\n\`\`\``;
    }
    // Ensure directive is also appended in fallback path
    try {
      // Add todo directive based on classifier in fallback path as well
      let shouldAddTodoDirective = false;
      try { shouldAddTodoDirective = await shouldSuggestTodosFor(userInput, droppedFiles); } catch (_) { shouldAddTodoDirective = false; }
      if (shouldAddTodoDirective) {
        const directive = 'Please use the todo list tool to track your progress and create a clear todo plan.';
        prompt = `${prompt}\n\n${directive}`;
      }
      // Capture reasoning effort level from the top-bar selector in the fallback path as well.
      const src = (typeof window !== 'undefined' && window.selectedReasoningEffort)
        ? window.selectedReasoningEffort
        : (defaultReasoningEffort || 'none');
      const lvl = String(src || 'none').toLowerCase();
      reasoningEffortLevel = lvl || 'none';
    } catch (_) {
      reasoningEffortLevel = defaultReasoningEffort || 'none';
    }
  }

  // Debug: write full request being sent to GPT
  try {
    await writeUserRequest(prompt);
  } catch (err) {
    console.error('[DEBUG] Failed to write userrequest.txt:', err);
  }

  // Non-streaming UI: wait for full model responses and only render via history afterwards
  try {
    // Setup per-session thread state maps
    window.__auaciAbortControllers = window.__auaciAbortControllers || {};
    window.__auaciRequestIds = window.__auaciRequestIds || {};
    window.__auaciStopRequested = window.__auaciStopRequested || {};
    window.__auaciStopRequested[sessionId] = false;

    window.isGptResponding = true;
    try { window.__auaciRespondingSessions = window.__auaciRespondingSessions || {}; if (sessionId) window.__auaciRespondingSessions[sessionId] = true; } catch (_) {}
    try { require('../tabManager').refreshTabs(); } catch (_) {}
    if (shouldRenderForSession(sessionId)) showGptIndicator(sessionId);

    // Helper to make a full request and return the entire response text + tool_calls
    // New API format: tools sent as array, tool_calls returned as separate event
    // Tool results sent back via history array with role: "tool"
    async function completeOnce(userPromptText, opts = {}) {
      const payload = { app_id: 'editor-v1' };
      
      // User prompt (can be null when sending tool results)
      if (userPromptText) {
        payload.user_prompt = userPromptText;
      }
      
      // System prompt
      if (systemPrompt) payload.system_prompt = systemPrompt;
      
      // Session ID
      if (sessionId) payload.session_id = sessionId;
      
      // Include history (for tool results)
      payload.include_history = true;
      
      // Tools array
      const toolDefs = opts.tools || getTools();
      if (Array.isArray(toolDefs) && toolDefs.length > 0) {
        payload.tools = toolDefs;
        payload.tool_choice = opts.toolChoice || 'auto';
      }
      
      // History (for sending tool results back)
      if (Array.isArray(opts.history) && opts.history.length > 0) {
        payload.history = opts.history;
      }
      
      let selectedModel = null;
      try { selectedModel = require('../modelSelector').getSelectedModel(); } catch (_) { selectedModel = null; }

      // Prepare abort + stop control for this request
      const abortCtrl = new AbortController();
      window.__auaciAbortControllers[sessionId] = abortCtrl;
      window.__auaciStopRequested[sessionId] = false;
      window.__auaciRequestIds[sessionId] = null;

      // Ensure stop button posts to server and aborts local read
      try {
        const btn = document.getElementById('stop-response-btn');
        if (btn) {
          btn.disabled = false;
          btn.onclick = async () => {
            try { window.__auaciStopRequested[sessionId] = true; } catch (_) {}
            try { if (window.__auaciRequestIds[sessionId]) await stopRequest(window.__auaciRequestIds[sessionId]); } catch (_) {}
            try { const ac = window.__auaciAbortControllers[sessionId]; if (ac) ac.abort(); } catch (_) {}
            try { btn.disabled = true; } catch (_) {}
          };
        }
      } catch (_) {}

      let textOut = '';
      let toolCalls = null;
      let responseSessionId = null;
      let responseRequestId = null;
      
      try {
        const streamOpts = {
          model: selectedModel || undefined,
          signal: abortCtrl.signal,
          tools: payload.tools,
          toolChoice: payload.tool_choice,
          onStart: (obj) => { 
            try { 
              window.__auaciRequestIds[sessionId] = obj && obj.request_id;
              responseSessionId = obj && obj.session_id;
              responseRequestId = obj && obj.request_id;
            } catch (_) {} 
          }
        };

        // Forward reasoning and other advanced options via extra_body when talking to the proxy/Poe.
        const extraBody = {};
        // Reasoning effort: prefer current toolbar choice, else config default.
        const selectedEffort = (typeof reasoningEffortLevel === 'string' && reasoningEffortLevel)
          ? reasoningEffortLevel
          : (defaultReasoningEffort || 'none');
        const effEffort = String(selectedEffort || 'none').toLowerCase();
        if (effEffort && effEffort !== 'none') {
          extraBody.reasoning_effort = effEffort;
        }
        // Verbosity and temperature from config defaults with safe fallbacks.
        let v = (defaultVerbosity || 'high').toLowerCase();
        if (!['low', 'medium', 'high'].includes(v)) v = 'high';
        extraBody.verbosity = v;

        let t = Number.isFinite(defaultTemperature) ? defaultTemperature : 0;
        if (!Number.isFinite(t)) t = 0;
        if (t < 0) t = 0;
        if (t > 2) t = 2;
        extraBody.temperature = t;

        extraBody.seed = 42;
        extraBody.max_completion_tokens = 200000;
        extraBody.parallel_tool_calls = false;
        extraBody.response_format = { type: 'text' };
        streamOpts.extraBody = extraBody;

        for await (const chunk of streamChat(payload, streamOpts)) {
          // CHECK STOP STATE: if session has been stopped, stop processing chunks
          try {
            const stoppedSessions = window.__auaciStoppedSessions || {};
            if (stoppedSessions[sessionId]) {
              console.log('[MainAssistant] Stop state detected - ending stream processing');
              break;
            }
          } catch (_) {}
          
          // Handle content event
          if (chunk && chunk.event === 'content' && typeof chunk.content === 'string') {
            textOut += chunk.content;
          }
          // Handle legacy content format
          else if (chunk && typeof chunk.content === 'string' && !chunk.event) {
            textOut += chunk.content;
          }
          // Handle tool_calls event
          else if (chunk && chunk.event === 'tool_calls' && Array.isArray(chunk.tool_calls)) {
            toolCalls = chunk.tool_calls;
            console.log(`[completeOnce] Received ${toolCalls.length} tool_calls from API`);
          }
          // Handle errors
          else if (chunk && chunk.error) {
            throw new Error(String(chunk.error));
          }
          // Handle cancellation
          else if (chunk && chunk.status === 'cancelled') {
            break;
          }
        }
      } catch (e) {
        const msg = (e && e.name === 'AbortError') ? 'Request aborted' : (e && e.message ? e.message : String(e));
        console.warn('[completeOnce] stream aborted or failed:', msg);
        // treat as cancelled; out may be partial
      } finally {
        // clear active request id
        try { window.__auaciRequestIds[sessionId] = null; } catch (_) {}
        try { delete window.__auaciAbortControllers[sessionId]; } catch (_) {}
      }

      return { 
        text: textOut || '', 
        tool_calls: toolCalls,
        session_id: responseSessionId,
        request_id: responseRequestId
      };
    }
    
    // Helper to convert API tool_calls to internal tool format
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
          id: tc.id,
          name: fn.name || '',
          input: args,
          raw: JSON.stringify(tc)
        };
      });
    }
    
    // Helper to build tool result history entry
    function buildToolResultHistory(toolCallId, toolName, result) {
      const content = typeof result === 'string' ? result : JSON.stringify(result);
      const truncatedContent = truncateToolContent(content, 15 * 1024); // 15KB limit
      
      return {
        role: 'tool',
        tool_call_id: toolCallId,
        name: toolName,
        content: truncatedContent
      };
    }

// First full response using the original prompt
    let response = await completeOnce(prompt);
    let fullResponse = response.text || '';
    let apiToolCalls = response.tool_calls || null;
    let lastModelReply = fullResponse;
    
    // NEW HISTORY FORMAT: Use SEGMENTED GPT entry
    // This separates GPT text from tool calls for proper rendering
    let gptEntry = createGptEntry();
    
    // Legacy string for backward compatibility during transition
    let fullResponseForHistory = fullResponse;
    
    console.log(`[DEBUG] Initial fullResponse length: ${fullResponse.length}`);
    
    // Convert API tool_calls to internal format, or fall back to parsing <tool_use> blocks
    let initialTools = [];
    if (apiToolCalls && apiToolCalls.length > 0) {
      initialTools = convertApiToolCalls(apiToolCalls);
      
      // NEW FORMAT: Add text segment (without tool JSON), then add tool segments
      const cleanText = removeToolUseBlocks(fullResponse);
      if (cleanText) {
        addTextSegment(gptEntry, cleanText);
      }
      for (const tc of initialTools) {
        addToolSegment(gptEntry, tc.id, tc.name, tc.input);
      }
      
      console.log(`[DEBUG] Initial response contains ${initialTools.length} tool_calls from API:`, initialTools.map(t => t.name));
    } else {
      // Fallback: parse <tool_use> blocks from text (for backward compatibility)
      let parsedInitial = extractToolUsesAndSanitize(fullResponse);
      initialTools = Array.isArray(parsedInitial.tools) ? parsedInitial.tools : [];
      
      // NEW FORMAT: Add text segment, then tool segments
      const cleanText = parsedInitial.sanitizedText || removeToolUseBlocks(fullResponse);
      if (cleanText) {
        addTextSegment(gptEntry, cleanText);
      }
      for (const t of initialTools) {
        addToolSegment(gptEntry, t.id || `call_${Date.now()}`, t.name, t.input);
      }
      
      console.log(`[DEBUG] Initial response contains ${initialTools.length} tools from text:`, initialTools.map(t => t.name));
    }

    // Early finalize: if the ONLY tool is attempt_completion, first check the session TODO list.
    try {
      const normalizeToolName = (n) => {
        if (!n || typeof n !== 'string') return '';
        const s = n.trim();
        if (/^attempt[-_]?completion$/i.test(s)) return 'attempt_completion';
        return s;
      };
      if (Array.isArray(initialTools) && initialTools.length === 1 && normalizeToolName(initialTools[0].name) === 'attempt_completion') {
        console.log('[DEBUG] Early attempt_completion detected - checking TODO list before finalizing');
        let allowFinalize = false;
        try {
          const guardMsg = await buildTodoGuardMessageForAttemptCompletion(sessionId);
          if (guardMsg && guardMsg.trim()) {
            console.log('[DEBUG] Active TODO list found - rejecting early attempt_completion and requesting continuation instead');
            const followUpResp = await completeOnce(guardMsg);
            fullResponse = followUpResp.text || '';
            apiToolCalls = followUpResp.tool_calls || null;
            fullResponseForHistory = fullResponse;
            lastModelReply = fullResponse;
            // Update initialTools from new response
            if (apiToolCalls && apiToolCalls.length > 0) {
              initialTools = convertApiToolCalls(apiToolCalls);
            } else {
              let parsedInitial = extractToolUsesAndSanitize(fullResponse || '');
              initialTools = Array.isArray(parsedInitial.tools) ? parsedInitial.tools : [];
            }
            console.log(`[DEBUG] Guard follow-up initial tools count: ${initialTools.length}`);
          } else {
            allowFinalize = true;
          }
        } catch (guardErr) {
          console.warn('[DEBUG] Early attempt_completion TODO guard failed; allowing finalize as fallback:', guardErr && guardErr.message ? guardErr.message : guardErr);
          allowFinalize = true;
        }

        if (allowFinalize) {
          console.log('[DEBUG] Early attempt_completion allowed - persisting and rendering immediately');
          // Persist
          try {
            const embeddedForStorage = embedToolResultsInGptResponse(fullResponseForHistory, []);
            if (currentEntryIndex !== null) await updateGptResponse(currentEntryIndex, embeddedForStorage, false, sessionId);
            fullResponseForHistory = embeddedForStorage;
          } catch (e) { console.warn('[DEBUG] Early attempt_completion persist failed:', e && e.message ? e.message : e); }
          // Render inline immediately
          try {
            const embeddedForRender = embedToolResultsForRendering(fullResponseForHistory, [], { mode: 'live', skipCleanup: true });
            if (shouldRenderForSession(sessionId)) {
renderLatestGptMessageInline(embeddedForRender, [], currentEntryIndex);
              try { animateLatestGptMessage(); } catch (_) {}
              try { maybeSmoothScrollToBottomIfNeeded(); } catch (_) {}
            }
          } catch (e) { console.warn('[DEBUG] Early attempt_completion render failed:', e && e.message ? e.message : e); }
          // Finalize entry and return
          try {
            if (currentEntryIndex !== null) {
              await finalizeChatEntry(currentEntryIndex, fullResponseForHistory, sessionId);
            } else {
              chatEntry.gpt = fullResponseForHistory;
              await saveChatHistory(chatEntry, sessionId);
            }
          } catch (e) { console.warn('[DEBUG] Early attempt_completion finalize failed:', e && e.message ? e.message : e); }
          // Cleanup UI state
          try { if (thinkingTimer) clearInterval(thinkingTimer); } catch (_) {}
          try { if (thinkingDivRef && typeof thinkingDivRef.remove === 'function') thinkingDivRef.remove(); } catch (_) {}
          try { const btn = document.getElementById('stop-response-btn'); if (btn) { btn.disabled = true; btn.onclick = null; } } catch (_) {}
          if (shouldRenderForSession(sessionId)) hideGptIndicator(sessionId);
          try { if (window.__auaciRespondingSessions) window.__auaciRespondingSessions[sessionId] = false; } catch (_) {}
          try { require('../tabManager').refreshTabs(); } catch (_) {}
          window.isGptResponding = false;
          return;
        }
      }
    } catch (e) { console.warn('[DEBUG] Early attempt_completion check failed:', e && e.message ? e.message : e); }

    // Render segment 1 immediately (append-only): render just the first model reply
    try {
      if (currentEntryIndex !== null) {
        // NEW HISTORY FORMAT: Use serialized segmented gptEntry
        const serializedForStorage = serializeGptEntry(gptEntry);
        const hasSegments = gptEntry.segments && gptEntry.segments.length > 0;
        const contentToSave = hasSegments ? serializedForStorage : embedToolResultsInGptResponse(fullResponseForHistory, []);
        await updateGptResponse(currentEntryIndex, contentToSave, false, sessionId);
      }
      // Render the text part only - tool placeholders will be rendered during tool execution
      appendGptTextSegment(lastModelReply, []);
      
      try { animateLatestGptMessage(); } catch (_) {}
      try { maybeSmoothScrollToBottomIfNeeded(); } catch (_) {}
    } catch (segErr) {
      console.error('[DEBUG] Failed to render first segment:', segErr);
    }

    // Decide whether to continue based on tools from the initial response
    // Use initialTools (from API tool_calls or parsed from text) instead of re-parsing
    let continueChain = false;
    try {
      // initialTools was already populated from apiToolCalls or parsed from text
      console.log(`[DEBUG] continueChain check - initialTools count: ${initialTools.length}`);
      console.log(`[DEBUG] continueChain check - tools:`, initialTools.map(t => t.name));
      if (Array.isArray(initialTools) && initialTools.length > 0) {
        // Any tool except attempt_completion requires a system response
        continueChain = initialTools.some(t => t && typeof t.name === 'string' && !/^attempt[_-]?completion$/i.test(t.name));
      }
      console.log(`[DEBUG] continueChain result: ${continueChain}`);
    } catch (_) { continueChain = false; }

    if (continueChain) {
      attachThinkingIndicator();
    } else {
      // No further action; finalize and cleanup
      try { if (thinkingTimer) clearInterval(thinkingTimer); } catch (_) {}
      try { if (thinkingDivRef && typeof thinkingDivRef.remove === 'function') thinkingDivRef.remove(); } catch (_) {}
      try { const btn = document.getElementById('stop-response-btn'); if (btn) { btn.disabled = true; btn.onclick = null; } } catch (_) {}
      if (shouldRenderForSession(sessionId)) hideGptIndicator(sessionId);
      try { if (window.__auaciRespondingSessions) window.__auaciRespondingSessions[sessionId] = false; } catch (_) {}
      window.isGptResponding = false;
      // Finalize entry
      try {
        // NEW HISTORY FORMAT: Use serialized segmented gptEntry
        const serializedFinal = serializeGptEntry(gptEntry);
        const hasSegments = gptEntry.segments && gptEntry.segments.length > 0;
        const finalContent = hasSegments ? serializedFinal : embedToolResultsInGptResponse(fullResponseForHistory, []);
        if (currentEntryIndex !== null) {
          await finalizeChatEntry(currentEntryIndex, finalContent, sessionId);
        } else {
          chatEntry.gpt = finalContent;
          await saveChatHistory(chatEntry, sessionId);
        }
      } catch (finalErr) { console.error('[DEBUG] Finalize error:', finalErr); }
      // Render final message inline and finish
      try {
        const embeddedNow = embedToolResultsInGptResponse(fullResponseForHistory, []);
        if (shouldRenderForSession(sessionId)) {
renderLatestGptMessageInline(embeddedNow, [], currentEntryIndex);
          try { animateLatestGptMessage(); } catch (_) {}
          try { maybeSmoothScrollToBottomIfNeeded(); } catch (_) {}
        }
      } catch (_) {}
      return;
    }

    // Tool-use processing loop: parse tool calls, execute, send results back (non-stream), until none remain
    let loopGuard = 0;
    const toolRuns = [];
    let finalized = false;
    // Queue to handle multiple tools emitted in a single GPT reply (process one per turn)
    const pendingToolsQueue = [];

while (loopGuard < 100) { // safety to avoid infinite loops (raised from 12)
      // CHECK IMMEDIATE STOP: if user clicked stop button, exit tool processing loop
      try {
        const stoppedSessions = window.__auaciStoppedSessions || {};
        if (stoppedSessions[sessionId]) {
          console.log('[MainAssistant] Stop state detected - exiting tool processing loop');
          break;
        }
      } catch (_) {}
      if (window.stopRequested) break;
      loopGuard++;
      
      // Reset processed tool IDs for this iteration (avoid duplicates within same reply only)
      const processedTools = new Set();
      
      // Source tools for this iteration: prefer queued tools first
      let tools = [];

      // Helper: streaming command execution (run_command/bash)
      async function executeStreamingCommand(toolName, input, ephemeralIndexRef) {
        return new Promise((resolveStreaming) => {
          try {
            const cmd = input && typeof input.command === 'string' ? input.command : '';
            const cwd = input && input.path ? (require('path').isAbsolute(input.path) ? input.path : require('path').join(process.cwd(), input.path)) : process.cwd();
            const child = spawn(cmd, { cwd, shell: true, env: process.env });

            // Create ephemeral run record wired for renderer
            const runRec = {
              name: toolName,
              input: input || {},
              result: { finished: { command: cmd, output: '', exit_code: undefined, new_pwd: cwd } },
              success: undefined,
              summary: { program: (cmd.split(/\s+/)[0] || ''), exit_code: undefined }
            };
            // Assign a stable uid for this run so DOM mapping is robust
            const uid = `run-${Date.now()}-${Math.random().toString(36).slice(2,6)}`;
            runRec.__uid = uid;
            const idx = toolRuns.length; // append at end
            toolRuns.push(runRec);
            if (ephemeralIndexRef) ephemeralIndexRef.index = idx;
            // Mount a minimal placeholder synchronously so fastAppend can update it immediately
            try {
              const contentEl = getGptContentElForEntry(currentEntryIndex);
              if (contentEl) {
                // prefer an existing .auaci-tool-box without uid or append a new one
                let box = contentEl.querySelector('.auaci-tool-box[data-tool-uid="' + uid + '"]');
                if (!box) {
                  // Try to find the next unclaimed placeholder
                  const unclaimed = Array.from(contentEl.querySelectorAll('.auaci-tool-box')).find(b => !b.getAttribute('data-tool-uid'));
                  if (unclaimed) {
                    box = unclaimed;
                    box.setAttribute('data-tool-uid', uid);
                  } else {
                    // Create a new placeholder at the end
                    try {
                      box = document.createElement('div');
                      box.className = 'auaci-tool-box';
                      box.setAttribute('data-tool-uid', uid);
                      // Minimal header/body structure expected by renderer
                      const header = document.createElement('div'); header.className = 'tool-header';
                      const nameSpan = document.createElement('span'); nameSpan.className = 'auaci-tool-name'; nameSpan.textContent = toolName;
                      header.appendChild(nameSpan);
                      const body = document.createElement('div'); body.className = 'tool-body';
                      // Terminal minimal structure for run_command/bash
                      body.innerHTML = '<div class="tool-line">' + pendingIconSvg() + '<span class="tool-sub">Pending</span></div>';
                      box.appendChild(header);
                      box.appendChild(body);
                      contentEl.appendChild(box);
                    } catch (_) {}
                  }
                }
              }
            } catch (_) {}

            const MAX_OUTPUT_CHARS = 2000;
            let floodTerminated = false;
            let userTerminated = false;

            // State for throttling and fast-path DOM updates
            let didInitialRender = false;
            let lastPersistTs = 0;
            let carryPartial = '';

            const stripAuaciChunk = (text) => {
              try {
                const combined = String(text || '');
                const parts = (carryPartial + combined).split(/\r?\n/);
                // keep last as carry if not newline-terminated
                const endsWithNl = /\r?\n$/.test(carryPartial + combined);
                carryPartial = endsWithNl ? '' : (parts.pop() || '');
                const filtered = parts.filter(l => !l.includes('.auaci')).join('\n');
                return filtered + (endsWithNl ? '\n' : '');
              } catch (_) { return String(text || ''); }
            };

            // Allow UI to stop this command
            const onStop = (ev) => {
              try {
                const d = ev && ev.detail ? ev.detail : {};
                // Relaxed: stop if entry matches (we run one streaming command at a time)
                if (String(d.entry_index) === String(currentEntryIndex)) {
                  userTerminated = true;
                  try { child.kill('SIGTERM'); } catch (_) {}
                }
              } catch (_) {}
            };
            try { window.addEventListener('auaci:stop-command', onStop); } catch (_) {}

            // Heavy update: embed + persist + full re-render (throttled)
            const heavyUpdate = async () => {
              try {
                const now = Date.now();
                if (now - lastPersistTs < 1000 && didInitialRender) return; // persist at most every 1s during streaming
                lastPersistTs = now;
                const embeddedForStorage = embedToolResultsInGptResponse(fullResponseForHistory, toolRuns);
                if (currentEntryIndex !== null) await updateGptResponse(currentEntryIndex, embeddedForStorage, false, sessionId);
                const embeddedForRender = embedToolResultsForRendering(fullResponseForHistory, toolRuns, { mode: 'live', skipCleanup: true });
                const parsedForAll = extractToolUsesAndSanitize(embeddedForRender || '');
                if (!shouldRenderForSession(sessionId)) return; // skip UI updates for non-active sessions
                const contentEl = getGptContentElForEntry(currentEntryIndex);
                if (contentEl) enhanceToolPlaceholders(contentEl, parsedForAll.tools, toolRuns);
                didInitialRender = true;
              } catch (_) {}
            };
            // Persist-only (no UI) immediate save to ensure history.json is up-to-date
            const persistOnlyImmediate = async () => {
              try {
                const embeddedForStorage = embedToolResultsInGptResponse(fullResponseForHistory, toolRuns);
                if (currentEntryIndex !== null) await updateGptResponse(currentEntryIndex, embeddedForStorage, false, sessionId);
                fullResponseForHistory = embeddedForStorage;
              } catch (_) {}
            };
            const scheduleHeavyUpdate = () => { try { setTimeout(() => { heavyUpdate().catch(()=>{}); }, 0); } catch (_) {} };

            // Fast path: update only the terminal pre element
            const fastAppend = (appendText) => {
              try {
                if (!shouldRenderForSession(sessionId)) return;
                const contentEl = getGptContentElForEntry(currentEntryIndex);
                if (!contentEl) return;
                // Prefer uid-based lookup for robustness
                const uid = runRec && runRec.__uid ? String(runRec.__uid) : null;
                let box = null;
                if (uid && contentEl) box = contentEl.querySelector(`.auaci-tool-box[data-tool-uid="${uid}"]`);
                // Fallback to index alignment if uid not found
                if (!box) {
                  const boxes = contentEl.querySelectorAll('.auaci-tool-box');
                  box = boxes && boxes[idx] ? boxes[idx] : null;
                }
                if (!box) return;
                const pre = box.querySelector('.tool-terminal pre.terminal-output');
                if (!pre) return;
                const prev = pre.textContent || '';
                const next = prev + appendText;
                // Clamp to 30 lines like renderer
                const lines = next.split(/\r?\n/);
                const total = lines.length;
                const shown = Math.min(30, total);
                pre.textContent = lines.slice(0, shown).join('\n');
                // Update truncation note if present
                const scroll = box.querySelector('.terminal-scroll');
                if (scroll) {
                  try {
                    const nearBottom = (scroll.scrollHeight - (scroll.scrollTop + scroll.clientHeight)) <= 4;
                    if (nearBottom) scroll.scrollTop = scroll.scrollHeight;
                  } catch (_) {}
                }
              } catch (_) {}
            };

            // Write handler for stdout/stderr
            const onData = (buf) => {
              try {
                const raw = (typeof buf === 'string') ? buf : buf.toString('utf8');
                const s = stripAuaciChunk(raw);
                if (s) runRec.result.finished.output += s;
                if (!floodTerminated && runRec.result.finished.output.length > MAX_OUTPUT_CHARS) {
                  floodTerminated = true;
                  try {
                    runRec.result.flood_notice = 'Command was producgin way to many outputs system blocked further flood of information to prevent lag spikes.';
                    runRec.result.terminated_by_system_flood = true;
                  } catch (_) {}
                  try { child.kill('SIGTERM'); } catch (_) {}
                  // Fallback hard kill if process doesn\'t exit quickly
                  try { setTimeout(() => { try { child.kill('SIGKILL'); } catch (_) {} }, 1500); } catch (_) {}
                  // Defer heavy update so UI thread doesn\'t stall
                  scheduleHeavyUpdate();
                }
                if (!didInitialRender) {
                  // First mount: schedule to avoid blocking UI
                  scheduleHeavyUpdate();
                } else {
                  // Fast path: only append to terminal DOM
                  fastAppend(s);
                }
                // Throttled persist (deferred)
                scheduleHeavyUpdate();
              } catch (_) {}
            };
            child.stdout?.on('data', onData);
            child.stderr?.on('data', onData);

            child.on('close', async (code) => {
              try { runRec.result.finished.exit_code = typeof code === 'number' ? code : 0; } catch (_) {}
              if (floodTerminated) {
                try {
                  runRec.result.flood_notice = 'Command was producgin way to many outputs system blocked further flood of information to prevent lag spikes.';
                  runRec.result.terminated_by_system_flood = true;
                } catch (_) {}
              }
              if (userTerminated) {
                try {
                  runRec.result.user_stop_message = 'User doesnt want the command to be ran because of a reason.';
                  runRec.result.terminated_by_user = true;
                } catch (_) {}
              }
              try { runRec.success = (runRec.result && runRec.result.finished && typeof runRec.result.finished.exit_code === 'number') ? (runRec.result.finished.exit_code === 0) : undefined; } catch (_) {}
              // Minimal DOM finalize: hide Stop, show flood notice if any
              try {
                if (shouldRenderForSession(sessionId)) {
                  // Prefer uid-based lookup for robustness
                  const uid = runRec && runRec.__uid ? String(runRec.__uid) : null;
                  let box = null;
                  if (uid && contentEl) box = contentEl.querySelector(`.auaci-tool-box[data-tool-uid="${uid}"]`);
                  if (!box) {
                    const boxes = contentEl ? contentEl.querySelectorAll('.auaci-tool-box') : [];
                    box = boxes && boxes[idx] ? boxes[idx] : null;
                  }
                  if (box) {
                    const btn = box.querySelector('.cmd-stop-btn'); if (btn) { btn.disabled = true; btn.textContent = 'Stopped'; }

                    if (runRec.result.terminated_by_system_flood && typeof runRec.result.flood_notice === 'string') {
                      const scroll = box.querySelector('.terminal-scroll');
                      if (scroll && !scroll.querySelector('.terminal-flood-notice')) {
                        const div = document.createElement('div');
                        div.className = 'terminal-line terminal-flood-notice';
                        div.setAttribute('style','white-space:pre; background:#000; color:#fbbf24; margin-top:6px;');
                        div.textContent = String(runRec.result.flood_notice);
                        scroll.appendChild(div);
                      }
                    }
                  }
                }
              } catch (_) {}
              // Ensure tool result is saved into history.json before proceeding
              try { await persistOnlyImmediate(); } catch (_) {}
              // Defer heavy persist+render to keep UI responsive
              scheduleHeavyUpdate();
              try { window.removeEventListener('auaci:stop-command', onStop); } catch (_) {}
              resolveStreaming({ finished: { command: cmd, output: runRec.result.finished.output, exit_code: runRec.result.finished.exit_code, new_pwd: cwd },
                                flood_notice: runRec.result.flood_notice, terminated_by_system_flood: !!runRec.result.terminated_by_system_flood,
                                user_stop_message: runRec.result.user_stop_message, terminated_by_user: !!runRec.result.terminated_by_user });
            });
          } catch (e) {
            resolveStreaming({ error: String(e && e.message ? e.message : e) });
          }
        });
      }


      // Interactive Ask tool: render UI and wait for user to press Answer
      async function executeAskInteractive(toolObj, ephemeralIndexRef) {
        const tInput = (toolObj && toolObj.input) || {};
        const question = typeof tInput.question === 'string' ? tInput.question : '';
        const mode = typeof tInput.mode === 'string' ? String(tInput.mode).toLowerCase() : 'free';
        const options = Array.isArray(tInput.options) ? tInput.options : [];

        // Create ephemeral run record and update UI similar to streaming tools
        const runRec = {
          name: 'ask',
          input: { question, mode, options },
          result: { waiting: true, question, mode, options },
          success: undefined,
          summary: { question: question ? (question.length > 80 ? question.slice(0,80) + '…' : question) : '' }
        };
        const idx = toolRuns.length; toolRuns.push(runRec);
        if (ephemeralIndexRef) ephemeralIndexRef.index = idx;

        const doUpdate = async () => {
          try {
            const embeddedForStorage = embedToolResultsInGptResponse(fullResponseForHistory, toolRuns);
            // Persist embedded content and also update our working copy so subsequent steps append to the latest
            if (currentEntryIndex !== null) await updateGptResponse(currentEntryIndex, embeddedForStorage, false, sessionId);
            fullResponseForHistory = embeddedForStorage;
            const embeddedForRender = embedToolResultsForRendering(fullResponseForHistory, toolRuns, { mode: 'live', skipCleanup: true });
            const parsedForAll = extractToolUsesAndSanitize(embeddedForRender || '');
            if (!shouldRenderForSession(sessionId)) return; // skip UI updates for non-active sessions
            const contentEl = getGptContentElForEntry(currentEntryIndex);
            if (contentEl) enhanceToolPlaceholders(contentEl, parsedForAll.tools, toolRuns);
            return embeddedForStorage;
          } catch (_) { return null; }
        };
        await doUpdate();

        // Wait for a DOM event from the Ask tool UI
        return await new Promise((resolve) => {
          const toolIndex = (typeof toolObj.index !== 'undefined') ? String(toolObj.index) : null;
          const onAnswered = async (ev) => {
            try {
              const d = ev && ev.detail ? ev.detail : {};
              if (!d || (toolIndex !== null && String(d.tool_index) !== String(toolIndex))) return;
              // Build result object
              const ansText = (typeof d.user_input === 'string') ? d.user_input : '';
              const selectedValues = Array.isArray(d.selected_values) ? d.selected_values : [];
              const out = {
                question: question,
                mode: mode,
                options: options,
                answer: {
                  text: ansText,
                  selected: selectedValues
                }
              };
              // Update run record and UI
              try {
                runRec.result = { question, mode, options, answer_text: ansText, selected: selectedValues, waiting: false, answered: true };
                runRec.success = true;
                const embeddedNow = await doUpdate();
                // Extra safety: Re-embed using the freshest saved history, then persist again
                try {
                  const inc = require('../incrementalHistoryStorage');
                  const hist = await inc.getCurrentChatHistory();
                  let freshText = '';
                  try {
                    if (hist && Array.isArray(hist.chat) && typeof currentEntryIndex === 'number') {
                      const ent = hist.chat[currentEntryIndex];
                      if (ent && typeof ent.gpt === 'string') freshText = ent.gpt;
                    }
                  } catch (_) { freshText = ''; }
                  const toEmbed = freshText && freshText.trim() ? freshText : (embeddedNow || fullResponseForHistory || '');
                  const reEmbedded = embedToolResultsInGptResponse(toEmbed, toolRuns);
                  if (currentEntryIndex !== null) await updateGptResponse(currentEntryIndex, reEmbedded, false, sessionId);
                  fullResponseForHistory = reEmbedded;
                } catch (_) {}
              } catch (_) {}
              window.removeEventListener('auaci:ask-answered', onAnswered);
              resolve(out);
            } catch (e) {
              // Resolve gracefully even on unexpected errors
              window.removeEventListener('auaci:ask-answered', onAnswered);
              resolve({ question, mode, options, answer: { text: '', selected: [] }, error: String(e && e.message ? e.message : e) });
            }
          };
          window.addEventListener('auaci:ask-answered', onAnswered);
        });
      }

      // Interactive delete tool: render UI and wait for user confirmation
      async function executeDeleteWithPermissionInteractive(toolObj, ephemeralIndexRef) {
        const tInput = (toolObj && toolObj.input) || {};
        const rawItems = Array.isArray(tInput.items)
          ? tInput.items
          : (Array.isArray(tInput.paths) ? tInput.paths : []);
        const reason = typeof tInput.reason === 'string' ? tInput.reason : '';
        const baseDir = typeof tInput.base_dir === 'string' ? tInput.base_dir : undefined;

        const items = [];
        for (const raw of rawItems) {
          if (!raw) continue;
          if (typeof raw === 'string') {
            items.push({ path: raw, selected: true });
          } else if (typeof raw === 'object') {
            const p = (raw.path || raw.target || raw.file || '').toString().trim();
            if (!p) continue;
            const selected = (typeof raw.selected === 'boolean') ? !!raw.selected : true;
            const kind = raw.kind || null;
            items.push({ path: p, selected, kind });
          }
        }

        if (!items.length) {
          return {
            mode: 'preview',
            waiting: false,
            items: [],
            summary: {
              requested_count: 0,
              to_delete_count: 0,
              deleted_count: 0,
              skipped_count: 0,
              missing_count: 0,
              failed_count: 0,
              base_dir: baseDir,
              reason,
            },
          };
        }

        // Build preview state for UI; classification and actual deletion are done in the handler on execute.
        const previewItems = items.map((it) => ({
          requested_path: it.path,
          full_path: it.path,
          selected: it.selected !== false,
          kind: it.kind || null,
          status: 'pending',
          error: undefined,
        }));

        const previewSummary = {
          requested_count: previewItems.length,
          to_delete_count: previewItems.filter(i => i.selected).length,
          deleted_count: 0,
          skipped_count: 0,
          missing_count: 0,
          failed_count: 0,
          base_dir: baseDir,
          reason,
        };

        const runRec = {
          name: 'delete_file_folder_with_permission',
          input: {
            items: previewItems.map(it => ({ path: it.requested_path || it.full_path, selected: it.selected, kind: it.kind })),
            base_dir: baseDir,
            reason,
          },
          result: {
            mode: 'preview',
            waiting: true,
            items: previewItems,
            summary: previewSummary,
          },
          success: undefined,
          summary: { requested_count: previewSummary.requested_count },
        };
        const idx = toolRuns.length; toolRuns.push(runRec);
        if (ephemeralIndexRef) ephemeralIndexRef.index = idx;

        const doUpdate = async () => {
          try {
            const embeddedForStorage = embedToolResultsInGptResponse(fullResponseForHistory, toolRuns);
            if (currentEntryIndex !== null) await updateGptResponse(currentEntryIndex, embeddedForStorage, false, sessionId);
            fullResponseForHistory = embeddedForStorage;
            const embeddedForRender = embedToolResultsForRendering(fullResponseForHistory, toolRuns, { mode: 'live', skipCleanup: true });
            const parsedForAll = extractToolUsesAndSanitize(embeddedForRender || '');
            if (!shouldRenderForSession(sessionId)) return;
            const contentEl = getGptContentElForEntry(currentEntryIndex);
            if (contentEl) enhanceToolPlaceholders(contentEl, parsedForAll.tools, toolRuns);
            return embeddedForStorage;
          } catch (_) { return null; }
        };
        await doUpdate();

        // Wait for DOM event with the user's final selection
        return await new Promise((resolve) => {
          const toolIndex = (typeof toolObj.index !== 'undefined') ? String(toolObj.index) : null;
          const onConfirmed = async (ev) => {
            try {
              const d = ev && ev.detail ? ev.detail : {};
              if (!d || (toolIndex !== null && String(d.tool_index) !== String(toolIndex))) return;
              const uiItems = Array.isArray(d.items) ? d.items : [];

              let resultObj;
              try {
                const { executeCommand } = require('../commands/executor');
                resultObj = await executeCommand({
                  name: 'delete_file_folder_with_permission',
                  input: { items: uiItems, base_dir: baseDir, reason, mode: 'execute' },
                  raw: '',
                });
              } catch (execErr) {
                resultObj = {
                  error: String(execErr && execErr.message ? execErr.message : execErr),
                  items: uiItems,
                  mode: 'execute',
                };
              }

              try {
                if (resultObj && typeof resultObj.waiting === 'undefined') resultObj.waiting = false;
                runRec.result = resultObj;
                runRec.success = !resultObj.error;
                if (resultObj.summary && typeof resultObj.summary === 'object') runRec.summary = resultObj.summary;
                const embeddedNow = await doUpdate();
                // Extra safety: re-embed using freshest saved history
                try {
                  const inc = require('../incrementalHistoryStorage');
                  const hist = await inc.getCurrentChatHistory();
                  let freshText = '';
                  try {
                    if (hist && Array.isArray(hist.chat) && typeof currentEntryIndex === 'number') {
                      const ent = hist.chat[currentEntryIndex];
                      if (ent && typeof ent.gpt === 'string') freshText = ent.gpt;
                    }
                  } catch (_) { freshText = ''; }
                  const toEmbed = freshText && freshText.trim() ? freshText : (embeddedNow || fullResponseForHistory || '');
                  const reEmbedded = embedToolResultsInGptResponse(toEmbed, toolRuns);
                  if (currentEntryIndex !== null) await updateGptResponse(currentEntryIndex, reEmbedded, false, sessionId);
                  fullResponseForHistory = reEmbedded;
                } catch (_) {}
              } catch (_) {}

              window.removeEventListener('auaci:delete-with-permission-confirm', onConfirmed);
              resolve(resultObj);
            } catch (e) {
              window.removeEventListener('auaci:delete-with-permission-confirm', onConfirmed);
              resolve({ error: String(e && e.message ? e.message : e), items: [], mode: 'execute', waiting: false });
            }
          };
          window.addEventListener('auaci:delete-with-permission-confirm', onConfirmed);
        });
      }

      let fromQueue = false;
      if (pendingToolsQueue.length > 0) {
        tools = pendingToolsQueue.splice(0, pendingToolsQueue.length);
        fromQueue = true;
        console.log(`[DEBUG] Loop ${loopGuard}: Using ${tools.length} queued tool(s)`);
      } else {
        // For the first loop iteration, use API tool_calls if available, otherwise parse from text
        // For subsequent iterations, only consider tools from the most recent model reply
        if (loopGuard === 1 && apiToolCalls && apiToolCalls.length > 0) {
          tools = convertApiToolCalls(apiToolCalls);
          console.log(`[DEBUG] Loop ${loopGuard}: Using ${tools.length} tool_calls from API`);
        } else {
          const responseToAnalyze = (loopGuard === 1) ? fullResponse : lastModelReply;
          const parsed = extractToolUsesAndSanitize(responseToAnalyze || '');
          tools = parsed && Array.isArray(parsed.tools) ? parsed.tools : [];
          console.log(`[DEBUG] Loop ${loopGuard}: Analyzing ${loopGuard === 1 ? 'initial fullResponse' : 'lastModelReply'} (${(responseToAnalyze||'').length} chars)`);
          console.log(`[DEBUG] Loop ${loopGuard}: Found ${tools ? tools.length : 0} tools from text`);
        }
        if (tools && tools.length > 0) {
          console.log(`[DEBUG] Tool names: ${tools.map(t => t.name).join(', ')}`);
        }
      }
      if (!tools || tools.length === 0) break;

      const normalizeToolName = (n) => {
        if (!n || typeof n !== 'string') return '';
        const s = n.trim();
        // unify common aliases
        if (/^attempt[-_]?completion$/i.test(s)) return 'attempt_completion';
        if (/^context[-_]?search$/i.test(s)) return 'context_search';
        if (/^apply[-_]?patch$/i.test(s)) return 'apply_patch';
        return s;
      };

      let handledOneToolInThisReply = false;
      for (let toolIdx = 0; toolIdx < tools.length; toolIdx++) {
        const tool = tools[toolIdx];
        const toolName = normalizeToolName(tool && tool.name);
        const toolId = `${toolName}_${JSON.stringify(tool.input || {})}`; // Create unique ID for this tool call
        
        console.log(`[DEBUG] Processing tool: ${tool.name} -> normalized: ${toolName}`);
        
        // Skip tools we've already processed
        if (processedTools.has(toolId)) {
          console.log(`[DEBUG] Skipping already processed tool: ${toolName}`);
          continue;
        }
        
        // Finalization tool – stop requesting further model responses
        if (toolName === 'attempt_completion') {
          console.log(`[DEBUG] Found attempt_completion, setting finalized=true and persisting state`);
          finalized = true;
          try {
            if (currentEntryIndex !== null) {
              const embeddedForStorage = embedToolResultsInGptResponse(fullResponseForHistory, toolRuns);
              await updateGptResponse(currentEntryIndex, embeddedForStorage, false, sessionId);
            }
          } catch (e) { console.warn('[DEBUG] attempt_completion immediate persist failed:', e && e.message ? e.message : e); }
          // Immediately update UI to render attempt_completion text inline (full re-render of last message)
          try {
            const embeddedForRender = embedToolResultsForRendering(fullResponseForHistory, toolRuns, { mode: 'live', skipCleanup: true });
renderLatestGptMessageInline(embeddedForRender, toolRuns, currentEntryIndex);
            try { animateLatestGptMessage(); } catch (_) {}
            try { maybeSmoothScrollToBottomIfNeeded(); } catch (_) {}
          } catch (_) {}
          break;
        }
        // Guard against malformed tool blocks with missing/empty name
        if (!toolName) {
          console.warn('[sendMessage] Skipping tool with missing/empty name:', tool && tool.raw ? String(tool.raw).slice(0,200) : '');
          continue;
        }
        
        // Mark this tool as processed
        processedTools.add(toolId);

        // LIVE RENDERING: Render tool placeholder BEFORE executing the tool
        // This ensures tools appear in the correct order: Text → Tool → Text → Tool
        if (shouldRenderForSession(sessionId)) {
          appendToolPlaceholder(toolName, tool.input, toolRuns);
          try { maybeSmoothScrollToBottomIfNeeded(); } catch (_) {}
        }

        // Execute tool
        console.log(`[DEBUG] Executing tool: ${toolName}`);
        let resultObj = null;
        let usedStreaming = false;
        let streamIndexRef = { index: -1 };
        try {
          if (toolName === 'run_command' || toolName === 'bash') {
            usedStreaming = true;
            resultObj = await executeStreamingCommand(toolName, tool.input || {}, streamIndexRef);
            console.log(`[DEBUG] Tool ${toolName} streamed successfully`);
          } else if (toolName === 'ask') {
            usedStreaming = true;
            resultObj = await executeAskInteractive(tool, streamIndexRef);
            console.log('[DEBUG] Tool ask completed interactively');
          } else if (toolName === 'delete_file_folder_with_permission') {
            usedStreaming = true;
            resultObj = await executeDeleteWithPermissionInteractive(tool, streamIndexRef);
            console.log('[DEBUG] Tool delete_file_folder_with_permission completed interactively');
          } else {
            // Non-streaming tools (grep, view, ls, glob, context_search, etc.)
            resultObj = await executeCommand({ name: toolName, input: tool.input, raw: tool.raw });
            console.log(`[DEBUG] Tool ${toolName} executed successfully`);
          }
        } catch (execErr) {
          console.log(`[DEBUG] Tool ${toolName} execution failed:`, execErr.message || execErr);
          resultObj = { error: String(execErr && execErr.message ? execErr.message : execErr) };
        }

// Record run for history embedding
        let runRec;
        try {
          // Build run record using normalized tool name
          const toolCopy = Object.assign({}, tool, { name: toolName });
          runRec = buildToolRunRecord(toolCopy, resultObj);
          if (!runRec || typeof runRec !== 'object') {
            runRec = { name: tool.name || 'unknown', input: tool.input || {}, result: { error: String(resultObj || 'malformed_result') }, success: false, summary: {} };
          }
        } catch (e) {
          runRec = { name: tool && tool.name ? String(tool.name) : 'unknown', input: tool && tool.input ? tool.input : {}, result: { error: String(e && e.message ? e.message : e || 'unknown error in buildToolRunRecord') }, success: false, summary: {} };
        }

        if (usedStreaming && streamIndexRef && typeof streamIndexRef.index === 'number' && streamIndexRef.index >= 0) {
          // Replace the ephemeral streaming run with the final sanitized run
          try {
            toolRuns[streamIndexRef.index] = runRec;
          } catch (_) {}
        } else {
          toolRuns.push(runRec);
        }

        // NEW HISTORY FORMAT: Update segmented gptEntry with system response
        try {
          const toolCallId = tool.id || `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
          const sanitizedResult = sanitizeResultForHistory(toolName, tool.input || {}, resultObj);
          
          // Update the tool segment's system_response (pass toolName for matching)
          updateToolSystemResponse(gptEntry, toolName, sanitizedResult);
          
          console.log(`[DEBUG] Updated gptEntry with tool ${toolName} system response`);
        } catch (e) {
          console.warn('[DEBUG] Failed to update gptEntry with tool system response:', e && e.message ? e.message : e);
        }

        // In new history format, tool runs are not stored separately; they are embedded into GPT text.
        // Persisting is handled via updateGptResponse with embedded content below.

        // Render segment for tool system response immediately (embed results so placeholders show summaries)
        console.log(`[DEBUG] Rendering tool result for ${toolName}`);
        try {
          if (currentEntryIndex !== null) {
            // NEW HISTORY FORMAT: Serialize the segmented gptEntry for storage
            const serializedForStorage = serializeGptEntry(gptEntry);
            
            // Also create legacy embedded format for backward compatibility
            const embeddedForStorage = embedToolResultsInGptResponse(fullResponseForHistory, toolRuns);
            
            // Use serialized if we have segments, otherwise use legacy
            const hasSegments = gptEntry.segments && gptEntry.segments.length > 0;
            const contentToSave = hasSegments ? serializedForStorage : embeddedForStorage;
            await updateGptResponse(currentEntryIndex, contentToSave, false, sessionId);
            console.log(`[DEBUG] Updated GPT response in history for ${toolName}`);
          }
          // Update existing placeholders with latest toolRuns (no duplicate placeholders)
          try {
            // For rendering, use buildRenderableContent from the new format
            const renderableContent = buildRenderableContent(gptEntry);
            const embeddedForRender = embedToolResultsForRendering(fullResponseForHistory, toolRuns, { mode: 'live', skipCleanup: true });
            const parsedForAll = extractToolUsesAndSanitize(embeddedForRender || '');
            if (shouldRenderForSession(sessionId)) {
              const contentEl = getGptContentElForEntry(currentEntryIndex);
              if (contentEl) enhanceToolPlaceholders(contentEl, parsedForAll.tools, toolRuns);
            }
            console.log(`[DEBUG] Updated tool placeholders for ${toolName}`);
          } catch (e) {
            console.warn('[DEBUG] Failed to enhance tool placeholders:', e);
          }
          if (shouldRenderForSession(sessionId)) {
            try { animateLatestGptMessage(); } catch (_) {}
            try { maybeSmoothScrollToBottomIfNeeded(); } catch (_) {}
            attachThinkingIndicator();
          }
        } catch (segErr) {
          console.error('[DEBUG] Failed to render tool result segment:', segErr);
        }

        // Build pure JSON result to send back to the model using history array format
        let resultText;
        // Attach session todo list snapshot (if active) alongside the tool system response we send back to the model
        resultText = await attachSessionTodoIfActive(resultObj, sessionId);
        console.log(`[DEBUG] Sending ${toolName} result back to GPT (${resultText.length} chars):`, resultText.slice(0, 300) + (resultText.length > 300 ? '...' : ''));
        
        // Send tool result back to model using history array format
        if (window.__auaciStopRequested && window.__auaciStopRequested[sessionId]) { break; }
        
        // Build tool result history entry
        const toolResultHistory = buildToolResultHistory(
          tool.id || `call_${Date.now()}`, // Use tool call ID if available
          toolName,
          resultText
        );
        
        // Send tool result back via history array
        const modelResponse = await completeOnce(null, { 
          history: [toolResultHistory]
        });
        
        let modelReply = modelResponse.text || '';
        let nextApiToolCalls = modelResponse.tool_calls || null;
        lastModelReply = modelReply;
        console.log(`[DEBUG] GPT replied after ${toolName} with ${modelReply.length} chars:`, (modelReply || '').slice(0, 200) + ((modelReply||'').length > 200 ? '...' : ''));

        // Auto-continue / finalization guard:
        // - If reply contains attempt_completion, check the session TODO list:
        //   - if todos are still pending -> reject attempt_completion and send a guard system response instead
        //   - if todos are complete/absent -> mark the session finalized
        // - If reply has no tools at all but the previous step used a tool, nudge the model to continue once.
        let renderableReply = modelReply;
        
        // Get tools from API response or parse from text
        let nextTools = [];
        if (nextApiToolCalls && nextApiToolCalls.length > 0) {
          nextTools = convertApiToolCalls(nextApiToolCalls);
        } else {
          const parsedNext = extractToolUsesAndSanitize(modelReply || '');
          nextTools = Array.isArray(parsedNext.tools) ? parsedNext.tools : [];
        }
        
        try {
          const previousStepUsedTool = true; // we just executed a tool in this step
          const normalizeToolName = (n) => {
            if (!n || typeof n !== 'string') return '';
            const s = n.trim();
            if (/^attempt[-_]?completion$/i.test(s)) return 'attempt_completion';
            return s;
          };

          const hasAttemptCompletion = nextTools.some(t => t && typeof t.name === 'string' && normalizeToolName(t.name) === 'attempt_completion');
          const hasAnyTool = nextTools.length > 0;

          if (hasAttemptCompletion) {
            const guardMsg = await buildTodoGuardMessageForAttemptCompletion(sessionId);
            if (guardMsg && guardMsg.trim()) {
              console.log('[DEBUG] attempt_completion reply intercepted: active TODO list found; sending guard system response instead of finalizing');
              const followUpResp = await completeOnce(guardMsg);
              renderableReply = followUpResp.text || '';
              nextApiToolCalls = followUpResp.tool_calls || null;
              lastModelReply = renderableReply;
              // Update nextTools for queue processing
              if (nextApiToolCalls && nextApiToolCalls.length > 0) {
                nextTools = convertApiToolCalls(nextApiToolCalls);
              } else {
                const parsedFollowUp = extractToolUsesAndSanitize(renderableReply || '');
                nextTools = Array.isArray(parsedFollowUp.tools) ? parsedFollowUp.tools : [];
              }
              // Do NOT set finalized here; allow further steps and additional tool calls.
            } else {
              console.log('[DEBUG] attempt_completion reply accepted: TODO list is complete or empty; marking session finalized');
              renderableReply = modelReply || '';
              lastModelReply = renderableReply;
              finalized = true;
            }
          } else if (!hasAnyTool && previousStepUsedTool) {
            const CONTINUE_MSG = 'System Response: You returned a message without any tools, this message will NOT be shown to the users. If you are finished with the user asked task, please use the attempt_completion tool to summarize what you have done during the process of completing the task.';
            const followUpResp = await completeOnce(CONTINUE_MSG);
            // Use the follow-up for render/persistence; suppress the no-tool interstitial completely
            renderableReply = followUpResp.text || '';
            nextApiToolCalls = followUpResp.tool_calls || null;
            lastModelReply = renderableReply;
            // Update nextTools for queue processing
            if (nextApiToolCalls && nextApiToolCalls.length > 0) {
              nextTools = convertApiToolCalls(nextApiToolCalls);
            } else {
              const parsedFollowUp = extractToolUsesAndSanitize(renderableReply || '');
              nextTools = Array.isArray(parsedFollowUp.tools) ? parsedFollowUp.tools : [];
            }
            console.log('[DEBUG] Auto-continue triggered after tool; suppressed no-tool reply and requested continuation');
          }
        } catch (_) {}

        // Accumulate only the renderable reply (exclude any suppressed interstitial and tool JSON)
        fullResponse += (renderableReply || '');
        fullResponseForHistory += (renderableReply || '');
        
        // NEW HISTORY FORMAT: Add text segment for the model reply, then tool segments
        const cleanReplyText = removeToolUseBlocks(renderableReply || '');
        if (cleanReplyText) {
          addTextSegment(gptEntry, cleanReplyText);
        }
        
        // Add any new tool calls from the model reply as tool segments
        if (nextTools.length > 0) {
          for (const t of nextTools) {
            if (t && typeof t.name === 'string') {
              addToolSegment(gptEntry, t.id || `call_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`, t.name, t.input || {});
            }
          }
        }
        
        handledOneToolInThisReply = true;
        
        // Queue additional tools from API response for processing
        if (nextTools.length > 0) {
          for (const t of nextTools) {
            if (t && typeof t.name === 'string') pendingToolsQueue.push(t);
          }
          console.log(`[DEBUG] Queued ${nextTools.length} tool(s) from model response`);
        }
        
        // If there were additional tools in this same reply and we are not already consuming a queue,
        // enqueue the remaining tools to process on subsequent turns.
        if (!fromQueue && Array.isArray(tools) && tools.length > 1) {
          for (let j = 0; j < tools.length; j++) {
            if (j === toolIdx) continue; // skip the one we just handled
            const t = tools[j];
            if (t && typeof t.name === 'string') pendingToolsQueue.push(t);
          }
          console.log(`[DEBUG] Queued ${pendingToolsQueue.length} additional tool(s) from the same reply`);
        }

        // Render segment for the subsequent model reply immediately
        console.log(`[DEBUG] Rendering subsequent model reply after ${toolName} (reply length: ${modelReply.length})`);
        try {
          if (currentEntryIndex !== null) {
            // NEW HISTORY FORMAT: Use serialized segmented gptEntry for storage
            const serializedForStorage = serializeGptEntry(gptEntry);
            const embeddedForStorage = embedToolResultsInGptResponse(fullResponseForHistory, toolRuns);
            const hasSegments = gptEntry.segments && gptEntry.segments.length > 0;
            const contentToSave = hasSegments ? serializedForStorage : embeddedForStorage;
            await updateGptResponse(currentEntryIndex, contentToSave, false, sessionId);
            console.log(`[DEBUG] Updated GPT response in history after ${toolName} model reply`);
          }
          // Append only the new GPT text segment (renderable)
          appendGptTextSegment(lastModelReply || '', toolRuns);
          
          // NOTE: Tool placeholders for nextTools will be rendered in the next loop iteration
          // BEFORE each tool is executed (see "LIVE RENDERING" comment above)
          
          console.log(`[DEBUG] Appended inline subsequent model reply after ${toolName}`);
          if (shouldRenderForSession(sessionId)) {
            try { animateLatestGptMessage(); } catch (_) {}
            try { maybeSmoothScrollToBottomIfNeeded(); } catch (_) {}
          }
          // Decide whether to continue based on just-produced model reply
          let shouldContinue = false;
          if (nextTools.length > 0) {
            shouldContinue = nextTools.some(t => t && typeof t.name === 'string' && !/^attempt[_-]?completion$/i.test(t.name));
          }
          if (shouldContinue) attachThinkingIndicator(); else { try { if (thinkingTimer) clearInterval(thinkingTimer); } catch (_) {} try { if (thinkingDivRef && typeof thinkingDivRef.remove === 'function') thinkingDivRef.remove(); } catch (_) {} }
        } catch (segErr) {
          console.error('[DEBUG] Failed to render model reply segment:', segErr);
        }
        if (handledOneToolInThisReply) {
          // Only one tool per model reply
          break;
        }
      }

      if (finalized) break;
    }

// Finished: embed tool results, save history, and refresh the history view
    // NEW HISTORY FORMAT: Use serialized segmented gptEntry for cleaner storage
    // Serialize the segmented entry (this produces clean <tool_use> blocks with system responses)
    const serializedEntry = serializeGptEntry(gptEntry);
    
    // Also create the legacy embedded format for backward compatibility
    const embedded = embedToolResultsInGptResponse(fullResponseForHistory, toolRuns);
    
    // Use the serialized entry if it has segments, otherwise use the legacy embedded format
    const hasSegments = gptEntry.segments && gptEntry.segments.length > 0;
    const finalGptContent = hasSegments ? serializedEntry : embedded;
    chatEntry.gpt = finalGptContent;
    
    console.log(`[DEBUG] Final gptEntry has ${gptEntry.segments ? gptEntry.segments.length : 0} segments`);

    // Save entry (prefer incremental finalizer; fallback to classic)
    let incrementalFinalized = false;
    if (currentEntryIndex !== null) {
      try {
        await finalizeChatEntry(currentEntryIndex, finalGptContent, sessionId);
        incrementalFinalized = true;
        console.log(`[DEBUG] Finalized chat entry at index: ${currentEntryIndex}`);
      } catch (err) {
        console.error('[DEBUG] Failed to finalize chat entry:', err);
      }
    }
    if (!incrementalFinalized) {
      try { await saveChatHistory(chatEntry, sessionId); } catch (saveError) { console.error('[DEBUG] Failed to save chat entry (fallback):', saveError); }
    }

    // Remove thinking placeholder and indicator
    try { if (thinkingTimer) clearInterval(thinkingTimer); } catch (_) {}
    try { if (thinkingDivRef && typeof thinkingDivRef.remove === 'function') thinkingDivRef.remove(); } catch (_) {}
    try { const btn = document.getElementById('stop-response-btn'); if (btn) { btn.disabled = true; btn.onclick = null; } } catch (_) {}
    if (shouldRenderForSession(sessionId)) hideGptIndicator(sessionId);
        try { if (window.__auaciRespondingSessions) window.__auaciRespondingSessions[sessionId] = false; } catch (_) {}
        try { if (window.__auaciRespondingStartTime) delete window.__auaciRespondingStartTime[sessionId]; } catch (_) {}
        try { require('../tabManager').refreshTabs(); } catch (_) {}
        window.isGptResponding = false;

    // Finalization: perform a non-destructive placeholder enhancement pass for active session only
    try {
      if (shouldRenderForSession(sessionId)) {
        const container = document.getElementById('chat-messages');
        const gpts = container ? container.querySelectorAll('.message.gpt-message') : [];
        const last = gpts && gpts.length ? gpts[gpts.length - 1] : null;
        const contentEl = last ? last.querySelector('.message-content') : null;
        if (contentEl) {
          const embeddedForRender = embedToolResultsForRendering(fullResponseForHistory, toolRuns, { mode: 'live', skipCleanup: true });
          const parsedForAll = extractToolUsesAndSanitize(embeddedForRender || '');
          enhanceToolPlaceholders(contentEl, parsedForAll.tools, toolRuns);
        }
      }
    } catch (e) {
      console.warn('[DEBUG] Final enhancement pass failed (safe to ignore):', e);
    }

    // Finalization: do not re-render the whole message (avoid wiping previously appended segments)
    console.log(`[DEBUG] Final render step skipped (append-only mode). toolRuns=${toolRuns.length}`);
    try {
      // Save scroll position for this session after render (only if active and container present)
      if (sessionId && shouldRenderForSession(sessionId)) {
        const container = document.getElementById('chat-messages');
        if (container) await scrollCache.saveScrollFromContainer(sessionId, container);
      }
    } catch (e) {
      console.error('[DEBUG] Failed to save scroll position:', e);
    }
  } catch (err) {
    console.error('[DEBUG] Error in MainAssistant:', err);
try { if (thinkingTimer) clearInterval(thinkingTimer); } catch (_) {}
    try {
      if (thinkingDivRef) {
        const c = thinkingDivRef.querySelector('.message-content');
        if (c) c.innerHTML = `Error: ${escapeHTML(err && err.message ? err.message : String(err))}. Is the GPT server running at http://localhost:8129/chat?`;
      }
    } catch (_) {}
    window.isGptResponding = false;
    hideGptIndicator();
  }
}

// Build a structured record for a tool run including display-friendly summary
function buildToolRunRecord(tool, resultObj) {
  const name = tool && tool.name ? String(tool.name) : '';
  const input = tool && tool.input && typeof tool.input === 'object' ? tool.input : {};
  const error = resultObj && resultObj.error;
  const success = !error;
  // Sanitize the stored "result" to avoid embedding file contents (view) or long outputs.
  const sanitizedResult = sanitizeResultForHistory(name, input, resultObj);

  const rec = { name, input, result: sanitizedResult, success };

  // Display-oriented summary
  const summary = {};
  if (name === 'ls') {
    summary.path = input.path || '.';
    if (Array.isArray(resultObj && resultObj.entries)) summary.entriesCount = resultObj.entries.length;
    if (typeof input.max_depth === 'number') summary.max_depth = input.max_depth;
  } else if (name === 'view') {
    summary.path = input.path || '';
    if (typeof resultObj?.total_line_count === 'number') summary.total_line_count = resultObj.total_line_count;
    if (typeof resultObj?.is_truncated === 'boolean') summary.is_truncated = resultObj.is_truncated;
  } else if (name === 'run_command' || name === 'bash') {
    const cmd = input && typeof input.command === 'string' ? input.command : '';
    summary.program = cmd.split(/\s+/).filter(Boolean)[0] || (name === 'bash' ? 'bash' : 'sh');
    const finished = resultObj && resultObj.finished;
    if (finished && typeof finished.exit_code === 'number') summary.exit_code = finished.exit_code;
  } else if (name === 'txtlize') {
    summary.name = input && typeof input.name === 'string' ? input.name : '';
    if (typeof resultObj?.file_count === 'number') summary.file_count = resultObj.file_count;
    if (typeof resultObj?.total_size_bytes === 'number') summary.total_size_bytes = resultObj.total_size_bytes;
    if (typeof resultObj?.output_path === 'string') summary.output_path = resultObj.output_path;
  } else if (name === 'read_any_files') {
    const req = Array.isArray(input?.files) ? input.files.map(x => (x && x.path) ? String(x.path) : '').filter(Boolean) : [];
    summary.files_requested = req;
    const files = Array.isArray(resultObj?.files) ? resultObj.files : [];
    const successCount = files.filter(f => !(f && f.error)).length;
    const errorCount = files.length - successCount;
    summary.read_count = successCount;
    summary.error_count = errorCount;
  } else if (name === 'grep' || name === 'context_search' || name === 'context-search') {
    summary.path = input && input.path ? input.path : '';
    const patterns = Array.isArray(input?.queries) ? input.queries : [];
    summary.patterns = patterns;
    const stats = Array.isArray(resultObj?.pattern_stats) ? resultObj.pattern_stats : [];
    const foundMap = {};
    for (const s of stats) {
      if (s && typeof s.pattern === 'string') foundMap[s.pattern] = !!s.matched;
    }
    summary.found_by_pattern = foundMap;
    if (Array.isArray(resultObj?.matched_files)) summary.found_files_count = resultObj.matched_files.length;
  } else if (name === 'view') {
    summary.path = input && typeof input.path === 'string' ? input.path : '';
    if (typeof resultObj?.total_line_count === 'number') summary.total_line_count = resultObj.total_line_count;
    if (typeof resultObj?.is_truncated === 'boolean') summary.is_truncated = resultObj.is_truncated;
    if (typeof resultObj?.displayed_lines === 'number') summary.displayed_lines = resultObj.displayed_lines;
    if (typeof resultObj?.range_info === 'string') summary.range_info = resultObj.range_info;
  } else if (name === 'ask') {
    summary.question = (input && typeof input.question === 'string') ? input.question : '';
    summary.mode = (input && typeof input.mode === 'string') ? input.mode : undefined;
    if (Array.isArray(input?.options)) summary.options_count = input.options.length;
  } else if (name === 'apply_patch') {
    summary.title = input && typeof input.title === 'string' ? input.title : 'File patch';
    if (typeof resultObj?.applied_count === 'number') summary.applied_count = resultObj.applied_count;
    if (typeof resultObj?.error_count === 'number') summary.error_count = resultObj.error_count;
    if (Array.isArray(resultObj?.applied)) summary.files_modified = resultObj.applied.map(a => a.file_path);
    if (Array.isArray(resultObj?.errors)) summary.files_with_errors = resultObj.errors.map(e => e.file_path);
  }
  rec.summary = summary;
  return rec;
}

function sanitizeResultForHistory(name, input, resultObj) {
  const err = resultObj && resultObj.error ? String(resultObj.error) : undefined;
  if (name === 'view') {
    return {
      success: !err,
      total_line_count: (resultObj && typeof resultObj.total_line_count === 'number') ? resultObj.total_line_count : undefined,
      is_truncated: (resultObj && typeof resultObj.is_truncated === 'boolean') ? resultObj.is_truncated : undefined,
      displayed_lines: (resultObj && typeof resultObj.displayed_lines === 'number') ? resultObj.displayed_lines : undefined,
      range_info: (resultObj && typeof resultObj.range_info === 'string') ? resultObj.range_info : undefined,
      error: err
    };
  } else if (name === 'ls') {
    const out = {};
    // Do not carry display_content for ls so renderer uses the view-like layout
    const entriesCount = Array.isArray(resultObj && resultObj.entries) ? resultObj.entries.length : undefined;
    if (entriesCount !== undefined) out.entriesCount = entriesCount;
    if (resultObj?.summary) out.summary = resultObj.summary;
    if (err) out.error = err;
    return out;
  } else if (name === 'read_any_files') {
    // Build a compact summary per file and drop large content payloads
    const files = Array.isArray(resultObj && resultObj.files) ? resultObj.files : [];
    const filesSummary = files.map(f => {
      const pth = f && f.path ? String(f.path) : '';
      const size = (typeof f?.size_bytes === 'number') ? f.size_bytes : undefined;
      const isEmpty = (typeof f?.is_empty === 'boolean') ? f.is_empty : undefined;
      let displayedLines = undefined;
      try {
        if (typeof f?.displayed_lines === 'number') displayedLines = f.displayed_lines;
        else if (typeof f?.content === 'string') displayedLines = f.content.split(/\n/).length;
      } catch (_) {}
      const error = (typeof f?.error === 'string') ? f.error : undefined;
      const exists = error ? false : true;
      return { path: pth, displayed_lines: displayedLines, size_bytes: size, is_empty: isEmpty, error, exists };
    });
    // Also record which files were requested by input
    let requested = [];
    try {
      if (Array.isArray(input?.files)) requested = input.files.map(x => (x && x.path) ? String(x.path) : '').filter(Boolean);
    } catch (_) {}
    return { files: filesSummary, requested_files: requested };
  } else if (name === 'run_command' || name === 'bash') {
    // Preserve terminal transcript details for rendering a virtual terminal, but clamp output to first 30 lines
    const srcFinished = resultObj && resultObj.finished ? resultObj.finished : undefined;
    let finished;
    if (srcFinished && typeof srcFinished.output === 'string') {
      let lines = srcFinished.output.split(/\r?\n/);
      // Exclude any cache paths lines
      try { lines = lines.filter(l => !l.includes('.auaci')); } catch (_) {}
      const total = lines.length;
      const shown = Math.min(30, total);
      const clipped = lines.slice(0, shown).join('\n');
      finished = {
        command: srcFinished.command,
        output: clipped,
        exit_code: srcFinished.exit_code,
        new_pwd: srcFinished.new_pwd,
        run_id: srcFinished.run_id,
        displayed_lines: shown,
        total_lines: total,
        truncated: total > shown
      };
    } else if (srcFinished) {
      // No string output available; copy minimal metadata
      finished = {
        command: srcFinished.command,
        exit_code: srcFinished.exit_code,
        new_pwd: srcFinished.new_pwd,
        run_id: srcFinished.run_id,
        displayed_lines: 0,
        total_lines: 0,
        truncated: false
      };
    }
    // Keep a short preview for summaries if needed
    const output_preview = srcFinished && typeof srcFinished.output === 'string'
      ? srcFinished.output.slice(0, 512)
      : undefined;
    const out = { finished, output_preview, error: err };
    if (typeof resultObj?.flood_notice === 'string') out.flood_notice = resultObj.flood_notice;
    if (typeof resultObj?.user_stop_message === 'string') out.user_stop_message = resultObj.user_stop_message;
    if (typeof resultObj?.terminated_by_user === 'boolean') out.terminated_by_user = resultObj.terminated_by_user;
    if (typeof resultObj?.terminated_by_system_flood === 'boolean') out.terminated_by_system_flood = resultObj.terminated_by_system_flood;
    return out;
  } else if (name === 'txtlize') {
    const out = {};
    if (typeof resultObj?.output_path === 'string') out.output_path = resultObj.output_path;
    if (typeof resultObj?.file_count === 'number') out.file_count = resultObj.file_count;
    if (typeof resultObj?.total_size_bytes === 'number') out.total_size_bytes = resultObj.total_size_bytes;
    if (err) out.error = err;
    return out;
  } else if (name === 'grep' || name === 'context_search' || name === 'context-search') {
    const out = {};
    if (typeof resultObj?.display_content === 'string') out.display_content = resultObj.display_content;
    if (Array.isArray(resultObj?.pattern_stats)) out.pattern_stats = resultObj.pattern_stats;
    if (Array.isArray(resultObj?.matched_files)) {
      out.matched_files_count = resultObj.matched_files.length;
      out.matched_files = resultObj.matched_files; // Preserve enhanced file info
    }
    if (typeof resultObj?.path === 'string') out.path = resultObj.path;
    if (resultObj?.summary) out.summary = resultObj.summary;
    if (err) out.error = err;
    return out;
  } else if (name === 'apply_patch') {
    const out = {};
    if (typeof resultObj?.title === 'string') out.title = resultObj.title;
    if (typeof resultObj?.applied_count === 'number') out.applied_count = resultObj.applied_count;
    if (typeof resultObj?.error_count === 'number') out.error_count = resultObj.error_count;
    // Include simplified info about applied/failed patches
    if (Array.isArray(resultObj?.applied)) {
      out.applied = resultObj.applied.map(a => ({
        file_path: a.file_path,
        start_line: a.replaced?.start_line,
        deleted_line_count: a.replaced?.deleted_line_count,
        inserted_line_count: a.replaced?.inserted_line_count
      }));
    }
    if (Array.isArray(resultObj?.errors)) {
      out.errors = resultObj.errors.map(e => ({
        file_path: e.file_path,
        error: e.error,
        hints: e.hints
      }));
    }
    if (Array.isArray(resultObj?.syntax_issues)) {
      out.syntax_issues = resultObj.syntax_issues.map(s => ({
        file_path: s.file_path,
        language: s.language,
        checker: s.checker,
        initial_ok: s.initial_ok,
        autofix_attempted: s.autofix_attempted,
        autofix_applied: s.autofix_applied,
        autofix_error: s.autofix_error,
        final_ok: s.final_ok,
      }));
    }
    if (err) out.error = err;
    return out;
  } else if (name === 'find_files') {
    // For find_files, preserve patterns and the matched files list for custom SVG rendering; omit display_content
    const out = {};
    if (Array.isArray(input?.patterns)) out.patterns = input.patterns;
    if (Array.isArray(resultObj?.files)) {
      out.files = resultObj.files.map(f => ({ fileName: f && f.fileName, fullPath: f && f.fullPath })).filter(f => f.fileName || f.fullPath);
    }
    if (typeof resultObj?.matched_files === 'string') out.matched_files = resultObj.matched_files; // legacy
    if (resultObj?.summary) out.summary = resultObj.summary;
    if (err) out.error = err;
    return out;
  } else if (name === 'add_todos' || name === 'create_todo_list' || name === 'read_todos' || name === 'mark_todo_as_done' || name === 'remove_todos') {
    // For todo tools, do NOT include display_content so the custom renderer can build SVG-based UI.
    const out = {};
    if (resultObj?.todo_list) out.todo_list = resultObj.todo_list;
    if (resultObj?.summary) out.summary = resultObj.summary;
    // Pass along identifiers for highlighting/striking in the renderer
    if (name === 'add_todos' && Array.isArray(resultObj?.added_todos)) {
      out.added_ids = resultObj.added_todos.map(t => t && t.id).filter(Boolean);
    }
    if (name === 'mark_todo_as_done' && Array.isArray(resultObj?.completed_todos)) {
      out.completed_ids = resultObj.completed_todos.map(t => t && t.id).filter(Boolean);
    }
    if (name === 'remove_todos') {
      const remP = Array.isArray(resultObj?.removed_todos?.pending) ? resultObj.removed_todos.pending : [];
      const remC = Array.isArray(resultObj?.removed_todos?.completed) ? resultObj.removed_todos.completed : [];
      const ids = [];
      for (const t of remP) if (t && t.id) ids.push(t.id);
      for (const t of remC) if (t && t.id) ids.push(t.id);
      if (ids.length) out.removed_ids = ids;
    }
    if (err) out.error = err;
    return out;
  } else {
    // Default: copy result but remove large 'content' fields if present, preserve display_content
    if (!resultObj || typeof resultObj !== 'object') return resultObj;
    const copy = Object.assign({}, resultObj);
    if (typeof copy.content === 'string') delete copy.content;
    // Always preserve display_content for enhanced Warp-style tools
    return copy;
  }
}

// Strip legacy grep display_content blocks that were rendered as plain text
// before the new CSS/box-based renderer. These blocks start with
// "🔍 Search Results in" and include pattern stats and tips.
function stripLegacyGrepDisplayText(text) {
  if (!text || typeof text !== 'string') return text;
  const marker = '🔍 Search Results in ';
  let out = text;
  while (true) {
    const idx = out.indexOf(marker);
    if (idx === -1) break;
    // Start at the marker line
    let start = idx;
    // Optionally include the preceding newline for cleaner layout
    const prevNl = out.lastIndexOf('\n', idx - 1);
    if (prevNl !== -1) start = prevNl + 1;

    let i = idx;
    const len = out.length;
    // Advance line by line while the content looks like legacy grep output
    while (i < len) {
      const nextNl = out.indexOf('\n', i);
      const lineEnd = nextNl === -1 ? len : nextNl;
      const line = out.slice(i, lineEnd);
      const trimmed = line.trim();
      if (!trimmed) {
        // blank lines are part of the block
      } else if (/^(─+|📋 |📄 |✅ |❌ |💡 |Found \d+ match|Found \d+ matches|No matches found|\uD83D\uDCCA|  - )/.test(trimmed)) {
        // Still part of the legacy grep summary block
      } else {
        // First non-legacy line; stop here
        break;
      }
      if (nextNl === -1) {
        i = len;
        break;
      }
      i = nextNl + 1;
    }
    out = out.slice(0, start) + out.slice(i);
  }
  return out;
}

// Strip any plain-text auto-continue system warnings that may have leaked into
// saved GPT text from older logic.
function stripAutoContinueSystemWarnings(text) {
  if (!text || typeof text !== 'string') return text;
  const marker = 'System Response: You returned a message without any tools, this message will NOT be shown to the users.';
  if (text.indexOf(marker) === -1) return text;
  return text
    .split(/\r?\n/)
    .filter(line => !line.includes(marker))
    .join('\n');
}

/**
 * embedToolResultsForRendering(gptText, toolRuns)
 * - For UI rendering, embed tool results while preserving conversational text
 * - Unlike embedToolResultsInGptResponse, this keeps all the intermediate GPT responses
 * - Only embeds results into <tool_use> blocks, doesn't strip conversational content
 */
function repairUnclosedToolUseBlocks(text) {
  try {
    if (!text || typeof text !== 'string') return text;
    let out = text;
    let idx = 0;
    while (true) {
      const start = out.indexOf('<tool_use>', idx);
      if (start === -1) break;
      const close = out.indexOf('</tool_use>', start);
      const nextOpen = out.indexOf('<tool_use>', start + 9);
      if (close !== -1 && (nextOpen === -1 || close < nextOpen)) { idx = close + 10; continue; }
      // No close before next open or end — try to wrap the first JSON object after start
      const braceStart = out.indexOf('{', start + 9);
      if (braceStart === -1) { idx = start + 9; continue; }
      const braceEnd = findMatchingJsonEnd(out, braceStart);
      if (braceEnd !== -1) {
        const insertPos = braceEnd + 1;
        out = out.slice(0, insertPos) + '</tool_use>' + out.slice(insertPos);
        idx = insertPos + 10;
      } else {
        idx = start + 9;
      }
    }
    return out;
  } catch (_) { return text; }
}

function embedToolResultsForRendering(gptText, toolRuns, options) {
  if (!gptText) return '';
  const mode = options && typeof options.mode === 'string' ? String(options.mode).toLowerCase() : null;
  const skipCleanup = !!(options && options.skipCleanup);
  try {
    gptText = repairUnclosedToolUseBlocks(gptText);
    if (!Array.isArray(toolRuns) || toolRuns.length === 0) {
      // if no runs, return original text (but repaired); still do optional cleanup later in this function
    }

    const toolUseRe = /<tool_use>\s*([\s\S]*?)\s*<\/tool_use>/gi;
    let out = '';
    let lastIndex = 0;
    let match;
    let matchIndex = 0;

    while ((match = toolUseRe.exec(gptText)) !== null) {
    const matchStart = match.index;
    const matchEnd = toolUseRe.lastIndex;
    const jsonRaw = match[1];
    out += gptText.slice(lastIndex, matchStart);

    // Attempt to parse the tool_use JSON robustly
    const originalObj = tolerantParseToolUseJson(jsonRaw);

    // Try to pick a matching run. Prefer same index; fall back to a name-match scan.
    let run = toolRuns[matchIndex] || null;
    if (!run && originalObj && originalObj.name) {
      const wanted = String(originalObj.name).toLowerCase();
      for (let r = 0; r < toolRuns.length; r++) {
        if (toolRuns[r] && String(toolRuns[r].name || '').toLowerCase() === wanted) {
          run = toolRuns[r];
          break;
        }
      }
    }

    // Determine the canonical name for special-case handling
    const inferredName = (run && run.name) || (originalObj && originalObj.name) || '';
    const lname = String(inferredName || '').toLowerCase();

    const systemResp = buildToolSystemResponse(run, originalObj, inferredName);

    // If the tool is attempt_completion, do not add a system response
    if (lname === 'attempt_completion' || lname === 'attempt-completion' || lname === 'attemptcompletion') {
      if (originalObj && typeof originalObj === 'object') {
        // Clean but don't add system response
        if (typeof originalObj.tool_system_response !== 'undefined') delete originalObj.tool_system_response;
        if (typeof originalObj.tool_system_results !== 'undefined') delete originalObj.tool_system_results;
        out += `<tool_use>${JSON.stringify(originalObj)}</tool_use>`;
      } else {
        const recon = { name: inferredName || '', input: (run && run.input) ? run.input : {} };
        out += `<tool_use>${JSON.stringify(recon)}</tool_use>`;
      }
    } else {
      // For all other tools: attach system response for rendering
      let fallback = null;
      if (!systemResp) {
        fallback = { success: false, error: 'tool execution result missing from session' };
      }

      const useNewKey = (lname === 'view' || lname === 'grep' || lname === 'context_search' || lname === 'context-search' ||
                          lname === 'find_files' || lname === 'ls' || lname === 'add_todos' || lname === 'create_todo_list' ||
                          lname === 'read_todos' || lname === 'mark_todo_as_done' || lname === 'remove_todos' || lname === 'ask');

      if (originalObj && typeof originalObj === 'object') {
        if (typeof originalObj.result !== 'undefined') delete originalObj.result;
        if (useNewKey) {
          if (typeof originalObj.tool_system_response !== 'undefined') delete originalObj.tool_system_response;
          originalObj.tool_system_results = systemResp || fallback;
          out += `<tool_use>${JSON.stringify(originalObj)}</tool_use>`;
        } else {
          if (typeof originalObj.tool_system_results !== 'undefined') delete originalObj.tool_system_results;
          originalObj.tool_system_response = systemResp || fallback;
          out += `<tool_use>${JSON.stringify(originalObj)}</tool_use>`;
        }
      } else if (run) {
        if (useNewKey) {
          const outObj = { name: run.name || '', input: run.input || {}, tool_system_results: systemResp || fallback };
          out += `<tool_use>${JSON.stringify(outObj)}</tool_use>`;
        } else {
          const outObj = { name: run.name || '', input: run.input || {}, tool_system_response: systemResp || fallback };
          out += `<tool_use>${JSON.stringify(outObj)}</tool_use>`;
        }
      } else {
        const outObj = useNewKey
          ? { name: inferredName || '', input: (originalObj && originalObj.input) ? originalObj.input : {}, tool_system_results: fallback }
          : { name: inferredName || '', input: (originalObj && originalObj.input) ? originalObj.input : {}, tool_system_response: fallback };
        out += `<tool_use>${JSON.stringify(outObj)}</tool_use>`;
      }
    }

    // For rendering, keep going after the tool_use block (don't strip following content)
    lastIndex = matchEnd;
    matchIndex++;
  }

  out += gptText.slice(lastIndex);
    // For live/streaming rendering paths, we skip heavy JSON cleanup to keep the UI responsive.
    // History renderers (which call this without options) still benefit from the cleanup.
    if (!skipCleanup && mode !== 'live') {
      try { out = removeAllToolResultJsonLocal(out); } catch (_) {}
      try { out = stripAutoContinueSystemWarnings(out); } catch (_) {}
    }
    return out;
  } catch (e) {
    try { console.warn('[embedToolResultsForRendering] failed, returning original text', e && e.message ? e.message : e); } catch (_) {}
    return String(gptText || '');
  }
}

/**
 * embedToolResultsInGptResponse(gptText, toolRuns)
 * - For saved history, embed sanitized run.result and run.summary into the preceding <tool_use> JSON
 *   under either "tool_system_response" (legacy / non-view/grep tools) or "tool_system_results"
 *   (preferred for view/grep which only need sanitized status results).
 * - Also removes the standalone JSON blocks that the agent would have appended after the tool use,
 *   so the saved text doesn't double-store results.
 *
 * This version is tolerant: it can recover malformed <tool_use> JSON (missing values),
 * will reconstruct name/input if necessary, and will insert a fallback tool_system_results/response
 * when no run record exists. attempt_completion is explicitly left without a saved system response.
 */
function embedToolResultsInGptResponse(gptText, toolRuns) {
  if (!gptText) return '';
  try {
    gptText = repairUnclosedToolUseBlocks(gptText);
    if (!Array.isArray(toolRuns) || toolRuns.length === 0) {
      // if no runs, still try a lightweight cleanup (remove orphan result JSON outside of <tool_use>)
      let cleaned = removeOrphanToolResultJsonLocal(gptText || '');
      cleaned = stripAutoContinueSystemWarnings(cleaned);
      return cleaned;
    }

    const toolUseRe = /<tool_use>\s*([\s\S]*?)\s*<\/tool_use>/gi;
    let out = '';
    let lastIndex = 0;
    let match;
    let matchIndex = 0;

  while ((match = toolUseRe.exec(gptText)) !== null) {
    const matchStart = match.index;
    const matchEnd = toolUseRe.lastIndex;
    const jsonRaw = match[1];
    out += gptText.slice(lastIndex, matchStart);

    // Attempt to parse the tool_use JSON robustly
    const originalObj = tolerantParseToolUseJson(jsonRaw);

    // Try to pick a matching run. Prefer same index; fall back to a name-match scan.
    let run = toolRuns[matchIndex] || null;
    if (!run && originalObj && originalObj.name) {
      const wanted = String(originalObj.name).toLowerCase();
      for (let r = 0; r < toolRuns.length; r++) {
        if (toolRuns[r] && String(toolRuns[r].name || '').toLowerCase() === wanted) {
          run = toolRuns[r];
          break;
        }
      }
    }

    // If still not found, try a loose fallback by looking for run with same input.path (if available)
    if (!run && originalObj && originalObj.input && typeof originalObj.input === 'object') {
      const wantPath = originalObj.input.path;
      if (wantPath) {
        for (let r = 0; r < toolRuns.length; r++) {
          const cand = toolRuns[r];
          if (cand && cand.input && (cand.input.path === wantPath || cand.input.path === String(wantPath))) {
            run = cand;
            break;
          }
        }
      }
    }

    // Determine the canonical name for special-case handling
    const inferredName = (run && run.name) || (originalObj && originalObj.name) || '';
    const lname = String(inferredName || '').toLowerCase();

    const systemResp = buildToolSystemResponse(run, originalObj, inferredName);

    // If the tool is attempt_completion, do not add a system response (leave as-is but try to clean malformed JSON)
    if (lname === 'attempt_completion' || lname === 'attempt-completion' || lname === 'attemptcompletion') {
      // If we could parse originalObj, re-emit a cleaned object without touching tool_system_response/tool_system_results;
      // otherwise reconstruct minimal object (name + input) so saved JSON is valid.
      if (originalObj && typeof originalObj === 'object') {
        // ensure we don't keep a corrupt tool_system_response/tool_system_results token if it exists but was malformed
        if (typeof originalObj.tool_system_response !== 'undefined') delete originalObj.tool_system_response;
        if (typeof originalObj.tool_system_results !== 'undefined') delete originalObj.tool_system_results;
        out += `<tool_use>${JSON.stringify(originalObj)}</tool_use>`;
      } else {
        const recon = { name: inferredName || '', input: (run && run.input) ? run.input : {} };
        out += `<tool_use>${JSON.stringify(recon)}</tool_use>`;
      }
    } else {
      // For all other tools: attach a useful tool_system_response or tool_system_results
      let fallback = null;
      if (!systemResp) {
        // If there was no run and no parseable object, create a fallback marker so history rendering won't fail.
        fallback = { success: false, error: 'tool execution result missing from session' };
      }

      const useNewKey = (lname === 'view' || lname === 'grep' || lname === 'context_search' || lname === 'context-search' ||
                          lname === 'find_files' || lname === 'ls' || lname === 'add_todos' || lname === 'create_todo_list' ||
                          lname === 'read_todos' || lname === 'mark_todo_as_done' || lname === 'remove_todos' || lname === 'ask');

      if (originalObj && typeof originalObj === 'object') {
        if (typeof originalObj.result !== 'undefined') delete originalObj.result;
        if (useNewKey) {
          if (typeof originalObj.tool_system_response !== 'undefined') delete originalObj.tool_system_response;
          originalObj.tool_system_results = systemResp || fallback;
          out += `<tool_use>${JSON.stringify(originalObj)}</tool_use>`;
        } else {
          if (typeof originalObj.tool_system_results !== 'undefined') delete originalObj.tool_system_results;
          originalObj.tool_system_response = systemResp || fallback;
          out += `<tool_use>${JSON.stringify(originalObj)}</tool_use>`;
        }
      } else if (run) {
        if (useNewKey) {
          const outObj = { name: run.name || '', input: run.input || {}, tool_system_results: systemResp || fallback };
          out += `<tool_use>${JSON.stringify(outObj)}</tool_use>`;
        } else {
          const outObj = { name: run.name || '', input: run.input || {}, tool_system_response: systemResp || fallback };
          out += `<tool_use>${JSON.stringify(outObj)}</tool_use>`;
        }
      } else {
        // Nothing to go on - emit a minimal placeholder with fallback
        const outObj = useNewKey
          ? { name: inferredName || '', input: (originalObj && originalObj.input) ? originalObj.input : {}, tool_system_results: fallback }
          : { name: inferredName || '', input: (originalObj && originalObj.input) ? originalObj.input : {}, tool_system_response: fallback };
        out += `<tool_use>${JSON.stringify(outObj)}</tool_use>`;
      }
    }

    // Advance pointer past the trailing JSON result if it immediately follows the match.
    // Skip whitespace/newlines then, if next char is '{', find JSON block and skip it.
    let posAfter = matchEnd;
    while (posAfter < gptText.length && /\s/.test(gptText[posAfter])) posAfter++;
    if (posAfter < gptText.length && gptText[posAfter] === '{') {
      const endPos = findMatchingJsonEnd(gptText, posAfter);
      if (endPos !== -1) {
        const candidate = gptText.slice(posAfter, endPos + 1);
        // Only skip if the immediate JSON looks like an orphaned tool result (conservative)
        if (looksLikeToolResultLocal(candidate)) {
          // Skip JSON block and any trailing annotation lines (e.g. "Note: ...") often appended by tools
          let afterJson = endPos + 1;
          afterJson = skipAnnotationLinesLocal(gptText, afterJson);
          lastIndex = afterJson;
        } else {
          lastIndex = matchEnd;
        }
      } else {
        lastIndex = matchEnd;
      }
    } else {
      lastIndex = matchEnd;
    }

    matchIndex++;
  }

  out += gptText.slice(lastIndex);
    // Safety: remove any remaining orphan tool-result JSON blocks outside <tool_use>
    out = removeOrphanToolResultJsonLocal(out);
    out = stripAutoContinueSystemWarnings(out);
    return out;
  } catch (e) {
    try { console.warn('[embedToolResultsInGptResponse] failed, returning original text', e && e.message ? e.message : e); } catch (_) {}
    return String(gptText || '');
  }
}

/**
 * Tolerant parsing helpers used by embedToolResultsInGptResponse
 */
function tryParseJsonLocal(text) {
  if (text == null) return null;
  let t = String(text).trim();
  if (!t) return null;
  // strip code fences
  t = t.replace(/^```[a-zA-Z0-9]*\n([\s\S]*?)\n```$/m, '$1').trim();
  // unescape common HTML entities
  t = t.replace(/&quot;/g, '\"').replace(/&#34;/g, '\"').replace(/&amp;/g, '&');
  try {
    return JSON.parse(t);
  } catch (_) {
    return null;
  }
}

function extractJsonObjectLocal(text) {
  const s = String(text || '');
  const start = s.indexOf('{');
  const end = s.lastIndexOf('}');
  if (start === -1 || end === -1 || end <= start) return null;
  return s.slice(start, end + 1);
}

function tolerantParseToolUseJson(jsonRaw) {
  // 1) Try relaxed JSON parse (handles code fences & &quot;)
  let obj = tryParseJsonLocal(jsonRaw);
  if (obj) return obj;

  // 2) Try to extract first {...} block and parse that
  const sub = extractJsonObjectLocal(jsonRaw);
  if (sub) {
    obj = tryParseJsonLocal(sub);
    if (obj) return obj;
    try {
      return JSON.parse(sub);
    } catch (_) { /* continue */ }
  }

  // 3) Fallback: attempt to extract name and input via regex
  const out = {};
  const mName = jsonRaw.match(/["']?name["']?\s*:\s*["']([^"']+)["']/i);
  if (mName) out.name = mName[1];
  const mInput = jsonRaw.match(/["']?input["']?\s*:\s*({[\s\S]*})/i);
  if (mInput) {
    const inp = tryParseJsonLocal(mInput[1]);
    if (inp) out.input = inp;
    else {
      try {
        out.input = JSON.parse(mInput[1]);
      } catch (_) {
        // leave input undefined if we cannot parse
      }
    }
  }
  return Object.keys(out).length ? out : null;
}

/**
 * Skip annotation lines (like "Note: ...", "Warning: ...") that sometimes follow a removed JSON block.
 * This prevents leftover "Note: Content truncated..." text from appearing as stray raw text below tools in history.
 *
 * text: full text
 * pos: index where we should begin checking/skipping
 * returns new index after skipping annotation lines (or same pos if none)
 */
function skipAnnotationLinesLocal(text, pos) {
  let i = pos;
  // skip initial whitespace/newlines
  while (i < text.length && /\s/.test(text[i])) i++;
  let advanced = false;
  const annotRe = /^\s*(?:Note\b|Note:|Warning\b|Warning:|Info:|Info\b|Hint:|Hint\b)/i;
  while (i < text.length) {
    const nextNl = text.indexOf('\n', i);
    const line = nextNl === -1 ? text.slice(i) : text.slice(i, nextNl);
    if (annotRe.test(line)) {
      // skip this annotation line and continue
      i = nextNl === -1 ? text.length : nextNl + 1;
      advanced = true;
      continue;
    }
    // allow one blank line after annotations, then stop
    if (line.trim() === '' && advanced) {
      i = nextNl === -1 ? text.length : nextNl + 1;
      break;
    }
    break;
  }
  return i;
}

function buildToolSystemResponse(run, originalObj, nameHint) {
  // Helper: drop transient fields that should never be persisted (e.g. session_todo_list)
  const stripTransient = (obj) => {
    try {
      if (!obj || typeof obj !== 'object') return obj;
      const copy = Array.isArray(obj) ? obj.map(stripTransient) : { ...obj };
      if ('session_todo_list' in copy) delete copy.session_todo_list;
      // If wrapped under result, strip there too
      if (copy.result && typeof copy.result === 'object' && 'session_todo_list' in copy.result) delete copy.result.session_todo_list;
      return copy;
    } catch (_) { return obj; }
  };

  // Name detection
  const name = (run && run.name) || (originalObj && originalObj.name) || nameHint || '';
  const lname = String(name || '').toLowerCase();

  // attempt_completion intentionally does not get a tool_system_response/tool_system_results
  if (lname === 'attempt_completion' || lname === 'attempt-completion' || lname === 'attemptcompletion') return null;

  // If we have a run object recorded, prefer its structured sanitized data
  if (run && typeof run === 'object') {
    // For view/grep and enhanced Warp-style tools we return the sanitized result directly
    if (lname === 'view' || lname === 'grep' || lname === 'context_search' || lname === 'context-search' || 
        lname === 'find_files' || lname === 'ls' || lname === 'add_todos' || lname === 'create_todo_list' || 
        lname === 'read_todos' || lname === 'mark_todo_as_done' || lname === 'remove_todos' || lname === 'ask') {
      return stripTransient(run.result || null);
    }
    // For other tools, return a structured object containing success/result/summary
    const res = {};
    res.success = typeof run.success === 'boolean' ? run.success : !!(run.result && typeof run.result.error === 'undefined');
    if (typeof run.result !== 'undefined') res.result = stripTransient(run.result);
    if (run.summary && Object.keys(run.summary).length > 0) res.summary = run.summary;
    return res;
  }

  // If no run, try to salvage from originalObj's tool_system_results/tool_system_response or result
  if (originalObj && typeof originalObj === 'object') {
    if (typeof originalObj.tool_system_results !== 'undefined') {
      const ts = stripTransient(originalObj.tool_system_results);
      // For view/grep and enhanced Warp-style tools this will already be a sanitized result object.
      if (lname === 'view' || lname === 'grep' || lname === 'context_search' || lname === 'context-search' ||
          lname === 'find_files' || lname === 'ls' || lname === 'add_todos' || lname === 'create_todo_list' ||
          lname === 'read_todos' || lname === 'mark_todo_as_done' || lname === 'remove_todos' || lname === 'ask') {
        return ts;
      }
      return ts;
    } else if (typeof originalObj.tool_system_response !== 'undefined') {
      const tsr = stripTransient(originalObj.tool_system_response);
      if (lname === 'view' || lname === 'grep' || lname === 'context_search' || lname === 'context-search' ||
          lname === 'find_files' || lname === 'ls' || lname === 'add_todos' || lname === 'create_todo_list' ||
          lname === 'read_todos' || lname === 'mark_todo_as_done' || lname === 'remove_todos') {
        if (tsr && typeof tsr.result !== 'undefined') return stripTransient(tsr.result);
        return tsr;
      }
      return tsr;
    } else if (originalObj.result) {
      return stripTransient(originalObj.result);
    }
  }

  // Nothing available
  return null;
}

/**
 * removeOrphanToolResultJsonLocal(text)
 * Similar heuristic to parser.removeOrphanToolResultJson but local to this module (used post-embedding)
 * IMPORTANT: do not remove JSON that lives inside <tool_use>...</tool_use> blocks.
 */
function removeOrphanToolResultJsonLocal(text) {
  if (!text || typeof text !== 'string') return text;
  const fences = findCodeFencesLocal(text);
  const toolRanges = findToolUseRangesLocal(text);

  const isInsideFence = (idx) => {
    for (const f of fences) if (idx >= f.start && idx < f.end) return true;
    return false;
  };
  const isInsideToolUse = (idx) => {
    for (const t of toolRanges) if (idx >= t.start && idx < t.end) return true;
    return false;
  };

  let out = '';
  let i = 0;
  while (i < text.length) {
    const ch = text[i];
    // Only attempt to strip JSON blobs that are not inside code fences and not inside <tool_use> blocks
    if (ch === '{' && !isInsideFence(i) && !isInsideToolUse(i)) {
      const end = findMatchingJsonEnd(text, i);
      if (end !== -1) {
        const candidate = text.slice(i, end + 1);
        if (looksLikeToolResultLocal(candidate)) {
          // Additional guard: only strip if this JSON appears very close after a closing </tool_use>
          // to avoid deleting legitimate JSON content elsewhere.
          const prevCloseIdx = text.lastIndexOf('</tool_use>', i);
          const nearAfterTool = prevCloseIdx !== -1 && (i - prevCloseIdx) <= 200; // within 200 chars
          if (nearAfterTool) {
            // Skip the JSON object and any trailing annotation lines (Note:, Warning:, etc.)
            let j = end + 1;
            j = skipAnnotationLinesLocal(text, j);
            i = j;
            continue;
          }
        }
      }
    }
    out += ch;
    i++;
  }
  return out;
}

/**
 * findToolUseRangesLocal(text)
 * Returns array of {start, end} ranges for <tool_use>...</tool_use> blocks in the text.
 * Used to avoid removing/altering JSON that lives inside these blocks.
 */
function findToolUseRangesLocal(text) {
  const ranges = [];
  const re = /<tool_use>[\s\S]*?<\/tool_use>/gi;
  let m;
  while ((m = re.exec(text)) !== null) {
    ranges.push({ start: m.index, end: re.lastIndex });
  }
  return ranges;
}

function looksLikeToolResultLocal(jsonStr) {
  try {
    const obj = JSON.parse(jsonStr);
    if (!obj || typeof obj !== 'object') return false;
    const keys = Object.keys(obj);
    const hasViewKeys = ('content' in obj) || ('total_line_count' in obj) || ('is_truncated' in obj);
    const hasLsKeys = Array.isArray(obj.entries);
    const hasRunKeys = (obj.finished && typeof obj.finished.exit_code !== 'undefined');
    const hasTxtlizeKeys = ('output_path' in obj) || ('file_count' in obj) || ('total_size_bytes' in obj);
    const hasSuccessOnly = ('success' in obj) && keys.length <= 4;
    const hasGenericError = ('error' in obj) && keys.length <= 3;
    // Grep-specific detection
    const hasGrepKeys = ('pattern_stats' in obj) || ('matched_files' in obj) || ('matched_files_count' in obj) || (Array.isArray(obj.pattern_stats));
    // Todo tool detection
    const hasTodoKeys = ('todo_list' in obj) || ('pending_todos' in obj) || ('completed_todos' in obj) || ('added_ids' in obj) || ('removed_ids' in obj) || ('completed_ids' in obj);
    // Enhanced Warp-style tools detection
    const hasEnhancedWarpDisplay = ('display_content' in obj) || ('summary' in obj && typeof obj.summary === 'object');
    return hasViewKeys || hasLsKeys || hasRunKeys || hasTxtlizeKeys || hasSuccessOnly || hasGenericError || hasGrepKeys || hasTodoKeys || hasEnhancedWarpDisplay;
  } catch (_) {
    return false;
  }
}

// Aggressive cleaner for rendering paths: remove any tool-result looking JSON outside code fences and outside <tool_use> blocks
function removeAllToolResultJsonLocal(text) {
  if (!text || typeof text !== 'string') return text;
  const fences = findCodeFencesLocal(text);
  const toolRanges = findToolUseRangesLocal(text);
  const isInsideFence = (idx) => { for (const f of fences) if (idx >= f.start && idx < f.end) return true; return false; };
  const isInsideToolUse = (idx) => { for (const t of toolRanges) if (idx >= t.start && idx < t.end) return true; return false; };
  let out = '';
  let i = 0;
  while (i < text.length) {
    const ch = text[i];
    if (ch === '{' && !isInsideFence(i) && !isInsideToolUse(i)) {
      const end = findMatchingJsonEnd(text, i);
      if (end !== -1) {
        const candidate = text.slice(i, end + 1);
        if (looksLikeToolResultLocal(candidate)) { i = end + 1; continue; }
      }
    }
    out += ch;
    i++;
  }
  return out;
}

function findCodeFencesLocal(text) {
  const ranges = [];
  const re = /```[\s\S]*?```/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    ranges.push({ start: m.index, end: re.lastIndex });
  }
  return ranges;
}

/**
 * findMatchingJsonEnd(text, startIndex)
 * Simple JSON object matcher that accounts for quoted strings and escapes.
 * Returns index of matching '}' or -1 if not found.
 */
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
      if (escape) {
        escape = false;
        continue;
      }
      if (ch === '\\') { escape = true; continue; }
      if (ch === stringChar) { inString = false; stringChar = null; continue; }
      continue;
    } else {
      if (ch === '"' || ch === "'") { inString = true; stringChar = ch; continue; }
      if (ch === '{') { depth++; continue; }
      if (ch === '}') {
        depth--;
        if (depth === 0) return i;
        continue;
      }
    }
  }
  return -1;
}

function animateLatestGptMessage() {
  const container = document.getElementById('chat-messages');
  if (!container) return;
  const gpts = container.querySelectorAll('.message.gpt-message');
  if (!gpts || !gpts.length) return;
  const last = gpts[gpts.length - 1];
  const content = last ? last.querySelector('.message-content') : null;
  if (!content) return;
  const kids = Array.from(content.children || []).filter(el => !el.classList.contains('thinking-inline'));
  let idx = 0;
  for (const el of kids) {
    if (!el.classList.contains('fade-line')) {
      try {
        el.classList.add('fade-line');
        el.style.setProperty('--delay', `${idx * 60}ms`);
      } catch (_) {}
      idx++;
    }
  }
}

function maybeSmoothScrollToBottomIfNeeded() {
  const container = document.getElementById('chat-messages');
  if (!container) return;
  const nearBottom = (container.scrollHeight - (container.scrollTop + container.clientHeight)) <= 24;
  const doNotAuto = (() => {
    try {
      const last = window.chatUserLastManualScrollAt || 0;
      return (!nearBottom && (Date.now() - last) <= 20000);
    } catch (_) { return false; }
  })();
  if (doNotAuto) return;
  try { smoothScrollTo(container, container.scrollHeight, { axis: 'y', duration: 240 }); } catch (_) {}
}

function renderLatestGptMessageInline(text, toolRuns, entryIndex = null) {
  try { marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() }); } catch (_) {}
  const container = document.getElementById('chat-messages');
  if (!container) return;
  let targetMsg = null;
  if (entryIndex != null) {
    targetMsg = container.querySelector(`.message.gpt-message[data-entry-index="${String(entryIndex)}"]`);
  }
  if (!targetMsg) {
    const gpts = container.querySelectorAll('.message.gpt-message');
    targetMsg = gpts && gpts.length ? gpts[gpts.length - 1] : null;
  }
  if (!targetMsg) return;
  const contentEl = targetMsg.querySelector('.message-content');
  if (!contentEl) return;
  const parsed = extractToolUsesAndSanitize(text || '');
  const cleanedText = parsed.sanitizedText;
  contentEl.innerHTML = marked.parse(cleanedText);
  // Ensure inline thinking is cleared on full re-render
  try { contentEl.querySelectorAll('.thinking-inline').forEach(n => n.remove()); } catch (_) {}
  try { enhanceToolPlaceholders(contentEl, parsed.tools, Array.isArray(toolRuns) ? toolRuns : []); } catch (_) {}
  try { setupCodeBlockListeners(); } catch (_) {}
}

module.exports = { sendMessage: MainAssistant, MainAssistant, setupCodeBlockListeners, embedToolResultsForRendering, embedToolResultsInGptResponse };
