// src/main/functions/editor/mediaUtils.js
// Utilities to detect media type and convert buffers for preview/rendering.

const path = require('path');

const IMAGE_EXTS = new Set(['.png', '.jpg', '.jpeg', '.gif', '.bmp', '.webp', '.svg', '.ico', '.tif', '.tiff', '.heic', '.heif']);
const AUDIO_EXTS = new Set(['.mp3', '.wav', '.ogg', '.m4a', '.flac', '.aac', '.aiff']);

function mimeForExt(ext) {
  const map = {
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.gif': 'image/gif',
    '.bmp': 'image/bmp',
    '.webp': 'image/webp',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
    '.tif': 'image/tiff',
    '.tiff': 'image/tiff',
    '.heic': 'image/heic',
    '.heif': 'image/heif',
    '.mp3': 'audio/mpeg',
    '.wav': 'audio/wav',
    '.ogg': 'audio/ogg',
    '.flac': 'audio/flac',
    '.m4a': 'audio/mp4',
    '.aac': 'audio/aac',
    '.aiff': 'audio/aiff'
  };
  return map[ext] || null;
}

/**
 * Very small heuristic to check whether a buffer looks like UTF-8/plain text.
 * Returns true for likely text, false for binary.
 */
function isLikelyText(buffer) {
  if (!buffer || buffer.length === 0) return true;
  const len = Math.min(buffer.length, 1024);
  let controlCount = 0;
  for (let i = 0; i < len; i++) {
    const ch = buffer[i];
    if (ch === 0) return false; // NUL is a strong binary signal
    if (ch < 7 || (ch > 13 && ch < 32)) controlCount++;
  }
  return (controlCount / len) < 0.3;
}

/**
 * Detects a best-fit media type for the provided buffer and filepath.
 * Returns an object: { type: 'image'|'audio'|'text'|'binary'|'unknown', mime?: string }
 */
function detectMediaType(buffer, filePath) {
  const ext = filePath ? path.extname(filePath).toLowerCase() : '';

  // Prefer extension first when available
  if (ext) {
    if (IMAGE_EXTS.has(ext)) return { type: 'image', mime: mimeForExt(ext) || 'application/octet-stream' };
    if (AUDIO_EXTS.has(ext)) return { type: 'audio', mime: mimeForExt(ext) || 'audio/*' };
  }

  // Magic-number checks for common image formats
  if (buffer && buffer.length >= 12) {
    // PNG
    if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47 &&
        buffer[4] === 0x0D && buffer[5] === 0x0A && buffer[6] === 0x1A && buffer[7] === 0x0A) {
      return { type: 'image', mime: 'image/png' };
    }
    // JPEG
    if (buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF) {
      return { type: 'image', mime: 'image/jpeg' };
    }
    // GIF
    if (buffer[0] === 0x47 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x38) {
      return { type: 'image', mime: 'image/gif' };
    }
    // WebP (RIFF....WEBP)
    if (buffer[0] === 0x52 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x46 &&
        buffer[8] === 0x57 && buffer[9] === 0x45 && buffer[10] === 0x42 && buffer[11] === 0x50) {
      return { type: 'image', mime: 'image/webp' };
    }
    // BMP
    if (buffer[0] === 0x42 && buffer[1] === 0x4D) {
      return { type: 'image', mime: 'image/bmp' };
    }
    // TIFF (II* or MM*)
    if ((buffer[0] === 0x49 && buffer[1] === 0x49 && buffer[2] === 0x2A) ||
        (buffer[0] === 0x4D && buffer[1] === 0x4D && buffer[2] === 0x2A)) {
      return { type: 'image', mime: 'image/tiff' };
    }
    // ICO
    if (buffer[0] === 0x00 && buffer[1] === 0x00 && buffer[2] === 0x01 && buffer[3] === 0x00) {
      return { type: 'image', mime: 'image/x-icon' };
    }
    // SVG heuristics (text) - look in first chunk for '<svg'
    const head = buffer.slice(0, 512).toString('utf8', 0, 512).toLowerCase();
    if (head.includes('<svg')) return { type: 'image', mime: 'image/svg+xml' };

    // Audio detection
    // ID3 tag (MP3)
    if (buffer[0] === 0x49 && buffer[1] === 0x44 && buffer[2] === 0x33) return { type: 'audio', mime: 'audio/mpeg' };
    // Ogg
    if (buffer[0] === 0x4F && buffer[1] === 0x67 && buffer[2] === 0x67 && buffer[3] === 0x53) return { type: 'audio', mime: 'audio/ogg' };
    // FLAC
    if (buffer[0] === 0x66 && buffer[1] === 0x4C && buffer[2] === 0x61 && buffer[3] === 0x43) return { type: 'audio', mime: 'audio/flac' };
    // WAV (RIFF + WAVE)
    if (buffer[0] === 0x52 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x46 &&
        buffer[8] === 0x57 && buffer[9] === 0x41 && buffer[10] === 0x56 && buffer[11] === 0x45) {
      return { type: 'audio', mime: 'audio/wav' };
    }
  }

  // Text heuristic
  if (isLikelyText(buffer)) return { type: 'text' };

  // Fallback to binary
  return { type: 'binary' };
}

/**
 * Convert a Buffer to a pretty binary representation string:
 * bytes grouped as 8-bit strings separated by spaces, lines of bytesPerLine bytes.
 */
function bufferToBinaryString(buffer, bytesPerLine = 16) {
  if (!buffer || buffer.length === 0) return '';
  const lines = [];
  for (let i = 0; i < buffer.length; i += bytesPerLine) {
    const slice = buffer.slice(i, i + bytesPerLine);
    const parts = Array.from(slice).map(b => b.toString(2).padStart(8, '0'));
    lines.push(parts.join(' '));
  }
  return lines.join('\n');
}

/**
 * Convert a Buffer into a data: URI for inline image rendering.
 */
function bufferToDataUrl(buffer, mime) {
  if (!buffer) return '';
  const b64 = buffer.toString('base64');
  return `data:${mime || 'application/octet-stream'};base64,${b64}`;
}

module.exports = {
  detectMediaType,
  bufferToBinaryString,
  bufferToDataUrl
};