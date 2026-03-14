package com.forsteri.createliquidfuel.core;

import com.forsteri.createliquidfuel.util.MathUtil;
import com.forsteri.createliquidfuel.util.Triplet;
import com.mojang.datafixers.util.Pair;
import com.simibubi.create.AllTags.AllItemTags;
import java.util.Map;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class DrainableFuelLoader {
    public static final Identifier IDENTIFIER = new Identifier("createliquidfuel", "drainable_fuel_loader");
    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000L;

    private DrainableFuelLoader() {
    }

    public static void load() {
        for (Item item : Registries.ITEM) {
            ItemStack stack = new ItemStack(item);
            int burnTime = FuelRegistry.INSTANCE.get(item);
            if (burnTime <= 0) {
                continue;
            }

            Storage<FluidVariant> storage = FluidStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack));
            if (storage == null) {
                continue;
            }

            StorageView<FluidVariant> found = null;
            for (StorageView<FluidVariant> view : storage) {
                if (view.isResourceBlank() || view.getAmount() <= 0) {
                    continue;
                }
                if (found != null) {
                    found = null;
                    break;
                }
                found = view;
            }
            if (found == null) {
                continue;
            }

            Fluid fluid = found.getResource().getFluid();
            long amountDroplets = found.getAmount();
            int amountMb = (int) Math.max(1L, amountDroplets / DROPLETS_PER_MB);

            boolean superHeats = AllItemTags.BLAZE_BURNER_FUEL_SPECIAL.matches(stack);
            int gcd = MathUtil.gcd(burnTime, amountMb);
            int burnPer = superHeats ? 32 : burnTime / gcd;
            int amountPer = superHeats ? 10 : amountMb / gcd;

            Map<Fluid, Pair<Identifier, Triplet<Integer, Boolean, Integer>>> map = BurnerStomachHandler.LIQUID_BURNER_FUEL_MAP;
            Pair<Identifier, Triplet<Integer, Boolean, Integer>> existing = map.get(fluid);
            if (existing != null && LiquidBurnerFuelJsonLoader.IDENTIFIER.equals(existing.getFirst())) {
                continue;
            }

            map.put(fluid, Pair.of(IDENTIFIER, Triplet.of(burnPer, superHeats, amountPer)));
        }
    }
}
