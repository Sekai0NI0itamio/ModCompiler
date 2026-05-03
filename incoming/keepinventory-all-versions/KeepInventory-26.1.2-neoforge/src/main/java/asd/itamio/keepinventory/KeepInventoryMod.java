package asd.itamio.keepinventory;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        Level level = (Level) event.getLevel();
        if (level != null && !level.isClientSide()) {
            GameRules rules = level.getGameRules();
            if (rules != null) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            GameRules rules = event.getLevel().getGameRules();
            if (rules != null && !rules.getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }
}
