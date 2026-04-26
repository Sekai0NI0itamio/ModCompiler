---
id: FAILED-ONLY-FLAG-EMPTY-RESULT
title: --failed-only returns empty set even when there are failures
tags: [build-system, generator, failed-only, result.json, configuration-error]
versions: []
loaders: []
symbols: []
error_patterns: ["No failed targets found", "failed_only.*empty", "No failures found"]
---

## Issue

Running a generator script with `--failed-only` reports "No failed targets found" even though the last build run had failures.

## Error

```
No failed targets found in last run. Nothing to do.
```

## Root Cause

The `get_failed_targets()` function in the generator script reads `result.json` from the most recent `ModCompileRuns/` directory. However, the **top-level** `result.json` is the workflow-level result (not the per-mod result) and has no `mods` array. The function returns an empty set before checking the `artifacts/all-mod-builds/mods/` directory.

This happens when:
1. The generator's `get_failed_targets()` only checks `result.json` at the run root
2. The actual per-mod results are in `artifacts/all-mod-builds/mods/<mod-name>/result.json`

## Fix

**Workaround**: Run the generator without `--failed-only` to regenerate all targets, then rebuild. This wastes some GitHub Actions minutes but is reliable.

**Proper fix**: Update `get_failed_targets()` to check both locations:
```python
def get_failed_targets():
    runs_dir = ROOT / "ModCompileRuns"
    for run_dir in sorted(runs_dir.iterdir(), reverse=True):
        # Check top-level result.json first
        result_file = run_dir / "result.json"
        if result_file.exists():
            result = json.loads(result_file.read_text())
            mods = result.get("mods", [])
            if mods:  # Only use if it has mod data
                return {m["folder_name"] for m in mods if m.get("status") != "success"}

        # Fall back to per-mod result.json files
        mods_dir = run_dir / "artifacts" / "all-mod-builds" / "mods"
        if mods_dir.exists():
            failed = set()
            for mod_dir in mods_dir.iterdir():
                r_file = mod_dir / "result.json"
                if r_file.exists():
                    r = json.loads(r_file.read_text())
                    if r.get("status") != "success":
                        failed.add(mod_dir.name)
            return failed
    return None
```

## Verified

Confirmed in Day Counter port (Challenge 15). Workaround: ran generator without `--failed-only`.
