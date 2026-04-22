#!/usr/bin/env python3
"""Generate Seed Protect mod bundle for missing versions."""
import json
import shutil
from pathlib import Path

# Determine mode
import sys
failed_only = "--failed-only" in sys.argv

# Base paths
repo_root = Path(__file__).parent.parent
incoming_dir = repo_root / "incoming"
bundle_name = "seed-protect-missing-versions"
bundle_dir = incoming_dir / bundle_name
zip_path = incoming_dir / f"{bundle_name}.zip"

# If failed-only mode, read the most recent run to find failures
if failed_only:
    runs_dir = repo_root / "ModCompileRuns"
    if not runs_dir.exists():
        print("ERROR: No ModCompileRuns directory found. Cannot use --failed-only mode.")
        sys.exit(1)
    
    # Find most recent run
    run_dirs = sorted([d for d in runs_dir.iterdir() if d.is_dir()], reverse=True)
    if not run_dirs:
        print("ERROR: No run directories found in ModCompileRuns/")
        sys.exit(1)
    
    latest_run = run_dirs[0]
    result_json = latest_run / "result.json"
    
    if not result_json.exists():
        print(f"ERROR: No result.json found in {latest_run}")
        sys.exit(1)
    
    with open(result_json) as f:
        result = json.load(f)
    
    # Extract failed targets
    failed_targets = set()
    for job in result.get("jobs", []):
        if job.get("status") != "success":
            # Parse job name like "build-seed-protect-1-18-fabric"
            name = job.get("name", "")
            if name.startswith("build-"):
                target = name.replace("build-", "").replace("-", ".")
                # Convert back: seed.protect.1.18.fabric -> 1.18 fabric
                parts = target.split(".")
                # Find where version starts (first digit)
                for i, p in enumerate(parts):
                    if p.isdigit():
                        version_parts = parts[i:]
                        loader = version_parts[-1]
                        mc_version = ".".join(version_parts[:-1])
                        failed_targets.add((mc_version, loader))
                        break
    
    print(f"Found {len(failed_targets)} failed targets from {latest_run.name}")
    for mc, loader in sorted(failed_targets):
        print(f"  - {mc} {loader}")
    
    if not failed_targets:
        print("No failures found. Nothing to rebuild.")
        sys.exit(0)
    
    # Filter targets to only failed ones
    targets_to_build = failed_targets
else:
    # Build all missing targets - ONLY FABRIC for now
    # Forge 1.21.6+ has broken eventbus API, skip those
    targets_to_build = {
        ("1.18", "fabric"),
        ("1.18.1", "fabric"),
        ("1.19", "fabric"),
        ("1.19.1", "fabric"),
        ("1.19.2", "fabric"),
        ("1.19.3", "fabric"),
    }
    print(f"Building {len(targets_to_build)} missing Fabric targets")
    print("NOTE: Skipping Forge 1.21.6-1.21.11 due to broken eventbus API in those versions")

# Clean and recreate bundle directory
if bundle_dir.exists():
    shutil.rmtree(bundle_dir)
bundle_dir.mkdir(parents=True, exist_ok=True)

# Common metadata
mod_id = "seedprotect"
mod_name = "Seed Protect"
mod_version = "1.0.0"
group = "com.seedprotect"
description = "Prevents farmland and planted crops from being trampled by players and mobs."
authors = "Itamio"
license_id = "LicenseRef-All-Rights-Reserved"
homepage = "https://modrinth.com/mod/seed-protect"

