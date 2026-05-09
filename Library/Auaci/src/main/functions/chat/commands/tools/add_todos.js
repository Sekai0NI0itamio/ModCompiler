// src/main/functions/chat/commands/tools/add_todos.js
const { loadTodos, saveTodos, newId, appendLog } = require('./_shared');

module.exports = async function add_todos(params) {
  const todos = Array.isArray(params && params.todos) ? params.todos : [];
  await appendLog(`[tools.add_todos] count=${todos.length}`);
  const { pending, completed } = await loadTodos();
  const toAdd = todos.map(t => ({ id: newId(), title: t.title || '', details: t.details || '', status: t.status || 'todo' }));
  const newPending = [...pending, ...toAdd.filter(t => t.status !== 'done')];
  const newCompleted = [...completed, ...toAdd.filter(t => t.status === 'done')];
  await saveTodos(newPending, newCompleted);
  return { todo_list: { pending_todos: newPending, completed_todos: newCompleted.length ? newCompleted : null } };
};
