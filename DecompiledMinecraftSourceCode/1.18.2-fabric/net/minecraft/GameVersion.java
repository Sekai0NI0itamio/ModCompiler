/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft;

import net.minecraft.SaveVersion;

/**
 * The game version interface used by Minecraft, replacing the javabridge
 * one's occurrences in Minecraft code.
 */
public interface GameVersion
extends com.mojang.bridge.game.GameVersion {
    @Override
    @Deprecated
    default public int getWorldVersion() {
        return this.getSaveVersion().getId();
    }

    @Override
    @Deprecated
    default public String getSeriesId() {
        return this.getSaveVersion().getSeries();
    }

    /**
     * {@return the save version information for this game version}
     */
    public SaveVersion getSaveVersion();
}

