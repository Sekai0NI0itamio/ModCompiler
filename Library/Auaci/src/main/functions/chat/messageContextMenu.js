// src/main/functions/chat/messageContextMenu.js
// Right-click context menu for chat messages (GPT and user)
// Provides:
//  - GPT message: Copy raw, Copy formatted (visible text), Delete (GPT only)
//  - User message: Copy (raw text), Edit, Delete (entire entry)

const { deleteChatEntry, deleteGptPart, deleteEntriesFromIndex } = require('./incrementalHistoryStorage');
const { deleteMessagesByEntryIndex, deleteGptMessagesByEntryIndex, deleteMessagesFromEntryIndex } = require('./rawHistoryStorage');
const { getSessionId } = require('./sessionManager');
const { displayChatHistory } = require('./historyDisplay');

const MENU_STYLE_ID = 'auaci-chat-msg-context-styles';
const MENU_ID = 'auaci-chat-msg-contextmenu';
const DIALOG_ID = 'auaci-confirm-dialog';

function injectStyles() {
  if (document.getElementById(MENU_STYLE_ID)) return;
  const css = `
    /* Context menu for chat messages */
    #${MENU_ID} {
      position: fixed;
      z-index: 2147483647;
      min-width: 160px;
      background: #ffffff;
      border: 1px solid #dfe3e6;
      box-shadow: 0 6px 18px rgba(0,0,0,0.08);
      border-radius: 6px;
      font-family: "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      color: #111827;
      padding: 6px 0;
      user-select: none;
    }
    #${MENU_ID} .mi-item {
      padding: 8px 14px;
      font-size: 13px;
      cursor: pointer;
      white-space: nowrap;
    }
    #${MENU_ID} .mi-item:hover {
      background: #f3f4f6;
    }
    #${MENU_ID} .mi-item.delete-item {
      color: #dc2626;
    }
    #${MENU_ID} .mi-item.delete-item:hover {
      background: #fef2f2;
    }
    #${MENU_ID} .mi-separator {
      height: 1px;
      background: #e5e7eb;
      margin: 4px 0;
    }
    /* Confirmation dialog */
    #${DIALOG_ID} {
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
    }
    #${DIALOG_ID} .dialog-box {
      background: #fff;
      border-radius: 10px;
      padding: 20px 24px;
      max-width: 400px;
      box-shadow: 0 10px 40px rgba(0,0,0,0.15);
    }
    #${DIALOG_ID} .dialog-title {
      font-size: 16px;
      font-weight: 600;
      color: #111;
      margin-bottom: 10px;
    }
    #${DIALOG_ID} .dialog-message {
      font-size: 14px;
      color: #4b5563;
      margin-bottom: 18px;
      line-height: 1.5;
    }
    #${DIALOG_ID} .dialog-buttons {
      display: flex;
      gap: 10px;
      justify-content: flex-end;
    }
    #${DIALOG_ID} .dialog-btn {
      padding: 8px 16px;
      border-radius: 6px;
      font-size: 13px;
      cursor: pointer;
      border: none;
    }
    #${DIALOG_ID} .dialog-btn.cancel {
      background: #f3f4f6;
      color: #374151;
    }
    #${DIALOG_ID} .dialog-btn.cancel:hover {
      background: #e5e7eb;
    }
    #${DIALOG_ID} .dialog-btn.confirm {
      background: #dc2626;
      color: #fff;
    }
    #${DIALOG_ID} .dialog-btn.confirm:hover {
      background: #b91c1c;
    }
    #${DIALOG_ID} .dialog-btn.primary {
      background: #3b82f6;
      color: #fff;
    }
    #${DIALOG_ID} .dialog-btn.primary:hover {
      background: #2563eb;
    }
    /* Edit mode styles - highlight the selected user message */
    .user-message.edit-mode .message-content {
      background: #fef9c3 !important;
      border: 2px solid #fbbf24 !important;
      box-shadow: 0 0 0 3px rgba(251, 191, 36, 0.2);
      transition: background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
    }
    #cancel-edit-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 6px 12px;
      margin-bottom: 8px;
      font-size: 12px;
      font-weight: 500;
      color: #92400e;
      background: #fef3c7;
      border: 1px solid #fbbf24;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    #cancel-edit-btn:hover {
      background: #fde68a;
      border-color: #f59e0b;
    }
    #cancel-edit-btn:active {
      transform: scale(0.98);
    }
  `;
  const s = document.createElement('style');
  s.id = MENU_STYLE_ID;
  s.textContent = css;
  document.head.appendChild(s);
}

