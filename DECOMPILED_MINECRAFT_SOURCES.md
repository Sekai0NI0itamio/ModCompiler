# Decompiled Minecraft Sources System

## Overview

This repository now contains a comprehensive system for decompiling and storing Minecraft source code for ALL supported versions and loaders. This eliminates the need for on-demand decompilation and provides AI agents with immediate access to Minecraft APIs.

## Location

All decompiled sources are stored in: `decompiled-minecraft/`

After the initial decompilation run, the structure will be:

```
decompiled-minecraft/
  ├── 1.8.9-forge/
  ├── 1.12.2-forge/
  ├── 1.16.5-forge/
  ├── 1.16.5-fabric/
  ├── 1.17.1-forge/
  ├── 1.17.1-fabric/
  ├── 1.18.2-forge/
  ├── 1.18.2-fabric/
  ├── 1.19.4-forge/
  ├── 1.19.4-fabric/
  ├── 1.20.1-forge/
  ├── 1.20.1-fabric/
  ├── 1.20.1-neoforge/
  ├── 1.20.6-forge/
  ├── 1.20.6-fabric/
  ├── 1.20.6-neoforge/
  ├── 1.21.1-forge/
  ├── 1.21.1-fabric/
  ├── 1.21.1-neoforge/
  ├── 1.21.8-forge/
  ├── 1.21.8-fabric/
  ├── 1.21.8-neoforge/
  ├── 1.21.11-forge/
  ├── 1.21.11-fabric/
  ├── 1.21.11-neoforge/
  ├── README.md
  └── SUMMARY.md
```

## Initial Setup

To populate the decompiled sources for the first time:

1. Go to GitHub Actions: https://github.com/Sekai0NI0itamio/ModCompiler/actions/workflows/decompile-all-minecraft-versions.yml

2. Click "Run workflow"

3. Select:
   - Branch: `main`
   - commit_to_repo: `yes`

4. Click "Run workflow"

5. Wait ~2 hours for all 27 versions to decompile

6. The workflow will automatically commit the decompiled sources to the repository

## Usage for AI Agents

When fixing mod compilation errors, AI agents should:

### 1. Search for the class or method

```bash
grepSearch(
  query="class FarmBlock",
  includePattern="decompiled-minecraft/1.18.2-fabric/**/*.java"
)
```

### 2. Read the full class file

```bash
readFile(
  path="decompiled-minecraft/1.18.2-fabric/net/minecraft/world/level/block/FarmBlock.java",
  explanation="Reading FarmBlock to understand the fallOn method signature"
)
```

### 3. Use the actual API from the decompiled source

The decompiled source shows the EXACT method signatures, parameter types, and class names for that specific Minecraft version and loader.

## Common Search Patterns

### Find a specific class
```bash
grepSearch(
  query="class FarmBlock",
  includePattern="decompiled-minecraft/1.18.2-fabric/**/*.java"
)
```

### Find a method signature
```bash
grepSearch(
  query="void fallOn\\(",
  includePattern="decompiled-minecraft/1.18.2-fabric/**/*.java"
)
```

### Find event classes
```bash
grepSearch(
  query="class.*TrampleEvent",
  includePattern="decompiled-minecraft/1.21.6-forge/**/*.java"
)
```

### Find all classes in a package
```bash
grepSearch(
  query="package net\\.minecraft\\.world\\.level\\.block",
  includePattern="decompiled-minecraft/1.18.2-fabric/**/*.java"
)
```

## Workflow Details

**File**: `.github/workflows/decompile-all-minecraft-versions.yml`

**What it does**:
1. Runs 27 parallel jobs (one per version+loader combination)
2. Each job:
   - Sets up the correct Java version
   - Copies the template workspace
   - Runs `./gradlew genSources` to decompile Minecraft
   - Extracts the decompiled .java files
   - Uploads them as an artifact
3. Final job:
   - Downloads all 27 artifacts
   - Organizes them into `decompiled-minecraft/`
   - Creates README.md and SUMMARY.md
   - Commits everything to the repository

**Runtime**: ~2 hours (27 jobs × ~5 minutes each, running 5 in parallel)

**Disk usage**: ~500MB-1GB (decompiled sources are text files)

## Updating Sources

To regenerate all decompiled sources (e.g., after adding a new Minecraft version):

```bash
gh workflow run decompile-all-minecraft-versions.yml --field commit_to_repo=yes
```

Or manually trigger via GitHub Actions web interface.

## Benefits

1. **No on-demand decompilation**: Sources are pre-decompiled and ready to use
2. **Faster AI responses**: No waiting for Gradle to download and decompile
3. **Reliable**: Decompilation happens once in a controlled environment
4. **Searchable**: All sources are in the repository and can be searched with grep
5. **Version-specific**: Each version+loader has its own directory with exact APIs
6. **Offline-capable**: Sources are in the repo, no network needed

## Example: Solving the Seed Protect Issue

The Seed Protect mod failed to build for Fabric 1.18-1.19.3 because the FarmBlock class name and package changed between versions.

With decompiled sources in the repository:

1. Search for FarmBlock in Fabric 1.18.2:
   ```bash
   grepSearch(
     query="class.*Farm.*Block",
     includePattern="decompiled-minecraft/1.18.2-fabric/**/*.java"
   )
   ```

2. Find the actual class (might be FarmBlock, FarmlandBlock, or something else)

3. Read the full class to see the `fallOn` method signature

4. Use the exact class name and method signature in the mixin

This eliminates guesswork and ensures the mod compiles correctly.

## Troubleshooting

### Workflow fails to trigger
- GitHub's workflow_dispatch cache can take 5-10 minutes to update after pushing a new workflow
- Use the web interface instead: https://github.com/Sekai0NI0itamio/ModCompiler/actions

### Decompilation fails for a specific version
- Check `decompiled-minecraft/SUMMARY.md` for status
- Check `decompiled-minecraft/{version}-{loader}/metadata.json` for file count
- If file_count is 0, check `gradle-error.log` in that directory

### Sources not found
- Verify the workflow completed successfully
- Check that `commit_to_repo` was set to `yes`
- Pull the latest changes: `git pull origin main`

## Future Enhancements

- Add more Minecraft versions as they're released
- Include intermediate versions (1.18, 1.18.1, 1.19, etc.) for finer-grained API tracking
- Add a search index for faster lookups
- Generate API documentation from decompiled sources
