# Template Notes

These templates are verified by the template-verify workflow. Keep these rules:
- Build with ./modcompiler-build.sh (fast build first, then full build if needed).
- Template verify runs set MODCOMPILER_GRADLE_TASKS=jar for speed; Fabric uses dev jars in this mode.
- Re-run template-verify after any template or dependency changes.
- Keep ForgeGradle plugin versions pinned (no version ranges).
- Fabric Loom versions are pinned to release numbers (no -SNAPSHOT) to avoid missing artifacts.
- Keep version-specific API notes in the Java sources so future updates do not regress.

Range-specific notes:
- Minecraft 26.1+ requires Java 25 and Gradle 9.4+.
- Obfuscation was removed in 26.1 — official Mojang parameter names are available directly.
- Forge 26.1+: loader version `[64,)`, Minecraft version range `[26.1,27)`.
- SubscribeEvent lives in net.minecraftforge.eventbus.api.
- Use FMLJavaModLoadingContext.getModEventBus(); do not use getModBusGroup.
