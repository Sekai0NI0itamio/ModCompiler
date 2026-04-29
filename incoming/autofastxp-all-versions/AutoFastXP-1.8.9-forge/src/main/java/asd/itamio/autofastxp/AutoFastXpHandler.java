package asd.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class AutoFastXpHandler {
    private static final int THROW_INTERVAL = 3;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;
        if (!mc.gameSettings.keyBindUseItem.isKeyDown()) return;
        ItemStack main = mc.thePlayer.getHeldItem();
        if (!isXpBottle(main)) return;
        tickCounter++;
        if (tickCounter < THROW_INTERVAL) return;
        tickCounter = 0;
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, main);
    }

    private boolean isXpBottle(ItemStack stack) {
        if (stack == null) return false;
        return Item.getIdFromItem(stack.getItem()) == 384;
    }
}
