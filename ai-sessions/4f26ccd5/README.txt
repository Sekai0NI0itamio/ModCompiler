Session ID: 4f26ccd5
Started: 2026-05-07T21:16:25.345532

Contents of this session directory:
  project_info/          - Modrinth project metadata + first version source
  target_list.json       - List of version/loader combinations to build
  metadata.json          - Mod metadata for build_adapter.py (pre-generated)
  bundle/                - Pre-scaffolded bundle dirs (one per target)
    <mc>-<loader>/       - e.g. bundle/1.8.9-forge/
      src/main/java/...  - REPLACE the ExampleMod.java here with your mod source
      src/main/resources/- Resource files (mcmod.info, mods.toml, etc.)
  build/<mc>-<loader>/   - Build workspace per target (created by 'compile')
  artifacts/             - Successfully compiled jars

WORKFLOW:
  1. For each target in bundle/, replace ExampleMod.java with your mod source
  2. Call build_bundle() to create the zip
  3. Call trigger_build() to compile via GitHub Actions

To browse repo templates (read-only):
  ls('repo:1.21.2-1.21.8/fabric/template')
