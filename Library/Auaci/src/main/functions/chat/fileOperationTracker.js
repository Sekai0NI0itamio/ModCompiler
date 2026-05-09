// src/main/functions/chat/fileOperationTracker.js
// Tracks file operations made by GPT responses to enable restore functionality

const fs = require('fs').promises;
const path = require('path');
const { getSessionId } = require('./sessionManager');

const OPERATION_LOG_DIR = path.join(process.cwd(), '.auaci', 'chathistory', 'operations');

/**
 * Ensure the operations directory exists
 */
async function ensureOperationsDir() {
  try {
    await fs.mkdir(OPERATION_LOG_DIR, { recursive: true });
  } catch (err) {
    if (err.code !== 'EEXIST') {
      console.error('[fileOperationTracker] Failed to create directory:', err);
    }
  }
}

/**
 * Get the operation log file path for a session
 */
function getOperationLogPath(sessionId) {
  return path.join(OPERATION_LOG_DIR, `${sessionId}.json`);
}

/**
 * Load operation log for a session
 */
async function loadOperationLog(sessionId = null) {
  await ensureOperationsDir();
  
  const sid = sessionId || await getSessionId();
  const filePath = getOperationLogPath(sid);
  
  try {
    const data = await fs.readFile(filePath, 'utf8');
    const parsed = JSON.parse(data);
    return {
      session_id: parsed.session_id || sid,
      operations: Array.isArray(parsed.operations) ? parsed.operations : []
    };
  } catch (err) {
    if (err.code === 'ENOENT') {
      // File doesn't exist, return empty log
      return {
        session_id: sid,
        operations: []
      };
    }
    console.error('[fileOperationTracker] Failed to load operation log:', err);
    return {
      session_id: sid,
      operations: []
    };
  }
}

/**
 * Save operation log for a session
 */
async function saveOperationLog(sessionId, operations) {
  await ensureOperationsDir();
  
  const filePath = getOperationLogPath(sessionId);
  const data = {
    session_id: sessionId,
    operations: operations,
    updated_at: new Date().toISOString()
  };
  
  try {
    await fs.writeFile(filePath, JSON.stringify(data, null, 2), 'utf8');
  } catch (err) {
    console.error('[fileOperationTracker] Failed to save operation log:', err);
  }
}

/**
 * Record a file operation
 * @param {string} sessionId - Session ID
 * @param {number} entryIndex - Chat entry index
 * @param {string} operationType - Type of operation ('apply_patch', 'write_to_file', 'edit_function', etc.)
 * @param {object} operationData - Operation details
 * @param {string} operationData.filePath - File path
 * @param {string} operationData.originalContent - Original file content before operation
 * @param {object} operationData.operationDetails - Specific operation details
 */
async function recordFileOperation(sessionId, entryIndex, operationType, operationData) {
  try {
    const log = await loadOperationLog(sessionId);
    
    const operation = {
      entryIndex,
      operationType,
      timestamp: new Date().toISOString(),
      filePath: operationData.filePath,
      originalContent: operationData.originalContent,
      operationDetails: operationData.operationDetails || {},
      fileExisted: operationData.fileExisted, // Track if file existed before operation
      operationSubtype: operationData.operationSubtype // Track creation/deletion subtype
    };
    
    log.operations.push(operation);
    
    await saveOperationLog(sessionId, log.operations);
    
    console.log(`[fileOperationTracker] Recorded ${operationType} operation for entry ${entryIndex}:`, {
      filePath: operationData.filePath,
      fileExisted: operationData.fileExisted,
      operationSubtype: operationData.operationSubtype,
      contentLength: operationData.originalContent ? operationData.originalContent.length : 0
    });
    
  } catch (err) {
    console.error('[fileOperationTracker] Failed to record operation:', err);
  }
}

/**
 * Get file operations after a specific entry index (inclusive)
 * When restoring to a message, we need to revert the GPT response AT that index and everything after
 */
