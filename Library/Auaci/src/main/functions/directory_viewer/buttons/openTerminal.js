// src/buttons/openTerminal.js
const fs = require('fs').promises;
const path = require('path');
const os = require('os');
const { execFile } = require('child_process');

// Shell-safe single-quote escaper for paths and other literal strings.
function shSingleQuote(str) {
  // Replace every single quote ' with '\'' which in JS becomes: '\"'\"'
  return "'" + String(str).replace(/'/g, "'\"'\"'") + "'";
}

/**
 * Detect how to run a file based on its extension or type
 * Returns the command to execute the file, or null if unknown
 */
function getFileRunCommand(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  const fileName = path.basename(filePath);
  
  // Map of extensions to run commands
  const runCommands = {
    // Python
    '.py': `python3 ${shSingleQuote(fileName)}`,
    '.pyw': `python3 ${shSingleQuote(fileName)}`,
    
    // JavaScript/Node
    '.js': `node ${shSingleQuote(fileName)}`,
    '.mjs': `node ${shSingleQuote(fileName)}`,
    '.cjs': `node ${shSingleQuote(fileName)}`,
    
    // TypeScript (via ts-node or npx)
    '.ts': `npx ts-node ${shSingleQuote(fileName)}`,
    '.tsx': `npx ts-node ${shSingleQuote(fileName)}`,
    
    // Shell scripts
    '.sh': `bash ${shSingleQuote(fileName)}`,
    '.bash': `bash ${shSingleQuote(fileName)}`,
    '.zsh': `zsh ${shSingleQuote(fileName)}`,
    
    // Ruby
    '.rb': `ruby ${shSingleQuote(fileName)}`,
    
    // Perl
    '.pl': `perl ${shSingleQuote(fileName)}`,
    '.pm': `perl ${shSingleQuote(fileName)}`,
    
    // PHP
    '.php': `php ${shSingleQuote(fileName)}`,
    
    // Go
    '.go': `go run ${shSingleQuote(fileName)}`,
    
    // Rust
    '.rs': `rustc ${shSingleQuote(fileName)} -o /tmp/rust_out && /tmp/rust_out`,
    
    // Java
    '.java': `javac ${shSingleQuote(fileName)} && java ${shSingleQuote(fileName.replace('.java', ''))}`,
    
    // C/C++
    '.c': `gcc ${shSingleQuote(fileName)} -o /tmp/c_out && /tmp/c_out`,
    '.cpp': `g++ ${shSingleQuote(fileName)} -o /tmp/cpp_out && /tmp/cpp_out`,
    '.cc': `g++ ${shSingleQuote(fileName)} -o /tmp/cpp_out && /tmp/cpp_out`,
    
    // Lua
    '.lua': `lua ${shSingleQuote(fileName)}`,
    
    // R
    '.r': `Rscript ${shSingleQuote(fileName)}`,
    '.R': `Rscript ${shSingleQuote(fileName)}`,
    
    // Swift
    '.swift': `swift ${shSingleQuote(fileName)}`,
    
    // Kotlin
    '.kt': `kotlinc ${shSingleQuote(fileName)} -include-runtime -d /tmp/kt_out.jar && java -jar /tmp/kt_out.jar`,
    
    // Scala
    '.scala': `scala ${shSingleQuote(fileName)}`,
    
    // Groovy
    '.groovy': `groovy ${shSingleQuote(fileName)}`,
    
    // Dart
    '.dart': `dart ${shSingleQuote(fileName)}`,
    
    // Elixir
    '.ex': `elixir ${shSingleQuote(fileName)}`,
    '.exs': `elixir ${shSingleQuote(fileName)}`,
    
    // Haskell
    '.hs': `runhaskell ${shSingleQuote(fileName)}`,
    
    // Clojure
    '.clj': `clojure ${shSingleQuote(fileName)}`,
    
    // PowerShell
    '.ps1': `pwsh ${shSingleQuote(fileName)}`,
  };
  
  if (runCommands[ext]) {
    return runCommands[ext];
  }
  
  // For files without extension or unknown extensions, try to make executable and run
  // This handles binary files
  return `chmod +x ${shSingleQuote(fileName)} && ./${shSingleQuote(fileName)}`;
}

