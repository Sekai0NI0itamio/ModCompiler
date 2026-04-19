# ModCompiler

This repository is a GitHub-only scaffold for compiling Minecraft mods from a committed zip file. The workflow is manual-only, processes one zip per run, resolves the requested exact Minecraft version or same-minor version range to one or more version folders, patches a vendored template, and then builds each exact target as a separate GitHub Actions matrix job.

Deep usage guide: [docs/SYSTEM_MANUAL.md]

## Repo Layout

- `incoming/`: commit the zip you want to build here, then run the workflow and point `zip_path` at it.
- `To Be Decompiled/`: commit a jar here when you want GitHub Actions to decompile it.
- `version-manifest.json`: source of truth for version ranges, loader support, Java versions, build commands, and template locations.
- `<range>/build_adapter.py`: range-specific adapter entrypoint that patches the vendored template for that generation.
- `<range>/<loader>/template/`: the pinned template snapshot for that version range and loader.
- `<range>/<loader>/PROVENANCE.md`: where the vendored template came from and whether it is exact or anchor-based.

Active top-level version folders:

- `1.8.9`
- `1.12-1.12.2`
- `1.16.5`
- `1.17-1.17.1`
- `1.18-1.18.2`
- `1.19-1.19.4`
- `1.20-1.20.6`
- `1.21-1.21.1`
- `1.21.2-1.21.8`
- `1.21.9-1.21.11`

## Zip Contract

The committed zip must contain one folder per mod at the top level:

```text
my-batch.zip
  CoolMod/
    src/
    mod.txt
    version.txt
  OtherMod/
    src/
    mod.txt
    version.txt
```

`version.txt` uses strict `key=value`:

```text
minecraft_version=1.20.6
loader=fabric
```

For a per-version fan-out build across one Minecraft minor family:

```text
minecraft_version=1.21-1.21.8
loader=fabric
```

`mod.txt` uses strict `key=value`:

```text
mod_id=coolmod
name=Cool Mod
mod_version=1.0.0
group=com.example.coolmod
entrypoint_class=com.example.coolmod.CoolMod
description=One-line or escaped multi-line description
authors=YourName, AnotherAuthor
license=MIT
homepage=https://example.com
sources=https://github.com/example/coolmod
issues=https://github.com/example/coolmod/issues
runtime_side=client
```

`runtime_side` is optional. Omit it for normal client+server mods. Set it to `client` for client-only mods such as HUD, keybind, or movement-toggle mods. The scaffold will then generate a Fabric client entrypoint and client-only Forge metadata.
`loader` may be `forge`, `fabric`, or `neoforge` (where available).

## Build Behavior

The workflow runs in three main stages, plus one optional publish stage:

1. `prepare`: validates the zip, extracts each mod folder, parses metadata, expands same-minor version ranges into exact versions, resolves the version folder for each exact target, and creates the matrix.
2. `build`: builds one exact target per matrix job, with workflow input `max_parallel` controlling how many run at once. Use `all` for no scaffold-side cap.
3. `bundle`: downloads all per-mod artifacts, writes one combined summary artifact, and publishes a Markdown table to the GitHub Actions run summary.
4. `publish-modrinth` (optional): if `modrinth_project_url` is set, uploads successful jars to that Modrinth project using the `MODRINTH_TOKEN` repository secret.

If one exact target from a range fails, its artifact still contains the error and the workflow keeps going with the remaining exact versions from that same range.

If you want to publish later (for example, after reviewing an auto-update bundle), run the `Publish Modrinth Bundle` workflow with the prior run ID and artifact name (typically `final-results` for auto-update or `all-mod-builds` for normal builds). It will normalize the bundle and upload any missing versions.

Each per-mod artifact contains:

- `build.log`
- `input-metadata.json`
- `result.json`
- `jars/` when a jar was produced

The combined artifact contains:

- `SUMMARY.md`
- `run-summary.json`
- `mods/<slug>/...` copies of each per-mod artifact

If Modrinth publishing is enabled, the workflow also uploads a `modrinth-publish` artifact with:

- `SUMMARY.md`
- `result.json`

## Current Template State

The Forge side uses pinned official MDK snapshots for:

- `1.8.9`
- `1.12.2`
- `1.16.5`
- `1.20.6`
- `1.21.1`
- `1.21.8`
- `1.21.11`

The Fabric side uses:

