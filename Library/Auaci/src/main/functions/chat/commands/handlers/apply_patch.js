// src/main/functions/chat/commands/handlers/apply_patch.js
// ──────────────────────────────────────────────────────────────────────
// Copilot-style apply_patch handler.
// Accepts a unified patch string in the "*** Begin Patch … *** End Patch"
// format, processes it through the 6-pass fuzzy context-matching engine,
// and writes the resulting changes to disk.
// ──────────────────────────────────────────────────────────────────────

const path = require('path');
const fs = require('fs').promises;
const crypto = require('crypto');
const { execFile } = require('child_process');
const { appendLog, detectEOL } = require('../lib/utils');
const {
  processPatch,
  ActionType,
  DiffError,
  InvalidContextError,
  InvalidPatchFormatError,
  PATCH_PREFIX,
  ADD_FILE_PREFIX,
  DELETE_FILE_PREFIX,
  UPDATE_FILE_PREFIX,
  guessIndentation,
  transformIndentation,
} = require('../lib/copilot-apply-patch-core');

/**
 * Get the current working directory for the chat session.
 * Falls back to process.cwd() if not available.
 */
function getChatCwd() {
  try {
    const { getCurrentCwd } = require('../../ui/terminalInput');
    const cwd = getCurrentCwd();
    if (cwd) return cwd;
  } catch (_) {}
  return process.cwd();
}

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

// ─── main handler ─────────────────────────────────────────────────────

