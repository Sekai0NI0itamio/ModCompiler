#!/usr/bin/env python3
"""
Generate the toggle-sprint-all-versions bundle for all supported
Minecraft versions and loaders.

Existing on Modrinth (skip these — already published):
  Fabric:  1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6,
           1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6,
           1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11
  Forge:   1.12.2, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.6,
           1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11

Missing (these are what we build):
  Forge:   1.8.9, 1.16.5, 1.17.1, 1.18-1.18.2, 1.19-1.19.4,
           1.20.5, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5
  Fabric:  1.16.5, 1.17.1, 1.18-1.18.2, 1.19-1.19.4
  NeoForge: 1.20.4, 1.20.5, 1.20.6, 1.21-1.21.1, 1.21.2-1.21.8,
            1.21.9-1.21.11

This is a CLIENT-SIDE ONLY mod (runtime_side=client).

The mod toggles sprint on/off when the sprint key is pressed.
Logic: on each client tick, if sprint is locked, keep the player sprinting
       (unless in a menu, sneaking, using item, or moving backward).
       Toggle the lock when the sprint key is pressed.
"""

import zipfile
from pathlib import Path

ROOT = Path(__file__).parent.parent
BUNDLE_DIR = ROOT / "incoming" / "toggle-sprint-all-versions"

MOD_ID = "togglesprint"
MOD_NAME = "Toggle Sprint"
MOD_VERSION = "1.0.0"
GROUP = "asd.itamio.togglesprint"
DESCRIPTION = "Allows you to keep sprinting when sprint is toggled."
AUTHORS = "Itamio"
LICENSE = "MIT"
HOMEPAGE = "https://modrinth.com/mod/toggle-sprint"

# ---------------------------------------------------------------------------
# mod.txt template
# ---------------------------------------------------------------------------
MOD_TXT = f"""\
mod_id={MOD_ID}
name={MOD_NAME}
mod_version={MOD_VERSION}
group={GROUP}
entrypoint_class=asd.itamio.togglesprint.{{entrypoint}}
description={DESCRIPTION}
authors={AUTHORS}
license={LICENSE}
homepage={HOMEPAGE}
runtime_side=client
"""

# ---------------------------------------------------------------------------
# 1.8.9 Forge — uses LWJGL Keyboard + old event bus
# ---------------------------------------------------------------------------
FORGE_189_MOD = """\
package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import org.lwjgl.input.Keyboard;

@Mod(modid = ToggleSprintMod.MODID, name = "Toggle Sprint", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class ToggleSprintMod {
    public static final String MODID = "togglesprint";
    private boolean sprintToggled = false;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        // Left Control key = 29
        if (Keyboard.getEventKey() == 29 && Keyboard.getEventKeyState()) {
            sprintToggled = !sprintToggled;
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.movementInput == null) return;
        if (sprintToggled && mc.thePlayer.movementInput.moveForward > 0.0F
                && !mc.thePlayer.isSneaking()) {
            mc.thePlayer.setSprinting(true);
        }
    }
}
"""

# ---------------------------------------------------------------------------
# 1.12.2 Forge — uses LWJGL Keyboard + old event bus (same as original)
# ---------------------------------------------------------------------------
FORGE_1122_MOD = """\
package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import org.lwjgl.input.Keyboard;

@Mod(modid = ToggleSprintMod.MODID, name = "Toggle Sprint", version = "1.0.0",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.12,1.12.2]")
public class ToggleSprintMod {
    public static final String MODID = "togglesprint";
    private boolean sprintToggled = false;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        // Left Control key = 29
        if (Keyboard.getEventKey() == 29 && Keyboard.getEventKeyState()) {
            sprintToggled = !sprintToggled;
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player.movementInput == null) return;
        if (sprintToggled && mc.player.movementInput.moveForward > 0.0F
                && !mc.player.isSneaking()) {
            mc.player.setSprinting(true);
        }
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.16.5: Component doesn't exist yet — use ITextComponent/StringTextComponent
# ---------------------------------------------------------------------------
FORGE_1165_CONTROLLER = """\
package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.StringTextComponent;

public final class ToggleSprintController {
    private boolean sprintLocked;
    private boolean sprintKeyWasDown;

