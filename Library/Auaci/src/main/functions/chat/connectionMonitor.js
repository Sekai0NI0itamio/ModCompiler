// src/main/functions/chat/connectionMonitor.js
// Monitors internet connectivity and API responsiveness
// Shows status messages in the indicator area

const CHECK_INTERVAL = 1000; // Check every second
const API_TIMEOUT_THRESHOLD = 15000; // 15 seconds before showing timeout warning

let checkIntervalId = null;
let isOnline = true;
let apiRequestStartTime = null;
let currentSessionId = null;
let isToolExecuting = false; // Track if a tool is currently executing

/**
 * Check if we have internet connectivity
 */
async function checkConnectivity() {
  try {
    // Use navigator.onLine as primary check
    if (!navigator.onLine) {
      return false;
    }
    // Optional: ping a reliable endpoint (but don't block on it)
    return true;
  } catch (_) {
    return navigator.onLine;
  }
}

/**
 * Update the indicator with connection status
 */
function updateIndicator(status, message, showRetry = false) {
  const indicator = document.getElementById('gpt-response-indicator');
  if (!indicator) return;
  
  // Only update if indicator is visible (GPT is responding)
  if (!indicator.classList.contains('visible')) return;
  
  const existingStatus = indicator.querySelector('.connection-status');
  if (existingStatus) existingStatus.remove();
  
  if (status === 'ok') return;
  
  const statusDiv = document.createElement('div');
  statusDiv.className = 'connection-status';
  statusDiv.style.cssText = 'font-size: 12px; color: #f59e0b; margin-top: 4px;';
  
  let html = `<span>${message}</span>`;
  if (showRetry) {
    html += ` <button class="retry-btn" style="margin-left: 8px; padding: 2px 8px; font-size: 11px; cursor: pointer; background: #3b82f6; color: white; border: none; border-radius: 4px;">Retry</button>`;
  }
  statusDiv.innerHTML = html;
  
  if (showRetry) {
    const retryBtn = statusDiv.querySelector('.retry-btn');
    if (retryBtn) {
      retryBtn.onclick = () => {
        retryCurrentRequest();
      };
    }
  }
  
  indicator.appendChild(statusDiv);
}

/**
 * Retry the current request
 */
function retryCurrentRequest() {
  if (!currentSessionId) return;
  
  // Abort current request
  try {
    const controllers = window.__auaciAbortControllers || {};
    if (controllers[currentSessionId]) {
      controllers[currentSessionId].abort();
    }
  } catch (_) {}
  
  // Clear status
  const indicator = document.getElementById('gpt-response-indicator');
  if (indicator) {
    const status = indicator.querySelector('.connection-status');
    if (status) status.remove();
  }
  
  // Trigger retry event
  window.dispatchEvent(new CustomEvent('auaci:retry-request', {
    detail: { sessionId: currentSessionId }
  }));
}

/**
 * Main check loop
 */
async function runCheck() {
  const wasOnline = isOnline;
  isOnline = await checkConnectivity();
  
  // Check if GPT is responding
  const isResponding = window.isGptResponding || false;
  if (!isResponding) {
    apiRequestStartTime = null;
    return;
  }
  
  // Handle offline state
  if (!isOnline) {
    updateIndicator('offline', 'Disconnected, attempting to reconnect...', false);
    return;
  }
  
  // If we just came back online, clear the status
  if (!wasOnline && isOnline) {
    updateIndicator('ok', '');
  }
  
  // Check API timeout (only when not executing a tool)
  if (apiRequestStartTime && !isToolExecuting) {
    const elapsed = Date.now() - apiRequestStartTime;
    if (elapsed > API_TIMEOUT_THRESHOLD) {
      const seconds = Math.floor(elapsed / 1000);
      updateIndicator('timeout', `API hasn't been responding for ${seconds}s, attempt to reconnect...`, true);
    }
  }
}

/**
 * Start monitoring
 */
function startMonitoring() {
  if (checkIntervalId) return;
  checkIntervalId = setInterval(runCheck, CHECK_INTERVAL);
  
  // Listen for online/offline events
  window.addEventListener('online', () => {
    isOnline = true;
    updateIndicator('ok', '');
  });
  
  window.addEventListener('offline', () => {
    isOnline = false;
    if (window.isGptResponding) {
      updateIndicator('offline', 'Disconnected, attempting to reconnect...', false);
    }
  });
}

/**
 * Stop monitoring
 */
function stopMonitoring() {
  if (checkIntervalId) {
    clearInterval(checkIntervalId);
    checkIntervalId = null;
  }
}

/**
 * Mark that an API request has started
 */
function markRequestStart(sessionId) {
  apiRequestStartTime = Date.now();
  currentSessionId = sessionId;
}

/**
 * Mark that an API request has received data (reset timeout)
 */
function markRequestActivity() {
  if (apiRequestStartTime) {
    apiRequestStartTime = Date.now();
  }
  // Clear any timeout warning
  const indicator = document.getElementById('gpt-response-indicator');
  if (indicator) {
    const status = indicator.querySelector('.connection-status');
    if (status && status.textContent.includes('API')) {
      status.remove();
    }
  }
}

/**
 * Mark that an API request has ended
 */
function markRequestEnd() {
  apiRequestStartTime = null;
  currentSessionId = null;
  // Clear any status messages
  const indicator = document.getElementById('gpt-response-indicator');
  if (indicator) {
    const status = indicator.querySelector('.connection-status');
    if (status) status.remove();
  }
}

/**
 * Mark that a tool is starting execution (pause timeout tracking)
 */
function markToolStart() {
  isToolExecuting = true;
}

/**
 * Mark that a tool has finished execution (resume timeout tracking)
 */
function markToolEnd() {
  isToolExecuting = false;
  // Reset the request start time so timeout counts from now
  if (apiRequestStartTime) {
    apiRequestStartTime = Date.now();
  }
}

/**
 * Check if currently online
 */
function isCurrentlyOnline() {
  return isOnline;
}

module.exports = {
  startMonitoring,
  stopMonitoring,
  markRequestStart,
  markRequestActivity,
  markRequestEnd,
  markToolStart,
  markToolEnd,
  isCurrentlyOnline,
  checkConnectivity
};
