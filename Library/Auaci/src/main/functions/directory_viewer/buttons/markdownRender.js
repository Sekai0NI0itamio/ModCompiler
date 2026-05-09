// src/main/functions/directory_viewer/buttons/markdownRender.js
const fs = require('fs').promises;
const path = require('path');
const marked = require('marked');
const { createOverlay, createPopupWithTolerance } = require('../popupUtils');

// Configure marked once for GitHub‑style markdown
marked.setOptions({
  gfm: true,
  breaks: false,
  headerIds: true,
  mangle: false
});

/**
 * Open a popup that renders the given file as Markdown with optimized native scrolling.
 *
 * @param {string} projectRoot - Absolute project root path (for display only).
 * @param {string} filePath - Absolute path to the file to render.
 */
async function markdownRender(projectRoot, filePath) {
  if (!filePath) return;

  let content = '';
  let error = null;

  try {
    const data = await fs.readFile(filePath, 'utf8');
    content = data != null ? String(data) : '';
  } catch (err) {
    console.error('markdownRender: failed to read file', filePath, err);
    error = err;
  }

  const overlay = createOverlay('rgba(0, 0, 0, 0.5)', 12000);
  overlay.classList.add('md-render-overlay');
  overlay.dataset.preventMonacoFocus = 'true';
  overlay.style.WebkitAppRegion = 'no-drag';

  const card = document.createElement('div');
  card.dataset.preventMonacoFocus = 'true';
  Object.assign(card.style, {
    width: 'min(960px, 94vw)',
    maxHeight: '90vh',
    background: '#ffffff',
    borderRadius: '8px',
    boxShadow: '0 10px 30px rgba(0,0,0,0.28)',
    padding: '16px',
    boxSizing: 'border-box',
    display: 'flex',
    flexDirection: 'column',
    gap: '10px',
    color: '#111827',
    fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    WebkitAppRegion: 'no-drag'
  });

  // Header row with title + close button
  const header = document.createElement('div');
  Object.assign(header.style, {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: '8px'
  });

  const title = document.createElement('div');
  title.textContent = 'Markdown render';
  Object.assign(title.style, {
    fontSize: '15px',
    fontWeight: '600',
    color: '#111827'
  });

  const closeBtn = document.createElement('button');
  closeBtn.textContent = 'Close';
  Object.assign(closeBtn.style, {
    padding: '6px 10px',
    borderRadius: '10%',
    border: '1px solid #d1d5db',
    background: '#f9fafb',
    color: '#111827',
    cursor: 'pointer',
    fontSize: '13px'
  });

  header.appendChild(title);
  header.appendChild(closeBtn);

  // Path + info line
  const info = document.createElement('div');
  const rel = (() => {
    try {
      if (!projectRoot) return filePath;
      const r = path.relative(projectRoot, filePath).replace(/\\/g, '/');
      return r && !r.startsWith('..') ? r : filePath;
    } catch (_) {
      return filePath;
    }
  })();
  info.textContent = rel;
  Object.assign(info.style, {
    fontSize: '12px',
    color: '#374151',
    background: '#f3f4f6',
    padding: '6px 8px',
    borderRadius: '6px',
    border: '1px solid #e5e7eb',
    wordBreak: 'break-all'
  });

  // Scrollable container
  const scroll = document.createElement('div');
  Object.assign(scroll.style, {
    flex: '1',
    minHeight: '260px',
    maxHeight: 'calc(90vh - 90px)',
    overflowY: 'auto',
    borderRadius: '6px',
    border: '1px solid #e5e7eb',
    background: '#ffffff',
    boxSizing: 'border-box',
    position: 'relative',
    userSelect: 'text',
    WebkitUserSelect: 'text',
    MozUserSelect: 'text',
    msUserSelect: 'text',
    cursor: 'text',
    WebkitAppRegion: 'no-drag'
  });

  // Inject CSS styles once
  injectMarkdownStylesheet();

  if (error) {
    const errDiv = document.createElement('div');
    errDiv.textContent = `Failed to read file: ${error.message || String(error)}`;
    Object.assign(errDiv.style, {
      color: '#b91c1c',
      background: '#fef2f2',
      border: '1px solid #fecaca',
      padding: '8px 10px',
      borderRadius: '6px',
      fontSize: '13px',
      margin: '12px 14px'
    });
    scroll.appendChild(errDiv);
  } else {
    // Parse markdown and split into chunks for virtual rendering
    let html = '';
    try {
      html = marked.parse(content || '');
    } catch (err) {
      console.error('markdownRender: marked failed', err);
      html = `<pre style="white-space: pre-wrap;">${escapeHtml(content || '')}</pre>`;
    }

    // Render markdown once and rely on browser optimizations for large documents
    renderMarkdownContent(scroll, html);
  }

  // Bottom row: small hint
  const hint = document.createElement('div');
  hint.textContent = 'Rendered as Markdown — links are clickable, tables and code blocks use standard HTML rendering.';
  Object.assign(hint.style, {
    fontSize: '12px',
    color: '#4b5563',
    marginTop: '4px'
  });

  card.appendChild(header);
  card.appendChild(info);
  card.appendChild(scroll);
  card.appendChild(hint);

  overlay.appendChild(card);
  document.body.appendChild(overlay);

  function close() {
    try {
      if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
    } catch (_) {}
    window.removeEventListener('keydown', onKeyDown);
    if (cleanupTolerance) cleanupTolerance();
  }

  const cleanupTolerance = createPopupWithTolerance(overlay, card, close, 30);

  function onKeyDown(e) {
    if (e.key === 'Escape') {
      close();
    }
    // Handle copy with Cmd+C / Ctrl+C
    if ((e.metaKey || e.ctrlKey) && e.key === 'c') {
      const selection = window.getSelection();
      if (selection && selection.rangeCount > 0 && !selection.isCollapsed) {
        e.preventDefault();
        copySelectionAsFormattedText(selection, scroll);
      }
    }
  }

  window.addEventListener('keydown', onKeyDown);
  closeBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    close();
  });
}

