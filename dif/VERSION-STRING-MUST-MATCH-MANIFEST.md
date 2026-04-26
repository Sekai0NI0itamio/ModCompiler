---
id: VERSION-STRING-MUST-MATCH-MANIFEST
title: Build prepare fails — version string must exactly match supported_versions in version-manifest.json
tags: [build-system, prepare-failure, version-manifest, configuration-error]
versions: []
loaders: [forge, fabric, neoforge]
symbols: []
error_patterns: ["does not support exact Minecraft", "Supported exact versions:", "version.txt.*not in supported_versions"]
---

## Issue

The build prepare step fails immediately with a version validation error before any compilation happens.

## Error

```
1.17-1.17.1/forge/template does not support exact Minecraft 1.17.
Supported exact versions: 1.17.1
```

or

```
version.txt specifies minecraft_version=1.21.2 but this is not in supported_versions
for the 1.21.2-1.21.8 forge template. Supported: 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8
```

## Root Cause

The build system validates `version.txt` against the `supported_versions` list in `version-manifest.json`. The version string must be an **exact match** — range min/max values that aren't explicitly listed will fail.

Common mistakes:
- Using `1.17` when only `1.17.1` is in the list
- Using `1.20` when the list starts at `1.20.1`
- Using `1.21.2` for Forge when Forge only supports `1.21.3` and above in that range

## Fix

Always check `version-manifest.json` before writing `version.txt`. For each range, look at the `supported_versions` array:

```json
"forge": {
  "supported_versions": ["1.17.1"]
}
```

Common correct mappings:

| Wrong | Correct | Reason |
|-------|---------|--------|
| `1.17` fabric | `1.17.1` | Only `1.17.1` in fabric supported_versions |
| `1.17` forge | `1.17.1` | Only `1.17.1` in forge supported_versions |
| `1.20` fabric | `1.20.1` | First entry in fabric supported_versions |
| `1.20` forge | `1.20.1` | First entry in forge supported_versions |
| `1.21.2` forge | `1.21.3` | `1.21.2` not in forge list, starts at `1.21.3` |
| `1.20` neoforge | `1.20.2` | First entry in neoforge supported_versions |

In the generator script, always use the exact version from the manifest:
```python
# WRONG
("1.17", "forge"),

# CORRECT — matches supported_versions in manifest
("1.17.1", "forge"),
```

## Verified

Confirmed in Seed Protect port (Phase 2, Challenge 3, Run 3).
