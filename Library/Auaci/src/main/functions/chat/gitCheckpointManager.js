// src/main/functions/chat/gitCheckpointManager.js
// Manages git-based checkpoints for conversation state restoration

const { execFile } = require('child_process');
const path = require('path');
const fs = require('fs').promises;
const crypto = require('crypto');
const { getSessionFilePath } = require('./sessionManager');
const {
  getCreatedFilesAfterEntry,
  getFileOperationsAfterEntry,
  revertFileOperations,
  truncateOperationLog
} = require('./fileOperationTracker');

const PROJECT_ROOT = process.cwd();
const CHECKPOINT_BASE_DIR = path.join(PROJECT_ROOT, '.auaci', 'checkpoints');
const CHECKPOINT_INDEX_DIR = path.join(PROJECT_ROOT, '.auaci', 'chathistory', 'checkpoints');
const RESTORE_BACKUP_DIR = path.join(PROJECT_ROOT, '.auaci', 'chathistory', 'restore_backups');
const DEFAULT_EXCLUDES = [
  '.auaci',
  'node_modules',
  'dist',
  'build',
  '.git',
  '.Trash',
  '.DS_Store'
];

const restoreUndoState = new Map();
const sessionLocks = new Map();

function getCheckpointMode() {
  const env = String(process.env.AUACI_CHECKPOINT_MODE || '').toLowerCase();
  if (env === 'git' || env === 'lite') return env;
  try {
    const cfg = require('../configHandler').getConfig();
    const cfgMode = cfg && typeof cfg.checkpointMode === 'string'
      ? cfg.checkpointMode.toLowerCase()
      : '';
    if (cfgMode === 'git' || cfgMode === 'lite') return cfgMode;
  } catch (_) {}
  return 'lite';
}

function getCheckpointRepoDir(sessionId) {
  return path.join(CHECKPOINT_BASE_DIR, String(sessionId || 'unknown'));
}

function getCheckpointIndexPath(sessionId) {
  return path.join(CHECKPOINT_INDEX_DIR, `${String(sessionId || 'unknown')}.json`);
}

