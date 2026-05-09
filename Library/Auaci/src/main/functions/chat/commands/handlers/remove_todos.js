// src/main/functions/chat/commands/handlers/remove_todos.js
const { loadTodos, saveTodos } = require('../lib/todos');
const { appendLog } = require('../lib/utils');

// Enhanced Warp-style todo removal renderer (no emojis)
function formatRemovedTodo(todo, index, isCompleted = false) {
  const indexStr = String(index + 1).padStart(2, ' ');
  const statusIcon = isCompleted ? '[x]' : '[ ]';
  return `${indexStr}. ${statusIcon} ${todo.title}${todo.details ? `\n     ${todo.details}` : ''}`;
}

function renderRemovalSummary(removedPending, removedCompleted, notFoundIds, remainingCounts) {
  const sections = [];
  const totalRemoved = removedPending.length + removedCompleted.length;
  
  if (totalRemoved > 0) {
    sections.push(`Removed ${totalRemoved} todo${totalRemoved > 1 ? 's' : ''}`);
    sections.push('-'.repeat(50));
    
    if (removedPending.length > 0) {
      sections.push(`Pending tasks removed (${removedPending.length}):`);
      removedPending.forEach((todo, idx) => {
        sections.push(formatRemovedTodo(todo, idx, false));
      });
      if (removedCompleted.length > 0) sections.push(''); // Add spacing
    }
    
    if (removedCompleted.length > 0) {
      sections.push(`Completed tasks removed (${removedCompleted.length}):`);
      removedCompleted.forEach((todo, idx) => {
        sections.push(formatRemovedTodo(todo, idx, true));
      });
    }
    
    sections.push('');
    sections.push(`Remaining: ${remainingCounts.pending} pending, ${remainingCounts.completed} completed`);
  }
  
  if (notFoundIds.length > 0) {
    if (sections.length > 0) sections.push('');
    sections.push(`Could not find ${notFoundIds.length} todo ID${notFoundIds.length > 1 ? 's' : ''}:`);
    sections.push('-'.repeat(40));
    notFoundIds.forEach((id, idx) => {
      sections.push(`${String(idx + 1).padStart(2, ' ')}. ${id}`);
    });
  }
  
  if (totalRemoved === 0 && notFoundIds.length === 0) {
    sections.push('No valid todo IDs provided');
    sections.push('-'.repeat(40));
    sections.push('Please provide valid todo IDs to remove.');
  }
  
  return sections.join('\n');
}

module.exports = async function removeTodosCmd(params) {
  const ids = Array.isArray(params.todo_ids) ? params.todo_ids : [];
  
  if (ids.length === 0) {
    await appendLog('[tools.remove_todos] No todo IDs provided');
    return {
      success: false,
      error: 'No todo IDs provided',
      error_code: 'ERR_INVALID_INPUT',
      display_content: 'No todo IDs provided\n' + '-'.repeat(40) + '\nPlease provide an array of todo IDs to remove.',
      todo_list: { pending_todos: [], completed_todos: null },
      summary: { removed_count: 0, not_found_count: 0 }
    };
  }
  
  await appendLog(`[tools.remove_todos] Removing ${ids.length} todo${ids.length > 1 ? 's' : ''} by ID`);
  
  const { pending, completed } = await loadTodos();
  
  // Track what was removed and what wasn't found
  const removedPending = [];
  const removedCompleted = [];
  const notFoundIds = [];
  const foundIds = new Set();
  
  // Collect todos that will be removed for display
  pending.forEach(todo => {
    if (ids.includes(todo.id)) {
      removedPending.push(todo);
      foundIds.add(todo.id);
    }
  });
  
  completed.forEach(todo => {
    if (ids.includes(todo.id)) {
      removedCompleted.push(todo);
      foundIds.add(todo.id);
    }
  });
  
  // Find IDs that weren't found
  ids.forEach(id => {
    if (!foundIds.has(id)) {
      notFoundIds.push(id);
    }
  });
  
  // Filter out the todos to be removed
  const newPending = pending.filter(t => !ids.includes(t.id));
  const newCompleted = completed.filter(t => !ids.includes(t.id));
  
  await saveTodos(newPending, newCompleted);
  
  const displayContent = renderRemovalSummary(
    removedPending, 
    removedCompleted, 
    notFoundIds, 
    { pending: newPending.length, completed: newCompleted.length }
  );
  
  const result = {
    success: true,
    display_content: displayContent,
    todo_list: { 
      pending_todos: newPending, 
      completed_todos: newCompleted.length ? newCompleted : null 
    },
    summary: {
      removed_count: removedPending.length + removedCompleted.length,
      removed_pending: removedPending.length,
      removed_completed: removedCompleted.length,
      not_found_count: notFoundIds.length,
      remaining_pending: newPending.length,
      remaining_completed: newCompleted.length
    },
    removed_todos: {
      pending: removedPending,
      completed: removedCompleted
    },
    not_found_ids: notFoundIds
  };
  
  await appendLog(`[tools.remove_todos] Successfully removed ${removedPending.length + removedCompleted.length} todo${removedPending.length + removedCompleted.length > 1 ? 's' : ''}, ${notFoundIds.length} not found`);
  return result;
};
