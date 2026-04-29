#!/usr/bin/env python3
"""
Generator for Stackable Totems (up to 64) — all missing versions bundle.
Mod: https://modrinth.com/mod/stackable-totems-(up-to-64)
Server-side only Forge/NeoForge mod. LivingUseTotemEvent first appeared in 1.19.4.
Targets: Forge 1.19.4+, NeoForge 1.20.2+

Run:
    python3 scripts/generate_stackabletotems_bundle.py
    python3 scripts/generate_stackabletotems_bundle.py --failed-only

Already published (skip these):
  1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6  forge
"""

import argparse
import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUNDLE_DIR = ROOT / "incoming" / "stackabletotems-all-versions"
ZIP_PATH = ROOT / "incoming" / "stackabletotems-all-versions.zip"

MOD_ID = "stackabletotems"
MOD_NAME = "Stackable Totems of Undying"
MOD_VERSION = "1.0.0"
GROUP = "net.itamio.stackabletotems"
ENTRYPOINT = f"{GROUP}.StackableTotemsMod"
DESCRIPTION = "Allows Totems of Undying to stack up to 64, consuming one per use."
AUTHORS = "Itamio"
LICENSE = "MIT"
HOMEPAGE = "https://modrinth.com/mod/stackable-totems-(up-to-64)"

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def mod_txt():
    return (
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={GROUP}\nentrypoint_class={ENTRYPOINT}\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side=server\n"
    )

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"


# ===========================================================================
# FORGE 1.19.4–1.20.4
# LivingUseTotemEvent uses @SubscribeEvent + @Cancelable (old EventBus 6)
# Stack size: reflection on "f_41370_" (SRG name for maxStackSize int field)
# ===========================================================================
SRC_119_FORGE = """\
package net.itamio.stackabletotems;

import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = ObfuscationReflectionHelper.findField(
                    net.minecraft.world.item.Item.class, "f_41370_");
                f.setAccessible(true);
                f.set(Items.TOTEM_OF_UNDYING, 64);
            } catch (Exception e) {
                try {
                    Field f = net.minecraft.world.item.Item.class
                        .getDeclaredField("maxStackSize");
                    f.setAccessible(true);
                    f.set(Items.TOTEM_OF_UNDYING, 64);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    @SubscribeEvent
    public void onUseTotem(LivingUseTotemEvent event) {
        net.minecraft.world.item.ItemStack totem = event.getTotem();
        if (totem.getCount() > 1) {
            totem.shrink(1);
            event.setCanceled(true);
        }
    }
}
"""

# ===========================================================================
# FORGE 1.20.5–1.21.5
# LivingUseTotemEvent uses CancellableEventBus (EventBus 6 with BUS field)
# Stack size: DataComponents.MAX_STACK_SIZE via reflection on "components" field
# ===========================================================================
SRC_1205_FORGE = """\
package net.itamio.stackabletotems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.world.item.Item.class
                    .getDeclaredField("components");
                f.setAccessible(true);
                net.minecraft.core.component.DataComponentMap map =
                    (net.minecraft.core.component.DataComponentMap) f.get(Items.TOTEM_OF_UNDYING);
                net.minecraft.core.component.DataComponentMap newMap =
                    net.minecraft.core.component.DataComponentMap.builder()
                        .addAll(map)
                        .set(DataComponents.MAX_STACK_SIZE, 64)
                        .build();
                f.set(Items.TOTEM_OF_UNDYING, newMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @SubscribeEvent
    public void onUseTotem(LivingUseTotemEvent event) {
        net.minecraft.world.item.ItemStack totem = event.getTotem();
        if (totem.getCount() > 1) {
            totem.shrink(1);
            event.setCanceled(true);
        }
    }
}
"""

