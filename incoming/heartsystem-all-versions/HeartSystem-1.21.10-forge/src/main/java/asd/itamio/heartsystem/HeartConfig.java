package asd.itamio.heartsystem;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class HeartConfig {
    private final ForgeConfigSpec.IntValue startHearts;
    private final ForgeConfigSpec.IntValue maxHearts;
    private final ForgeConfigSpec.IntValue minHearts;

    public HeartConfig(FMLJavaModLoadingContext context) {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Heart System Configuration");
        startHearts = builder.comment("Hearts a new player starts with (1 heart = 2 HP)").defineInRange("startHearts", 10, 1, 100);
        maxHearts   = builder.comment("Maximum hearts a player can have").defineInRange("maxHearts", 20, 1, 100);
        minHearts   = builder.comment("Minimum hearts before permadeath").defineInRange("minHearts", 0, 0, 99);
        context.registerConfig(ModConfig.Type.SERVER, builder.build());
    }

    public int getStartHearts() { return startHearts.get(); }
    public int getMaxHearts()   { return maxHearts.get(); }
    public int getMinHearts()   { return minHearts.get(); }
}
