# Template Notes

These templates are verified by the template-verify workflow. Keep these rules:
- Build with ./modcompiler-build.sh (fast build first, then full build if needed).
- Workflow runs set MODCOMPILER_GRADLE_TASKS=jar for speed; use build if you need reobf or remap outputs.
- Re-run template-verify after any template or dependency changes.
- Keep ForgeGradle plugin versions pinned in 1.21.x (no version ranges).
- Keep version-specific API notes in the Java sources so future updates do not regress.