module.exports = async function applyPatchCmd(params) {
  const patchText = extractPatchText(params);
  if (!patchText) {
    throw new Error(
      'apply_patch: `input` must contain a valid *** Begin Patch … *** End Patch block'
    );
  }

  const cwd = getChatCwd();
  const checkpointMode = await resolveCheckpointMode(cwd);
  let trashBatchDir = null;
  const trashMetaByFile = new Map();
  const ensureTrashBatchDir = async () => {
    if (trashBatchDir || checkpointMode !== 'lite') return trashBatchDir;
    trashBatchDir = createTrashBatchDir(cwd);
    await fs.mkdir(trashBatchDir, { recursive: true });
    return trashBatchDir;
  };
  const results = [];
  const errors = [];
  const touchedFiles = new Set();
  const originalByFile = new Map();
  const fileExistedByFile = new Map();
  const moveMetaByTarget = new Map();

  // File-reader used by the core – resolves relative paths against cwd.
  const readFn = async (relPath) => {
    const abs = path.isAbsolute(relPath)
      ? relPath
      : path.join(cwd, relPath);
    const content = await fs.readFile(abs, 'utf8');
    if (!originalByFile.has(abs)) {
      originalByFile.set(abs, content);
      fileExistedByFile.set(abs, true);
    }
    return content;
  };

  let usedHealing = false;
  let appliedViaLoose = false;

  // ── Run the Copilot core pipeline ──────────────────────────────────
  try {
    let commit;
    try {
      commit = await processPatch(patchText, readFn);
    } catch (e) {
      const healed = healPatchText(patchText);
      if (healed && healed !== patchText) {
        try {
          commit = await processPatch(healed, readFn);
          usedHealing = true;
        } catch (_) {
          // fall through to loose application
        }
      }
      if (!commit) {
        commit = await processPatchLoosely(patchText, readFn, cwd);
        usedHealing = true;
        appliedViaLoose = true;
      }
    }

    for (const [relPath, change] of Object.entries(commit.changes)) {
      const abs = path.isAbsolute(relPath)
        ? relPath
        : path.join(cwd, relPath);

      try {
        if (change.type === ActionType.DELETE) {
          if (!originalByFile.has(abs)) {
            try {
              originalByFile.set(abs, await fs.readFile(abs, 'utf8'));
              fileExistedByFile.set(abs, true);
            } catch (_) {}
          }
          let trashed = false;
          if (checkpointMode === 'lite' && isWithinPath(abs, cwd) && !isWithinPath(abs, path.join(cwd, '.Trash'))) {
            try {
              const batch = await ensureTrashBatchDir();
              if (batch) {
                const trashPath = await moveToTrash(abs, cwd, batch);
                trashMetaByFile.set(abs, { trashPath });
                trashed = true;
              }
            } catch (_) {
              trashed = false;
            }
          }
          if (!trashed) {
            await fs.unlink(abs);
          }
          results.push({ file_path: abs, message: 'Deleted file', operation: 'delete' });
          touchedFiles.add(abs);

        } else if (change.type === ActionType.ADD) {
          const dir = path.dirname(abs);
          await fs.mkdir(dir, { recursive: true });
          if (!originalByFile.has(abs)) {
            originalByFile.set(abs, null);
            fileExistedByFile.set(abs, false);
          }
          await fs.writeFile(abs, change.newContent, 'utf8');
          const lineCount = (change.newContent.match(/\n/g) || []).length + 1;
          results.push({
            file_path: abs,
            message: `Created file (${lineCount} lines)`,
            operation: 'add',
            new_lines: lineCount,
          });
          touchedFiles.add(abs);

        } else if (change.type === ActionType.UPDATE) {
          let targetAbs = abs;
          if (change.movePath) {
            targetAbs = path.isAbsolute(change.movePath)
              ? change.movePath
              : path.join(cwd, change.movePath);
            await fs.mkdir(path.dirname(targetAbs), { recursive: true });
            if (abs !== targetAbs && !moveMetaByTarget.has(targetAbs)) {
              let targetExisted = false;
              let targetOriginal = null;
              try {
                targetOriginal = await fs.readFile(targetAbs, 'utf8');
                targetExisted = true;
              } catch (err) {
                if (err && err.code !== 'ENOENT') {
                  console.warn('[apply_patch] Failed to read move target:', err);
                }
              }
              moveMetaByTarget.set(targetAbs, { from: abs, targetExisted, targetOriginal });
            }
          }

          const eol = detectEOL(change.oldContent || '');
          let content = change.newContent;
          if (eol !== '\n') content = content.replace(/\n/g, eol);

          await fs.writeFile(targetAbs, content, 'utf8');
          if (change.movePath && abs !== targetAbs) {
            try { await fs.unlink(abs); } catch (_) {}
          }

          const oldLineCount = (change.oldContent || '').split('\n').length;
          const newLineCount = (change.newContent || '').split('\n').length;
          const delta = newLineCount - oldLineCount;
          let msg = 'Applied patch';
          if (delta > 0) msg += ` (+${delta} lines)`;
          else if (delta < 0) msg += ` (${delta} lines)`;
          if (change.movePath) msg += ` → ${change.movePath}`;

          results.push({
            file_path: targetAbs,
            message: msg,
            operation: 'update',
            old_lines: oldLineCount,
            new_lines: newLineCount,
          });
          touchedFiles.add(targetAbs);
        }
      } catch (e) {
        errors.push({ file_path: abs, error: String(e.message || e) });
      }
    }

    if (commit.fuzz) {
      await appendLog(`[apply_patch] fuzz bitmask: ${commit.fuzz}`);
    }
  } catch (e) {
    errors.push({
      error: String(e.message || e),
      type: e instanceof DiffError ? e.name : 'Error',
    });
  }

  // ── Syntax checks ─────────────────────────────────────────────────
  const syntaxIssues = [];
  for (const abs of touchedFiles) {
    try {
      const content = await fs.readFile(abs, 'utf8');
      const info = await runSyntaxCheck(abs, content);
      if (!info.skipped && !info.ok) {
        syntaxIssues.push({ file_path: abs, errors: info.errors });
      }
    } catch (_) {}
  }

  // ── Build summary ─────────────────────────────────────────────────
  const summary = {
    applied_count: results.length,
    error_count: errors.length,
    applied: results,
    errors,
    success: errors.length === 0,
    healed: usedHealing || false,
    healing_strategy: appliedViaLoose ? 'loose' : (usedHealing ? 'normalized' : 'none'),
  };
  if (syntaxIssues.length) summary.syntax_issues = syntaxIssues;

  // ── Record for undo / restore tracking ────────────────────────────
  try {
    const { recordFileOperation } = require('../../fileOperationTracker');
    const ctx = params && params.__context ? params.__context : {};
    if (!params.__suppressRecord && ctx && ctx.sessionId != null && ctx.entryIndex != null) {
      for (const filePath of touchedFiles) {
        const moveMeta = moveMetaByTarget.get(filePath);
        if (moveMeta && moveMeta.from) {
          const fromPath = moveMeta.from;
          await recordFileOperation(ctx.sessionId, ctx.entryIndex, 'apply_patch', {
            filePath: fromPath,
            originalContent: originalByFile.get(fromPath) ?? null,
            fileExisted: fileExistedByFile.get(fromPath) !== false,
            operationSubtype: 'file_deletion',
            operationDetails: { moveTo: filePath }
          }).catch(() => {});

          if (moveMeta.targetExisted) {
            await recordFileOperation(ctx.sessionId, ctx.entryIndex, 'apply_patch', {
              filePath,
              originalContent: moveMeta.targetOriginal,
              fileExisted: true,
              operationSubtype: 'file_edit',
              operationDetails: { moveFrom: fromPath }
            }).catch(() => {});
          } else {
            await recordFileOperation(ctx.sessionId, ctx.entryIndex, 'apply_patch', {
              filePath,
              originalContent: null,
              fileExisted: false,
              operationSubtype: 'file_creation',
              operationDetails: { moveFrom: fromPath }
            }).catch(() => {});
          }
          continue;
        }
        const r = results.find((r) => r.file_path === filePath);
        const rawOp = r ? r.operation : 'file_edit';
        const normalizedOp = rawOp === 'delete'
          ? 'file_deletion'
          : (rawOp === 'add' ? 'file_creation' : rawOp);
        const trashMeta = trashMetaByFile.get(filePath);
        const opDetails = Object.assign(
          { patch_length: patchText.length },
          trashMeta && trashMeta.trashPath ? { trashPath: trashMeta.trashPath } : {}
        );
        await recordFileOperation(ctx.sessionId, ctx.entryIndex, 'apply_patch', {
          filePath,
          originalContent: originalByFile.get(filePath) || null,
          fileExisted: fileExistedByFile.get(filePath) !== false,
          operationSubtype: normalizedOp || 'file_edit',
          operationDetails: opDetails,
        }).catch(() => {});
      }
    }
  } catch (_) {}

  await appendLog(`[apply_patch] applied=${results.length} errors=${errors.length}`);
  return summary;
};