async function getFileOperationsAfterEntry(sessionId, entryIndex) {
  try {
    const log = await loadOperationLog(sessionId);
    
    // Filter operations that occurred after the specified entry (inclusive)
    // This includes operations made by the GPT response at entryIndex and all subsequent entries
    const operations = log.operations.filter(op => op.entryIndex >= entryIndex);
    
    // Group by file path and sort by timestamp (newest first)
    const fileOperations = {};
    operations.forEach(op => {
      if (!fileOperations[op.filePath]) {
        fileOperations[op.filePath] = [];
      }
      fileOperations[op.filePath].push(op);
    });
    
    // Sort each file's operations by timestamp (newest first)
    Object.keys(fileOperations).forEach(filePath => {
      fileOperations[filePath].sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
      console.log(`[fileOperationTracker] File ${filePath}: ${fileOperations[filePath].length} operation(s) to process`);
    });
    
    return fileOperations;
    
  } catch (err) {
    console.error('[fileOperationTracker] Failed to get operations:', err);
    return {};
  }
}

/**
 * Get files that were created by GPT after a specific entry index
 * Uses the oldest operation per file to determine whether the file existed before GPT touched it
 */
async function getCreatedFilesAfterEntry(sessionId, entryIndex) {
  try {
    const fileOperations = await getFileOperationsAfterEntry(sessionId, entryIndex);
    const createdFiles = [];

    Object.keys(fileOperations).forEach(filePath => {
      const ops = fileOperations[filePath] || [];
      if (!ops.length) return;
      const oldestOperation = ops[ops.length - 1];
      const wasFileCreated = oldestOperation.fileExisted === false;
      if (wasFileCreated) {
        createdFiles.push(filePath);
      }
    });

    return createdFiles;
  } catch (err) {
    console.error('[fileOperationTracker] Failed to get created files:', err);
    return [];
  }
}

/**
 * Revert file operations for a specific file
 * Handles multiple operations on the same file by checking all operations to determine final state
 */
