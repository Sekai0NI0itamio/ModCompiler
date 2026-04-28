---
id: JAVA6-COMPAT-189-FORGE
title: Forge 1.8.9 — Java 6 source level: no underscores in literals, no diamond <>, no lambdas
tags: [forge, compile-error, java6, 1.8.9, diamond-operator, lambda, underscore-literal]
versions: [1.8.9]
loaders: [forge]
symbols: [ConcurrentHashMap, diamond, lambda, underscore]
error_patterns: ["underscores in literals are not supported in -source 1.6", "diamond operator is not supported in -source 1.6", "lambda expressions are not supported in -source 1.6"]
---

## Issue

The Forge 1.8.9 template compiles with Java source level 1.6. Several modern
Java features are not available and cause compile errors.

## Errors

```
error: underscores in literals are not supported in -source 1.6
    static final long TIMEOUT_MS = 60_000L;
                                     ^

error: diamond operator is not supported in -source 1.6
    Map<String, Long> pending = new ConcurrentHashMap<>();
                                                      ^

error: lambda expressions are not supported in -source 1.6
    pending.entrySet().removeIf(e -> now - e.getValue() > TIMEOUT_MS);
                                ^
```

## Root Cause

The 1.8.9 Forge template sets `sourceCompatibility = 1.6` in its build.gradle.
Java 6 does not support:
- Underscore separators in numeric literals (`60_000L`)
- Diamond operator for generic type inference (`new HashMap<>()`)
- Lambda expressions (`e -> ...`)
- `removeIf()` and other Java 8 stream/functional methods

## Fix

**Numeric literals** — remove underscores:
```java
// Wrong
static final long TIMEOUT_MS = 60_000L;
// Correct
static final long TIMEOUT_MS = 60000L;
```

**Diamond operator** — specify full generic type:
```java
// Wrong
Map<String, Long> map = new ConcurrentHashMap<>();
// Correct
Map<String, Long> map = new ConcurrentHashMap<String, Long>();
```

**Lambdas / removeIf** — use explicit iterator:
```java
// Wrong
pending.entrySet().removeIf(e -> now - e.getValue() > TIMEOUT_MS);

// Correct
Iterator<Map.Entry<String, Long>> it = pending.entrySet().iterator();
while (it.hasNext()) {
    if (now - it.next().getValue() > TIMEOUT_MS) it.remove();
}
```

**Also**: `getCommandSenderName()` is the correct method on `EntityPlayerMP` in
1.8.9 (not `getName()` which was added later). Use `getGameProfile().getName()`
as a safe alternative that works in both 1.8.9 and later versions.

## Verified

Confirmed in TPA Teleport all-versions port (run 4, April 2026).
