const fs = require('fs').promises;
const path = require('path');
const os = require('os');
const { clipboard, ipcRenderer } = require('electron');
const { scanDirectoryTree } = require('../scan');
const { renderDirectoryTree } = require('../render');
const { createPopupWithTolerance } = require('../popupUtils');
const { sendAutoEditIdea, stopAutoEditRequest } = require('./autoEditAi');
const { createTxtfile } = require('./txtfilelize');

/**
 * showAutoEdit(projectRoot, relativePath = '')
 *
 * Presents a modal that:
 * - Shows the expected "file_path + ```code block``` format where the FIRST line inside the code block
 *   is a comment containing the path (supports many comment syntaxes).
 * - For .txt files only: also supports the older "path line before fence" format (filepath.txt then ```...```)
 * - Provides a "Prompt" button which copies the recommended GPT prompt to the clipboard.
 * - Loads the user's clipboard into a large textarea.
 * - [Preview] builds an ASCII project tree of the files that would be added and shows an editable file path + content view.
 * - [Auto edit] writes files to disk (with confirmation if overwriting).
 *
 * UI details:
 * - Buttons use 10% rounded corners and light backgrounds; text is full black.
 * - Preview window contains a close button bottom-right and, for file content view:
 *     - file path is an editable text input
 *     - shows up to 2 annotation lines (plain text) above the editable code
 *     - allows editing the code and enables a red "Save" button and gray "Cancel" at the bottom-right
 *     - "Save" will update the code block (and inline path or the preceding .txt path) in the main textarea (and then hide itself), but will not close preview
 *
 * Additional features (added):
 * - Auto-detect whether a parsed path appears to be a full (absolute) path or a relative path (handles macOS ~/ and /Users/ prefixes, Windows drives, UNC).
 * - In preview, user can toggle each file between "Relative" and "Full path" mode. That controls what is shown/edited in the path input and what string will be written back into the code block when saving.
 * - Tilde expansion (~) is supported when interpreting full paths for macOS compatibility.
 *
 * Changes implemented:
 * - Preview no longer exposes per-file "Relative/Full" toggles. The preview is simplified and shows the full code block (including the path comment or path-before-fence) in an editable code-block textarea.
 * - The main window now exposes a global path-detection control: Auto (default), Force Relative, Force Full.
 *   * Auto counts whether the first line comment in each code block looks like a full path or a relative path and chooses the mode by majority.
 *   * The UI shows counts of how many paths looked relative vs full and which mode is being applied.
 * - When previewing / writing files, the selected detection behavior is applied uniformly to determine how the raw paths are interpreted.
 * - The preview shows the full modified code block (fence + path line/comment + code) for each file, editable. Saving parses that block and updates the main textarea accordingly.
 *
 * Additional: single snapshot backup & un-edit support (one snapshot only).
 */

