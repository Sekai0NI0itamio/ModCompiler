package net.itamio.stackabletotems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.world.item.Item.class
                    .getDeclaredField("components");
                f.setAccessible(true);
                net.minecraft.core.component.DataComponentMap map =
                    (net.minecraft.core.component.DataComponentMap) f.get(Items.TOTEM_OF_UNDYING);
                net.minecraft.core.component.DataComponentMap newMap =
                    net.minecraft.core.component.DataComponentMap.builder()
                        .addAll(map)
                        .set(DataComponents.MAX_STACK_SIZE, 64)
                        .build();
                f.set(Items.TOTEM_OF_UNDYING, newMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @SubscribeEvent
    public void onUseTotem(LivingUseTotemEvent event) {
        net.minecraft.world.item.ItemStack totem = event.getTotem();
        if (totem.getCount() > 1) {
            totem.shrink(1);
            event.setCanceled(true);
        }
    }
}
