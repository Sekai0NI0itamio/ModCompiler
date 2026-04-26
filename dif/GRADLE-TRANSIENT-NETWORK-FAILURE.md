---
id: GRADLE-TRANSIENT-NETWORK-FAILURE
title: Transient Gradle/Maven download failures on GitHub Actions — just retry
tags: [build-system, transient, network, gradle, retry]
versions: []
loaders: [forge, fabric, neoforge]
symbols: []
error_patterns: ["timeout.*ms", "Response 304.*has no content", "Could not download.*timeout", "Connection reset", "Could not GET.*timed out"]
---

## Issue

A build fails due to a network error while downloading Gradle, Forge, or Maven dependencies. The error is transient and not caused by any code problem.

## Error

```
Downloading gradle-9.3.1-bin.zip — timeout (10000ms)
```

or

```
Could not download datafixerupper-4.0.26.jar — Response 304: Not Modified has no content
```

or

```
> Could not GET 'https://maven.minecraftforge.net/...' — Connection reset
```

## Root Cause

GitHub Actions runners occasionally experience transient network issues:
- DNS resolution failures
- TCP connection resets
- HTTP 304 responses with no body (cache inconsistency)
- Download timeouts

These are infrastructure issues, not code problems.

## Fix

**Simply retry the build.** Use `--failed-only` to only rebuild the failed targets:

```bash
python3 scripts/generate_<mod>_bundle.py --failed-only
git add incoming/
git commit -m "Retry transient network failures"
git push
python3 scripts/run_build.py incoming/<bundle>.zip
```

The retry will almost always succeed. If it fails again with the same error, wait a few minutes and retry once more.

**Do not** change any source code or configuration — the error is not caused by your mod.

## Verified

Confirmed in Day Counter port (Challenge 13: Forge 1.21.3 Gradle timeout, Challenge 14: Forge 1.17.1 304 error). Both resolved by retrying.
