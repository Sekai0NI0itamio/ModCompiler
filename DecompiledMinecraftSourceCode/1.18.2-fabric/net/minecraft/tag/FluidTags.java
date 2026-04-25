/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.tag;

import net.minecraft.fluid.Fluid;
import net.minecraft.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class FluidTags {
    public static final TagKey<Fluid> WATER = FluidTags.register("water");
    public static final TagKey<Fluid> LAVA = FluidTags.register("lava");

    private FluidTags() {
    }

    private static TagKey<Fluid> register(String id) {
        return TagKey.of(Registry.FLUID_KEY, new Identifier(id));
    }
}

