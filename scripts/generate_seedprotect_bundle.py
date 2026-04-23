#!/usr/bin/env python3
"""
Generate Seed Protect mod bundle for all missing versions.

Missing versions (from Profile Diagnosis report):
  Fabric:   1.17, 1.20, 1.21, 1.21.2, 1.21.9, 26.1, 26.1.1, 26.1.2
  Forge:    1.12, 1.17, 1.20, 1.21.2, 1.21.6, 1.21.7, 1.21.8, 1.21.9,
            1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2
  NeoForge: 1.20, 26.1, 26.1.1, 26.1.2

Key API notes:
  Forge 1.12:       net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent
                    @SubscribeEvent from net.minecraftforge.fml.common.eventhandler
  Forge 1.17-1.21.5: net.minecraftforge.event.level.BlockEvent.FarmlandTrampleEvent
                    @SubscribeEvent from net.minecraftforge.eventbus.api
  Forge 1.21.6+:    EventBus 7 — FarmlandTrampleEvent.BUS.addListener(true, ...)
                    Listener returns boolean (true = cancel). No @SubscribeEvent.
  Fabric 1.17:      yarn mappings — net.minecraft.block.FarmlandBlock.onLandedUpon
  Fabric 1.20-1.21: Mojang mappings — net.minecraft.world.level.block.FarmBlock.fallOn
  Fabric 26.1:      Mojang mappings — same as 1.21.x, Java 25, new loom plugin
  NeoForge 1.20+:   net.neoforged.neoforge.event.level.BlockEvent.FarmlandTrampleEvent
"""
import json
import shutil
import sys
from pathlib import Path

# ── Mode ─────────────────────────────────────────────────────────────────────
failed_only = "--failed-only" in sys.argv

repo_root   = Path(__file__).parent.parent
incoming    = repo_root / "incoming"
bundle_name = "seed-protect-missing-versions"
bundle_dir  = incoming / bundle_name
zip_path    = incoming / f"{bundle_name}.zip"

# ── Failed-only mode ──────────────────────────────────────────────────────────
if failed_only:
    runs_dir = repo_root / "ModCompileRuns"
    if not runs_dir.exists():
        print("ERROR: No ModCompileRuns directory found.")
        sys.exit(1)
    run_dirs = sorted([d for d in runs_dir.iterdir() if d.is_dir()], reverse=True)
    if not run_dirs:
        print("ERROR: No run directories found in ModCompileRuns/")
        sys.exit(1)
    latest_run = run_dirs[0]
    result_json = latest_run / "result.json"
    if not result_json.exists():
        print(f"ERROR: No result.json in {latest_run}")
        sys.exit(1)
    result = json.loads(result_json.read_text())
    failed_targets = set()
    for job in result.get("jobs", []):
        if job.get("status") != "success":
            name = job.get("name", "")
            if name.startswith("build-"):
                parts = name.replace("build-", "").split("-")
                for i, p in enumerate(parts):
                    if p.isdigit():
                        loader = parts[-1]
                        mc_version = ".".join(parts[i:-1])
                        failed_targets.add((mc_version, loader))
                        break
    print(f"Found {len(failed_targets)} failed targets from {latest_run.name}")
    for mc, loader in sorted(failed_targets):
        print(f"  - {mc} {loader}")
    if not failed_targets:
        print("No failures found. Nothing to rebuild.")
        sys.exit(0)
    targets_to_build = failed_targets
else:
    targets_to_build = {
        # Fabric — use exact versions from manifest supported_versions
        ("1.17.1", "fabric"),   # 1.17-1.17.1 range, anchor=1.17.1
        ("1.20.1", "fabric"),   # 1.20-1.20.6 range, first supported
        ("1.21",   "fabric"),   # 1.21-1.21.1 range, min_version
        ("1.21.2", "fabric"),   # 1.21.2-1.21.8 range, min_version
        ("1.21.9", "fabric"),   # 1.21.9-1.21.11 range, min_version
        ("26.1",   "fabric"),
        ("26.1.1", "fabric"),
        ("26.1.2", "fabric"),
        # Forge — use exact versions from manifest supported_versions
        # NOTE: Forge only released for MC 26.1.2 (not 26.1 or 26.1.1)
        ("1.12",    "forge"),   # 1.12-1.12.2 range, min_version
        ("1.17.1",  "forge"),   # 1.17-1.17.1 range, only supported version
        ("1.20.1",  "forge"),   # 1.20-1.20.6 range, first supported
        ("1.21.3",  "forge"),   # 1.21.2-1.21.8 range, first supported (1.21.2 not in list)
        ("1.21.6",  "forge"),
        ("1.21.7",  "forge"),
        ("1.21.8",  "forge"),
        ("1.21.9",  "forge"),
        ("1.21.10", "forge"),
        ("1.21.11", "forge"),
        ("26.1.2",  "forge"),   # Only 26.1.2 has a Forge release
        # NeoForge — use exact versions from manifest supported_versions
        ("1.20.2",  "neoforge"),  # 1.20-1.20.6 range, first neoforge supported
        ("26.1",    "neoforge"),
        ("26.1.1",  "neoforge"),
        ("26.1.2",  "neoforge"),
    }
    print(f"Building {len(targets_to_build)} missing targets")

