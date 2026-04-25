/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.render.block.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;

@Environment(value=EnvType.CLIENT)
public abstract class SkullBlockEntityModel
extends Model {
    public SkullBlockEntityModel() {
        super(RenderLayer::getEntityTranslucent);
    }

    public abstract void setHeadRotation(float var1, float var2, float var3);
}

