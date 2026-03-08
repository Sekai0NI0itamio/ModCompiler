package com.itamio.allowdisconnect.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class AllowDisconnectFabricMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register(this::onAfterInit);
    }

    private void onAfterInit(MinecraftClient client, Screen screen, int width, int height) {
        if (!isSupportedScreen(screen)) {
            return;
        }

        ButtonWidget button = new ButtonWidget(
            (width - 150) / 2,
            height - 40,
            150,
            20,
            Text.translatable("menu.disconnect"),
            ignored -> disconnect(client)
        );
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

    private static void disconnect(MinecraftClient client) {
        client.disconnect(new MultiplayerScreen(new TitleScreen()));
    }
}
