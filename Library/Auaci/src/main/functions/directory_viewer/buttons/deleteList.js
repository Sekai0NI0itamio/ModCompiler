/**
 * Delete List - allows bulk deletion of files/folders by pasting paths
 * Shows a window with an editable text box where users can paste file/folder paths
 * (one per line) and delete them by moving to trash.
 */

const fs = require('fs').promises;
const path = require('path');
const { moveToTrash } = require('./DeleteFunction');

async function showDeleteListWindow(folderPath) {
  try {
    // Verify it's a directory
    const stat = await fs.lstat(folderPath);
    if (!stat.isDirectory()) {
      alert('Delete List is only available for folders.');
      return;
    }

    const folderName = path.basename(folderPath);

    // Create overlay
    const overlay = document.createElement('div');
    Object.assign(overlay.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      width: '100vw',
      height: '100vh',
      background: 'rgba(0, 0, 0, 0.5)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      zIndex: '1000'
    });

    // Create card container
    const card = document.createElement('div');
    Object.assign(card.style, {
      background: '#fff',
      borderRadius: '8px',
      padding: '20px',
      minWidth: '500px',
      maxWidth: '90vw',
      maxHeight: '90vh',
      boxShadow: '0 10px 30px rgba(0,0,0,0.25)',
      display: 'flex',
      flexDirection: 'column',
      boxSizing: 'border-box'
    });

    // Title
    const title = document.createElement('div');
    title.textContent = `Delete List - ${folderName}`;
    Object.assign(title.style, {
      fontSize: '16px',
      fontWeight: '600',
      marginBottom: '8px'
    });

    // Path display
    const pathDisplay = document.createElement('div');
    pathDisplay.textContent = `Folder: ${folderPath}`;
    Object.assign(pathDisplay.style, {
      fontSize: '12px',
      color: '#666',
      marginBottom: '12px',
      wordBreak: 'break-all',
      fontFamily: 'monospace',
      background: '#f5f5f5',
      padding: '8px',
      borderRadius: '4px'
    });

    // Instructions
    const instructions = document.createElement('div');
    instructions.textContent = 'Paste file or folder paths below (one per line). Items will be moved to Trash:';
    Object.assign(instructions.style, {
      fontSize: '13px',
      color: '#555',
      marginBottom: '12px'
    });

    // Textarea for paths
    const textarea = document.createElement('textarea');
    textarea.placeholder = 'Paste paths here (one per line)...\nExample:\n/path/to/file1.txt\n/path/to/folder\n/path/to/file2.js';
    Object.assign(textarea.style, {
      width: '100%',
      height: '250px',
      padding: '10px',
      fontSize: '13px',
      fontFamily: 'monospace',
      border: '1px solid #ccc',
      borderRadius: '6px',
      boxSizing: 'border-box',
      resize: 'vertical',
      outline: 'none'
    });

    // Status message
    const statusMsg = document.createElement('div');
    Object.assign(statusMsg.style, {
      fontSize: '12px',
      color: '#666',
      marginTop: '12px',
      minHeight: '20px'
    });

    // Button container
    const buttonContainer = document.createElement('div');
    Object.assign(buttonContainer.style, {
      display: 'flex',
      gap: '8px',
      marginTop: '16px',
      justifyContent: 'flex-end'
    });

    // Delete button
    const deleteBtn = document.createElement('button');
    deleteBtn.textContent = 'Delete';
    Object.assign(deleteBtn.style, {
      padding: '8px 16px',
      background: '#dc2626',
      color: '#fff',
      border: 'none',
      borderRadius: '6px',
      cursor: 'pointer',
      fontSize: '14px',
      fontWeight: '500'
    });

    deleteBtn.addEventListener('click', async () => {
      const paths = textarea.value
        .split('\n')
        .map(p => p.trim())
        .filter(p => p.length > 0);

      if (paths.length === 0) {
        statusMsg.textContent = 'No paths provided.';
        statusMsg.style.color = '#f59e0b';
        return;
      }

      deleteBtn.disabled = true;
      cancelBtn.disabled = true;
      textarea.disabled = true;
      statusMsg.textContent = `Deleting ${paths.length} item(s)...`;
      statusMsg.style.color = '#3b82f6';

      const results = {
        success: [],
        failed: []
      };

      for (const itemPath of paths) {
        try {
          await moveToTrash(itemPath);
          results.success.push(itemPath);
        } catch (err) {
          results.failed.push({
            path: itemPath,
            error: err && err.message ? err.message : String(err)
          });
        }
      }

      // Show results
      let resultText = `Deleted: ${results.success.length}/${paths.length}`;
      let resultColor = '#10b981';

      if (results.failed.length > 0) {
        resultText += ` | Failed: ${results.failed.length}`;
        resultColor = '#ef4444';
        console.error('Delete List failures:', results.failed);
      }

      statusMsg.textContent = resultText;
      statusMsg.style.color = resultColor;

      deleteBtn.disabled = false;
      cancelBtn.disabled = false;
      textarea.disabled = false;

      // Clear textarea on success
      if (results.failed.length === 0) {
        setTimeout(() => {
          textarea.value = '';
        }, 500);
      }
    });

    // Cancel button
    const cancelBtn = document.createElement('button');
    cancelBtn.textContent = 'Close';
    Object.assign(cancelBtn.style, {
      padding: '8px 16px',
      background: '#e5e7eb',
      color: '#111',
      border: 'none',
      borderRadius: '6px',
      cursor: 'pointer',
      fontSize: '14px',
      fontWeight: '500'
    });

    cancelBtn.addEventListener('click', () => {
      if (overlay.parentNode) {
        document.body.removeChild(overlay);
      }
    });

    buttonContainer.appendChild(deleteBtn);
    buttonContainer.appendChild(cancelBtn);

    // Assemble card
    card.appendChild(title);
    card.appendChild(pathDisplay);
    card.appendChild(instructions);
    card.appendChild(textarea);
    card.appendChild(statusMsg);
    card.appendChild(buttonContainer);

    overlay.appendChild(card);
    document.body.appendChild(overlay);

    // Focus textarea
    textarea.focus();

    // Close on overlay click
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        if (overlay.parentNode) {
          document.body.removeChild(overlay);
        }
      }
    });

  } catch (err) {
    console.error('Failed to open Delete List window:', err);
    alert('Failed to open Delete List: ' + (err && err.message ? err.message : String(err)));
  }
}

module.exports = { showDeleteListWindow };
