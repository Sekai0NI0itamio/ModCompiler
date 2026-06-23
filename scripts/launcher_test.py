#!/usr/bin/env python3
"""Launcher test for Minecraft mods — memory-safe, process-group aware.

Key design choices:
- Sets JAVA_TOOL_OPTIONS=-Xmx1G to cap ALL Java subprocesses
- Never uses subprocess.PIPE with Java/Minecraft (would buffer in Python memory)
- Redirects game output to temp files and polls via filesystem
- Kills the ENTIRE process group (not just the wrapper) when done
- Uses start_new_session=True + explicit process group kill for cleanup

Usage:
  python3 scripts/launcher_test.py \
      --jar-name my-mod.jar \
      --game-version 1.21 \
      --test-mc 1.21 \
      --loader fabric \
      --loader-regex 'fabric-loader.*' \
      --mcrt-jar fabric \
      --java-version 21 \
      --safe-key fabric-1.21 \
      --out-dir launcher-results
"""

import argparse
import json
import os
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

socket.setdefaulttimeout(120)

# === MEMORY SAFETY: Cap ALL Java subprocesses to 1GB default heap ===
# This prevents OOM killer from terminating our Python process
os.environ["JAVA_TOOL_OPTIONS"] = os.environ.get("JAVA_TOOL_OPTIONS", "") + " -Xmx1G -XX:MaxMetaspaceSize=256M"

# Game-specific memory limits (2G is safe for 7GB CI runners)
GAME_JVM_ARGS = "-Djava.awt.headless=true -Xmx2G -Xms512M -XX:MaxMetaspaceSize=512M"
# HMC wrapper memory limit (small)
HMC_JVM_ARGS = "-Xmx512M -XX:MaxMetaspaceSize=128M"

# === Load shared library from scripts/ ===
exec(compile(open("scripts/launcher_test_lib.py").read(),
             "scripts/launcher_test_lib.py", "exec"))
resolve_deps = resolve_dependencies  # noqa: F821
find_java_home_lib = find_java_home  # noqa: F821
patch_mc = patch_fabric_mc_version  # noqa: F821

HMC_JAR = "headlessmc-launcher-wrapper-2.9.0.jar"
FABRIC_INSTALLER_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.1/fabric-installer-1.1.1.jar"
FABRIC_INSTALLER_JAR = "fabric-installer-1.1.1.jar"
MCRT_VERSION = "4.4.0"
MCRT_REPO = "headlesshq/mc-runtime-test"
LAUNCH_TIMEOUT = 120  # seconds


def parse_args():
    p = argparse.ArgumentParser(description="Launcher test for mod jars")
    p.add_argument("--jar-name", required=True)
    p.add_argument("--jar-path", default=None)
    p.add_argument("--file-url", default=None)
    p.add_argument("--game-version", required=True)
    p.add_argument("--test-mc", required=True)
    p.add_argument("--loader", required=True)
    p.add_argument("--original-loader", default=None)
    p.add_argument("--loader-regex", required=True)
    p.add_argument("--mcrt-jar", required=True)
    p.add_argument("--java-version", default="17")
    p.add_argument("--safe-key", required=True)
    p.add_argument("--out-dir", default="launcher-results")
    p.add_argument("--version-number", default="")
    p.add_argument("--slug", default="")
    return p.parse_args()


