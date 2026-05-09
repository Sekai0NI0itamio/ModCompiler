// Parses model output into sanitized text and structured tool uses

const { hasClaudeToolCalls, convertClaudeToOpenAI, extractClaudeToolCalls } = require('../helpers/claudeToolConverter');

/**
 * Find all "protected" ranges in text where tool tags should NOT be parsed:
 * - Fenced code blocks (```...```)
 * - Inline code (`...`)
 * 
 * NOTE: We intentionally do NOT protect quoted strings ("..." or '...') because
 * the JSON inside <tool_use> blocks contains many quoted strings, and protecting
 * them would cause legitimate tools to be skipped.
 * 
 * Returns array of { start, end } objects
 */
function findProtectedRanges(text) {
  const ranges = [];
  
  // Find fenced code blocks (```...```) - highest priority
  const fencedRe = /```[\s\S]*?```/g;
  let match;
  while ((match = fencedRe.exec(text)) !== null) {
    ranges.push({ start: match.index, end: match.index + match[0].length });
  }
  
  // Helper to check if position is already in a range
  const isInRange = (pos) => ranges.some(r => pos >= r.start && pos < r.end);
  
  // Find inline code (`...`) - but not inside fenced blocks
  const inlineRe = /`[^`\n]+`/g;
  while ((match = inlineRe.exec(text)) !== null) {
    if (!isInRange(match.index)) {
      ranges.push({ start: match.index, end: match.index + match[0].length });
    }
  }
  
  return ranges;
}

/**
 * Check if a position is inside any protected range
 */
function isInsideProtectedRange(pos, protectedRanges) {
  return protectedRanges.some(r => pos >= r.start && pos < r.end);
}

function extractToolUsesAndSanitize(text) {
  if (!text || typeof text !== 'string') return { sanitizedText: text || '', tools: [], originalText: text || '' };

  // First, check for and convert Claude Code XML tool calls to OpenAI format
  let processedText = text;
  if (hasClaudeToolCalls(text)) {
    console.log('[parser] Detected Claude Code XML tool calls, converting to OpenAI format');
    processedText = convertClaudeToOpenAI(text);
  }

  const tools = [];
  let sanitizedText = processedText;
  let originalText = text; // Keep the original for history

  // Find all protected ranges (code blocks, quotes) where we should NOT parse tool tags
  const protectedRanges = findProtectedRanges(text);

  // Match <tool_use>{...}</tool_use>
  const toolRe = /<tool_use>\s*([\s\S]*?)\s*<\/tool_use>/gi;
  let toolIndex = 0;

  // 1) Replace complete tool blocks with placeholders (but skip those inside protected ranges)
  sanitizedText = sanitizedText.replace(toolRe, (m, jsonRaw, offset) => {
    // Skip if this match is inside a protected range (code blocks, quotes)
    if (isInsideProtectedRange(offset, protectedRanges)) {
      return m; // Keep the original text
    }
    
    // Try parsing tool JSON. Accept tolerant parsing and add robust fallbacks for name/input extraction.
    let parsed = tryParseJson(jsonRaw) || tryParseJson(extractJsonObject(jsonRaw));
    if (!parsed) {
      try { parsed = JSON.parse(jsonRaw); } catch (_) { parsed = null; }
    }

    let name = parsed && parsed.name ? String(parsed.name).trim() : '';
    let input = (parsed && parsed.input && typeof parsed.input === 'object') ? parsed.input : {};
    
    // VALIDATION: Only treat as a tool if it has a valid name field
    // This prevents false positives when GPT writes <tool_use> as an example in text
    if (!name) {
      try {
        const mName = String(jsonRaw).match(/\"name\"\s*:\s*\"([^\"]*)/i);
        if (mName && mName[1]) name = mName[1].trim();
      } catch (_) {}
    }
    
    // If still no valid name, don't treat as a tool
    if (!name) {
      return m; // Keep the original text
    }
    
    // Prefer direct result; else tool_system_results; else unwrap tool_system_response.result if present
    let result = null;
    if (parsed && typeof parsed.result !== 'undefined') {
      result = parsed.result;
    } else if (parsed && typeof parsed.tool_system_results !== 'undefined') {
      result = parsed.tool_system_results;
    } else if (parsed && typeof parsed.tool_system_response !== 'undefined') {
      const tsr = parsed.tool_system_response;
      if (tsr && typeof tsr === 'object' && typeof tsr.result !== 'undefined') result = tsr.result; else result = tsr;
    } else {
      result = null;
    }

    // Fallback for input when strict JSON parsing failed
    if ((!input || Object.keys(input).length === 0)) {
      try {
        const s = String(jsonRaw);
        const keyIdx = s.search(/\"input\"\s*:/i);
        if (keyIdx !== -1) {
          const braceIdx = s.indexOf('{', keyIdx);
          if (braceIdx !== -1) {
            const end = findMatchingJsonEnd(s, braceIdx);
            if (end !== -1) {
              const objStr = s.slice(braceIdx, end + 1);
              const maybe = tryParseJson(objStr) || (function(){ try { return JSON.parse(objStr); } catch(_) { return null; }})();
              if (maybe && typeof maybe === 'object') input = maybe;
            }
          }
        }
      } catch (_) {}
    }

    // Placeholder box: minimal content; will be enhanced at render-time using structured data
    const idx = toolIndex++;
    const placeholder = `
