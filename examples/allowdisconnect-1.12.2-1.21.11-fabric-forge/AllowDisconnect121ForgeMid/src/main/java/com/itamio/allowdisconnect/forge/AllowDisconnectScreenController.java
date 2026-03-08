package com.itamio.allowdisconnect.forge;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

public final class AllowDisconnectScreenController {
    private Screen trackedScreen;
    private boolean buttonInjected;

    public void onClientTick(Minecraft minecraft) {
        if (minecraft == null) {
            trackedScreen = null;
            buttonInjected = false;
            return;
        }

        Screen screen = minecraft.screen;
        if (screen != trackedScreen) {
            trackedScreen = screen;
            buttonInjected = false;
        }

        if (screen == null || buttonInjected || !isSupportedScreen(screen)) {
            return;
        }

        Button button = Button.builder(
            Component.translatable("menu.disconnect"),
            ignored -> disconnect(minecraft)
        ).bounds((screen.width - 150) / 2, screen.height - 40, 150, 20).build();
        if (tryAddButton(screen, button)) {
            buttonInjected = true;
        }
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

    private static boolean tryAddButton(Screen screen, Button button) {
        for (Method method : screen.getClass().getMethods()) {
            String name = method.getName();
            if (!name.equals("addRenderableWidget") && !name.equals("addButton") && !name.equals("addWidget")) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(button.getClass())) {
                continue;
            }
            try {
                method.invoke(screen, button);
                return true;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    private static void disconnect(Minecraft minecraft) {
        Screen nextScreen = new JoinMultiplayerScreen(new TitleScreen());
        try {
            Minecraft.class.getMethod("disconnect", Screen.class, boolean.class).invoke(minecraft, nextScreen, Boolean.FALSE);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Minecraft.class.getMethod("disconnect", Screen.class).invoke(minecraft, nextScreen);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        minecraft.setScreen(nextScreen);
    }
}
