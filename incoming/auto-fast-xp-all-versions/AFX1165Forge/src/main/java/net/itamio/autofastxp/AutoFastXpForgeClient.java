package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;

public final class AutoFastXpForgeClient {
    private AutoFastXpForgeClient() {}

    public static void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) return;
        if (!mc.options.keyUse.isDown()) return;

        // Check main hand
        if (mc.player.getMainHandItem().getItem() == Items.EXPERIENCE_BOTTLE) {
            mc.gameMode.useItem(mc.player, Hand.MAIN_HAND);
            return;
        }

        // Check offhand
        if (mc.player.getOffhandItem().getItem() == Items.EXPERIENCE_BOTTLE) {
            mc.gameMode.useItem(mc.player, Hand.OFF_HAND);
        }
    }
}
