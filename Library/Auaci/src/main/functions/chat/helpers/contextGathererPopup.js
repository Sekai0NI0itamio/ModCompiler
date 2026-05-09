// src/main/functions/chat/helpers/contextGathererPopup.js
// Popup handler for viewing context gatherer summary

/**
 * Simple markdown to HTML converter for the summary popup
 */
function simpleMarkdownToHtml(md) {
  if (!md) return '';
  
  let html = md
    // Escape HTML
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    // Headers
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    // Bold
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    // Italic
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    // Inline code
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // Lists
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/^(\d+)\. (.+)$/gm, '<li>$2</li>')
    // Line breaks
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>');
  
  // Wrap lists
  html = html.replace(/(<li>.*?<\/li>)+/gs, '<ul>$&</ul>');
  
  // Wrap in paragraph
  html = '<p>' + html + '</p>';
  
  // Clean up empty paragraphs
  html = html.replace(/<p><\/p>/g, '');
  html = html.replace(/<p>(<h[123]>)/g, '$1');
  html = html.replace(/(<\/h[123]>)<\/p>/g, '$1');
  html = html.replace(/<p>(<ul>)/g, '$1');
  html = html.replace(/(<\/ul>)<\/p>/g, '$1');
  
  return html;
}

/**
 * Show the context gatherer summary popup
 */
function showContextGathererSummary(resultId) {
  // Context Gatherer tool has been removed
  console.warn('[contextGathererPopup] context_gatherer tool has been removed. Use semantic_search instead.');
  
  if (!data) {
    console.warn('[contextGathererPopup] No data found for resultId:', resultId);
    return;
  }
  
  const { task, response, context, summary } = data;
  
  // Create overlay
  const overlay = document.createElement('div');
  overlay.className = 'cg-summary-overlay';
  
  // Create popup
  const popup = document.createElement('div');
  popup.className = 'cg-summary-popup';
  
  // Brain icon
  const brainIcon = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z" fill="#0369a1"/></svg>`;
  
  // Close icon
  const closeIcon = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`;
  
  // Build stats line
  const statsHtml = summary ? `
    <div class="cg-summary-stats">
      <span><strong>${summary.iterations || 0}</strong> iterations</span>
      <span><strong>${summary.toolCalls || 0}</strong> tool calls</span>
      <span><strong>${summary.contextSizeFormatted || '0 B'}</strong> context</span>
      <span><strong>${summary.filesDiscovered || 0}</strong> files</span>
      <span><strong>~${formatTokens(summary.estimatedTotalTokens)}</strong> tokens</span>
    </div>
  ` : '';
  
  // Convert response to HTML
  const responseHtml = simpleMarkdownToHtml(response);
  
  popup.innerHTML = `
    <div class="cg-summary-header">
      <div class="cg-summary-title">
        ${brainIcon}
        <span>Context Gatherer Summary</span>
      </div>
      <button class="cg-summary-close" title="Close">${closeIcon}</button>
    </div>
    <div class="cg-summary-body">
      ${statsHtml}
      <div class="cg-summary-content">
        ${responseHtml}
      </div>
    </div>
  `;
  
  overlay.appendChild(popup);
  document.body.appendChild(overlay);
  
  // Close handlers
  const close = () => {
    overlay.style.animation = 'cg-overlay-fade-in 0.15s ease reverse';
    popup.style.animation = 'cg-popup-slide-in 0.15s ease reverse';
    setTimeout(() => overlay.remove(), 150);
  };
  
  // Close button
  popup.querySelector('.cg-summary-close').addEventListener('click', close);
  
  // Click outside to close
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) close();
  });
  
  // Escape key to close
  const onKeyDown = (e) => {
    if (e.key === 'Escape') {
      close();
      document.removeEventListener('keydown', onKeyDown);
    }
  };
  document.addEventListener('keydown', onKeyDown);
}

function formatTokens(tokens) {
  if (!tokens) return '0';
  if (tokens < 1000) return String(tokens);
  return `${(tokens / 1000).toFixed(1)}k`;
}

// Expose globally for onclick handlers
if (typeof window !== 'undefined') {
  window.showContextGathererSummary = showContextGathererSummary;
}

module.exports = { showContextGathererSummary, simpleMarkdownToHtml };