function getRestoreBackupDir(sessionId) {
  return path.join(RESTORE_BACKUP_DIR, `${String(sessionId || 'unknown')}`);
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function withSessionLock(sessionId, fn) {
  const key = String(sessionId || 'unknown');
  while (sessionLocks.get(key)) {
    await new Promise(r => setTimeout(r, 25));
  }
  sessionLocks.set(key, true);
  try {
    return await fn();
  } finally {
    sessionLocks.delete(key);
  }
}

function gitArgs(sessionId, args) {
  const repoDir = getCheckpointRepoDir(sessionId);
  const gitDir = path.join(repoDir, '.git');
  return ['--git-dir', gitDir, '--work-tree', PROJECT_ROOT, ...args];
}

async function runGit(sessionId, args) {
  return new Promise((resolve, reject) => {
    execFile('git', gitArgs(sessionId, args), { cwd: PROJECT_ROOT }, (err, stdout, stderr) => {
      if (err) {
        err.stdout = stdout;
        err.stderr = stderr;
        return reject(err);
      }
      resolve({ stdout: String(stdout || ''), stderr: String(stderr || '') });
    });
  });
}

async function writeExcludeFile(sessionId) {
  const repoDir = getCheckpointRepoDir(sessionId);
  const excludePath = path.join(repoDir, '.git', 'info', 'exclude');
  try {
    const existing = await fs.readFile(excludePath, 'utf8').catch(() => '');
    const lines = new Set(existing.split(/\r?\n/).filter(Boolean));
    DEFAULT_EXCLUDES.forEach(e => lines.add(e));
    await ensureDir(path.dirname(excludePath));
    await fs.writeFile(excludePath, Array.from(lines).join('\n') + '\n', 'utf8');
  } catch (err) {
    console.warn('[gitCheckpointManager] Failed to write exclude file:', err?.message || err);
  }
}

/**
 * Initialize per-session checkpoint repository (bare git repo)
 */
async function initializeCheckpointRepo(sessionId) {
  try {
    const repoDir = getCheckpointRepoDir(sessionId);
    const gitDir = path.join(repoDir, '.git');
    try {
      await fs.access(gitDir);
      return true;
    } catch (_) {}

    await ensureDir(repoDir);
    await new Promise((resolve, reject) => {
      execFile('git', ['init', '--quiet', '--bare', gitDir], { cwd: PROJECT_ROOT }, (err) => {
        if (err) return reject(err);
        resolve();
      });
    });

    await runGit(sessionId, ['config', 'user.email', 'aicoder@localhost']).catch(() => {});
    await runGit(sessionId, ['config', 'user.name', 'AI Coder']).catch(() => {});
    await writeExcludeFile(sessionId);

    console.log('[gitCheckpointManager] Initialized checkpoint repo:', gitDir);
    return true;
  } catch (err) {
    console.error('[gitCheckpointManager] Failed to initialize checkpoint repo:', err);
    return false;
  }
}

async function createRestoreUndoSnapshotLite(sessionId, fileOperations) {
  const baseDir = getRestoreBackupDir(sessionId);
  const backupDir = path.join(baseDir, `files_${Date.now()}`);
  await ensureDir(backupDir);
  const manifest = {
    createdAt: new Date().toISOString(),
    files: []
  };
  const filePaths = Object.keys(fileOperations || {});
  for (const filePath of filePaths) {
    const ops = fileOperations[filePath] || [];
    const trashOp = ops.find(op =>
      op &&
      (op.operationSubtype === 'file_deletion' || op.operationSubtype === 'directory_deletion') &&
      op.operationDetails &&
      op.operationDetails.trashPath
    );
    const trashPath = trashOp && trashOp.operationDetails ? trashOp.operationDetails.trashPath : null;
    let kind = trashOp && trashOp.operationSubtype === 'directory_deletion' ? 'directory' : null;
    let existed = false;
    let content = null;
    try {
      const stat = await fs.lstat(filePath);
      existed = !!stat;
      if (!kind) kind = stat && stat.isDirectory() ? 'directory' : 'file';
      if (kind === 'file') {
        content = await fs.readFile(filePath, 'utf8');
      }
    } catch (err) {
      if (!kind) kind = 'file';
      existed = false;
    }
    const entry = {
      path: filePath,
      existed,
      kind,
      restoreFromTrash: !!trashPath,
      trashPath: trashPath || undefined
    };
    if (existed && kind === 'file') {
      const name = crypto.createHash('sha256').update(filePath).digest('hex') + '.txt';
      const outPath = path.join(backupDir, name);
      await ensureDir(path.dirname(outPath));
      await fs.writeFile(outPath, content || '', 'utf8');
      entry.backup = name;
    }
    manifest.files.push(entry);
  }
  const manifestPath = path.join(backupDir, 'manifest.json');
  await fs.writeFile(manifestPath, JSON.stringify(manifest, null, 2), 'utf8');
  return { backupDir, manifestPath };
}

async function undoRestoreLite(state) {
  if (!state || !state.undoSnapshot || !state.undoSnapshot.manifestPath) return false;
  let manifest;
  try {
    const raw = await fs.readFile(state.undoSnapshot.manifestPath, 'utf8');
    manifest = JSON.parse(raw);
  } catch (err) {
    console.warn('[gitCheckpointManager] Failed to read lite undo manifest:', err?.message || err);
    return false;
  }
  const files = Array.isArray(manifest.files) ? manifest.files : [];
  for (const entry of files) {
    if (!entry || !entry.path) continue;
    const targetPath = entry.path;
    if (entry.restoreFromTrash && entry.trashPath && entry.existed === false) {
      try {
        await ensureDir(path.dirname(entry.trashPath));
        await fs.rm(entry.trashPath, { recursive: true, force: true }).catch(() => {});
        await fs.rename(targetPath, entry.trashPath);
      } catch (_) {}
      continue;
    }
    if (entry.kind === 'directory') {
      if (!entry.existed) {
        try { await fs.rm(targetPath, { recursive: true, force: true }); } catch (_) {}
      }
      continue;
    }
    if (entry.existed) {
      if (!entry.backup) continue;
      try {
        const content = await fs.readFile(path.join(state.undoSnapshot.backupDir, entry.backup), 'utf8');
        await ensureDir(path.dirname(targetPath));
        await fs.writeFile(targetPath, content, 'utf8');
      } catch (_) {}
    } else {
      try { await fs.unlink(targetPath); } catch (_) {}
    }
  }
  return true;
}

async function loadCheckpointIndex(sessionId) {
  const indexPath = getCheckpointIndexPath(sessionId);
  try {
    const raw = await fs.readFile(indexPath, 'utf8');
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') return { session_id: sessionId, checkpoints: [] };
    if (!Array.isArray(parsed.checkpoints)) parsed.checkpoints = [];
    return parsed;
  } catch (err) {
    return { session_id: sessionId, checkpoints: [] };
  }
}

async function saveCheckpointIndex(sessionId, data) {
  const indexPath = getCheckpointIndexPath(sessionId);
  await ensureDir(path.dirname(indexPath));
  await fs.writeFile(indexPath, JSON.stringify(data, null, 2), 'utf8');
}

async function backupSessionHistory(sessionId) {
  const backupDir = getRestoreBackupDir(sessionId);
  await ensureDir(backupDir);

  const sessionFilePath = await getSessionFilePath(sessionId);
  const rawHistoryPath = path.join(PROJECT_ROOT, '.auaci', 'chathistory', 'rawhistory', `${sessionId}.json`);

  const backupInfo = {
    backupDir,
    sessionFileBackup: path.join(backupDir, 'session.json'),
    rawHistoryBackup: path.join(backupDir, 'rawhistory.json')
  };

  try {
    const sessionData = await fs.readFile(sessionFilePath, 'utf8').catch(() => null);
    if (sessionData != null) {
      await fs.writeFile(backupInfo.sessionFileBackup, sessionData, 'utf8');
    }
  } catch (_) {}

  try {
    const rawData = await fs.readFile(rawHistoryPath, 'utf8').catch(() => null);
    if (rawData != null) {
      await fs.writeFile(backupInfo.rawHistoryBackup, rawData, 'utf8');
    }
  } catch (_) {}

  return backupInfo;
}

async function restoreSessionHistoryFromBackup(sessionId, backupInfo) {
  if (!backupInfo || !backupInfo.backupDir) return false;
  const sessionFilePath = await getSessionFilePath(sessionId);
  const rawHistoryPath = path.join(PROJECT_ROOT, '.auaci', 'chathistory', 'rawhistory', `${sessionId}.json`);

  try {
    const sessionData = await fs.readFile(backupInfo.sessionFileBackup, 'utf8').catch(() => null);
    if (sessionData != null) {
      await fs.writeFile(sessionFilePath, sessionData, 'utf8');
    }
  } catch (_) {}

  try {
    const rawData = await fs.readFile(backupInfo.rawHistoryBackup, 'utf8').catch(() => null);
    if (rawData != null) {
      await ensureDir(path.dirname(rawHistoryPath));
      await fs.writeFile(rawHistoryPath, rawData, 'utf8');
    }
  } catch (_) {}

  return true;
}

/**
 * Create a checkpoint (git commit) before processing a message
 * @param {string} sessionId - Session ID
 * @param {number} entryIndex - Chat entry index
 * @param {string} userMessage - User's message text (for context)
 */
async function createCheckpoint(sessionId, entryIndex, userMessage = '') {
  return withSessionLock(sessionId, async () => {
    try {
      const mode = getCheckpointMode();
      const shortMessage = String(userMessage || '').slice(0, 60);

      const indexData = await loadCheckpointIndex(sessionId);
      indexData.checkpoints = Array.isArray(indexData.checkpoints) ? indexData.checkpoints : [];
      const existingIdx = indexData.checkpoints.findIndex(c => c.entryIndex === entryIndex);
      const entry = {
        entryIndex,
        commit: null,
        timestamp: new Date().toISOString(),
        message: shortMessage,
        mode
      };
      if (mode === 'git') {
        await initializeCheckpointRepo(sessionId);
        const commitMessage = `Checkpoint: Session ${sessionId} - Message ${entryIndex} - ${shortMessage}`;
        await runGit(sessionId, ['add', '-A', '--', '.']).catch(() => {});
        await runGit(sessionId, ['commit', '--allow-empty', '-m', commitMessage]).catch(() => {});
        const head = await runGit(sessionId, ['rev-parse', 'HEAD']);
        const commitHash = head.stdout.trim();
        entry.commit = commitHash;
      }
      if (existingIdx >= 0) indexData.checkpoints[existingIdx] = entry;
      else indexData.checkpoints.push(entry);
      await saveCheckpointIndex(sessionId, indexData);

      console.log(`[gitCheckpointManager] Created checkpoint for entry ${entryIndex} (${entry.mode || 'unknown'})`);
      return { success: true, commit: entry.commit, mode: entry.mode };
    } catch (err) {
      console.error('[gitCheckpointManager] Failed to create checkpoint:', err);
      return { success: false };
    }
  });
}

/**
 * Restore to a specific checkpoint by reverting all changes after it
 * @param {number} entryIndex - Chat entry index to restore to
 */
async function restoreToCheckpoint(sessionId, entryIndex) {
  return withSessionLock(sessionId, async () => {
    try {
      const indexData = await loadCheckpointIndex(sessionId);
      const checkpoint = Array.isArray(indexData.checkpoints)
        ? indexData.checkpoints.find(c => c.entryIndex === entryIndex)
        : null;

      const mode = checkpoint && checkpoint.mode
        ? checkpoint.mode
        : (checkpoint && checkpoint.commit ? 'git' : getCheckpointMode());

      if (mode === 'lite') {
        const fileOperations = await getFileOperationsAfterEntry(sessionId, entryIndex);
        const filePaths = Object.keys(fileOperations || {});
        const undoSnapshot = await createRestoreUndoSnapshotLite(sessionId, fileOperations);
        const backupInfo = await backupSessionHistory(sessionId);
        restoreUndoState.set(String(sessionId), {
          mode: 'lite',
          undoSnapshot,
          backupInfo,
          createdAt: Date.now()
        });
        for (const filePath of filePaths) {
          await revertFileOperations(sessionId, filePath, fileOperations[filePath]);
        }
        await truncateOperationLog(sessionId, entryIndex);
        console.log(`[gitCheckpointManager] Restored to checkpoint at entry ${entryIndex} (lite)`);
        return { success: true, canUndo: true, mode: 'lite' };
      }

      if (!checkpoint || !checkpoint.commit) {
        console.warn(`[gitCheckpointManager] No checkpoint found for entry ${entryIndex}`);
        return { success: false, reason: 'no_checkpoint' };
      }

      await initializeCheckpointRepo(sessionId);

      const undoCommit = await createRestoreUndoSnapshot(sessionId);
      const backupInfo = await backupSessionHistory(sessionId);
      restoreUndoState.set(String(sessionId), {
        mode: 'git',
        undoCommit,
        backupInfo,
        createdAt: Date.now()
      });

      await runGit(sessionId, ['reset', '--hard', checkpoint.commit]);

      // Clean up AI-created files (if tracked)
      try {
        const createdFiles = await getCreatedFilesAfterEntry(sessionId, entryIndex);
        for (const filePath of createdFiles) {
          await fs.unlink(filePath).catch(() => {});
        }
      } catch (_) {}

      console.log(`[gitCheckpointManager] Restored to checkpoint at entry ${entryIndex}`);
      return { success: true, canUndo: true };
    } catch (err) {
      console.error('[gitCheckpointManager] Failed to restore checkpoint:', err);
      return { success: false };
    }
  });
}

/**
 * Get list of all checkpoints for the current session
 */
async function getCheckpoints(sessionId) {
  try {
    const indexData = await loadCheckpointIndex(sessionId);
    return Array.isArray(indexData.checkpoints) ? indexData.checkpoints : [];
  } catch (err) {
    console.error('[gitCheckpointManager] Failed to get checkpoints:', err);
    return [];
  }
}

/**
 * Clean up old checkpoints (keep last N)
 * @param {number} keepCount - Number of recent checkpoints to keep
 */
async function cleanupOldCheckpoints(sessionId, keepCount = 50) {
  try {
    const indexData = await loadCheckpointIndex(sessionId);
    if (!Array.isArray(indexData.checkpoints) || indexData.checkpoints.length <= keepCount) {
      return true;
    }
    indexData.checkpoints = indexData.checkpoints.slice(-keepCount);
    await saveCheckpointIndex(sessionId, indexData);
    return true;
  } catch (err) {
    console.error('[gitCheckpointManager] Failed to cleanup checkpoints:', err);
    return false;
  }
}

async function createRestoreUndoSnapshot(sessionId) {
  await runGit(sessionId, ['add', '-A', '--', '.']).catch(() => {});
  const commitMessage = `RestoreUndo: Session ${sessionId} - ${new Date().toISOString()}`;
  await runGit(sessionId, ['commit', '--allow-empty', '-m', commitMessage]).catch(() => {});
  const head = await runGit(sessionId, ['rev-parse', 'HEAD']);
  return head.stdout.trim();
}

async function undoLastRestore(sessionId) {
  return withSessionLock(sessionId, async () => {
    try {
      const state = restoreUndoState.get(String(sessionId));
      if (!state || !state.undoCommit) {
        if (!state || state.mode !== 'lite') {
          return { success: false, reason: 'no_undo' };
        }
        await undoRestoreLite(state);
        await restoreSessionHistoryFromBackup(sessionId, state.backupInfo);
        restoreUndoState.delete(String(sessionId));
        return { success: true };
      }
      await initializeCheckpointRepo(sessionId);
      await runGit(sessionId, ['reset', '--hard', state.undoCommit]);
      await restoreSessionHistoryFromBackup(sessionId, state.backupInfo);
      restoreUndoState.delete(String(sessionId));
      return { success: true };
    } catch (err) {
      console.error('[gitCheckpointManager] Failed to undo restore:', err);
      return { success: false };
    }
  });
}

async function invalidateRestoreUndo(sessionId) {
  const key = String(sessionId || 'unknown');
  const state = restoreUndoState.get(key);
  if (state && state.backupInfo && state.backupInfo.backupDir) {
    try {
      await fs.rm(state.backupInfo.backupDir, { recursive: true, force: true });
    } catch (_) {}
  }
  restoreUndoState.delete(key);
  return true;
}

module.exports = {
  initializeCheckpointRepo,
  createCheckpoint,
  restoreToCheckpoint,
  getCheckpoints,
  cleanupOldCheckpoints,
  undoLastRestore,
  invalidateRestoreUndo
};
