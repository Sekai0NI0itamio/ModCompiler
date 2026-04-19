package asd.itamio.craftableslimeballs;

import net.fabricmc.api.ModInitializer;

// Recipe is registered via JSON data pack:
// data/craftableslimeballs/recipes/slimeballs.json
// Fabric loads data pack recipes automatically — no Java registration needed.
public class CraftableSlimeBalls implements ModInitializer {
    public static final String MODID = "craftableslimeballs";

    @Override
    public void onInitialize() {
        // Recipe loaded from data/craftableslimeballs/recipes/slimeballs.json
    }
}
