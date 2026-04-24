package asd.itamio.veinminer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();
    public VeinMinerMod() {
        MinecraftForge.EVENT_BUS.register(new VeinMinerHandler());
        MinecraftForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
