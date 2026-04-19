package asd.itamio.craftableslimeballs;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// Recipe is registered via JSON data pack:
// data/craftableslimeballs/recipes/slimeballs.json
// No Java registration needed for shaped crafting recipes in this era.
@Mod(CraftableSlimeBalls.MODID)
public class CraftableSlimeBalls {
    public static final String MODID = "craftableslimeballs";

    public CraftableSlimeBalls() {
        // No event bus listeners needed for a pure recipe mod.
    }
}
