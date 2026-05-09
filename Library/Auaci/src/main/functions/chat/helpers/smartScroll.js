// src/main/functions/chat/helpers/smartScroll.js
// Smart scrolling system that respects user intent during streaming
// Auto-scrolls only when user is near the bottom, allows manual scrolling

const { smoothScrollTo } = require('./smoothScroll');

// Global state to track user scroll behavior
const scrollState = new Map();

/**
 * Check if user is near the bottom of the container (within threshold)
 * @param {HTMLElement} container - The scrollable container
 * @param {number} threshold - Pixels from bottom to consider "near bottom" (default: 100px)
 * @returns {boolean} True if user is near the bottom
 */
function isNearBottom(container, threshold = 100) {
  if (!container) return false;
  
  const scrollTop = container.scrollTop;
  const scrollHeight = container.scrollHeight;
  const clientHeight = container.clientHeight;
  
  // Calculate distance from bottom
  const distanceFromBottom = scrollHeight - scrollTop - clientHeight;
  
  return distanceFromBottom <= threshold;
}

/**
 * Get or create scroll state for a container
 * @param {HTMLElement} container - The scrollable container
 * @returns {Object} Scroll state object
 */
function getScrollState(container) {
  if (!container) return null;
  
  const containerId = container.id || container.dataset.scrollId || `container_${Math.random().toString(36).slice(2)}`;
  
  if (!scrollState.has(containerId)) {
    scrollState.set(containerId, {
      container,
      lastScrollTop: container.scrollTop,
      lastScrollHeight: container.scrollHeight,
      userScrolledUp: false,
      lastScrollTime: Date.now(),
      autoScrollEnabled: true
    });
  }
  
  return scrollState.get(containerId);
}

/**
 * Update scroll state based on current scroll position
 * Tracks when user manually scrolls up to read previous messages
 * @param {HTMLElement} container - The scrollable container
 */
function updateScrollState(container) {
  const state = getScrollState(container);
  if (!state) return;
  
  const currentScrollTop = container.scrollTop;
  const currentScrollHeight = container.scrollHeight;
  
  // Detect if user scrolled up (manually reading previous messages)
  if (currentScrollTop < state.lastScrollTop && currentScrollHeight === state.lastScrollHeight) {
    // User scrolled up within same content - they're reading previous messages
    state.userScrolledUp = true;
    state.autoScrollEnabled = false;
  } else if (currentScrollHeight > state.lastScrollHeight) {
    // Content grew - reset user scroll state if they're near bottom
    if (isNearBottom(container, 100)) {
      state.userScrolledUp = false;
      state.autoScrollEnabled = true;
    }
  }
  
  // Update state
  state.lastScrollTop = currentScrollTop;
  state.lastScrollHeight = currentScrollHeight;
  state.lastScrollTime = Date.now();
}

/**
 * Smart scroll to bottom - only scrolls if user is near bottom or hasn't scrolled up
 * @param {HTMLElement} container - The scrollable container
 * @param {Object} options - Scroll options
 * @param {boolean} options.force - Force scroll regardless of user position
 * @param {number} options.threshold - Distance from bottom to consider "near" (default: 100px)
 * @param {boolean} options.smooth - Use smooth scrolling (default: true)
 */
function smartScrollToBottom(container, options = {}) {
  if (!container) return;
  
  const {
    force = false,
    threshold = 100,
    smooth = true
  } = options;
  
  const state = getScrollState(container);
  if (!state) return;
  
  // Always update scroll state before making decisions
  updateScrollState(container);
  
  // Check if we should auto-scroll
  const shouldAutoScroll = force || 
    (!state.userScrolledUp && isNearBottom(container, threshold));
  
  if (shouldAutoScroll) {
    const targetScrollTop = container.scrollHeight;
    
    if (smooth) {
      smoothScrollTo(container, targetScrollTop, { axis: 'y', duration: 200 });
    } else {
      container.scrollTop = targetScrollTop;
    }
    
    // Update state to reflect auto-scroll
    state.lastScrollTop = targetScrollTop;
    state.lastScrollHeight = container.scrollHeight;
    state.userScrolledUp = false;
    state.autoScrollEnabled = true;
  }
}

/**
 * Reset scroll state for a container (useful when switching sessions)
 * @param {HTMLElement} container - The scrollable container
 */
function resetScrollState(container) {
  if (!container) return;
  
  const containerId = container.id || container.dataset.scrollId;
  if (containerId && scrollState.has(containerId)) {
    scrollState.delete(containerId);
  }
}

/**
 * Enable auto-scroll for a container (user can re-enable by scrolling to bottom)
 * @param {HTMLElement} container - The scrollable container
 */
function enableAutoScroll(container) {
  const state = getScrollState(container);
  if (state) {
    state.autoScrollEnabled = true;
    state.userScrolledUp = false;
  }
}

/**
 * Disable auto-scroll for a container (user scrolled up to read)
 * @param {HTMLElement} container - The scrollable container
 */
function disableAutoScroll(container) {
  const state = getScrollState(container);
  if (state) {
    state.autoScrollEnabled = false;
    state.userScrolledUp = true;
  }
}

/**
 * Check if auto-scroll is currently enabled for a container
 * @param {HTMLElement} container - The scrollable container
 * @returns {boolean} True if auto-scroll is enabled
 */
function isAutoScrollEnabled(container) {
  const state = getScrollState(container);
  return state ? state.autoScrollEnabled : true;
}

/**
 * Setup scroll event listener for a container to track user behavior
 * @param {HTMLElement} container - The scrollable container
 */
function setupScrollTracking(container) {
  if (!container) return;
  
  // Initialize scroll state
  getScrollState(container);
  
  // Add scroll event listener
  const handleScroll = () => {
    updateScrollState(container);
  };
  
  // Remove existing listener if any
  if (container.__smartScrollHandler) {
    container.removeEventListener('scroll', container.__smartScrollHandler);
  }
  
  container.__smartScrollHandler = handleScroll;
  container.addEventListener('scroll', handleScroll, { passive: true });
}

module.exports = {
  isNearBottom,
  smartScrollToBottom,
  updateScrollState,
  resetScrollState,
  enableAutoScroll,
  disableAutoScroll,
  isAutoScrollEnabled,
  setupScrollTracking,
  getScrollState
};