# ===========================================================================
# FORGE 1.21.6–1.21.8 — EventBus 7
# FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(handler)
# LivingUseTotemEvent.BUS.addListener(alwaysCancelling=true, handler)
# Return true to cancel
# ===========================================================================
SRC_1216_FORGE = """\
package net.itamio.stackabletotems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod(FMLJavaModLoadingContext context) {
        FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(this::setup);
        LivingUseTotemEvent.BUS.addListener(true, StackableTotemsMod::onUseTotem);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.world.item.Item.class
                    .getDeclaredField("components");
                f.setAccessible(true);
                net.minecraft.core.component.DataComponentMap map =
                    (net.minecraft.core.component.DataComponentMap) f.get(Items.TOTEM_OF_UNDYING);
                net.minecraft.core.component.DataComponentMap newMap =
                    net.minecraft.core.component.DataComponentMap.builder()
                        .addAll(map)
                        .set(DataComponents.MAX_STACK_SIZE, 64)
                        .build();
                f.set(Items.TOTEM_OF_UNDYING, newMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static boolean onUseTotem(LivingUseTotemEvent event) {
        net.minecraft.world.item.ItemStack totem = event.getTotem();
        if (totem.getCount() > 1) {
            totem.shrink(1);
            return true;
        }
        return false;
    }
}
"""

# ===========================================================================
# FORGE 1.21.9–26.1.2 — EventBus 7, record-based LivingUseTotemEvent
# Same pattern as 1.21.6-1.21.8
# ===========================================================================
SRC_1219_FORGE = """\
package net.itamio.stackabletotems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod(FMLJavaModLoadingContext context) {
        FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(this::setup);
        LivingUseTotemEvent.BUS.addListener(true, StackableTotemsMod::onUseTotem);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.world.item.Item.class
                    .getDeclaredField("components");
                f.setAccessible(true);
                net.minecraft.core.component.DataComponentMap map =
                    (net.minecraft.core.component.DataComponentMap) f.get(Items.TOTEM_OF_UNDYING);
                net.minecraft.core.component.DataComponentMap newMap =
                    net.minecraft.core.component.DataComponentMap.builder()
                        .addAll(map)
                        .set(DataComponents.MAX_STACK_SIZE, 64)
                        .build();
                f.set(Items.TOTEM_OF_UNDYING, newMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static boolean onUseTotem(LivingUseTotemEvent event) {
        net.minecraft.world.item.ItemStack totem = event.getTotem();
        if (totem.getCount() > 1) {
            totem.shrink(1);
            return true;
        }
        return false;
    }
}
"""


# ===========================================================================
# NEOFORGE 1.20.2–1.20.4
# LivingUseTotemEvent from net.neoforged.neoforge.event.entity.living
# Uses @SubscribeEvent + ICancellableEvent.setCanceled(true)
# Stack size: reflection on "f_41370_" (SRG name for maxStackSize)
# ===========================================================================
SRC_120_NEO = """\
package net.itamio.stackabletotems;

import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod(IEventBus modBus) {
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.world.item.Item.class
                    .getDeclaredField("f_41370_");
                f.setAccessible(true);
                f.set(Items.TOTEM_OF_UNDYING, 64);
            } catch (Exception e) {
                try {
                    Field f = net.minecraft.world.item.Item.class
                        .getDeclaredField("maxStackSize");
                    f.setAccessible(true);
                    f.set(Items.TOTEM_OF_UNDYING, 64);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    @SubscribeEvent
    public void onUseTotem(LivingUseTotemEvent event) {
        net.minecraft.world.item.ItemStack totem = event.getTotem();
        if (totem.getCount() > 1) {
            totem.shrink(1);
            event.setCanceled(true);
        }
    }
}
"""

# ===========================================================================
# NEOFORGE 1.20.5–1.21.8
# LivingUseTotemEvent from net.neoforged.neoforge.event.entity.living
# Stack size: DataComponents.MAX_STACK_SIZE via reflection on "components"
# ===========================================================================
SRC_1205_NEO = """\
package net.itamio.stackabletotems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod(IEventBus modBus) {
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.world.item.Item.class
                    .getDeclaredField("components");
                f.setAccessible(true);
                net.minecraft.core.component.DataComponentMap map =
                    (net.minecraft.core.component.DataComponentMap) f.get(Items.TOTEM_OF_UNDYING);
                net.minecraft.core.component.DataComponentMap newMap =
                    net.minecraft.core.component.DataComponentMap.builder()
                        .addAll(map)
                        .set(DataComponents.MAX_STACK_SIZE, 64)
                        .build();
                f.set(Items.TOTEM_OF_UNDYING, newMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @SubscribeEvent
    public void onUseTotem(LivingUseTotemEvent event) {
        net.minecraft.world.item.ItemStack totem = event.getTotem();
        if (totem.getCount() > 1) {
            totem.shrink(1);
            event.setCanceled(true);
        }
    }
}
"""

