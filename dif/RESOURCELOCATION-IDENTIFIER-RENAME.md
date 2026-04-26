---
id: RESOURCELOCATION-IDENTIFIER-RENAME
title: ResourceLocation ↔ Identifier renaming across Forge/NeoForge versions
tags: [forge, neoforge, compile-error, api-change, ResourceLocation, Identifier, 1.21.9, 1.21.10, 1.21.11, 26.1]
versions: [1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2]
loaders: [forge, neoforge]
symbols: [ResourceLocation, Identifier]
error_patterns: ["cannot find symbol.*ResourceLocation", "cannot find symbol.*Identifier", "import net.minecraft.resources.ResourceLocation.*cannot find", "import net.minecraft.resources.Identifier.*cannot find"]
---

## Issue

`ResourceLocation` and `Identifier` are the same concept but the class name changes across Forge/NeoForge versions. Using the wrong name causes a compile error.

## Error

```
error: cannot find symbol
import net.minecraft.resources.Identifier;
                              ^
  symbol:   class Identifier
  location: package net.minecraft.resources
```

or

```
error: cannot find symbol
import net.minecraft.resources.ResourceLocation;
                              ^
  symbol:   class ResourceLocation
  location: package net.minecraft.resources
```

## Root Cause

Mojang renamed the class between versions:

| Forge version | Class name |
|---------------|-----------|
| Forge 52-60.x (1.21.1–1.21.10) | `ResourceLocation` |
| Forge 61.x (1.21.11) | `Identifier` |
| Forge 64.x (26.1.2) | `Identifier` |
| NeoForge 1.20.x–1.21.11 | `ResourceLocation` |
| NeoForge 26.1.x | `Identifier` |

## Fix

Use the AI Source Search to verify which name is correct for the specific version:

```bash
python3 scripts/ai_source_search.py --version 1.21.11 --loader forge \
    --queries "class Identifier" "class ResourceLocation" --local-only
```

Then use the correct name in your source. If a version range spans both names (e.g. 1.21.9-1.21.11), **split the range** into separate targets:
- 1.21.9, 1.21.10 → `ResourceLocation`
- 1.21.11 → `Identifier`

## Verified

Confirmed in Day Counter port (run-20260426-005930). Forge 1.21.11 uses `Identifier`, Forge 1.21.9-1.21.10 use `ResourceLocation`.
