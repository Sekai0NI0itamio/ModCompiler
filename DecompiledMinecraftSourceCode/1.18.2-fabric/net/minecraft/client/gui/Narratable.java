/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;

@Environment(value=EnvType.CLIENT)
public interface Narratable {
    public void appendNarrations(NarrationMessageBuilder var1);
}

