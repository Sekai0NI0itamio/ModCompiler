package com.itamio.accountswitcher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("accountswitcher")
public class AccountSwitcherMod {
    public static final String MODID = "accountswitcher";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private ConfigHandler configHandler;
    private static String currentAccount = "Unknown";

    public AccountSwitcherMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(this);
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v2.0.0 initialized");
    }

    @SubscribeEvent
    public void onRenderScreen(ScreenEvent.Render.Post event) {
        try {
            if (!(event.getScreen() instanceof TitleScreen)) return;
            GuiGraphics gg = event.getGuiGraphics();
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
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
