#!/usr/bin/env python3
"""
Generates the Account Switcher bundle — all versions across Forge, Fabric, NeoForge.
Targets: All versions supported by version-manifest.json

Run: python3 scripts/generate_account_switcher_bundle.py [--failed-only]
"""
import argparse, json, shutil, zipfile
from pathlib import Path

ROOT   = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "incoming" / "account-switcher-all-versions"

MOD_ID      = "accountswitcher"
MOD_NAME    = "Account Switcher"
MOD_VERSION = "2.0.0"
GROUP       = "com.itamio.accountswitcher"
DESCRIPTION = "A mod that allows automatic switching between offline Minecraft accounts based on configuration file changes."
AUTHORS     = "itamio"
LICENSE     = "MIT"
HOMEPAGE    = "https://modrinth.com/mod/account-switcher"
ENTRYPOINT  = f"{GROUP}.AccountSwitcherMod"
PKG         = GROUP.replace('.', '/')

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def mod_txt(runtime_side: str = "client") -> str:
    return (
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={GROUP}\nentrypoint_class={ENTRYPOINT}\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side={runtime_side}\n"
    )

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"
# ============================================================
# SHARED: ConfigHandler and SessionManager (identical across all versions)
# ============================================================

CONFIG_HANDLER = """\
package com.itamio.accountswitcher;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfigHandler {
    private static final String CONFIG_FILE = "account.txt";
    private static final String CONFIG_DIR = "config";
    private Path configPath;
    private String currentAccount = "Unknown";
    private long lastModified = 0L;
    private ScheduledExecutorService executor;

    public ConfigHandler() {
        initializeConfigFile();
    }

    private void initializeConfigFile() {
        try {
            File configDir = new File("config");
            if (!configDir.exists()) configDir.mkdirs();
            configPath = Paths.get("config", "account.txt");
            if (!Files.exists(configPath)) {
                createConfigWithCurrentAccount();
            } else {
                currentAccount = readAccountFromFile();
            }
            AccountSwitcherMod.getLogger().info("Config initialized. Current account: " + currentAccount);
        } catch (IOException e) {
            AccountSwitcherMod.getLogger().error("Failed to initialize config file", e);
        }
    }

    private void createConfigWithCurrentAccount() throws IOException {
        String name = SessionManager.getCurrentAccount();
        if (name == null || name.isEmpty() || name.equals("Unknown")) name = "Player";
        try (BufferedWriter w = Files.newBufferedWriter(configPath)) {
            w.write("# Account Switcher Configuration"); w.newLine();
            w.write("# Change the account name below to switch accounts"); w.newLine();
            w.write("account=" + name); w.newLine();
        }
        currentAccount = name;
        AccountSwitcherMod.getLogger().info("Created config file with account: " + name);
    }

    private String readAccountFromFile() {
        try {
            if (Files.exists(configPath)) {
                for (String line : Files.readAllLines(configPath)) {
                    line = line.trim();
                    if (line.startsWith("account=")) return line.substring(8).trim();
                }
            }
        } catch (IOException e) {
            AccountSwitcherMod.getLogger().error("Failed to read config file", e);
        }
        return "Unknown";
    }

    public void startMonitoring() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            if (!currentAccount.equals("Unknown")) {
                AccountSwitcherMod.getLogger().info("Performing initial account switch to: " + currentAccount);
                int retries = 0;
                while (retries < 10 && !SessionManager.isMinecraftReady()) {
                    try { Thread.sleep(1000L); retries++; }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
                switchAccount(currentAccount);
                AccountSwitcherMod.updateCurrentAccount(currentAccount);
            }
        }, 3L, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(this::checkForChanges, 5L, 1L, TimeUnit.SECONDS);
        AccountSwitcherMod.getLogger().info("Started monitoring config file for changes");
    }

    private void checkForChanges() {
        try {
            if (Files.exists(configPath)) {
                long modified = Files.getLastModifiedTime(configPath).toMillis();
                if (modified > lastModified) {
                    lastModified = modified;
                    String newAccount = readAccountFromFile();
                    if (!newAccount.equals(currentAccount)) {
                        AccountSwitcherMod.getLogger().info("Account change detected: " + currentAccount + " -> " + newAccount);
                        currentAccount = newAccount;
                        switchAccount(newAccount);
                        AccountSwitcherMod.updateCurrentAccount(newAccount);
                    }
                }
            }
        } catch (IOException e) {
            AccountSwitcherMod.getLogger().error("Error checking config changes", e);
        }
    }

    private void switchAccount(String accountName) {
        try {
            if (!SessionManager.isMinecraftReady()) {
                AccountSwitcherMod.getLogger().warn("Minecraft not ready, skipping account switch");
                return;
            }
            boolean success = SessionManager.switchToAccount(accountName);
            if (success) {
                currentAccount = accountName;
                AccountSwitcherMod.getLogger().info("Account switch completed: " + accountName);
            } else {
                AccountSwitcherMod.getLogger().error("Account switch failed");
            }
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Failed to switch account", e);
        }
    }

    public String getCurrentAccount() { return currentAccount; }

    public void stopMonitoring() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
"""

