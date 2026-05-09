// src/main/functions/chat/concurrent/sessionStateManager.js
// Core state manager for concurrent session execution
// Captures, stores, and restores session execution state for seamless switching

const SESSION_STATUS = {
  IDLE: 'idle',
  STREAMING: 'streaming',
  EXECUTING_TOOL: 'executing_tool',
  AWAITING_INPUT: 'awaiting_input',
  ERROR: 'error',
  COMPLETED: 'completed'
};

// In-memory state store for all sessions
// sessionStates[sessionId] = SessionState
const _sessionStates = new Map();

// Cleanup timeout (5 minutes for completed sessions)
const CLEANUP_TIMEOUT_MS = 5 * 60 * 1000;

/**
 * SessionState class - holds complete execution state for a session
 */
class SessionState {
  constructor(sessionId) {
    this.sessionId = sessionId;
    this.status = SESSION_STATUS.IDLE;
    this.createdAt = Date.now();
    this.updatedAt = Date.now();
    
    // Execution state
    this.isResponding = false;
    this.thinkingStartTime = null;
    this.currentEntryIndex = null;
    
    // Stream state
    this.streamBuffer = '';
    this.accumulatedContent = '';
    this.previousRenderedHtml = '';
    
    // Tool execution state
    this.toolRuns = [];
    this.pendingTools = [];
    this.executingToolIndex = null;
    this.executingToolName = null;
    
    // Render state (captured when switching away)
    this.capturedDomHtml = null;
    this.capturedScrollTop = null;
    this.capturedScrollHeight = null;
    
    // Error state
    this.lastError = null;
    
    // Abort controller reference (not serializable)
    this.abortController = null;
    
    // Cleanup timer
    this._cleanupTimer = null;
  }
  
  /**
   * Update the state timestamp
   */
  touch() {
    this.updatedAt = Date.now();
  }
  
  /**
   * Set responding state with thinking timer
   */
  startResponding() {
    this.isResponding = true;
    this.status = SESSION_STATUS.STREAMING;
    this.thinkingStartTime = Date.now();
    this.touch();
  }
  
  /**
   * Stop responding state
   */
  stopResponding() {
    this.isResponding = false;
    this.thinkingStartTime = null;
    this.touch();
  }
  
  /**
   * Get elapsed thinking time in seconds
   */
  getThinkingSeconds() {
    if (!this.thinkingStartTime) return 0;
    return Math.floor((Date.now() - this.thinkingStartTime) / 1000);
  }
  
  /**
   * Update stream buffer with new content
   */
  updateStreamBuffer(fullText, newChunk) {
    this.streamBuffer = fullText || '';
    this.touch();
  }
  
  /**
   * Set accumulated GPT content (full response so far)
   */
  setAccumulatedContent(content) {
    this.accumulatedContent = content || '';
    this.touch();
  }
  
  /**
   * Set previous rendered HTML for continuity
   */
  setPreviousRenderedHtml(html) {
    this.previousRenderedHtml = html || '';
    this.touch();
  }
  
  /**
   * Start tool execution
   */
  startToolExecution(toolIndex, toolName) {
    this.status = SESSION_STATUS.EXECUTING_TOOL;
    this.executingToolIndex = toolIndex;
    this.executingToolName = toolName;
    // Ensure toolRuns is always a valid array
    if (!Array.isArray(this.toolRuns)) {
      this.toolRuns = [];
    }
    this.touch();
  }
  
  /**
   * Complete tool execution
   */
  completeToolExecution(toolIndex, result) {
    if (this.executingToolIndex === toolIndex) {
      this.executingToolIndex = null;
      this.executingToolName = null;
    }
    // Update tool runs - ensure toolRuns is always a valid array
    if (!Array.isArray(this.toolRuns)) {
      this.toolRuns = [];
    }
    // Ensure the array is large enough
    while (this.toolRuns.length <= toolIndex) {
      this.toolRuns.push({ name: '', input: {}, result: null, success: null });
    }
    if (this.toolRuns[toolIndex]) {
      this.toolRuns[toolIndex].result = result;
      this.toolRuns[toolIndex].success = !result?.error;
    }
    this.status = SESSION_STATUS.STREAMING;
    this.touch();
  }
  
  /**
   * Set awaiting user input state (ask tool)
   */
  setAwaitingInput(toolIndex, toolName) {
    this.status = SESSION_STATUS.AWAITING_INPUT;
    this.executingToolIndex = toolIndex;
    this.executingToolName = toolName;
    this.touch();
  }
  
