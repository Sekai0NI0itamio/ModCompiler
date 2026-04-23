# Template Notes

Source: Official FabricMC/fabric-example-mod 26.1 branch

These templates are verified by the template-verify workflow. Keep these rules:
- Build with ./modcompiler-build.sh
- Re-run template-verify after any template or dependency changes.

Range-specific notes (Fabric 26.1+):
- loom_version=1.16-SNAPSHOT — resolves from https://maven.fabricmc.net/ (declared in settings.gradle).
- NO yarn mappings — Fabric 26.1 uses official Mojang names directly (no remapping).
- modImplementation -> implementation (no remapping needed).
- Property name: fabric_api_version (NOT fabric_version).
- Minecraft classes are on the classpath via the 'minecraft' dependency + loom.
- Java 25 required. Gradle 9.4+ required.
- Fabric Loader 0.19.2+, Loom 1.16-SNAPSHOT.
- Mixin class names use Mojang mappings: net.minecraft.world.level.block.FarmBlock / fallOn.
