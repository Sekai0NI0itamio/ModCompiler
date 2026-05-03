package asd.itamio.keepinventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(KeepInventoryMod.MODID)
public class KeepInventoryMod {
    public static final String MODID = "keepinventory";
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20;

    public KeepInventoryMod(FMLJavaModLoadingContext context) {
        LevelEvent.Load.BUS.addListener(this::onLevelLoad);
        TickEvent.LevelTickEvent.Post.BUS.addListener(this::onLevelTick);
    }

    private void onLevelLoad(LevelEvent.Load event) {
        LevelAccessor la = event.getLevel();
        if (la instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel) la;
            sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
        }
    }

    private void onLevelTick(TickEvent.LevelTickEvent.Post event) {
        if (!(event.level() instanceof ServerLevel)) return;
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            ServerLevel sl = (ServerLevel) event.level();
            if (!sl.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                sl.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, sl.getServer());
            }
        }
    }
}
