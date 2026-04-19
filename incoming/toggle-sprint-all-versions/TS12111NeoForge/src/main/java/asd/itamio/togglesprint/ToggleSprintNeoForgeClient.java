package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "togglesprint", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ToggleSprintNeoForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintNeoForgeClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        CONTROLLER.onClientTick(Minecraft.getInstance());
    }
}
