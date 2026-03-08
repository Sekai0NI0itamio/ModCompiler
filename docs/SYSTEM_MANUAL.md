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
- `1.21-1.21.8`
- `1.21.9-1.21.11`

Inside each version folder are supported loader templates:

- `forge/`
- `fabric/`

Each template is vendored into the repository.
That means GitHub Actions does not fetch a template project at build time.
It starts from the template already stored in the repo, patches it with your metadata, copies in your `src/`, and then runs the version-specific Gradle build.

## Files That Control Everything

### `version-manifest.json`

This is the source of truth for:

- supported version folders
- minimum and maximum versions covered by each folder
- whether Forge and/or Fabric are supported in that folder
- Java version rules
- which template directory is used
- which adapter family is used
- which build command and jar path pattern are expected

When you ask the system to build `1.12.2`, the builder does not guess.
It resolves that version against `version-manifest.json`, finds the correct range folder, and then uses the loader information from that same file.

If you ask it to build a same-minor range such as `1.21-1.21.8`, it expands that request into exact targets from `1.21` through `1.21.8`.
Each exact target becomes its own build entry and artifact.

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
This exists because Forge/Fabric metadata formats differ across eras.
For example:

- old Forge uses `mcmod.info`
- newer Forge uses `mods.toml`
- Fabric uses `fabric.mod.json`

The adapter knows how to patch the correct files for that generation.

## How The Build Workflow Works

The `Build Mods` workflow is a three-stage pipeline.

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

Each exact target from the zip is built as its own matrix job, but the workflow is configured to run sequentially.

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
```

Important behavior:

- `authors` is comma-separated
- `entrypoint_class` must be fully qualified
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
- `loader` must be `forge` or `fabric`
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

## How The Decompile Workflow Works

The second workflow is `Jar Decompile`.

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
2. write the mod specifically for that API generation
3. set `mod.txt` and `version.txt`
4. place the package in a top-level folder
5. zip it
6. commit the zip to `incoming/`
7. run `Build Mods`
8. inspect the artifact or the log
9. iterate until clean

## Included Example

This repository now includes ready-to-build Tpa Teleport examples for the included Forge 1.12.2 and Fabric 1.21 families:

```text
incoming/tpateleport-1.12.2-forge.zip
incoming/tpateleport-1.21.8-fabric.zip
incoming/tpateleport-1.21-1.21.8-fabric-range.zip
incoming/tpateleport-1.21.11-fabric.zip
```

The unpacked source packages live under:

```text
examples/tpateleport-1.12.2-forge/TpaTeleport1122/
examples/tpateleport-1.21.8-fabric/TpaTeleportFabric1218/
examples/tpateleport-1.21-1.21.8-fabric-range/TpaTeleportFabric121Range/
examples/tpateleport-1.21.11-fabric/TpaTeleportFabric12111/
```

Use that as a reference for:

- folder structure
- `mod.txt`
- `version.txt`
- how a version-specific source tree should look
