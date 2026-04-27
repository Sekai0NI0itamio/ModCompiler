package asd.itamio.veinminer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@Mod("veinminer")
public class VeinMinerMod {
    public static final String MODID = "veinminer";
    public static VeinMinerConfig config = new VeinMinerConfig();

    public VeinMinerMod(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(this::setup);
        modBus.addListener(VeinMinerKeyHandler::register);
    }

    private void setup(FMLCommonSetupEvent event) {
        NeoForge.EVENT_BUS.register(new VeinMinerHandler());
        NeoForge.EVENT_BUS.register(new VeinMinerKeyHandler());
    }
}
