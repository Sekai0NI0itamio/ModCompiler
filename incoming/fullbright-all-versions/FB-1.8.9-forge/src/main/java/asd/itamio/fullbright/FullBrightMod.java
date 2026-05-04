package asd.itamio.fullbright;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = FullBrightMod.MODID, name = "Working Full Bright", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class FullBrightMod {
    public static final String MODID = "fullbright";

    @EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new FullBrightHandler());
    }
}