/**
 * Show a confirmation dialog
 * @param {string} title - Dialog title
 * @param {string} message - Dialog message
 * @param {string} confirmText - Confirm button text
 * @param {string} confirmClass - Confirm button class ('confirm' for red, 'primary' for blue)
 * @returns {Promise<boolean>} - True if confirmed, false if cancelled
 */
function showConfirmDialog(title, message, confirmText = 'Delete', confirmClass = 'confirm') {
  return new Promise((resolve) => {
    injectStyles();
    
    // Remove existing dialog if any
    const existing = document.getElementById(DIALOG_ID);
    if (existing) existing.remove();
    
    const dialog = document.createElement('div');
    dialog.id = DIALOG_ID;
    dialog.innerHTML = `
      <div class="dialog-box">
        <div class="dialog-title">${title}</div>
        <div class="dialog-message">${message}</div>
        <div class="dialog-buttons">
          <button class="dialog-btn cancel">Cancel</button>
          <button class="dialog-btn ${confirmClass}">${confirmText}</button>
        </div>
      </div>
    `;
    
    document.body.appendChild(dialog);
    
    const cancelBtn = dialog.querySelector('.dialog-btn.cancel');
    const confirmBtn = dialog.querySelector('.dialog-btn.' + confirmClass);
    
    const cleanup = () => {
      window.removeEventListener('keydown', onKey, true);
      dialog.remove();
    };
    
    cancelBtn.addEventListener('click', () => {
      cleanup();
      resolve(false);
    });
    
    confirmBtn.addEventListener('click', () => {
      cleanup();
      resolve(true);
    });
    
    // Close on backdrop click
    dialog.addEventListener('click', (e) => {
      if (e.target === dialog) {
        cleanup();
        resolve(false);
      }
    });
    
    // Close on Escape
    const onKey = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        cleanup();
        resolve(false);
      } else if (e.key === 'Enter') {
        // Enter key triggers the confirm/OK button
        e.preventDefault();
        e.stopPropagation();
        cleanup();
        resolve(true);
      }
    };
    window.addEventListener('keydown', onKey, true); // Use capture phase
  });
}

function hideMenu() {
  const m = document.getElementById(MENU_ID);
  if (m) m.remove();
  window.removeEventListener('mousedown', onWindowMouse);
  window.removeEventListener('keydown', onKeyDown);
}

function onWindowMouse(e) {
  const m = document.getElementById(MENU_ID);
  if (!m) return;
  if (!m.contains(e.target)) hideMenu();
}

function onKeyDown(e) {
  if (e.key === 'Escape') hideMenu();
}

