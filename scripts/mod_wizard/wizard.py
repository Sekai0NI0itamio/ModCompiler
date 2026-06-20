"""
mod_wizard/wizard.py — Step functions for the mod creation wizard.

Implements the clipboard-driven flow:
  1. Ask mod name
  2. Ask mod description
  3. Generate prompt → auto-set clipboard → user pastes to AI
  4. User copies AI response to clipboard → script auto-reads it → restores original
  5. Parse response, run diagnosis, show issues
  6. If clean → ask to compile → if fail → offer fix prompt → loop
"""

import re
import select
import sys
from pathlib import Path

from . import clipboard, display, prompt_composer
from .response_parser import parse as parse_response
from .diagnosis import diagnose, summarize_for_fix_prompt
from .build_engine import build as build_mod

ROOT = Path(__file__).resolve().parents[2]
PROMPTS_DIR = ROOT / "prompts"


def _show_summary(summary: str) -> None:
    if not summary:
        return
    print()
    display.header("AI Summary", step="Info")
    for line in summary.splitlines():
        print(f"  {line}")
    print()


# ── User input helpers ──────────────────────────────────────────────────────

def _ask(prompt: str) -> str:
    try:
        return input(f"  > {prompt}").strip()
    except (EOFError, KeyboardInterrupt):
        display.goodbye()


def _confirm(prompt: str, default_yes: bool = True) -> bool:
    default = "Y/n" if default_yes else "y/N"
    while True:
        ans = _ask(f"{prompt} ({default}): ").lower()
        if not ans:
            return default_yes
        if ans in ("yes", "y"):
            return True
        if ans in ("no", "n"):
            return False


def _read_multiline() -> str | None:
    lines = []
    try:
        while True:
            line = input()
            if not line.strip():
                break
            lines.append(line)
    except EOFError:
        pass
    return "\n".join(lines) if lines else None


def _wait_for_enter(msg: str = "Press Enter when ready...") -> None:
    """Wait for the user to press Enter, handling Ctrl+C gracefully."""
    try:
        input(f"  > {msg}")
    except (EOFError, KeyboardInterrupt):
        display.goodbye()


# ── Step functions ──────────────────────────────────────────────────────────

def step1_get_name() -> str:
    """Ask for the mod name."""
    display.header("What is the preferred name of your mod?", step="Step 1")
    print()
    print("  Examples: 'Super Jump', 'Vein Miner', 'Auto Fish'")
    print()
    while True:
        name = _ask("Mod name: ")
        if name:
            display.ok(f"Name: \"{name}\"")
            return name
        display.warn("Please enter a name.")


def step2_get_description() -> str:
    """Ask for the mod description, with clipboard-paste support."""
    display.header("Describe your mod — what does it do?", step="Step 2")
    print()
    print("  ┌─────────────────────────────────────────────────────┐")
    print("  │  • Type 'paste' → read clipboard automatically      │")
    print("  │    (no Cmd+V — the script reads it for you)         │")
    print("  │  • Type your description, press Enter twice to end  │")
    print("  │  • Press Ctrl+D when done                           │")
    print("  └─────────────────────────────────────────────────────┘")
    print()

    while True:
        first = _ask("Description: ")

        if first.lower() == "paste":
            print()
            print("  Reading clipboard...")
            clip = clipboard.read()
            if not clip or not clip.strip():
                display.warn("Clipboard is empty. Please type manually.")
                continue
            display.ok(f"{len(clip)} characters read")
            print()
            print("  ── Preview ──")
            for line in clipboard.preview(clip).splitlines()[:15]:
                print(f"  │ {line}")
            print("  ─────────────")
            print()
            if _confirm("Use this clipboard content?"):
                display.ok(f"Description: {len(clip)} chars (clipboard)")
                return clip
            else:
                print("  Please type your description manually:")
                print()
                continue

        elif first:
            lines = [first]
            extra = _read_multiline()
            if extra:
                lines.append(extra)
            desc = "\n".join(lines).strip()
            if desc:
                display.ok(f"Description: {len(desc)} characters")
                return desc
            display.warn("Description cannot be empty.")
            print()


