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
