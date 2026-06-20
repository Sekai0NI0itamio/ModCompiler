package immersive_aircraft;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * Adds a few simple shaped crafting recipes that assemble the aircraft items
 * from vanilla ingredients. Keeps the mod self-contained without depending on
 * a recipe mod like JEI.
 */
public class ModRecipes {
    public static void register() {
        // Biplane: iron + wool + propeller
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(Main.itemBiplane),
                " I ",
                "WPW",
                " I ",
                'I', "ingotIron",
                'W', new ItemStack(Blocks.WOOL, 1, 0),
                'P', Items.STICK
        ));

        // Airship: light wool + string
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(Main.itemAirship),
                "WWW",
                "WSW",
                "WWW",
                'W', new ItemStack(Blocks.WOOL, 1, 11),  // blue wool
                'S', Items.STRING
        ));

        // Cargo Airship: white wool + chest
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(Main.itemCargoAirship),
                "WWW",
                "WCW",
                "WWW",
                'W', new ItemStack(Blocks.WOOL, 1, 0),
                'C', Blocks.CHEST
        ));

        // Gyrodyne: smaller craft
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(Main.itemGyrodyne),
                "WIW",
                "WPW",
                "WIW",
                'I', "ingotIron",
                'W', new ItemStack(Blocks.WOOL, 1, 5),  // lime wool
                'P', Items.STICK
        ));

        // Quadrocopter: a redstone-driven device
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(Main.itemQuadrocopter),
                "WPW",
                "ISI",
                "WPW",
                'I', "ingotIron",
                'S', Items.LEATHER,
                'W', new ItemStack(Blocks.WOOL, 1, 14), // red wool
                'P', Items.REDSTONE
        ));
    }
}
