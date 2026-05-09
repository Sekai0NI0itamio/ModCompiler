// src/main/functions/chat/commands/handlers/run_command.js
// Executes commands in the IDE's terminal panel so users can see real-time output
// Uses a per-session terminal for each AI chat session
// Waits for the terminal's running state to clear before returning results

const { ipcRenderer } = require('electron');

// Map of chat session IDs to terminal session IDs
const sessionTerminalMap = new Map();

// Command progress notification state
let commandNotificationEl = null;
let elapsedInterval = null;
let autoStopInterval = null;
let commandStartTime = null;
let isInTimeoutMode = false;
let autoStopCountdown = 60;
let continueForever = false;
let customTimeoutSeconds = 120;

// Note: Truncation happens at GPT system level via gptCycle.js
// This tool returns full output without any local truncation

/**
 * Get or create a terminal session ID for a chat session
 * Each chat session gets its own terminal
 */
async function getTerminalSessionId() {
  try {
    const { getSessionId } = require('../../sessionManager');
    const chatSessionId = await getSessionId();
    
    if (chatSessionId && sessionTerminalMap.has(chatSessionId)) {
      return sessionTerminalMap.get(chatSessionId);
    }
    
    // Create a unique terminal session ID for this chat session
    const terminalSessionId = `ai-agent-${chatSessionId || Date.now()}`;
    
    if (chatSessionId) {
      sessionTerminalMap.set(chatSessionId, terminalSessionId);
    }
    
    return terminalSessionId;
  } catch (err) {
    console.warn('[run_command] Failed to get chat session ID:', err);
    return `ai-agent-${Date.now()}`;
  }
}

/**
 * Get the current working directory from terminalInput
 */
function getCurrentWorkingDirectory() {
  try {
    const { getCurrentCwd } = require('../../ui/terminalInput');
    return getCurrentCwd();
  } catch (_) {
    return null;
  }
}

/**
 * Show stop options dialog (for right-click - allows entering a reason)
 * @returns {Promise<{action: string, reason?: string}>} - action: 'cancel', 'force', 'force-with-reason'
 */
