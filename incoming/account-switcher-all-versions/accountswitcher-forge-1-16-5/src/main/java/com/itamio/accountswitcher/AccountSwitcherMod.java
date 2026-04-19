package com.itamio.accountswitcher;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("accountswitcher")
public class AccountSwitcherMod {
    public static final String MODID = "accountswitcher";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private ConfigHandler configHandler;
    private static String currentAccount = "Unknown";

    public AccountSwitcherMod() {
        MinecraftForge.EVENT_BUS.register(this);
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v2.0.0 initialized");
    }

    @SubscribeEvent
    public void onRenderScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen)) return;
            net.minecraft.client.gui.FontRenderer font = mc.fontRenderer;
            com.mojang.blaze3d.matrix.MatrixStack ms = event.getMatrixStack();
            String prefix = "Account: ";
            String account = currentAccount;
            font.drawStringWithShadow(ms, prefix, 10, 10, 0xFFFFFF);
            font.drawStringWithShadow(ms, account, 10 + font.getStringWidth(prefix), 10, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }
    }

    public static Logger getLogger() { return LOGGER; }
    public static String getCurrentAccount() { return currentAccount; }
    public static void updateCurrentAccount(String account) { currentAccount = account; }
}
