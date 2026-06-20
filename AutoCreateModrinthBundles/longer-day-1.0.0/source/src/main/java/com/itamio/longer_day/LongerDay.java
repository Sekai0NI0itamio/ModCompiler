package com.itamio.longer_day;

import com.itamio.longer_day.handler.ClientTimeHandler;
import com.itamio.longer_day.handler.TimeHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = LongerDay.MODID,
   name = LongerDay.NAME,
   version = LongerDay.VERSION,
   acceptedMinecraftVersions = "[1.12.2]"
)
public class LongerDay {
   public static final String MODID = "longer_day";
   public static final String NAME = "Longer Day";
   public static final String VERSION = "1.0.0";
   public static Logger logger;

   public static int dayLengthMinutes = 60;
   public static int nightLengthMinutes = 60;
   public static boolean syncNightWithDay = true;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      logger = event.getModLog();

      Configuration config = new Configuration(event.getSuggestedConfigurationFile());
      try {
         config.load();
         dayLengthMinutes = config.getInt("dayLengthMinutes", Configuration.CATEGORY_GENERAL, 60, 1, 1440,
               "Length of a Minecraft day in real minutes (vanilla is 10). Default: 60");
         syncNightWithDay = config.getBoolean("syncNightWithDay", Configuration.CATEGORY_GENERAL, true,
               "If true, night length automatically matches day length. Default: true");
         nightLengthMinutes = config.getInt("nightLengthMinutes", Configuration.CATEGORY_GENERAL, 60, 1, 1440,
               "Length of a Minecraft night in real minutes (vanilla is 10). Only used if syncNightWithDay is false. Default: 60");
      } finally {
         if (config.hasChanged()) {
            config.save();
         }
      }

      if (syncNightWithDay) {
         nightLengthMinutes = dayLengthMinutes;
      }

      logger.info("Longer Day config: day={}min night={}min sync={}", dayLengthMinutes, nightLengthMinutes, syncNightWithDay);
   }

   @EventHandler
   public void init(FMLInitializationEvent event) {
      MinecraftForge.EVENT_BUS.register(new TimeHandler());
      logger.info("Longer Day server handler registered");

      if (event.getSide() == Side.CLIENT) {
         MinecraftForge.EVENT_BUS.register(new ClientTimeHandler());
         logger.info("Longer Day client time handler registered");
      }
   }
}