function copyText(text) {
  if (typeof text !== 'string') text = String(text ?? '');
  try {
    return navigator.clipboard.writeText(text);
  } catch (_) {
    return new Promise((resolve) => {
      try {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.left = '-1000px';
        ta.style.top = '-1000px';
        document.body.appendChild(ta);
        ta.focus();
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
      } catch (_) {}
      resolve();
    });
  }
}
function showMenu(x, y, context) {
  injectStyles();
  hideMenu();

  const menu = document.createElement('div');
  menu.id = MENU_ID;

  function addItem(label, onClick, className = '') {
    const el = document.createElement('div');
    el.className = 'mi-item' + (className ? ' ' + className : '');
    el.textContent = label;
    el.addEventListener('click', async (ev) => {
      ev.stopPropagation();
      hideMenu();
      try { await onClick(); } catch (_) {}
    });
    menu.appendChild(el);
  }
  
  function addSeparator() {
    const sep = document.createElement('div');
    sep.className = 'mi-separator';
    menu.appendChild(sep);
  }

  if (window.__restoreUndoAvailable) {
    addItem('undo restore', async () => {
      await undoRestore();
    });
    addSeparator();
  }

  if (context.type === 'gpt') {
    addItem('copy raw', async () => {
      const idx = context.entryIndex;
      let text = '';
      try {
        if (Array.isArray(window.allChatEntries) && idx >= 0 && idx < window.allChatEntries.length) {
          const entry = window.allChatEntries[idx];
          if (entry && typeof entry.gpt === 'string') text = entry.gpt;
        }
      } catch (_) {}
      if (!text && context.messageEl) {
        const mc = context.messageEl.querySelector('.message-content');
        if (mc) text = mc.innerText || mc.textContent || '';
      }
      await copyText(text || '');
    });
    addItem('copy formatted', async () => {
      let text = '';
      try {
        const mc = context.messageEl ? context.messageEl.querySelector('.message-content') : null;
        if (mc) text = mc.innerText || mc.textContent || '';
      } catch (_) {}
      await copyText(text || '');
    });
    addSeparator();
    addItem('delete', async () => {
      const confirmed = await showConfirmDialog(
        'Delete GPT Response',
        'Are you sure you want to delete this GPT response? The user message will be kept.',
        'Delete',
        'confirm'
      );
      if (confirmed) {
        await deleteGptMessage(context.entryIndex, context.messageEl);
      }
    }, 'delete-item');
  } else if (context.type === 'user') {
    addItem('copy', async () => {
      const idx = context.entryIndex;
      let text = '';
      try {
        if (Array.isArray(window.allChatEntries) && idx >= 0 && idx < window.allChatEntries.length) {
          const entry = window.allChatEntries[idx];
          if (entry && entry.user && typeof entry.user.text === 'string') text = entry.user.text;
        }
      } catch (_) {}
      if (!text && context.messageEl) {
        const mc = context.messageEl.querySelector('.message-content');
        if (mc) text = mc.innerText || mc.textContent || '';
      }
      await copyText(text || '');
    });
    addItem('edit', async () => {
      await editUserMessage(context.entryIndex);
    });
    
    addSeparator();
    addItem('delete', async () => {
      const confirmed = await showConfirmDialog(
        'Delete Message',
        'Are you sure you want to delete this message and its GPT response?',
        'Delete',
        'confirm'
      );
      if (confirmed) {
        await deleteUserMessage(context.entryIndex, context.messageEl);
      }
    }, 'delete-item');
  }

  document.body.appendChild(menu);

  // Position with viewport clamping
  const vw = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
  const vh = Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0);
  const rect = menu.getBoundingClientRect();
  let left = x;
  let top = y;
  if (left + rect.width > vw) left = Math.max(8, vw - rect.width - 8);
  if (top + rect.height > vh) top = Math.max(8, vh - rect.height - 8);
  menu.style.left = `${left}px`;
  menu.style.top = `${top}px`;

  window.addEventListener('mousedown', onWindowMouse);
  window.addEventListener('keydown', onKeyDown);
}

/**
 * Delete only the GPT message part (keeps user message)
 */
async function deleteGptMessage(entryIndex, messageEl) {
  if (entryIndex < 0) {
    console.warn('[messageContextMenu] Invalid entry index for delete');
    return;
  }
  
  try {
    const sessionId = await getSessionId();
    
    await deleteGptPart(entryIndex, sessionId);
    await deleteGptMessagesByEntryIndex(sessionId, entryIndex);
    
    if (Array.isArray(window.allChatEntries) && entryIndex < window.allChatEntries.length) {
      window.allChatEntries[entryIndex].gpt = '';
    }
    
    if (messageEl) {
      messageEl.remove();
    }
    
    console.log(`[messageContextMenu] Deleted GPT message at index ${entryIndex}`);
    
  } catch (err) {
    console.error('[messageContextMenu] Failed to delete GPT message:', err);
  }
}

/**
 * Delete user message and corresponding GPT response (entire entry)
 */
async function deleteUserMessage(entryIndex, messageEl) {
  if (entryIndex < 0) {
    console.warn('[messageContextMenu] Invalid entry index for delete');
    return;
  }
  
  try {
    const sessionId = await getSessionId();
    
    const displayDeleted = await deleteChatEntry(entryIndex, sessionId);
    if (!displayDeleted) {
      console.warn('[messageContextMenu] Failed to delete from display history');
    }
    
    await deleteMessagesByEntryIndex(sessionId, entryIndex);
    
    if (Array.isArray(window.allChatEntries) && entryIndex < window.allChatEntries.length) {
      window.allChatEntries.splice(entryIndex, 1);
    }
    
    const container = document.getElementById('chat-messages');
    if (container) {
      const messages = container.querySelectorAll(`[data-entry-index="${entryIndex}"]`);
      messages.forEach(msg => msg.remove());
      
      const allMessages = container.querySelectorAll('[data-entry-index]');
      allMessages.forEach(msg => {
        const idx = parseInt(msg.getAttribute('data-entry-index'), 10);
        if (idx > entryIndex) {
          msg.setAttribute('data-entry-index', String(idx - 1));
        }
      });
    }
    
    console.log(`[messageContextMenu] Deleted user message and GPT response at index ${entryIndex}`);
    
  } catch (err) {
    console.error('[messageContextMenu] Failed to delete user message:', err);
  }
}