def step3_generate_and_set_clipboard(name: str, description: str) -> Path:
    """Generate the AI prompt, save to file, and SET the system clipboard to it.

    Returns the path to the saved prompt file.
    """
    display.header("Generating AI prompt...", step="Step 3")

    prompt = prompt_composer.build(name, description)
    safe_name = name.lower().replace(" ", "_").replace("'", "").replace("?", "")
    prompt_path = PROMPTS_DIR / f"{safe_name}_prompt.txt"
    PROMPTS_DIR.mkdir(parents=True, exist_ok=True)
    prompt_path.write_text(prompt, encoding="utf-8")

    display.ok(f"Prompt saved: {prompt_path}")
    display.info(f"Size: {len(prompt)} chars, {len(prompt.splitlines())} lines")

    # Set clipboard
    print()
    display.info("Setting clipboard to prompt text...")
    if clipboard.set(prompt):
        display.ok("Clipboard set — the prompt is now in your clipboard")
    else:
        display.warn("Could not set clipboard automatically")
        display.info(f"Please open the file manually: {prompt_path}")

    return prompt_path


def step4_get_response_from_clipboard(prompt_path: Path) -> str | None:
    """Tell user to paste to AI, then read their response from clipboard.

    Saves the original clipboard before the prompt was set,
    and restores it after reading the response.

    Returns the AI response text, or None if skipped.
    """
    display.header("Your task: Get AI response", step="Step 4")

    print()
    print(f"  ┌{'─' * 54}┐")
    print(f"  │ {'YOUR TASK':^54} │")
    print(f"  ├{'─' * 54}┤")
    print(f"  │ {'1. The prompt is ALREADY in your clipboard':<54} │")
    _short_path = str(prompt_path)[:48]
    print("  │    (or open: " + _short_path + ")")
    print(f"  │ {'':<54} │")
    print(f"  │ {'2. Paste (Cmd+V) to your AI assistant':<54} │")
    print(f"  │ {'3. Wait for the AI to generate code':<54} │")
    print(f"  │ {'4. Copy the AI FULL response (Cmd+A, Cmd+C)':<54} │")
    print(f"  │ {'5. Return here — do NOT paste anything':<54} │")
    print(f"  └{'─' * 54}┘")
    print()

    _wait_for_enter("Press Enter when you've copied the AI response to clipboard...")

    print()
    display.info("Reading your clipboard (this is the AI response)...")
    response = clipboard.read()

    if response is None or not response.strip():
        display.warn("Clipboard is empty — did you copy the AI response?")
        if _confirm("Try again?"):
            return step4_get_response_from_clipboard(prompt_path)
        return None

    # Restore original clipboard
    print()
    display.info("Restoring your original clipboard content...")
    if clipboard.restore():
        display.ok("Original clipboard restored")
    else:
        display.info("(no backup to restore)")

    display.ok(f"Response received: {len(response)} characters")

    # Auto-save the response
    safe_name = prompt_path.stem.replace("_prompt", "")
    response_path = PROMPTS_DIR / f"{safe_name}_response.txt"
    response_path.write_text(response, encoding="utf-8")
    display.info(f"Response saved: {response_path}")

    return response


