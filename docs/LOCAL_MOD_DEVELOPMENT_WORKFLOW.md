# Local Mod Development Workflow

## Overview

This document explains the proper workflow for developing multiple mods in the local 1.12.2 Forge workspace without conflicts or build issues.

## Critical Issue: Source Code Conflicts

### The Problem

When developing multiple mods sequentially in the same workspace (`Mod Developement/1.12.2-forge/`), old mod source files can remain in the `src/` directory and get compiled into new mods, causing:

- Wrong mod loading (old mod appears instead of new mod)
- Conflicting class definitions
- Incorrect mod metadata in compiled jars
- Debug messages from previous mods appearing in logs

### Example of the Issue

```
User creates "Hostile Mobs" mod → builds successfully
User creates "No Hostile Mobs" mod → old files still present
User creates "Area Dig" mod → both old mods' files still present
Result: All three mods compile together into one jar!
```

**Log Evidence:**
```
[Server thread/INFO] [Hostile Mobs]: [DEBUG] Player joined - activating hostile mobs
```
This appears even when testing "No Hostile Mobs" or "Area Dig" because old source files weren't removed.

## Proper Workflow for Sequential Mod Development

### Step 1: Clean the Workspace Before Starting a New Mod

Before creating a new mod, ALWAYS clean out old mod source files:

```bash
# Remove all old mod packages from src
rm -rf "Mod Developement/1.12.2-forge/src/main/java/com/"*

# Remove all old resource assets
rm -rf "Mod Developement/1.12.2-forge/src/main/resources/assets/"*

# Clean build artifacts
cd "Mod Developement/1.12.2-forge"
./gradlew clean
```

### Step 2: Preserve Previous Mod Before Cleaning

Before removing old source files, save the completed mod to `ModCollection`:

```bash
# Copy the built jar
cp "Mod Developement/1.12.2-forge/build/libs/Your-Mod-1.0.0.jar" \
   "Mod Developement/1.12.2-forge/ModCollection/"

# Save the source code
mkdir -p "Mod Developement/1.12.2-forge/ModCollection/Your-Mod-Src"
cp -r "Mod Developement/1.12.2-forge/src/main/java/com/yourmod" \
      "Mod Developement/1.12.2-forge/ModCollection/Your-Mod-Src/"
cp -r "Mod Developement/1.12.2-forge/src/main/resources" \
      "Mod Developement/1.12.2-forge/ModCollection/Your-Mod-Src/"
```

### Step 3: Create the New Mod

Now you can safely create new mod files without conflicts:

```bash
# Create new mod package
mkdir -p "Mod Developement/1.12.2-forge/src/main/java/com/newmod"

# Create new mod files
# ... your mod development here ...
```

### Step 4: Update Build Configuration

Update `build.gradle` for the new mod:

```gradle
version = "1.0.0"
group = "com.newmod"
archivesBaseName = "New-Mod"
```

Update `src/main/resources/mcmod.info`:

```json
[
{
  "modid": "newmod",
  "name": "New Mod",
  "description": "Description of new mod",
  "version": "1.0.0",
  "mcversion": "1.12.2",
  ...
}
]
```

### Step 5: Build and Test

```bash
cd "Mod Developement/1.12.2-forge"
./gradlew clean build
```

Verify the jar contains ONLY your new mod:
```bash
jar tf build/libs/New-Mod-1.0.0.jar | grep "\.class$"
```

You should see ONLY classes from your new mod package.

## Recommended: Use a Dedicated Build Script

Create a script that automates the clean workflow:

