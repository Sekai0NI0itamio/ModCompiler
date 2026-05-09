// src/main/functions/chat/helpers/toolRenders/completed/attempt_completion.js
// Renderer for completed attempt_completion tool

const { escapeHtmlLite, pendingIconSvg } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  // Get the completion text from various possible locations
  const attemptText = getAttemptText(input, result);
  
  if (attemptText && attemptText.trim()) {
    // Render as markdown if marked is available
    try {
      const marked = require('marked');
      const { createRenderer } = require('../../../renderer');
      marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() });
      const html = marked.parse(attemptText);
      lines.push(`<div class="attempt-content" style="padding:4px 0;">${html}</div>`);
    } catch (_) {
      // Fallback to plain text
      lines.push(`<div class="attempt-content" style="padding:4px 0;">${escapeHtmlLite(attemptText)}</div>`);
    }
  } else {
    // No content yet - show pending state
    lines.push(`<div class="tool-line">${pendingIconSvg()}<span style="margin-left:6px;color:#6b7280;">Generating summary...</span></div>`);
  }
  
  return lines.join('');
}

function getAttemptText(input, result) {
  // Try various locations for the completion text
  if (input?.result && typeof input.result === 'string' && input.result.trim()) {
    return input.result;
  }
  if (result?.result && typeof result.result === 'string' && result.result.trim()) {
    return result.result;
  }
  if (result?.text && typeof result.text === 'string' && result.text.trim()) {
    return result.text;
  }
  if (result?.message && typeof result.message === 'string' && result.message.trim()) {
    return result.message;
  }
  if (result?.content && typeof result.content === 'string' && result.content.trim()) {
    return result.content;
  }
  return '';
}

module.exports = { render };
