// src/main/functions/chat/fileUtils.js
const fs = require('fs').promises;

function formatFileSize(size) {
  const units = ['B', 'KB', 'MB', 'GB'];
  let n = size, i = 0;
  while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
  return `${n.toFixed(1)} ${units[i]}`;
}

// Deprecated: replaced by inline tokens in the input area
function createFileBox(file) {
  return `
    <div class="file-box">
      <span>${file.name}</span>
      <span>(${formatFileSize(file.size)})</span>
      <button class="remove-file-btn" data-name="${file.name}" title="Remove file">x</button>
    </div>
  `;
}

function createHistoryFileBox(file) {
  return `
    <div class="file-box">
      <span>${file.name}</span>
      <span>(${formatFileSize(file.size)})</span>
    </div>
  `;
}

// Deprecated: no-op; inline tokens manage removal
function setupRemoveFileListeners() {
  const filePreviews = document.getElementById('file-previews');
  // Clear existing listeners by cloning buttons
  document.querySelectorAll('.remove-file-btn').forEach(btn => {
    const newBtn = btn.cloneNode(true);
    btn.parentNode.replaceChild(newBtn, btn);
  });

  // Attach listeners with delay to ensure DOM is ready
  setTimeout(() => {
    const buttons = document.querySelectorAll('.remove-file-btn');
    console.log('Attaching listeners to', buttons.length, 'remove buttons');
    buttons.forEach(btn => {
      btn.addEventListener('click', async (e) => {
        const fileName = e.target.getAttribute('data-name');
        console.log('Remove button clicked:', fileName, 'Dropped files:', window.droppedFiles);
        if (!fileName) {
          console.error('No data-name attribute found on button');
          return;
        }
        window.droppedFiles = window.droppedFiles.filter(file => file.name !== fileName);
        filePreviews.innerHTML = window.droppedFiles.map(createFileBox).join('');
        fs.appendFile('/tmp/events.log', `[${new Date().toISOString()}] Removed file ${fileName}\n`);
        // Persist updated draft (text + files)
        try {
          const text = document.getElementById('user-input')?.value || '';
          await require('./incrementalHistoryStorage').saveCurrentInputDraft(text, window.droppedFiles);
        } catch (_) {}
        setupRemoveFileListeners(); // Reattach listeners for new buttons
      });
    });
  }, 0); // Run after DOM updates
}

module.exports = { formatFileSize, createFileBox, createHistoryFileBox, setupRemoveFileListeners };