SESSION_MANAGER = """\
package com.itamio.accountswitcher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SessionManager {

    public static boolean switchToAccount(String accountName) {
        try {
            Object mc = getMinecraft();
            if (mc == null) {
                AccountSwitcherMod.getLogger().error("Minecraft instance is null");
                return false;
            }
            Object newSession = buildSession(accountName);
            if (newSession == null) {
                AccountSwitcherMod.getLogger().error("Could not create Session object");
                return false;
            }
            String[] fieldNames = {"session", "field_71449_j", "theSession"};
            Field sessionField = null;
            for (String name : fieldNames) {
                try {
                    sessionField = mc.getClass().getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            if (sessionField == null) {
                for (Field f : mc.getClass().getDeclaredFields()) {
                    if (f.getType().getSimpleName().equals("Session")
                            || f.getType().getSimpleName().equals("GameProfile")) {
                        sessionField = f;
                        break;
                    }
                }
            }
            if (sessionField == null) {
                AccountSwitcherMod.getLogger().error("Could not find session field in Minecraft class");
                return false;
            }
            sessionField.setAccessible(true);
            sessionField.set(mc, newSession);
            AccountSwitcherMod.getLogger().info("Successfully switched to account: " + accountName);
            return true;
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Failed to switch account to: " + accountName, e);
            return false;
        }
    }

    private static Object buildSession(String accountName) {
        String[] sessionClassNames = {
            "net.minecraft.client.Session",
            "net.minecraft.util.Session"
        };
        for (String className : sessionClassNames) {
            try {
                Class<?> sessionClass = Class.forName(className);
                try {
                    return sessionClass.getConstructor(String.class, String.class, String.class, String.class)
                        .newInstance(accountName, accountName, "", "legacy");
                } catch (Exception ignored) {}
                try {
                    return sessionClass.getConstructor(String.class, String.class, String.class)
                        .newInstance(accountName, accountName, "");
                } catch (Exception ignored) {}
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    public static String getCurrentAccount() {
        try {
            Object mc = getMinecraft();
            if (mc == null) return "Unknown";
            try {
                Method getSession = mc.getClass().getMethod("getSession");
                Object session = getSession.invoke(mc);
                if (session != null) {
                    try { return (String) session.getClass().getMethod("getUsername").invoke(session); }
                    catch (Exception ignored) {}
                    try { return (String) session.getClass().getMethod("func_111285_a").invoke(session); }
                    catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            String[] fieldNames = {"session", "field_71449_j", "theSession"};
            for (String name : fieldNames) {
                try {
                    Field f = mc.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    Object session = f.get(mc);
                    if (session != null) {
                        try { return (String) session.getClass().getMethod("getUsername").invoke(session); }
                        catch (Exception ignored) {}
                        try { return (String) session.getClass().getMethod("func_111285_a").invoke(session); }
                        catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Failed to get current account", e);
        }
        return "Unknown";
    }

    public static boolean isMinecraftReady() {
        try {
            Object mc = getMinecraft();
            if (mc == null) return false;
            try {
                Method getSession = mc.getClass().getMethod("getSession");
                return getSession.invoke(mc) != null;
            } catch (Exception ignored) {}
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Object getMinecraft() {
        String[] classNames = {"net.minecraft.client.Minecraft"};
        String[] methodNames = {"getInstance", "getMinecraft", "func_71410_x"};
        for (String cls : classNames) {
            try {
                Class<?> mcClass = Class.forName(cls);
                for (String method : methodNames) {
                    try {
                        return mcClass.getMethod(method).invoke(null);
                    } catch (Exception ignored) {}
                }
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }
}
"""
# ============================================================
# FORGE SOURCE TEMPLATES
# ============================================================

