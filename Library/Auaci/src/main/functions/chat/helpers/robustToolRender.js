// src/main/functions/chat/helpers/robustToolRender.js
// Robust tool rendering that handles errors gracefully and works with sanitized data

const { renderToolExecuting, renderToolCompleted, isToolExecuting, getToolHeaderTitle } = require('./toolRenders');
const { escapeHtmlLite, okIconSvg, failIconSvg, pendingIconSvg } = require('./toolRenders/shared');

/**
 * Safely render a tool box with error handling
 * @param {HTMLElement} box - The tool box element
 * @param {object} toolData - Tool data (name, input, result)
 * @param {object} options - Render options
 */
function safeRenderToolBox(box, toolData, options = {}) {
  if (!box) return;
  
  const { name, input, result } = toolData || {};
  const toolName = String(name || box.getAttribute('data-tool') || 'tool');
  const toolIndex = options.toolIndex || box.getAttribute('data-tool-index') || '0';
  
  try {
    applyTraceMeta(box, result);

    // Ensure .tool-body exists
    let body = box.querySelector('.tool-body');
    if (!body) {
      body = document.createElement('div');
      body.className = 'tool-body';
      box.appendChild(body);
    }
    
    // Check if tool is still executing
    const executing = isToolExecuting(result);
    
    // Update header
    updateToolHeader(box, toolName, executing, result);
    
    // Render body content
    let html = '';
    if (executing) {
      box.classList.add('tool-executing');
      html = renderToolExecuting(toolName, input || {}, toolIndex);
    } else {
      box.classList.remove('tool-executing');
      html = renderToolCompleted(toolName, input || {}, result || {}, { toolIndex });
    }
    
    body.innerHTML = html;
    
    // Wire up interactive elements if needed
    wireInteractiveElements(box, toolName, input, result, toolIndex);
    
  } catch (err) {
    console.error(`[robustToolRender] Error rendering ${toolName}:`, err);
    renderErrorState(box, toolName, err);
  }
}

/**
 * Update the tool header with appropriate title
 */
function updateToolHeader(box, toolName, isExecuting, result) {
  try {
    let header = box.querySelector('.tool-header');
    if (!header) {
      header = document.createElement('div');
      header.className = 'tool-header';
      box.insertBefore(header, box.firstChild);
    }
    
    let nameEl = header.querySelector('.auaci-tool-name');
    if (!nameEl) {
      nameEl = document.createElement('span');
      nameEl.className = 'auaci-tool-name';
      header.appendChild(nameEl);
    }
    
    nameEl.textContent = getToolHeaderTitle(toolName, isExecuting, result);

    // Optional trace info (duration, error code)
    const meta = result && result._meta ? result._meta : null;
    let metaEl = header.querySelector('.auaci-tool-meta');
    if (!metaEl) {
      metaEl = document.createElement('span');
      metaEl.className = 'auaci-tool-meta';
      metaEl.style.marginLeft = '8px';
      metaEl.style.fontSize = '11px';
      metaEl.style.color = '#6b7280';
      header.appendChild(metaEl);
    }

    if (meta && meta.metrics && typeof meta.metrics.duration_ms === 'number' && !isExecuting) {
      const ms = Math.max(0, Math.round(meta.metrics.duration_ms));
      const err = meta.error_code ? ` • ${meta.error_code}` : '';
      metaEl.textContent = `(${ms}ms${err})`;
      metaEl.style.display = 'inline';
    } else {
      metaEl.textContent = '';
      metaEl.style.display = 'none';
    }
  } catch (_) {}
}

function applyTraceMeta(box, result) {
  if (!box || !result || !result._meta) return;
  const meta = result._meta || {};
  if (meta.status) box.setAttribute('data-tool-status', String(meta.status));
  if (meta.error_code) box.setAttribute('data-tool-error-code', String(meta.error_code));
  if (meta.metrics && typeof meta.metrics.duration_ms === 'number') {
    box.setAttribute('data-tool-duration-ms', String(Math.round(meta.metrics.duration_ms)));
  }
}

/**
 * Wire up interactive elements for tools that need them
 */
function wireInteractiveElements(box, toolName, input, result, toolIndex) {
  const lname = String(toolName).toLowerCase();
  
  // Ask tool - wire up confirm button
  if (lname === 'ask' && isToolExecuting(result)) {
    wireAskTool(box, input, toolIndex);
  }
  
  // Terminal scroll behavior
  if (lname === 'bash' || lname === 'run_command') {
    wireTerminalScroll(box);
  }
}

/**
 * Wire up ask tool interactive elements
 */
