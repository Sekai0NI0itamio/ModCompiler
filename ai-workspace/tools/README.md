# AI Workspace — Tools

This directory contains references and copies of the key tools available in the ModCompiler project for diagnosing and fixing Minecraft mods.

---

## ModrinthProjectDiagnosis.yml

**Location:** `.github/workflows/ModrinthProjectDiagnosis.yml` (original) or `./tools/ModrinthProjectDiagnosis.yml` (copy)

**Purpose:** Complete health check for any Modrinth mod project.

**What it does:**
- Fetches all published versions from a Modrinth project
- Selects the latest jar per (loader, MC version) slot
- Analyzes each jar: class count, method count, mod metadata, shell detection
- Tests every working jar in a headless Minecraft instance
- Captures crash reports, game logs, stdout/stderr for every test
- Decompiles every jar using Vineflower
- Downloads the 4 oldest working versions as reference
- Fetches full project metadata (description, body, categories, links)
- Produces a complete DiagnosisLogs bundle

**Output bundle structure:**
```
DiagnosisLogs/
├── Final-Diagnosis.txt          — Health summary table (HEALTHY/WARNING/UNHEALTHY/SHELL/MISSING)
├── project-info.json            — Machine-readable project metadata
├── project-description.md       — Human-readable project description
├── recommendation.txt           — AI agent step-by-step guide
├── Old Working Versions/        — Decompiled oldest 4 working versions
│   ├── v1-<version>-<date>/     — Per-version folder with jar/ and decompiled/
│   ├── v2-.../
│   ├── v3-.../
│   └── v4-.../
└── <version>-<loader>-<mc>/     — Per-version health folder
    ├── health-report.txt        — Health score, crash summary, skip reason
    ├── metadata.txt             — Version metadata
    ├── jar/                     — Original jar file
    ├── decompiled/              — Decompiled Java source code
    └── launcher-logs/           — Game logs, crash reports, stdout/stderr
```

**How to run:** Trigger via `workflow_dispatch` on GitHub Actions with the `modrinth_project_url` input. The workflow runs in parallel across all version+loader slots (~2-3 minutes total).

---

## trigger_workflow.py

**Location:** `scripts/trigger_workflow.py` (original) or `./tools/trigger_workflow.py` (symlink)

**Purpose:** Trigger a GitHub Actions workflow, wait for it to complete, and return the results. This is the PRIMARY way to run workflows — do NOT use `gh workflow run` manually. This script blocks until the run finishes, so the AI agent can automatically continue processing after the run completes.

**Prerequisites:**
- `gh` CLI must be installed and authenticated (`gh auth status`)
- Or provide a GitHub token via `--token` or `GH_TOKEN`/`GITHUB_TOKEN` env var

**Usage:**
```bash
# Trigger Build Mods, wait for completion, download artifacts
python3 tools/trigger_workflow.py build.yml \
    --inputs zip_path=incoming/my-fix.zip max_parallel=all \
    --download-artifacts

# Trigger ModrinthProjectDiagnosis
python3 tools/trigger_workflow.py ModrinthProjectDiagnosis.yml \
    --inputs modrinth_project_url=https://modrinth.com/mod/pingfix \
    --timeout 60
```

**What it does:**
1. Triggers the workflow via `gh workflow run`
2. Finds the run ID
3. Polls job status every N seconds (default: 15)
4. Prints job progress as it runs
5. When complete, shows failed job logs (if any)
6. Downloads all artifacts (if `--download-artifacts`)
7. Exits with code 0 on success, 1 on failure

**After the run completes**, the AI agent should:
1. Read the downloaded artifacts (especially `all-mod-builds/SUMMARY.md` and `test-results/*.txt`)
2. If any tests failed, read the `crash-logs/` artifact
3. Rate the versions in `workspace/ratings.txt`
4. If the fix was successful, add DIF entries

