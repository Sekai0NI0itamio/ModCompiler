// src/chat/functions/renderer.js
const marked = require('marked');

function createRenderer() {
  const renderer = new marked.Renderer();
  renderer.code = function (codeToken, info) {
    let lang = 'text';
    if (typeof info === 'string' && info.trim()) {
      lang = info.trim().split(/\s+/)[0];
    } else if (info && typeof info === 'object' && info.lang) {
      lang = info.lang;
    } else if (codeToken && typeof codeToken === 'object' && codeToken.lang) {
      lang = codeToken.lang;
    } else if (codeToken && typeof codeToken === 'object' && typeof codeToken.raw === 'string') {
      const m = codeToken.raw.match(/^```([\w+-]+)/);
      if (m) lang = m[1];
    }
    const alias = { py: 'python', python3: 'python', js: 'javascript', sh: 'bash', shell: 'bash' };
    lang = (alias[lang.toLowerCase()] || lang).toLowerCase();
    let rawCode = '';
    if (typeof codeToken === 'string') {
      rawCode = codeToken;
    } else if (codeToken && typeof codeToken.text === 'string') {
      rawCode = codeToken.text;
    } else if (codeToken && typeof codeToken.raw === 'string') {
      rawCode = codeToken.raw.replace(/^```[\s\S]*?\n|```$/g, '');
    }
    const safeCode = rawCode.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const buttonId = 'copy-btn-' + Math.random().toString(36).slice(2, 11);
    return `
      <div class="code-container" data-language="${lang}">
        <div class="code-header">
          <span class="code-language">${lang.toUpperCase()}</span>
          <button class="copy-button" id="${buttonId}" data-code="${encodeURIComponent(rawCode)}" title="Copy code">
            <svg class="copy-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2" stroke="currentColor" stroke-width="2" fill="none"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" stroke="currentColor" stroke-width="2" fill="none"/>
            </svg>
            <svg class="check-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <polyline points="20,6 9,17 4,12" stroke="currentColor" stroke-width="2" fill="none"/>
            </svg>
          </button>
        </div>
        <pre><code class="language-${lang}">${safeCode}</code></pre>
      </div>
    `.trim();
  };
  return renderer;
}

module.exports = { createRenderer };