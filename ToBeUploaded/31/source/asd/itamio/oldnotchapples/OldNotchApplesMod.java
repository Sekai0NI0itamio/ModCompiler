package asd.itamio.oldnotchapples;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.Logger;
import java.io.File;

@Mod(modid = OldNotchApplesMod.MODID, name = OldNotchApplesMod.NAME, version = OldNotchApplesMod.VERSION)
public class OldNotchApplesMod {
    public static final String MODID = "oldnotchapples";
    public static final String NAME = "Old Notch Apples";
    public static final String VERSION = "1.0.0";
    
    public static Logger logger;
    public static OldNotchApplesConfig config;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new OldNotchApplesConfig(new File(event.getModConfigurationDirectory(), "oldnotchapples.cfg"));
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register the crafting recipe for Enchanted Golden Apple
        // 8 gold blocks + 1 apple = 1 Enchanted Golden Apple
        ItemStack enchantedApple = new ItemStack(Items.GOLDEN_APPLE, 1, 1); // meta 1 = Enchanted Golden Apple
        
        GameRegistry.addShapedRecipe(
            enchantedApple.getItem().getRegistryName(),
            null, // resource group
            enchantedApple,
            "GGG",
            "GAG",
            "GGG",
            'G', Blocks.GOLD_BLOCK,
            'A', Items.APPLE
        );
        
        // Register event handler for custom effects
        MinecraftForge.EVENT_BUS.register(new OldNotchApplesHandler());
        
        logger.info("Old Notch Apples mod initialized - Enchanted Golden Apples are now craftable!");
    }
}
