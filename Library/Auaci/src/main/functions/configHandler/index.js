// src/main/functions/configHandler/index.js
// Config handler: ensures config.json exists under the app's userData folder.
// On macOS we explicitly create ~/Library/Application Support/Auaci so you get the
// "Auaci" folder name (capitalization) as requested. This module logs key paths
// and errors to the console for easy debugging when you launch Electron from Terminal.

const fs = require('fs').promises;
const path = require('path');

// Load defaults from separate module so other code can require them if needed
const { DEFAULT_CONFIG } = require('./defaultconfig');

let _config = null;
let _configPath = null;
let _baseDir = null;

async function ensureDir(dir) {
  try {
    await fs.mkdir(dir, { recursive: true });
    return true;
  } catch (err) {
    throw err;
  }
}

async function readConfigFile(cfgPath) {
  try {
    const txt = await fs.readFile(cfgPath, 'utf8');
    if (!txt) return null;
    return JSON.parse(txt);
  } catch (err) {
    return null;
  }
}

async function atomicWrite(cfgPath, obj) {
  const tmp = cfgPath + '.tmp';
  const payload = JSON.stringify(obj, null, 2);
  try {
    await fs.writeFile(tmp, payload, { encoding: 'utf8' });
    await fs.rename(tmp, cfgPath);
  } catch (err) {
    // fallback: try direct write
    try {
      await fs.writeFile(cfgPath, payload, { encoding: 'utf8' });
    } catch (e) {
      throw e || err;
    }
  }
}

/**
 * init(electronApp)
 * Ensures config file exists and is loaded.
 * On macOS will create ~/Library/Application Support/Auaci (capitalized) explicitly.
 */
async function init(electronApp) {
  if (!electronApp || typeof electronApp.getPath !== 'function') {
    throw new Error('init requires an Electron app instance');
  }

  // Get default userData path from Electron
  const userData = electronApp.getPath('userData');
  console.info('[configHandler] electron.app.getPath("userData") =', userData);

  // On macOS, force the "Auaci" folder inside Application Support so the folder name
  // matches your desired capitalization (~/Library/Application Support/Auaci).
  if (process.platform === 'darwin') {
    try {
      // userData typically = .../Library/Application Support/<AppName>
      const appSupportParent = path.dirname(userData); // -> .../Library/Application Support
      _baseDir = path.join(appSupportParent, 'Auaci'); // force capitalized Auaci
      console.info('[configHandler] macOS detected -> forcing base dir to:', _baseDir);
    } catch (err) {
      // fallback to returned userData if something odd occurs
      _baseDir = userData;
      console.warn('[configHandler] Failed to derive Auaci path, falling back to userData:', err && err.message ? err.message : err);
    }
  } else {
    // Non-mac platforms: use electron's userData
    _baseDir = userData;
    console.info('[configHandler] non-macOS platform -> base dir =', _baseDir);
  }

  _configPath = path.join(_baseDir, 'config.json');
  console.info('[configHandler] config path resolved to:', _configPath);

  // Ensure directory exists
  try {
    await ensureDir(_baseDir);
    console.info('[configHandler] ensured directory exists:', _baseDir);
  } catch (err) {
    console.error('[configHandler] Failed to create baseDir:', _baseDir, err && err.message ? err.message : err);
    // continue so that read attempt may still show meaningful error to caller
  }

  // Try to read existing config
  let existing = null;
  try {
    existing = await readConfigFile(_configPath);
  } catch (err) {
    existing = null;
  }

  if (existing && typeof existing === 'object') {
    // Merge with defaults so missing keys get sane defaults
    _config = Object.assign({}, DEFAULT_CONFIG, existing);
    // Persist merged defaults back to disk (for missing keys)
    try {
      await atomicWrite(_configPath, _config);
      console.info('[configHandler] merged existing config and persisted to disk:', _configPath);
    } catch (err) {
      console.error('[configHandler] Failed to persist merged config:', err && err.message ? err.message : err);
      // do not throw; keep in-memory
    }
  } else {
    // No existing config -> write default configuration
    _config = Object.assign({}, DEFAULT_CONFIG);
    try {
      await atomicWrite(_configPath, _config);
      console.info('[configHandler] wrote default config to:', _configPath);
    } catch (err) {
      console.error('[configHandler] Failed to write default config to disk:', err && err.message ? err.message : err);
      // retry once
      try {
        await new Promise(r => setTimeout(r, 60));
        await atomicWrite(_configPath, _config);
        console.info('[configHandler] retry succeeded writing default config to:', _configPath);
      } catch (err2) {
        console.error('[configHandler] Retry failed writing default config:', err2 && err2.message ? err2.message : err2);
      }
    }
  }

  return getConfig();
}

function getConfig() {
  return _config ? Object.assign({}, _config) : Object.assign({}, DEFAULT_CONFIG);
}

async function setConfig(obj) {
  if (!obj || typeof obj !== 'object') throw new Error('setConfig expects an object');
  _config = Object.assign({}, obj);
  if (!_configPath) throw new Error('configHandler not initialized');
  await atomicWrite(_configPath, _config);
  return getConfig();
}

async function updateConfig(patch) {
  if (!patch || typeof patch !== 'object') throw new Error('updateConfig expects an object patch');
  _config = Object.assign({}, _config || {}, patch);
  if (!_configPath) throw new Error('configHandler not initialized');
  await atomicWrite(_configPath, _config);
  return getConfig();
}

module.exports = {
  init,
  getConfig,
  setConfig,
  updateConfig,
  _DEFAULT: DEFAULT_CONFIG
};