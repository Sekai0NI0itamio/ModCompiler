package com.alwaysrain;

import com.alwaysrain.config.AlwaysRainConfig;
import java.io.File;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "alwaysrain",
   name = "Always Rain",
   version = "1.0.0",
   acceptedMinecraftVersions = "[1.12.2]"
)
@EventBusSubscriber(
   modid = "alwaysrain"
)
public final class AlwaysRainMod {
   public static final String MOD_ID = "alwaysrain";
   public static final String NAME = "Always Rain";
   public static final String VERSION = "1.0.0";
   public static final Logger LOGGER = LogManager.getLogger("Always Rain");
   private static AlwaysRainConfig config;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      config = new AlwaysRainConfig(new File(event.getModConfigurationDirectory(), "alwaysrain.txt"));
      config.load();
   }

   @SubscribeEvent
   public static void onWorldTick(WorldTickEvent event) {
      if (event.phase == Phase.END && event.world != null && !event.world.field_72995_K) {
         if (event.world.field_73011_w.func_76569_d() && config != null) {
            config.reloadIfChanged();
            if (event.world.func_82737_E() % 20L == 0L) {
               enforceWeather(event.world, config.getWeatherMode());
            }
         }
      }
   }

   private static void enforceWeather(World world, AlwaysRainConfig.WeatherMode weatherMode) {
      WorldInfo worldInfo = world.func_72912_H();
      worldInfo.func_176142_i(0);
      worldInfo.func_76080_g(Integer.MAX_VALUE);
      worldInfo.func_76084_b(true);
      worldInfo.func_76090_f(Integer.MAX_VALUE);
      worldInfo.func_76069_a(weatherMode == AlwaysRainConfig.WeatherMode.THUNDERSTORM);
   }
}
