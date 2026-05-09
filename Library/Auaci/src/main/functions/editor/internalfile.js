// src/main/functions/editor/internalfile.js
const fs = require('fs').promises;
const path = require('path');
const { readFileContent } = require('./fileOperations');
const { addFiles } = require('./tabManagement');

/**
 * Returns true if the drag event looks like it originated from the
 * Directory-Viewer (i.e. we set `text/plain` to a file path ourselves).
 */
function isInternalDrag(e) {
  return (
    e.dataTransfer &&
    e.dataTransfer.types.includes('text/plain') &&  // our path
    !e.dataTransfer.types.includes('Files')         // no external files
  );
}

function setupInternalDragDrop() {
  const editorContent = document.getElementById('editor-content');
  if (!editorContent) return;

  let dragging = false;

  // Use capture: true so we run before Monaco / other handlers and can prevent the default
  editorContent.addEventListener('dragenter', (e) => {
    if (!isInternalDrag(e)) return;           // let external handler work
    // Prevent the browser/editor from inserting dropped text
    e.preventDefault();
    e.stopImmediatePropagation();
    dragging = true;
    // Indicate visually
    editorContent.classList.add('dragover');
    // Suggest copy effect
    try { e.dataTransfer.dropEffect = 'copy'; } catch (_) {}
  }, { capture: true });

  editorContent.addEventListener('dragover', (e) => {
    if (!isInternalDrag(e)) return;
    // Prevent default so drop doesn't insert text into the focused editor
    e.preventDefault();
    e.stopImmediatePropagation();
    try { e.dataTransfer.dropEffect = 'copy'; } catch (_) {}
  }, { capture: true });

  editorContent.addEventListener('dragleave', (e) => {
    if (!isInternalDrag(e)) return;
    // Stop propagation so other handlers don't run
    e.preventDefault();
    e.stopImmediatePropagation();
    dragging = false;
    editorContent.classList.remove('dragover');
  }, { capture: true });

  editorContent.addEventListener('drop', async (e) => {
    if (!isInternalDrag(e)) return;
    // Prevent default insertion into editor and stop other listeners
    e.preventDefault();
    e.stopImmediatePropagation();

    dragging = false;
    editorContent.classList.remove('dragover');

    // One or many paths -- we store each on a new line
    const raw = e.dataTransfer.getData('text/plain') || '';
    const paths = raw.split(/\r?\n/).map(s => s.trim()).filter(Boolean);

    const files = [];

    for (const p of paths) {
      try {
        const stats = await fs.stat(p);
        if (!stats.isFile()) continue;        // skip folders
        const fileObj = await readFileContent(p);
        if (fileObj) files.push(fileObj);
      } catch (err) {
        await fs.appendFile(
          '/tmp/editor.log',
          `[${new Date().toISOString()}] internalfile: failed for ${p}: ${err.message}\n`
        );
      }
    }

    if (files.length) addFiles(files);
  }, { capture: true });
}

module.exports = { setupInternalDragDrop };