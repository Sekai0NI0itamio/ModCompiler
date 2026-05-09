// src/main/functions/chat/ui/indicator.js
// PERFORMANCE OPTIMIZED VERSION
const { shouldRenderForSession } = require('../helpers/renderGate');

// Performance optimization: Cache DOM elements
let cachedIndicator = null;
let cachedStopButton = null;

// Performance optimization: Debounced updates
let indicatorUpdateTimer = null;
const INDICATOR_UPDATE_DEBOUNCE = 100;

function getIndicator() {
  if (!cachedIndicator) {
    cachedIndicator = document.getElementById('gpt-response-indicator');
  }
  return cachedIndicator;
}

function getStopButton() {
  if (!cachedStopButton) {
    cachedStopButton = document.getElementById('stop-response-btn');
  }
  return cachedStopButton;
}

function showGptIndicator(sessionId = null) {
  if (!sessionId) return;
  if (!shouldRenderForSession(sessionId)) return;
  
  const indicator = getIndicator();
  if (indicator) {
    indicator.classList.add('visible');
    const btn = getStopButton();
    if (btn) {
      btn.disabled = false;
      btn.style.opacity = '1';
    }
  }
}

function hideGptIndicator(sessionId = null) {
  const indicator = document.getElementById('gpt-response-indicator');
  if (indicator) {
    indicator.classList.remove('visible');
    try { const btn = document.getElementById('stop-response-btn'); if (btn) { btn.disabled = true; btn.onclick = null; } } catch (_) {}
  }
}

// Performance optimization: Cache for thinking elements
const thinkingElementsCache = new Map();

function showInlineThinking(sessionId) {
  if (!sessionId || !shouldRenderForSession(sessionId)) return;
  
  try {
    window.__auaciInlineThinkingTimers = window.__auaciInlineThinkingTimers || {};
    window.__auaciRespondingStartTime = window.__auaciRespondingStartTime || {};
    
    // Performance optimization: Use cached container
    const container = document.getElementById('chat-messages');
    if (!container) return;
    
    // Performance optimization: Optimized DOM queries
    const gpts = container.querySelectorAll('.message.gpt-message');
    const last = gpts && gpts.length ? gpts[gpts.length - 1] : null;
    const content = last ? last.querySelector('.message-content') : null;
    if (!content) return;
    
    // Performance optimization: Single DOM query for existing indicators
    const existingIndicators = content.querySelectorAll('.thinking-inline');
    if (existingIndicators.length > 0) {
      // Already exists, just update the timer
      const existingEl = existingIndicators[existingIndicators.length - 1];
      thinkingElementsCache.set(sessionId, { element: existingEl, content });
      
      // Update timer with requestAnimationFrame
      setupOptimizedTimer(sessionId, content);
      return;
    }
    
    // Remove old indicators
    existingIndicators.forEach(n => n.remove());
    
    const secsNow = Math.max(0, Math.floor(((Date.now()) - (window.__auaciRespondingStartTime[sessionId] || Date.now())) / 1000));
    
    // Performance optimization: Use document fragment for single DOM insertion
    const fragment = document.createDocumentFragment();
    const el = document.createElement('div');
    el.className = 'thinking-inline';
    el.innerHTML = `<span class=\"typing-text\">GPT is thinking (<span class=\"thinking-seconds\">${secsNow}</span>s)</span> <span class=\"gpt-indicator-dots\"><span class=\"dot\"></span><span class=\"dot\"></span><span class=\"dot\"></span></span>`;
    fragment.appendChild(el);
    content.appendChild(fragment);
    
    // Cache the element
    thinkingElementsCache.set(sessionId, { element: el, content });
    
    // Setup optimized timer
    setupOptimizedTimer(sessionId, content);
    
  } catch (_) {}
}

/**
 * Performance optimized timer using requestAnimationFrame
 */
function setupOptimizedTimer(sessionId, content) {
  // Clear existing timer
  if (window.__auaciInlineThinkingTimers[sessionId]) {
    cancelAnimationFrame(window.__auaciInlineThinkingTimers[sessionId]);
  }
  
  const update = () => {
    if (!shouldRenderForSession(sessionId)) {
      window.__auaciInlineThinkingTimers[sessionId] = null;
      return;
    }
    
    const cached = thinkingElementsCache.get(sessionId);
    if (!cached || !cached.element) {
      window.__auaciInlineThinkingTimers[sessionId] = null;
      return;
    }
    
    const secondsEl = cached.element.querySelector('.thinking-seconds');
    if (!secondsEl) {
      window.__auaciInlineThinkingTimers[sessionId] = null;
      return;
    }
    
    const start = window.__auaciRespondingStartTime[sessionId] || Date.now();
    const s = Math.max(0, Math.floor((Date.now() - start) / 1000));
    secondsEl.textContent = String(s);
    
    // Schedule next update
    window.__auaciInlineThinkingTimers[sessionId] = requestAnimationFrame(update);
  };
  
  // Start the animation frame loop
  window.__auaciInlineThinkingTimers[sessionId] = requestAnimationFrame(update);
}

function hideInlineThinking(sessionId) {
  try {
    // Clear animation frame timer
    if (window.__auaciInlineThinkingTimers) {
      if (sessionId) {
        if (window.__auaciInlineThinkingTimers[sessionId]) {
          cancelAnimationFrame(window.__auaciInlineThinkingTimers[sessionId]);
          delete window.__auaciInlineThinkingTimers[sessionId];
        }
      } else {
        // clear all timers if no sessionId provided
        for (const k of Object.keys(window.__auaciInlineThinkingTimers)) {
          try { cancelAnimationFrame(window.__auaciInlineThinkingTimers[k]); } catch (_) {}
          delete window.__auaciInlineThinkingTimers[k];
        }
      }
    }
    
    // Clear cache
    if (sessionId) {
      thinkingElementsCache.delete(sessionId);
    } else {
      thinkingElementsCache.clear();
    }
    
    const container = document.getElementById('chat-messages');
    if (!container) return;
    
    // Performance optimization: Single DOM query
    const indicators = container.querySelectorAll('.thinking-inline');
    if (indicators.length > 0) {
      const fragment = document.createDocumentFragment();
      indicators.forEach(n => {
        if (n.parentNode) {
          n.remove();
        }
      });
    }
  } catch (_) {}
}

module.exports = { showGptIndicator, hideGptIndicator, showInlineThinking, hideInlineThinking };
