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
            // 1.16.5 uses SRG field names — use reflection to avoid obfuscation issues
            Object screen = mc.getClass().getField("field_71462_r").get(mc);
            if (screen == null || !screen.getClass().getSimpleName().contains("TitleScreen")) return;
            Object font = mc.getClass().getField("field_71466_p").get(mc);
            com.mojang.blaze3d.matrix.MatrixStack ms = event.getMatrixStack();
            String prefix = "Account: ";
            String account = currentAccount;
            // drawString(MatrixStack, String, float, float, int)
            font.getClass().getMethod("drawString",
                com.mojang.blaze3d.matrix.MatrixStack.class, String.class, float.class, float.class, int.class)
                .invoke(font, ms, prefix, 10f, 10f, 0xFFFFFF);
            int prefixWidth = (int) font.getClass().getMethod("width", String.class).invoke(font, prefix);
            font.getClass().getMethod("drawString",
                com.mojang.blaze3d.matrix.MatrixStack.class, String.class, float.class, float.class, int.class)
                .invoke(font, ms, account, 10f + prefixWidth, 10f, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }
    }

    public static Logger getLogger() { return LOGGER; }
    public static String getCurrentAccount() { return currentAccount; }
    public static void updateCurrentAccount(String account) { currentAccount = account; }
}