    public void onClientTick(Minecraft client) {
        if (client == null || client.options == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.player == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.isPaused()) {
            sprintKeyWasDown = client.options.keySprint.isDown();
            return;
        }
        boolean sprintKeyDown = client.options.keySprint.isDown();
        if (sprintKeyDown && !sprintKeyWasDown) {
            sprintLocked = !sprintLocked;
            client.options.keySprint.setDown(false);
            client.player.setSprinting(false);
            client.player.displayClientMessage(
                new StringTextComponent("Toggle Sprint: " + (sprintLocked ? "ON" : "OFF")), true);
        }
        sprintKeyWasDown = sprintKeyDown;
        if (sprintLocked) {
            client.player.setSprinting(shouldKeepSprinting(client));
        }
    }

    private boolean shouldKeepSprinting(Minecraft client) {
        if (client.screen != null) return false;
        if (client.player == null) return false;
        if (client.player.isSpectator() || client.player.isPassenger()) return false;
        if (client.player.isShiftKeyDown() || client.player.isUsingItem()) return false;
        return client.options.keyUp.isDown() && !client.options.keyDown.isDown();
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.17.1 - 1.18.1: Component exists but literal() not yet — use TextComponent
# ---------------------------------------------------------------------------
FORGE_117_118_CONTROLLER = """\
package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;

public final class ToggleSprintController {
    private boolean sprintLocked;
    private boolean sprintKeyWasDown;

    public void onClientTick(Minecraft client) {
        if (client == null || client.options == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.player == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.isPaused()) {
            sprintKeyWasDown = client.options.keySprint.isDown();
            return;
        }
        boolean sprintKeyDown = client.options.keySprint.isDown();
        if (sprintKeyDown && !sprintKeyWasDown) {
            sprintLocked = !sprintLocked;
            client.options.keySprint.setDown(false);
            client.player.setSprinting(false);
            client.player.displayClientMessage(
                new TextComponent("Toggle Sprint: " + (sprintLocked ? "ON" : "OFF")), true);
        }
        sprintKeyWasDown = sprintKeyDown;
        if (sprintLocked) {
            client.player.setSprinting(shouldKeepSprinting(client));
        }
    }

    private boolean shouldKeepSprinting(Minecraft client) {
        if (client.screen != null) return false;
        if (client.player == null) return false;
        if (client.player.isSpectator() || client.player.isPassenger()) return false;
        if (client.player.isShiftKeyDown() || client.player.isUsingItem()) return false;
        return client.options.keyUp.isDown() && !client.options.keyDown.isDown();
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.18.2 - 1.20.6: Component.literal() available
# ---------------------------------------------------------------------------
FORGE_LEGACY_CONTROLLER = """\
package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ToggleSprintController {
    private boolean sprintLocked;
    private boolean sprintKeyWasDown;

    public void onClientTick(Minecraft client) {
        if (client == null || client.options == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.player == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.isPaused()) {
            sprintKeyWasDown = client.options.keySprint.isDown();
            return;
        }
        boolean sprintKeyDown = client.options.keySprint.isDown();
        if (sprintKeyDown && !sprintKeyWasDown) {
            sprintLocked = !sprintLocked;
            client.options.keySprint.setDown(false);
            client.player.setSprinting(false);
            client.player.displayClientMessage(
                Component.literal("Toggle Sprint: " + (sprintLocked ? "ON" : "OFF")), true);
        }
        sprintKeyWasDown = sprintKeyDown;
        if (sprintLocked) {
            client.player.setSprinting(shouldKeepSprinting(client));
        }
    }

    private boolean shouldKeepSprinting(Minecraft client) {
        if (client.screen != null) return false;
        if (client.player == null) return false;
        if (client.player.isSpectator() || client.player.isPassenger()) return false;
        if (client.player.isShiftKeyDown() || client.player.isUsingItem()) return false;
        return client.options.keyUp.isDown() && !client.options.keyDown.isDown();
    }
}
"""

FORGE_LEGACY_CLIENT = """\
package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;

public final class ToggleSprintForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintForgeClient() {}

    public static void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.END) {
            CONTROLLER.onClientTick(Minecraft.getInstance());
        }
    }
}
"""

FORGE_LEGACY_MOD = """\
package asd.itamio.togglesprint;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("togglesprint")
public final class ToggleSprintForgeMod {
    public static final String MOD_ID = "togglesprint";

