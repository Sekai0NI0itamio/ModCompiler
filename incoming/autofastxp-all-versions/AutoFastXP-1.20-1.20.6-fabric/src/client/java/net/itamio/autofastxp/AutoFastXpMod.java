package net.itamio.autofastxp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class AutoFastXpMod implements ClientModInitializer {
    private static final int THROW_INTERVAL = 3;
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (client.currentScreen != null) return;
            if (!client.options.useKey.isPressed()) return;
            ItemStack main = client.player.getMainHandStack();
            ItemStack off = client.player.getOffHandStack();
            boolean useMain = isXpBottle(main);
            boolean useOff = isXpBottle(off);
            if (!useMain && !useOff) return;
            tickCounter++;
            if (tickCounter < THROW_INTERVAL) return;
            tickCounter = 0;
            Hand hand = useMain ? Hand.MAIN_HAND : Hand.OFF_HAND;
            client.interactionManager.interactItem(client.player, hand);
        });
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
