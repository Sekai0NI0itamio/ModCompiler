package net.itamio.stackabletotems;

import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod(IEventBus modBus) {
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.world.item.Item.class
                    .getDeclaredField("f_41370_");
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
