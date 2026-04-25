/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public enum FogShape {
    SPHERE(0),
    CYLINDER(1);

    private final int id;

    private FogShape(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}

