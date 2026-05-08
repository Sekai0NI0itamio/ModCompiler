package asd.itamio.heartsystem;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class HeartConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.IntValue START_HEARTS;
    private static final ForgeConfigSpec.IntValue MAX_HEARTS;
    private static final ForgeConfigSpec.IntValue MIN_HEARTS;
    private static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("Heart System Configuration");
        START_HEARTS = BUILDER.comment("Hearts a new player starts with (1 heart = 2 HP)").defineInRange("startHearts", 10, 1, 100);
        MAX_HEARTS   = BUILDER.comment("Maximum hearts a player can have").defineInRange("maxHearts", 20, 1, 100);
        MIN_HEARTS   = BUILDER.comment("Minimum hearts before permadeath").defineInRange("minHearts", 0, 0, 99);
        SPEC = BUILDER.build();
    }

    public HeartConfig() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    public int getStartHearts() { return START_HEARTS.get(); }
    public int getMaxHearts()   { return MAX_HEARTS.get(); }
    public int getMinHearts()   { return MIN_HEARTS.get(); }
}
