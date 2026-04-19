package com.loadmyworldproperly;

import com.loadmyworldproperly.client.SingleplayerWorldLoadFixer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = LoadMyWorldProperlyMod.MOD_ID,
    name = LoadMyWorldProperlyMod.NAME,
    version = LoadMyWorldProperlyMod.VERSION,
    clientSideOnly = true
)
@Mod.EventBusSubscriber(modid = LoadMyWorldProperlyMod.MOD_ID)
public final class LoadMyWorldProperlyMod {
    public static final String MOD_ID = "loadmyworldproperly";
    public static final String NAME = "Load My World PROPERLY";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    private static final SingleplayerWorldLoadFixer FIXER = new SingleplayerWorldLoadFixer();

    private LoadMyWorldProperlyMod() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FIXER.onClientTick();
        }
    }
}
