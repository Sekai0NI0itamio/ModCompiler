---
id: NEOFORGE-120-SUPPORTED-VERSIONS
title: NeoForge 1.20 — template only supports 1.20.2, 1.20.4, 1.20.5, 1.20.6 (not 1.20 or 1.20.1)
tags: [neoforge, compile-error, pre-build, version-string, 1.20, supported-versions]
versions: [1.20.2, 1.20.4, 1.20.5, 1.20.6]
loaders: [neoforge]
symbols: [supported_versions, minecraft_version]
error_patterns: ["does not support exact Minecraft 1.20\\.", "Supported exact versions: 1.20.2, 1.20.4"]
---

## Issue

The NeoForge template for the `1.20-1.20.6` range does not support MC 1.20 or
1.20.1. Attempting to build for those versions causes a pre-build validation failure.

## Error

```
Pre-build validation failed.

Requested version spec: 1.20-1.20.6
Exact build target: 1.20
Loader: neoforge

1.20-1.20.6/neoforge/template does not support exact Minecraft 1.20.
Supported exact versions: 1.20.2, 1.20.4, 1.20.5, 1.20.6
```

## Root Cause

NeoForge did not exist for MC 1.20 and 1.20.1 — it was introduced in 1.20.2.
The `version-manifest.json` reflects this: the neoforge loader for the
`1.20-1.20.6` range only lists `supported_versions: ["1.20.2", "1.20.4", "1.20.5", "1.20.6"]`.

## Fix

When generating NeoForge targets for the 1.20 range, only include:
- `1.20.2`
- `1.20.4`
- `1.20.5-1.20.6` (or individual versions)

Do NOT include `1.20` or `1.20.1` for NeoForge.

```python
# Correct NeoForge 1.20 targets
("MyMod-1.20.2-neoforge",       src, "neoforge", "1.20.2",        {}),
("MyMod-1.20.4-neoforge",       src, "neoforge", "1.20.4",        {}),
("MyMod-1.20.5-1.20.6-neoforge",src, "neoforge", "1.20.5-1.20.6", {}),
```

## Fabric Note

The Fabric template for `1.20-1.20.6` supports `1.20.1` through `1.20.6` but
NOT `1.20` itself. Use `1.20.1-1.20.6` as the range for fabric.

## Verified

Confirmed in TPA Teleport all-versions port (run 3, April 2026).