def step5_diagnose_and_decide(
    name: str, description: str, response_text: str
) -> tuple[bool, dict | None]:
    """Parse the AI response, run diagnosis, show results.

    Returns (should_compile: bool, result_dict | None)
    where result_dict = {"files": ..., "metadata": ...}
    """
    display.header("Diagnosing extracted files...", step="Step 5")

    result = parse_response(response_text)
    files = result["files"]
    metadata = result["metadata"]
    summary = result.get("summary", "")

    _show_summary(summary)

    if not files:
        display.err("No files found in the AI response.")
        display.info("The response may not use the ---FILE: path--- format.")
        print()
        if _confirm("Create a fix prompt to send back to the AI?"):
            return False, None
        return False, None

    display.ok(f"Found {len(files)} files:")
    for fp in sorted(files):
        print(f"      {fp}")
    print()

    # Run diagnosis
    issues = diagnose(files, metadata)
    display.header("Diagnosis results", step="Diagnosis")
    for issue in issues:
        if issue.startswith("✓"):
            display.ok(issue)
        else:
            display.warn(issue)
    print()

    # Check if there are structural issues (excluding the "all clear" message)
    real_issues = [i for i in issues if not i.startswith("✓")]
    if real_issues:
        display.warn(f"Found {len(real_issues)} potential issue(s)")
        if _confirm("Send a fix prompt back to the AI to resolve these?"):
            return False, None
        print()

    # Ask about compilation
    print()
    if _confirm("Compile the mod now?"):
        return True, result
    else:
        display.info("Compilation skipped. Process complete.")
        return False, result

    return False, result


def step6_compile_and_fix_loop(
    name: str, description: str, result: dict, max_cycles: int = 5
) -> bool:
    """Compile the mod. If it fails, offer a fix-prompt cycle.

    Returns True if compilation succeeded, False if ultimately failed/skipped.
    """
    files = result["files"]
    metadata = result["metadata"]

    for cycle in range(1, max_cycles + 1):
        if cycle > 1:
            display.header(f"Fix cycle {cycle}/{max_cycles}", step="Recompile")

        success, build_log = build_mod(files, metadata)

        if success:
            return True

        # Build failed
        print()
        display.header("Build failed", step="Result")
        print()

        if cycle >= max_cycles:
            display.err(f"Max fix cycles ({max_cycles}) reached. Giving up.")
            return False

        # Ask if user wants to create a fix prompt
        print()
        if not _confirm("Create a fix prompt to send back to the AI (same chat)?"):
            display.info("Fix skipped. Process complete.")
            return False

        # Extract error summary
        # Extract errors with surrounding context (2 lines before/after)
        log_lines = build_log.splitlines()
        error_lines = []
        for i, line in enumerate(log_lines):
            if any(kw in line.lower() for kw in (
                "error:", "cannot find symbol", "does not exist",
                "BUILD FAILED", "compilation error",
            )):
                start = max(0, i - 2)
                end = min(len(log_lines), i + 3)
                for j in range(start, end):
                    if j not in {e[0] for e in error_lines}:
                        error_lines.append((j, log_lines[j]))
        error_lines.sort()
        error_summary = "\n".join(l for _, l in error_lines[:40]) if error_lines else "Unknown build error"

        # Build fix prompt
        fix_prompt = prompt_composer.build_fix_prompt(
            name, description, build_log, error_summary,
        )

        # Save fix prompt to file
        fix_path = PROMPTS_DIR / f"fix_cycle{cycle}_{name.lower().replace(' ', '_')}_prompt.txt"
        fix_path.write_text(fix_prompt, encoding="utf-8")
        display.info(f"Fix prompt saved: {fix_path}")

        # Set clipboard to fix prompt
        print()
        display.info("Setting clipboard to fix prompt...")
        clipboard.backup()  # Explicit save before setting
        if clipboard.set(fix_prompt):
            display.ok("Fix prompt is now in your clipboard")
        else:
            display.warn("Could not set clipboard")
            display.info(f"Open the file manually: {fix_path}")

        # User sends to AI, copies response
        print()
        print(f"  ┌{'─' * 54}┐")
        print(f"  │ {'SEND TO AI (same chat session)':^54} │")
        print(f"  ├{'─' * 54}┤")
        print(f"  │ {'1. Paste (Cmd+V) to the SAME AI chat':<54} │")
        print(f"  │ {'2. AI will return corrected files':<54} │")
        print(f"  │ {'3. Copy AI response (Cmd+A, Cmd+C)':<54} │")
        print(f"  │ {'4. Return here — press Enter':<54} │")
        print(f"  └{'─' * 54}┘")
        print()

        _wait_for_enter("Press Enter when you've copied the AI fix response...")

        # Read fix response from clipboard
        print()
        display.info("Reading clipboard for AI fix response...")
        fix_response = clipboard.read()

        # Restore original
        if clipboard.restore():
            display.ok("Original clipboard restored")

        if not fix_response or not fix_response.strip():
            display.warn("Clipboard is empty — skipping this fix cycle.")
            continue

        display.ok(f"Fix response received: {len(fix_response)} characters")

        # Save fix response
        fix_resp_path = PROMPTS_DIR / f"fix_cycle{cycle}_{name.lower().replace(' ', '_')}_response.txt"
        fix_resp_path.write_text(fix_response, encoding="utf-8")

        # Parse the fix response
        display.info("Parsing fix response...")
        fix_result = parse_response(fix_response)
        fix_files = fix_result["files"]
        fix_metadata = fix_result["metadata"]

        _show_summary(fix_result.get("summary", ""))

        if not fix_files:
            display.err("No files found in the fix response.")
            continue

        display.ok(f"AI returned {len(fix_files)} corrected file(s)")

        # Merge fix files into existing files (replace matching paths, add new ones)
        for fp, ct in fix_files.items():
            files[fp] = ct

        # Merge metadata
        for k, v in fix_metadata.items():
            if v:
                metadata[k] = v

    return False


