package net.minecraft.client.renderer.fog.environment;

import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WaterFogEnvironment extends FogEnvironment {
    private static final int WATER_FOG_DISTANCE = 96;
    private static final float BIOME_FOG_TRANSITION_TIME = 5000.0F;
    private static int targetBiomeFog = -1;
    private static int previousBiomeFog = -1;
    private static long biomeChangedTime = -1L;

    @Override
    public void setupFog(FogData p_406618_, Entity p_409109_, BlockPos p_408827_, ClientLevel p_406286_, float p_408021_, DeltaTracker p_407294_) {
        p_406618_.environmentalStart = -8.0F;
        p_406618_.environmentalEnd = 96.0F;
        if (p_409109_ instanceof LocalPlayer localplayer) {
            p_406618_.environmentalEnd = p_406618_.environmentalEnd * Math.max(0.25F, localplayer.getWaterVision());
            if (p_406286_.getBiome(p_408827_).is(BiomeTags.HAS_CLOSER_WATER_FOG)) {
                p_406618_.environmentalEnd *= 0.85F;
            }
        }

        p_406618_.skyEnd = p_406618_.environmentalEnd;
        p_406618_.cloudEnd = p_406618_.environmentalEnd;
    }

    @Override
    public boolean isApplicable(@Nullable FogType p_409990_, Entity p_406892_) {
        return p_409990_ == FogType.WATER;
    }

    @Override
    public int getBaseColor(ClientLevel p_409944_, Camera p_407580_, int p_409596_, float p_409082_) {
        long i = Util.getMillis();
        int j = p_409944_.getBiome(p_407580_.getBlockPosition()).value().getWaterFogColor();
        if (biomeChangedTime < 0L) {
            targetBiomeFog = j;
            previousBiomeFog = j;
            biomeChangedTime = i;
        }

        float f = Mth.clamp((float)(i - biomeChangedTime) / 5000.0F, 0.0F, 1.0F);
        int k = ARGB.lerp(f, previousBiomeFog, targetBiomeFog);
        if (targetBiomeFog != j) {
            targetBiomeFog = j;
            previousBiomeFog = k;
            biomeChangedTime = i;
        }

        return k;
    }

    @Override
    public void onNotApplicable() {
        biomeChangedTime = -1L;
    }
}
