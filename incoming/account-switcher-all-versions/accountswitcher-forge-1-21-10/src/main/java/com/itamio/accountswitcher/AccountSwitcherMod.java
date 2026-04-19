package com.itamio.accountswitcher;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
// eventbus.api removed in 1.21.6+
import net.minecraftforge.client.event.ScreenEvent;

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

    // no @SubscribeEvent needed with register(this)
    public void onRenderScreen(ScreenEvent.Render.Post event) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
            net.minecraft.client.gui.Font font = mc.font;
            net.minecraft.client.gui.GuiGraphics gg = event.getGuiGraphics();
            String prefix = "Account: ";
            String account = currentAccount;
            gg.drawString(font, prefix, 10, 10, 0xFFFFFF);
            gg.drawString(font, account, 10 + font.width(prefix), 10, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }
    }

    public static Logger getLogger() { return LOGGER; }
    public static String getCurrentAccount() { return currentAccount; }
    public static void updateCurrentAccount(String account) { currentAccount = account; }
}
