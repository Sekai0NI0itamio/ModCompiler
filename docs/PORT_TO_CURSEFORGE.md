# Port Mod to CurseForge — User Manual

This tool ports your mod versions from Modrinth to CurseForge automatically.
You run one command locally, it dispatches a GitHub Actions workflow using your stored secrets, waits for it to finish, and prints the full results back to your terminal.

---

## How it works

1. The local script (`scripts/port_to_curseforge.py`) dispatches the GitHub Actions workflow with your two project links as inputs.
2. The workflow runs on GitHub's servers using your `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` secrets — you never type them locally.
3. The workflow fetches all versions from Modrinth, checks what already exists on CurseForge, and uploads only the missing ones.
4. The local script polls until the workflow finishes, then downloads and prints the full log to your terminal.

---

## One-time setup

### 1. GitHub secrets

The workflow reads two secrets from your repository. You only set these once.

Go to: **GitHub → Your repo → Settings → Secrets and variables → Actions → New repository secret**

| Secret name | What it is | Where to get it |
|---|---|---|
| `MODRINTH_TOKEN` | Modrinth API token | modrinth.com → Account → Settings → API Tokens |
| `CURSEFORGE_TOKEN` | CurseForge upload token | curseforge.com → Account → API Tokens |

Both secrets must be set or the workflow will fail.

### 2. gh CLI

The local script uses the `gh` CLI to dispatch and monitor the workflow. You said you already have it authenticated, so nothing to do here. If you ever need to re-authenticate:

```bash
gh auth login
```

### 3. The workflow file must be on main

The workflow file (`.github/workflows/port-to-curseforge.yml`) must be committed and pushed to the default branch before the script can dispatch it. This was already done when the tool was set up. You do not need to do anything here unless you reset the branch.

---

## Finding your CurseForge project ID

The CurseForge upload API requires the **numeric project ID**, not the slug. The slug (e.g. `fix-my-pinggggg`) does not work.

To find the numeric ID:

1. Go to your CurseForge project page, e.g. `https://www.curseforge.com/minecraft/mc-mods/fix-my-pinggggg`
2. Look at the **right-hand sidebar** — there is a field labelled **Project ID** with a number like `1526309`

That number is what you pass to the script.

---

## Running the script

From the repo root:

```bash
python3 scripts/port_to_curseforge.py <modrinth_url> <curseforge_project_id>
```

### Real example

```bash
python3 scripts/port_to_curseforge.py \
  https://modrinth.com/mod/pingfix \
  1526309
```

### Dry run — see what would be uploaded without uploading anything

Always do this first when porting a new mod. It shows you the full diff table and exits without touching CurseForge.

```bash
python3 scripts/port_to_curseforge.py \
  https://modrinth.com/mod/pingfix \
  1526309 \
  --dry-run
```

### Arguments

| Argument | Required | Description |
|---|---|---|
| `modrinth_url` | Yes | Full Modrinth URL or just the slug (e.g. `pingfix`) |
| `curseforge_project_id` | Yes | Numeric project ID from the CurseForge sidebar (e.g. `1526309`) |
| `--dry-run` | No | Show the diff table but do not upload anything |
| `--repo` | No | Override the GitHub repo (default: auto-detected from `git remote`) |
| `--output-dir` | No | Where to save logs locally (default: `CurseForgePortRuns/`) |

---

## What the script prints

```
======================================================================
  Modrinth -> CurseForge Port
----------------------------------------------------------------------
  Modrinth  : https://modrinth.com/mod/pingfix
  CurseForge: 1526309
  Dry run   : False
  Repo      : YourUser/ModCompiler  (branch: main)
  Output    : /path/to/ModCompiler/CurseForgePortRuns/port-20260501-040000
======================================================================

[1/4] Dispatching workflow...
  Workflow dispatched. Waiting for run to appear...
  Run ID : 25201364042
  URL    : https://github.com/YourUser/ModCompiler/actions/runs/25201364042

[2/4] Waiting for workflow to complete...
  [04:00:10] status: queued
  [04:00:21] status: in_progress
  [04:01:05] status: completed
  Conclusion: success

[3/4] Downloading logs...
  Saved to: .../CurseForgePortRuns/port-.../run.log
  === WORKFLOW LOG ===
  ...

[4/4] Downloading artifacts...
  No artifacts.

======================================================================
  ✓  Workflow SUCCESS
  Run    : https://github.com/YourUser/ModCompiler/actions/runs/25201364042
  Output : .../CurseForgePortRuns/port-20260501-040000
======================================================================
```

