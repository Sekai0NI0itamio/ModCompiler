// src/main/functions/directory_viewer/buttons/revealInFinder.js
const { execFile } = require('child_process');
const path = require('path');
const { ipcRenderer } = require('electron');

async function revealInFinder(targetPath) {
    return new Promise(async (resolve, reject) => {
        try {
            const appPath = await ipcRenderer.invoke('get-app-path');
            // binary moved to bin/
            const binaryPath = path.join(appPath, 'bin', 'reveal_in_finder');
            execFile(binaryPath, [targetPath], (error, stdout, stderr) => {
                if (error || stderr) {
                    console.error(`Failed to reveal ${targetPath} in Finder:`, stderr || error.message);
                    alert('Failed to reveal in Finder.');
                    reject(stderr || error.message);
                    return;
                }
                console.log(`Revealed ${targetPath} in Finder`);
                resolve();
            });
        } catch (err) {
            console.error(`Failed to get app path:`, err);
            alert('Failed to reveal in Finder.');
            reject(err);
        }
    });
}

module.exports = { revealInFinder };