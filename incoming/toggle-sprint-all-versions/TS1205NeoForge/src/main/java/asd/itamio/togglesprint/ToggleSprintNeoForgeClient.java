package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class ToggleSprintNeoForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintNeoForgeClient() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        CONTROLLER.onClientTick(Minecraft.getInstance());
    }
}