- Official `FabricMC/fabric-example-mod` `1.17` branch as an adapted fallback for `1.16.5`
- Official `FabricMC/fabric-example-mod` `1.17` branch for `1.17-1.17.1`
- Official `FabricMC/fabric-example-mod` `1.18` branch for `1.18-1.18.2`
- Official `FabricMC/fabric-example-mod` `1.19` branch for `1.19-1.19.4`
- Official `FabricMC/fabric-example-mod` `1.20` branch for `1.20-1.20.6`
- Official `FabricMC/fabric-example-mod` `1.21` branch retargeted to `1.21-1.21.1`
- Official `FabricMC/fabric-example-mod` `1.21` branch retargeted to `1.21.2-1.21.8`
- Official `FabricMC/fabric-example-mod` `1.21` branch for `1.21.9-1.21.11`

The NeoForge side uses the official NeoForge MDK templates (NeoGradle) for `1.20.2`, `1.20.4-1.20.6`, and `1.21.x`.

Some range folders are intentionally marked as `anchor_only` in `version-manifest.json`. That means the scaffold resolves the exact Minecraft version correctly, but the underlying dependency versions are still anchored to the vendored template snapshot. Before first production use, extend the manifest and adapters if you need exact Forge/Fabric/NeoForge dependency resolution across every patch version in those broad ranges.

## Build Script

All builds (template verify, normal builds, and AI rebuilds) now use `scripts/modcompiler-build.sh`, which is copied into each temporary workspace as `./modcompiler-build.sh`. The script defaults to a fast path that skips download tasks (and runs offline) and falls back to a full build if the fast path fails. You can control this with:

- `MODCOMPILER_FAST_BUILD=0` to disable the fast path
- `MODCOMPILER_FAST_ONLY=1` to fail instead of falling back
- `MODCOMPILER_SKIP_DOWNLOADS=0` to allow downloads even in fast mode
- `MODCOMPILER_SKIP_TESTS=0` to keep `test`/`check` tasks
- `MODCOMPILER_GRADLE_TASKS` to override the task list (defaults to `build`)

## First GitHub Test

This repo now includes a ready-to-commit example package at `incoming/example-1.12.2-forge.zip`. Its unpacked source lives under `examples/example-1.12.2-forge/` if you want to inspect or edit it before pushing.

The first remote test flow is:

1. Create a GitHub repository from this folder and push the current contents.
2. In GitHub, open the `Actions` tab and enable workflows if the repo is new.
3. Run the `Build Mods` workflow manually.
4. For `zip_path`, enter `incoming/example-1.12.2-forge.zip`.
5. For `max_parallel`, enter either a positive number or `all`. The workflow now defaults to `all`, which means it will fan out as far as the repo runner quota allows.
6. Optionally set `modrinth_project_url` to a Modrinth project URL or slug if you want successful jars uploaded automatically.
7. If you want Modrinth publishing, create a repository secret named `MODRINTH_TOKEN` first. The workflow does not take the token as a normal input because GitHub workflow inputs are not true secrets.
8. After the run finishes, inspect the per-mod artifact and the combined `all-mod-builds` artifact.
9. If the build fails, download `build.log` from the artifact and use that as the next debugging input.

This repo also includes a larger batch example at `incoming/togglesprint-1.20.1-1.21.11-fabric-forge.zip`. That archive contains multiple top-level mod folders so unsupported Forge exact patches can be skipped while still covering the full supported span. It fans out across exact versions for:

- Fabric `1.20.1-1.20.6`
- Forge `1.20.1-1.20.4`
- Forge `1.20.6`
- Fabric `1.21-1.21.11`
- Forge `1.21-1.21.1`
- Forge `1.21.3-1.21.5`
- Forge `1.21.6-1.21.11`

## Local Mod Compile Script

If you want one local command that uploads a zip bundle, runs the existing GitHub compile workflow, then downloads the finished artifacts back to your computer, use:

```bash
python3 scripts/mod_compile.py /absolute/or/relative/path/to/your-bundle.zip
```

Optional Modrinth auto-publish uses the same workflow input the manual GitHub run already supports:

```bash
python3 scripts/mod_compile.py /path/to/your-bundle.zip \
  --modrinth-project-url https://modrinth.com/mod/your-project
```

What this script does:

- Creates a temporary GitHub branch from the current committed `HEAD`
- Copies your zip into `incoming/mod-compile/<session>/...`
- Dispatches `.github/workflows/build.yml`
- Waits for the run to finish
- Downloads `all-mod-builds` into `ModCompileRuns/<session>/`
- Also downloads `modrinth-publish` when `--modrinth-project-url` is provided
- Writes `request.json`, `result.json`, and `SUMMARY.md` alongside the downloaded artifacts

