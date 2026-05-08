package asd.itamio.heartsystem;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public class HeartConfig {
    private final ModConfigSpec.IntValue startHearts;
    private final ModConfigSpec.IntValue maxHearts;
    private final ModConfigSpec.IntValue minHearts;

    public HeartConfig(IEventBus modBus, ModContainer container) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Heart System Configuration");
        startHearts = builder.comment("Hearts a new player starts with (1 heart = 2 HP)").defineInRange("startHearts", 10, 1, 100);
        maxHearts   = builder.comment("Maximum hearts a player can have").defineInRange("maxHearts", 20, 1, 100);
        minHearts   = builder.comment("Minimum hearts before permadeath").defineInRange("minHearts", 0, 0, 99);
        container.registerConfig(ModConfig.Type.SERVER, builder.build());
    }

    public int getStartHearts() { return startHearts.get(); }
    public int getMaxHearts()   { return maxHearts.get(); }
    public int getMinHearts()   { return minHearts.get(); }
}