// ─── helpers ──────────────────────────────────────────────────────────

/**
 * Extract the patch text from whatever shape the params arrive in.
 * Accepts: params.input, params.diff, params.patch, params.content — all as raw strings
 * containing `*** Begin Patch … *** End Patch`.
 */
function extractPatchText(params) {
  if (!params || typeof params !== 'object') return null;
  let text = params.input || params.diff || params.patch || params.content || '';
  if (typeof text !== 'string') text = String(text);
  text = text.trim();
  if (!text) return null;

  const normalized = normalizePatchText(text);
  return normalized || null;
}

function normalizePatchText(text) {
  if (!text) return '';
  let out = text.replace(/\r\n/g, '\n');

  const fenced = out.match(/```(?:\w+)?\s*([\s\S]*?)```/);
  if (fenced && fenced[1]) out = fenced[1].trim();

  const pfx = PATCH_PREFIX.trim();
  const idx = out.indexOf(pfx);
  if (idx !== -1) out = out.slice(idx);

  if (!out.startsWith(pfx)) return '';
  if (!out.includes('*** End Patch')) out += '\n*** End Patch';
  return out.trim();
}

function healPatchText(text) {
  const normalized = normalizePatchText(text);
  if (!normalized) return '';
  return normalized;
}

async function processPatchLoosely(patchText, readFn, cwd) {
  const ops = parsePatchOps(patchText);
  if (!ops || ops.length === 0) {
    throw new InvalidPatchFormatError('Invalid patch text', 'invalidPatchText');
  }

  const commit = { changes: {}, fuzz: 0 };

  for (const op of ops) {
    if (op.type === 'delete') {
      const content = await readFn(op.path);
      commit.changes[op.path] = { type: ActionType.DELETE, oldContent: content };
      continue;
    }

    if (op.type === 'add') {
      commit.changes[op.path] = { type: ActionType.ADD, newContent: op.content || '' };
      continue;
    }

    const original = await readFn(op.path);
    const eol = detectEOL(original);
    const hunks = parseUpdateHunks(op.update || '');
    const updated = applyUpdateHunks(original, hunks, eol);
    commit.changes[op.path] = {
      type: ActionType.UPDATE,
      oldContent: original,
      newContent: updated,
      movePath: op.movePath || undefined,
    };
  }

  commit.fuzz = 'loose';
  return commit;
}

