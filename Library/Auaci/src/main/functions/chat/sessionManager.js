// src/main/functions/chat/sessionManager.js
// Robust session manager: searches multiple candidate history locations (cwd and homedir),
// reuses any existing session_id.txt or session files, prefers existing data rather than
// creating a new session unnecessarily.

const { execFile } = require('child_process');
const fs = require('fs').promises;
const path = require('path');
const os = require('os');
const crypto = require('crypto');

const CANDIDATE_BASE_DIRS = [
  path.join(process.cwd(), '.auaci', 'chathistory'), // project-local (preferred)
  path.join(os.homedir(), '.auaci', 'chathistory')   // user-home (fallback)
];

// Ensure candidate dirs do not point into the repository source code directories
// If a candidate resolves to a path inside the project source (e.g., src/ or __dirname), prefer cwd location only.
for (let i = 0; i < CANDIDATE_BASE_DIRS.length; i++) {
  const resolved = path.resolve(CANDIDATE_BASE_DIRS[i]);
  // Block writing into the codebase (any path under process.cwd()/src or __dirname)
  const srcDir = path.resolve(process.cwd(), 'src');
  const thisFileDir = path.resolve(__dirname);
  if (resolved.startsWith(srcDir) || resolved.startsWith(thisFileDir)) {
    // Replace with project-local .auaci/chathistory to be safe
    CANDIDATE_BASE_DIRS[i] = path.join(process.cwd(), '.auaci', 'chathistory');
  }
}

const SESSION_FILE_NAME = 'session_id.txt';
const SESSIONS_SUBDIR = 'sessions';
const LOG_PATH = '/tmp/events.log';

async function appendLog(msg) {
  try {
    await fs.appendFile(LOG_PATH, `[${new Date().toISOString()}] ${msg}\n`);
  } catch (_) { /* ignore logging errors */ }
}

async function tryGenerateSessionFromCmd() {
  const binary = path.join(process.cwd(), 'generate_session');
  return new Promise((resolve) => {
    execFile(binary, [], { cwd: process.cwd() }, (err, stdout) => {
      if (!err && stdout) return resolve(String(stdout || '').trim());
      return resolve(null);
    });
  });
}

async function pathExists(p) {
  try {
    await fs.access(p);
    return true;
  } catch {
    return false;
  }
}

async function readSessionIdFileAt(baseDir) {
  try {
    const p = path.join(baseDir, SESSION_FILE_NAME);
    const s = String(await fs.readFile(p, 'utf8') || '').trim();
    if (s) return { sessionId: s, dir: baseDir, path: p };
  } catch (_) {}
  return null;
}

async function findAnySessionFileIn(baseDir) {
  try {
    const sessionsDir = path.join(baseDir, SESSIONS_SUBDIR);
    const exists = await pathExists(sessionsDir);
    if (!exists) return null;
    const files = (await fs.readdir(sessionsDir)).filter(f => f.endsWith('.json'));
    if (!files || files.length === 0) return null;

    // choose the most recently modified session file
    let chosen = null;
    let chosenMtime = 0;
    for (const f of files) {
      const full = path.join(sessionsDir, f);
      try {
        const st = await fs.stat(full);
        const mtime = st.mtimeMs || st.ctimeMs || 0;
        if (!chosen || mtime > chosenMtime) {
          chosen = f;
          chosenMtime = mtime;
        }
      } catch (_) {}
    }
    if (!chosen) return null;
    const sid = chosen.replace(/\.json$/, '');
    return { sessionId: sid, dir: baseDir, path: path.join(sessionsDir, chosen) };
  } catch (_) {
    return null;
  }
}

async function ensureDirsAt(baseDir) {
  try {
    await fs.mkdir(baseDir, { recursive: true });
    await fs.mkdir(path.join(baseDir, SESSIONS_SUBDIR), { recursive: true });
  } catch (_) {}
}

/**
 * setSessionId(sessionId)
 * Persist the provided sessionId as session_id.txt in the preferred candidate dir.
 * This is used when the GPT server returns its generated session_id so the renderer
 * reuses the server-side identifier on subsequent requests.
 */
async function setSessionId(sessionId) {
  if (!sessionId || typeof sessionId !== 'string') return;
  try {
    // Persist session id only into the directory where the session file will live.
    // If a session file exists in any candidate dir, write session_id.txt next to it; otherwise write to preferred dir.
    let targetDir = null;
    for (const baseDir of CANDIDATE_BASE_DIRS) {
      const candidatePath = path.join(baseDir, SESSIONS_SUBDIR, `${sessionId}.json`);
      try {
        await fs.access(candidatePath);
        targetDir = baseDir;
        break;
      } catch (_) {}
    }
    if (!targetDir) targetDir = CANDIDATE_BASE_DIRS[0];

    await ensureDirsAt(targetDir);
    const p = path.join(targetDir, SESSION_FILE_NAME);
    await fs.writeFile(p, sessionId, 'utf8');
    await appendLog(`setSessionId: wrote ${sessionId} -> ${p}`);
  } catch (e) {
    await appendLog(`setSessionId: failed to persist session id: ${String(e && e.message ? e.message : e)}`);
  }
}

/**
 * getSessionId()
 * - Prefer existing session_id.txt found in candidate dirs
 * - If missing, prefer existing session file in candidate dirs (reuse)
 * - Else try generate_session command
 * - Finally fall back to random UUID
 *
 * Returns: the chosen sessionId and sets chosenDir to where session_id.txt was or will be written.
 */
