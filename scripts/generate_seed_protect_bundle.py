#!/usr/bin/env python3
"""
Generates the Seed Protect all-versions bundle.
Run: python3 scripts/generate_seed_protect_bundle.py

Seed Protect mod: Prevents farmland and planted crops from being trampled by players and mobs.
Original version: 1.12.2 Forge
"""
import argparse, json, shutil, subprocess, zipfile
from pathlib import Path

ROOT   = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "incoming" / "seed-protect-all-versions"

MOD_ID      = "seedprotect"
MOD_NAME    = "Seed Protect"
MOD_VERSION = "1.0.0"
GROUP       = "com.seedprotect"
DESCRIPTION = "Prevents farmland and planted crops from being trampled by players and mobs."
AUTHORS     = "Itamio"
LICENSE     = "LicenseRef-All-Rights-Reserved"
HOMEPAGE    = "https://modrinth.com/mod/seed-protect"
ENTRYPOINT  = f"{GROUP}.SeedProtectMod"
PKG         = GROUP.replace('.', '/')
JAVA_MAIN   = f"src/main/java/{PKG}/SeedProtectMod.java"

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def mod_txt() -> str:
    return (f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
            f"group={GROUP}\nentrypoint_class={ENTRYPOINT}\n"
            f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
            f"homepage={HOMEPAGE}\nruntime_side=server\n")

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"

# ============================================================
# SOURCE CODE FOR DIFFERENT VERSIONS
# ============================================================

# 1.8.9 Forge - FarmlandTrampleEvent exists in 1.8.9
SRC_189_FORGE = """\
package com.seedprotect;

import net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(
    modid = SeedProtectMod.MOD_ID,
    name = SeedProtectMod.NAME,
    version = SeedProtectMod.VERSION,
    acceptedMinecraftVersions = "[1.8.9]"
)
@EventBusSubscriber(modid = SeedProtectMod.MOD_ID)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";
    
    private SeedProtectMod() {
    }
    
    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
"""

# 1.12.2 Forge - Original version
SRC_1122_FORGE = """\
package com.seedprotect;

import net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(
    modid = SeedProtectMod.MOD_ID,
    name = SeedProtectMod.NAME,
    version = SeedProtectMod.VERSION,
    acceptedMinecraftVersions = "[1.12.2]"
)
@EventBusSubscriber(modid = SeedProtectMod.MOD_ID)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";
    
    private SeedProtectMod() {
    }
    
    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
"""

# 1.16.5 Forge - FarmlandTrampleEvent still exists
SRC_1165_FORGE = """\
package com.seedprotect;

import net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod(SeedProtectMod.MOD_ID)
@EventBusSubscriber(modid = SeedProtectMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";
    
    public SeedProtectMod() {
    }
    
    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
"""

# 1.16.5 Fabric - Need to use mixin for Fabric
SRC_1165_FABRIC = """\
package com.seedprotect;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SeedProtectMod implements ModInitializer {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";
    
    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient) {
                BlockPos pos = hitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);
                
                // Check if block is farmland
                if (state.getBlock() instanceof FarmlandBlock) {
                    // Check if entity is trying to trample
                    // In Fabric, we need to prevent the trample by returning FAIL
                    // This is a simplified approach - actual trample logic is more complex
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });
    }
}
"""

# 1.17+ Forge - FarmlandTrampleEvent still exists
SRC_117_FORGE = """\
package com.seedprotect;

import net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod(SeedProtectMod.MOD_ID)
@EventBusSubscriber(modid = SeedProtectMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";
    
    public SeedProtectMod() {
    }
    
    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
"""

# 1.19+ Forge - Package changed from world to level
SRC_119_FORGE = """\
package com.seedprotect;

import net.minecraftforge.event.level.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod(SeedProtectMod.MOD_ID)
@EventBusSubscriber(modid = SeedProtectMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";
    
    public SeedProtectMod() {
    }
    
    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
"""

# 1.17+ Fabric - Similar to 1.16.5 Fabric
SRC_117_FABRIC = SRC_1165_FABRIC

# 1.20+ Forge - FarmlandTrampleEvent still exists (uses level package)
SRC_120_FORGE = SRC_119_FORGE

# 1.20+ Fabric - Similar to 1.16.5 Fabric
SRC_120_FABRIC = SRC_1165_FABRIC

