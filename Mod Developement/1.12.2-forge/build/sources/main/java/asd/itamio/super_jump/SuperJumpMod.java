package asd.itamio.super_jump;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = SuperJumpMod.MODID, name = SuperJumpMod.NAME, version = SuperJumpMod.VERSION, acceptableRemoteVersions = "*")
public class SuperJumpMod {
    public static final String MODID = "super_jump";
    public static final String NAME = "Super Jump";
    public static final String VERSION = "1.0.0";
    public static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new JumpHandler());
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandJumpHeight());
    }
}