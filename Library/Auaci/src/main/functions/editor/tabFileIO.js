// src/main/functions/editor/tabFileIO.js
// Helpers that read disk files and construct tab objects.
// Keeps file I/O outside of the tab manager for clarity.

const fs = require('fs').promises;
const path = require('path');
const tabUtils = require('./tabUtils');
const mediaUtils = require('./mediaUtils');

async function createFileTabFromPath(filePath, content) {
  // Throws on failure (caller should handle)
  const stats = await fs.stat(filePath);
  const id = tabUtils.createTabId();

  let fileContent = '';
  let mediaType = null;
  let mime = null;

  // If caller provided content explicitly
  if (typeof content !== 'undefined' && content !== null) {
    if (Buffer.isBuffer(content)) {
      const det = mediaUtils.detectMediaType(content, filePath);
      mediaType = det.type;
      mime = det.mime;
      if (mediaType === 'image') {
        fileContent = mediaUtils.bufferToDataUrl(content, mime);
      } else if (mediaType === 'audio') {
        // no textual preview for audio
        fileContent = '';
      } else {
        // For text or binary/unknown: try UTF-8 render (do not render as 0/1)
        try {
          fileContent = content.toString('utf8');
        } catch (_) {
          fileContent = '';
        }
      }
    } else {
      // Content is a string (maybe the dir viewer provided it)
      fileContent = String(content);
      try {
        const det = mediaUtils.detectMediaType(Buffer.from(fileContent, 'utf8'), filePath);
        mediaType = det.type;
        mime = det.mime;
      } catch (_) {
        mediaType = null;
      }
    }
  } else {
    // Read raw buffer and detect type
    const buffer = await fs.readFile(filePath);
    const det = mediaUtils.detectMediaType(buffer, filePath);
    mediaType = det.type;
    mime = det.mime;

    if (mediaType === 'image') {
      fileContent = mediaUtils.bufferToDataUrl(buffer, mime);
    } else if (mediaType === 'audio') {
      fileContent = ''; // no textual preview for audio
    } else {
      // For text or binary: try UTF-8 render (do not convert to binary bits)
      try {
        fileContent = buffer.toString('utf8');
      } catch (_) {
        fileContent = '';
      }
    }
  }

  const fileObj = {
    name: path.basename(filePath),
    size: stats.size,
    diskSize: stats.size,
    diskMtimeMs: stats.mtimeMs,
    path: filePath,
    content: fileContent,
    unsaved: false,
    savedContent: fileContent == null ? '' : String(fileContent),
    id
  };

  if (mediaType) fileObj.mediaType = mediaType;
  if (mime) fileObj.mime = mime;
  if (mediaType === 'image') fileObj.dataUrl = fileContent;

  return fileObj;
}

module.exports = { createFileTabFromPath };
