package asd.itamio.areadig;

import net.minecraft.enchantment.Enchantment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent.Register;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "areadig",
   name = "Area Dig",
   version = "1.0.0",
   acceptedMinecraftVersions = "[1.12.2]"
)
public class AreaDigMod {
   public static final String MODID = "areadig";
   public static final String NAME = "Area Dig";
   public static final String VERSION = "1.0.0";
   public static Logger logger;
   public static EnchantmentAreaDig AREA_DIG_ENCHANTMENT;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      logger = event.getModLog();
      MinecraftForge.EVENT_BUS.register(this);
      MinecraftForge.EVENT_BUS.register(new BlockBreakHandler());
      logger.info("Area Dig mod initialized");
   }

   @EventHandler
   public void init(FMLInitializationEvent event) {
      logger.info("Area Dig enchantment registered");
   }

   @SubscribeEvent
   public void registerEnchantments(Register<Enchantment> event) {
      AREA_DIG_ENCHANTMENT = new EnchantmentAreaDig();
      event.getRegistry().register(AREA_DIG_ENCHANTMENT);
      logger.info("Registered Area Dig enchantment");
   }
}
