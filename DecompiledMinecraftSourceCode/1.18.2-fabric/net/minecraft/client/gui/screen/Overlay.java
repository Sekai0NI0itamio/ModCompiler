/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.gui.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawableHelper;

@Environment(value=EnvType.CLIENT)
public abstract class Overlay
extends DrawableHelper
implements Drawable {
    public boolean pausesGame() {
        return true;
    }
}

