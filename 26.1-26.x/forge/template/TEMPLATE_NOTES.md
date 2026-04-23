# Template Notes

Source: Official Forge 26.1.2-64.0.4 MDK (maven.minecraftforge.net)

These templates are verified by the template-verify workflow. Keep these rules:
- Build with ./modcompiler-build.sh
- Re-run template-verify after any template or dependency changes.

Range-specific notes (Forge 26.1+):
- NO mappings block — obfuscation was removed in 26.1. Official Mojang names available directly.
- ForgeGradle version: [7.0.17,8) — range allowed (unlike 1.21.x which required pinned version).
- EventBus 7: @SubscribeEvent is now from net.minecraftforge.eventbus.api.listener (NOT eventbus.api).
- Constructor: ExampleMod(FMLJavaModLoadingContext context) — use context.getModBusGroup().
- Cancellable listeners return boolean (true = cancel) instead of event.setCanceled(true).
- Game bus events: use EventName.BUS.addListener(alwaysCancelling, handler) directly.
- annotationProcessor 'net.minecraftforge:eventbus-validator:7.0.1' validates listeners at compile time.
- Java 25 required. Gradle 9.3.1+ required.
- loaderVersion="[64,)" in mods.toml.
