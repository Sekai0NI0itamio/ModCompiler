---
id: FABRIC-SPLIT-VS-PRESPLIT-SOURCE-DIR
title: Fabric — wrong source directory (src/main/java vs src/client/java) for split vs presplit adapters
tags: [fabric, build-system, source-layout, fabric_split, fabric_presplit, 1.20]
versions: [1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1]
loaders: [fabric]
symbols: []
error_patterns: ["class file for.*not found", "cannot find symbol.*client", "source set.*not found"]
---

## Issue

Client-side Fabric code fails to compile or is not included in the jar because it was placed in the wrong source directory.

## Root Cause

The Fabric build system uses two different adapter families with different source layouts:

| Adapter | Versions | Client source dir | Common source dir |
|---------|----------|-------------------|-------------------|
| `fabric_presplit` | 1.16.5–1.19.4 | `src/main/java` | `src/main/java` |
| `fabric_split` | 1.20+ | `src/client/java` | `src/main/java` |

The `fabric.mod.json` file **always** goes in `src/main/resources` regardless of adapter.

## Fix

Check the `adapter_family` in `version-manifest.json` for the target version range:

```json
"fabric": {
  "adapter_family": "fabric_split"  // or "fabric_presplit"
}
```

Then place source files accordingly:

**fabric_presplit (1.16.5–1.19.4):**
```
src/main/java/com/mymod/MyMod.java          ← main class
src/main/java/com/mymod/client/ClientCode.java  ← client code (same dir)
src/main/resources/fabric.mod.json
src/main/resources/mymod.mixins.json
```

**fabric_split (1.20+):**
```
src/main/java/com/mymod/MyMod.java          ← main class
src/client/java/com/mymod/client/ClientCode.java  ← client code (separate dir!)
src/main/resources/fabric.mod.json
src/main/resources/mymod.mixins.json
```

In the generator script, use the `use_client_srcset` flag:
```python
# fabric_presplit: use_client_srcset=False
("MyMod-1.19.4-fabric", "1.19.4", "fabric", ..., False)

# fabric_split: use_client_srcset=True
("MyMod-1.20.1-fabric", "1.20.1", "fabric", ..., True)
```

## Verified

Confirmed in Sort Chest port (Lesson 4) and multiple other mods.
