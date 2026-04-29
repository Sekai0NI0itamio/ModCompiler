package net.itamio.stackabletotems;

import net.fabricmc.api.ModInitializer;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import java.lang.reflect.Field;

public class StackableTotemsMod implements ModInitializer {
    @Override
    public void onInitialize() {
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
    }
}
