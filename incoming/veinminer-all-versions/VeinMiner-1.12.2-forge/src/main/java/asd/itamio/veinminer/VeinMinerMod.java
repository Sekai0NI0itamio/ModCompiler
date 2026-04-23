package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;
import java.io.File;

@Mod(modid = VeinMinerMod.MODID, name = VeinMinerMod.NAME, version = VeinMinerMod.VERSION,
     acceptedMinecraftVersions = "[1.12,1.12.2]")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static final String NAME = "Vein Miner";
    public static final String VERSION = "1.0.0";
    public static Logger logger;
    public static VeinMinerConfig config = new VeinMinerConfig();

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        File configFile = new File(event.getModConfigurationDirectory(), "veinminer.cfg");
        Configuration cfg = new Configuration(configFile);
        cfg.load();
        config.enableVeinMiner = cfg.getBoolean("Enable Vein Miner", "general", true, "");
        config.requireSneak = cfg.getBoolean("Require Sneak", "general", true, "");
        config.maxBlocks = cfg.getInt("Max Blocks", "general", 64, 1, 1000, "");
        config.consumeDurability = cfg.getBoolean("Consume Durability", "balance", true, "");
        config.consumeHunger = cfg.getBoolean("Consume Hunger", "balance", true, "");
        config.hungerMultiplier = cfg.getFloat("Hunger Multiplier", "balance", 1.0f, 0f, 10f, "");
        config.limitToCorrectTool = cfg.getBoolean("Limit To Correct Tool", "balance", true, "");
        config.dropAtOneLocation = cfg.getBoolean("Drop At One Location", "performance", true, "");
        config.disableSound = cfg.getBoolean("Disable Sound", "performance", false, "");
        config.cooldownTicks = cfg.getInt("Cooldown Ticks", "balance", 0, 0, 200, "");
        config.mineOres = cfg.getBoolean("Mine Ores", "blocks", true, "");
        config.mineLogs = cfg.getBoolean("Mine Logs", "blocks", true, "");
        config.mineStone = cfg.getBoolean("Mine Stone", "blocks", false, "");
        config.mineDirt = cfg.getBoolean("Mine Dirt", "blocks", false, "");
        config.mineGravel = cfg.getBoolean("Mine Gravel", "blocks", false, "");
        config.mineSand = cfg.getBoolean("Mine Sand", "blocks", false, "");
        config.mineClay = cfg.getBoolean("Mine Clay", "blocks", false, "");
        config.mineNetherrack = cfg.getBoolean("Mine Netherrack", "blocks", false, "");
        config.mineEndStone = cfg.getBoolean("Mine End Stone", "blocks", false, "");
        config.mineGlowstone = cfg.getBoolean("Mine Glowstone", "blocks", true, "");
        if (cfg.hasChanged()) cfg.save();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
