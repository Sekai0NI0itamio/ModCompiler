// src/main/functions/chat/helpers/toolRenderSanitizer.js
// Sanitizes tool data for render history storage
// Strips large content (file contents, command outputs) while preserving metadata needed for rendering

/**
 * Sanitize a tool result for render history storage
 * Removes large content while keeping essential metadata for display
 * @param {string} toolName - The tool name
 * @param {object} input - The tool input parameters
 * @param {object} result - The full tool result
 * @returns {object} - Sanitized result suitable for render history
 */
function sanitizeToolResultForRender(toolName, input, result) {
  if (!result || typeof result !== 'object') {
    return result;
  }

  const name = normalizeToolName(toolName);
  const { envelope, data } = unwrapToolResult(result);
  
  try {
    let sanitized;
    switch (name) {
      case 'view':
        sanitized = sanitizeViewResult(input, data);
        break;
      case 'read_any_files':
      case 'read_multiple_files':
        sanitized = sanitizeMultiFileResult(input, data);
        break;
      case 'ls':
        sanitized = sanitizeLsResult(input, data);
        break;
      case 'grep':
      case 'context_search':
      case 'context-search':
        sanitized = sanitizeGrepResult(input, data);
        break;
      case 'run_command':
      case 'bash':
        sanitized = sanitizeBashResult(input, data);
        break;
      case 'apply_patch':
        sanitized = sanitizePatchResult(input, data);
        break;
      case 'write_to_file':
      case 'create_file':
        sanitized = sanitizeWriteResult(input, data);
        break;
      case 'str_replace':
        sanitized = sanitizeStrReplaceResult(input, data);
        break;
      case 'fs_append':
        sanitized = sanitizeFsAppendResult(input, data);
        break;
      case 'find_files':
        sanitized = sanitizeFindFilesResult(input, data);
        break;
      case 'glob':
        sanitized = sanitizeGlobResult(input, data);
        break;
      case 'web_fetch':
        sanitized = sanitizeWebFetchResult(input, data);
        break;
      case 'web_search':
        sanitized = sanitizeWebSearchResult(input, data);
        break;
      case 'diagnostics':
        sanitized = sanitizeDiagnosticsResult(input, data);
        break;
      case 'ask':
        sanitized = sanitizeAskResult(input, data);
        break;
      case 'delete_file_folder_with_permission':
        sanitized = sanitizeDeleteResult(input, data);
        break;
      case 'attempt_completion':
        sanitized = sanitizeAttemptCompletionResult(input, data);
        break;
      case 'get_function':
        sanitized = sanitizeGetFunctionResult(input, data);
        break;
      case 'edit_function':
        sanitized = sanitizeEditFunctionResult(input, data);
        break;
      case 'edit_lines':
        sanitized = sanitizeEditLinesResult(input, data);
        break;
      // Todo tools - keep as-is (usually small)
      case 'create_todo_list':
      case 'read_todos':
      case 'add_todos':
      case 'remove_todos':
      case 'mark_todo_as_done':
      case 'update_todo_status':
        sanitized = data;
        break;
      default:
        sanitized = sanitizeGenericResult(input, data);
        break;
    }

    return attachEnvelopeMeta(sanitized, envelope);
  } catch (err) {
    console.warn(`[toolRenderSanitizer] Error sanitizing ${name}:`, err);
    const fallback = { error: data?.error, _sanitized: true };
    return attachEnvelopeMeta(fallback, envelope);
  }
}

/**
 * Normalize tool name to standard format
 */
function normalizeToolName(name) {
  if (!name || typeof name !== 'string') return '';
  const s = name.trim().toLowerCase();
  if (/^context[-_]?search$/i.test(s)) return 'context_search';
  if (/^apply[-_]?patch$/i.test(s)) return 'apply_patch';
  if (/^run[-_]?command$/i.test(s)) return 'run_command';
  if (/^read[-_]?any[-_]?files$/i.test(s)) return 'read_any_files';
  if (/^read[-_]?multiple[-_]?files$/i.test(s)) return 'read_multiple_files';
  return s;
}

function unwrapToolResult(result) {
  if (result && typeof result === 'object' && result._tool_result === true) {
    return { envelope: result, data: result.data ?? {} };
  }
  return { envelope: null, data: result };
}

