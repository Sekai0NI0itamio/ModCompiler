package asd.itamio.veinminer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = VeinMinerMod.MODID, name = "Vein Miner", version = "1.0.0",
     acceptedMinecraftVersions = "[1.8.9]")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
