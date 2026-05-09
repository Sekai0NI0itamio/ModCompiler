// src/main/functions/editor/dragDrop.js
const { setupExternalDragDrop } = require('./externalfile');
const { setupInternalDragDrop } = require('./internalfile');

function setupDragDrop() {
  // 1. From outside the application (uses the C helper)
  setupExternalDragDrop();

  // 2. From the app’s own Directory-Viewer
  setupInternalDragDrop();
}

module.exports = { setupDragDrop };