---

## What the workflow does internally

The workflow runs entirely on GitHub's servers. Here is what happens step by step:

**Step 1 — Fetch Modrinth**
Reads all published versions from your Modrinth project. For each `(minecraft_version, loader)` pair, it keeps only the most recently published version. This means if you uploaded the same MC version twice (e.g. to fix a bug), only the latest one is considered.

**Step 2 — Fetch CurseForge**
Reads all existing files from your CurseForge project. Builds the same `(minecraft_version, loader)` key set.

**Step 3 — Diff**
Compares the two sets. Any `(minecraft_version, loader)` combo that exists on Modrinth but not on CurseForge is added to the upload list. Prints a table:

```
  ====================================================================
  Versions on Modrinth missing from CurseForge: 3
  --------------------------------------------------------------------
  #    MC Version     Loader       Version #            Type       Published
  --------------------------------------------------------------------
  1    1.20.1         forge        1.2.0                release    2025-03-10 14:22:00  MyMod-1.2.0.jar
  2    1.21.1         fabric       1.2.0                release    2025-03-10 14:23:00  MyMod-1.2.0.jar
  3    1.21.1         neoforge     1.2.0                release    2025-03-10 14:24:00  MyMod-1.2.0.jar
  ====================================================================
```

**Step 4 — Upload**
For each missing version:
- Downloads the jar from Modrinth's CDN
- Resolves the correct CurseForge game version IDs (MC version + loader are separate IDs on CurseForge)
- Uploads the jar with the original changelog, display name, and release type (release / beta / alpha)

---

## Supported loaders

| Modrinth name | CurseForge name |
|---|---|
| `forge` | Forge |
| `fabric` | Fabric |
| `neoforge` | NeoForge |
| `quilt` | Quilt |

---

## Local output files

Every run saves files to `CurseForgePortRuns/port-<timestamp>/`:

| File | Contents |
|---|---|
| `run.log` | Full workflow log from GitHub Actions |
| `artifacts/` | Any artifacts produced by the workflow (currently none) |

---

## Common errors and fixes

### `workflow port-to-curseforge.yml not found on the default branch`

The workflow file is not on `main` yet. Push it:

```bash
git add .github/workflows/port-to-curseforge.yml
git commit -m "Add port-to-curseforge workflow"
git push origin main
```

Then run the script again.

---

### `CURSEFORGE_TOKEN secret is not set on this repository`

Go to **GitHub → Repo → Settings → Secrets and variables → Actions** and add `CURSEFORGE_TOKEN`.

---

### `CurseForge input 'fix-my-pinggggg' is a slug, not a numeric project ID`

You passed the slug instead of the numeric ID. Go to your CurseForge project page, find the **Project ID** number in the right sidebar, and use that instead:

```bash
# Wrong
python3 scripts/port_to_curseforge.py https://modrinth.com/mod/pingfix fix-my-pinggggg

# Correct
python3 scripts/port_to_curseforge.py https://modrinth.com/mod/pingfix 1526309
```

---

### `Not found (HTTP 404)` on the CurseForge files endpoint

The numeric project ID is wrong, or the project does not exist yet on CurseForge. Double-check the ID from the sidebar.

---

### `[WARN] No CurseForge version ID found for MC '1.20.1'`

CurseForge does not have a game version entry for that exact MC version. The workflow tries stripping the patch version (e.g. `1.20.1` → `1.20`) as a fallback. If both fail, that version is skipped. This usually means the MC version is very new and CurseForge hasn't added it yet — check the CurseForge game versions list and try again later.

---

### Workflow dispatched but no run appeared

GitHub sometimes takes a few seconds to queue the run. The script waits up to 120 seconds. If it still times out, check the Actions tab on GitHub directly to see if the run is stuck in a queue.

---

## Running via GitHub Actions UI directly (without the local script)

You can also trigger the workflow manually from the GitHub web UI if you prefer:

1. Go to your repo on GitHub
2. Click **Actions**
3. Click **Port Mod to CurseForge** in the left sidebar
4. Click **Run workflow**
5. Fill in:
   - **Modrinth project URL or slug** — e.g. `https://modrinth.com/mod/pingfix`
   - **CurseForge numeric project ID** — e.g. `1526309`
   - **Dry run** — `true` to preview, `false` to upload
6. Click **Run workflow**

The local script is just a convenience wrapper that does this for you and streams the results back to your terminal.
