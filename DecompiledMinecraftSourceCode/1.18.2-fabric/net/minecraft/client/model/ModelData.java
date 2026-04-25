/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.client.model;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;

@Environment(value=EnvType.CLIENT)
public class ModelData {
    private ModelPartData data = new ModelPartData(ImmutableList.of(), ModelTransform.NONE);

    public ModelPartData getRoot() {
        return this.data;
    }
}