async function showAutoEdit(projectRoot, relativePath = '') {
  // Guard
  if (!projectRoot) {
    console.error('showAutoEdit requires projectRoot');
    return;
  }

  // Auto-edit snapshot locations (one snapshot only)
  const AUTOEDIT_DIR = path.join(projectRoot, '.auaci', 'autoedit');
  const AUTOEDIT_META = path.join(AUTOEDIT_DIR, 'last.json');
  const AUTOEDIT_BACKUPS = path.join(AUTOEDIT_DIR, 'backups');
  const AUTOEDIT_AI_HISTORY = path.join(AUTOEDIT_DIR, 'ai_chat_history.json');
  const AUTOEDIT_AI_SESSIONS = path.join(AUTOEDIT_DIR, 'ai_chat_sessions.json');

  // History helpers for Auto Edit AI chat (up to 20 most recent entries per project)
  async function loadAutoEditAiHistory() {
    try {
      const raw = await fs.readFile(AUTOEDIT_AI_HISTORY, 'utf8');
      const data = JSON.parse(raw);
      if (Array.isArray(data)) return data;
      if (data && Array.isArray(data.entries)) return data.entries;
      return [];
    } catch (err) {
      if (err && err.code === 'ENOENT') return [];
      console.error('Failed to load Auto Edit AI history:', err);
      return [];
    }
  }

  async function appendAutoEditAiHistoryEntry(entry) {
    try {
      await fs.mkdir(AUTOEDIT_DIR, { recursive: true });
      const existing = await loadAutoEditAiHistory();
      const updated = [entry].concat(existing || []);
      const trimmed = updated.slice(0, 20);
      const payload = { version: 1, entries: trimmed };
      await fs.writeFile(AUTOEDIT_AI_HISTORY, JSON.stringify(payload, null, 2), 'utf8');
    } catch (err) {
      console.error('Failed to save Auto Edit AI history:', err);
    }
  }

  // Conversation-style history sessions for Auto Edit AI
  async function loadAutoEditSessions() {
    try {
      const raw = await fs.readFile(AUTOEDIT_AI_SESSIONS, 'utf8');
      const data = JSON.parse(raw);
      if (Array.isArray(data)) return data;
      if (data && Array.isArray(data.sessions)) return data.sessions;
      return [];
    } catch (err) {
      if (err && err.code === 'ENOENT') return [];
      console.error('Failed to load Auto Edit AI sessions:', err);
      return [];
    }
  }

  async function saveAutoEditSessions(sessions) {
    try {
      await fs.mkdir(AUTOEDIT_DIR, { recursive: true });
      const payload = { version: 1, sessions: Array.isArray(sessions) ? sessions : [] };
      await fs.writeFile(AUTOEDIT_AI_SESSIONS, JSON.stringify(payload, null, 2), 'utf8');
    } catch (err) {
      console.error('Failed to save Auto Edit AI sessions:', err);
    }
  }

  async function persistAutoEditSession(session) {
    if (!session || !session.id) return;
    try {
      const existing = await loadAutoEditSessions();
      const idx = existing.findIndex(s => s && s.id === session.id);
      if (idx >= 0) {
        existing[idx] = session;
      } else {
        existing.unshift(session);
      }
      await saveAutoEditSessions(existing);
    } catch (err) {
      console.error('Failed to persist Auto Edit AI session:', err);
    }
  }

  async function getAutoEditSessionById(sessionId) {
    if (!sessionId) return null;
    try {
      const all = await loadAutoEditSessions();
      return all.find(s => s && s.id === sessionId) || null;
    } catch (err) {
      console.error('Failed to load Auto Edit AI session by id:', err);
      return null;
    }
  }

  async function createHistorySessionFromExchange({ projectRoot: projectRootParam, baseUrl, model, idea, attachments, responseText, stopped }) {
    try {
      const now = new Date();
      const all = await loadAutoEditSessions();
      const id = 'sess-' + now.getTime().toString(36);
      const createdAt = now.toISOString();
      const firstLine = (idea || '').split(/\r?\n/)[0].trim();
      const defaultName = firstLine || `Session ${all.length + 1}`;
      const safeName = defaultName.slice(0, 120);

      const messageId = 'msg-' + now.getTime().toString(36);
      const message = {
        id: messageId,
        createdAt,
        idea: idea || '',
        responseText: responseText || '',
        attachments: (attachments || []).map(a => ({
          path: a.path || '',
          name: a.name || '',
          content: a.content != null ? String(a.content) : '',
          displayPath: a.displayPath || ''
        })),
        model: model || '',
        baseUrl: baseUrl || '',
        stopped: !!stopped
      };

      const session = {
        id,
        name: safeName,
        createdAt,
        updatedAt: createdAt,
        projectRoot: projectRootParam || projectRoot,
        baseUrl: baseUrl || '',
        defaultModel: model || '',
        messages: [message]
      };

      all.unshift(session);
      await saveAutoEditSessions(all);
      return session;
    } catch (err) {
      console.error('Failed to create Auto Edit AI history session:', err);
      return null;
    }
  }

  // Helper: normalize folder-relative input (keep .. if present, remove leading/trailing slashes & leading ./)
  function normalizeFolderRel(raw) {
    let v = String(raw || '').trim();
    if (!v || v === '.' || v === './') return '';
    // remove leading './' sequences but keep possible '../'
    v = v.replace(/^(\.\/)+/, '');
    // remove leading slashes
    v = v.replace(/^[/\\]+/, '');
    // remove trailing slashes
    v = v.replace(/[/\\]+$/, '');
    return v;
  }

  // Expand a leading tilde to the user's home directory.
  function expandTilde(candidate) {
    if (!candidate || typeof candidate !== 'string') return candidate;
    // only expand a leading ~ followed by slash/backslash or nothing
    return candidate.replace(/^~(?=$|[\\/])/, os.homedir());
  }

  // Slight detection for "looks like an absolute / full path".
  // Recognizes:
  //  - leading '/' or '\'  (POSIX)
  //  - leading '~' (home shorthand)
  //  - Windows drive like 'C:\' or 'C:/'
  //  - UNC paths starting with '\\'
  function isLikelyAbsolutePathString(s) {
    if (!s || typeof s !== 'string') return false;
    const t = s.trim();
    if (t === '') return false;
    if (t.startsWith('~')) return true;
    if (t.startsWith('/') || t.startsWith('\\')) return true;
    if (/^[A-Za-z]:[\\/]/.test(t)) return true;
    if (/^\\\\/.test(t)) return true;
    return false;
  }

  // combine a raw path (from code block) with a folder-relative value for display / write targets
  function combineRawWithFolder(rawPath, folderRel) {
    const raw = String(rawPath || '').trim();
    const folder = String(folderRel || '').trim();
    if (!folder || folder === '') return raw;
    // If raw is absolute (or tilde-style), don't combine
    if (raw.startsWith('~') || path.isAbsolute(raw) || isLikelyAbsolutePathString(raw)) return raw;
    // strip leading slashes from raw to avoid accidental absolute
    const rawNoLead = raw.replace(/^[/\\]+/, '');
    // Use posix join to keep forward slashes in the combined path (works with path.resolve later)
    const joined = path.posix.join(folder.replace(/\\/g, '/'), rawNoLead);
    return joined;
  }

  // Resolve a given raw path into an absolute path under projectRoot (safely).
  // Returns { absPath, relPath } where relPath is the path relative to projectRoot (no leading slash).
  function safeResolveUnderProject(projectRootParam, raw) {
    const projectAbs = path.resolve(projectRootParam);
    let candidate = String(raw || '').trim();

    // Remove surrounding quotes if present
    if ((candidate.startsWith('"') && candidate.endsWith('"')) || (candidate.startsWith("'") && candidate.endsWith("'"))) {
      candidate = candidate.slice(1, -1);
    }

    // Expand tilde to home if present at start
    if (candidate.startsWith('~')) {
      candidate = expandTilde(candidate);
    }

    // If absolute, attempt to map relative to project; otherwise resolve from project
    let abs;
    if (path.isAbsolute(candidate)) {
      abs = path.resolve(candidate);
    } else {
      abs = path.resolve(projectAbs, candidate);
    }

    // Ensure the resolved path is inside the project root. If not, fallback to basename inside project root.
    const rel = path.relative(projectAbs, abs);
    if (!rel || rel === '' || rel.startsWith('..') || path.isAbsolute(rel)) {
      // fallback to basename (place file in project root)
      const bname = path.basename(candidate) || 'newfile.txt';
      abs = path.resolve(projectAbs, bname);
      return { absPath: abs, relPath: path.relative(projectAbs, abs) };
    }

    return { absPath: abs, relPath: rel };
  }

  //
  // Snapshot / backup helpers for "auto edit" (single snapshot)
  // NOTE: This must NOT delete AI chat history or sessions, which also live under AUTOEDIT_DIR.
  //       We only clear snapshot-related data: backups + snapshot metadata.
  async function clearAutoEditSnapshot() {
    // Best-effort: remove the backups directory and snapshot metadata file.
    // Keep AUTOEDIT_DIR itself so ai_chat_history.json and ai_chat_sessions.json remain.
    try {
      // Remove backups directory (if it exists)
      try {
        if (typeof fs.rm === 'function') {
          await fs.rm(AUTOEDIT_BACKUPS, { recursive: true, force: true });
        } else {
          await fs.rmdir(AUTOEDIT_BACKUPS, { recursive: true });
        }
      } catch (e) {
        // ignore (directory may not exist)
      }

      // Remove snapshot metadata file (if it exists)
      try {
        await fs.unlink(AUTOEDIT_META);
      } catch (e) {
        // ignore (file may not exist)
      }
    } catch (e) {
      // ignore
    }
  }

  async function ensureAutoEditBackupDirs() {
    try {
      await fs.mkdir(AUTOEDIT_BACKUPS, { recursive: true });
    } catch (e) {
      // ignore
    }
  }

  async function copySrcToBackup(srcAbs, relPath) {
    const dest = path.join(AUTOEDIT_BACKUPS, relPath);
    await fs.mkdir(path.dirname(dest), { recursive: true });
    try {
      if (typeof fs.copyFile === 'function') {
        await fs.copyFile(srcAbs, dest);
      } else {
        const data = await fs.readFile(srcAbs);
        await fs.writeFile(dest, data);
      }
    } catch (err) {
      // bubble up
      throw err;
    }
  }

  async function copyDirToBackup(srcDir, relDir) {
    // ensure directory exists in backup
    await fs.mkdir(path.join(AUTOEDIT_BACKUPS, relDir), { recursive: true });
    const entries = await fs.readdir(srcDir, { withFileTypes: true });
    for (const entry of entries) {
      const name = entry.name;
      // skip some obvious metadata to reduce backup noise (optional)
      if (name === '.git' || name === '.DS_Store') continue;
      const childSrc = path.join(srcDir, name);
      const childRel = path.join(relDir, name);
      try {
        const st = await fs.stat(childSrc);
        if (st.isDirectory()) {
          await copyDirToBackup(childSrc, childRel);
        } else if (st.isFile()) {
          await copySrcToBackup(childSrc, childRel);
        }
      } catch (err) {
        // best-effort: log and continue
        console.warn('copyDirToBackup entry failed', childSrc, err && err.message);
      }
    }
  }

  async function backupPathRecursively(absPath, relPath) {
    try {
      const st = await fs.stat(absPath);
      if (st.isFile()) {
        await copySrcToBackup(absPath, relPath);
      } else if (st.isDirectory()) {
        await copyDirToBackup(absPath, relPath);
      }
    } catch (err) {
      console.warn('backupPathRecursively failed for', absPath, err && err.message);
      throw err;
    }
  }

  async function loadAutoEditMeta() {
    try {
      const raw = await fs.readFile(AUTOEDIT_META, 'utf8');
      return JSON.parse(raw);
    } catch (err) {
      return null;
    }
  }

  async function copyBackupDirToProject(srcDir, destDir) {
    await fs.mkdir(destDir, { recursive: true });
    const entries = await fs.readdir(srcDir, { withFileTypes: true });
    for (const entry of entries) {
      const name = entry.name;
      const src = path.join(srcDir, name);
      const dst = path.join(destDir, name);
      try {
        const st = await fs.stat(src);
        if (st.isDirectory()) {
          await copyBackupDirToProject(src, dst);
        } else if (st.isFile()) {
          await fs.mkdir(path.dirname(dst), { recursive: true });
          if (typeof fs.copyFile === 'function') {
            await fs.copyFile(src, dst);
          } else {
            const data = await fs.readFile(src);
            await fs.writeFile(dst, data);
          }
        }
      } catch (err) {
        console.warn('copyBackupDirToProject failed for', src, err && err.message);
      }
    }
  }

  // Build overlay
  const overlay = document.createElement('div');
  Object.assign(overlay.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    width: '100vw',
    height: '100vh',
    background: 'rgba(0,0,0,0.45)',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: '10000'
  });

  const card = document.createElement('div');
  Object.assign(card.style, {
    width: 'min(920px, 94vw)',
    maxHeight: '90vh',
    overflow: 'auto',
    background: '#fff',
    padding: '16px',
    borderRadius: '8px',
    boxShadow: '0 10px 30px rgba(0,0,0,0.24)',
    boxSizing: 'border-box',
    position: 'relative'
  });

  // Header row
  const headerRow = document.createElement('div');
  Object.assign(headerRow.style, {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: '10px',
    marginBottom: '8px'
  });

  const headerLeft = document.createElement('div');
  const headerTitle = document.createElement('div');
  headerTitle.textContent = 'File format expected by Auto edit';
  Object.assign(headerTitle.style, { fontSize: '15px', fontWeight: '700', color: '#111' });

  const headerDesc = document.createElement('div');
  headerDesc.innerHTML = 'Copy the prompt to get the format the gpt should respond in.';
  Object.assign(headerDesc.style, { fontSize: '12px', color: '#333', marginTop: '8px' });

  headerLeft.appendChild(headerTitle);
  headerLeft.appendChild(headerDesc);

  const headerRight = document.createElement('div');

  // Un-edit button (hidden until we detect a saved snapshot)
  const uneditBtn = document.createElement('button');
  uneditBtn.textContent = 'Un-edit';
  Object.assign(uneditBtn.style, {
    padding: '8px 10px',
    borderRadius: '10%',
    border: '1px solid #d6dde6',
    background: '#fff',
    color: '#000',
    cursor: 'pointer',
    display: 'none', // shown only if snapshot present
    marginRight: '8px'
  });

  const closeBtn = document.createElement('button');
  closeBtn.textContent = 'Close';
  Object.assign(closeBtn.style, {
    padding: '8px 10px',
    borderRadius: '10%', // 10% rounded edges
    border: '1px solid #ddd',
    background: '#f7f9fb',
    color: '#000',
    cursor: 'pointer'
  });

  headerRight.appendChild(uneditBtn);
  headerRight.appendChild(closeBtn);

  headerRow.appendChild(headerLeft);
  headerRow.appendChild(headerRight);

  card.appendChild(headerRow);

  // Prompt copy area: show a large "Prompt" button.
  const promptContainer = document.createElement('div');
  Object.assign(promptContainer.style, {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '12px',
    marginBottom: '10px',
    padding: '8px',
    borderRadius: '6px',
    background: '#fbfdff',
    border: '1px solid #eef6ff'
  });

  const promptLabel = document.createElement('div');
  promptLabel.textContent = 'Copy GPT prompt (click button)';
  Object.assign(promptLabel.style, {
    fontSize: '15px',
    fontWeight: '600',
    color: '#111'
  });

  const promptBtn = document.createElement('button');
  promptBtn.textContent = 'Prompt';
  Object.assign(promptBtn.style, {
    padding: '12px 16px',
    borderRadius: '10%', // 10% rounded edges
    border: '1px solid #d6dde6',
    background: '#eef7ff',
    color: '#000',
    cursor: 'pointer',
    fontSize: '15px',
    boxSizing: 'border-box'
  });

  // Updated canonical prompt to copy (triple backticks included).
  const promptText = [
    'For each file, include the full modified/newly-created file contents in a code block (dont show code for unmodified code files). The first line of a code block must be a single-line comment that is the file path. Example:',
    '```javascript',
    '// src/main/app.js',
    'console.log("hello")',
    '```',
    '',
  ].join('\n');

  // Copy feedback handling
  let promptCopyTimer = null;
  promptBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    try {
      clipboard.writeText(promptText);
      // Visual feedback: change button text and show a short status
      const prevText = promptBtn.textContent;
      promptBtn.textContent = 'Copied ✓';
      promptBtn.disabled = true;
      statusDiv.textContent = 'Prompt copied to clipboard';
      if (promptCopyTimer) clearTimeout(promptCopyTimer);
      promptCopyTimer = setTimeout(() => {
        promptBtn.textContent = prevText;
        promptBtn.disabled = false;
        statusDiv.textContent = '';
        promptCopyTimer = null;
      }, 1400);
    } catch (err) {
      console.error('Failed to copy prompt:', err);
      statusDiv.textContent = 'Failed to copy prompt.';
      setTimeout(() => {
        statusDiv.textContent = '';
      }, 1800);
    }
  });

  promptContainer.appendChild(promptLabel);
  promptContainer.appendChild(promptBtn);
  card.appendChild(promptContainer);

  //
  // NEW: Target folder input area (below promptContainer)
  //
  const folderContainer = document.createElement('div');
  Object.assign(folderContainer.style, {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
    marginBottom: '10px'
  });

  const folderLabelRow = document.createElement('div');
  folderLabelRow.textContent = 'Target folder (relative to project root)';
  Object.assign(folderLabelRow.style, { fontSize: '13px', fontWeight: '600', color: '#111' });
  folderContainer.appendChild(folderLabelRow);

  const folderRow = document.createElement('div');
  Object.assign(folderRow.style, { display: 'flex', gap: '8px', alignItems: 'center' });

  const folderInput = document.createElement('input');
  folderInput.type = 'text';
  const initialFolder = normalizeFolderRel(relativePath || '');
  folderInput.placeholder = 'e.g. src/components (leave blank for project root)';
  folderInput.value = initialFolder;
  Object.assign(folderInput.style, {
    width: '420px',
    maxWidth: '94vw',
    padding: '8px',
    fontFamily: 'monospace',
    fontSize: '13px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    boxSizing: 'border-box',
    background: '#fff',
    color: '#000'
  });
  folderRow.appendChild(folderInput);

  const folderStatus = document.createElement('div');
  folderStatus.style.fontSize = '12px';
  folderStatus.style.color = '#666';
  folderStatus.style.flex = '1';
  folderRow.appendChild(folderStatus);

  folderContainer.appendChild(folderRow);

  // message when folder does not exist
  const folderMessage = document.createElement('div');
  folderMessage.style.fontSize = '12px';
  folderMessage.style.color = '#a06400'; // darker yellowish
  folderMessage.style.minHeight = '18px';
  folderMessage.textContent = ''; // set by validation
  folderContainer.appendChild(folderMessage);

  card.appendChild(folderContainer);

  //
  // NEW: Path detection control (Auto / Force Relative / Force Full)
  //
  const detectionContainer = document.createElement('div');
  Object.assign(detectionContainer.style, {
    display: 'flex',
    gap: '10px',
    alignItems: 'center',
    marginBottom: '10px'
  });

  const detectionLabel = document.createElement('div');
  detectionLabel.textContent = 'Path detection:';
  Object.assign(detectionLabel.style, { fontSize: '13px', fontWeight: '600', color: '#111' });

  const detectionControl = document.createElement('div');
  Object.assign(detectionControl.style, {
    display: 'inline-flex',
    borderRadius: '6px',
    overflow: 'hidden',
    border: '1px solid #e6eaf0'
  });

  const detAutoBtn = document.createElement('button');
  detAutoBtn.textContent = 'Auto';
  Object.assign(detAutoBtn.style, {
    padding: '6px 8px',
    border: 'none',
    background: '#eef7ff', // default selected
    color: '#000',
    cursor: 'pointer',
    fontSize: '12px'
  });

  const detRelBtn = document.createElement('button');
  detRelBtn.textContent = 'Force relative';
  Object.assign(detRelBtn.style, {
    padding: '6px 8px',
    border: 'none',
    background: '#fff',
    color: '#000',
    cursor: 'pointer',
    fontSize: '12px'
  });

  const detFullBtn = document.createElement('button');
  detFullBtn.textContent = 'Force full';
  Object.assign(detFullBtn.style, {
    padding: '6px 8px',
    border: 'none',
    background: '#fff',
    color: '#000',
    cursor: 'pointer',
    fontSize: '12px'
  });

  detectionControl.appendChild(detAutoBtn);
  detectionControl.appendChild(detRelBtn);
  detectionControl.appendChild(detFullBtn);

  const detectionSummary = document.createElement('div');
  detectionSummary.style.fontSize = '12px';
  detectionSummary.style.color = '#333';
  detectionSummary.textContent = 'Detected: 0 relative, 0 full. Using: Auto';

  detectionContainer.appendChild(detectionLabel);
  detectionContainer.appendChild(detectionControl);
  detectionContainer.appendChild(detectionSummary);

  card.appendChild(detectionContainer);

  // Large fixed-size textarea (filled with clipboard)
  const textarea = document.createElement('textarea');
  textarea.placeholder = 'Paste files here (format: code fence where the FIRST line inside the code block is a comment containing the path)...';
  Object.assign(textarea.style, {
    width: '840px',
    maxWidth: '94vw',
    height: '360px',
    minHeight: '360px',
    fontFamily: 'monospace',
    fontSize: '13px',
    padding: '10px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    boxSizing: 'border-box',
    resize: 'vertical',
    background: '#fff',
    color: '#000'
  });

  try {
    const clipText = clipboard.readText();
    textarea.value = clipText || '';
  } catch (err) {
    console.error('Failed to read clipboard for auto-edit:', err);
  }

  card.appendChild(textarea);

  const statusDiv = document.createElement('div');
  Object.assign(statusDiv.style, { marginTop: '8px', minHeight: '18px', color: '#333', fontSize: '13px' });
  card.appendChild(statusDiv);

  // Buttons row (Preview on left, Auto edit on right)
  const buttonsRow = document.createElement('div');
  Object.assign(buttonsRow.style, {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: '8px',
    marginTop: '12px'
  });

  const historySessionsBtn = document.createElement('button');
  historySessionsBtn.textContent = 'History session';
  Object.assign(historySessionsBtn.style, {
    padding: '10px 12px',
    borderRadius: '10%',
    border: '1px solid #d6dde6',
    background: '#f7f9fb',
    color: '#000',
    cursor: 'pointer'
  });

  const aiChatBtn = document.createElement('button');
  aiChatBtn.textContent = 'AI Chat';
  Object.assign(aiChatBtn.style, {
    padding: '10px 12px',
    borderRadius: '10%',
    border: '1px solid #10b981',
    background: 'linear-gradient(180deg, #d1fae5, #a7f3d0)',
    color: '#000',
    cursor: 'pointer'
  });

  const previewBtn = document.createElement('button');
  previewBtn.textContent = 'Preview';
  Object.assign(previewBtn.style, {
    padding: '10px 12px',
    borderRadius: '10%',
    border: '1px solid #d6dde6',
    background: '#eef7ff',
    color: '#000',
    cursor: 'pointer'
  });

  const autoBtn = document.createElement('button');
  autoBtn.textContent = 'Auto edit';
  Object.assign(autoBtn.style, {
    padding: '10px 12px',
    borderRadius: '10%',
    border: '1px solid #0b66ff',
    background: 'linear-gradient(180deg, #dff0ff, #d6ecff)',
    color: '#000',
    cursor: 'pointer'
  });

  buttonsRow.appendChild(historySessionsBtn);
  buttonsRow.appendChild(aiChatBtn);
  buttonsRow.appendChild(previewBtn);
  buttonsRow.appendChild(autoBtn);
  card.appendChild(buttonsRow);

  overlay.appendChild(card);
  document.body.appendChild(overlay);

  // Close handlers for main overlay
  function closeOverlay() {
    if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
    window.removeEventListener('keydown', onKeyMain);
    if (promptCopyTimer) {
      clearTimeout(promptCopyTimer);
      promptCopyTimer = null;
    }
    if (cleanupTolerance) {
      cleanupTolerance();
    }
  }
  
  // Set up tolerance zone for the autoEdit popup
  const cleanupTolerance = createPopupWithTolerance(overlay, card, closeOverlay, 30);
  
  closeBtn.addEventListener('click', () => closeOverlay());
  function onKeyMain(e) {
    if (e.key === 'Escape') closeOverlay();
  }
  window.addEventListener('keydown', onKeyMain);

  //
  // AI Chat popup: compose message + attachments, send to local host, populate response into main textarea
  //
  historySessionsBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    openHistorySessionsWindow().catch(err => {
      console.error('Failed to open Auto Edit history sessions window:', err);
      statusDiv.textContent = 'Failed to open history sessions.';
    });
  });

  aiChatBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    openAiChatPopup();
  });

  function openAiChatPopup() {
    const chatOv = document.createElement('div');
    Object.assign(chatOv.style, {
      position: 'fixed', top: '0', left: '0', width: '100vw', height: '100vh',
      background: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: '12000'
    });

    const chatCard = document.createElement('div');
    Object.assign(chatCard.style, {
      width: 'min(880px, 94vw)', maxHeight: '88vh', overflow: 'auto', background: '#fff', padding: '14px', borderRadius: '8px', boxShadow: '0 10px 30px rgba(0,0,0,0.25)'
    });

    const title = document.createElement('div');
    title.textContent = 'AI Chat';
    Object.assign(title.style, { fontSize: '16px', fontWeight: '700', marginBottom: '8px', color: '#111' });

    // Connection row: base URL + model
    const connRow = document.createElement('div');
    Object.assign(connRow.style, { display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '8px' });

    const baseLabel = document.createElement('div');
    baseLabel.textContent = 'Base URL';
    Object.assign(baseLabel.style, { fontSize: '12px', width: '64px' });

    const baseInput = document.createElement('input');
    baseInput.type = 'text';
    baseInput.placeholder = 'http://localhost:8129/chat';
    baseInput.value = 'http://localhost:8129/chat';
    Object.assign(baseInput.style, { flex: '1', padding: '6px 8px', border: '1px solid #e6eaf0', borderRadius: '6px', fontFamily: 'monospace', fontSize: '12px' });

    const modelLabel = document.createElement('div');
    modelLabel.textContent = 'Model';
    Object.assign(modelLabel.style, { fontSize: '12px' });

    const modelInput = document.createElement('input');
    modelInput.type = 'text';
    modelInput.value = 'gpt-5.1-codex';
    Object.assign(modelInput.style, { width: '140px', padding: '6px 8px', border: '1px solid #e6eaf0', borderRadius: '6px', fontFamily: 'monospace', fontSize: '12px' });

    connRow.appendChild(baseLabel);
    connRow.appendChild(baseInput);
    connRow.appendChild(modelLabel);
    connRow.appendChild(modelInput);

    // Idea textarea
    const ideaLabel = document.createElement('div');
    ideaLabel.textContent = 'Your idea';
    Object.assign(ideaLabel.style, { fontSize: '13px', fontWeight: '600', marginTop: '6px', marginBottom: '4px' });

    const ideaInput = document.createElement('textarea');
    ideaInput.placeholder = 'Describe your idea/request for changes...';
    Object.assign(ideaInput.style, { width: '100%', height: '140px', fontFamily: 'monospace', fontSize: '13px', padding: '8px', border: '1px solid #e6eaf0', borderRadius: '6px', resize: 'vertical', background: '#fff', color: '#000' });

    // Attachments section
    const attachHeaderRow = document.createElement('div');
    Object.assign(attachHeaderRow.style, {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginTop: '10px',
      marginBottom: '4px',
      gap: '8px'
    });

    const attachHeader = document.createElement('div');
    attachHeader.textContent = 'Attachments';
    Object.assign(attachHeader.style, { fontSize: '13px', fontWeight: '600' });

    const historyBtn = document.createElement('button');
    historyBtn.textContent = 'Retrieve history';
    Object.assign(historyBtn.style, {
      padding: '4px 8px',
      borderRadius: '8px',
      border: '1px solid #d6dde6',
      background: '#f7f9fb',
      cursor: 'pointer',
      color: '#000',
      fontSize: '11px'
    });

    attachHeaderRow.appendChild(attachHeader);
    attachHeaderRow.appendChild(historyBtn);

    const attachRow = document.createElement('div');
    Object.assign(attachRow.style, { display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '6px' });

    const attachPathInput = document.createElement('input');
    attachPathInput.type = 'text';
    attachPathInput.placeholder = 'File path (full or relative to project root)';
    Object.assign(attachPathInput.style, { flex: '1', padding: '6px 8px', borderRadius: '6px', border: '1px solid #e6eaf0', fontFamily: 'monospace', fontSize: '12px' });

    const pathModeSelect = document.createElement('select');
    Object.assign(pathModeSelect.style, {
      padding: '6px 8px',
      borderRadius: '6px',
      border: '1px solid #e6eaf0',
      fontSize: '12px',
      background: '#fff',
      color: '#000'
    });
    const pathModeFull = document.createElement('option');
    pathModeFull.value = 'full';
    pathModeFull.textContent = 'Full path';
    const pathModeRelative = document.createElement('option');
    pathModeRelative.value = 'relative';
    pathModeRelative.textContent = 'Relative to project root';
    pathModeSelect.appendChild(pathModeFull);
    pathModeSelect.appendChild(pathModeRelative);
    pathModeSelect.value = 'full';

    const attachBtn = document.createElement('button');
    attachBtn.textContent = 'Attach file';
    Object.assign(attachBtn.style, { padding: '6px 8px', borderRadius: '8px', border: '1px solid #d6dde6', background: '#f7f9fb', cursor: 'pointer', color: '#000' });

    attachRow.appendChild(attachPathInput);
    attachRow.appendChild(pathModeSelect);
    attachRow.appendChild(attachBtn);

    const attachList = document.createElement('div');
    Object.assign(attachList.style, { border: '1px solid #eef2f6', borderRadius: '6px', padding: '8px', background: '#fafafa', maxHeight: '180px', overflowY: 'auto', fontFamily: 'monospace', fontSize: '12px' });

    const status = document.createElement('div');
    Object.assign(status.style, { marginTop: '8px', minHeight: '18px', fontSize: '12px', color: '#333' });

    // Live response area
    const liveLabel = document.createElement('div');
    liveLabel.textContent = 'Response (live)';
    Object.assign(liveLabel.style, { fontSize: '12px', fontWeight: '600', marginTop: '6px', marginBottom: '4px' });

    const liveOutput = document.createElement('textarea');
    Object.assign(liveOutput.style, { width: '100%', height: '160px', fontFamily: 'monospace', fontSize: '12px', padding: '6px 8px', border: '1px solid #e6eaf0', borderRadius: '6px', resize: 'vertical', background: '#fff', color: '#000' });
    liveOutput.placeholder = 'Streaming response will appear here...';
    liveOutput.readOnly = true;

    // Throttled updater to avoid re-rendering on every small chunk
    const updateLiveOutput = createThrottledTextSetter(liveOutput, 80);

    // Bottom row: Cancel / Send
    const btnRow = document.createElement('div');
    Object.assign(btnRow.style, { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '10px' });

    const timers = document.createElement('div');
    Object.assign(timers.style, { display: 'flex', gap: '12px', fontSize: '12px', color: '#444' });
    const waitTimerEl = document.createElement('div');
    waitTimerEl.textContent = 'Waiting: 0s';
    const respTimerEl = document.createElement('div');
    respTimerEl.textContent = 'Responding: 0s';
    timers.appendChild(waitTimerEl);
    timers.appendChild(respTimerEl);

    const rightBtns = document.createElement('div');
    Object.assign(rightBtns.style, { display: 'flex', gap: '8px' });

    const stopBtn = document.createElement('button');
    stopBtn.textContent = 'Stop';
    Object.assign(stopBtn.style, { padding: '8px 12px', borderRadius: '8px', border: '1px solid #f87171', background: 'linear-gradient(180deg, #ffe4e6, #fecaca)', cursor: 'pointer', color: '#000' });
    stopBtn.disabled = true;

    const cancelChatBtn = document.createElement('button');
    cancelChatBtn.textContent = 'Close';
    Object.assign(cancelChatBtn.style, { padding: '8px 12px', borderRadius: '8px', border: '1px solid #ddd', background: '#f7f9fb', cursor: 'pointer', color: '#000' });

    const sendBtn = document.createElement('button');
    sendBtn.textContent = 'Send';
    Object.assign(sendBtn.style, { padding: '8px 12px', borderRadius: '8px', border: '1px solid #0b66ff', background: 'linear-gradient(180deg, #dff0ff, #d6ecff)', cursor: 'pointer', color: '#000' });

    rightBtns.appendChild(stopBtn);
    rightBtns.appendChild(cancelChatBtn);
    rightBtns.appendChild(sendBtn);

    btnRow.appendChild(timers);
    btnRow.appendChild(rightBtns);

    chatCard.appendChild(title);
    chatCard.appendChild(connRow);
    chatCard.appendChild(ideaLabel);
    chatCard.appendChild(ideaInput);
    chatCard.appendChild(attachHeaderRow);
    chatCard.appendChild(attachRow);
    chatCard.appendChild(attachList);
    chatCard.appendChild(status);
    chatCard.appendChild(liveLabel);
    chatCard.appendChild(liveOutput);
    chatCard.appendChild(btnRow);

    chatOv.appendChild(chatCard);
    document.body.appendChild(chatOv);

    const cleanup = createPopupWithTolerance(chatOv, chatCard, () => closeChat(), 30);

    function closeChat() {
      try { if (chatOv.parentNode) chatOv.parentNode.removeChild(chatOv); } catch (_) {}
      try { window.removeEventListener('keydown', onKey); } catch (_) {}
      if (cleanup) cleanup();
    }
    function onKey(e) { if (e.key === 'Escape') closeChat(); }
    window.addEventListener('keydown', onKey);

    const attachments = [];

    function addAttachmentFromData(attachment) {
      if (!attachment) return;
      const fullPath = String(attachment.path || '').trim();
      const name = attachment.name || (fullPath ? path.basename(fullPath) : 'attachment');
      const content = String(attachment.content != null ? attachment.content : '');
      const displayPath = attachment.displayPath || fullPath;

      // Compute size (prefer explicit size if provided, otherwise estimate from content)
      let size = typeof attachment.size === 'number' ? attachment.size : undefined;
      if (typeof size !== 'number') {
        try {
          size = Buffer.byteLength(content || '', 'utf8');
        } catch (e) {
          size = 0;
        }
      }

      const record = { path: fullPath, name, content, displayPath, size };
      attachments.push(record);

      // Build row with left (name + path) and right (size + remove) aligned
      const left = document.createElement('div');
      const labelPath = displayPath || fullPath;
      left.textContent = labelPath ? `${name}  (${labelPath})` : name;
      Object.assign(left.style, { padding: '2px 0', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' });

      const removeBtn = document.createElement('button');
      removeBtn.textContent = 'Remove';
      Object.assign(removeBtn.style, {
        marginLeft: '8px',
        padding: '2px 6px',
        border: '1px solid #ddd',
        background: '#fff',
        borderRadius: '4px',
        cursor: 'pointer'
      });

      const sizeSpan = document.createElement('div');
      sizeSpan.textContent = formatBytes(size);
      Object.assign(sizeSpan.style, { marginLeft: '8px', fontSize: '12px', color: '#666', minWidth: '64px', textAlign: 'right' });

      const right = document.createElement('div');
      Object.assign(right.style, { display: 'flex', alignItems: 'center', gap: '8px' });
      right.appendChild(sizeSpan);
      right.appendChild(removeBtn);

      const rowWrap = document.createElement('div');
      Object.assign(rowWrap.style, { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px', padding: '2px 0' });
      rowWrap.appendChild(left);
      rowWrap.appendChild(right);
      attachList.appendChild(rowWrap);

      removeBtn.addEventListener('click', () => {
        const idx = attachments.indexOf(record);
        if (idx >= 0) attachments.splice(idx, 1);
        try { attachList.removeChild(rowWrap); } catch (_) {}
        status.textContent = `${attachments.length} file(s) attached.`;
      });

      status.textContent = `${attachments.length} file(s) attached.`;
    }

    async function preloadTxtlizedAttachment(explicit = false) {
      try {
        const tmpDir = path.join(projectRoot, '.auaci', 'tmp');
        let entries;
        try {
          entries = await fs.readdir(tmpDir, { withFileTypes: true });
        } catch (err) {
          if (err && err.code === 'ENOENT') {
            if (explicit) status.textContent = 'Txt file not found.';
            return false; // tmp dir not created yet
          }
          console.error('Failed to read .auaci/tmp for AI Chat preload:', err);
          if (explicit) status.textContent = 'Failed to read txtlized directory.';
          return false;
        }
        const candidates = [];
        for (const entry of entries) {
          if (!entry.isFile || !entry.isFile()) continue;
          const name = entry.name;
          if (!name.toLowerCase().endsWith('.txt')) continue;
          const fullPath = path.join(tmpDir, name);
          try {
            const st = await fs.stat(fullPath);
            const mtimeMs = Number(st.mtimeMs || (st.mtime && st.mtime.getTime()) || 0);
            candidates.push({ name, fullPath, mtimeMs });
          } catch (_) {
            // skip unreadable
          }
        }
        if (!candidates.length) {
          if (explicit) status.textContent = 'Txt file not found.';
          return false;
        }
        // Sort oldest to newest so attachments are added in a stable order
        candidates.sort((a, b) => a.mtimeMs - b.mtimeMs);

        let attachedAny = false;
        const existingPaths = new Set((attachments || []).map(a => a.path));

        for (const cand of candidates) {
          if (existingPaths.has(cand.fullPath)) continue;
          try {
            const data = await fs.readFile(cand.fullPath);
            const content = data.toString('utf8');
            let displayPath = cand.fullPath;
            try {
              const rel = path.relative(projectRoot, cand.fullPath).replace(/\\/g, '/');
              if (rel && !rel.startsWith('..')) displayPath = rel;
            } catch (_) {}
            addAttachmentFromData({ path: cand.fullPath, name: cand.name, content, displayPath });
            attachedAny = true;
          } catch (err) {
            console.error('Failed to read txtlized file for AI Chat preload:', err);
          }
        }

        if (!attachedAny) {
          if (explicit) status.textContent = 'Txt file not found.';
          return false;
        }

        if (explicit) status.textContent = `${attachments.length} file(s) attached.`;
        return true;
      } catch (err) {
        console.error('Failed to preload txtlized attachment for AI Chat:', err);
        if (explicit) status.textContent = 'Failed to load txt file.';
        return false;
      }
    }

    // Open a popup with recent Auto Edit AI history and allow reapplying an entry
    async function openHistoryPopup() {
      let entries = [];
      try {
        entries = await loadAutoEditAiHistory();
      } catch (err) {
        console.error('Failed to load Auto Edit AI history:', err);
        status.textContent = 'Failed to load history.';
        return;
      }
      if (!entries || !entries.length) {
        status.textContent = 'No Auto Edit AI history found yet.';
        return;
      }

      const hov = document.createElement('div');
      Object.assign(hov.style, {
        position: 'fixed',
        top: '0',
        left: '0',
        width: '100vw',
        height: '100vh',
        background: 'rgba(0,0,0,0.5)',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        zIndex: '13000'
      });

      const hCard = document.createElement('div');
      Object.assign(hCard.style, {
        width: 'min(820px, 94vw)',
        maxHeight: '80vh',
        background: '#fff',
        borderRadius: '8px',
        boxShadow: '0 10px 30px rgba(0,0,0,0.25)',
        padding: '12px',
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        gap: '8px'
      });

      const hTitle = document.createElement('div');
      hTitle.textContent = 'Auto Edit AI — recent history';
      Object.assign(hTitle.style, { fontSize: '14px', fontWeight: '700', color: '#111' });

      const hList = document.createElement('div');
      Object.assign(hList.style, {
        flex: '1',
        overflowY: 'auto',
        border: '1px solid #e5e7eb',
        borderRadius: '6px',
        padding: '6px',
        fontFamily: 'monospace',
        fontSize: '12px',
        background: '#f9fafb'
      });

      let selectedIndex = -1;
      function selectRow(row, idx) {
        const rows = hList.querySelectorAll('.ae-history-row');
        rows.forEach(r => { r.style.background = 'transparent'; });
        if (row) row.style.background = '#e0f2fe';
        selectedIndex = idx;
      }

      entries.forEach((entry, idx) => {
        const row = document.createElement('div');
        row.className = 'ae-history-row';
        Object.assign(row.style, {
          padding: '4px 6px',
          borderBottom: '1px solid #e5e7eb',
          cursor: 'pointer',
          display: 'flex',
          flexDirection: 'column',
          gap: '2px'
        });

        const ts = entry.timestamp || entry.ts || null;
        let tsLabel = '';
        if (ts) {
          try {
            const d = new Date(ts);
            if (!isNaN(d.getTime())) {
              tsLabel = d.toLocaleString();
            } else {
              tsLabel = String(ts);
            }
          } catch (_) {
            tsLabel = String(ts);
          }
        }

        const headerLine = document.createElement('div');
        const attCount = Array.isArray(entry.attachments) ? entry.attachments.length : 0;
        headerLine.textContent = `[${idx + 1}] ${tsLabel || 'Unknown time'}  —  ${attCount} file(s)`;
        Object.assign(headerLine.style, { fontWeight: '600', color: '#111' });

        const ideaPreviewLine = document.createElement('div');
        const ideaFirstLine = (entry.idea || '').split(/\r?\n/)[0];
        ideaPreviewLine.textContent = ideaFirstLine || '(no idea text)';
        Object.assign(ideaPreviewLine.style, { color: '#374151' });

        const respPreviewLine = document.createElement('div');
        const respPreview = (entry.responseText || '').replace(/\s+/g, ' ').slice(0, 140);
        respPreviewLine.textContent = respPreview || '';
        Object.assign(respPreviewLine.style, { color: '#6b7280', fontSize: '11px' });

        row.appendChild(headerLine);
        if (ideaPreviewLine.textContent) row.appendChild(ideaPreviewLine);
        if (respPreviewLine.textContent) row.appendChild(respPreviewLine);

        row.addEventListener('click', () => {
          selectRow(row, idx);
        });

        hList.appendChild(row);
      });

      const hStatus = document.createElement('div');
      Object.assign(hStatus.style, { minHeight: '18px', fontSize: '12px', color: '#333' });

      const hBtnRow = document.createElement('div');
      Object.assign(hBtnRow.style, {
        display: 'flex',
        justifyContent: 'flex-end',
        gap: '8px',
        marginTop: '6px'
      });

      const hCloseBtn = document.createElement('button');
      hCloseBtn.textContent = 'Close';
      Object.assign(hCloseBtn.style, {
        padding: '8px 10px',
        borderRadius: '6px',
        border: '1px solid #d6dde6',
        background: '#f7f9fb',
        cursor: 'pointer',
        color: '#000'
      });

      const hApplyBtn = document.createElement('button');
      hApplyBtn.textContent = 'Reapply';
      Object.assign(hApplyBtn.style, {
        padding: '8px 10px',
        borderRadius: '6px',
        border: '1px solid #0b66ff',
        background: 'linear-gradient(180deg, #dff0ff, #d6ecff)',
        cursor: 'pointer',
        color: '#000'
      });

      hBtnRow.appendChild(hCloseBtn);
      hBtnRow.appendChild(hApplyBtn);

      hCard.appendChild(hTitle);
      hCard.appendChild(hList);
      hCard.appendChild(hStatus);
      hCard.appendChild(hBtnRow);

      hov.appendChild(hCard);
      document.body.appendChild(hov);

      function applyEntry(entry) {
        try {
          ideaInput.value = entry.idea || '';
        } catch (_) {}
        try {
          attachments.splice(0, attachments.length);
          attachList.innerHTML = '';
        } catch (_) {}
        if (entry.attachments && Array.isArray(entry.attachments)) {
          for (const att of entry.attachments) {
            addAttachmentFromData(att);
          }
        }
        liveBuffer = entry.responseText || '';
        updateLiveOutput(liveBuffer);

        try {
          textarea.value = entry.responseText || '';
          statusDiv.textContent = 'Loaded AI history into editor.';
          updateDetectionSummary();
        } catch (_) {}
        status.textContent = 'History re-applied.';
      }

      let histCleanup = null;
      function closeHistory() {
        try { if (hov.parentNode) hov.parentNode.removeChild(hov); } catch (_) {}
        window.removeEventListener('keydown', onHistoryKey);
        if (histCleanup) histCleanup();
      }

      histCleanup = createPopupWithTolerance(hov, hCard, () => {
        closeHistory();
      }, 30);

      function onHistoryKey(ev) {
        if (ev.key === 'Escape') {
          closeHistory();
        }
      }
      window.addEventListener('keydown', onHistoryKey);

      hCloseBtn.addEventListener('click', () => {
        closeHistory();
      });

      hApplyBtn.addEventListener('click', () => {
        if (selectedIndex < 0 || selectedIndex >= entries.length) {
          hStatus.textContent = 'Please select a history entry to reapply.';
          return;
        }
        const entry = entries[selectedIndex];
        applyEntry(entry);
        closeHistory();
      });
    }

    // Wire history button to open the popup
    historyBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      openHistoryPopup().catch(err => {
        console.error('Failed to open Auto Edit AI history popup:', err);
        status.textContent = 'Failed to open history.';
      });
    });

    // Fire-and-forget preload of most recent txtlized file (if any)
    preloadTxtlizedAttachment().catch(() => {});

    let abortController = null;
    let currentRequestId = null;
    let liveBuffer = '';
    let stopped = false;
    let finalized = false;

    async function finalizeWith(text) {
      if (finalized) return;
      finalized = true;
      stopTimers();
      const responseText = text || '';
      try { textarea.value = responseText; } catch (_) {}
      try { statusDiv.textContent = 'Loaded AI response into editor.'; } catch (_) {}
      try { updateDetectionSummary(); } catch (_) {}

      let baseUrl = '';
      let model = '';
      let ideaText = '';
      let attachmentCopies = [];
      try {
        baseUrl = (baseInput.value || '').trim();
        model = (modelInput.value || '').trim() || 'gpt-5.1-codex';
        ideaText = (ideaInput.value || '').trim();
        attachmentCopies = attachments.map(a => ({
          path: a.path || '',
          name: a.name || '',
          content: a.content != null ? String(a.content) : '',
          displayPath: a.displayPath || ''
        }));
      } catch (err) {
        console.error('Failed to prepare Auto Edit AI history/common data:', err);
      }

      // Save to Auto Edit AI history (fire-and-forget)
      try {
        const historyEntry = {
          id: Date.now(),
          timestamp: new Date().toISOString(),
          projectRoot,
          baseUrl,
          model,
          idea: ideaText,
          attachments: attachmentCopies,
          responseText,
          stopped: !!stopped
        };
        (async () => {
          try {
            await appendAutoEditAiHistoryEntry(historyEntry);
          } catch (err) {
            console.error('Failed to append Auto Edit AI history entry:', err);
          }
        })();
      } catch (err) {
        console.error('Failed to prepare Auto Edit AI history entry:', err);
      }

      // Offer to create a reusable history session for this exchange
      try {
        const shouldCreate = await showConfirmationModal(
          'Create history session?',
          'Do you want to create a reusable history session from this AI Chat exchange?\n\n' +
          'You will be able to reopen it later from the "History session" button,\n' +
          'see all messages on the right side, and run Auto edit directly from any response.',
          []
        );
        if (shouldCreate) {
          try {
            await createHistorySessionFromExchange({
              projectRoot,
              baseUrl,
              model,
              idea: ideaText,
              attachments: attachmentCopies,
              responseText,
              stopped
            });
          } catch (err) {
            console.error('Failed to create Auto Edit AI history session from chat:', err);
          }
        }
      } catch (err) {
        console.error('Failed while offering to create Auto Edit AI history session:', err);
      }

      closeChat();
    }

    async function collectFilesRecursivelyForAttach(startPath, out) {
      try {
        const entries = await fs.readdir(startPath, { withFileTypes: true });
        for (const entry of entries) {
          const childPath = path.join(startPath, entry.name);
          try {
            if (entry.isDirectory && entry.isDirectory()) {
              await collectFilesRecursivelyForAttach(childPath, out);
            } else if (entry.isFile && entry.isFile()) {
              out.push(childPath);
            } else {
              const st = await fs.stat(childPath).catch(() => null);
              if (st && st.isDirectory()) {
                await collectFilesRecursivelyForAttach(childPath, out);
              } else if (st && st.isFile()) {
                out.push(childPath);
              }
            }
          } catch (_) {
            // ignore per-entry failures
          }
        }
      } catch (err) {
        console.error('Failed to traverse selected folder for attachments:', startPath, err);
      }
    }

    async function handleGenerateTxtfileFromEmptyPath() {
      try {
        // Open the txtfilelize screen targeting the current folder under the project root.
        await createTxtfile(projectRoot, relativePath || '');
      } catch (err) {
        console.error('Generate Txtfile failed:', err);
        status.textContent = 'Failed to open Txtfile generator.';
        return;
      }

      // After txtfilelize closes, try to attach the most recent txt file from .auaci/tmp.
      try {
        const attached = await preloadTxtlizedAttachment(true);
        if (!attached) {
          // preloadTxtlizedAttachment already set a helpful status message when explicit=true.
          return;
        }
      } catch (err) {
        console.error('Failed after Txtfile generation when attaching file:', err);
        status.textContent = 'Txt file not found.';
      }
    }

    async function openSystemFileSelectorAndAttach() {
      try {
        // Resolve a sensible default folder: prefer the Auto Edit folder input when available.
        let defaultFolderRel = '';
        try {
          defaultFolderRel = normalizeFolderRel(folderInput && folderInput.value ? folderInput.value : (relativePath || ''));
        } catch (_) {
          defaultFolderRel = normalizeFolderRel(relativePath || '');
        }
        const safeFolder = safeResolveUnderProject(projectRoot, defaultFolderRel || '.');

        const result = await ipcRenderer.invoke('autoedit-open-file-dialog', {
          defaultPath: safeFolder.absPath || projectRoot
        });

        if (!result || result.canceled || !Array.isArray(result.paths) || result.paths.length === 0) {
          status.textContent = 'Selection cancelled.';
          return;
        }

        const selectedPaths = result.paths;
        const filesToAttach = [];

        for (const p of selectedPaths) {
          try {
            const st = await fs.stat(p);
            if (st.isDirectory()) {
              await collectFilesRecursivelyForAttach(p, filesToAttach);
            } else if (st.isFile()) {
              filesToAttach.push(p);
            }
          } catch (err) {
            console.error('Failed to inspect selected path for attachments:', p, err);
          }
        }

        if (!filesToAttach.length) {
          status.textContent = 'No files found in selection.';
          return;
        }

        let attachedCount = 0;
        for (const filePath of filesToAttach) {
          try {
            const data = await fs.readFile(filePath);
            const content = data.toString('utf8');
            const name = path.basename(filePath);
            let displayPath = filePath;
            try {
              const rel = path.relative(projectRoot, filePath).replace(/\\/g, '/');
              if (rel && !rel.startsWith('..')) displayPath = rel;
            } catch (_) {}
            addAttachmentFromData({ path: filePath, name, content, displayPath });
            attachedCount++;
          } catch (err) {
            console.error('Failed to read selected attachment:', filePath, err);
          }
        }

        if (attachedCount) {
          attachPathInput.value = filesToAttach[0];
          status.textContent = `${attachments.length} file(s) attached.`;
        } else {
          status.textContent = 'Failed to read selected file(s).';
        }
      } catch (err) {
        console.error('System file selector failed:', err);
        status.textContent = 'Failed to open file selector.';
      }
    }

    function openEmptyPathOptions() {
      const ov = document.createElement('div');
      Object.assign(ov.style, {
        position: 'fixed',
        top: '0',
        left: '0',
        width: '100vw',
        height: '100vh',
        background: 'rgba(0,0,0,0.45)',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        zIndex: '13000'
      });

      const card = document.createElement('div');
      Object.assign(card.style, {
        background: '#fff',
        padding: '14px',
        borderRadius: '8px',
        boxShadow: '0 8px 24px rgba(0,0,0,0.18)',
        width: 'min(420px, 94vw)',
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        gap: '10px'
      });

      const title = document.createElement('div');
      title.textContent = 'Nothing is inside';
      Object.assign(title.style, { fontSize: '15px', fontWeight: '700', color: '#111' });

      const msg = document.createElement('div');
      msg.textContent = 'No file path was provided. Choose how you want to attach files:';
      Object.assign(msg.style, { fontSize: '13px', color: '#333' });

      const btnRow = document.createElement('div');
      Object.assign(btnRow.style, { display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '4px' });

      function closeOv() {
        try { if (ov.parentNode) ov.parentNode.removeChild(ov); } catch (_) {}
        window.removeEventListener('keydown', onKey);
        if (cleanup) cleanup();
      }

      const genBtn = document.createElement('button');
      genBtn.textContent = 'Generate Txtfile';
      Object.assign(genBtn.style, {
        padding: '8px 10px',
        borderRadius: '8px',
        border: '1px solid #0b66ff',
        background: 'linear-gradient(180deg, #dff0ff, #d6ecff)',
        cursor: 'pointer',
        color: '#000',
        fontSize: '13px'
      });
      genBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        closeOv();
        await handleGenerateTxtfileFromEmptyPath();
      });

      const selectBtn = document.createElement('button');
      selectBtn.textContent = 'Open macOS file select';
      Object.assign(selectBtn.style, {
        padding: '8px 10px',
        borderRadius: '8px',
        border: '1px solid #d6dde6',
        background: '#f7f9fb',
        cursor: 'pointer',
        color: '#000',
        fontSize: '13px'
      });
      selectBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        closeOv();
        await openSystemFileSelectorAndAttach();
      });

      const cancelBtn = document.createElement('button');
      cancelBtn.textContent = 'Cancel';
      Object.assign(cancelBtn.style, {
        padding: '8px 10px',
        borderRadius: '8px',
        border: '1px solid #d6dde6',
        background: '#fff',
        cursor: 'pointer',
        color: '#000',
        fontSize: '13px'
      });
      cancelBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        closeOv();
      });

      btnRow.appendChild(cancelBtn);
      btnRow.appendChild(selectBtn);
      btnRow.appendChild(genBtn);

      card.appendChild(title);
      card.appendChild(msg);
      card.appendChild(btnRow);

      ov.appendChild(card);
      document.body.appendChild(ov);

      const cleanup = createPopupWithTolerance(ov, card, closeOv, 30);
      function onKey(e) { if (e.key === 'Escape') closeOv(); }
      window.addEventListener('keydown', onKey);
    }

    attachBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      let inputPath = (attachPathInput.value || '').trim();
      if (!inputPath) {
        openEmptyPathOptions();
        return;
      }

      // Expand tilde and normalize to absolute path
      inputPath = expandTilde(inputPath);
      let fullPath = inputPath;
      try {
        if (!path.isAbsolute(fullPath)) {
          // Interpret non-absolute paths relative to the current project
          fullPath = path.resolve(projectRoot, fullPath);
        } else {
          fullPath = path.resolve(fullPath);
        }
      } catch (_) {
        // If resolution fails, keep original and let fs.readFile throw
      }
      try { fullPath = path.normalize(fullPath); } catch (_) {}

      // Compute a display path based on the selected mode (full vs relative)
      let displayPath = fullPath;
      if (pathModeSelect && pathModeSelect.value === 'relative') {
        try {
          const rel = path.relative(projectRoot, fullPath).replace(/\\/g, '/');
          if (rel && !rel.startsWith('..')) displayPath = rel;
        } catch (_) {}
      }

      // Show normalized full path in the input field
      attachPathInput.value = fullPath;

      try {
        const data = await fs.readFile(fullPath);
        const content = data.toString('utf8');
        const name = path.basename(fullPath);
        addAttachmentFromData({ path: fullPath, name, content, displayPath });
        status.textContent = `${attachments.length} file(s) attached.`;
      } catch (err) {
        console.error('Failed to read attachment:', err);
        status.textContent = 'Failed to read file. Check the path and permissions.';
      }
    });

    cancelChatBtn.addEventListener('click', () => closeChat());

    stopBtn.addEventListener('click', async () => {
      if (stopBtn.disabled) return;
      stopped = true;
      stopBtn.disabled = true;
      sendBtn.disabled = true;
      try {
        // ask server to stop if we have a request id
        if (currentRequestId) {
          const base = (baseInput.value || '').trim().replace(/\/$/, '');
          try {
            await stopAutoEditRequest({ url: base, requestId: currentRequestId });
          } catch (_) {}
        }
      } finally {
        try { if (abortController) abortController.abort(); } catch (_) {}
      }
      // finalize with what we have so far
      await finalizeWith(liveBuffer);
    });

    let waitTimer = null, respTimer = null;
    let waitStart = null, respStart = null;

    function startWaitTimer() {
      waitStart = Date.now();
      waitTimer = setInterval(() => {
        const secs = Math.floor((Date.now() - waitStart) / 1000);
        waitTimerEl.textContent = `Waiting: ${secs}s`;
      }, 1000);
    }
    function startRespTimer() {
      respStart = Date.now();
      respTimer = setInterval(() => {
        const secs = Math.floor((Date.now() - respStart) / 1000);
        respTimerEl.textContent = `Responding: ${secs}s`;
      }, 1000);
    }
    function stopTimers() {
      if (waitTimer) { clearInterval(waitTimer); waitTimer = null; }
      if (respTimer) { clearInterval(respTimer); respTimer = null; }
    }

    async function sendChat() {
      const url = (baseInput.value || '').trim().replace(/\/$/, '');
      const model = (modelInput.value || '').trim() || 'gpt-5.1-codex';
      const idea = (ideaInput.value || '').trim();
      if (!url) { status.textContent = 'Please set Base URL.'; return; }
      if (!idea) { status.textContent = 'Please enter your idea.'; return; }

      // Build message body as described
      const msgParts = [];
      msgParts.push(idea);
      msgParts.push('');
      msgParts.push('Files Attached:');
      if (!attachments.length) {
        msgParts.push('(none)');
      } else {
        for (const a of attachments) {
          msgParts.push(`Name: ${a.name}`);
          msgParts.push(`Content: ${a.content}`);
          msgParts.push('');
        }
      }
      const userMessage = msgParts.join('\n');

      // Final prompt the proxy expects in a single string
      const finalPrompt = [
        'Prompt:',
        promptText,
        '',
        'Message:',
        userMessage
      ].join('\n');

      // Begin timers and request
      startWaitTimer();
      status.textContent = 'Sending...';

      // prepare abort controller and enable Stop
      abortController = new AbortController();
      stopped = false; finalized = false; liveBuffer = ''; currentRequestId = null;
      stopBtn.disabled = false;
      sendBtn.disabled = true;

      let responseText = '';
      try {
        responseText = await sendAutoEditIdea({
          idea,
          attachments,
          systemPrompt: promptText,
          model,
          url,
          includeHistory: false,
          appId: 'auto_edit',
          sessionId: null,
          signal: abortController.signal,
          onRequestId: (rid) => { currentRequestId = rid || null; },
          onStart: () => { try { startRespTimer(); status.textContent = 'Receiving...'; } catch (_) {} },
          onChunk: (chunk) => {
            try {
              liveBuffer += String(chunk || '');
              updateLiveOutput(liveBuffer);
            } catch (_) {}
          },
        });
      } catch (err) {
        // If we stopped intentionally, finalize with what we have, otherwise show error
        if (stopped) { await finalizeWith(liveBuffer); return; }
        console.error('AI Chat request failed:', err);
        stopTimers();
        status.textContent = `Error: ${err && err.message ? err.message : String(err)}`;
        stopBtn.disabled = true;
        sendBtn.disabled = false;
        return;
      }

      stopBtn.disabled = true;
      sendBtn.disabled = false;
      await finalizeWith(responseText);
    }

    sendBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      sendChat();
    });
  }

  //
  // History sessions UI (list + per-session view)
  //
  async function openHistorySessionsWindow() {
    let sessions = [];
    try {
      sessions = await loadAutoEditSessions();
    } catch (err) {
      console.error('Failed to load Auto Edit AI sessions list:', err);
      sessions = [];
    }

    const hov = document.createElement('div');
    Object.assign(hov.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      width: '100vw',
      height: '100vh',
      background: 'rgba(0,0,0,0.5)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      zIndex: '13000'
    });

    const card = document.createElement('div');
    Object.assign(card.style, {
      width: 'min(880px, 94vw)',
      maxHeight: '84vh',
      background: '#fff',
      borderRadius: '8px',
      boxShadow: '0 10px 30px rgba(0,0,0,0.25)',
      padding: '12px',
      boxSizing: 'border-box',
      display: 'flex',
      flexDirection: 'column',
      gap: '8px'
    });

    const headerRow = document.createElement('div');
    Object.assign(headerRow.style, {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center'
    });

    const title = document.createElement('div');
    title.textContent = 'Auto Edit — history sessions';
    Object.assign(title.style, { fontSize: '15px', fontWeight: '700', color: '#111' });

    const closeBtn = document.createElement('button');
    closeBtn.textContent = 'Close';
    Object.assign(closeBtn.style, {
      padding: '6px 10px',
      borderRadius: '8px',
      border: '1px solid #ddd',
      background: '#f7f9fb',
      cursor: 'pointer',
      color: '#000'
    });

    headerRow.appendChild(title);
    headerRow.appendChild(closeBtn);

    const list = document.createElement('div');
    Object.assign(list.style, {
      flex: '1',
      overflowY: 'auto',
      border: '1px solid #e5e7eb',
      borderRadius: '6px',
      padding: '6px',
      fontFamily: 'monospace',
      fontSize: '12px',
      background: '#f9fafb'
    });

    if (!sessions || !sessions.length) {
      const empty = document.createElement('div');
      empty.textContent = 'No history sessions yet. After an AI Chat finishes, opt in to create a conversation.';
      Object.assign(empty.style, { padding: '4px', color: '#4b5563' });
      list.appendChild(empty);
    } else {
      sessions.forEach((session, idx) => {
        const row = document.createElement('div');
        row.className = 'ae-session-row';
        row.dataset.sessionId = session.id;
        Object.assign(row.style, {
          padding: '6px 8px',
          borderBottom: '1px solid #e5e7eb',
          cursor: 'pointer',
          display: 'flex',
          flexDirection: 'column',
          gap: '2px'
        });

        const nameEl = document.createElement('div');
        nameEl.textContent = session.name || `Session ${idx + 1}`;
        Object.assign(nameEl.style, { fontWeight: '600', color: '#111' });

        const metaEl = document.createElement('div');
        let createdLabel = 'Unknown time';
        if (session.createdAt) {
          try {
            const d = new Date(session.createdAt);
            createdLabel = !isNaN(d.getTime()) ? d.toLocaleString() : String(session.createdAt);
          } catch (_) {
            createdLabel = String(session.createdAt);
          }
        }
        metaEl.textContent = `${createdLabel}  •  ${session.id}`;
        Object.assign(metaEl.style, { color: '#4b5563' });

        const countEl = document.createElement('div');
        const count = Array.isArray(session.messages) ? session.messages.length : 0;
        countEl.textContent = `${count} message(s)`;
        Object.assign(countEl.style, { color: '#6b7280', fontSize: '11px' });

        row.appendChild(nameEl);
        row.appendChild(metaEl);
        row.appendChild(countEl);

        row.addEventListener('click', () => {
          const rows = list.querySelectorAll('.ae-session-row');
          rows.forEach(r => { r.style.background = 'transparent'; });
          row.style.background = '#e0f2fe';
        });

        row.addEventListener('dblclick', () => {
          close();
          openHistorySessionDetail(session.id).catch(err => {
            console.error('Failed to open Auto Edit history session detail:', err);
          });
        });

        row.addEventListener('contextmenu', (ev) => {
          ev.preventDefault();
          const currentName = session.name || '';
          const newName = window.prompt('Rename session', currentName);
          if (!newName) return;
          const trimmed = newName.trim();
          if (!trimmed) return;
          session.name = trimmed.slice(0, 120);
          nameEl.textContent = session.name;
          persistAutoEditSession(session);
        });

        list.appendChild(row);
      });
    }

    const hint = document.createElement('div');
    hint.textContent = 'Tip: right-click to rename; double-click to open a session.';
    Object.assign(hint.style, { fontSize: '11px', color: '#4b5563' });

    card.appendChild(headerRow);
    card.appendChild(list);
    card.appendChild(hint);

    hov.appendChild(card);
    document.body.appendChild(hov);

    let cleanup = null;
    function close() {
      try { if (hov.parentNode) hov.parentNode.removeChild(hov); } catch (_) {}
      window.removeEventListener('keydown', onKey);
      if (cleanup) cleanup();
    }

    cleanup = createPopupWithTolerance(hov, card, () => {
      close();
    }, 30);

    function onKey(e) {
      if (e.key === 'Escape') close();
    }
    window.addEventListener('keydown', onKey);

    closeBtn.addEventListener('click', () => close());
  }

  async function openHistorySessionDetail(sessionId) {
    const session = await getAutoEditSessionById(sessionId);
    if (!session) {
      console.warn('Auto Edit history session not found:', sessionId);
      return;
    }

    const hov = document.createElement('div');
    Object.assign(hov.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      width: '100vw',
      height: '100vh',
      background: 'rgba(0,0,0,0.5)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      zIndex: '13000'
    });

    const card = document.createElement('div');
    Object.assign(card.style, {
      width: 'min(1040px, 96vw)',
      maxHeight: '88vh',
      background: '#fff',
      borderRadius: '8px',
      boxShadow: '0 10px 30px rgba(0,0,0,0.25)',
      padding: '12px',
      boxSizing: 'border-box',
      display: 'flex',
      flexDirection: 'column',
      gap: '8px'
    });

    const headerRow = document.createElement('div');
    Object.assign(headerRow.style, {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center'
    });

    const headerLeft = document.createElement('div');
    const titleEl = document.createElement('div');
    titleEl.textContent = 'History session';
    Object.assign(titleEl.style, { fontSize: '15px', fontWeight: '700', color: '#111' });

    const subtitleEl = document.createElement('div');
    let createdLabel = 'Unknown time';
    if (session.createdAt) {
      try {
        const d = new Date(session.createdAt);
        createdLabel = !isNaN(d.getTime()) ? d.toLocaleString() : String(session.createdAt);
      } catch (_) {
        createdLabel = String(session.createdAt);
      }
    }
    subtitleEl.textContent = `${session.name || 'Unnamed session'} • ${createdLabel} • ${session.id}`;
    Object.assign(subtitleEl.style, { fontSize: '11px', color: '#4b5563' });

    headerLeft.appendChild(titleEl);
    headerLeft.appendChild(subtitleEl);

    const headerRight = document.createElement('div');
    Object.assign(headerRight.style, { display: 'flex', gap: '8px', alignItems: 'center' });

    const newMsgBtn = document.createElement('button');
    newMsgBtn.textContent = '+ New message';
    Object.assign(newMsgBtn.style, {
      padding: '6px 10px',
      borderRadius: '8px',
      border: '1px solid #10b981',
      background: 'linear-gradient(180deg, #d1fae5, #a7f3d0)',
      cursor: 'pointer',
      color: '#000',
      fontSize: '12px'
    });

    const closeBtn = document.createElement('button');
    closeBtn.textContent = 'Close';
    Object.assign(closeBtn.style, {
      padding: '6px 10px',
      borderRadius: '8px',
      border: '1px solid #ddd',
      background: '#f7f9fb',
      cursor: 'pointer',
      color: '#000',
      fontSize: '12px'
    });

    headerRight.appendChild(newMsgBtn);
    headerRight.appendChild(closeBtn);

    headerRow.appendChild(headerLeft);
    headerRow.appendChild(headerRight);

    // Connection row shared for this session
    const connRow = document.createElement('div');
    Object.assign(connRow.style, { display: 'flex', gap: '8px', alignItems: 'center' });

    const baseLabel = document.createElement('div');
    baseLabel.textContent = 'Base URL';
    Object.assign(baseLabel.style, { fontSize: '12px', width: '64px' });

    const baseInput = document.createElement('input');
    baseInput.type = 'text';
    baseInput.placeholder = 'http://localhost:8129/chat';
    baseInput.value = session.baseUrl || 'http://localhost:8129/chat';
    Object.assign(baseInput.style, {
      flex: '1',
      padding: '6px 8px',
      border: '1px solid #e6eaf0',
      borderRadius: '6px',
      fontFamily: 'monospace',
      fontSize: '12px'
    });

    const modelLabel = document.createElement('div');
    modelLabel.textContent = 'Model';
    Object.assign(modelLabel.style, { fontSize: '12px' });

    const modelInput = document.createElement('input');
    modelInput.type = 'text';
    modelInput.value = session.defaultModel || 'gpt-5.1-codex';
    Object.assign(modelInput.style, {
      width: '160px',
      padding: '6px 8px',
      border: '1px solid #e6eaf0',
      borderRadius: '6px',
      fontFamily: 'monospace',
      fontSize: '12px'
    });

    connRow.appendChild(baseLabel);
    connRow.appendChild(baseInput);
    connRow.appendChild(modelLabel);
    connRow.appendChild(modelInput);

    const bodyRow = document.createElement('div');
    Object.assign(bodyRow.style, {
      display: 'flex',
      gap: '10px',
      alignItems: 'stretch',
      marginTop: '6px',
      minHeight: '360px'
    });

    const leftPanel = document.createElement('div');
    Object.assign(leftPanel.style, {
      flex: '1',
      display: 'flex',
      flexDirection: 'column',
      gap: '6px',
      fontFamily: 'monospace',
      fontSize: '13px'
    });

    const rightPanel = document.createElement('div');
    Object.assign(rightPanel.style, {
      width: '280px',
      maxWidth: '34%',
      borderLeft: '1px solid #e5e7eb',
      paddingLeft: '8px',
      boxSizing: 'border-box',
      display: 'flex',
      flexDirection: 'column',
      gap: '4px'
    });

    const rightTitle = document.createElement('div');
    rightTitle.textContent = 'Messages in this session';
    Object.assign(rightTitle.style, { fontSize: '13px', fontWeight: '600', color: '#111' });

    const msgList = document.createElement('div');
    Object.assign(msgList.style, {
      flex: '1',
      overflowY: 'auto',
      border: '1px solid #e5e7eb',
      borderRadius: '6px',
      padding: '6px',
      background: '#f9fafb',
      fontFamily: 'monospace',
      fontSize: '12px'
    });

    rightPanel.appendChild(rightTitle);
    rightPanel.appendChild(msgList);

    bodyRow.appendChild(leftPanel);
    bodyRow.appendChild(rightPanel);

    card.appendChild(headerRow);
    card.appendChild(connRow);
    card.appendChild(bodyRow);

    hov.appendChild(card);
    document.body.appendChild(hov);

    let cleanup = null;
    function close() {
      try { if (hov.parentNode) hov.parentNode.removeChild(hov); } catch (_) {}
      window.removeEventListener('keydown', onKey);
      if (cleanup) cleanup();
      try {
        session.baseUrl = (baseInput.value || '').trim();
        session.defaultModel = (modelInput.value || '').trim() || 'gpt-5.1-codex';
        session.updatedAt = new Date().toISOString();
        persistAutoEditSession(session);
      } catch (_) {}
    }

    cleanup = createPopupWithTolerance(hov, card, () => {
      close();
    }, 30);

    function onKey(e) {
      if (e.key === 'Escape') close();
    }
    window.addEventListener('keydown', onKey);

    closeBtn.addEventListener('click', () => close());

    function formatTs(iso) {
      if (!iso) return '';
      try {
        const d = new Date(iso);
        if (!isNaN(d.getTime())) return d.toLocaleString();
        return String(iso);
      } catch (_) {
        return String(iso);
      }
    }

    let selectedMessageId = null;

    function renderMessageList() {
      msgList.innerHTML = '';
      const messages = Array.isArray(session.messages) ? session.messages : [];
      if (!messages.length) {
        const empty = document.createElement('div');
        empty.textContent = 'No messages yet. Click "+ New message" to start.';
        Object.assign(empty.style, { padding: '4px', color: '#4b5563' });
        msgList.appendChild(empty);
        return;
      }
      messages.forEach((msg, idx) => {
        const row = document.createElement('div');
        row.className = 'ae-session-msg-row';
        row.dataset.msgId = msg.id;
        Object.assign(row.style, {
          padding: '4px 6px',
          borderBottom: '1px solid #e5e7eb',
          cursor: 'pointer',
          borderRadius: '4px',
          marginBottom: '2px'
        });
        if (msg.id === selectedMessageId) row.style.background = '#e0f2fe';

        const header = document.createElement('div');
        header.textContent = `[${idx + 1}] ${formatTs(msg.createdAt)}`;
        Object.assign(header.style, { fontWeight: '600', color: '#111' });

        const ideaPreview = document.createElement('div');
        const firstLine = (msg.idea || '').split(/\r?\n/)[0].trim();
        ideaPreview.textContent = firstLine || '(no message text)';
        Object.assign(ideaPreview.style, { color: '#374151' });

        const modelLine = document.createElement('div');
        modelLine.textContent = `Model: ${msg.model || session.defaultModel || 'gpt-5.1-codex'}`;
        Object.assign(modelLine.style, { color: '#6b7280', fontSize: '11px' });

        row.appendChild(header);
        row.appendChild(ideaPreview);
        row.appendChild(modelLine);

        row.addEventListener('click', () => {
          selectedMessageId = msg.id;
          renderMessageList();
          showMessageView(msg);
        });

        msgList.appendChild(row);
      });
    }

    function showMessageView(message) {
      leftPanel.innerHTML = '';

      const ideaLabel = document.createElement('div');
      ideaLabel.textContent = 'User message';
      Object.assign(ideaLabel.style, { fontSize: '13px', fontWeight: '600' });

      const ideaView = document.createElement('textarea');
      ideaView.readOnly = true;
      ideaView.value = message.idea || '';
      Object.assign(ideaView.style, {
        width: '100%',
        height: '120px',
        padding: '6px 8px',
        border: '1px solid #e6eaf0',
        borderRadius: '6px',
        fontFamily: 'monospace',
        fontSize: '13px',
        resize: 'vertical',
        background: '#fff',
        color: '#000'
      });

      const respLabel = document.createElement('div');
      respLabel.textContent = 'GPT response';
      Object.assign(respLabel.style, { fontSize: '12px', fontWeight: '600', marginTop: '6px' });

      const respView = document.createElement('textarea');
      respView.readOnly = true;
      respView.value = message.responseText || '';
      Object.assign(respView.style, {
        width: '100%',
        height: '220px',
        padding: '6px 8px',
        border: '1px solid #e6eaf0',
        borderRadius: '6px',
        fontFamily: 'monospace',
        fontSize: '12px',
        resize: 'vertical',
        background: '#fff',
        color: '#000'
      });

      const controlsRow = document.createElement('div');
      Object.assign(controlsRow.style, {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginTop: '6px'
      });

      const leftControls = document.createElement('div');
      Object.assign(leftControls.style, { display: 'flex', gap: '6px' });

      const copyBtn = document.createElement('button');
      copyBtn.textContent = 'Copy response';
      Object.assign(copyBtn.style, {
        padding: '6px 8px',
        borderRadius: '6px',
        border: '1px solid #d6dde6',
        background: '#f7f9fb',
        cursor: 'pointer',
        color: '#000',
        fontSize: '12px'
      });
      copyBtn.addEventListener('click', () => {
        try { clipboard.writeText(message.responseText || ''); } catch (_) {}
      });

      leftControls.appendChild(copyBtn);

      const autoEditBtn = document.createElement('button');
      autoEditBtn.textContent = 'Auto edit from this response';
      Object.assign(autoEditBtn.style, {
        padding: '6px 10px',
        borderRadius: '6px',
        border: '1px solid #0b66ff',
        background: 'linear-gradient(180deg, #dff0ff, #d6ecff)',
        cursor: 'pointer',
        color: '#000',
        fontSize: '12px'
      });
      autoEditBtn.disabled = !(message.responseText && message.responseText.trim());

      const localStatus = document.createElement('div');
      Object.assign(localStatus.style, { fontSize: '12px', color: '#333', minHeight: '18px' });

      autoEditBtn.addEventListener('click', async () => {
        if (!message.responseText) {
          localStatus.textContent = 'No response text available for Auto edit.';
          return;
        }
        autoEditBtn.disabled = true;
        localStatus.textContent = 'Running Auto edit...';
        try {
          await runAutoEditFromText({
            sourceText: message.responseText,
            folderRel: folderInput.value || '',
            detectionModeOverride: detectionMode,
            statusElement: localStatus,
            closeOverlayAfter: false
          });
          autoEditBtn.disabled = false;
        } catch (err) {
          console.error('Auto edit from history session failed:', err);
          localStatus.textContent = `Auto edit failed: ${err && err.message ? err.message : String(err)}`;
          autoEditBtn.disabled = false;
        }
      });

      controlsRow.appendChild(leftControls);
      controlsRow.appendChild(autoEditBtn);

      leftPanel.appendChild(ideaLabel);
      leftPanel.appendChild(ideaView);
      leftPanel.appendChild(respLabel);
      leftPanel.appendChild(respView);
      leftPanel.appendChild(controlsRow);
      leftPanel.appendChild(localStatus);
    }

    function showComposeView(initialIdea) {
      leftPanel.innerHTML = '';

      const ideaLabel = document.createElement('div');
      ideaLabel.textContent = 'Your idea';
      Object.assign(ideaLabel.style, { fontSize: '13px', fontWeight: '600' });

      const ideaInput = document.createElement('textarea');
      ideaInput.placeholder = 'Describe your idea/request for changes...';
      ideaInput.value = initialIdea || '';
      Object.assign(ideaInput.style, {
        width: '100%',
        height: '140px',
        padding: '8px',
        border: '1px solid #e6eaf0',
        borderRadius: '6px',
        fontFamily: 'monospace',
        fontSize: '13px',
        resize: 'vertical',
        background: '#fff',
        color: '#000'
      });

      const attachLabel = document.createElement('div');
      attachLabel.textContent = 'Attachments';
      Object.assign(attachLabel.style, { fontSize: '12px', fontWeight: '600', marginTop: '4px' });

      const attachRow = document.createElement('div');
      Object.assign(attachRow.style, { display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '4px' });

      const attachPathInput = document.createElement('input');
      attachPathInput.type = 'text';
      attachPathInput.placeholder = 'File path (full or relative to project root)';
      Object.assign(attachPathInput.style, {
        flex: '1',
        padding: '6px 8px',
        borderRadius: '6px',
        border: '1px solid #e6eaf0',
        fontFamily: 'monospace',
        fontSize: '12px'
      });

      const attachBtn = document.createElement('button');
      attachBtn.textContent = 'Attach file';
      Object.assign(attachBtn.style, {
        padding: '6px 8px',
        borderRadius: '8px',
        border: '1px solid #d6dde6',
        background: '#f7f9fb',
        cursor: 'pointer',
        color: '#000',
        fontSize: '12px'
      });

      attachRow.appendChild(attachPathInput);
      attachRow.appendChild(attachBtn);

      const attachList = document.createElement('div');
      Object.assign(attachList.style, {
        border: '1px solid #eef2f6',
        borderRadius: '6px',
        padding: '6px',
        background: '#fafafa',
        maxHeight: '120px',
        overflowY: 'auto',
        fontFamily: 'monospace',
        fontSize: '12px'
      });

      const respLabel = document.createElement('div');
      respLabel.textContent = 'Response (live)';
      Object.assign(respLabel.style, { fontSize: '12px', fontWeight: '600', marginTop: '6px' });

      const respOutput = document.createElement('textarea');
      respOutput.readOnly = true;
      respOutput.placeholder = 'Streaming response will appear here...';
      Object.assign(respOutput.style, {
        width: '100%',
        height: '200px',
        padding: '6px 8px',
        border: '1px solid #e6eaf0',
        borderRadius: '6px',
        fontFamily: 'monospace',
        fontSize: '12px',
        resize: 'vertical',
        background: '#fff',
        color: '#000'
      });

      const statusEl = document.createElement('div');
      Object.assign(statusEl.style, { fontSize: '12px', color: '#333', minHeight: '18px', marginTop: '4px' });

      // Throttled updater for streaming response text
      const updateRespOutput = createThrottledTextSetter(respOutput, 80);

      const controlsRow = document.createElement('div');
      Object.assign(controlsRow.style, {
        display: 'flex',
        justifyContent: 'flex-end',
        gap: '8px',
        marginTop: '6px'
      });

      const stopBtn = document.createElement('button');
      stopBtn.textContent = 'Stop';
      Object.assign(stopBtn.style, {
        padding: '6px 10px',
        borderRadius: '8px',
        border: '1px solid #f87171',
        background: 'linear-gradient(180deg, #ffe4e6, #fecaca)',
        cursor: 'pointer',
        color: '#000',
        fontSize: '12px'
      });
      stopBtn.disabled = true;

      const sendBtn = document.createElement('button');
      sendBtn.textContent = 'Send';
      Object.assign(sendBtn.style, {
        padding: '6px 10px',
        borderRadius: '8px',
        border: '1px solid #0b66ff',
        background: 'linear-gradient(180deg, #dff0ff, #d6ecff)',
        cursor: 'pointer',
        color: '#000',
        fontSize: '12px'
      });

      controlsRow.appendChild(stopBtn);
      controlsRow.appendChild(sendBtn);

      leftPanel.appendChild(ideaLabel);
      leftPanel.appendChild(ideaInput);
      leftPanel.appendChild(attachLabel);
      leftPanel.appendChild(attachRow);
      leftPanel.appendChild(attachList);
      leftPanel.appendChild(respLabel);
      leftPanel.appendChild(respOutput);
      leftPanel.appendChild(statusEl);
      leftPanel.appendChild(controlsRow);

      const attachments = [];

      function addAttachmentFromData(att) {
        if (!att) return;
        const record = {
          path: att.path || '',
          name: att.name || '',
          content: att.content != null ? String(att.content) : '',
          displayPath: att.displayPath || att.path || ''
        };

        // compute size if provided or estimate from content
        let size = typeof att.size === 'number' ? att.size : undefined;
        if (typeof size !== 'number') {
          try {
            size = Buffer.byteLength(record.content || '', 'utf8');
          } catch (e) {
            size = 0;
          }
        }
        record.size = size;

        attachments.push(record);

        const left = document.createElement('div');
        left.textContent = `${record.name || 'attachment'}${record.displayPath ? '  (' + record.displayPath + ')' : ''}`;
        Object.assign(left.style, { padding: '2px 0', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' });

        const removeBtn = document.createElement('button');
        removeBtn.textContent = 'Remove';
        Object.assign(removeBtn.style, {
          marginLeft: '8px',
          padding: '2px 6px',
          borderRadius: '4px',
          border: '1px solid #ddd',
          background: '#fff',
          cursor: 'pointer',
          fontSize: '11px'
        });

        const sizeSpan = document.createElement('div');
        sizeSpan.textContent = formatBytes(size);
        Object.assign(sizeSpan.style, { marginLeft: '8px', fontSize: '12px', color: '#666', minWidth: '64px', textAlign: 'right' });

        const right = document.createElement('div');
        Object.assign(right.style, { display: 'flex', alignItems: 'center', gap: '8px' });
        right.appendChild(sizeSpan);
        right.appendChild(removeBtn);

        const wrap = document.createElement('div');
        Object.assign(wrap.style, { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px', padding: '2px 0' });
        wrap.appendChild(left);
        wrap.appendChild(right);

        attachList.appendChild(wrap);

        removeBtn.addEventListener('click', () => {
          const idx = attachments.indexOf(record);
          if (idx >= 0) attachments.splice(idx, 1);
          try { attachList.removeChild(wrap); } catch (_) {}
          statusEl.textContent = `${attachments.length} file(s) attached.`;
        });

        statusEl.textContent = `${attachments.length} file(s) attached.`;
      }

      attachBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        let inputPath = (attachPathInput.value || '').trim();
        if (!inputPath) { statusEl.textContent = 'Please enter a path.'; return; }
        inputPath = expandTilde(inputPath);
        let fullPath = inputPath;
        try {
          if (!path.isAbsolute(fullPath)) {
            fullPath = path.resolve(projectRoot, fullPath);
          } else {
            fullPath = path.resolve(fullPath);
          }
        } catch (_) {}
        try { fullPath = path.normalize(fullPath); } catch (_) {}

        attachPathInput.value = fullPath;

        try {
          const data = await fs.readFile(fullPath);
          const content = data.toString('utf8');
          const name = path.basename(fullPath);
          let displayPath = fullPath;
          try {
            const rel = path.relative(projectRoot, fullPath).replace(/\\/g, '/');
            if (rel && !rel.startsWith('..')) displayPath = rel;
          } catch (_) {}
          addAttachmentFromData({ path: fullPath, name, content, displayPath });
        } catch (err) {
          console.error('Failed to read attachment for history session:', err);
          statusEl.textContent = 'Failed to read file. Check the path and permissions.';
        }
      });

      let abortController = null;
      let currentRequestId = null;
      let liveBuffer = '';
      let sending = false;
      let stoppedLocal = false;

      async function finalizeStreaming(finalResponseText) {
        const responseText = finalResponseText || liveBuffer || '';
        sending = false;
        stopBtn.disabled = true;
        sendBtn.disabled = false;
        respOutput.value = responseText;
        if (!responseText) {
          statusEl.textContent = 'No response received.';
          return;
        }

        const ideaText = (ideaInput.value || '').trim();
        const baseUrl = (baseInput.value || '').trim().replace(/\/$/, '');
        const model = (modelInput.value || '').trim() || 'gpt-5.1-codex';

        const now = new Date();
        const msg = {
          id: 'msg-' + now.getTime().toString(36),
          createdAt: now.toISOString(),
          idea: ideaText,
          responseText,
          attachments: attachments.map(a => ({
            path: a.path || '',
            name: a.name || '',
            content: a.content != null ? String(a.content) : '',
            displayPath: a.displayPath || ''
          })),
          model,
          baseUrl,
          stopped: !!stoppedLocal
        };

        if (!Array.isArray(session.messages)) session.messages = [];
        session.messages.push(msg);
        session.baseUrl = baseUrl;
        session.defaultModel = model;
        session.updatedAt = now.toISOString();

        try {
          await persistAutoEditSession(session);
        } catch (err) {
          console.error('Failed to persist Auto Edit session after send:', err);
        }

        selectedMessageId = msg.id;
        renderMessageList();
        showMessageView(msg);
      }

      async function sendMessage() {
        const baseUrl = (baseInput.value || '').trim().replace(/\/$/, '');
        const model = (modelInput.value || '').trim() || 'gpt-5.1-codex';
        const ideaText = (ideaInput.value || '').trim();
        if (!baseUrl) { statusEl.textContent = 'Please set Base URL.'; return; }
        if (!ideaText) { statusEl.textContent = 'Please enter your idea.'; return; }

        sending = true;
        stoppedLocal = false;
        liveBuffer = '';
        respOutput.value = '';
        statusEl.textContent = 'Sending...';

        abortController = new AbortController();
        stopBtn.disabled = false;
        sendBtn.disabled = true;

        let responseText = '';
        try {
          responseText = await sendAutoEditIdea({
            idea: ideaText,
            attachments,
            systemPrompt: promptText,
            model,
            url: baseUrl,
            includeHistory: true,
            appId: 'auto_edit',
            sessionId: session.id,
            signal: abortController.signal,
            onRequestId: (rid) => { currentRequestId = rid || null; },
            onStart: () => { statusEl.textContent = 'Receiving...'; },
            onChunk: (chunk) => {
              liveBuffer += String(chunk || '');
              updateRespOutput(liveBuffer);
            }
          });
        } catch (err) {
          if (stoppedLocal) {
            await finalizeStreaming(liveBuffer);
            return;
          }
          console.error('History session AI Chat failed:', err);
          statusEl.textContent = `Error: ${err && err.message ? err.message : String(err)}`;
          sending = false;
          stopBtn.disabled = true;
          sendBtn.disabled = false;
          return;
        }

        await finalizeStreaming(responseText);
      }

      sendBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        if (sending) return;
        sendMessage();
      });

      stopBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        if (!sending || stopBtn.disabled) return;
        stoppedLocal = true;
        stopBtn.disabled = true;
        sendBtn.disabled = true;
        try {
          if (currentRequestId) {
            const base = (baseInput.value || '').trim().replace(/\/$/, '');
            try {
              await stopAutoEditRequest({ url: base, requestId: currentRequestId });
            } catch (_) {}
          }
        } finally {
          try { if (abortController) abortController.abort(); } catch (_) {}
        }
        await finalizeStreaming(liveBuffer);
      });
    }

    newMsgBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      selectedMessageId = null;
      renderMessageList();
      showComposeView('');
    });

    if (Array.isArray(session.messages) && session.messages.length) {
      const last = session.messages[session.messages.length - 1];
      selectedMessageId = last.id;
      renderMessageList();
      showMessageView(last);
    } else {
      renderMessageList();
      showComposeView('');
    }
  }

  //
  // Helper functions: parse comments (multiple languages), path detection, parse blocks, safe resolve, update textarea
  //

  // Return object describing a comment line if it contains a path (prefix preserved), otherwise null.
  // Supports many single-line comment syntaxes and common single-line block forms.
  function parseCommentLine(line) {
    if (!line || typeof line !== 'string') return null;
    // block comment: /* path */
    let m = line.match(/^\s*\/\*\s*(.+?)\s*\*\/\s*$/);
    if (m) return { path: m[1].trim(), type: 'block', prefix: '/* ', suffix: ' */' };

    // HTML comment <!-- path -->
    m = line.match(/^\s*<!--\s*(.+?)\s*-->\s*$/);
    if (m) return { path: m[1].trim(), type: 'html', prefix: '<!-- ', suffix: ' -->' };

    // Single-line comment types with captured prefix (preserves leading spaces + marker)
    m = line.match(/^(\s*\/\/\s*)(.+?)\s*$/);
    if (m) return { path: m[2].trim(), type: '//', prefix: m[1] };

    m = line.match(/^(\s*#\s*)(.+?)\s*$/);
    if (m) return { path: m[2].trim(), type: '#', prefix: m[1] };

    m = line.match(/^(\s*--\s*)(.+?)\s*$/);
    if (m) return { path: m[2].trim(), type: '--', prefix: m[1] };

    m = line.match(/^(\s*;\s*)(.+?)\s*$/);
    if (m) return { path: m[2].trim(), type: ';', prefix: m[1] };

    m = line.match(/^(\s*%\s*)(.+?)\s*$/);
    if (m) return { path: m[2].trim(), type: '%', prefix: m[1] };

    // REM style (batch) - case-insensitive
    m = line.match(/^(\s*(?:REM|rem)\s+)(.+?)\s*$/);
    if (m) return { path: m[2].trim(), type: 'rem', prefix: m[1] };

    return null;
  }

  // Slightly permissive path-like detection: accepts strings with at least a slash or a dot-extension-like pattern and no spaces.
  function isPathLike(s) {
    if (!s || typeof s !== 'string') return false;
    if (/\s/.test(s)) return false; // don't accept spaces
    if (/[\/\\]/.test(s)) return true; // contains slash
    if (/\.[A-Za-z0-9]+$/.test(s)) return true; // ends with .ext
    return false;
  }

  // Choose a reasonable comment style for a given language identifier (fence info)
  function commentForLang(lang) {
    const l = (lang || '').toLowerCase();
    if (!l) return { type: 'line', prefix: '# ' };
    if (['js','javascript','ts','typescript','java','c','cpp','c++','csharp','cs','go','rust','swift','kotlin','scala','php'].includes(l)) return { type: 'line', prefix: '// ' };
    if (['py','python','sh','bash','zsh','shell','ruby','rb','perl','r'].includes(l)) return { type: 'line', prefix: '# ' };
    if (['lua'].includes(l)) return { type: 'line', prefix: '-- '};
    if (['sql'].includes(l)) return { type: 'line', prefix: '-- '};
    if (['html','xml','xhtml'].includes(l)) return { type: 'block', prefix: '<!-- ', suffix: ' -->' };
    if (['css','scss','less'].includes(l)) return { type: 'block', prefix: '/* ', suffix: ' */' };
    // fallback
    return { type: 'line', prefix: '# ' };
  }

  /**
   * parseFileBlocks(text)
   *
   * Scans text and extracts code blocks that correspond to files.
   * Only supports code fences where the FIRST line inside the code block is a comment containing the path (new preferred format).
   * Exception: if the line immediately before a fence is a path-like string that ends with .txt, treat it as a file path for that block.
   *
   * Returns entries with metadata for editing/updating:
   * {
   *   rawPath,
   *   code,
   *   lang,
   *   annotations,
   *   blockStart, blockEnd, codeStart, codeEnd,
   *   inlinePath: boolean, // true if path was found as a comment inside the block
   *   comment: { type, prefix, suffix } // when inlinePath=true and a comment style was parsed
   *   pathLineIndex: number // when inlinePath=false and .txt-style path-before-fence was used
   *   isLikelyFull: boolean // guess whether rawPath is an absolute/full path
   * }
   */
  function parseFileBlocks(text) {
    const lines = String(text || '').split(/\r?\n/);
    const results = [];
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (!line || !line.startsWith('```')) continue;
      const fenceLine = line;
      const lang = fenceLine.slice(3).trim();
      const blockStart = i;
      // find closing fence
      let j = i + 1;
      while (j < lines.length && !lines[j].startsWith('```')) {
        j++;
      }
      const blockEnd = j < lines.length ? j : lines.length; // closing fence index or EOF
      const codeLines = lines.slice(blockStart + 1, blockEnd);

      // Attempt to find an inline path inside the first code line (comment)
      let parsedComment = null;
      if (codeLines.length > 0) {
        parsedComment = parseCommentLine(codeLines[0]);
        if (parsedComment && !isPathLike(parsedComment.path)) {
          parsedComment = null;
        }
      }

      let rawPath = null;
      let usedInline = false;
      let code = '';
      const annotations = [];
      let commentInfo = null;
      let pathLineIndex = null;

      if (parsedComment) {
        rawPath = parsedComment.path;
        usedInline = true;
        commentInfo = { type: parsedComment.type, prefix: parsedComment.prefix, suffix: parsedComment.suffix };
        // actual code is everything after the first line inside the block
        code = codeLines.slice(1).join('\n');
        // gather up to two non-empty lines immediately above the opening fence (annotations)
        let k = blockStart - 1;
        while (k >= 0 && annotations.length < 2) {
          const a = lines[k].replace(/\r?\n/g, '').trim();
          if (a && !a.startsWith('```')) annotations.unshift(a);
          k--;
        }
      } else {
        // Try the old style: a raw path line immediately before the fence
        // Only accept this form when the path ends with .txt (case-insensitive).
        const prevIndex = blockStart - 1;
        if (prevIndex >= 0) {
          let prev = lines[prevIndex].trim();
          // remove quotes if any
          prev = prev.replace(/^["']|["']$/g, '');
          if (prev && isPathLike(prev) && /\.txt$/i.test(prev) && !prev.startsWith('```')) {
            rawPath = prev;
            usedInline = false;
            code = codeLines.join('\n');
            pathLineIndex = prevIndex;
            // gather up to two non-empty lines above the path line (these would be annotations)
            let k = prevIndex - 1;
            while (k >= 0 && annotations.length < 2) {
              const a = lines[k].replace(/\r?\n/g, '').trim();
              if (a && !a.startsWith('```')) annotations.unshift(a);
              k--;
            }
          }
        }
      }

      if (rawPath) {
        const codeStart = blockStart + 1 + (usedInline ? 1 : 0); // index where editable code begins in lines[]
        const codeEnd = Math.max(blockEnd - 1, codeStart - 1); // last index of code (may be < codeStart if empty)
        const entry = {
          rawPath,
          code,
          lang,
          annotations,
          blockStart,
          blockEnd,
          codeStart,
          codeEnd,
          inlinePath: usedInline,
          comment: commentInfo,
          pathLineIndex
        };
        // Guess whether the rawPath looks like a full/absolute path
        entry.isLikelyFull = isLikelyAbsolutePathString(rawPath);
        results.push(entry);
      }

      // advance i to end of block to continue scanning after it
      i = blockEnd;
    }
    return results;
  }

  /**
   * updateTextareaForFile(oldRelPath, newCode, newRawPath, targetFolderRel)
   *
   * Finds the code block in the main textarea that corresponds to oldRelPath (resolved under projectRoot OR
   * resolved under projectRoot with the targetFolderRel applied for display/target). Replaces editable code portion with newCode.
   * If newRawPath is provided, the path comment (or the .txt path line before the fence) will be updated as well.
   *
   * Returns an object { success: boolean, newRelPath?: string, newInline?: boolean } on success, or { success: false }.
   */
  function updateTextareaForFile(oldRelPath, newCode, newRawPath, targetFolderRel = '') {
    try {
      const currentText = String(textarea.value || '');
      const parsed = parseFileBlocks(currentText);
      if (!parsed.length) return { success: false };
      const lines = currentText.split(/\r?\n/);

      for (const p of parsed) {
        // compute safe rels both for the raw path as found and for the raw path combined with the target folder
        const safeOrig = safeResolveUnderProject(projectRoot, p.rawPath);
        const relOrig = safeOrig.relPath;

        const combinedRawForThisBlock = combineRawWithFolder(p.rawPath || '', targetFolderRel);
        const safeCombined = safeResolveUnderProject(projectRoot, combinedRawForThisBlock);
        const relCombined = safeCombined.relPath;

        // If the oldRelPath matches either resolved form, this is our block.
        if (relOrig === oldRelPath || relCombined === oldRelPath) {
          const bStart = p.blockStart;
          const bEnd = p.blockEnd; // index of closing fence or lines.length
          const prevLineIdx = p.pathLineIndex; // may be null
          const before = (prevLineIdx != null) ? lines.slice(0, prevLineIdx) : lines.slice(0, bStart);
          const after = (bEnd < lines.length) ? lines.slice(bEnd) : [];

          // Prepare new code lines
          const newCodeLines = (newCode === '' ? [] : String(newCode).split(/\r?\n/));

          if (p.inlinePath) {
            // Inline comment style: first line inside block is the path comment.
            // Recreate fence + comment + new code
            // Determine new first-inside-line (the comment)
            let newFirstInsideLine = lines[p.codeStart] || ''; // default to original if no update
            if (typeof newRawPath === 'string') {
              // Preserve original comment prefix/suffix if present, otherwise choose based on lang
              if (p.comment && p.comment.type) {
                if (p.comment.suffix) {
                  // block-like comment (/* */ or html)
                  const prefix = p.comment.prefix != null ? p.comment.prefix : (commentForLang(p.lang).prefix || '/* ');
                  const suffix = p.comment.suffix != null ? p.comment.suffix : (commentForLang(p.lang).suffix || ' */');
                  newFirstInsideLine = `${prefix}${newRawPath}${suffix}`;
                } else {
                  // single-line comment prefix preserved
                  const prefix = p.comment.prefix != null ? p.comment.prefix : (commentForLang(p.lang).prefix || '# ');
                  newFirstInsideLine = `${prefix}${newRawPath}`;
                }
              } else {
                // No original comment info: choose based on language
                const cf = commentForLang(p.lang);
                if (cf.type === 'block') {
                  newFirstInsideLine = `${cf.prefix}${newRawPath}${cf.suffix}`;
                } else {
                  newFirstInsideLine = `${cf.prefix}${newRawPath}`;
                }
              }
            }

            // Reconstruct block: fence line + newFirstInsideLine + newCodeLines, then append closing fence (included in after)
            const fenceLine = lines[bStart];
            const newBlock = [fenceLine, newFirstInsideLine].concat(newCodeLines);
            const merged = before.concat(newBlock, after);
            textarea.value = merged.join('\n');

            // Compute new safe relpath for returned metadata (use newRawPath if provided, else original), but always reflect the targetFolderRel for display/write
            const effectiveRawForNewSafe = typeof newRawPath === 'string' ? combineRawWithFolder(newRawPath, targetFolderRel) : combineRawWithFolder(p.rawPath, targetFolderRel);
            const newSafe = safeResolveUnderProject(projectRoot, effectiveRawForNewSafe);
            return { success: true, newRelPath: newSafe.relPath, newInline: true };
          } else {
            // Old-style .txt path-before-fence
            // prevLineIdx should be defined
            if (prevLineIdx == null) {
              // cannot handle
              return { success: false };
            }

            // If newRawPath provided and it still ends with .txt -> update the prev line
            if (typeof newRawPath === 'string' && /\.txt$/i.test(newRawPath)) {
              // replace the prev line with newRawPath, keep the block as fence + code
              const fenceLine = lines[bStart];
              const newBlock = [newRawPath, fenceLine].concat(newCodeLines);
              const merged = before.concat(newBlock, after);
              textarea.value = merged.join('\n');

              const effectiveRawForNewSafe = combineRawWithFolder(newRawPath, targetFolderRel);
              const newSafe = safeResolveUnderProject(projectRoot, effectiveRawForNewSafe);
              return { success: true, newRelPath: newSafe.relPath, newInline: false };
            }

            // If newRawPath provided and does NOT end with .txt -> convert to inline-comment style
            if (typeof newRawPath === 'string' && !/\.txt$/i.test(newRawPath)) {
              // Remove the prev-line, insert inline comment inside the block
              const fenceLine = lines[bStart];
              // choose a comment style by language
              const cf = commentForLang(p.lang);
              let commentLine;
              if (cf.type === 'block') {
                commentLine = `${cf.prefix}${newRawPath}${cf.suffix}`;
              } else {
                commentLine = `${cf.prefix}${newRawPath}`;
              }
              const newBlock = [fenceLine, commentLine].concat(newCodeLines);
              const merged = before.concat(newBlock, after);
              textarea.value = merged.join('\n');

              const effectiveRawForNewSafe = combineRawWithFolder(newRawPath, targetFolderRel);
              const newSafe = safeResolveUnderProject(projectRoot, effectiveRawForNewSafe);
              return { success: true, newRelPath: newSafe.relPath, newInline: true };
            }

            // No newRawPath (only updating code) -> keep prev line intact and replace code block content
            if (typeof newRawPath === 'undefined') {
              const fenceLine = lines[bStart];
              const newBlock = [lines[prevLineIdx], fenceLine].concat(newCodeLines);
              const merged = before.concat(newBlock, after);
              textarea.value = merged.join('\n');

              const effectiveRawForNewSafe = combineRawWithFolder(p.rawPath, targetFolderRel);
              const newSafe = safeResolveUnderProject(projectRoot, effectiveRawForNewSafe);
              return { success: true, newRelPath: newSafe.relPath, newInline: false };
            }

            // Fallback: if newRawPath provided but something unexpected, fail
            return { success: false };
          }
        }
      }

      return { success: false };
    } catch (err) {
      console.error('Failed to update textarea for file', oldRelPath, err);
      return { success: false };
    }
  }

  // Build nested tree structure from a list of relative paths
  function buildNestedTree(relPaths) {
    const root = { name: '.', fullPath: '', children: new Map(), isDir: true };
    for (const p of relPaths) {
      const normalized = p.replace(/\\/g, '/').replace(/^\/+/, '');
      const parts = normalized.split('/').filter(Boolean);
      let node = root;
      for (let i = 0; i < parts.length; i++) {
        const part = parts[i];
        const isLast = i === parts.length - 1;
        if (!node.children.has(part)) {
          const child = {
            name: part,
            fullPath: node.fullPath ? (node.fullPath + '/' + part) : part,
            children: new Map(),
            isDir: !isLast,
            isFile: isLast
          };
          node.children.set(part, child);
        }
        node = node.children.get(part);
      }
    }
    return root;
  }

  // Render ASCII tree lines (and collect metadata) from the nested tree
  function renderAsciiTreeLines(rootNode) {
    const lines = [];
    // top line
    lines.push({ text: '└── ./', relPath: '.', isDir: true });
    function walk(node, prefix) {
      const children = Array.from(node.children.values());
      // sort directories first, then files, alphabetical
      children.sort((a, b) => {
        if (a.isDir && !b.isDir) return -1;
        if (!a.isDir && b.isDir) return 1;
        return a.name.localeCompare(b.name);
      });
      for (let i = 0; i < children.length; i++) {
        const child = children[i];
        const isLast = i === children.length - 1;
        const branch = isLast ? '└── ' : '├── ';
        const text = prefix + branch + (child.isDir ? child.name + '/' : child.name);
        lines.push({ text, relPath: child.fullPath, isDir: child.isDir, isFile: child.isFile });
        if (child.isDir && child.children && child.children.size > 0) {
          const newPrefix = prefix + (isLast ? '    ' : '│   ');
          walk(child, newPrefix);
        }
      }
    }
    walk(rootNode, '    ');
    return lines;
  }

  // Throttle textarea updates during streaming to avoid UI lag.
  // It batches rapid changes and only writes to the DOM at most once per interval.
  function createThrottledTextSetter(textarea, intervalMs = 80) {
    let latestText = '';
    let timeoutId = null;

    return function update(text) {
      if (!textarea) return;
      latestText = String(text != null ? text : '');
      if (timeoutId != null) return;

      timeoutId = setTimeout(() => {
        timeoutId = null;
        // Element may have been removed/closed
        if (!textarea || !textarea.isConnected) return;
        textarea.value = latestText;
        try {
          textarea.scrollTop = textarea.scrollHeight;
        } catch (_) {}
      }, intervalMs);
    };
  }

  // Format bytes into a short human-readable string using 2 significant digits (e.g. 1004 KB -> "1 MB")
  function formatBytes(bytes) {
    if (typeof bytes !== 'number' || !isFinite(bytes)) return '0 B';
    const thresh = 1024;
    if (bytes < thresh) return `${bytes} B`;
    const units = ['KB','MB','GB','TB','PB','EB'];
    let u = -1;
    let val = bytes;
    do {
      val = val / thresh;
      u++;
    } while (val >= thresh && u < units.length - 1);

    // Determine decimal places so we keep ~2 significant digits
    const absVal = Math.abs(val);
    const digits = absVal >= 1 ? Math.floor(Math.log10(absVal)) + 1 : 1;
    let decimals = Math.max(0, 2 - digits);
    if (decimals > 2) decimals = 2;

    let out = val.toFixed(decimals);
    // Trim trailing .0
    out = out.replace(/\.0+$/, '');
    return `${out} ${units[u]}`;
  }

  // Small helper: show a confirmation modal with an optional list of items.
  // Returns a Promise that resolves to true if confirmed, false otherwise.
  function showConfirmationModal(title, message, items = []) {
    return new Promise((resolve) => {
      const confirmOverlay = document.createElement('div');
      Object.assign(confirmOverlay.style, {
        position: 'fixed',
        top: '0',
        left: '0',
        width: '100vw',
        height: '100vh',
        background: 'rgba(0,0,0,0.5)',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        zIndex: '12000'
      });

      const confirmCard = document.createElement('div');
      Object.assign(confirmCard.style, {
        width: 'min(760px, 92vw)',
        maxHeight: '84vh',
        overflow: 'auto',
        background: '#fff',
        padding: '14px',
        borderRadius: '8px',
        boxShadow: '0 10px 30px rgba(0,0,0,0.25)',
        boxSizing: 'border-box'
      });

      const titleEl = document.createElement('div');
      titleEl.textContent = title || 'Confirm';
      Object.assign(titleEl.style, { fontSize: '15px', fontWeight: '700', marginBottom: '8px', color: '#111' });
      confirmCard.appendChild(titleEl);

      const msgEl = document.createElement('div');
      msgEl.innerText = message || '';
      Object.assign(msgEl.style, { fontSize: '13px', color: '#333', marginBottom: '10px', whiteSpace: 'pre-wrap' });
      confirmCard.appendChild(msgEl);

      if (items && items.length) {
        const listWrap = document.createElement('div');
        Object.assign(listWrap.style, {
          maxHeight: '240px',
          overflow: 'auto',
          border: '1px solid #eee',
          background: '#fafafa',
          padding: '8px',
          borderRadius: '6px',
          fontFamily: 'monospace',
          fontSize: '13px',
          color: '#111',
          marginBottom: '10px'
        });
        // show items (limit to e.g. first 500 lines to avoid huge modals)
        const maxToShow = 500;
        const showItems = items.slice(0, maxToShow);
        showItems.forEach(it => {
          const line = document.createElement('div');
          line.textContent = it;
          Object.assign(line.style, { padding: '2px 0' });
          listWrap.appendChild(line);
        });
        if (items.length > maxToShow) {
          const more = document.createElement('div');
          more.textContent = `... and ${items.length - maxToShow} more`;
          Object.assign(more.style, { padding: '4px 0', color: '#666', fontSize: '12px' });
          listWrap.appendChild(more);
        }
        confirmCard.appendChild(listWrap);
      }

      const btnRow = document.createElement('div');
      Object.assign(btnRow.style, { display: 'flex', justifyContent: 'flex-end', gap: '8px' });

      const cancelBtn = document.createElement('button');
      cancelBtn.textContent = 'Cancel';
      Object.assign(cancelBtn.style, {
        padding: '8px 12px',
        borderRadius: '10%',
        border: '1px solid #ddd',
        background: '#f7f9fb',
        color: '#000',
        cursor: 'pointer'
      });

      const okBtn = document.createElement('button');
      okBtn.textContent = 'Proceed';
      Object.assign(okBtn.style, {
        padding: '8px 12px',
        borderRadius: '10%',
        border: '1px solid #0b66ff',
        background: 'linear-gradient(180deg, #dff0ff, #d6ecff)',
        color: '#000',
        cursor: 'pointer'
      });

      btnRow.appendChild(cancelBtn);
      btnRow.appendChild(okBtn);
      confirmCard.appendChild(btnRow);
      confirmOverlay.appendChild(confirmCard);
      document.body.appendChild(confirmOverlay);

      function cleanup() {
        if (confirmOverlay.parentNode) confirmOverlay.parentNode.removeChild(confirmOverlay);
        window.removeEventListener('keydown', onKey);
        if (confirmCleanup) confirmCleanup();
      }
      
      // Set up tolerance zone for confirmation modal
      const confirmCleanup = createPopupWithTolerance(confirmOverlay, confirmCard, () => {
        cleanup();
        resolve(false);
      }, 30);
      
      function onKey(e) {
        if (e.key === 'Escape') {
          cleanup();
          resolve(false);
        }
      }
      window.addEventListener('keydown', onKey);

      cancelBtn.addEventListener('click', () => {
        cleanup();
        resolve(false);
      });
      okBtn.addEventListener('click', () => {
        cleanup();
        resolve(true);
      });
    });
  }

  // Parse an edited block (what the preview shows) into newRawPath and newCode.
  // Accepts both inline-comment style and path-before-fence (.txt) style.
  function parseEditedBlockToNewValues(text) {
    const lines = String(text || '').split(/\r?\n/);
    // skip leading empty lines
    let idx = 0;
    while (idx < lines.length && lines[idx].trim() === '') idx++;

    if (idx >= lines.length) return { newRawPath: undefined, newCode: '' };

    // If first non-empty line is NOT a fence, assume path-before-fence style
    if (!lines[idx].startsWith('```')) {
      const candidatePathLine = lines[idx].trim();
      // find fence
      let fenceStart = idx + 1;
      while (fenceStart < lines.length && !lines[fenceStart].startsWith('```')) fenceStart++;
      if (fenceStart >= lines.length) {
        // no fence found; treat everything after first line as code
        const codeLines = lines.slice(idx + 1);
        return { newRawPath: candidatePathLine, newCode: codeLines.join('\n') };
      } else {
        // fence found
        let fenceEnd = fenceStart + 1;
        while (fenceEnd < lines.length && !lines[fenceEnd].startsWith('```')) fenceEnd++;
        const codeLines = lines.slice(fenceStart + 1, fenceEnd < lines.length ? fenceEnd : lines.length);
        return { newRawPath: candidatePathLine, newCode: codeLines.join('\n') };
      }
    } else {
      // starts with fence
      const fenceLine = lines[idx];
      let fenceEnd = idx + 1;
      while (fenceEnd < lines.length && !lines[fenceEnd].startsWith('```')) fenceEnd++;
      const firstInside = (lines[idx + 1] || '').trim();
      const parsedComment = parseCommentLine(firstInside);
      let newRawPath;
      let codeStartIndex;
      if (parsedComment && isPathLike(parsedComment.path)) {
        newRawPath = parsedComment.path;
        codeStartIndex = idx + 2;
      } else {
        // No parseable comment: we treat the entire content inside fence as code
        newRawPath = undefined;
        codeStartIndex = idx + 1;
      }
      const codeLines = lines.slice(codeStartIndex, fenceEnd < lines.length ? fenceEnd : lines.length);
      return { newRawPath, newCode: codeLines.join('\n') };
    }
  }

  // Helper to compute the counts (how many inline-paths looked full vs relative)
  function computeDetectionCountsFromText(text) {
    const parsed = parseFileBlocks(String(text || ''));
    const total = parsed.length;
    let fullCount = 0;
    for (const p of parsed) {
      if (p.isLikelyFull) fullCount++;
    }
    const relCount = total - fullCount;
    return { total, fullCount, relCount };
  }

  // Helper to compute displayRaw (the path string shown inside the preview path input) for a given parsed entry and selected mode.
  // selectedMode: 'full' | 'relative'
  function computeDisplayRawForPreview(p, selectedMode, targetFolderVal) {
    // p is a parsed block (rawPath, isLikelyFull, etc.)
    try {
      if (selectedMode === 'full') {
        if (p.isLikelyFull) {
          let cand = String(p.rawPath || '');
          // expand tilde for display clarity
          if (cand.startsWith('~')) cand = expandTilde(cand);
          return cand;
        } else {
          // show the combined path (so preview displays an absolute/fully-qualified path when possible)
          return combineRawWithFolder(p.rawPath || '', targetFolderVal);
        }
      } else {
        // 'relative' mode: prefer a relative-looking display value
        if (p.isLikelyFull) {
          const safe = safeResolveUnderProject(projectRoot, p.rawPath);
          // show the relative path under project root (or fallback to the original raw path)
          return safe.relPath || String(p.rawPath || '');
        } else {
          return p.rawPath;
        }
      }
    } catch (e) {
      return p.rawPath || '';
    }
  }

  // Helper to compute effective raw used for writing files (prior to safeResolve)
  // This ensures consistent behavior between Preview and Auto edit.
  function computeEffectiveRawForWrite(p, selectedMode, targetFolderVal) {
    if (selectedMode === 'full') {
      if (p.isLikelyFull) {
        let cand = String(p.rawPath || '');
        if (cand.startsWith('~')) cand = expandTilde(cand);
        return cand;
      } else {
        return combineRawWithFolder(p.rawPath || '', targetFolderVal);
      }
    } else {
      // relative mode: convert absolute-looking raw into a project-relative path (if possible)
      if (p.isLikelyFull) {
        const safe = safeResolveUnderProject(projectRoot, p.rawPath);
        const rel = safe.relPath || path.basename(String(p.rawPath || ''));
        return combineRawWithFolder(rel, targetFolderVal);
      } else {
        return combineRawWithFolder(p.rawPath || '', targetFolderVal);
      }
    }
  }

  // Build preview modal which shows ASCII tree and file content viewer (editable).
  // NOTE: preview no longer shows per-file Relative/Full toggles. It shows the full code block (with fence and path)
  // that will be inserted into the main textarea; Saving will parse that block and update the main textarea.
  function openPreviewModal(parsedFiles) {
    // Build a list of relPaths (unique) using the safe.relPath we already populated for display
    const relSet = new Set(parsedFiles.map(p => p.safe.relPath));
    const relPaths = Array.from(relSet);

    const nested = buildNestedTree(relPaths);
    const lines = renderAsciiTreeLines(nested);

    // create preview overlay
    const pov = document.createElement('div');
    Object.assign(pov.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      width: '100vw',
      height: '100vh',
      background: 'rgba(0,0,0,0.5)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      zIndex: '11000'
    });

    const pCard = document.createElement('div');
    Object.assign(pCard.style, {
      width: 'min(1000px, 96vw)',
      height: '80vh',
      background: '#fff',
      display: 'flex',
      borderRadius: '8px',
      overflow: 'hidden',
      boxShadow: '0 10px 30px rgba(0,0,0,0.25)',
      position: 'relative'
    });

    // Close button bottom-right inside preview card
    const previewCloseBtn = document.createElement('button');
    previewCloseBtn.textContent = 'Close';
    Object.assign(previewCloseBtn.style, {
      position: 'absolute',
      bottom: '8px',
      right: '8px',
      padding: '6px 8px',
      borderRadius: '8px',
      border: '1px solid #ddd',
      background: '#f7f9fb',
      color: '#000',
      cursor: 'pointer',
      zIndex: 11100
    });
    previewCloseBtn.addEventListener('click', () => {
      closePreview();
    });
    pCard.appendChild(previewCloseBtn);

    // Left: tree (scrollable)
    const left = document.createElement('div');
    Object.assign(left.style, {
      width: '40%',
      minWidth: '260px',
      background: '#fafafa',
      padding: '12px',
      boxSizing: 'border-box',
      overflowY: 'auto',
      fontFamily: 'monospace',
      fontSize: '13px',
      borderRight: '1px solid #eee'
    });

    // Stats at top
    const filesCount = parsedFiles.length;
    const uniqueDirs = new Set();
    let totalBytes = 0;
    for (const p of parsedFiles) {
      const dir = path.dirname(p.safe.relPath || '');
      if (dir && dir !== '.') uniqueDirs.add(dir);
      totalBytes += (p.code ? p.code.length : 0);
    }
    const stats = document.createElement('div');
    stats.textContent = `Files: ${filesCount}    Estimated bytes: ${totalBytes}    Folders: ${uniqueDirs.size}`;
    Object.assign(stats.style, { marginBottom: '8px', fontSize: '12px', color: '#333' });
    left.appendChild(stats);

    // render each ASCII line as clickable element when it's a file
    lines.forEach((ln) => {
      const el = document.createElement('div');
      el.textContent = ln.text;
      Object.assign(el.style, {
        padding: '2px 4px',
        whiteSpace: 'pre',
        cursor: ln.isFile ? 'pointer' : 'default',
        color: ln.isFile ? '#0b66ff' : '#111'
      });
      if (ln.isFile) {
        el.dataset.rel = ln.relPath;
      }
      left.appendChild(el);
    });

    // Right: content viewer (with header and footer for buttons)
    const right = document.createElement('div');
    Object.assign(right.style, {
      width: '60%',
      padding: '12px',
      boxSizing: 'border-box',
      overflowY: 'auto',
      fontFamily: 'monospace',
      fontSize: '13px',
      display: 'flex',
      flexDirection: 'column'
    });

    const rightHeaderRow = document.createElement('div');
    Object.assign(rightHeaderRow.style, {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginBottom: '8px'
    });

    const rightHeader = document.createElement('div');
    rightHeader.textContent = 'File content (full code block editable)';
    Object.assign(rightHeader.style, { fontWeight: '700', fontSize: '14px' });
    rightHeaderRow.appendChild(rightHeader);

    right.appendChild(rightHeaderRow);

    const rightBodyWrap = document.createElement('div');
    Object.assign(rightBodyWrap.style, {
      border: '1px solid #eee',
      padding: '8px',
      borderRadius: '6px',
      minHeight: '360px',
      background: '#fff',
      color: '#000',
      display: 'flex',
      flexDirection: 'column',
      gap: '8px'
    });

    // initial message
    const initialMsg = document.createElement('div');
    initialMsg.textContent = 'Pick a file to check its content!';
    rightBodyWrap.appendChild(initialMsg);

    right.appendChild(rightBodyWrap);

    // Footer row for Cancel/Save (kept visible but Save hidden until edits)
    const footer = document.createElement('div');
    Object.assign(footer.style, {
      display: 'flex',
      justifyContent: 'flex-end',
      gap: '8px',
      marginTop: '10px'
    });

    const cancelBtn = document.createElement('button');
    cancelBtn.textContent = 'Cancel';
    Object.assign(cancelBtn.style, {
      padding: '8px 12px',
      borderRadius: '10%',
      border: '1px solid #ddd',
      background: '#f7f9fb',
      color: '#000',
      cursor: 'pointer'
    });

    const saveBtn = document.createElement('button');
    saveBtn.textContent = 'Save';
    Object.assign(saveBtn.style, {
      padding: '8px 12px',
      borderRadius: '10%', // or '10px' if you prefer rounded corners in pixels
      border: "1px solid #c00",
      background: 'linear-gradient(180deg, #ffd6d6, #ffb3b3)',
      color: '#000',
      cursor: 'pointer',
      display: 'none' // hidden until edits happen
    });

    footer.appendChild(cancelBtn);
    footer.appendChild(saveBtn);

    right.appendChild(footer);

    // clicking Cancel closes the preview
    cancelBtn.addEventListener('click', () => {
      closePreview();
    });

    // We'll keep a reference to the currently shown parsed file so save can work
    let currentParsedFile = null;
    let currentBlockEditor = null;
    let currentInitialBlockText = null;

    // Helper to update left tree entry when file path changes
    function updateLeftNodeRel(oldRel, newRel) {
      try {
        const node = left.querySelector(`[data-rel="${oldRel}"]`);
        if (!node) return;
        // update dataset
        node.dataset.rel = newRel;
        // replace last segment of visible text with new base name
        const text = node.textContent || '';
        const base = path.basename(newRel);
        // keep prefix (everything up to last non-slash chunk)
        const newText = text.replace(/[^\/\\]+(?:\/?)\s*$/, base);
        node.textContent = newText;
        // make it look clickable still
        node.style.color = '#0b66ff';
      } catch (e) {
        // ignore
      }
    }

    // Clicking a file line should display its content (and allow editing)
    left.addEventListener('click', (e) => {
      const target = e.target;
      if (!target || !target.dataset) return;
      const rel = target.dataset.rel;
      if (!rel) return;
      // find parsedFile for rel
      const pf = parsedFiles.find(p => p.safe.relPath === rel);
      if (!pf) return;

      currentParsedFile = pf;

      // Clear right body wrap and render path input + annotations + editable editor
      rightBodyWrap.innerHTML = '';

      // Path input (editable) - no per-file relative/full toggle in preview
      const pathRow = document.createElement('div');
      Object.assign(pathRow.style, { display: 'flex', gap: '8px', alignItems: 'center' });

      const pathInput = document.createElement('input');
      pathInput.type = 'text';

      // Show the display raw path that was computed when preview was opened
      pathInput.value = pf.displayRaw || (pf.rawPath || '');
      Object.assign(pathInput.style, {
        width: '100%',
        fontFamily: 'monospace',
        fontSize: '13px',
        padding: '6px 8px',
        borderRadius: '6px',
        border: '1px solid #e6eaf0',
        boxSizing: 'border-box',
        background: '#fff',
        color: '#000'
      });
      pathRow.appendChild(pathInput);

      // small detected label (show original detection for this file)
      const detectedLabel = document.createElement('div');
      detectedLabel.style.fontSize = '12px';
      detectedLabel.style.color = '#666';
      detectedLabel.style.marginLeft = '6px';
      detectedLabel.textContent = pf.isLikelyFull ? 'Detected: Full' : 'Detected: Relative';
      pathRow.appendChild(detectedLabel);

      rightBodyWrap.appendChild(pathRow);

      // Annotations: show up to 2 lines (plain text)
      if (pf.annotations && pf.annotations.length) {
        const annWrap = document.createElement('div');
        annWrap.style.fontSize = '12px';
        annWrap.style.color = '#444';
        annWrap.style.whiteSpace = 'pre-wrap';
        annWrap.style.padding = '4px 6px';
        annWrap.style.background = '#f6f8fa';
        annWrap.style.borderRadius = '4px';
        annWrap.style.border = '1px solid #f0f2f4';
        annWrap.textContent = pf.annotations.join('\n');
        rightBodyWrap.appendChild(annWrap);
      } else {
        // keep small spacer for consistent layout
        const spacer = document.createElement('div');
        spacer.style.height = '6px';
        rightBodyWrap.appendChild(spacer);
      }

      // Create editable textarea for the full code block (includes fence + path comment or path-line + fence).
      const blockEditor = document.createElement('textarea');
      Object.assign(blockEditor.style, {
        width: '100%',
        height: 'calc(100% - 120px)',
        minHeight: '220px',
        fontFamily: 'monospace',
        fontSize: '13px',
        padding: '8px',
        borderRadius: '6px',
        border: '1px solid #e6eaf0',
        boxSizing: 'border-box',
        resize: 'vertical',
        background: '#fff',
        color: '#000'
      });

      // Build a full code block representation for editing
      let blockText = '';
      if (pf.inlinePath) {
        // construct comment line using original prefix/suffix if available, but use pf.displayRaw for the path string shown
        const cf = commentForLang(pf.lang);
        const prefix = (pf.comment && pf.comment.prefix) ? pf.comment.prefix : (cf.prefix || '# ');
        const suffix = (pf.comment && pf.comment.suffix) ? pf.comment.suffix : (cf.suffix || '');
        const fenceLine = '```' + (pf.lang || '');
        blockText = [fenceLine, `${prefix}${pf.displayRaw || pf.rawPath || ''}`, pf.code || '', '```'].join('\n');
      } else {
        // path-before-fence (.txt) style - show path line above the fence
        const fenceLine = '```' + (pf.lang || '');
        blockText = [(pf.displayRaw || pf.rawPath || ''), fenceLine, pf.code || '', '```'].join('\n');
      }

      blockEditor.value = blockText;
      rightBodyWrap.appendChild(blockEditor);

      // status small line below editor
      const localStatus = document.createElement('div');
      localStatus.style.fontSize = '12px';
      localStatus.style.color = '#333';
      localStatus.style.minHeight = '18px';
      rightBodyWrap.appendChild(localStatus);

      // initialize save state
      currentBlockEditor = blockEditor;
      currentInitialBlockText = blockText;
      saveBtn.style.display = 'none';
      saveBtn.disabled = true;

      // detect edits -> enable show save button
      function checkChanged() {
        const changedBlock = blockEditor.value !== (currentInitialBlockText || '');
        const changedPath = pathInput.value !== (pf.displayRaw || pf.rawPath || '');
        if (changedBlock || changedPath) {
          saveBtn.style.display = 'inline-block';
          saveBtn.disabled = false;
        } else {
          saveBtn.style.display = 'none';
          saveBtn.disabled = true;
        }
      }

      blockEditor.addEventListener('input', checkChanged);
      pathInput.addEventListener('input', checkChanged);

      // Save handler (parses the edited block and uses updateTextareaForFile to update the main textarea)
      saveBtn.onclick = async () => {
        if (!currentParsedFile || !currentBlockEditor) return;
        // Parse edited block to extract newRawPath and newCode
        // But if user edited the path input separately use that as newRawPath if provided.
        const editedBlockText = currentBlockEditor.value;
        const parsedEdited = parseEditedBlockToNewValues(editedBlockText);
        // If the path input was edited, prefer that newRawPath
        const pathFromInput = (typeof pathInput.value === 'string' && pathInput.value.trim() !== '') ? pathInput.value.trim() : undefined;
        const newRawPath = (typeof pathFromInput === 'string' && pathFromInput !== '') ? pathFromInput : parsedEdited.newRawPath;
        const newCode = parsedEdited.newCode === undefined ? currentParsedFile.code : parsedEdited.newCode;

        const oldRel = currentParsedFile.safe.relPath;
        const result = updateTextareaForFile(oldRel, newCode, newRawPath, currentParsedFile.targetFolder || '');
        if (result && result.success) {
          localStatus.textContent = 'Saved to main input.';
          statusDiv.textContent = `Saved changes for ${result.newRelPath} to main input.`;

          // update the parsedFile.code and rawPath/safe/inline flags so subsequent edits compare correctly
          currentParsedFile.code = newCode;
          currentParsedFile.rawPath = newRawPath || currentParsedFile.rawPath;
          currentParsedFile.safe = safeResolveUnderProject(projectRoot, combineRawWithFolder(currentParsedFile.rawPath, currentParsedFile.targetFolder || ''));
          // Keep inlinePath as reported by updateTextareaForFile
          currentParsedFile.inlinePath = !!result.newInline;
          currentInitialBlockText = currentBlockEditor.value;
          saveBtn.style.display = 'none';
          saveBtn.disabled = true;

          // Update left node to reflect new rel path
          try {
            const newRel = result.newRelPath || currentParsedFile.safe.relPath;
            updateLeftNodeRel(oldRel, newRel);
            currentParsedFile.safe.relPath = newRel;
          } catch (e) {
            // ignore
          }
        } else {
          localStatus.textContent = 'Failed to save: could not locate corresponding block in main input.';
          statusDiv.textContent = 'Failed to save changes: could not locate block.';
        }
      };
    });

    // Close preview handlers with tolerance zone
    function closePreview() {
      if (pov.parentNode) pov.parentNode.removeChild(pov);
      window.removeEventListener('keydown', onKey);
      if (previewCleanup) previewCleanup();
    }
    
    // Set up tolerance zone for preview modal
    const previewCleanup = createPopupWithTolerance(pov, pCard, closePreview, 30);
    
    function onKey(ev) {
      if (ev.key === 'Escape') {
        closePreview();
      }
    }
    window.addEventListener('keydown', onKey);

    pCard.appendChild(left);
    pCard.appendChild(right);
    pov.appendChild(pCard);
    document.body.appendChild(pov);
  }

  //
  // Detection UI wiring
  //
  let detectionMode = 'auto'; // 'auto' | 'relative' | 'full'
  function applyDetectionButtonStyles() {
    detAutoBtn.style.background = detectionMode === 'auto' ? '#eef7ff' : '#fff';
    detRelBtn.style.background = detectionMode === 'relative' ? '#eef7ff' : '#fff';
    detFullBtn.style.background = detectionMode === 'full' ? '#eef7ff' : '#fff';
  }
  detAutoBtn.addEventListener('click', () => {
    detectionMode = 'auto';
    applyDetectionButtonStyles();
    updateDetectionSummary(); // recompute summary
  });
  detRelBtn.addEventListener('click', () => {
    detectionMode = 'relative';
    applyDetectionButtonStyles();
    updateDetectionSummary();
  });
  detFullBtn.addEventListener('click', () => {
    detectionMode = 'full';
    applyDetectionButtonStyles();
    updateDetectionSummary();
  });

  // Update summary text based on current textarea contents
  function updateDetectionSummary() {
    try {
      const { total, fullCount, relCount } = computeDetectionCountsFromText(textarea.value || '');
      let usingMode = detectionMode;
      if (detectionMode === 'auto') {
        usingMode = (fullCount > relCount) ? 'full' : 'relative';
      }
      detectionSummary.textContent = `Detected: ${relCount} relative, ${fullCount} full. Using: ${detectionMode === 'auto' ? ('Auto → ' + (usingMode === 'full' ? 'Full' : 'Relative')) : (detectionMode === 'full' ? 'Force full' : 'Force relative')}`;
    } catch (e) {
      detectionSummary.textContent = 'Detected: 0 relative, 0 full. Using: Auto';
    }
  }

  // debounce detection summary updates while typing
  let detectTimer = null;
  textarea.addEventListener('input', () => {
    if (detectTimer) clearTimeout(detectTimer);
    detectTimer = setTimeout(() => {
      updateDetectionSummary();
      detectTimer = null;
    }, 220);
  });

  // initial detection summary
  (async () => {
    try {
      updateDetectionSummary();
      applyDetectionButtonStyles();
    } catch (e) {
      console.error('Initial detection summary failed', e);
    }
  })();

  //
  // Core Auto edit routine (reusable by main button and history session views)
  //
  async function runAutoEditFromText({ sourceText, folderRel, detectionModeOverride, statusElement, closeOverlayAfter = true } = {}) {
    const status = statusElement || statusDiv;
    if (status) status.textContent = 'Parsing input...';

    const text = sourceText || '';
    const parsed = parseFileBlocks(text);
    if (!parsed.length) {
      if (status) status.textContent = 'No file/code block pairs found in the input. Nothing to do.';
      return;
    }

    const targetFolderVal = normalizeFolderRel(folderRel || '');

    // compute counts & chosen mode
    const fullCount = parsed.filter(p => p.isLikelyFull).length;
    const relCount = parsed.length - fullCount;
    let selectedMode = typeof detectionModeOverride === 'string' ? detectionModeOverride : detectionMode;
    if (selectedMode === 'auto') selectedMode = (fullCount > relCount) ? 'full' : 'relative';
    if (status) status.textContent = `Detected ${relCount} relative, ${fullCount} full. Using: ${selectedMode === 'full' ? 'Full' : 'Relative'}. Preparing writes...`;

    // Resolve safe paths and prepare writes. Use the folder value to compute the actual effective path that will be written.
    const prepared = parsed.map(p => {
      const effectiveRaw = computeEffectiveRawForWrite(p, selectedMode, targetFolderVal);
      const safe = safeResolveUnderProject(projectRoot, effectiveRaw);
      return { rawPath: p.rawPath, effectiveRaw, code: p.code || '', lang: p.lang || '', safe };
    });

    // Check for existing files/directories that would be overwritten or conflict
    const existingFiles = [];
    const existingDirs = [];
    for (const item of prepared) {
      try {
        const st = await fs.stat(item.safe.absPath);
        if (st.isFile()) {
          existingFiles.push(item);
        } else if (st.isDirectory()) {
          existingDirs.push(item);
        } else {
          existingFiles.push(item);
        }
      } catch (err) {
        // ENOENT means does not exist -> okay
        if (err && err.code && err.code !== 'ENOENT') {
          console.warn('stat error while checking existing path', item.safe.absPath, err);
        }
      }
    }

    // If any conflicts found, ask user to confirm replacement
    let preExistingAbsPaths = new Set();
    if (existingFiles.length || existingDirs.length) {
      let messageParts = [];
      if (existingFiles.length) {
        messageParts.push(`The following ${existingFiles.length} file(s) already exist and their contents will be overwritten:`);
        messageParts = messageParts.concat(existingFiles.map(i => i.safe.relPath));
      }
      if (existingDirs.length) {
        messageParts.push('');
        messageParts.push(`The following ${existingDirs.length} path(s) exist and are directories. Overwriting these paths will delete the directory contents and replace them with files:`);
        messageParts = messageParts.concat(existingDirs.map(i => i.safe.relPath));
      }
      messageParts.push('');
      messageParts.push('Do you want to proceed?');

      const itemsList = [].concat(
        existingFiles.map(i => i.safe.relPath),
        existingDirs.map(i => i.safe.relPath)
      );

      const userConfirmed = await showConfirmationModal('Confirm overwrite / replace', messageParts.join('\n'), itemsList);
      if (!userConfirmed) {
        if (status) status.textContent = 'Operation cancelled by user. No files were written.';
        return;
      }

      // Remove any previous snapshot to enforce "single snapshot" policy, then back up existing files/dirs
      try {
        await clearAutoEditSnapshot();
      } catch (e) {
        // ignore
      }
      await ensureAutoEditBackupDirs();

      if (status) status.textContent = 'Backing up existing files and directories...';

      // Backup files (best-effort)
      for (const f of existingFiles) {
        try {
          await backupPathRecursively(f.safe.absPath, f.safe.relPath);
        } catch (err) {
          console.warn('Failed to backup file before auto edit:', f.safe.absPath, err && err.message);
        }
      }

      // Backup directories (recursively)
      for (const d of existingDirs) {
        try {
          await backupPathRecursively(d.safe.absPath, d.safe.relPath);
        } catch (err) {
          console.warn('Failed to backup directory before auto edit:', d.safe.absPath, err && err.message);
        }
      }

      // Build set of existing absolute paths (for later determining which were newly created)
      preExistingAbsPaths = new Set([
        ...existingFiles.map(i => path.resolve(i.safe.absPath)),
        ...existingDirs.map(i => path.resolve(i.safe.absPath))
      ]);

      // If directories need to be removed, delete them now (recursively)
      if (existingDirs.length) {
        if (status) status.textContent = 'Removing existing directories that conflict...';
        for (const dirItem of existingDirs) {
          try {
            if (typeof fs.rm === 'function') {
              await fs.rm(dirItem.safe.absPath, { recursive: true, force: true });
            } else {
              await fs.rmdir(dirItem.safe.absPath, { recursive: true });
            }
          } catch (err) {
            console.error('Failed to remove directory', dirItem.safe.absPath, err);
          }
        }
      }
    }

    // Write files
    let createdCount = 0;
    let overwrittenCount = 0;
    const errors = [];

    const preExistingFilePaths = new Set(existingFiles.map(i => path.resolve(i.safe.absPath)));

    for (const item of prepared) {
      try {
        const dir = path.dirname(item.safe.absPath);
        await fs.mkdir(dir, { recursive: true });
        await fs.writeFile(item.safe.absPath, item.code, 'utf8');

        const abs = path.resolve(item.safe.absPath);
        if (preExistingFilePaths.has(abs)) {
          overwrittenCount++;
        } else {
          createdCount++;
        }
      } catch (err) {
        console.error('Failed to write file', item.safe.absPath, err);
        errors.push({ path: item.safe.relPath, error: err.message || String(err) });
      }
    }

    // Prepare metadata and persist snapshot (single snapshot)
    try {
      const addedRelPaths = prepared.filter(item => {
        const abs = path.resolve(item.safe.absPath);
        return !preExistingAbsPaths.has(abs);
      }).map(i => i.safe.relPath);

      const backedUpRelRoots = [
        ...existingFiles.map(i => i.safe.relPath),
        ...existingDirs.map(i => i.safe.relPath)
      ];

      await fs.mkdir(AUTOEDIT_DIR, { recursive: true });

      const meta = {
        timestamp: new Date().toISOString(),
        backedUpFiles: backedUpRelRoots,
        addedFiles: addedRelPaths
      };

      await fs.writeFile(AUTOEDIT_META, JSON.stringify(meta, null, 2), 'utf8');
    } catch (err) {
      console.warn('Failed to save auto-edit snapshot metadata:', err && err.message);
    }

    // Refresh directory tree in UI
    try {
      const tree = await scanDirectoryTree(projectRoot);
      const containerEl = document.getElementById('directory-viewer-content');
      if (containerEl) {
        containerEl.innerHTML = '';
        renderDirectoryTree(tree);
      }
    } catch (err) {
      console.error('Failed to refresh directory tree after Auto edit:', err);
    }

    if (status) {
      status.textContent = `Created ${createdCount} new file(s). Overwritten ${overwrittenCount} file(s). ${errors.length ? ('Errors: ' + errors.length) : ''}`;
    }
    if (errors.length) {
      console.warn('Auto edit errors:', errors);
    }

    if (closeOverlayAfter) {
      setTimeout(() => {
        try { closeOverlay(); } catch (e) { /* ignore */ }
      }, 900);
    }
  }

  //
  // Click handlers for preview and auto edit
  //
  previewBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const text = textarea.value || '';
    const parsed = parseFileBlocks(text);
    if (!parsed.length) {
      statusDiv.textContent = 'No file/code block pairs found in the input.';
      return;
    }

    const targetFolderVal = normalizeFolderRel(folderInput.value);

    // compute counts
    const fullCount = parsed.filter(p => p.isLikelyFull).length;
    const relCount = parsed.length - fullCount;
    // determine selected mode
    let selectedMode = detectionMode;
    if (detectionMode === 'auto') selectedMode = (fullCount > relCount) ? 'full' : 'relative';

    statusDiv.textContent = `Detected ${relCount} relative, ${fullCount} full. Using: ${selectedMode === 'full' ? 'Full' : 'Relative'}.`;

    // Map each parsed entry to safe path under project for display; keep rawPath as-is (so editing still modifies original blocks)
    const parsedSafe = parsed.map(p => {
      const displayRaw = computeDisplayRawForPreview(p, selectedMode, targetFolderVal);
      const effectiveRaw = computeEffectiveRawForWrite(p, selectedMode, targetFolderVal);
      const safe = safeResolveUnderProject(projectRoot, effectiveRaw);
      // Determine if the original raw path looked full
      const isLikelyFull = isLikelyAbsolutePathString(p.rawPath || '');
      // compute original absolute if it looked full (expand tilde)
      let origAbs = null;
      if (isLikelyFull) {
        try {
          let cand = String(p.rawPath || '');
          if ((cand.startsWith('"') && cand.endsWith('"')) || (cand.startsWith("'") && cand.endsWith("'"))) cand = cand.slice(1, -1);
          if (cand.startsWith('~')) cand = expandTilde(cand);
          origAbs = path.resolve(cand);
        } catch (e) {
          origAbs = null;
        }
      }
      // keep original parsed info but attach safe/display info and the target folder used
      return Object.assign({}, p, { safe, displayRaw, targetFolder: targetFolderVal, isLikelyFull, origAbs });
    });

    openPreviewModal(parsedSafe);
  });

  autoBtn.addEventListener('click', async (e) => {
    e.stopPropagation();
    await runAutoEditFromText({
      sourceText: textarea.value || '',
      folderRel: folderInput.value,
      detectionModeOverride: detectionMode,
      statusElement: statusDiv,
      closeOverlayAfter: true,
    });
  });

  //
  // Folder input validation wiring
  //
  let folderValidateTimer = null;
  async function checkFolderExistsAndUpdateUI(folderRel) {
    const normalized = normalizeFolderRel(folderRel || '');
    const candidate = normalized || '.'; // '.' means project root
    // compute safe path (this will clamp to project root if outside)
    const safe = safeResolveUnderProject(projectRoot, candidate);
    try {
      const st = await fs.stat(safe.absPath);
      if (st.isDirectory()) {
        // exists
        folderInput.style.border = '1px solid #d6dde6';
        folderStatus.textContent = `Applies to: ./${safe.relPath || ''}`; // show resolved rel
        folderMessage.textContent = '';
        return { exists: true, safe };
      } else {
        folderInput.style.border = '1px solid #e6b800';
        folderStatus.textContent = `Applies to: ./${safe.relPath || ''}`;
        folderMessage.textContent = 'This folder does not exist, but we will create it for you if you insist.';
        return { exists: false, safe };
      }
    } catch (err) {
      // not found or other error
      folderInput.style.border = '1px solid #e6b800';
      folderStatus.textContent = `Applies to: ./${safe.relPath || ''}`;
      folderMessage.textContent = 'This folder does not exist, but we will create it for you if you insist.';
      return { exists: false, safe, error: err && err.code ? err.code : String(err) };
    }
  }

  // debounce input validation
  folderInput.addEventListener('input', () => {
    if (folderValidateTimer) clearTimeout(folderValidateTimer);
    folderValidateTimer = setTimeout(() => {
      checkFolderExistsAndUpdateUI(folderInput.value).catch((e) => {
        console.error('Folder validation failed', e);
      });
      folderValidateTimer = null;
    }, 220);
  });

  // initial validation
  (async () => {
    try {
      await checkFolderExistsAndUpdateUI(folderInput.value);
    } catch (e) {
      console.error('Initial folder validation failed', e);
    }
  })();

  //
  // Detect if an auto-edit snapshot exists and enable "Un-edit" button
  //
  (async () => {
    try {
      const meta = await loadAutoEditMeta();
      if (meta && meta.timestamp) {
        uneditBtn.style.display = 'inline-block';
        uneditBtn.title = `Un-edit snapshot from ${meta.timestamp}`;
      } else {
        uneditBtn.style.display = 'none';
      }
    } catch (err) {
      uneditBtn.style.display = 'none';
    }
  })();

  // Un-edit handler: restore snapshot and delete added files
  uneditBtn.addEventListener('click', async (ev) => {
    ev.stopPropagation();
    statusDiv.textContent = 'Preparing Un-edit...';
    let meta;
    try {
      const raw = await fs.readFile(AUTOEDIT_META, 'utf8');
      meta = JSON.parse(raw);
    } catch (err) {
      statusDiv.textContent = 'No auto-edit snapshot found.';
      return;
    }

    const added = Array.isArray(meta.addedFiles) ? meta.addedFiles : [];
    const backed = Array.isArray(meta.backedUpFiles) ? meta.backedUpFiles : [];

    const itemsForModal = [].concat(
      added.map(p => `To delete: ${p}`),
      backed.map(p => `To restore: ${p}`)
    );

    const messageParts = [
      `Snapshot recorded: ${meta.timestamp}`,
      '',
      `Files to delete (created by auto edit):`,
      ...(added.length ? added : ['(none)']),
      '',
      `Files/directories to restore (original contents backed up):`,
      ...(backed.length ? backed : ['(none)']),
      '',
      'Proceed with Un-edit? This will restore backed-up files and delete files that were created by the auto edit.'
    ];

    const userConfirmed = await showConfirmationModal('Un-edit confirmation', messageParts.join('\n'), itemsForModal);
    if (!userConfirmed) {
      statusDiv.textContent = 'Un-edit cancelled';
      return;
    }

    statusDiv.textContent = 'Performing Un-edit (restoring backups and removing added files)...';
    const errors = [];

    // Delete added files
    for (const rel of added) {
      const targetAbs = path.resolve(projectRoot, rel);
      try {
        const st = await fs.stat(targetAbs);
        if (st.isFile()) {
          await fs.unlink(targetAbs);
        } else if (st.isDirectory()) {
          if (typeof fs.rm === 'function') {
            await fs.rm(targetAbs, { recursive: true, force: true });
          } else {
            await fs.rmdir(targetAbs, { recursive: true });
          }
        } else {
          // try unlink
          try { await fs.unlink(targetAbs); } catch (_) {}
        }
      } catch (err) {
        if (err && err.code === 'ENOENT') {
          // already gone — fine
        } else {
          errors.push({ path: rel, error: err && err.message ? err.message : String(err) });
        }
      }
    }

    // Restore backed up files/directories
    for (const rel of backed) {
      const backupSrc = path.join(AUTOEDIT_BACKUPS, rel);
      const targetAbs = path.resolve(projectRoot, rel);
      try {
        const st = await fs.stat(backupSrc);
        if (st.isFile()) {
          await fs.mkdir(path.dirname(targetAbs), { recursive: true });
          if (typeof fs.copyFile === 'function') {
            await fs.copyFile(backupSrc, targetAbs);
          } else {
            const data = await fs.readFile(backupSrc);
            await fs.writeFile(targetAbs, data);
          }
        } else if (st.isDirectory()) {
          // restore directory recursively
          await copyBackupDirToProject(backupSrc, targetAbs);
        }
      } catch (err) {
        errors.push({ path: rel, error: err && err.message ? err.message : String(err) });
      }
    }

    // Remove snapshot folder
    try {
      await clearAutoEditSnapshot();
    } catch (e) {
      // ignore
    }

    // Refresh directory tree in UI
    try {
      const tree = await scanDirectoryTree(projectRoot);
      const containerEl = document.getElementById('directory-viewer-content');
      if (containerEl) {
        containerEl.innerHTML = '';
        renderDirectoryTree(tree);
      }
    } catch (err) {
      console.error('Failed to refresh directory tree after Un-edit:', err);
    }

    statusDiv.textContent = `Un-edit completed. Restored ${backed.length} item(s). Deleted ${added.length} item(s). ${errors.length ? ('Errors: ' + errors.length) : ''}`;
    if (errors.length) {
      console.warn('Un-edit errors:', errors);
    }

    uneditBtn.style.display = 'none';
  });
}

module.exports = { showAutoEdit };