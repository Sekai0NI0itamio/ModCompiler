package com.itamio.trainlize_furnance_minecart;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "trainlize_furnance_minecart",
   name = "Trainlize Furnance Minecart",
   version = "1.0.0",
   acceptedMinecraftVersions = "[1.12.2]"
)
public class TrainlizeFurnanceMinecart {
   public static final String MODID = "trainlize_furnance_minecart";
   public static final String NAME = "Trainlize Furnance Minecart";
   public static final String VERSION = "1.0.0";
   public static Logger logger;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      logger = event.getModLog();
      logger.info("Trainlize Furnance Minecart preInit");
   }
}