function showStopOptionsDialog() {
  return new Promise((resolve) => {
    const DIALOG_ID = 'command-stop-dialog';
    
    // Remove existing dialog if any
    const existing = document.getElementById(DIALOG_ID);
    if (existing) existing.remove();
    
    const dialog = document.createElement('div');
    dialog.id = DIALOG_ID;
    dialog.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0,0,0,0.4);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 2147483648;
    `;
    
    dialog.innerHTML = `
      <div class="dialog-box" style="
        background: #fff;
        border-radius: 10px;
        padding: 20px 24px;
        max-width: 450px;
        width: 90%;
        box-shadow: 0 10px 40px rgba(0,0,0,0.15);
      ">
        <div class="dialog-title" style="
          font-size: 16px;
          font-weight: 600;
          color: #111;
          margin-bottom: 10px;
        ">Stop Command with Reason</div>
        <div class="dialog-message" style="
          font-size: 14px;
          color: #4b5563;
          margin-bottom: 18px;
          line-height: 1.5;
        ">Provide a reason for stopping this command (will be sent to GPT):</div>
        
        <div style="margin-bottom: 18px;">
          <textarea id="stop-reason-input" placeholder="Enter reason for stopping (e.g., 'taking too long', 'wrong command', 'need different approach', etc.)" style="
            width: 100%;
            min-height: 80px;
            padding: 10px 12px;
            border-radius: 6px;
            border: 1px solid #d1d5db;
            font-size: 13px;
            font-family: inherit;
            resize: vertical;
            box-sizing: border-box;
          "></textarea>
        </div>
        
        <div class="dialog-buttons" style="display: flex; gap: 10px; justify-content: flex-end;">
          <button class="dialog-btn cancel" style="
            padding: 8px 16px;
            border-radius: 6px;
            font-size: 13px;
            cursor: pointer;
            border: none;
            background: #f3f4f6;
            color: #374151;
          ">Cancel</button>
          <button class="dialog-btn stop-with-reason" style="
            padding: 8px 16px;
            border-radius: 6px;
            font-size: 13px;
            cursor: pointer;
            border: none;
            background: #dc2626;
            color: white;
          ">Stop with Reason</button>
        </div>
      </div>
    `;
    
    document.body.appendChild(dialog);
    
    const cleanup = () => {
      window.removeEventListener('keydown', onKey, true);
      dialog.remove();
    };
    
    // Stop with reason button
    const stopWithReasonBtn = dialog.querySelector('.dialog-btn.stop-with-reason');
    stopWithReasonBtn.addEventListener('mouseover', () => {
      stopWithReasonBtn.style.background = '#b91c1c';
    });
    stopWithReasonBtn.addEventListener('mouseout', () => {
      stopWithReasonBtn.style.background = '#dc2626';
    });
    stopWithReasonBtn.addEventListener('click', () => {
      const input = document.getElementById('stop-reason-input');
      const reason = input.value.trim() || 'User stopped the command';
      cleanup();
      resolve({ action: 'force-with-reason', reason });
    });
    
    // Cancel button
    const cancelBtn = dialog.querySelector('.dialog-btn.cancel');
    cancelBtn.addEventListener('mouseover', () => {
      cancelBtn.style.background = '#e5e7eb';
    });
    cancelBtn.addEventListener('mouseout', () => {
      cancelBtn.style.background = '#f3f4f6';
    });
    cancelBtn.addEventListener('click', () => {
      cleanup();
      resolve({ action: 'cancel' });
    });
    
    // Close on backdrop click
    dialog.addEventListener('click', (e) => {
      if (e.target === dialog) {
        cleanup();
        resolve({ action: 'cancel' });
      }
    });
    
    // Handle keyboard
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        cleanup();
        resolve({ action: 'cancel' });
      } else if (e.key === 'Enter' && !e.shiftKey) {
        // Enter submits with reason (unless shift is held for newline)
        const activeEl = document.activeElement;
        if (activeEl && activeEl.id === 'stop-reason-input') {
          e.preventDefault();
          e.stopPropagation();
          const reason = activeEl.value.trim() || 'User stopped the command';
          cleanup();
          resolve({ action: 'force-with-reason', reason });
        }
      }
    };
    window.addEventListener('keydown', onKey, true);
    
    // Focus the textarea
    setTimeout(() => {
      const input = document.getElementById('stop-reason-input');
      if (input) input.focus();
    }, 50);
  });
}

/**
 * Show continue options dialog
 * @returns {Promise<{action: string, seconds?: number}>} - action: 'cancel', '120', 'custom', 'forever'
 */
function showContinueOptionsDialog() {
  return new Promise((resolve) => {
    const DIALOG_ID = 'command-continue-dialog';
    
    // Remove existing dialog if any
    const existing = document.getElementById(DIALOG_ID);
    if (existing) existing.remove();
    
    const dialog = document.createElement('div');
    dialog.id = DIALOG_ID;
    dialog.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0,0,0,0.4);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 2147483648;
    `;
    
    dialog.innerHTML = `
      <div class="dialog-box" style="
        background: #fff;
        border-radius: 10px;
        padding: 20px 24px;
        max-width: 400px;
        box-shadow: 0 10px 40px rgba(0,0,0,0.15);
      ">
        <div class="dialog-title" style="
          font-size: 16px;
          font-weight: 600;
          color: #111;
          margin-bottom: 10px;
        ">Continue Command</div>
        <div class="dialog-message" style="
          font-size: 14px;
          color: #4b5563;
          margin-bottom: 18px;
          line-height: 1.5;
        ">How long would you like to continue running this command?</div>
        
        <div class="dialog-options" style="display: flex; flex-direction: column; gap: 10px; margin-bottom: 18px;">
          <button class="option-btn" data-action="120" style="
            padding: 10px 16px;
            border-radius: 6px;
            font-size: 13px;
            cursor: pointer;
            border: 1px solid #d1d5db;
            background: #f9fafb;
            color: #374151;
            text-align: left;
            transition: all 0.15s;
          ">Continue for 120 seconds more</button>
          
          <div style="display: flex; gap: 8px; align-items: center;">
            <button class="option-btn" data-action="custom" style="
              flex: 1;
              padding: 10px 16px;
              border-radius: 6px;
              font-size: 13px;
              cursor: pointer;
              border: 1px solid #d1d5db;
              background: #f9fafb;
              color: #374151;
              text-align: left;
              transition: all 0.15s;
            ">Continue for</button>
            <input type="number" id="custom-seconds-input" value="300" min="10" max="3600" style="
              width: 80px;
              padding: 8px 10px;
              border-radius: 6px;
              border: 1px solid #d1d5db;
              font-size: 13px;
              text-align: center;
            "/>
            <span style="font-size: 13px; color: #6b7280;">seconds</span>
          </div>
          
          <button class="option-btn" data-action="forever" style="
            padding: 10px 16px;
            border-radius: 6px;
            font-size: 13px;
            cursor: pointer;
            border: 1px solid #d1d5db;
            background: #f9fafb;
            color: #374151;
            text-align: left;
            transition: all 0.15s;
          ">Continue forever (until manual stop)</button>
        </div>
        
        <div class="dialog-buttons" style="display: flex; gap: 10px; justify-content: flex-end;">
          <button class="dialog-btn cancel" style="
            padding: 8px 16px;
            border-radius: 6px;
            font-size: 13px;
            cursor: pointer;
            border: none;
            background: #f3f4f6;
            color: #374151;
          ">Cancel</button>
        </div>
      </div>
    `;
    
    document.body.appendChild(dialog);
    
    const cleanup = () => {
      dialog.remove();
      window.removeEventListener('keydown', onKey);
    };
    
    // Handle option button clicks
    const optionBtns = dialog.querySelectorAll('.option-btn');
    optionBtns.forEach(btn => {
      btn.addEventListener('mouseover', () => {
        btn.style.background = '#e5e7eb';
        btn.style.borderColor = '#9ca3af';
      });
      btn.addEventListener('mouseout', () => {
        btn.style.background = '#f9fafb';
        btn.style.borderColor = '#d1d5db';
      });
      btn.addEventListener('click', () => {
        const action = btn.getAttribute('data-action');
        if (action === 'custom') {
          const input = document.getElementById('custom-seconds-input');
          const seconds = parseInt(input.value, 10) || 300;
          cleanup();
          resolve({ action: 'custom', seconds: Math.max(10, Math.min(3600, seconds)) });
        } else {
          cleanup();
          resolve({ action });
        }
      });
    });
    
    // Cancel button
    const cancelBtn = dialog.querySelector('.dialog-btn.cancel');
    cancelBtn.addEventListener('mouseover', () => {
      cancelBtn.style.background = '#e5e7eb';
    });
    cancelBtn.addEventListener('mouseout', () => {
      cancelBtn.style.background = '#f3f4f6';
    });
    cancelBtn.addEventListener('click', () => {
      cleanup();
      resolve({ action: 'cancel' });
    });
    
    // Close on backdrop click
    dialog.addEventListener('click', (e) => {
      if (e.target === dialog) {
        cleanup();
        resolve({ action: 'cancel' });
      }
    });
    
    // Handle keyboard
    const onKey = (e) => {
      if (e.key === 'Escape') {
        cleanup();
        resolve({ action: 'cancel' });
      } else if (e.key === 'Enter') {
        // Enter defaults to 120 seconds
        cleanup();
        resolve({ action: '120' });
      }
    };
    window.addEventListener('keydown', onKey);
    
    // Focus the input for easy editing
    setTimeout(() => {
      const input = document.getElementById('custom-seconds-input');
      if (input) input.select();
    }, 50);
  });
}

