package com.daycounter.client;

import com.daycounter.config.DayCounterConfig;
import com.daycounter.util.DayCounterFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Text;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class DayCounterClientHandler {
   private final DayCounterConfig config;

   public DayCounterClientHandler(DayCounterConfig config) {
      this.config = config;
   }

   @SubscribeEvent
   public void onRenderOverlay(Text event) {
      Minecraft minecraft = Minecraft.func_71410_x();
      if (minecraft != null && minecraft.field_71439_g != null && minecraft.field_71441_e != null && !minecraft.field_71474_y.field_74319_N) {
         this.config.reloadIfChanged();
         String text = DayCounterFormatter.format(minecraft.field_71441_e.func_82737_E(), minecraft.field_71441_e.func_72820_D(), this.config.getDisplayMode());
         if (!text.isEmpty()) {
            FontRenderer fontRenderer = minecraft.field_71466_p;
            ScaledResolution resolution = event.getResolution();
            int width = fontRenderer.func_78256_a(text);
            int x = this.config.getAnchor().resolveX(resolution.func_78326_a(), width, this.config.getOffsetX());
            int y = this.config.getAnchor().resolveY(resolution.func_78328_b(), fontRenderer.field_78288_b, this.config.getOffsetY());
            fontRenderer.func_175063_a(text, x, y, 16777215);
         }
      }
   }
}
