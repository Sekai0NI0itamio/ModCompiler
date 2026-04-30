package com.itamio.accountswitcher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("accountswitcher")
public class AccountSwitcherMod {
    public static final String MODID = "accountswitcher";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private ConfigHandler configHandler;
    private static String currentAccount = "Unknown";

    public AccountSwitcherMod() {
        ScreenEvent.Render.Post.BUS.addListener(this::onRenderScreen);
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v2.0.0 initialized");
    }

    private void onRenderScreen(ScreenEvent.Render.Post event) {
        try {
            if (!(event.getScreen() instanceof TitleScreen)) return;
            GuiGraphicsExtractor gg = event.getGuiGraphics();
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            String prefix = "Account: ";
            String account = currentAccount;
            gg.text(font, prefix, 10, 10, 0xFFFFFF);
            gg.text(font, account, 10 + font.width(prefix), 10, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }
    }

    public static Logger getLogger() { return LOGGER; }
    public static String getCurrentAccount() { return currentAccount; }
    public static void updateCurrentAccount(String account) { currentAccount = account; }
}
