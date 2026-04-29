package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AutoFastXpHandler {
    private static final int THROW_INTERVAL = 3;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (!mc.options.keyUse.isDown()) return;
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        ItemStack active = isXpBottle(main) ? main : (isXpBottle(off) ? off : null);
        if (active == null) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        net.minecraft.world.InteractionHand hand = isXpBottle(main)
            ? net.minecraft.world.InteractionHand.MAIN_HAND
            : net.minecraft.world.InteractionHand.OFF_HAND;
        mc.gameMode.useItem(mc.player, hand);
    }

    private boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
