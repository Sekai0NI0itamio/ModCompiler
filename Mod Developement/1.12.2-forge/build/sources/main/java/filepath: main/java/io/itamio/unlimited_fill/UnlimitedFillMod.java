package io.itamio.unlimited_fill;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = UnlimitedFillMod.MOD_ID,
    name = UnlimitedFillMod.MOD_NAME,
    version = UnlimitedFillMod.VERSION,
    acceptedMinecraftVersions = "[1.12.2]"
)
public class UnlimitedFillMod {
    public static final String MOD_ID = "unlimited_fill";
    public static final String MOD_NAME = "Unlimited Fill Mod";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Unlimited Fill Mod loading...");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Register event handler for command interception
        MinecraftForge.EVENT_BUS.register(new FillCommandHandler());
        LOGGER.info("Unlimited Fill Mod initialized.");
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Register custom command for smart fill
        event.registerServerCommand(new CommandSmartFill());
    }
}