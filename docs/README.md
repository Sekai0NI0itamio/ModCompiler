# ModCompiler Documentation

## Overview

This folder contains documentation for the ModCompiler project, which helps build and publish Minecraft mods across multiple versions.

## Documentation Index

### Core System Documentation

1. **[SYSTEM_MANUAL.md](SYSTEM_MANUAL.md)**
   - Complete system overview
   - GitHub Actions workflows
   - Build system architecture
   - Remote compilation process

2. **[IDE_AGENT_INSTRUCTION_SHEET.txt](IDE_AGENT_INSTRUCTION_SHEET.txt)**
   - Intended workflow for AI IDE assistants
   - Project goals and scope
   - Main operating rules
   - Folder structure explanation

3. **[FORGE_API_NOTES.md](FORGE_API_NOTES.md)**
   - Forge API reference notes
   - Version-specific API differences

4. **[MODRINTH_PUBLISHING_GUIDE.md](MODRINTH_PUBLISHING_GUIDE.md)** 📦 **PUBLISHING**
   - How to publish mods to Modrinth
   - Step-by-step workflow
   - Command examples and options
   - Real-world example (Area Dig mod)

5. **[AI_AGENT_MODRINTH_WORKFLOW.md](AI_AGENT_MODRINTH_WORKFLOW.md)** 🤖 **AI AGENT WORKFLOW** ⭐ **RECOMMENDED**
   - **For AI coding agents (Claude, ChatGPT, Kiro, etc.)**
   - Pre-generate metadata for instant uploads
   - 10x faster than standard workflow
   - **DEFAULT WORKFLOW for locally-developed mods**
   - Auto-detects source code (skips decompilation)

6. **[PACKAGE_NAMING_STANDARD.md](PACKAGE_NAMING_STANDARD.md)** 📛 **NAMING STANDARD**
   - Required package structure: `asd.itamio.<modname>`
   - Author attribution: Itamio
   - Naming conventions and rules
   - Complete examples and templates

### Local Development Guides

4. **[LOCAL_MOD_DEVELOPMENT_WORKFLOW.md](LOCAL_MOD_DEVELOPMENT_WORKFLOW.md)** ⭐ **START HERE**
   - **CRITICAL**: Proper workflow for developing multiple mods
   - How to avoid source code conflicts
   - Step-by-step process for sequential mod development
   - Best practices for workspace management

5. **[QUICK_START_NEW_MOD.md](QUICK_START_NEW_MOD.md)** ⚡ **QUICK REFERENCE**
   - Fast reference for starting a new mod
   - One-page workflow summary
   - Common commands and verification steps
   - Checklist for new mod creation

6. **[TROUBLESHOOTING_MOD_CONFLICTS.md](TROUBLESHOOTING_MOD_CONFLICTS.md)** 🔧 **PROBLEM SOLVING**
   - Diagnosing source code conflicts
   - Symptoms and solutions
   - Real-world examples
   - Diagnostic commands and fixes

## Quick Links by Task

### "I want to create a new mod"
→ Read: [QUICK_START_NEW_MOD.md](QUICK_START_NEW_MOD.md)  
→ Run: `./clean_workspace.sh` in the 1.12.2-forge directory

### "My mod is loading the wrong code"
→ Read: [TROUBLESHOOTING_MOD_CONFLICTS.md](TROUBLESHOOTING_MOD_CONFLICTS.md)  
→ Fix: Clean workspace and rebuild

### "I want to understand the full workflow"
→ Read: [LOCAL_MOD_DEVELOPMENT_WORKFLOW.md](LOCAL_MOD_DEVELOPMENT_WORKFLOW.md)  
→ Then: [IDE_AGENT_INSTRUCTION_SHEET.txt](IDE_AGENT_INSTRUCTION_SHEET.txt)

### "I want to build for multiple Minecraft versions"
→ Read: [SYSTEM_MANUAL.md](SYSTEM_MANUAL.md)  
→ Use: GitHub Actions workflows

### "I want to publish my mod to Modrinth"
→ Read: [MODRINTH_PUBLISHING_GUIDE.md](MODRINTH_PUBLISHING_GUIDE.md)  
→ Commands: `generate` then `create-drafts`

### "I'm an AI agent preparing a mod for upload" 🤖
→ Read: [AI_AGENT_MODRINTH_WORKFLOW.md](AI_AGENT_MODRINTH_WORKFLOW.md)  
→ **DEFAULT**: Generate metadata files, use `--use-ai-metadata` flag  
→ **10x faster** than standard workflow!

## Critical Issue: Source Code Conflicts

### The Problem

When developing multiple mods in the same workspace without cleaning between them, old mod source files remain and get compiled into new mods. This causes:

- Wrong mod loading in Minecraft
- Conflicting class definitions
- Incorrect mod metadata
- Debug messages from old mods

### The Solution

**Always clean the workspace before starting a new mod:**

