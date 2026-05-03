package asd.itamio.keepinventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
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
    public void onLevelLoad(LevelEvent.Load event) {
        LevelAccessor la = event.getLevel();
        if (la instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel) la;
            sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel)) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            ServerLevel sl = (ServerLevel) event.level;
            if (!sl.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
            }
        }
    }
}
