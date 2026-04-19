package net.itamio.togglesprint.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

@Environment(EnvType.CLIENT)
public final class ToggleSprintFabricMod implements ClientModInitializer {
   private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

   public void onInitializeClient() {
      ClientTickEvents.END_CLIENT_TICK.register(CONTROLLER::onClientTick);
   }
}
