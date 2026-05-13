# Adding New Minecraft Version Templates to ModCompiler

This guide explains how to add new Minecraft version / loader templates to ModCompiler.

## Overview

ModCompiler uses standardized templates for each Minecraft version range and mod loader combination. These templates provide the build infrastructure, allowing the AI to only generate source and resource files.

## Step 1: Add Entry to `version-manifest.json`

First, add an entry to the `ranges` array in `version-manifest.json`:

```json
{
  "ranges": [
    {
      "folder": "X.X.X-Y.Y.Y",
      "loaders": {
        "fabric": {
          "adapter_family": "fabric_split",
          "anchor_version": "X.Y.Z",
          "build_command": ["./modcompiler-build.sh"],
          "exact_dependency_mode": "anchor_only",
          "jar_glob": "build/libs/*.jar",
          "java_rules": [
            {"min": "X.X.X", "max": "Y.Y.Y", "version": 21}
          ],
          "supported_versions": ["X.Y.Z", ...],
          "template_dir": "X.X.X-Y.Y.Y/fabric/template"
        },
        "forge": { ... },
        "neoforge": { ... }
      },
      "min_version": "X.X.X",
      "max_version": "Y.Y.Y"
    }
  ]
}
```

### Fields Explained

- **`folder`**: Name of the directory where this range's templates and adapters are stored
- **`min_version` / `max_version`**: Range of Minecraft versions this entry covers
- **`loaders`**: Object with keys `fabric`, `forge`, `neoforge` (as applicable)
  - **`adapter_family`**: Build adapter family: `fabric_presplit`, `fabric_split`, `forge_legacy_mcmod`, `forge_mods_toml`, `neoforge_mods_toml`
  - **`anchor_version`**: Primary version for dependencies and mappings
  - **`build_command`**: Build script to execute (usually `./modcompiler-build.sh`)
  - **`exact_dependency_mode`**: `"exact"` or `"anchor_only"`
  - **`supported_versions`**: List of specific Minecraft versions this range supports
  - **`template_dir`**: Path to the template for this loader
  - **`java_rules`**: Java versions required for builds
  - **`dependency_overrides`**: (Optional) Specific overrides for certain versions

## Step 2: Create Directory Structure

Create a directory for the new range with subdirectories for each loader. For example:
```
1.XX-1.YY/
├── fabric/
│   └── template/
├── forge/
│   └── template/
└── neoforge/
    └── template/
```

## Step 3: Copy/Adapt a Template

Copy an existing template from a similar range and adapt it:

### Key Template Files

The templates should include **build files** that the AI will NOT modify:
- `build.gradle` or `build.gradle.kts` (Gradle build script)
- `settings.gradle` or `settings.gradle.kts` (Gradle settings)
- `gradle.properties` (Gradle properties)
- `gradlew` and `gradlew.bat` (Gradle wrapper scripts)
- `gradle/wrapper/gradle-wrapper.jar` and `gradle-wrapper.properties` (Gradle wrapper)
- `.gitignore`
- `TEMPLATE_NOTES.md` (optional, for maintainers)

The templates **should also include minimal source/resource files** that will be shown to the AI:
- `src/main/java/com/example/examplemod/ExampleMod.java` (minimal mod class)
- `src/main/resources/pack.mcmeta`
- `src/main/resources/fabric.mod.json` or `META-INF/mods.toml` or `META-INF/neoforge.mods.toml`

## Step 4: Create Build Adapter

Create or adapt `build_adapter.py` in the range directory. This script handles:
- Copying AI-generated source/resource files into the template
- Executing the build process
- Collecting build artifacts

## Step 5: Test the New Template

Run these verification steps:

### 1. Test Prompt Generation

```bash
python3 scripts/test_prompt_generation.py
```

This ensures:
- Template files are collected correctly
- Build files are excluded from the prompt
- The AI is instructed not to create build files

### 2. Test Specific or All Templates Locally

**To test all templates locally (takes a long time!):**
```bash
python3 scripts/test_all_templates.py
```
⚠️ **Note:** To test locally you need ALL required Java versions (8, 16, 17, 21, and 25) installed. GitHub Actions is recommended for full testing!

**To test a single range and loader:**
First, make sure you have the required Java version for that range installed, then run:
```bash
mkdir -p .workflow_artifacts/test-output/1.21.2-1.21.8-neoforge
python3 scripts/verify_template.py --range-folder 1.21.2-1.21.8 --loader neoforge --output-dir .workflow_artifacts/test-output/1.21.2-1.21.8-neoforge --timeout-minutes 10
```

### 3. Run Template Verify GitHub Workflow

Trigger the `Template Verify` GitHub workflow (`.github/workflows/template-verify.yml`) to test the new template. You can run it for just your new range.

## Important Rules

1. **AI Only Generates Source/Resources**: Never include build files in what the AI sees or creates
2. **Document Everything**: Add `TEMPLATE_NOTES.md` to document key details for maintainers
3. **Pin Dependency Versions**: Use specific versions for plugins and dependencies, avoid ranges
4. **Test Thoroughly**: Run all verification steps before committing
5. **Follow Naming Standards**: Keep `version-manifest.json` consistent with existing entries

## Example: Adding a New 27.x Range

```
27.0-27.x/
├── fabric/
│   └── template/
│       ├── build.gradle
│       ├── settings.gradle
│       ├── gradle.properties
│       ├── gradlew
│       ├── gradlew.bat
│       ├── gradle/wrapper/...
│       ├── TEMPLATE_NOTES.md
│       └── src/main/...
├── forge/
│   └── template/...
├── neoforge/
│   └── template/...
└── build_adapter.py
```

And add the corresponding entry in `version-manifest.json`.

## Troubleshooting

- **Build Failures**: Check `template-verify` workflow artifacts for logs
- **Wrong Dependencies**: Verify `dependency_overrides` in `version-manifest.json`
- **Incorrect Java Version**: Double-check `java_rules` in `version-manifest.json`
- **Build Files in Prompts**: Verify `_collect_template_files()` in `generate_prompts_in_bundle.py` correctly excludes build files
