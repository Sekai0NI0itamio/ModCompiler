package asd.itamio.autoreplant;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "autoreplant",
   name = "Auto Replant",
   version = "1.0.0",
   acceptedMinecraftVersions = "[1.12.2]"
)
public class AutoReplantMod {
   public static final String MODID = "autoreplant";
   public static final String NAME = "Auto Replant";
   public static final String VERSION = "1.0.0";
   public static Logger logger;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      logger = event.getModLog();
      MinecraftForge.EVENT_BUS.register(new ReplantHandler());
      logger.info("Auto Replant initialized - crops and trees will auto-replant!");
   }
}
