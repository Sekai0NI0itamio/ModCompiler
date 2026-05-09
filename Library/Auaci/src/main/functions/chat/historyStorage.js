// src/main/functions/chat/historyStorage.js
// Session-based history storage for Auaci chat
// - session files are stored at: .auaci/chathistory/sessions/<session_id>.json
// - a session_id is kept at: .auaci/chathistory/session_id.txt
// - provides loadChatHistory() and saveChatHistory(chatEntry)

const fs = require('fs').promises;
const path = require('path');
const { getSessionFilePath, getSessionId } = require('./sessionManager');

const LOG_PATH = '/tmp/events.log';

async function appendLog(msg) {
  try {
    await fs.appendFile(LOG_PATH, `[${new Date().toISOString()}] ${msg}\n`);
  } catch (e) { /* ignore */ }
}

async function ensureChatHistoryFile(forSessionId = null) {
  const filePath = await getSessionFilePath(forSessionId);
    try {
      // Check if the file already exists
      await fs.access(filePath);
      return filePath;
    } catch {
      // File doesn't exist. If a sessionId was provided, prefer an existing session file elsewhere
      if (forSessionId) {
        try {
          const sessionManager = require('./sessionManager');
          for (const baseDir of sessionManager.CANDIDATE_BASE_DIRS) {
            const altPath = path.join(baseDir, 'sessions', `${forSessionId}.json`);
            try {
              await fs.access(altPath);
              // Found existing session file; prefer using that path instead of copying
              await appendLog(`Found existing session data at ${altPath}; will use that path`);
              return altPath;
            } catch {
              // Continue to next candidate directory
            }
          }
        } catch (e) {
          await appendLog(`Error checking for existing session files: ${e && e.message ? e.message : String(e)}`);
        }
      }
    // Create initial session file with a friendly default name
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
      session_id: forSessionId || (await getSessionId()),
      name: defaultName,
      chat: []
    };

    try {
      await fs.mkdir(path.dirname(filePath), { recursive: true });
      await fs.writeFile(filePath, JSON.stringify(init, null, 2), 'utf8');
      await appendLog(`Created new session file: ${filePath}`);
    } catch (e) {
      await appendLog(`Failed to create session file ${filePath}: ${e.message}`);
      throw e;
    }
    return filePath;
  }
}

async function loadChatHistory(forSessionId = null) {
  const filePath = await ensureChatHistoryFile(forSessionId);
  try {
    const data = await fs.readFile(filePath, 'utf8');
    
    // Validate data before parsing
    if (!data || data.trim().length === 0) {
      await appendLog(`Empty session file at ${filePath}, returning empty history`);
      return { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), chat: [] };
    }
    
    let obj;
    try {
      obj = JSON.parse(data);
    } catch (parseErr) {
      // JSON is corrupted - try to recover
      await appendLog(`Session file ${filePath} has corrupted JSON: ${parseErr.message}`);
      
      // Create a backup of the corrupted file
      const backupPath = filePath + '.corrupted.' + Date.now();
      try {
        await fs.writeFile(backupPath, data, 'utf8');
        await appendLog(`Backed up corrupted session to ${backupPath}`);
      } catch (_) {}
      
      // Return empty history
      return { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), chat: [], _recovered: true };
    }
    
    // Validate and repair structure
    if (!obj || typeof obj !== 'object') {
      obj = { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), chat: [] };
    }
    if (!Array.isArray(obj.chat)) obj.chat = [];
    if (!obj.creationdate) obj.creationdate = new Date().toISOString();
    
    // update lastused
    obj.lastused = new Date().toISOString();
    try { await fs.writeFile(filePath, JSON.stringify(obj, null, 2), 'utf8'); } catch (_) {}
    await appendLog(`Loaded history from ${filePath} (entries=${Array.isArray(obj.chat)?obj.chat.length:0})`);
    return obj;
  } catch (err) {
    console.error(`[historyStorage] Error reading ${filePath}: ${err && err.message ? err.message : err}`);
    await appendLog(`Error reading ${filePath}: ${String(err && err.message ? err.message : err)}`);
    return { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), chat: [] };
  }
}

