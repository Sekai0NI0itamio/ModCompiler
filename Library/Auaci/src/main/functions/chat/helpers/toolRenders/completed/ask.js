// src/main/functions/chat/helpers/toolRenders/completed/ask.js
// Renderer for completed ask tool

const { escapeHtmlLite, okIconSvg } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const question = input?.question || '';
  const mode = String(input?.mode || 'free').toLowerCase();
  const opts = Array.isArray(input?.options) ? input.options : [];
  
  // Check if answered
  const hasFlat = !!(result?.answer_text || (Array.isArray(result?.selected) && result.selected.length > 0));
  const hasNested = !!(result?.answer?.text || (Array.isArray(result?.answer?.selected) && result.answer.selected.length > 0));
  const isAnswered = !!(result?.waiting === false || result?.answered === true) || hasFlat || hasNested;
  
  // Question header
  lines.push(`<div class="tool-line" style="font-weight:600;">${escapeHtmlLite(String(question))}</div>`);
  
  if (isAnswered) {
    // Show the answer
    const answerText = result?.answer_text || result?.answer?.text || '';
    const selected = result?.selected || result?.answer?.selected || [];
    
    if (mode === 'free' && answerText) {
      lines.push(`<div class="tool-line">${okIconSvg()}<span class="tool-file" style="margin-left:6px;">${escapeHtmlLite(String(answerText))}</span></div>`);
    } else if ((mode === 'single' || mode === 'multi') && selected.length > 0) {
      for (const sel of selected) {
        lines.push(`<div class="tool-line">${okIconSvg()}<span class="tool-file" style="margin-left:6px;">${escapeHtmlLite(String(sel))}</span></div>`);
      }
    } else {
      lines.push(`<div class="tool-line">${okIconSvg()}<span class="tool-sub" style="margin-left:6px;color:#6b7280;">Answered</span></div>`);
    }
  } else {
    // Show waiting state (should not normally happen in completed renderer)
    lines.push(`<div class="tool-line"><span class="tool-sub" style="color:#6b7280;">Waiting for response...</span></div>`);
  }
  
  return lines.join('');
}

module.exports = { render };
