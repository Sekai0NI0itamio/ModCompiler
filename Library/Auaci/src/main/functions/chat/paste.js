// src/main/functions/chat/paste.js
// Uses united/fsUtils.readFileContent instead of invoking the binary inline.

const fs = require('fs').promises;
const path = require('path');
const { saveCurrentInputDraft } = require('./incrementalHistoryStorage');
const { readFileContent } = require('../united/fsUtils');
const { appendLog } = require('../united/logger');
const { ipcRenderer } = require('electron');

const logPath = '/tmp/events.log';

/**
 * Check if a path is a directory
 */
async function isDirectory(filePath) {
  try {
    const stat = await fs.stat(filePath);
    return stat.isDirectory();
  } catch (err) {
    console.log(`[paste] isDirectory check failed for ${filePath}:`, err.message);
    return false;
  }
}

/**
 * Check if a path exists
 */
async function pathExists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

/**
 * Check if a string looks like a file/folder path
 */
function looksLikePath(text) {
  if (!text || typeof text !== 'string') return false;
  const trimmed = text.trim();
  // Check for common path patterns
  // macOS/Linux absolute paths
  if (trimmed.startsWith('/')) return true;
  // macOS home directory
  if (trimmed.startsWith('~/')) return true;
  // Windows absolute paths (C:\, D:\, etc.)
  if (/^[A-Za-z]:[\\\/]/.test(trimmed)) return true;
  // UNC paths (\\server\share)
  if (trimmed.startsWith('\\\\')) return true;
  return false;
}

