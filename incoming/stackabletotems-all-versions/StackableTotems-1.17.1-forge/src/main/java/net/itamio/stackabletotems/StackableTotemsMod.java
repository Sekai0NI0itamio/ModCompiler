package net.itamio.stackabletotems;

import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
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
}
