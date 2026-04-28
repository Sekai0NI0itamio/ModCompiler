---
id: FORGE-117-TEMPLATE-SUPPORTS-1171-ONLY
title: Forge/Fabric 1.17-1.17.1 — template only supports 1.17.1, not 1.17
tags: [forge, fabric, compile-error, pre-build, version-string, 1.17, 1.17.1]
versions: [1.17.1]
loaders: [forge, fabric]
symbols: [supported_versions, minecraft_version]
error_patterns: ["does not support exact Minecraft 1.17\\.", "Supported exact versions: 1.17.1"]
---

## Issue

The `1.17-1.17.1` template for both Forge and Fabric only supports MC 1.17.1,
not 1.17. Attempting to build for `1.17` causes a pre-build validation failure.

## Error

```
Pre-build validation failed.

Requested version spec: 1.17-1.17.1
Exact build target: 1.17
Loader: forge

1.17-1.17.1/forge/template does not support exact Minecraft 1.17.
Supported exact versions: 1.17.1
```

## Root Cause

The `version-manifest.json` for the `1.17-1.17.1` range lists only `1.17.1` in
`supported_versions` for both forge and fabric loaders. MC 1.17 (without the
`.1`) is not supported.

## Fix

When generating targets for the 1.17 range, use `1.17.1` as the exact version,
not `1.17-1.17.1` as a range:

```python
# Wrong — will try to build for 1.17 which is unsupported
("MyMod-1.17-1.17.1-forge", src, "forge", "1.17-1.17.1", {}),

# Correct — only 1.17.1 is supported
("MyMod-1.17.1-forge", src, "forge", "1.17.1", {}),
```

## Verified

Confirmed in TPA Teleport all-versions port (run 3, April 2026).
