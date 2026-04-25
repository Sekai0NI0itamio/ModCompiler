/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.world.gen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.gen.feature.FeatureConfig;

public class ShipwreckFeatureConfig
implements FeatureConfig {
    public static final Codec<ShipwreckFeatureConfig> CODEC = ((MapCodec)Codec.BOOL.fieldOf("is_beached")).orElse(false).xmap(ShipwreckFeatureConfig::new, config -> config.isBeached).codec();
    public final boolean isBeached;

    public ShipwreckFeatureConfig(boolean isBeached) {
        this.isBeached = isBeached;
    }
}

