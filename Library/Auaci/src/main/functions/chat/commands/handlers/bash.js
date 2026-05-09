// src/main/functions/chat/commands/handlers/bash.js
// Executes bash commands via the IDE terminal panel
// This is an alias for run_command - both use the same terminal-based execution

const runCommandHandler = require('./run_command');

async function bashCmd(params) {
  const command = params.command;
  if (!command) {
    return { success: false, error: 'bash: missing command', error_code: 'ERR_INVALID_INPUT' };
  }
  
  // Delegate to run_command handler which uses the terminal panel
  return await runCommandHandler({ command });
}

// Cancel is no longer supported since we use the terminal panel
bashCmd.cancel = function cancel(runId) {
  console.warn('[bash] Cancel not supported for terminal-based execution');
  return false;
};

module.exports = bashCmd;
