// src/main/functions/settings/init.js
// Small initializer for the settings window. Loads category content and wires UI.

(function () {
  // If running in a renderer with nodeIntegration disabled, this still works (uses fetch).
  async function loadCategory(name) {
    const container = document.getElementById('category-content');
    if (!container) return;

    try {
      const resp = await fetch(`./categories/${name}.html`);
      if (!resp.ok) {
        container.innerHTML = `<h1>Unable to load ${name}</h1><p>HTTP ${resp.status}</p>`;
        return;
      }
      const html = await resp.text();
      container.innerHTML = html;
    } catch (err) {
      container.innerHTML = `<h1>Error</h1><p>${err && err.message ? err.message : String(err)}</p>`;
      return;
    }

    // Try to load a category-specific JS module (./categories/<name>.js).
    // Preferred: use require() when Node integration is available (fast, CommonJS).
    // Fallback: create a <script> element to load the JS file.
    async function tryLoadCategoryScript() {
      const scriptRelPath = `./categories/${name}.js`;

      // Helper to attempt to call well-known exported init patterns
      function callInitIfPresent(mod) {
        try {
          if (!mod) return;
          // CommonJS export { init }
          if (typeof mod.init === 'function') {
            try { mod.init(); } catch (e) { console.error(`Error running ${name}.init():`, e); }
            return;
          }
          // Named export (module.exports = function) - treat it as init
          if (typeof mod === 'function') {
            try { mod(); } catch (e) { console.error(`Error running ${name} exported function:`, e); }
            return;
          }
          // Window-global initializer e.g. window.initChatSettings
          const globalFnName = `init${name.charAt(0).toUpperCase()}${name.slice(1)}Settings`;
          if (typeof window[globalFnName] === 'function') {
            try { window[globalFnName](); } catch (e) { console.error(`Error running ${globalFnName}:`, e); }
            return;
          }
        } catch (e) {
          console.error('callInitIfPresent error:', e);
        }
      }

      // Try require() when available
      try {
        if (typeof require === 'function') {
          // require path should be relative to this script location in the settings folder
          // Use a try/catch so missing module does not break the rest
          try {
            const mod = require(scriptRelPath);
            // If it's a promise (ESM transpiled), allow thenable
            if (mod && typeof mod.then === 'function') {
              // ESM-ish promise: await then call exported init
              try {
                const resolved = await mod;
                callInitIfPresent(resolved);
              } catch (e) {
                console.warn(`Failed to resolve promise export for ${scriptRelPath}:`, e);
              }
            } else {
              callInitIfPresent(mod);
            }
            return;
          } catch (e) {
            // require failed (module not found or other). Fall through to script tag fallback.
            // Log at debug level to help troubleshooting.
            console.debug(`require('${scriptRelPath}') failed:`, e && e.message ? e.message : e);
          }
        }
      } catch (e) {
        // require not available or other error - proceed to fallback
      }

      // Fallback: create a script element to load the JS. This works when nodeIntegration is off.
      try {
        // Remove any previously appended category script with same src
        const existing = document.querySelector(`script[data-category-script="${scriptRelPath}"]`);
        if (existing) existing.remove();

        await new Promise((resolve, reject) => {
          const s = document.createElement('script');
          s.setAttribute('data-category-script', scriptRelPath);
          s.src = scriptRelPath;
          s.type = 'text/javascript';
          s.onload = () => {
            // After load, try to call any global init
            try {
              const globalFnName = `init${name.charAt(0).toUpperCase()}${name.slice(1)}Settings`;
              if (typeof window[globalFnName] === 'function') {
                try { window[globalFnName](); } catch (e) { console.error(`Error running ${globalFnName}:`, e); }
              }
            } catch (e) {
              console.error('Error calling global init after script load:', e);
            }
            resolve();
          };
          s.onerror = (err) => {
            console.warn(`Failed to load category script ${scriptRelPath}:`, err);
            // Do not reject hard; resolving keeps UI usable even without script.
            resolve();
          };
          document.body.appendChild(s);
        });
      } catch (e) {
        console.warn('Failed to insert category script tag fallback:', e);
      }
    }

    // Attempt to load the category script (non-blocking)
    tryLoadCategoryScript().catch((e) => {
      console.warn('tryLoadCategoryScript error:', e);
    });
  }

  function setupCategoryClicks() {
    const cats = document.querySelectorAll('.category');
    cats.forEach((el) => {
      el.addEventListener('click', (e) => {
        cats.forEach(c => c.classList.remove('active'));
        el.classList.add('active');
        const cat = el.getAttribute('data-category') || 'general';
        loadCategory(cat);
      });
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    setupCategoryClicks();
    // load default
    loadCategory('general');
  });
})();