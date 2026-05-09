// src/main/functions/chat/helpers/renderGate.js
// Centralized render gating per session. Ensures only the active session renders UI.

function ensureGlobals() {
  try {
    if (!window.__auaciSessionRenderEnabled) window.__auaciSessionRenderEnabled = Object.create(null);
    if (typeof window.__auaciActiveSessionId !== 'string') {
      // leave undefined; will be set by events/tab switches
    }
  } catch (_) {}
}

function setActiveSessionId(sessionId) {
  ensureGlobals();
  try { window.__auaciActiveSessionId = sessionId || null; } catch (_) {}
}

function getActiveSessionId() {
  try { return window.__auaciActiveSessionId || null; } catch (_) { return null; }
}

function enableRenderForSession(sessionId, enabled = true) {
  ensureGlobals();
  try { window.__auaciSessionRenderEnabled[String(sessionId || '')] = !!enabled; } catch (_) {}
}

function isRenderEnabled(sessionId) {
  ensureGlobals();
  try {
    const sid = String(sessionId || '');
    // default to true if not explicitly disabled
    return window.__auaciSessionRenderEnabled.hasOwnProperty(sid) ? !!window.__auaciSessionRenderEnabled[sid] : true;
  } catch (_) { return true; }
}

function shouldRenderForSession(sessionId) {
  ensureGlobals();
  try {
    // Allow rendering when sessionId is unknown or when active isn't set yet (background/unfocused states)
    if (!sessionId) return true;
    const active = getActiveSessionId();
    if (!active) return true; // do not block background rendering when active session hasn't been determined
    if (String(sessionId) !== String(active)) return false;
    return isRenderEnabled(sessionId);
  } catch (_) { return true; }
}

module.exports = {
  setActiveSessionId,
  getActiveSessionId,
  enableRenderForSession,
  isRenderEnabled,
  shouldRenderForSession
};
