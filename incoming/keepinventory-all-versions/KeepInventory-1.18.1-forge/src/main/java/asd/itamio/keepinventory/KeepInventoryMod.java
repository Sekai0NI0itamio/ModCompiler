package asd.itamio.keepinventory;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.getWorld() instanceof Level)) return;
        Level level = (Level) event.getWorld();
        if (!level.isClientSide()) {
            GameRules rules = level.getGameRules();
            if (rules != null) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isClientSide()) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            GameRules rules = event.world.getGameRules();
            if (rules != null && !rules.getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }
}
