/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.world.biome.source.util;

import net.minecraft.util.math.Spline;
import net.minecraft.world.biome.source.util.VanillaTerrainParameters;

public class VanillaTerrainParametersCreator {
    public static VanillaTerrainParameters createSurfaceParameters(boolean amplified) {
        return VanillaTerrainParameters.createSurfaceParameters(amplified);
    }

    public static VanillaTerrainParameters createCavesParameters() {
        return new VanillaTerrainParameters(Spline.fixedFloatFunction(0.0f), Spline.fixedFloatFunction(0.0f), Spline.fixedFloatFunction(0.0f));
    }

    public static VanillaTerrainParameters createFloatingIslandsParameters() {
        return new VanillaTerrainParameters(Spline.fixedFloatFunction(0.0f), Spline.fixedFloatFunction(0.0f), Spline.fixedFloatFunction(0.0f));
    }

    public static VanillaTerrainParameters createNetherParameters() {
        return new VanillaTerrainParameters(Spline.fixedFloatFunction(0.0f), Spline.fixedFloatFunction(0.0f), Spline.fixedFloatFunction(0.0f));
    }

    public static VanillaTerrainParameters createEndParameters() {
        return new VanillaTerrainParameters(Spline.fixedFloatFunction(0.0f), Spline.fixedFloatFunction(1.0f), Spline.fixedFloatFunction(0.0f));
    }
}

