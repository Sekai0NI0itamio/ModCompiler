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

## Recently Added

| ID | Title | Loaders | Versions |
|----|-------|---------|----------|
| FORGE-117-NO-REGISTER-KEY-MAPPINGS-EVENT | Forge 1.17.1 — RegisterKeyMappingsEvent does not exist | forge | 1.17, 1.17.1 |
| FABRIC-119-REGISTRY-PATH-SPLIT | Fabric 1.19–1.19.2 uses old registry path, 1.19.3+ uses new | fabric | 1.19–1.19.4 |
| KEYMAPPING-CATEGORY-REQUIRED-1219 | 1.21.9+ — KeyMapping requires Category not String | forge, neoforge, fabric | 1.21.9–1.21.11 |
| NEOFORGE-1219-MODCONTAINER-REQUIRED | NeoForge 1.21.9+ — ModContainer required in @Mod constructor | neoforge | 1.21.9–1.21.11 |
| ANCHOR-ONLY-YARN-PITFALL | anchor_only mode — Loom downloads yarn for exact MC version | fabric | 1.19–1.19.2 |
| ALWAYS-DIAGNOSE-BEFORE-BUILDING | Always run diagnosis before building — never rebuild published versions | all | — |
