---
id: ALWAYS-DIAGNOSE-BEFORE-BUILDING
title: Always run diagnosis before building — never rebuild already-published versions
tags: [workflow, best-practice, modrinth, diagnosis, wasted-builds]
versions: []
loaders: [forge, fabric, neoforge]
symbols: []
error_patterns: ["already exists on Modrinth", "version already uploaded", "skipping.*already published"]
---

## Issue

Building targets that are already published on Modrinth wastes GitHub Actions
minutes, risks duplicate version uploads, and slows down the overall build.

## Root Cause

When updating a mod to all versions, it's tempting to build everything from scratch.
But the mod may already have many versions published. Building them again:
- Wastes CI minutes (each build takes 2–6 minutes)
- The Modrinth publish step skips already-uploaded versions, but the build still runs
- The `--failed-only` flag in generator scripts reads from the last run's failures,
  which may include targets that were already published in earlier runs

## Fix

**Step 1**: Always run the diagnosis first:

```bash
python3 scripts/fetch_modrinth_project.py \
    --project https://modrinth.com/mod/<slug> \
    --output-dir /tmp/diagnosis
cat /tmp/diagnosis/summary.txt
```

This shows exactly which MC versions and loaders are already published.

**Step 2**: Compare against `version-manifest.json` to find the missing targets.

**Step 3**: Build ONLY the missing targets. After generating the full bundle with
`--failed-only`, filter the zip to exclude already-published targets:

```python
import zipfile

already_published = {
    'VeinMiner-1.21-1.21.1-forge',   # already on Modrinth
    'VeinMiner-1.21.5-1.21.8-fabric', # already on Modrinth
    # ... etc
}

with zipfile.ZipFile('incoming/veinminer-all-versions.zip') as src:
    with zipfile.ZipFile('incoming/veinminer-missing-only.zip', 'w', zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            top = item.filename.split('/')[0]
            if top not in already_published:
                dst.writestr(item, src.read(item.filename))
```

**Step 4**: After each successful run, update the `already_published` set before
the next retry. Targets that succeeded in the last run are now published and must
not be rebuilt.

## Also see

- `NEVER-REBUILD-GREEN-TARGETS` — same principle for targets within a single run
- Profile Diagnosis workflow (`.github/workflows/profile-diagnosis.yml`) — runs
  full diagnosis for all mods owned by a Modrinth user

## Verified

Confirmed in Optimized Vein Miner all-versions port. Multiple runs wasted rebuilding
already-published targets before this workflow was established.
