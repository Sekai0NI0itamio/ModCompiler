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

## build_mods.py

**Location:** `build_mods.py` (original) or `./tools/build_mods.py` (copy)

**Purpose:** Compiles mod source code across all supported MC version+loader combinations.

**What it does:**
- Takes a zip file of mod source code (placed in `incoming/` directory)
- Reads `version-manifest.json` to determine which targets to build
- Generates Gradle projects for each (slug, mc_version, loader) combo
- Compiles using the correct Java version per target
- Runs built jars through headless Minecraft launcher tests
- Bundles all results into a combined artifact

**How to trigger:**
1. Package fixed source code into a zip
2. Place in `incoming/` directory (or commit and push)
3. Trigger `build.yml` workflow with `zip_path` input pointing to the zip

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