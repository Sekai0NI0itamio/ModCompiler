// src/main/functions/chat/incrementalHistoryStorage.js
// Real-time incremental history storage for Auaci chat
// Saves chat entries and updates them progressively as GPT responses stream
// and tool results are received, ensuring no conversation data is lost
// even if the user exits the app mid-conversation.

const fs = require('fs').promises;
const path = require('path');
const { getSessionFilePath, getSessionId } = require('./sessionManager');

const LOG_PATH = '/tmp/events.log';

async function appendLog(msg) {
  try {
    await fs.appendFile(LOG_PATH, `[${new Date().toISOString()}] ${msg}\n`);
  } catch (e) { /* ignore */ }
}

/**
 * ensureSessionFile()
 * Ensures the session file exists and returns the parsed session data
 * IMPORTANT: This function now includes better error handling and recovery
 */
async function ensureSessionFile(sessionIdOverride = null) {
  const filePath = await getSessionFilePath(sessionIdOverride);
  try {
    await fs.access(filePath);
    const data = await fs.readFile(filePath, 'utf8');
    
    // Validate JSON before parsing
    if (!data || data.trim().length === 0) {
      throw new Error('Empty session file');
    }
    
    let obj;
    try {
      obj = JSON.parse(data);
    } catch (parseErr) {
      // JSON is corrupted - try to recover
      await appendLog(`Session file ${filePath} has corrupted JSON, attempting recovery`);
      
      // Create a backup of the corrupted file
      const backupPath = filePath + '.corrupted.' + Date.now();
      try {
        await fs.writeFile(backupPath, data, 'utf8');
        await appendLog(`Backed up corrupted session to ${backupPath}`);
      } catch (_) {}
      
      // Create fresh session data
      const sessionId = sessionIdOverride || (await getSessionId());
      obj = {
        creationdate: new Date().toISOString(),
        lastused: new Date().toISOString(),
        session_id: sessionId,
        name: 'Recovered Chat',
        chat: []
      };
      
      await fs.writeFile(filePath, JSON.stringify(obj, null, 2), 'utf8');
      await appendLog(`Created recovered session file: ${filePath}`);
    }
    
    // Validate session structure
    if (!obj || typeof obj !== 'object') {
      throw new Error('Invalid session object');
    }
    
    // Ensure required fields exist
    if (!obj.session_id) obj.session_id = sessionIdOverride || (await getSessionId());
    if (!Array.isArray(obj.chat)) obj.chat = [];
    if (!obj.creationdate) obj.creationdate = new Date().toISOString();
    if (!obj.name) obj.name = 'Chat Session';
    
    obj.lastused = new Date().toISOString();
    return { filePath, sessionData: obj };
  } catch (err) {
    // File doesn't exist or is unreadable - create new
    const sessionId = sessionIdOverride || (await getSessionId());
    let defaultName = 'default chat';
    try {
      const tm = require('./tabManager');
      const sessions = (tm && typeof tm.getSessions === 'function') ? (await tm.getSessions()) : [];
      const names = Array.isArray(sessions) ? sessions.map(s => String(s.name || '').toLowerCase()) : [];
      let hasPlain = names.includes('default chat');
      let max = 0;
      for (const n of names) {
        const m = n.match(/^default\s*chat\s*(\d+)$/i);
        if (m) { const v = parseInt(m[1], 10); if (!Number.isNaN(v)) max = Math.max(max, v); }
      }
      if (!hasPlain && max === 0) defaultName = 'default chat';
      else if (hasPlain && max === 0) defaultName = 'default chat2';
      else defaultName = `default chat${max + 1}`;
    } catch (_) { defaultName = 'default chat'; }

    const init = {
      creationdate: new Date().toISOString(),
      lastused: new Date().toISOString(),
      session_id: sessionId,
      name: defaultName,
      chat: []
    };
    try {
      await fs.mkdir(path.dirname(filePath), { recursive: true });
      await fs.writeFile(filePath, JSON.stringify(init, null, 2), 'utf8');
      await appendLog(`Created new session file: ${filePath}`);
      return { filePath, sessionData: init };
    } catch (writeErr) {
      console.error('[incrementalHistoryStorage] Failed to create session file:', writeErr?.message || writeErr);
      await appendLog(`Failed to create session file: ${String(writeErr?.message || writeErr)}`);
      throw writeErr;
    }
  }
}

