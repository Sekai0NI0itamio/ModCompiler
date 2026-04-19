package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.event.TickEvent;

public final class ToggleSprintNeoForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintNeoForgeClient() {}

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CONTROLLER.onClientTick(Minecraft.getInstance());
        }
    }
}
