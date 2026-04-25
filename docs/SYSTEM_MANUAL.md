# ModCompiler Manual

This document explains how the repository works, what GitHub Actions does during a build or decompile run, what your source package is expected to look like, and how to write a mod that this system can compile.

## What This Repository Is

This repository is a GitHub-only Minecraft mod build system.

You do not build mods locally with this setup.
Instead, you:

1. Put a mod source package zip into `incoming/`
2. Commit and push it
3. Run the `Build Mods` GitHub Actions workflow
4. Download the built jars and logs from the workflow artifacts

It also includes a second GitHub workflow, `Jar Decompile`, that:

1. Takes a committed `.jar` from `To Be Decompiled/`
2. Decompiles it in GitHub Actions
3. Extracts metadata when possible
4. Produces a zip with `src/` plus `mod_info.txt`

And a third workflow, `Decompile All Minecraft Versions and Add Missing to Repository`, that:

1. Decompiles Minecraft API sources for every version+loader combination
2. Commits the extracted `.java` files into `decompiled-sources/` in the repository
3. Skips versions that are already committed, so re-runs are cheap
4. Lets AI agents and developers search the API without re-running decompilation

## Core Design

The system is built around version folders.
Each top-level version folder represents either one exact Minecraft version or one version family:

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

Inside each version folder are supported loader templates:

- `forge/`
- `fabric/`
- `neoforge/` (only in ranges where NeoForge exists)

Each template is vendored into the repository.
That means GitHub Actions does not fetch a template project at build time.
It starts from the template already stored in the repo, patches it with your metadata, copies in your `src/`, and then runs the version-specific Gradle build.

## Files That Control Everything

### `version-manifest.json`

This is the source of truth for:

- supported version folders
- minimum and maximum versions covered by each folder
- whether Forge, Fabric, and/or NeoForge are supported in that folder
- Java version rules
- which template directory is used
- which adapter family is used
- which build command and jar path pattern are expected

When you ask the system to build `1.12.2`, the builder does not guess.
It resolves that version against `version-manifest.json`, finds the correct range folder, and then uses the loader information from that same file.

If you ask it to build a same-minor range such as `1.21-1.21.8`, it expands that request into exact targets from `1.21` through `1.21.8`.
Each exact target becomes its own build entry and artifact.
In this repo, `1.21` and `1.21.1` route to `1.21-1.21.1`, while `1.21.2` through `1.21.8` route to `1.21.2-1.21.8`.

### `build_mods.py`

This is the root Python entrypoint used by the workflows.
It exposes multiple commands:

- `prepare`
- `build-one`
- `bundle`
- `decompile-jar`

The GitHub workflows call these commands instead of directly embedding complicated logic in YAML.

### `modcompiler/`

This folder contains the shared Python implementation:

- parsing `mod.txt` and `version.txt`
- zip validation
- version resolution
- per-range template patching
- artifact summary generation
- jar decompilation and metadata extraction

### `<range>/build_adapter.py`

Each range folder has a small adapter entrypoint that delegates to the shared adapter system.
This exists because Forge/Fabric/NeoForge metadata formats differ across eras.
For example:

- old Forge uses `mcmod.info`
- newer Forge uses `mods.toml`
- Fabric uses `fabric.mod.json`
- NeoForge uses `neoforge.mods.toml`

The adapter knows how to patch the correct files for that generation.

## How The Build Workflow Works

The `Build Mods` workflow is a three-stage pipeline with one optional publish stage.

### Stage 1: Prepare

The workflow reads the zip you pointed to in `incoming/`.

It then:

1. extracts the zip safely
2. validates that each top-level entry is a mod folder
3. checks that every mod folder contains:
   - `src/`
   - `mod.txt`
   - `version.txt`
4. parses the metadata files
5. expands same-minor version ranges into exact Minecraft versions when needed
6. resolves each exact Minecraft version to one repo version folder
7. checks that the requested loader is actually supported there
8. creates a build plan and matrix metadata

If this stage fails, the issue is usually one of:

- bad zip layout
- missing `mod.txt`
- missing `version.txt`
- wrong `key=value` format
- unsupported loader for that version
- unsupported Minecraft version

### Stage 2: Build

