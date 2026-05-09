const fs = require('fs').promises;
const path = require('path');

async function saveFolderStates(folderStates, projectRoot) {
  try {
    const dir = path.join(projectRoot, '.auaci', 'directorystates');
    await fs.mkdir(dir, { recursive: true });
    const filePath = path.join(dir, 'states.json');
    const data = Object.fromEntries(folderStates); // Convert Map to object
    await fs.writeFile(filePath, JSON.stringify(data, null, 2));
  } catch (err) {
    console.error('Failed to save folder states:', err);
  }
}

async function loadFolderStates(projectRoot) {
  try {
    const filePath = path.join(projectRoot, '.auaci', 'directorystates', 'states.json');
    const data = await fs.readFile(filePath, 'utf8');
    return new Map(Object.entries(JSON.parse(data)));
  } catch (err) {
    if (err.code === 'ENOENT') {
      return new Map(); // File doesn't exist, return empty Map
    }
    console.error('Failed to load folder states:', err);
    return new Map();
  }
}

module.exports = { saveFolderStates, loadFolderStates };