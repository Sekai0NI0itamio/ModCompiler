// src/main/functions/chat/concurrent/index.js
// Main entry point for concurrent session management
// Re-exports all concurrent session functionality

const sessionStateManager = require('./sessionStateManager');
const sessionRenderer = require('./sessionRenderer');

module.exports = {
  // Session state management
  ...sessionStateManager,
  
  // Session rendering
  ...sessionRenderer
};