function attachEnvelopeMeta(sanitized, envelope) {
  if (!envelope || !sanitized || typeof sanitized !== 'object') return sanitized;

  const meta = {
    ok: envelope.ok,
    status: envelope.status,
    message: envelope.message,
    error_code: envelope.error_code,
    metrics: envelope.metrics,
    schema_id: envelope.schema_id,
    schema_version: envelope.schema_version
  };

  const out = { ...sanitized, _meta: meta };

  if (typeof out.ok !== 'boolean') out.ok = meta.ok;
  if (!out.status) out.status = meta.status;
  if (!out.message && meta.message) out.message = meta.message;
  if (!out.error_code && meta.error_code) out.error_code = meta.error_code;
  if (!out.error && meta.ok === false && meta.message) out.error = meta.message;
  if (typeof out.success !== 'boolean') out.success = meta.ok;

  return out;
}

// ============ Individual Tool Sanitizers ============

function sanitizeViewResult(input, result) {
  return {
    success: !result.error,
    path: input?.path || result?.path,
    total_line_count: result?.total_line_count,
    displayed_lines: result?.displayed_lines,
    is_truncated: result?.is_truncated,
    range_info: result?.range_info,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeMultiFileResult(input, result) {
  // Keep file metadata but strip content
  const files = Array.isArray(result?.files) ? result.files.map(f => ({
    path: f?.path,
    displayed_lines: f?.displayed_lines,
    total_line_count: f?.total_line_count ?? f?.total_lines,
    total_lines: f?.total_lines ?? f?.total_line_count,
    range: f?.range,
    is_truncated: f?.is_truncated ?? f?.truncated,
    error: f?.error,
    error_code: f?.error_code,
    success: f?.success !== false && !f?.error
  })) : [];
  
  return {
    mode: 'multiple',
    files,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeLsResult(input, result) {
  return {
    success: !result.error,
    path: input?.path || result?.summary?.path || result?.summary?.base_path,
    entriesCount: Array.isArray(result?.entries) ? result.entries.length : (result?.summary?.total_items || 0),
    max_depth: input?.max_depth || result?.summary?.max_depth,
    summary: result?.summary ? {
      total_items: result.summary.total_items,
      base_path: result.summary.base_path || result.summary.path,
      max_depth: result.summary.max_depth
    } : undefined,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeGrepResult(input, result) {
  return {
    success: !result.error,
    path: input?.path || result?.path,
    patterns: Array.isArray(input?.queries) ? input.queries : (result?.summary?.patterns || []),
    pattern_stats: result?.pattern_stats,
    matched_files_count: result?.matched_files?.length || 0,
    matched_files: result?.matched_files?.map(f => {
      if (typeof f === 'string') return f;
      return f?.path || f?.file_path;
    }) || [],
    summary: result?.summary,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeBashResult(input, result) {
  const finished = result?.finished;
  if (!finished) {
    return {
      command: input?.command,
      waiting: result?.waiting,
      error: result?.error,
      _sanitized: true
    };
  }
  
  // NO LINE-BASED TRUNCATION HERE
  // Return full output - gptCycle.js will handle 15KB byte-based truncation
  const output = finished.output || '';
  const lines = output.split(/\r?\n/);
  
  return {
    finished: {
      command: finished.command || input?.command,
      output: output, // Full output, no line truncation
      exit_code: finished.exit_code,
      new_pwd: finished.new_pwd,
      displayed_lines: lines.length,
      total_lines: lines.length,
      truncated: false // Truncation will happen in gptCycle.js, not here
    },
    flood_notice: result?.flood_notice,
    terminated_by_system_flood: result?.terminated_by_system_flood,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizePatchResult(input, result) {
  // Keep patch metadata but strip actual diff content
  const applied = Array.isArray(result?.applied) ? result.applied.map(ap => {
    const diffPreview = buildDiffPreview(ap?.diff_hunk, 60);
    return {
      file_path: ap?.file_path,
      message: ap?.message,
      inserted_line_count: ap?.inserted_line_count || ap?.replaced?.inserted_line_count,
      deleted_line_count: ap?.deleted_line_count || ap?.replaced?.deleted_line_count,
      auto_aligned_to_line: ap?.auto_aligned_to_line,
      diff_header: ap?.diff_hunk?.header,
      diff_preview: diffPreview
    };
  }) : [];
  
  const errors = Array.isArray(result?.errors) ? result.errors.map(e => ({
    file_path: e?.file_path,
    error: e?.error
  })) : [];
  
  return {
    title: result?.title,
    applied_count: result?.applied_count || applied.length,
    error_count: result?.error_count || errors.length,
    applied,
    errors,
    error: result?.error,
    _sanitized: true
  };
}

function buildDiffPreview(diffHunk, maxLines = 60) {
  if (!diffHunk || !Array.isArray(diffHunk.lines) || diffHunk.lines.length === 0) {
    return null;
  }
  const header = diffHunk.header ? String(diffHunk.header) : '';
  const lines = diffHunk.lines.slice(0, maxLines).map(l => {
    const type = l?.type || 'context';
    const text = String(l?.text || '');
    const prefix = type === 'add' ? '+' : (type === 'del' ? '-' : (type === 'meta' ? '' : ' '));
    return `${prefix}${text}`;
  });
  if (diffHunk.lines.length > maxLines) {
    lines.push('… diff preview truncated …');
  }
  return [header, ...lines].filter(Boolean).join('\n');
}

function sanitizeWriteResult(input, result) {
  return {
    success: !result.error,
    path: input?.path || result?.path,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeStrReplaceResult(input, result) {
  return {
    success: !result.error,
    path: input?.path || result?.path,
    replacements_made: result?.replacements_made,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeFsAppendResult(input, result) {
  return {
    success: !result.error,
    path: input?.path || result?.path,
    bytes_appended: result?.bytes_appended,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeFindFilesResult(input, result) {
  // Keep file list but strip any content
  const files = Array.isArray(result?.files) ? result.files.map(f => ({
    fileName: f?.fileName,
    fullPath: f?.fullPath
  })) : [];
  
  return {
    patterns: result?.patterns || input?.patterns,
    files,
    total_found: files.length,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeGlobResult(input, result) {
  return {
    pattern: input?.pattern,
    path: input?.path,
    matches_count: Array.isArray(result?.matches) ? result.matches.length : 0,
    matches: result?.matches?.slice(0, 50), // Limit to 50 matches for render
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeWebFetchResult(input, result) {
  // Strip fetched content, keep metadata
  return {
    success: !result.error,
    url: input?.url || result?.url,
    title: result?.title,
    content_length: result?.content?.length || result?.content_length,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeWebSearchResult(input, result) {
  // Keep search results metadata but limit
  const results = Array.isArray(result?.results) ? result.results.slice(0, 10).map(r => ({
    title: r?.title,
    url: r?.url,
    snippet: r?.snippet?.slice(0, 200) // Truncate snippets
  })) : [];
  
  return {
    query: input?.query || result?.query,
    results,
    total_results: result?.results?.length || results.length,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeDiagnosticsResult(input, result) {
  // Keep diagnostic info but limit size
  const results = Array.isArray(result?.results) ? result.results.map(r => ({
    path: r?.path,
    exists: r?.exists,
    issues: Array.isArray(r?.issues) ? r.issues.slice(0, 20).map(issue => ({
      line: issue?.line,
      column: issue?.column,
      severity: issue?.severity,
      message: issue?.message?.slice(0, 200)
    })) : []
  })) : [];

  return {
    paths: input?.paths,
    results,
    summary: result?.summary,
    success: !result?.error,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeAskResult(input, result) {
  return {
    question: input?.question,
    mode: input?.mode,
    options: input?.options,
    waiting: result?.waiting,
    answered: result?.answered,
    answer_text: result?.answer_text || result?.answer?.text,
    selected: result?.selected || result?.answer?.selected,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeDeleteResult(input, result) {
  // Keep item status but strip any content
  const items = Array.isArray(result?.items) ? result.items.map(it => ({
    requested_path: it?.requested_path,
    full_path: it?.full_path,
    kind: it?.kind,
    status: it?.status,
    selected: it?.selected,
    error: it?.error
  })) : [];
  
  return {
    mode: result?.mode || input?.mode,
    waiting: result?.waiting,
    items,
    summary: result?.summary,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeAttemptCompletionResult(input, result) {
  // Keep the completion text (usually small)
  return {
    result: input?.result || result?.result || result?.text,
    _sanitized: true
  };
}

function sanitizeGetFunctionResult(input, result) {
  const symbolName = result?.symbol_name || result?.function_name || result?.symbol?.name || input?.name;
  const startLine = result?.start_line ?? result?.symbol?.start_line;
  const endLine = result?.end_line ?? result?.symbol?.end_line;
  const lineCount = result?.line_count ?? result?.symbol?.line_count;
  const symbols = Array.isArray(result?.symbols)
    ? result.symbols.slice(0, 50).map(s => ({
      name: s?.name,
      type: s?.type,
      start_line: s?.start_line,
      end_line: s?.end_line,
      line_count: s?.line_count
    }))
    : undefined;

  return {
    success: !result.error,
    path: input?.path || result?.path,
    symbol_name: symbolName,
    function_name: symbolName,
    start_line: startLine,
    end_line: endLine,
    line_count: lineCount,
    symbols,
    total_symbols: result?.total_symbols,
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeEditFunctionResult(input, result) {
  const changes = result?.changes || {};
  const startLine = result?.start_line ?? changes.start_line;
  const endLine = result?.end_line ?? (typeof changes.start_line === 'number' && typeof changes.old_line_count === 'number'
    ? changes.start_line + changes.old_line_count - 1
    : undefined);
  const oldLines = result?.old_lines ?? changes.old_line_count;
  const newLines = result?.new_lines ?? changes.new_line_count;

  return {
    success: !result.error,
    path: input?.path || result?.path,
    function_name: input?.function_name || result?.function_name || result?.symbol,
    symbol: result?.symbol || input?.name,
    action: result?.action || input?.action,
    start_line: startLine,
    end_line: endLine,
    old_lines: oldLines,
    new_lines: newLines,
    lines_changed: result?.lines_changed ?? (typeof oldLines === 'number' ? oldLines : undefined),
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeEditLinesResult(input, result) {
  const changes = result?.changes || {};
  const startLine = result?.start_line ?? changes.start_line ?? input?.start_line;
  const endLine = result?.end_line ?? changes.end_line ?? input?.end_line;
  const oldLines = result?.old_lines ?? changes.old_line_count;
  const newLines = result?.new_lines ?? changes.new_line_count;

  return {
    success: !result.error,
    path: input?.path || result?.path,
    start_line: startLine,
    end_line: endLine,
    old_lines: oldLines,
    new_lines: newLines,
    lines_changed: result?.lines_changed ?? (typeof oldLines === 'number' ? oldLines : undefined),
    error: result?.error,
    _sanitized: true
  };
}

function sanitizeGenericResult(input, result) {
  // For unknown tools, try to keep it small
  const sanitized = { _sanitized: true };
  
  if (result.error) sanitized.error = result.error;
  if (result.success !== undefined) sanitized.success = result.success;
  if (result.path) sanitized.path = result.path;
  if (result.message) sanitized.message = String(result.message).slice(0, 500);
  
  // Check total size and truncate if needed
  try {
    const str = JSON.stringify(result);
    if (str.length < 2000) {
      // Small enough, return as-is with sanitized flag
      return { ...result, _sanitized: true };
    }
  } catch (_) {}
  
  return sanitized;
}

/**
 * Sanitize tool input for render history
 * Removes large content from input (like file contents in write operations)
 */
function sanitizeToolInputForRender(toolName, input) {
  if (!input || typeof input !== 'object') return input;

  const sanitized = { ...input };

  // Remove content fields that can be large
  const contentFields = ['content', 'file_content', 'new_content', 'replacement', 'patch', 'diff'];
  for (const field of contentFields) {
    if (sanitized[field] && typeof sanitized[field] === 'string' && sanitized[field].length > 500) {
      sanitized[field] = `[${sanitized[field].length} chars]`;
      sanitized._content_stripped = true;
    }
  }

  return sanitized;
}

function sanitizeForToolHistory(toolName, input, result) {
  return {
    name: toolName,
    input: sanitizeToolInputForRender(toolName, input),
    result: sanitizeToolResultForRender(toolName, input, result),
    _render_only: true
  };
}

/**
 * Create a minimal tool entry for render history
 * Contains only what's needed to render the tool UI
 */
function createRenderToolEntry(toolName, input, result) {
  return {
    name: toolName,
    input: sanitizeToolInputForRender(toolName, input),
    result: sanitizeToolResultForRender(toolName, input, result),
    _render_only: true
  };
}

module.exports = {
  sanitizeToolResultForRender,
  sanitizeToolInputForRender,
  createRenderToolEntry,
  sanitizeForToolHistory,
  normalizeToolName
};