/**
 * Get terminal module
 */
function getTerminalModule() {
  try {
    return require('../../../terminal/init');
  } catch (err) {
    console.error('[run_command] Failed to load terminal module:', err);
    return null;
  }
}

/**
 * Create and show the command progress notification bar
 * Shows elapsed time from start, then switches to timeout mode after 2 minutes
 */
function showCommandNotification(command, onStop, onContinue) {
  hideCommandNotification(); // Clear any existing notification
  
  const inputContainer = document.getElementById('input-container');
  if (!inputContainer) return;
  
  commandStartTime = Date.now();
  isInTimeoutMode = false;
  autoStopCountdown = 60;
  
  // Create notification element - position above the GPT response indicator with spacing
  commandNotificationEl = document.createElement('div');
  commandNotificationEl.id = 'command-progress-notification';
  commandNotificationEl.style.cssText = `
    position: absolute;
    top: -64px;
    left: 0;
    right: 0;
    background: rgba(230, 243, 230, 0.95);
    border: 1px solid #c3e6c3;
    border-radius: 6px 6px 0 0;
    padding: 6px 12px;
    font-size: 12px;
    color: #2d5a2d;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    box-shadow: 0 -2px 4px rgba(0,0,0,0.05);
    backdrop-filter: blur(4px);
    z-index: 101;
    margin-bottom: 4px;
  `;
  
  // Message text
  const msgSpan = document.createElement('span');
  msgSpan.id = 'command-progress-msg';
  msgSpan.style.fontWeight = '500';
  msgSpan.textContent = 'Running command... 0s';
  
  // Buttons container (visible immediately)
  const buttonsContainer = document.createElement('div');
  buttonsContainer.id = 'command-timeout-buttons';
  buttonsContainer.style.cssText = 'display: flex; margin-left: auto; gap: 8px;';
  
  // Stop button
  const stopBtn = document.createElement('button');
  stopBtn.textContent = 'Stop';
  stopBtn.title = 'Left-click: Force stop | Right-click: Stop with reason';
  stopBtn.style.cssText = `
    padding: 5px 14px;
    font-size: 12px;
    font-weight: 500;
    cursor: pointer;
    background: #dc2626;
    color: white;
    border: none;
    border-radius: 4px;
    transition: background 0.15s;
  `;
  stopBtn.onmouseover = () => { stopBtn.style.background = '#b91c1c'; };
  stopBtn.onmouseout = () => { stopBtn.style.background = '#dc2626'; };
  
  // Left-click: Force stop immediately (no dialog)
  stopBtn.onclick = (e) => {
    e.preventDefault();
    e.stopPropagation();
    console.log('[run_command] Stop button left-clicked - force stopping immediately');
    onStop(null);
  };
  
  // Right-click: Show dialog with reason input
  stopBtn.oncontextmenu = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    console.log('[run_command] Stop button right-clicked - showing reason dialog');
    
    const result = await showStopOptionsDialog();
    
    if (result.action === 'cancel') {
      return;
    }
    
    if (result.action === 'force-with-reason') {
      onStop(result.reason);
    } else {
      onStop(null);
    }
  };
  
  // Options button (to change timeout settings)
  const continueBtn = document.createElement('button');
  continueBtn.textContent = 'Options';
  continueBtn.style.cssText = `
    padding: 5px 14px;
    font-size: 12px;
    font-weight: 500;
    cursor: pointer;
    background: #3b82f6;
    color: white;
    border: none;
    border-radius: 4px;
    transition: background 0.15s;
  `;
  continueBtn.onmouseover = () => { continueBtn.style.background = '#2563eb'; };
  continueBtn.onmouseout = () => { continueBtn.style.background = '#3b82f6'; };
  continueBtn.onclick = async () => {
    // Show continue options dialog
    const result = await showContinueOptionsDialog();
    
    if (result.action === 'cancel') {
      // User cancelled, keep showing timeout notification
      return;
    }
    
    let newTimeoutSeconds;
    if (result.action === '120') {
      newTimeoutSeconds = 120;
      continueForever = false;
    } else if (result.action === 'custom') {
      newTimeoutSeconds = result.seconds || 300;
      continueForever = false;
    } else if (result.action === 'forever') {
      continueForever = true;
      newTimeoutSeconds = 0; // Not used when forever
    }
    
    // Reset to counting mode
    commandStartTime = Date.now();
    isInTimeoutMode = false;
    autoStopCountdown = 60;
    customTimeoutSeconds = newTimeoutSeconds;
    
    if (continueForever) {
      switchToForeverMode();
    } else {
      switchToCountingMode();
    }
    
    onContinue();
  };
  
  buttonsContainer.appendChild(stopBtn);
  buttonsContainer.appendChild(continueBtn);
  
  commandNotificationEl.appendChild(msgSpan);
  commandNotificationEl.appendChild(buttonsContainer);
  
  inputContainer.appendChild(commandNotificationEl);
  
  // Start elapsed time counter
  elapsedInterval = setInterval(() => {
    if (!commandNotificationEl) return;
    
    const elapsed = Math.floor((Date.now() - commandStartTime) / 1000);
    const msgEl = document.getElementById('command-progress-msg');
    const buttonsEl = document.getElementById('command-timeout-buttons');
    
    if (continueForever) {
      // Forever mode - just show elapsed time
      if (msgEl) {
        msgEl.textContent = `Running command... ${elapsed}s (no timeout)`;
      }
    } else if (!isInTimeoutMode) {
      // Counting up mode
      const timeoutSeconds = customTimeoutSeconds || 120;
      if (elapsed >= timeoutSeconds) {
        // Switch to timeout mode
        isInTimeoutMode = true;
        switchToTimeoutMode(onStop);
      } else {
        // Show time remaining until timeout
        const remaining = timeoutSeconds - elapsed;
        if (msgEl) {
          msgEl.textContent = `Running command... ${elapsed}s (${remaining}s until timeout)`;
        }
      }
    }
  }, 1000);
  
  function switchToForeverMode() {
    const notifEl = document.getElementById('command-progress-notification');
    const msgEl = document.getElementById('command-progress-msg');
    
    // Use a distinct color for forever mode (purple-ish)
    if (notifEl) {
      notifEl.style.background = 'rgba(237, 233, 254, 0.95)';
      notifEl.style.borderColor = '#a78bfa';
      notifEl.style.color = '#5b21b6';
    }
    if (msgEl) {
      msgEl.textContent = 'Running command... 0s (no timeout)';
    }
    
    // Clear auto-stop interval if running
    if (autoStopInterval) {
      clearInterval(autoStopInterval);
      autoStopInterval = null;
    }
  }
  
  function switchToCountingMode() {
    const notifEl = document.getElementById('command-progress-notification');
    const msgEl = document.getElementById('command-progress-msg');
    
    if (notifEl) {
      notifEl.style.background = 'rgba(230, 243, 230, 0.95)';
      notifEl.style.borderColor = '#c3e6c3';
      notifEl.style.color = '#2d5a2d';
    }
    if (msgEl) {
      const timeoutSec = customTimeoutSeconds || 120;
      msgEl.textContent = `Running command... 0s (${timeoutSec}s until timeout)`;
    }
    
    // Clear auto-stop interval if running
    if (autoStopInterval) {
      clearInterval(autoStopInterval);
      autoStopInterval = null;
    }
  }
  
  function switchToTimeoutMode(onStopCallback) {
    const notifEl = document.getElementById('command-progress-notification');
    const msgEl = document.getElementById('command-progress-msg');
    
    // Change to warning colors (amber/yellow style)
    if (notifEl) {
      notifEl.style.background = 'rgba(254, 243, 199, 0.95)';
      notifEl.style.borderColor = '#fbbf24';
      notifEl.style.color = '#92400e';
    }
    
    if (msgEl) {
      msgEl.textContent = `Command running for 2+ minutes. Auto-stopping in ${autoStopCountdown}s`;
    }
    
    // Start auto-stop countdown
    autoStopInterval = setInterval(() => {
      autoStopCountdown--;
      const el = document.getElementById('command-progress-msg');
      if (el && isInTimeoutMode) {
        el.textContent = `Command running for 2+ minutes. Auto-stopping in ${autoStopCountdown}s`;
      }
      if (autoStopCountdown <= 0) {
        if (autoStopInterval) {
          clearInterval(autoStopInterval);
          autoStopInterval = null;
        }
        onStopCallback(null); // Auto-stop with no reason
      }
    }, 1000);
  }
}

