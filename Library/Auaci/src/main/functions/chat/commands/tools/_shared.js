// src/main/functions/chat/commands/tools/_shared.js
const fs = require('fs').promises;
const fssync = require('fs');
const path = require('path');

const LOG_PATH = '/tmp/events.log';
const TODO_PATH = path.join(process.cwd(), '.auaci', 'todos.json');

function ensureDirSync(dir) {
  try {
    fssync.mkdirSync(dir, { recursive: true });
  } catch (_) {}
}

async function appendLog(msg) {
  try {
    await fs.appendFile(LOG_PATH, `[${new Date().toISOString()}] ${msg}\n`);
  } catch (_) {}
}

async function loadTodos() {
  try {
    const raw = await fs.readFile(TODO_PATH, 'utf8');
    const obj = JSON.parse(raw);
    return { pending: Array.isArray(obj.pending) ? obj.pending : [], completed: Array.isArray(obj.completed) ? obj.completed : [] };
  } catch {
    return { pending: [], completed: [] };
  }
}

async function saveTodos(pending, completed) {
  ensureDirSync(path.dirname(TODO_PATH));
  await fs.writeFile(TODO_PATH, JSON.stringify({ pending, completed }, null, 2), 'utf8');
}

function newId() {
  return (require('crypto').randomUUID ? require('crypto').randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2,8)}`);
}

module.exports = {
  fs,
  fssync,
  path,
  ensureDirSync,
  appendLog,
  loadTodos,
  saveTodos,
  newId,
  TODO_PATH,
};
