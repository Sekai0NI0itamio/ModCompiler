package net.itamio.togglesprint.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class ToggleSprintFabricMod implements ClientModInitializer {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(CONTROLLER::onClientTick);
    }
}