    public ToggleSprintForgeMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(ToggleSprintForgeClient::onClientTick);
        }
    }
}
"""

# ---------------------------------------------------------------------------
# Forge 1.21+ (1.21-1.21.1, 1.21.2-1.21.8): same as legacy
# ---------------------------------------------------------------------------
FORGE_MODERN_MOD = FORGE_LEGACY_MOD

# ---------------------------------------------------------------------------
# Forge 1.21.9+: uses @EventBusSubscriber pattern (FMLEnvironment.dist removed)
# Event type is TickEvent.ClientTickEvent.Post (inner class)
# SubscribeEvent is from net.minecraftforge.eventbus.api.listener
# ---------------------------------------------------------------------------
FORGE_2119_CLIENT = """\
package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent.Post;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = "togglesprint", value = {Dist.CLIENT})
public final class ToggleSprintForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintForgeClient() {}

    @SubscribeEvent
    public static void onClientTick(Post event) {
        CONTROLLER.onClientTick(Minecraft.getInstance());
    }
}
"""

FORGE_2119_MOD = """\
package asd.itamio.togglesprint;

import net.minecraftforge.fml.common.Mod;

@Mod("togglesprint")
public final class ToggleSprintForgeMod {
    public static final String MOD_ID = "togglesprint";
}
"""

# ---------------------------------------------------------------------------
# Fabric presplit (1.16.5–1.19.4): uses Yarn-mapped deobfuscated names
# In fabric_presplit, the Yarn intermediary class_XXX names are NOT available
# at source level. We must use the actual Yarn-mapped names:
#   MinecraftClient (= class_310), Text (= class_2561), etc.
# ---------------------------------------------------------------------------
FABRIC_PRESPLIT_CONTROLLER = """\
package asd.itamio.togglesprint;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class ToggleSprintController {
    private boolean sprintLocked;
    private boolean sprintKeyWasDown;

    public void onClientTick(MinecraftClient client) {
        if (client == null || client.options == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.player == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.isPaused()) {
            sprintKeyWasDown = client.options.keySprint.isPressed();
            return;
        }
        boolean sprintKeyDown = client.options.keySprint.isPressed();
        if (sprintKeyDown && !sprintKeyWasDown) {
            sprintLocked = !sprintLocked;
            client.player.setSprinting(false);
            client.player.sendMessage(
                Text.of("Toggle Sprint: " + (sprintLocked ? "ON" : "OFF")), true);
        }
        sprintKeyWasDown = sprintKeyDown;
        if (sprintLocked) {
            client.player.setSprinting(shouldKeepSprinting(client));
        }
    }

    private boolean shouldKeepSprinting(MinecraftClient client) {
        if (client.currentScreen != null) return false;
        if (client.player == null) return false;
        if (client.player.isSpectator() || client.player.hasVehicle()) return false;
        if (client.player.isSneaking() || client.player.isUsingItem()) return false;
        return client.options.keyForward.isPressed()
            && !client.options.keyBack.isPressed();
    }
}
"""

FABRIC_PRESPLIT_MOD = """\
package asd.itamio.togglesprint;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class ToggleSprintFabricMod implements ClientModInitializer {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(CONTROLLER::onClientTick);
    }
}
"""

# ---------------------------------------------------------------------------
# Fabric split (1.20+): uses Yarn intermediary class_XXX names
# These are the names that the fabric_split adapter uses at source level.
# ---------------------------------------------------------------------------
FABRIC_CONTROLLER = """\
package asd.itamio.togglesprint;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2561;
import net.minecraft.class_310;

@Environment(EnvType.CLIENT)
public final class ToggleSprintController {
    private boolean sprintLocked;
    private boolean sprintKeyWasDown;

    public void onClientTick(class_310 client) {
        if (client == null || client.field_1690 == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.field_1724 == null) {
            sprintLocked = false;
            sprintKeyWasDown = false;
            return;
        }
        if (client.method_1493()) {
            sprintKeyWasDown = client.field_1690.field_1867.method_1434();
            return;
        }
        boolean sprintKeyDown = client.field_1690.field_1867.method_1434();
        if (sprintKeyDown && !sprintKeyWasDown) {
            sprintLocked = !sprintLocked;
            client.field_1690.field_1867.method_23481(false);
            client.field_1724.method_5728(false);
            client.field_1724.method_7353(
                class_2561.method_43470("Toggle Sprint: " + (sprintLocked ? "ON" : "OFF")), true);
        }
        sprintKeyWasDown = sprintKeyDown;
        if (sprintLocked) {
            client.field_1724.method_5728(shouldKeepSprinting(client));
        }
    }