/**
 * saveSessionData(sessionData, filePath)
 * Internal helper to write session data to file with atomic write protection
 */
async function saveSessionData(sessionData, filePath) {
  try {
    sessionData.lastused = new Date().toISOString();
    const jsonContent = JSON.stringify(sessionData, null, 2);
    
    // Validate JSON before writing (catch serialization issues)
    try {
      JSON.parse(jsonContent);
    } catch (validateErr) {
      await appendLog(`Session data validation failed before write: ${validateErr.message}`);
      throw new Error('Invalid session data - cannot serialize');
    }
    
    // Atomic write: write to temp file first, then rename
    // OPTIMIZED: Removed verification read - trust that the write succeeds
    const tempPath = filePath + '.tmp.' + Date.now();
    try {
      await fs.writeFile(tempPath, jsonContent, 'utf8');
      
      // Rename temp to actual (atomic on most filesystems)
      await fs.rename(tempPath, filePath);
      
      await appendLog(`Updated session file: ${filePath} (entries=${Array.isArray(sessionData.chat) ? sessionData.chat.length : 0})`);
    } catch (writeErr) {
      // Clean up temp file if it exists
      try { await fs.unlink(tempPath); } catch (_) {}
      throw writeErr;
    }
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to save session data:', err?.message || err);
    await appendLog(`Failed to save session data: ${String(err?.message || err)}`);
    throw err;
  }
}

/**
 * initiateChatEntry(userInput, files)
 * Immediately saves a new chat entry with user input and empty GPT response
 * Returns the entry index for future updates
 */
async function initiateChatEntry(userInput, files = [], forSessionId = null) {
  try {
    const { filePath, sessionData } = await ensureSessionFile(forSessionId);
    
    const chatEntry = {
      timestamp: new Date().toISOString(),
      user: { 
        text: userInput || '', 
        files: Array.isArray(files) ? files : []
      },
      gpt: '',
      status: 'streaming' // indicates this entry is being actively updated
    };

    sessionData.chat = Array.isArray(sessionData.chat) ? sessionData.chat : [];
    sessionData.chat.push(chatEntry);
    
    await saveSessionData(sessionData, filePath);
    
    const entryIndex = sessionData.chat.length - 1;
    await appendLog(`Initiated new chat entry at index ${entryIndex}`);
    return entryIndex;
    
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to initiate chat entry:', err?.message || err);
    await appendLog(`Failed to initiate chat entry: ${String(err?.message || err)}`);
    throw err;
  }
}

/**
 * updateGptResponse(entryIndex, gptContent, isComplete)
 * Updates the GPT response content for the given entry index
 * If isComplete is true, removes the 'streaming' status
 */
async function updateGptResponse(entryIndex, gptContent, isComplete = false, forSessionId = null) {
  try {
    const { filePath, sessionData } = await ensureSessionFile(forSessionId);
    
    if (!Array.isArray(sessionData.chat) || entryIndex >= sessionData.chat.length || entryIndex < 0) {
      throw new Error(`Invalid entry index: ${entryIndex}`);
    }

    const entry = sessionData.chat[entryIndex];
    if (!entry) {
      throw new Error(`No entry found at index: ${entryIndex}`);
    }

    entry.gpt = gptContent || '';
    
    if (isComplete) {
      entry.status = 'complete';
    }
    
    await saveSessionData(sessionData, filePath);
    await appendLog(`Updated GPT response for entry ${entryIndex}, complete: ${isComplete}`);
    
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to update GPT response:', err?.message || err);
    await appendLog(`Failed to update GPT response for entry ${entryIndex}: ${String(err?.message || err)}`);
    // Don't throw here to avoid interrupting the streaming process
  }
}

/**
 * addToolRun(entryIndex, toolRun)
 * Adds a completed tool run to the specified chat entry
 */