# ── Clean bundle dir ──────────────────────────────────────────────────────────
if bundle_dir.exists():
    shutil.rmtree(bundle_dir)
bundle_dir.mkdir(parents=True, exist_ok=True)

# ── Common metadata ───────────────────────────────────────────────────────────
MOD_ID      = "seedprotect"
MOD_NAME    = "Seed Protect"
MOD_VERSION = "1.0.0"
GROUP       = "com.seedprotect"
DESCRIPTION = "Prevents farmland and planted crops from being trampled by players and mobs."
AUTHORS     = "Itamio"
LICENSE     = "LicenseRef-All-Rights-Reserved"
HOMEPAGE    = "https://modrinth.com/mod/seed-protect"


# ─────────────────────────────────────────────────────────────────────────────
# FABRIC
# ─────────────────────────────────────────────────────────────────────────────

def create_fabric(mc_version: str):
    folder_name = f"seed-protect-{mc_version}-fabric"
    mod_folder  = bundle_dir / folder_name
    src_dir     = mod_folder / "src" / "main"
    java_dir    = src_dir / "java" / "com" / "seedprotect"
    res_dir     = src_dir / "resources"
    java_dir.mkdir(parents=True, exist_ok=True)
    (java_dir / "mixin").mkdir(exist_ok=True)
    res_dir.mkdir(parents=True, exist_ok=True)

    # ── Main class ────────────────────────────────────────────────────────────
    (java_dir / "SeedProtectMod.java").write_text(f"""package com.seedprotect;

import net.fabricmc.api.ModInitializer;

public final class SeedProtectMod implements ModInitializer {{
    public static final String MOD_ID = "{MOD_ID}";

    @Override
    public void onInitialize() {{
    }}
}}
""")

    # ── Mixin ─────────────────────────────────────────────────────────────────
    # Fabric mapping eras:
    #   1.17.x        → yarn: net.minecraft.block.FarmlandBlock / onLandedUpon
    #   1.18.x–1.20.x → yarn: net.minecraft.block.FarmlandBlock / onLandedUpon
    #   1.21+         → Mojang: net.minecraft.world.level.block.FarmBlock / fallOn
    #   26.1+         → Mojang (no obfuscation): net.minecraft.world.level.block.FarmBlock / fallOn
    is_yarn = mc_version.startswith("1.17") or mc_version.startswith("1.18") or \
              mc_version.startswith("1.19") or mc_version.startswith("1.20")
    is_26   = mc_version.startswith("26.")

    if is_yarn:
        # Yarn mappings (1.17.x)
        mixin_src = """package com.seedprotect.mixin;

import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fabric 1.17.x — yarn mappings
// Class: net.minecraft.block.FarmlandBlock
// Method: onLandedUpon (yarn name for fallOn)
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
    @Inject(method = "onLandedUpon", at = @At("HEAD"), cancellable = true)
    private void seedprotect_cancelTrample(World world, BlockState state,
            BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        ci.cancel();
    }
}
"""
    else:
        # Mojang mappings (1.20+, 26.1+)
        mixin_src = """package com.seedprotect.mixin;

import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fabric 1.20+ / 26.1+ — Mojang mappings
// Class: net.minecraft.world.level.block.FarmBlock
// Method: fallOn
@Mixin(FarmBlock.class)
public abstract class FarmlandBlockMixin {
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void seedprotect_cancelTrample(Level level, BlockState state,
            BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        ci.cancel();
    }
}
"""
    (java_dir / "mixin" / "FarmlandBlockMixin.java").write_text(mixin_src)

    # ── fabric.mod.json ───────────────────────────────────────────────────────
    java_req = ">=25" if is_26 else ">=21" if not is_yarn else ">=16"
    fabric_mod = {
        "schemaVersion": 1,
        "id": MOD_ID,
        "version": MOD_VERSION,
        "name": MOD_NAME,
        "description": DESCRIPTION,
        "authors": [AUTHORS],
        "license": LICENSE,
        "contact": {"homepage": HOMEPAGE},
        "environment": "*",
        "entrypoints": {"main": ["com.seedprotect.SeedProtectMod"]},
        "mixins": ["seedprotect.mixins.json"],
        "depends": {
            "fabricloader": ">=0.14.0",
            "minecraft": f"~{mc_version}",
            "java": java_req,
            "fabric-api": "*",
        }
    }
    (res_dir / "fabric.mod.json").write_text(json.dumps(fabric_mod, indent=2))

    # ── Mixin config ──────────────────────────────────────────────────────────
    mixin_config = {
        "required": True,
        "package": "com.seedprotect.mixin",
        "compatibilityLevel": "JAVA_21" if not is_26 else "JAVA_25",
        "mixins": ["FarmlandBlockMixin"],
        "injectors": {"defaultRequire": 1}
    }
    (res_dir / "seedprotect.mixins.json").write_text(json.dumps(mixin_config, indent=2))

    # ── mod.txt / version.txt ─────────────────────────────────────────────────
    (mod_folder / "mod.txt").write_text(
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={GROUP}\nentrypoint_class=com.seedprotect.SeedProtectMod\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side=server\n"
    )
    (mod_folder / "version.txt").write_text(
        f"minecraft_version={mc_version}\nloader=fabric\n"
    )
    print(f"  Created {folder_name}")


