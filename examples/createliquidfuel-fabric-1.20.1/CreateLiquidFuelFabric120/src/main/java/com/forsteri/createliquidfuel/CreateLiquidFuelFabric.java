package com.forsteri.createliquidfuel;

import com.forsteri.createliquidfuel.core.DrainableFuelLoader;
import com.forsteri.createliquidfuel.core.LiquidBurnerFuelJsonLoader;
import com.forsteri.createliquidfuel.mixin.BlazeBurnerFluidAccess;
import com.simibubi.create.AllBlockEntityTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.resource.ResourceType;

public final class CreateLiquidFuelFabric implements ModInitializer {
    public static final String MOD_ID = "createliquidfuel";

    @Override
    public void onInitialize() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(LiquidBurnerFuelJsonLoader.INSTANCE);

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                DrainableFuelLoader.load();
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> DrainableFuelLoader.load());

        FluidStorage.SIDED.registerForBlockEntity(
            (blockEntity, direction) -> ((BlazeBurnerFluidAccess) blockEntity).createliquidfuel$getTank(),
            AllBlockEntityTypes.BLAZE_BURNER.get()
        );
    }
}
