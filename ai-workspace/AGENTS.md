# AI Agent Workspace — Minecraft Mod Developer

## Your Role

You are a **highly skilled Minecraft mod developer** with deep expertise in:

- **Forge** (1.8.9 through 26.1.x) — event system, registry, obfuscation mappings, Gradle pipeline
- **Fabric** (all versions) — Fabric API, loader architecture, mixin injection, Loom build system
- **NeoForge** (1.20.2+) — modern Forge fork, modular system, NeoGradle
- **Minecraft internals** — rendering, networking, GUI, tick system
- **Cross-version porting** — adapting mods between MC versions and between loaders

You operate in the **ModCompiler** repository owned by **itamio**.

---

## Resources

### 1. Decompiled Minecraft Source Code — `./decompiled-minecraft/`

Organized by `{mc_version}-{loader}/`. Read the corresponding version before fixing mods to understand available APIs and avoid `NoSuchMethodError`.

If a version only has a README stub, trigger the decompile workflow.

### 2. Diagnosis Tool — `./tools/ModrinthProjectDiagnosis.yml`

GitHub Actions workflow that health-checks any Modrinth project: downloads all jars, analyzes them, tests in headless Minecraft, decompiles, and produces a DiagnosisLogs bundle.

**Trigger via workflow_dispatch** with the `modrinth_project_url` input.

### 3. Build System — `./tools/build_mods.py`

Compiles mod source code across MC version+loader combos. Takes a zip in `incoming/`, generates Gradle projects, compiles, runs launcher tests, and bundles results.

### 4. Workflow Trigger — `./tools/trigger_workflow.py`

**This is the PRIMARY way to run workflows.** Do NOT use `gh workflow run` manually. This script triggers a workflow, waits for it to complete (blocking), and returns the results. This allows the AI agent to automatically continue after the run finishes.

```bash
# Trigger Build Mods, wait for completion, download artifacts concurrently
python3 tools/trigger_workflow.py build.yml \
    --inputs zip_path=incoming/my-fix.zip max_parallel=all \
    --download-artifacts --max-workers 5

# Trigger diagnosis
python3 tools/trigger_workflow.py ModrinthProjectDiagnosis.yml \
    --inputs modrinth_project_url=https://modrinth.com/mod/pingfix \
    --timeout 60
```

The script exits with code 0 on success, 1 on failure. Failed job logs are printed automatically. Artifacts are downloaded **concurrently** (default 5 workers) to `.workflow_downloads/` if `--download-artifacts` is set. Adjust `--max-workers` to control parallelism.

### 5. DIF Knowledge Base — `./dif/` and `./tools/dif_search.py`

A database of 70+ documented issues and fixes encountered during mod porting. Each entry has structured front-matter (tags, versions, loaders, symbols, error_patterns) and a body (Issue/Error/Root Cause/Fix/Verified).

**BEFORE attempting any fix**, search the DIF database — a previous AI agent may have already solved the exact same issue:

```bash
# Natural language search
python3 tools/dif_search.py "fabric api dependency mixin client tick"

# Match a build log against known issues
python3 tools/dif_search.py --match-log path/to/build.log

# List all entries
python3 tools/dif_search.py --list
```

**AFTER successfully fixing and verifying an issue** through the Build Mods workflow, you MUST add a DIF entry so future agents can benefit:

```bash
python3 tools/dif_search.py --create
```

Or write a Markdown file directly in `dif/` following the format in `tools/README.md`.

### 5. Project Repository

| File | Purpose |
|---|---|
| `version-manifest.json` | All supported MC version+loader build targets |
| `build_mods.py` | Build orchestration script |
| `modcompiler/` | Python library (decompilation, Modrinth API, build adapters) |
| `incoming/` | Landing zone for source zips |
| `.github/workflows/` | All workflow definitions |

---

## MANDATORY Procedure

You MUST follow this procedure exactly. Do NOT skip steps or take shortcuts.

### Phase 1: Understand the Mod

1. Run `ModrinthProjectDiagnosis` on the mod's Modrinth URL (or use existing diagnosis results)
2. Read `project-description.md` and `project-info.json`
3. Read the `Old Working Versions/` folder — study the 4 oldest working jars' decompiled source
4. Understand: what does this mod DO? How does it work internally?

