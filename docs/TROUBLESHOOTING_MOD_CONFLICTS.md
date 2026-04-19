# Troubleshooting: Mod Source Code Conflicts

## Symptoms of Source Code Conflicts

### Symptom 1: Wrong Mod Loads
You built "Area Dig" but Minecraft shows "Hostile Mobs" or another old mod.

**Example Log:**
```
[Server thread/INFO] [Hostile Mobs]: [DEBUG] Player joined - activating hostile mobs
```

**Cause**: Old mod source files are still in `src/main/java/com/` and got compiled into your jar.

### Symptom 2: Multiple Mods in One Jar
Your jar file is much larger than expected and contains classes from multiple mods.

**Check:**
```bash
jar tf build/libs/Your-Mod.jar | grep "com/" | cut -d'/' -f1-3 | sort -u
```

**Bad Output (conflict):**
```
com/areadig
com/hostilemobs
com/nohostilemobs
```

**Good Output (clean):**
```
com/areadig
```

### Symptom 3: Build Errors About Duplicate Classes
Gradle fails with errors about classes being defined multiple times.

**Cause**: Multiple mod main classes with `@Mod` annotation in the same build.

### Symptom 4: Wrong Mod Metadata
Minecraft shows the wrong mod name, description, or version in the mods list.

**Cause**: `mcmod.info` wasn't updated or multiple `mcmod.info` files exist.

## Diagnostic Commands

### Check What's in Your Source Directory
```bash
ls -la "Mod Developement/1.12.2-forge/src/main/java/com/"
```

**Clean Output (good):**
```
areadig/
```

**Conflicted Output (bad):**
```
areadig/
hostilemobs/
nohostilemobs/
```

### Check What's in Your Built Jar
```bash
jar tf "Mod Developement/1.12.2-forge/build/libs/Your-Mod.jar" | grep "\.class$"
```

Look for class files from multiple packages. If you see classes from old mods, you have a conflict.

### Check Package Names in Jar
```bash
jar tf "Mod Developement/1.12.2-forge/build/libs/Your-Mod.jar" | grep "com/" | cut -d'/' -f1-3 | sort -u
```

Should show ONLY your current mod's package.

### Check for Multiple mcmod.info Files
```bash
find "Mod Developement/1.12.2-forge/src/main/resources" -name "mcmod.info"
```

Should show only ONE file.

### Check for Multiple Asset Folders
```bash
ls "Mod Developement/1.12.2-forge/src/main/resources/assets/"
```

Should show only YOUR mod's asset folder.

## Solutions

### Solution 1: Complete Workspace Cleanup

This fixes 90% of conflicts:

```bash
cd "Mod Developement/1.12.2-forge"

# Remove ALL old mod source files
rm -rf src/main/java/com/*

# Remove ALL old mod assets
rm -rf src/main/resources/assets/*

# Remove old mcmod.info
rm -f src/main/resources/mcmod.info

# Clean build artifacts
./gradlew clean

# Now recreate your current mod files
```

### Solution 2: Selective Package Removal

If you know which old mod is causing issues:

```bash
# Remove specific old mod package
rm -rf "Mod Developement/1.12.2-forge/src/main/java/com/oldmodpackage"

# Remove specific old mod assets
rm -rf "Mod Developement/1.12.2-forge/src/main/resources/assets/oldmodpackage"

# Clean and rebuild
./gradlew clean build
```

### Solution 3: Verify and Rebuild

After cleaning, verify before building:

```bash
# Check source directory
echo "Source packages:"
ls src/main/java/com/

# Check assets
echo "Asset folders:"
ls src/main/resources/assets/

# Check mcmod.info
echo "mcmod.info content:"
cat src/main/resources/mcmod.info

# If all looks good, build
./gradlew clean build
```

### Solution 4: Start Fresh from Template

If conflicts persist, copy from the clean template:

```bash
# Backup your current mod source
cp -r "Mod Developement/1.12.2-forge/src/main/java/com/yourmod" /tmp/

# Reset workspace from template
rm -rf "Mod Developement/1.12.2-forge/src"
cp -r "1.12-1.12.2/forge/template/src" "Mod Developement/1.12.2-forge/"

# Clean template files
rm -rf "Mod Developement/1.12.2-forge/src/main/java/com/"*
rm -rf "Mod Developement/1.12.2-forge/src/main/resources/assets/"*

# Restore your mod
cp -r /tmp/yourmod "Mod Developement/1.12.2-forge/src/main/java/com/"

# Rebuild
./gradlew clean build
```

## Prevention

### Best Practice 1: Clean Before Starting New Mod

Always run this before creating a new mod:

