---
id: FABRIC-SEND-MESSAGE-SIGNATURE
title: Fabric ServerPlayerEntity.sendMessage() — boolean overlay arg, not UUID
tags: [fabric, compile-error, api-change, sendMessage, ServerPlayerEntity, 1.16.5, 1.17, 1.18, 1.19, 1.20]
versions: [1.16.5, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6]
loaders: [fabric]
symbols: [sendMessage, ServerPlayerEntity, LiteralText, Text]
error_patterns: ["incompatible types: UUID cannot be converted to boolean"]
---

## Issue

When sending a message to a player in Fabric, the two-argument `sendMessage()`
overload takes `(Text, boolean)` where the boolean is `overlay` (true = action bar,
false = chat). Passing a `UUID` as the second argument causes a compile error.

## Error

```
error: incompatible types: UUID cannot be converted to boolean
    player.sendMessage(new LiteralText("Hello"), player.getUuid());
                                                        ^
```

## Root Cause

Fabric's `ServerPlayerEntity.sendMessage()` has this signature:
```java
public void sendMessage(Text message, boolean overlay)
```

The `overlay` boolean controls whether the message appears in the action bar
(`true`) or in chat (`false`). There is no overload that takes a UUID.

This is different from Forge/NeoForge where `sendMessage(Component, UUID)` existed
in some versions.

## Fix

Always pass `false` as the second argument to send a chat message:

```java
// Correct — sends to chat
player.sendMessage(new LiteralText("Hello"), false);

// Correct for 1.19+ (Text.literal replaces LiteralText)
player.sendMessage(Text.literal("Hello"), false);
```

## Version Matrix

| MC Version | Text class | sendMessage signature |
|------------|-----------|----------------------|
| 1.16.5–1.18.x | `new LiteralText("...")` | `sendMessage(text, false)` |
| 1.19–1.20.6 | `Text.literal("...")` | `sendMessage(text, false)` |
| 1.21+ | `Text.literal("...")` | `sendSystemMessage(text)` (no overlay arg) |

## Verified

Confirmed in TPA Teleport all-versions port (runs 5–7, April 2026).
