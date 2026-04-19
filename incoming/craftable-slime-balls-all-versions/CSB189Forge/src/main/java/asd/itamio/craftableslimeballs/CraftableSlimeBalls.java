package asd.itamio.craftableslimeballs;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid = CraftableSlimeBalls.MODID, name = "Craftable Slime Balls", version = "1.0.0",
     acceptedMinecraftVersions = "[1.8.9]")
public class CraftableSlimeBalls {
    public static final String MODID = "craftableslimeballs";

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // 8 sugar cane surrounding 1 water bucket -> 9 slime balls
        // In 1.8.9 the item is Items.reeds (sugar cane)
        GameRegistry.addRecipe(new ItemStack(Items.slime_ball, 9),
            "AAA",
            "ABA",
            "AAA",
            'A', new ItemStack(Items.reeds),
            'B', new ItemStack(Items.water_bucket)
        );
    }
}