/**
 * Render markdown content once and rely on native browser optimizations.
 */
function renderMarkdownContent(scrollContainer, html) {
  const mdContainer = document.createElement('div');
  mdContainer.classList.add('md-render-content');
  Object.assign(mdContainer.style, {
    padding: '12px 14px',
    fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    fontSize: '14px',
    lineHeight: '1.55',
    color: '#111827'
  });

  mdContainer.innerHTML = html;
  mdContainer.querySelectorAll('script').forEach(el => el.remove());

  const children = Array.from(mdContainer.children);
  const LARGE_DOC_BLOCKS = 120;
  const LARGE_DOC_CHARS = 120000;
  const isLargeDoc = children.length >= LARGE_DOC_BLOCKS || (html && html.length >= LARGE_DOC_CHARS);

  if (isLargeDoc) {
    // Let the browser skip rendering offscreen content for smoother scrolling.
    children.forEach(el => {
      el.style.contentVisibility = 'auto';
      el.style.containIntrinsicSize = '0 24px';
    });
  }

  scrollContainer.appendChild(mdContainer);
}

function escapeHtml(text) {
  if (text == null) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Copy selected text with HTML formatting to clipboard
 */
function copySelectionAsFormattedText(selection, container) {
  if (!selection || selection.isCollapsed) return;

  try {
    // Get the selected range
    const range = selection.getRangeAt(0);
    
    // Check if selection is within our container
    if (!container.contains(range.commonAncestorContainer)) return;

    // Clone the selected content
    const clonedContent = range.cloneContents();
    
    // Create a temporary container to get the HTML
    const tempDiv = document.createElement('div');
    tempDiv.appendChild(clonedContent);
    
    // Get both HTML and plain text versions
    const htmlContent = tempDiv.innerHTML;
    const plainText = selection.toString();

    // Use Clipboard API to write both formats
    if (navigator.clipboard && navigator.clipboard.write) {
      const htmlBlob = new Blob([htmlContent], { type: 'text/html' });
      const textBlob = new Blob([plainText], { type: 'text/plain' });
      
      const clipboardItem = new ClipboardItem({
        'text/html': htmlBlob,
        'text/plain': textBlob
      });
      
      navigator.clipboard.write([clipboardItem]).catch(err => {
        // Fallback to plain text if HTML copy fails
        navigator.clipboard.writeText(plainText).catch(console.error);
      });
    } else {
      // Fallback for older browsers
      navigator.clipboard.writeText(plainText).catch(console.error);
    }
  } catch (err) {
    console.error('Failed to copy selection:', err);
    // Last resort fallback
    try {
      navigator.clipboard.writeText(selection.toString());
    } catch (_) {}
  }
}

// Inject stylesheet once (cached)
let stylesheetInjected = false;
function injectMarkdownStylesheet() {
  if (stylesheetInjected) return;
  stylesheetInjected = true;

  const style = document.createElement('style');
  style.textContent = `
    .md-render-content {
      user-select: text !important;
      -webkit-user-select: text !important;
      -moz-user-select: text !important;
      -ms-user-select: text !important;
      cursor: text;
    }
    .md-render-content * {
      user-select: text !important;
      -webkit-user-select: text !important;
      -moz-user-select: text !important;
      -ms-user-select: text !important;
    }
    .md-render-content ::selection { background: #3b82f6; color: #ffffff; }
    .md-render-content h1 { font-size: 26px; margin: 0 0 12px; font-weight: 700; }
    .md-render-content h2 { font-size: 22px; margin: 18px 0 8px; font-weight: 600; }
    .md-render-content h3 { font-size: 18px; margin: 16px 0 6px; font-weight: 600; }
    .md-render-content p { margin: 0 0 10px; }
    .md-render-content ul, .md-render-content ol { margin: 0 0 10px 22px; padding: 0 0 0 4px; }
    .md-render-content li { margin-bottom: 4px; }
    .md-render-content code {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
      font-size: 13px;
      background: #f3f4f6;
      padding: 2px 4px;
      border-radius: 4px;
      color: #000000;
    }
    .md-render-content pre {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
      font-size: 13px;
      background: #f3f4f6;
      color: #000000;
      padding: 10px 12px;
      border-radius: 6px;
      overflow-x: auto;
      margin: 0 0 12px;
      border: 1px solid #e5e7eb;
    }
    .md-render-content pre code {
      background: transparent;
      padding: 0;
      border-radius: 0;
      color: #000000;
    }
    .md-render-content a { color: #2563eb; text-decoration: underline; }
    .md-render-content table { border-collapse: collapse; width: 100%; margin-bottom: 12px; }
    .md-render-content th, .md-render-content td { border: 1px solid #e5e7eb; padding: 6px 8px; font-size: 13px; }
    .md-render-content th { background: #f3f4f6; font-weight: 600; }
  `;
  document.head.appendChild(style);
}

module.exports = { markdownRender };
