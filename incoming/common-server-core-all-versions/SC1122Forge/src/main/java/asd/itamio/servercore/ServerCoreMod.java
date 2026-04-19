package asd.itamio.servercore;

import asd.itamio.servercore.command.CommandDelHome;
import asd.itamio.servercore.command.CommandHome;
import asd.itamio.servercore.command.CommandRtp;
import asd.itamio.servercore.command.CommandSetHome;
import asd.itamio.servercore.command.CommandTpa;
import asd.itamio.servercore.command.CommandTpacancel;
import asd.itamio.servercore.command.CommandTpaccept;
import asd.itamio.servercore.command.CommandTpacceptAll;
import asd.itamio.servercore.command.CommandTpadeny;
import asd.itamio.servercore.command.CommandTpadenyAll;
import asd.itamio.servercore.command.CommandTpahere;
import asd.itamio.servercore.config.ServerCoreConfig;
import asd.itamio.servercore.event.ServerCoreEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "servercore",
   name = "ServerCore",
   version = "1.0.0",
   acceptableRemoteVersions = "*",
   acceptedMinecraftVersions = "[1.12.2]"
)
public class ServerCoreMod {
   public static final String MOD_ID = "servercore";
   public static final String NAME = "ServerCore";
   public static final String VERSION = "1.0.0";
   public static final Logger LOGGER = LogManager.getLogger("ServerCore");
   @Instance("servercore")
   public static ServerCoreMod INSTANCE;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      ServerCoreConfig.load(event.getSuggestedConfigurationFile());
      MinecraftForge.EVENT_BUS.register(new ServerCoreEvents());
   }

   @EventHandler
   public void serverStarting(FMLServerStartingEvent event) {
      event.registerServerCommand(new CommandTpa());
      event.registerServerCommand(new CommandTpahere());
      event.registerServerCommand(new CommandTpacancel());
      event.registerServerCommand(new CommandTpaccept());
      event.registerServerCommand(new CommandTpacceptAll());
      event.registerServerCommand(new CommandTpadeny());
      event.registerServerCommand(new CommandTpadenyAll());
      event.registerServerCommand(new CommandSetHome());
      event.registerServerCommand(new CommandHome());
      event.registerServerCommand(new CommandDelHome());
      event.registerServerCommand(new CommandRtp());
   }
}