async function getSessionId() {
  // 1) check for existing session_id.txt in candidate dirs (in defined priority)
  for (const baseDir of CANDIDATE_BASE_DIRS) {
    const res = await readSessionIdFileAt(baseDir);
    if (res && res.sessionId) {
      await appendLog(`Found existing session_id.txt at ${res.path} -> ${res.sessionId}`);
      return res.sessionId;
    }
  }

  // 2) check for any existing session files in candidate dirs and reuse the most recent
  for (const baseDir of CANDIDATE_BASE_DIRS) {
    const found = await findAnySessionFileIn(baseDir);
    if (found && found.sessionId) {
      // Found an existing session file; prefer returning its session id and do not write session_id.txt automatically.
      // Writing session_id.txt is a user-visible action and should only happen when explicitly requested.
      await appendLog(`Reused existing session file ${found.path}; will not auto-write session_id.txt`);
      return found.sessionId;
    }
  }

  // 3) try external generate_session command (project root)
  try {
    const fromCmd = await tryGenerateSessionFromCmd();
    if (fromCmd) {
      // Persist into first writable candidate (prefer process.cwd location)
      const targetDir = CANDIDATE_BASE_DIRS[0];
      try {
        await ensureDirsAt(targetDir);
        await fs.writeFile(path.join(targetDir, SESSION_FILE_NAME), fromCmd, 'utf8');
        await appendLog(`generate_session provided id ${fromCmd}; saved to ${path.join(targetDir, SESSION_FILE_NAME)}`);
      } catch (e) {
        await appendLog(`generate_session returned id ${fromCmd} but failed to write session_id.txt: ${String(e && e.message ? e.message : e)}`);
      }
      return fromCmd;
    }
  } catch (e) {
    await appendLog(`generate_session invocation error: ${String(e && e.message ? e.message : e)}`);
  }

  // 4) fallback to random id and persist into the preferred project-local candidate
  const fallback = (crypto && crypto.randomUUID) ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2,8)}`;
  try {
    const targetDir = CANDIDATE_BASE_DIRS[0]; // enforce project-local for new sessions
    await ensureDirsAt(targetDir);
    // create sessions subdir and the session JSON file as an empty session object
    const sidPath = path.join(targetDir, SESSION_FILE_NAME);
    const sessionJsonPath = path.join(targetDir, SESSIONS_SUBDIR, `${fallback}.json`);
    const init = { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), session_id: fallback, name: 'New Session', chat: [] };
    await fs.writeFile(sidPath, fallback, 'utf8');
    await fs.mkdir(path.join(targetDir, SESSIONS_SUBDIR), { recursive: true });
    await fs.writeFile(sessionJsonPath, JSON.stringify(init, null, 2), 'utf8');
    await appendLog(`Falling back to generated id ${fallback}; wrote session_id.txt and session file to ${targetDir}`);
  } catch (e) {
    await appendLog(`Failed to persist fallback session id and file: ${String(e && e.message ? e.message : e)}`);
  }
  return fallback;
}

/**
 * getSessionFilePath(sessionId = null)
 * - If sessionId is provided, try to locate the file in candidate directories (prefer cwd then homedir).
 * - If not found, create/return a path in the preferred candidate (CANDIDATE_BASE_DIRS[0]).
 */
async function getSessionFilePath(sessionId = null) {
  // If sessionId provided, try to find file in existing sessions dirs
  if (sessionId) {
    for (const baseDir of CANDIDATE_BASE_DIRS) {
      const filePath = path.join(baseDir, SESSIONS_SUBDIR, `${sessionId}.json`);
      if (await pathExists(filePath)) {
        await appendLog(`Found session file for provided sessionId at ${filePath}`);
        return filePath;
      }
    }
    // Not found: create new session file in the preferred project-local dir
    const preferred = CANDIDATE_BASE_DIRS[0];
    await ensureDirsAt(preferred);
    const newPath = path.join(preferred, SESSIONS_SUBDIR, `${sessionId}.json`);
    // create empty session file immediately to reserve the session
    const init = { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), session_id: sessionId, name: 'New Session', chat: [] };
    await fs.mkdir(path.join(preferred, SESSIONS_SUBDIR), { recursive: true });
    await fs.writeFile(newPath, JSON.stringify(init, null, 2), 'utf8');
    await appendLog(`Created new session file for provided sessionId at ${newPath}`);
    return newPath;
  }

  // No sessionId provided -> determine sessionId and location via getSessionId
  const sid = await getSessionId();

  // Prefer to find existing session file in candidate dirs first
  for (const baseDir of CANDIDATE_BASE_DIRS) {
    const filePath = path.join(baseDir, SESSIONS_SUBDIR, `${sid}.json`);
    if (await pathExists(filePath)) {
      await appendLog(`Using existing session file: ${filePath}`);
      return filePath;
    }
  }

  // Else create in preferred candidate (project-local)
  const preferred = CANDIDATE_BASE_DIRS[0];
  await ensureDirsAt(preferred);
  const target = path.join(preferred, SESSIONS_SUBDIR, `${sid}.json`);
  // create file to ensure new sessions are stored in project-local
  const init = { creationdate: new Date().toISOString(), lastused: new Date().toISOString(), session_id: sid, name: 'New Session', chat: [] };
  await fs.mkdir(path.join(preferred, SESSIONS_SUBDIR), { recursive: true });
  await fs.writeFile(target, JSON.stringify(init, null, 2), 'utf8');
  await appendLog(`Will create session file at: ${target}`);
  return target;
}

module.exports = {
  getSessionId,
  getSessionFilePath,
  setSessionId,
  CANDIDATE_BASE_DIRS
};