# ===========================================================================
# NEOFORGE 1.21.9–1.21.11
# ModContainer required in constructor
# LivingUseTotemEvent still uses @SubscribeEvent + ICancellableEvent
# ===========================================================================
SRC_1219_NEO = """\
package net.itamio.stackabletotems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.world.item.Item.class
                    .getDeclaredField("components");
                f.setAccessible(true);
                net.minecraft.core.component.DataComponentMap map =
                    (net.minecraft.core.component.DataComponentMap) f.get(Items.TOTEM_OF_UNDYING);
                net.minecraft.core.component.DataComponentMap newMap =
                    net.minecraft.core.component.DataComponentMap.builder()
                        .addAll(map)
                        .set(DataComponents.MAX_STACK_SIZE, 64)
                        .build();
                f.set(Items.TOTEM_OF_UNDYING, newMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @SubscribeEvent
    public void onUseTotem(LivingUseTotemEvent event) {
        net.minecraft.world.item.ItemStack totem = event.getTotem();
        if (totem.getCount() > 1) {
            totem.shrink(1);
            event.setCanceled(true);
        }
    }
}
"""

# ===========================================================================
# NEOFORGE 26.1–26.1.2
# Standalone @EventBusSubscriber, ModContainer required
# FMLEnvironment.getDist() from net.neoforged.fml.loading
# ===========================================================================
SRC_261_NEO = """\
package net.itamio.stackabletotems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.world.item.Item.class
                    .getDeclaredField("components");
                f.setAccessible(true);
                net.minecraft.core.component.DataComponentMap map =
                    (net.minecraft.core.component.DataComponentMap) f.get(Items.TOTEM_OF_UNDYING);
                net.minecraft.core.component.DataComponentMap newMap =
                    net.minecraft.core.component.DataComponentMap.builder()
                        .addAll(map)
                        .set(DataComponents.MAX_STACK_SIZE, 64)
                        .build();
                f.set(Items.TOTEM_OF_UNDYING, newMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @SubscribeEvent
    public void onUseTotem(LivingUseTotemEvent event) {
        net.minecraft.world.item.ItemStack totem = event.getTotem();
        if (totem.getCount() > 1) {
            totem.shrink(1);
            event.setCanceled(true);
        }
    }
}
"""


# ===========================================================================
# FORGE 1.16.5–1.19.2 — REFLECTION ONLY (no LivingUseTotemEvent)
# Vanilla LivingEntity.tryUseTotem() already calls itemStack.shrink(1) / decrement(1)
# so stacking works correctly once maxStackSize is set to 64.
# We only need to set the field via reflection at startup.
# Uses FMLCommonSetupEvent (EventBus 6 style — getModEventBus())
# ===========================================================================
SRC_REFLECT_ONLY = """\
package net.itamio.stackabletotems;

import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.world.item.Item.class
                    .getDeclaredField("f_41370_");
                f.setAccessible(true);
                f.set(Items.TOTEM_OF_UNDYING, 64);
            } catch (Exception e) {
                try {
                    Field f = net.minecraft.world.item.Item.class
                        .getDeclaredField("maxStackSize");
                    f.setAccessible(true);
                    f.set(Items.TOTEM_OF_UNDYING, 64);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
}
"""

# 1.16.5 uses net.minecraft.item (pre-1.17 package)
SRC_REFLECT_ONLY_1165 = """\
package net.itamio.stackabletotems;

import net.minecraft.item.Items;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.lang.reflect.Field;

@Mod("stackabletotems")
public class StackableTotemsMod {
    public StackableTotemsMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Field f = net.minecraft.item.Item.class
                    .getDeclaredField("f_41370_");
                f.setAccessible(true);
                f.set(Items.TOTEM_OF_UNDYING, 64);
            } catch (Exception e) {
                try {
                    Field f = net.minecraft.item.Item.class
                        .getDeclaredField("maxStackSize");
                    f.setAccessible(true);
                    f.set(Items.TOTEM_OF_UNDYING, 64);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
}
"""

