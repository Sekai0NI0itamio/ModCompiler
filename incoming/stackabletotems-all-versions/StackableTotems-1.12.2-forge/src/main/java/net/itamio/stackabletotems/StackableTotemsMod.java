package net.itamio.stackabletotems;

import net.minecraft.init.Items;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import java.lang.reflect.Field;

@Mod(modid = "stackabletotems", name = "Stackable Totems of Undying", version = "1.0.0",
     acceptedMinecraftVersions = "[1.12,1.12.2]")
public class StackableTotemsMod {
    @EventHandler
    public void init(FMLInitializationEvent event) {
        try {
            Field f = net.minecraft.item.Item.class
                .getDeclaredField("field_77777_bU");
            f.setAccessible(true);
            f.set(Items.TOTEM_OF_UNDYING, 64);
        } catch (Exception e) {
            try {
                Field f = net.minecraft.item.Item.class
                    .getDeclaredField("maxStackSize");
                f.setAccessible(true);
                f.set(Items.TOTEM_OF_UNDYING, 64);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