    private boolean shouldKeepSprinting(class_310 client) {
        if (client.field_1755 != null) return false;
        if (client.field_1724 == null) return false;
        if (client.field_1724.method_7325() || client.field_1724.method_5765()) return false;
        if (client.field_1724.method_5715() || client.field_1724.method_6115()) return false;
        return client.field_1690.field_1894.method_1434()
            && !client.field_1690.field_1881.method_1434();
    }
}
"""

FABRIC_MOD = """\
package asd.itamio.togglesprint;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

@Environment(EnvType.CLIENT)
public final class ToggleSprintFabricMod implements ClientModInitializer {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(CONTROLLER::onClientTick);
    }
}
"""

# ---------------------------------------------------------------------------
# NeoForge 1.20.4-1.20.6: ClientTickEvent is in net.neoforged.neoforge.event.TickEvent
# ---------------------------------------------------------------------------
NEOFORGE_1204_CLIENT = """\
package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class ToggleSprintNeoForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintNeoForgeClient() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        CONTROLLER.onClientTick(Minecraft.getInstance());
    }
}
"""

NEOFORGE_1204_MOD = """\
package asd.itamio.togglesprint;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod("togglesprint")
public final class ToggleSprintNeoForgeMod {
    public static final String MOD_ID = "togglesprint";

    public ToggleSprintNeoForgeMod(IEventBus modEventBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(ToggleSprintNeoForgeClient::onClientTick);
        }
    }
}
"""

# ---------------------------------------------------------------------------
# NeoForge 1.21-1.21.8: ClientTickEvent is in net.neoforged.neoforge.client.event
# ---------------------------------------------------------------------------
NEOFORGE_CLIENT = """\
package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class ToggleSprintNeoForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintNeoForgeClient() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        CONTROLLER.onClientTick(Minecraft.getInstance());
    }
}
"""

NEOFORGE_MOD = """\
package asd.itamio.togglesprint;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod("togglesprint")
public final class ToggleSprintNeoForgeMod {
    public static final String MOD_ID = "togglesprint";

    public ToggleSprintNeoForgeMod(IEventBus modEventBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(ToggleSprintNeoForgeClient::onClientTick);
        }
    }
}
"""

# ---------------------------------------------------------------------------
# NeoForge 1.21.9+: FMLEnvironment.dist removed — use @EventBusSubscriber
# ---------------------------------------------------------------------------
NEOFORGE_2119_CLIENT = """\
package asd.itamio.togglesprint;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class ToggleSprintNeoForgeClient {
    private static final ToggleSprintController CONTROLLER = new ToggleSprintController();

    private ToggleSprintNeoForgeClient() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        CONTROLLER.onClientTick(Minecraft.getInstance());
    }
}
"""

NEOFORGE_2119_MOD = """\
package asd.itamio.togglesprint;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod("togglesprint")
public final class ToggleSprintNeoForgeMod {
    public static final String MOD_ID = "togglesprint";

