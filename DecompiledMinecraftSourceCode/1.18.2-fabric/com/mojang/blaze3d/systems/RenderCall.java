/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package com.mojang.blaze3d.systems;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public interface RenderCall {
    public void execute();
}

