# Template Provenance

- Source: `https://github.com/FabricMC/fabric-example-mod` (26.1 branch)
- Snapshot: Fabric Loader 0.19.2, Fabric API 0.146.1+26.1.2, Loom 1.16-SNAPSHOT (April 2026)
- Java target: 25 (Minecraft 26.1+ requirement)
- Gradle: 9.4.0+
- Notes:
  - Minecraft 26.1 removed obfuscation. Yarn mappings are no longer officially supported by Fabric.
  - loom_version=1.16-SNAPSHOT resolves from https://maven.fabricmc.net/ (declared in settings.gradle).
  - modImplementation -> implementation, no remapping.
  - Property name: fabric_api_version (not fabric_version).
  - Mixin class names use Mojang mappings (same as Forge/NeoForge).
