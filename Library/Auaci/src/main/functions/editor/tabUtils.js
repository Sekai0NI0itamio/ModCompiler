// src/main/functions/editor/tabUtils.js
const path = require('path');

function formatFileSize(size) {
  const units = ['B', 'KB', 'MB', 'GB'];
  let n = size, i = 0;
  while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
  return `${n.toFixed(1)} ${units[i]}`;
}

function createTabId() {
  return 't' + Date.now() + Math.floor(Math.random() * 1000);
}

module.exports = { formatFileSize, createTabId };