function parsePatchOps(text) {
  const normalized = normalizePatchText(text);
  if (!normalized) return null;

  const lines = normalized.split('\n');
  const ops = [];
  let current = null;

  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];
    if (line.startsWith('*** End Patch')) break;

    if (line.startsWith(ADD_FILE_PREFIX)) {
      current = { type: 'add', path: line.slice(ADD_FILE_PREFIX.length).trim(), content: '' };
      ops.push(current);
      continue;
    }
    if (line.startsWith(DELETE_FILE_PREFIX)) {
      current = { type: 'delete', path: line.slice(DELETE_FILE_PREFIX.length).trim() };
      ops.push(current);
      continue;
    }
    if (line.startsWith(UPDATE_FILE_PREFIX)) {
      current = { type: 'update', path: line.slice(UPDATE_FILE_PREFIX.length).trim(), update: '', movePath: null };
      ops.push(current);
      continue;
    }
    if (line.startsWith('*** Move to: ') && current && current.type === 'update') {
      current.movePath = line.slice('*** Move to: '.length).trim();
      continue;
    }

    if (!current) continue;

    if (current.type === 'add') {
      const contentLine = line.startsWith('+') ? line.slice(1) : line;
      current.content = appendLine(current.content, contentLine);
      continue;
    }

    if (current.type === 'update') {
      current.update = current.update ? current.update + '\n' + line : line;
    }
  }

  return ops;
}

function parseUpdateHunks(updateText) {
  const lines = updateText.split('\n');
  const hunks = [];
  let current = null;

  for (const raw of lines) {
    if (raw.startsWith('@@')) {
      if (current) hunks.push(current);
      current = { header: raw.slice(2).trim(), lines: [] };
      continue;
    }
    if (raw.startsWith('*** End of File')) {
      if (!current) current = { header: '', lines: [] };
      current.eof = true;
      continue;
    }
    if (!current) {
      current = { header: '', lines: [] };
    }

    if (raw.startsWith('+') || raw.startsWith('-') || raw.startsWith(' ')) {
      current.lines.push(raw);
    } else if (raw.length) {
      current.lines.push(' ' + raw);
    }
  }

  if (current) hunks.push(current);
  return hunks.filter(h => h.lines.length > 0 || h.eof);
}

