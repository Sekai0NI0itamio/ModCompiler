---
id: FABRIC-MANIFEST-VERSION-VS-PUBLISHED-VERSION
title: Fabric — version-manifest.json supported_versions may differ from what was previously published (e.g. 1.17 not 1.17.1, 1.21 not 1.21.1)
tags: [fabric, best-practice, version-manifest, missing-versions, diagnosis]
versions: [1.17, 1.21]
loaders: [fabric]
symbols: []
error_patterns: []
---

## Issue

After completing a port, the manifest comparison still shows 2 missing Fabric targets
(`1.17 fabric` and `1.21 fabric`) even though `1.17.1` and `1.21.1` were already
published. The generator was targeting the wrong exact version strings.

## Root Cause

The `version-manifest.json` `supported_versions` list for Fabric does **not always
match** the Forge list for the same range folder. Specifically:

| Range folder | Forge supported_versions | Fabric supported_versions |
|-------------|--------------------------|--------------------------|
| `1.17-1.17.1` | `["1.17.1"]` | `["1.17"]` |
| `1.21-1.21.1` | `["1.21", "1.21.1"]` | `["1.21"]` |

The Fabric template for `1.17-1.17.1` targets MC `1.17` (not `1.17.1`), and the
Fabric template for `1.21-1.21.1` targets MC `1.21` (not `1.21.1`).

A generator that assumed "Fabric uses the same versions as Forge" would target
`1.17.1` and `1.21.1` for Fabric — producing jars that Modrinth accepts but that
don't satisfy the manifest's `1.17` and `1.21` entries.

## Fix

**Always read `supported_versions` from `version-manifest.json` per loader, not
per range folder.** Never assume Fabric and Forge share the same version list.

```python
import json

with open('version-manifest.json') as f:
    manifest = json.load(f)

for r in manifest['ranges']:
    for loader, cfg in r['loaders'].items():
        versions = cfg.get('supported_versions', [r.get('min_version')])
        for v in versions:
            print(f"{v:12} {loader}")
```

For the `1.17-1.17.1` range:
- Forge target: `1.17.1`
- Fabric target: `1.17`

For the `1.21-1.21.1` range:
- Forge targets: `1.21`, `1.21.1`
- NeoForge targets: `1.21`, `1.21.1`
- Fabric target: `1.21` only

In the generator TARGETS list:
```python
# WRONG — assumes Fabric uses same versions as Forge
("mymod-fabric-1-17-1", "1.17.1", "fabric", ...),  # wrong!
("mymod-fabric-1-21-1", "1.21.1", "fabric", ...),  # wrong!

# CORRECT — read from manifest
("mymod-fabric-1-17",   "1.17",   "fabric", ...),  # correct
("mymod-fabric-1-21",   "1.21",   "fabric", ...),  # correct
```

## Detection

Run the manifest comparison after every build run:

```python
import json, os

with open('version-manifest.json') as f:
    manifest = json.load(f)

all_targets = set()
for r in manifest['ranges']:
    for loader, cfg in r['loaders'].items():
        versions = cfg.get('supported_versions', [r.get('min_version')])
        for v in versions:
            all_targets.add((v, loader))

# published = set of (version, loader) from fetch_modrinth_project.py
missing = sorted(all_targets - published)
print(f'MISSING ({len(missing)}):')
for t in missing:
    print(f'  {t[0]:12} {t[1]}')
```

If `1.17 fabric` or `1.21 fabric` appear in the missing list after publishing
`1.17.1` and `1.21.1`, this is the cause.

## Verified

Confirmed in Account Switcher all-versions port (run 3, April 2026).
After runs 1 and 2 published 41 targets, the manifest comparison showed
`1.17 fabric` and `1.21 fabric` still missing. Adding those 2 targets resolved it.
