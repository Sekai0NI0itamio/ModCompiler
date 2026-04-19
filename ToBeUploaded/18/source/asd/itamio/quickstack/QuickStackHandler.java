package asd.itamio.quickstack;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class QuickStackHandler {
    
    public static void performQuickStack() {
        if (!QuickStackMod.config.enableQuickStack) {
            return;
        }
        
        Minecraft mc = Minecraft.getMinecraft();
        
        if (mc.player == null || mc.player.world == null) {
            return;
        }
        
        // Send packet to server to perform quick stack
        QuickStackMod.network.sendToServer(new QuickStackPacket());
    }
}
