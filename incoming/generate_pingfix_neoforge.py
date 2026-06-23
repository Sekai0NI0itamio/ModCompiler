#!/usr/bin/env python3
"""Generate pingfix-neoforge-all.zip with source for all supported NeoForge versions."""

import zipfile
from pathlib import Path

NEOFORGE_VERSIONS = [
    "1.20.2", "1.20.4", "1.20.6",
    "1.21", "1.21.1",
    "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8",
    "1.21.9", "1.21.10", "1.21.11",
    "26.1", "26.1.1", "26.1.2",
]

MOD_TXT = """mod_id=pingfix
name=PingFix
mod_version=3.2.0
group=com.itamio.pingfix
entrypoint_class=com.itamio.pingfix.PingFixMod
description=Fixes multiplayer server list ping by periodically refreshing the server browser.
authors=Itamio
license=MIT
homepage=https://modrinth.com/mod/pingfix
runtime_side=client
"""

PINGFIX_MOD = '''package com.itamio.pingfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PingFixMod.MOD_ID)
public final class PingFixMod {
    public static final String MOD_ID = "pingfix";
    private static final int REFRESH_INTERVAL_FRAMES = 1200;
    private int frameCounter = 0;

    public PingFixMod() {
        NeoForge.EVENT_BUS.addListener(this::onRenderGui);
    }

    private void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof JoinMultiplayerScreen) {
            frameCounter++;
            if (frameCounter >= REFRESH_INTERVAL_FRAMES) {
                frameCounter = 0;
                mc.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
            }
        } else {
            frameCounter = 0;
        }
    }
}
'''

NEOFORGE_MODS_TOML = '''modLoader="javafml"
loaderVersion="[1,)"
license="MIT"

[[mods]]
modId="pingfix"
version="3.2.0"
displayName="PingFix"
description="Fixes multiplayer server list ping by periodically refreshing the server browser."
authors="Itamio"

[[dependencies.pingfix]]
modId="neoforge"
mandatory=true
versionRange="[0,)"
ordering="NONE"
side="CLIENT"

[[dependencies.pingfix]]
modId="minecraft"
mandatory=true
versionRange="[0,)"
ordering="NONE"
side="CLIENT"
'''

PACK_MCMETA = '''{
  "pack": {
    "description": "PingFix resources",
    "pack_format": 1
  }
}
'''

OUTPUT = Path("incoming/pingfix-neoforge-all.zip")

with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as zf:
    for version in NEOFORGE_VERSIONS:
        prefix = f"pingfix-neoforge-{version}/"
        zf.writestr(f"{prefix}mod.txt", MOD_TXT)
        zf.writestr(f"{prefix}version.txt", f"minecraft_version={version}\nloader=neoforge\n")
        zf.writestr(f"{prefix}src/main/java/com/itamio/pingfix/PingFixMod.java", PINGFIX_MOD)
        zf.writestr(f"{prefix}src/main/resources/META-INF/neoforge.mods.toml", NEOFORGE_MODS_TOML)
        zf.writestr(f"{prefix}src/main/resources/pack.mcmeta", PACK_MCMETA)

print(f"Created {OUTPUT} with {len(NEOFORGE_VERSIONS)} NeoForge versions")
