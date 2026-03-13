package com.itamio.fpsdisplay.client;

import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Post;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class FpsDisplayOverlay {
   @SubscribeEvent
   public void onRenderOverlay(Post event) {
      if (event.type == ElementType.TEXT) {
         int fps = Minecraft.func_175610_ah();
         String fpsText = "FPS: " + fps;
         Color color;
         if (fps < 20) {
            color = Color.RED;
         } else if (fps < 60) {
            color = Color.YELLOW;
         } else if (fps < 120) {
            color = Color.GREEN;
         } else {
            color = new Color(128, 0, 128);
         }

         new ScaledResolution(Minecraft.func_71410_x());
         int x = 2;
         int y = 2;
         Minecraft mc = Minecraft.func_71410_x();
         mc.field_71466_p.func_78276_b(fpsText, x, y, color.getRGB());
      }
   }
}
