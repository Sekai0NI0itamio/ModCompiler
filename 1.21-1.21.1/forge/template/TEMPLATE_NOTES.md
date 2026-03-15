# Template Notes

These templates are verified by the template-verify workflow. Keep these rules:
- Build with ./modcompiler-build.sh (fast build first, then full build if needed).
- Workflow runs set MODCOMPILER_GRADLE_TASKS=jar for speed; use build if you need reobf or remap outputs.
- Re-run template-verify after any template or dependency changes.
- Keep ForgeGradle plugin versions pinned in 1.21.x (no version ranges).
- Fabric Loom versions are pinned to release numbers (no -SNAPSHOT) to avoid missing artifacts.
- Keep version-specific API notes in the Java sources so future updates do not regress.

Range-specific notes:
- Forge 1.21-1.21.1: use FMLJavaModLoadingContext.getModEventBus(); getModBusGroup() is not available.
- Forge 1.21-1.21.1: register creative tab contents via modEventBus.addListener; BuildCreativeModeTabContentsEvent.BUS does not exist.
- Forge 1.21-1.21.1: ResourceLocation(String, String) is private; use ResourceLocation.fromNamespaceAndPath.