/**
 * Hide and cleanup the command notification
 */
function hideCommandNotification() {
  if (elapsedInterval) {
    clearInterval(elapsedInterval);
    elapsedInterval = null;
  }
  if (autoStopInterval) {
    clearInterval(autoStopInterval);
    autoStopInterval = null;
  }
  if (commandNotificationEl && commandNotificationEl.parentNode) {
    commandNotificationEl.parentNode.removeChild(commandNotificationEl);
  }
  commandNotificationEl = null;
  commandStartTime = null;
  isInTimeoutMode = false;
  autoStopCountdown = 60;
  continueForever = false;
  customTimeoutSeconds = 120;
}

/**
 * Ensure terminal is visible and session exists for this chat session
 * Creates a per-session terminal if needed
 */
async function ensureTerminalReady() {
  const termModule = getTerminalModule();
  if (!termModule) {
    console.error('[run_command] Terminal module not available');
    return { ready: false, sessionId: null };
  }
  
  try {
    // Get the terminal session ID for this chat session
    const sessionId = await getTerminalSessionId();
    
    // Get the current working directory
    const cwd = getCurrentWorkingDirectory();
    
    // Step 1: Check if terminal is already visible
    const wasVisible = termModule.isTerminalVisible ? termModule.isTerminalVisible() : false;
    
    // Step 2: Show terminal panel first (use async version if available)
    if (termModule.showTerminalAsync) {
      await termModule.showTerminalAsync();
    } else if (termModule.showTerminal) {
      termModule.showTerminal();
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    
    // Step 3: Create/ensure session for this chat session
    const existingIds = termModule.getSessionIds ? termModule.getSessionIds() : [];
    
    if (!existingIds.includes(sessionId)) {
      // Create a new session with a descriptive title
      const chatSessionId = sessionId.replace('ai-agent-', '');
      const title = `AI: ${chatSessionId.slice(0, 8)}`;
      
      if (termModule.createSession) {
        await termModule.createSession(sessionId, title);
        console.log(`[run_command] Created terminal session: ${sessionId}`);
        
        // Set the cwd on the session before starting backend
        if (cwd && termModule.setSessionCwd) {
          termModule.setSessionCwd(sessionId, cwd);
        }
        
        // Start the backend for this session
        if (termModule.startBackendForSession) {
          await termModule.startBackendForSession(sessionId);
        }
        
        // Add tab to UI
        if (termModule.addSessionTabToUI) {
          termModule.addSessionTabToUI(sessionId, title);
        }
      }
    } else {
      // Session exists - check if we need to cd to the current directory
      if (cwd) {
        // Send cd command to sync terminal to current cwd
        const { ipcRenderer } = require('electron');
        ipcRenderer.send('terminal-input', { sessionId, data: `cd ${cwd}\n` });
        await new Promise(resolve => setTimeout(resolve, 100));
      }
    }
    
    // Switch to this session
    if (termModule.switchSession) {
      termModule.switchSession(sessionId);
    }
    
    await new Promise(resolve => setTimeout(resolve, 300));
    
    return { ready: true, sessionId };
    
  } catch (err) {
    console.error('[run_command] Failed to setup terminal:', err);
    return { ready: false, sessionId: null };
  }
}

/**
 * Clean ANSI escape codes and control sequences from output
 */
function cleanAnsiCodes(text) {
  return text
    // Remove standard ANSI escape sequences (colors, cursor, etc.)
    .replace(/\x1b\[[0-9;]*[a-zA-Z]/g, '')
    // Remove OSC sequences (title setting, etc.)
    .replace(/\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)/g, '')
    // Remove bracketed paste mode sequences
    .replace(/\x1b\[\?[0-9]+[hl]/g, '')
    // Remove other escape sequences
    .replace(/\x1b[PX^_].*?\x1b\\/g, '')
    .replace(/\x1b\][^\a]*(?:\a|\x1b\\)/g, '')
    // Remove alternate screen buffer sequences
    .replace(/\x1b\[\?47[hl]/g, '')
    .replace(/\x1b\[\?1049[hl]/g, '')
    // Remove carriage returns
    .replace(/\r/g, '')
    // Remove null bytes
    .replace(/\x00/g, '')
    // Remove other control characters except newline and tab
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '');
}

/**
 * Check if a line looks like a shell prompt
 */
function isShellPrompt(line) {
  const trimmed = line.trim();
  if (!trimmed) return false;
  
  // Ends with common prompt characters
  if (/[\$\%\>]\s*$/.test(trimmed)) {
    // But not if it's a very long line (likely output, not prompt)
    if (trimmed.length < 150) {
      return true;
    }
  }
  
  // Matches user@host pattern followed by prompt char
  if (/^[a-zA-Z0-9_-]+@[a-zA-Z0-9_.-]+.*[\$\%\>]\s*$/.test(trimmed)) {
    return true;
  }
  
  return false;
}

/**
 * Send command to terminal and capture output
 * Uses terminal's running state to detect when command finishes
 */
async function executeInTerminal(command, timeout = 120000) {
  return new Promise(async (resolve) => {
    const termModule = getTerminalModule();
    
    // Ensure terminal and session are ready
    const { ready, sessionId } = await ensureTerminalReady();
    if (!ready || !sessionId) {
      resolve({
        finished: {
          command,
          output: 'Error: Failed to initialize terminal. Please try again.',
          exit_code: 1,
          new_pwd: getCurrentWorkingDirectory() || process.cwd(),
        },
        success: false,
        error: 'Failed to initialize terminal',
        error_code: 'ERR_TERMINAL'
      });
      return;
    }
    
    let rawOutput = '';
    let commandSent = false;
    let commandEchoed = false;
    let resolved = false;
    let checkRunningInterval = null;
    let lastDataTime = Date.now();
    let forceStopRequested = false;
    let localCommandStartTime = Date.now(); // Track start time locally for quick command detection
    let commandConfirmedRunning = false; // Track if we've confirmed the command started running
    let noOutputSinceStart = true; // Track if we've received any output since command was sent
    let promptSeenCount = 0; // Count consecutive prompt detections for reliability

    const cleanup = () => {
      if (resolved) return;
      resolved = true;
      if (checkRunningInterval) clearTimeout(checkRunningInterval);
      hideCommandNotification();
      ipcRenderer.removeListener('terminal-data', dataHandler);
      // Clear the running command state
      if (termModule && termModule.clearRunningCommand) {
        termModule.clearRunningCommand(sessionId);
      }
    };

    const processOutput = () => {
      // Clean ANSI codes
      let cleanOutput = rawOutput;
      // Remove ANSI color codes and escape sequences
      cleanOutput = cleanOutput.replace(/\x1b\[[0-9;]*m/g, '');
      cleanOutput = cleanOutput.replace(/\x1b\[[0-9;]*[A-Za-z]/g, '');
      
      // Split into lines
      const lines = cleanOutput.split('\n');
      const resultLines = [];
      let foundCommandEcho = false;
      
      // Find the last prompt line index
      let lastPromptIndex = -1;
      for (let i = lines.length - 1; i >= 0; i--) {
        if (isShellPrompt(lines[i])) {
          lastPromptIndex = i;
          break;
        }
      }
      
      // Process lines from bottom to top to show most recent output first
      // This time we keep it in reverse order (don't reverse back)
      for (let i = lines.length - 1; i >= 0; i--) {
        const line = lines[i];
        const trimmed = line.trim();
        
        // Skip empty lines at the start (when processing from bottom)
        if (resultLines.length === 0 && !trimmed) continue;
        
        // Skip the echoed command (first occurrence when going bottom-up)
        if (!foundCommandEcho && trimmed === command.trim()) {
          foundCommandEcho = true;
          continue;
        }
        
        // Skip .auaci references
        if (line.includes('.auaci')) continue;
        
        // Skip the final prompt line
        if (i === lastPromptIndex) continue;
        
        // Skip lines that are just prompts near the end
        if (isShellPrompt(line) && i >= lastPromptIndex - 2 && lastPromptIndex > 0) continue;
        
        resultLines.push(line);
      }
      
      // Remove trailing empty lines (now at the beginning since we're in reverse)
      while (resultLines.length > 0 && !resultLines[resultLines.length - 1].trim()) {
        resultLines.pop();
      }
      
      // Keep in reverse order - don't reverse back!
      // This way, the output is shown from end to start (most recent first)
      // When gptCycle.js truncates the last 15KB, the user will see recent results
      return resultLines.join('\n').trim();
    };

    const finishWithOutput = (exitCode = 0, stopReason = null) => {
      cleanup();
      
      let cleanOutput = processOutput();
      
      // Record the full output for debugging
      const fullOutputLength = cleanOutput.length;
      console.log(`[run_command] Command finished, full output length: ${fullOutputLength} characters`);
      
      // NO TRUNCATION HERE - GPT system will handle truncation
      // We return the full output content
      
      // If there's a stop reason, format the output with the reason
      let finalOutput;
      if (stopReason) {
        finalOutput = `User Reason for Stopping the Command: ${stopReason}\n\nCommand Output:\n${cleanOutput || '(no output)'}`;
      } else {
        finalOutput = cleanOutput || '(command completed with no output)';
      }
      
      // Calculate line count to prevent automatic truncation
      const linesArr = finalOutput.split(/\r?\n/);
      const totalLines = linesArr.length;
      
      resolve({
        finished: {
          command,
          output: finalOutput,
          exit_code: exitCode,
          new_pwd: getCurrentWorkingDirectory() || process.cwd(),
          full_output_length: fullOutputLength, // Record full length for debugging
          displayed_lines: totalLines, // Show all lines
          total_lines: totalLines, // Total lines equals displayed lines
          truncated: false, // Explicitly disable truncation
        },
        success: exitCode === 0
      });
    };

    // Force stop the command (send Ctrl+C to terminal and wait for it to stop)
    const forceStopCommand = async (stopReason = null) => {
      if (forceStopRequested) return;
      forceStopRequested = true;
      
      console.log('[run_command] Force stopping command', stopReason ? `with reason: ${stopReason}` : '');
      hideCommandNotification();
      
      try {
        // Send Ctrl+C to stop the command
        ipcRenderer.send('terminal-input', { sessionId, data: '\x03' });
      } catch (_) {}
      
      // Wait for the command to actually stop (check for shell prompt)
      let stopCheckCount = 0;
      const maxStopChecks = 20; // 2 seconds max wait
      
      const waitForStop = () => {
        stopCheckCount++;
        
        // Check if terminal reports command stopped
        const isRunning = termModule && termModule.isCommandRunning 
          ? termModule.isCommandRunning(sessionId) 
          : false;
        
        if (!isRunning || stopCheckCount >= maxStopChecks) {
          // Command stopped or timeout - finish with results
          console.log('[run_command] Command stopped after Ctrl+C');
          if (!resolved) {
            finishWithOutput(130, stopReason); // 130 = terminated by Ctrl+C
          }
        } else {
          // Still running, check again
          setTimeout(waitForStop, 100);
        }
      };
      
      // Start checking after a brief delay for Ctrl+C to take effect
      setTimeout(waitForStop, 200);
    };

    // Listen for terminal output
    const dataHandler = (_event, payload) => {
      if (payload.sessionId !== sessionId) return;
      
      const chunk = payload.chunk || '';
      lastDataTime = Date.now();
      
      if (commandSent) {
        rawOutput += chunk;
        noOutputSinceStart = false; // We received some output
        
        // Check if we see the command echoed back
        if (!commandEchoed && rawOutput.includes(command)) {
          commandEchoed = true;
          commandConfirmedRunning = true; // Command was echoed, it's definitely running
          console.log('[run_command] Command echoed back, confirmed running');
        }
      }
    };

    ipcRenderer.on('terminal-data', dataHandler);

    // Send command to terminal
    setTimeout(() => {
      try {
        // Mark command as running in terminal state
        if (termModule && termModule.setRunningCommand) {
          termModule.setRunningCommand(sessionId, command);
        }
        
        ipcRenderer.send('terminal-input', { sessionId, data: command + '\n' });
        commandSent = true;
        console.log(`[run_command] Sent command to terminal: ${command}`);
        
        // Show the command progress notification immediately
        showCommandNotification(
          command,
          // onStop callback (receives optional reason)
          (stopReason) => {
            console.log('[run_command] User/auto chose to stop command');
            forceStopCommand(stopReason);
          },
          // onContinue callback
          () => {
            console.log('[run_command] User chose to continue');
            // Notification handles resetting the timer internally
          }
        );
        
        // Start checking if command is still running
        let checkCount = 0;
        const getCheckInterval = () => {
          // First 10 seconds: check every 100ms
          // 10-20 seconds: check every 500ms
          // After 20 seconds: check every 1000ms
          const timeSinceStart = Date.now() - localCommandStartTime;
          if (timeSinceStart < 10000) return 100;
          if (timeSinceStart < 20000) return 500;
          return 1000;
        };
        
        const scheduleNextCheck = () => {
          if (resolved) return;
          checkRunningInterval = setTimeout(checkIfDone, getCheckInterval());
        };
        
        const checkIfDone = () => {
          if (resolved) return;
          checkCount++;
          
          const isRunning = termModule && termModule.isCommandRunning 
            ? termModule.isCommandRunning(sessionId) 
            : true;
          
          const timeSinceLastData = Date.now() - lastDataTime;
          const timeSinceStart = Date.now() - localCommandStartTime;
          
          // Clean the current output for analysis
          const cleanOutput = cleanAnsiCodes(rawOutput);
          const lines = cleanOutput.split('\n').filter(l => l.trim());
          const lastLine = lines.length > 0 ? lines[lines.length - 1] : '';
          const lastFewLines = lines.slice(-5).join('\n');
          
          // Check for shell prompt in output (indicates command finished)
          const hasPromptAtEnd = isShellPrompt(lastLine) || 
            /[a-zA-Z0-9_-]+@[a-zA-Z0-9_.-]+.*[\$\%\>]\s*$/.test(lastFewLines) ||
            /[\$\%\>]\s*$/.test(lastLine);
          
          // For prompt detection, track consecutive detections
          if (hasPromptAtEnd) {
            promptSeenCount++;
          } else {
            promptSeenCount = 0; // Reset if prompt disappears (more output came)
          }
          
          // INSTANT command detection: if we see command echo AND prompt immediately after
          // This catches commands like `ls` that finish in milliseconds
          if (commandEchoed && hasPromptAtEnd && timeSinceLastData > 50 && rawOutput.length > 0) {
            console.log('[run_command] Instant command detected (echo + prompt + idle)');
            if (!resolved) finishWithOutput(0);
            return;
          }
          
          // Very minimum wait time - only 150ms to allow command to start
          const minRunTime = 150;
          if (timeSinceStart < minRunTime) {
            scheduleNextCheck();
            return;
          }
          
          // If we haven't even seen the command echo yet, keep waiting
          // This prevents finishing before the command even starts
          if (!commandEchoed && timeSinceStart < 10000) {
            scheduleNextCheck();
            return;
          }
          
          // Command is done if terminal says it's not running anymore
          // BUT only trust this if we've confirmed the command started running
          if (!isRunning && commandEchoed && commandConfirmedRunning) {
            console.log('[run_command] Terminal reports command finished');
            if (!resolved) finishWithOutput(0);
            return;
          }
          
          // Quick command detection: if we have output with a prompt and data is idle
          // Single prompt detection is enough if data has been idle
          if (commandEchoed && hasPromptAtEnd && timeSinceLastData > 150) {
            console.log('[run_command] Detected prompt after command echo, command finished');
            if (!resolved) finishWithOutput(0);
            return;
          }
          
          // For commands that finished very quickly with stable prompt
          if (promptSeenCount >= 2 && timeSinceStart > 200 && timeSinceLastData > 100) {
            console.log('[run_command] Quick command with stable prompt, finishing');
            if (!resolved) finishWithOutput(0);
            return;
          }
          
          // Crash/error detection: look for common error patterns followed by prompt
          const hasErrorPattern = /error|Error|ERROR|failed|Failed|FAILED|exception|Exception|crash|Crash|CRASH|not found|command not found|No such file|Permission denied|Segmentation fault|Killed|Aborted/i.test(cleanOutput);
          if (hasErrorPattern && hasPromptAtEnd && timeSinceLastData > 200) {
            console.log('[run_command] Detected error/crash pattern with prompt, command finished');
            if (!resolved) finishWithOutput(1); // Exit code 1 for errors
            return;
          }
          
          // Fallback: if no data for 5 seconds and we have output with a prompt
          if (commandEchoed && timeSinceLastData > 5000 && hasPromptAtEnd) {
            console.log('[run_command] Detected prompt after long idle, assuming command finished');
            finishWithOutput(0);
            return;
          }
          
          // Extended fallback: if no data for 10 seconds even without clear prompt
          // but we have some output (command might have finished without clean prompt)
          if (timeSinceLastData > 10000 && rawOutput.length > 0 && timeSinceStart > 10000) {
            console.log('[run_command] Very long idle with output, assuming command finished');
            finishWithOutput(0);
            return;
          }
          
          // Safety: if command has been running for a very long time with no output at all
          // after the initial echo, something might be wrong
          if (commandEchoed && noOutputSinceStart && timeSinceStart > 30000) {
            console.log('[run_command] Command running 30s with no output after echo, may be frozen');
            // Don't auto-finish here, let the timeout mechanism handle it
            // Just log for debugging
          }
          
          scheduleNextCheck();
        };
        
        scheduleNextCheck();
        
      } catch (err) {
        cleanup();
        resolve({
          finished: {
            command,
            output: `Error sending command to terminal: ${err.message}`,
            exit_code: 1,
            new_pwd: process.cwd(),
          },
          success: false,
          error: `Error sending command to terminal: ${err.message}`,
          error_code: 'ERR_TERMINAL'
        });
      }
    }, 150);
  });
}

/**
 * Main handler for run_command tool
 */
module.exports = async function runCommandCmd(params) {
  const command = params.command;
  if (!command || typeof command !== 'string') {
    throw new Error('run_command: missing command');
  }

  console.log(`[run_command] Executing: ${command}`);
  
  // Execute command in the AI Agent terminal session
  const result = await executeInTerminal(command);
  
  console.log(`[run_command] Completed with exit code: ${result?.finished?.exit_code}`);
  return result;
};

// Export helpers for other modules
module.exports.executeInTerminal = executeInTerminal;
module.exports.ensureTerminalReady = ensureTerminalReady;
module.exports.getTerminalSessionId = getTerminalSessionId;
module.exports.getCurrentWorkingDirectory = getCurrentWorkingDirectory;