/**
 * Edit a user message - puts text in input and sets up for resend
 */
async function editUserMessage(entryIndex) {
  if (entryIndex < 0) {
    console.warn('[messageContextMenu] Invalid entry index for edit');
    return;
  }
  
  try {
    // Get the user message text - try multiple sources
    let userText = '';
    let userFiles = [];
    
    // First try window.allChatEntries (populated from history)
    if (Array.isArray(window.allChatEntries) && entryIndex < window.allChatEntries.length) {
      const entry = window.allChatEntries[entryIndex];
      if (entry && entry.user) {
        userText = entry.user.text || '';
        userFiles = Array.isArray(entry.user.files) ? entry.user.files : [];
      }
    }
    
    // If not found in window.allChatEntries, try to get from DOM (for live-streamed messages)
    if (!userText) {
      const container = document.getElementById('chat-messages');
      if (container) {
        const userMsgEl = container.querySelector(`.user-message[data-entry-index="${entryIndex}"]`);
        if (userMsgEl) {
          const contentEl = userMsgEl.querySelector('.message-content');
          if (contentEl) {
            // Get text content, excluding file boxes
            const textDiv = contentEl.querySelector('div:not(.file-box)');
            if (textDiv) {
              userText = textDiv.textContent || '';
            } else {
              // Fallback: get all text content
              userText = contentEl.textContent || '';
            }
          }
        }
      }
    }
    
    // If still not found, try loading from history file directly
    if (!userText) {
      try {
        const { getCurrentChatHistory } = require('./incrementalHistoryStorage');
        const history = await getCurrentChatHistory();
        if (history && Array.isArray(history.chat) && entryIndex < history.chat.length) {
          const entry = history.chat[entryIndex];
          if (entry && entry.user) {
            userText = entry.user.text || '';
            userFiles = Array.isArray(entry.user.files) ? entry.user.files : [];
          }
        }
      } catch (e) {
        console.warn('[messageContextMenu] Failed to load from history file:', e);
      }
    }
    
    if (!userText) {
      console.warn('[messageContextMenu] No user text found for edit');
      return;
    }
    
    // Put the text in the input
    const userInput = document.getElementById('user-input');
    if (userInput) {
      if (userInput.value !== undefined) {
        userInput.value = userText;
      } else {
        userInput.innerText = userText;
      }
      
      // Restore files
      window.droppedFiles = userFiles;
      
      // Trigger resize
      try {
        userInput.dispatchEvent(new Event('input', { bubbles: true }));
      } catch (_) {}
      
      // Focus the input and position cursor at the end
      userInput.focus();
      
      // Position cursor at the very end after a short delay to ensure DOM is updated
      setTimeout(() => {
        const textLength = userText.length;
        
        // For textarea/input elements
        if (userInput.setSelectionRange) {
          userInput.setSelectionRange(textLength, textLength);
        } else if (userInput.selectionStart !== undefined) {
          userInput.selectionStart = textLength;
          userInput.selectionEnd = textLength;
        }
        
        // For contenteditable elements
        if (userInput.isContentEditable) {
          const range = document.createRange();
          const sel = window.getSelection();
          if (userInput.childNodes.length > 0) {
            // Find the last text node
            let lastNode = userInput;
            while (lastNode.lastChild) {
              lastNode = lastNode.lastChild;
            }
            if (lastNode.nodeType === Node.TEXT_NODE) {
              range.setStart(lastNode, lastNode.length);
            } else {
              range.selectNodeContents(lastNode);
              range.collapse(false);
            }
          } else {
            range.selectNodeContents(userInput);
            range.collapse(false);
          }
          sel.removeAllRanges();
          sel.addRange(range);
        }
        
        // Scroll the input to the end to show the cursor position
        if (userInput.scrollHeight > userInput.clientHeight) {
          userInput.scrollTop = userInput.scrollHeight;
        }
        
        // Also scroll the input container if it exists
        const inputScroll = document.getElementById('input-scroll');
        if (inputScroll && inputScroll.scrollHeight > inputScroll.clientHeight) {
          inputScroll.scrollTop = inputScroll.scrollHeight;
        }
      }, 10);
    }
    
    // Set up the pending edit state
    window.__pendingEditEntryIndex = entryIndex;
    
    // Apply edit mode styling to the selected message and show cancel button
    applyEditModeUI(true, entryIndex);
    
    console.log(`[messageContextMenu] Set up edit for entry ${entryIndex}`);
    
  } catch (err) {
    console.error('[messageContextMenu] Failed to edit user message:', err);
  }
}

