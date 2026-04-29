package asd.itamio.autofastxp;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = AutoFastXpMod.MODID, name = "Auto Fast XP", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class AutoFastXpMod {
    public static final String MODID = "autofastxp";

    @EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new AutoFastXpHandler());
    }
}
