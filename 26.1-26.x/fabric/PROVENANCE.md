# Template Provenance

- Source: `https://github.com/FabricMC/fabric-example-mod` (26.1 branch)
- Snapshot: Fabric Loader 0.18.4, Fabric API 0.146.1+26.1.2, Loom 1.15.3 (April 2026)
- Base guidance: Fabric getting started + Fabric Docs buildscript changes for 26.1.
- Java target: 25 (Minecraft 26.1+ requirement)
- Gradle: 9.4.0+ (required for Java 25)
- Notes: Minecraft 26.1 removed obfuscation. Yarn mappings are no longer officially supported by Fabric.
  The loom plugin changed from fabric-loom-remap to net.fabricmc.fabric-loom (no remapping).
  modImplementation -> implementation, remapJar -> jar.
  Fabric API names updated to match official Mojang names.