**Options:**
| Flag | Default | Description |
|---|---|---|
| `--inputs KEY=VALUE ...` | (required) | Workflow input parameters |
| `--ref REF` | current branch | Git ref to run on |
| `--timeout MINUTES` | 60 | Max wait time |
| `--poll-interval SECONDS` | 15 | Status check interval |
| `--download-artifacts` | off | Download artifacts after completion |
| `--artifact-dir DIR` | .workflow_downloads | Where to save artifacts |
| `--repo OWNER/REPO` | from git remote | Target repository |

---

## build_mods.py

**Location:** `build_mods.py` (original) or `./tools/build_mods.py` (copy)

**Purpose:** Compiles mod source code across all supported MC version+loader combinations.

**What it does:**
- Takes a zip file of mod source code (placed in `incoming/` directory)
- Reads `version-manifest.json` to determine which targets to build
- Generates Gradle projects for each (slug, mc_version, loader) combo
- Compiles using the correct Java version per target (parallel matrix)
- Runs built jars through headless Minecraft launcher tests (parallel matrix)
- Bundles all results into a combined artifact

**Build workflow pipeline (build.yml):**
```
prepare -> build (matrix) -> bundle
                            \-> prepare-tests -> launcher-test (matrix, max-parallel:20) -> collect-tests -> publish-modrinth
```

