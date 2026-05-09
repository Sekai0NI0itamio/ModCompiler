// src/main/functions/chat/concurrent/sessionRenderer.js
// Handles rendering restoration when switching to a session
// Restores DOM state, streaming, thinking timers, and tool animations

const { getSessionState, SESSION_STATUS } = require('./sessionStateManager');

/**
 * Restore session render state when switching to a session
 * @param {string} sessionId - The session to restore
 * @param {HTMLElement} container - The chat messages container
 * @param {Object} options - Additional options
 * @returns {Object} Restoration result with state info
 */
async function restoreSessionRender(sessionId, container, options = {}) {
  if (!sessionId || !container) {
    return { success: false, reason: 'missing-params' };
  }
  
  const state = getSessionState(sessionId);
  if (!state) {
    return { success: false, reason: 'no-state' };
  }
  
  console.log(`[SessionRenderer] Restoring session ${sessionId}, status: ${state.status}, isResponding: ${state.isResponding}, hasCapturedDom: ${!!state.capturedDomHtml}, streamBuffer length: ${state.streamBuffer?.length || 0}`);
  
  // Don't restore for completed/idle sessions - let history display handle them
  if (state.status === SESSION_STATUS.COMPLETED || state.status === SESSION_STATUS.IDLE) {
    console.log(`[SessionRenderer] Session ${sessionId} is ${state.status}, skipping restoration`);
    return { success: false, reason: 'session-not-active', status: state.status };
  }
  
  const result = {
    success: true,
    sessionId,
    status: state.status,
    isResponding: state.isResponding,
    thinkingSeconds: state.getThinkingSeconds(),
    hasCapturedDom: !!state.capturedDomHtml,
    hasStreamBuffer: !!state.streamBuffer,
    toolCount: state.toolRuns.length
  };
  
  try {
    // If we have captured DOM and session is still active, restore it
    // This replaces the history-loaded content with the live streaming content
    if (state.capturedDomHtml && state.isResponding) {
      console.log(`[SessionRenderer] Restoring from captured DOM (${state.capturedDomHtml.length} chars)`);
      
      // Performance optimization: Use document fragment for single DOM insertion
      const fragment = document.createDocumentFragment();
      const tempDiv = document.createElement('div');
      tempDiv.innerHTML = state.capturedDomHtml;
      while (tempDiv.firstChild) {
        fragment.appendChild(tempDiv.firstChild);
      }
      container.innerHTML = '';
      container.appendChild(fragment);
      
      // Restore scroll position
      if (typeof state.capturedScrollTop === 'number') {
        // Use immediate scroll to avoid animation
        container.style.scrollBehavior = 'auto';
        container.scrollTop = state.capturedScrollTop;
        container.style.scrollBehavior = '';
      }
      
      result.restoredFromCapture = true;
      
      // Setup code block listeners and return early - captured DOM has everything
      try {
        const { setupCodeBlockListeners } = require('../helpers/dom');
        setupCodeBlockListeners();
      } catch (_) {}
      
      return result;
    } else if (state.isResponding && (state.streamBuffer || state.accumulatedContent)) {
      // No captured DOM but we have stream buffer or accumulated content - render the current state
      // This handles the case where streaming started but DOM wasn't captured yet
      const contentToRender = state.accumulatedContent || state.streamBuffer || '';
      console.log(`[SessionRenderer] Restoring from content (${contentToRender.length} chars), toolRuns: ${state.toolRuns?.length || 0}`);
      
      const gptMessages = container.querySelectorAll('.message.gpt-message');
      console.log(`[SessionRenderer] Found ${gptMessages.length} GPT messages in container`);
      
      const lastGpt = gptMessages.length > 0 ? gptMessages[gptMessages.length - 1] : null;
      
      if (lastGpt) {
        const gptContent = lastGpt.querySelector('.message-content');
        console.log(`[SessionRenderer] Found gptContent: ${!!gptContent}`);
        
        if (gptContent) {
          // Use renderGptResponse to properly render tools
          try {
            const { renderGptResponse } = require('../send/sendMessage');
            // Ensure toolRuns is always a valid array to prevent undefined length errors
            const toolRuns = Array.isArray(state.toolRuns) ? state.toolRuns : [];
            renderGptResponse(gptContent, contentToRender, toolRuns);
            console.log(`[SessionRenderer] Rendered with renderGptResponse, toolRuns: ${toolRuns.length}`);
          } catch (e) {
            // Fallback to basic marked rendering if renderGptResponse fails
            console.warn(`[SessionRenderer] renderGptResponse failed, falling back to marked:`, e);
            const marked = require('marked');
            const { createRenderer } = require('../renderer');
            try {
              marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });
            } catch (_) {}
            gptContent.innerHTML = contentToRender ? marked.parse(contentToRender) : '';
          }
          
          // Scroll to bottom after restoring content
          try {
            container.scrollTop = container.scrollHeight;
          } catch (_) {}
          
          result.restoredFromStreamBuffer = true;
        } else {
          console.log(`[SessionRenderer] No .message-content found in last GPT message`);
        }
      } else {
        // No GPT message found - create one
        console.log(`[SessionRenderer] No GPT messages found, creating placeholder`);
        
        const gptDiv = document.createElement('div');
        gptDiv.className = 'message gpt-message';
        const gptContent = document.createElement('div');
        gptContent.className = 'message-content';
        
        // Use renderGptResponse to properly render tools
        try {
          const { renderGptResponse } = require('../send/sendMessage');
          const toolRuns = state.toolRuns || [];
          const contentToRender = state.accumulatedContent || state.streamBuffer || '';
          renderGptResponse(gptContent, contentToRender, toolRuns);
        } catch (e) {
          // Fallback
          const marked = require('marked');
          const { createRenderer } = require('../renderer');
          try {
            marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });
          } catch (_) {}
          const contentToRender = state.accumulatedContent || state.streamBuffer || '';
          gptContent.innerHTML = contentToRender ? marked.parse(contentToRender) : '';
        }
        
        gptDiv.appendChild(gptContent);
        container.appendChild(gptDiv);
        
        // Scroll to bottom
        try {
          container.scrollTop = container.scrollHeight;
        } catch (_) {}
        
        result.restoredFromStreamBuffer = true;
        result.createdGptPlaceholder = true;
      }
    } else if (state.isResponding && state.previousRenderedHtml) {
      // Fallback: use previous rendered HTML if available
      console.log(`[SessionRenderer] Restoring from previous rendered HTML`);
      
      const gptMessages = container.querySelectorAll('.message.gpt-message');
      const lastGpt = gptMessages.length > 0 ? gptMessages[gptMessages.length - 1] : null;
      
      if (lastGpt) {
        const gptContent = lastGpt.querySelector('.message-content');
        if (gptContent) {
          // Use renderGptResponse for proper tool rendering
          try {
            const { renderGptResponse } = require('../send/sendMessage');
            const toolRuns = state.toolRuns || [];
            renderGptResponse(gptContent, state.previousRenderedHtml, toolRuns);
          } catch (e) {
            gptContent.innerHTML = state.previousRenderedHtml;
          }
          result.restoredFromAccumulated = true;
        }
      }
    }
    
    // If session is responding and has no stream content yet, show thinking indicator
    // Don't show thinking if we already have streaming content
    if (state.isResponding && !state.streamBuffer && !result.restoredFromCapture) {
      await restoreThinkingIndicator(sessionId, container, state);
      result.restoredThinking = true;
    }
    
    // If session has executing tool, ensure animation is shown
    if (state.status === SESSION_STATUS.EXECUTING_TOOL && state.executingToolIndex !== null) {
      restoreToolAnimation(container, state);
      result.restoredToolAnimation = true;
    }
    
    // If session is awaiting input, ensure ask tool UI is ready
    if (state.status === SESSION_STATUS.AWAITING_INPUT) {
      restoreAskToolUI(container, state);
      result.restoredAskTool = true;
    }
    
    // Setup code block listeners
    try {
      const { setupCodeBlockListeners } = require('../helpers/dom');
      setupCodeBlockListeners();
    } catch (_) {}
    
  } catch (e) {
    console.error('[SessionRenderer] Error restoring session:', e);
    result.success = false;
    result.error = e.message;
  }
  
  return result;
}

