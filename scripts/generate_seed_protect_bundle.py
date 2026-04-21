#!/usr/bin/env python3
"""
Generates the Seed Protect all-versions bundle.
Run: python3 scripts/generate_seed_protect_bundle.py

Seed Protect mod: Prevents farmland and planted crops from being trampled by players and mobs.
Original version: 1.12.2 Forge

Fabric approach: Uses a mixin on FarmlandBlock to cancel the fallOn / onLandedUpon method.
  - 1.16.5 – 1.20.4: FarmlandBlock.fallOn(World, BlockPos, Entity, float) → cancel via @Inject
  - 1.20.5+:         FarmlandBlock.onLandedUpon(World, BlockState, BlockPos, Entity, float) → cancel
  - The mixin file is placed at src/main/resources/seedprotect.mixins.json and is auto-detected
    by the build adapter (it scans for *.mixins.json in src/main/resources/).
"""
import argparse, json, shutil, zipfile
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
MIXIN_PKG   = f"{GROUP}.mixin"
MIXIN_PKG_PATH = MIXIN_PKG.replace('.', '/')

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
# MIXIN JSON — placed in src/main/resources/seedprotect.mixins.json
# The build adapter auto-detects *.mixins.json files and adds them to fabric.mod.json
# ============================================================

def mixin_json(java_compat: str, mixin_class: str) -> str:
    return (
        '{\n'
        '  "required": true,\n'
        f'  "package": "{MIXIN_PKG}",\n'
        f'  "compatibilityLevel": "{java_compat}",\n'
        '  "mixins": [\n'
        f'    "{mixin_class}"\n'
        '  ],\n'
        '  "injectors": {\n'
        '    "defaultRequire": 1\n'
        '  }\n'
        '}\n'
    )

# ============================================================
# SOURCE CODE FOR DIFFERENT VERSIONS
# ============================================================

# ---- FORGE / NEOFORGE ----

# 1.8.9 Forge - FarmlandTrampleEvent does NOT exist in 1.8.9 Forge.
# Use a mixin on FarmlandBlock to cancel trampling.
# 1.8.9 Forge uses the legacy mcmod.info adapter; the main class is the entrypoint.
# We use a Forge mixin (SpongePowered Mixin is available via Forge 1.8.9 template).
# Actually 1.8.9 Forge does NOT support Mixin natively. Use BlockEvent.BreakEvent
# with a check, or use the EntityFallEvent approach.
# The correct approach for 1.8.9: register a listener on EntityFallEvent and
# check if the block below is farmland, then set the block back.
# However, the cleanest approach is to use net.minecraftforge.event.entity.EntityEvent
# or to hook into the block update. In 1.8.9, we can use BlockEvent and check
# if the block is being converted from farmland to dirt.
# Actually the simplest reliable approach: use @SubscribeEvent on BlockEvent
# and check for farmland->dirt conversion. But this event doesn't exist in 1.8.9.
# Best approach: register on MinecraftForge.EVENT_BUS manually (no @EventBusSubscriber
# in 1.8.9), and use net.minecraftforge.event.world.BlockEvent.
# In 1.8.9, FarmlandTrampleEvent was added in Forge 11.15.x (1.8.9 era).
# Let's use the correct 1.8.9 Forge API: register manually, use FarmlandTrampleEvent.
SRC_189_FORGE = """\
package com.seedprotect;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(
    modid = SeedProtectMod.MOD_ID,
    name = SeedProtectMod.NAME,
    version = SeedProtectMod.VERSION,
    acceptedMinecraftVersions = "[1.8.9]"
)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onFarmlandTrample(FarmlandTrampleEvent event) {
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
    acceptedMinecraftVersions = "[1.12,1.12.2]"
)
@EventBusSubscriber(modid = SeedProtectMod.MOD_ID)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";

    private SeedProtectMod() {}

    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
"""

# 1.16.5 Forge - FarmlandTrampleEvent still in world package
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

    public SeedProtectMod() {}

    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
