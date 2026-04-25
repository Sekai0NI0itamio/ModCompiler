/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.block.entity;

import net.minecraft.block.entity.BannerPattern;
import net.minecraft.block.entity.BannerPatterns;
import net.minecraft.registry.Registerable;

public interface OneTwentyOneBannerPatterns {
    public static void bootstrap(Registerable<BannerPattern> bannerPatternRegisterable) {
        BannerPatterns.register(bannerPatternRegisterable, BannerPatterns.FLOW);
        BannerPatterns.register(bannerPatternRegisterable, BannerPatterns.GUSTER);
    }
}

