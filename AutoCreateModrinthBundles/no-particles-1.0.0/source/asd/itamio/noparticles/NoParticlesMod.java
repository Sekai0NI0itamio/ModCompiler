package asd.itamio.noparticles;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;
import java.io.File;

@Mod(modid = NoParticlesMod.MODID, name = NoParticlesMod.NAME, version = NoParticlesMod.VERSION, clientSideOnly = true)
public class NoParticlesMod {
    public static final String MODID = "noparticles";
    public static final String NAME = "No Particles";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    public static NoParticlesConfig config;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new NoParticlesConfig(new File(event.getModConfigurationDirectory(), "noparticles.cfg"));
        logger.info("No Particles mod initialized - all particles disabled!");
    }
}
