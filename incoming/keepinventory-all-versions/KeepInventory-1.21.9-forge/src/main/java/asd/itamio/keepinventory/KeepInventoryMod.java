package asd.itamio.keepinventory;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.gameevent.TickEvent;
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
        Level level = (Level) event.getLevel();
        if (level != null && !level.isClientSide()) {
            GameRules rules = level.getGameRules();
            if (rules != null) {
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            }
        }
    }

    private void onLevelTick(TickEvent.LevelTickEvent.Post event) {
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
