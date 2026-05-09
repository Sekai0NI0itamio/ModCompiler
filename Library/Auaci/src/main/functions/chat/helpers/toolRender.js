// Shared rendering helper for tool placeholders inside a message-content container.
// Renders compact summaries for tools (ls, view, grep, run_command/bash, etc.)
// - For "view": show one line per file with success/fail icon + file path (NO file contents shown).
// - For "ls": show path + lightweight details (entries: N or max_depth).
// This helper consumes: "tools" (parsed from <tool_use> tags) and "toolRuns" (structured run summaries if available).

const marked = require('marked');
const { createRenderer } = require('../renderer');
const { revealInFinder } = require('../../directory_viewer/buttons/revealInFinder');

// Import modular tool renderers
const toolRenders = require('./toolRenders');
const { renderToolExecuting, isToolExecuting, getToolHeaderTitle } = toolRenders;

// Import enhanced tool renderer
const { renderEnhancedToolResult } = require('./enhancedToolRenderer');

function escapeHtmlLite(s) {
  return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function okIconSvg() {
  return '<svg class="tool-icon ok" width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M9 16.2l-3.5-3.5L4 14.2l5 5 11-11-1.5-1.5L9 16.2z" fill="#1a7f37"/></svg>';
}
function failIconSvg() {
  return '<svg class="tool-icon fail" width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M18.3 5.71L12 12.01 5.7 5.7 4.29 7.11 10.59 13.4 4.29 19.7 5.7 21.11 12 14.81 18.3 21.12 19.71 19.71 13.41 13.4 19.71 7.1z" fill="#b00020"/></svg>';
}
function pendingIconSvg() {
  return '<svg class="tool-icon pending" width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="10" stroke="#999" stroke-width="2" fill="none"/><path d="M12 6v6l4 2" stroke="#999" stroke-width="2" fill="none" stroke-linecap="round"/></svg>';
}

function spinnerIconSvg() {
  return '<svg class="tool-icon spinner" width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="10" stroke="#e5e7eb" stroke-width="2" fill="none"/><path d="M12 2a10 10 0 0 1 10 10" stroke="#3b82f6" stroke-width="2" fill="none" stroke-linecap="round"/></svg>';
}

// Todo-specific icons (high-quality SVG, no emojis)
function checkboxUncheckedSvg() {
  return '<svg class="todo-icon unchecked" width="16" height="16" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" fill="none"><rect x="3" y="3" width="18" height="18" rx="4" ry="4" stroke="#6b7280" stroke-width="2" fill="none"/></svg>';
}
function checkboxCheckedSvg() {
  return '<svg class="todo-icon checked" width="16" height="16" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" fill="none"><rect x="3" y="3" width="18" height="18" rx="4" ry="4" stroke="#1a7f37" stroke-width="2" fill="none"/><polyline points="7,12 11,16 17,8" stroke="#1a7f37" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>';
}

function isTodoTool(nameLower) {
  const n = String(nameLower || '').toLowerCase();
  return n === 'create_todo_list' || n === 'read_todos' || n === 'add_todos' || n === 'remove_todos' || n === 'mark_todo_as_done' || n === 'update_todo_status';
}

// Utility: determine a reasonable comment prefix from file extension.
// Fallback to '//' if unknown.
function commentPrefixForPath(p) {
  if (!p || typeof p !== 'string') return '//';
  const idx = p.lastIndexOf('.');
  const ext = idx !== -1 ? p.slice(idx + 1).toLowerCase() : '';
  const hashExts = new Set(['py', 'sh', 'bash', 'zsh', 'rb', 'pl', 'r', 'yml', 'yaml', 'ini', 'env', 'ps1']);
  const slashslashExts = new Set(['js','ts','jsx','tsx','java','c','cpp','cs','go','rs','swift','kt','kts','scala','php']);
  if (hashExts.has(ext)) return '#';
  if (slashslashExts.has(ext)) return '//';
  // Defaults
  return '//';
}

// Utility: derive a language class for code block from extension (best-effort)
function languageForPath(p) {
  if (!p || typeof p !== 'string') return '';
  const idx = p.lastIndexOf('.');
  const ext = idx !== -1 ? p.slice(idx + 1).toLowerCase() : '';
  const map = {
    'js':'javascript','jsx':'jsx','ts':'typescript','tsx':'tsx','py':'python','java':'java',
    'c':'c','cpp':'cpp','cc':'cpp','cs':'csharp','go':'go','rs':'rust','swift':'swift',
    'kt':'kotlin','kts':'kotlin','rb':'ruby','sh':'bash','bash':'bash','ps1':'powershell',
    'json':'json','html':'html','css':'css','scss':'scss','yaml':'yaml','yml':'yaml','lua':'lua'
  };
  return map[ext] || '';
}

/**
 * Try to coerce a value into true/false/null.
 * Accepts booleans, numeric 0/1, and common "true"/"false"/"1"/"0" string forms.
 * Returns:
 *   true  -> definite true
 *   false -> definite false
 *   null  -> unknown / cannot coerce
 */
function coerceBooleanOrNull(v) {
  if (v === null || typeof v === 'undefined') return null;
  if (typeof v === 'boolean') return v;
  if (typeof v === 'number') {
    if (v === 1) return true;
    if (v === 0) return false;
    return null;
  }
  if (typeof v === 'string') {
    const s = v.trim().toLowerCase();
    if (s === 'true' || s === '1' || s === 'yes' || s === 'y') return true;
    if (s === 'false' || s === '0' || s === 'no' || s === 'n') return false;
    return null;
  }
  return null;
}

/**
 * computeReplaceCounts(theDiff, summary)
 * - Prefer summary counts if provided by a run record.
 * - Support both SEARCH/REPLACE blocks and unified-diff style to compute added/removed counts.
 */
function computeReplaceCounts(theDiff, summary) {
  // Prefer summary if provided by run record
  if (summary && (typeof summary.added === 'number' || typeof summary.removed === 'number')) {
    return { added: Number(summary.added||0), removed: Number(summary.removed||0) };
  }
  const diff = typeof theDiff === 'string' ? theDiff : '';
  if (!diff) return { added: 0, removed: 0 };

  // If unified diff format detected, count +/- lines (skip file header lines)
  const lines = diff.split(/\r?\n/);
  const unifiedHunkRe = /^@@\s*-\d+(?:,\d+)?\s*\+\d+(?:,\d+)?\s*@@/;
  const hasUnified = lines.some(l => unifiedHunkRe.test(l));
  if (hasUnified) {
    let added = 0, removed = 0;
    for (const line of lines) {
      if (line.startsWith('+++') || line.startsWith('---') || unifiedHunkRe.test(line) || line.startsWith('diff ') || line.startsWith('index ')) continue;
      if (line.startsWith('+')) added++;
      else if (line.startsWith('-')) removed++;
    }
    return { added, removed };
  }

  // Fallback: parse the custom SEARCH/REPLACE block format (state machine)
  let removed = 0, added = 0;
  let state = 'idle';
  for (const line of lines) {
    if (/^\s*[-]{5,}\s*SEARCH/i.test(line)) { state = 'search'; continue; }
    if (/^\s*[=]{5,}/.test(line) && state === 'search') { state = 'replace'; continue; }
    if (/^\s*[+]{5,}\s*REPLACE/i.test(line)) { state = 'idle'; continue; }
    if (state === 'search') removed++;
    else if (state === 'replace') added++;
  }
  return { added, removed };
}

/**
 * Try to extract modified file contents from a run record.
 * Looks through a few common shapes produced by server-side handlers:
 * - result.file_contents_after_edit: [{ file_content: { path, content, ... } }]
 * - result.file_content: { path, content } or result.file_contents: [{path, content}]
 * - summary.files / summary.modified_files / result.modified_files
 * - input.path + (result.content || result.file_content)
 *
 * Returns array of { path, content } or [].
 */
function extractFilesFromRun(r) {
  const out = [];
  if (!r || typeof r !== 'object') return out;

  function pushIfValid(p, c) {
    if (!p) return;
    if (typeof c !== 'string') return;
    out.push({ path: String(p), content: String(c) });
  }

  // Helper to scan arbitrary object for content/path pairs
  function scanObj(obj) {
    if (!obj || typeof obj !== 'object') return;
    // Known array shapes
    if (Array.isArray(obj.file_contents_after_edit)) {
      for (const item of obj.file_contents_after_edit) {
        if (item && item.file_content && item.file_content.path && item.file_content.content) {
          pushIfValid(item.file_content.path, item.file_content.content);
        } else if (item && item.path && item.content) {
          pushIfValid(item.path, item.content);
        }
      }
    }
    if (Array.isArray(obj.file_contents)) {
      for (const item of obj.file_contents) {
        if (item && item.path && item.content) pushIfValid(item.path, item.content);
      }
    }
    if (Array.isArray(obj.files)) {
      for (const f of obj.files) {
        if (f && f.path && f.content) pushIfValid(f.path, f.content);
        // sometimes files entries contain 'file_path' and 'file_text'
        if (f && f.file_path && f.file_text) pushIfValid(f.file_path, f.file_text);
      }
    }
    if (Array.isArray(obj.modified_files)) {
      for (const f of obj.modified_files) {
        if (f && f.path && f.content) pushIfValid(f.path, f.content);
      }
    }
    // Single-object shapes
    if (obj.file_content && obj.file_content.path && obj.file_content.content) pushIfValid(obj.file_content.path, obj.file_content.content);
    if (obj.path && obj.content) pushIfValid(obj.path, obj.content);
    if (obj.path && obj.file_text) pushIfValid(obj.path, obj.file_text);
  }

  scanObj(r.result);
  scanObj(r.summary);
  scanObj(r);

  // Fallback: sometimes result contains a plain 'content' and input.path
  try {
    if (r.input && r.input.path && r.result && typeof r.result.content === 'string') {
      pushIfValid(r.input.path, r.result.content);
    }
  } catch (_) {}

  // Deduplicate by path (keep first)
  const seen = new Set();
  const dedup = [];
  for (const f of out) {
    if (!seen.has(f.path)) {
      seen.add(f.path);
      dedup.push(f);
    }
  }
  return dedup;
}
/**
 * Strip left-side line-number prefixes produced by withLineNumbers (e.g. "1|line").
 */
function stripNumericLinePrefixes(s) {
  if (typeof s !== 'string') return s;
  // remove leading "digits|"
  return s.replace(/^\s*\d+\|/gm, '');
}

/**
 * Hide a single adjacent previous element (simple heuristic) when a replace_in_file tool appears
 * while still incomplete. We only hide a previous element if it's a plain textual block (P, DIV, SPAN)
 * and doesn't contain code/pre/list/table. We mark it so it can be restored later.
 */
function hideImmediatePreviousTextElement(box) {
  try {
    const prev = box.previousSibling;
    if (!prev) return;
    if (prev.nodeType === Node.ELEMENT_NODE) {
      const el = prev;
      const tag = (el.tagName || '').toUpperCase();
      if (!['P','DIV','SPAN','SECTION'].includes(tag)) return;
      // don't hide if it contains code blocks, lists, tables, or other tool boxes
      if (el.querySelector && (el.querySelector('pre, code, ol, ul, table, .auaci-tool-box'))) return;
      if (!el.textContent || !el.textContent.trim()) return;
      el.dataset.auaciHiddenByReplace = '1';
      el.style.display = 'none';
      // store marker on box to allow restore
      const arr = box.__auaciHiddenNodes || [];
      arr.push(el);
      box.__auaciHiddenNodes = arr;
    }
  } catch (_) {}
}

/**
 * Restore any previously hidden nodes for a box.
 */
function restoreHiddenNodes(box) {
  try {
    if (!box) return;
    const nodes = box.__auaciHiddenNodes;
    if (Array.isArray(nodes)) {
      for (const n of nodes) {
        try {
          if (n && n.dataset && n.dataset.auaciHiddenByReplace) {
            n.style.display = '';
            delete n.dataset.auaciHiddenByReplace;
          }
        } catch (_) {}
      }
      delete box.__auaciHiddenNodes;
    }
  } catch (_) {}
}

// --- Helpers to robustly extract text for attempt_completion ---
function tryParseJsonSoft(text) {
  if (text == null) return null;
  let t = String(text).trim();
  if (!t) return null;
  // strip code fences
  t = t.replace(/^```[a-zA-Z0-9]*\n([\s\S]*?)\n```$/m, '$1').trim();
  // unescape common HTML entities
  t = t.replace(/&quot;/g, '"').replace(/&#34;/g, '"').replace(/&amp;/g, '&');
  try {
    return JSON.parse(t);
  } catch (_) {
    // Try to extract first {...} block
    const s = String(t);
    const start = s.indexOf('{');
    const end = s.lastIndexOf('}');
    if (start !== -1 && end !== -1 && end > start) {
      const sub = s.slice(start, end + 1);
      try {
        return JSON.parse(sub);
      } catch (_) { return null; }
    }
    return null;
  }
}

function extractAttemptTextFromObj(obj) {
  if (!obj || typeof obj !== 'object') return '';
  const candidates = [];
  // Flat fields
  if (typeof obj.result === 'string') candidates.push(obj.result);
  if (typeof obj.text === 'string') candidates.push(obj.text);
  if (typeof obj.message === 'string') candidates.push(obj.message);
  if (typeof obj.content === 'string') candidates.push(obj.content);
  if (typeof obj.display_content === 'string') candidates.push(obj.display_content);
  // Nested common locations
  if (obj.input && typeof obj.input === 'object') {
    if (typeof obj.input.result === 'string') candidates.push(obj.input.result);
    if (typeof obj.input.text === 'string') candidates.push(obj.input.text);
    if (typeof obj.input.message === 'string') candidates.push(obj.input.message);
    if (typeof obj.input.content === 'string') candidates.push(obj.input.content);
  }
  if (obj.tool_system_results && typeof obj.tool_system_results === 'object') {
    const r = extractAttemptTextFromObj(obj.tool_system_results);
    if (r) candidates.push(r);
  }
  if (obj.tool_system_response && typeof obj.tool_system_response === 'object') {
    const r = extractAttemptTextFromObj(obj.tool_system_response);
    if (r) candidates.push(r);
  }
  for (const c of candidates) {
    if (typeof c === 'string' && c.trim()) return String(c);
  }
  return '';
}

function getAttemptText(runObj, rawJsonMaybe) {
  // Prefer run-provided strings
  try {
    if (runObj && typeof runObj.result === 'string' && runObj.result.trim()) return String(runObj.result);
  } catch (_) {}
  try {
    if (runObj && runObj.input && typeof runObj.input.result === 'string' && runObj.input.result.trim()) return String(runObj.input.result);
  } catch (_) {}
  try {
    if (runObj && runObj.result && typeof runObj.result.result === 'string' && runObj.result.result.trim()) return String(runObj.result.result);
  } catch (_) {}
  try {
    if (runObj && runObj.result && typeof runObj.result.text === 'string' && runObj.result.text.trim()) return String(runObj.result.text);
  } catch (_) {}
  // Fallback: parse raw tool JSON
  const obj = tryParseJsonSoft(rawJsonMaybe);
  if (obj) {
    const s = extractAttemptTextFromObj(obj);
    if (s && s.trim()) return s;
  }
  return '';
}

/**
 * enhanceToolPlaceholders(container, tools, toolRuns)
 * - container: DOM element that contains the placeholder .auaci-tool-box nodes
 * - tools: parsed tool entries from extractToolUsesAndSanitize (may include result if saved inline)
 * - toolRuns: structured run records (saved or generated), preferred for display if present
 *
 * This version renders each placeholder independently in its original position.
 * It no longer groups multiple tools of the same type together, preserving the
 * exact ordering as produced by the model.
 */
function enhanceToolPlaceholders(container, tools, toolRuns) {
  if (!container) return;
  const boxes = Array.from(container.querySelectorAll('.auaci-tool-box'));
  if (!boxes || boxes.length === 0) return;

  // Queue for attempt_completion texts to be appended at the end (below all tools)
  const attemptAppendQueue = [];

  // Ensure CSS for hiding scrollbars is present once
  try {
    if (!document.getElementById('auaci-tool-styles')) {
      const style = document.createElement('style');
      style.id = 'auaci-tool-styles';
      style.textContent = `
        .no-scrollbar::-webkit-scrollbar { display: none; }
        .no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
        .tool-code { max-width: 100%; overflow-x: auto; white-space: pre; padding:8px; background:#0b1220; color:#e6edf3; border-radius:6px; }
        .tool-code.light { background:#f6f6f6; color:#111; }
        .summary-box pre { margin:0; }
        
        /* Spinner animation for executing tools */
        @keyframes tool-spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
        .tool-icon.spinner {
          animation: tool-spin 1s linear infinite;
        }
        .tool-executing {
          opacity: 0.9;
        }
        .tool-executing .tool-body {
          background: linear-gradient(90deg, #f8f9fa 0%, #f1f5f9 50%, #f8f9fa 100%);
          background-size: 200% 100%;
          animation: tool-shimmer 1.5s ease-in-out infinite;
        }
        @keyframes tool-shimmer {
          0% { background-position: 200% 0; }
          100% { background-position: -200% 0; }
        }

        /* Patch diff (apply_patch) */
        .patch-diff { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace; font-size: 12px; line-height: 1.4; }
        .patch-file { border: 1px solid #e5e7eb; border-radius: 10px; overflow: hidden; margin: 8px 0; }
        .patch-file-header { padding: 8px 10px; background: #f9fafb; border-bottom: 1px solid #e5e7eb; font-weight: 600; color: #111; white-space: normal; word-break: break-word; overflow-wrap: anywhere; }
        .patch-hunk { background: #0b1220; color: #e6edf3; padding: 8px 10px; overflow-x: auto; }
        .diff-line { white-space: pre; }
        .diff-line.meta { color: #93c5fd; }
        .diff-line.add { background: rgba(16,185,129,0.12); color: #d1fae5; }
        .diff-line.del { background: rgba(239,68,68,0.12); color: #fee2e2; }
        .diff-line.context { color: #e6edf3; }

        /* Unrecognized Tool standalone box (not a tool box) */
        .unrecognized-tool-box {
          background:#FEF2F2;
          border:1px solid #FCA5A5;
          color:#991B1B;
          border-radius:10px;
          padding:10px;
          margin:8px 0;
          font-weight:600;
        }

        /* Todo list styles */
        .todo-list { margin: 4px 0; }
        .todo-section-title { font-weight: 600; margin: 8px 0 6px; color: #111; }
        .todo-item { display:flex; align-items:flex-start; gap:8px; padding:6px 8px; border-radius:8px; }
        .todo-item.new { background: rgba(52, 211, 153, 0.12); outline: 1px solid rgba(16,185,129,0.35); }
        .todo-item .todo-text { color:#111; line-height:1.35; }
        .todo-item.completed .todo-text { color:#6b7280; text-decoration: line-through; }
        .todo-item .todo-details { color:#6b7280; font-size: 12px; margin-top: 2px; }
        .todo-icon { flex: 0 0 auto; margin-top: 1px; }

        /* Terminal styles */
        .tool-terminal { background:#000; color:#fff; }
        .tool-terminal .terminal-scroll { background:#000; max-height: calc(1.35em * 8); overflow: auto; }
        .tool-terminal .terminal-line, .tool-terminal .terminal-output { background:#000; color:#fff; }
        .tool-terminal .terminal-output { white-space: pre; }
        /* Force black text background across all terminal descendants */
        .tool-terminal, .tool-terminal * { background:#000 !important; color:#fff !important; }
        /* WebKit scrollbar customization */
        .tool-terminal .terminal-scroll::-webkit-scrollbar { width: 8px; height: 8px; background: transparent; }
        .tool-terminal .terminal-scroll::-webkit-scrollbar-track { background: transparent; }
        .tool-terminal .terminal-scroll::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0); border-radius: 8px; transition: background-color 160ms ease; }
        .tool-terminal .terminal-scroll::-webkit-scrollbar-corner { background: transparent; }
        .tool-terminal .terminal-scroll::-webkit-scrollbar-track-piece { background: transparent; }
        .tool-terminal .terminal-scroll.show-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0.35); }
        /* Firefox scrollbar customization */
        .tool-terminal .terminal-scroll { scrollbar-color: transparent transparent; scrollbar-width: thin; }
        .tool-terminal .terminal-scroll.show-scrollbar { scrollbar-color: rgba(255,255,255,0.35) transparent; }
        /* Horizontal code blocks auto-hide scrollbar */
        .tool-code::-webkit-scrollbar { height: 8px; background: transparent; }
        .tool-code::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0); border-radius: 8px; transition: background-color 160ms ease; }
        .tool-code.show-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0.25); }
        .tool-code { scrollbar-color: transparent transparent; scrollbar-width: thin; }
        .tool-code.show-scrollbar { scrollbar-color: rgba(255,255,255,0.25) transparent; }
        pre::-webkit-scrollbar { height:8px; }
        pre::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0); border-radius:8px; transition: background-color 160ms ease; }
        pre.show-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(255,255,255,0.25); }
      `;
      document.head.appendChild(style);
    }
  } catch (_) {}

  // Normalize an array of runs aligned with placeholders order:
  // Preferred source: toolRuns (if provided). Fallback: use parsed tools[].result.
  const runs = [];
  for (let i = 0; i < boxes.length; i++) {
    const t = (Array.isArray(tools) && tools[i]) ? tools[i] : null;
    const savedRun = (Array.isArray(toolRuns) && toolRuns[i]) ? toolRuns[i] : null;

    if (savedRun) {
      runs.push(Object.assign({}, {
        name: savedRun.name || (t && t.name) || '',
        input: savedRun.input || (t && t.input) || {},
        result: savedRun.result !== undefined ? savedRun.result : (t && t.result) || null,
        summary: savedRun.summary || {},
        success: typeof savedRun.success === 'boolean' ? savedRun.success : (savedRun.result && !savedRun.result.error ? true : !!(t && t.result && !t.result.error))
      }));
    } else if (t) {
      runs.push({
        name: t.name || '',
        input: t.input || {},
        result: t.result || null,
        summary: (t.result && t.result.summary) ? t.result.summary : {},
        success: t.result && typeof t.result.error !== 'undefined' ? !t.result.error : null
      });
    } else {
      runs.push({ name: '', input: {}, result: null, summary: {}, success: null });
    }
  }

  // Render each box independently to preserve position
  const recognizedTools = new Set([
    'ls','view','read_any_files','grep','find_files','glob','attempt_completion','bash','run_command','txtlize','write_to_file','apply_patch',
    // new tools:
    'context-search', 'context_search', 'ask', 'delete_file_folder_with_permission',
    // todo tools
    'create_todo_list','read_todos','add_todos','remove_todos','mark_todo_as_done','update_todo_status',
    // file tools
    'create_file', 'diagnostics',
    // new editing & fetch tools
    'str_replace', 'fs_append', 'read_multiple_files', 'web_fetch', 'web_search',
    // AST-based and precise editing tools
    'get_function', 'edit_function', 'edit_lines'
  ]);
  // Build a map of runs by uid for robust matching to DOM boxes
  const runsByUid = new Map();
  for (let ri = 0; ri < runs.length; ri++) {
    const run = runs[ri] || {};
    if (run.__uid) runsByUid.set(String(run.__uid), { run, index: ri });
  }

  for (let i = 0; i < boxes.length; i++) {
    const box = boxes[i];
    // Prefer uid-based matching from the DOM
    const uid = box.getAttribute('data-tool-uid');
    const matched = uid && runsByUid.has(uid) ? runsByUid.get(uid) : null;
    const r = matched ? matched.run : (runs[i] || {});
    // origName: the tool identifier used for renderer selection; displayName: shown in the header
    const origName = String(r.name || '') || String(box.getAttribute('data-tool') || '');
    let displayName = origName;
    // For read_any_files, show the main title as \"View\" (only for display)
    if ((origName || '').toLowerCase() === 'read_any_files') displayName = 'View';
    const name = displayName;
    const lname = (origName || '').toLowerCase();
    const isUnrecognized = !!origName && !recognizedTools.has(lname === 'view' ? 'view' : lname);
    const toolMeta = (Array.isArray(tools) && tools[i]) ? tools[i] : null;

    // If unrecognized: replace the tool box node with a standalone "Unrecognized Tool" box and skip further rendering
    if (isUnrecognized) {
      try {
        const replacement = document.createElement('div');
        replacement.className = 'unrecognized-tool-box';
        replacement.textContent = 'Unrecognized Tool';
        if (box && box.parentNode) {
          box.parentNode.replaceChild(replacement, box);
        }
      } catch (_) {}
      continue;
    }

    // Ensure .tool-body exists
    let body = box.querySelector('.tool-body');
    if (!body) {
      body = document.createElement('div');
      body.className = 'tool-body';
      box.appendChild(body);
    }

    // Check if tool is still executing (no result yet)
    const isExecutingState = isToolExecuting(r.result);
    
    // If executing, render the executing state and update header
    if (isExecutingState) {
      // Add executing class to box for styling
      box.classList.add('tool-executing');
      
      // Get tool index for interactive tools
      const toolIndex = box.getAttribute('data-tool-index') || String(i);
      
      // Render executing state body
      body.innerHTML = renderToolExecuting(origName, r.input || {}, toolIndex);
      
      // Update header title to show executing state
      const headerNameEl = box.querySelector('.tool-header .auaci-tool-name');
      if (headerNameEl) {
        headerNameEl.textContent = getToolHeaderTitle(origName, true, null);
      }
      
      // Wire up confirm button for ask tool in executing state
      if (lname === 'ask') {
        const confirmBtn = box.querySelector('.ask-confirm-btn');
        if (confirmBtn && !confirmBtn.dataset.wired) {
          confirmBtn.dataset.wired = 'true';
          confirmBtn.addEventListener('click', (e) => {
            e.preventDefault();
            const idx = confirmBtn.getAttribute('data-tool-index');
            const q = (r.input && r.input.question) || '';
            const mode = (r.input && r.input.mode) ? String(r.input.mode).toLowerCase() : 'free';
            const opts = Array.isArray(r.input && r.input.options) ? r.input.options : [];
            let userInput = '';
            let selectedValues = [];
            if (mode === 'free') {
              const ta = box.querySelector('textarea.ask-input');
              userInput = ta ? String(ta.value || '').trim() : '';
            } else if (mode === 'multi') {
              const checks = box.querySelectorAll('input.ask-opt[type="checkbox"]');
              selectedValues = Array.from(checks).filter(el => el.checked).map(el => String(el.getAttribute('data-value') || ''));
            } else {
              const radios = box.querySelectorAll('input.ask-opt[type="radio"]');
              for (const el of Array.from(radios)) {
                if (el.checked) { selectedValues = [String(el.getAttribute('data-value') || '')]; break; }
              }
            }
            // Dispatch event
            window.dispatchEvent(new CustomEvent('auaci:ask-answered', {
              detail: { tool_index: String(idx || ''), question: q, mode, options: opts, user_input: userInput, selected_values: selectedValues }
            }));
            // Disable button immediately
            confirmBtn.disabled = true;
            confirmBtn.textContent = 'Submitted';
            confirmBtn.style.background = '#9ca3af';
          });
        }
      }
      
      continue; // Skip the rest of the rendering for this tool
    }
    
    // Remove executing class if present (tool completed)
    box.classList.remove('tool-executing');

    const lines = [];
    
    // Check if this is an enhanced navigation tool
    const enhancedTools = ['ls', 'find_files', 'glob', 'semantic_search'];
    if (enhancedTools.includes(lname)) {
      try {
        const enhancedResult = renderEnhancedToolResult(lname, r.result || {});
        
        // Use enhanced rendering
        if (enhancedResult.summary) {
          lines.push(`<div class="tool-line" style="font-weight:600;">${escapeHtmlLite(enhancedResult.summary)}</div>`);
        }
        
        if (enhancedResult.details && enhancedResult.details.length > 0) {
          enhancedResult.details.forEach(detail => {
            if (detail.trim()) {
              lines.push(`<div class="tool-line">${escapeHtmlLite(detail)}</div>`);
            }
          });
        }
        
        // Add execution time if available
        if (enhancedResult.metadata && enhancedResult.metadata.executionTime) {
          lines.push(`<div class="tool-line" style="color:#6b7280;font-size:11px;">Completed in ${enhancedResult.metadata.executionTime}ms</div>`);
        }
      } catch (enhancedError) {
        console.warn(`[Enhanced Renderer] Failed to render ${lname}:`, enhancedError);
        // Fallback to original rendering
        lines.push(`<div class="tool-line">${escapeHtmlLite(lname)} completed</div>`);
      }
    } else if (name === 'ls') {
    } else if (name === 'ls' && !enhancedTools.includes(lname)) {
        // Original ls rendering (fallback)
        const filePath = (r.summary && (r.summary.base_path || r.summary.path)) || (r.input && r.input.path) || '';
        const filesCount = (typeof r.result?.entriesCount === 'number')
          ? r.result.entriesCount
          : (typeof r.summary?.entriesCount === 'number')
            ? r.summary.entriesCount
            : (typeof r.summary?.total_items === 'number')
              ? r.summary.total_items
              : (Array.isArray(r.result?.entries) ? r.result.entries.length : 0);
        const depth = (typeof r.summary?.max_depth === 'number') ? r.summary.max_depth : (typeof r.input?.max_depth === 'number' ? r.input.max_depth : null);
        const icon = okIconSvg();
        const colParts = [];
        colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(filePath || ''))}</div>`);
        colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Found ${escapeHtmlLite(String(Number(filesCount)||0))} files</div>`);
        if (depth != null && !isNaN(depth)) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Search depth ${escapeHtmlLite(String(depth))}</div>`);
        }
        lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;\">${colParts.join('')}</div></div>`);
      } else if (lname === 'view' || lname === 'read_any_files') {
        // For 'view' tool (single file) and 'read_any_files' (multiple files) render appropriately.
        if (lname === 'read_any_files') {
          // Render one line per file with success/fail based on error presence (no file contents shown)
          const files = Array.isArray(r.result?.files) ? r.result.files : [];
          if (files.length === 0) {
            lines.push(`<div class=\"tool-line\">${pendingIconSvg()}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;\"><div class=\"tool-sub\">No files</div></div></div>`);
          } else {
            for (const f of files) {
              const p = f && f.path ? f.path : '';
              const displayed = (typeof f?.displayed_lines === 'number') ? f.displayed_lines : undefined;
              const hasError = !!(f && f.error);
              const icon = hasError ? failIconSvg() : okIconSvg();
              const subs = [];
              if (hasError && typeof f.error === 'string') subs.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(f.error))}</div>`);
              if (!hasError && typeof displayed === 'number') subs.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Lines Read: ${escapeHtmlLite(String(displayed))}</div>`);
              lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;\"><div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(p||''))}</div>${subs.join('')}</div></div>`);
            }
          }
        } else {
          // lname === 'view' : check for multi-file mode first
          // Multi-file mode can be detected by:
          // 1. result.mode === 'multiple' with result.files array
          // 2. input.paths array (even if result doesn't have mode flag)
          const hasMultiFileResult = r.result && r.result.mode === 'multiple' && Array.isArray(r.result.files);
          const hasMultiFileInput = r.input && Array.isArray(r.input.paths) && r.input.paths.length > 0;
          const isMultiFileMode = hasMultiFileResult || hasMultiFileInput;
          
          if (isMultiFileMode) {
            // Multi-file view mode - render one line per file
            const files = hasMultiFileResult ? r.result.files : [];
            
            // If we have input.paths but no result.files yet, show the paths as pending
            if (files.length === 0 && hasMultiFileInput) {
              for (const pathItem of r.input.paths) {
                const p = typeof pathItem === 'string' ? pathItem : (pathItem && pathItem.path ? pathItem.path : '');
                if (p) {
                  lines.push(`<div class=\"tool-line\">${pendingIconSvg()}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;\"><div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(p))}</div><div class=\"tool-sub\" style=\"color:#6b7280;\">Reading...</div></div></div>`);
                }
              }
            } else if (files.length === 0) {
              lines.push(`<div class=\"tool-line\">${pendingIconSvg()}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;\"><div class=\"tool-sub\">No files</div></div></div>`);
            } else {
              for (const f of files) {
                const p = f && f.path ? f.path : '';
                const displayed = (typeof f?.displayed_lines === 'number') ? f.displayed_lines : undefined;
                const hasError = !!(f && (f.error || f.success === false));
                const icon = hasError ? failIconSvg() : okIconSvg();
                const subs = [];
                if (hasError && typeof f.error === 'string') subs.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(f.error))}</div>`);
                if (!hasError && typeof displayed === 'number') subs.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Lines Read: ${escapeHtmlLite(String(displayed))}</div>`);
                lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;\"><div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(p||''))}</div>${subs.join('')}</div></div>`);
              }
            }
          } else {
            // Single-file view
            const filePath = (r.input && r.input.path) || (r.summary && r.summary.path) || (r.result && r.result.path) || '';
            let success = null;
            if (typeof r.success === 'boolean') success = r.success;
            else if (r.result) success = typeof r.result.error === 'undefined' || !r.result.error;
            const icon = (success === true) ? okIconSvg() : (success === false ? failIconSvg() : pendingIconSvg());
            const rangeInfo = (r.result && typeof r.result.range_info === 'string') ? r.result.range_info : (r.summary && r.summary.range_info ? r.summary.range_info : '');
            const usedRanges = Array.isArray(r.input?.ranges) && r.input.ranges.length > 0;
            let displayRange = '';
            if (rangeInfo && usedRanges) {
              try {
                const m = String(rangeInfo).match(/^\((.*)\)$/);
                displayRange = m ? m[1] : String(rangeInfo);
              } catch (_) {
                displayRange = String(rangeInfo);
              }
            }
            const column = [
              `<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(filePath || ''))}</div>`,
              ...(displayRange ? [`<div class=\"tool-sub\" style=\"color:#6b7280;\">Lines Read: ${escapeHtmlLite(displayRange)}</div>`] : [])
            ].join('');
            lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;\">${column}</div></div>`);
          }
        }
      } else if (lname === 'grep') {
        // Show directory and a status per pattern only (no files or line details)
        const basePath = (r.summary && r.summary.path) || (r.input && r.input.path) || '.';
        const patterns = (r.summary && Array.isArray(r.summary.patterns)) ? r.summary.patterns : (Array.isArray(r.input?.queries) ? r.input.queries : []);
        const foundMap = (r.summary && r.summary.found_by_pattern) ? r.summary.found_by_pattern : {};
        const patternStatsArray = Array.isArray(r.result?.pattern_stats) ? r.result.pattern_stats : (Array.isArray(r.summary?.pattern_stats) ? r.summary.pattern_stats : []);
        lines.push(`<div class=\"tool-line\" style=\"font-weight:600;\">Searched in \"${escapeHtmlLite(String(basePath))}\"</div>`);
        if (patterns && patterns.length > 0) {
          for (const pat of patterns) {
            let status = null;
            try {
              if (foundMap && typeof foundMap === 'object' && Object.prototype.hasOwnProperty.call(foundMap, pat)) {
                status = coerceBooleanOrNull(foundMap[pat]);
              } else if (patternStatsArray && patternStatsArray.length > 0) {
                const stat = patternStatsArray.find(s => s && String(s.pattern) === String(pat));
                if (stat && typeof stat.matched !== 'undefined') status = coerceBooleanOrNull(stat.matched);
              }
            } catch (_) {
              status = null;
            }
            const icon = (status === true) ? okIconSvg() : (status === false ? failIconSvg() : pendingIconSvg());
            lines.push(`<div class=\"tool-line\">${icon}<span class=\"tool-file\">${escapeHtmlLite(String(pat))}</span></div>`);
          }
        }
      } else if (lname === 'context-search' || lname === 'context_search') {
        // Context search: ONLY render status and the search strings (patterns). Do not render paths, lines, or file contents.
        const patterns = (r.summary && Array.isArray(r.summary.patterns)) ? r.summary.patterns : (Array.isArray(r.input?.queries) ? r.input.queries : []);
        const patternStatsArray = Array.isArray(r.result?.pattern_stats) ? r.result.pattern_stats : (Array.isArray(r.summary?.pattern_stats) ? r.summary.pattern_stats : []);
        if (patterns && patterns.length) {
          for (const pat of patterns) {
            let status = null;
            if (patternStatsArray && patternStatsArray.length) {
              const stat = patternStatsArray.find(s => s && String(s.pattern) === String(pat));
              if (stat && typeof stat.matched !== 'undefined') status = coerceBooleanOrNull(stat.matched);
            }
            const icon = (status === true) ? okIconSvg() : (status === false ? failIconSvg() : pendingIconSvg());
            lines.push(`<div class=\"tool-line\">${icon}<span class=\"tool-file\">${escapeHtmlLite(String(pat))}</span></div>`);
          }
        }
      } else if (name === 'find_files') {
      } else if (name === 'find_files' && !enhancedTools.includes(lname)) {
        // Original find_files rendering (fallback)
        const patterns = (r.result && Array.isArray(r.result.patterns)) ? r.result.patterns : (Array.isArray(r.input?.patterns) ? r.input.patterns : []);
        const files = Array.isArray(r.result?.files) ? r.result.files : [];
        const hasWildcards = (p) => /[\*\?\[]/.test(String(p||''));
        const basename = (p) => {
          try { return String(p).split(/\\|\//).pop(); } catch (_) { return String(p||''); }
        };
        const matchedByPattern = new Map();
        for (const pat of patterns) matchedByPattern.set(pat, []);
        if (patterns.length === 0) {
          // If no patterns provided, just list found files (treat all as found)
          lines.push(`<div class=\"tool-line\" style=\"font-weight:600;\">Looked for Files:</div>`);
          for (const f of files) {
            const label = f && (f.fileName || f.fullPath) ? (f.fileName || f.fullPath) : '';
            lines.push(`<div class=\"tool-line\">${okIconSvg()}<span class=\"tool-file\">${escapeHtmlLite(String(label))}</span></div>`);
          }
        } else {
          // Build mapping from pattern -> found file names
          for (const f of files) {
            const fn = String(f && f.fileName ? f.fileName : '');
            for (const pat of patterns) {
              if (hasWildcards(pat)) {
                // Approximate: if pattern contains fn as a substring when wildcards removed, mark as match
                // but better approach: use basename comparison for simple wildcard cases
                const b = basename(pat);
                if (b === fn || (b && fn.includes(b.replace(/[\*\?\[\]]/g, '')))) {
                  matchedByPattern.get(pat)?.push(fn);
                }
              } else {
                if (basename(pat) === fn) matchedByPattern.get(pat)?.push(fn);
              }
            }
          }
          lines.push(`<div class=\"tool-line\" style=\"font-weight:600;\">Looked for Files:</div>`);
          for (const pat of patterns) {
            const matches = matchedByPattern.get(pat) || [];
            if (hasWildcards(pat)) {
              if (matches.length > 0) {
                // Show all matched files under this pattern as found
                for (const m of matches) {
                  lines.push(`<div class=\"tool-line\">${okIconSvg()}<span class=\"tool-file\">${escapeHtmlLite(String(m))}</span></div>`);
                }
              } else {
                // Show the pattern as not found
                lines.push(`<div class=\"tool-line\">${failIconSvg()}<span class=\"tool-file\">${escapeHtmlLite(String(pat))}</span></div>`);
              }
            } else {
              // exact file name
              const label = basename(pat);
              const found = matches.length > 0;
              const icon = found ? okIconSvg() : failIconSvg();
              lines.push(`<div class=\"tool-line\">${icon}<span class=\"tool-file\">${escapeHtmlLite(String(label))}</span></div>`);
            }
          }
        }
      } else if (name === 'glob') {
      } else if (name === 'glob' && !enhancedTools.includes(lname)) {
        // Original glob rendering (fallback)
        const basePath = (r.input && r.input.path) || '.';
        const pattern = (r.input && r.input.pattern) ? r.input.pattern : '*';
        const matches = Array.isArray(r.result?.matches) ? r.result.matches : [];
        const found = matches.length > 0;
        const icon = found ? okIconSvg() : failIconSvg();
        lines.push(`<div class=\"tool-line\" style=\"font-weight:600;\">Searched in \"${escapeHtmlLite(String(basePath))}\"</div>`);
        lines.push(`<div class=\"tool-line\">${icon}<span class=\"tool-file\">${escapeHtmlLite(String(pattern))}</span></div>`);
      } else if (lname === 'delete_file_folder_with_permission') {
        // Interactive delete tool: initial preview is interactive; after execution it becomes read-only.
        const res = r.result || {};
        const items = Array.isArray(res.items) ? res.items : [];
        const mode = (res.mode || r.input?.mode || 'preview').toString().toLowerCase();
        const waiting = !!(res.waiting === true || mode === 'preview');
        const baseDir = (res.summary && res.summary.base_dir) || r.input?.base_dir || '';
        const reason = (res.summary && res.summary.reason) || r.input?.reason || '';

        const headerLine = waiting
          ? 'Select files and folders to delete'
          : 'Delete results';
        lines.push(`<div class=\"tool-line\" style=\"font-weight:600;\">${escapeHtmlLite(headerLine)}</div>`);
        if (reason) {
          lines.push(`<div class=\"tool-line\"><span class=\"tool-sub\">Reason:</span> <span class=\"tool-file\">${escapeHtmlLite(String(reason))}</span></div>`);
        }
        if (baseDir) {
          lines.push(`<div class=\"tool-line\"><span class=\"tool-sub\">Base dir:</span> <span class=\"tool-file\">${escapeHtmlLite(String(baseDir))}</span></div>`);
        }

        if (!items.length) {
          lines.push('<div class=\"tool-line\"><span class=\"tool-sub\">No files or folders were provided.</span></div>');
        } else {
          for (let idx = 0; idx < items.length; idx++) {
            const it = items[idx] || {};
            const pathLabel = it.requested_path || it.full_path || '';
            const kind = it.kind === 'directory' ? 'Folder' : (it.kind === 'file' ? 'File' : 'Item');
            const missing = it.status === 'missing';
            const disabled = !waiting || missing;
            const selected = (typeof it.selected === 'boolean') ? !!it.selected : true;
            let statusLabel = '';
            if (waiting) {
              if (missing) statusLabel = 'Missing (cannot delete)';
              else if (!selected) statusLabel = 'Will be kept';
              else statusLabel = 'Selected for deletion';
            } else {
              if (it.status === 'deleted') statusLabel = 'Deleted';
              else if (it.status === 'skipped' || !selected) statusLabel = 'Kept';
              else if (missing) statusLabel = 'Missing';
              else if (it.status === 'error') statusLabel = it.error ? `Error: ${it.error}` : 'Error';
              else statusLabel = 'Pending';
            }
            const checkedAttr = selected && !missing ? 'checked' : '';
            const disabledAttr = disabled ? 'disabled' : '';
            const rowClasses = ['tool-line','delete-item-row'];
            if (missing) rowClasses.push('missing');
            lines.push(
              `<div class=\"${rowClasses.join(' ')}\" data-path=\"${escapeHtmlLite(String(pathLabel))}\" data-full-path=\"${escapeHtmlLite(String(it.full_path || ''))}\" data-kind=\"${escapeHtmlLite(String(it.kind || ''))}\" data-missing=\"${missing ? '1' : '0'}\">` +
              `<input type=\"checkbox\" class=\"delete-item-toggle\" ${checkedAttr} ${disabledAttr} style=\"margin-right:8px;\">` +
              `<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;\">` +
                `<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(pathLabel))}</div>` +
                `<div class=\"tool-sub\" style=\"color:#6b7280;\">${escapeHtmlLite(kind)} • ${escapeHtmlLite(statusLabel)}</div>` +
              `</div>` +
              `</div>`
            );
          }
        }
      } else if (isTodoTool(lname)) {
        // Render Todos with high-quality SVG icons and full-list re-render behavior
        const res = r.result || {};
        const tl = res.todo_list || {};
        const pending = Array.isArray(tl.pending_todos) ? tl.pending_todos : [];
        const completed = Array.isArray(tl.completed_todos) ? tl.completed_todos : [];
        const addedIds = Array.isArray(res.added_ids) ? new Set(res.added_ids) : new Set();

        function renderItem(item, isCompleted) {
          const isNew = addedIds.has(item.id);
          const cls = ['todo-item'];
          if (isCompleted) cls.push('completed');
          if (isNew) cls.push('new');
          const icon = isCompleted ? checkboxCheckedSvg() : checkboxUncheckedSvg();
          const title = escapeHtmlLite(String(item.title || ''));
          const details = escapeHtmlLite(String(item.details || ''));
          return `
            <div class="${cls.join(' ')}">
              ${icon}
              <div class="todo-main">
                <div class="todo-text">${title}</div>
                ${details ? `<div class="todo-details">${details}</div>` : ''}
              </div>
            </div>
          `;
        }

        // Header title inside body (explicit), actual header is set below
        lines.push(`<div class=\"tool-line\" style=\"font-weight:600;\">Todos</div>`);
        lines.push('<div class="todo-list">');
        for (const t of pending) lines.push(renderItem(t, false));
        for (const t of completed) lines.push(renderItem(t, true));
      } else if (name === 'attempt_completion') {
        // Render attempt_completion text as normal markdown content
        const attemptText = getAttemptText(r, toolMeta && toolMeta.raw);
        if (attemptText && attemptText.trim()) {
          try { marked.setOptions({ gfm: true, breaks: true, renderer: createRenderer() }); } catch (_) {}
          const html = marked.parse(attemptText);
          // Render the markdown content inside the tool body
          lines.push(`<div class=\"attempt-content\" style=\"padding:4px 0;\">${html}</div>`);
        } else {
          // No content yet - show pending state
          lines.push(`<div class=\"tool-line\">${pendingIconSvg()}<span style=\"margin-left:6px;color:#6b7280;\">Generating summary...</span></div>`);
        }
      } else if (name === 'bash' || name === 'run_command') {
        // Virtual terminal rendering: show cwd prompt + command, then output transcript
        // No truncation here - gptCycle.js handles 15KB byte-based truncation
        const finished = r.result && r.result.finished ? r.result.finished : {};
        const cmd = (finished && finished.command) || (r.input && r.input.command) || '';
        const cwd = (finished && finished.new_pwd) || '';
        let rawOutput = (finished && typeof finished.output === 'string') ? finished.output : (typeof r.result?.output_preview === 'string' ? r.result.output_preview : '');
        const baseDir = (() => { try { const parts = String(cwd||'').split('/').filter(Boolean); return parts.length ? parts[parts.length-1] : cwd || '.'; } catch (_) { return cwd || '.'; } })();
        const prompt = `${escapeHtmlLite(baseDir)} % ${escapeHtmlLite(String(cmd))}`;
        // Display full output - no line truncation
        let displayOutput = rawOutput || '';
        let totalLines = typeof finished.total_lines === 'number' ? finished.total_lines : undefined;
        let displayedLines = typeof finished.displayed_lines === 'number' ? finished.displayed_lines : undefined;
        let truncated = typeof finished.truncated === 'boolean' ? finished.truncated : undefined;
        if (typeof displayOutput === 'string') {
          const linesArr = displayOutput.split(/\r?\n/);
          totalLines = Number.isFinite(totalLines) ? totalLines : linesArr.length;
          displayedLines = Number.isFinite(displayedLines) ? displayedLines : linesArr.length; // Show ALL lines
          truncated = (typeof truncated === 'boolean') ? truncated : false;
          displayOutput = linesArr.join('\n'); // Don't slice - show everything
        }
        // Only show truncation note if output contains the truncation marker from gptCycle.js
        const truncNote = (rawOutput && rawOutput.includes('[Output Truncated]')) 
          ? `<div class=\"terminal-line\" style=\"white-space:pre; background:#000; color:#fbbf24; margin-top:6px;\">⚠️ Output truncated to 15KB - showing most recent results at the top</div>` 
          : '';
        const floodNote = (typeof r.result?.terminated_by_system_flood === 'boolean' && r.result.terminated_by_system_flood && typeof r.result?.flood_notice === 'string')
          ? `<div class=\"terminal-line\" style=\"white-space:pre; background:#000; color:#fbbf24; margin-top:6px;\">${escapeHtmlLite(String(r.result.flood_notice))}</div>` : '';
        const globalStopBtn = document.getElementById('stop-response-btn');
        const sessionRunning = !!(globalStopBtn && !globalStopBtn.disabled);
        const stopVisible = (typeof finished.exit_code === 'undefined') && sessionRunning;
        if (stopVisible) {
          lines.push(`<div class=\"tool-line\" style=\"display:flex;justify-content:flex-end;\"><button class=\"cmd-stop-btn\" data-run-index=\"${String(i)}\" style=\"padding:6px 10px;border:1px solid #e5e7eb;border-radius:6px;background:#fff;cursor:pointer;\">Stop</button></div>`);
        }
        lines.push(`
<div class=\"tool-terminal\" style=\"background:#000; color:#fff; margin:0; padding:8px; border-radius:8px; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace; line-height:1.35;\">\n  <div class=\"terminal-scroll\" style=\"background:#000; max-height: calc(1.35em * 8); overflow: auto;\">\n    <div class=\"terminal-line\" style=\"white-space:pre; background:#000; color:#fff;\">${prompt}</div>\n    ${displayOutput ? `<pre class=\"terminal-output\" style=\"white-space:pre; margin:6px 0 0; background:#000; color:#fff;\">${escapeHtmlLite(displayOutput)}</pre>` : ''}\n    ${truncNote}${floodNote}\n  </div>\n</div>`);
      } else if (lname === 'read_any_files') {
        // Render one line per file with success/fail based on error presence (no file contents shown)
        // Title shown as "View"
        const files = Array.isArray(r.result?.files) ? r.result.files : [];
        if (files.length === 0) {
          lines.push(`<div class=\"tool-line\">${pendingIconSvg()}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;\"><div class=\"tool-sub\">No files</div></div></div>`);
        } else {
          for (const f of files) {
            const p = f && f.path ? f.path : '';
            const displayed = (typeof f?.displayed_lines === 'number') ? f.displayed_lines : undefined;
            const hasError = !!(f && f.error);
            const icon = hasError ? failIconSvg() : okIconSvg();
            const subs = [];
            if (hasError && typeof f.error === 'string') subs.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(f.error))}</div>`);
            if (!hasError && typeof displayed === 'number') subs.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Lines Read: ${escapeHtmlLite(String(displayed))}</div>`);
            lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;\"><div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(p||''))}</div>${subs.join('')}</div></div>`);
          }
        }
      } else if (lname === 'apply_patch' || (r.result && r.result.__render_as === 'apply_patch')) {
        // Render compact patch summary (NO code shown): file + added/removed counts.
        const res = r.result || {};
        const applied = Array.isArray(res.applied) ? res.applied : [];
        const errorCount = Number(res.error_count || 0);
        const hasErrorFlag = !!(res && res.error);

        if (applied.length > 0) {
          for (const ap of applied) {
            const f = ap && ap.file_path ? String(ap.file_path) : '';
            const rep = ap && ap.replaced ? ap.replaced : {};

            let added = 0;
            let removed = 0;

            // Primary source: replaced.{inserted_line_count, deleted_line_count}
            if (rep.inserted_line_count !== undefined) added = Number(rep.inserted_line_count);
            if (rep.deleted_line_count !== undefined) removed = Number(rep.deleted_line_count);

            // Secondary source: flattened fields
            if (added === 0 && ap.inserted_line_count !== undefined) added = Number(ap.inserted_line_count);
            if (removed === 0 && ap.deleted_line_count !== undefined) removed = Number(ap.deleted_line_count);

            // Fallback: parse message
            if (added === 0 && removed === 0 && ap.message) {
              const msg = String(ap.message);
              const deletedMatch = msg.match(/deleted (\d+) lines?/);
              const insertedMatch = msg.match(/inserted (\d+) lines?/);
              const replacedMatch = msg.match(/replaced (\d+) lines? with (\d+)/);
              if (deletedMatch) removed = Number(deletedMatch[1]);
              if (insertedMatch) added = Number(insertedMatch[1]);
              if (replacedMatch) {
                removed = Number(replacedMatch[1]);
                added = Number(replacedMatch[2]);
              }
            }

            const autoAligned = (typeof ap.auto_aligned_to_line === 'number' && ap.auto_aligned_to_line > 0) ? ap.auto_aligned_to_line : null;
            lines.push(`<div class=\"tool-line\"><span class=\"tool-file\">File: ${escapeHtmlLite(f)}</span>${autoAligned ? `<span class=\"tool-sub\" style=\"margin-left:8px;color:#6b7280;\">auto-aligned to line ${autoAligned}</span>` : ''}</div>`);
            lines.push(`<div class=\"tool-line\" style=\"color:#1a7f37;\">+ ${added}</div>`);
            lines.push(`<div class=\"tool-line\" style=\"color:#b00020;\">- ${removed}</div>`);
          }
        } else if (errorCount > 0 || hasErrorFlag || r.success === false) {
          lines.push(`<div class=\"tool-line\" style=\"font-weight:600; color:#b00020;\">Patch failed</div>`);
          try {
            if (Array.isArray(res.errors) && res.errors.length > 0) {
              const e0 = res.errors[0] || {};
              const ef = e0.file_path ? ` (${escapeHtmlLite(String(e0.file_path))})` : '';
              if (e0.error) lines.push(`<div class=\"tool-line\">${escapeHtmlLite(String(e0.error))}${ef}</div>`);
            } else if (typeof res.error === 'string') {
              lines.push(`<div class=\"tool-line\">${escapeHtmlLite(res.error)}</div>`);
            }
          } catch (_) {}
        } else {
          lines.push(`<div class=\"tool-line\">${pendingIconSvg()}<span style=\"margin-left:6px;\">Patch pending</span></div>`);
        }
      } else if (name === 'write_to_file') {
        // Render file path with success/fail icon - styled with column layout (same as create_file)
        const filePath = (r.input && r.input.path) || (r.summary && r.summary.path) || '';
        const hasError = !!(r.result && r.result.error);
        const success = r.result && !hasError;
        const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
        const colParts = [];
        colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(filePath || ''))}</div>`);
        if (success) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#1a7f37;\">File written</div>`);
        } else if (hasError) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(r.result.error || 'Failed'))}</div>`);
        } else {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Writing...</div>`);
        }
        lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
      } else if (lname === 'ask') {
        // Interactive Ask tool
        const q = (r.input && r.input.question) || '';
        const mode = (r.input && r.input.mode) ? String(r.input.mode).toLowerCase() : 'free';
        const opts = Array.isArray(r.input && r.input.options) ? r.input.options : [];
        const toolIndex = String(box.getAttribute('data-tool-index') || '');
const hasFlat = !!(r.result && (typeof r.result.answer_text === 'string' || (Array.isArray(r.result.selected) && r.result.selected.length > 0)));
const hasNested = !!(r.result && r.result.answer && (typeof r.result.answer.text === 'string' || (Array.isArray(r.result.answer.selected) && r.result.answer.selected.length > 0)));
const isAnswered = !!(r.result && (r.result.waiting === false || r.result.answered === true)) || hasFlat || hasNested;

        // Ensure header contains Ask title and (if waiting) the Answer button on the same line
        try {
          const headerEl = box.querySelector('.tool-header');
          if (headerEl) {
            headerEl.style.display = 'flex';
            headerEl.style.alignItems = 'center';
            headerEl.style.justifyContent = 'space-between';
            // Remove stale button if any
            const oldBtn = headerEl.querySelector('.ask-answer-btn');
            if (oldBtn) oldBtn.remove();
            if (!isAnswered) {
              const btn = document.createElement('button');
              btn.className = 'ask-answer-btn';
              btn.textContent = 'Answer';
              btn.style.padding = '6px 10px';
              btn.style.border = '1px solid #e5e7eb';
              btn.style.borderRadius = '6px';
              btn.style.background = '#fff';
              btn.style.cursor = 'pointer';
              btn.style.zIndex = '100';
              btn.style.position = 'relative';
              btn.style.pointerEvents = 'auto';
              headerEl.appendChild(btn);
            }
          }
        } catch (_) {}

        // Body content
        lines.push(`<div class=\"tool-line\" style=\"font-weight:600;\">${escapeHtmlLite(String(q))}</div>`);
        if (!isAnswered) {
          // Render editable input controls
          if (mode === 'free') {
            lines.push(`<div class=\"tool-line\"><textarea class=\"ask-input\" data-tool-index=\"${toolIndex}\" placeholder=\"Type your response...\" style=\"width:100%;min-height:72px;padding:8px;border:1px solid #e5e7eb;border-radius:6px;\"></textarea></div>`);
          } else if (mode === 'multi') {
            if (opts.length === 0) {
              lines.push(`<div class=\"tool-line\"><em style=\"color:#6b7280;\">No options provided</em></div>`);
            } else {
              for (let oi = 0; oi < opts.length; oi++) {
                const val = String(opts[oi]);
                const id = `ask-${toolIndex}-opt-${oi}`;
                lines.push(`<div class=\"tool-line\" style=\"display:flex;align-items:center;gap:8px;\"><input type=\"checkbox\" class=\"ask-opt\" id=\"${id}\" data-value=\"${escapeHtmlLite(val)}\"><label for=\"${id}\" class=\"tool-file\">${escapeHtmlLite(val)}</label></div>`);
              }
            }
          } else { // single
            if (opts.length === 0) {
              lines.push(`<div class=\"tool-line\"><em style=\"color:#6b7280;\">No options provided</em></div>`);
            } else {
              for (let oi = 0; oi < opts.length; oi++) {
                const val = String(opts[oi]);
                const id = `ask-${toolIndex}-opt-${oi}`;
                lines.push(`<div class=\"tool-line\" style=\"display:flex;align-items:center;gap:8px;\"><input type=\"radio\" name=\"ask-${toolIndex}-single\" class=\"ask-opt\" id=\"${id}\" data-value=\"${escapeHtmlLite(val)}\"><label for=\"${id}\" class=\"tool-file\">${escapeHtmlLite(val)}</label></div>`);
              }
            }
          }
        } else {
          // Read-only render: inputs disabled reflecting the saved answer
const ansText = (typeof r.result?.answer_text === 'string' && r.result.answer_text.trim()) ? r.result.answer_text : (r.result && r.result.answer && typeof r.result.answer.text === 'string' ? r.result.answer.text : '');
const selected = Array.isArray(r.result?.selected) ? r.result.selected : (r.result && r.result.answer && Array.isArray(r.result.answer.selected) ? r.result.answer.selected : []);
          if (mode === 'free') {
            const safeText = escapeHtmlLite(String(ansText || ''));
            lines.push(`<div class=\"tool-line\"><textarea disabled style=\"width:100%;min-height:72px;padding:8px;border:1px solid #e5e7eb;border-radius:6px;background:#f9fafb;\">${safeText}</textarea></div>`);
          } else if (mode === 'multi') {
            if (opts.length === 0) {
              lines.push(`<div class=\"tool-line\"><em style=\"color:#6b7280;\">No options</em></div>`);
            } else {
              for (let oi = 0; oi < opts.length; oi++) {
                const val = String(opts[oi]);
                const id = `ask-${toolIndex}-opt-${oi}`;
                const checked = selected.includes(val) ? 'checked' : '';
                lines.push(`<div class=\"tool-line\" style=\"display:flex;align-items:center;gap:8px;\"><input type=\"checkbox\" disabled ${checked} id=\"${id}\"><label for=\"${id}\" class=\"tool-file\">${escapeHtmlLite(val)}</label></div>`);
              }
            }
          } else { // single
            if (opts.length === 0) {
              lines.push(`<div class=\"tool-line\"><em style=\"color:#6b7280;\">No options</em></div>`);
            } else {
              const selectedOne = selected && selected.length ? String(selected[0]) : '';
              for (let oi = 0; oi < opts.length; oi++) {
                const val = String(opts[oi]);
                const id = `ask-${toolIndex}-opt-${oi}`;
                const checked = (val === selectedOne) ? 'checked' : '';
                lines.push(`<div class=\"tool-line\" style=\"display:flex;align-items:center;gap:8px;\"><input type=\"radio\" disabled name=\"ask-${toolIndex}-single\" ${checked} id=\"${id}\"><label for=\"${id}\" class=\"tool-file\">${escapeHtmlLite(val)}</label></div>`);
              }
            }
          }
          // Remove any header Answer button if present
          try { const headerEl = box.querySelector('.tool-header'); const btn = headerEl && headerEl.querySelector('.ask-answer-btn'); if (btn) btn.remove(); } catch (_) {}
        }
      } else if (name === 'txtlize') {
        // Render summary with name, file count, and size; include an Open button to reveal in Finder
        const displayName = (r.summary && r.summary.name) || (r.input && r.input.name) || '';
        const fileCount = (r.summary && r.summary.file_count) || (r.result && r.result.file_count) || 0;
        const sizeBytes = (r.summary && r.summary.total_size_bytes) || (r.result && r.result.total_size_bytes) || 0;
        const outPath = (r.summary && r.summary.output_path) || (r.result && r.result.output_path) || '';
        const humanSize = formatBytes(sizeBytes);
        lines.push(`
<div class=\"tool-line\" style=\"font-weight:600;\">Txtlized - \"${escapeHtmlLite(String(displayName))}\"</div>
<div class=\"tool-line\">${Number(fileCount)||0} files \u00A0 \u00A0 ${escapeHtmlLite(humanSize)}</div>
<div class=\"tool-line\"><button class=\"txtlize-open\" data-output-path=\"${escapeHtmlLite(String(outPath))}\" style=\"padding:6px 10px; border:1px solid #e5e7eb; border-radius:6px; background:#fff; cursor:pointer;\">open</button></div>`);
      } else if (lname === 'create_file') {
        // Render file path with success/fail icon - styled with column layout
        const filePath = (r.input && r.input.path) || (r.summary && r.summary.path) || '';
        const hasError = !!(r.result && r.result.error);
        const success = r.result && !hasError;
        const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
        const colParts = [];
        colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(filePath || ''))}</div>`);
        if (success) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#1a7f37;\">File created</div>`);
        } else if (hasError) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(r.result.error || 'Failed'))}</div>`);
        } else {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Creating...</div>`);
        }
        lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
      } else if (lname === 'diagnostics') {
        // Render diagnostics results - styled with per-file rows
        const paths = Array.isArray(r.input && r.input.paths) ? r.input.paths : [];
        const results = Array.isArray(r.result && r.result.results) ? r.result.results : [];
        const summary = (r.result && r.result.summary) || {};
        const totalIssues = summary.total_issues || 0;
        const filesWithIssues = summary.files_with_issues || 0;
        
        if (results.length === 0 && paths.length > 0) {
          // Still running - show each file as pending
          for (const p of paths) {
            lines.push(`<div class=\"tool-line\">${pendingIconSvg()}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\"><div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(p))}</div><div class=\"tool-sub\" style=\"color:#6b7280;\">Checking...</div></div></div>`);
          }
        } else if (results.length > 0) {
          // Show results per file with column layout
          for (const res of results) {
            const hasIssues = res.issues && res.issues.length > 0;
            const icon = !res.exists ? failIconSvg() : (hasIssues ? failIconSvg() : okIconSvg());
            const colParts = [];
            colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(res.path || ''))}</div>`);
            
            if (!res.exists) {
              colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">File not found</div>`);
            } else if (hasIssues) {
              // Show issue count and first issue
              colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${res.issues.length} issue(s)</div>`);
              const firstIssue = res.issues[0];
              const loc = firstIssue.line > 0 ? `Line ${firstIssue.line}: ` : '';
              colParts.push(`<div class=\"tool-sub\" style=\"color:${firstIssue.severity === 'error' ? '#b00020' : '#b45309'};font-size:11px;max-width:400px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;\">${escapeHtmlLite(loc + firstIssue.message)}</div>`);
              if (res.issues.length > 1) {
                colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;font-size:11px;\">+${res.issues.length - 1} more</div>`);
              }
            } else {
              colParts.push(`<div class=\"tool-sub\" style=\"color:#1a7f37;\">No issues</div>`);
            }
            
            lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
          }
          // Summary line with border
          const summaryColor = totalIssues === 0 ? '#1a7f37' : '#b00020';
          const summaryText = totalIssues === 0 ? 'All files passed' : `${totalIssues} issue(s) in ${filesWithIssues} file(s)`;
          lines.push(`<div class=\"tool-line\" style=\"margin-top:4px;padding-top:4px;border-top:1px solid #e5e7eb;\"><span style=\"color:${summaryColor};font-weight:500;\">${summaryText}</span></div>`);
        } else {
          lines.push(`<div class=\"tool-line\">${pendingIconSvg()}<span style=\"margin-left:6px;\">Diagnostics</span></div>`);
        }
      } else if (lname === 'str_replace') {
        // String replace tool - styled like view with column layout
        const filePath = (r.input && r.input.path) || '';
        const hasError = !!(r.result && r.result.error);
        const success = r.result && r.result.success;
        const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
        const colParts = [];
        colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(filePath || ''))}</div>`);
        if (success && r.result.line) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Line ${r.result.line}</div>`);
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">${r.result.old_lines} line(s) → ${r.result.new_lines} line(s)</div>`);
        } else if (hasError) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(r.result.error || ''))}</div>`);
        } else if (!success && !hasError) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Replacing...</div>`);
        }
        lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
      } else if (lname === 'fs_append') {
        // File append tool - styled like view with column layout
        const filePath = (r.input && r.input.path) || '';
        const hasError = !!(r.result && r.result.error);
        const success = r.result && r.result.success;
        const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
        const colParts = [];
        colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(filePath || ''))}</div>`);
        if (success) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#1a7f37;\">+${r.result.lines_added || 0} line(s) appended</div>`);
          if (r.result.total_lines) {
            colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Total: ${r.result.total_lines} lines</div>`);
          }
        } else if (hasError) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(r.result.error || ''))}</div>`);
        } else {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Appending...</div>`);
        }
        lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
      } else if (lname === 'read_multiple_files') {
        // Multi-file read tool - styled like read_any_files with per-file rows
        const files = (r.result && Array.isArray(r.result.files)) ? r.result.files : [];
        const paths = (r.input && Array.isArray(r.input.paths)) ? r.input.paths : [];
        const summary = r.result && r.result.summary;
        
        if (files.length === 0 && paths.length > 0) {
          // Still loading
          for (const p of paths) {
            lines.push(`<div class=\"tool-line\">${pendingIconSvg()}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\"><div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(p))}</div><div class=\"tool-sub\" style=\"color:#6b7280;\">Reading...</div></div></div>`);
          }
        } else {
          // Show results per file
          for (const f of files) {
            const icon = f.success ? okIconSvg() : failIconSvg();
            const colParts = [];
            colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(f.path || ''))}</div>`);
            if (f.success) {
              colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Lines: ${f.range || '1-' + f.total_lines} (${f.displayed_lines} displayed)</div>`);
            } else {
              colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(f.error || 'Error'))}</div>`);
            }
            lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
          }
          // Summary line
          if (summary) {
            const summaryColor = summary.failed > 0 ? '#b45309' : '#1a7f37';
            lines.push(`<div class=\"tool-line\" style=\"margin-top:4px;padding-top:4px;border-top:1px solid #e5e7eb;\"><span style=\"color:${summaryColor};font-weight:500;\">${summary.success}/${summary.total} files read successfully</span></div>`);
          }
        }
      } else if (lname === 'web_fetch') {
        // Web fetch tool - styled with URL and content preview
        const url = (r.input && r.input.url) || '';
        const mode = (r.input && r.input.mode) || 'truncated';
        const hasError = !!(r.result && r.result.error);
        const success = r.result && r.result.success;
        const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
        
        // URL display with icon
        const colParts = [];
        // Truncate URL for display if too long
        const displayUrl = url.length > 60 ? url.substring(0, 57) + '...' : url;
        colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-all;overflow-wrap:anywhere;\" title=\"${escapeHtmlLite(url)}\">${escapeHtmlLite(displayUrl)}</div>`);
        
        if (success) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">${r.result.content_length || 0} characters (${mode})</div>`);
        } else if (hasError) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(r.result.error || 'Fetch failed'))}</div>`);
        } else {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Fetching...</div>`);
        }
        
        lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
        
        // Show content preview for successful fetches
        if (success && r.result.content) {
          const preview = String(r.result.content).substring(0, 200).trim();
          if (preview) {
            lines.push(`<div class=\"tool-content-preview\" style=\"margin-top:6px;padding:8px;background:#f8f9fa;border-radius:6px;border:1px solid #e5e7eb;font-size:11px;color:#4b5563;line-height:1.4;max-height:80px;overflow:hidden;white-space:pre-wrap;word-break:break-word;\">${escapeHtmlLite(preview)}${r.result.content.length > 200 ? '...' : ''}</div>`);
          }
        }
      } else if (lname === 'web_search') {
        // Web search tool - styled with query and search results
        const query = (r.input && r.input.query) || '';
        const hasError = !!(r.result && r.result.error);
        const success = r.result && r.result.success;
        const resultCount = (r.result && r.result.result_count) || 0;
        const results = (r.result && Array.isArray(r.result.results)) ? r.result.results : [];
        const instantAnswer = r.result && r.result.instant_answer;
        const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
        
        // Search icon SVG
        const searchIconSvg = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="11" cy="11" r="7" stroke="#3b82f6" stroke-width="2"/><path d="M16 16l4 4" stroke="#3b82f6" stroke-width="2" stroke-linecap="round"/></svg>';
        
        // Query display with search icon
        const colParts = [];
        colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;font-weight:500;\">"${escapeHtmlLite(query)}"</div>`);
        
        if (success) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#1a7f37;\">Found ${resultCount} result(s)</div>`);
        } else if (hasError) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(r.result.error || 'Search failed'))}</div>`);
        } else {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Searching...</div>`);
        }
        
        lines.push(`<div class=\"tool-line\">${searchIconSvg}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
        
        // Show instant answer if available
        if (success && instantAnswer) {
          let answerHtml = '<div class="web-search-instant" style="margin-top:8px;padding:10px;background:linear-gradient(135deg,#eff6ff,#f0fdf4);border-radius:8px;border:1px solid #bfdbfe;">';
          answerHtml += '<div style="font-size:11px;font-weight:600;color:#1e40af;margin-bottom:4px;">📌 Quick Answer</div>';
          if (instantAnswer.answer) {
            answerHtml += `<div style="font-size:12px;color:#1f2937;line-height:1.4;">${escapeHtmlLite(instantAnswer.answer)}</div>`;
          }
          if (instantAnswer.abstract) {
            answerHtml += `<div style="font-size:12px;color:#374151;line-height:1.4;margin-top:4px;">${escapeHtmlLite(instantAnswer.abstract.substring(0, 300))}${instantAnswer.abstract.length > 300 ? '...' : ''}</div>`;
            if (instantAnswer.abstractSource) {
              answerHtml += `<div style="font-size:10px;color:#6b7280;margin-top:4px;">Source: ${escapeHtmlLite(instantAnswer.abstractSource)}</div>`;
            }
          }
          if (instantAnswer.definition) {
            answerHtml += `<div style="font-size:12px;color:#374151;line-height:1.4;margin-top:4px;"><em>${escapeHtmlLite(instantAnswer.definition.substring(0, 200))}${instantAnswer.definition.length > 200 ? '...' : ''}</em></div>`;
          }
          answerHtml += '</div>';
          lines.push(answerHtml);
        }
        
        // Show search results (max 5 for compact display)
        if (success && results.length > 0) {
          let resultsHtml = '<div class="web-search-results" style="margin-top:8px;">';
          const displayResults = results.slice(0, 5);
          for (let ri = 0; ri < displayResults.length; ri++) {
            const res = displayResults[ri];
            const title = res.title || 'Untitled';
            const url = res.url || '';
            const snippet = res.snippet || '';
            // Truncate URL for display
            const displayUrl = url.length > 50 ? url.substring(0, 47) + '...' : url;
            
            resultsHtml += `<div class="web-search-result" style="padding:8px 10px;background:#ffffff;border:1px solid #e5e7eb;border-radius:6px;margin-bottom:6px;">`;
            resultsHtml += `<div style="font-size:12px;font-weight:500;color:#1d4ed8;line-height:1.3;margin-bottom:2px;">${escapeHtmlLite(title)}</div>`;
            resultsHtml += `<div style="font-size:10px;color:#059669;margin-bottom:4px;" title="${escapeHtmlLite(url)}">${escapeHtmlLite(displayUrl)}</div>`;
            if (snippet) {
              resultsHtml += `<div style="font-size:11px;color:#4b5563;line-height:1.35;">${escapeHtmlLite(snippet.substring(0, 150))}${snippet.length > 150 ? '...' : ''}</div>`;
            }
            resultsHtml += '</div>';
          }
          if (results.length > 5) {
            resultsHtml += `<div style="font-size:11px;color:#6b7280;text-align:center;padding:4px;">+ ${results.length - 5} more result(s)</div>`;
          }
          resultsHtml += '</div>';
          lines.push(resultsHtml);
        }
      } else if (lname === 'get_function') {
        // AST-based symbol extraction tool - styled with file path and symbol info
        const filePath = (r.input && r.input.path) || '';
        const symbolName = (r.input && r.input.name) || (r.result && (r.result.symbol_name || r.result.function_name)) || '';
        const listOnly = r.input && r.input.list_only;
        const hasError = !!(r.result && r.result.error);
        const success = r.result && !hasError;
        const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
        
        const colParts = [];
        colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(filePath || ''))}</div>`);
        
        if (success) {
          if (listOnly && r.result.symbols) {
            const symbolCount = Array.isArray(r.result.symbols) ? r.result.symbols.length : 0;
            colParts.push(`<div class=\"tool-sub\" style=\"color:#1a7f37;\">Found ${symbolCount} symbol(s)</div>`);
          } else if (symbolName && (r.result.code || r.result.line_count)) {
            const lineCount = Number.isFinite(r.result.line_count) ? r.result.line_count : String(r.result.code || '').split('\\n').length;
            colParts.push(`<div class=\"tool-sub\" style=\"color:#1a7f37;\">Extracted "${escapeHtmlLite(symbolName)}" (${lineCount} lines)</div>`);
            if (r.result.start_line && r.result.end_line) {
              colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Lines ${r.result.start_line}-${r.result.end_line}</div>`);
            }
          } else if (r.result.symbols) {
            const symbolCount = Array.isArray(r.result.symbols) ? r.result.symbols.length : 0;
            colParts.push(`<div class=\"tool-sub\" style=\"color:#1a7f37;\">Found ${symbolCount} symbol(s)</div>`);
          }
        } else if (hasError) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(r.result.error || 'Failed'))}</div>`);
        } else {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Extracting...</div>`);
        }
        
        lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
      } else if (lname === 'edit_function') {
        // AST-based function editing tool - styled with file path and edit info
        const filePath = (r.input && r.input.path) || '';
        const symbolName = (r.input && r.input.name) || '';
        const action = (r.input && r.input.action) || 'replace';
        const hasError = !!(r.result && r.result.error);
        const success = r.result && r.result.success;
        const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
        
        const colParts = [];
        colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(filePath || ''))}</div>`);
        
        let actionLabel;
        switch (action) {
          case 'delete': actionLabel = 'Deleted'; break;
          case 'insert_before': actionLabel = 'Inserted before'; break;
          case 'insert_after': actionLabel = 'Inserted after'; break;
          default: actionLabel = 'Edited';
        }
        
        if (success) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#1a7f37;\">${actionLabel} "${escapeHtmlLite(symbolName)}"</div>`);
          if (r.result.lines_changed) {
            colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">${r.result.lines_changed} line(s) changed</div>`);
          }
          if (r.result.start_line && r.result.end_line) {
            colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Lines ${r.result.start_line}-${r.result.end_line}</div>`);
          }
        } else if (hasError) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(r.result.error || 'Failed'))}</div>`);
        } else {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Editing...</div>`);
        }
        
        lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
      } else if (lname === 'edit_lines') {
        // Line-based editing tool - styled with file path and line range info
        const filePath = (r.input && r.input.path) || '';
        const startLine = r.input && r.input.start_line;
        const endLine = r.input && r.input.end_line;
        const action = (r.input && r.input.action) || 'replace';
        const hasError = !!(r.result && r.result.error);
        const success = r.result && r.result.success;
        const icon = hasError ? failIconSvg() : (success ? okIconSvg() : pendingIconSvg());
        
        const colParts = [];
        colParts.push(`<div class=\"tool-file\" style=\"white-space:normal;word-break:break-word;overflow-wrap:anywhere;\">${escapeHtmlLite(String(filePath || ''))}</div>`);
        
        const lineRange = startLine === endLine ? `line ${startLine}` : `lines ${startLine}-${endLine}`;
        let actionLabel;
        switch (action) {
          case 'delete': actionLabel = 'Deleted'; break;
          case 'insert_before': actionLabel = 'Inserted before'; break;
          case 'insert_after': actionLabel = 'Inserted after'; break;
          default: actionLabel = 'Edited';
        }
        
        if (success) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#1a7f37;\">${actionLabel} ${lineRange}</div>`);
          if (r.result.old_lines !== undefined && r.result.new_lines !== undefined) {
            colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">${r.result.old_lines} line(s) → ${r.result.new_lines} line(s)</div>`);
          }
        } else if (hasError) {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#b00020;\">${escapeHtmlLite(String(r.result.error || 'Failed'))}</div>`);
        } else {
          colParts.push(`<div class=\"tool-sub\" style=\"color:#6b7280;\">Editing...</div>`);
        }
        
        lines.push(`<div class=\"tool-line\">${icon}<div class=\"tool-col\" style=\"display:flex;flex-direction:column;gap:2px;margin-left:6px;\">${colParts.join('')}</div></div>`);
      } else {
        // Generic: just show the tool name
        lines.push(`<div class=\"tool-line\"><span class=\"tool-cmd\">${escapeHtmlLite(name || 'tool')}</span></div>`);
      }

    // Check for enhanced Warp-style display_content
    const hasDisplayContent = r.result && typeof r.result.display_content === 'string';
    const isTodo = isTodoTool(lname);
    // Skip display_content for tools with custom rendering
    const skipDisplayContentTools = new Set(['grep','glob','find_files','context-search','context_search','ls','delete_file_folder_with_permission','str_replace','fs_append','web_fetch','web_search','diagnostics','get_function','edit_function','edit_lines']);
    if (hasDisplayContent && !isTodo && !skipDisplayContentTools.has(lname)) {
      // Replace the lines with enhanced display content (skip for custom-rendered tools)
      const displayContent = String(r.result.display_content);
      lines.length = 0; // Clear existing lines
      lines.push(`<div class=\"warp-enhanced-display\" style=\"white-space: pre-wrap; font-family: monospace; line-height: 1.4; padding: 8px; background: #f8f9fa; border-radius: 6px; border: 1px solid #e1e4e8; margin: 4px 0;\">${escapeHtmlLite(displayContent)}</div>`);
      
      // For read_multiple_files, check if any files have truncation messages
      if (lname === 'read_multiple_files' && r.result && Array.isArray(r.result.files)) {
        for (const file of r.result.files) {
          if (file.truncation_message) {
            lines.push(`<div class=\"tool-truncation-message\" style=\"padding: 8px; margin: 8px 0; background: #fff3cd; border: 1px solid #ffc107; border-radius: 6px; color: #856404; font-size: 13px; line-height: 1.5;\"><strong>${escapeHtmlLite(file.path)}:</strong><br>${escapeHtmlLite(file.truncation_message)}</div>`);
          }
        }
      }
    }
    
    // Add truncation message if present (for view tool)
    if (r.result && typeof r.result.truncation_message === 'string') {
      lines.push(`<div class=\"tool-truncation-message\" style=\"padding: 8px; margin: 8px 0; background: #fff3cd; border: 1px solid #ffc107; border-radius: 6px; color: #856404; font-size: 13px; line-height: 1.5;\">${escapeHtmlLite(r.result.truncation_message)}</div>`);
    }

    body.innerHTML = lines.join('');

    // Wire terminal scrollbar show/hide behavior
    try {
      if (name === 'bash' || name === 'run_command') {
        const scrollEl = box.querySelector('.terminal-scroll');
        if (scrollEl) {
          let hideTimer = null;
          const show = () => {
            try { scrollEl.classList.add('show-scrollbar'); } catch (_) {}
            if (hideTimer) { try { clearTimeout(hideTimer); } catch (_) {} }
            hideTimer = setTimeout(() => { try { scrollEl.classList.remove('show-scrollbar'); } catch (_) {} }, 2000);
          };
          ['wheel','scroll','touchstart','mouseenter','mousemove','keydown'].forEach(evt => {
            try { scrollEl.addEventListener(evt, show, { passive: true }); } catch (_) {}
          });
          // Hide immediately on mouse leave
          try {
            scrollEl.addEventListener('mouseleave', () => {
              if (hideTimer) { try { clearTimeout(hideTimer); } catch (_) {} hideTimer = null; }
              try { scrollEl.classList.remove('show-scrollbar'); } catch (_) {}
            }, { passive: true });
          } catch (_) {}
          // Initial state: hidden until interaction
          try { scrollEl.classList.remove('show-scrollbar'); } catch (_) {}
        }
      }
    } catch (_) {}

    // Wire up Delete-with-permission confirm button if present
    try {
      if (lname === 'delete_file_folder_with_permission') {
        const headerEl = box.querySelector('.tool-header');
        const res = r.result || {};
        const waiting = !!(res && (res.waiting === true || (res.mode || '').toString().toLowerCase() === 'preview'));
        if (headerEl) {
          headerEl.style.display = 'flex';
          headerEl.style.alignItems = 'center';
          headerEl.style.justifyContent = 'space-between';
          const oldBtn = headerEl.querySelector('.delete-confirm-btn');
          if (oldBtn) oldBtn.remove();
          if (waiting) {
            const btn = document.createElement('button');
            btn.className = 'delete-confirm-btn';
            btn.textContent = 'Delete selected';
            btn.style.padding = '6px 10px';
            btn.style.border = '1px solid #e5e7eb';
            btn.style.borderRadius = '6px';
            btn.style.background = '#fff';
            btn.style.cursor = 'pointer';
            headerEl.appendChild(btn);
            // Attach click handler to collect selected items and notify engine
            btn.addEventListener('click', (e) => {
              e.preventDefault();
              const toolIndex = box.getAttribute('data-tool-index');
              const rows = box.querySelectorAll('.delete-item-row');
              const items = [];
              for (const row of Array.from(rows)) {
                const p = row.getAttribute('data-path') || '';
                const full = row.getAttribute('data-full-path') || '';
                const kind = row.getAttribute('data-kind') || '';
                const missing = row.getAttribute('data-missing') === '1';
                const checkbox = row.querySelector('input.delete-item-toggle');
                const selected = checkbox ? !!checkbox.checked : false;
                items.push({ path: p, full_path: full || undefined, kind, selected, missing });
              }
              try {
                window.dispatchEvent(new CustomEvent('auaci:delete-with-permission-confirm', { detail: { tool_index: String(toolIndex || ''), items } }));
              } catch (_) {}
              try { btn.disabled = true; btn.textContent = 'Deleting…'; } catch (_) {}
            });
          }
        }
      }
    } catch (_) {}

    // Wire up Ask Answer button if present
    try {
      if (lname === 'ask') {
        const btn = box.querySelector('.ask-answer-btn');
        if (btn) {
          btn.addEventListener('click', (e) => {
            e.preventDefault();
            const toolIndex = box.getAttribute('data-tool-index');
            const q = (r.input && r.input.question) || '';
            const mode = (r.input && r.input.mode) ? String(r.input.mode).toLowerCase() : 'free';
            const opts = Array.isArray(r.input && r.input.options) ? r.input.options : [];
            let userInput = '';
            let selectedValues = [];
            if (mode === 'free') {
              const ta = box.querySelector('textarea.ask-input');
              userInput = ta ? String(ta.value || '').trim() : '';
            } else if (mode === 'multi') {
              const checks = box.querySelectorAll('input.ask-opt[type=\"checkbox\"]');
              selectedValues = Array.from(checks).filter(el => el.checked).map(el => String(el.getAttribute('data-value') || ''));
            } else {
              const radios = box.querySelectorAll(`input.ask-opt[type=\"radio\"][name=\"ask-${toolIndex}-single\"]`);
              for (const el of Array.from(radios)) {
                if (el.checked) { selectedValues = [ String(el.getAttribute('data-value') || '') ]; break; }
              }
            }
            try {
              // Dispatch to engine so it persists and re-renders via doUpdate
              window.dispatchEvent(new CustomEvent('auaci:ask-answered', { detail: { tool_index: String(toolIndex || ''), question: q, mode, options: opts, user_input: userInput, selected_values: selectedValues } }));
            } catch (_) {}
            // Immediately switch to dormant (read-only) locally for instant feedback
            try {
              // Remove the Answer button from header now
              const headerEl = box.querySelector('.tool-header');
              const headerBtn = headerEl && headerEl.querySelector('.ask-answer-btn');
              if (headerBtn) headerBtn.remove();
              // Disable inputs reflecting the current selection/value
              if (mode === 'free') {
                const ta = box.querySelector('textarea.ask-input');
                if (ta) {
                  ta.value = userInput;
                  ta.setAttribute('disabled', 'true');
                  ta.style.background = '#f9fafb';
                }
              } else if (mode === 'multi') {
                const checks = box.querySelectorAll('input.ask-opt[type=\"checkbox\"]');
                for (const el of Array.from(checks)) {
                  const val = String(el.getAttribute('data-value') || '');
                  el.checked = selectedValues.includes(val);
                  el.setAttribute('disabled', 'true');
                }
              } else {
                const radios = box.querySelectorAll(`input.ask-opt[type=\"radio\"][name=\"ask-${toolIndex}-single\"]`);
                for (const el of Array.from(radios)) {
                  const val = String(el.getAttribute('data-value') || '');
                  el.checked = (selectedValues.length ? selectedValues[0] : '') === val;
                  el.setAttribute('disabled', 'true');
                }
              }
            } catch (_) {}
          });
        }
      }
    } catch (_) {}

    // Wire up Stop button for running commands (live render only)
    try {
      if (lname === 'bash' || lname === 'run_command') {
        const btn = box.querySelector('.cmd-stop-btn');
        if (btn) {
          btn.addEventListener('click', (e) => {
            e.preventDefault();
            try {
              // Find entry index from the closest message parent
              let parentMsg = box.closest('.message.gpt-message');
              const entryIdx = parentMsg ? parentMsg.getAttribute('data-entry-index') : null;
              const runIdx = btn.getAttribute('data-run-index');
              if (entryIdx != null && runIdx != null) {
                window.dispatchEvent(new CustomEvent('auaci:stop-command', { detail: { entry_index: String(entryIdx), run_index: Number(runIdx) } }));
                btn.disabled = true; btn.textContent = 'Stopping…';
              }
            } catch (_) {}
          });
        }
      }
    } catch (_) {}

    // Wire up txtlize open button if present
    try {
      if (name === 'txtlize') {
        const btn = box.querySelector('.txtlize-open');
        if (btn) {
          btn.addEventListener('click', async (e) => {
            e.preventDefault();
            const p = btn.getAttribute('data-output-path');
            if (p) {
              try { await revealInFinder(p); } catch (_) {}
            }
          });
        }
      }
    } catch (_) {}

    // fix header title casing & contextual titles
    const headerNameEl = box.querySelector('.tool-header .auaci-tool-name');
    if (headerNameEl) {
      if (name === 'ls') headerNameEl.textContent = 'ls';
      else if (name === 'attempt_completion') headerNameEl.textContent = 'Summary';
      else if (lname === 'delete_file_folder_with_permission') headerNameEl.textContent = 'Delete files/folders';
      else if (name === 'write_to_file') {
        const run = runs[i] || {};
        const isIncomplete = (run.result == null);
        headerNameEl.textContent = isIncomplete ? 'Writing to file' : 'Wrote to file';
      } else if (lname === 'apply_patch' || (r.result && r.result.__render_as === 'apply_patch')) {
        headerNameEl.textContent = 'Apply patch';
      } else if (lname === 'context-search' || lname === 'context_search') {
        headerNameEl.textContent = 'Context search';
      } else if (isTodoTool(lname)) {
        headerNameEl.textContent = 'Todos';
      } else if (lname === 'find_files') {
        headerNameEl.textContent = 'Find files';
      } else if (lname === 'run_command' || lname === 'bash') {
        headerNameEl.textContent = 'Terminal';
      } else if (lname === 'create_file') {
        const run = runs[i] || {};
        const isIncomplete = (run.result == null);
        headerNameEl.textContent = isIncomplete ? 'Creating file' : 'Created file';
      } else if (lname === 'diagnostics') {
        headerNameEl.textContent = 'Diagnostics';
      } else if (lname === 'str_replace') {
        const run = runs[i] || {};
        const isIncomplete = (run.result == null);
        const hasError = run.result && run.result.error;
        headerNameEl.textContent = isIncomplete ? 'Replacing...' : (hasError ? 'Replace failed' : 'Replaced');
      } else if (lname === 'fs_append') {
        const run = runs[i] || {};
        const isIncomplete = (run.result == null);
        headerNameEl.textContent = isIncomplete ? 'Appending...' : 'Appended';
      } else if (lname === 'read_multiple_files') {
        headerNameEl.textContent = 'Read files';
      } else if (lname === 'web_fetch') {
        const run = runs[i] || {};
        const isIncomplete = (run.result == null);
        headerNameEl.textContent = isIncomplete ? 'Fetching...' : 'Fetched';
      } else {
        headerNameEl.textContent = (name.charAt(0).toUpperCase() + name.slice(1));
      }
    }

    // For bash: style as split white (header) + black (body) unified box
    if (name === 'bash') {
      try {
        // Make the outer box transparent so only header/body borders show
        box.style.background = 'transparent';
        box.style.border = 'none';
        box.style.padding = '0';
        box.style.margin = '8px 0';

        // Header: white with top-rounded corners and joined border
        const headerEl = box.querySelector('.tool-header');
        if (headerEl) {
          headerEl.style.background = '#fff';
          headerEl.style.color = '#111';
          headerEl.style.padding = '8px 10px';
          headerEl.style.margin = '0';
          headerEl.style.border = '1px solid #e5e7eb';
          headerEl.style.borderBottom = 'none';
          headerEl.style.borderTopLeftRadius = '10px';
          headerEl.style.borderTopRightRadius = '10px';
          headerEl.style.borderBottomLeftRadius = '0';
          headerEl.style.borderBottomRightRadius = '0';
          headerEl.style.boxSizing = 'border-box';
        }

        // Body: pure black with bottom-rounded corners and joined border (squared top corners)
        body.style.background = '#000';
        body.style.color = '#fff';
        body.style.margin = '0';
        body.style.marginTop = '-1px'; // overlap 1px to eliminate any hairline gap
        body.style.padding = '0';
        body.style.border = '1px solid #e5e7eb';
        body.style.borderTop = 'none';
        body.style.borderBottomLeftRadius = '10px';
        body.style.borderBottomRightRadius = '10px';
        body.style.borderTopLeftRadius = '0';
        body.style.borderTopRightRadius = '0';
        body.style.boxSizing = 'border-box';
        // Hide scrollbars visually while allowing scroll on any child with .no-scrollbar
        body.classList.add('no-scrollbar');
      } catch (_) {}
    }

  }

  // After rendering all tools, append any attempt_completion texts at the end of the message content
  try {
    if (attemptAppendQueue.length > 0) {
      const frag = document.createDocumentFragment();
      for (const item of attemptAppendQueue) {
        const wrapper = document.createElement('div');
        // Intentionally no special classes/styles so content is normal text
        wrapper.setAttribute('data-attempt-index', item.id);
        wrapper.innerHTML = item.html;
        frag.appendChild(wrapper);
      }
      container.appendChild(frag);
    }
  } catch (_) {}
}

// Format bytes as KB/MB/GB succinctly
function formatBytes(bytes) {
  const b = Number(bytes||0);
  if (b < 1024) return `${b} B`;
  const kb = b/1024;
  if (kb < 1024) return `${kb.toFixed(kb<10?2:1)} KB`;
  const mb = kb/1024;
  if (mb < 1024) return `${mb.toFixed(mb<10?2:1)} MB`;
  const gb = mb/1024;
  return `${gb.toFixed(gb<10?2:1)} GB`;
}

module.exports = { enhanceToolPlaceholders, okIconSvg, failIconSvg, pendingIconSvg };
