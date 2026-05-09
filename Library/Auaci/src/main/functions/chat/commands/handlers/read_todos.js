// src/main/functions/chat/commands/handlers/read_todos.js
const { loadTodos } = require('../lib/todos');
const { appendLog } = require('../lib/utils');

// Enhanced Warp-style todo list renderer (no emojis)
function formatTodoItem(todo, index, isCompleted = false) {
  const statusIcon = isCompleted ? '[x]' : '[ ]';
  const indexStr = String(index + 1).padStart(2, ' ');
  return `${indexStr}. ${statusIcon} ${todo.title}${todo.details ? `\n     ${todo.details}` : ''}`;
}

function renderTodoList(pending, completed) {
  const sections = [];
  
  if (pending.length > 0) {
    sections.push(`Pending Tasks (${pending.length})`);
    sections.push('-'.repeat(40));
    pending.forEach((todo, idx) => {
      sections.push(formatTodoItem(todo, idx, false));
    });
  }
  
  if (completed.length > 0) {
    if (sections.length > 0) sections.push(''); // Add spacing
    sections.push(`Completed Tasks (${completed.length})`);
    sections.push('-'.repeat(40));
    completed.forEach((todo, idx) => {
      sections.push(formatTodoItem(todo, idx, true));
    });
  }
  
  if (pending.length === 0 && completed.length === 0) {
    sections.push('Todo List');
    sections.push('-'.repeat(40));
    sections.push('No todos found. Use create_todo_list or add_todos to get started.');
  }
  
  return sections.join('\n');
}

module.exports = async function readTodosCmd() {
  await appendLog('[tools.read_todos] Reading todo list');
  const { pending, completed } = await loadTodos();
  
  const displayContent = renderTodoList(pending, completed);
  
  const result = {
    success: true,
    display_content: displayContent,
    todo_list: {
      pending_todos: pending,
      completed_todos: completed.length ? completed : null
    },
    summary: {
      pending_count: pending.length,
      completed_count: completed.length,
      total_count: pending.length + completed.length
    }
  };
  
  await appendLog(`[tools.read_todos] Found ${pending.length} pending, ${completed.length} completed todos`);
  return result;
};
