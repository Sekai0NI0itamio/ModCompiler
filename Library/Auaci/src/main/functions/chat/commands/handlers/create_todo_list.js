// src/main/functions/chat/commands/handlers/create_todo_list.js
const { newId, saveTodos } = require('../lib/todos');
const { appendLog } = require('../lib/utils');

// Enhanced Warp-style todo list creation renderer (no emojis)
function formatNewTodo(todo, index) {
  const indexStr = String(index + 1).padStart(2, ' ');
  return `${indexStr}. [ ] ${todo.title}${todo.details ? `\n     ${todo.details}` : ''}`;
}

function renderTodoListCreation(todos) {
  const sections = [];
  
  if (todos.length > 0) {
    sections.push(`Created new todo list with ${todos.length} task${todos.length > 1 ? 's' : ''}`);
    sections.push('-'.repeat(60));
    
    todos.forEach((todo, idx) => {
      sections.push(formatNewTodo(todo, idx));
    });
    
    sections.push('');
    sections.push(`Ready to start. Use read_todos to view your list anytime.`);
  } else {
    sections.push('New Todo List Created');
    sections.push('-'.repeat(40));
    sections.push('Empty list created. Use add_todos to add your first tasks.');
  }
  
  return sections.join('\n');
}

module.exports = async function createTodoListCmd(params) {
  const todos = Array.isArray(params.todos) ? params.todos : [];
  
  await appendLog(`[tools.create_todo_list] Creating new todo list with ${todos.length} todo${todos.length > 1 ? 's' : ''}`);
  
  const pending = todos.map(t => ({ 
    id: newId(), 
    title: String(t.title || '').trim() || 'Untitled Task', 
    details: String(t.details || '').trim() 
  }));
  
  const completed = [];
  await saveTodos(pending, completed);
  
  const displayContent = renderTodoListCreation(pending);
  
  const result = {
    success: true,
    display_content: displayContent,
    todo_list: { 
      pending_todos: pending, 
      completed_todos: completed.length ? completed : null 
    },
    summary: {
      created_count: pending.length,
      total_pending: pending.length,
      total_completed: 0
    },
    created_todos: pending
  };
  
  await appendLog(`[tools.create_todo_list] Successfully created todo list with ${pending.length} todo${pending.length > 1 ? 's' : ''}`);
  return result;
};
