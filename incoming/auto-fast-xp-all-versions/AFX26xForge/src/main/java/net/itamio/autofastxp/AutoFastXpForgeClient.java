package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.TickEvent;

public final class AutoFastXpForgeClient {
    private AutoFastXpForgeClient() {}

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) return;
        if (!mc.options.keyUse.isDown()) return;

        // Check main hand
        if (mc.player.getMainHandItem().getItem() == Items.EXPERIENCE_BOTTLE) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return;
        }

        // Check offhand
        if (mc.player.getOffhandItem().getItem() == Items.EXPERIENCE_BOTTLE) {
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
        }
    }
}
