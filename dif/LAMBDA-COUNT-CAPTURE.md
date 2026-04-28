---
id: LAMBDA-COUNT-CAPTURE
title: Lambda captures non-final count variable — use final int finalCount = count before lambda
tags: [forge, neoforge, fabric, compile-error, lambda, effectively-final, 1.20, 1.21]
versions: [1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.2]
loaders: [forge, neoforge, fabric]
symbols: [sendSuccess, sendFeedback, count, finalCount]
error_patterns: ["local variables referenced from a lambda expression must be final or effectively final"]
---

## Issue

When a `count` variable is incremented inside a loop and then referenced inside
a `sendSuccess(() -> ...)` or `sendFeedback(() -> ...)` supplier lambda, the
compiler rejects it because the variable is not effectively final.

## Error

```
error: local variables referenced from a lambda expression must be final or effectively final
    src.sendSuccess(() -> Component.literal("Accepted " + count + " request(s)."), false);
                                                          ^
```

## Root Cause

In Minecraft 1.20+, `CommandSourceStack.sendSuccess()` takes a `Supplier<Component>`
(a lambda) instead of a `Component` directly. Any local variable captured inside
that lambda must be effectively final — meaning it cannot be modified after its
declaration. A `count` variable that is incremented in a loop (`count++`) is not
effectively final.

This only affects versions where `sendSuccess` takes a supplier (1.20+). In
1.17–1.19.4, `sendSuccess(Component, boolean)` takes the component directly, so
there is no lambda and no capture issue.

## Fix

Capture the count into a final variable immediately before the lambda:

```java
// Wrong — count is modified in the loop above
int count = 0;
for (...) { count++; }
src.sendSuccess(() -> Component.literal("Accepted " + count + " request(s)."), false);

// Correct — capture into final before the lambda
int count = 0;
for (...) { count++; }
final int finalCount = count;
src.sendSuccess(() -> Component.literal("Accepted " + finalCount + " request(s)."), false);
```

The same fix applies to `sendFeedback(() -> Text.literal(...))` in Fabric 1.20+.

## When This Appears

This error appears in any command handler method that:
1. Counts items in a loop (`tpacceptall`, `tpadenyall`, `tpacancel all`)
2. Uses `sendSuccess(() -> ...)` or `sendFeedback(() -> ...)` after the loop

## Derivation Chain Warning

When building source strings via `.replace()` chains in a generator script, the
lambda is introduced by replacing `src.sendSuccess(Component.literal(` with
`src.sendSuccess(() -> Component.literal(`. After this replacement, any `count`
variable in the string becomes a lambda capture issue. The `finalCount` fix must
be applied AFTER the lambda replacement, not before.

```python
# Apply lambda replacement first
src = src.replace("src.sendSuccess(Component.literal(", "src.sendSuccess(() -> Component.literal(")
# Then fix the count capture
src = src.replace(
    'src.sendSuccess(() -> Component.literal("Accepted "+count+',
    'final int finalCount=count; src.sendSuccess(() -> Component.literal("Accepted "+finalCount+'
)
```

## Verified

Confirmed in TPA Teleport all-versions port (runs 4–6, April 2026).
