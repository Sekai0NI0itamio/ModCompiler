// src/main/functions/debug/init.js
const { startSizeLogger } = require('./sizelogger');

/**
 * Initialize the size logger. This wrapper ensures we start after DOM ready.
 * It configures the logger to append to /tmp/size.log once per second.
 */
function initSizeLogger() {
  const start = () => {
    startSizeLogger({
      directory: '#directory-viewer',
      editor: '#editor',
      chat: '#chat',
      app: '#app-container'
    }, {
      intervalMs: 1000,
      filePath: '/tmp/size.log',
      appendNewline: true
    });
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, { once: true });
  } else {
    start();
  }
}

module.exports = { initSizeLogger };