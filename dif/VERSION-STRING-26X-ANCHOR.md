---
id: VERSION-STRING-26X-ANCHOR
title: 26.1-26.x version string must be the anchor version (26.1.2), not "26.1-26.x" or "26.x"
tags: [compile-error, prepare, version-string, 26.1, manifest, anchor]
versions: [26.1.2]
loaders: [forge, fabric, neoforge]
symbols: [minecraft_version, version.txt, supported_versions]
error_patterns: ["Unsupported version format '26.x'", "Unsupported version format '26.1-26.x'"]
---

## Issue

When building for the `26.1-26.x` version range, the `version.txt` file must
use the anchor version string `26.1.2` — not the folder name `26.1-26.x` or
the shorthand `26.x`.

## Error

```
Unsupported version format '26.x'
```

or

```
Unsupported version format '26.1-26.x'
```

## Root Cause

The build prepare script validates the `minecraft_version` field in `version.txt`
against the `supported_versions` list in `version-manifest.json`. The 26.1-26.x
range has `supported_versions: ["26.1.2"]` (and optionally `26.1`, `26.1.1`).
The folder name `26.1-26.x` is not a valid version string.

## Fix

In `version.txt` for 26.1-26.x targets, use the anchor version:

```
minecraft_version=26.1.2
loader=forge
```

Or use a range that matches the supported_versions list:
```
minecraft_version=26.1-26.1.2
loader=fabric
```

Check `version-manifest.json` for the exact `supported_versions` list for the
26.1-26.x range before setting the version string.

## General Rule

The `minecraft_version` in `version.txt` must be either:
1. An exact version that appears in `supported_versions` for that range
2. A range like `X.Y-X.Y.Z` where both endpoints are in `supported_versions`

Never use the folder name as the version string.

## Verified

Confirmed in TPA Teleport all-versions port (run 2, April 2026).
