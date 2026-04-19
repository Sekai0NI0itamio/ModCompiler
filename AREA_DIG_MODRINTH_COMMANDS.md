# Area Dig - Modrinth Publishing Commands

## Quick Reference

Area Dig is in bundle **10** in `ToBeUploaded/10/`

## Command 1: Generate Bundle

Generate the Modrinth bundle metadata, description, and art:

```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 10
```

**What this does:**
- Analyzes the jar file
- **Uses local source from `Area-Dig-Src/`** (no decompilation needed!)
- Reads metadata from your `mcmod.info`
- Generates description and summary
- Creates icon and banner art
- Prepares Modrinth metadata files
- Outputs to `AutoCreateModrinthBundles/area-dig-1.0.0/`

**Why it's fast:**
Since we have the `Area-Dig-Src/` folder with source code, the script skips the GitHub decompilation workflow and uses our local source directly. This is much faster and more accurate!

## Command 2: Review and Verify

After generation, review the output:

```bash
# View summary
cat AutoCreateModrinthBundles/area-dig-1.0.0/SUMMARY.md

# View project metadata
cat AutoCreateModrinthBundles/area-dig-1.0.0/modrinth.project.json

# Check icon
open AutoCreateModrinthBundles/area-dig-1.0.0/icon.webp
```

If everything looks good, mark as verified:

```bash
echo "verified" > AutoCreateModrinthBundles/area-dig-1.0.0/verify.txt
```

## Command 3: Create Draft

Create the draft project on Modrinth:

```bash
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts \
  --only-bundle area-dig-1.0.0 \
  --verified \
  --create-via github
```

**Important Notes:**
- Use `--only-bundle 10` for generate (bundle NUMBER)
- Use `--only-bundle area-dig-1.0.0` for create-drafts (bundle SLUG)
- The slug is auto-generated from the jar filename

**What this does:**
- Creates draft project on Modrinth
- Uploads the jar file
- Sets all metadata
- Uploads icon
- Returns project URL

## Complete Workflow

```bash
# Step 1: Generate
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 10

# Step 2: Review
cat AutoCreateModrinthBundles/area-dig-1.0.0/SUMMARY.md

# Step 3: Verify
echo "verified" > AutoCreateModrinthBundles/area-dig-1.0.0/verify.txt

# Step 4: Create Draft
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts \
  --only-bundle area-dig-1.0.0 \
  --verified \
  --create-via github
```

## Expected Output

### After Generate:
```
Generating 1 Modrinth bundle(s) from /Users/.../ToBeUploaded...
Using local source from Area-Dig-Src/ (skipping decompilation)
Reading metadata from mcmod.info...
Generating description with AI...
Creating icon and banner art...
Complete: Area-Dig-1.0.0.jar -> AutoCreateModrinthBundles/area-dig-1.0.0
(art ready; review verify.txt before creating the Modrinth draft)
Finished generation: 1 ready, 0 skipped, 0 failed.
```

**Note:** Because we have `Area-Dig-Src/` folder, the script uses local source instead of triggering GitHub decompilation. This is much faster!

### After Create-Drafts:
```
Creating draft for area-dig-1.0.0 via GitHub...
✓ Draft project created
✓ Project URL: https://modrinth.com/mod/area-dig
✓ Draft state saved to draft_state.json
```

## Troubleshooting

### If bundle already exists:
```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --only-bundle 10 --force
```

### If not verified:
```bash
echo "verified" > AutoCreateModrinthBundles/area-dig-1.0.0/verify.txt
```

### Check bundle contents:
```bash
ls -la ToBeUploaded/10/
```

Should show:
- `Area-Dig-1.0.0.jar`
- `Area-Dig-Src/` (folder with source code)

## After Publishing

1. Go to Modrinth.com
2. Log in
3. Navigate to your drafts
4. Find "Area Dig"
5. Review and publish

Done! 🎉
