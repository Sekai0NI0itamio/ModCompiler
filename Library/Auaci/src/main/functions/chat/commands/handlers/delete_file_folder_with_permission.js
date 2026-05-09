// src/main/functions/chat/commands/handlers/delete_file_folder_with_permission.js
// Delete files/folders only after explicit user confirmation.
// This handler is used by the interactive delete_file_folder_with_permission tool
// and can also be called directly in "execute" mode.

const fs = require('fs').promises;
const path = require('path');
const crypto = require('crypto');
const { appendLog } = require('../lib/utils');
const { recordFileOperation } = require('../../fileOperationTracker');

function normalizeCheckpointMode(value) {
  if (!value || typeof value !== 'string') return '';
  const v = value.toLowerCase();
  return (v === 'lite' || v === 'git') ? v : '';
}

async function resolveCheckpointMode(baseDir) {
  const env = normalizeCheckpointMode(process.env.AUACI_CHECKPOINT_MODE);
  if (env) return env;
  try {
    const { ipcRenderer } = require('electron');
    if (ipcRenderer && ipcRenderer.invoke) {
      const cfg = await ipcRenderer.invoke('get-config');
      const cfgMode = normalizeCheckpointMode(cfg && cfg.checkpointMode);
      if (cfgMode) return cfgMode;
    }
  } catch (_) {}
  return 'lite';
}

function createTrashBatchDir(baseDir) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-');
  const rand = crypto.randomBytes(4).toString('hex');
  return path.join(baseDir, '.Trash', `auaci_${ts}_${rand}`);
}

function isWithinPath(targetPath, basePath) {
  const resolvedBase = path.resolve(basePath);
  const resolvedTarget = path.resolve(targetPath);
  return resolvedTarget === resolvedBase || resolvedTarget.startsWith(resolvedBase + path.sep);
}

async function moveToTrash(fullPath, baseDir, trashBatchDir) {
  const rel = path.relative(baseDir, fullPath);
  const dest = path.join(trashBatchDir, rel);
  await fs.mkdir(path.dirname(dest), { recursive: true });
  await fs.rename(fullPath, dest);
  return dest;
}

async function collectFilesRecursively(dirPath, out = []) {
  let entries = [];
  try {
    entries = await fs.readdir(dirPath, { withFileTypes: true });
  } catch (_) {
    return out;
  }
  for (const entry of entries) {
    const full = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      await collectFilesRecursively(full, out);
    } else if (entry.isFile()) {
      out.push(full);
    }
  }
  return out;
}

function normalizeItems(params, baseDir) {
  const rawItems = [];
  if (Array.isArray(params.items)) rawItems.push(...params.items);
  else if (Array.isArray(params.paths)) rawItems.push(...params.paths);
  const ctx = params && params.__context ? params.__context : null;

  const items = [];
  for (const raw of rawItems) {
    if (!raw) continue;
    let obj = raw;
    if (typeof raw === 'string') {
      obj = { path: raw };
    }
    if (!obj || typeof obj !== 'object') continue;
    const p = (obj.path || obj.target || obj.file || '').toString().trim();
    if (!p) continue;
    const full = path.isAbsolute(p) ? p : path.join(baseDir, p);
    const selected = (typeof obj.selected === 'boolean') ? !!obj.selected : true;
    const reason = typeof obj.reason === 'string' ? obj.reason : undefined;

    items.push({
      requested_path: p,
      full_path: full,
      selected,
      reason,
      kind: obj.kind || null,
      __context: ctx,
      status: 'pending',
      error: undefined,
    });
  }
  return items;
}

async function classifyPath(item, safeBase) {
  try {
    const stat = await fs.lstat(item.full_path);
    if (!stat) {
      item.kind = 'missing';
      item.status = 'missing';
      item.error = 'Path does not exist';
      return;
    }
    item.kind = stat.isDirectory() ? 'directory' : 'file';
    // If outside the safe base, mark as skipped
    const resolvedBase = path.resolve(safeBase);
    const resolvedTarget = path.resolve(item.full_path);
    if (!resolvedTarget.startsWith(resolvedBase + path.sep) && resolvedTarget !== resolvedBase) {
      item.status = 'skipped';
      item.error = 'Refusing to delete path outside base_dir';
    }
  } catch {
    item.kind = 'missing';
    item.status = 'missing';
    item.error = 'Path does not exist';
  }
}