def _forge_mod_src(event_import, event_class, render_body, use_eventbus_api=True):
    if use_eventbus_api:
        subscribe_import = "import net.minecraftforge.eventbus.api.SubscribeEvent;"
        subscribe_annotation = "    @SubscribeEvent"
    else:
        subscribe_import = "// eventbus.api removed in 1.21.6+"
        subscribe_annotation = "    // no @SubscribeEvent needed with register(this)"

    return f"""\
package com.itamio.accountswitcher;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
{subscribe_import}
{event_import}
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("{MOD_ID}")
public class AccountSwitcherMod {{
    public static final String MODID = "{MOD_ID}";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private ConfigHandler configHandler;
    private static String currentAccount = "Unknown";

    public AccountSwitcherMod() {{
        MinecraftForge.EVENT_BUS.register(this);
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v{MOD_VERSION} initialized");
    }}

{subscribe_annotation}
    public void onRenderScreen({event_class} event) {{
{render_body}
    }}

    public static Logger getLogger() {{ return LOGGER; }}
    public static String getCurrentAccount() {{ return currentAccount; }}
    public static void updateCurrentAccount(String account) {{ currentAccount = account; }}
}}
"""

# 1.12.2 Forge — no MatrixStack, GuiScreenEvent.DrawScreenEvent.Post, fontRenderer
FORGE_122_SRC = f"""\
package com.itamio.accountswitcher;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "{MOD_ID}", name = "{MOD_NAME}", version = "{MOD_VERSION}")
public class AccountSwitcherMod {{
    public static final String MODID = "{MOD_ID}";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private ConfigHandler configHandler;
    private static String currentAccount = "Unknown";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {{
        MinecraftForge.EVENT_BUS.register(this);
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v{MOD_VERSION} initialized");
    }}

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {{
        try {{
            if (!(event.getGui() instanceof net.minecraft.client.gui.GuiMainMenu)) return;
            net.minecraft.client.gui.FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            String prefix = "Account: ";
            String account = currentAccount;
            fr.drawStringWithShadow(prefix, 10, 10, 0xFFFFFF);
            fr.drawStringWithShadow(account, 10 + fr.getStringWidth(prefix), 10, 0x00FF00);
        }} catch (Exception e) {{ LOGGER.error("Render error", e); }}
    }}

    public static Logger getLogger() {{ return LOGGER; }}
    public static String getCurrentAccount() {{ return currentAccount; }}
    public static void updateCurrentAccount(String account) {{ currentAccount = account; }}
}}
"""

# 1.16.5 Forge — MatrixStack, GuiScreenEvent, SRG field names via reflection
FORGE_1165_RENDER = """\
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            Object screen = mc.getClass().getField("field_71462_r").get(mc);
            if (screen == null || !screen.getClass().getSimpleName().contains("TitleScreen")) return;
            Object font = mc.getClass().getField("field_71466_p").get(mc);
            com.mojang.blaze3d.matrix.MatrixStack ms = event.getMatrixStack();
            String prefix = "Account: ";
            String account = currentAccount;
            font.getClass().getMethod("drawString",
                com.mojang.blaze3d.matrix.MatrixStack.class, String.class, float.class, float.class, int.class)
                .invoke(font, ms, prefix, 10f, 10f, 0xFFFFFF);
            int prefixWidth = (int) font.getClass().getMethod("width", String.class).invoke(font, prefix);
            font.getClass().getMethod("drawString",
                com.mojang.blaze3d.matrix.MatrixStack.class, String.class, float.class, float.class, int.class)
                .invoke(font, ms, account, 10f + prefixWidth, 10f, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }"""

# 1.17.1 Forge — GuiScreenEvent still, getMatrixStack()
FORGE_171_RENDER = """\
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
            net.minecraft.client.gui.Font font = mc.font;
            com.mojang.blaze3d.vertex.PoseStack ps = event.getMatrixStack();
            String prefix = "Account: ";
            String account = currentAccount;
            net.minecraft.client.gui.GuiComponent.drawString(ps, font, prefix, 10, 10, 0xFFFFFF);
            net.minecraft.client.gui.GuiComponent.drawString(ps, font, account,
                10 + font.width(prefix), 10, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }"""