function setupPaste() {
  const userInput = document.getElementById('user-input');
  const filePreviews = document.getElementById('file-previews');
  const gptResponseIndicator = document.getElementById('gpt-response-indicator');

  const showDuplicateNotification = (fileName) => {
    if (!gptResponseIndicator) return;
    gptResponseIndicator.innerHTML = `
      <span class="gpt-indicator-text">This file is already pasted: ${fileName}</span>
    `;
    gptResponseIndicator.classList.add('visible');
    setTimeout(() => {
      if (!gptResponseIndicator) return;
      gptResponseIndicator.classList.remove('visible');
      gptResponseIndicator.innerHTML = `
        <span class="gpt-indicator-text">GPT is responding</span>
        <span class="gpt-indicator-dots">
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="dot"></span>
        </span>
      `;
    }, 2000);
  };

  userInput.addEventListener('paste', async (e) => {
    const types = e.clipboardData ? Array.from(e.clipboardData.types || []) : [];
    const hasClipboardFiles = (e.clipboardData && e.clipboardData.files && e.clipboardData.files.length > 0) ||
                              types.includes('Files') || types.includes('public.file-url') || types.includes('text/uri-list');

    // Always block native insertion to strip any formatting; we will insert plain text or tokens ourselves
    try { e.preventDefault(); e.stopPropagation(); } catch (_) {}

    function extractPlainText(evt) {
      try {
        if (evt && evt.clipboardData) {
          let t = evt.clipboardData.getData('text/plain');
          if (t && typeof t === 'string') return t;
          t = evt.clipboardData.getData('text');
          if (t && typeof t === 'string') return t;
          const html = evt.clipboardData.getData('text/html');
          if (html) {
            const div = document.createElement('div');
            div.innerHTML = html;
            return div.textContent || div.innerText || '';
          }
        }
      } catch (_) {}
      return '';
    }

    const text = extractPlainText(e);
    await appendLog(logPath, `[paste] Paste event - hasClipboardFiles: ${hasClipboardFiles}, text length: ${text.length}, types: ${types.join(', ')}`);
    
    const result = await ipcRenderer.invoke('get-pasted-files');
    await appendLog(logPath, `[paste] get-pasted-files result: ${JSON.stringify(result)}`);
    
    // Check if we got files from the native binary
    let filesToProcess = [];
    
    if (result && result.files && result.files.length > 0) {
      filesToProcess = result.files;
    } else if (result && result.clipboardData) {
      // Native binary didn't find files, but we have clipboard data
      // Try to extract file/folder paths from public.file-url
      const fileUrl = result.clipboardData['public.file-url'];
      if (fileUrl && fileUrl.startsWith('file://')) {
        try {
          // Decode the file URL to get the path
          const urlPath = decodeURIComponent(fileUrl.replace('file://', ''));
          // Remove trailing slash if present
          const cleanPath = urlPath.endsWith('/') ? urlPath.slice(0, -1) : urlPath;
          
          if (await pathExists(cleanPath)) {
            await appendLog(logPath, `[paste] Extracted path from file-url: ${cleanPath}`);
            filesToProcess = [{ path: cleanPath, name: path.basename(cleanPath) }];
          }
        } catch (err) {
          await appendLog(logPath, `[paste] Failed to parse file-url: ${err.message}`);
        }
      }
      
      // Also check text/uri-list format (can contain multiple file:// URLs)
      if (filesToProcess.length === 0 && result.clipboardData.availableTypes && 
          result.clipboardData.availableTypes.includes('text/uri-list')) {
        // The uri-list might be in the plain text
        const plainText = result.clipboardData['text/plain'] || '';
        if (plainText.includes('file://')) {
          const urls = plainText.split('\n').filter(line => line.startsWith('file://'));
          for (const url of urls) {
            try {
              const urlPath = decodeURIComponent(url.replace('file://', '').trim());
              const cleanPath = urlPath.endsWith('/') ? urlPath.slice(0, -1) : urlPath;
              if (await pathExists(cleanPath)) {
                filesToProcess.push({ path: cleanPath, name: path.basename(cleanPath) });
              }
            } catch (_) {}
          }
        }
      }
    }
    
    if (filesToProcess.length > 0) {
      const folders = [];
      const files = [];
      
      // First pass: categorize items as files or folders
      for (const file of filesToProcess) {
        try {
          const filePath = file.path;
          await appendLog(logPath, `[paste] Processing: ${filePath}`);
          
          // Check if path exists
          if (!await pathExists(filePath)) {
            await appendLog(logPath, `[paste] Path does not exist: ${filePath}`);
            continue;
          }
          
          // Check if it's a directory
          if (await isDirectory(filePath)) {
            await appendLog(logPath, `[paste] Detected folder: ${filePath}`);
            folders.push({ path: filePath, name: file.name || path.basename(filePath) });
            continue;
          }
          
          // It's a file - check for duplicates
          if ((window.droppedFiles || []).find(f => f.path === filePath)) {
            showDuplicateNotification(file.name);
            await appendLog(logPath, `⚠️ File already attached: ${filePath}`);
            continue;
          }
          
          // Use unified reader which prefers the native binary then falls back to fs
          const fileData = await readFileContent(filePath, { logPath });
          if (fileData) {
            files.push({ name: fileData.name, size: fileData.size, path: fileData.path, content: fileData.content });
          }
        } catch (err) {
          await appendLog(logPath, `[paste] Error processing ${file.path}: ${err && err.message ? err.message : String(err)}`);
        }
      }

      // Insert folder paths as text
      if (folders.length > 0) {
        const folderPaths = folders.map(f => f.path).join('\n');
        await appendLog(logPath, `[paste] Inserting ${folders.length} folder path(s) as text`);
        insertTextAtCursor(userInput, folderPaths);
      }

      // Insert file tokens
      if (files.length > 0) {
        // Insert inline file tokens into the contenteditable input
        try { require('./helpers/fileTokens').insertFileTokens(files); } catch (_) {}
        // Update droppedFiles + draft
        window.droppedFiles = (window.droppedFiles || []).concat(files);
        try { await saveCurrentInputDraft(document.getElementById('user-input')?.textContent || '', window.droppedFiles); } catch (_) {}
        await appendLog(logPath, `[paste] Pasted ${files.length} files (inline tokens)`);
      }
      
      // Trigger input for auto-resize/layout updates
      if (folders.length > 0 || files.length > 0) {
        try { userInput.dispatchEvent(new Event('input', { bubbles: true })); } catch (_) {}
      }
      
      // If we processed something, we're done
      if (folders.length > 0 || files.length > 0) {
        return;
      }
    }
    
    // No files from IPC or clipboard data, check if text looks like a path
    if (text) {
      // Check if the pasted text looks like a folder path
      if (looksLikePath(text)) {
        const trimmedPath = text.trim();
        await appendLog(logPath, `[paste] Text looks like path: ${trimmedPath}`);
        
        // Check if it exists and is a directory
        if (await pathExists(trimmedPath) && await isDirectory(trimmedPath)) {
          // Insert the full path as text
          await appendLog(logPath, `[paste] Inserting folder path as text: ${trimmedPath}`);
          insertTextAtCursor(userInput, trimmedPath);
          try { userInput.dispatchEvent(new Event('input', { bubbles: true })); } catch (_) {}
          return;
        }
      }
      
      // Insert plain text only (strip formatting) for both contenteditable and textarea
      insertTextAtCursor(userInput, text);
      await appendLog(logPath, `[paste] Pasted plain text`);
      // Trigger input so auto-resize expands to fit pasted content
      try { userInput.dispatchEvent(new Event('input', { bubbles: true })); } catch (_) {}
    }
  });
}

/**
 * Insert text at cursor position in input element
 */
function insertTextAtCursor(userInput, text) {
  if (userInput && userInput.tagName === 'TEXTAREA') {
    const start = userInput.selectionStart;
    const end = userInput.selectionEnd;
    const value = userInput.value;
    userInput.value = value.slice(0, start) + text + value.slice(end);
    userInput.selectionStart = userInput.selectionEnd = start + text.length;
  } else {
    try {
      // execCommand falls back to plain text insertion in most engines
      const ok = document.execCommand('insertText', false, text);
      if (!ok) {
        const sel = window.getSelection();
        if (sel && sel.rangeCount) {
          const range = sel.getRangeAt(0);
          range.deleteContents();
          range.insertNode(document.createTextNode(text));
          range.collapse(false);
          sel.removeAllRanges();
          sel.addRange(range);
        } else {
          userInput.appendChild(document.createTextNode(text));
        }
      }
    } catch (_) {
      // final fallback
      userInput.appendChild(document.createTextNode(text));
    }
  }
}

module.exports = { setupPaste };