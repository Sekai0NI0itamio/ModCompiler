# Package Naming Standard

## Overview

All mods created in this project follow a consistent package naming convention to maintain organization and proper attribution.

## Standard Package Structure

### Format
```
asd.itamio.<modname>
```

### Components

1. **asd** - Top-level domain identifier
2. **itamio** - Author identifier
3. **modname** - Specific mod identifier (lowercase, no spaces)

## Author Information

**Author:** Itamio  
**Package Prefix:** `asd.itamio`

All mods created in this project are authored by Itamio and must use this package prefix.

## Examples

### Area Dig Mod
```
Package: asd.itamio.areadig
Main Class: asd.itamio.areadig.AreaDigMod
```

### No Hostile Mobs
```
Package: asd.itamio.nohostilemobs
Main Class: asd.itamio.nohostilemobs.NoHostileMobsMod
```

### Hypothetical "Super Tools" Mod
```
Package: asd.itamio.supertools
Main Class: asd.itamio.supertools.SuperToolsMod
```

## Directory Structure

### Source Files
```
src/main/java/
└── asd/
    └── itamio/
        └── modname/
            ├── ModNameMod.java (main class)
            ├── Config.java (if needed)
            ├── EventHandler.java (if needed)
            └── ... (other classes)
```

### Resources
```
src/main/resources/
├── mcmod.info (with authorList: ["Itamio"])
└── assets/
    └── modname/
        ├── lang/
        │   └── en_us.lang
        ├── textures/ (if needed)
        └── models/ (if needed)
```

## Build Configuration

### build.gradle
```gradle
version = "1.0.0"
group = "asd.itamio.modname"
archivesBaseName = "Mod-Name"
```

### mcmod.info
```json
{
  "modid": "modname",
  "name": "Mod Name",
  "authorList": ["Itamio"],
  ...
}
```

## Naming Rules

### Mod ID (modid)
- All lowercase
- No spaces
- No special characters except underscore
- Examples: `areadig`, `nohostilemobs`, `super_tools`

### Package Name
- All lowercase
- No spaces
- No special characters
- Must follow: `asd.itamio.<modid>`

### Main Class Name
- PascalCase
- Ends with "Mod"
- Examples: `AreaDigMod`, `NoHostileMobsMod`, `SuperToolsMod`

### Archive Base Name (jar file)
- PascalCase or kebab-case
- Can have spaces or hyphens
- Examples: `Area-Dig`, `No-Hostile-Mobs`, `Super-Tools`

## Complete Example: Creating "Block Breaker" Mod

### Step 1: Package Structure
```
src/main/java/asd/itamio/blockbreaker/
├── BlockBreakerMod.java
├── BlockBreakerConfig.java
└── BlockBreakHandler.java
```

### Step 2: Main Class
```java
package asd.itamio.blockbreaker;

import net.minecraftforge.fml.common.Mod;

@Mod(modid = BlockBreakerMod.MODID, 
     name = BlockBreakerMod.NAME, 
     version = BlockBreakerMod.VERSION)
public class BlockBreakerMod {
    public static final String MODID = "blockbreaker";
    public static final String NAME = "Block Breaker";
    public static final String VERSION = "1.0.0";
    
    // ... mod code ...
}
```

### Step 3: build.gradle
```gradle
version = "1.0.0"
group = "asd.itamio.blockbreaker"
archivesBaseName = "Block-Breaker"
```

### Step 4: mcmod.info
```json
{
  "modid": "blockbreaker",
  "name": "Block Breaker",
  "description": "Breaks blocks automatically",
  "version": "1.0.0",
  "mcversion": "1.12.2",
  "authorList": ["Itamio"],
  ...
}
```

### Step 5: Resources
```
src/main/resources/assets/blockbreaker/
└── lang/
    └── en_us.lang
```

## Why This Standard?

### Benefits

1. **Consistent Attribution**
   - All mods clearly attributed to Itamio
   - Easy to identify mod author

2. **Namespace Organization**
   - Prevents conflicts with other mods
   - Clear ownership hierarchy

3. **Professional Structure**
   - Follows Java package conventions
   - Matches industry standards

4. **Easy Maintenance**
   - Predictable file locations
   - Consistent naming across all mods

5. **Modrinth/CurseForge Ready**
   - Proper author attribution
   - Professional presentation

## Common Mistakes to Avoid

### ❌ Wrong Package Names
```java
package com.example.mymod;        // Wrong: not using asd.itamio
package com.mymod;                // Wrong: not using asd.itamio
package asd.itamio.MyMod;         // Wrong: capital letters in package
package asd.itamio.my-mod;        // Wrong: hyphens not allowed
```

### ✅ Correct Package Names
```java
package asd.itamio.mymod;         // Correct!
package asd.itamio.areadig;       // Correct!
package asd.itamio.nohostilemobs; // Correct!
```

### ❌ Wrong Author Attribution
```json
{
  "authorList": ["YourName"],     // Wrong
  "authorList": ["Example"],      // Wrong
  "authorList": [],               // Wrong
}
```

### ✅ Correct Author Attribution
```json
{
  "authorList": ["Itamio"],       // Correct!
}
```

## Checklist for New Mods

Before building a new mod, verify:

- [ ] Package starts with `asd.itamio.`
- [ ] Package name is all lowercase
- [ ] Mod ID matches package name (last part)
- [ ] Main class ends with "Mod"
- [ ] `build.gradle` group is `asd.itamio.<modname>`
- [ ] `mcmod.info` authorList is `["Itamio"]`
- [ ] Resources are in `assets/<modname>/`
- [ ] No old package names (com.example, etc.)

## Migration from Old Packages

If you have mods with old package names (like `com.areadig`), follow these steps:

### Step 1: Clean Workspace
```bash
cd "Mod Developement/1.12.2-forge"
./clean_workspace.sh
```

### Step 2: Create New Package Structure
```bash
mkdir -p src/main/java/asd/itamio/modname
```

### Step 3: Update All Files
- Change package declarations in all .java files
- Update build.gradle group
- Update mcmod.info authorList
- Move resources to correct location

### Step 4: Rebuild
```bash
./gradlew clean build
```

## Template for New Mods

Use this as a starting point:

```java
package asd.itamio.MODNAME;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = MODNAMEMod.MODID, 
     name = MODNAMEMod.NAME, 
     version = MODNAMEMod.VERSION)
public class MODNAMEMod {
    public static final String MODID = "MODNAME";
    public static final String NAME = "Mod Display Name";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info(NAME + " initialized");
    }
}
```

Replace `MODNAME` with your actual mod name (lowercase for package/modid, PascalCase for class name).

## Summary

**Standard Format:** `asd.itamio.<modname>`  
**Author:** Itamio  
**Always Use:** This exact format for all mods

This ensures consistency, proper attribution, and professional organization across all mods in the project.

## Related Documentation

- [QUICK_START_NEW_MOD.md](QUICK_START_NEW_MOD.md) - Creating new mods
- [LOCAL_MOD_DEVELOPMENT_WORKFLOW.md](LOCAL_MOD_DEVELOPMENT_WORKFLOW.md) - Development workflow
- [MODRINTH_PUBLISHING_GUIDE.md](MODRINTH_PUBLISHING_GUIDE.md) - Publishing to Modrinth
