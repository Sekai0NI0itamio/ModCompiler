# Template Notes

These templates are verified by the template-verify workflow. Keep these rules:
- Build with ./modcompiler-build.sh (fast build first, then full build if needed).
- Template verify runs set MODCOMPILER_GRADLE_TASKS=jar for speed; Fabric uses dev jars in this mode.
- Re-run template-verify after any template or dependency changes.
- Keep ForgeGradle plugin versions pinned in 1.21.x (no version ranges).
- Fabric Loom versions are pinned to release numbers (no -SNAPSHOT) to avoid missing artifacts.
- For these templates, keep the plugin id as `fabric-loom` (legacy); the newer `net.fabricmc.fabric-loom*` IDs may not have markers for the pinned Loom versions.
- Keep version-specific API notes in the Java sources so future updates do not regress.
