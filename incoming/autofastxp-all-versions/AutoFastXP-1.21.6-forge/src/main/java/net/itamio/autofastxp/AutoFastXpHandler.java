package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AutoFastXpHandler {
    private static final int THROW_INTERVAL = 3;
    private static int tickCounter = 0;

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (!mc.options.keyUse.isDown()) return;
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        boolean useMain = isXpBottle(main);
        boolean useOff = isXpBottle(off);
        if (!useMain && !useOff) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        InteractionHand hand = useMain ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        mc.gameMode.useItem(mc.player, hand);
    }

    private static boolean isXpBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}
