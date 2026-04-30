package com.itamio.accountswitcher;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "accountswitcher", name = "Account Switcher", version = "2.0.0")
public class AccountSwitcherMod {
    public static final String MODID = "accountswitcher";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private ConfigHandler configHandler;
    private static String currentAccount = "Unknown";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v2.0.0 initialized");
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        try {
            if (!(event.getGui() instanceof net.minecraft.client.gui.GuiMainMenu)) return;
            net.minecraft.client.gui.FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            String prefix = "Account: ";
            String account = currentAccount;
            fr.drawStringWithShadow(prefix, 10, 10, 0xFFFFFF);
            fr.drawStringWithShadow(account, 10 + fr.getStringWidth(prefix), 10, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }
    }

    public static Logger getLogger() { return LOGGER; }
    public static String getCurrentAccount() { return currentAccount; }
    public static void updateCurrentAccount(String account) { currentAccount = account; }
}
