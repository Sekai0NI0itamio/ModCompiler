---
id: FABRIC-165-MOB-ENTITY-PACKAGE
title: Fabric 1.16.5 — MobEntity is in net.minecraft.entity.mob, not net.minecraft.entity
tags: [fabric, compile-error, yarn-mappings, MobEntity, 1.16.5]
versions: [1.16.5]
loaders: [fabric]
symbols: [MobEntity]
error_patterns: ["cannot find symbol.*class MobEntity", "Mixin has no targets"]
---

## Issue

Fabric 1.16.5 Mixin targeting `MobEntity` fails to compile when the import uses
`net.minecraft.entity.MobEntity`.

## Error

```
error: cannot find symbol
import net.minecraft.entity.MobEntity;
                           ^
  symbol:   class MobEntity
  location: package net.minecraft.entity

Mixin has no targets
```

## Root Cause

In Fabric 1.16.5 (yarn mappings), `MobEntity` lives in the `mob` subpackage:

```
net.minecraft.entity.mob.MobEntity   ← CORRECT for 1.16.5
net.minecraft.entity.MobEntity       ← WRONG (does not exist)
```

This is different from 1.17+ where the class is also in `entity.mob`, but the
confusion arises because the decompiled sources for 1.16.5 may show the class
at the top-level `entity` package in some contexts.

## Fix

Always use `net.minecraft.entity.mob.MobEntity` for Fabric 1.16.5:

```java
// WRONG — 1.16.5 fabric
import net.minecraft.entity.MobEntity;

// CORRECT — 1.16.5 fabric
import net.minecraft.entity.mob.MobEntity;
```

The same applies to 1.17–1.20.6 fabric (yarn mappings) — `MobEntity` is always
in `net.minecraft.entity.mob`.

For 1.21+ fabric (Mojang mappings), the class is `net.minecraft.world.entity.Mob`
(not `MobEntity` at all).

## Package Summary

| Version range | Loader | Class |
|---------------|--------|-------|
| 1.16.5–1.20.6 | Fabric (yarn) | `net.minecraft.entity.mob.MobEntity` |
| 1.21+ | Fabric (Mojang) | `net.minecraft.world.entity.Mob` |

## Verified

Confirmed in No Hostile Mobs all-versions port (run 1 failed, run 2 fixed, May 2026).
Fabric 1.16.5 Mixin failed with "Mixin has no targets" because the import was wrong.
