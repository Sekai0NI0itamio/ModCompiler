// src/main/functions/chat/resize-textarea.js
// Auto-resize the chat input textarea up to 8 lines, then become scrollable.
// Also ensure spellcheck is disabled.

(function initResizeTextarea() {
  // NOTE: #user-input is a contenteditable div (not a textarea).
  // This module’s job is to cap the wrapper (#input-scroll) to ~8 lines worth of height,
  // while avoiding any forced scrollTop changes that fight the user.
  //
  // Key performance goals:
  // - Do NOT read scrollHeight on every keystroke (can be expensive for large content).
  // - Do NOT attach MutationObservers to the contenteditable (too chatty for big pastes).

  function run() {
    const input = document.getElementById('user-input');
    if (!input) return;

    // Disable spellcheck and related auto-corrections explicitly (also set in HTML)
    try {
      input.setAttribute('spellcheck', 'false');
      input.setAttribute('autocorrect', 'off');
      input.setAttribute('autocapitalize', 'off');
      input.setAttribute('autocomplete', 'off');
    } catch (_) {}

    const wrapper = document.getElementById('input-scroll');
    if (!wrapper) return;

    function pxToNumber(px) {
      const n = parseFloat(String(px || '').replace('px', ''));
      return Number.isFinite(n) ? n : 0;
    }

    function computeLineHeight() {
      const cs = window.getComputedStyle(input);
      const lh = pxToNumber(cs.lineHeight) || Math.ceil(pxToNumber(cs.fontSize) * 1.6);
      return lh > 0 ? lh : 22;
    }

    function applySizing() {
      const lineHeight = computeLineHeight();
      const maxLines = 8;

      const cs = window.getComputedStyle(wrapper);
      const padTop = pxToNumber(cs.paddingTop);
      const padBottom = pxToNumber(cs.paddingBottom);

      // Let the wrapper grow naturally up to this cap; scroll only when it overflows.
      const cap = Math.round((lineHeight * maxLines) + padTop + padBottom);
      wrapper.style.maxHeight = cap + 'px';
      wrapper.style.overflowY = 'auto';

      // Ensure consistent spacing below input container
      const container = document.getElementById('input-container');
      if (container) container.style.marginBottom = Math.round(lineHeight) + 'px';
    }

    applySizing();
    window.addEventListener('resize', applySizing);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', run, { once: true });
  } else {
    run();
  }
})();