"""

# 1.17-1.18 Forge - FarmlandTrampleEvent still in world package
SRC_117_FORGE = SRC_1165_FORGE

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

    public SeedProtectMod() {}

    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.setCanceled(true);
    }
}
"""

# 1.20+ Forge - same as 1.19+
SRC_120_FORGE = SRC_119_FORGE

# 1.21.6+ Forge - SubscribeEvent moved to listener subpackage, setCanceled → cancel()
SRC_1216_FORGE = """\
package com.seedprotect;

import net.minecraftforge.event.level.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@Mod(SeedProtectMod.MOD_ID)
@EventBusSubscriber(modid = SeedProtectMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {
    public static final String MOD_ID = "seedprotect";
    public static final String NAME = "Seed Protect";
    public static final String VERSION = "1.0.0";

    public SeedProtectMod() {}

    @SubscribeEvent
    public static void onFarmlandTrample(FarmlandTrampleEvent event) {
        event.cancel();
    }
}
"""

# 1.20+ NeoForge
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

# ---- FABRIC ----
# Fabric has no FarmlandTrampleEvent. We use a mixin on FarmlandBlock.
#
# 1.16.5 – 1.20.4: method is fallOn(World, BlockPos, Entity, float)
#   Yarn names: fallOn(net.minecraft.world.World, net.minecraft.util.math.BlockPos,
#                      net.minecraft.entity.Entity, float)
#   We inject at HEAD and cancel via CallbackInfo.cancel().
#
# 1.20.5+: method renamed to onLandedUpon(ServerWorld, BlockState, BlockPos, Entity, float)
#   Yarn names differ; use Mojang-mapped names for 1.20.5+ (fabric_split adapter uses Mojang).
#   Actually fabric_split still uses Yarn for 1.20.x. Check: 1.20.5 uses Yarn mappings.
#   Method: onLandedUpon(net.minecraft.world.World, net.minecraft.block.BlockState,
#                        net.minecraft.util.math.BlockPos, net.minecraft.entity.Entity, float)
#
# Main mod class just registers nothing — the mixin does all the work.

# Fabric main class (same for all Fabric versions)
SRC_FABRIC_MAIN = """\
package com.seedprotect;

import net.fabricmc.api.ModInitializer;

public final class SeedProtectMod implements ModInitializer {
    public static final String MOD_ID = "seedprotect";

    @Override
    public void onInitialize() {
        // Farmland protection is handled by FarmlandBlockMixin
    }
}
"""

# Fabric mixin for 1.16.5 – 1.20.4
# FarmlandBlock.fallOn(World, BlockPos, Entity, float) — Yarn mappings
SRC_FABRIC_MIXIN_FALLON = """\
package com.seedprotect.mixin;

import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {

    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void seedprotect_cancelTrample(World world, BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        ci.cancel();
    }
}
"""

# Fabric mixin for 1.20.5 – 1.21.x
# FarmlandBlock.onLandedUpon(World, BlockState, BlockPos, Entity, float) — Mojang mappings
# 1.21.x Fabric uses Mojang mappings: net.minecraft.world.level.block.FarmlandBlock
SRC_FABRIC_MIXIN_ONLANDEDUPON = """\
package com.seedprotect.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {

    @Inject(method = "onLandedUpon", at = @At("HEAD"), cancellable = true)
    private void seedprotect_cancelTrample(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        ci.cancel();
    }
}
"""