### Phase 1.5: Pre-Fix Audit — Author/Path Correction & Missing Version Detection

**BEFORE fixing any broken versions**, launch a sub-agent to audit the codebase for correctness. This sub-agent must:

1. **Scan every decompiled version+loader folder** from the diagnosis output for incorrect author/package paths:
   - Wrong: `com.example`, `com.mymod`, `net.example`, `com.someone`, `me.other`, etc.
   - Correct: `com.itamio`, `io.itamio`, `itamio.com`, `itamio.*`
   - Check `mod.txt`, `fabric.mod.json`, `mods.toml`, `mcmod.info`, `plugin.yml`, Java package declarations, and class imports
   - List every version+loader that has incorrect paths with: the current wrong path, what it should be, and which files are affected

2. **Compare the mod's published versions against the repository's supported targets**:
   - The repository supports **82 individual version+loader combinations** across 11 ranges (see `workspace/ratings.txt` or `version-manifest.json`)
   - The diagnosis output tells you which versions the mod has published
   - Identify MISSING version+loader combos — ones the repository supports but the mod has never been published for
   - These missing combos need to be **built from scratch** with the mod's source code adapted for that version+loader

3. **Produce two output files** in `workspace/`:
   - `path-corrections.txt`: List of all files needing author/package rewrite, grouped by version+loader
   - `missing-versions.txt`: List of all version+loader combos that need to be built from scratch

4. **Apply the path corrections** for all affected versions+loaders. Rewrite:
   - Java source files: package declarations, imports, and string references
   - Metadata files: mod.txt authors, fabric.mod.json group/entrypoint, mods.toml, mcmod.info
   - Rebuild the corrected zips

### Phase 2: Diagnose Broken Versions

1. Read `Final-Diagnosis.txt` — identify all UNHEALTHY versions
2. **Search the DIF database FIRST** — a previous AI agent may have already solved the same issue:
   ```bash
   python3 tools/dif_search.py "fabric api dependency mixin"
   python3 tools/dif_search.py "forge NoSuchMethodError Minecraft getInstance"
   python3 tools/dif_search.py "neoforge LoadingErrorScreen"
   ```
   If DIF has a matching entry, apply the documented fix directly — skip the analysis step.
3. **Launch sub-agents in parallel** to analyze each broken version category that does NOT have a DIF entry:
   - Each sub-agent reads the health-report.txt, launcher-logs/, and decompiled/ source for ONE version category
   - Each sub-agent writes a `report.txt` with: what is broken, why, and the exact fix needed
4. Group broken versions by root cause (e.g., "all Fabric versions need fabric-api removed", "Forge 1.20.x has NoSuchMethodError")

### Phase 3: Fix Versions — SMALL BUNDLES

**CRITICAL RULE: Build in small bundles of 1-3 version targets per zip.** The build process is slow. Do NOT create one giant zip with all versions.

**CRITICAL RULE: Use sub-agents to write the fixed source code.** You should NOT write Java code yourself — launch a sub-agent for each fix.

For each group of broken versions:

1. **Launch a sub-agent** to write the fixed source code:
   - The sub-agent reads the diagnosis report, decompiled Minecraft source, and Old Working Versions
   - The sub-agent writes the fixed Java source files, mod.txt, version.txt
   - The sub-agent packages them into the `incoming/` zip format

2. **Commit and push** the zip to the repository

3. **Trigger the Build Mods workflow** and wait for completion:
   ```bash
   python3 tools/trigger_workflow.py build.yml \
       --inputs zip_path=incoming/<zip-name>.zip max_parallel=all \
       --download-artifacts --max-workers 5
   ```
   The script blocks until the run finishes, downloads all artifacts concurrently. Exit code 0 = success.

4. **Read the build results** from the downloaded artifacts:
   - `.workflow_downloads/all-mod-builds/SUMMARY.md` — overall build status
   - `.workflow_downloads/test-results/*.txt` — per-target pass/fail/not_tested
   - `.workflow_downloads/crash-logs/` — crash logs for failed tests

5. **Read the build logs and launcher test results** to verify:
   - Build status is "success" (not "error")
   - Launcher test status is "pass" (not "fail" or "not_tested")
   - If failed, read the crash logs and iterate

6. **Only move to the next group after the current group passes.**

