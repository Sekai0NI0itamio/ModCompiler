// src/main/functions/chat/commands/handlers/mark_todo_as_done.js
const { loadTodos, saveTodos } = require('../lib/todos');
const { appendLog } = require('../lib/utils');

// Enhanced Warp-style completion renderer (no emojis)
function formatCompletedTodo(todo, index) {
  const indexStr = String(index + 1).padStart(2, ' ');
  return `${indexStr}. [x] ${todo.title}${todo.details ? `\n     ${todo.details}` : ''}`;
}

function renderCompletionSummary(completedTodos, notFoundIds, remainingCount) {
  const sections = [];
  
  if (completedTodos.length > 0) {
    sections.push(`Marked ${completedTodos.length} todo${completedTodos.length > 1 ? 's' : ''} as complete`);
    sections.push('-'.repeat(50));
    
    completedTodos.forEach((todo, idx) => {
      sections.push(formatCompletedTodo(todo, idx));
    });
    
    sections.push('');
    sections.push(`Remaining pending todos: ${remainingCount}`);
  }
  
  if (notFoundIds.length > 0) {
    if (sections.length > 0) sections.push('');
    sections.push(`Could not find ${notFoundIds.length} todo ID${notFoundIds.length > 1 ? 's' : ''}:`);
    sections.push('-'.repeat(40));
    notFoundIds.forEach((id, idx) => {
      sections.push(`${String(idx + 1).padStart(2, ' ')}. ${id}`);
    });
  }
  
  if (completedTodos.length === 0 && notFoundIds.length === 0) {
    sections.push('No valid todo IDs provided');
    sections.push('-'.repeat(40));
    sections.push('Please provide valid todo IDs to mark as complete.');
  }
  
  return sections.join('\n');
}

module.exports = async function markTodoAsDoneCmd(params) {
  const ids = Array.isArray(params.todo_ids) ? params.todo_ids : [];
  
  if (ids.length === 0) {
    await appendLog('[tools.mark_todo_as_done] No todo IDs provided');
    return {
      success: false,
      error: 'No todo IDs provided',
      error_code: 'ERR_INVALID_INPUT',
      display_content: 'No todo IDs provided\n' + '-'.repeat(40) + '\nPlease provide an array of todo IDs to mark as complete.',
      todo_list: { pending_todos: [], completed_todos: null },
      summary: { completed_count: 0, not_found_count: 0 }
    };
  }
  
  await appendLog(`[tools.mark_todo_as_done] Marking ${ids.length} todo${ids.length > 1 ? 's' : ''} as done`);
  
  const { pending, completed } = await loadTodos();
  const remaining = [];
  const moved = [];
  const notFoundIds = [];
  
  // Track which IDs were found
  const foundIds = new Set();
  
  for (const t of pending) {
    if (ids.includes(t.id)) {
      moved.push(t);
      foundIds.add(t.id);
    } else {
      remaining.push(t);
    }
  }
  
  // Find IDs that weren't found in pending todos
  for (const id of ids) {
    if (!foundIds.has(id)) {
      notFoundIds.push(id);
    }
  }
  
  const newCompleted = [...completed, ...moved];
  await saveTodos(remaining, newCompleted);
  
  const displayContent = renderCompletionSummary(moved, notFoundIds, remaining.length);
  
  const result = {
    success: true,
    display_content: displayContent,
    todo_list: { 
      pending_todos: remaining, 
      completed_todos: newCompleted.length ? newCompleted : null 
    },
    summary: {
      completed_count: moved.length,
      not_found_count: notFoundIds.length,
      remaining_pending: remaining.length,
      total_completed: newCompleted.length
    },
    completed_todos: moved,
    not_found_ids: notFoundIds
  };
  
  await appendLog(`[tools.mark_todo_as_done] Successfully completed ${moved.length} todo${moved.length > 1 ? 's' : ''}, ${notFoundIds.length} not found`);
  return result;
};