function applyUpdateHunks(text, hunks, eol) {
  let updated = text;
  const targetIndent = guessIndentation(updated.split(eol), 4, true);

  for (const hunk of hunks) {
    const oldLines = [];
    const newLines = [];

    for (const line of hunk.lines) {
      const prefix = line[0];
      const body = line.slice(1);
      if (prefix !== '+') oldLines.push(body);
      if (prefix !== '-') newLines.push(body);
    }

    const oldStr = oldLines.join(eol);
    let newStr = newLines.join(eol);

    if (newStr && newStr.includes('\n')) {
      const srcIndent = guessIndentation(newStr.split('\n'), targetIndent.tabSize, targetIndent.insertSpaces);
      newStr = transformIndentation(newStr, srcIndent, targetIndent);
    }

    if (!oldStr && newStr) {
      updated = appendWithEol(updated, newStr, eol);
      continue;
    }

    const match = findAndReplaceOne(updated, oldStr, newStr, eol);
    if (match.type === 'multiple') {
      const narrowed = tryHeaderDisambiguation(updated, oldStr, newStr, hunk.header, eol);
      if (!narrowed) {
        throw new InvalidContextError('Multiple matches found while applying hunk', text, 'multipleMatches');
      }
      updated = narrowed;
      continue;
    }
    if (match.type === 'none') {
      throw new InvalidContextError('No matching context found while applying hunk', text, 'invalidContext');
    }
    updated = match.text;
  }

  return updated;
}

function tryHeaderDisambiguation(text, oldStr, newStr, header, eol) {
  if (!header || !header.trim()) return null;

  const lines = text.split(eol);
  const headerTrim = header.trim();
  let headerLine = lines.findIndex(l => l.includes(headerTrim) || l.trim() === headerTrim);
  if (headerLine === -1) return null;

  const radius = 200;
  const start = Math.max(0, headerLine - radius);
  const end = Math.min(lines.length, headerLine + radius);
  const windowText = lines.slice(start, end).join(eol);
  const match = findAndReplaceOne(windowText, oldStr, newStr, eol);
  if (match.type === 'none' || match.type === 'multiple') return null;

  const prefix = lines.slice(0, start).join(eol);
  const suffix = lines.slice(end).join(eol);
  const joined = [prefix, match.text, suffix].filter(Boolean).join(eol);
  return joined;
}

function appendWithEol(text, addition, eol) {
  if (!text) return addition;
  if (text.endsWith(eol)) return text + addition;
  return text + eol + addition;
}

function appendLine(content, line) {
  if (!content) return line;
  return content + '\n' + line;
}

function findAndReplaceOne(text, oldStr, newStr, eol) {
  const exact = tryExactMatch(text, oldStr, newStr);
  if (exact.type !== 'none') return exact;

  const whitespace = tryWhitespaceFlexibleMatch(text, oldStr, newStr, eol);
  if (whitespace.type !== 'none') return whitespace;

  const fuzzy = tryFuzzyMatch(text, oldStr, newStr, eol);
  if (fuzzy.type !== 'none') return fuzzy;

  const similarity = trySimilarityMatch(text, oldStr, newStr, eol);
  if (similarity.type !== 'none') return similarity;

  return { text, type: 'none', editPosition: [] };
}

function tryExactMatch(text, oldStr, newStr) {
  const matchPositions = [];
  for (let searchIdx = 0; ;) {
    const idx = text.indexOf(oldStr, searchIdx);
    if (idx === -1) break;
    matchPositions.push(idx);
    searchIdx = idx + oldStr.length;
  }
  if (matchPositions.length === 0) {
    return { text, editPosition: [], type: 'none' };
  }

  const identical = getIdenticalChars(oldStr, newStr);
  const editPosition = matchPositions.map(idx => ({
    start: idx + identical.leading,
    end: idx + oldStr.length - identical.trailing,
    text: newStr.slice(identical.leading, newStr.length - identical.trailing),
  }));

  if (matchPositions.length > 1) {
    return { text, type: 'multiple', editPosition, matchPositions };
  }
  const firstIdx = matchPositions[0];
  const replaced = text.slice(0, firstIdx) + newStr + text.slice(firstIdx + oldStr.length);
  return { text: replaced, type: 'exact', editPosition };
}