# 1.18.x Forge — ScreenEvent.DrawScreenEvent.Post, getPoseStack()
FORGE_118_RENDER = """\
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
            net.minecraft.client.gui.Font font = mc.font;
            com.mojang.blaze3d.vertex.PoseStack ps = event.getPoseStack();
            String prefix = "Account: ";
            String account = currentAccount;
            net.minecraft.client.gui.GuiComponent.drawString(ps, font, prefix, 10, 10, 0xFFFFFF);
            net.minecraft.client.gui.GuiComponent.drawString(ps, font, account,
                10 + font.width(prefix), 10, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }"""

# 1.19.x Forge — ScreenEvent.Render.Post, getPoseStack()
FORGE_119_RENDER = """\
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
            net.minecraft.client.gui.Font font = mc.font;
            com.mojang.blaze3d.vertex.PoseStack ps = event.getPoseStack();
            String prefix = "Account: ";
            String account = currentAccount;
            net.minecraft.client.gui.GuiComponent.drawString(ps, font, prefix, 10, 10, 0xFFFFFF);
            net.minecraft.client.gui.GuiComponent.drawString(ps, font, account,
                10 + font.width(prefix), 10, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }"""

# 1.20.1+ Forge — ScreenEvent.Render.Post, getGuiGraphics()
FORGE_120_RENDER = """\
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
            net.minecraft.client.gui.Font font = mc.font;
            net.minecraft.client.gui.GuiGraphics gg = event.getGuiGraphics();
            String prefix = "Account: ";
            String account = currentAccount;
            gg.drawString(font, prefix, 10, 10, 0xFFFFFF);
            gg.drawString(font, account, 10 + font.width(prefix), 10, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }"""

# 26.1.2 Forge — ScreenEvent.Render.Post.BUS.addListener, GuiGraphicsExtractor.text()
FORGE_26_SRC = """\
package com.itamio.accountswitcher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("{MOD_ID}")
public class AccountSwitcherMod {{
    public static final String MODID = "{MOD_ID}";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private ConfigHandler configHandler;
    private static String currentAccount = "Unknown";

    public AccountSwitcherMod() {{
        ScreenEvent.Render.Post.BUS.addListener(this::onRenderScreen);
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v{MOD_VERSION} initialized");
    }}

    private void onRenderScreen(ScreenEvent.Render.Post event) {{
        try {{
            if (!(event.getScreen() instanceof TitleScreen)) return;
            GuiGraphicsExtractor gg = event.getGuiGraphics();
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            String prefix = "Account: ";
            String account = currentAccount;
            gg.text(font, prefix, 10, 10, 0xFFFFFF);
            gg.text(font, account, 10 + font.width(prefix), 10, 0x00FF00);
        }} catch (Exception e) {{ LOGGER.error("Render error", e); }}
    }}

    public static Logger getLogger() {{ return LOGGER; }}
    public static String getCurrentAccount() {{ return currentAccount; }}
    public static void updateCurrentAccount(String account) {{ currentAccount = account; }}
}}
"""
# ============================================================
# NEOFORGE SOURCE TEMPLATES
# ============================================================

# NeoForge 1.20.2-1.21.5 — @SubscribeEvent, ScreenEvent.Render.Post, getGuiGraphics()
def _neoforge_mod_src(extra_imports="", constructor_args="", constructor_body_extra=""):
    return f"""\
package com.itamio.accountswitcher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
{extra_imports}
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("{MOD_ID}")
public class AccountSwitcherMod {{
    public static final String MODID = "{MOD_ID}";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private ConfigHandler configHandler;
    private static String currentAccount = "Unknown";

    public AccountSwitcherMod(IEventBus modBus{constructor_args}) {{
        NeoForge.EVENT_BUS.register(this);
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v{MOD_VERSION} initialized");
    }}

    @SubscribeEvent
    public void onRenderScreen(ScreenEvent.Render.Post event) {{
        try {{
            if (!(event.getScreen() instanceof TitleScreen)) return;
            GuiGraphics gg = event.getGuiGraphics();
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            String prefix = "Account: ";
            String account = currentAccount;
            gg.drawString(font, prefix, 10, 10, 0xFFFFFF);
            gg.drawString(font, account, 10 + font.width(prefix), 10, 0x00FF00);
        }} catch (Exception e) {{ LOGGER.error("Render error", e); }}
    }}

    public static Logger getLogger() {{ return LOGGER; }}
    public static String getCurrentAccount() {{ return currentAccount; }}
    public static void updateCurrentAccount(String account) {{ currentAccount = account; }}
}}
"""

