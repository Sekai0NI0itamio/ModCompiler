package asd.itamio.multiplayerlikesingleplayer;

import asd.itamio.multiplayerlikesingleplayer.command.CommandMlspStats;
import asd.itamio.multiplayerlikesingleplayer.command.CommandReloadPermissions;
import asd.itamio.multiplayerlikesingleplayer.event.MLSPClientEvents;
import asd.itamio.multiplayerlikesingleplayer.event.MLSPCommonEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "multiplayerinsingleplayer",
   name = "MultiplayerInSingleplayer",
   version = "1.0.0",
   acceptableRemoteVersions = "*",
   acceptedMinecraftVersions = "[1.12.2]"
)
public class MultiplayerLikeSingleplayerMod {
   public static final String MOD_ID = "multiplayerinsingleplayer";
   public static final String NAME = "MultiplayerInSingleplayer";
   public static final String VERSION = "1.0.0";
   public static final Logger LOGGER = LogManager.getLogger("MultiplayerInSingleplayer");
   @Instance("multiplayerinsingleplayer")
   public static MultiplayerLikeSingleplayerMod INSTANCE;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      MinecraftForge.EVENT_BUS.register(new MLSPCommonEvents());
      if (event.getSide().isClient()) {
         MinecraftForge.EVENT_BUS.register(new MLSPClientEvents());
      }
   }

   @EventHandler
   public void serverStarting(FMLServerStartingEvent event) {
      event.registerServerCommand(new CommandReloadPermissions());
      event.registerServerCommand(new CommandMlspStats());
   }
}
