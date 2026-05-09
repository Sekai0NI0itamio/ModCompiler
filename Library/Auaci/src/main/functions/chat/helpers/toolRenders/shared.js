// src/main/functions/chat/helpers/toolRenders/shared.js
// Shared utilities for tool rendering

function escapeHtmlLite(s) {
  return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function okIconSvg() {
  return '<svg class="tool-icon ok" width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M9 16.2l-3.5-3.5L4 14.2l5 5 11-11-1.5-1.5L9 16.2z" fill="#1a7f37"/></svg>';
}

function failIconSvg() {
  return '<svg class="tool-icon fail" width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M18.3 5.71L12 12.01 5.7 5.7 4.29 7.11 10.59 13.4 4.29 19.7 5.7 21.11 12 14.81 18.3 21.12 19.71 19.71 13.41 13.4 19.71 7.1z" fill="#b00020"/></svg>';
}

function pendingIconSvg() {
  return '<svg class="tool-icon pending" width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="10" stroke="#999" stroke-width="2" fill="none"/><path d="M12 6v6l4 2" stroke="#999" stroke-width="2" fill="none" stroke-linecap="round"/></svg>';
}

function spinnerIconSvg() {
  return '<svg class="tool-icon spinner" width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style="animation: tool-spin 1s linear infinite;"><circle cx="12" cy="12" r="10" stroke="#e5e7eb" stroke-width="2" fill="none"/><path d="M12 2a10 10 0 0 1 10 10" stroke="#3b82f6" stroke-width="2" fill="none" stroke-linecap="round"/></svg>';
}

// Inject spinner animation CSS if not present
function ensureSpinnerStyles() {
  if (typeof document !== 'undefined' && !document.getElementById('tool-spinner-styles')) {
    const style = document.createElement('style');
    style.id = 'tool-spinner-styles';
    style.textContent = `
      @keyframes tool-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
      .tool-icon.spinner {
        animation: tool-spin 1s linear infinite;
      }
      .tool-executing {
        opacity: 0.85;
      }
      .tool-executing .tool-body {
        background: linear-gradient(90deg, #f8f9fa 0%, #f1f5f9 50%, #f8f9fa 100%);
        background-size: 200% 100%;
        animation: tool-shimmer 1.5s ease-in-out infinite;
      }
      @keyframes tool-shimmer {
        0% { background-position: 200% 0; }
        100% { background-position: -200% 0; }
      }
    `;
    document.head.appendChild(style);
  }
}

/**
 * Build a tool line with icon and column layout
 */
function buildToolLine(icon, colParts) {
  return `<div class="tool-line">${icon}<div class="tool-col" style="display:flex;flex-direction:column;gap:2px;margin-left:6px;">${colParts.join('')}</div></div>`;
}

/**
 * Build a file path element
 */
function buildFilePath(path) {
  return `<div class="tool-file" style="white-space:normal;word-break:break-word;overflow-wrap:anywhere;">${escapeHtmlLite(String(path || ''))}</div>`;
}

/**
 * Build a sub-info element
 */
function buildSubInfo(text, color = '#6b7280') {
  return `<div class="tool-sub" style="color:${color};">${escapeHtmlLite(String(text))}</div>`;
}

/**
 * Build a summary line with border
 */
function buildSummaryLine(text, color = '#6b7280') {
  return `<div class="tool-line" style="margin-top:4px;padding-top:4px;border-top:1px solid #e5e7eb;"><span style="color:${color};font-weight:500;">${escapeHtmlLite(text)}</span></div>`;
}

module.exports = {
  escapeHtmlLite,
  okIconSvg,
  failIconSvg,
  pendingIconSvg,
  spinnerIconSvg,
  ensureSpinnerStyles,
  buildToolLine,
  buildFilePath,
  buildSubInfo,
  buildSummaryLine
};