async function deletePath(item, safeBase, opts = {}) {
  // Ensure classification first
  if (!item.kind || item.status === 'pending') {
    await classifyPath(item, safeBase);
  }
  if (item.status === 'missing' || item.status === 'skipped') return;
  if (!item.selected) {
    item.status = 'skipped';
    return;
  }

  try {
    const ctx = item.__context || {};
    const checkpointMode = opts && opts.checkpointMode ? opts.checkpointMode : 'git';
    const baseDir = opts && opts.baseDir ? opts.baseDir : safeBase;
    const trashBatchDir = opts && opts.trashBatchDir ? opts.trashBatchDir : null;
    const trashRoot = path.join(baseDir, '.Trash');
    const canTrash = checkpointMode === 'lite' && trashBatchDir && isWithinPath(item.full_path, baseDir) && !isWithinPath(item.full_path, trashRoot);

    if (canTrash) {
      const trashPath = await moveToTrash(item.full_path, baseDir, trashBatchDir);
      if (ctx && ctx.sessionId != null && ctx.entryIndex != null) {
        await recordFileOperation(ctx.sessionId, ctx.entryIndex, 'delete_file', {
          filePath: item.full_path,
          originalContent: null,
          fileExisted: true,
          operationSubtype: item.kind === 'directory' ? 'directory_deletion' : 'file_deletion',
          operationDetails: { requestedPath: item.requested_path, trashPath, fromDirectory: item.kind === 'directory' ? item.full_path : undefined }
        }).catch(() => {});
      }
      item.status = 'deleted';
      return;
    }

    let originalContent = null;
    if (item.kind === 'file') {
      try {
        originalContent = await fs.readFile(item.full_path, 'utf8');
      } catch (_) {}
    }
    if (item.kind === 'directory') {
      if (ctx && ctx.sessionId != null && ctx.entryIndex != null) {
        const files = await collectFilesRecursively(item.full_path);
        for (const filePath of files) {
          let fileContent = null;
          try {
            fileContent = await fs.readFile(filePath, 'utf8');
          } catch (_) {}
          await recordFileOperation(ctx.sessionId, ctx.entryIndex, 'delete_file', {
            filePath,
            originalContent: fileContent,
            fileExisted: true,
            operationSubtype: 'file_deletion',
            operationDetails: { requestedPath: item.requested_path, fromDirectory: item.full_path }
          }).catch(() => {});
        }
      }
      await fs.rm(item.full_path, { recursive: true, force: true });
    } else {
      await fs.rm(item.full_path, { force: true });
    }
    item.status = 'deleted';

    if (ctx && ctx.sessionId != null && ctx.entryIndex != null && item.kind === 'file') {
      recordFileOperation(ctx.sessionId, ctx.entryIndex, 'delete_file', {
        filePath: item.full_path,
        originalContent,
        fileExisted: true,
        operationSubtype: 'file_deletion',
        operationDetails: { requestedPath: item.requested_path }
      }).catch(() => {});
    }
  } catch (e) {
    item.status = 'error';
    item.error = e && e.message ? e.message : String(e);
  }
}

function buildSummary(items, mode, baseDir, reason) {
  const requested = items.length;
  const selected = items.filter(i => i.selected).length;
  const deleted = items.filter(i => i.status === 'deleted').length;
  const missing = items.filter(i => i.status === 'missing').length;
  const skipped = items.filter(i => i.status === 'skipped').length;
  const failed = items.filter(i => i.status === 'error').length;

  return {
    requested_count: requested,
    to_delete_count: selected,
    deleted_count: deleted,
    missing_count: missing,
    skipped_count: skipped,
    failed_count: failed,
    mode,
    base_dir: baseDir,
    reason: reason || undefined,
  };
}

