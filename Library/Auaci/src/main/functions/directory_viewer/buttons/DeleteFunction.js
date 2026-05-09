/**
 * Dedicated delete/move-to-trash helpers.
 *
 * Behavior:
 *  - On macOS (darwin): attempt to move items to the user's ~/.Trash folder. If rename
 *    fails due to cross-device links, fall back to copying the item into Trash and
 *    removing the original.
 *  - On Linux: attempt to move to ~/.local/share/Trash/files (simple best-effort).
 *  - On Windows: no reliable simple "move to Recycle Bin" from pure Node without native modules;
 *    as a conservative fallback we remove the item (same as previous behavior). This file can
 *    be extended later to call a native helper to send items to the Recycle Bin.
 *
 * Exports:
 *  - moveToTrash(src) -> Promise<string|null>  : returns destination path in Trash (or null when not applicable)
 *  - deleteFileToTrash(filePath) -> Promise<{success, dest}>
 *  - deleteFolderToTrash(folderPath) -> Promise<{success, dest}>
 *  - emptyFolderContentsToTrash(folderPath) -> Promise<{success, moved:[], errors:[]}>
 *
 * Note: functions prefer moving to Trash but will fall back to permanent removal if necessary.
 */

const fs = require('fs').promises;
const fsSync = require('fs');
const path = require('path');
const os = require('os');

async function uniqueDest(baseDest) {
  // Return a destination path that's not already present by appending a timestamp+random suffix if needed.
  let dest = baseDest;
  while (true) {
    try {
      await fs.access(dest);
      // exists -> create a new candidate
      const ext = path.extname(baseDest);
      const base = path.basename(baseDest, ext);
      const parent = path.dirname(baseDest);
      const suffix = `-${Date.now()}-${Math.random().toString(36).slice(2,8)}`;
      dest = path.join(parent, `${base}${suffix}${ext}`);
    } catch (err) {
      if (err && err.code === 'ENOENT') return dest;
      // Unexpected access error -> rethrow
      throw err;
    }
  }
}

async function copyRecursive(src, dest) {
  const st = await fs.lstat(src);
  if (st.isSymbolicLink()) {
    // replicate symlink
    const linkTarget = await fs.readlink(src);
    try {
      await fs.symlink(linkTarget, dest);
    } catch (e) {
      // fallback: copy file contents (best-effort)
      await fs.copyFile(src, dest);
    }
    return;
  }

  if (st.isDirectory()) {
    await fs.mkdir(dest, { recursive: true });
    const entries = await fs.readdir(src, { withFileTypes: true });
    for (const ent of entries) {
      const srcChild = path.join(src, ent.name);
      const dstChild = path.join(dest, ent.name);
      if (ent.isDirectory()) {
        await copyRecursive(srcChild, dstChild);
      } else if (ent.isSymbolicLink()) {
        const linkTarget = await fs.readlink(srcChild);
        try {
          await fs.symlink(linkTarget, dstChild);
        } catch (e) {
          await fs.copyFile(srcChild, dstChild);
        }
      } else {
        await fs.copyFile(srcChild, dstChild);
      }
    }
  } else {
    await fs.copyFile(src, dest);
  }
}

async function removePath(src) {
  // Remove a file or directory (aggressive fallback).
  try {
    if (typeof fs.rm === 'function') {
      await fs.rm(src, { recursive: true, force: true });
    } else {
      // older Node versions
      await fs.rmdir(src, { recursive: true });
    }
  } catch (err) {
    // try unlink as a last ditch
    try {
      await fs.unlink(src);
    } catch (e) {
      // give up and surface original error
      throw err;
    }
  }
}

/**
 * Move a path into the user's Trash (best-effort).
 * Returns the destination path in Trash on success, or null if a permanent removal path was used.
 */
async function moveToTrash(src) {
  if (!src) throw new Error('Source path required');
  const plat = process.platform;

  // macOS: use ~/.Trash
  if (plat === 'darwin') {
    const trashDir = path.join(os.homedir(), '.Trash');
    try { await fs.mkdir(trashDir, { recursive: true }); } catch (_) {}
    const base = path.basename(src);
    let dest = path.join(trashDir, base);
    dest = await uniqueDest(dest);

    try {
      await fs.rename(src, dest);
      return dest;
    } catch (err) {
      // Cross-device or permission issues -> fallback to copy & remove
      if (err && (err.code === 'EXDEV' || err.code === 'EINVAL' || err.code === 'EPERM')) {
        await copyRecursive(src, dest);
        await removePath(src);
        return dest;
      }
      throw err;
    }
  }

  // Linux: follow XDG Trash spec common location (~/.local/share/Trash/files)
  if (plat === 'linux') {
    const trashDir = path.join(os.homedir(), '.local', 'share', 'Trash', 'files');
    try { await fs.mkdir(trashDir, { recursive: true }); } catch (_) {}
    const base = path.basename(src);
    let dest = path.join(trashDir, base);
    dest = await uniqueDest(dest);

    try {
      await fs.rename(src, dest);
      return dest;
    } catch (err) {
      if (err && err.code === 'EXDEV') {
        await copyRecursive(src, dest);
        await removePath(src);
        return dest;
      }
      throw err;
    }
  }

  // Windows: no cross-platform pure-Node RecycleBin move by default.
  // For now fall back to permanent removal (matching previous behavior).
  if (plat === 'win32') {
    await removePath(src);
    return null;
  }

  // Other: permanent removal fallback
  await removePath(src);
  return null;
}

/**
 * Delete a single file, preferring to move it to Trash when possible.
 * Returns an object { success: boolean, dest?: string|null }
 */
async function deleteFileToTrash(filePath) {
  try {
    const dest = await moveToTrash(filePath);
    return { success: true, dest: dest || null };
  } catch (err) {
    // fallback: attempt unlink
    try {
      await fs.unlink(filePath);
      return { success: true, dest: null };
    } catch (e) {
      throw e;
    }
  }
}

/**
 * Delete a folder and its contents, preferring to move it to Trash when possible.
 * Returns { success: boolean, dest?: string|null }
 */
async function deleteFolderToTrash(folderPath) {
  try {
    const dest = await moveToTrash(folderPath);
    return { success: true, dest: dest || null };
  } catch (err) {
    // fallback: remove recursively
    try {
      await removePath(folderPath);
      return { success: true, dest: null };
    } catch (e) {
      throw e;
    }
  }
}

/**
 * Move every entry inside the folder into Trash (leaving the folder itself).
 * Returns { success: boolean, moved: Array<{source,dest}>, errors: Array<{source,error}> }
 */
async function emptyFolderContentsToTrash(folderPath) {
  const moved = [];
  const errors = [];
  try {
    const entries = await fs.readdir(folderPath);
    for (const name of entries) {
      const child = path.join(folderPath, name);
      try {
        const dest = await moveToTrash(child);
        moved.push({ source: child, dest: dest || null });
      } catch (err) {
        // If moving to trash fails, attempt a forced remove as last resort and report error if that fails too
        try {
          await removePath(child);
          moved.push({ source: child, dest: null });
        } catch (e) {
          errors.push({ source: child, error: e && e.message ? e.message : String(e) });
        }
      }
    }
    return { success: errors.length === 0, moved, errors };
  } catch (err) {
    throw err;
  }
}

module.exports = {
  moveToTrash,
  deleteFileToTrash,
  deleteFolderToTrash,
  emptyFolderContentsToTrash
};