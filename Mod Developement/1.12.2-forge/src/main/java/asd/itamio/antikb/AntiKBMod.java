package asd.itamio.antikb;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;
import java.io.File;

@Mod(modid = AntiKBMod.MODID, name = AntiKBMod.NAME, version = AntiKBMod.VERSION, clientSideOnly = true)
@SideOnly(Side.CLIENT)
public class AntiKBMod {
    public static final String MODID = "antikb";
    public static final String NAME = "Anti KB";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    public static AntiKBConfig config;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new AntiKBConfig(new File(event.getModConfigurationDirectory(), "antikb.cfg"));
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new AntiKBHandler());
        logger.info("Anti KB mod initialized - All knockback cancelled!");
    }
}
