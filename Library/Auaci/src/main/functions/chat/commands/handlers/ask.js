// src/main/functions/chat/commands/handlers/ask.js
// Interactive Ask tool handler (non-executing placeholder)
// This handler does not execute any side effects. The Ask tool is rendered
// and completed via UI in toolRender/sendMessage. Returning a minimal object
// keeps executor tolerant if called directly.

module.exports = async function askCmd(params) {
  const question = (params && typeof params.question === 'string') ? params.question : '';
  const mode = (params && typeof params.mode === 'string') ? params.mode.toLowerCase() : 'free';
  const options = Array.isArray(params && params.options) ? params.options : [];
  return {
    waiting: true,
    question,
    mode,
    options
  };
};
