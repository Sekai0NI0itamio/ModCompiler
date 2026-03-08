package net.itamio.togglesprint.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ToggleSprintForgeMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ToggleSprintForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintForgeClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CONTROLLER.onClientTick(Minecraft.getInstance());
    }
}
