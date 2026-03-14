package com.forsteri.createliquidfuel.mixin;

import com.forsteri.createliquidfuel.core.BurnerStomachHandler;
import com.forsteri.createliquidfuel.core.CreateLiquidFuelTank;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlazeBurnerBlockEntity.class, remap = false)
public abstract class MixinBlazeBurnerBlockEntity extends SmartBlockEntity implements BlazeBurnerFluidAccess {
    @Unique
    private CreateLiquidFuelTank createliquidfuel$tank;

    public MixinBlazeBurnerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public CreateLiquidFuelTank createliquidfuel$getTank() {
        if (createliquidfuel$tank == null) {
            createliquidfuel$tank = new CreateLiquidFuelTank(this::setChanged);
        }
        return createliquidfuel$tank;
    }

    @Inject(method = "addBehaviours", at = @At("TAIL"))
    private void createliquidfuel$addBehaviours(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        createliquidfuel$getTank();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void createliquidfuel$tick(CallbackInfo ci) {
        BurnerStomachHandler.tick((BlazeBurnerBlockEntity) (Object) this);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void createliquidfuel$read(NbtCompound compound, boolean clientPacket, CallbackInfo ci) {
        if (createliquidfuel$tank != null && compound.contains("Stomach", NbtElement.COMPOUND_TYPE)) {
            createliquidfuel$tank.readFromNbt(compound.getCompound("Stomach"));
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void createliquidfuel$write(NbtCompound compound, boolean clientPacket, CallbackInfo ci) {
        if (createliquidfuel$tank != null) {
            compound.put("Stomach", createliquidfuel$tank.writeToNbt(new NbtCompound()));
        }
    }
}