Each exact target from the zip is built as its own matrix job.
The `Build Mods` workflow now accepts `max_parallel`, which controls how many exact targets GitHub builds at the same time.
Use `all` for no scaffold-side cap, or enter a positive number if you want to throttle the matrix.

For each exact target:

1. the correct template is copied into an isolated workspace
2. your `src/` is copied into that workspace
3. the version adapter patches build files and metadata files
4. the workflow switches to the required Java version
5. Gradle builds the mod
6. the built jar is copied into the per-mod artifact folder

Each per-mod artifact contains:

- `build.log`
- `input-metadata.json`
- `result.json`
- `jars/` when a jar was produced

### Stage 3: Bundle

After all builds finish, the workflow:

1. downloads all per-mod artifacts
2. creates one combined artifact
3. writes a Markdown summary
4. publishes a table to the GitHub Actions job summary

That combined artifact is the easiest place to inspect the whole run.

### Optional Stage 4: Publish To Modrinth

If you enter `modrinth_project_url` when starting the workflow, the repository will attempt to upload every successful built jar to that Modrinth project.

Important security detail:

- GitHub manual workflow inputs are not true secrets
- because of that, the workflow does not accept the Modrinth API key as a plain input
- instead, you must store the token in a repository secret named `MODRINTH_TOKEN`

When Modrinth publishing runs, it:

1. reads the combined build artifact
2. skips failed builds automatically
3. selects the primary distributable jar from each successful build
4. resolves the Modrinth project from the URL or slug you entered
5. checks whether that exact jar/version target already exists on Modrinth
6. uploads the missing versions
7. writes a `modrinth-publish` artifact with `SUMMARY.md` and `result.json`

If authentication fails, the workflow reports that the Modrinth token is invalid or missing, but it does not print the token.

### Publish Modrinth Bundle (Standalone)

If you already have a bundle artifact from a prior run (for example `final-results` from auto-update or `all-mod-builds` from the build workflow) and want to publish later, use the `Publish Modrinth Bundle` workflow. It:

1. downloads the artifact from the run ID you provide
2. normalizes the bundle structure if needed
3. uploads any missing versions to the Modrinth project you supply

Inputs you provide:

- `run_id`: the workflow run ID that produced the artifact
- `artifact_name`: usually `final-results` or `all-mod-builds`
- `modrinth_project_url`: the target Modrinth project URL or slug
- `bundle_subdir` (optional): if the bundle lives in a subfolder of the artifact

As with the main publish stage, it requires the `MODRINTH_TOKEN` repository secret.

## What The Build Input Must Look Like

The builder expects one committed zip file under `incoming/`.

That zip must contain one top-level folder per mod:

```text
my-batch.zip
  MyMod/
    src/
    mod.txt
    version.txt
  AnotherMod/
    src/
    mod.txt
    version.txt
```

No files should sit loose at the top level.
The top level must contain folders only.

## `mod.txt`

`mod.txt` uses strict `key=value` format.

Required keys:

- `mod_id`
- `name`
- `mod_version`
- `group`
- `entrypoint_class`
- `description`
- `authors`
- `license`

Optional keys:

- `homepage`
- `sources`
- `issues`
- `runtime_side`

Example:

```text
mod_id=tpateleport
name=Tpa Teleport
mod_version=1.0.0
group=net.itamio.tpateleport
entrypoint_class=net.itamio.tpateleport.TpaTeleportMod
description=Server-side teleport request utility
authors=Itamio
license=MIT
homepage=https://modrinth.com/
sources=https://github.com/example/repo
issues=https://github.com/example/repo/issues
runtime_side=client
```

Important behavior:

- `authors` is comma-separated
- `entrypoint_class` must be fully qualified
- `runtime_side` defaults to `both`
- valid `runtime_side` values are `both`, `client`, and `server`
- use `runtime_side=client` for client-only mods so Fabric gets a client entrypoint and Forge is marked client-side only
- unknown keys fail validation
- duplicate keys fail validation
- comments are allowed only as full lines starting with `#`

## `version.txt`

`version.txt` also uses strict `key=value`.

Required keys:

- `minecraft_version`
- `loader`

Example:

```text
minecraft_version=1.12.2
loader=forge
```