function tryWhitespaceFlexibleMatch(text, oldStr, newStr, eol) {
  const haystack = text.split(eol).map(line => line.trim());
  const needle = oldStr.split(eol).map(line => line.trim());
  if (!needle.length) return { text, editPosition: [], type: 'none' };

  const matchedLines = [];
  for (let i = 0; i <= haystack.length - needle.length; i++) {
    if (haystack.slice(i, i + needle.length).join('\n') === needle.join('\n')) {
      matchedLines.push(i);
      i += needle.length - 1;
    }
  }
  if (matchedLines.length === 0) return { text, editPosition: [], type: 'none' };
  if (matchedLines.length > 1) return { text, editPosition: [], type: 'multiple', matchPositions: matchedLines };

  const lineOffsets = buildLineOffsets(text, eol);
  const startLine = matchedLines[0];
  const endLine = startLine + needle.length;
  const startIdx = lineOffsets[startLine] || 0;
  const endIdx = endLine < lineOffsets.length ? lineOffsets[endLine] : text.length;

  const replaced = text.slice(0, startIdx) + newStr + text.slice(endIdx);
  return { text: replaced, type: 'whitespace', editPosition: [{ start: startIdx, end: endIdx, text: newStr }] };
}

function tryFuzzyMatch(text, oldStr, newStr, eol) {
  const hasTrailingLF = oldStr.endsWith(eol);
  if (hasTrailingLF) oldStr = oldStr.slice(0, -eol.length);

  const oldLines = oldStr.split(eol);
  const pattern = oldLines
    .map((line, i) => {
      const escaped = escapeRegex(line);
      return i < oldLines.length - 1 || hasTrailingLF
        ? `${escaped}[ \t]*\\r?\\n`
        : `${escaped}[ \t]*`;
    })
    .join('');
  const regex = new RegExp(pattern, 'g');

  const matches = Array.from(text.matchAll(regex));
  if (matches.length === 0) return { text, editPosition: [], type: 'none' };
  if (matches.length > 1) return { text, editPosition: [], type: 'multiple', matchPositions: matches.map(m => m.index || 0) };

  const match = matches[0];
  const startIdx = match.index || 0;
  const endIdx = startIdx + match[0].length;
  const replaced = text.slice(0, startIdx) + newStr + text.slice(endIdx);
  return { text: replaced, type: 'fuzzy', editPosition: [{ start: startIdx, end: endIdx, text: newStr }] };
}

function trySimilarityMatch(text, oldStr, newStr, eol) {
  if (oldStr.length > 1000 || oldStr.split(eol).length > 20) return { text, editPosition: [], type: 'none' };

  const lines = text.split(eol);
  const oldLines = oldStr.split(eol);
  if (lines.length > 1000) return { text, editPosition: [], type: 'none' };

  const newLines = newStr.split(eol);
  const identical = getIdenticalLines(oldLines, newLines);

  let bestMatch = { startLine: -1, startOffset: 0, oldLength: 0, similarity: 0 };
  let startOffset = 0;

  for (let i = 0; i <= lines.length - oldLines.length; i++) {
    let totalSimilarity = 0;
    let oldLength = 0;
    let startOffsetIdenticalIncr = 0;
    let endOffsetIdenticalIncr = 0;

    for (let j = 0; j < oldLines.length; j++) {
      const similarity = calculateSimilarity(oldLines[j], lines[i + j]);
      totalSimilarity += similarity;
      oldLength += lines[i + j].length;

      if (j < identical.leading) startOffsetIdenticalIncr += lines[i + j].length + eol.length;
      if (j >= oldLines.length - identical.trailing) endOffsetIdenticalIncr += lines[i + j].length + eol.length;
    }

    const avgSimilarity = totalSimilarity / oldLines.length;
    if (avgSimilarity > 0.95 && avgSimilarity > bestMatch.similarity) {
      bestMatch = {
        startLine: i + identical.leading,
        startOffset: startOffset + startOffsetIdenticalIncr,
        similarity: avgSimilarity,
        oldLength: oldLength + (oldLines.length - 1) * eol.length - startOffsetIdenticalIncr - endOffsetIdenticalIncr,
      };
    }

    startOffset += lines[i].length + eol.length;
  }

  if (bestMatch.startLine === -1) return { text, editPosition: [], type: 'none' };

  const newStrMinimized = newLines.slice(identical.leading, newLines.length - identical.trailing).join(eol);
  const matchStart = bestMatch.startLine - identical.leading;
  const afterIdx = matchStart + oldLines.length - identical.trailing;
  const newText = [
    ...lines.slice(0, bestMatch.startLine),
    ...newLines.slice(identical.leading, newLines.length - identical.trailing),
    ...lines.slice(afterIdx),
  ].join(eol);

  return {
    text: newText,
    type: 'similarity',
    editPosition: [{ start: bestMatch.startOffset, end: bestMatch.startOffset + bestMatch.oldLength, text: newStrMinimized }],
  };
}

