/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.world.gen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.world.gen.feature.JigsawFeature;
import net.minecraft.world.gen.feature.StructurePoolFeatureConfig;

public class VillageFeature
extends JigsawFeature {
    public VillageFeature(Codec<StructurePoolFeatureConfig> configCodec) {
        super(configCodec, 0, true, true, context -> true);
    }
}

