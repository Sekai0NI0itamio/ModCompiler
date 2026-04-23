# Template Provenance

- Source: `https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle`
- Snapshot: NeoForge 26.1.2.22-beta, ModDevGradle 2.0.141 (April 2026)
- Java target: 25 (Minecraft 26.1+ requirement)
- Gradle: 9.2.1
- Notes:
  - NeoForge 26.1 is still in beta (no stable release as of April 2026).
  - Uses ModDevGradle (net.neoforged.moddev) not NeoGradle.
  - FMLJavaModLoadingContext removed — use IEventBus + ModContainer constructor injection.
  - neoforge.mods.toml uses template expansion via generateModMetadata task.
  - settings.gradle uses foojay-resolver-convention for Java toolchain resolution.
