package net.itamio.autofastxp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

public class AutoFastXpMod implements ClientModInitializer {
    private static final int THROW_INTERVAL = 3;
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            if (client.screen != null) return;
            if (!client.options.keyUse.isDown()) return;
            ItemStack main = client.player.getMainHandItem();
            ItemStack off = client.player.getOffhandItem();
            boolean useMain = isXpBottle(main);
            boolean useOff = isXpBottle(off);
            if (!useMain && !useOff) return;
            tickCounter++;
            if (tickCounter < THROW_INTERVAL) return;
            tickCounter = 0;
            InteractionHand hand = useMain ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            client.gameMode.useItem(client.player, hand);
        });
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