// DEPRECATED in new history format: tool runs are embedded inside GPT text
// Keep as a no-op for backward compatibility if called by older code
async function addToolRun(entryIndex, toolRun) {
  try {
    const { filePath } = await ensureSessionFile();
    await appendLog(`(noop)addToolRun called for entry ${entryIndex} - new format embeds results in GPT text`);
  } catch (_) {
    // ignore
  }
}

/**
 * finalizeChatEntry(entryIndex, finalGptContent, toolRuns)
 * Marks a chat entry as complete with final content
 * This is called when all streaming and tool execution is finished
 */
async function finalizeChatEntry(entryIndex, finalGptContent, forSessionId = null) {
  try {
    const { filePath, sessionData } = await ensureSessionFile(forSessionId);
    
    if (!Array.isArray(sessionData.chat) || entryIndex >= sessionData.chat.length || entryIndex < 0) {
      throw new Error(`Invalid entry index: ${entryIndex}`);
    }

    const entry = sessionData.chat[entryIndex];
    if (!entry) {
      throw new Error(`No entry found at index: ${entryIndex}`);
    }

    entry.gpt = finalGptContent || '';
    entry.status = 'complete';
    delete entry.streaming; // remove any legacy streaming flag
    
    await saveSessionData(sessionData, filePath);
    await appendLog(`Finalized chat entry ${entryIndex} (tool runs embedded in GPT text)`);
    
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to finalize chat entry:', err?.message || err);
    await appendLog(`Failed to finalize chat entry ${entryIndex}: ${String(err?.message || err)}`);
    throw err;
  }
}

/**
 * getCurrentChatHistory()
 * Returns the current chat history (for compatibility with existing code)
 */
async function getCurrentChatHistory() {
  try {
    const { sessionData } = await ensureSessionFile();
    return sessionData;
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to get chat history:', err?.message || err);
    return { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), chat: [] };
  }
}

/**
 * cleanupIncompleteEntries()
 * Removes or marks as failed any entries that were left in 'streaming' state
 * This should be called on app startup to clean up from unexpected exits
 */
async function cleanupIncompleteEntries() {
  try {
    const { filePath, sessionData } = await ensureSessionFile();
    
    if (!Array.isArray(sessionData.chat)) {
      return;
    }

    let hasChanges = false;
    for (const entry of sessionData.chat) {
      if (entry.status === 'streaming') {
        entry.status = 'interrupted';
        entry.gpt = entry.gpt || '[Response was interrupted]';
        hasChanges = true;
      }
    }
    
    if (hasChanges) {
      await saveSessionData(sessionData, filePath);
      await appendLog('Cleaned up incomplete chat entries from previous session');
    }
    
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to cleanup incomplete entries:', err?.message || err);
    await appendLog(`Failed to cleanup incomplete entries: ${String(err?.message || err)}`);
  }
}

/**
 * Draft helpers: per-session input text persistence
 */
function sanitizeFilesForDraft(files) {
  if (!Array.isArray(files)) return [];
  return files.map(f => {
    const out = { name: f && f.name ? String(f.name) : '', size: typeof f?.size === 'number' ? f.size : 0 };
    if (f && f.path) out.path = f.path;
    return out;
  });
}

async function saveCurrentInputDraft(text, files) {
  try {
    const { filePath, sessionData } = await ensureSessionFile();
    const draftObj = {
      text: (typeof text === 'string') ? text : (text && typeof text.text === 'string' ? text.text : ''),
      files: sanitizeFilesForDraft(Array.isArray(files) ? files : (text && Array.isArray(text.files) ? text.files : []))
    };
    sessionData.input_draft = draftObj;
    await saveSessionData(sessionData, filePath);
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to save input draft:', err?.message || err);
  }
}

async function loadCurrentInputDraft() {
  try {
    const { sessionData } = await ensureSessionFile();
    const raw = sessionData ? sessionData.input_draft : '';
    if (typeof raw === 'string') return { text: raw, files: [] };
    if (raw && typeof raw === 'object') {
      const text = typeof raw.text === 'string' ? raw.text : '';
      const files = Array.isArray(raw.files) ? sanitizeFilesForDraft(raw.files) : [];
      return { text, files };
    }
    return { text: '', files: [] };
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to load input draft:', err?.message || err);
    return { text: '', files: [] };
  }
}