# ─────────────────────────────────────────────────────────────────────────────
# FORGE
# ─────────────────────────────────────────────────────────────────────────────

def create_forge(mc_version: str):
    folder_name = f"seed-protect-{mc_version}-forge"
    mod_folder  = bundle_dir / folder_name
    src_dir     = mod_folder / "src" / "main"
    java_dir    = src_dir / "java" / "com" / "seedprotect"
    res_dir     = src_dir / "resources"
    java_dir.mkdir(parents=True, exist_ok=True)
    res_dir.mkdir(parents=True, exist_ok=True)
    (res_dir / "META-INF").mkdir(exist_ok=True)

    # ── Determine API era ─────────────────────────────────────────────────────
    # Era 1: 1.12.x — legacy mcmod.info, old eventhandler package
    # Era 2: 1.17–1.21.5 — mods.toml, eventbus.api.SubscribeEvent
    # Era 3: 1.21.6+ — EventBus 7, FarmlandTrampleEvent.BUS.addListener

    def ver_tuple(v):
        parts = []
        for p in v.split("."):
            try:
                parts.append(int(p))
            except ValueError:
                parts.append(0)
        return tuple(parts)

    vt = ver_tuple(mc_version)
    is_legacy   = vt < (1, 13)          # 1.12.x
    is_eb7      = vt >= (1, 21, 6) or mc_version.startswith("26.")  # EventBus 7
    is_modern   = not is_legacy and not is_eb7  # 1.17–1.21.5

    # ── Main class ────────────────────────────────────────────────────────────
    if is_legacy:
        main_src = f"""package com.seedprotect;

import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid = SeedProtectMod.MOD_ID, name = SeedProtectMod.NAME, version = SeedProtectMod.VERSION)
@Mod.EventBusSubscriber(modid = SeedProtectMod.MOD_ID)
public final class SeedProtectMod {{
    public static final String MOD_ID = "{MOD_ID}";
    public static final String NAME = "{MOD_NAME}";
    public static final String VERSION = "{MOD_VERSION}";

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {{
        event.setCanceled(true);
    }}
}}
"""
    elif is_modern:
        # Forge 1.17 uses net.minecraftforge.event.world (not event.level)
        # Forge 1.18+ uses net.minecraftforge.event.level
        def ver_tuple_v(v):
            parts = []
            for p in v.split("."):
                try: parts.append(int(p))
                except ValueError: parts.append(0)
            return tuple(parts)
        vt2 = ver_tuple_v(mc_version)
        event_pkg = "net.minecraftforge.event.world" if vt2 < (1, 18) else "net.minecraftforge.event.level"

        main_src = f"""package com.seedprotect;

import {event_pkg}.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("{MOD_ID}")
@Mod.EventBusSubscriber(modid = "{MOD_ID}", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {{
    public static final String MOD_ID = "{MOD_ID}";

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {{
        event.setCanceled(true);
    }}
}}
"""
    else:
        # EventBus 7 (Forge 1.21.6+)
        # - FarmlandTrampleEvent.BUS.addListener(alwaysCancelling=true, handler)
        # - Listener returns boolean (true = cancel)
        # - No @SubscribeEvent annotation
        main_src = f"""package com.seedprotect;

import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Forge 1.21.6+ uses EventBus 7.
// Each event has its own static BUS field.
// Cancellation is done by returning true from the listener (boolean return type).
// @SubscribeEvent is no longer used for cancellable event listeners.
@Mod("{MOD_ID}")
public final class SeedProtectMod {{
    public static final String MOD_ID = "{MOD_ID}";

    public SeedProtectMod(FMLJavaModLoadingContext context) {{
        // Register on the game/Forge bus (not the mod bus).
        // alwaysCancelling = true tells EventBus 7 this listener always cancels.
        BlockEvent.FarmlandTrampleEvent.BUS.addListener(
            /* alwaysCancelling = */ true,
            SeedProtectMod::onFarmlandTrample
        );
    }}

    private static boolean onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {{
        // Return true to cancel the trample event.
        return true;
    }}
}}
"""

    (java_dir / "SeedProtectMod.java").write_text(main_src)

    # ── mods.toml / mcmod.info ────────────────────────────────────────────────
    if is_legacy:
        mcmod = json.dumps([{
            "modid": MOD_ID,
            "name": MOD_NAME,
            "description": DESCRIPTION,
            "version": MOD_VERSION,
            "mcversion": mc_version,
            "authorList": [AUTHORS],
            "url": HOMEPAGE,
        }], indent=2)
        (res_dir / "mcmod.info").write_text(mcmod)
        (res_dir / "pack.mcmeta").write_text(
            json.dumps({"pack": {"pack_format": 3, "description": MOD_NAME}}, indent=2)
        )
    else:
        # Determine loader version range
        if is_eb7:
            loader_ver = "[64,)"
            mc_range   = f"[{mc_version},)"
        elif vt >= (1, 21, 9):
            loader_ver = "[59,)"
            mc_range   = f"[{mc_version},1.22)"
        elif vt >= (1, 21, 2):
            loader_ver = "[53,)"
            mc_range   = f"[{mc_version},1.22)"
        elif vt >= (1, 21,):
            loader_ver = "[51,)"
            mc_range   = f"[{mc_version},1.22)"
        elif vt >= (1, 20,):
            loader_ver = "[47,)"
            mc_range   = f"[{mc_version},1.21)"
        elif vt >= (1, 19,):
            loader_ver = "[41,)"
            mc_range   = f"[{mc_version},1.20)"
        elif vt >= (1, 18,):
            loader_ver = "[38,)"
            mc_range   = f"[{mc_version},1.19)"
        else:
            loader_ver = "[36,)"
            mc_range   = f"[{mc_version},1.18)"

        mods_toml = f"""modLoader="javafml"
loaderVersion="{loader_ver}"
license="{LICENSE}"

[[mods]]
modId="{MOD_ID}"
version="{MOD_VERSION}"
displayName="{MOD_NAME}"
description="{DESCRIPTION}"
authors="{AUTHORS}"
displayURL="{HOMEPAGE}"

[[dependencies.{MOD_ID}]]
modId="forge"
mandatory=true
versionRange="{loader_ver}"
ordering="NONE"
side="SERVER"

[[dependencies.{MOD_ID}]]
modId="minecraft"
mandatory=true
versionRange="{mc_range}"
ordering="NONE"
side="SERVER"
"""
        (res_dir / "META-INF" / "mods.toml").write_text(mods_toml)
        (res_dir / "pack.mcmeta").write_text(
            json.dumps({"pack": {"pack_format": 15, "description": MOD_NAME}}, indent=2)
        )

    # ── mod.txt / version.txt ─────────────────────────────────────────────────
    (mod_folder / "mod.txt").write_text(
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={GROUP}\nentrypoint_class=com.seedprotect.SeedProtectMod\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side=server\n"
    )
    (mod_folder / "version.txt").write_text(
        f"minecraft_version={mc_version}\nloader=forge\n"
    )
    print(f"  Created {folder_name}")


