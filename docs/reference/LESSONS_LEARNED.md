# Lessons Learned: Source Code Conflict Issue

## Date
April 3, 2026

## Issue Summary

During development of multiple mods ("No Hostile Mobs" and "Area Dig"), a critical workflow issue was discovered where old mod source files remained in the workspace and were compiled into new mods, causing conflicts and incorrect behavior.

## The Problem

### What Happened

1. User requested creation of "No Hostile Mobs" mod
2. Mod was created in `Mod Developement/1.12.2-forge/src/main/java/com/nohostilemobs/`
3. Mod compiled successfully
4. User then requested creation of "Area Dig" mod
5. New mod was created in `src/main/java/com/areadig/`
6. **Critical mistake**: Old `nohostilemobs` package was NOT removed
7. Additionally, an even older `hostilemobs` package was still present
8. Build compiled ALL THREE mods into one jar

### Symptoms Observed

When testing the "Area Dig" mod, the logs showed:
```
[Server thread/INFO] [Hostile Mobs]: [DEBUG] Player itamio joined - activating hostile mobs
[Server thread/INFO] [Hostile Mobs]: [DEBUG] Debug mode: ENABLED
[Server thread/INFO] [Hostile Mobs]: [DEBUG] Zombies only mode: ENABLED
```

This was from an old "Hostile Mobs" mod that should not have been present.

### Root Cause

The Gradle build system compiles ALL Java files in `src/main/java/`, regardless of package name. When multiple mod packages exist simultaneously:

```
src/main/java/com/
├── hostilemobs/      ← Old mod, should be removed
├── nohostilemobs/    ← Previous mod, should be removed
└── areadig/          ← Current mod, should be ONLY one present
```

All three get compiled into the same jar file, causing:
- Multiple `@Mod` annotations
- Conflicting mod IDs
- Wrong mod loading (alphabetically first mod loads)
- Incorrect behavior

## The Solution

### Immediate Fix

1. Removed old mod packages:
   ```bash
   rm -rf src/main/java/com/hostilemobs
   rm -rf src/main/java/com/nohostilemobs
   ```

2. Built each mod separately:
   - Restored No Hostile Mobs source
   - Built clean jar
   - Saved to ReadyMods/
   - Removed source
   - Restored Area Dig source
   - Built clean jar
   - Saved to ReadyMods/

3. Result: Two clean, separate jars that work correctly

### Long-Term Prevention

Created comprehensive documentation:

1. **LOCAL_MOD_DEVELOPMENT_WORKFLOW.md**
   - Detailed workflow for sequential mod development
   - Step-by-step cleanup process
   - Best practices

2. **QUICK_START_NEW_MOD.md**
   - Quick reference guide
   - One-page workflow
   - Verification checklist

3. **TROUBLESHOOTING_MOD_CONFLICTS.md**
   - Diagnostic commands
   - Symptoms and solutions
   - Real-world examples

4. **clean_workspace.sh**
   - Automated cleanup script
   - Interactive safety prompts
   - Clear instructions

5. Updated **IDE_AGENT_INSTRUCTION_SHEET.txt**
   - Added critical cleanup steps
   - References to new documentation

## Key Learnings

### For Developers

