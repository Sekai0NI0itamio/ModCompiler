package asd.itamio.fullbright;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class FullBrightHandler {
    
    // Override the brightness calculation to always return maximum brightness
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onGetBrightness(EntityViewRenderEvent.FogDensity event) {
        if (!FullBrightConfig.enabled) return;
        
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            // Set gamma to maximum (15.0 is way beyond normal max of 1.0)
            mc.gameSettings.gammaSetting = 15.0F;
        }
    }
}
