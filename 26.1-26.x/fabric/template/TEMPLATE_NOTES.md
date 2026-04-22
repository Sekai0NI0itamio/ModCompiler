# Template Notes

These templates are verified by the template-verify workflow. Keep these rules:
- Build with ./modcompiler-build.sh (fast build first, then full build if needed).
- Template verify runs set MODCOMPILER_GRADLE_TASKS=jar for speed; Fabric uses dev jars in this mode.
- Re-run template-verify after any template or dependency changes.
- Fabric Loom versions are pinned to release numbers (no -SNAPSHOT) to avoid missing artifacts.
- Keep version-specific API notes in the Java sources so future updates do not regress.

Range-specific notes:
- Minecraft 26.1+ requires Java 25 and Gradle 9.4+.
- Obfuscation was removed in 26.1 — Yarn mappings are NO LONGER USED by Fabric.
- Use the net.fabricmc.fabric-loom plugin (NOT fabric-loom-remap).
- modImplementation -> implementation (no remapping needed in 26.1+).
- remapJar -> jar.
- Fabric API names updated to match official Mojang names (e.g. ItemGroupEvents -> CreativeModeTabEvents).
- If your mod uses raw OpenGL calls, migrate to Blaze3D API (OpenGL planned for removal in 26.2+).
