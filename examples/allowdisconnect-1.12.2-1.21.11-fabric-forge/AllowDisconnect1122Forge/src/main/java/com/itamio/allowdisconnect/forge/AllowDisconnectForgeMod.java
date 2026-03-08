package com.itamio.allowdisconnect.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(
    modid = AllowDisconnectForgeMod.MOD_ID,
    name = "Allow Disconnect",
    version = "1.0.0",
    clientSideOnly = true
)
public final class AllowDisconnectForgeMod {
    public static final String MOD_ID = "allowdisconnect";
    private static final int DISCONNECT_BUTTON_ID = 0xAD01;

    public AllowDisconnectForgeMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen screen = event.getGui();
        if (!isSupportedScreen(screen)) {
            return;
        }

        event.getButtonList().add(new GuiButton(
            DISCONNECT_BUTTON_ID,
            (screen.width - 150) / 2,
            screen.height - 40,
            150,
            20,
            "Disconnect"
        ));
    }

    @SubscribeEvent
    public void onButtonPressed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton().id != DISCONNECT_BUTTON_ID) {
            return;
        }
        if (!isSupportedScreen(event.getGui())) {
            return;
        }

        disconnect(Minecraft.getMinecraft());
        event.setCanceled(true);
    }

    private static boolean isSupportedScreen(GuiScreen screen) {
        String name = screen.getClass().getSimpleName();
        switch (name) {
            case "GuiConnecting":
            case "GuiDownloadTerrain":
                return true;
            default:
                return false;
        }
    }

    private static void disconnect(Minecraft minecraft) {
        if (minecraft.world != null) {
            minecraft.world.sendQuittingDisconnectingPacket();
        }
        minecraft.loadWorld(null);
        minecraft.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
    }
}
