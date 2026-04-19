package net.mcreator.ashenremains.init;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Category;
import net.minecraft.world.level.GameRules.Key;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   bus = Bus.MOD
)
public class AshenremainsModGameRules {
   public static final Key<BooleanValue> DOPLANTGROWTH = GameRules.m_46189_("doPlantGrowth", Category.UPDATES, BooleanValue.m_46250_(true));
   public static final Key<BooleanValue> DOFIRESPREAD = GameRules.m_46189_("doFireSpread", Category.PLAYER, BooleanValue.m_46250_(true));
   public static final Key<BooleanValue> MOB_FIRES = GameRules.m_46189_("mobFires", Category.PLAYER, BooleanValue.m_46250_(true));
}