function wireAskTool(box, input, toolIndex) {
  const confirmBtn = box.querySelector('.ask-confirm-btn');
  if (confirmBtn && !confirmBtn.dataset.wired) {
    confirmBtn.dataset.wired = 'true';
    confirmBtn.addEventListener('click', (e) => {
      e.preventDefault();
      
      const q = (input && input.question) || '';
      const mode = (input && input.mode) ? String(input.mode).toLowerCase() : 'free';
      const opts = Array.isArray(input && input.options) ? input.options : [];
      
      let userInput = '';
      let selectedValues = [];
      
      if (mode === 'free') {
        const ta = box.querySelector('textarea.ask-input');
        userInput = ta ? String(ta.value || '').trim() : '';
      } else if (mode === 'multi') {
        const checks = box.querySelectorAll('input.ask-opt[type="checkbox"]');
        selectedValues = Array.from(checks).filter(el => el.checked).map(el => String(el.getAttribute('data-value') || ''));
      } else {
        const radios = box.querySelectorAll('input.ask-opt[type="radio"]');
        for (const el of Array.from(radios)) {
          if (el.checked) {
            selectedValues = [String(el.getAttribute('data-value') || '')];
            break;
          }
        }
      }
      
      // Dispatch event
      window.dispatchEvent(new CustomEvent('auaci:ask-answered', {
        detail: {
          tool_index: String(toolIndex || ''),
          question: q,
          mode,
          options: opts,
          user_input: userInput,
          selected_values: selectedValues
        }
      }));
      
      // Disable button
      confirmBtn.disabled = true;
      confirmBtn.textContent = 'Submitted';
      confirmBtn.style.background = '#9ca3af';
    });
  }
}

/**
 * Wire up an element to show scrollbar while scrolling/hovering, then hide after inactivity
 */
function wireAutoHideScrollbar(el, options = {}) {
  if (!el || el.__auaciScrollbarWired) return;
  el.__auaciScrollbarWired = true;
  const hideDelay = Number(options.hideDelay || 900);
  const leaveDelay = Number(options.leaveDelay || 300);

  function clearTimer() {
    if (el.__auaciHideTimer) {
      clearTimeout(el.__auaciHideTimer);
      el.__auaciHideTimer = null;
    }
  }

  function scheduleHide(delay = hideDelay) {
    clearTimer();
    el.__auaciHideTimer = setTimeout(() => {
      try { el.classList.remove('show-scrollbar'); } catch(_) {}
      el.__auaciHideTimer = null;
    }, delay);
  }

  function showAndSchedule() {
    try { el.classList.add('show-scrollbar'); } catch(_) {}
    scheduleHide();
  }

  el.addEventListener('scroll', () => {
    showAndSchedule();
  }, { passive: true });

  el.addEventListener('mouseenter', () => {
    showAndSchedule();
  });

  el.addEventListener('mouseleave', () => {
    clearTimer();
    scheduleHide(leaveDelay);
  });
}

/**
 * Wire terminal and other scrollable elements inside a tool box
 */
function wireTerminalScroll(box) {
  if (!box) return;
  const terminal = box.querySelector('.terminal-scroll');
  if (terminal) wireAutoHideScrollbar(terminal, { hideDelay: 900, leaveDelay: 300 });

  // Also wire common horizontal scroll blocks inside the tool box
  const codeBlocks = Array.from(box.querySelectorAll('.tool-code, .patch-hunk, pre'));
  for (const cb of codeBlocks) {
    wireAutoHideScrollbar(cb, { hideDelay: 900, leaveDelay: 300 });
  }
}

/**
 * Render an error state for a tool box
 */
function renderErrorState(box, toolName, error) {
  try {
    let body = box.querySelector('.tool-body');
    if (!body) {
      body = document.createElement('div');
      body.className = 'tool-body';
      box.appendChild(body);
    }
    
    const errorMsg = error?.message || String(error);
    body.innerHTML = `
      <div class="tool-line">
        ${failIconSvg()}
        <span class="tool-sub" style="margin-left:6px;color:#b00020;">
          Render error: ${escapeHtmlLite(errorMsg.slice(0, 100))}
        </span>
      </div>
    `;
    
    // Update header to show error
    updateToolHeader(box, toolName, false, { error: errorMsg });
  } catch (_) {
    // Last resort - just show something
    if (box) {
      box.innerHTML = '<div class="tool-body"><div class="tool-line" style="color:#b00020;">Tool render failed</div></div>';
    }
  }
}

/**
 * Batch render multiple tool boxes efficiently
 * @param {HTMLElement} container - Container with tool boxes
 * @param {Array} tools - Array of tool data
 * @param {Array} toolRuns - Array of tool run records
 */
function batchRenderToolBoxes(container, tools, toolRuns = []) {
  if (!container) return;
  
  const boxes = Array.from(container.querySelectorAll('.auaci-tool-box'));
  if (boxes.length === 0) return;
  
  // Ensure CSS is injected
  ensureToolStyles();
  
  // Build a map of runs by uid for robust matching
  const runsByUid = new Map();
  for (let i = 0; i < (toolRuns || []).length; i++) {
    const run = toolRuns[i];
    if (run && run.__uid) {
      runsByUid.set(String(run.__uid), { run, index: i });
    }
  }
  
  // Render each box
  for (let i = 0; i < boxes.length; i++) {
    const box = boxes[i];
    const uid = box.getAttribute('data-tool-uid');
    
    // Match tool data
    let toolData = null;
    if (uid && runsByUid.has(uid)) {
      const matched = runsByUid.get(uid);
      toolData = matched.run;
    } else {
      // Fallback to index-based matching
      const tool = (Array.isArray(tools) && tools[i]) ? tools[i] : null;
      const run = (Array.isArray(toolRuns) && toolRuns[i]) ? toolRuns[i] : null;
      
      toolData = {
        name: run?.name || tool?.name || box.getAttribute('data-tool') || '',
        input: run?.input || tool?.input || {},
        result: run?.result || tool?.result || tool?.tool_system_response || null
      };
    }
    
    safeRenderToolBox(box, toolData, { toolIndex: String(i) });
  }
}

