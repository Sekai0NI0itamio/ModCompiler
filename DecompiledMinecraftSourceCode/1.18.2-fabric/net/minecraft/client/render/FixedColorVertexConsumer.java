/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;

@Environment(value=EnvType.CLIENT)
public abstract class FixedColorVertexConsumer
implements VertexConsumer {
    protected boolean colorFixed;
    protected int fixedRed = 255;
    protected int fixedGreen = 255;
    protected int fixedBlue = 255;
    protected int fixedAlpha = 255;

    @Override
    public void fixedColor(int red, int green, int blue, int alpha) {
        this.fixedRed = red;
        this.fixedGreen = green;
        this.fixedBlue = blue;
        this.fixedAlpha = alpha;
        this.colorFixed = true;
    }

    @Override
    public void unfixColor() {
        this.colorFixed = false;
    }
}