function renderDisplayContent(items, summary, mode) {
  const sections = [];
  const header = mode === 'preview'
    ? '🧹 Delete Files/Folders (preview)'
    : '🧹 Delete Files/Folders (executed)';
  sections.push(header);
  sections.push('─'.repeat(60));

  if (summary.reason) {
    sections.push(`Reason: ${summary.reason}`);
    sections.push('');
  }

  sections.push(`Items requested: ${summary.requested_count}`);
  sections.push(`Selected for deletion: ${summary.to_delete_count}`);
  if (mode !== 'preview') {
    sections.push(`Deleted: ${summary.deleted_count}`);
    sections.push(`Skipped: ${summary.skipped_count}`);
    sections.push(`Missing: ${summary.missing_count}`);
    sections.push(`Failed: ${summary.failed_count}`);
  }
  sections.push('');

  if (!items.length) {
    sections.push('No files or folders were provided.');
  } else {
    sections.push('Items:');
    items.forEach((item, idx) => {
      const idxStr = String(idx + 1).padStart(2, ' ');
      const kind = item.kind === 'directory' ? 'folder' : (item.kind === 'file' ? 'file' : 'unknown');
      let statusLabel = 'pending';
      if (mode === 'preview') {
        if (item.status === 'missing') statusLabel = 'missing (cannot delete)';
        else if (!item.selected) statusLabel = 'not selected';
        else statusLabel = 'will ask for confirmation';
      } else {
        if (item.status === 'deleted') statusLabel = 'deleted';
        else if (item.status === 'skipped' || !item.selected) statusLabel = 'kept';
        else if (item.status === 'missing') statusLabel = 'missing';
        else if (item.status === 'error') statusLabel = `error: ${item.error || 'unknown error'}`;
      }
      const labelPath = item.requested_path || item.full_path;
      sections.push(`${idxStr}. ${labelPath} (${kind}) -> ${statusLabel}`);
    });
  }

  return sections.join('\n');
}

module.exports = async function deleteFileFolderWithPermissionCmd(params) {
  const modeRaw = (params && typeof params.mode === 'string') ? params.mode.toLowerCase() : 'execute';
  const mode = modeRaw === 'preview' ? 'preview' : 'execute';
  const reason = params && typeof params.reason === 'string' ? params.reason : undefined;
  const baseDir = params && typeof params.base_dir === 'string' && params.base_dir.trim()
    ? params.base_dir
    : process.cwd();

  await appendLog(`[tools.delete_file_folder_with_permission] start mode=${mode}`);

  const items = normalizeItems(params || {}, baseDir);
  if (!items.length) {
    const emptySummary = buildSummary(items, mode, baseDir, reason);
    return {
      display_content: renderDisplayContent(items, emptySummary, mode),
      items,
      summary: emptySummary,
      mode,
      waiting: mode === 'preview',
      success: mode !== 'preview'
    };
  }

  const safeBase = baseDir || process.cwd();
  const checkpointMode = await resolveCheckpointMode(safeBase);
  let trashBatchDir = null;
  if (mode === 'execute' && checkpointMode === 'lite') {
    try {
      trashBatchDir = createTrashBatchDir(safeBase);
      await fs.mkdir(trashBatchDir, { recursive: true });
    } catch (_) {
      trashBatchDir = null;
    }
  }

  if (mode === 'preview') {
    for (const item of items) {
      await classifyPath(item, safeBase);
    }
    const summary = buildSummary(items, mode, baseDir, reason);
    const display_content = renderDisplayContent(items, summary, mode);
    await appendLog(`[tools.delete_file_folder_with_permission] preview ${items.length} items`);
    return {
      display_content,
      items,
      summary,
      mode,
      waiting: true,
      success: true
    };
  }

  // execute mode: actually delete selected items
  for (const item of items) {
    await deletePath(item, safeBase, { checkpointMode, trashBatchDir, baseDir: safeBase });
  }

  const summary = buildSummary(items, mode, baseDir, reason);
  const display_content = renderDisplayContent(items, summary, mode);

  await appendLog(`[tools.delete_file_folder_with_permission] execute deleted=${summary.deleted_count} skipped=${summary.skipped_count} failed=${summary.failed_count}`);

  return {
    display_content,
    items,
    summary,
    mode,
    waiting: false,
    success: summary.failed_count === 0
  };
};