  /**
   * Clear awaiting input state
   */
  clearAwaitingInput() {
    if (this.status === SESSION_STATUS.AWAITING_INPUT) {
      this.status = SESSION_STATUS.STREAMING;
      this.executingToolIndex = null;
      this.executingToolName = null;
    }
    this.touch();
  }
  
  /**
   * Set error state
   */
  setError(error) {
    this.status = SESSION_STATUS.ERROR;
    this.lastError = error ? String(error.message || error) : null;
    this.isResponding = false;
    this.touch();
  }
  
  /**
   * Mark session as completed
   */
  complete() {
    this.status = SESSION_STATUS.COMPLETED;
    this.isResponding = false;
    this.thinkingStartTime = null;
    this.executingToolIndex = null;
    this.executingToolName = null;
    // Clear captured DOM state to prevent stale restoration
    this.capturedDomHtml = null;
    this.capturedScrollTop = null;
    this.capturedScrollHeight = null;
    this.touch();
  }
  
  /**
   * Reset to idle state
   */
  reset() {
    this.status = SESSION_STATUS.IDLE;
    this.isResponding = false;
    this.thinkingStartTime = null;
    this.streamBuffer = '';
    this.accumulatedContent = '';
    this.previousRenderedHtml = '';
    this.toolRuns = [];
    this.pendingTools = [];
    this.executingToolIndex = null;
    this.executingToolName = null;
    this.capturedDomHtml = null;
    this.capturedScrollTop = null;
    this.capturedScrollHeight = null;
    this.lastError = null;
    this.currentEntryIndex = null;
    this.touch();
  }
  
  /**
   * Performance optimized DOM state capture
   * Only captures essential state, not full HTML
   */
  captureDomState(container) {
    if (!container) return;
    try {
      // Performance optimization: Only capture scroll position
      // Full HTML capture is too memory-intensive
      this.capturedScrollTop = container.scrollTop;
      this.capturedScrollHeight = container.scrollHeight;
      
      // Performance optimization: Only capture essential DOM state
      // Store references to active elements instead of full HTML
      this.captureEssentialState(container);
      
      this.touch();
    } catch (e) {
      console.warn('[SessionState] Failed to capture DOM state:', e);
    }
  }
  
  /**
   * Capture essential DOM state for restoration
   * More memory-efficient than full HTML capture
   */
  captureEssentialState(container) {
    try {
      // Store references to active elements
      const activeElements = {
        thinkingIndicators: Array.from(container.querySelectorAll('.thinking-inline')).map(el => ({
          text: el.textContent,
          className: el.className
        })),
        toolBoxes: Array.from(container.querySelectorAll('.auaci-tool-box')).map(box => ({
          toolName: box.getAttribute('data-tool'),
          isExecuting: box.classList.contains('tool-executing')
        })),
        lastMessageIndex: container.querySelector('.message:last-child')?.getAttribute('data-entry-index')
      };
      
      this.capturedDomState = activeElements;
    } catch (e) {
      console.warn('[SessionState] Failed to capture essential state:', e);
    }
  }
  
  /**
   * Serialize state for persistence (excludes non-serializable fields)
   */
  toJSON() {
    return {
      sessionId: this.sessionId,
      status: this.status,
      createdAt: this.createdAt,
      updatedAt: this.updatedAt,
      isResponding: this.isResponding,
      thinkingStartTime: this.thinkingStartTime,
      currentEntryIndex: this.currentEntryIndex,
      streamBuffer: this.streamBuffer,
      accumulatedContent: this.accumulatedContent,
      previousRenderedHtml: this.previousRenderedHtml,
      toolRuns: this.toolRuns,
      pendingTools: this.pendingTools,
      executingToolIndex: this.executingToolIndex,
      executingToolName: this.executingToolName,
      capturedDomState: this.capturedDomState, // Performance optimized state
      capturedDomHtml: this.capturedDomHtml, // Legacy support
      capturedScrollTop: this.capturedScrollTop,
      capturedScrollHeight: this.capturedScrollHeight,
      lastError: this.lastError
    };
  }
  