/**
 * Apply or remove edit mode UI styling
 * @param {boolean} isEditing - Whether we're entering or exiting edit mode
 * @param {number|null} entryIndex - The entry index of the message being edited
 */
function applyEditModeUI(isEditing, entryIndex = null) {
  const cancelBtn = document.getElementById('cancel-edit-btn');
  const container = document.getElementById('chat-messages');
  
  if (isEditing && entryIndex !== null) {
    // Remove any previous edit-mode styling from other messages
    if (container) {
      container.querySelectorAll('.user-message.edit-mode').forEach(el => {
        el.classList.remove('edit-mode');
      });
    }
    
    // Add edit mode class to the selected user message for yellow styling
    if (container) {
      const userMsgEl = container.querySelector(`.user-message[data-entry-index="${entryIndex}"]`);
      if (userMsgEl) {
        userMsgEl.classList.add('edit-mode');
      }
    }
    
    // Create cancel button if it doesn't exist
    if (!cancelBtn) {
      createCancelEditButton();
    } else {
      cancelBtn.style.display = 'inline-flex';
    }
  } else {
    // Remove edit mode styling from all messages
    if (container) {
      container.querySelectorAll('.user-message.edit-mode').forEach(el => {
        el.classList.remove('edit-mode');
      });
    }
    
    // Hide cancel button
    if (cancelBtn) {
      cancelBtn.style.display = 'none';
    }
  }
}

/**
 * Create the cancel edit button in the input area
 */
function createCancelEditButton() {
  const inputContainer = document.getElementById('input-container');
  if (!inputContainer) return;
  
  // Check if button already exists
  if (document.getElementById('cancel-edit-btn')) return;
  
  const btn = document.createElement('button');
  btn.id = 'cancel-edit-btn';
  btn.textContent = 'Cancel Edit';
  btn.title = 'Cancel editing and clear input';
  btn.addEventListener('click', cancelEdit);
  
  // Insert before the input-scroll element
  const inputScroll = document.getElementById('input-scroll');
  if (inputScroll) {
    inputContainer.insertBefore(btn, inputScroll);
  } else {
    inputContainer.appendChild(btn);
  }
}

/**
 * Cancel the current edit operation
 */
function cancelEdit() {
  // Clear the pending edit state
  delete window.__pendingEditEntryIndex;
  
  // Clear the input
  const userInput = document.getElementById('user-input');
  if (userInput) {
    if (userInput.value !== undefined) {
      userInput.value = '';
    } else {
      userInput.textContent = '';
    }
    
    // Trigger resize
    try {
      userInput.dispatchEvent(new Event('input', { bubbles: true }));
    } catch (_) {}
  }
  
  // Clear dropped files
  window.droppedFiles = [];
  
  // Remove edit mode styling
  applyEditModeUI(false);
  
  console.log('[messageContextMenu] Edit cancelled');
}

/**
 * Check if there's a pending edit and handle it before sending
 * Returns true if edit was handled (caller should not proceed with normal send)
 */
async function handlePendingEdit() {
  const entryIndex = window.__pendingEditEntryIndex;
  if (entryIndex === undefined || entryIndex === null) {
    return false;
  }
  
  // Show confirmation dialog
  const confirmed = await showConfirmDialog(
    'Resend Message',
    'This will restore files to this point and delete all messages after it. Continue?',
    'OK',
    'primary'
  );
  
  if (!confirmed) {
    // Clear the pending edit and remove edit mode UI
    delete window.__pendingEditEntryIndex;
    applyEditModeUI(false);
    return true; // Don't proceed with send
  }
  
  try {
    const sessionId = await getSessionId();
    
    await restoreToMessage(entryIndex, { restoreInput: false });
    console.log(`[messageContextMenu] Restored to index ${entryIndex} for edit resend`);
    
  } catch (err) {
    console.error('[messageContextMenu] Failed to delete entries for edit:', err);
  }
  
  // Clear the pending edit and remove edit mode UI
  delete window.__pendingEditEntryIndex;
  applyEditModeUI(false);
  
  return false; // Proceed with normal send
}
/**
 * Restore to a specific message - reverts file changes and deletes subsequent messages
 * @param {number} entryIndex - The entry index to restore to
 */
