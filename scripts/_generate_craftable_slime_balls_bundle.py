#!/usr/bin/env python3
"""
Generate the craftable-slime-balls-all-versions bundle for all supported
Minecraft versions and loaders.

The 1.8.9 and 1.12.2 Forge folders are already created manually.
This script creates all remaining targets (1.16.5 through 1.21.11,
Forge + Fabric + NeoForge where applicable).

Recipe:
  8 sugar cane (surrounding) + 1 water bucket (center) -> 9 slime balls
  Pattern:
    AAA
    ABA
    AAA
  A = minecraft:sugar_cane  (minecraft:reeds in 1.12.2, handled separately)
  B = minecraft:water_bucket

In 1.16.5+ the recipe JSON lives in:
  src/main/resources/data/craftableslimeballs/recipes/slimeballs.json

Forge 1.16.5 - 1.20.6: uses @Mod + FMLCommonSetupEvent (mods.toml adapter handles metadata)
Forge 1.21+:            uses @Mod + IEventBus (new event bus registration style)
Fabric all versions:    uses ModInitializer interface
NeoForge all versions:  same as modern Forge (IEventBus)
"""

import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
BUNDLE_DIR = ROOT / "incoming" / "craftable-slime-balls-all-versions"

MOD_ID = "craftableslimeballs"
MOD_NAME = "Craftable Slime Balls"
MOD_VERSION = "1.0.0"
GROUP = "asd.itamio.craftableslimeballs"
ENTRYPOINT = "asd.itamio.craftableslimeballs.CraftableSlimeBalls"
DESCRIPTION = "Craft 9 slime balls by arranging 8 sugar cane around a water bucket in a 3x3 pattern."
AUTHORS = "Itamio"
LICENSE = "MIT"
HOMEPAGE = "https://modrinth.com/mod/craftable-slime-balls"

RECIPE_JSON = """{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "AAA",
    "ABA",
    "AAA"
  ],
  "key": {
    "A": { "item": "minecraft:sugar_cane" },
    "B": { "item": "minecraft:water_bucket" }
  },
  "result": {
    "item": "minecraft:slime_ball",
    "count": 9
  }
}
"""

MOD_TXT_TEMPLATE = """\
mod_id={mod_id}
name={name}
mod_version={mod_version}
group={group}
entrypoint_class={entrypoint}
description={description}
authors={authors}
license={license}
homepage={homepage}
"""

# ---------------------------------------------------------------------------
# Forge source templates
# ---------------------------------------------------------------------------

# Forge 1.16.5 - 1.20.6: FMLCommonSetupEvent style
FORGE_LEGACY_JAVA = """\
package asd.itamio.craftableslimeballs;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Recipe is registered via JSON data pack:
// data/craftableslimeballs/recipes/slimeballs.json
// No Java registration needed for shaped crafting recipes in this era.
@Mod(CraftableSlimeBalls.MODID)
public class CraftableSlimeBalls {
    public static final String MODID = "craftableslimeballs";

    public CraftableSlimeBalls() {
        // No event bus listeners needed for a pure recipe mod.
    }
}
"""

# Forge 1.21+ (1.21-1.21.1, 1.21.2-1.21.8, 1.21.9-1.21.11): IEventBus style
FORGE_MODERN_JAVA = """\
package asd.itamio.craftableslimeballs;

import net.minecraftforge.fml.common.Mod;

// Recipe is registered via JSON data pack:
// data/craftableslimeballs/recipes/slimeballs.json
// No Java registration needed for shaped crafting recipes.
@Mod(CraftableSlimeBalls.MODID)
public class CraftableSlimeBalls {
    public static final String MODID = "craftableslimeballs";

    public CraftableSlimeBalls() {
        // No event bus listeners needed for a pure recipe mod.
    }
}
"""

# ---------------------------------------------------------------------------
# Fabric source template (all versions 1.16.5+)
# ---------------------------------------------------------------------------
FABRIC_JAVA = """\
package asd.itamio.craftableslimeballs;

import net.fabricmc.api.ModInitializer;

// Recipe is registered via JSON data pack:
// data/craftableslimeballs/recipes/slimeballs.json
// Fabric loads data pack recipes automatically — no Java registration needed.
public class CraftableSlimeBalls implements ModInitializer {
    public static final String MODID = "craftableslimeballs";

    @Override
    public void onInitialize() {
        // Recipe loaded from data/craftableslimeballs/recipes/slimeballs.json
    }
}
"""

# ---------------------------------------------------------------------------
# NeoForge source template (all versions)
# ---------------------------------------------------------------------------
NEOFORGE_JAVA = """\
package asd.itamio.craftableslimeballs;

import net.neoforged.fml.common.Mod;

// Recipe is registered via JSON data pack:
// data/craftableslimeballs/recipes/slimeballs.json
// No Java registration needed for shaped crafting recipes.
@Mod(CraftableSlimeBalls.MODID)
public class CraftableSlimeBalls {
    public static final String MODID = "craftableslimeballs";

    public CraftableSlimeBalls() {
        // No event bus listeners needed for a pure recipe mod.
    }
}
"""

