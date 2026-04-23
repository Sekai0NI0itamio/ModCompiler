#!/usr/bin/env python3
"""
Test build for 26.1-26.x templates.
Creates a minimal mod bundle for each loader to verify the templates compile.
"""
import json
import shutil
from pathlib import Path

repo_root   = Path(__file__).parent.parent
incoming    = repo_root / "incoming"
bundle_name = "template-test-26x"
bundle_dir  = incoming / bundle_name
zip_path    = incoming / f"{bundle_name}.zip"

if bundle_dir.exists():
    shutil.rmtree(bundle_dir)
bundle_dir.mkdir(parents=True, exist_ok=True)

MOD_ID      = "templatetest"
MOD_NAME    = "Template Test 26.x"
MOD_VERSION = "1.0.0"
GROUP       = "com.templatetest"
DESCRIPTION = "Minimal template test for 26.1-26.x loaders."
AUTHORS     = "TestAuthor"
LICENSE     = "MIT"

def write_mod_txt(folder, entrypoint):
    (folder / "mod.txt").write_text(
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={GROUP}\nentrypoint_class={entrypoint}\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
    )

def write_version_txt(folder, mc_version, loader):
    (folder / "version.txt").write_text(
        f"minecraft_version={mc_version}\nloader={loader}\n"
    )

# ── Fabric 26.1.2 ────────────────────────────────────────────────────────────
def create_fabric():
    folder = bundle_dir / "templatetest-26.1.2-fabric"
    src    = folder / "src" / "main" / "java" / "com" / "templatetest"
    res    = folder / "src" / "main" / "resources"
    src.mkdir(parents=True, exist_ok=True)
    res.mkdir(parents=True, exist_ok=True)

    (src / "TemplateMod.java").write_text("""package com.templatetest;

import net.fabricmc.api.ModInitializer;

public final class TemplateMod implements ModInitializer {
    public static final String MOD_ID = "templatetest";

    @Override
    public void onInitialize() {
    }
}
""")

    (res / "fabric.mod.json").write_text(json.dumps({
        "schemaVersion": 1,
        "id": MOD_ID,
        "version": MOD_VERSION,
        "name": MOD_NAME,
        "description": DESCRIPTION,
        "authors": [AUTHORS],
        "license": LICENSE,
        "environment": "*",
        "entrypoints": {"main": ["com.templatetest.TemplateMod"]},
        "depends": {
            "fabricloader": ">=0.19.0",
            "minecraft": "~26.1",
            "java": ">=25",
            "fabric-api": "*",
        }
    }, indent=2))

    write_mod_txt(folder, "com.templatetest.TemplateMod")
    write_version_txt(folder, "26.1.2", "fabric")
    print("  Created templatetest-26.1.2-fabric")

# ── Forge 26.1.2 ─────────────────────────────────────────────────────────────
def create_forge():
    folder = bundle_dir / "templatetest-26.1.2-forge"
    src    = folder / "src" / "main" / "java" / "com" / "templatetest"
    res    = folder / "src" / "main" / "resources" / "META-INF"
    src.mkdir(parents=True, exist_ok=True)
    res.mkdir(parents=True, exist_ok=True)

    # EventBus 7: constructor takes FMLJavaModLoadingContext, use getModBusGroup()
    (src / "TemplateMod.java").write_text("""package com.templatetest;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Forge 26.1+ EventBus 7: use context.getModBusGroup() for mod bus events.
@Mod(TemplateMod.MODID)
public final class TemplateMod {
    public static final String MODID = "templatetest";

    public TemplateMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();
        // Register listeners here
    }
}
""")

    (res / "mods.toml").write_text(f"""modLoader="javafml"
loaderVersion="[64,)"
license="{LICENSE}"

[[mods]]
modId="{MOD_ID}"
version="{MOD_VERSION}"
displayName="{MOD_NAME}"
description="{DESCRIPTION}"
authors="{AUTHORS}"

[[dependencies.{MOD_ID}]]
modId="forge"
mandatory=true
versionRange="[64,)"
ordering="NONE"
side="BOTH"

[[dependencies.{MOD_ID}]]
modId="minecraft"
mandatory=true
versionRange="[26.1.2,26.2,)"
ordering="NONE"
side="BOTH"
""")

    (folder / "src" / "main" / "resources" / "pack.mcmeta").write_text(
        json.dumps({"pack": {"pack_format": 15, "description": MOD_NAME}}, indent=2)
    )

    write_mod_txt(folder, "com.templatetest.TemplateMod")
    write_version_txt(folder, "26.1.2", "forge")
    print("  Created templatetest-26.1.2-forge")

# ── NeoForge 26.1.2 ──────────────────────────────────────────────────────────
def create_neoforge():
    folder    = bundle_dir / "templatetest-26.1.2-neoforge"
    src       = folder / "src" / "main" / "java" / "com" / "templatetest"
    templates = folder / "src" / "main" / "templates" / "META-INF"
    src.mkdir(parents=True, exist_ok=True)
    templates.mkdir(parents=True, exist_ok=True)

    # NeoForge 26.1+: FMLJavaModLoadingContext removed, use IEventBus + ModContainer
    (src / "TemplateMod.java").write_text("""package com.templatetest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// NeoForge 26.1+: FMLJavaModLoadingContext removed.
// Constructor injection: IEventBus and ModContainer provided by FML.
@Mod(TemplateMod.MODID)
public final class TemplateMod {
    public static final String MODID = "templatetest";

    public TemplateMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register mod event listeners on modEventBus
        // Register game event listeners on NeoForge.EVENT_BUS
    }
}
""")

    # NeoForge 26.1 uses template expansion for mods.toml
    (templates / "neoforge.mods.toml").write_text(f"""license="{LICENSE}"

[[mods]]
modId="{MOD_ID}"
version="{MOD_VERSION}"
displayName="{MOD_NAME}"
description="{DESCRIPTION}"

[[dependencies.{MOD_ID}]]
modId="neoforge"
type="required"
versionRange="[26.1.2.22-beta,)"
ordering="NONE"
side="BOTH"

[[dependencies.{MOD_ID}]]
modId="minecraft"
type="required"
versionRange="[26.1.2]"
ordering="NONE"
side="BOTH"
""")

    write_mod_txt(folder, "com.templatetest.TemplateMod")
    write_version_txt(folder, "26.1.2", "neoforge")
    print("  Created templatetest-26.1.2-neoforge")

create_fabric()
create_forge()
create_neoforge()

print(f"\nCreating zip: {zip_path}")
if zip_path.exists():
    zip_path.unlink()
shutil.make_archive(str(zip_path.with_suffix("")), "zip", bundle_dir)

print(f"\n✓ Bundle created: {zip_path}")
print(f"✓ Contains {len(list(bundle_dir.iterdir()))} mod folders")
print("\nNext steps:")
print("  1. git add scripts/ incoming/")
print("  2. git commit -m 'Add 26.x template test bundle'")
print("  3. git push")
print("  4. python3 scripts/run_build.py incoming/template-test-26x.zip")