async function clearCurrentInputDraft() {
  try {
    const { filePath, sessionData } = await ensureSessionFile();
    if (sessionData && typeof sessionData === 'object') {
      sessionData.input_draft = { text: '', files: [] };
      await saveSessionData(sessionData, filePath);
    }
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to clear input draft:', err?.message || err);
  }
}

/**
 * deleteChatEntry(entryIndex)
 * Deletes a chat entry at the specified index (both user and GPT parts)
 * @param {number} entryIndex - Index of the entry to delete
 * @param {string|null} forSessionId - Optional session ID override
 */
async function deleteChatEntry(entryIndex, forSessionId = null) {
  try {
    const { filePath, sessionData } = await ensureSessionFile(forSessionId);
    
    if (!Array.isArray(sessionData.chat) || entryIndex < 0 || entryIndex >= sessionData.chat.length) {
      throw new Error(`Invalid entry index: ${entryIndex}`);
    }
    
    // Remove the entry at the specified index
    sessionData.chat.splice(entryIndex, 1);
    
    await saveSessionData(sessionData, filePath);
    await appendLog(`Deleted chat entry at index ${entryIndex}`);
    
    return true;
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to delete chat entry:', err?.message || err);
    await appendLog(`Failed to delete chat entry ${entryIndex}: ${String(err?.message || err)}`);
    return false;
  }
}

/**
 * deleteGptPart(entryIndex)
 * Deletes only the GPT response part of a chat entry, keeping the user message
 * @param {number} entryIndex - Index of the entry
 * @param {string|null} forSessionId - Optional session ID override
 */
async function deleteGptPart(entryIndex, forSessionId = null) {
  try {
    const { filePath, sessionData } = await ensureSessionFile(forSessionId);
    
    if (!Array.isArray(sessionData.chat) || entryIndex < 0 || entryIndex >= sessionData.chat.length) {
      throw new Error(`Invalid entry index: ${entryIndex}`);
    }
    
    // Clear the GPT part but keep the user message
    sessionData.chat[entryIndex].gpt = '';
    sessionData.chat[entryIndex].status = 'gpt_deleted';
    
    await saveSessionData(sessionData, filePath);
    await appendLog(`Deleted GPT part at index ${entryIndex}`);
    
    return true;
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to delete GPT part:', err?.message || err);
    await appendLog(`Failed to delete GPT part ${entryIndex}: ${String(err?.message || err)}`);
    return false;
  }
}

/**
 * deleteEntriesFromIndex(startIndex)
 * Deletes all chat entries from startIndex onwards
 * @param {number} startIndex - Starting index to delete from
 * @param {string|null} forSessionId - Optional session ID override
 */
async function deleteEntriesFromIndex(startIndex, forSessionId = null) {
  try {
    const { filePath, sessionData } = await ensureSessionFile(forSessionId);
    
    if (!Array.isArray(sessionData.chat) || startIndex < 0) {
      throw new Error(`Invalid start index: ${startIndex}`);
    }
    
    // Remove all entries from startIndex onwards
    const removedCount = sessionData.chat.length - startIndex;
    sessionData.chat.splice(startIndex);
    
    await saveSessionData(sessionData, filePath);
    await appendLog(`Deleted ${removedCount} entries from index ${startIndex}`);
    
    return true;
  } catch (err) {
    console.error('[incrementalHistoryStorage] Failed to delete entries from index:', err?.message || err);
    await appendLog(`Failed to delete entries from index ${startIndex}: ${String(err?.message || err)}`);
    return false;
  }
}

module.exports = {
  initiateChatEntry,
  updateGptResponse,
  addToolRun, // kept as no-op for compatibility
  finalizeChatEntry,
  getCurrentChatHistory,
  cleanupIncompleteEntries,
  saveCurrentInputDraft,
  loadCurrentInputDraft,
  clearCurrentInputDraft,
  deleteChatEntry,
  deleteGptPart,
  deleteEntriesFromIndex,
  ensureSessionFile,
  saveSessionData
};
