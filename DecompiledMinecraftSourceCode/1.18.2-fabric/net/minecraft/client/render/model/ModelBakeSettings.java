/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.render.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.AffineTransformation;

@Environment(value=EnvType.CLIENT)
public interface ModelBakeSettings {
    default public AffineTransformation getRotation() {
        return AffineTransformation.identity();
    }

    default public boolean isUvLocked() {
        return false;
    }
}

