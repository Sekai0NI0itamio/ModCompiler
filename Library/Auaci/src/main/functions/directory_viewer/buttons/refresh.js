// src/main/functions/directory_viewer/buttons/refresh.js
const { smartRefresh } = require('../smartRefresh');

async function refreshDirectory(projectRoot) {
  await smartRefresh(projectRoot);
}

module.exports = { refreshDirectory };