All build and test jobs run in parallel. Each launcher test runs in its own job with:
- Per-test Java version (only installs what's needed)
- Per-test Minecraft version cache
- Per-test HeadlessMC cache
- 120s timeout per test (background launch + log detection)

**How to trigger:**
1. Package fixed source code into a zip (SMALL BUNDLES: 1-3 targets per zip)
2. Place in `incoming/` directory (or commit and push)
3. Trigger `build.yml` workflow:
   ```bash
   gh workflow run build.yml -f zip_path=incoming/my-fix.zip -f max_parallel=all
   ```
4. Optionally set `modrinth_project_url` to publish on success

**Key commands:**
```bash
# Prepare build matrix from a source zip
python3 build_mods.py prepare \
  --zip-path incoming/my-fix.zip \
  --manifest version-manifest.json \
  --output-dir .workflow_state/prepared

# Build one specific target from the matrix
python3 build_mods.py build-one \
  --plan .workflow_state/prepared/build-plan.json \
  --manifest version-manifest.json \
  --prepared-dir .workflow_state/prepared \
  --slug <target-slug> \
  --artifact-dir .workflow_artifacts/per-mod/<slug>

# Bundle all build results into a combined artifact
python3 build_mods.py bundle \
  --artifacts-root .workflow_artifacts \
  --output-dir .workflow_artifacts/combined
```

**Artifacts produced:**
- `prepared-inputs` — Build matrix and plan
- `mod-{slug}` — Per-target build result (jar + result.json)
- `all-mod-builds` — Combined build results with SUMMARY.md
- `test-result-{slug}` — Per-target launcher test result
- `test-results` — Combined test results (pass/fail/not_tested per slug)
- `crash-logs` — Combined crash logs from failed tests
- `modrinth-publish` — Publishing results (if modrinth_project_url was set)

---

## DIF — Documentary of Issues and Fixes

**Location:** `./dif/` (symlink to `../dif/`), `./tools/dif_core.py`, `./tools/dif_search.py`

**Purpose:** A knowledge base of issues encountered during mod porting, their root causes, and verified fixes. The DIF system allows AI agents to learn from previous fixes and avoid repeating the same mistakes.

**What it does:**
- Stores structured issue/fix documents as Markdown files with YAML front-matter
- Each entry has: ID, title, tags, versions, loaders, symbols, error_patterns, and body (Issue/Error/Root Cause/Fix/Verified sections)
- NLP search engine for natural language queries
- Build log matcher — match error output against known issues
- Run scanner — scan an entire build run for DIF matches

**How to search (BEFORE attempting a fix):**
```bash
# Natural language search
python3 tools/dif_search.py "fabric api dependency mixin client tick"

# Search for a specific error
python3 tools/dif_search.py "cannot find symbol EventBusSubscriber"

# Match a build log against known issues
python3 tools/dif_search.py --match-log path/to/build.log

# List all entries
python3 tools/dif_search.py --list
```

**How to add a new entry (AFTER successfully fixing and verifying):**
```bash
python3 tools/dif_search.py --create
```

Or create a Markdown file directly in `dif/` following this format:
```markdown
---
id: UNIQUE-ID
title: Short human-readable title
tags: [forge, fabric, compile-error, api-change]
versions: [1.21.6, 1.21.7]
loaders: [forge]
symbols: [ClassName, AnotherClass]
error_patterns: ["regex pattern matching the error"]
---

## Issue
Description of the problem.

## Error
```
exact error text
```

## Root Cause
Why it happens.

## Fix
How to fix it.

## Verified
Which versions/runs confirmed this fix works.
```

**When to add a DIF entry:**
- You encountered a build or launcher test failure
- You identified the root cause
- You implemented a fix
- The fix was verified through the Build Mods workflow (build=success AND launcher-test=pass)

**Current entries:** 70+ entries covering Forge, Fabric, NeoForge, and build system issues across all MC versions.

---

## Ratings System

**Location:** `workspace/ratings.txt`

**Purpose:** Track build difficulty for each of the 82 version+loader combinations the repository supports. Over many build runs, this data identifies which combos consistently fail and should be removed from `version-manifest.json` to speed up builds.

**Format:**
```
# ModCompiler Build Difficulty Ratings
# Format: VERSION | LOADER | RATING
# Rating: 0=untested, 1=easy (builds first try), 2=medium (minor fixes), 3=hard (major issues/always fails)
#
# --- 1.20-1.20.6 ---
1.20.1 | fabric | 0
1.20.1 | forge | 0
1.20.2 | fabric | 0
...
```

**Rating definitions:**
| Rating | Meaning | When to use |
|---|---|---|
| 0 | Untested | Default — no build has been attempted yet |
| 1 | Easy | Built and passed launcher test on first try, no source changes |
| 2 | Medium | Needed minor fixes (package rename, import fix) but passed after |
| 3 | Hard | Needed major rewrites (mixin, API migration) or repeatedly failed |

**When to update:**
- After every successful build+test cycle in Phase 4 (Verify and Record)
- Rating is per version+loader combo, not per mod
- Over many runs, combos with rating 3 can be removed from the manifest

**How to read the results:**
```bash
# Show all combos rated 3 (hard)
grep "| 3$" workspace/ratings.txt

# Count by rating
grep -c "| 1$" workspace/ratings.txt  # easy
grep -c "| 2$" workspace/ratings.txt  # medium
grep -c "| 3$" workspace/ratings.txt  # hard
grep -c "| 0$" workspace/ratings.txt  # untested
```

---

## Companion Workflows

| Workflow | File | Purpose |
|---|---|---|
| Build Mods | `build.yml` | Builds mods from source zips and runs launcher tests |
| ModrinthProjectDiagnosis | `ModrinthProjectDiagnosis.yml` | Full health check of Modrinth projects |
| Auto Mod Converter | `auto-mod-to-all-version-converter.yml` | AI-powered mod porting pipeline |
| Decompile All Versions | `decompile-all-minecraft-versions-and-add-missing-to-repository.yml` | Decompiles Minecraft source for all versions |

---

## Key Repository Files

| File | Purpose |
|---|---|
| `version-manifest.json` | Defines all supported MC version+loader build targets |
| `build_mods.py` | Build orchestration script |
| `build.gradle` / `settings.gradle` | Gradle templates for building mods |
| `templates/` | Per-loader template projects |
| `modcompiler/` | Python library with decompilation, Modrinth API, and utility modules |
| `scripts/` | Helper scripts for decompilation, bundle assembly, and prompt generation |
| `incoming/` | Landing zone for source zips to be built |
| `DecompiledMinecraftSourceCode/` | Minecraft decompiled source code by version+loader |