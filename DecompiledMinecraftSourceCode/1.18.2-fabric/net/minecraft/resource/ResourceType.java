/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.resource;

import com.mojang.bridge.game.GameVersion;
import com.mojang.bridge.game.PackType;

public enum ResourceType {
    CLIENT_RESOURCES("assets", PackType.RESOURCE),
    SERVER_DATA("data", PackType.DATA);

    private final String directory;
    private final PackType packType;

    private ResourceType(String name, PackType packType) {
        this.directory = name;
        this.packType = packType;
    }

    public String getDirectory() {
        return this.directory;
    }

    public int getPackVersion(GameVersion gameVersion) {
        return gameVersion.getPackVersion(this.packType);
    }
}

