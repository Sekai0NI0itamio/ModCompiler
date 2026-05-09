// src/main/functions/chat/helpers/toolRenders/completed/web_fetch.js
// Renderer for completed web_fetch tool

const { escapeHtmlLite, okIconSvg, failIconSvg, buildToolLine, buildFilePath, buildSubInfo } = require('../shared');

function render(input, result, options = {}) {
  const lines = [];
  
  const url = input?.url || result?.url || '';
  const title = result?.title || '';
  const hasError = !!(result?.error);
  const success = result?.success !== false && !hasError;
  const contentLength = result?.content_length;
  
  const icon = hasError ? failIconSvg() : (success ? okIconSvg() : failIconSvg());
  
  const colParts = [];
  colParts.push(buildFilePath(url));
  
  if (title) {
    colParts.push(buildSubInfo(title));
  }
  
  if (hasError) {
    colParts.push(buildSubInfo(result.error, '#b00020'));
  } else if (contentLength) {
    colParts.push(buildSubInfo(`${formatBytes(contentLength)} fetched`));
  } else if (success) {
    colParts.push(buildSubInfo('Content fetched', '#1a7f37'));
  }
  
  lines.push(buildToolLine(icon, colParts));
  return lines.join('');
}

function formatBytes(bytes) {
  if (!bytes || bytes < 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i++;
  }
  return `${size.toFixed(i > 0 ? 1 : 0)} ${units[i]}`;
}

module.exports = { render };
