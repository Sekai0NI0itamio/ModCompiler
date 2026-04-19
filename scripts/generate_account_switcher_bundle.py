#!/usr/bin/env python3
"""
Generates the Account Switcher bundle — all 26 shell Forge versions (2.0.0).
Targets: Forge 1.16.5 through 1.21.11 (all versions that have shells).
Source of truth: Fabric 2.0.0 (UeiCtbGY) + Forge 1.8.9 2.0.0 (w16LaP5n)

Key insight: SessionManager uses reflection to find Minecraft.session — works
across ALL versions unchanged. Only the title screen rendering and event
registration differ per version.

Run: python3 scripts/generate_account_switcher_bundle.py [--failed-only]
"""
import argparse, json, shutil, subprocess, zipfile
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
# ConfigHandler: pure Java file I/O — no MC API, works everywhere
# SessionManager: reflection-based session field access — works everywhere
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

/**
 * Uses reflection to access Minecraft.session — works across all versions
 * without any version-specific imports.
 */
public class SessionManager {

    public static boolean switchToAccount(String accountName) {
        try {
            Object mc = getMinecraft();
            if (mc == null) {
                AccountSwitcherMod.getLogger().error("Minecraft instance is null");
                return false;
            }
            // Build a new Session object via reflection — constructor varies by version
            Object newSession = buildSession(accountName);
            if (newSession == null) {
                AccountSwitcherMod.getLogger().error("Could not create Session object");
                return false;
            }
            // Try known field names for the session field across all versions
            String[] fieldNames = {"session", "field_71449_j", "theSession"};
            Field sessionField = null;
            for (String name : fieldNames) {
                try {
                    sessionField = mc.getClass().getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            if (sessionField == null) {
                // Fallback: search all fields for a Session-typed field
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
        // Try modern Session (1.16+): Session(String username, UUID uuid, String token, Optional<ProfileKeys>, Type)
        // Try intermediate Session (1.12-1.15): Session(String username, String uuid, String token, String type)
        // Try legacy Session (1.8.9): Session(String username, String uuid, String token, String type)
        String[] sessionClassNames = {
            "net.minecraft.client.Session",
            "net.minecraft.util.Session"
        };
        for (String className : sessionClassNames) {
            try {
                Class<?> sessionClass = Class.forName(className);
                // Try 4-arg String constructor (legacy)
                try {
                    return sessionClass.getConstructor(String.class, String.class, String.class, String.class)
                        .newInstance(accountName, accountName, "", "legacy");
                } catch (Exception ignored) {}
                // Try 3-arg constructor
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
            // Try getSession() method
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
            // Try field access
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
            // Check session exists
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
        // Try all known static accessor method names
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
# VERSION-SPECIFIC MAIN MOD CLASS
# Key differences:
#   1.16.5:        ScreenEvent doesn't exist → GuiScreenEvent.DrawScreenEvent.Post
#                  FMLJavaModLoadingContext for config registration
#   1.17.1-1.18.x: ScreenEvent.DrawScreenEvent.Post (new class name)
#   1.19.x-1.20.x: ScreenEvent.Render.Post (renamed again)
#   1.20.5+:       GuiGraphics replaces direct font rendering
#   1.21.5+:       eventbus.api removed → plain register(this)
# ============================================================

def _forge_mod_src(
    mc_range: str,
    event_import: str,
    event_class: str,
    render_body: str,
    extra_imports: str = "",
    use_eventbus_api: bool = True,
    mod_loading_ctx: str = "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext",
) -> str:
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
{extra_imports}
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

# ---- Render body variants ----

# 1.16.5 – 1.19.x: drawString on FontRenderer
RENDER_BODY_LEGACY = """\
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
            net.minecraft.client.gui.Font font = mc.font;
            String prefix = "Account: ";
            String account = currentAccount;
            com.mojang.blaze3d.vertex.PoseStack ps = event.getPoseStack();
            net.minecraft.client.gui.GuiComponent.drawString(ps, font, prefix, 10, 10, 0xFFFFFF);
            net.minecraft.client.gui.GuiComponent.drawString(ps, font, account,
                10 + font.width(prefix), 10, 0x00FF00);
        } catch (Exception e) { LOGGER.error("Render error", e); }"""

# 1.20.1 – 1.20.4: GuiGraphics introduced
RENDER_BODY_GUI_GRAPHICS = """\
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

# 1.21.5+: same GuiGraphics but eventbus.api gone
RENDER_BODY_GUI_GRAPHICS_NEW = RENDER_BODY_GUI_GRAPHICS

# ---- Build all 26 Forge shell targets ----

TARGETS = [
    # (slug, mc_version, mc_range_display, event_import, event_class, render_body, use_eventbus_api)
    ("accountswitcher-forge-1-16-5", "1.16.5", "1.16.5",
     "import net.minecraftforge.client.event.GuiScreenEvent;",
     "GuiScreenEvent.DrawScreenEvent.Post",
     """\
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
        } catch (Exception e) { LOGGER.error("Render error", e); }""",
     True),

    ("accountswitcher-forge-1-17-1", "1.17.1", "1.17.1",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.DrawScreenEvent.Post",
     RENDER_BODY_LEGACY, True),

    ("accountswitcher-forge-1-18", "1.18", "1.18",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.DrawScreenEvent.Post",
     RENDER_BODY_LEGACY, True),

    ("accountswitcher-forge-1-18-1", "1.18.1", "1.18.1",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.DrawScreenEvent.Post",
     RENDER_BODY_LEGACY, True),

    ("accountswitcher-forge-1-18-2", "1.18.2", "1.18.2",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.DrawScreenEvent.Post",
     RENDER_BODY_LEGACY, True),

    ("accountswitcher-forge-1-19", "1.19", "1.19",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_LEGACY, True),

    ("accountswitcher-forge-1-19-1", "1.19.1", "1.19.1",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_LEGACY, True),

    ("accountswitcher-forge-1-19-2", "1.19.2", "1.19.2",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_LEGACY, True),

    ("accountswitcher-forge-1-19-3", "1.19.3", "1.19.3",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_LEGACY, True),

    ("accountswitcher-forge-1-19-4", "1.19.4", "1.19.4",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_LEGACY, True),

    ("accountswitcher-forge-1-20-1", "1.20.1", "1.20.1",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS, True),

    ("accountswitcher-forge-1-20-2", "1.20.2", "1.20.2",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS, True),

    ("accountswitcher-forge-1-20-3", "1.20.3", "1.20.3",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS, True),

    ("accountswitcher-forge-1-20-4", "1.20.4", "1.20.4",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS, True),

    ("accountswitcher-forge-1-20-6", "1.20.6", "1.20.6",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS, True),

    ("accountswitcher-forge-1-21", "1.21", "1.21",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS, True),

    ("accountswitcher-forge-1-21-1", "1.21.1", "1.21.1",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS, True),

    ("accountswitcher-forge-1-21-3", "1.21.3", "1.21.3",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS, True),

    ("accountswitcher-forge-1-21-4", "1.21.4", "1.21.4",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS, True),

    ("accountswitcher-forge-1-21-5", "1.21.5", "1.21.5",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS_NEW, False),

    ("accountswitcher-forge-1-21-6", "1.21.6", "1.21.6",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS_NEW, False),

    ("accountswitcher-forge-1-21-7", "1.21.7", "1.21.7",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS_NEW, False),

    ("accountswitcher-forge-1-21-8", "1.21.8", "1.21.8",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS_NEW, False),

    ("accountswitcher-forge-1-21-9", "1.21.9", "1.21.9",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS_NEW, False),

    ("accountswitcher-forge-1-21-10", "1.21.10", "1.21.10",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS_NEW, False),

    ("accountswitcher-forge-1-21-11", "1.21.11", "1.21.11",
     "import net.minecraftforge.client.event.ScreenEvent;",
     "ScreenEvent.Render.Post",
     RENDER_BODY_GUI_GRAPHICS_NEW, False),
]


def get_failed_slugs() -> set:
    runs_dir = ROOT / "ModCompileRuns"
    if not runs_dir.exists():
        return set()
    runs = sorted(runs_dir.iterdir(), reverse=True)
    for run in runs:
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

    count = 0
    for (slug, mc, mc_range, event_import, event_class, render_body, use_eventbus_api) in TARGETS:
        if failed_only and slug not in failed_slugs:
            continue

        base = BUNDLE / slug
        java_dir = base / "src" / "main" / "java" / PKG

        # Main mod class
        write(java_dir / "AccountSwitcherMod.java",
              _forge_mod_src(mc_range, event_import, event_class, render_body,
                             use_eventbus_api=use_eventbus_api))

        # Shared classes (identical across all versions)
        write(java_dir / "ConfigHandler.java", CONFIG_HANDLER)
        write(java_dir / "SessionManager.java", SESSION_MANAGER)

        # mod.txt and version.txt
        write(base / "mod.txt", mod_txt("client"))
        write(base / "version.txt", version_txt(mc, "forge"))

        count += 1
        print(f"  Generated: {slug}")

    # Create zip
    zip_path = ROOT / "incoming" / "account-switcher-all-versions.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for f in BUNDLE.rglob("*"):
            if f.is_file():
                zf.write(f, f.relative_to(BUNDLE))

    print(f"\nGenerated {count} targets → {zip_path}")
    return zip_path


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed-only", action="store_true",
                        help="Only regenerate targets that failed in the last run")
    args = parser.parse_args()
    generate(failed_only=args.failed_only)
