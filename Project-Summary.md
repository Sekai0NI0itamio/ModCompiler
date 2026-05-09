What this project is
A GitHub-based system for building and publishing Minecraft mods across every supported version and loader. The user co-develops a mod with an AI IDE agent, approves it locally in a 1.12.2 Forge workspace, then the system fans it out to all versions via GitHub Actions.

Core docs
SYSTEM_MANUAL.md — The build system works by committing a zip to incoming/, running the Build Mods GitHub Actions workflow, and downloading the resulting jars. Each version range has a vendored template. A version-manifest.json is the single source of truth for what versions/loaders are supported. There's also a Jar Decompile workflow and an optional Modrinth auto-publish stage.

IDE_AGENT_INSTRUCTION_SHEET.txt — The master operating guide for AI agents. Two paths for updating a mod: Path A (from a Modrinth link — must run Fetch Modrinth Project workflow first, mandatory), Path B (from local source in ModCollection). Critical rules: never compile cross-version locally, always use --failed-only on retries, run run_build.py blocking (not background), write scripts to files not inline python3 -c, one shell command per call.

AI_AGENT_MODRINTH_WORKFLOW.md — When preparing a mod for Modrinth, the AI agent should pre-generate 4 metadata files (project_info.json, version_info.json, description.md, summary.txt) in ToBeUploaded/<n>/ai_metadata/ and use --use-ai-metadata flag. This is 10x faster than the standard flow.

MODRINTH_PUBLISHING_GUIDE.md — Two-command workflow: generate then create-drafts --verified. Always use --only-bundle to target a specific mod. Local source skips decompilation automatically.

QUICK_START_NEW_MOD.md — Clean workspace → create mod with asd.itamio.<modname> package → build → verify jar → test in Minecraft.

LOCAL_MOD_DEVELOPMENT_WORKFLOW.md — One mod at a time in the workspace. Always save to ModCollection before cleaning. Clean src/main/java/com/* and src/main/resources/assets/* between mods.

LESSONS_LEARNED.md — Documents the source conflict bug: multiple mod packages in src/ all compile into one jar. Gradle compiles everything it finds.

TROUBLESHOOTING_MOD_CONFLICTS.md — Diagnostic commands to detect conflicts, solutions ranging from selective package removal to full workspace reset.

FORGE_API_NOTES.md — Quick reference: use getModEventBus() not getModBusGroup(), Brigadier needs ArgumentType instances, teleport APIs change — always verify in templates.

PACKAGE_NAMING_STANDARD.md — All mods must use asd.itamio.<modname>, author Itamio, main class <ModName>Mod.

MOD_IDEAS.md — List of mod ideas and completed mods. Vein Miner is listed as completed (#13).

ADDING_NEW_MINECRAFT_VERSION.md — Full checklist for adding a new MC version: download official MDKs (never guess), create templates, add to version-manifest.json, run a test build. Documents all the 26.1.2 challenges (loom SNAPSHOT, no mappings block for Forge, NeoForge constructor injection, duplicate mods.toml, beta version wildcards).

Example docs (real build histories)
SORT_CHEST_ALL_VERSIONS.md — 27 versions, 10 build runs. Key API table for every Forge/Fabric version covering event classes, button API, item comparison, and carried stack access.

SET_HOME_ANYWHERE_ALL_VERSIONS.md — 39 versions, 21 build runs. Documents 7 distinct SavedData/computeIfAbsent API eras from 1.8.9 through 1.21.11. Critical lesson: never chain .replace() calls — write explicit strings per version. Also documents the --failed-only system, blocking vs background processes, and the eventbus.api removal in Forge 1.21.6+.

TOGGLE_SPRINT_ALL_VERSIONS.md — 42 new versions, zero failures. Documents fabric_presplit vs fabric_split source directory split, Yarn field name changes in 1.18, Forge Component API eras, NeoForge ClientTickEvent package move, and FMLEnvironment.dist removal in NeoForge 1.21.9+.

CRAFTABLE_SLIME_BALLS_ALL_VERSIONS.md — 61 versions, zero failures. Recipe-only mod. Key lesson: zip must have mod folders at the top level (use relative_to(bundle_dir)). 1.8.9 needs Java recipe registration, 1.12.2 uses assets/ path, 1.13+ uses data/ path.

SEED_PROTECT_ALL_VERSIONS.md — 73 versions total across two phases. Documents Fabric yarn vs Mojang mapping boundary (1.21 is the split), Forge 1.17.x event.world vs event.level, Forge 1.21.6+ EventBus 7 (BUS.addListener(true, handler) returning boolean), NeoForge Bus.FORGE not Bus.GAME, and why 26.x was skipped (all loaders unstable).

ALLOW_OFFLINE_LAN_JOIN_ALL_VERSIONS.md — 72 versions, 6 build runs. Documents the three Forge server-starting event eras, Fabric Log4j vs SLF4J split at 1.17, Forge version gaps (1.17 plain, 1.20.5, 1.21.2), and the eventbus.api.listener sub-package that doesn't exist anywhere.

COMMON_SERVER_CORE_ALL_VERSIONS.md — Documents false-positive shell detection (never trust local file size, use Modrinth API), decompiled source using obfuscated names (use it to understand logic, not to compile), Method.invoke() varargs with method references, HolderLookup.Provider not available pre-1.20.5, and the Fabric 1.17-1.20.x Yarn incompatibility with Mojang source.