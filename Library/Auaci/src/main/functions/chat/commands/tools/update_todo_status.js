// src/main/functions/chat/commands/tools/update_todo_status.js
const { loadTodos, saveTodos, appendLog } = require('./_shared');

module.exports = async function update_todo_status(params) {
  const id = params && params.id;
  const status = params && params.status;
  if (!id || !status) throw new Error('update_todo_status: id and status required');
  await appendLog(`[tools.update_todo_status] id=${id} status=${status}`);
  const { pending, completed } = await loadTodos();
  let found = null;
  const newPending = [];
  const newCompleted = [];
  for (const t of pending) {
    if (t.id === id) { found = { ...t }; continue; }
    newPending.push(t);
  }
  for (const t of completed) {
    if (t.id === id) { found = { ...t }; continue; }
    newCompleted.push(t);
  }
  if (!found) throw new Error('update_todo_status: todo not found');
  found.status = status;
  if (status === 'done') newCompleted.push(found); else newPending.push(found);
  await saveTodos(newPending, newCompleted);
  return { todo_list: { pending_todos: newPending, completed_todos: newCompleted } };
};
