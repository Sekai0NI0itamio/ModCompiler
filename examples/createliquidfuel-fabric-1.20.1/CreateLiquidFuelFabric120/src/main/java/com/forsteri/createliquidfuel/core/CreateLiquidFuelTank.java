package com.forsteri.createliquidfuel.core;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

public final class CreateLiquidFuelTank extends SingleVariantStorage<FluidVariant> {
    private final Runnable onChange;

    public CreateLiquidFuelTank(Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected long getCapacity(FluidVariant variant) {
        return FluidConstants.BUCKET;
    }

    @Override
    protected boolean canInsert(FluidVariant variant) {
        return BurnerStomachHandler.isValidFuel(variant.getFluid());
    }

    @Override
    protected boolean canExtract(FluidVariant variant) {
        return false;
    }

    public void consume(long droplets) {
        if (droplets <= 0 || amount <= 0) {
            return;
        }
        amount = Math.max(0L, amount - droplets);
        if (amount == 0L) {
            variant = FluidVariant.blank();
        }
        onChange.run();
    }

    public void clear() {
        amount = 0L;
        variant = FluidVariant.blank();
        onChange.run();
    }

    public void readFromNbt(NbtCompound tag) {
        if (!tag.contains("Variant", NbtElement.COMPOUND_TYPE)) {
            clear();
            return;
        }
        variant = FluidVariant.fromNbt(tag.getCompound("Variant"));
        amount = tag.getLong("Amount");
    }

    public NbtCompound writeToNbt(NbtCompound tag) {
        if (amount <= 0L || variant.isBlank()) {
            return tag;
        }
        tag.put("Variant", variant.toNbt());
        tag.putLong("Amount", amount);
        return tag;
    }
}
