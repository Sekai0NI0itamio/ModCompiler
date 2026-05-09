// src/main/functions/chat/commands/handlers/add_todos.js
const { loadTodos, saveTodos, newId } = require('../lib/todos');
const { appendLog } = require('../lib/utils');

// Enhanced Warp-style todo addition renderer (no emojis)
function formatAddedTodo(todo, originalIndex) {
  const indexStr = String(originalIndex + 1).padStart(2, ' ');
  return `${indexStr}. [ ] ${todo.title}${todo.details ? `\n     ${todo.details}` : ''}`;
}

function renderAdditionSummary(addedTodos, totalPending) {
  const sections = [];
  
  sections.push(`Added ${addedTodos.length} new todo${addedTodos.length > 1 ? 's' : ''}`);
  sections.push('-'.repeat(50));
  
  addedTodos.forEach((todo, idx) => {
    const originalIndex = totalPending - addedTodos.length + idx;
    sections.push(formatAddedTodo(todo, originalIndex));
  });
  
  sections.push('');
  sections.push(`Total pending todos: ${totalPending}`);
  
  return sections.join('\n');
}

module.exports = async function addTodosCmd(params) {
  const todos = Array.isArray(params.todos) ? params.todos : [];
  
  if (todos.length === 0) {
    await appendLog('[tools.add_todos] No todos provided');
    return {
      success: false,
      error: 'No todos provided',
      error_code: 'ERR_INVALID_INPUT',
      display_content: 'No todos provided\n' + '-'.repeat(30) + '\nPlease provide an array of todos to add.',
      todo_list: { pending_todos: [], completed_todos: null },
      summary: { added_count: 0, total_pending: 0 }
    };
  }
  
  await appendLog(`[tools.add_todos] Adding ${todos.length} todo${todos.length > 1 ? 's' : ''}`);
  
  const { pending, completed } = await loadTodos();
  const added = todos.map(t => ({ 
    id: newId(), 
    title: String(t.title || '').trim() || 'Untitled Task', 
    details: String(t.details || '').trim() 
  }));
  
  const newPending = [...pending, ...added];
  await saveTodos(newPending, completed);
  
  const displayContent = renderAdditionSummary(added, newPending.length);
  
  const result = {
    success: true,
    display_content: displayContent,
    todo_list: { 
      pending_todos: newPending, 
      completed_todos: completed.length ? completed : null 
    },
    summary: {
      added_count: added.length,
      total_pending: newPending.length,
      total_completed: completed.length
    },
    added_todos: added
  };
  
  await appendLog(`[tools.add_todos] Successfully added ${added.length} todo${added.length > 1 ? 's' : ''}, total pending: ${newPending.length}`);
  return result;
};
