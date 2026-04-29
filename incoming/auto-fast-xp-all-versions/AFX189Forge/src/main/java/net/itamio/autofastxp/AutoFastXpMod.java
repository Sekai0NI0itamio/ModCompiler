package net.itamio.autofastxp;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

@Mod(modid = AutoFastXpMod.MODID, name = "Auto Fast XP", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class AutoFastXpMod {
    public static final String MODID = "autofastxp";

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) return;
        if (!mc.gameSettings.keyBindUseItem.isKeyDown()) return;

        // Check main hand (current item)
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held != null && held.getItem() == Items.experience_bottle) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
            return;
        }

        // Check offhand
        ItemStack offhand = mc.thePlayer.inventory.offHandInventory[0];
        if (offhand != null && offhand.getItem() == Items.experience_bottle) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, offhand);
        }
    }
}
