package asd.itamio.heartsystem;

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HeartSystemMod.MOD_ID)
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    public HeartSystemMod(FMLJavaModLoadingContext context) {
        config = new HeartConfig(context);
        HeartEventHandler handler = new HeartEventHandler();
        PlayerEvent.LoadFromFile.BUS.addListener(handler::onPlayerLoad);
        PlayerEvent.SaveToFile.BUS.addListener(handler::onPlayerSave);
        PlayerEvent.Clone.BUS.addListener(handler::onPlayerClone);
        LivingDeathEvent.BUS.addListener(handler::onLivingDeath);
        logger.info("[HeartSystem] Heart-based permadeath system active.");
    }
}
