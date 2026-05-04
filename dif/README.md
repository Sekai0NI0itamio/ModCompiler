# Documentary of Issues and Fixes (DIF)

This directory contains structured issue/fix documents for the ModCompiler system.
Each file documents a specific problem encountered during mod porting, its root cause,
and the verified fix.

## How to Use

### Search (natural language)
```bash
python3 scripts/dif_search.py "farmland trample event neoforge 26.1"
python3 scripts/dif_search.py "modrinth project not found publish failed"
python3 scripts/dif_search.py "cannot find symbol EventBusSubscriber forge"
python3 scripts/dif_search.py "fabric yarn mojang mappings FarmBlock"
```

### Match a build log
```bash
python3 scripts/dif_search.py --match-log ModCompileRuns/run-xxx/artifacts/all-mod-builds/mods/mymod/build.log
```

### Scan a full run for DIF matches
```bash
python3 scripts/dif_search.py --scan-run ModCompileRuns/run-20260426-005930
```

### List all entries
```bash
python3 scripts/dif_search.py --list
```

### Create a new entry
```bash
python3 scripts/dif_search.py --create
```

## File Format

Each `.md` file has a YAML front-matter block followed by Markdown body:

```markdown
---
id: UNIQUE-ID
title: Short human-readable title
tags: [forge, fabric, compile-error, api-change, ...]
versions: [1.21.6, 1.21.7, 1.21.8]
loaders: [forge]
symbols: [ClassName, AnotherClass]
error_patterns: ["regex pattern matching the error", "another pattern"]
---

## Issue
Description of the problem.

## Error
\`\`\`
exact error text
\`\`\`

## Root Cause
Why it happens.

## Fix
How to fix it.

## Verified
Which versions/runs confirmed this fix works.
```

## Integration

The DIF system is automatically used by:
- **`run_build.py`** — after failed builds, matches errors against DIF and shows relevant entries
- **`ai_source_search.py`** — `--dif-search` flag for NLP search alongside source search
- **`dif_search.py`** — standalone search tool

All search logic lives in **`scripts/dif_core.py`** — the single source of truth.

## Current Entries