<div class=\"auaci-tool-box\" data-tool=\"${escapeHtml(name)}\" data-tool-index=\"${idx}\">\n  <div class=\"tool-header\"><span class=\"auaci-tool-name\">${escapeHtml(name || 'tool')}</span></div>\n  <div class=\"tool-body\"></div>\n</div>`;

    tools.push({ name, input, raw: jsonRaw, result, index: idx });
    return placeholder;
  });

  // 2) If there is a trailing, incomplete <tool_use> ... (no closing tag yet), hide its raw text
  //    and emit a placeholder so the UI shows only the box display (e.g., for replace_in_file).
  //    BUT skip if it's inside a protected range (code blocks, quotes)
  const incompleteMatch = /<tool_use>\s*([\s\S]*)$/i.exec(sanitizedText);
  if (incompleteMatch) {
    const startIdx = incompleteMatch.index;
    
    // Skip if inside a protected range
    if (!isInsideProtectedRange(startIdx, protectedRanges)) {
      const partialRaw = incompleteMatch[1] || '';

      // Try to recover some structure (name, input) from partial JSON
      const partialObj = tryParseJson(partialRaw) || tryParseJson(extractJsonObject(partialRaw)) || null;
      let name = '';
      let input = {};
      if (partialObj && typeof partialObj === 'object') {
        if (partialObj.name) name = String(partialObj.name).trim();
        if (partialObj.input && typeof partialObj.input === 'object') input = partialObj.input;
      } else {
        // Fallback: regex to capture name field even if JSON is incomplete
        const mName = partialRaw.match(/\"name\"\s*:\s*\"([^\"]*)/i);
        if (mName) name = mName[1];
      }
      
      // Only create placeholder if we have a valid name
      if (name) {
        const idx = toolIndex++;
        const placeholder = `
<div class=\"auaci-tool-box\" data-tool=\"${escapeHtml(name)}\" data-tool-index=\"${idx}\">\n  <div class=\"tool-header\"><span class=\"auaci-tool-name\">${escapeHtml(name || 'tool')}</span></div>\n  <div class=\"tool-body\"></div>\n</div>`;

        tools.push({ name, input, raw: partialRaw, result: null, index: idx });

        // Preserve any assistant text that appears after the partial tool JSON.
        // Heuristic: find the end of the first JSON object after the <tool_use> tag and only replace up to there.
        let replaceEnd = sanitizedText.length;
        try {
          const openBrace = sanitizedText.indexOf('{', startIdx);
          if (openBrace !== -1) {
            const jsonEnd = findMatchingJsonEnd(sanitizedText, openBrace);
            if (jsonEnd !== -1) {
              replaceEnd = jsonEnd + 1;
              // Also skip any trailing annotation lines immediately following the JSON (e.g., "Note: ...")
              replaceEnd = skipAnnotationLines(sanitizedText, replaceEnd);
            }
          }
        } catch (_) {}

        sanitizedText = sanitizedText.slice(0, startIdx) + placeholder + sanitizedText.slice(replaceEnd);
      }
    }
  }

  // Strip orphan tool-result JSON blocks that may have been appended outside <tool_use>
  // so they don't leak into the visible UI during streaming.
  sanitizedText = removeOrphanToolResultJson(sanitizedText);
  // Additionally strip raw terminal transcript text that sometimes leaks outside tool boxes
  try {
    if (!(typeof window !== 'undefined' && window.AUACI_KEEP_TERMINAL_TEXT === true)) {
      sanitizedText = removeRawTerminalText(sanitizedText);
    }
  } catch (_) {
    sanitizedText = removeRawTerminalText(sanitizedText);
  }
  // Also hide accidental system-context lines that should never be visible to the user
  sanitizedText = sanitizedText.replace(/(^|\n)Actual conversation history:[^\n]*\n?/i, '$1');

  return { sanitizedText, tools, originalText };
}