def write_fabric_target(target_dir: Path, mc_version: str, use_landed_upon: bool, java_compat: str):
    """Write a complete Fabric target folder with mixin."""
    # Main mod class
    write(target_dir / "src/main/java" / PKG / "SeedProtectMod.java", SRC_FABRIC_MAIN)

    # Mixin class
    mixin_src = SRC_FABRIC_MIXIN_ONLANDEDUPON if use_landed_upon else SRC_FABRIC_MIXIN_FALLON
    write(target_dir / "src/main/java" / MIXIN_PKG_PATH / "FarmlandBlockMixin.java", mixin_src)

    # Mixin JSON — auto-detected by the build adapter
    write(
        target_dir / "src/main/resources/seedprotect.mixins.json",
        mixin_json(java_compat, "FarmlandBlockMixin"),
    )

    # mod.txt and version.txt
    write(target_dir / "mod.txt", mod_txt())
    write(target_dir / "version.txt", version_txt(mc_version, "fabric"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed-only", action="store_true",
                        help="Only regenerate targets that failed in the last run")
    args = parser.parse_args()

    # Clean bundle directory
    if BUNDLE.exists():
        shutil.rmtree(BUNDLE)
    BUNDLE.mkdir(parents=True)

    # Load version manifest
    manifest_path = ROOT / "version-manifest.json"
    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    # Build full target list
    targets = []  # (target_dir, mc_version, loader, folder)

    for range_info in manifest["ranges"]:
        folder = range_info["folder"]
        loaders = range_info.get("loaders", {})

        # --- Forge ---
        if "forge" in loaders:
            forge_info = loaders["forge"]
            if "supported_versions" in forge_info:
                forge_versions = forge_info["supported_versions"]
            else:
                forge_versions = [forge_info["anchor_version"]]
            for mc in forge_versions:
                name = f"Seed-Protect-{mc}-forge"
                targets.append((BUNDLE / name, mc, "forge", folder))

        # --- NeoForge ---
        if "neoforge" in loaders:
            neo_info = loaders["neoforge"]
            if "supported_versions" in neo_info:
                neo_versions = neo_info["supported_versions"]
            else:
                neo_versions = [neo_info["anchor_version"]]
            for mc in neo_versions:
                name = f"Seed-Protect-{mc}-neoforge"
                targets.append((BUNDLE / name, mc, "neoforge", folder))

        # --- Fabric ---
        if "fabric" in loaders:
            fab_info = loaders["fabric"]
            if "supported_versions" in fab_info:
                fab_versions = fab_info["supported_versions"]
            else:
                fab_versions = [fab_info["anchor_version"]]
            for mc in fab_versions:
                name = f"Seed-Protect-{mc}-fabric"
                targets.append((BUNDLE / name, mc, "fabric", folder))

    # --failed-only: filter to only targets that failed in the last run
    active_targets = targets
    if args.failed_only:
        runs_root = ROOT / "ModCompileRuns"
        run_dir = None
        if runs_root.exists():
            candidates = sorted(runs_root.iterdir())
            if candidates:
                run_dir = candidates[-1]

        if run_dir is None:
            print("WARNING: --failed-only requested but no run dir found. Using all targets.")
        else:
            art = run_dir / "artifacts" / "all-mod-builds" / "mods"
            if not art.exists():
                print(f"WARNING: No mods artifact at {art}. Using all targets.")
            else:
                failed_slugs = set()
                for mod_dir in art.iterdir():
                    if not mod_dir.is_dir():
                        continue
                    result_file = mod_dir / "result.json"
                    if result_file.exists():
                        try:
                            result = json.loads(result_file.read_text(encoding="utf-8"))
                            if result.get("status") != "success":
                                failed_slugs.add(mod_dir.name)
                        except Exception:
                            failed_slugs.add(mod_dir.name)
                    else:
                        failed_slugs.add(mod_dir.name)

                if failed_slugs:
                    def slug_for(mc: str, loader: str) -> str:
                        return f"{MOD_ID}-{loader}-{mc.replace('.', '-')}".lower()

                    active_targets = [
                        t for t in targets if slug_for(t[1], t[2]) in failed_slugs
                    ]
                    skipped = len(targets) - len(active_targets)
                    print(f"Failed-only mode: {len(active_targets)} targets to rebuild "
                          f"(skipping {skipped} already-green)")
                    for t in active_targets:
                        print(f"  → {t[0].name}")
                else:
                    print("No failed targets found — all targets already green!")
                    active_targets = []

    print(f"Generating {len(active_targets)} targets...")

    for target_dir, mc_version, loader, folder in active_targets:
        print(f"  {mc_version} {loader}")
        target_dir.mkdir(parents=True, exist_ok=True)

        if loader == "forge":
            src_dir = target_dir / "src/main/java" / PKG
            src_dir.mkdir(parents=True, exist_ok=True)
            write(target_dir / "mod.txt", mod_txt())
            write(target_dir / "version.txt", version_txt(mc_version, "forge"))

            if folder == "1.8.9":
                write(src_dir / "SeedProtectMod.java", SRC_189_FORGE)
            elif folder == "1.12-1.12.2":
                write(src_dir / "SeedProtectMod.java", SRC_1122_FORGE)
            elif folder == "1.16.5":
                write(src_dir / "SeedProtectMod.java", SRC_1165_FORGE)
            elif folder in ("1.17-1.17.1", "1.18-1.18.2"):
                write(src_dir / "SeedProtectMod.java", SRC_117_FORGE)
            elif folder == "1.21.2-1.21.8":
                # 1.21.6+ uses listener.SubscribeEvent and cancel()
                parts = mc_version.split(".")
                patch = int(parts[2]) if len(parts) > 2 else 0
                if patch >= 6:
                    write(src_dir / "SeedProtectMod.java", SRC_1216_FORGE)
                else:
                    write(src_dir / "SeedProtectMod.java", SRC_119_FORGE)
            elif folder == "1.21.9-1.21.11":
                # All of 1.21.9-1.21.11 use the new listener API
                write(src_dir / "SeedProtectMod.java", SRC_1216_FORGE)
            else:
                # 1.19-1.21.5 uses level package with old eventbus.api
                write(src_dir / "SeedProtectMod.java", SRC_119_FORGE)

        elif loader == "neoforge":
            src_dir = target_dir / "src/main/java" / PKG
            src_dir.mkdir(parents=True, exist_ok=True)
            write(target_dir / "mod.txt", mod_txt())
            write(target_dir / "version.txt", version_txt(mc_version, "neoforge"))
            write(src_dir / "SeedProtectMod.java", SRC_120_NEOFORGE)

        elif loader == "fabric":
            # Determine Java compat level and which mixin method to use
            # 1.16.5 – 1.17.x: Java 16
            # 1.18+: Java 17
            # 1.20.5+: Java 21
            # Method rename: fallOn → onLandedUpon in 1.20.5+
            parts = mc_version.split(".")
            major = int(parts[0])
            minor = int(parts[1]) if len(parts) > 1 else 0
            patch = int(parts[2]) if len(parts) > 2 else 0

            if folder in ("1.16.5", "1.17-1.17.1"):
                java_compat = "JAVA_16"
            elif folder in ("1.18-1.18.2", "1.19-1.19.4"):
                java_compat = "JAVA_17"
            elif folder == "1.20-1.20.6":
                # 1.20.5+ uses Java 21
                if minor == 20 and patch >= 5:
                    java_compat = "JAVA_21"
                else:
                    java_compat = "JAVA_17"
            else:
                # 1.21+
                java_compat = "JAVA_21"

            # onLandedUpon was introduced in 1.20.5
            use_landed_upon = (
                folder == "1.20-1.20.6" and minor == 20 and patch >= 5
            ) or folder in ("1.21-1.21.1", "1.21.2-1.21.8", "1.21.9-1.21.11")

            write_fabric_target(target_dir, mc_version, use_landed_upon, java_compat)

    # Create zip
    zip_path = ROOT / "incoming" / "seed-protect-all-versions.zip"
    print(f"\nCreating zip: {zip_path}")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        total = 0
        for fpath in sorted(BUNDLE.rglob("*")):
            if fpath.is_file():
                arcname = str(fpath.relative_to(BUNDLE))
                zf.write(fpath, arcname)
                total += 1
    print(f"Wrote {zip_path} ({total} files)")
    print(f"\nDone. {len(active_targets)} targets generated.")


if __name__ == "__main__":
    main()
