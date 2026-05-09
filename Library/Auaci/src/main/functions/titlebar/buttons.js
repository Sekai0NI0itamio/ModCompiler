// src/main/functions/titlebar/buttons.js
// Adds additional action buttons to the titlebar (settings etc.)

const { ipcRenderer } = require('electron');

function ensureRightContainer() {
  const bar = document.getElementById('app-titlebar');
  if (!bar) return null;
  let right = bar.querySelector('.titlebar-right');
  if (!right) {
    right = document.createElement('div');
    right.className = 'titlebar-right';
    bar.appendChild(right);
  }
  return right;
}

function createBackupButton() {
  const right = ensureRightContainer();
  if (!right) return;

  // If backup button already exists, nothing to do
  if (right.querySelector('.tb-backup')) return;

  const btn = document.createElement('button');
  btn.className = 'tb-backup';
  btn.title = 'Create Backup';
  btn.setAttribute('aria-label', 'Create Backup');
  btn.type = 'button';

  // Image path relative to renderer index.html
  const img = document.createElement('img');
  img.src = './assets/backup/backup.png';
  img.alt = 'Backup';
  img.style.width = '18px';
  img.style.height = '18px';
  btn.appendChild(img);

  // Click opens backup window via IPC to main
  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    try {
      ipcRenderer.send('open-backup-window');
    } catch (err) {
      console.error('Failed to send open-backup-window IPC:', err && err.message ? err.message : err);
    }
  });

  // Insert before settings button so backup appears left of settings if settings exists
  const settingsBtn = right.querySelector('.tb-settings');
  if (settingsBtn) right.insertBefore(btn, settingsBtn);
  else right.appendChild(btn);
}

function createSettingsButton() {
  const right = ensureRightContainer();
  if (!right) return;

  // If settings button already exists, do nothing
  if (right.querySelector('.tb-settings')) return;

  const btn = document.createElement('button');
  btn.className = 'tb-settings';
  btn.title = 'Settings';
  btn.setAttribute('aria-label', 'Settings');
  btn.type = 'button';

  // Image path relative to renderer index.html
  const img = document.createElement('img');
  img.src = './assets/titlebar/settings.png';
  img.alt = 'Settings';
  img.style.width = '18px';
  img.style.height = '18px';
  btn.appendChild(img);

  // Click opens settings window via IPC to main
  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    try {
      ipcRenderer.send('open-settings-window');
    } catch (err) {
      console.error('Failed to send open-settings-window IPC:', err && err.message ? err.message : err);
    }
  });

  right.appendChild(btn);
}

function initTitlebarButtons() {
  try {
    // Create settings first, then insert backup before it.
    // This ensures backup appears to the left of settings regardless of existing DOM order.
    createSettingsButton();
    createBackupButton();
  } catch (err) {
    console.error('initTitlebarButtons error:', err && err.message ? err.message : err);
  }
}

module.exports = { initTitlebarButtons };