NEO_STD_SRC = _neoforge_mod_src()
NEO_1219_SRC = _neoforge_mod_src(
    extra_imports="import net.neoforged.fml.ModContainer;",
    constructor_args=", ModContainer modContainer"
)

# NeoForge 26.1-26.1.2 — GuiGraphicsExtractor.text() instead of GuiGraphics.drawString()
NEO_26_SRC = f"""\
package com.itamio.accountswitcher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("{MOD_ID}")
public class AccountSwitcherMod {{
    public static final String MODID = "{MOD_ID}";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private ConfigHandler configHandler;
    private static String currentAccount = "Unknown";

    public AccountSwitcherMod(IEventBus modBus, ModContainer modContainer) {{
        NeoForge.EVENT_BUS.register(this);
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v{MOD_VERSION} initialized");
    }}

    @SubscribeEvent
    public void onRenderScreen(ScreenEvent.Render.Post event) {{
        try {{
            if (!(event.getScreen() instanceof TitleScreen)) return;
            GuiGraphicsExtractor gg = event.getGuiGraphics();
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            String prefix = "Account: ";
            String account = currentAccount;
            gg.text(font, prefix, 10, 10, 0xFFFFFF);
            gg.text(font, account, 10 + font.width(prefix), 10, 0x00FF00);
        }} catch (Exception e) {{ LOGGER.error("Render error", e); }}
    }}

    public static Logger getLogger() {{ return LOGGER; }}
    public static String getCurrentAccount() {{ return currentAccount; }}
    public static void updateCurrentAccount(String account) {{ currentAccount = account; }}
}}
"""
# ============================================================
# FABRIC SOURCE TEMPLATES
# Fabric uses a mixin on TitleScreen.render() to draw the account name.
# render() signature:
#   1.16.5-1.19.4: render(MatrixStack matrices, int mouseX, int mouseY, float delta)
#   1.20.1-1.20.6: render(DrawContext context, int mouseX, int mouseY, float delta)  [yarn: DrawContext]
#   1.21+:         render(DrawContext context, int mouseX, int mouseY, float delta)   [Mojang: GuiGraphics via DrawContext]
# ============================================================

# Fabric 1.16.5-1.19.4 (presplit adapter) — MatrixStack render, yarn mappings
# TitleScreen is net.minecraft.client.gui.screen.TitleScreen
# textRenderer field, drawStringWithShadow or drawString
FABRIC_PRESPLIT_MOD = f"""\
package com.itamio.accountswitcher;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AccountSwitcherMod implements ClientModInitializer {{
    public static final String MODID = "{MOD_ID}";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private static String currentAccount = "Unknown";
    private ConfigHandler configHandler;

    @Override
    public void onInitializeClient() {{
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v{MOD_VERSION} initialized");
    }}

    public static Logger getLogger() {{ return LOGGER; }}
    public static String getCurrentAccount() {{ return currentAccount; }}
    public static void updateCurrentAccount(String account) {{ currentAccount = account; }}
}}
"""

# Mixin for Fabric 1.16.5-1.19.4 — MatrixStack render
FABRIC_PRESPLIT_MIXIN = """\
package com.itamio.accountswitcher.mixin;

import com.itamio.accountswitcher.AccountSwitcherMod;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            net.minecraft.client.font.TextRenderer tr = mc.textRenderer;
            String prefix = "Account: ";
            String account = AccountSwitcherMod.getCurrentAccount();
            tr.drawWithShadow(matrices, prefix, 10, 10, 0xFFFFFF);
            tr.drawWithShadow(matrices, account, 10 + tr.getWidth(prefix), 10, 0x00FF00);
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Render error", e);
        }
    }
}
"""

# Mixin for Fabric 1.20.1-1.20.6 — DrawContext render (yarn split adapter)
# DrawContext is net.minecraft.client.gui.DrawContext
FABRIC_SPLIT_120_MIXIN = """\
package com.itamio.accountswitcher.mixin;

import com.itamio.accountswitcher.AccountSwitcherMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            net.minecraft.client.font.TextRenderer tr = mc.textRenderer;
            String prefix = "Account: ";
            String account = AccountSwitcherMod.getCurrentAccount();
            context.drawText(tr, prefix, 10, 10, 0xFFFFFF, true);
            context.drawText(tr, account, 10 + tr.getWidth(prefix), 10, 0x00FF00, true);
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Render error", e);
        }
    }
}
"""

