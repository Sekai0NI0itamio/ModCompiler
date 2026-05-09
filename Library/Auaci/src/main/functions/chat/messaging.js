// src/main/functions/chat/messaging.js
// Thin wrapper to retain existing imports in other modules
const { sendMessage, setupCodeBlockListeners } = require('./send/sendMessage');

module.exports = { sendMessage, setupCodeBlockListeners };