Range example:

```text
minecraft_version=1.21-1.21.8
loader=fabric
```

Important behavior:

- `minecraft_version` can be one exact version or one inclusive same-minor range
- valid range example: `1.21-1.21.8`
- invalid range example: `1.19-1.20` because cross-minor ranges are not expanded automatically
- `loader` must be `forge`, `fabric`, or `neoforge`
- each exact version is resolved into the matching repo range folder automatically
- if one exact version from a range fails, the workflow preserves that failure and continues with the remaining exact versions

## What Your `src/` Folder Should Contain

Your package should contain only your mod source tree, usually something like:

```text
src/
  main/
    java/
      com/example/mymod/MyMod.java
```

You can also include:

- `src/main/resources/...`
- Kotlin source trees if your template/toolchain supports them

However, for loader metadata, the system is opinionated:

- generated metadata from the template/adapters wins
- if your uploaded `src/` includes conflicting metadata files, the adapter may overwrite them

That is intentional.
The system treats `mod.txt` and `version.txt` as the canonical build metadata.

## How To Write A Mod For This System

### Step 1: Choose The Build Target

Pick either one exact version or one same-minor range, plus the loader, first.

Examples:

- `1.12.2` + `forge`
- `1.20.6` + `forge`
- `1.21-1.21.8` + `fabric`
- `1.21.1` + `fabric`
- `1.21.8` + `fabric`
- `1.21.11` + `fabric`

Do not write the mod first and choose the API later.
Minecraft mod APIs differ too much across generations.

### Step 2: Code Against The Correct API Era

Use the actual APIs for the target version.

Examples:

- Forge 1.12.2 uses the older command/event APIs
- modern Forge uses `mods.toml`
- Fabric uses `fabric.mod.json`

This system helps with build scaffolding.
It does not magically translate one Minecraft API generation into another.

If you write a mod for modern Brigadier-based command APIs and then claim it is `1.12.2`, the build will fail because the source code itself is wrong for that era.

### Step 3: Create A Stable Entrypoint Class

Your Java entrypoint class should be the class named in `entrypoint_class`.

Example:

```text
entrypoint_class=net.itamio.tpateleport.TpaTeleportMod
```

That means the file path should be:

```text
src/main/java/net/itamio/tpateleport/TpaTeleportMod.java
```

### Step 4: Keep Build Metadata Out Of The Source When Possible

Do not rely on a hand-written `mods.toml`, `mcmod.info`, or `fabric.mod.json` inside your source package unless you understand exactly how the adapter for that version behaves.

The intended path is:

1. write Java/Kotlin/resources
2. define metadata in `mod.txt`
3. define target version and loader in `version.txt`
4. let the adapter generate the correct loader metadata

### Step 5: Package It Correctly

Put the mod in its own top-level folder inside the zip.

For example:

```text
TpaTeleport1122/
  src/
  mod.txt
  version.txt
```

Then zip that top-level folder, not just the contents.

## How To Use GitHub To Build A Mod

### Step 1: Put The Zip In `incoming/`

Example:

```text
incoming/tpateleport-1.12.2-forge.zip
```

### Step 2: Commit And Push

Typical flow:

```bash
git add incoming/tpateleport-1.12.2-forge.zip
git commit -m "Add tpateleport 1.12.2 forge build package"
git push
```

### Step 3: Run `Build Mods`

In GitHub:

1. open the repository
2. open `Actions`
3. select `Build Mods`
4. click `Run workflow`
5. enter the zip path such as `incoming/tpateleport-1.12.2-forge.zip`
6. enter `max_parallel`, either as a positive number or `all`

### Step 4: Read The Result

If the build succeeds:

- download the per-mod artifact
- download the combined artifact
- the jar will be inside the artifact `jars/` folder

If the build fails:

- open `build.log`
- inspect `result.json`
- compare the error against your source package and target version

## What Is Expected To Happen During A Successful Build

For a successful run, this is the normal chain:

1. GitHub checks out the repository
2. the workflow reads your zip path
3. Python validates the zip
4. if the input requested a range, Python expands it into exact targets
5. each exact version is resolved to the repo range folder
6. the loader-specific template is copied
7. your source is merged into the workspace
8. metadata files are generated or patched
9. Gradle builds the mod
10. the resulting jar is published as an artifact
11. the run summary records the mod id, loader, target version, and status