async function revertFileOperations(sessionId, filePath, operations) {
  try {
    if (!operations || operations.length === 0) {
      console.log(`[fileOperationTracker] No operations to revert for ${filePath}`);
      return;
    }
    
    // Operations are sorted newest-first, so get the OLDEST operation (last in array)
    // to check the original state before any modifications
    const oldestOperation = operations[operations.length - 1];
    const newestOperation = operations[0];
    
    // Log detailed operation info for debugging
    console.log(`[fileOperationTracker] Reverting ${filePath}: ${operations.length} operation(s)`);
    console.log(`[fileOperationTracker] Oldest operation:`, {
      fileExisted: oldestOperation.fileExisted,
      operationSubtype: oldestOperation.operationSubtype,
      operationType: oldestOperation.operationType,
      hasOriginalContent: !!oldestOperation.originalContent
    });
    
    // CRITICAL: Only trust fileExisted from the OLDEST operation
    // All subsequent operations will have fileExisted=true because file exists by then
    // The OLDEST operation tells us if the file existed BEFORE GPT started modifying it
    const wasFileCreated = oldestOperation.fileExisted === false;
    
    console.log(`[fileOperationTracker] Decision: wasFileCreated=${wasFileCreated} (based on oldest operation only)`);
    
    // Handle file deletion (if file was created by GPT and needs to be deleted)
    if (wasFileCreated || 
        oldestOperation.operationSubtype === 'todo_creation') {
      
      // File was created by GPT, so delete it
      try {
        await fs.unlink(filePath);
        console.log(`[fileOperationTracker] Deleted file created by GPT: ${filePath}`);
      } catch (err) {
        if (err.code !== 'ENOENT') {
          console.warn(`[fileOperationTracker] Failed to delete file ${filePath}:`, err);
        }
      }
      return;
    }
    
    // Handle directory deletion via trash restore
    if (operations.some(op => op.operationSubtype === 'directory_deletion')) {
      const trashOp = operations.find(op => op.operationSubtype === 'directory_deletion' && op.operationDetails && op.operationDetails.trashPath);
      if (trashOp && trashOp.operationDetails && trashOp.operationDetails.trashPath) {
        try {
          await fs.rm(filePath, { recursive: true, force: true }).catch(() => {});
          await fs.mkdir(path.dirname(filePath), { recursive: true }).catch(() => {});
          await fs.rename(trashOp.operationDetails.trashPath, filePath);
          console.log(`[fileOperationTracker] Restored deleted directory: ${filePath}`);
          return;
        } catch (err) {
          console.warn(`[fileOperationTracker] Failed to restore directory from trash ${filePath}:`, err);
        }
      }
      return;
    }

    // Handle todo additions - revert to original todo state
    if (operations.some(op => op.operationSubtype === 'todo_addition')) {
      // Write the original content back to the todo file
      if (oldestOperation.originalContent) {
        await fs.mkdir(path.dirname(filePath), { recursive: true }).catch(() => {});
        await fs.writeFile(filePath, oldestOperation.originalContent, 'utf8');
        console.log(`[fileOperationTracker] Reverted todo additions for ${filePath}`);
      }
      return;
    }
    
    // Handle file deletion - restore deleted files
    if (operations.some(op => op.operationSubtype === 'file_deletion')) {
      const trashOp = operations.find(op => op.operationSubtype === 'file_deletion' && op.operationDetails && op.operationDetails.trashPath);
      if (trashOp && trashOp.operationDetails && trashOp.operationDetails.trashPath) {
        try {
          await fs.rm(filePath, { recursive: true, force: true }).catch(() => {});
          await fs.mkdir(path.dirname(filePath), { recursive: true }).catch(() => {});
          await fs.rename(trashOp.operationDetails.trashPath, filePath);
          console.log(`[fileOperationTracker] Restored deleted file from trash: ${filePath}`);
          return;
        } catch (err) {
          console.warn(`[fileOperationTracker] Failed to restore file from trash ${filePath}:`, err);
        }
      }
      // Write the original content back to recreate the file
      if (oldestOperation.originalContent) {
        await fs.mkdir(path.dirname(filePath), { recursive: true }).catch(() => {});
        await fs.writeFile(filePath, oldestOperation.originalContent, 'utf8');
        console.log(`[fileOperationTracker] Restored deleted file: ${filePath}`);
      }
      return;
    }
    
    // Handle file restoration (file existed before GPT modifications)
    if (!oldestOperation.originalContent) {
      console.warn(`[fileOperationTracker] No original content recorded for ${filePath}`);
      return;
    }
    
    // Write the original content back to the file
    await fs.mkdir(path.dirname(filePath), { recursive: true }).catch(() => {});
    await fs.writeFile(filePath, oldestOperation.originalContent, 'utf8');
    
    console.log(`[fileOperationTracker] Reverted ${operations.length} operations for ${filePath}`);
    
  } catch (err) {
    console.error(`[fileOperationTracker] Failed to revert operations for ${filePath}:`, err);
  }
}

/**
 * Clear operation log for a session
 */
async function clearOperationLog(sessionId) {
  await ensureOperationsDir();
  
  const filePath = getOperationLogPath(sessionId);
  
  try {
    await fs.unlink(filePath);
    console.log(`[fileOperationTracker] Cleared operation log for session ${sessionId}`);
  } catch (err) {
    if (err.code !== 'ENOENT') {
      console.error('[fileOperationTracker] Failed to clear operation log:', err);
    }
  }
}

/**
 * Truncate operation log for a session to entries strictly before entryIndex.
 * Useful after restoring to a prior message so stale operations don't apply.
 */
async function truncateOperationLog(sessionId, entryIndex) {
  await ensureOperationsDir();
  try {
    const log = await loadOperationLog(sessionId);
    const ops = Array.isArray(log.operations) ? log.operations : [];
    const filtered = ops.filter(op => Number.isFinite(op.entryIndex) && op.entryIndex < entryIndex);
    await saveOperationLog(sessionId, filtered);
    return true;
  } catch (err) {
    console.error('[fileOperationTracker] Failed to truncate operation log:', err);
    return false;
  }
}

module.exports = {
  recordFileOperation,
  getFileOperationsAfterEntry,
  revertFileOperations,
  clearOperationLog,
  loadOperationLog,
  saveOperationLog,
  getCreatedFilesAfterEntry,
  truncateOperationLog
};
