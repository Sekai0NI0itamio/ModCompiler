package com.itamio.allowdisconnect.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.client.event.GuiScreenEvent;

public final class AllowDisconnectForgeClient {
    private AllowDisconnectForgeClient() {
    }

    public static void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
        Screen screen = event.getGui();
        if (!isSupportedScreen(screen)) {
            return;
        }

        Button button = new Button(
            (screen.width - 150) / 2,
            screen.height - 40,
            150,
            20,
            new TranslationTextComponent("menu.disconnect"),
            ignored -> disconnect(Minecraft.getInstance())
        );
        event.addWidget(button);
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

    private static void disconnect(Minecraft minecraft) {
        Screen nextScreen = new MultiplayerScreen(new MainMenuScreen());
        try {
            Minecraft.class.getMethod("disconnect", Screen.class).invoke(minecraft, nextScreen);
        } catch (ReflectiveOperationException ignored) {
            minecraft.setScreen(nextScreen);
        }
    }
}
