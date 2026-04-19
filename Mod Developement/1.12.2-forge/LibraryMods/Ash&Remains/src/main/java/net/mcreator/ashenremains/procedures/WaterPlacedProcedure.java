package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class WaterPlacedProcedure {
   @SubscribeEvent
   public static void onRightClickBlock(RightClickBlock event) {
      if (event.getHand() == event.getEntity().m_7655_()) {
         execute(event);
      }
   }

   public static void execute() {
      execute(null);
   }

   private static void execute(@Nullable Event event) {
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
   }
}
