---
id: FORGE-SEND-MESSAGE-UUID-REMOVED
title: Forge/NeoForge — sendMessage(Component, UUID) removed in 1.19+, use sendSystemMessage()
tags: [forge, neoforge, compile-error, api-change, sendMessage, sendSystemMessage, 1.19, 1.20, 1.21]
versions: [1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.2]
loaders: [forge, neoforge]
symbols: [sendMessage, sendSystemMessage, ServerPlayer, Component]
error_patterns: ["incompatible types: UUID cannot be converted to ResourceKey", "incompatible types: UUID cannot be converted to boolean", "cannot find symbol.*method sendMessage.*UUID"]
---

## Issue

In Forge/NeoForge 1.19+, `ServerPlayer.sendMessage(Component, UUID)` was removed.
The replacement is `sendSystemMessage(Component)` which takes only one argument.

## Error

```
error: incompatible types: UUID cannot be converted to ResourceKey<ChatType>
    player.sendMessage(Component.literal("Hello"), player.getUUID());
                                                          ^
```

or in 1.21+:

```
error: incompatible types: UUID cannot be converted to boolean
    player.sendSystemMessage(Component.literal("Hello"), player.getUUID());
                                                                ^
```

## Root Cause

The chat system was overhauled in 1.19. `sendMessage(Component, UUID)` was
replaced with `sendSystemMessage(Component)` for system/non-player messages.
The UUID parameter (which identified the sender for chat attribution) is no
longer needed for system messages.

In 1.17–1.18, `sendMessage(Component, UUID)` still existed and worked.
In 1.19+, it was removed entirely.

## Fix

**1.17–1.18.x (sendMessage with UUID still works):**
```java
player.sendMessage(new TextComponent("Hello"), player.getUUID());
```

**1.19+ (use sendSystemMessage, no UUID):**
```java
player.sendSystemMessage(Component.literal("Hello"));
```

## Version Matrix

| MC Version | Loader | Method |
|------------|--------|--------|
| 1.17–1.18.x | Forge | `player.sendMessage(new TextComponent("..."), player.getUUID())` |
| 1.19–1.20.6 | Forge | `player.sendSystemMessage(Component.literal("..."))` |
| 1.21+ | Forge | `player.sendSystemMessage(Component.literal("..."))` |
| 1.20.2+ | NeoForge | `player.sendSystemMessage(Component.literal("..."))` |
| 26.1.x | Forge/NeoForge | `player.sendSystemMessage(Component.literal("..."))` |

## Common Mistake

When deriving 1.19+ source from 1.17-1.18 source via string replacement, the
replacement chain:
1. `new TextComponent(` → `Component.literal(`
2. `.sendMessage(Component.literal(` → `.sendSystemMessage(Component.literal(`

Step 2 only replaces the opening of the call. The trailing `, player.getUUID())`
must also be removed. If you forget to strip the UUID arg, you get the
"UUID cannot be converted to ResourceKey" error.

**Correct replacement pattern:**
```python
src = src.replace(".sendMessage(Component.literal(", ".sendSystemMessage(Component.literal(")
src = src.replace("), player.getUUID());", "));")
src = src.replace("), req.getUUID());", "));")
```

## Verified

Confirmed in TPA Teleport all-versions port (runs 3–6, April 2026).
