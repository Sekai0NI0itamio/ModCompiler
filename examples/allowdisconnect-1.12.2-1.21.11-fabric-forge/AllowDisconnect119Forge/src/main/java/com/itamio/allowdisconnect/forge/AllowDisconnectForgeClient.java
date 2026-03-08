package com.itamio.allowdisconnect.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;

public final class AllowDisconnectForgeClient {
    private AllowDisconnectForgeClient() {
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isSupportedScreen(screen)) {
            return;
        }

        Button button = new Button(
            (screen.width - 150) / 2,
            screen.height - 40,
            150,
            20,
            Component.translatable("menu.disconnect"),
            ignored -> disconnect(Minecraft.getInstance())
        );
        event.addListener(button);
    }

    private static boolean isSupportedScreen(Screen screen) {
        String name = screen.getClass().getSimpleName();
        switch (name) {
            case "ConnectingScreen":
            case "ConnectScreen":
            case "DownloadTerrainScreen":
            case "DownloadingTerrainScreen":
            case "ReceivingLevelScreen":
            case "LevelLoadingScreen":
                return true;
            default:
                return false;
        }
    }

    private static void disconnect(Minecraft minecraft) {
        Screen nextScreen = new JoinMultiplayerScreen(new TitleScreen());
        try {
            Minecraft.class.getMethod("disconnect", Screen.class).invoke(minecraft, nextScreen);
        } catch (ReflectiveOperationException ignored) {
            minecraft.setScreen(nextScreen);
        }
    }
}