# ===========================================================================
# TARGETS
# ===========================================================================
TARGETS = [
    # ---- FORGE 1.16.5–1.19.2 (reflection-only: vanilla already shrinks stack by 1) ----
    # LivingUseTotemEvent does NOT exist in these Forge versions.
    # Vanilla LivingEntity.tryUseTotem() already calls itemStack.decrement(1) / shrink(1).
    # We only need to set maxStackSize=64 via reflection so players can hold >1 totem.
    ("StackableTotems-1.16.5-forge",  "1.16.5",  "forge", SRC_REFLECT_ONLY_1165, GROUP, ENTRYPOINT),
    ("StackableTotems-1.17.1-forge",  "1.17.1",  "forge", SRC_REFLECT_ONLY, GROUP, ENTRYPOINT),
    ("StackableTotems-1.18-forge",    "1.18",    "forge", SRC_REFLECT_ONLY, GROUP, ENTRYPOINT),
    ("StackableTotems-1.18.1-forge",  "1.18.1",  "forge", SRC_REFLECT_ONLY, GROUP, ENTRYPOINT),
    ("StackableTotems-1.18.2-forge",  "1.18.2",  "forge", SRC_REFLECT_ONLY, GROUP, ENTRYPOINT),
    ("StackableTotems-1.19-forge",    "1.19",    "forge", SRC_REFLECT_ONLY, GROUP, ENTRYPOINT),
    ("StackableTotems-1.19.1-forge",  "1.19.1",  "forge", SRC_REFLECT_ONLY, GROUP, ENTRYPOINT),
    # ---- FORGE 1.19.3-1.19.4 (LivingUseTotemEvent added in Forge 44.x) ----
    ("StackableTotems-1.19.3-forge",  "1.19.3",  "forge", SRC_119_FORGE,   GROUP, ENTRYPOINT),
    ("StackableTotems-1.19.4-forge",  "1.19.4",  "forge", SRC_119_FORGE,   GROUP, ENTRYPOINT),
    # ---- FORGE 1.21-1.21.1 (DataComponents era, EventBus 6 BUS pattern) ----
    ("StackableTotems-1.21-forge",    "1.21",    "forge", SRC_1205_FORGE,  GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.1-forge",  "1.21.1",  "forge", SRC_1205_FORGE,  GROUP, ENTRYPOINT),
    # ---- FORGE 1.21.2-1.21.8 (DataComponents, EventBus 6 BUS) ----
    ("StackableTotems-1.21.3-forge",  "1.21.3",  "forge", SRC_1205_FORGE,  GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.4-forge",  "1.21.4",  "forge", SRC_1205_FORGE,  GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.5-forge",  "1.21.5",  "forge", SRC_1205_FORGE,  GROUP, ENTRYPOINT),
    # ---- FORGE 1.21.6-1.21.8 (EventBus 7) ----
    ("StackableTotems-1.21.6-forge",  "1.21.6",  "forge", SRC_1216_FORGE,  GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.7-forge",  "1.21.7",  "forge", SRC_1216_FORGE,  GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.8-forge",  "1.21.8",  "forge", SRC_1216_FORGE,  GROUP, ENTRYPOINT),
    # ---- FORGE 1.21.9-1.21.11 (EventBus 7, record-based) ----
    ("StackableTotems-1.21.9-forge",  "1.21.9",  "forge", SRC_1219_FORGE,  GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.10-forge", "1.21.10", "forge", SRC_1219_FORGE,  GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.11-forge", "1.21.11", "forge", SRC_1219_FORGE,  GROUP, ENTRYPOINT),
    # ---- FORGE 26.1.2 (EventBus 7, record-based) ----
    ("StackableTotems-26.1.2-forge",  "26.1.2",  "forge", SRC_1219_FORGE,  GROUP, ENTRYPOINT),
    # ---- NEOFORGE 1.20.2-1.20.4 (pre-DataComponents, SRG field) ----
    ("StackableTotems-1.20.2-neoforge", "1.20.2", "neoforge", SRC_120_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.20.4-neoforge", "1.20.4", "neoforge", SRC_120_NEO, GROUP, ENTRYPOINT),
    # ---- NEOFORGE 1.20.5-1.21.8 (DataComponents, @SubscribeEvent) ----
    ("StackableTotems-1.20.5-neoforge", "1.20.5", "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.20.6-neoforge", "1.20.6", "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21-neoforge",   "1.21",   "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.1-neoforge", "1.21.1", "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.2-neoforge", "1.21.2", "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.3-neoforge", "1.21.3", "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.4-neoforge", "1.21.4", "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.5-neoforge", "1.21.5", "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.6-neoforge", "1.21.6", "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.7-neoforge", "1.21.7", "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.8-neoforge", "1.21.8", "neoforge", SRC_1205_NEO, GROUP, ENTRYPOINT),
    # ---- NEOFORGE 1.21.9-1.21.11 (ModContainer required) ----
    ("StackableTotems-1.21.9-neoforge",  "1.21.9",  "neoforge", SRC_1219_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.10-neoforge", "1.21.10", "neoforge", SRC_1219_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-1.21.11-neoforge", "1.21.11", "neoforge", SRC_1219_NEO, GROUP, ENTRYPOINT),
    # ---- NEOFORGE 26.1-26.1.2 ----
    ("StackableTotems-26.1-neoforge",   "26.1",   "neoforge", SRC_261_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-26.1.1-neoforge", "26.1.1", "neoforge", SRC_261_NEO, GROUP, ENTRYPOINT),
    ("StackableTotems-26.1.2-neoforge", "26.1.2", "neoforge", SRC_261_NEO, GROUP, ENTRYPOINT),
]


def get_failed_targets():
    runs_dir = ROOT / "ModCompileRuns"
    if not runs_dir.exists():
        return None
    runs = sorted(runs_dir.iterdir(), reverse=True)
    for run in runs:
        summary = run / "SUMMARY.md"
        if not summary.exists():
            continue
        text = summary.read_text()
        failed = set()
        for t in TARGETS:
            folder = t[0]
            # job name is folder lowercased with dots→dashes
            job = folder.lower().replace(".", "-")
            if f"❌" in text and job in text:
                failed.add(folder)
            elif "FAILED" in text and folder in text:
                failed.add(folder)
        if failed:
            print(f"Reading failures from SUMMARY: {run.name}")
            return failed
        # Also try result.json
        result_file = run / "result.json"
        if result_file.exists():
            try:
                data = json.loads(result_file.read_text())
                failed = set()
                for job in data.get("jobs", []):
                    if job.get("status") != "success":
                        name = job.get("name", "")
                        for t in TARGETS:
                            if t[0].lower().replace(".", "-") in name.lower():
                                failed.add(t[0])
                if failed:
                    print(f"Reading failures from result.json: {run.name}")
                    return failed
            except Exception:
                pass
    return None


def write_target(folder_name, mc_version, loader, src, group, entrypoint):
    pkg_path = group.replace(".", "/")
    base = BUNDLE_DIR / folder_name
    write(base / "mod.txt", mod_txt())
    write(base / "version.txt", version_txt(mc_version, loader))
    write(base / "src" / "main" / "java" / pkg_path / "StackableTotemsMod.java", src)


def build_zip(targets_to_include):
    ZIP_PATH.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as zf:
        for t in targets_to_include:
            folder_name = t[0]
            folder = BUNDLE_DIR / folder_name
            for file in sorted(folder.rglob("*")):
                if file.is_file():
                    zf.write(file, file.relative_to(BUNDLE_DIR))
    print(f"Wrote {ZIP_PATH} ({ZIP_PATH.stat().st_size // 1024} KB, {len(targets_to_include)} targets)")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed-only", action="store_true")
    args = parser.parse_args()

    if args.failed_only:
        failed = get_failed_targets()
        if failed is None:
            print("No previous run found — generating all targets")
            targets = TARGETS
        elif not failed:
            print("No failed targets in last run — nothing to do")
            sys.exit(0)
        else:
            targets = [t for t in TARGETS if t[0] in failed]
            if not targets:
                print("Warning: failed set did not match any TARGETS — generating all")
                targets = TARGETS
    else:
        targets = TARGETS

    if BUNDLE_DIR.exists():
        shutil.rmtree(BUNDLE_DIR)
    BUNDLE_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Generating {len(targets)} target(s)...")
    for t in targets:
        folder_name, mc_version, loader, src, group, entrypoint = t
        print(f"  {folder_name}")
        write_target(folder_name, mc_version, loader, src, group, entrypoint)

    build_zip(targets)
    print("Done.")


if __name__ == "__main__":
    main()