# ─────────────────────────────────────────────────────────────────────────────
# NEOFORGE
# ─────────────────────────────────────────────────────────────────────────────

def create_neoforge(mc_version: str):
    folder_name = f"seed-protect-{mc_version}-neoforge"
    mod_folder  = bundle_dir / folder_name
    src_dir     = mod_folder / "src" / "main"
    java_dir    = src_dir / "java" / "com" / "seedprotect"
    res_dir     = src_dir / "resources"
    java_dir.mkdir(parents=True, exist_ok=True)
    res_dir.mkdir(parents=True, exist_ok=True)
    (res_dir / "META-INF").mkdir(exist_ok=True)

    is_26 = mc_version.startswith("26.")

    # ── Main class ────────────────────────────────────────────────────────────
    # NeoForge uses its own event package throughout all versions.
    # NeoForge 26.1+ uses EventBus 7 — same pattern as Forge 26.1+:
    #   FarmlandTrampleEvent.BUS.addListener(true, handler)
    #   Listener returns boolean (true = cancel). No @EventBusSubscriber.
    # NeoForge 1.20.x–1.21.x uses the old @EventBusSubscriber pattern.
    is_neo_eb7 = mc_version.startswith("26.")

    if is_neo_eb7:
        main_src = f"""package com.seedprotect;

import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;

// NeoForge 26.1+ uses EventBus 7.
// Each event has its own static BUS field.
// Cancellation is done by returning true from the listener.
@Mod("{MOD_ID}")
public final class SeedProtectMod {{
    public static final String MOD_ID = "{MOD_ID}";

    public SeedProtectMod(FMLJavaModLoadingContext context) {{
        BlockEvent.FarmlandTrampleEvent.BUS.addListener(
            /* alwaysCancelling = */ true,
            SeedProtectMod::onFarmlandTrample
        );
    }}

    private static boolean onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {{
        return true;
    }}
}}
"""
    else:
        main_src = f"""package com.seedprotect;

import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@Mod("{MOD_ID}")
@Mod.EventBusSubscriber(modid = "{MOD_ID}", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SeedProtectMod {{
    public static final String MOD_ID = "{MOD_ID}";

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {{
        event.setCanceled(true);
    }}
}}
"""
    (java_dir / "SeedProtectMod.java").write_text(main_src)

    # ── neoforge.mods.toml ────────────────────────────────────────────────────
    if is_26:
        neo_range = "[26.1,)"
        mc_range  = "[26.1,27)"
    else:
        neo_range = "[20.4,)"
        mc_range  = f"[{mc_version},1.21)"

    mods_toml = f"""modLoader="javafml"
loaderVersion="[1,)"
license="{LICENSE}"

[[mods]]
modId="{MOD_ID}"
version="{MOD_VERSION}"
displayName="{MOD_NAME}"
description="{DESCRIPTION}"
authors="{AUTHORS}"
displayURL="{HOMEPAGE}"

[[dependencies.{MOD_ID}]]
modId="neoforge"
mandatory=true
versionRange="{neo_range}"
ordering="NONE"
side="SERVER"

[[dependencies.{MOD_ID}]]
modId="minecraft"
mandatory=true
versionRange="{mc_range}"
ordering="NONE"
side="SERVER"
"""
    (res_dir / "META-INF" / "neoforge.mods.toml").write_text(mods_toml)
    (res_dir / "pack.mcmeta").write_text(
        json.dumps({"pack": {"pack_format": 15, "description": MOD_NAME}}, indent=2)
    )

    # ── mod.txt / version.txt ─────────────────────────────────────────────────
    (mod_folder / "mod.txt").write_text(
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={GROUP}\nentrypoint_class=com.seedprotect.SeedProtectMod\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side=server\n"
    )
    (mod_folder / "version.txt").write_text(
        f"minecraft_version={mc_version}\nloader=neoforge\n"
    )
    print(f"  Created {folder_name}")


# ─────────────────────────────────────────────────────────────────────────────
# Generate all targets
# ─────────────────────────────────────────────────────────────────────────────
for mc_version, loader in sorted(targets_to_build):
    if loader == "fabric":
        create_fabric(mc_version)
    elif loader == "forge":
        create_forge(mc_version)
    elif loader == "neoforge":
        create_neoforge(mc_version)

# ── Create zip ────────────────────────────────────────────────────────────────
print(f"\nCreating zip: {zip_path}")
if zip_path.exists():
    zip_path.unlink()
shutil.make_archive(str(zip_path.with_suffix("")), "zip", bundle_dir)

count = len(list(bundle_dir.iterdir()))
print(f"\n✓ Bundle created: {zip_path}")
print(f"✓ Contains {count} mod folders")
print("\nNext steps:")
print("  1. git add scripts/ incoming/")
print(f'  2. git commit -m "Add Seed Protect missing versions"')
print("  3. git push")
print(f'  4. python3 scripts/run_build.py incoming/{bundle_name}.zip --modrinth https://modrinth.com/mod/seed-protect')
