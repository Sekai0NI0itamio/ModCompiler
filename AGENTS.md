# Agent Instructions for ModCompiler

This file is the primary instruction reference for AI coding agents working in this repository.

## Build Workflow Rules

### ALWAYS use full parallelism

When running the `Build Mods` GitHub Actions workflow, **always leave `max_parallel` at the default value `all`.** The workflow automatically resolves `all` to the exact number of matrix jobs, so every version+loader builds in parallel.

- If there are 81 jobs, `max_parallel` must be 81.
- Do **not** set `max_parallel` to `1` unless the user explicitly orders emergency throttling. It makes the run unnecessarily slow.
- Do **not** use `gh run watch`. Instead use the `scripts/monitor_workflow.py` script described below.

## Monitoring Workflow Runs

Use the provided Python monitor script instead of interactive `gh` watch commands.

```bash
python3 scripts/monitor_workflow.py <run-id>
```

The script:
- Polls the run with minimal output
- Reports status, conclusion, and completed/total jobs
- Automatically downloads all logs into `workflow_logs/` when the run finishes

To save logs elsewhere:

```bash
python3 scripts/monitor_workflow.py <run-id> --out /path/to/logs
```

For a private repository or higher API rate limits, set:

```bash
export GITHUB_TOKEN=<your-token>
```

## General Rules

1. **Do not create files unless necessary.** Prefer editing existing files.
2. **Do not add documentation files** unless the user explicitly asks for them.
3. **Prefer simple solutions.** Do not over-engineer fixes.
4. **Test changes** via the `Build Mods` workflow or the relevant GitHub Actions workflow.
5. **Document fixes** in the appropriate project report (e.g., `docs/publishing/PINGFIX_FIX_REPORT.md`) when asked to track issues and resolutions.

## Modrinth Project Diagnosis Workflow

### How It Works

The `ModrinthProjectDiagnosis.yml` workflow performs comprehensive testing of all published versions of a mod:

1. **Matrix Generation**: Creates a test matrix for each version+loader+MC version combination
2. **Download**: Fetches the published JAR from Modrinth
3. **Code Analysis**: Decompiles and analyzes class/method count, metadata presence
4. **Loader Installation**: Installs the appropriate mod loader (Fabric/Forge/NeoForge)
5. **Launcher Test**: Runs the game with the mod installed and verifies it reaches the title screen

### Health Status Interpretation

| Status | Meaning | Action |
|--------|---------|--------|
| **HEALTHY** | All checks passed | No action needed |
| **WARNING** | Minor concerns (small jar, few classes) but launcher passed | Monitor |
| **UNHEALTHY** | Launcher test failed or multiple concerns | Investigate |
| **SHELL** | Corrupted/empty jar | Mod bug - needs fix |
| **MISSING** | Version+loader not published | Not a bug |

### Distinguishing Infrastructure vs. Mod Bugs

**Infrastructure Issues (Fix in ModCompiler):**
- `not_tested` status (loader installation failed)
- OOM crashes (memory limits too low)
- Wrong MC version launched (regex mismatch)
- Process hanging (orphaned processes)

**Mod Bugs (Fix in Mod Code):**
- Actual crash reports with stacktraces
- NullPointerException, ClassCastException, etc.
- Exit code 1 with crash in game logs
- Mod-specific rendering errors

### Example: Toggle Sprint Mod Analysis

**Project:** https://modrinth.com/mod/toggle-sprint
**Total Versions Tested:** 81
**UNHEALTHY:** 17 (21%)
**WARNING:** 54 (67%)
**MISSING:** 10 (12%)

**Failure Analysis:**

| Category | Affected Versions | Root Cause |
|----------|-------------------|------------|
| Fabric old versions | 1.16.5, 1.17.1, 1.19, 1.19.1 | Exit code 1 - mod incompatibility with older Fabric API |
| Forge 1.20.x | 1.20.1-1.20.4 | `NullPointerException: Cannot invoke Boolean.booleanValue()` - mod bug |
| Forge 1.21.x | 1.21, 1.21.1, 1.21.3-1.21.8 | Rendering overlay crashes - mod compatibility issue |

**Action Required:** Update mod code to fix NPE and improve backward compatibility.

### Running a Diagnosis

```bash
gh workflow run ModrinthProjectDiagnosis.yml -f modrinth_project_url="https://modrinth.com/mod/<mod-slug>"
```

### Interpreting Results

1. Check the `Final-Diagnosis.txt` report for overall summary
2. Examine individual `health-report.txt` files for crash details
3. Review launcher logs in `launcher-result-*` directories for specific errors
4. Determine if failures are infrastructure-related (fix ModCompiler) or mod bugs (fix mod code)
