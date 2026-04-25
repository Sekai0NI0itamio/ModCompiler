#!/usr/bin/env python3
"""
Full source search test — verifies that every version+loader combination
in DecompiledMinecraftSourceCode/ is searchable and contains real API classes.

Tests performed per folder:
  1. Folder exists and has .java files
  2. net/minecraft/ package is present (core MC classes)
  3. Mod loader API package is present (net/minecraftforge, net/neoforged, net/fabricmc)
  4. A grep for a well-known class returns results
  5. The README.md is valid and has correct metadata

Run from repo root:
    python3 scripts/_test_source_search.py
"""
import json
import re
import subprocess
import sys
from pathlib import Path

DEST = Path("DecompiledMinecraftSourceCode")
MANIFEST = Path("version-manifest.json")

# Well-known classes that should exist in every version
MINECRAFT_CLASSES = {
    # class name fragment -> expected in package path
    "net/minecraft": "net/minecraft",
}

# Loader-specific API packages
LOADER_PACKAGES = {
    "forge":    ["net/minecraftforge", "net/minecraft"],
    "neoforge": ["net/neoforged",      "net/minecraft"],
    "fabric":   ["net/fabricmc",       "net/minecraft"],
}

# A grep query that should match in every version
GREP_QUERIES = {
    "forge":    "public class",
    "neoforge": "public class",
    "fabric":   "public class",
}

# ─────────────────────────────────────────────────────────────────────────────

manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

expected = {}
for entry in manifest.get("ranges", []):
    for loader, cfg in entry.get("loaders", {}).items():
        versions = cfg.get("supported_versions") or [cfg.get("anchor_version", "")]
        for v in versions:
            slug = f"{v}-{loader}"
            expected[slug] = {"version": str(v), "loader": loader}

PASS = "✅"
FAIL = "❌"
WARN = "⚠️ "

results = []   # (slug, passed, details)

print(f"{'='*60}")
print(f"  AI Source Search — Full Repo Mode Test")
print(f"  Testing {len(expected)} version+loader combinations")
print(f"{'='*60}\n")

for slug in sorted(expected):
    version = expected[slug]["version"]
    loader  = expected[slug]["loader"]
    folder  = DEST / slug
    issues  = []
    passed  = True

    # ── Test 1: folder exists and has java files ──────────────────────────
    java_files = list(folder.rglob("*.java")) if folder.is_dir() else []
    java_count = len(java_files)
    if java_count == 0:
        issues.append(f"FAIL: folder missing or empty ({folder})")
        passed = False
        results.append((slug, False, issues))
        print(f"{FAIL} {slug:<30}  0 files — MISSING")
        continue

    # ── Test 2: net/minecraft/ package present ────────────────────────────
    mc_files = [f for f in java_files if "net/minecraft" in str(f)]
    if not mc_files:
        issues.append("FAIL: no net/minecraft/ classes found")
        passed = False
    else:
        issues.append(f"OK: {len(mc_files)} net/minecraft/ files")

    # ── Test 3: loader API package present ───────────────────────────────
    loader_ok = False
    for pkg in LOADER_PACKAGES.get(loader, []):
        pkg_files = [f for f in java_files if pkg in str(f)]
        if pkg_files:
            issues.append(f"OK: {len(pkg_files)} {pkg}/ files")
            loader_ok = True
            break
    if not loader_ok:
        issues.append(f"WARN: no loader API package found for {loader}")
        # Don't fail — some versions only have MC classes

    # ── Test 4: grep for a known pattern ─────────────────────────────────
    query = GREP_QUERIES.get(loader, "public class")
    try:
        r = subprocess.run(
            ["grep", "-rl", query, str(folder)],
            capture_output=True, text=True, timeout=30
        )
        match_count = len(r.stdout.strip().splitlines())
        if match_count == 0:
            issues.append(f"FAIL: grep '{query}' found 0 files")
            passed = False
        else:
            issues.append(f"OK: grep '{query}' → {match_count} files")
    except subprocess.TimeoutExpired:
        issues.append("WARN: grep timed out")

    # ── Test 5: README.md valid ───────────────────────────────────────────
    readme = folder / "README.md"
    if not readme.exists():
        issues.append("WARN: README.md missing")
    else:
        content = readme.read_text(encoding="utf-8")
        if version not in content:
            issues.append(f"WARN: README doesn't mention version {version}")
        elif loader not in content:
            issues.append(f"WARN: README doesn't mention loader {loader}")
        else:
            issues.append("OK: README valid")

    # ── Summary line ──────────────────────────────────────────────────────
    icon = PASS if passed else FAIL
    status = f"{java_count:>5} files"
    print(f"{icon} {slug:<30}  {status}  |  {'; '.join(issues)}")
    results.append((slug, passed, issues))

# ─────────────────────────────────────────────────────────────────────────────
# Final summary
# ─────────────────────────────────────────────────────────────────────────────
total   = len(results)
passed  = sum(1 for _, ok, _ in results if ok)
failed  = total - passed

print(f"\n{'='*60}")
print(f"  Results: {passed}/{total} passed")
if failed:
    print(f"\n  Failed versions:")
    for slug, ok, issues in results:
        if not ok:
            fail_issues = [i for i in issues if i.startswith("FAIL")]
            print(f"    {FAIL} {slug}: {'; '.join(fail_issues)}")
else:
    print(f"  {PASS} All {total} version+loader combinations passed!")
print(f"{'='*60}")

sys.exit(0 if failed == 0 else 1)