def create_fabric_version(mc_version: str):
    """Create Fabric version for given MC version."""
    folder_name = f"seed-protect-{mc_version}-fabric"
    mod_folder = bundle_dir / folder_name
    src_dir = mod_folder / "src" / "main"
    java_dir = src_dir / "java" / "com" / "seedprotect"
    resources_dir = src_dir / "resources"
    
    # Create directories
    java_dir.mkdir(parents=True, exist_ok=True)
    (java_dir / "mixin").mkdir(exist_ok=True)
    resources_dir.mkdir(parents=True, exist_ok=True)
    
    # Main mod class
    main_class = f"""package com.seedprotect;

import net.fabricmc.api.ModInitializer;

public final class SeedProtectMod implements ModInitializer {{
    public static final String MOD_ID = "{mod_id}";

    @Override
    public void onInitialize() {{
    }}
}}
"""
    (java_dir / "SeedProtectMod.java").write_text(main_class)
    
    # Mixin class - use the actual method signature from decompiled code
    # The method name in Fabric is "fallOn" for 1.17+ (changed from "onLandedUpon")
    mixin_class = """package com.seedprotect.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.block.FarmBlock")
public abstract class FarmlandBlockMixin {
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void seedprotect_cancelTrample(net.minecraft.world.level.Level world, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity entity, float fallDistance, CallbackInfo ci) {
        ci.cancel();
    }
}
"""
    (java_dir / "mixin" / "FarmlandBlockMixin.java").write_text(mixin_class)
    
    # fabric.mod.json
    fabric_mod_json = {
        "schemaVersion": 1,
        "id": mod_id,
        "version": mod_version,
        "name": mod_name,
        "description": description,
        "authors": [authors],
        "license": license_id,
        "contact": {
            "homepage": homepage
        },
        "environment": "server",
        "entrypoints": {
            "main": ["com.seedprotect.SeedProtectMod"]
        },
        "mixins": [
            "seedprotect.mixins.json"
        ],
        "depends": {
            "fabricloader": ">=0.7.0",
            "minecraft": mc_version
        }
    }
    (resources_dir / "fabric.mod.json").write_text(json.dumps(fabric_mod_json, indent=2))
    
    # Mixin config
    mixin_config = {
        "required": True,
        "package": "com.seedprotect.mixin",
        "compatibilityLevel": "JAVA_8",
        "mixins": [],
        "client": [],
        "server": [
            "FarmlandBlockMixin"
        ],
        "injectors": {
            "defaultRequire": 1
        }
    }
    (resources_dir / "seedprotect.mixins.json").write_text(json.dumps(mixin_config, indent=2))
    
    # mod.txt
    mod_txt = f"""mod_id={mod_id}
name={mod_name}
mod_version={mod_version}
group={group}
entrypoint_class=com.seedprotect.SeedProtectMod
description={description}
authors={authors}
license={license_id}
homepage={homepage}
runtime_side=server
"""
    (mod_folder / "mod.txt").write_text(mod_txt)
    
    # version.txt
    version_txt = f"""minecraft_version={mc_version}
loader=fabric
"""
    (mod_folder / "version.txt").write_text(version_txt)
    
    print(f"  Created {folder_name}")

def create_forge_version(mc_version: str):
    """Create Forge version for given MC version."""
    folder_name = f"seed-protect-{mc_version}-forge"
    mod_folder = bundle_dir / folder_name
    src_dir = mod_folder / "src" / "main"
    java_dir = src_dir / "java" / "com" / "seedprotect"
    resources_dir = src_dir / "resources"
    
    # Create directories
    java_dir.mkdir(parents=True, exist_ok=True)
    resources_dir.mkdir(parents=True, exist_ok=True)
    (resources_dir / "META-INF").mkdir(exist_ok=True)
    
    # Main mod class
    main_class = f"""package com.seedprotect;

import net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("{mod_id}")
@Mod.EventBusSubscriber(modid = "{mod_id}", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {{
    public static final String MOD_ID = "{mod_id}";
    public static final String NAME = "{mod_name}";
    public static final String VERSION = "{mod_version}";

    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {{
        event.setCanceled(true);
    }}
}}
"""
    (java_dir / "SeedProtectMod.java").write_text(main_class)
    
    # mods.toml
    mods_toml = f"""modLoader="javafml"
loaderVersion="[36,)"
license="{license_id}"

[[mods]]
modId="{mod_id}"
version="{mod_version}"
displayName="{mod_name}"
description="{description}"
authors="{authors}"
displayURL="{homepage}"

[[dependencies.{mod_id}]]
modId="forge"
mandatory=true
versionRange="[36,)"
ordering="NONE"
side="SERVER"

[[dependencies.{mod_id}]]
modId="minecraft"
mandatory=true
versionRange="[{mc_version}]"
ordering="NONE"
side="SERVER"
"""
    (resources_dir / "META-INF" / "mods.toml").write_text(mods_toml)
    
    # mod.txt
    mod_txt = f"""mod_id={mod_id}
name={mod_name}
mod_version={mod_version}
group={group}
entrypoint_class=com.seedprotect.SeedProtectMod
description={description}
authors={authors}
license={license_id}
homepage={homepage}
runtime_side=server
"""
    (mod_folder / "mod.txt").write_text(mod_txt)
    
    # version.txt
    version_txt = f"""minecraft_version={mc_version}
loader=forge
"""
    (mod_folder / "version.txt").write_text(version_txt)
    
    print(f"  Created {folder_name}")

# Generate all target versions
for mc_version, loader in sorted(targets_to_build):
    if loader == "fabric":
        create_fabric_version(mc_version)
    elif loader == "forge":
        create_forge_version(mc_version)

# Create zip
print(f"\nCreating zip: {zip_path}")
if zip_path.exists():
    zip_path.unlink()
shutil.make_archive(str(zip_path.with_suffix("")), "zip", bundle_dir)

print(f"\n✓ Bundle created: {zip_path}")
print(f"✓ Contains {len(list(bundle_dir.iterdir()))} mod folders")
print("\nNext steps:")
print("  1. git add scripts/ incoming/")
print(f'  2. git commit -m "Add Seed Protect missing versions"')
print("  3. git push")
print(f'  4. python3 scripts/run_build.py incoming/{bundle_name}.zip --modrinth https://modrinth.com/mod/seed-protect')
