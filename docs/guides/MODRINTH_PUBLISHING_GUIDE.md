# Modrinth Publishing Guide

## Overview

This guide explains how to publish mods to Modrinth using the automated draft creation system.

## Prerequisites

1. Completed mod jar file
2. Source code for the mod
3. Modrinth account (for publishing)
4. Modrinth API token stored in repository secrets (for GitHub publishing)

## Step-by-Step Process

### Step 1: Prepare the Bundle

Create a bundle folder in `ToBeUploaded/` with:
- One jar file (your compiled mod)
- One source folder (your mod's source code)

**Example Structure:**
```
ToBeUploaded/10/
├── Area-Dig-1.0.0-FINAL.jar
└── Area-Dig-Src/
    └── src/
        └── main/
            ├── java/
            │   └── com/
            │       └── areadig/
            │           ├── AreaDigMod.java
            │           ├── EnchantmentAreaDig.java
            │           └── BlockBreakHandler.java
            └── resources/
                ├── mcmod.info
                └── assets/
                    └── areadig/
                        └── lang/
                            └── en_us.lang
```

### Step 2: Generate the Modrinth Bundle

This command analyzes your mod, decompiles it if needed, generates descriptions, creates art, and prepares all Modrinth metadata.

**Command:**
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate
```

**To target only a specific bundle:**
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 10
```

**What This Does:**
1. Uploads jar to GitHub for remote decompilation
2. Extracts mod metadata (name, version, description)
3. Generates AI-powered description and summary
4. Creates icon and banner art
5. Prepares `modrinth.project.json` and `modrinth.version.json`
6. Creates `verify.txt` for manual review
7. Outputs to `AutoCreateModrinthBundles/<slug>/`

**Output Location:**
```
AutoCreateModrinthBundles/area-dig-1.0.0-final/
├── modrinth.project.json    # Project metadata
├── modrinth.version.json    # Version metadata
├── verify.txt               # Verification status
├── SUMMARY.md              # Generation summary
├── icon.webp               # Generated icon
├── art/                    # Generated artwork
├── source/                 # Source code
└── input/                  # Original jar
```

### Step 3: Review the Generated Bundle

Check the generated files:

```bash
# View the summary
cat AutoCreateModrinthBundles/area-dig-1.0.0-final/SUMMARY.md

# View project metadata
cat AutoCreateModrinthBundles/area-dig-1.0.0-final/modrinth.project.json

# View version metadata
cat AutoCreateModrinthBundles/area-dig-1.0.0-final/modrinth.version.json

# Check the icon
open AutoCreateModrinthBundles/area-dig-1.0.0-final/icon.webp
```

**What to Verify:**
- [ ] Title is correct
- [ ] Summary is accurate
- [ ] Description doesn't invent features
- [ ] Categories are appropriate
- [ ] Game versions are correct
- [ ] Loader (forge/fabric) is correct
- [ ] Icon looks good

### Step 4: Mark as Verified

Once you've reviewed and approved the bundle:

```bash
echo "verified" > AutoCreateModrinthBundles/area-dig-1.0.0-final/verify.txt
```

### Step 5: Create the Modrinth Draft

This command creates the actual draft project on Modrinth.

**Command (via GitHub - Recommended):**
```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts \
  --only-bundle area-dig-1.0.0-final \
  --verified \
  --create-via github
```

**Command (Direct API - Alternative):**
```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts \
  --only-bundle area-dig-1.0.0-final \
  --verified
```

**What This Does:**
1. Checks that bundle is marked as verified
2. Creates draft project on Modrinth
3. Uploads the jar file
4. Sets all metadata (title, description, categories, etc.)
5. Uploads icon
6. Creates initial version entry
7. Saves draft state to `draft_state.json`

**Output:**
```
Creating Modrinth draft for: area-dig-1.0.0-final
✓ Project created: https://modrinth.com/mod/area-dig
✓ Version uploaded: 1.0.0-FINAL
✓ Draft saved to draft_state.json
```

## Real-World Example: Area Dig Mod

### Complete Workflow (Recommended for Local Development)

Since we developed Area Dig locally and already have the source code, we can skip the remote decompilation step:

```bash
# 1. Prepare bundle (already done in ToBeUploaded/10/)
ls ToBeUploaded/10/
# Output: Area-Dig-1.0.0.jar  Area-Dig-Src/

# 2. Generate Modrinth bundle (using bundle number, skips decompile)
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 10

# Note: Since we have Area-Dig-Src/ folder with source code, 
# the script will use it directly instead of decompiling the jar.
# This is faster and more accurate!

# 3. Review the generated bundle
cat AutoCreateModrinthBundles/area-dig-1.0.0/SUMMARY.md

# 4. Mark as verified
echo "verified" > AutoCreateModrinthBundles/area-dig-1.0.0/verify.txt

# 5. Create draft on Modrinth via GitHub (using bundle slug)
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts \
  --only-bundle area-dig-1.0.0 \
  --verified \
  --create-via github
```

### Why This Works

When you have a source folder in your bundle (like `Area-Dig-Src/`), the script automatically:
- ✅ Uses your local source code
- ✅ Skips the GitHub decompilation workflow
- ✅ Reads metadata from your `mcmod.info`
- ✅ Generates descriptions based on your actual code
- ✅ Faster processing (no remote workflow wait time)

This is the **recommended approach** for mods developed locally in the 1.12.2 workspace!

### Expected Output

**After Generate (with local source):**
```
Generating 1 Modrinth bundle(s)...
Using local source from Area-Dig-Src/
Reading metadata from mcmod.info...
Complete: Area-Dig-1.0.0.jar -> AutoCreateModrinthBundles/area-dig-1.0.0
(art ready; review verify.txt before creating the Modrinth draft)
Finished generation: 1 ready, 0 skipped, 0 failed.
```

**After Generate (without local source - requires decompile):**
```
Uploaded 1 jar(s) to GitHub on temporary branch...
Generating 1 Modrinth bundle(s)...
Dispatching GitHub decompile workflow for Area-Dig-1.0.0.jar...
Waiting for decompilation to complete...
Complete: Area-Dig-1.0.0.jar -> AutoCreateModrinthBundles/area-dig-1.0.0
(art ready; review verify.txt before creating the Modrinth draft)
Finished generation: 1 ready, 0 skipped, 0 failed.
```

**After Create-Drafts:**
```
Creating draft for area-dig-1.0.0 via GitHub...
✓ Draft project created
✓ Project URL: https://modrinth.com/mod/area-dig
✓ Draft state saved
```

## Local Source vs Remote Decompilation

### When to Use Local Source (Recommended)

Use local source when:
- ✅ You developed the mod locally (like in `Mod Developement/1.12.2-forge/`)
- ✅ You have the original source code
- ✅ You want faster processing
- ✅ You want more accurate metadata

**Setup:**
```
ToBeUploaded/10/
├── Area-Dig-1.0.0.jar
└── Area-Dig-Src/          ← Include this!
    └── src/
        └── main/
            ├── java/
            └── resources/
```

**Benefits:**
- No GitHub workflow needed
- Faster (seconds vs minutes)
- Uses your actual source code
- Reads your actual mcmod.info
- More accurate descriptions

### When Remote Decompilation Happens

Remote decompilation only happens when:
- ❌ No source folder is provided
- ❌ Only a jar file exists in the bundle

**Setup:**
```
ToBeUploaded/10/
└── Area-Dig-1.0.0.jar     ← Only jar, no source
```

**Process:**
1. Uploads jar to GitHub
2. Triggers decompile workflow
3. Waits for completion
4. Downloads decompiled source
5. Extracts metadata

This is slower but works for jars without source code.

## Command Reference

### Generate Command

**Basic:**
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate
```

**Options:**
- `--only-bundle <number>` - Process only specific bundle number
- `--force` - Regenerate even if bundle already exists

**Examples:**
```bash
# Generate all bundles
python3 scripts/auto_create_modrinth_draft_projects.py generate

# Generate only bundle 10 (Area Dig)
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 10

# Generate only bundle 8
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 8

# Force regenerate bundle 10
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 10 --force
```

### Create-Drafts Command

**Basic:**
```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --verified
```

**Options:**
- `--only-bundle <slug>` - Create draft only for specific bundle slug
- `--verified` - Only process bundles marked as verified
- `--create-via github` - Use GitHub API for creation (recommended)

**Examples:**
```bash
# Create all verified drafts
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --verified

# Create draft for specific bundle via GitHub
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts \
  --only-bundle area-dig-1.0.0-final \
  --verified \
  --create-via github

# Create draft for specific bundle via direct API
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts \
  --only-bundle area-dig-1.0.0-final \
  --verified
```

## Bundle Slug Format

The bundle slug is automatically generated from the jar filename:
- `Area-Dig-1.0.0-FINAL.jar` → `area-dig-1.0.0-final`
- `No-Hostile-Mobs-1.0.0.jar` → `no-hostile-mobs-1.0.0`

Use the slug (not the bundle number) with `--only-bundle` in create-drafts.

## Verification Workflow

### verify.txt States

**pending** (default):
```
pending

Replace the first line with:
verified
```

**verified** (ready for draft creation):
```
verified
```

### Verification Checklist

Before marking as verified:
- [ ] Title matches mod name
- [ ] Summary is concise and accurate
- [ ] Description explains features clearly
- [ ] No invented or exaggerated features
- [ ] Categories are appropriate
- [ ] Side support (client/server) is correct
- [ ] Game versions are correct
- [ ] Loader (forge/fabric/neoforge) is correct
- [ ] Icon looks professional
- [ ] License is correct

## After Draft Creation

### On Modrinth

1. Log into Modrinth
2. Go to your profile → Drafts
3. Find your mod draft
4. Review all information
5. Add additional screenshots if desired
6. Add additional description sections
7. Click "Publish" when ready

### Draft State File

The `draft_state.json` file tracks the draft:
```json
{
  "project_id": "abc123",
  "project_slug": "area-dig",
  "version_id": "xyz789",
  "created_at": "2026-04-03T14:00:00Z",
  "status": "draft"
}
```

## Troubleshooting

### Issue: "Bundle already exists"
**Solution:** Use `--force` to regenerate
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 10 --force
```

### Issue: "Bundle not verified"
**Solution:** Mark bundle as verified
```bash
echo "verified" > AutoCreateModrinthBundles/<slug>/verify.txt
```

### Issue: "Modrinth API token invalid"
**Solution:** Set up `MODRINTH_TOKEN` repository secret in GitHub

### Issue: "GitHub wiki sync failed"
**Solution:** This is a warning, not an error. The draft will still be created.

## Best Practices

1. **Always include source folder for local mods**
   - Faster processing (no GitHub workflow)
   - More accurate metadata
   - Uses your actual code and mcmod.info

2. **Use --only-bundle for single mods**
   - Prevents processing other bundles
   - Faster execution
   - Clearer output

3. **Use GitHub creation method**
   - More reliable
   - Better error handling
   - Recommended approach

4. **Keep source organized**
   - Include all source files
   - Maintain proper structure (`src/main/java`, `src/main/resources`)
   - Include mcmod.info with correct metadata

5. **Test locally first**
   - Build and test mod
   - Verify it works
   - Then create draft

6. **For locally developed mods (recommended workflow)**
   ```
   ToBeUploaded/<number>/
   ├── Mod-Name-1.0.0.jar        ← Built jar
   └── Mod-Name-Src/             ← Your source code
       └── src/
           └── main/
               ├── java/
               │   └── asd/itamio/modname/
               └── resources/
                   ├── mcmod.info
                   └── assets/
   ```
   This setup uses local source (fast, accurate)

## Summary

**Two-Command Workflow:**

```bash
# 1. Generate bundle
python3 scripts/auto_create_modrinth_draft_projects.py generate

# 2. Create draft (after verification)
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts \
  --only-bundle <slug> \
  --verified \
  --create-via github
```

That's it! Your mod is now a draft on Modrinth, ready for final review and publishing.

## Related Documentation

- [IDE_AGENT_INSTRUCTION_SHEET.txt](IDE_AGENT_INSTRUCTION_SHEET.txt) - Overall workflow
- [SYSTEM_MANUAL.md](SYSTEM_MANUAL.md) - Build system details
- [LOCAL_MOD_DEVELOPMENT_WORKFLOW.md](LOCAL_MOD_DEVELOPMENT_WORKFLOW.md) - Local development