function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function tryParseJson(text) {
  if (text == null) return null;
  let t = String(text).trim();
  if (!t) return null;
  
  // Only strip code fences if the text actually contains them
  // This prevents stripping code fences from plain text that doesn't have them
  const codeFenceMatch = t.match(/^```[a-zA-Z0-9]*\n([\s\S]*?)\n```$/m);
  if (codeFenceMatch) {
    t = codeFenceMatch[1].trim();
  }
  
  // unescape common HTML entities
  t = t.replace(/&quot;/g, '"').replace(/&#34;/g, '"').replace(/&amp;/g, '&');
  try {
    return JSON.parse(t);
  } catch (_) {
    return null;
  }
}

function extractJsonObject(text) {
  const s = String(text || '');
  const start = s.indexOf('{');
  const end = s.lastIndexOf('}');
  if (start === -1 || end === -1 || end <= start) return null;
  return s.slice(start, end + 1);
}

// --- Heuristics to remove orphan tool-result JSON from visible text (used during streaming) ---
function removeOrphanToolResultJson(text) {
  if (!text || typeof text !== 'string') return text;
  if (text.indexOf('{') === -1) return text; // quick path
  const fences = findCodeFences(text);
  const isInsideFence = (idx) => {
    for (const f of fences) if (idx >= f.start && idx < f.end) return true;
    return false;
  };
  let out = '';
  let i = 0;

  while (i < text.length) {
    const ch = text[i];
    if (ch === '{' && !isInsideFence(i)) {
      const end = findMatchingJsonEnd(text, i);
      if (end !== -1) {
        const candidate = text.slice(i, end + 1);
        if (looksLikeToolResult(candidate)) {
          // Skip this JSON object entirely and also skip a following "Note:"-style annotation if present
          let j = end + 1;
          j = skipAnnotationLines(text, j);
          i = j;
          continue;
        }
      }
    }
    out += ch;
    i++;
  }
  return out;
}

function looksLikeToolResult(jsonStr) {
  try {
    const obj = JSON.parse(jsonStr);
    if (!obj || typeof obj !== 'object') return false;
    const keys = Object.keys(obj);
    const hasViewKeys = ('content' in obj) || ('total_line_count' in obj) || ('is_truncated' in obj);
    const hasLsKeys = Array.isArray(obj.entries);
    const hasRunKeys = (obj.finished && typeof obj.finished.exit_code !== 'undefined');
    const hasGenericError = ('error' in obj) && keys.length <= 3;
    const hasTxtlizeKeys = ('output_path' in obj) || ('file_count' in obj) || ('total_size_bytes' in obj);
    const hasSuccessSmall = ('success' in obj) && keys.length <= 4;
    // Grep-specific heuristics: pattern_stats / matched_files are common grep outputs
    const hasGrepKeys = ('pattern_stats' in obj) || ('matched_files' in obj) || ('matched_files_count' in obj) || (Array.isArray(obj.pattern_stats));
    const hasEnhancedWarpDisplay = ('display_content' in obj) || ('summary' in obj && typeof obj.summary === 'object');
    return hasViewKeys || hasLsKeys || hasRunKeys || hasGenericError || hasTxtlizeKeys || hasSuccessSmall || hasGrepKeys || hasEnhancedWarpDisplay;
  } catch (_) {
    return false;
  }
}

// --- Additional heuristic: strip raw terminal transcripts that leak outside <tool_use> ---
function removeRawTerminalText(text) {
  if (!text || typeof text !== 'string') return text;
  // Quick check
  if (text.indexOf('Terminal') === -1) return text;

  const fences = findCodeFences(text);
  const toolRanges = findToolUseRanges(text);
  const isInsideFence = (idx) => { for (const f of fences) if (idx >= f.start && idx < f.end) return true; return false; };
  const isInsideTool = (idx) => { for (const r of toolRanges) if (idx >= r.start && idx < r.end) return true; return false; };

  // Rebuild the string while removing blocks that look like stray terminal transcripts:
  // Pattern: a line "Terminal" followed by a prompt-like line containing " % " within the next few lines.
  let out = '';
  let i = 0;
  while (i < text.length) {
    const termIdx = text.indexOf('Terminal', i);
    if (termIdx === -1) { out += text.slice(i); break; }
    // copy text before match
    out += text.slice(i, termIdx);
    // If inside fence or tool_use, keep as-is
    if (isInsideFence(termIdx) || isInsideTool(termIdx)) {
      out += 'Terminal';
      i = termIdx + 'Terminal'.length;
      continue;
    }
    // Ensure "Terminal" is at line boundary
    const lineStart = (() => { const nl = text.lastIndexOf('\n', termIdx); return (nl === -1) ? 0 : (nl + 1); })();
    const lineEnd = (() => { const nl = text.indexOf('\n', termIdx); return (nl === -1) ? text.length : nl; })();
    const firstLine = text.slice(lineStart, lineEnd).trim();
    if (!/^Terminal\s*$/i.test(firstLine)) {
      // Not a standalone Terminal header; keep literal
      out += text.slice(termIdx, termIdx + 'Terminal'.length);
      i = termIdx + 'Terminal'.length;
      continue;
    }
    // Look ahead up to the next blank line or next tool_use to detect a prompt-like line containing " % "
    let j = lineEnd + 1;
    let foundPrompt = false;
    let stopAt = text.length;
    const nextTool = text.indexOf('<tool_use>', j);
    if (nextTool !== -1) stopAt = Math.min(stopAt, nextTool);
    // Stop at next blank line boundary too
    const mBlank = /\n\s*\n/.exec(text.slice(j));
    if (mBlank) stopAt = Math.min(stopAt, j + mBlank.index);
    // Limit the scan window to avoid pathological cases
    const MAX_WINDOW = j + 4000; // 4KB window
    stopAt = Math.min(stopAt, MAX_WINDOW, text.length);
    const windowStr = text.slice(j, stopAt);
    // A prompt line typically looks like "basename % command"
    const promptRe = /^.*\s%\s.*$/m;
    foundPrompt = promptRe.test(windowStr);
    if (foundPrompt) {
      // Remove the whole block from "Terminal" line through the scanned window
      i = stopAt;
      continue;
    } else {
      // No prompt found; keep literal text
      out += text.slice(termIdx, stopAt);
      i = stopAt;
      continue;
    }
  }
  return out;
}

function findToolUseRanges(text) {
  const ranges = [];
  const re = /<tool_use>[\s\S]*?<\/tool_use>/gi;
  let m;
  while ((m = re.exec(text)) !== null) {
    ranges.push({ start: m.index, end: re.lastIndex });
  }
  return ranges;
}

function findCodeFences(text) {
  const ranges = [];
  const re = /```[\s\S]*?```/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    ranges.push({ start: m.index, end: re.lastIndex });
  }
  return ranges;
}

