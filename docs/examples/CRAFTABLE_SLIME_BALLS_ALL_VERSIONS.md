# Craftable Slime Balls — Multi-Version Build Documentation

## Overview

**Mod:** Craftable Slime Balls
**Modrinth URL:** https://modrinth.com/mod/craftable-slime-balls

Craftable Slime Balls adds a single crafting recipe: combine 8 sugar cane and 1 water bucket in a crafting table to produce 9 slime balls. It's a pure recipe mod with no gameplay logic beyond that one recipe.

**Final result: 61 versions built and published across Forge, Fabric, and NeoForge — zero failures.**

---

## Step-by-Step Commands

Commands were run in the following exact order.

### 1. Trigger the Fetch Modrinth Project workflow

```bash
gh workflow run fetch-modrinth-project.yml \
  --repo Sekai0NI0itamio/ModCompiler \
  --field modrinth_project_url=https://modrinth.com/mod/craftable-slime-balls
```

### 2. Wait for the workflow to complete

```bash
gh run watch <run_id> --repo Sekai0NI0itamio/ModCompiler
```

### 3. Download the bundle artifact

```bash
gh run download <run_id> \
  --repo Sekai0NI0itamio/ModCompiler \
  --name modrinth-project-bundle \
  --dir modrinth-downloads/craftable-slime-balls
```

### 4. Read the bundle contents

Inspect the downloaded files to understand the mod's structure and source:

- `modrinth-downloads/craftable-slime-balls/summary.txt`
- `modrinth-downloads/craftable-slime-balls/mod_info.txt`
- Decompiled Java source files

### 5. Run the generator script

```bash
python3 scripts/_generate_craftable_slime_balls_bundle.py
```

This script generates the full multi-version bundle under `incoming/craftable-slime-balls-all-versions/`.

### 6. Create the zip with correct structure

Paths inside the zip must be relative to the bundle folder (mod folders at the top level, not nested under a parent path):

```bash
python3 -c "
import zipfile
from pathlib import Path

bundle_dir = Path('incoming/craftable-slime-balls-all-versions')
zip_path = Path('incoming/craftable-slime-balls-all-versions.zip')

with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
    for file in bundle_dir.rglob('*'):
        if file.is_file():
            zf.write(file, file.relative_to(bundle_dir))
"
```

### 7. Stage and commit

```bash
git add incoming/craftable-slime-balls-all-versions/ \
        incoming/craftable-slime-balls-all-versions.zip \
        scripts/_generate_craftable_slime_balls_bundle.py \
        modrinth-downloads/craftable-slime-balls/
```

```bash
git commit -m "Add craftable-slime-balls all-versions bundle (1.8.9 through 1.21.11)"
```

```bash
git push
```

### 8. Trigger build and publish

```bash
python3 scripts/run_build.py \
  incoming/craftable-slime-balls-all-versions.zip \
  --modrinth https://modrinth.com/mod/craftable-slime-balls
```

> **Note:** `run_build.py` is blocking — always run it with `executeBash`, never as a background process.

---

## Challenges and Solutions

### Challenge 1: Wrong zip structure

**Problem:** The first zip was created by zipping the `incoming/` folder directly, which produced paths like `incoming/craftable-slime-balls-all-versions/CSB.../`. The build system expects mod folders at the top level of the zip.

**Error:**
```
incoming: missing required entries: src, mod.txt, version.txt
```

**Fix:** Recreate the zip using Python's `zipfile` module with paths relative to the bundle folder (`file.relative_to(bundle_dir)`), so each mod folder sits at the root of the archive.

---

### Challenge 2: Original mod was a shell for all versions except 1.12.2

**Problem:** Decompiling the original mod revealed that all Java `init()` methods were empty. The recipe only worked in 1.12.2 because Forge auto-discovers JSON recipes from `assets/<modid>/recipes/`. For every other version, the mod was effectively a no-op — those versions didn't exist at all in the original release.

**Solution:** Treat all non-1.12.2 versions as new ports. Generate proper recipe JSON files and, for 1.8.9, Java-based recipe registration.

