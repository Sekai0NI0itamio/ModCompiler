// src/main/functions/chat/helpers/toolRenders/completed/todo.js
// Renderer for completed todo tools

const { escapeHtmlLite } = require('../shared');

function checkboxUncheckedSvg() {
  return '<svg class="todo-icon unchecked" width="16" height="16" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" fill="none"><rect x="3" y="3" width="18" height="18" rx="4" ry="4" stroke="#6b7280" stroke-width="2" fill="none"/></svg>';
}

function checkboxCheckedSvg() {
  return '<svg class="todo-icon checked" width="16" height="16" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" fill="none"><rect x="3" y="3" width="18" height="18" rx="4" ry="4" stroke="#1a7f37" stroke-width="2" fill="none"/><polyline points="7,12 11,16 17,8" stroke="#1a7f37" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>';
}

function render(input, result, options = {}) {
  const lines = [];
  
  const res = result || {};
  const tl = res.todo_list || {};
  const pending = Array.isArray(tl.pending_todos) ? tl.pending_todos : [];
  const completed = Array.isArray(tl.completed_todos) ? tl.completed_todos : [];
  const addedIds = Array.isArray(res.added_ids) ? new Set(res.added_ids) : new Set();
  
  function renderItem(item, isCompleted) {
    const isNew = addedIds.has(item.id);
    const cls = ['todo-item'];
    if (isCompleted) cls.push('completed');
    if (isNew) cls.push('new');
    const icon = isCompleted ? checkboxCheckedSvg() : checkboxUncheckedSvg();
    const title = escapeHtmlLite(String(item.title || ''));
    const details = escapeHtmlLite(String(item.details || ''));
    return `
      <div class="${cls.join(' ')}">
        ${icon}
        <div class="todo-main">
          <div class="todo-text">${title}</div>
          ${details ? `<div class="todo-details">${details}</div>` : ''}
        </div>
      </div>
    `;
  }
  
  lines.push(`<div class="tool-line" style="font-weight:600;">Todos</div>`);
  lines.push('<div class="todo-list">');
  
  for (const t of pending) {
    lines.push(renderItem(t, false));
  }
  for (const t of completed) {
    lines.push(renderItem(t, true));
  }
  
  lines.push('</div>');
  
  return lines.join('');
}

module.exports = { render };