```bash
#!/bin/bash
# build_new_mod.sh

MOD_NAME=$1
MOD_PACKAGE=$2

if [ -z "$MOD_NAME" ] || [ -z "$MOD_PACKAGE" ]; then
    echo "Usage: ./build_new_mod.sh <ModName> <package>"
    echo "Example: ./build_new_mod.sh \"Area Dig\" areadig"
    exit 1
fi

echo "Preparing workspace for new mod: $MOD_NAME"

# Save current mod if it exists
if [ -d "src/main/java/com" ]; then
    echo "Backing up current mod to ModCollection..."
    CURRENT_JAR=$(ls build/libs/*.jar 2>/dev/null | head -1)
    if [ -f "$CURRENT_JAR" ]; then
        cp "$CURRENT_JAR" ModCollection/
    fi
fi

# Clean workspace
echo "Cleaning workspace..."
rm -rf src/main/java/com/*
rm -rf src/main/resources/assets/*
./gradlew clean

# Create new mod structure
echo "Creating new mod structure..."
mkdir -p "src/main/java/com/$MOD_PACKAGE"
mkdir -p "src/main/resources/assets/$MOD_PACKAGE"

echo "Workspace ready for: $MOD_NAME"
echo "Package: com.$MOD_PACKAGE"
```

## Building Multiple Mods Separately

If you need to build multiple mods and keep them all:

### Option 1: Use Separate Workspaces

```bash
# Create dedicated workspace for each mod
cp -r "Mod Developement/1.12.2-forge" "Mod Developement/1.12.2-forge-mod1"
cp -r "Mod Developement/1.12.2-forge" "Mod Developement/1.12.2-forge-mod2"
```

### Option 2: Build and Move Pattern

```bash
# Build mod 1
cd "Mod Developement/1.12.2-forge"
./gradlew clean build
cp build/libs/Mod1.jar ReadyMods/

# Clean and build mod 2
rm -rf src/main/java/com/*
# ... create mod 2 files ...
./gradlew clean build
cp build/libs/Mod2.jar ReadyMods/
```

### Option 3: Use ModCollection as Source of Truth

Keep completed mods in `ModCollection` and restore them when needed:

```bash
# Restore a previous mod for updates
cp -r "ModCollection/My-Mod-Src/com/mymod" "src/main/java/com/"
cp -r "ModCollection/My-Mod-Src/resources/"* "src/main/resources/"
```

## Verification Checklist

Before considering a mod "complete", verify:

- [ ] Only your mod's package exists in `src/main/java/com/`
- [ ] Only your mod's assets exist in `src/main/resources/assets/`
- [ ] `build.gradle` has correct `group` and `archivesBaseName`
- [ ] `mcmod.info` has correct `modid` and `name`
- [ ] `./gradlew clean build` succeeds
- [ ] Jar file contains only your mod's classes
- [ ] Test in Minecraft shows correct mod name and behavior
- [ ] No debug messages from other mods appear in logs

## Common Mistakes to Avoid

### ❌ Don't Do This:
```bash
# Creating new mod without cleaning
# Old files still present!
mkdir src/main/java/com/newmod
# Now both old and new mods compile together
```

### ✅ Do This Instead:
```bash
# Save old mod first
cp build/libs/Old-Mod.jar ModCollection/

# Clean workspace
rm -rf src/main/java/com/*

# Create new mod
mkdir src/main/java/com/newmod
```

### ❌ Don't Do This:
```bash
# Just updating build.gradle and mcmod.info
# Old source files still compile!
```

### ✅ Do This Instead:
```bash
# Remove old source files
rm -rf src/main/java/com/oldmod

# Update build files
# Create new source files
```

## Quick Reference Commands

### Clean Workspace Completely
```bash
cd "Mod Developement/1.12.2-forge"
rm -rf src/main/java/com/*
rm -rf src/main/resources/assets/*
./gradlew clean
```

### Verify Jar Contents
```bash
jar tf build/libs/Your-Mod.jar | grep "com/" | head -20
```

### Check for Multiple Mods in Jar
```bash
jar tf build/libs/Your-Mod.jar | grep "\.class$" | cut -d'/' -f1-3 | sort -u
```

If you see multiple package names, you have a conflict!

## Summary

The key principle: **One mod at a time in the workspace.**

1. Build current mod
2. Save to ModCollection
3. Clean workspace completely
4. Create next mod
5. Repeat

Following this workflow prevents source code conflicts and ensures each mod builds cleanly and independently.

## Related Documentation

- See `IDE_AGENT_INSTRUCTION_SHEET.txt` for the intended workflow
- See `SYSTEM_MANUAL.md` for build system details
- See `ModCollection/` for examples of preserved mod sources
