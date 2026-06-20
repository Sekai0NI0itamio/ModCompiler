"""
mod_wizard/main.py — Main orchestration loop and CLI entry point.

Usage:
    python3 scripts/mod_wizard/main.py                        # AI mode (default)
    python3 scripts/mod_wizard/main.py --runtype manual       # Manual clipboard mode
    python3 scripts/mod_wizard/main.py --runtype ai           # AI mode (explicit)
    python3 scripts/mod_wizard/main.py --from-response file   # Quick build
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

if __name__ == "__main__" and __package__ is None:
    _ROOT = Path(__file__).resolve().parents[2]
    sys.path.insert(0, str(_ROOT / "scripts"))
    from mod_wizard import display, wizard, clipboard
    from mod_wizard import ai_client as _ai_client
    from mod_wizard.session import Session, STEP_LABELS
    from mod_wizard.response_parser import parse as _parse
    from mod_wizard.build_engine import build as _build
else:
    from . import display, wizard, clipboard
    from . import ai_client as _ai_client
    from .session import Session, STEP_LABELS
    from .response_parser import parse as _parse
    from .build_engine import build as _build


# ═══════════════════════════════════════════════════════════════════════════════
#  Startup menu — new or resume
# ═══════════════════════════════════════════════════════════════════════════════

def _startup_menu() -> Session:
    """Show new/resume menu and return a Session."""
    sessions = Session.list_sessions()

    if not sessions:
        display.info("No saved sessions found. Starting new session.")
        return Session.create()

    print()
    print("  Options:")
    print("    1. Create new mod")
    print("    2. Resume saved session")
    print()

    choice = input("  > Choose (1/2): ").strip()

    if choice == "2":
        return _pick_session(sessions)

    return Session.create()


def _pick_session(sessions: list[dict]) -> Session:
    """Display session list and let user pick one to resume."""
    while True:
        print()
        display.header("Saved Sessions", step="Resume")
        print()
        print(f"  {'#':>3}  {'Mod Name':<22s}  {'Step':<26s}  {'Last Updated':<18s}")
        print(f"  {'---':>3}  {'-------':<22s}  {'----':<26s}  {'------------':<18s}")
        for i, s in enumerate(sessions, 1):
            name = s.get("mod_name") or "(untitled)"
            step = STEP_LABELS.get(s.get("current_step", "start"), s.get("current_step", ""))
            updated = s.get("updated_at", "")[:16].replace("T", " ")
            print(f"  {i:>3}  {name:<22s}  {step:<26s}  {updated:<18s}")
        print()
        print("  Enter number to resume, 'd#' to delete (e.g. d2), or Enter for new.")
        print()

        raw = input("  > Session #: ").strip()

        if not raw:
            return Session.create()

        if raw.startswith("d") and raw[1:].isdigit():
            idx = int(raw[1:])
            if 1 <= idx <= len(sessions):
                Session.delete(sessions[idx - 1]["id"])
                display.ok(f"Deleted session #{idx}")
                sessions = Session.list_sessions()
                if not sessions:
                    display.info("No more saved sessions. Starting new.")
                    return Session.create()
                continue
            display.warn("Invalid number.")
            continue

        if raw.isdigit():
            idx = int(raw)
            if 1 <= idx <= len(sessions):
                session_id = sessions[idx - 1]["id"]
                try:
                    session = Session.load(session_id)
                except Exception as e:
                    display.err(f"Failed to load session: {e}")
                    continue
                session.replay_history()
                return session
            display.warn("Invalid number.")
            continue

        return Session.create()


# ═══════════════════════════════════════════════════════════════════════════════
#  AI mode (default) — fully automated via local freedeepseek server
# ═══════════════════════════════════════════════════════════════════════════════

def run_ai_mode() -> None:
    """Run the wizard in AI mode — sends prompts to local freedeepseek server."""
    display.banner()

    display.info("Checking AI server connection...")
    if not wizard.ai_check_server():
        display.err("Cannot reach AI server at http://localhost:8129")
        display.info("Make sure the freedeepseek server is running.")
        display.info("Falling back to manual mode.")
        run_manual_mode()
        return

    display.ok("AI server reachable — using DeepSeek Instant (DeepThink + Web Search)")

    session = _startup_menu()
    session.install_tee()

    mods_built = 0
    mods_failed = 0

    while True:
        step = session.current_step

        if step == "complete":
            display.ok("This session is already complete.")
            name = session.mod_name or "unknown"
            if wizard._confirm("Publish this mod to Modrinth?"):
                _publish_to_modrinth(name)
            mods_built += 1
            if not wizard.step7_done_ask_continue():
                break
            session.uninstall_tee()
            session = Session.create()
            session.install_tee()
            continue

        # ── Step 1: Get name ──
        if step == "start":
            name = wizard.step1_get_name()
            session.update("step1_name", mod_name=name)
        name = session.mod_name

        # ── Step 2: Get description ──
        if step in ("start", "step1_name"):
            description = wizard.step2_get_description()
            session.update("step2_desc", description=description)
        description = session.description

        # ── Step 3: Send prompt ──
        if step in ("start", "step1_name", "step2_desc"):
            response, resume_url = wizard.ai_step3_send_prompt(name, description)
            session.update("step3_sent", resume_url=resume_url, response=response)
            if not response:
                display.err("No response from AI.")
                mods_failed += 1
                if not wizard.step7_done_ask_continue():
                    break
                session.uninstall_tee()
                session = Session.create()
                session.install_tee()
                continue
        response = session.response
        resume_url = session.resume_url

        # ── Step 5: Diagnose ──
        if step in ("start", "step1_name", "step2_desc", "step3_sent"):
            should_compile, result = wizard.step5_diagnose_and_decide(
                name, description, response
            )
            if not result:
                if wizard._confirm("Send a fix prompt to the AI?"):
                    display.info("Sending diagnosis fix prompt...")
                    fix_text, resume_url = _send_ai_fix_prompt(
                        "Your previous response had structural issues. "
                        "Please return ALL files using ---FILE: path--- format, "
                        "include a main class with @Mod, mcmod.info, "
                        "and the ---METADATA--- block with group + archivesBaseName.",
                        resume_url=resume_url,
                    )
                    if fix_text:
                        fix_parsed = _parse(fix_text)
                        wizard._show_summary(fix_parsed.get("summary", ""))
                        if fix_parsed["files"]:
                            result = fix_parsed
                            should_compile = True
            session.update("step5_diagnose", result=result,
                           should_compile=should_compile, resume_url=resume_url)
        should_compile = session.should_compile
        result = session.result
        resume_url = session.resume_url

        # ── Step 6: Compile ──
        if step in ("start", "step1_name", "step2_desc", "step3_sent", "step5_diagnose"):
            compiled_ok = False
            if should_compile and result:
                compiled_ok, resume_url = wizard.ai_compile_and_fix_loop(
                    name, description, result, resume_url=resume_url
                )
            elif result:
                display.info("Mod files extracted but not compiled.")
            session.update("step6_compile", compiled_ok=compiled_ok,
                           resume_url=resume_url, result=result)
        compiled_ok = session.compiled_ok
        resume_url = session.resume_url
        result = session.result

        if not compiled_ok:
            mods_failed += 1
            if not wizard.step7_done_ask_continue():
                break
            session.uninstall_tee()
            session = Session.create()
            session.install_tee()
            continue

        # ── Step 7: Refinement ──
        confirmed, resume_url = wizard.step_refinement_cycle(
            name, description, result, resume_url=resume_url
        )

        if confirmed:
            session.update("complete", confirmed=True, resume_url=resume_url,
                           result=result, compiled_ok=compiled_ok)
            if wizard._confirm("Publish this mod to Modrinth?"):
                _publish_to_modrinth(name)
            mods_built += 1
        else:
            session.update("step7_refine", confirmed=False, resume_url=resume_url,
                           result=result, compiled_ok=compiled_ok)
            mods_failed += 1

        if not wizard.step7_done_ask_continue():
            break
        session.uninstall_tee()
        session = Session.create()
        session.install_tee()

    session.uninstall_tee()
    display.summary(mods_built, mods_failed)


def _send_ai_fix_prompt(
    prompt: str, resume_url: str | None = None
) -> tuple[str | None, str | None]:
    """Send a fix prompt to the AI server.

    Returns (response_text, updated_resume_url).
    """
    display.info("Sending to AI...")
    result = _ai_client.send_prompt(
        prompt, model="Instant", thinking=True,
        web_search=True,
        resume_url=resume_url, response_mode="direct",
    )
    if result["error"]:
        display.err(f"AI error: {result['message']}")
        return None, resume_url
    new_url = result.get("resume_url")
    if new_url:
        resume_url = new_url
    return result["response"], resume_url


# ═══════════════════════════════════════════════════════════════════════════════
#  Manual mode — clipboard-driven
# ═══════════════════════════════════════════════════════════════════════════════

def run_manual_mode() -> None:
    """Run the wizard in manual clipboard mode."""
    display.banner()

    mods_built = 0
    mods_failed = 0

    while True:
        name = wizard.step1_get_name()
        description = wizard.step2_get_description()

        prompt_path = wizard.step3_generate_and_set_clipboard(name, description)

        response = wizard.step4_get_response_from_clipboard(prompt_path)
        if not response:
            display.info("No response received — skipping.")
            if not wizard.step7_done_ask_continue():
                break
            continue

        should_compile, result = wizard.step5_diagnose_and_decide(
            name, description, response
        )

        if not result:
            if wizard._confirm("Send a fix prompt back to the AI?"):
                clipboard.backup()
                clipboard.set(
                    "Your previous response had issues. "
                    "Please return ALL files using ---FILE: path--- format, "
                    "include a main class with @Mod, mcmod.info, "
                    "and the ---METADATA--- block with group + archivesBaseName."
                )
                display.ok("Fix prompt in clipboard — paste to same AI chat")
                wizard._wait_for_enter(
                    "Press Enter when you have copied the AI fix response..."
                )
                fix_resp = clipboard.read()
                clipboard.restore()
                if fix_resp:
                    fix_result = _parse(fix_resp)
                    if fix_result["files"]:
                        result = fix_result
                        should_compile = True

        if should_compile and result:
            if wizard.step6_compile_and_fix_loop(name, description, result):
                mods_built += 1
            else:
                mods_failed += 1
        elif result:
            display.info("Mod files extracted but not compiled.")
            mods_failed += 1
        else:
            mods_failed += 1

        if not wizard.step7_done_ask_continue():
            break

    display.summary(mods_built, mods_failed)


# ═══════════════════════════════════════════════════════════════════════════════
#  Quick build (from existing response file)
# ═══════════════════════════════════════════════════════════════════════════════

def run_quick_build(response_path: Path) -> None:
    print()
    print(f"  Quick-build mode: {response_path}")
    text = response_path.read_text(encoding="utf-8")
    result = _parse(text)
    if not result["files"]:
        display.err("No files found in response.")
        sys.exit(1)
    display.ok(f"Found {len(result['files'])} files")
    success, log = _build(result["files"], result["metadata"])
    if not success:
        sys.exit(1)


# ═══════════════════════════════════════════════════════════════════════════════
#  Entry point
# ═══════════════════════════════════════════════════════════════════════════════

def main() -> None:
    import argparse
    parser = argparse.ArgumentParser(
        description="Continuous interactive wizard for Minecraft mod creation"
    )
    parser.add_argument(
        "--runtype",
        choices=["ai", "manual"],
        default="ai",
        help="Run mode: 'ai' uses local freedeepseek server (default), "
             "'manual' uses clipboard-driven flow",
    )
    parser.add_argument(
        "--from-response", "-r",
        help="Skip questions — build directly from an existing AI response file",
    )
    args = parser.parse_args()

    if args.from_response:
        rp = Path(args.from_response).expanduser().resolve()
        if not rp.exists():
            print(f"ERROR: File not found: {args.from_response}")
            sys.exit(1)
        run_quick_build(rp)
    elif args.runtype == "manual":
        run_manual_mode()
    else:
        run_ai_mode()


if __name__ == "__main__":
    main()

def _publish_to_modrinth(name: str) -> None:
    """Prepare and publish the mod to Modrinth.

    Copies the jar and source to ToBeUploaded/ then runs the
    auto_create_modrinth_draft_projects.py generate + create-drafts flow.
    """
    import shutil, subprocess

    slug = name.lower().replace(" ", "-").replace("'", "").replace("?", "")
    workspace = ROOT / "Mod Development" / "1.12.2-forge"
    ready_mods = workspace / "ReadyMods"
    to_be_uploaded = ROOT / "ToBeUploaded" / slug
    scripts_dir = ROOT / "scripts"

    jars = sorted(ready_mods.glob("*.jar"),
                  key=lambda p: p.stat().st_mtime, reverse=True)
    if not jars:
        display.err("No jar found in ReadyMods/")
        return

    jar = jars[0]
    to_be_uploaded.mkdir(parents=True, exist_ok=True)
    shutil.copy2(jar, to_be_uploaded / jar.name)

    src_dir = workspace / "src"
    dest_src = to_be_uploaded / f"{slug}-src"
    if dest_src.exists():
        shutil.rmtree(dest_src)
    shutil.copytree(src_dir, dest_src)

    display.ok(f"Prepared bundle: {to_be_uploaded}")

    display.info("Generating Modrinth draft bundle...")
    result = subprocess.run(
        ["python3", str(scripts_dir / "auto_create_modrinth_draft_projects.py"),
         "generate", "--only-bundle", slug, "--nolinks"],
        cwd=ROOT, capture_output=True, text=True, timeout=300,
    )
    if result.returncode != 0:
        display.warn(f"Generate completed with warnings:\n{result.stderr[-500:]}")
    else:
        display.ok("Draft bundle generated")

    bundle_dir = ROOT / "AutoCreateModrinthBundles" / slug
    verify_file = bundle_dir / "verify.txt"
    display.info(f"Review the generated bundle at: {bundle_dir}")
    display.info(f"Edit {verify_file} and add 'verified' on its own line when ready.")

    if wizard._confirm("Ready to publish to Modrinth?"):
        display.info("Publishing to Modrinth...")
        result2 = subprocess.run(
            ["python3", str(scripts_dir / "auto_create_modrinth_draft_projects.py"),
             "create-drafts", "--verified", "--only-bundle", slug, "--nolinks"],
            cwd=ROOT, capture_output=True, text=True, timeout=300,
        )
        if result2.returncode == 0:
            display.ok("Published to Modrinth!")
        else:
            display.warn(f"Publish may need attention:\n{result2.stderr[-500:]}")