---

### Challenge 3: API and path differences across versions

| Version range | Recipe system | Recipe path | Sugar cane item ID |
|---|---|---|---|
| 1.8.9 | Java only (`GameRegistry.addRecipe`) | N/A | `Items.reeds` |
| 1.12.2 | JSON (Forge auto-discovery) | `assets/<modid>/recipes/` | `minecraft:reeds` |
| 1.13+ | JSON (data pack) | `data/<modid>/recipes/` | `minecraft:sugar_cane` |

- **1.8.9** has no JSON recipe system at all — recipes must be registered in Java using `GameRegistry.addRecipe()` with `Items.reeds`.
- **1.12.2** uses the `assets/` path and the old `minecraft:reeds` item ID.
- **1.13+** moved to the data pack format under `data/` and renamed the item to `minecraft:sugar_cane`.

---

### Challenge 4: Forge import style changes in newer versions

- **1.16.5–1.20.6:** Uses `FMLJavaModLoadingContext` for mod initialization hooks.
- **1.21+:** The `@Mod` annotation alone is sufficient for pure recipe mods — no context or event bus registration needed.

---

## Version Coverage

**61 versions total across 3 loaders.**

### Forge (21 versions)

| Version |
|---|
| 1.8.9 |
| 1.12.2 |
| 1.16.5 |
| 1.17.1 |
| 1.18 |
| 1.18.1 |
| 1.18.2 |
| 1.19 |
| 1.19.1 |
| 1.19.2 |
| 1.19.3 |
| 1.19.4 |
| 1.20.1 |
| 1.20.4 |
| 1.20.6 |
| 1.21 |
| 1.21.1 |
| 1.21.4 |
| 1.21.9 |
| 1.21.10 |
| 1.21.11 |

### Fabric (25 versions)

| Version |
|---|
| 1.16.5 |
| 1.17.1 |
| 1.18 |
| 1.18.1 |
| 1.18.2 |
| 1.19 |
| 1.19.1 |
| 1.19.2 |
| 1.19.3 |
| 1.19.4 |
| 1.20.1 |
| 1.20.4 |
| 1.20.6 |
| 1.21 |
| 1.21.1 |
| 1.21.2 |
| 1.21.3 |
| 1.21.4 |
| 1.21.5 |
| 1.21.6 |
| 1.21.7 |
| 1.21.8 |
| 1.21.9 |
| 1.21.10 |
| 1.21.11 |

### NeoForge (15 versions)

| Version |
|---|
| 1.20.4 |
| 1.20.6 |
| 1.21 |
| 1.21.1 |
| 1.21.2 |
| 1.21.3 |
| 1.21.4 |
| 1.21.5 |
| 1.21.6 |
| 1.21.7 |
| 1.21.8 |
| 1.21.9 |
| 1.21.10 |
| 1.21.11 |

---

## Key Lessons Learned

- **Always use the Fetch Modrinth Project workflow first** — never guess the source structure. The decompiled source revealed critical details (empty `init()` methods, JSON recipe locations) that would have been impossible to infer otherwise.

- **Zip must have mod folders at the top level** — not nested under any parent path. The build system looks for `src/`, `mod.txt`, and `version.txt` directly inside the zip root.

- **Use Python's `zipfile` module with `rglob` and `relative_to(bundle_dir)`** for correct zip structure. Avoid zipping a parent directory directly.

- **Recipe-only mods are the simplest to port** — there are no Java API differences to worry about across most versions. The only variables are the recipe file path and item IDs.

- **1.8.9 is the only version that requires Java recipe registration** — it predates the JSON recipe system entirely. Use `GameRegistry.addRecipe()` with `Items.reeds`.

- **1.12.2 uses `assets/<modid>/recipes/`** and the item ID `minecraft:reeds`.

- **1.13+ uses `data/<modid>/recipes/`** (data pack format) and the item ID `minecraft:sugar_cane`.

- **`run_build.py` is blocking** — always run it with `executeBash`, never as a background process. It needs to stream output and return an exit code.
