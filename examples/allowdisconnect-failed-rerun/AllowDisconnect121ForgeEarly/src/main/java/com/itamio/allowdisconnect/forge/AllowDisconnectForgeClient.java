package com.itamio.allowdisconnect.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
    modid = AllowDisconnectForgeMod.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = Dist.CLIENT
)
public final class AllowDisconnectForgeClient {
    private static final AllowDisconnectScreenController CONTROLLER = new AllowDisconnectScreenController();

    private AllowDisconnectForgeClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        CONTROLLER.onClientTick(Minecraft.getInstance());
    }
}
