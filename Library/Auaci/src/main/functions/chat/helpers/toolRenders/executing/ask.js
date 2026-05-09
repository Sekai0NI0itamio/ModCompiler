// Executing state render for ask tool
// Shows interactive inputs that user can interact with while waiting
const { escapeHtmlLite } = require('../shared');

module.exports = function renderAskExecuting(input, toolIndex) {
  const question = (input && input.question) || '';
  const mode = (input && input.mode) || 'free';
  const options = (input && Array.isArray(input.options)) ? input.options : [];
  const idx = toolIndex || '0';
  
  const lines = [];
  lines.push(`<div class="tool-line" style="font-weight:600;">${escapeHtmlLite(question || 'Waiting for input...')}</div>`);
  
  // Show enabled inputs so user can interact while waiting
  if (mode === 'free') {
    lines.push(`<div class="tool-line"><textarea class="ask-input" data-tool-index="${idx}" placeholder="Type your response..." style="width:100%;min-height:72px;padding:8px;border:1px solid #e5e7eb;border-radius:6px;resize:vertical;"></textarea></div>`);
  } else if (options.length > 0) {
    for (let i = 0; i < options.length; i++) {
      const opt = options[i];
      const inputType = mode === 'multi' ? 'checkbox' : 'radio';
      const nameAttr = mode === 'multi' ? '' : `name="ask-${idx}-single"`;
      const optId = `ask-exec-${idx}-opt-${i}`;
      lines.push(`<div class="tool-line" style="display:flex;align-items:center;gap:8px;"><input type="${inputType}" ${nameAttr} class="ask-opt" id="${optId}" data-value="${escapeHtmlLite(String(opt))}"><label for="${optId}" class="tool-file" style="cursor:pointer;">${escapeHtmlLite(String(opt))}</label></div>`);
    }
  }
  
  // Add confirm button
  lines.push(`<div class="tool-line" style="margin-top:8px;display:flex;gap:8px;align-items:center;">
    <button class="ask-confirm-btn" data-tool-index="${idx}" style="padding:6px 14px;background:#3b82f6;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:13px;">Confirm</button>
    <span style="color:#6b7280;font-size:12px;">Waiting for your response...</span>
  </div>`);
  
  return lines.join('');
};