/**
 * openTerminal(projectRoot, relativePath)
 * - Build a plain-text shell script (joined with "&&") and run it in Terminal.app via AppleScript.
 * - If helper <projectRoot>/bin/open_terminal exists, invoke it with the raw script string.
 * - Otherwise, fallback to invoking /usr/bin/osascript directly (no temp .command file).
 * - Now supports both folders and files. For files, opens terminal in the file's directory
 *   with an option to run the file after other commands.
 */
async function openTerminal(projectRoot, relativePath = '') {
  const fullPath = path.join(projectRoot, relativePath || '');
  const resolvedFullPath = path.resolve(fullPath);
  
  // Check if the path is a file or directory
  let isFile = false;
  let fileName = '';
  let baseFolder = resolvedFullPath;
  
  try {
    const stat = await fs.stat(resolvedFullPath);
    isFile = stat.isFile();
    if (isFile) {
      fileName = path.basename(resolvedFullPath);
      baseFolder = path.dirname(resolvedFullPath);
    }
  } catch (err) {
    console.warn('Error checking path type:', err);
  }
  
  const resolvedBaseFolder = baseFolder;
  
  // Detect existing virtual environments in the folder
  let hasVirtualEnv = false;
  let detectedVenvName = '.venv';
  
  try {
    const venvNames = ['.venv', 'venv', '.virtualenv', 'virtualenv', 'env', '.env'];
    
    for (const name of venvNames) {
      const venvPath = path.join(resolvedBaseFolder, name);
      const activatePath = path.join(venvPath, 'bin', 'activate');
      
      try {
        await fs.access(activatePath);
        hasVirtualEnv = true;
        detectedVenvName = name;
        break;
      } catch (err) {
        // Continue checking other names
      }
    }
  } catch (err) {
    console.warn('Error detecting virtual environment:', err);
  }

  // overlay
  const overlay = document.createElement('div');
  Object.assign(overlay.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    width: '100vw',
    height: '100vh',
    background: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: '1000'
  });

  const card = document.createElement('div');
  Object.assign(card.style, {
    display: 'flex',
    gap: '12px',
    background: '#fff',
    padding: '16px',
    borderRadius: '8px',
    boxShadow: '0 8px 28px rgba(0,0,0,0.12)',
    width: 'min(92vw, 820px)',
    maxWidth: '820px',
    boxSizing: 'border-box'
  });

  // LEFT
  const left = document.createElement('div');
  Object.assign(left.style, {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
    flex: '1',
    minWidth: '360px'
  });

  const title = document.createElement('div');
  title.textContent = 'Open Terminal';
  Object.assign(title.style, { fontSize: '15px', fontWeight: '600', color: '#111' });

  // Show different info for files vs folders
  const displayBase = path.relative(projectRoot, resolvedBaseFolder) || '.';
  const info = document.createElement('div');
  if (isFile) {
    info.innerHTML = `<strong>File:</strong> ${fileName}<br><strong>Directory:</strong> ${displayBase}`;
  } else {
    info.textContent = `Base folder (relative to project): ${displayBase}`;
  }
  Object.assign(info.style, { fontSize: '12px', color: '#444', background: '#f7f9fb', padding: '8px', borderRadius: '6px', border: '1px solid #eef4ff', wordBreak: 'break-all' });

  // Options container
  const optsContainer = document.createElement('div');
  Object.assign(optsContainer.style, { display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '6px' });

  function createCheckboxRow(labelText, checked = false) {
    const row = document.createElement('label');
    Object.assign(row.style, { display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '13px' });
    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.checked = checked;
    Object.assign(cb.style, { width: '16px', height: '16px' });
    const lbl = document.createElement('span');
    lbl.textContent = labelText;
    row.appendChild(cb);
    row.appendChild(lbl);
    return { row, cb };
  }

  const setDir = createCheckboxRow('Set terminal to folder directory (cd into it)', true);
  const sourceVenv = createCheckboxRow('Source virtualenv', hasVirtualEnv);
  const venvInput = document.createElement('input');
  venvInput.type = 'text';
  venvInput.value = detectedVenvName;
  Object.assign(venvInput.style, { padding: '6px', fontSize: '13px', borderRadius: '6px', border: '1px solid #d6dde6', width: '160px' });

  const createVenv = createCheckboxRow('Create a python venv and source after', false);

  const venvRow = document.createElement('div');
  Object.assign(venvRow.style, { display: 'flex', alignItems: 'center', gap: '8px' });
  venvRow.appendChild(sourceVenv.row);
  const venvHint = document.createElement('span');
  venvHint.textContent = 'name:';
  Object.assign(venvHint.style, { fontSize: '12px', color: '#666' });
  venvRow.appendChild(venvHint);
  venvRow.appendChild(venvInput);

  // Commands area
  const commandsTitle = document.createElement('div');
  commandsTitle.textContent = 'What are the commands the terminal should run after the upper commands are executed?';
  Object.assign(commandsTitle.style, { fontSize: '12px', color: '#666' });

  const commandsList = document.createElement('div');
  Object.assign(commandsList.style, { minHeight: '80px', border: '1px solid #e6edf6', padding: '8px', borderRadius: '6px', display: 'flex', flexDirection: 'column', gap: '6px', background: '#fafcfe' });

  // Add command input row
  const addRow = document.createElement('div');
  Object.assign(addRow.style, { display: 'flex', gap: '8px' });

  const addInput = document.createElement('input');
  addInput.type = 'text';
  addInput.placeholder = 'Enter command... (e.g. npm run dev)';
  Object.assign(addInput.style, { padding: '8px', fontSize: '13px', borderRadius: '6px', border: '1px solid #d6dde6', flex: '1' });

  const addBtn = document.createElement('button');
  addBtn.textContent = 'Add';
  Object.assign(addBtn.style, { padding: '8px', borderRadius: '6px', border: '1px solid #0b66ff', background: 'linear-gradient(180deg, #0b66ff, #075fe6)', color: '#fff', cursor: 'pointer' });

  addRow.appendChild(addInput);
  addRow.appendChild(addBtn);

  // Run file option (only for files)
  let runFile = null;
  let runFileContainer = null;
  if (isFile) {
    runFileContainer = document.createElement('div');
    Object.assign(runFileContainer.style, { 
      marginTop: '8px', 
      padding: '10px', 
      background: '#f0f9ff', 
      border: '1px solid #bae6fd', 
      borderRadius: '6px' 
    });
    
    runFile = createCheckboxRow(`Run file once above commands are executed`, true);
    
    const runFileHint = document.createElement('div');
    const detectedCommand = getFileRunCommand(resolvedFullPath);
    runFileHint.textContent = `Will execute: ${detectedCommand}`;
    Object.assign(runFileHint.style, { 
      fontSize: '11px', 
      color: '#0369a1', 
      marginTop: '4px',
      marginLeft: '24px',
      fontFamily: 'monospace'
    });
    
    runFileContainer.appendChild(runFile.row);
    runFileContainer.appendChild(runFileHint);
  }

  // Delete selected button
  const deleteBtn = document.createElement('button');
  deleteBtn.textContent = 'Delete selected';
  Object.assign(deleteBtn.style, { padding: '8px', borderRadius: '6px', border: '1px solid #d6dde6', background: '#f7f9fb', cursor: 'pointer' });

  // Status div
  const statusDiv = document.createElement('div');
  Object.assign(statusDiv.style, { minHeight: '18px', color: '#333', fontSize: '13px' });

  // assemble left side
  left.appendChild(title);
  left.appendChild(info);
  optsContainer.appendChild(setDir.row);
  optsContainer.appendChild(venvRow);
  optsContainer.appendChild(createVenv.row);
  left.appendChild(optsContainer);
  left.appendChild(commandsTitle);
  left.appendChild(commandsList);
  left.appendChild(addRow);
  if (runFileContainer) {
    left.appendChild(runFileContainer);
  }
  left.appendChild(deleteBtn);
  left.appendChild(statusDiv);

  // RIGHT: actions
  const right = document.createElement('div');
  Object.assign(right.style, {
    width: '240px',
    minWidth: '180px',
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
    boxSizing: 'border-box'
  });

  const rightTitle = document.createElement('div');
  rightTitle.textContent = 'Actions';
  Object.assign(rightTitle.style, { fontSize: '14px', fontWeight: '600' });

  const openBtn = document.createElement('button');
  openBtn.textContent = 'Open Terminal';
  Object.assign(openBtn.style, {
    padding: '10px',
    borderRadius: '6px',
    border: '1px solid #0b66ff',
    background: 'linear-gradient(180deg, #0b66ff, #075fe6)',
    color: '#fff',
    cursor: 'pointer'
  });

  const closeBtn = document.createElement('button');
  closeBtn.textContent = 'Close';
  Object.assign(closeBtn.style, {
    padding: '8px',
    borderRadius: '6px',
    border: '1px solid #d6dde6',
    background: '#f7f9fb',
    cursor: 'pointer'
  });

  const tip = document.createElement('div');
  if (isFile) {
    tip.textContent = 'Terminal will open in the file\'s directory. Enable "Run file" to execute it after other commands.';
  } else {
    tip.textContent = 'Terminal will open (macOS Terminal.app). You can add multiple commands which run after folder/venv steps.';
  }
  Object.assign(tip.style, { fontSize: '12px', color: '#666' });

  const shortcutHint = document.createElement('div');
  shortcutHint.textContent = 'Tip: Press Enter to open terminal';
  Object.assign(shortcutHint.style, { fontSize: '11px', color: '#9ca3af', marginTop: '8px' });

  right.appendChild(rightTitle);
  right.appendChild(openBtn);
  right.appendChild(closeBtn);
  right.appendChild(tip);
  right.appendChild(shortcutHint);

  card.appendChild(left);
  card.appendChild(right);
  overlay.appendChild(card);
  document.body.appendChild(overlay);

  // Selection + commands model
  const commands = [];
  const selected = new Set();

  function renderCommands() {
    commandsList.innerHTML = '';
    if (commands.length === 0) {
      const empty = document.createElement('div');
      empty.textContent = 'No commands added';
      Object.assign(empty.style, { color: '#999', fontSize: '13px' });
      commandsList.appendChild(empty);
      return;
    }
    for (let i = 0; i < commands.length; i++) {
      const cmd = commands[i];
      const item = document.createElement('div');
      item.textContent = cmd;
      Object.assign(item.style, {
        padding: '8px',
        borderRadius: '6px',
        background: selected.has(i) ? '#e8f0ff' : '#fff',
        border: '1px solid #e6edf6',
        fontSize: '13px',
        cursor: 'pointer'
      });
      item.dataset.index = String(i);
      item.addEventListener('click', () => {
        const idx = Number(item.dataset.index);
        if (selected.has(idx)) selected.delete(idx);
        else selected.add(idx);
        renderCommands();
      });
      commandsList.appendChild(item);
    }
  }

  addBtn.addEventListener('click', () => {
    const txt = addInput.value.trim();
    if (!txt) {
      statusDiv.textContent = 'Cannot add empty command';
      return;
    }
    commands.push(txt);
    addInput.value = '';
    selected.clear();
    renderCommands();
    statusDiv.textContent = `Added command (${commands.length})`;
  });

  addInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.stopPropagation(); // Prevent triggering the global Enter handler
      addBtn.click();
    }
  });

  deleteBtn.addEventListener('click', () => {
    if (selected.size === 0) {
      statusDiv.textContent = 'No command selected';
      return;
    }
    const toRemove = Array.from(selected).sort((a, b) => b - a);
    for (const idx of toRemove) {
      if (idx >= 0 && idx < commands.length) commands.splice(idx, 1);
    }
    selected.clear();
    renderCommands();
    statusDiv.textContent = `Removed ${toRemove.length} command(s)`;
  });

  // Update venv input enabling
  venvInput.disabled = !sourceVenv.cb.checked;
  sourceVenv.cb.addEventListener('change', () => {
    venvInput.disabled = !sourceVenv.cb.checked;
  });

  // Build the shell script that will be executed in Terminal (joined using "&&")
  function buildShellScript(opts) {
    const pieces = [];
    const cwd = opts.cwd || '';

    if (opts.setToFolder && cwd) {
      pieces.push(`cd ${shSingleQuote(cwd)}`);
    }

    const venvName = String(opts.venvName || '.venv');

    if (opts.createVenv) {
      pieces.push(`python3 -m venv ${shSingleQuote(venvName)}`);
    }

    if (opts.sourceVenv) {
      const activatePath = `${venvName}/bin/activate`;
      pieces.push(`if [ -f ${shSingleQuote(activatePath)} ]; then source ${shSingleQuote(activatePath)}; else echo "No venv found at ${venvName}"; fi`);
    }

    for (const c of opts.commands || []) {
      pieces.push(c);
    }

    // Add run file command if enabled
    if (opts.runFile && opts.fileName) {
      const runCommand = getFileRunCommand(opts.filePath);
      pieces.push(runCommand);
    }

    return pieces.join(' && ');
  }

  // Helper: fallback open using osascript (AppleScript)
  async function fallbackOpen(script, statusEl, overlayEl) {
    try {
      const args = [
        '-e', 'on run argv',
        '-e', 'set theScript to item 1 of argv',
        '-e', 'tell application "Terminal"',
        '-e', 'activate',
        '-e', 'set newWindow to do script ""',
        '-e', 'delay 0.12',
        '-e', 'do script theScript in newWindow',
        '-e', 'end tell',
        '-e', 'end run',
        script
      ];

      execFile('/usr/bin/osascript', args, (err, stdout, stderr) => {
        if (err) {
          console.error('Fallback osascript failed:', err, stderr);
          statusEl.textContent = 'Failed to open terminal (fallback).';
        } else {
          statusEl.textContent = 'Opened terminal (fallback).';
          setTimeout(() => {
            if (document.body.contains(overlayEl)) document.body.removeChild(overlayEl);
          }, 1200);
        }
      });
    } catch (err) {
      console.error('Fallback open failed:', err);
      statusEl.textContent = 'Failed to open terminal (fallback).';
    }
  }

  // Function to trigger open terminal
  async function triggerOpenTerminal() {
    statusDiv.textContent = 'Opening terminal...';
    const opts = {
      cwd: path.resolve(resolvedBaseFolder),
      setToFolder: !!setDir.cb.checked,
      sourceVenv: !!sourceVenv.cb.checked,
      venvName: (venvInput.value || '.venv').trim() || '.venv',
      createVenv: !!createVenv.cb.checked,
      commands: commands.slice(),
      runFile: isFile && runFile && runFile.cb.checked,
      fileName: fileName,
      filePath: resolvedFullPath
    };

    const script = buildShellScript(opts);
    const binPath = path.join(projectRoot, 'bin', 'open_terminal');

    try {
      await fs.access(binPath);
      execFile(binPath, [script], (err) => {
        if (err) {
          console.error('open_terminal helper failed:', err);
          statusDiv.textContent = 'Failed to open terminal via helper. Falling back...';
          fallbackOpen(script, statusDiv, overlay);
        } else {
          statusDiv.textContent = 'Opened terminal';
          setTimeout(() => { if (document.body.contains(overlay)) document.body.removeChild(overlay); }, 600);
        }
      });
    } catch (err) {
      fallbackOpen(script, statusDiv, overlay);
    }
  }

  // open terminal action
  openBtn.addEventListener('click', async (e) => {
    e.stopPropagation();
    await triggerOpenTerminal();
  });

  closeBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    if (document.body.contains(overlay)) document.body.removeChild(overlay);
  });

  // Clicking outside card closes overlay
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) {
      if (document.body.contains(overlay)) document.body.removeChild(overlay);
    }
  });
  card.addEventListener('click', (e) => e.stopPropagation());

  // Handle keyboard events
  function onKey(e) {
    if (e.key === 'Escape') {
      if (document.body.contains(overlay)) document.body.removeChild(overlay);
      window.removeEventListener('keydown', onKey);
    } else if (e.key === 'Enter') {
      // Check if an input field is focused
      const activeEl = document.activeElement;
      const isInputFocused = activeEl && (
        activeEl.tagName === 'INPUT' || 
        activeEl.tagName === 'TEXTAREA' ||
        activeEl.isContentEditable
      );
      
      // If no input is focused, trigger open terminal
      if (!isInputFocused) {
        e.preventDefault();
        e.stopPropagation();
        triggerOpenTerminal();
        window.removeEventListener('keydown', onKey);
      }
    }
  }
  window.addEventListener('keydown', onKey);

  // initial render
  renderCommands();
  
  // Focus the open button so Enter works immediately
  openBtn.focus();
}

module.exports = { openTerminal };
