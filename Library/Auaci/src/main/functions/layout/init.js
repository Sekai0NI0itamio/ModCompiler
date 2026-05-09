// src/main/functions/layout/init.js
/**
 * Apply proportional widths to the main panels based on a base sample.
 *
 * Default base values (from your SizeLogger sample):
 *  directory: 271
 *  editor:    540
 *  chat:      589
 *  app:       1400
 *
 * The module computes percentages (e.g. directoryPct = 271 / 1400 * 100)
 * and applies them as CSS flex-grow ratios.
 *
 * EXPANSION RULE: When a section is hidden, only the rightmost visible section
 * expands to fill the space. If no right section exists, the leftmost expands.
 *
 * Exports: initProportionalLayout(base)
 * - base (optional): object with directory/editor/chat/app numeric widths.
 */
const DEFAULT_BASE = { directory: 271, editor: 540, chat: 589, app: 1400 };

// Store the base ratios for recalculation when visibility changes
let currentBase = DEFAULT_BASE;

/**
 * Compute flex values from base object and apply to elements by id.
 * Respects section-hidden class and applies expansion rule:
 * - Only the rightmost visible section expands to absorb hidden space
 * - If no right section, leftmost visible section expands
 */
function applyProportions(base = DEFAULT_BASE) {
  if (!base || !base.app || base.app === 0) return;
  currentBase = base;

  const directoryEl = document.getElementById('directory-viewer');
  const editorEl = document.getElementById('editor');
  const chatEl = document.getElementById('chat');

  if (!directoryEl || !editorEl || !chatEl) return;

  const directoryHidden = directoryEl.classList.contains('section-hidden');
  const editorHidden = editorEl.classList.contains('section-hidden');
  const chatHidden = chatEl.classList.contains('section-hidden');

  // Calculate total width and individual widths
  const totalWidth = base.directory + base.editor + base.chat;
  
  // Determine which sections are visible (in order: directory, editor, chat)
  const visibleSections = [];
  if (!directoryHidden) visibleSections.push({ el: directoryEl, baseWidth: base.directory, name: 'directory' });
  if (!editorHidden) visibleSections.push({ el: editorEl, baseWidth: base.editor, name: 'editor' });
  if (!chatHidden) visibleSections.push({ el: chatEl, baseWidth: base.chat, name: 'chat' });

  // Calculate hidden space to redistribute
  let hiddenSpace = 0;
  if (directoryHidden) hiddenSpace += base.directory;
  if (editorHidden) hiddenSpace += base.editor;
  if (chatHidden) hiddenSpace += base.chat;

  // Apply hidden styles
  const applyHidden = (el) => {
    el.style.flex = '0 0 0px';
    el.style.width = '0';
    el.style.maxWidth = '0';
    el.style.minWidth = '0';
    el.style.padding = '0';
    el.style.margin = '0';
    el.style.border = 'none';
    el.style.overflow = 'hidden';
    el.style.visibility = 'hidden';
  };

  // Apply visible styles with specific width
  const applyVisible = (el, widthValue, isLastVisible) => {
    el.style.flex = `0 0 ${widthValue}px`;
    el.style.width = `${widthValue}px`;
    el.style.maxWidth = '';
    el.style.minWidth = '80px';
    el.style.boxSizing = 'border-box';
    el.style.overflow = 'hidden';
    el.style.visibility = 'visible';
    el.style.borderRight = isLastVisible ? 'none' : '1px solid #ccc';
  };

  // Apply hidden to all hidden sections
  if (directoryHidden) applyHidden(directoryEl);
  if (editorHidden) applyHidden(editorEl);
  if (chatHidden) applyHidden(chatEl);

  // If no visible sections, nothing to do
  if (visibleSections.length === 0) return;

  // Get container width
  const container = document.getElementById('app-container');
  if (!container) return;
  const containerWidth = container.clientWidth;

  // Calculate the width each visible section should have
  // The rightmost visible section gets the extra space from hidden sections
  const rightmostIndex = visibleSections.length - 1;

  visibleSections.forEach((section, index) => {
    const isRightmost = index === rightmostIndex;
    const isLastVisible = index === visibleSections.length - 1;
    
    // Calculate base percentage width
    const basePercentage = section.baseWidth / totalWidth;
    let sectionWidth = basePercentage * containerWidth;
    
    // Only the rightmost visible section absorbs the hidden space
    if (isRightmost && hiddenSpace > 0) {
      const hiddenPercentage = hiddenSpace / totalWidth;
      sectionWidth += hiddenPercentage * containerWidth;
    }
    
    applyVisible(section.el, Math.floor(sectionWidth), isLastVisible);
  });
}

/**
 * Re-apply proportions (called when section visibility changes)
 */
function reapplyProportions() {
  applyProportions(currentBase);
}

/**
 * Initialize proportional layout.
 * - base: optional object with directory/editor/chat/app numbers (as in SizeLogger output)
 *
 * The function will:
 * - apply proportions immediately (when DOM ready)
 * - re-apply on window resize
 * - if elements are not yet present, watch for them and apply once they appear
 */
function initProportionalLayout(base = DEFAULT_BASE) {
  const start = () => {
    // Initial apply if elements exist
    applyProportions(base);

    // Re-apply on window resize to ensure percent-based widths remain correct
    window.addEventListener('resize', () => applyProportions(base));

    // If any elements are missing, observe DOM and apply once they appear
    const neededIds = ['directory-viewer', 'editor', 'chat'];
    const missing = neededIds.filter(id => !document.getElementById(id));
    if (missing.length > 0 && typeof MutationObserver === 'function') {
      const mo = new MutationObserver(() => {
        const stillMissing = neededIds.filter(id => !document.getElementById(id));
        if (stillMissing.length === 0) {
          applyProportions(base);
          mo.disconnect();
        }
      });
      mo.observe(document.body, { childList: true, subtree: true });
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, { once: true });
  } else {
    start();
  }
}

module.exports = { initProportionalLayout, reapplyProportions };