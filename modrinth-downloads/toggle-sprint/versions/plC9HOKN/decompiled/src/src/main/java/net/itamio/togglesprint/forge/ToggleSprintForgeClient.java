package net.itamio.togglesprint.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent.Post;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "togglesprint",
   value = {Dist.CLIENT}
)
public final class ToggleSprintForgeClient {
   private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

   private ToggleSprintForgeClient() {
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      CONTROLLER.onClientTick(Minecraft.getInstance());
   }
}