def step7_done_ask_continue() -> bool:
    """Ask if the user wants to create another mod."""
    display.header("All tasks complete!", step="Done")
    return _confirm("Create another mod?")

# ═══════════════════════════════════════════════════════════════════════════════
#  AI-mode steps (uses local freedeepseek browser automation server)
# ═══════════════════════════════════════════════════════════════════════════════

_ai = None

def _get_ai():
    global _ai
    if _ai is None:
        from . import ai_client as _ai_mod
        _ai = _ai_mod
    return _ai


def ai_check_server() -> bool:
    """Check if the local AI server is reachable."""
    return _get_ai().check_server()


def ai_step3_send_prompt(name: str, description: str) -> tuple[str | None, str | None]:
    """Generate prompt and send it to the AI server.

    Returns (response_text, resume_url). (None, None) on failure.
    """
    ai = _get_ai()
    display.header("Sending prompt to AI server...", step="Step 3")

    prompt = prompt_composer.build(name, description)

    safe_name = name.lower().replace(" ", "_").replace("'", "").replace("?", "")
    prompt_path = PROMPTS_DIR / f"{safe_name}_prompt.txt"
    PROMPTS_DIR.mkdir(parents=True, exist_ok=True)
    prompt_path.write_text(prompt, encoding="utf-8")
    display.info(f"Prompt saved: {prompt_path}")
    display.info(f"Size: {len(prompt)} chars")

    print()
    display.info("Sending to DeepSeek Instant (direct + DeepThink + Web Search)...")
    display.info("Waiting for response (30-120 seconds)...")

    result = ai.send_prompt(
        prompt,
        model="Instant",
        thinking=True,
        web_search=True,
        response_mode="direct",
    )

    if result["error"]:
        display.err(f"AI server error: {result['message']}")
        return None, None

    response = result["response"]
    resume_url = result.get("resume_url")

    if not response:
        display.err("AI returned empty response")
        return None, None

    display.ok(f"Response received: {len(response)} characters")
    if resume_url:
        display.info(f"Chat URL: {resume_url}")

    response_path = PROMPTS_DIR / f"{safe_name}_response.txt"
    response_path.write_text(response, encoding="utf-8")
    display.info(f"Response saved: {response_path}")

    return response, resume_url


