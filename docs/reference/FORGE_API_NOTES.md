# Forge API Notes (Quick Reference)

These notes are intended for the AI helper when updating mods. Always prefer the
version-specific `reference/` template in this repo for authoritative usage.

## Mod Event Bus (Forge 1.20+)
- Use `FMLJavaModLoadingContext.get().getModEventBus()` or the injected context's
  `getModEventBus()` method.
- Do **not** use `getModBusGroup()` (removed in modern Forge).
- Register listeners directly on the mod event bus:
  - `modEventBus.addListener(this::commonSetup);`
  - Avoid `FMLCommonSetupEvent.getBus(...)`.

## Brigadier Arguments (Forge 1.13+)
- `Commands.argument()` expects an **ArgumentType** instance.
  - Examples: `StringArgumentType.word()`, `StringArgumentType.string()`,
    `IntegerArgumentType.integer()`, `MessageArgumentType.message()`.
- Do **not** pass `String.class` or similar types.
- `StringArgumentType` lives in `net.minecraft.commands.arguments.StringArgumentType`.
- `EntityArgument` is for entity selectors, not plain strings.

## Teleport APIs (1.20+)
- `ServerPlayer` teleport signatures change over time.
- Always verify the exact method signature in the **reference template** or in
  the local Minecraft sources (`library_search`) before coding.

## Imports
- Missing imports are a common failure cause. Prefer copying import lists from
  the reference template for the target version and loader.

