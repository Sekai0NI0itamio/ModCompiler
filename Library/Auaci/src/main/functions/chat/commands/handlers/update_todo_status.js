// src/main/functions/chat/commands/handlers/update_todo_status.js
const { loadTodos, saveTodos } = require('../lib/todos');
const { appendLog } = require('../lib/utils');

module.exports = async function updateTodoStatusCmd(params) {
  await appendLog(`[executor] update_todo_status id=${JSON.stringify(params && params.id)} status=${JSON.stringify(params && params.status)}`);
  const id = params.id;
  const status = params.status;
  if (!id || !status) {
    return { success: false, error: 'update_todo_status: id and status required', error_code: 'ERR_INVALID_INPUT' };
  }
  const { pending, completed } = await loadTodos();
  let found = null;
  let newPending = [];
  let newCompleted = [];
  for (const t of pending) { if (t.id === id) { found = Object.assign({}, t); continue; } newPending.push(t); }
  for (const t of completed) { if (t.id === id) { found = Object.assign({}, t); continue; } newCompleted.push(t); }
  if (!found) {
    return { success: false, error: 'update_todo_status: todo not found', error_code: 'ERR_NOT_FOUND', todo_id: id };
  }
  found.status = status;
  if (status === 'done') newCompleted.push(found); else newPending.push(found);
  await saveTodos(newPending, newCompleted);
  return { success: true, todo_list: { pending_todos: newPending, completed_todos: newCompleted } };
};