    public ToggleSprintNeoForgeMod(IEventBus modEventBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(ToggleSprintNeoForgeClient::onClientTick);
        }
    }
}
"""

# Shared NeoForge controller (same as Forge legacy — uses Component.literal)
NEOFORGE_CONTROLLER = FORGE_LEGACY_CONTROLLER

# ---------------------------------------------------------------------------
# Target matrix
# Each entry: (folder, mc_version, loader, entrypoint_class, files)
# files: dict of relative_path -> content
# ---------------------------------------------------------------------------

PKG = "asd/itamio/togglesprint"


def forge_files_189():
    return {
        f"src/main/java/{PKG}/ToggleSprintMod.java": FORGE_189_MOD,
    }


def forge_files_1122():
    return {
        f"src/main/java/{PKG}/ToggleSprintMod.java": FORGE_1122_MOD,
    }


def forge_files_1165():
    """Forge 1.16.5: StringTextComponent (no Component.literal)"""
    return {
        f"src/main/java/{PKG}/ToggleSprintForgeMod.java": FORGE_LEGACY_MOD,
        f"src/main/java/{PKG}/ToggleSprintForgeClient.java": FORGE_LEGACY_CLIENT,
        f"src/main/java/{PKG}/ToggleSprintController.java": FORGE_1165_CONTROLLER,
    }


def forge_files_117_118():
    """Forge 1.17.1 - 1.18.1: TextComponent (not Component.literal)"""
    return {
        f"src/main/java/{PKG}/ToggleSprintForgeMod.java": FORGE_LEGACY_MOD,
        f"src/main/java/{PKG}/ToggleSprintForgeClient.java": FORGE_LEGACY_CLIENT,
        f"src/main/java/{PKG}/ToggleSprintController.java": FORGE_117_118_CONTROLLER,
    }


def forge_files_legacy():
    """Forge 1.18.2 - 1.20.6: Component.literal available"""
    return {
        f"src/main/java/{PKG}/ToggleSprintForgeMod.java": FORGE_LEGACY_MOD,
        f"src/main/java/{PKG}/ToggleSprintForgeClient.java": FORGE_LEGACY_CLIENT,
        f"src/main/java/{PKG}/ToggleSprintController.java": FORGE_LEGACY_CONTROLLER,
    }


def forge_files_modern():
    """Forge 1.21 - 1.21.8: same as legacy"""
    return {
        f"src/main/java/{PKG}/ToggleSprintForgeMod.java": FORGE_MODERN_MOD,
        f"src/main/java/{PKG}/ToggleSprintForgeClient.java": FORGE_LEGACY_CLIENT,
        f"src/main/java/{PKG}/ToggleSprintController.java": FORGE_LEGACY_CONTROLLER,
    }


def forge_files_2119():
    """Forge 1.21.9+: @EventBusSubscriber pattern"""
    return {
        f"src/main/java/{PKG}/ToggleSprintForgeMod.java": FORGE_2119_MOD,
        f"src/main/java/{PKG}/ToggleSprintForgeClient.java": FORGE_2119_CLIENT,
        f"src/main/java/{PKG}/ToggleSprintController.java": FORGE_LEGACY_CONTROLLER,
    }


def fabric_presplit_files():
    """Fabric 1.16.5 - 1.19.4: fabric_presplit adapter, Yarn deobf names"""
    return {
        f"src/main/java/{PKG}/ToggleSprintFabricMod.java": FABRIC_PRESPLIT_MOD,
        f"src/main/java/{PKG}/ToggleSprintController.java": FABRIC_PRESPLIT_CONTROLLER,
    }


def fabric_files():
    """Fabric 1.20+: fabric_split adapter, Yarn intermediary class_XXX names"""
    return {
        f"src/main/java/{PKG}/ToggleSprintFabricMod.java": FABRIC_MOD,
        f"src/main/java/{PKG}/ToggleSprintController.java": FABRIC_CONTROLLER,
    }


def neoforge_1204_files():
    """NeoForge 1.20.4-1.20.6: ClientTickEvent in neoforge.client.event"""
    return {
        f"src/main/java/{PKG}/ToggleSprintNeoForgeMod.java": NEOFORGE_1204_MOD,
        f"src/main/java/{PKG}/ToggleSprintNeoForgeClient.java": NEOFORGE_1204_CLIENT,
        f"src/main/java/{PKG}/ToggleSprintController.java": NEOFORGE_CONTROLLER,
    }


def neoforge_files():
    """NeoForge 1.21 - 1.21.8: ClientTickEvent in neoforge.client.event"""
    return {
        f"src/main/java/{PKG}/ToggleSprintNeoForgeMod.java": NEOFORGE_MOD,
        f"src/main/java/{PKG}/ToggleSprintNeoForgeClient.java": NEOFORGE_CLIENT,
        f"src/main/java/{PKG}/ToggleSprintController.java": NEOFORGE_CONTROLLER,
    }


def neoforge_2119_files():
    """NeoForge 1.21.9+: same ClientTickEvent, but FMLEnvironment.dist still works"""
    return {
        f"src/main/java/{PKG}/ToggleSprintNeoForgeMod.java": NEOFORGE_2119_MOD,
        f"src/main/java/{PKG}/ToggleSprintNeoForgeClient.java": NEOFORGE_2119_CLIENT,
        f"src/main/java/{PKG}/ToggleSprintController.java": NEOFORGE_CONTROLLER,
    }


# entrypoint per loader/era
EP_189 = "ToggleSprintMod"
EP_1122 = "ToggleSprintMod"
EP_FORGE_LEGACY = "ToggleSprintForgeMod"
EP_FORGE_MODERN = "ToggleSprintForgeMod"
EP_FORGE_2119 = "ToggleSprintForgeMod"
EP_FABRIC = "ToggleSprintFabricMod"
EP_NEOFORGE = "ToggleSprintNeoForgeMod"

TARGETS = [
    # (folder, mc_version, loader, entrypoint_suffix, files_fn)
    # --- Missing Forge ---
    ("TS189Forge",       "1.8.9",          "forge",    EP_189,          forge_files_189),
    ("TS1165Forge",      "1.16.5",         "forge",    EP_FORGE_LEGACY, forge_files_1165),
    ("TS1171Forge",      "1.17.1",         "forge",    EP_FORGE_LEGACY, forge_files_117_118),
    ("TS1182Forge",      "1.18-1.18.2",    "forge",    EP_FORGE_LEGACY, forge_files_117_118),
    ("TS1194Forge",      "1.19-1.19.4",    "forge",    EP_FORGE_LEGACY, forge_files_legacy),
    ("TS121Forge",       "1.21-1.21.1",    "forge",    EP_FORGE_MODERN, forge_files_modern),
    ("TS1213Forge",      "1.21.3",         "forge",    EP_FORGE_MODERN, forge_files_modern),
    ("TS1214Forge",      "1.21.4",         "forge",    EP_FORGE_MODERN, forge_files_modern),
    ("TS1215Forge",      "1.21.5",         "forge",    EP_FORGE_MODERN, forge_files_modern),
    # --- Missing Fabric ---
    ("TS1165Fabric",     "1.16.5",         "fabric",   EP_FABRIC,       fabric_presplit_files),
    ("TS1171Fabric",     "1.17.1",         "fabric",   EP_FABRIC,       fabric_presplit_files),
    ("TS1182Fabric",     "1.18-1.18.2",    "fabric",   EP_FABRIC,       fabric_presplit_files),
    ("TS1194Fabric",     "1.19-1.19.4",    "fabric",   EP_FABRIC,       fabric_presplit_files),
    # --- Missing NeoForge ---
    ("TS1204NeoForge",   "1.20.4",         "neoforge", EP_NEOFORGE,     neoforge_1204_files),
    ("TS1205NeoForge",   "1.20.5",         "neoforge", EP_NEOFORGE,     neoforge_1204_files),
    ("TS1206NeoForge",   "1.20.6",         "neoforge", EP_NEOFORGE,     neoforge_1204_files),
    ("TS121NeoForge",    "1.21-1.21.1",    "neoforge", EP_NEOFORGE,     neoforge_files),
    ("TS1214NeoForge",   "1.21.2-1.21.8",  "neoforge", EP_NEOFORGE,     neoforge_files),
    ("TS12111NeoForge",  "1.21.9-1.21.11", "neoforge", EP_NEOFORGE,     neoforge_2119_files),
]


def write(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print(f"  {path.relative_to(ROOT)}")


def create_target(folder, mc_version, loader, entrypoint_suffix, files_fn):
    target_dir = BUNDLE_DIR / folder
    print(f"\n[{folder}] mc={mc_version} loader={loader}")

    entrypoint = f"asd.itamio.togglesprint.{entrypoint_suffix}"

    write(target_dir / "mod.txt", (
        f"mod_id={MOD_ID}\n"
        f"name={MOD_NAME}\n"
        f"mod_version={MOD_VERSION}\n"
        f"group={GROUP}\n"
        f"entrypoint_class={entrypoint}\n"
        f"description={DESCRIPTION}\n"
        f"authors={AUTHORS}\n"
        f"license={LICENSE}\n"
        f"homepage={HOMEPAGE}\n"
        f"runtime_side=client\n"
    ))
    write(target_dir / "version.txt", f"minecraft_version={mc_version}\nloader={loader}\n")

    for rel_path, content in files_fn().items():
        write(target_dir / rel_path, content)


def main():
    print(f"Generating toggle-sprint bundle → {BUNDLE_DIR}")
    print(f"Targets: {len(TARGETS)}")

    for folder, mc_version, loader, ep, files_fn in TARGETS:
        create_target(folder, mc_version, loader, ep, files_fn)

    # Create zip
    zip_path = ROOT / "incoming" / "toggle-sprint-all-versions.zip"
    print(f"\nCreating zip: {zip_path}")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for item in sorted(BUNDLE_DIR.rglob("*")):
            if item.is_file():
                zf.write(item, item.relative_to(BUNDLE_DIR))
    print(f"ZIP OK — {zip_path.stat().st_size // 1024} KB")

    print("\nNext steps:")
    print("  git add incoming/toggle-sprint-all-versions/ incoming/toggle-sprint-all-versions.zip")
    print("  git commit -m 'Add toggle-sprint all-versions bundle'")
    print("  git push")
    print("  python3 scripts/run_build.py incoming/toggle-sprint-all-versions.zip --modrinth https://modrinth.com/mod/toggle-sprint")


if __name__ == "__main__":
    main()