/**
 * Ensure tool CSS styles are injected
 */
function ensureToolStyles() {
  if (typeof document === 'undefined') return;
  if (document.getElementById('auaci-robust-tool-styles')) return;
  
  const style = document.createElement('style');
  style.id = 'auaci-robust-tool-styles';
  style.textContent = `
    .no-scrollbar::-webkit-scrollbar { display: none; }
    .no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
    
    .tool-code { max-width: 100%; overflow-x: auto; white-space: pre; padding:8px; background:#0b1220; color:#e6edf3; border-radius:6px; }
    .tool-code::-webkit-scrollbar { height: 8px; background: transparent; }
    .tool-code::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0); border-radius: 8px; transition: background-color 160ms ease; }
    .tool-code.show-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0.25); }
    .tool-code { scrollbar-color: transparent transparent; scrollbar-width: thin; }
    .tool-code.show-scrollbar { scrollbar-color: rgba(255,255,255,0.25) transparent; }
    .tool-code.light { background:#f6f6f6; color:#111; }
    .summary-box pre { margin:0; }
    
    @keyframes tool-spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
    .tool-icon.spinner {
      animation: tool-spin 1s linear infinite;
    }
    .tool-executing {
      opacity: 0.9;
    }
    .tool-executing .tool-body {
      background: linear-gradient(90deg, #f8f9fa 0%, #f1f5f9 50%, #f8f9fa 100%);
      background-size: 200% 100%;
      animation: tool-shimmer 1.5s ease-in-out infinite;
    }
    @keyframes tool-shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }

    .tool-terminal { background:#000; color:#fff; }
    .tool-terminal .terminal-scroll { background:#000; max-height: calc(1.35em * 8); overflow: auto; }
    .tool-terminal .terminal-line, .tool-terminal .terminal-output { background:#000; color:#fff; }
    .tool-terminal .terminal-output { white-space: pre; }
    .tool-terminal, .tool-terminal * { background:#000 !important; color:#fff !important; }
    
    .tool-terminal .terminal-scroll::-webkit-scrollbar { width: 8px; height: 8px; background: transparent; }
    .tool-terminal .terminal-scroll::-webkit-scrollbar-track { background: transparent; }
    .tool-terminal .terminal-scroll::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0); border-radius: 8px; transition: background-color 160ms ease; }
    .tool-terminal .terminal-scroll.show-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0.35); }
    .tool-terminal .terminal-scroll { scrollbar-color: transparent transparent; scrollbar-width: thin; }
    .tool-terminal .terminal-scroll.show-scrollbar { scrollbar-color: rgba(255,255,255,0.35) transparent; }
    /* ensure horizontal code blocks also use auto-hide thumb */
    .patch-hunk::-webkit-scrollbar, pre::-webkit-scrollbar { height:8px; }
    .patch-hunk::-webkit-scrollbar-thumb, pre::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0); border-radius:8px; transition: background-color 160ms ease; }
    .patch-hunk.show-scrollbar::-webkit-scrollbar-thumb, pre.show-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0.25); }
    .patch-hunk { scrollbar-color: transparent transparent; scrollbar-width: thin; }
    .patch-hunk.show-scrollbar { scrollbar-color: rgba(255,255,255,0.25) transparent; }

    .todo-list { margin: 4px 0; }
    .todo-item { display:flex; align-items:flex-start; gap:8px; padding:6px 8px; border-radius:8px; }
    .todo-item.new { background: rgba(52, 211, 153, 0.12); outline: 1px solid rgba(16,185,129,0.35); }
    .todo-item .todo-text { color:#111; line-height:1.35; }
    .todo-item.completed .todo-text { color:#6b7280; text-decoration: line-through; }
    .todo-item .todo-details { color:#6b7280; font-size: 12px; margin-top: 2px; }
    .todo-icon { flex: 0 0 auto; margin-top: 1px; }

    .unrecognized-tool-box {
      background:#FEF2F2;
      border:1px solid #FCA5A5;
      color:#991B1B;
      border-radius:10px;
      padding:10px;
      margin:8px 0;
      font-weight:600;
    }
  `;
  document.head.appendChild(style);
}

module.exports = {
  safeRenderToolBox,
  batchRenderToolBoxes,
  renderErrorState,
  ensureToolStyles
};