/**
 * Performance optimized thinking indicator restoration
 */
async function restoreThinkingIndicator(sessionId, container, state) {
  try {
    // Performance optimization: Use cached element if available
    const cachedIndicator = window.__auaciCachedThinkingIndicators?.[sessionId];
    if (cachedIndicator && cachedIndicator.element && cachedIndicator.element.parentNode) {
      // Already exists, just update the timer
      setupOptimizedThinkingTimer(sessionId, cachedIndicator.element, state);
      return;
    }
    
    // Find the last GPT message content
    const gptMessages = container.querySelectorAll('.message.gpt-message');
    const lastGpt = gptMessages.length > 0 ? gptMessages[gptMessages.length - 1] : null;
    const content = lastGpt ? lastGpt.querySelector('.message-content') : null;
    
    if (!content) return;
    
    // Remove any existing thinking indicators
    content.querySelectorAll('.thinking-inline').forEach(el => el.remove());
    
    // Calculate elapsed seconds from stored start time
    const elapsedSeconds = state.getThinkingSeconds();
    
    // Performance optimization: Use document fragment
    const fragment = document.createDocumentFragment();
    const el = document.createElement('div');
    el.className = 'thinking-inline';
    el.innerHTML = `
      <span class=\"typing-text\">GPT is thinking (<span class=\"thinking-seconds\">${elapsedSeconds}</span>s)</span>
      <span class=\"gpt-indicator-dots\">
        <span class=\"dot\"></span><span class=\"dot\"></span><span class=\"dot\"></span>
      </span>
    `;
    fragment.appendChild(el);
    content.appendChild(fragment);
    
    // Cache the element
    window.__auaciCachedThinkingIndicators = window.__auaciCachedThinkingIndicators || {};
    window.__auaciCachedThinkingIndicators[sessionId] = { element: el };
    
    // Setup optimized timer
    setupOptimizedThinkingTimer(sessionId, el, state);
    
    // Also show global indicator
    try {
      const { showGptIndicator } = require('../ui/indicator');
      showGptIndicator(sessionId);
    } catch (_) {}
    
  } catch (e) {
    console.warn('[SessionRenderer] Error restoring thinking indicator:', e);
  }
}

