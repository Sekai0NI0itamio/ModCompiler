package com.itamio.pingfix.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

public final class PingFixForgeClient {
    private static final PingFixController CONTROLLER = new PingFixController();

    private PingFixForgeClient() {
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CONTROLLER.onClientTick(Minecraft.getInstance());
    }
}
