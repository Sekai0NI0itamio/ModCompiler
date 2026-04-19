package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraftforge.event.level.LevelEvent.Load;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class FireTickDisableProcedure {
   @SubscribeEvent
   public static void onWorldLoad(Load event) {
      execute(event, event.getLevel());
   }

   public static void execute(LevelAccessor world) {
      execute(null, world);
   }

   private static void execute(@Nullable Event event, LevelAccessor world) {
      ((BooleanValue)world.m_6106_().m_5470_().m_46170_(GameRules.f_46131_)).m_46246_(false, world.m_7654_());
   }
}
