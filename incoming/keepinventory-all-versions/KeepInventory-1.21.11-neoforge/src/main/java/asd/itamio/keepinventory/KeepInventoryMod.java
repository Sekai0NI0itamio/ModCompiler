package asd.itamio.keepinventory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            MinecraftServer server = event.getServer();
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
                    level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
                }
            }
        }
    }
}
