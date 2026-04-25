/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.gui.hud.spectator;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.spectator.SpectatorMenu;

@Environment(value=EnvType.CLIENT)
public interface SpectatorMenuCloseCallback {
    public void close(SpectatorMenu var1);
}