# 1.20+ NeoForge - Similar to Forge but with NeoForge event bus
SRC_120_NEOFORGE = """\
package com.seedprotect;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.level.BlockEvent.FarmlandTrampleEvent;

@Mod(SeedProtectMod.MOD_ID)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";
    
    public SeedProtectMod() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(this);
    }
    
    @SubscribeEvent
    public void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
"""

# 1.21+ Forge - FarmlandTrampleEvent still exists
SRC_121_FORGE = SRC_119_FORGE

# 1.21+ Fabric - Similar to 1.16.5 Fabric
SRC_121_FABRIC = SRC_1165_FABRIC

# 1.21+ NeoForge - Similar to 1.20+ NeoForge
SRC_121_NEOFORGE = SRC_120_NEOFORGE

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed-only", action="store_true", help="Only regenerate targets that failed in the last run")
    args = parser.parse_args()
    
    # Clean bundle directory
    if BUNDLE.exists():
        shutil.rmtree(BUNDLE)
    BUNDLE.mkdir(parents=True)
    
    # Get version manifest
    manifest_path = ROOT / "version-manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)
    
    # Track which targets to build
    targets = []
    
    # For now, let's build for all Forge and NeoForge versions
    # Fabric will need more work (mixins)
    for range_info in manifest["ranges"]:
        folder = range_info["folder"]
        min_version = range_info["min_version"]
        max_version = range_info["max_version"]
        
        # Determine which exact versions to build
        if "supported_versions" in range_info.get("loaders", {}).get("forge", {}):
            exact_versions = range_info["loaders"]["forge"]["supported_versions"]
        else:
            # Build for the anchor version
            anchor = range_info["loaders"]["forge"]["anchor_version"]
            exact_versions = [anchor]
        
        # Forge targets
        if "forge" in range_info["loaders"]:
            for mc_version in exact_versions:
                target_name = f"Seed-Protect-{mc_version}-forge"
                target_dir = BUNDLE / target_name
                targets.append((target_dir, mc_version, "forge", folder))
        
        # NeoForge targets (if available)
        if "neoforge" in range_info["loaders"]:
            if "supported_versions" in range_info["loaders"]["neoforge"]:
                neo_versions = range_info["loaders"]["neoforge"]["supported_versions"]
            else:
                neo_anchor = range_info["loaders"]["neoforge"]["anchor_version"]
                neo_versions = [neo_anchor]
            
            for mc_version in neo_versions:
                target_name = f"Seed-Protect-{mc_version}-neoforge"
                target_dir = BUNDLE / target_name
                targets.append((target_dir, mc_version, "neoforge", folder))
    
    print(f"Generating {len(targets)} targets...")
    
    for target_dir, mc_version, loader, folder in targets:
        print(f"  {mc_version} {loader}")
        
        # Create directory structure
        src_dir = target_dir / "src" / "main" / "java" / PKG
        src_dir.mkdir(parents=True, exist_ok=True)
        
        # Write mod.txt
        write(target_dir / "mod.txt", mod_txt())
        
        # Write version.txt
        write(target_dir / "version.txt", version_txt(mc_version, loader))
        
        # Write appropriate source code
        java_file = src_dir / "SeedProtectMod.java"
        
        # Select source based on version and loader
        if loader == "forge":
            if folder == "1.8.9":
                write(java_file, SRC_189_FORGE)
            elif folder == "1.12-1.12.2":
                write(java_file, SRC_1122_FORGE)
            elif folder == "1.16.5":
                write(java_file, SRC_1165_FORGE)
            elif folder in ["1.17-1.17.1", "1.18-1.18.2"]:
                write(java_file, SRC_117_FORGE)
            else:  # 1.19+ Forge
                write(java_file, SRC_119_FORGE)
        elif loader == "neoforge":
            write(java_file, SRC_120_NEOFORGE)
        elif loader == "fabric":
            if folder == "1.16.5":
                write(java_file, SRC_1165_FABRIC)
            else:  # 1.17+ Fabric
                write(java_file, SRC_117_FABRIC)
    
    # Create zip file
    zip_path = ROOT / "incoming" / "seed-protect-all-versions.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for target_dir, _, _, _ in targets:
            for file_path in target_dir.rglob("*"):
                if file_path.is_file():
                    arcname = file_path.relative_to(BUNDLE)
                    zf.write(file_path, arcname)
    
    print(f"\nBundle created: {zip_path}")
    print(f"Targets generated: {len(targets)}")
    
    # Show summary
    print("\nTargets:")
    for target_dir, mc_version, loader, _ in targets:
        print(f"  - {mc_version} {loader}")

if __name__ == "__main__":
    main()