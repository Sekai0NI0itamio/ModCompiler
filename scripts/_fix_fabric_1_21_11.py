#!/usr/bin/env python3
"""
Fix Fabric 1.21.11: patch RandomTeleportService.java to use
the Forge 1.21.11 version (renamed to fabric package) which
uses reflection-based dimensionKey() instead of location().
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUNDLE_SRC = Path("/tmp/common-server-core-bundle")
FORGE_PKG = "com/itamio/servercore/forge"
FABRIC_PKG = "com/itamio/servercore/fabric"
REF_1_21_11_FORGE = "CqurFhjF"

gen = ROOT / "scripts" / "generate_servercore_bundle.py"
content = gen.read_text()

# The write_fabric_1_21_11 function needs to use the Forge 1.21.11 source
# for ALL files (not just TeleportUtil), renamed to fabric package.
# The Forge 1.21.11 source uses reflection for all API changes.

old = '''def write_fabric_1_21_11(base):
    """For Fabric 1.21.11, use the Forge 1.21.11 TeleportUtil (renamed to fabric package).
    The Fabric 1.21.11 TeleportUtil uses intermediary names which don't compile.
    The Forge 1.21.11 TeleportUtil uses reflection and official names — works for Fabric too."""
    write_fabric_src(base, patch_1_21_11_api)
    # Override TeleportUtil with the Forge 1.21.11 version (renamed to fabric package)
    try:
        forge_tu = (BUNDLE_SRC / "versions" / REF_1_21_11_FORGE / "decompiled" / "src" / "src" / "main" / "java" / FORGE_PKG / "TeleportUtil.java").read_text(encoding="utf-8")
        # Rename package
        fabric_tu = forge_tu.replace(
            "package com.itamio.servercore.forge;",
            "package com.itamio.servercore.fabric;"
        ).replace(
            "import com.itamio.servercore.forge.",
            "import com.itamio.servercore.fabric."
        )
        write(base / "src" / "main" / "java" / FABRIC_PKG / "TeleportUtil.java", fabric_tu)
    except Exception as e:
        print(f"Warning: could not load 1.21.11 Forge TeleportUtil for Fabric: {e}")'''

new = '''def write_fabric_1_21_11(base):
    """For Fabric 1.21.11, use the Forge 1.21.11 source for ALL shared files
    (renamed to fabric package). The Forge 1.21.11 source uses reflection for
    all API changes (ResourceLocation, location(), etc.) and compiles cleanly."""
    for fname in FORGE_SHARED_FILES:
        src_path = BUNDLE_SRC / "versions" / REF_1_21_11_FORGE / "decompiled" / "src" / "src" / "main" / "java" / FORGE_PKG / fname
        if src_path.exists():
            src = src_path.read_text(encoding="utf-8")
        else:
            src = read_forge_file(fname)
            if fname == "ServerCoreData.java":
                src = patch_server_core_data(src)
        # Rename package from forge to fabric
        src = src.replace(
            "package com.itamio.servercore.forge;",
            "package com.itamio.servercore.fabric;"
        ).replace(
            "import com.itamio.servercore.forge.",
            "import com.itamio.servercore.fabric."
        )
        write(base / "src" / "main" / "java" / FABRIC_PKG / fname, src)
    # Write clean Fabric entrypoint
    fabric_mod = """\\
package com.itamio.servercore.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class ServerCoreFabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                ServerCoreCommands.register(dispatcher)
        );
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ServerCoreData data = ServerCoreData.get(server);
            if (!data.hasSeen(player.getUUID())) {
                data.markSeen(player.getUUID());
                RandomTeleportService.RtpResult result =
                    RandomTeleportService.getInstance().teleport(player, "minecraft:overworld");
                if (!result.isSuccess()) {
                    MessageUtil.send(player, "First-join teleport failed: " + result.getMessage());
                }
            }
        });
    }
}
"""
    write(base / "src" / "main" / "java" / FABRIC_PKG / "ServerCoreFabricMod.java", fabric_mod)'''

if old in content:
    content = content.replace(old, new)
    gen.write_text(content)
    print("Fixed write_fabric_1_21_11")
else:
    print("ERROR: could not find old function — check manually")
    # Show what's there
    idx = content.find("def write_fabric_1_21_11")
    print(content[idx:idx+300])
