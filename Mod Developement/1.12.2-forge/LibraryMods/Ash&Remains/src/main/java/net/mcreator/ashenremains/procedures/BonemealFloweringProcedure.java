package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class BonemealFloweringProcedure {
   @SubscribeEvent
   public static void onBonemeal(BonemealEvent event) {
      execute(event);
   }

   public static void execute() {
      execute(null);
   }

   private static void execute(@Nullable Event event) {
   }
}