/**
 * Performance optimized thinking timer using requestAnimationFrame
 */
function setupOptimizedThinkingTimer(sessionId, element, state) {
  // Clear existing timer
  if (window.__auaciInlineThinkingTimers?.[sessionId]) {
    cancelAnimationFrame(window.__auaciInlineThinkingTimers[sessionId]);
  }
  
  const update = () => {
    const secondsEl = element.querySelector('.thinking-seconds');
    if (!secondsEl || !secondsEl.parentNode) {
      window.__auaciInlineThinkingTimers[sessionId] = null;
      return;
    }
    
    const secs = state.getThinkingSeconds();
    secondsEl.textContent = String(secs);
    
    // Schedule next update
    window.__auaciInlineThinkingTimers[sessionId] = requestAnimationFrame(update);
  };
  
  // Start the animation frame loop
  window.__auaciInlineThinkingTimers = window.__auaciInlineThinkingTimers || {};
  window.__auaciInlineThinkingTimers[sessionId] = requestAnimationFrame(update);
}

/**
 * Restore tool animation for executing tool
 */
function restoreToolAnimation(container, state) {
  try {
    const toolBoxes = container.querySelectorAll('.auaci-tool-box');
    
    for (let i = 0; i < toolBoxes.length; i++) {
      const box = toolBoxes[i];
      const toolIndex = parseInt(box.getAttribute('data-tool-index') || String(i), 10);
      
      if (toolIndex === state.executingToolIndex) {
        // Add executing class for animation
        box.classList.add('tool-executing');
        
        // Ensure spinner is visible
        const header = box.querySelector('.tool-header');
        if (header) {
          const nameEl = header.querySelector('.auaci-tool-name');
          if (nameEl && state.executingToolName) {
            nameEl.textContent = `Executing ${state.executingToolName}...`;
          }
        }
        
        break;
      }
    }
  } catch (e) {
    console.warn('[SessionRenderer] Error restoring tool animation:', e);
  }
}

