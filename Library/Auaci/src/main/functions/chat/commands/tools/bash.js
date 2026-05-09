// src/main/functions/chat/commands/tools/bash.js
// Executes bash commands via the IDE terminal panel
// This is an alias for run_command - both use the same terminal-based execution

const runCommandHandler = require('../handlers/run_command');

module.exports = async function bash(params) {
  const command = params && params.command;
  if (!command) throw new Error('bash: missing command');
  
  // Delegate to run_command handler which uses the terminal panel
  return await runCommandHandler({ command });
};
