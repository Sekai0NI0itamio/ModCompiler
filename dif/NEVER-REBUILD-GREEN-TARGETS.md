---
id: NEVER-REBUILD-GREEN-TARGETS
title: Best practice — never rebuild already-green targets, always use --failed-only
tags: [best-practice, build-system, failed-only, github-actions, modrinth]
versions: []
loaders: []
symbols: []
error_patterns: []
---

## Issue

Rebuilding already-successful targets wastes GitHub Actions minutes, slows the overall build, and can cause the Modrinth publish step to skip already-uploaded versions.

## Root Cause

When a bundle zip contains targets that already built successfully in a previous run, the build system rebuilds them unnecessarily. The Modrinth publish step then sees these as "already exists" and skips them — but the rebuild still consumed GitHub Actions minutes.

## Fix

Always use `--failed-only` when regenerating a bundle after partial failures:

```bash
# After a partial build failure:
python3 scripts/generate_<mod>_bundle.py --failed-only
git add incoming/
git commit -m "Fix failing targets: <description>"
git push
python3 scripts/run_build.py incoming/<bundle>.zip --modrinth <url>
```

The `--failed-only` flag reads the most recent `ModCompileRuns/` result and only regenerates the targets that failed.

**Only rebuild all targets when:**
- You've made a change that affects all versions (e.g. fixing a shared utility class)
- You're doing the initial build of a new mod
- The `--failed-only` flag is broken (see FAILED-ONLY-FLAG-EMPTY-RESULT)

## Verified

Documented in LESSONS_LEARNED.md (April 23, 2026) and reinforced throughout all mod ports.
