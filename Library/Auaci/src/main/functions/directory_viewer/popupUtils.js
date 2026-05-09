// src/main/functions/directory_viewer/popupUtils.js

/**
 * Creates a click handler with tolerance zone for popup windows.
 * This prevents accidental closure when clicking near the popup window.
 * 
 * @param {HTMLElement} overlay - The overlay element that contains the popup
 * @param {HTMLElement} popup - The popup window element (card/container)
 * @param {Function} onClose - Function to call when popup should be closed
 * @param {number} tolerance - Tolerance zone in pixels (default: 30)
 */
function createPopupWithTolerance(overlay, popup, onClose, tolerance = 30) {
  if (!overlay || !popup || typeof onClose !== 'function') {
    console.error('createPopupWithTolerance: Invalid parameters');
    return;
  }

  // Variables to track pointer events for proper click detection
  let pointerDownOnOverlay = false;
  let pointerUpOnOverlay = false;

  /**
   * Checks if a point is within the tolerance zone of the popup
   * @param {number} x - X coordinate
   * @param {number} y - Y coordinate
   * @returns {boolean} True if within tolerance zone
   */
  function isWithinToleranceZone(x, y) {
    const rect = popup.getBoundingClientRect();
    return (
      x >= rect.left - tolerance &&
      x <= rect.right + tolerance &&
      y >= rect.top - tolerance &&
      y <= rect.bottom + tolerance
    );
  }

  // Pointer events (preferred for modern browsers)
  overlay.addEventListener('pointerdown', (e) => {
    pointerDownOnOverlay = (e.target === overlay);
    pointerUpOnOverlay = false;
  });

  overlay.addEventListener('pointerup', (e) => {
    pointerUpOnOverlay = (e.target === overlay);
  });

  overlay.addEventListener('pointercancel', () => {
    pointerDownOnOverlay = false;
    pointerUpOnOverlay = false;
  });

  // Fallback mouse handlers (for older browsers)
  overlay.addEventListener('mousedown', (e) => {
    pointerDownOnOverlay = (e.target === overlay);
    pointerUpOnOverlay = false;
  });

  overlay.addEventListener('mouseup', (e) => {
    pointerUpOnOverlay = (e.target === overlay);
  });

  // Main click handler with tolerance zone
  overlay.addEventListener('click', (e) => {
    // Only handle clicks that started and ended on the overlay background
    if (e.target !== overlay || !pointerDownOnOverlay || !pointerUpOnOverlay) {
      pointerDownOnOverlay = false;
      pointerUpOnOverlay = false;
      return;
    }

    // Check if click is within tolerance zone
    if (!isWithinToleranceZone(e.clientX, e.clientY)) {
      try {
        onClose();
      } catch (error) {
        console.error('Error in popup close handler:', error);
      }
    }

    // Reset flags
    pointerDownOnOverlay = false;
    pointerUpOnOverlay = false;
  });

  // Prevent clicks inside popup from closing it
  popup.addEventListener('click', (e) => {
    e.stopPropagation();
  });

  // Return cleanup function
  return function cleanup() {
    pointerDownOnOverlay = false;
    pointerUpOnOverlay = false;
  };
}

/**
 * Creates an overlay element with standard styling for popups
 * @param {string} backgroundColor - Background color (default: 'rgba(0, 0, 0, 0.5)')
 * @param {number} zIndex - Z-index for the overlay (default: 1000)
 * @returns {HTMLElement} The overlay element
 */
function createOverlay(backgroundColor = 'rgba(0, 0, 0, 0.5)', zIndex = 1000) {
  const overlay = document.createElement('div');
  Object.assign(overlay.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    width: '100vw',
    height: '100vh',
    background: backgroundColor,
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: String(zIndex)
  });
  return overlay;
}

module.exports = {
  createPopupWithTolerance,
  createOverlay
};