# ---------------------------------------------------------------------------
# Target matrix
# ---------------------------------------------------------------------------
# Each entry: (folder_name, minecraft_version, loader, java_source)
TARGETS = [
    # 1.16.5
    ("CSB1165Forge",   "1.16.5",       "forge",    FORGE_LEGACY_JAVA),
    ("CSB1165Fabric",  "1.16.5",       "fabric",   FABRIC_JAVA),
    # 1.17-1.17.1
    ("CSB1171Forge",   "1.17.1",       "forge",    FORGE_LEGACY_JAVA),
    ("CSB1171Fabric",  "1.17.1",       "fabric",   FABRIC_JAVA),
    # 1.18-1.18.2
    ("CSB1182Forge",   "1.18-1.18.2",  "forge",    FORGE_LEGACY_JAVA),
    ("CSB1182Fabric",  "1.18-1.18.2",  "fabric",   FABRIC_JAVA),
    # 1.19-1.19.4
    ("CSB1194Forge",   "1.19-1.19.4",  "forge",    FORGE_LEGACY_JAVA),
    ("CSB1194Fabric",  "1.19-1.19.4",  "fabric",   FABRIC_JAVA),
    # 1.20-1.20.6
    ("CSB1201Forge",   "1.20.1",       "forge",    FORGE_LEGACY_JAVA),
    ("CSB1201Fabric",  "1.20.1",       "fabric",   FABRIC_JAVA),
    ("CSB1204Forge",   "1.20.4",       "forge",    FORGE_LEGACY_JAVA),
    ("CSB1204Fabric",  "1.20.4",       "fabric",   FABRIC_JAVA),
    ("CSB1204NeoForge","1.20.4",       "neoforge", NEOFORGE_JAVA),
    ("CSB1206Forge",   "1.20.6",       "forge",    FORGE_LEGACY_JAVA),
    ("CSB1206Fabric",  "1.20.6",       "fabric",   FABRIC_JAVA),
    ("CSB1206NeoForge","1.20.6",       "neoforge", NEOFORGE_JAVA),
    # 1.21-1.21.1
    ("CSB121Forge",    "1.21-1.21.1",  "forge",    FORGE_MODERN_JAVA),
    ("CSB121Fabric",   "1.21-1.21.1",  "fabric",   FABRIC_JAVA),
    ("CSB121NeoForge", "1.21-1.21.1",  "neoforge", NEOFORGE_JAVA),
    # 1.21.2-1.21.8
    ("CSB1214Forge",   "1.21.4",       "forge",    FORGE_MODERN_JAVA),
    ("CSB1214Fabric",  "1.21.2-1.21.8","fabric",   FABRIC_JAVA),
    ("CSB1214NeoForge","1.21.2-1.21.8","neoforge", NEOFORGE_JAVA),
    # 1.21.9-1.21.11
    ("CSB12111Forge",  "1.21.9-1.21.11","forge",   FORGE_MODERN_JAVA),
    ("CSB12111Fabric", "1.21.9-1.21.11","fabric",  FABRIC_JAVA),
    ("CSB12111NeoForge","1.21.9-1.21.11","neoforge",NEOFORGE_JAVA),
]


def write_file(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print(f"  wrote {path.relative_to(ROOT)}")


def create_target(folder_name: str, mc_version: str, loader: str, java_src: str):
    target_dir = BUNDLE_DIR / folder_name
    print(f"\n[{folder_name}] mc={mc_version} loader={loader}")

    # mod.txt
    write_file(target_dir / "mod.txt", MOD_TXT_TEMPLATE.format(
        mod_id=MOD_ID,
        name=MOD_NAME,
        mod_version=MOD_VERSION,
        group=GROUP,
        entrypoint=ENTRYPOINT,
        description=DESCRIPTION,
        authors=AUTHORS,
        license=LICENSE,
        homepage=HOMEPAGE,
    ))

    # version.txt
    write_file(target_dir / "version.txt", f"minecraft_version={mc_version}\nloader={loader}\n")

    # Java source
    java_path = target_dir / "src/main/java/asd/itamio/craftableslimeballs/CraftableSlimeBalls.java"
    write_file(java_path, java_src)

    # Recipe JSON — goes in data/ for 1.16.5+
    recipe_path = target_dir / "src/main/resources/data/craftableslimeballs/recipes/slimeballs.json"
    write_file(recipe_path, RECIPE_JSON)


def main():
    print(f"Generating craftable-slime-balls bundle in {BUNDLE_DIR}")
    print(f"Targets: {len(TARGETS)} (plus 1.8.9 and 1.12.2 already created)")

    for folder_name, mc_version, loader, java_src in TARGETS:
        create_target(folder_name, mc_version, loader, java_src)

    print(f"\nDone. Bundle directory: {BUNDLE_DIR}")
    print("Next steps:")
    print("  git add incoming/craftable-slime-balls-all-versions/")
    print("  git commit -m 'Add craftable-slime-balls all-versions bundle'")
    print("  git push")
    print("  python3 scripts/run_build.py incoming/craftable-slime-balls-all-versions.zip --modrinth https://modrinth.com/mod/craftable-slime-balls")


if __name__ == "__main__":
    main()