# Fabric 1.21+ (split adapter, Mojang mappings) — GuiGraphics render
# TitleScreen is net.minecraft.client.gui.screens.TitleScreen (Mojang)
# Font is net.minecraft.client.gui.Font
# GuiGraphics is net.minecraft.client.gui.GuiGraphics
FABRIC_SPLIT_121_MOD = f"""\
package com.itamio.accountswitcher;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AccountSwitcherMod implements ClientModInitializer {{
    public static final String MODID = "{MOD_ID}";
    private static final Logger LOGGER = LogManager.getLogger("AccountSwitcher");
    private static String currentAccount = "Unknown";
    private ConfigHandler configHandler;

    @Override
    public void onInitializeClient() {{
        configHandler = new ConfigHandler();
        configHandler.startMonitoring();
        LOGGER.info("Account Switcher Mod v{MOD_VERSION} initialized");
    }}

    public static Logger getLogger() {{ return LOGGER; }}
    public static String getCurrentAccount() {{ return currentAccount; }}
    public static void updateCurrentAccount(String account) {{ currentAccount = account; }}
}}
"""

FABRIC_SPLIT_121_MIXIN = """\
package com.itamio.accountswitcher.mixin;

import com.itamio.accountswitcher.AccountSwitcherMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            String prefix = "Account: ";
            String account = AccountSwitcherMod.getCurrentAccount();
            guiGraphics.drawString(font, prefix, 10, 10, 0xFFFFFF);
            guiGraphics.drawString(font, account, 10 + font.width(prefix), 10, 0x00FF00);
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Render error", e);
        }
    }
}
"""

# Fabric 26.x — GuiGraphicsExtractor.text() instead of GuiGraphics.drawString()
# TitleScreen uses extractRenderState() not render() in 26.x
# Use @Inject on extractRenderState instead
FABRIC_26_MIXIN = """\
package com.itamio.accountswitcher.mixin;

import com.itamio.accountswitcher.AccountSwitcherMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            String prefix = "Account: ";
            String account = AccountSwitcherMod.getCurrentAccount();
            graphics.text(font, prefix, 10, 10, 0xFFFFFF);
            graphics.text(font, account, 10 + font.width(prefix), 10, 0x00FF00);
        } catch (Exception e) {
            AccountSwitcherMod.getLogger().error("Render error", e);
        }
    }
}
"""

def fabric_mixins_json(compat_level="JAVA_21"):
    return f"""\
{{
  "required": true,
  "package": "com.itamio.accountswitcher.mixin",
  "compatibilityLevel": "{compat_level}",
  "client": [
    "TitleScreenMixin"
  ],
  "injectors": {{
    "defaultRequire": 1
  }}
}}
"""
# ============================================================
# TARGET LISTS
# ============================================================

# Already published — skip these
ALREADY_PUBLISHED = {
    # Forge
    ("1.8.9", "forge"), ("1.12.2", "forge"),
    ("1.16.5", "forge"), ("1.17.1", "forge"),
    ("1.18", "forge"), ("1.18.1", "forge"), ("1.18.2", "forge"),
    ("1.19", "forge"), ("1.19.1", "forge"), ("1.19.2", "forge"), ("1.19.3", "forge"), ("1.19.4", "forge"),
    ("1.20.1", "forge"), ("1.20.2", "forge"), ("1.20.3", "forge"), ("1.20.4", "forge"), ("1.20.6", "forge"),
    ("1.21", "forge"), ("1.21.1", "forge"),
    ("1.21.3", "forge"), ("1.21.4", "forge"), ("1.21.5", "forge"), ("1.21.6", "forge"),
    ("1.21.7", "forge"), ("1.21.8", "forge"),
    ("1.21.9", "forge"), ("1.21.10", "forge"), ("1.21.11", "forge"),
    # Fabric (1.21.1-1.21.8 range only)
    ("1.21.1", "fabric"), ("1.21.2", "fabric"), ("1.21.3", "fabric"), ("1.21.4", "fabric"),
    ("1.21.5", "fabric"), ("1.21.6", "fabric"), ("1.21.7", "fabric"), ("1.21.8", "fabric"),
}

# (slug, mc_version, loader, source_key)
# source_key maps to a source dict built in generate()
FORGE_TARGETS = [
    # 1.12 — 1.12.2 Forge API, no MatrixStack, GuiMainMenu, fontRenderer
    ("accountswitcher-forge-1-12", "1.12", "forge", "forge_122"),
    # 26.1.2 Forge — EventBus 7, GuiGraphicsExtractor
    ("accountswitcher-forge-26-1-2", "26.1.2", "forge", "forge_26"),
]