/**
 * Restore ask tool UI for awaiting input state
 */
function restoreAskToolUI(container, state) {
  try {
    const toolBoxes = container.querySelectorAll('.auaci-tool-box');
    
    for (let i = 0; i < toolBoxes.length; i++) {
      const box = toolBoxes[i];
      const toolIndex = parseInt(box.getAttribute('data-tool-index') || String(i), 10);
      const toolName = box.getAttribute('data-tool') || '';
      
      if (toolIndex === state.executingToolIndex && toolName.toLowerCase() === 'ask') {
        // Ensure the ask tool input form is visible and enabled
        const confirmBtn = box.querySelector('.ask-confirm-btn');
        if (confirmBtn) {
          confirmBtn.disabled = false;
          confirmBtn.textContent = 'Submit Answer';
          confirmBtn.style.background = '';
        }
        
        // Focus the input if present
        const textarea = box.querySelector('textarea.ask-input');
        if (textarea) {
          textarea.focus();
        }
        
        break;
      }
    }
  } catch (e) {
    console.warn('[SessionRenderer] Error restoring ask tool UI:', e);
  }
}

/**
 * Get streaming renderer state for a session
 * Used to resume streaming when switching back
 */
function getStreamingState(sessionId) {
  const state = getSessionState(sessionId);
  if (!state) return null;
  
  return {
    streamBuffer: state.streamBuffer,
    accumulatedContent: state.accumulatedContent,
    previousRenderedHtml: state.previousRenderedHtml,
    isStreaming: state.status === SESSION_STATUS.STREAMING && state.isResponding
  };
}

/**
 * Create a streaming renderer that syncs with session state
 */
function createSessionAwareStreamingRenderer(sessionId, containerEl) {
  const state = getSessionState(sessionId);
  
  // Import the base StreamingRenderer
  // We'll create a wrapper that syncs with session state
  return {
    container: containerEl,
    sessionId,
    
    update(currentResponseText) {
      if (!state) return;
      
      // Update session state
      state.updateStreamBuffer(currentResponseText, '');
      
      // Render if this is the active session
      const { shouldRenderForSession } = require('../helpers/renderGate');
      if (shouldRenderForSession(sessionId)) {
        // Use the standard rendering logic
        const marked = require('marked');
        const { createRenderer } = require('../renderer');
        
        try {
          marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });
        } catch (_) {}
        
        const html = state.previousRenderedHtml + (currentResponseText ? marked.parse(currentResponseText) : '');
        containerEl.innerHTML = html;
        
        try {
          const { setupCodeBlockListeners } = require('../helpers/dom');
          setupCodeBlockListeners();
        } catch (_) {}
      }
    },
    
    setPreviousContent(html) {
      if (state) {
        state.setPreviousRenderedHtml(html);
      }
    },
    
    reset() {
      // Don't reset session state, just prepare for new content
    },
    
    clear() {
      if (state) {
        state.streamBuffer = '';
        state.previousRenderedHtml = '';
      }
      if (containerEl) {
        containerEl.innerHTML = '';
      }
    }
  };
}

module.exports = {
  restoreSessionRender,
  restoreThinkingIndicator,
  restoreToolAnimation,
  restoreAskToolUI,
  getStreamingState,
  createSessionAwareStreamingRenderer
};