def ai_compile_and_fix_loop(
    name: str,
    description: str,
    result: dict,
    resume_url: str | None = None,
    max_cycles: int = 5,
) -> tuple[bool, str | None]:
    """Compile + auto-fix via AI server using the same chat (resumed by URL).

    Returns (success, updated_resume_url).
    """
    ai = _get_ai()
    files = result["files"]
    metadata = result["metadata"]
    current_resume_url = resume_url

    for cycle in range(1, max_cycles + 1):
        if cycle > 1:
            display.header(f"Fix cycle {cycle}/{max_cycles}", step="Recompile")

        success, build_log = build_mod(files, metadata)

        if success:
            return True, current_resume_url

        print()
        display.header("Build failed", step="Result")
        print()

        if cycle >= max_cycles:
            display.err(f"Max fix cycles ({max_cycles}) reached.")
            return False, current_resume_url

        if not _confirm("Send fix prompt to AI (same chat)?"):
            display.info("Fix skipped.")
            return False, current_resume_url

        log_lines = build_log.splitlines()
        error_lines = []
        for i, line in enumerate(log_lines):
            if any(kw in line.lower() for kw in (
                "error:", "cannot find symbol", "does not exist",
                "BUILD FAILED", "compilation error",
            )):
                start = max(0, i - 2)
                end = min(len(log_lines), i + 3)
                for j in range(start, end):
                    if j not in {e[0] for e in error_lines}:
                        error_lines.append((j, log_lines[j]))
        error_lines.sort()
        error_summary = "\n".join(l for _, l in error_lines[:40]) if error_lines else "Unknown build error"

        fix_prompt = prompt_composer.build_fix_prompt(
            name, description, build_log, error_summary,
        )

        fix_path = PROMPTS_DIR / f"fix_cycle{cycle}_{name.lower().replace(' ', '_')}_prompt.txt"
        fix_path.write_text(fix_prompt, encoding="utf-8")
        display.info(f"Fix prompt saved: {fix_path}")

        display.info("Sending fix prompt to AI (same chat)...")
        fix_result = ai.send_prompt(
            fix_prompt,
            model="Instant",
            thinking=True,
            web_search=True,
            resume_url=current_resume_url,
            response_mode="direct",
        )

        if fix_result["error"]:
            display.err(f"AI server error: {fix_result['message']}")
            if not _confirm("Try again?"):
                return False, current_resume_url
            continue

        fix_response = fix_result["response"]
        if not fix_response:
            display.err("AI returned empty fix response")
            continue

        new_resume_url = fix_result.get("resume_url")
        if new_resume_url:
            current_resume_url = new_resume_url

        display.ok(f"Fix response: {len(fix_response)} characters")

        fix_resp_path = PROMPTS_DIR / f"fix_cycle{cycle}_{name.lower().replace(' ', '_')}_response.txt"
        fix_resp_path.write_text(fix_response, encoding="utf-8")

        display.info("Parsing fix response...")
        fix_parsed = parse_response(fix_response)
        fix_files = fix_parsed["files"]
        fix_metadata = fix_parsed["metadata"]

        _show_summary(fix_parsed.get("summary", ""))

        if not fix_files:
            display.err("No files found in fix response.")
            continue

        display.ok(f"AI returned {len(fix_files)} corrected file(s)")

        for fp, ct in fix_files.items():
            files[fp] = ct
        for k, v in fix_metadata.items():
            if v:
                metadata[k] = v

    return False, current_resume_url


# ═══════════════════════════════════════════════════════════════════════════════
#  Refinement cycle — test mod, request changes, recompile
# ═══════════════════════════════════════════════════════════════════════════════

