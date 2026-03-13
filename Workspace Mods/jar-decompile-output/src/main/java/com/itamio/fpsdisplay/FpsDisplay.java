package com.itamio.fpsdisplay;

import com.itamio.fpsdisplay.client.FpsDisplayOverlay;
import com.itamio.fpsdisplay.config.FpsDisplayConfig;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Text;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "fpsdisplay",
   name = "FPS Display",
   version = "1.0.0",
   acceptedMinecraftVersions = "[1.8.9]"
)
public class FpsDisplay {
   public static final String MODID = "fpsdisplay";
   public static final String NAME = "FPS Display";
   public static final String VERSION = "1.0.0";
   public static final Logger LOGGER = LogManager.getLogger("fpsdisplay");
   public static FpsDisplayConfig config;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      config = new FpsDisplayConfig();
      LOGGER.info("==========================================");
      LOGGER.info("           FPS DISPLAY v1.0.0");
      LOGGER.info("==========================================");
      LOGGER.info("  Author: itamio");
      LOGGER.info("  Status: Successfully loaded!");
      LOGGER.info("  Config: " + (config.isEnabled() ? "Enabled" : "Disabled"));
      LOGGER.info("==========================================");
   }

   @EventHandler
   public void init(FMLInitializationEvent event) {
      MinecraftForge.EVENT_BUS.register(this);
      MinecraftForge.EVENT_BUS.register(new FpsDisplayOverlay());
   }

   @SubscribeEvent
   public void onRenderOverlay(Text event) {
      if (config.isEnabled()) {
         ;
      }
   }
}