## What Is Expected To Happen During A Failed Build

Failure is not unusual while developing a mod.
The useful part is where the failure happens.

Typical failure categories:

- bad zip layout
- wrong metadata keys
- wrong `entrypoint_class`
- source code written against the wrong Minecraft API
- wrong imports for the selected version
- unsupported loader/version combination
- missing generated jar after Gradle finishes

When that happens, the workflow should still leave enough output for you to debug the issue.

## Decompiled Minecraft Sources Repository

The repository includes a pre-generated library of decompiled Minecraft API sources under:

```text
decompiled-sources/
  1.8.9-forge/
  1.12.2-forge/
  1.16.5-fabric/
  1.16.5-forge/
  1.17.1-fabric/
  1.17.1-forge/
  1.18.2-fabric/
  1.18-forge/
  ...
  26.1.2-neoforge/
```

Each folder contains the extracted `.java` files for that Minecraft version and loader combination, plus a `README.md` with generation metadata.

### Why This Exists

AI coding agents (Kiro, Copilot, etc.) and developers frequently need to look up Minecraft API signatures — class names, method signatures, event types — before writing or fixing mod code.

Without this library, every lookup requires triggering a full Gradle decompilation run in GitHub Actions, which takes 10–60 minutes per version.

With this library, you can search the sources instantly:

```bash
# Find all uses of a class or method name
grep -r "DimensionDataStorage" decompiled-sources/1.21.5-forge/

# List all event classes for a version
ls decompiled-sources/1.21.5-forge/ | grep -i event

# Search across all versions at once
grep -r "computeIfAbsent" decompiled-sources/
```

### For IDE Agents (Kiro)

When writing or fixing mod code, reference the decompiled sources directly from the repository instead of triggering the `AI Source Search` workflow:

```
decompiled-sources/<version>-<loader>/
```

For example, to look up the `SavedData` API for Forge 1.21.5:

```bash
grep -r "SavedData" decompiled-sources/1.21.5-forge/
```

Or browse the folder tree to discover available classes:

```bash
ls decompiled-sources/1.21.5-forge/net/minecraft/world/level/saveddata/
```

The `AI Source Search` workflow remains available for richer regex queries, context-line output, and full class dumps when you need more than a simple grep.

### Keeping The Library Up To Date

The library is populated and maintained by the `Decompile All Minecraft Versions and Add Missing to Repository` workflow.

**Run it from the GitHub Actions UI:**

1. Open the repository on GitHub
2. Open `Actions`
3. Select `Decompile All Minecraft Versions and Add Missing to Repository`
4. Click `Run workflow`
5. Leave all inputs at their defaults to process only missing versions
6. Click `Run workflow`

**Or trigger it from your terminal:**

```bash
python3 scripts/decompile_all_and_commit.py
```

The script requires the `gh` CLI to be installed and authenticated.

**Workflow inputs:**

| Input | Default | Description |
|-------|---------|-------------|
| `force_regenerate` | `no` | Set to `yes` to re-decompile even if the folder already exists |
| `specific_version` | _(blank)_ | Only process one version, e.g. `1.21.5` |
| `specific_loader` | _(blank)_ | Only process one loader: `forge`, `fabric`, or `neoforge` |
| `commit_message_prefix` | `chore(sources)` | Prefix for the auto-generated git commit message |

Already-committed folders are skipped automatically, so re-runs are cheap.

### Adding Decompiled Sources For A New Minecraft Version

When a new Minecraft version is added to `version-manifest.json`, its decompiled sources are not committed automatically.
Follow these steps to add them:

**Step 1: Add the version to `version-manifest.json`**

Add the new version range entry with its loader configurations, template directory, and Java version rules.
See the existing entries as a reference.

**Step 2: Add the template**

Create the template directory at `<range>/<loader>/template/` and populate it with the Gradle project for that version.
Update `<range>/<loader>/PROVENANCE.md` to record where the template came from.

**Step 3: Run the decompile workflow for the new version only**

```bash
python3 scripts/decompile_all_and_commit.py --version 1.22.1 --loader forge
```

