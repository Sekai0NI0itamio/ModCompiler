package com.botfriend;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = BotFriendMod.MOD_ID,
    name = BotFriendMod.MOD_NAME,
    version = BotFriendMod.MOD_VERSION,
    acceptedMinecraftVersions = "*",
    acceptableRemoteVersions = "*"
)
public final class BotFriendMod {
    public static final String MOD_ID = "botfriend";
    public static final String MOD_NAME = "BotFriend";
    public static final String MOD_VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    private static BotFriendMod instance;
    private final FriendManager friendManager;
    private final FriendBrainService brainService;

    public BotFriendMod() {
        instance = this;
        BotFriendConfig.load(LOGGER);
        this.brainService = new FriendBrainService(LOGGER);
        this.friendManager = new FriendManager(brainService, LOGGER);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static BotFriendMod getInstance() {
        return instance;
    }

    public FriendManager getFriendManager() {
        return friendManager;
    }

    public FriendBrainService getBrainService() {
        return brainService;
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        friendManager.attachServer(event.getServer());
        event.registerServerCommand(new FriendCommand(friendManager, brainService));
    }

    @EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        BotFriendConfig.load(LOGGER);
        friendManager.queueRestoreAll();
    }

    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        friendManager.detachServer();
        brainService.shutdown();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        friendManager.tick();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            friendManager.onPlayerLogin((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            friendManager.onPlayerLogout((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (event.getPlayer() == null || event.getMessage() == null) {
            return;
        }
        friendManager.handleAddressedChat(event.getPlayer(), event.getMessage());
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        friendManager.onLivingAttack(event);
    }
}