Requirements:

- `git`
- `gh`
- A GitHub token in `GH_TOKEN` or `GITHUB_TOKEN` with repo write access and workflow dispatch access

Notes:

- The script uploads only the committed repo state plus the selected zip bundle. Uncommitted local repo changes are not included in the temporary branch.
- `--max-parallel` accepts either `all` or a positive integer and is passed straight through to the workflow input.

## Jar Decompile Workflow

The repo also includes a second manual workflow named `Jar Decompile`.

Input contract:

- Commit one jar under `To Be Decompiled/`
- Run the `Jar Decompile` workflow
- Pass either the bare jar filename, for example `some-mod.jar`, or the repo-relative path, for example `To Be Decompiled/some-mod.jar`

What it does:

1. Downloads the Vineflower Java decompiler in GitHub Actions
2. Extracts the jar and decompiles its class files to `src/main/java`
3. Reads supported mod metadata when present from `fabric.mod.json`, `META-INF/mods.toml`, or `mcmod.info`
4. Writes a `mod_info.txt` summary with loader, mod id, name, version, supported Minecraft range, guessed repo folders, and other discovered fields
5. Packages `src/` plus `mod_info.txt` into one zip file inside the workflow artifact

The `jar-decompile-output` artifact contains:

- `decompile.log`
- `mod_info.txt` on success
- `<slug>.zip` on success
- `SUMMARY.md`
- `result.json`

If the workflow input is wrong or decompilation fails, the run still writes `decompile.log`, `SUMMARY.md`, and `result.json` so the failure is inspectable from the artifact.

## Auto Create Modrinth Drafts

Use the local draft helper when you want to decompile jars, generate listing copy and art, optionally create public GitHub source/docs repos, then upload the verified bundle to Modrinth:

```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --verified
```

`generate` now expects `ToBeUploaded/` to contain one folder per mod bundle, and each bundle folder must contain:

- exactly one top-level mod `.jar`
- exactly one top-level source-code folder

Example layout:

```text
ToBeUploaded/
  1/
    All-Mobs-Hate-The-SUN.jar
    All-Most-Hate-The-SUN/
      src/...
  2/
    Instant-Hoppers-1.0.jar
    Instant-Hoppers-Src/
      instanthoppers/Main.java
```

The AI prompt now uses the provided source folder for `projectinfo.txt`, while the remote decompile workflow is still used to collect jar-derived metadata and validation details.

Useful flags:

- `--github-owner <user-or-org>`: owner for generated public per-mod GitHub repos. If omitted, the script uses the owner from the current `origin` remote.
- `--nolinks`: disables GitHub repo/wiki/issues link generation during `generate`, and strips all external links from the Modrinth draft payload during `create-drafts`.

Examples:

```bash
python3 scripts/auto_create_modrinth_draft_projects.py generate --github-owner Sekai0NI0itamio
python3 scripts/auto_create_modrinth_draft_projects.py generate --nolinks
python3 scripts/auto_create_modrinth_draft_projects.py create-drafts --verified --nolinks
```

When link generation is enabled, `generate` now creates one public GitHub repo per mod slug and keeps it safe to rerun:

- Enables GitHub Issues and GitHub Wiki on the repo
- Pushes the provided source tree plus `mod_info.txt` when available
- Writes a generated `README.md`
- Adds GitHub issue templates under `.github/ISSUE_TEMPLATE`
- Pushes a small GitHub Wiki with `Home`, `Installation`, and `Troubleshooting`
- Stores the generated link state in `external_links.json` inside the bundle

Safety note:

- If the target repo or wiki already exists and is not marked as ModCompiler-managed, the script reuses the URLs but does not overwrite its content.

### GitHub Token Permissions

Recommended classic PAT scopes:

- `repo`
- `workflow`

Recommended fine-grained PAT permissions:

- Repository access: `All repositories` under the target owner, or at minimum the current `ModCompiler` repo plus the generated per-mod repos
- `Administration`: `Read and write`
- `Contents`: `Read and write`
- `Actions`: `Read and write`

Why those are needed:

- `Administration` is used to create public repositories and ensure issues/wiki are enabled
- `Contents` is used to push the decompiled source snapshot, README, and issue templates
- `Actions` is used by the existing ModCompiler workflow dispatch and wait/download flow