async function restoreToMessage(entryIndex, options = {}) {
  if (entryIndex < 0) {
    console.warn('[messageContextMenu] Invalid entry index for restore');
    return;
  }

  try {
    const restoreInput = options.restoreInput !== false;
    const sessionId = await getSessionId();
    
    // Show progress bar
    const progressBar = showProgressBar('Restoring to checkpoint...');
    
    // Step 1: Use git to restore files to the checkpoint
    const { ipcRenderer } = require('electron');
    
    // Update progress
    if (progressBar.updateProgress) {
      progressBar.updateProgress(0.3, 'Reverting files to checkpoint...');
    }
    
    // Restore using git
    const restored = await ipcRenderer.invoke('git-restore-checkpoint', sessionId, entryIndex);
    
    if (!restored || restored.success === false) {
      console.error('[messageContextMenu] Failed to restore checkpoint');
      if (progressBar.updateProgress) {
        progressBar.updateProgress(1.0, 'Restore failed');
      }
      if (progressBar.hide) {
        progressBar.hide();
      }
      return;
    }
    
    // Step 2: Get the user message text to restore to input (optional)
    let userText = '';
    let userFiles = [];
    
    if (restoreInput && Array.isArray(window.allChatEntries) && entryIndex < window.allChatEntries.length) {
      const entry = window.allChatEntries[entryIndex];
      if (entry && entry.user) {
        userText = entry.user.text || '';
        userFiles = Array.isArray(entry.user.files) ? entry.user.files : [];
      }
    }
    
    // File changes already reverted by git restore above - skip the old revert function
    
    // Step 3: Delete all entries from entryIndex onwards
    const totalEntries = Array.isArray(window.allChatEntries) ? window.allChatEntries.length : 0;
    const entriesToDelete = totalEntries - entryIndex;
    
    // Update progress
    if (progressBar.updateProgress) {
      progressBar.updateProgress(0.8, 'Deleting messages...');
    }
    
    // Delete from display history
    await deleteEntriesFromIndex(entryIndex, sessionId);
    
    // Delete from raw history
    await deleteMessagesFromEntryIndex(sessionId, entryIndex);
    
    // Update window.allChatEntries
    if (Array.isArray(window.allChatEntries)) {
      window.allChatEntries.splice(entryIndex);
    }
    
    // Remove DOM elements from entryIndex onwards
    const container = document.getElementById('chat-messages');
    if (container) {
      const allMessages = container.querySelectorAll('[data-entry-index]');
      allMessages.forEach(msg => {
        const idx = parseInt(msg.getAttribute('data-entry-index'), 10);
        if (idx >= entryIndex) {
          msg.remove();
        }
      });
    }
    
    // Step 4: Put the user message text in the input
    if (restoreInput) {
      const userInput = document.getElementById('user-input');
      if (userInput && userText) {
        if (userInput.value !== undefined) {
          userInput.value = userText;
        } else {
          userInput.innerText = userText;
        }
        
        // Restore files
        window.droppedFiles = userFiles;
        
        // Trigger resize
        try {
          userInput.dispatchEvent(new Event('input', { bubbles: true }));
        } catch (_) {}
        
        // Focus the input
        userInput.focus();
      }
    }
    
    // Hide progress bar
    if (progressBar.hide) {
      progressBar.hide();
    }

    if (restored && restored.canUndo) {
      window.__restoreUndoAvailable = true;
    }

    console.log(`[messageContextMenu] Restored to message at index ${entryIndex}`);
    
  } catch (err) {
    console.error('[messageContextMenu] Failed to restore to message:', err);
    
    // Hide progress bar on error
    const progressBar = document.getElementById('auaci-restore-progress');
    if (progressBar) progressBar.remove();
  }
}

/**
 * Undo the most recent restore (if no new message has been sent)
 */
