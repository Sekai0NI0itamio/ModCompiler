#!/usr/bin/env python3
"""Launcher test for Minecraft mods with proper process group management.

Kills the ENTIRE process tree (xvfb-run + java + minecraft) instead of just
the parent process, and drains stdout/stderr in background threads so that
the test never blocks after the game has loaded.

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
import signal
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile
from pathlib import Path

socket.setdefaulttimeout(120)

exec(compile(open("scripts/launcher_test_lib.py").read(), "scripts/launcher_test_lib.py", "exec"))
resolve_deps = resolve_dependencies  # noqa: F821
find_java_home_lib = find_java_home  # noqa: F821
patch_mc = patch_fabric_mc_version  # noqa: F821

HMC_JAR = "headlessmc-launcher-wrapper-2.9.0.jar"
FABRIC_INSTALLER_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.1/fabric-installer-1.1.1.jar"
FABRIC_INSTALLER_JAR = "fabric-installer-1.1.1.jar"
MCRT_VERSION = "4.4.0"
MCRT_REPO = "headlesshq/mc-runtime-test"
LAUNCH_TIMEOUT = 120


def parse_args():
    p = argparse.ArgumentParser(description="Launcher test for mod jars")
    p.add_argument("--jar-name", required=True)
    p.add_argument("--jar-path", default=None,
                   help="Path to local jar file (optional; if not set, downloads --file-url)")
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


def drain_stream(stream, buf, limit):
    """Read lines from a stream into buf (used in a background thread)."""
    try:
        for line in iter(stream.readline, ""):
            try:
                text = line if isinstance(line, str) else line.decode("utf-8", errors="replace")
            except Exception:
                text = str(line)
            if len(buf) < limit:
                buf.append(text)
            else:
                # Keep the tail
                buf.pop(0)
                buf.append(text)
    except Exception:
        pass
    try:
        stream.close()
    except Exception:
        pass


def kill_process_group(proc, timeout_soft=10, timeout_hard=5):
    """Kill the entire process group of proc, falling back to pkill."""
    pgid = None
    try:
        pgid = os.getpgid(proc.pid)
    except Exception:
        pgid = None

    if pgid is not None and pgid > 0:
        try:
            os.killpg(pgid, signal.SIGTERM)
        except Exception:
            pass
        deadline = time.time() + timeout_soft
        while time.time() < deadline:
            if proc.poll() is not None:
                break
            time.sleep(0.5)

    if proc.poll() is None:
        try:
            if pgid is not None and pgid > 0:
                os.killpg(pgid, signal.SIGKILL)
        except Exception:
            pass
        try:
            proc.kill()
        except Exception:
            pass
        try:
            proc.wait(timeout=timeout_hard)
        except Exception:
            pass

    # Brute-force cleanup: kill any lingering java / Xvfb / hmc processes
    for pat in ["headlessmc-launcher", "Xvfb", "net.minecraft", "net.neoforged", "cpw.mods"]:
        try:
            subprocess.run(["pkill", "-f", pat], capture_output=True, timeout=5)
        except Exception:
            pass
    time.sleep(1)
    try:
        subprocess.run(["pkill", "-9", "-f", "java"], capture_output=True, timeout=5)
    except Exception:
        pass
    time.sleep(1)


def initial_cleanup():
    try:
        subprocess.run(["pkill", "-f", "headlessmc-launcher"], capture_output=True, timeout=5)
    except Exception:
        pass
    try:
        subprocess.run(["pkill", "-f", "Xvfb"], capture_output=True, timeout=5)
    except Exception:
        pass
    try:
        subprocess.run(["pkill", "-9", "-f", "java"], capture_output=True, timeout=5)
    except Exception:
        pass
    time.sleep(1)


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


def install_modloader(args, result, mcrt_installed_stdout):
    """Download Minecraft and install the mod loader. Returns version_id or None."""
    # Download Minecraft
    mc_json = Path(os.path.expanduser("~/.minecraft/versions")) / args.test_mc / f"{args.test_mc}.json"
    if not mc_json.exists():
        print(f"  Downloading Minecraft {args.test_mc}...")
        subprocess.run(
            ["java", "-jar", HMC_JAR, "--command", "download", args.test_mc],
            capture_output=True, text=True, timeout=300,
        )

    install_result = None
    if args.loader == "fabric":
        if not Path(FABRIC_INSTALLER_JAR).exists():
            print(f"  Downloading Fabric installer...")
            urllib.request.urlretrieve(FABRIC_INSTALLER_URL, FABRIC_INSTALLER_JAR)
        minecraft_dir = os.path.expanduser("~/.minecraft")
        print(f"  Installing Fabric for {args.test_mc}...")
        install_result = subprocess.run(
            ["java", "-jar", FABRIC_INSTALLER_JAR, "client",
             "-dir", minecraft_dir, "-mcversion", args.test_mc, "-noprofile"],
            capture_output=True, text=True, timeout=300,
        )
        if install_result.returncode != 0:
            print(f"  WARNING: Fabric installer returned code {install_result.returncode}")
            if install_result.stderr:
                print(f"  stderr: {install_result.stderr[:500]}")
    else:
        print(f"  Installing {args.loader} for {args.test_mc} (Java {args.java_version})...")
        install_result = subprocess.run(
            ["java", "-jar", HMC_JAR, "--command", args.loader, args.test_mc,
             "--java", str(args.java_version)],
            capture_output=True, text=True, timeout=600,
        )
        install_stderr = (install_result.stderr or "")[:500]
        install_stdout = (install_result.stdout or "")[:500]
        mcrt_installed_stdout["text"] = install_result.stdout or ""
        if install_result.returncode != 0:
            print(f"  WARNING: {args.loader} install returned code {install_result.returncode}")
        if install_stderr:
            print(f"  Install stderr: {install_stderr}")
        if install_stdout:
            print(f"  Install stdout: {install_stdout}")

    # Verify installation
    versions_dir = Path(os.path.expanduser("~/.minecraft/versions"))
    if args.loader == "fabric":
        fabric_dirs = list(versions_dir.glob(f"fabric-loader-*-{args.test_mc}"))
        if not fabric_dirs:
            result["status"] = "not_tested"
            reason = f"No Fabric installation found for {args.test_mc}"
            if install_result and install_result.returncode != 0:
                reason += f" (installer failed with code {install_result.returncode})"
            result["crash_summary"] = reason
            result["launch_stderr"] = (install_result.stderr or "")[:2000] if install_result else ""
            print(f"  SKIPPED: {reason}")
            return None
        return fabric_dirs[0].name
    else:
        loader_pattern = "forge" if args.loader == "forge" else "neoforge"
        version_id = None
        nf_version = None
        poll_waited = 0
        poll_interval = 10
        while poll_waited < 300:
            if versions_dir.exists():
                loader_dirs = [
                    d for d in versions_dir.iterdir()
                    if d.is_dir() and loader_pattern in d.name.lower() and args.test_mc in d.name
                ]
                if loader_dirs:
                    version_id = loader_dirs[0].name
                    print(f"  {args.loader} installed: {version_id} (after {poll_waited}s)")
                    break
            time.sleep(poll_interval)
            poll_waited += poll_interval

        if not version_id and args.loader == "neoforge":
            nf_version = resolve_neoforge_version(  # noqa: F821
                args.test_mc, mcrt_installed_stdout.get("text", "")
            )
            if nf_version:
                print(f"  NeoForge version resolved: {nf_version}")
                nf_code, nf_out, nf_err = install_neoforge_direct(  # noqa: F821
                    args.test_mc, nf_version
                )
                print(f"  NeoForge direct installer exit code: {nf_code}")
                if nf_out:
                    print(f"  NeoForge installer stdout: {nf_out[:1000]}")
                if nf_err:
                    print(f"  NeoForge installer stderr: {nf_err[:1000]}")
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
            list_result = subprocess.run(
                ["java", "-jar", HMC_JAR, "--command", "list", args.loader_regex],
                capture_output=True, text=True, timeout=30,
            )
            list_output = (list_result.stdout or "").strip()
            if not list_output or "Couldn't find" in list_output or "No" in list_output:
                result["status"] = "not_tested"
                reason = f"No {args.loader} installation found for {args.test_mc}"
                if install_result and install_result.returncode != 0:
                    reason += f" (install failed with code {install_result.returncode})"
                result["crash_summary"] = reason
                result["launch_stderr"] = (install_result.stderr or "")[:2000] if install_result else ""
                print(f"  SKIPPED: {reason}")
                return None
            version_id = list_output.split("\n")[0].strip()
            print(f"  {args.loader} installed (via hmc list): {version_id}")

        return version_id


def write_result_and_exit(result, out_dir, safe_key, final_status=None):
    if final_status:
        result["status"] = final_status
    entry_dir = Path(out_dir) / safe_key
    entry_dir.mkdir(parents=True, exist_ok=True)
    (entry_dir / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(f"  Status: {result['status']}")
    print("::endgroup::")


def main():
    args = parse_args()
    original_loader = args.original_loader or args.loader
    github_token = os.environ.get("GITHUB_TOKEN", "").strip()

    initial_cleanup()

    java_home = find_java_home_lib(args.java_version)
    if java_home:
        os.environ["JAVA_HOME"] = java_home
    print(f"  JAVA_HOME={java_home}")

    # Setup run directory
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
        "crash_summary": "",
        "logs": "",
    }

    # Place mod jar into mods/
    if args.jar_path and Path(args.jar_path).exists():
        shutil.copy2(args.jar_path, str(run_dir / "mods" / args.jar_name))
        print(f"  Using local jar: {args.jar_path}")
    elif args.file_url:
        print(f"  Downloading {args.jar_name}...")
        for attempt in range(3):
            r = subprocess.run(
                ["curl", "-fsSL", "--retry", "3", "--retry-delay", "5",
                 args.file_url, "-o", f"run/mods/{args.jar_name}"],
                capture_output=True, text=True, timeout=120,
            )
            if r.returncode == 0:
                break
            time.sleep(5)

    # Resolve dependencies
    unresolved = resolve_deps(run_dir / "mods", args.test_mc, args.loader)
    if unresolved:
        result["status"] = "dep_unresolved"
        result["crash_summary"] = f"Unresolved dependencies: {', '.join(unresolved)}"
        print(f"  FAILED: Unresolved dependencies: {unresolved}")
        write_result_and_exit(result, out_dir, args.safe_key)
        return 0

    if not (run_dir / "mods" / args.jar_name).exists():
        result["crash_summary"] = f"Failed to download mod jar: {args.jar_name}"
        print(f"  FAILED: Could not download {args.jar_name}")
        write_result_and_exit(result, out_dir, args.safe_key)
        return 0

    # Patch fabric.mod.json if test_mc != game_version
    if args.loader == "fabric" and args.test_mc != args.game_version:
        patch_mc(run_dir / "mods" / args.jar_name, args.game_version, args.test_mc)

    # Install mod loader
    mcrt_installed_stdout = {}
    version_id = install_modloader(args, result, mcrt_installed_stdout)
    if not version_id:
        write_result_and_exit(result, out_dir, args.safe_key)
        return 0

    # Download mc-runtime-test jar
    mcrt_name = download_mcrt_jar(args.test_mc, args.mcrt_jar, github_token)
    if not mcrt_name:
        result["status"] = "not_tested"
        result["crash_summary"] = f"No mc-runtime-test jar for {args.test_mc}/{args.mcrt_jar}"
        print(f"  FAILED: No mc-runtime-test jar available")
        write_result_and_exit(result, out_dir, args.safe_key)
        return 0

    # ---- LAUNCH GAME WITH PROCESS GROUP MANAGEMENT ----
    print(f"  Launching Minecraft {args.test_mc} with {args.loader}...")
    launch_proc = subprocess.Popen(
        ["xvfb-run", "java", "-Dhmc.check.xvfb=true",
         "-jar", HMC_JAR, "--command", "launch", args.loader_regex, "-regex",
         "--jvm", "-Djava.awt.headless=true"],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        start_new_session=True,  # create new session/process group so we can killpg
    )

    # Drain stdout/stderr in background threads — prevents blocking after kill
    stdout_buf = []
    stderr_buf = []
    stdout_thread = threading.Thread(
        target=drain_stream, args=(launch_proc.stdout, stdout_buf, 500), daemon=True,
    )
    stderr_thread = threading.Thread(
        target=drain_stream, args=(launch_proc.stderr, stderr_buf, 500), daemon=True,
    )
    stdout_thread.start()
    stderr_thread.start()

    # Wait for game to load, then kill
    game_started = False
    crash_detected = False
    waited = 0
    poll_interval = 5

    while waited < LAUNCH_TIMEOUT:
        time.sleep(poll_interval)
        waited += poll_interval

        if launch_proc.poll() is not None:
            print(f"  Game exited on its own after {waited}s (exit code {launch_proc.returncode})")
            game_started = True
            break

        crash_reports_dir = Path("run/crash-reports")
        crash_files = list(crash_reports_dir.glob("crash-*.txt")) if crash_reports_dir.exists() else []
        hs_err = list(Path("run").glob("hs_err_pid*.log"))

        if crash_files or hs_err:
            print(f"  CRASH DETECTED after {waited}s!")
            crash_detected = True
            time.sleep(3)
            kill_process_group(launch_proc)
            break

        log_path = Path("run/logs/latest.log")
        if log_path.exists():
            try:
                log_text = log_path.read_text(encoding="utf-8", errors="replace")
                if "Setting user:" in log_text or "Loaded" in log_text:
                    if not game_started:
                        print(f"  Game reached title screen after {waited}s")
                        game_started = True
                        if waited < LAUNCH_TIMEOUT - 10:
                            time.sleep(10)
                            waited += 10
                        break
            except Exception:
                pass

    # Kill if still running
    if launch_proc.poll() is None:
        print(f"  Killing game after {waited}s (timeout)")
        kill_process_group(launch_proc)

    # Drain background threads
    stdout_thread.join(timeout=5)
    stderr_thread.join(timeout=5)

    # Capture buffered output
    launch_stdout = "".join(stdout_buf)[:10000]
    launch_stderr = "".join(stderr_buf)[:10000]

    # Collect results
    crash_reports_dir = Path("run/crash-reports")
    crash_files = list(crash_reports_dir.glob("crash-*.txt")) if crash_reports_dir.exists() else []
    hs_err = list(Path("run").glob("hs_err_pid*.log"))

    log_text = ""
    log_path = Path("run/logs/latest.log")
    if log_path.exists():
        try:
            log_text = log_path.read_text(encoding="utf-8", errors="replace")[:5000]
        except Exception:
            log_text = ""
    elif Path("run/latest.log").exists():
        try:
            log_text = Path("run/latest.log").read_text(encoding="utf-8", errors="replace")[:5000]
        except Exception:
            log_text = ""

    exit_code = launch_proc.returncode if launch_proc.poll() is not None else -9
    result["launch_stdout"] = launch_stdout[:2000]
    result["launch_stderr"] = launch_stderr[:2000]

    if crash_files or hs_err:
        result["status"] = "launcher_failed"
        if crash_files:
            result["crash_summary"] = crash_files[0].read_text(encoding="utf-8", errors="replace")[:500]
        elif hs_err:
            result["crash_summary"] = hs_err[0].read_text(encoding="utf-8", errors="replace")[:500]
        result["logs"] = log_text
        print(f"  FAILED: Crash report found")
    elif crash_detected:
        result["status"] = "launcher_failed"
        result["crash_summary"] = "Crash detected during game load"
        result["logs"] = log_text
        print(f"  FAILED: Crash detected")
    elif game_started and exit_code not in (0, -9):
        result["status"] = "launcher_failed"
        result["crash_summary"] = f"Game exited with code {exit_code}"
        result["logs"] = log_text
        print(f"  FAILED: Game exited with code {exit_code}")
    elif game_started:
        result["status"] = "passed"
        result["logs"] = log_text
        print(f"  PASSED")
    else:
        result["status"] = "launcher_failed"
        result["crash_summary"] = f"Game never reached title screen (timed out after {LAUNCH_TIMEOUT}s)"
        result["logs"] = log_text
        print(f"  FAILED: Timed out waiting for game to load")

    # Save crash logs on failure
    if result["status"] == "launcher_failed" or result["status"] == "dep_unresolved":
        crash_dir = Path(out_dir) / "crash-logs"
        crash_sub = crash_dir / args.safe_key
        crash_sub.mkdir(parents=True, exist_ok=True)
        if Path("run/crash-reports").exists():
            shutil.copytree("run/crash-reports", crash_sub / "crash-reports", dirs_exist_ok=True)
        if Path("run/logs").exists():
            shutil.copytree("run/logs", crash_sub / "logs", dirs_exist_ok=True)
        for f in Path("run").glob("hs_err_pid*.log"):
            shutil.copy2(str(f), str(crash_sub / f.name))

    # Write result
    entry_dir = out_dir / args.safe_key
    entry_dir.mkdir(parents=True, exist_ok=True)
    (entry_dir / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")

    # Simple status file for aggregation
    status_text = result["status"] if result["status"] in ("passed", "not_tested") else "failed"
    (out_dir / f"{args.slug or args.safe_key}.txt").write_text(status_text, encoding="utf-8")

    print(f"  Status: {status_text}")
    print("::endgroup::")

    # Final cleanup
    initial_cleanup()
    return 0


if __name__ == "__main__":
    sys.exit(main())