NEOFORGE_TARGETS = [
    ("accountswitcher-neoforge-1-20-2",  "1.20.2",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-20-4",  "1.20.4",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-20-5",  "1.20.5",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-20-6",  "1.20.6",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-21",    "1.21",    "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-21-1",  "1.21.1",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-21-2",  "1.21.2",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-21-3",  "1.21.3",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-21-4",  "1.21.4",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-21-5",  "1.21.5",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-21-6",  "1.21.6",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-21-7",  "1.21.7",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-21-8",  "1.21.8",  "neoforge", "neo_std"),
    ("accountswitcher-neoforge-1-21-9",  "1.21.9",  "neoforge", "neo_1219"),
    ("accountswitcher-neoforge-1-21-10", "1.21.10", "neoforge", "neo_1219"),
    ("accountswitcher-neoforge-1-21-11", "1.21.11", "neoforge", "neo_1219"),
    ("accountswitcher-neoforge-26-1",    "26.1",    "neoforge", "neo_26"),
    ("accountswitcher-neoforge-26-1-1",  "26.1.1",  "neoforge", "neo_26"),
    ("accountswitcher-neoforge-26-1-2",  "26.1.2",  "neoforge", "neo_26"),
]

FABRIC_TARGETS = [
    # presplit (1.16.5-1.19.4) — MatrixStack render, yarn mappings
    ("accountswitcher-fabric-1-16-5",  "1.16.5",  "fabric", "fabric_presplit"),
    ("accountswitcher-fabric-1-17-1",  "1.17.1",  "fabric", "fabric_presplit"),
    ("accountswitcher-fabric-1-18",    "1.18",    "fabric", "fabric_presplit"),
    ("accountswitcher-fabric-1-19",    "1.19",    "fabric", "fabric_presplit"),
    ("accountswitcher-fabric-1-19-1",  "1.19.1",  "fabric", "fabric_presplit"),
    ("accountswitcher-fabric-1-19-2",  "1.19.2",  "fabric", "fabric_presplit"),
    ("accountswitcher-fabric-1-19-3",  "1.19.3",  "fabric", "fabric_presplit"),
    ("accountswitcher-fabric-1-19-4",  "1.19.4",  "fabric", "fabric_presplit"),
    # split 1.20.x — DrawContext render, yarn mappings
    ("accountswitcher-fabric-1-20-1",  "1.20.1",  "fabric", "fabric_split_120"),
    ("accountswitcher-fabric-1-20-2",  "1.20.2",  "fabric", "fabric_split_120"),
    ("accountswitcher-fabric-1-20-3",  "1.20.3",  "fabric", "fabric_split_120"),
    ("accountswitcher-fabric-1-20-4",  "1.20.4",  "fabric", "fabric_split_120"),
    ("accountswitcher-fabric-1-20-5",  "1.20.5",  "fabric", "fabric_split_120"),
    ("accountswitcher-fabric-1-20-6",  "1.20.6",  "fabric", "fabric_split_120"),
    # split 1.21-1.21.8 — GuiGraphics render, Mojang mappings (already published, skip)
    # split 1.21.9-1.21.11 — GuiGraphics render, Mojang mappings
    ("accountswitcher-fabric-1-21-9",  "1.21.9",  "fabric", "fabric_split_121"),
    ("accountswitcher-fabric-1-21-10", "1.21.10", "fabric", "fabric_split_121"),
    ("accountswitcher-fabric-1-21-11", "1.21.11", "fabric", "fabric_split_121"),
    # 26.x — GuiGraphicsExtractor.text(), extractRenderState mixin
    ("accountswitcher-fabric-26-1",    "26.1",    "fabric", "fabric_26"),
    ("accountswitcher-fabric-26-1-1",  "26.1.1",  "fabric", "fabric_26"),
    ("accountswitcher-fabric-26-1-2",  "26.1.2",  "fabric", "fabric_26"),
]

ALL_TARGETS = FORGE_TARGETS + NEOFORGE_TARGETS + FABRIC_TARGETS
# ============================================================
# GENERATE FUNCTION
# ============================================================

def get_failed_slugs() -> set:
    runs_dir = ROOT / "ModCompileRuns"
    if not runs_dir.exists():
        return set()
    runs = sorted(runs_dir.iterdir(), reverse=True)
    for run in runs:
        summary = run / "artifacts" / "all-mod-builds" / "run-summary.json"
        if not summary.exists():
            summary = run / "result.json"
        if summary.exists():
            data = json.loads(summary.read_text(encoding="utf-8"))
            failed = set()
            for mod in data.get("mods", []):
                if mod.get("status") != "success":
                    failed.add(mod["slug"])
            return failed
    return set()


def generate(failed_only: bool = False):
    failed_slugs = get_failed_slugs() if failed_only else set()

    if BUNDLE.exists():
        shutil.rmtree(BUNDLE)
    BUNDLE.mkdir(parents=True)

    # Build source map
    sources = {
        # Forge
        "forge_122": FORGE_122_SRC,
        "forge_1165": _forge_mod_src(
            "import net.minecraftforge.client.event.GuiScreenEvent;",
            "GuiScreenEvent.DrawScreenEvent.Post",
            FORGE_1165_RENDER, True),
        "forge_171": _forge_mod_src(
            "import net.minecraftforge.client.event.GuiScreenEvent;",
            "GuiScreenEvent.DrawScreenEvent.Post",
            FORGE_171_RENDER, True),
        "forge_118": _forge_mod_src(
            "import net.minecraftforge.client.event.ScreenEvent;",
            "ScreenEvent.DrawScreenEvent.Post",
            FORGE_118_RENDER, True),
        "forge_119": _forge_mod_src(
            "import net.minecraftforge.client.event.ScreenEvent;",
            "ScreenEvent.Render.Post",
            FORGE_119_RENDER, True),
        "forge_120": _forge_mod_src(
            "import net.minecraftforge.client.event.ScreenEvent;",
            "ScreenEvent.Render.Post",
            FORGE_120_RENDER, True),
        "forge_26": FORGE_26_SRC,
        # NeoForge
        "neo_std": NEO_STD_SRC,
        "neo_1219": NEO_1219_SRC,
        "neo_26": NEO_26_SRC,
        # Fabric — main mod class
        "fabric_presplit_mod": FABRIC_PRESPLIT_MOD,
        "fabric_split_121_mod": FABRIC_SPLIT_121_MOD,
    }

    # Map source_key to (main_src, mixin_src, compat_level)
    fabric_config = {
        "fabric_presplit": (FABRIC_PRESPLIT_MOD, FABRIC_PRESPLIT_MIXIN, "JAVA_17"),
        "fabric_split_120": (FABRIC_PRESPLIT_MOD, FABRIC_SPLIT_120_MIXIN, "JAVA_21"),
        "fabric_split_121": (FABRIC_SPLIT_121_MOD, FABRIC_SPLIT_121_MIXIN, "JAVA_21"),
        "fabric_26": (FABRIC_SPLIT_121_MOD, FABRIC_26_MIXIN, "JAVA_25"),
    }

    count = 0
    for (slug, mc, loader, source_key) in ALL_TARGETS:
        if failed_only and slug not in failed_slugs:
            continue

        base = BUNDLE / slug
        java_dir = base / "src" / "main" / "java" / PKG

        if loader == "fabric":
            mod_src, mixin_src, compat = fabric_config[source_key]
            write(java_dir / "AccountSwitcherMod.java", mod_src)
            write(java_dir.parent.parent.parent.parent / PKG / "mixin" / "TitleScreenMixin.java", mixin_src)
            # Mixins JSON
            write(base / "src" / "main" / "resources" / "accountswitcher.mixins.json",
                  fabric_mixins_json(compat))
        else:
            # Forge / NeoForge
            src = sources[source_key]
            write(java_dir / "AccountSwitcherMod.java", src)

        # Shared classes
        write(java_dir / "ConfigHandler.java", CONFIG_HANDLER)
        write(java_dir / "SessionManager.java", SESSION_MANAGER)

        write(base / "mod.txt", mod_txt("client"))
        write(base / "version.txt", version_txt(mc, loader))

        count += 1
        print(f"  Generated: {slug}")

    # Create zip
    zip_path = ROOT / "incoming" / "account-switcher-all-versions.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for f in BUNDLE.rglob("*"):
            if f.is_file():
                zf.write(f, f.relative_to(BUNDLE))

    print(f"\nGenerated {count} targets -> {zip_path}")
    return zip_path


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed-only", action="store_true",
                        help="Only regenerate targets that failed in the last run")
    args = parser.parse_args()
    generate(failed_only=args.failed_only)