async function undoRestore() {
  try {
    const sessionId = await getSessionId();
    const { ipcRenderer } = require('electron');
    const result = await ipcRenderer.invoke('git-undo-restore', sessionId);
    if (!result || result.success === false) {
      console.warn('[messageContextMenu] No restore to undo or undo failed');
      return;
    }
    window.__restoreUndoAvailable = false;
    await syncUiAfterUndo(sessionId);
    console.log('[messageContextMenu] Undo restore completed');
  } catch (err) {
    console.error('[messageContextMenu] Failed to undo restore:', err);
  }
}

async function syncUiAfterUndo(sessionId) {
  try {
    const { loadChatHistory } = require('./historyStorage');
    const history = await loadChatHistory(sessionId);
    const totalEntries = Array.isArray(history.chat) ? history.chat.length : 0;
    const container = document.getElementById('chat-messages');
    if (!container) return;
    const nodes = container.querySelectorAll('[data-entry-index]');
    let maxIndex = -1;
    nodes.forEach(node => {
      const raw = node.getAttribute('data-entry-index');
      const idx = raw != null ? parseInt(String(raw), 10) : -1;
      if (Number.isFinite(idx)) maxIndex = Math.max(maxIndex, idx);
    });
    if (totalEntries > 0 && maxIndex < totalEntries - 1) {
      await displayChatHistory();
    }
  } catch (err) {
    console.warn('[messageContextMenu] Failed to sync UI after undo:', err);
  }
}


/**
 * Show a progress bar for the restore operation
 */
function showProgressBar(message) {
  const progressId = 'auaci-restore-progress';
  const existing = document.getElementById(progressId);
  if (existing) existing.remove();
  
  const progressBar = document.createElement('div');
  progressBar.id = progressId;
  progressBar.style.cssText = `
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: #e5e7eb;
    z-index: 2147483647;
  `;
  
  const progressFill = document.createElement('div');
  progressFill.style.cssText = `
    height: 100%;
    background: #3b82f6;
    width: 0%;
    transition: width 0.3s ease;
  `;
  
  const messageEl = document.createElement('div');
  messageEl.style.cssText = `
    position: fixed;
    top: 10px;
    left: 50%;
    transform: translateX(-50%);
    background: #1f2937;
    color: white;
    padding: 8px 16px;
    border-radius: 6px;
    font-size: 14px;
    z-index: 2147483647;
  `;
  messageEl.textContent = message;
  
  progressBar.appendChild(progressFill);
  document.body.appendChild(progressBar);
  document.body.appendChild(messageEl);
  
  return {
    updateProgress: (progress, newMessage) => {
      progressFill.style.width = `${Math.min(100, Math.max(0, progress * 100))}%`;
      if (newMessage) {
        messageEl.textContent = newMessage;
      }
    },
    hide: () => {
      setTimeout(() => {
        progressBar.remove();
        messageEl.remove();
      }, 500);
    }
  };
}

/**
 * Revert file changes made by GPT responses after a specific message
 * This function needs to track which file edits were made by which GPT responses
 */
/**
 * Revert file changes made by GPT responses after a specific message
 */

/**
 * Get file operations that occurred after a specific message
 * This function is deprecated - git checkpoint system now handles restores
 */

/**
 * Revert a single file operation
 * This function is deprecated - git checkpoint system now handles restores
 */

function setupMessageContextMenu() {
  const container = document.getElementById('chat-messages');
  if (!container) return;

  // Inject styles early to ensure edit mode styles are available
  injectStyles();

  container.addEventListener('contextmenu', (e) => {
    const target = e.target;
    const messageEl = target && typeof target.closest === 'function' ? target.closest('.message') : null;
    if (!messageEl) return;
    const isGpt = messageEl.classList.contains('gpt-message');
    const isUser = messageEl.classList.contains('user-message');
    if (!isGpt && !isUser) return;

    e.preventDefault();
    e.stopPropagation();

    const rawIndex = messageEl.getAttribute('data-entry-index');
    const entryIndex = rawIndex != null ? parseInt(String(rawIndex), 10) : -1;

    const ctx = {
      type: isGpt ? 'gpt' : 'user',
      entryIndex: Number.isFinite(entryIndex) ? entryIndex : -1,
      messageEl
    };
    showMenu(e.clientX, e.clientY, ctx);
  });
}

module.exports = { setupMessageContextMenu, handlePendingEdit, showConfirmDialog, applyEditModeUI, cancelEdit, restoreToMessage };