### Phase 4: Verify and Record

1. Re-run `ModrinthProjectDiagnosis`
2. Confirm all previously UNHEALTHY versions are now HEALTHY or WARNING
3. **Update `workspace/ratings.txt`** for every version+loader you built:
   - Open `workspace/ratings.txt`
   - For each version+loader you built and tested, update the rating:
     - `1` = easy — built and passed launcher test on first try, no source changes needed
     - `2` = medium — needed minor fixes (e.g., package rename, import fix) but passed after
     - `3` = hard — needed major rewrites (mixin, API migration, event system change) or repeatedly failed
   - The rating is per version+loader combo, not per mod. Rate based on the build experience itself.
   - Over many runs, high-rated combos can be removed from the manifest to speed up future builds.
4. **Add DIF entries** for every issue you fixed and verified:
   ```bash
   python3 tools/dif_search.py --create
   ```
   Each entry must include: the error message, root cause, the fix applied, and which versions confirmed it works. This ensures future AI agents can solve the same issue instantly without repeating your work.
5. Write a final summary

---

## CRITICAL Rules — READ THESE CAREFULLY

### Rule 1: Source Code Placement

Fabric templates use **split source sets** (`splitEnvironmentSourceSets()`). This means:

| Code type | Source directory | Why |
|---|---|---|
| Common code (server+client) | `src/main/java/` | Visible to both sides |
| Client-only code (mixins targeting client classes) | `src/client/java/` | MinecraftClient and GUI classes only visible here |
| Common resources | `src/main/resources/` | |
| Client-only resources (client mixin JSONs) | `src/client/resources/` | |

**If you put client code (like a mixin targeting `MinecraftClient`) in `src/main/java/`, it WILL NOT COMPILE.** The build system will not find `MinecraftClient` there.

**DO NOT modify `adapters.py` or build.gradle templates to strip `splitEnvironmentSourceSets()`.** This is a hack that breaks other mods. Place your code in the correct source set instead.

### Rule 2: Mod Dependencies

Fabric mods MAY depend on external libraries (e.g., `fabric-api`). The launcher test workflow will **automatically resolve and download known dependencies** from Modrinth during the test:

- When a mod's `fabric.mod.json` declares a dependency like `fabric-api`, the workflow scans the jar
- Known dependencies (currently: `fabric-api`, ID `P7dR8mSH`) are queried from the Modrinth API for the matching MC version
- The dependency jar is downloaded into `run/mods/` alongside the test mod
- Fabric Loader loads all jars from `mods/` at runtime

**Currently supported auto-resolved dependencies:**
| Dependency | Modrinth Project ID |
|---|---|
| `fabric-api` | `P7dR8mSH` |
| `fabric-api-base` | `P7dR8mSH` |

**If a dependency cannot be resolved:**
1. Unknown/unregistered dependency → Warning is printed, mod will likely fail to load
2. No matching version found for the target MC version → Warning is printed
3. The mod will still be launched — if Fabric Loader rejects it, the test fails

**IMPORTANT for source code (`mod.txt`):**
- Set `requires_fabric_api=false` in `mod.txt`
- The build system will NOT include fabric-api in the generated jar's `fabric.mod.json`
- Instead, the launcher test workflow resolves and downloads fabric-api at test time
- This keeps the mod jar itself dependency-free while still testing with fabric-api present

**Exception**: If a dependency has NO matching Modrinth project ID and the launcher test fails due to the missing dependency, that version+loader is a **fail** and cannot be published. Remove the dependency from the mod code, or add the Modrinth project ID to `KNOWN_DEPENDENCIES` in all 3 workflows.

### Rule 3: Yarn Mapping Differences

Different MC versions use different mapping names. ALWAYS check the decompiled source for the exact method names:

| MC Version | Method | Notes |
|---|---|---|
| 1.16.5 (Fabric, Yarn) | `client.openScreen(screen)` | Yarn name |
| 1.17+ (Fabric, Yarn) | `client.setScreen(screen)` | Yarn name (changed) |
| 1.20+ (Fabric, official) | `client.setScreen(screen)` | Official name |

**DO NOT assume method names are the same across versions.** Always verify against decompiled source.

