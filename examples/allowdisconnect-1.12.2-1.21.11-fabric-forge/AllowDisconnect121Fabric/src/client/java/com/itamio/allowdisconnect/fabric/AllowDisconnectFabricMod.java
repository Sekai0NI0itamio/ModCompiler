package com.itamio.allowdisconnect.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

public final class AllowDisconnectFabricMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register(this::onAfterInit);
    }

    private void onAfterInit(Minecraft client, Screen screen, int width, int height) {
        if (!isSupportedScreen(screen)) {
            return;
        }

        Button button = Button.builder(
            Component.translatable("menu.disconnect"),
            ignored -> disconnect(client)
        ).bounds((width - 150) / 2, height - 40, 150, 20).build();
        Screens.getButtons(screen).add(button);
    }

    private static boolean isSupportedScreen(Screen screen) {
        String name = screen.getClass().getSimpleName();
        switch (name) {
            case "ConnectingScreen":
            case "ConnectScreen":
            case "DownloadTerrainScreen":
            case "DownloadingTerrainScreen":
            case "ReceivingLevelScreen":
                return true;
            default:
                return false;
        }
    }

    private static void disconnect(Minecraft client) {
        client.disconnect(new JoinMultiplayerScreen(new TitleScreen()));
    }
}
