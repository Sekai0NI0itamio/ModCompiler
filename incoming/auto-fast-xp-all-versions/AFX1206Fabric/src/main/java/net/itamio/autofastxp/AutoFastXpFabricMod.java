package net.itamio.autofastxp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

@Environment(EnvType.CLIENT)
public final class AutoFastXpFabricMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;
        if (!client.options.keyUse.isPressed()) return;

        // Check main hand
        if (client.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            return;
        }

        // Check offhand
        if (client.player.getOffHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
            client.interactionManager.interactItem(client.player, Hand.OFF_HAND);
        }
    }
}