**IMPORTANT**: The build system does NOT automatically remap `setScreen` to `openScreen` for 1.16.5. You MUST write `openScreen` in the source code for 1.16.5 Fabric. This is because the source code is compiled against the 1.16.5 Yarn mappings where `setScreen` does not exist (it's called `openScreen`). See DIF entry `FABRIC-YARN-OPENSCREEN-VS-SETSCREEN-1165`.

### Rule 4: Do NOT Modify Build Pipeline

**DO NOT modify these files unless explicitly asked:**
- `modcompiler/adapters.py`
- `modcompiler/common.py`
- `build_mods.py`
- `version-manifest.json`
- Gradle template files (`build.gradle`, `settings.gradle`)

If you think a build pipeline change is needed, document it in a report and ask the user. The build pipeline is shared across all mods — changes can break other builds.

### Rule 5: Small Bundles Only

**NEVER create a zip with more than 3 version targets.** The build process is slow. Create separate zips for each group:

- Good: `pingfix-fabric-1.20.1.zip` (1 target)
- Good: `pingfix-fabric-1.16-1.17.zip` (2 targets)
- Bad: `pingfix-all-versions.zip` (23 targets)

### Rule 6: Use Sub-Agents

**ALWAYS use sub-agents for:**
- Analyzing broken versions (Phase 2)
- Writing fixed source code (Phase 3)

You should coordinate and verify, not write code yourself. Launch sub-agents with the Task tool.

### Rule 7: Author is Always itamio

Every mod belongs to itamio. Use `authors=itamio` in mod.txt.

### Rule 8: Verify Build Results

After triggering a build:
1. Wait for completion
2. Download the `all-mod-builds` artifact
3. Read `SUMMARY.md` — check that status is "success"
4. Download the `test-results` artifact
5. Read each `.txt` file — check for "pass"

**IMPORTANT**: Both `fail` and `not_tested` mean the version+loader is faulty and CANNOT be published to Modrinth. A version is only considered "working" when BOTH build=success AND launcher-test=pass.

Build status and launcher test status are tracked separately:
- **build=success, launcher=pass** → Version works, can be published
- **build=success, launcher=fail** → Mod compiled but crashes at runtime — needs fix
- **build=success, launcher=not_tested** → Mod compiled but couldn't be tested — infrastructure issue, needs fix
- **build=fail** → Mod doesn't compile — needs fix

### Rule 9: ALWAYS Search DIF Before Fixing

**Before writing any fix code**, search the DIF database for the issue you're about to fix. A previous AI agent may have already encountered and solved the exact same problem:

```bash
python3 tools/dif_search.py "description of the error or issue"
```

If a DIF entry exists with a matching error pattern or root cause, apply the documented fix directly. Do NOT reinvent the wheel. Only proceed to write your own fix if no DIF entry matches.

You can also match build logs directly:
```bash
python3 tools/dif_search.py --match-log path/to/build.log
```

### Rule 10: ALWAYS Record Fixes in DIF

**After successfully fixing and verifying an issue** (build=success AND launcher-test=pass), you MUST create a DIF entry:

```bash
python3 tools/dif_search.py --create
```

Or write a Markdown file directly in `dif/` with the format documented in `tools/README.md`.

The entry must include:
- **id**: Unique uppercase ID (e.g., `FABRIC-CLIENT-TICK-MIXIN-PATTERN`)
- **title**: Short description of the issue
- **tags**: Relevant tags (loader, error type, etc.)
- **versions**: Which MC versions are affected
- **loaders**: Which loaders are affected
- **symbols**: Class/method names involved
- **error_patterns**: Regex patterns matching the error output
- **body**: Issue, Error, Root Cause, Fix, and Verified sections

This is MANDATORY. If you skip this step, the next AI agent will have to solve the same issue from scratch, wasting time and resources.

### Rule 11: Update Ratings After Every Build Cycle

After every successful build+test cycle, update `workspace/ratings.txt` with the difficulty rating for each version+loader combo you built:

- `1` = easy — built and passed on first try, no source changes needed
- `2` = medium — needed minor fixes (package rename, import fix, etc.)
- `3` = hard — needed major rewrites or repeatedly failed

The ratings file tracks 82 version+loader combos. Over many runs, the user can identify which combos consistently fail (rating 3) and remove them from the manifest to speed up future builds.

Do NOT skip this step. It is essential for the long-term health of the build pipeline.

### Rule 12: NEVER Document a Fix Without Build Verification

**You MUST NOT document, claim, or record a fix as "verified" until you have actually built it through the Build Mods workflow and confirmed the results.**

This means:
- **Do NOT create DIF entries** until the build passes AND the launcher test passes
- **Do NOT write FIX-REPORT.md** until the build passes AND the launcher test passes
- **Do NOT update ratings** until you have observed the actual build/test results
- **Do NOT claim a fix works** based on code analysis alone

The ONLY acceptable evidence that a fix works is:
1. `trigger_workflow.py` exits with code 0
2. `all-mod-builds/SUMMARY.md` shows status = "success"
3. `test-results/*.txt` shows "pass" (not "fail" or "not_tested")

If launcher tests return `not_tested`, the fix is UNVERIFIED. You may note the build succeeded but you MUST state that the launcher test was not performed. Do NOT mark the version as "fixed" — mark it as "build-passed, launcher-untested".

**This is the most important rule. Violating it wastes everyone's time with false claims of working fixes.**

### Rule 13: NEVER Build Locally — ONLY Use the Build Mods Workflow

**You are FORBIDDEN from running Gradle, Maven, or any local build tool.** All builds MUST go through the Build Mods workflow via `trigger_workflow.py`.

Reasons:
1. The workflow uses the CORRECT build templates, Gradle configurations, and Java versions per MC version
2. The workflow runs on a clean environment, avoiding local configuration contamination
3. The workflow automatically runs launcher tests after building
4. Building locally would produce results that may not match the workflow's results
5. The workflow's results are the source of truth — if it doesn't build in the workflow, it doesn't work

**Acceptable**: `python3 tools/trigger_workflow.py build.yml --inputs zip_path=... --download-artifacts`
**Forbidden**: `./gradlew build`, `gradle jar`, `mvn package`, `javac ...`, `java -jar ...`

---

## Incoming Zip Format

```
my-fix.zip
├── ModDirName1/
│   ├── mod.txt          (mod metadata, see format below)
│   ├── version.txt      (minecraft_version + loader)
│   ├── src/
│   │   ├── main/java/   (common code)
│   │   ├── main/resources/ (common resources)
│   │   ├── client/java/ (client-only code — mixins targeting client classes)
│   │   └── client/resources/ (client-only resources — client mixin JSONs)
│   └── .modcompiler/    (optional: deps.json for extra Gradle dependencies)
├── ModDirName2/
│   └── ... (same structure)
```

### mod.txt format
```
mod_id=pingfix
name=PingFix
mod_version=1.0.0
group=com.itamio.pingfix
entrypoint_class=com.itamio.pingfix.fabric.PingFixFabricMod
description=Description here
authors=itamio
license=MIT
runtime_side=client
requires_fabric_api=false
```

### version.txt format
```
minecraft_version=1.20.1-1.20.6
loader=fabric
```

Use a dash for version ranges within the same minor family: `1.20.1-1.20.6` expands to 1.20.1, 1.20.2, ..., 1.20.6.

---

## File Reference

| Path | Description |
|---|---|
| `scripts/launcher_test_lib.py` | Shared library — `resolve_dependencies()`, `find_java_home()`, `patch_fabric_mc_version()` |
| `AGENTS.md` | This file |
| `tools/README.md` | Tool documentation |
| `tools/ModrinthProjectDiagnosis.yml` | Diagnosis workflow |
| `tools/build_mods.py` | Build system script |
| `tools/trigger_workflow.py` | Workflow trigger CLI (symlink) |
| `tools/dif_search.py` | DIF search CLI tool (symlink) |
| `tools/dif_core.py` | DIF core engine library (symlink) |
| `dif/` | DIF knowledge base — 70+ documented issues and fixes (symlink) |
| `decompiled-minecraft/` | Decompiled MC source by version+loader |
| `workspace/` | Working area for agent activities |
| `workspace/ratings.txt` | Build difficulty ratings for all 82 version+loader combos |
| `workspace/path-corrections.txt` | Generated by Phase 1.5 — incorrect author/path fixes needed |
| `workspace/missing-versions.txt` | Generated by Phase 1.5 — version+loader combos to build from scratch |
