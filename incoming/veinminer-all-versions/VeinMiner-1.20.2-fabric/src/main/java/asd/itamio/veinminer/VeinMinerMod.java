package asd.itamio.veinminer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VeinMinerMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("veinminer");
    public static VeinMinerConfig config = new VeinMinerConfig();

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register(new VeinMinerHandler());
    }
}
