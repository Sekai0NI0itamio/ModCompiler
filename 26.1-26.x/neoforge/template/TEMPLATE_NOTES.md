# Template Notes

Source: Official NeoForgeMDKs/MDK-26.1.2-ModDevGradle

These templates are verified by the template-verify workflow. Keep these rules:
- Build with ./modcompiler-build.sh
- Re-run template-verify after any template or dependency changes.

Range-specific notes (NeoForge 26.1+):
- Uses ModDevGradle (net.neoforged.moddev 2.0.141) — NOT NeoGradle.
- neo_version is still in beta format: 26.1.2.22-beta (no stable release yet as of April 2026).
- Constructor: ExampleMod(IEventBus modEventBus, ModContainer modContainer) — FMLJavaModLoadingContext REMOVED.
- neoforge.mods.toml is in src/main/templates/ and expanded via generateModMetadata task.
- Java 25 required. Gradle 9.2.1+ required.
- settings.gradle uses foojay-resolver-convention for Java toolchain resolution.