async function saveChatHistory(chatEntry, forSessionId = null) {
  try {
    const filePath = await ensureChatHistoryFile(forSessionId);
    const data = await fs.readFile(filePath, 'utf8');
    const obj = JSON.parse(data || '{}');
    obj.chat = Array.isArray(obj.chat) ? obj.chat : [];
    obj.chat.push(chatEntry);
    obj.lastused = new Date().toISOString();
    await fs.writeFile(filePath, JSON.stringify(obj, null, 2), 'utf8');
    console.log(`[DEBUG] Saved chat entry to: ${filePath}`);
    await appendLog(`Saved chat entry to ${filePath} (new_count=${obj.chat.length})`);
  } catch (err) {
    console.error(`[DEBUG] Error saving chat session: ${err && err.message ? err.message : err}`);
    await appendLog(`Error saving chat session: ${String(err && err.message ? err.message : err)}`);
  }
}

/**
 * updateLastChatEntry(updatedEntry)
 * Updates the last chat entry in the session file with new content
 * Used for incremental saving during streaming responses
 */
async function updateLastChatEntry(updatedEntry, forSessionId = null) {
  try {
    const filePath = await ensureChatHistoryFile(forSessionId);
    const data = await fs.readFile(filePath, 'utf8');
    const obj = JSON.parse(data || '{}');
    obj.chat = Array.isArray(obj.chat) ? obj.chat : [];
    
    if (obj.chat.length > 0) {
      obj.chat[obj.chat.length - 1] = updatedEntry;
      obj.lastused = new Date().toISOString();
      await fs.writeFile(filePath, JSON.stringify(obj, null, 2), 'utf8');
      console.log(`[DEBUG] Updated last chat entry in: ${filePath}`);
      await appendLog(`Updated last chat entry in ${filePath}`);
    }
  } catch (err) {
    console.error(`[DEBUG] Error updating last chat entry: ${err && err.message ? err.message : err}`);
    await appendLog(`Error updating last chat entry: ${String(err && err.message ? err.message : err)}`);
  }
}

/**
 * updateChatEntryAtIndex(index, updatedEntry)
 * Updates a specific chat entry by index
 */
async function updateChatEntryAtIndex(index, updatedEntry, forSessionId = null) {
  try {
    const filePath = await ensureChatHistoryFile(forSessionId);
    const data = await fs.readFile(filePath, 'utf8');
    const obj = JSON.parse(data || '{}');
    obj.chat = Array.isArray(obj.chat) ? obj.chat : [];
    
    if (index >= 0 && index < obj.chat.length) {
      obj.chat[index] = updatedEntry;
      obj.lastused = new Date().toISOString();
      await fs.writeFile(filePath, JSON.stringify(obj, null, 2), 'utf8');
      console.log(`[DEBUG] Updated chat entry at index ${index} in: ${filePath}`);
      await appendLog(`Updated chat entry at index ${index} in ${filePath}`);
    }
  } catch (err) {
    console.error(`[DEBUG] Error updating chat entry at index ${index}: ${err && err.message ? err.message : err}`);
    await appendLog(`Error updating chat entry at index ${index}: ${String(err && err.message ? err.message : err)}`);
  }
}

/**
 * ensureSessionDataIntegrity(forSessionId = null)
 * Ensures that session data is not overwritten and maintains integrity
 * This function should be called before any major operations
 */
async function ensureSessionDataIntegrity(forSessionId = null) {
  try {
    const filePath = await getSessionFilePath(forSessionId);
    
    // Check if file exists and has data
    try {
      await fs.access(filePath);
      const data = await fs.readFile(filePath, 'utf8');
      const obj = JSON.parse(data || '{}');
      
      // Verify the session file has the expected structure
      if (!obj.session_id || !Array.isArray(obj.chat)) {
        await appendLog(`Session file ${filePath} has invalid structure, attempting repair`);
        
        // Try to repair the file structure
        if (!obj.session_id) obj.session_id = forSessionId || (await getSessionId());
        if (!Array.isArray(obj.chat)) obj.chat = [];
        if (!obj.creationdate) obj.creationdate = new Date().toISOString();
        if (!obj.lastused) obj.lastused = new Date().toISOString();
        if (!obj.name) obj.name = 'Repaired Chat';
        
        await fs.writeFile(filePath, JSON.stringify(obj, null, 2), 'utf8');
        await appendLog(`Repaired session file ${filePath}`);
      }
      
      return true;
    } catch (e) {
      // File doesn't exist or can't be read
      await appendLog(`Session integrity check failed for ${filePath}: ${e.message}`);
      return false;
    }
  } catch (e) {
    await appendLog(`Error in session integrity check: ${e.message}`);
    return false;
  }
}

module.exports = { 
  loadChatHistory, 
  saveChatHistory, 
  updateLastChatEntry, 
  updateChatEntryAtIndex,
  ensureSessionDataIntegrity
};