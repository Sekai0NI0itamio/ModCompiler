package net.itamio.stackabletotems;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Items;
import java.lang.reflect.Field;

public class StackableTotemsMod implements ModInitializer {
    @Override
    public void onInitialize() {
        try {
            Field f = net.minecraft.item.Item.class
                .getDeclaredField("maxCount");
            f.setAccessible(true);
            f.set(Items.TOTEM_OF_UNDYING, 64);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
