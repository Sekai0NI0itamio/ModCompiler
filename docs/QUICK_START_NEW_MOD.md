# Quick Start: Creating a New Mod

## Before You Start

⚠️ **CRITICAL**: Always clean the workspace before starting a new mod to avoid source code conflicts!

## Quick Workflow

### 1. Save Current Mod (if any)

```bash
cd "Mod Developement/1.12.2-forge"

# Build current mod
./gradlew clean build

# Save jar to ModCollection
cp build/libs/*.jar ModCollection/

# Save source (optional but recommended)
CURRENT_MOD=$(ls src/main/java/com/ | head -1)
mkdir -p "ModCollection/${CURRENT_MOD}-Src"
cp -r src/main/java/com/${CURRENT_MOD} "ModCollection/${CURRENT_MOD}-Src/"
cp -r src/main/resources "ModCollection/${CURRENT_MOD}-Src/"
```

### 2. Clean Workspace

```bash
# Remove ALL old mod source files
rm -rf src/main/java/com/*

# Remove ALL old mod assets
rm -rf src/main/resources/assets/*

# Clean build artifacts
./gradlew clean
```

### 3. Create New Mod Structure

```bash
# Example: Creating "My New Mod" with package "mynewmod"
MOD_PACKAGE="mynewmod"

# Create Java package (IMPORTANT: Use asd.itamio prefix!)
mkdir -p "src/main/java/asd/itamio/${MOD_PACKAGE}"

# Create resources
mkdir -p "src/main/resources/assets/${MOD_PACKAGE}"
```

**IMPORTANT:** All mods must use the package structure `asd.itamio.<modname>` with author "Itamio".  
See [PACKAGE_NAMING_STANDARD.md](PACKAGE_NAMING_STANDARD.md) for details.

### 4. Update Build Configuration

Edit `build.gradle`:
```gradle
version = "1.0.0"
group = "asd.itamio.mynewmod"
archivesBaseName = "My-New-Mod"
```

Create `src/main/resources/mcmod.info`:
```json
[
{
  "modid": "mynewmod",
  "name": "My New Mod",
  "description": "Description here",
  "version": "1.0.0",
  "mcversion": "1.12.2",
  "url": "",
  "updateUrl": "",
  "authorList": ["Itamio"],
  "credits": "",
  "logoFile": "",
  "screenshots": [],
  "dependencies": []
}
]
```

### 5. Create Mod Files

Create your main mod class at:
`src/main/java/asd/itamio/mynewmod/MyNewMod.java`

```java
package asd.itamio.mynewmod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = MyNewMod.MODID, name = MyNewMod.NAME, version = MyNewMod.VERSION)
public class MyNewMod {
    public static final String MODID = "mynewmod";
    public static final String NAME = "My New Mod";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("My New Mod initialized");
    }
}
```

### 6. Build and Test

```bash
# Build
./gradlew clean build

# Verify jar contains only your mod
jar tf build/libs/My-New-Mod-1.0.0.jar | grep "com/" | head -10

# Copy to test location
mkdir -p ReadyMods
cp build/libs/My-New-Mod-1.0.0.jar ReadyMods/
```

### 7. Test in Minecraft

```bash
# Copy to Minecraft mods folder
cp ReadyMods/My-New-Mod-1.0.0.jar ~/Library/Application\ Support/minecraft/mods/
```

Launch Minecraft 1.12.2 with Forge and verify your mod loads correctly.

## Verification Checklist

Before testing, verify:

```bash
# Check only your package exists
ls src/main/java/asd/itamio/
# Should show ONLY: mynewmod

# Check jar contents
jar tf build/libs/My-New-Mod-1.0.0.jar | grep "\.class$" | head -5
# Should show ONLY classes from asd/itamio/mynewmod/

# Check for conflicts
jar tf build/libs/My-New-Mod-1.0.0.jar | grep "asd/itamio/" | cut -d'/' -f1-4 | sort -u
# Should show ONLY: asd/itamio/mynewmod
```

## Common Issues

### Issue: Old mod appears in logs
**Cause**: Old source files not removed  
**Fix**: Run cleanup commands from step 2

### Issue: Multiple mods in one jar
**Cause**: Multiple packages in `src/main/java/com/`  
**Fix**: Remove all packages except your current mod

### Issue: Wrong mod name in Minecraft
**Cause**: `mcmod.info` not updated  
**Fix**: Update `modid` and `name` in `mcmod.info`

### Issue: Build fails with class conflicts
**Cause**: Old build artifacts  
**Fix**: Run `./gradlew clean` before building

## One-Command Cleanup

Save this as `clean_workspace.sh`:

```bash
#!/bin/bash
echo "Cleaning workspace for new mod..."
cd "Mod Developement/1.12.2-forge"
rm -rf src/main/java/com/*
rm -rf src/main/resources/assets/*
./gradlew clean
echo "Workspace cleaned! Ready for new mod."
```

Make it executable:
```bash
chmod +x clean_workspace.sh
```

Use it:
```bash
./clean_workspace.sh
```

## Summary

**Golden Rule**: One mod at a time in the workspace.

1. ✅ Save current mod
2. ✅ Clean workspace completely  
3. ✅ Create new mod files
4. ✅ Update build config
5. ✅ Build and verify
6. ✅ Test in Minecraft

**Never skip step 2!** This prevents all source code conflicts.

## Need Help?

See `LOCAL_MOD_DEVELOPMENT_WORKFLOW.md` for detailed explanations and troubleshooting.
