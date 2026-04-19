package com.alwaysrain;

import com.alwaysrain.config.AlwaysRainConfig;
import java.io.File;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = AlwaysRainMod.MOD_ID, name = AlwaysRainMod.NAME, version = AlwaysRainMod.VERSION)
@Mod.EventBusSubscriber(modid = AlwaysRainMod.MOD_ID)
public final class AlwaysRainMod {
    public static final String MOD_ID = "alwaysrain";
    public static final String NAME = "Always Rain";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    private static AlwaysRainConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new AlwaysRainConfig(new File(event.getModConfigurationDirectory(), "alwaysrain.txt"));
        config.load();
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world == null || event.world.isRemote) {
            return;
        }

        if (!event.world.provider.isSurfaceWorld() || config == null) {
            return;
        }

        config.reloadIfChanged();
        if ((event.world.getTotalWorldTime() % 20L) != 0L) {
            return;
        }

        enforceWeather(event.world, config.getWeatherMode());
    }

    private static void enforceWeather(World world, AlwaysRainConfig.WeatherMode weatherMode) {
        WorldInfo worldInfo = world.getWorldInfo();
        worldInfo.setCleanWeatherTime(0);
        worldInfo.setRainTime(Integer.MAX_VALUE);
        worldInfo.setRaining(true);
        worldInfo.setThunderTime(Integer.MAX_VALUE);
        worldInfo.setThundering(weatherMode == AlwaysRainConfig.WeatherMode.THUNDERSTORM);
    }
}