def cleanup_processes():
    """Kill any game processes that might leak between runs.
    Never kill arbitrary java processes - they might be the CI runner's own processes!
    """
    for pat in ["headlessmc-launcher-wrapper", "HeadlessMC", "net.minecraft",
                "Xvfb-run", "Minecraft.*launcher", "neo.forge", "net.neoforged",
                "cpw.mods.bootstraplauncher", "net.minecraftforge"]:
        try:
            subprocess.run(
                ["pkill", "-9", "-f", pat],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL, timeout=5
            )
        except Exception:
            pass
    # Kill Xvfb (virtual display) processes specifically
    try:
        subprocess.run(
            ["pkill", "-9", "Xvfb"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL, timeout=5
        )
    except Exception:
        pass
    time.sleep(0.5)


def run_java(cmd_args, timeout=300, memory_limit="1G"):
    """Run a Java subprocess with explicit memory limits.

    Args:
        cmd_args: List of args AFTER 'java' (e.g., ["-jar", HMC_JAR, "--command", ...])
        timeout: Process timeout in seconds
        memory_limit: Override memory limit string (e.g., "512M")

    Returns:
        CompletedProcess
    """
    env = os.environ.copy()
    env["JAVA_TOOL_OPTIONS"] = f"-Xmx{memory_limit} -XX:MaxMetaspaceSize=256M"

    full_cmd = ["java"] + cmd_args
    return subprocess.run(
        full_cmd,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        timeout=timeout,
        env=env,
    )


def download_mcrt_jar(test_mc, mcrt_jar, github_token):
    mods_dir = Path("run/mods")
    mods_dir.mkdir(parents=True, exist_ok=True)
    pat = f"mc-runtime-test-{test_mc}-*-{mcrt_jar}-release.jar"
    existing = list(mods_dir.glob(pat))
    if existing:
        return existing[0].name

    url = f"https://api.github.com/repos/{MCRT_REPO}/releases/tags/{MCRT_VERSION}"
    headers = {"User-Agent": "ModCompiler/1.0"}
    if github_token:
        headers["Authorization"] = f"Bearer {github_token}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            release = json.loads(r.read().decode())
    except Exception as e:
        print(f"  WARNING: Could not fetch mc-runtime-test release: {e}")
        return None

    for asset in release.get("assets", []):
        name = asset.get("name", "")
        if re.match(
            f"mc-runtime-test-{re.escape(test_mc)}-.*-{re.escape(mcrt_jar)}-release\\.jar$",
            name,
        ):
            dl_url = asset.get("browser_download_url", "")
            if not dl_url:
                continue
            try:
                urllib.request.urlretrieve(dl_url, str(mods_dir / name))
                print(f"  Downloaded mc-runtime-test: {name}")
                return name
            except Exception as e:
                print(f"  WARNING: Download failed for {name}: {e}")
                return None
    print(f"  WARNING: No mc-runtime-test jar for {test_mc}/{mcrt_jar}")
    return None


def install_modloader(args, result):
    """Download Minecraft and install the mod loader. Returns version_id or None."""
    mc_json = Path(os.path.expanduser("~/.minecraft/versions")) / args.test_mc / f"{args.test_mc}.json"
    if not mc_json.exists():
        print(f"  Downloading Minecraft {args.test_mc}...")
        run_java(
            ["-jar", HMC_JAR, "--command", "download", args.test_mc],
            timeout=300, memory_limit="512M",
        )

    install_result = None
    if args.loader == "fabric":
        if not Path(FABRIC_INSTALLER_JAR).exists():
            print(f"  Downloading Fabric installer...")
            try:
                urllib.request.urlretrieve(FABRIC_INSTALLER_URL, FABRIC_INSTALLER_JAR)
            except Exception as e:
                print(f"  WARNING: Failed to download Fabric installer: {e}")
        minecraft_dir = os.path.expanduser("~/.minecraft")
        print(f"  Installing Fabric for {args.test_mc}...")
        install_result = run_java(
            ["-jar", FABRIC_INSTALLER_JAR, "client",
             "-dir", minecraft_dir, "-mcversion", args.test_mc, "-noprofile"],
            timeout=300, memory_limit="1G",
        )
    else:
        print(f"  Installing {args.loader} for {args.test_mc} (Java {args.java_version})...")
        install_result = run_java(
            ["-jar", HMC_JAR, "--command", args.loader, args.test_mc,
             "--java", str(args.java_version)],
            timeout=600, memory_limit="1G",
        )

    # Verify installation by looking at versions dir
    versions_dir = Path(os.path.expanduser("~/.minecraft/versions"))
    if args.loader == "fabric":
        fabric_dirs = list(versions_dir.glob(f"fabric-loader-*-{args.test_mc}"))
        if not fabric_dirs:
            result["status"] = "not_tested"
            reason = f"No Fabric installation found for {args.test_mc}"
            if install_result and install_result.returncode != 0:
                reason += f" (installer failed with code {install_result.returncode})"
            result["crash_summary"] = reason
            print(f"  SKIPPED: {reason}")
            return None
        return fabric_dirs[0].name
    else:
        loader_pattern = "forge" if args.loader == "forge" else "neoforge"
        version_id = None
        poll_waited = 0
        poll_interval = 10
        while poll_waited < 300:
            if versions_dir.exists():
                loader_dirs = [
                    d for d in versions_dir.iterdir()
                    if d.is_dir() and loader_pattern in d.name.lower()
                    and args.test_mc in d.name
                ]
                if loader_dirs:
                    version_id = loader_dirs[0].name
                    print(f"  {args.loader} installed: {version_id} (after {poll_waited}s)")
                    break
            time.sleep(poll_interval)
            poll_waited += poll_interval

        if not version_id and args.loader == "neoforge":
            nf_version = resolve_neoforge_version(args.test_mc, "")  # noqa: F821
            if nf_version:
                print(f"  NeoForge version resolved: {nf_version}")
                nf_code, _, _ = install_neoforge_direct(args.test_mc, nf_version)  # noqa: F821
                print(f"  NeoForge direct installer exit code: {nf_code}")
                poll_waited = 0
                while poll_waited < 300:
                    if versions_dir.exists():
                        loader_dirs = [
                            d for d in versions_dir.iterdir()
                            if d.is_dir() and loader_pattern in d.name.lower()
                            and (args.test_mc in d.name or (nf_version and nf_version in d.name))
                        ]
                        if loader_dirs:
                            version_id = loader_dirs[0].name
                            print(f"  {args.loader} installed via direct installer: {version_id} (after {poll_waited}s)")
                            break
                    time.sleep(poll_interval)
                    poll_waited += poll_interval

        if not version_id:
            # Try HMC list command as last resort
            list_result = subprocess.run(
                ["java", "-Xmx512M", "-jar", HMC_JAR, "--command", "list", args.loader_regex],
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=60,
            )
            list_output = (list_result.stdout or b"").decode(errors="replace").strip()
            if not list_output or "Couldn't find" in list_output or "No" in list_output:
                result["status"] = "not_tested"
                reason = f"No {args.loader} installation found for {args.test_mc}"
                if install_result and install_result.returncode != 0:
                    reason += f" (install failed with code {install_result.returncode})"
                result["crash_summary"] = reason
                print(f"  SKIPPED: {reason}")
                return None
            version_id = list_output.split("\n")[0].strip()
            print(f"  {args.loader} installed (via hmc list): {version_id}")

        return version_id


def launch_and_test(args, result):
    """Launch Minecraft and test. Modifies result dict in place.

    Detection logic (STRICT — no false positives):
    - Title screen detected in log → we kill it → PASSED
    - Game exits on its own with code 0 → PASSED
    - Game exits on its own with code != 0 → FAILED
    - Crash report / hs_err file → FAILED
    - Timeout without title screen → FAILED
    """
    print(f"  Launching Minecraft {args.test_mc} with {args.loader}...")

    # Create temp files for game output (NEVER use PIPE with Java/Minecraft)
    stdout_path = Path("run/game-stdout.log")
    stderr_path = Path("run/game-stderr.log")
    stdout_path.parent.mkdir(parents=True, exist_ok=True)

    stdout_file = open(stdout_path, "w")
    stderr_file = open(stderr_path, "w")

    try:
        # Start the game with start_new_session to create a new process group
        env = os.environ.copy()
        env["JAVA_TOOL_OPTIONS"] = "-Xmx512M -XX:MaxMetaspaceSize=128M"

        launch_proc = subprocess.Popen(
            ["xvfb-run", "java", "-Xmx512M", "-XX:MaxMetaspaceSize=128M",
             "-jar", HMC_JAR, "--command", "launch", args.loader_regex, "-regex",
             "--jvm", GAME_JVM_ARGS],
            stdout=stdout_file, stderr=stderr_file,
            start_new_session=True,
            env=env,
        )
        pgid = os.getpgid(launch_proc.pid)
    finally:
        stdout_file.close()
        stderr_file.close()

    # Poll for game loading / crashes
    title_screen_detected = False
    crash_detected = False
    self_exited = False
    self_exit_code = None
    waited = 0
    poll_interval = 5

    while waited < LAUNCH_TIMEOUT:
        # Check crash reports first
        crash_reports_dir = Path("run/crash-reports")
        if crash_reports_dir.exists() and any(crash_reports_dir.glob("crash-*.txt")):
            print(f"  CRASH REPORT DETECTED after {waited}s!")
            crash_detected = True
            break
        if any(Path("run").glob("hs_err_pid*.log")):
            print(f"  JVM CRASH detected after {waited}s!")
            crash_detected = True
            break

        # Check if process exited on its own
        if launch_proc.poll() is not None:
            self_exited = True
            self_exit_code = launch_proc.returncode
            print(f"  Game exited on its own after {waited}s (code: {self_exit_code})")
            break

        # Check log for title screen indicators
        log_path = Path("run/logs/latest.log")
        if log_path.exists():
            try:
                with open(log_path, "r", encoding="utf-8", errors="replace") as f:
                    log_tail = f.read()[-3000:]
                if ("Setting user:" in log_tail or
                        "Sound engine started" in log_tail or
                        "Created:" in log_tail or
                        "Minecraft initialized" in log_tail or
                        "Reloading resource" in log_tail):
                    if not title_screen_detected:
                        print(f"  Game reached title screen after {waited}s")
                        title_screen_detected = True
                        if waited < LAUNCH_TIMEOUT - 10:
                            time.sleep(10)
                            waited += 10
                        break
            except Exception:
                pass

        time.sleep(poll_interval)
        waited += poll_interval

    # Kill the entire process group (not just the parent process)
    print(f"  Cleaning up game process ({waited}s total)...")
    try:
        os.killpg(pgid, signal.SIGTERM)
        time.sleep(2)
        try:
            os.killpg(pgid, signal.SIGKILL)  # Hard kill as fallback
        except Exception:
            pass
    except Exception:
        try:
            launch_proc.kill()
        except Exception:
            pass

    # Final cleanup to be safe
    cleanup_processes()

    # Determine result - STRICT checking
    exit_code = launch_proc.returncode if launch_proc.poll() is not None else -9

    crash_reports_dir = Path("run/crash-reports")
    has_crash_report = crash_reports_dir.exists() and any(crash_reports_dir.glob("crash-*.txt"))
    has_hs_err = any(Path("run").glob("hs_err_pid*.log"))

    log_text = ""
    log_path = Path("run/logs/latest.log")
    if log_path.exists():
        try:
            with open(log_path, "r", encoding="utf-8", errors="replace") as f:
                log_text = f.read()[-5000:]
        except Exception:
            log_text = ""

    # Check for error indicators in log even if no crash report
    log_has_error = False
    error_indicators = [
        "Exception caught", "Caused by:", "java.lang.",
        "net.fabricmc.loader", "Error loading", "Crashing!",
        "The game crashed", "LoadingError", "InitializationError",
    ]
    if log_text:
        for indicator in error_indicators:
            if indicator in log_text:
                log_has_error = True
                break

    if has_crash_report or has_hs_err:
        result["status"] = "launcher_failed"
        if has_crash_report:
            crash_file = list(crash_reports_dir.glob("crash-*.txt"))[0]
            with open(crash_file, "r", encoding="utf-8", errors="replace") as f:
                result["crash_summary"] = f.read()[:500]
        elif has_hs_err:
            hs_file = list(Path("run").glob("hs_err_pid*.log"))[0]
            with open(hs_file, "r", encoding="utf-8", errors="replace") as f:
                result["crash_summary"] = f.read()[:500]
        result["logs"] = log_text
        print(f"  FAILED: crash report found")
    elif crash_detected:
        result["status"] = "launcher_failed"
        result["crash_summary"] = "Crash detected during game load"
        result["logs"] = log_text
        print(f"  FAILED: crash detected during monitoring")
    elif title_screen_detected:
        result["status"] = "passed"
        result["logs"] = log_text
        print(f"  PASSED (title screen detected)")
    elif self_exited and self_exit_code == 0:
        result["status"] = "passed"
        result["logs"] = log_text
        print(f"  PASSED (exited cleanly with code 0)")
    elif self_exited:
        result["status"] = "launcher_failed"
        result["crash_summary"] = f"Game exited with non-zero code: {self_exit_code}"
        result["logs"] = log_text
        print(f"  FAILED: game exited with code {self_exit_code}")
    elif log_has_error:
        result["status"] = "launcher_failed"
        result["crash_summary"] = "Game log contains error indicators but no crash report generated"
        result["logs"] = log_text
        print(f"  FAILED: errors detected in game log")
    else:
        result["status"] = "launcher_failed"
        result["crash_summary"] = f"Game never reached title screen (timed out after {LAUNCH_TIMEOUT}s)"
        result["logs"] = log_text
        print(f"  FAILED: timed out waiting for game to load")


def write_result(result, args):
    """Write result files and collect crash logs if failed."""
    out_dir = Path(args.out_dir)
    entry_dir = out_dir / args.safe_key
    entry_dir.mkdir(parents=True, exist_ok=True)

    # Write full result JSON
    (entry_dir / "result.json").write_text(
        json.dumps(result, indent=2), encoding="utf-8"
    )

    # Write simple status file (compatible with workflow collect-tests)
    if result["status"] == "passed":
        status_text = "pass"
    elif result["status"] == "not_tested":
        status_text = "not_tested"
    else:
        status_text = "fail"

    slug = args.slug if args.slug else args.safe_key
    (out_dir / f"{slug}.txt").write_text(status_text, encoding="utf-8")

    # Save crash logs on failure
    if result["status"] in ("launcher_failed", "dep_unresolved"):
        crash_out = entry_dir / "logs"
        crash_out.mkdir(parents=True, exist_ok=True)
        if Path("run/crash-reports").exists():
            shutil.copytree("run/crash-reports", crash_out / "crash-reports", dirs_exist_ok=True)
        if Path("run/logs").exists():
            shutil.copytree("run/logs", crash_out / "logs", dirs_exist_ok=True)
        for f in Path("run").glob("hs_err_pid*.log"):
            shutil.copy2(str(f), str(crash_out / f.name))
        game_log = Path("run/game-stdout.log")
        if game_log.exists():
            shutil.copy2(str(game_log), str(crash_out / "game-stdout.log"))

    print(f"  Status: {status_text}")


def main():
    args = parse_args()
    original_loader = args.original_loader or args.loader
    github_token = os.environ.get("GITHUB_TOKEN", "").strip()

    cleanup_processes()

    java_home = find_java_home_lib(args.java_version)
    if java_home:
        os.environ["JAVA_HOME"] = java_home
    print(f"  JAVA_HOME={java_home}")
    print(f"  JAVA_TOOL_OPTIONS={os.environ.get('JAVA_TOOL_OPTIONS', '(unset)')}")

    run_dir = Path("run")
    if run_dir.exists():
        shutil.rmtree(run_dir)
    run_dir.mkdir()
    (run_dir / "mods").mkdir()

    hmc_dir = Path("HeadlessMC")
    hmc_dir.mkdir(exist_ok=True)
    (hmc_dir / "config.properties").write_text(
        f"hmc.java.versions={java_home}/bin/java\n"
        f"hmc.gamedir={Path.cwd()}/run\n"
        f"hmc.offline=true\n"
        f"hmc.rethrow.launch.exceptions=true\n"
        f"hmc.exit.on.failed.command=true\n"
        f"hmc.assets.dummy=true\n"
    )
    (run_dir / "options.txt").write_text(
        "onboardAccessibility:false\npauseOnLostFocus:false\n"
    )

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"::group::Test: {original_loader} MC {args.game_version} (on {args.test_mc}, Java {args.java_version})")

    result = {
        "status": "launcher_failed",
        "slug": args.slug,
        "game_version": args.game_version,
        "loader": original_loader,
        "test_mc": args.test_mc,
        "version_number": args.version_number,
        "jar_name": args.jar_name,
        "file_url": args.file_url or "",
        "crash_summary": "",
        "logs": "",
        "timing": {
            "download_seconds": 0,
            "install_seconds": 0,
            "launch_seconds": 0,
            "total_seconds": 0,
        },
    }

    start_total = time.time()

    # Place mod jar
    start_download = time.time()
    if args.jar_path and Path(args.jar_path).exists():
        shutil.copy2(args.jar_path, str(run_dir / "mods" / args.jar_name))
        print(f"  Using local jar: {args.jar_path}")
        result["timing"]["download_seconds"] = 0
    elif args.file_url:
        print(f"  Downloading {args.jar_name} from {args.file_url}...")
        for attempt in range(3):
            r = subprocess.run(
                ["curl", "-fsSL", "--retry", "3", "--retry-delay", "5",
                 args.file_url, "-o", f"run/mods/{args.jar_name}"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120,
            )
            if r.returncode == 0:
                break
            time.sleep(5)
        result["timing"]["download_seconds"] = round(time.time() - start_download, 1)
        print(f"  Download took {result['timing']['download_seconds']}s")

    # Resolve dependencies
    unresolved = resolve_deps(run_dir / "mods", args.test_mc, args.loader)
    if unresolved:
        result["status"] = "dep_unresolved"
        result["crash_summary"] = f"Unresolved dependencies: {', '.join(unresolved)}"
        print(f"  FAILED: Unresolved dependencies: {unresolved}")
        write_result(result, args)
        return 0

    if not (run_dir / "mods" / args.jar_name).exists():
        result["crash_summary"] = f"Failed to find/obtain mod jar: {args.jar_name}"
        print(f"  FAILED: Could not obtain {args.jar_name}")
        write_result(result, args)
        return 0

    # Patch fabric.mod.json if needed
    if args.loader == "fabric" and args.test_mc != args.game_version:
        patch_mc(run_dir / "mods" / args.jar_name, args.game_version, args.test_mc)

    # Install mod loader
    start_install = time.time()
    version_id = install_modloader(args, result)
    result["timing"]["install_seconds"] = round(time.time() - start_install, 1)
    if version_id:
        print(f"  Install took {result['timing']['install_seconds']}s")
    if not version_id:
        result["timing"]["total_seconds"] = round(time.time() - start_total, 1)
        write_result(result, args)
        return 0

    # Download mc-runtime-test jar
    mcrt_name = download_mcrt_jar(args.test_mc, args.mcrt_jar, github_token)
    if not mcrt_name:
        result["status"] = "not_tested"
        result["crash_summary"] = f"No mc-runtime-test jar for {args.test_mc}/{args.mcrt_jar}"
        print(f"  FAILED: No mc-runtime-test jar available")
        result["timing"]["total_seconds"] = round(time.time() - start_total, 1)
        write_result(result, args)
        return 0

    # Launch and test
    start_launch = time.time()
    launch_and_test(args, result)
    result["timing"]["launch_seconds"] = round(time.time() - start_launch, 1)
    print(f"  Launch took {result['timing']['launch_seconds']}s")

    # Final timing
    result["timing"]["total_seconds"] = round(time.time() - start_total, 1)
    print(f"  Total time: {result['timing']['total_seconds']}s")

    # Write results
    write_result(result, args)

    return 0


if __name__ == "__main__":
    sys.exit(main())
