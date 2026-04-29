---
id: FORGE-EB7-FMLCOMMONSETUPEVENT-GETBUS
title: Forge 1.21.6+ EventBus 7 — FMLCommonSetupEvent.getBus(context.getModBusGroup()) replaces context.getModEventBus()
tags: [forge, compile-error, eventbus7, api-change, FMLCommonSetupEvent, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1]
versions: [1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.2]
loaders: [forge]
symbols: [FMLJavaModLoadingContext, getModEventBus, getModBusGroup, FMLCommonSetupEvent, BusGroup]
error_patterns: ["cannot find symbol.*method getModEventBus.*FMLJavaModLoadingContext"]
---

## Issue

Forge 1.21.6+ (EventBus 7) fails to compile when calling `context.getModEventBus()`
on a `FMLJavaModLoadingContext` parameter.

## Error

```
error: cannot find symbol
        context.getModEventBus().addListener(this::setup);
               ^
  symbol:   method getModEventBus()
  location: variable context of type FMLJavaModLoadingContext
```

## Root Cause

In Forge 1.21.6+ (EventBus 7), `FMLJavaModLoadingContext.getModEventBus()` was
removed. The new pattern uses `BusGroup` obtained from `context.getModBusGroup()`,
and each event class has a static `getBus(BusGroup)` factory method.

| Forge version | Mod bus registration |
|---------------|---------------------|
| 1.16.5–1.21.5 | `FMLJavaModLoadingContext.get().getModEventBus().addListener(handler)` |
| 1.21.6–26.1.2 | `FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(handler)` |

## Fix

Use the event class's static `getBus()` method with the `BusGroup`:

```java
// WRONG (1.21.5 and below pattern):
public MyMod(FMLJavaModLoadingContext context) {
    context.getModEventBus().addListener(this::setup);
}

// CORRECT (1.21.6+ EventBus 7):
public MyMod(FMLJavaModLoadingContext context) {
    FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(this::setup);
}
```

For other mod-bus events, the same pattern applies:
```java
RegisterEvent.getBus(context.getModBusGroup()).addListener(this::register);
GatherDataEvent.getBus(context.getModBusGroup()).addListener(this::gatherData);
```

In the generator script, use separate source strings for pre-1.21.6 and 1.21.6+:

```python
SRC_1205_FORGE = "... FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup) ..."
SRC_1216_FORGE = "... FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(this::setup) ..."
```

## Verified

Confirmed in Stackable Totems all-versions port (run 2, April 2026).
Forge 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.2 all passed after this fix.

## See Also

- `FORGE-EB7-EVENTBUS7-PATTERN` — general EventBus 7 migration guide
- `FORGE-EB7-POST-BUS-ADDLISTENER` — client tick event with EventBus 7