1. **Gradle compiles everything in src/**
   - Not just the "current" mod
   - Not just what's in build.gradle
   - EVERYTHING in the source tree

2. **One workspace = one mod at a time**
   - Cannot have multiple mod packages simultaneously
   - Must clean between mods
   - Save completed work before cleaning

3. **Verification is critical**
   - Check jar contents after building
   - Verify only one mod package in source
   - Test in Minecraft before considering complete

### For AI Assistants

1. **Always clean before new mod**
   - Check for existing packages
   - Remove old source files
   - Clean build artifacts

2. **Save before cleaning**
   - Copy jar to ModCollection
   - Save source to ModCollection
   - Preserve work before deletion

3. **Verify after building**
   - Check jar contents
   - Look for multiple packages
   - Confirm correct mod loads

### For Project Structure

1. **ModCollection is essential**
   - Serves as source history
   - Allows restoration of old mods
   - Prevents loss of work

2. **ReadyMods folder is useful**
   - Stores tested, working jars
   - Separates from build artifacts
   - Easy to find for testing

3. **Documentation prevents issues**
   - Clear workflows prevent mistakes
   - Examples show correct patterns
   - Troubleshooting guides save time

## Impact

### Before Fix
- Mods compiled together incorrectly
- Wrong mods loaded in Minecraft
- Confusing debug messages
- Wasted testing time

### After Fix
- Clean, separate mod jars
- Correct mod loading
- Clear build process
- Documented workflow

## Recommendations

### For Future Development

1. **Always run cleanup script first**
   ```bash
   ./clean_workspace.sh
   ```

2. **Follow the documented workflow**
   - See QUICK_START_NEW_MOD.md
   - Use verification checklist
   - Don't skip steps

3. **Verify before testing**
   ```bash
   jar tf build/libs/Your-Mod.jar | grep "com/" | cut -d'/' -f1-3 | sort -u
   ```

4. **Save completed work**
   ```bash
   cp build/libs/Your-Mod.jar ModCollection/
   ```

### For Project Maintenance

1. **Keep documentation updated**
   - Add new issues as discovered
   - Update workflows as they evolve
   - Include real examples

2. **Improve automation**
   - Consider pre-build checks
   - Add verification to build scripts
   - Warn about multiple packages

3. **Educate users**
   - Prominent warnings in README
   - Clear error messages
   - Easy-to-follow guides

## Metrics

### Time Impact
- Issue discovery: Immediate (during testing)
- Diagnosis: ~10 minutes
- Fix implementation: ~20 minutes
- Documentation creation: ~30 minutes
- Total: ~1 hour

### Files Created/Modified
- Created: 5 new documentation files
- Created: 1 cleanup script
- Modified: 1 instruction sheet
- Modified: 2 build.gradle files (during fix)

### Prevention Value
- Prevents: Hours of debugging for future users
- Clarifies: Proper workflow for sequential development
- Provides: Clear troubleshooting steps

## Conclusion

This issue highlighted a critical gap in the development workflow documentation. While the system was designed for sequential mod development, the proper cleanup process between mods was not explicitly documented or enforced.

The solution involved:
1. Fixing the immediate issue (separating the mods)
2. Documenting the proper workflow
3. Creating automation tools
4. Updating project documentation

This ensures future developers (human or AI) will follow the correct process and avoid this common pitfall.

## Related Documentation

- [LOCAL_MOD_DEVELOPMENT_WORKFLOW.md](LOCAL_MOD_DEVELOPMENT_WORKFLOW.md)
- [QUICK_START_NEW_MOD.md](QUICK_START_NEW_MOD.md)
- [TROUBLESHOOTING_MOD_CONFLICTS.md](TROUBLESHOOTING_MOD_CONFLICTS.md)
- [README.md](README.md)

## Status

✅ Issue resolved  
✅ Documentation created  
✅ Prevention measures implemented  
✅ Workflow clarified  
✅ Tools provided  

---

## Lesson: Never Rebuild Already-Green Targets

**Date**: April 23, 2026

When iterating on a multi-version build, it is highly recommended to ONLY
rebuild the targets that actually failed. Rebuilding already-successful targets:

- Wastes GitHub Actions minutes
- Delays results unnecessarily
- Can cause Modrinth publish to skip already-uploaded versions

**The correct approach:**

1. After a partial build, identify only the failed targets
2. Fix only those targets in the generator script
3. Use `--failed-only` flag when regenerating the bundle
4. Already-green targets from previous runs are already published — leave them alone

If you need to publish already-built jars without rebuilding, use the
`Publish Modrinth Bundle` workflow with the run ID of the successful run.

**No further action required.**