| ID | Title | Loaders | Versions |
|----|-------|---------|----------|
| FORGE-51-56-OVERLAY-API-INACCESSIBLE | Forge 1.21/1.21.6-8 HUD overlay API not in classpath | forge | 1.21, 1.21.6-8 |
| RESOURCELOCATION-IDENTIFIER-RENAME | ResourceLocation ↔ Identifier renaming | forge, neoforge | 1.21.9-11, 26.1.x |
| FORGE-EB7-EVENTBUS7-PATTERN | Forge 1.21.6+ EventBus 7 migration | forge | 1.21.6+ |
| NEOFORGE-26-EVENTBUSSUBSCRIBER-STANDALONE | NeoForge 26.1 standalone @EventBusSubscriber | neoforge | 26.1.x |
| FABRIC-YARN-VS-MOJANG-MAPPINGS | Fabric yarn vs Mojang mappings class names | fabric | all |
| MODRINTH-WRONG-SLUG | Modrinth publish "project not found" | — | — |
| FABRIC-26-HUD-CALLBACK-REMOVED | Fabric 26.1 HudRenderCallback removed | fabric | 26.1.x |
| FORGE-17-EVENT-WORLD-VS-LEVEL | Forge 1.17 event.world vs event.level | forge | 1.17.x |
| NEOFORGE-BUS-FORGE-NOT-GAME | NeoForge Bus.FORGE not Bus.GAME | neoforge | 1.20.x-1.21.x |
| VERSION-STRING-MUST-MATCH-MANIFEST | Version string must match manifest exactly | all | — |
| FABRIC-SPLIT-VS-PRESPLIT-SOURCE-DIR | Fabric split vs presplit source directory | fabric | all |
| FORGE-SCREEN-EVENT-CLASS-RENAME | GuiScreenEvent → ScreenEvent in 1.18 | forge | 1.8.9-1.18 |
| FORGE-ITEM-COMPARISON-API-CHANGE-1205 | isSameItemSameTags removed in 1.20.5 | forge, fabric | 1.20.5+ |
| GRADLE-TRANSIENT-NETWORK-FAILURE | Transient Gradle/Maven download failures | all | — |
| MULTIPLE-MOD-PACKAGES-IN-SOURCE | Multiple mod packages compiled into one jar | all | — |
| FORGE-BUTTON-API-CHANGE-1194 | Button constructor vs Button.builder() | forge | 1.8.9-1.19.4 |
| GUIGRAPHICSEXTRACTOR-VS-GUIGRAPHICS | GuiGraphicsExtractor in 26.1 layer rendering | forge, neoforge | 26.1.x |
| FAILED-ONLY-FLAG-EMPTY-RESULT | --failed-only returns empty set | — | — |
| SOURCE-SEARCH-CLASS-EXISTS-BUT-NOT-ACCESSIBLE | Source search finds class but it's not in API | forge | 1.21, 1.21.6-8 |
| FABRIC-PROTECTED-FIELDS-REFLECTION | Fabric HandledScreen protected fields | fabric | all |
| NEVER-REBUILD-GREEN-TARGETS | Best practice: use --failed-only | all | — |
| FORGE-26-FORGELAYER-EXTRACT-PATTERN | Forge 26.1.2 ForgeLayer.extract() pattern | forge | 26.1.2 |
| FORGE-FSTRING-ESCAPING-IN-GENERATOR | Generator script — Python f-string not applied, leaving {MOD_ID} and {{ literal braces in Java output | all | — |
| FABRIC-MANIFEST-VERSION-VS-PUBLISHED-VERSION | Fabric supported_versions differs from Forge for same range (1.17 not 1.17.1, 1.21 not 1.21.1) | fabric | 1.17, 1.21 |
| FORGE-SCREEN-EVENT-RENDER-SUBCLASS-HISTORY | Full history of Forge ScreenEvent subclass names and graphics accessors across all versions | forge | 1.16.5–26.1.2 |
| FABRIC-TITLESCREEN-MIXIN-RENDER-SIGNATURE-HISTORY | Full history of Fabric TitleScreen mixin render() signature across all versions | fabric | 1.16.5–26.1.2 |
| FABRIC-PARTICLEENGINE-CLEARPARTICLES-PRIVATE | Fabric 1.21–1.21.8 ParticleEngine.clearParticles() has private access — use reflection | fabric | 1.21–1.21.8 |
| NEOFORGE-120-LEVELTICK-NOT-IN-EARLY-20X | NeoForge 1.20.2–1.20.4 event.tick package doesn't exist — use RenderLevelStageEvent | neoforge | 1.20.2–1.20.4 |

## Recently Added

| ID | Title | Loaders | Versions |
|----|-------|---------|----------|
| FABRIC-26-SERVER-LIFECYCLE-EVENTS-UNCHANGED | Fabric 26.1.x server lifecycle events unchanged — only client HUD APIs removed | fabric | 26.1.x |
| NEOFORGE-26-FMLJAVAMODLOADINGCONTEXT-REMOVED | NeoForge 26.1+ FMLJavaModLoadingContext removed, use constructor injection | neoforge | 26.1.x |
| ZIP-PATH-MUST-BE-RELATIVE-TO-BUNDLE-FOLDER | Zip bundle must have mod folders at top level — run zip from inside bundle folder | all | — |
| FORGE-TICKEVENT-PACKAGE-HISTORY | Forge TickEvent package: fml.common.gameevent (1.8.9–1.12.2) vs event.TickEvent (1.16.5+) | forge | 1.8.9–26.1.2 |
| FORGE-WORLDEVENT-VS-LEVELEVENT-BOUNDARY | WorldEvent → LevelEvent rename happened in 1.19, NOT 1.18 | forge | 1.18–1.19.4 |
| FORGE-LEVEL-GETGAMERULES-REMOVED-1213 | Forge 1.21.3+ Level.getGameRules() removed — must cast to ServerLevel | forge | 1.21.3–26.1.2 |
| FORGE-GAMERULES-PACKAGE-MOVED-1219 | GameRules moved to gamerules subpackage in 1.21.9+ with new GameRule<Boolean> API | forge, neoforge, fabric | 1.21.9–26.1.2 |
| FORGE-WORLDEVENT-GETWORLD-RETURNS-IWORLD | WorldEvent.getWorld() returns IWorld in 1.12.2–1.16.5, cast required | forge | 1.12.2–1.16.5 |
| NEOFORGE-SERVERTICK-NOT-IN-EARLY-20X | NeoForge 1.20.2–1.20.6 ServerTickEvent doesn't exist in early 20.2.93 build | neoforge | 1.20.2–1.20.6 |
| FABRIC-26-GAMERULES-NEW-API | Fabric 26.1+ GameRules moved to gamerules subpackage with GameRule<Boolean> API | fabric | 26.1.x |
| FORGE-FSTRING-ESCAPING-IN-GENERATOR | Generator script — Python f-string not applied, leaving {MOD_ID} and {{ literal braces in Java output | all | — |
| FABRIC-MANIFEST-VERSION-VS-PUBLISHED-VERSION | Fabric supported_versions differs from Forge for same range (1.17 not 1.17.1, 1.21 not 1.21.1) | fabric | 1.17, 1.21 |
| FORGE-SCREEN-EVENT-RENDER-SUBCLASS-HISTORY | Full history of Forge ScreenEvent subclass names and graphics accessors (DrawScreenEvent.Post → Render.Post, getPoseStack → getGuiGraphics) | forge | 1.16.5–26.1.2 |
| FABRIC-TITLESCREEN-MIXIN-RENDER-SIGNATURE-HISTORY | Full history of Fabric TitleScreen mixin render() signature (MatrixStack → DrawContext → GuiGraphics → extractRenderState) | fabric | 1.16.5–26.1.2 |
| ALWAYS-CHECK-FULL-MANIFEST-NOT-JUST-PUBLISHED | Always compute missing targets from version-manifest.json, not just from what's published | all | — |
| FORGE-LIVINGUSETOTEM-NOT-IN-41X | Forge 1.19–1.19.2 (41.x) — LivingUseTotemEvent does not exist, added in 44.x (1.19.3+) | forge | 1.19–1.19.2 |
| FORGE-EB7-FMLCOMMONSETUPEVENT-GETBUS | Forge 1.21.6+ EventBus 7 — FMLCommonSetupEvent.getBus(context.getModBusGroup()) replaces getModEventBus() | forge | 1.21.6–26.1.2 |
| FORGE-DATACOMPONENTS-ITEM-STACK-SIZE | Forge/NeoForge 1.20.5+ — Item max stack size stored in DataComponents.MAX_STACK_SIZE | forge, neoforge | 1.20.5–26.1.2 |
| FORGE-ITEM-PACKAGE-PRE-117 | Forge pre-1.17 uses net.minecraft.item, 1.17+ uses net.minecraft.world.item | forge | 1.16.5–1.18.2 |
| FABRIC-121-MOJANG-MAPPINGS-SWITCH | Fabric 1.21+ switched to Mojang mappings — MinecraftClient→Minecraft, all class names changed | fabric | 1.21–26.1.2 |
| NEOFORGE-TICKEVENT-CLIENT-PACKAGE | NeoForge 1.21.2+ ClientTickEvent moved to net.neoforged.neoforge.client.event | neoforge | 1.21.2–26.1.2 |
| NEOFORGE-FMLENVIRONMENT-GETDIST | NeoForge 1.21.9+ FMLEnvironment.dist field → FMLEnvironment.getDist() method | neoforge | 1.21.9–26.1.2 |
| FABRIC-118-INTERACTITEM-WORLD-ARG | Fabric 1.18.x interactItem() requires World as 2nd argument | fabric | 1.18–1.18.2 |
| FORGE-EB7-POST-BUS-ADDLISTENER | Forge 1.21.6+ EventBus 7 — use TickEvent.ClientTickEvent.Post.BUS.addListener() | forge | 1.21.6–26.1.2 |
| FORGE-117-NO-REGISTER-KEY-MAPPINGS-EVENT | Forge 1.17.1 — RegisterKeyMappingsEvent does not exist | forge | 1.17, 1.17.1 |
| FABRIC-119-REGISTRY-PATH-SPLIT | Fabric 1.19–1.19.2 uses old registry path, 1.19.3+ uses new | fabric | 1.19–1.19.4 |
| KEYMAPPING-CATEGORY-REQUIRED-1219 | 1.21.9+ — KeyMapping requires Category not String | forge, neoforge, fabric | 1.21.9–1.21.11 |
| NEOFORGE-1219-MODCONTAINER-REQUIRED | NeoForge 1.21.9+ — ModContainer required in @Mod constructor | neoforge | 1.21.9–1.21.11 |
| ANCHOR-ONLY-YARN-PITFALL | anchor_only mode — Loom downloads yarn for exact MC version | fabric | 1.19–1.19.2 |
| ALWAYS-DIAGNOSE-BEFORE-BUILDING | Always run diagnosis before building — never rebuild published versions | all | — |
| FABRIC-COMMAND-API-V1-VS-V2 | Fabric CommandRegistrationCallback — v1 (1.16.5–1.18.x) vs v2 (1.19+) | fabric | 1.16.5–1.20.6 |
| FABRIC-SEND-MESSAGE-SIGNATURE | Fabric sendMessage() takes (Text, boolean overlay), not UUID | fabric | 1.16.5–1.20.6 |
| FABRIC-GET-SERVER-VS-GET-MINECRAFT-SERVER | Fabric 1.16.5 uses getMinecraftServer(), 1.17+ uses getServer() | fabric | 1.16.5–1.20.1 |
| FORGE-SEND-MESSAGE-UUID-REMOVED | Forge/NeoForge sendMessage(Component, UUID) removed in 1.19+, use sendSystemMessage() | forge, neoforge | 1.19–26.1.2 |
| FORGE-COMMAND-SOURCE-1165 | Forge 1.16.5 CommandSource.asPlayer() does not exist | forge | 1.16.5 |
| JAVA6-COMPAT-189-FORGE | Forge 1.8.9 Java 6: no underscores, no diamond <>, no lambdas | forge | 1.8.9 |
| LAMBDA-COUNT-CAPTURE | Lambda captures non-final count — use final int finalCount = count | forge, neoforge, fabric | 1.20–26.1.2 |
| VERSION-STRING-26X-ANCHOR | 26.1-26.x version string must be anchor version 26.1.2 | all | 26.1.2 |
| NEOFORGE-120-SUPPORTED-VERSIONS | NeoForge 1.20 only supports 1.20.2, 1.20.4, 1.20.5, 1.20.6 | neoforge | 1.20.2–1.20.6 |
| FORGE-117-TEMPLATE-SUPPORTS-1171-ONLY | Forge/Fabric 1.17 template only supports 1.17.1, not 1.17 | forge, fabric | 1.17.1 |
| FABRIC-26-SERVER-LIFECYCLE-EVENTS-UNCHANGED | Fabric 26.1.x server lifecycle events (ServerLifecycleEvents) are unchanged — only client HUD APIs were removed | fabric | 26.1.x |
| NEOFORGE-26-FMLJAVAMODLOADINGCONTEXT-REMOVED | NeoForge 26.1+ — FMLJavaModLoadingContext removed, use constructor injection (IEventBus, ModContainer) | neoforge | 26.1.x |
| ZIP-PATH-MUST-BE-RELATIVE-TO-BUNDLE-FOLDER | Zip bundle must have mod folders at top level — run zip from inside the bundle folder, not workspace root | all | — |
| FABRIC-PARTICLEENGINE-CLEARPARTICLES-PRIVATE | Fabric 1.21–1.21.8 ParticleEngine.clearParticles() has private access — use reflection | fabric | 1.21–1.21.8 |
| NEOFORGE-120-LEVELTICK-NOT-IN-EARLY-20X | NeoForge 1.20.2–1.20.4 net.neoforged.neoforge.event.tick package doesn't exist in early build — use RenderLevelStageEvent | neoforge | 1.20.2–1.20.4 |
