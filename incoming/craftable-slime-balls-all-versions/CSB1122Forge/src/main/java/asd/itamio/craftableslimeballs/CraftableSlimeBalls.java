package asd.itamio.craftableslimeballs;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

// In 1.12.2 Forge, recipes are loaded automatically from
// assets/craftableslimeballs/recipes/ — no Java registration needed.
@Mod(modid = CraftableSlimeBalls.MODID, name = "Craftable Slime Balls", version = "1.0.0",
     acceptedMinecraftVersions = "[1.12,1.12.2]")
public class CraftableSlimeBalls {
    public static final String MODID = "craftableslimeballs";

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Recipe is registered via JSON: assets/craftableslimeballs/recipes/slimeballs.json
    }
}