def step_refinement_cycle(
    name: str,
    description: str,
    result: dict,
    resume_url: str | None = None,
) -> tuple[bool, str | None]:
    """Post-compilation refinement loop.

    Asks the user to test the mod, then either confirm final or
    send refinement requests to the AI. Loops until user confirms.

    Returns (confirmed, updated_resume_url).
    """
    ai = _get_ai()
    files = result["files"]
    metadata = result["metadata"]
    current_resume_url = resume_url
    compiled_ok = True

    while True:
        print()
        if compiled_ok:
            display.header("Mod compiled successfully!", step="Test")
            print()
            print("  The mod jar is in ReadyMods/ — test it in Minecraft now.")
        else:
            display.header("Mod has build errors", step="Test")
            print()
            print("  The mod did NOT compile. You can request fixes or type 'done'")
            print("  to accept it as-is (it will not be functional).")
        print()
        print("  Options:")
        print("    1. Type 'done'  → confirm final, finish this mod")
        print("    2. Type changes → send refinement to AI, recompile, repeat")
        print()

        feedback = _ask("Changes (or 'done'): ")

        if feedback.lower() == "done":
            display.ok("Mod confirmed as final!")
            return True, current_resume_url

        if not feedback.strip():
            display.warn("Please enter changes or type 'done'.")
            continue

        display.info("Sending refinement request to AI (same chat)...")
        ref_prompt = prompt_composer.build_refinement_prompt(
            name, description, feedback,
        )

        ref_result = ai.send_prompt(
            ref_prompt,
            model="Instant",
            thinking=True,
            web_search=True,
            resume_url=current_resume_url,
            response_mode="direct",
        )

        if ref_result["error"]:
            display.err(f"AI error: {ref_result['message']}")
            if _confirm("Try again?"):
                continue
            return False, current_resume_url

        ref_response = ref_result["response"]
        if not ref_response:
            display.err("AI returned empty response")
            continue

        new_resume_url = ref_result.get("resume_url")
        if new_resume_url:
            current_resume_url = new_resume_url

        display.ok(f"Refinement response: {len(ref_response)} characters")

        ref_parsed = parse_response(ref_response)
        ref_files = ref_parsed["files"]
        ref_meta = ref_parsed["metadata"]

        _show_summary(ref_parsed.get("summary", ""))

        if not ref_files:
            display.err("No files found in refinement response")
            if _confirm("Try again?"):
                continue
            return False, current_resume_url

        display.ok(f"AI returned {len(ref_files)} modified file(s)")
        for fp in sorted(ref_files):
            print(f"      {fp}")

        for fp, ct in ref_files.items():
            files[fp] = ct
        for k, v in ref_meta.items():
            if v:
                metadata[k] = v

        display.info("Recompiling with refinements...")
        success, build_log = build_mod(files, metadata)

        if not success:
            compiled_ok = False
            display.err("Recompile failed after refinements")
            for line in build_log.splitlines():
                lower = line.lower()
                if any(kw in lower for kw in (
                    "error:", "cannot find symbol", "BUILD FAILED"
                )):
                    print(f"    {line.strip()}")
            if _confirm("Send fix prompt to AI?"):
                fix_prompt = prompt_composer.build_fix_prompt(
                    name, description, build_log,
                    "\n".join(l for l in build_log.splitlines()
                              if "error:" in l.lower())[:20],
                )
                fix_result = ai.send_prompt(
                    fix_prompt, model="Instant", thinking=True,
                    web_search=True,
                    resume_url=current_resume_url,
                    response_mode="direct",
                )
                if fix_result["response"]:
                    fix_parsed = parse_response(fix_result["response"])
                    _show_summary(fix_parsed.get("summary", ""))
                    for fp, ct in fix_parsed["files"].items():
                        files[fp] = ct
                    new_url = fix_result.get("resume_url")
                    if new_url:
                        current_resume_url = new_url
                    success2, _ = build_mod(files, metadata)
                    if success2:
                        compiled_ok = True
                        display.ok("Fixed and recompiled!")
                        continue
            display.warn("Recompile failed — you can try again or confirm as-is.")
        else:
            compiled_ok = True
            display.ok("Recompile successful with refinements!")