Or from the GitHub Actions UI, set `specific_version` to the new version and `specific_loader` to the loader.

The workflow will decompile the sources and commit them to `decompiled-sources/1.22.1-forge/`.

**Step 4: Verify the sources were committed**

```bash
git pull
ls decompiled-sources/1.22.1-forge/
```

If the folder is empty or missing, check the workflow run log for Gradle errors.
The most common cause is a missing or misconfigured template.

**Step 5: Run for all loaders**

Repeat Step 3 for each loader supported by the new version (`forge`, `fabric`, `neoforge`).
Or omit `--loader` to process all loaders at once:

```bash
python3 scripts/decompile_all_and_commit.py --version 1.22.1
```

---

## How The Jar Decompile Workflow Works

The `Jar Decompile` workflow is separate from the sources library above.

Its purpose is different:

- it does not build your source package
- it reverse-engineers a committed jar into inspectable source output

### Input

Commit a jar under:

```text
To Be Decompiled/
```

Then run the workflow and pass either:

- the bare file name, such as `example.jar`
- or the repo-relative path, such as `To Be Decompiled/example.jar`

### Output

The decompile artifact contains:

- `decompile.log`
- `SUMMARY.md`
- `result.json`
- `mod_info.txt` on success
- a zip containing `src/` plus `mod_info.txt` on success

### Metadata Expectations

The decompiler tries to read metadata from:

- `fabric.mod.json`
- `META-INF/mods.toml`
- `META-INF/neoforge.mods.toml`
- `mcmod.info`
- `META-INF/MANIFEST.MF`

This is best-effort, not guaranteed.
Some jars do not expose enough metadata to perfectly reconstruct project information.

## Important Limits Of The System

This repository is powerful, but it is not magic.

Important limits:

- it does not automatically port code between Minecraft API generations
- it does not guarantee a single source tree can compile across every version family
- it does not fix logic bugs in your mod
- it does not infer perfect metadata from every decompiled jar
- it depends on the vendored templates and adapters remaining accurate for each supported version family

In short:

- the system automates scaffolding, version routing, metadata generation, and GitHub-based building
- you still need source code that is actually correct for the target API

## Recommended Workflow For New Mods

Use this sequence:

1. decide the exact target version and loader
2. check `decompiled-sources/<version>-<loader>/` for API signatures before writing code
3. write the mod specifically for that API generation
4. set `mod.txt` and `version.txt`
5. place the package in a top-level folder
6. zip it
7. commit the zip to `incoming/`
8. if you want auto-publishing, create the `MODRINTH_TOKEN` repository secret
9. run `Build Mods`
10. optionally fill in `modrinth_project_url`
11. inspect the artifact or the log
12. iterate until clean

## Included Example

This repository now includes ready-to-build Tpa Teleport and Toggle Sprint examples:

```text
incoming/tpateleport-1.12.2-forge.zip
incoming/tpateleport-1.21-1.21.1-fabric-range.zip
incoming/tpateleport-1.21.8-fabric.zip
incoming/tpateleport-1.21-1.21.8-fabric-range.zip
incoming/tpateleport-1.21.11-fabric.zip
incoming/togglesprint-1.20.1-1.21.11-fabric-forge.zip
```

The unpacked source packages live under:

```text
examples/tpateleport-1.12.2-forge/TpaTeleport1122/
examples/tpateleport-1.21-1.21.1-fabric-range/TpaTeleportFabric12101Range/
examples/tpateleport-1.21.8-fabric/TpaTeleportFabric1218/
examples/tpateleport-1.21-1.21.8-fabric-range/TpaTeleportFabric121Range/
examples/tpateleport-1.21.11-fabric/TpaTeleportFabric12111/
examples/togglesprint-1.20.1-1.21.11-fabric-forge/
```

Use that as a reference for:

- folder structure
- `mod.txt`
- `version.txt`
- how a version-specific source tree should look

The Toggle Sprint batch is intentionally split into several top-level Forge folders instead of one giant Forge range. That lets the batch skip unsupported exact Forge patches such as `1.20.5` and `1.21.2`, and it also lets early `1.21` Forge builds use a different event-bus API family than `1.21.6+`.