function skipAnnotationLines(text, pos) {
  let i = pos;
  // skip initial whitespace/newlines
  while (i < text.length && /\s/.test(text[i])) i++;
  let advanced = false;
  const annotRe = /^\s*(?:Note\b|Note:|Warning\b|Warning:|Info:|Info\b|Hint:|Hint\b)/i;
  while (i < text.length) {
    const nextNl = text.indexOf('\n', i);
    const line = nextNl === -1 ? text.slice(i) : text.slice(i, nextNl);
    if (annotRe.test(line)) {
      // skip this annotation line and continue
      i = nextNl === -1 ? text.length : nextNl + 1;
      advanced = true;
      continue;
    }
    // allow one blank line after annotations, then stop
    if (line.trim() === '' && advanced) {
      i = nextNl === -1 ? text.length : nextNl + 1;
      break;
    }
    break;
  }
  return i;
}

function findMatchingJsonEnd(text, startIndex) {
  let i = startIndex;
  const len = text.length;
  if (text[i] !== '{') return -1;
  let depth = 0;
  let inString = false;
  let stringChar = null;
  let escape = false;
  for (; i < len; i++) {
    const ch = text[i];
    if (inString) {
      if (escape) { escape = false; continue; }
      if (ch === '\\') { escape = true; continue; }
      if (ch === stringChar) { inString = false; stringChar = null; continue; }
      continue;
    } else {
      if (ch === '"' || ch === "'") { inString = true; stringChar = ch; continue; }
      if (ch === '{') { depth++; continue; }
      if (ch === '}') { depth--; if (depth === 0) return i; continue; }
    }
  }
  return -1;
}

module.exports = { extractToolUsesAndSanitize };