function buildLineOffsets(text, eol) {
  const lines = text.split(eol);
  const offsets = [];
  let offset = 0;
  for (const line of lines) {
    offsets.push(offset);
    offset += line.length + eol.length;
  }
  return offsets;
}

function escapeRegex(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function calculateSimilarity(str1, str2) {
  if (str1 === str2) return 1.0;
  if (!str1.length || !str2.length) return 0.0;

  const matrix = [];
  for (let i = 0; i <= str1.length; i++) matrix[i] = [i];
  for (let j = 0; j <= str2.length; j++) matrix[0][j] = j;

  for (let i = 1; i <= str1.length; i++) {
    for (let j = 1; j <= str2.length; j++) {
      const cost = str1[i - 1] === str2[j - 1] ? 0 : 1;
      matrix[i][j] = Math.min(
        matrix[i - 1][j] + 1,
        matrix[i][j - 1] + 1,
        matrix[i - 1][j - 1] + cost
      );
    }
  }

  const distance = matrix[str1.length][str2.length];
  const maxLength = Math.max(str1.length, str2.length);
  return 1 - distance / maxLength;
}

function getIdenticalLines(a, b) {
  let leading = 0;
  let trailing = 0;
  while (leading < a.length && leading < b.length && a[leading] === b[leading]) {
    leading++;
  }
  while (trailing + leading < a.length && trailing + leading < b.length && a[a.length - 1 - trailing] === b[b.length - 1 - trailing]) {
    trailing++;
  }
  return { leading, trailing };
}

function getIdenticalChars(oldString, newString) {
  let leading = 0;
  let trailing = 0;
  while (leading < oldString.length && leading < newString.length && oldString[leading] === newString[leading]) {
    leading++;
  }
  while (trailing + leading < oldString.length && trailing + leading < newString.length && oldString[oldString.length - trailing - 1] === newString[newString.length - trailing - 1]) {
    trailing++;
  }
  return { leading, trailing };
}

/**
 * Lightweight syntax check (JSON parse / node --check / python -m py_compile).
 */
async function runSyntaxCheck(absPath, content) {
  const ext = path.extname(absPath).toLowerCase();

  if (ext === '.json') {
    try { JSON.parse(content); return { ok: true, skipped: false }; }
    catch (e) { return { ok: false, skipped: false, errors: [{ message: e.message }] }; }
  }

  let cmd, args;
  if (['.js', '.jsx', '.mjs', '.cjs'].includes(ext)) {
    cmd = 'node'; args = ['--check', absPath];
  } else if (ext === '.py') {
    cmd = 'python'; args = ['-m', 'py_compile', absPath];
  } else if (['.sh', '.bash', '.zsh'].includes(ext)) {
    cmd = 'bash'; args = ['-n', absPath];
  } else {
    return { ok: true, skipped: true };
  }

  return new Promise((resolve) => {
    try {
      execFile(cmd, args, { cwd: process.cwd() }, (error, stdout, stderr) => {
        if (!error) {
          resolve({ ok: true, skipped: false });
        } else {
          const out = String(stderr || stdout || '').trim();
          const errs = out.split(/\r?\n/).filter(Boolean).map((l) => ({ message: l }));
          resolve({ ok: false, skipped: false, errors: errs.length ? errs : [{ message: error.message }] });
        }
      });
    } catch (_) {
      resolve({ ok: true, skipped: true });
    }
  });
}