```bash
cd "Mod Developement/1.12.2-forge"
rm -rf src/main/java/com/*
rm -rf src/main/resources/assets/*
./gradlew clean
```

### Best Practice 2: Save Completed Mods

Before cleaning, save your completed mod:

```bash
# Save jar
cp build/libs/Completed-Mod.jar ModCollection/

# Save source
mkdir -p ModCollection/Completed-Mod-Src
cp -r src/main/java/com/completedmod ModCollection/Completed-Mod-Src/
cp -r src/main/resources ModCollection/Completed-Mod-Src/
```

### Best Practice 3: Use Dedicated Build Folder

Store completed, tested mods in a separate folder:

```bash
mkdir -p "Mod Developement/1.12.2-forge/ReadyMods"
cp build/libs/Your-Mod.jar ReadyMods/
```

### Best Practice 4: Verify After Every Build

```bash
# After building, always check:
jar tf build/libs/Your-Mod.jar | grep "com/" | cut -d'/' -f1-3 | sort -u

# Should show ONLY your mod's package
```

## Real-World Example: The Three-Mod Conflict

### What Happened
User created three mods sequentially:
1. "Hostile Mobs" (package: com.hostilemobs)
2. "No Hostile Mobs" (package: com.nohostilemobs)  
3. "Area Dig" (package: com.areadig)

Without cleaning between mods, all three packages remained in `src/main/java/com/`:
```
src/main/java/com/
├── hostilemobs/
├── nohostilemobs/
└── areadig/
```

### Result
Every build compiled ALL THREE MODS into one jar:
- Area-Dig-1.0.0.jar contained all three mods
- Minecraft loaded "Hostile Mobs" because it was first alphabetically
- Logs showed debug messages from "Hostile Mobs"
- The actual "Area Dig" functionality didn't work

### The Fix
```bash
# Step 1: Save all three mods' source to ModCollection
mkdir -p ModCollection/Hostile-Mobs-Src
cp -r src/main/java/com/hostilemobs ModCollection/Hostile-Mobs-Src/

mkdir -p ModCollection/No-Hostile-Mobs-Src  
cp -r src/main/java/com/nohostilemobs ModCollection/No-Hostile-Mobs-Src/

mkdir -p ModCollection/Area-Dig-Src
cp -r src/main/java/com/areadig ModCollection/Area-Dig-Src/

# Step 2: Clean workspace completely
rm -rf src/main/java/com/*
rm -rf src/main/resources/assets/*
./gradlew clean

# Step 3: Build each mod separately

# Build No Hostile Mobs
cp -r ModCollection/No-Hostile-Mobs-Src/nohostilemobs src/main/java/com/
# Update build.gradle and mcmod.info
./gradlew clean build
cp build/libs/No-Hostile-Mobs-1.0.0.jar ReadyMods/

# Clean again
rm -rf src/main/java/com/*

# Build Area Dig  
cp -r ModCollection/Area-Dig-Src/areadig src/main/java/com/
# Update build.gradle and mcmod.info
./gradlew clean build
cp build/libs/Area-Dig-1.0.0.jar ReadyMods/
```

### Result After Fix
- Two clean, separate jar files
- Each jar contains only its own mod
- Both mods work correctly in Minecraft
- No conflicting log messages

## Quick Fix Script

Save this as `fix_conflicts.sh`:

```bash
#!/bin/bash

echo "=== Mod Conflict Fixer ==="
echo ""
echo "This will clean your workspace and prepare for a fresh build."
echo "Make sure you've saved any important work!"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 1
fi

cd "Mod Developement/1.12.2-forge"

echo "Removing old source files..."
rm -rf src/main/java/com/*

echo "Removing old assets..."
rm -rf src/main/resources/assets/*

echo "Cleaning build artifacts..."
./gradlew clean

echo ""
echo "=== Workspace Cleaned ==="
echo "You can now create your mod files without conflicts."
echo ""
echo "Next steps:"
echo "1. Create your mod package in src/main/java/com/"
echo "2. Update build.gradle"
echo "3. Update mcmod.info"
echo "4. Build with: ./gradlew clean build"
```

## Summary

**The Root Cause**: Not cleaning old mod source files before starting a new mod.

**The Symptom**: Multiple mods compile into one jar, wrong mod loads.

**The Fix**: Remove all old source files before creating a new mod.

**The Prevention**: Always clean the workspace between mods.

## Related Documentation

- `LOCAL_MOD_DEVELOPMENT_WORKFLOW.md` - Detailed workflow guide
- `QUICK_START_NEW_MOD.md` - Quick reference for starting new mods
- `IDE_AGENT_INSTRUCTION_SHEET.txt` - Overall project workflow
