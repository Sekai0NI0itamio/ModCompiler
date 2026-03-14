package com.forsteri.createliquidfuel.core;

import com.forsteri.createliquidfuel.mixin.BlazeBurnerAccessor;
import com.forsteri.createliquidfuel.mixin.BlazeBurnerFluidAccess;
import com.forsteri.createliquidfuel.util.Triplet;
import com.mojang.datafixers.util.Pair;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.Identifier;

public final class BurnerStomachHandler {
    public static final Map<Fluid, Pair<Identifier, Triplet<Integer, Boolean, Integer>>> LIQUID_BURNER_FUEL_MAP = new HashMap<>();
    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000L;

    private BurnerStomachHandler() {
    }

    public static boolean isValidFuel(Fluid fluid) {
        return LIQUID_BURNER_FUEL_MAP.containsKey(fluid);
    }

    public static void tick(BlazeBurnerBlockEntity entity) {
        if (!(entity instanceof BlazeBurnerFluidAccess access)) {
            return;
        }

        CreateLiquidFuelTank tank = access.createliquidfuel$getTank();
        FluidVariant variant = tank.getResource();
        if (variant == null || variant.isBlank() || tank.getAmount() <= 0L) {
            return;
        }

        Pair<Identifier, Triplet<Integer, Boolean, Integer>> entry = LIQUID_BURNER_FUEL_MAP.get(variant.getFluid());
        if (entry == null) {
            return;
        }

        Triplet<Integer, Boolean, Integer> properties = entry.getSecond();
        if (properties == null) {
            return;
        }

        int burnTime = properties.getFirst();
        boolean superHeats = properties.getSecond();
        int mbConsumed = properties.getThird();
        long dropletsToConsume = mbConsumed * DROPLETS_PER_MB;

        if (tank.getAmount() < dropletsToConsume) {
            tank.clear();
            return;
        }

        BlazeBurnerAccessor burnerAccessor = (BlazeBurnerAccessor) entity;
        burnerAccessor.createliquidfuel$invokeSetBlockHeat(superHeats ? HeatLevel.SEETHING : HeatLevel.FADING);

        int newBurnTime = burnerAccessor.createliquidfuel$getRemainingBurnTime() + burnTime;
        if (newBurnTime <= 10000) {
            burnerAccessor.createliquidfuel$setRemainingBurnTime(newBurnTime);
            tank.consume(dropletsToConsume);
        }
    }
}
