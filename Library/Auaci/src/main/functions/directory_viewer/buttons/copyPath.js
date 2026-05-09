// src/main/functions/directory_viewer/buttons/copyPath.js
const { clipboard } = require('electron');

async function copyPath(targetPath, x, y) {
    try {
        clipboard.writeText(targetPath);
        console.log(`Copied path to clipboard: ${targetPath}`);

        // Create notification div
        const notification = document.createElement('div');
        Object.assign(notification.style, {
            position: 'absolute',
            left: `${x}px`,
            top: `${y + 20}px`, // Offset slightly below cursor
            background: '#fff',
            color: '#000',
            padding: '5px 10px',
            borderRadius: '4px',
            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.15)',
            fontSize: '12px',
            fontFamily: 'Arial, sans-serif',
            zIndex: '1002',
            maxWidth: '300px',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            opacity: '1',
            transition: 'opacity 2s ease-in-out 1s' // 2s fade-out after 1s delay
        });
        notification.textContent = targetPath;
        document.body.appendChild(notification);

        // Remove notification after 3s (1s display + 2s fade)
        setTimeout(() => {
            notification.style.opacity = '0';
            setTimeout(() => {
                document.body.removeChild(notification);
            }, 2000); // Match transition duration
        }, 1000);
    } catch (err) {
        console.error(`Failed to copy path ${targetPath}:`, err);
        alert('Failed to copy path to clipboard.');
    }
}

module.exports = { copyPath };