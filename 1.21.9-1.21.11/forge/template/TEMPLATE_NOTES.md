# Template Notes

These templates are verified by the template-verify workflow. Keep these rules:
- Build with ./modcompiler-build.sh (fast build first, then full build if needed).
- Template verify runs set MODCOMPILER_GRADLE_TASKS=jar for speed; Fabric uses dev jars in this mode.
- Re-run template-verify after any template or dependency changes.
- Keep ForgeGradle plugin versions pinned in 1.21.x (no version ranges).
- Fabric Loom versions are pinned to release numbers (no -SNAPSHOT) to avoid missing artifacts.
- Keep version-specific API notes in the Java sources so future updates do not regress.

Range-specific notes:
- Forge 1.21.9-1.21.11: SubscribeEvent lives in net.minecraftforge.eventbus.api.
- Forge 1.21.9-1.21.11: Use FMLJavaModLoadingContext.getModEventBus(); do not use getModBusGroup.