  /**
   * Restore state from serialized data
   */
  static fromJSON(data) {
    if (!data || !data.sessionId) return null;
    const state = new SessionState(data.sessionId);
    Object.assign(state, {
      status: data.status || SESSION_STATUS.IDLE,
      createdAt: data.createdAt || Date.now(),
      updatedAt: data.updatedAt || Date.now(),
      isResponding: !!data.isResponding,
      thinkingStartTime: data.thinkingStartTime || null,
      currentEntryIndex: data.currentEntryIndex ?? null,
      streamBuffer: data.streamBuffer || '',
      accumulatedContent: data.accumulatedContent || '',
      previousRenderedHtml: data.previousRenderedHtml || '',
      toolRuns: Array.isArray(data.toolRuns) ? data.toolRuns : [],
      pendingTools: Array.isArray(data.pendingTools) ? data.pendingTools : [],
      executingToolIndex: data.executingToolIndex ?? null,
      executingToolName: data.executingToolName || null,
      capturedDomState: data.capturedDomState || null,
      capturedDomHtml: data.capturedDomHtml || null,
      capturedScrollTop: data.capturedScrollTop ?? null,
      capturedScrollHeight: data.capturedScrollHeight ?? null,
      lastError: data.lastError || null
    });
    return state;
  }
}

// ============================================================================
// STATE MANAGER API
// ============================================================================

/**
 * Get or create session state
 */
function getSessionState(sessionId) {
  if (!sessionId) return null;
  
  if (!_sessionStates.has(sessionId)) {
    _sessionStates.set(sessionId, new SessionState(sessionId));
  }
  
  return _sessionStates.get(sessionId);
}

/**
 * Check if session state exists
 */
function hasSessionState(sessionId) {
  return _sessionStates.has(sessionId);
}

/**
 * Remove session state
 */
function removeSessionState(sessionId) {
  const state = _sessionStates.get(sessionId);
  if (state) {
    // Clear any cleanup timer
    if (state._cleanupTimer) {
      clearTimeout(state._cleanupTimer);
    }
    // Abort any pending requests
    if (state.abortController) {
      try { state.abortController.abort(); } catch (_) {}
    }
  }
  _sessionStates.delete(sessionId);
}

/**
 * Get all session states
 */
function getAllSessionStates() {
  return Array.from(_sessionStates.values());
}

/**
 * Get all responding sessions
 */
function getRespondingSessions() {
  return getAllSessionStates().filter(s => s.isResponding);
}

/**
 * Get all sessions awaiting input
 */
function getSessionsAwaitingInput() {
  return getAllSessionStates().filter(s => s.status === SESSION_STATUS.AWAITING_INPUT);
}

/**
 * Schedule cleanup for completed session
 */
function scheduleCleanup(sessionId, timeoutMs = CLEANUP_TIMEOUT_MS) {
  const state = _sessionStates.get(sessionId);
  if (!state) return;
  
  // Clear existing timer
  if (state._cleanupTimer) {
    clearTimeout(state._cleanupTimer);
  }
  
  // Schedule new cleanup
  state._cleanupTimer = setTimeout(() => {
    // Only cleanup if still completed/idle
    const current = _sessionStates.get(sessionId);
    if (current && (current.status === SESSION_STATUS.COMPLETED || current.status === SESSION_STATUS.IDLE)) {
      // Don't remove, just clear heavy data
      current.capturedDomHtml = null;
      current.previousRenderedHtml = '';
      current.streamBuffer = '';
    }
  }, timeoutMs);
}

/**
 * Capture state when switching away from a session
 */
function captureSessionState(sessionId, container) {
  const state = getSessionState(sessionId);
  if (!state) return;
  
  state.captureDomState(container);
  console.log(`[SessionStateManager] Captured state for session ${sessionId}, status: ${state.status}`);
}

/**
 * Get session status for tab indicator
 */
function getSessionStatus(sessionId) {
  const state = _sessionStates.get(sessionId);
  if (!state) return SESSION_STATUS.IDLE;
  return state.status;
}

/**
 * Check if session is responding
 */
function isSessionResponding(sessionId) {
  const state = _sessionStates.get(sessionId);
  return state ? state.isResponding : false;
}

/**
 * Check if session is awaiting input
 */
function isSessionAwaitingInput(sessionId) {
  const state = _sessionStates.get(sessionId);
  return state ? state.status === SESSION_STATUS.AWAITING_INPUT : false;
}

/**
 * Check if session has error
 */
function isSessionError(sessionId) {
  const state = _sessionStates.get(sessionId);
  return state ? state.status === SESSION_STATUS.ERROR : false;
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  SESSION_STATUS,
  SessionState,
  getSessionState,
  hasSessionState,
  removeSessionState,
  getAllSessionStates,
  getRespondingSessions,
  getSessionsAwaitingInput,
  scheduleCleanup,
  captureSessionState,
  getSessionStatus,
  isSessionResponding,
  isSessionAwaitingInput,
  isSessionError
};
