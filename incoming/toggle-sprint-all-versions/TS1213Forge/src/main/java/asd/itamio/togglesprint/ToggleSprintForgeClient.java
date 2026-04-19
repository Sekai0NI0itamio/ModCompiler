package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;

public final class ToggleSprintForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintForgeClient() {}

    public static void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.END) {
            CONTROLLER.onClientTick(Minecraft.getInstance());
        }
    }
}
