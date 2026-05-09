// src/main/functions/editor/fileOperations.js
// Delegates readFileContent to the unified fsUtils to avoid duplication.

const { readFileContent: readFileContentUnified } = require('../united/fsUtils');
const fs = require('fs').promises;
const path = require('path');

async function readFileContent(filePath) {
  return await readFileContentUnified(filePath);
}

async function saveFile(filePath, content) {
  try {
    // Ensure target directory exists for nested paths
    try {
      const dir = path.dirname(filePath);
      if (dir) await fs.mkdir(dir, { recursive: true });
    } catch (_) { /* ignore mkdir failures; write may still succeed */ }

    await fs.writeFile(filePath, content);
    console.log('Saved file:', filePath);
    return true;
  } catch (err) {
    console.error(`Failed to save file ${filePath}:`, err);
    alert('Failed to save file.');
    return false;
  }
}

module.exports = { readFileContent, saveFile };