```bash
cd "Mod Developement/1.12.2-forge"
./clean_workspace.sh
```

Or manually:
```bash
rm -rf src/main/java/com/*
rm -rf src/main/resources/assets/*
./gradlew clean
```

### Why This Matters

Example scenario:
1. Create "Mod A" → builds successfully
2. Create "Mod B" without cleaning → Mod A's files still present
3. Build "Mod B" → jar contains BOTH mods
4. Test in Minecraft → Mod A loads instead of Mod B

**Prevention**: Clean workspace between mods (see documentation above).

## Workflow Summary

### Local Development (First Version)

```
1. Clean workspace (if previous mod exists)
   ↓
2. Create mod in Mod Developement/1.12.2-forge/
   ↓
3. Build and test locally
   ↓
4. Save to ModCollection
   ↓
5. Prepare for Modrinth (optional)
```

### Multi-Version Compilation (GitHub)

```
1. Create mod source package
   ↓
2. Commit to incoming/
   ↓
3. Run GitHub Actions workflow
   ↓
4. Download built jars
   ↓
5. Publish to Modrinth (optional)
```

## Helper Scripts

### In `Mod Developement/1.12.2-forge/`:

- **`clean_workspace.sh`** - Clean workspace before new mod
- **`build_no_hostile_mobs.sh`** - Build No Hostile Mobs mod
- **`build_area_dig.sh`** - Build Area Dig mod

### In `scripts/`:

- **`auto_create_modrinth_draft_projects.py`** - Generate Modrinth drafts
- **`mod_compile.py`** - Remote compilation helper

## Common Commands

### Start a New Mod
```bash
cd "Mod Developement/1.12.2-forge"
./clean_workspace.sh
mkdir -p src/main/java/com/mymod
# ... create mod files ...
./gradlew clean build
```

### Check for Conflicts
```bash
# List packages in source
ls src/main/java/com/

# Check jar contents
jar tf build/libs/Your-Mod.jar | grep "com/" | cut -d'/' -f1-3 | sort -u
```

### Save Completed Mod
```bash
cp build/libs/Your-Mod.jar ModCollection/
mkdir -p ModCollection/Your-Mod-Src
cp -r src/main/java/com/yourmod ModCollection/Your-Mod-Src/
```

## Best Practices

1. ✅ **Always clean workspace before new mod**
2. ✅ **Save completed mods to ModCollection**
3. ✅ **Verify jar contents after building**
4. ✅ **Test in Minecraft before publishing**
5. ✅ **Use GitHub Actions for multi-version builds**
6. ❌ **Never skip workspace cleanup**
7. ❌ **Never have multiple mod packages in src simultaneously**

## Getting Help

1. Check the relevant documentation file above
2. Look for examples in `ModCollection/`
3. Review `examples/` for reference implementations
4. Check GitHub Actions logs for build errors

## Documentation Updates

This documentation was created to address a critical issue discovered during development where multiple mods were being compiled together due to not cleaning the workspace between mod creations.

**Date**: April 3, 2026  
**Issue**: Source code conflicts in local development  
**Solution**: Documented proper workflow and created cleanup tools

## Contributing

When adding new documentation:
1. Update this README with a link
2. Follow the existing format
3. Include practical examples
4. Add to the "Quick Links by Task" section if relevant

---

## Multi-Version Build Examples

These documents record real build sessions — challenges encountered, fixes applied, and lessons learned. Read them before starting a new multi-version port.

- **[SET_HOME_ANYWHERE_ALL_VERSIONS.md](examples/SET_HOME_ANYWHERE_ALL_VERSIONS.md)** — Complex SavedData API across 7 eras, 21 build runs, 39/39 ✓
- **[SORT_CHEST_ALL_VERSIONS.md](examples/SORT_CHEST_ALL_VERSIONS.md)** — Sorting mod, multiple loader/version combinations
- **[TOGGLE_SPRINT_ALL_VERSIONS.md](examples/TOGGLE_SPRINT_ALL_VERSIONS.md)** — Client-only mod, Fabric presplit vs split, 42 new versions ✓
- **[CRAFTABLE_SLIME_BALLS_ALL_VERSIONS.md](examples/CRAFTABLE_SLIME_BALLS_ALL_VERSIONS.md)** — Recipe-only mod, 61 versions, zero failures ✓
- **[COMMON_SERVER_CORE_ALL_VERSIONS.md](examples/COMMON_SERVER_CORE_ALL_VERSIONS.md)** — Server-side utility mod
- **[ALLOW_OFFLINE_LAN_JOIN_ALL_VERSIONS.md](examples/ALLOW_OFFLINE_LAN_JOIN_ALL_VERSIONS.md)** — Reflection-based server mod, 72 versions, Forge event API across all eras ✓

---

**Remember**: One mod at a time in the workspace. Clean between mods. Save completed work to ModCollection.
