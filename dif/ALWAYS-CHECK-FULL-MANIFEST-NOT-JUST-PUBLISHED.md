---
id: ALWAYS-CHECK-FULL-MANIFEST-NOT-JUST-PUBLISHED
title: Always compute missing targets from version-manifest.json, not just from what's already published
tags: [best-practice, diagnosis, version-manifest, missing-versions, all-loaders]
versions: []
loaders: [forge, fabric, neoforge]
symbols: []
error_patterns: []
---

## Issue

An agent completes a port, verifies the published state on Modrinth, and declares
the mod "fully updated" — but the repository actually supports more versions and
loaders than what was targeted. The user has to point out the missing versions.

## Root Cause

The agent computed the missing targets by comparing what was already published
against a manually-written list of targets. That list was incomplete because:

1. The agent only considered loaders the mod was originally published on (e.g. Forge only)
2. The agent skipped older versions assuming an API didn't exist, without verifying
3. The agent forgot to include Fabric targets entirely
4. The agent did not cross-reference against `version-manifest.json`

**The version-manifest.json is the single source of truth for what the repository supports.**
It is not the same as what is currently published on Modrinth.

## Real Example

Stackable Totems port (April 2026):
- Original mod: Forge only, 1.20.1–1.20.6
- After several build runs: 42 versions published (Forge + NeoForge, 1.16.5–26.1.2)
- Agent declared "done"
- User pointed out: still missing Fabric 1.16.5–26.1.2 AND Forge 1.12.2
- Root cause: agent never checked `version-manifest.json` for Fabric support
- Fix: 21 more targets added, all passed first try

## Fix

**Always run this check before declaring a port complete:**

```bash
python3 -c "
import json

with open('version-manifest.json') as f:
    manifest = json.load(f)

# Build complete target list from manifest
all_targets = set()
for r in manifest['ranges']:
    for loader, cfg in r['loaders'].items():
        versions = cfg.get('supported_versions', [r.get('min_version')])
        for v in versions:
            all_targets.add((v, loader))

# Replace with actual published set from fetch_modrinth_project.py
published = {
    # ('1.20.1', 'forge'), ...
}

missing = sorted(all_targets - published)
print(f'MISSING ({len(missing)} targets):')
for t in missing:
    print(f'  {t[0]:12} {t[1]}')
"
```

Or use the diagnosis script and compare:

```bash
python3 scripts/fetch_modrinth_project.py --project <url> --output-dir /tmp/diag
# Then manually compare /tmp/diag/index.json game_versions+loaders against manifest
```

## Rule

**Before declaring a port complete, always verify:**

1. Run `fetch_modrinth_project.py` to get the current published state
2. Run the manifest comparison script above
3. If `missing` is non-empty, build those targets
4. Only declare done when `missing` is empty

Do NOT rely on your own mental model of which versions/loaders the mod "should" support.
The manifest defines what the repository supports. Always use it.

## Verified

Confirmed in Stackable Totems all-versions port (April 2026).
