package net.itamio.togglesprint.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

public final class ToggleSprintForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintForgeClient() {
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CONTROLLER.onClientTick(Minecraft.getInstance());
    }
}
