package net.itamio.stackabletotems;

import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
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
                Field f = ObfuscationReflectionHelper.findField(
                    net.minecraft.world.item.Item.class, "f_41370_");
                f.setAccessible(true);
                f.set(Items.TOTEM_OF_UNDYING, 64);
            } catch (Exception e) {
                try {
                    Field f = net.minecraft.world.item.Item.class
                        .getDeclaredField("maxStackSize");
                    f.setAccessible(true);
                    f.set(Items.TOTEM_OF_UNDYING, 64);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
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
