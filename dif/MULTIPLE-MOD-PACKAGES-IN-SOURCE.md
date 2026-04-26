---
id: MULTIPLE-MOD-PACKAGES-IN-SOURCE
title: Multiple mod packages in src/ — Gradle compiles all of them into one jar
tags: [build-system, local-dev, gradle, source-conflict, multiple-mods]
versions: []
loaders: [forge, fabric, neoforge]
symbols: []
error_patterns: ["multiple.*@Mod.*annotations", "duplicate.*modid", "conflicting.*mod"]
---

## Issue

When developing locally, a new mod compiles but the jar contains code from a previous mod. The wrong mod loads in Minecraft.

## Symptoms

```
[Server thread/INFO] [OldMod]: [DEBUG] Player joined - activating old mod behavior
```

This appears even though you're building a completely different mod.

## Root Cause

Gradle compiles **ALL** Java files in `src/main/java/` regardless of package name. When multiple mod packages exist simultaneously:

```
src/main/java/com/
├── oldmod/      ← Previous mod, should be removed
└── newmod/      ← Current mod
```

Both get compiled into the same jar, causing:
- Multiple `@Mod` annotations
- Conflicting mod IDs
- Wrong mod loading (alphabetically first mod may load)

## Fix

**Before building a new mod**, remove all old mod packages:

```bash
# Check what packages exist
find src/main/java -name "*.java" | head -20

# Remove old packages
rm -rf src/main/java/com/oldmod/

# Verify only one package remains
find src/main/java -name "*.java" | grep "com/" | cut -d'/' -f1-5 | sort -u
```

**After building**, save the jar before cleaning:
```bash
cp build/libs/MyMod.jar ModCollection/
```

**Rule**: One workspace = one mod at a time. Always clean between mods.

## Verified

Confirmed in LESSONS_LEARNED.md (April 3, 2026). Caused hours of debugging before the root cause was identified.
