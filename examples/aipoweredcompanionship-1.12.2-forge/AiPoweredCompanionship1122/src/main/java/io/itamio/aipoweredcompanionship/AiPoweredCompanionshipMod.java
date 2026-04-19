package io.itamio.aipoweredcompanionship;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = AiPoweredCompanionshipMod.MOD_ID,
    name = AiPoweredCompanionshipMod.MOD_NAME,
    version = AiPoweredCompanionshipMod.MOD_VERSION,
    acceptedMinecraftVersions = "*",
    acceptableRemoteVersions = "*"
)
public final class AiPoweredCompanionshipMod {
    public static final String MOD_ID = "aipoweredcompanionship";
    public static final String MOD_NAME = "AI Powered Companionship";
    public static final String MOD_VERSION = "0.1.0";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    private static AiPoweredCompanionshipMod instance;
    private final CompanionBrainService brainService;
    private final CompanionManager manager;

    public AiPoweredCompanionshipMod() {
        instance = this;
        CompanionConfig.load(LOGGER);
        this.brainService = new CompanionBrainService(LOGGER);
        this.manager = new CompanionManager(brainService, LOGGER);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static AiPoweredCompanionshipMod getInstance() { return instance; }
    public CompanionManager getManager() { return manager; }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        manager.attachServer(event.getServer());
        event.registerServerCommand(new CompanionCommand(manager, brainService));
    }

    @EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        CompanionConfig.load(LOGGER);
    }

    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        manager.detachServer();
        brainService.shutdown();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        manager.tick();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            manager.onPlayerLogin((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            manager.onPlayerLogout((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (event.getPlayer() == null || event.getMessage() == null) return;
        manager.handleMessage(event.getPlayer(), event.getMessage());
    }
}
