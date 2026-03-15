# Template Notes

These templates are verified by the template-verify workflow. Keep these rules:
- Build with ./modcompiler-build.sh (fast build first, then full build if needed).
- Template verify runs set MODCOMPILER_GRADLE_TASKS=jar for speed; Fabric uses dev jars in this mode.
- Re-run template-verify after any template or dependency changes.
- Keep ForgeGradle plugin versions pinned in 1.21.x (no version ranges).
- Fabric Loom versions are pinned to release numbers (no -SNAPSHOT) to avoid missing artifacts.
- Keep version-specific API notes in the Java sources so future updates do not regress.

Range-specific notes:
- Forge 1.21.2-1.21.8: use FMLJavaModLoadingContext.getModBusGroup(); getModEventBus() is not available.
- Forge 1.21.2-1.21.8: SubscribeEvent lives in net.minecraftforge.eventbus.api.listener.
- Forge 1.21.2-1.21.8: BuildCreativeModeTabContentsEvent.BUS is not available.
- Forge 1.21.2-1.21.8: ResourceLocation(String, String) is private; use ResourceLocation.fromNamespaceAndPath.
