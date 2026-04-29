package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

@Mod(modid = AutoFastXpMod.MODID, name = "Auto Fast XP", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.12,1.12.2]")
public class AutoFastXpMod {
    public static final String MODID = "autofastxp";

    public AutoFastXpMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (!mc.gameSettings.keyBindUseItem.isKeyDown()) return;

        // Check main hand
        ItemStack mainHand = mc.player.getHeldItem(EnumHand.MAIN_HAND);
        if (!mainHand.isEmpty() && mainHand.getItem() == Items.EXPERIENCE_BOTTLE) {
            mc.playerController.sendUseItem(mc.player, mc.world, mainHand);
            return;
        }

        // Check offhand
        ItemStack offHand = mc.player.getHeldItem(EnumHand.OFF_HAND);
        if (!offHand.isEmpty() && offHand.getItem() == Items.EXPERIENCE_BOTTLE) {
            mc.playerController.sendUseItem(mc.player, mc.world, offHand);
        }
    }
}
