#!/usr/bin/env python3
"""
Generates the Heart System (Lifesteal Parrot Mod) bundle for all missing MC versions.
Mod: https://modrinth.com/mod/lifesteal-parrot-mod
Server-side: lose a heart on death, gain one on player kill, banned at 0 hearts.

Already published: 1.12.2 forge

Run:
    python3 scripts/generate_heartsystem_bundle.py
    python3 scripts/generate_heartsystem_bundle.py --failed-only
"""

import argparse, json, shutil, sys, zipfile
from pathlib import Path

ROOT       = Path(__file__).resolve().parents[1]
BUNDLE_DIR = ROOT / "incoming" / "heartsystem-all-versions"
ZIP_PATH   = ROOT / "incoming" / "heartsystem-all-versions.zip"

MOD_ID      = "heartsystem"
MOD_NAME    = "Heart System"
MOD_VERSION = "1.0.0"
GROUP       = "asd.itamio.heartsystem"
DESCRIPTION = "Heart-based permadeath: lose a heart on death, gain a heart on player kill. Reach 0 hearts and you are permanently banned."
AUTHORS     = "Itamio"
LICENSE     = "MIT"
HOMEPAGE    = "https://modrinth.com/mod/lifesteal-parrot-mod"


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def mod_txt(entrypoint: str) -> str:
    return (
        f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
        f"group={GROUP}\nentrypoint_class={entrypoint}\n"
        f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
        f"homepage={HOMEPAGE}\nruntime_side=server\n"
    )

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"


# ===========================================================================
# 1.8.9 FORGE — Java 6, no diamond <>, no lambdas
# getCommandSenderName() does NOT exist — use getGameProfile().getName()
# ChatComponentText, MinecraftServer.getServer(), configurationManager
# ===========================================================================
SRC_189_FORGE = {
"HeartSystemMod.java": """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = HeartSystemMod.MOD_ID, name = "Heart System", version = "1.0.0",
     acceptedMinecraftVersions = "[1.8.9]")
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static Logger logger;
    public static HeartConfig config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new HeartConfig(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new HeartEventHandler());
    }
}
""",
"HeartEventHandler.java": """\
package asd.itamio.heartsystem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListBans;
import net.minecraft.server.management.UserListBansEntry;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        EntityPlayer player = event.entityPlayer;
        if (player.worldObj.isRemote) return;
        String uuidStr = event.playerUUID;
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), HeartSystemMod.config.getStartHearts());
        }
    }

    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        EntityPlayer player = event.entityPlayer;
        if (player.worldObj.isRemote) return;
        String uuidStr = event.playerUUID;
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        EntityPlayer newPlayer = event.entityPlayer;
        if (newPlayer.worldObj.isRemote) return;
        if (!event.wasDeath) return;
        UUID uuid = newPlayer.getUniqueID();
        if (!HeartStorage.get().has(uuid)) return;
        if (newPlayer instanceof EntityPlayerMP) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData.applyMaxHealth((EntityPlayerMP) newPlayer, hearts);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.entity.worldObj.isRemote) return;
        if (!(event.entity instanceof EntityPlayerMP)) return;
        EntityPlayerMP deadPlayer = (EntityPlayerMP) event.entity;
        UUID deadUUID = deadPlayer.getUniqueID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        String deadName = deadPlayer.getGameProfile().getName();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                server.getConfigurationManager().sendChatMsg(
                    new ChatComponentText("\\u00a7c[HeartSystem] " + deadName + " has been permanently banned (0 hearts)."));
                UserListBans banList = server.getConfigurationManager().getBannedPlayers();
                UserListBansEntry banEntry = new UserListBansEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadName),
                    null, null, null, "Permadeath: ran out of hearts");
                banList.addEntry(banEntry);
            }
            deadPlayer.playerNetServerHandler.kickPlayerFromServer(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts.");
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.addChatMessage(new ChatComponentText(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }
        DamageSource source = event.source;
        Entity killer = source.getEntity();
        if (killer instanceof EntityPlayerMP) {
            EntityPlayerMP killerPlayer = (EntityPlayerMP) killer;
            UUID killerUUID = killerPlayer.getUniqueID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = HeartSystemMod.config.getStartHearts();
            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.addChatMessage(new ChatComponentText(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.addChatMessage(new ChatComponentText(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
""",
"HeartStorage.java": """\
package asd.itamio.heartsystem;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    public static HeartStorage get() { return INSTANCE; }
    private final Map cache = new HashMap();
    private HeartStorage() {}

    public int load(String playerUUID, File playerFile) {
        UUID uuid = UUID.fromString(playerUUID);
        if (playerFile.exists()) {
            try {
                NBTTagCompound tag = CompressedStreamTools.read(playerFile);
                if (tag != null && tag.hasKey("hearts")) {
                    int h = tag.getInteger("hearts");
                    cache.put(uuid, Integer.valueOf(h));
                    return h;
                }
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, Integer.valueOf(hearts));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("hearts", hearts);
        try {
            CompressedStreamTools.write(tag, playerFile);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) {
        Integer h = (Integer) cache.get(uuid);
        return h != null ? h.intValue() : -1;
    }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, Integer.valueOf(hearts)); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
""",
"HeartData.java": """\
package asd.itamio.heartsystem;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";

    public static void applyMaxHealth(EntityPlayerMP player, int hearts) {
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        if (attr == null) return;
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(attr.getModifier(MODIFIER_UUID));
        }
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, 0);
        attr.applyModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }
}
""",
"HeartConfig.java": """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class HeartConfig {
    private static final String CAT = Configuration.CATEGORY_GENERAL;
    private final Configuration config;
    private int startHearts, maxHearts, minHearts;

    public HeartConfig(File configFile) {
        config = new Configuration(configFile);
        config.load();
        startHearts = config.getInt("startHearts", CAT, 10, 1, 100, "Hearts a new player starts with.");
        maxHearts   = config.getInt("maxHearts",   CAT, 20, 1, 100, "Maximum hearts a player can have.");
        minHearts   = config.getInt("minHearts",   CAT,  0, 0,  99, "Minimum hearts before permadeath.");
        if (config.hasChanged()) config.save();
    }

    public int getStartHearts() { return startHearts; }
    public int getMaxHearts()   { return maxHearts; }
    public int getMinHearts()   { return minHearts; }
}
""",
}


# ===========================================================================
# 1.12.2 FORGE — same as original working version
# ===========================================================================
SRC_1122_FORGE = {
"HeartSystemMod.java": """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = HeartSystemMod.MOD_ID, name = "Heart System", version = "1.0.0",
     acceptedMinecraftVersions = "[1.12,1.12.2]")
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static Logger logger;
    public static HeartConfig config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new HeartConfig(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new HeartEventHandler());
    }
}
""",
"HeartEventHandler.java": """\
package asd.itamio.heartsystem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListBans;
import net.minecraft.server.management.UserListBansEntry;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) return;
        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), HeartSystemMod.config.getStartHearts());
        }
    }

    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) return;
        String uuidStr = event.getPlayerUUID();
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        EntityPlayer newPlayer = event.getEntityPlayer();
        if (newPlayer.world.isRemote) return;
        if (!event.isWasDeath()) return;
        UUID uuid = newPlayer.getUniqueID();
        if (!HeartStorage.get().has(uuid)) return;
        if (newPlayer instanceof EntityPlayerMP) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData.applyMaxHealth((EntityPlayerMP) newPlayer, hearts);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().world.isRemote) return;
        if (!(event.getEntity() instanceof EntityPlayerMP)) return;
        EntityPlayerMP deadPlayer = (EntityPlayerMP) event.getEntity();
        UUID deadUUID = deadPlayer.getUniqueID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                server.getPlayerList().sendMessage(new TextComponentString(
                    "\\u00a7c[HeartSystem] " + deadPlayer.getName() + " has been permanently banned (0 hearts)."));
                UserListBans banList = server.getPlayerList().getBannedPlayers();
                banList.addEntry(new UserListBansEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName()),
                    null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.connection.disconnect(new TextComponentString(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(new TextComponentString(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }
        DamageSource source = event.getSource();
        Entity killer = source.getTrueSource();
        if (killer instanceof EntityPlayerMP) {
            EntityPlayerMP killerPlayer = (EntityPlayerMP) killer;
            UUID killerUUID = killerPlayer.getUniqueID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = HeartSystemMod.config.getStartHearts();
            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendMessage(new TextComponentString(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.sendMessage(new TextComponentString(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
""",
"HeartStorage.java": """\
package asd.itamio.heartsystem;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    public static HeartStorage get() { return INSTANCE; }
    private final Map<UUID, Integer> cache = new HashMap<UUID, Integer>();
    private HeartStorage() {}

    public int load(String playerUUID, File playerFile) {
        UUID uuid = UUID.fromString(playerUUID);
        if (playerFile.exists()) {
            try {
                NBTTagCompound tag = CompressedStreamTools.read(playerFile);
                if (tag != null && tag.hasKey("hearts")) {
                    int h = tag.getInteger("hearts");
                    cache.put(uuid, h);
                    return h;
                }
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("hearts", hearts);
        try {
            CompressedStreamTools.write(tag, playerFile);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
""",
"HeartData.java": """\
package asd.itamio.heartsystem;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";

    public static void applyMaxHealth(EntityPlayerMP player, int hearts) {
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (attr == null) return;
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(attr.getModifier(MODIFIER_UUID));
        }
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, 0);
        attr.applyModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }
}
""",
"HeartConfig.java": """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class HeartConfig {
    private static final String CAT = Configuration.CATEGORY_GENERAL;
    private final Configuration config;
    private int startHearts, maxHearts, minHearts;

    public HeartConfig(File configFile) {
        config = new Configuration(configFile);
        config.load();
        startHearts = config.getInt("startHearts", CAT, 10, 1, 100, "Hearts a new player starts with.");
        maxHearts   = config.getInt("maxHearts",   CAT, 20, 1, 100, "Maximum hearts a player can have.");
        minHearts   = config.getInt("minHearts",   CAT,  0, 0,  99, "Minimum hearts before permadeath.");
        if (config.hasChanged()) config.save();
    }

    public int getStartHearts() { return startHearts; }
    public int getMaxHearts()   { return maxHearts; }
    public int getMinHearts()   { return minHearts; }
}
""",
}


# ===========================================================================
# FORGE 1.16.5–1.18.2 — ForgeConfigSpec, StringTextComponent/TextComponent
# UserBanList/UserBanListEntry in net.minecraft.server.players (1.16.5+)
# broadcastMessage(Component, ChatType, UUID) in 1.17+
# broadcastMessage(ITextComponent, ChatType, UUID) in 1.16.5
# player.level is a PUBLIC FIELD in 1.16.5–1.19.4 (NOT a method)
# NbtIo.read(File) / NbtIo.write(tag, File) — File overloads exist in 1.17–1.20.2
# Attributes.MAX_HEALTH, AttributeModifier.Operation.ADDITION (1.16.5–1.20.6)
# ===========================================================================

# Shared HeartStorage for Forge 1.17–1.20.2 (NbtIo with File overload)
# NbtIo.read(File) returns CompoundTag (nullable), getInt returns int
_FORGE_STORAGE_NBTIO_FILE = """\
package asd.itamio.heartsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    public static HeartStorage get() { return INSTANCE; }
    private final Map<UUID, Integer> cache = new HashMap<>();
    private HeartStorage() {}

    public int load(String playerUUID, File playerFile) {
        UUID uuid = UUID.fromString(playerUUID);
        if (playerFile.exists()) {
            try {
                CompoundTag tag = NbtIo.read(playerFile);
                if (tag != null && tag.contains("hearts")) {
                    int h = tag.getInt("hearts");
                    cache.put(uuid, h);
                    return h;
                }
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        CompoundTag tag = new CompoundTag();
        tag.putInt("hearts", hearts);
        try {
            NbtIo.write(tag, playerFile);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
"""

# Shared HeartStorage for Forge/NeoForge 1.20.3–1.21.8 (NbtIo requires Path, getInt returns int)
_FORGE_STORAGE_NBTIO_PATH = """\
package asd.itamio.heartsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    public static HeartStorage get() { return INSTANCE; }
    private final Map<UUID, Integer> cache = new HashMap<>();
    private HeartStorage() {}

    public int load(String playerUUID, File playerFile) {
        UUID uuid = UUID.fromString(playerUUID);
        if (playerFile.exists()) {
            try {
                CompoundTag tag = NbtIo.read(playerFile.toPath());
                if (tag != null && tag.contains("hearts")) {
                    int h = tag.getInt("hearts");
                    cache.put(uuid, h);
                    return h;
                }
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        CompoundTag tag = new CompoundTag();
        tag.putInt("hearts", hearts);
        try {
            NbtIo.write(tag, playerFile.toPath());
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
"""

# HeartStorage for 1.21.9+ (NbtIo Path, getInt returns Optional<Integer>)
_FORGE_STORAGE_NBTIO_PATH_OPT = """\
package asd.itamio.heartsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    public static HeartStorage get() { return INSTANCE; }
    private final Map<UUID, Integer> cache = new HashMap<>();
    private HeartStorage() {}

    public int load(String playerUUID, File playerFile) {
        UUID uuid = UUID.fromString(playerUUID);
        if (playerFile.exists()) {
            try {
                CompoundTag tag = NbtIo.read(playerFile.toPath());
                if (tag != null && tag.contains("hearts")) {
                    int h = tag.getInt("hearts").orElse(-1);
                    if (h >= 0) { cache.put(uuid, h); return h; }
                }
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        CompoundTag tag = new CompoundTag();
        tag.putInt("hearts", hearts);
        try {
            NbtIo.write(tag, playerFile.toPath());
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
"""

# HeartData for Forge 1.16.5–1.20.4 (AttributeModifier with UUID, Operation.ADDITION)
_FORGE_DATA_UUID_ADDITION = """\
package asd.itamio.heartsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";

    public static void applyMaxHealth(ServerPlayer player, int hearts) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(MODIFIER_UUID);
        }
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }
}
"""

# HeartData for Forge 1.20.5–1.20.6 (Operation.ADD_VALUE, still UUID-based constructor)
_FORGE_DATA_UUID_ADDVALUE = """\
package asd.itamio.heartsystem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";

    public static void applyMaxHealth(ServerPlayer player, int hearts) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(MODIFIER_UUID);
        }
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, AttributeModifier.Operation.ADD_VALUE);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }
}
"""

# HeartData for Forge/NeoForge 1.21–1.21.8 (AttributeModifier record with ResourceLocation id, ADD_VALUE)
_FORGE_DATA_RL_ADDVALUE = """\
package asd.itamio.heartsystem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class HeartData {
    private static final ResourceLocation MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath("heartsystem", "maxhealth");

    public static void applyMaxHealth(ServerPlayer player, int hearts) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MODIFIER_ID);
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }
}
"""

# HeartData for 1.21.9–1.21.11 (same as above — ResourceLocation still exists)
_FORGE_DATA_RL_ADDVALUE_1219 = _FORGE_DATA_RL_ADDVALUE

# HeartData for 26.x (ResourceLocation renamed to Identifier)
_FORGE_DATA_IDENTIFIER_ADDVALUE = """\
package asd.itamio.heartsystem;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class HeartData {
    private static final Identifier MODIFIER_ID =
        Identifier.fromNamespaceAndPath("heartsystem", "maxhealth");

    public static void applyMaxHealth(ServerPlayer player, int hearts) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MODIFIER_ID);
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }
}
"""

# ForgeConfigSpec config for 1.16.5–1.21.5 (ModLoadingContext.get().registerConfig)
_FORGE_CONFIG_MODLOADINGCTX = """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class HeartConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.IntValue START_HEARTS;
    private static final ForgeConfigSpec.IntValue MAX_HEARTS;
    private static final ForgeConfigSpec.IntValue MIN_HEARTS;
    private static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("Heart System Configuration");
        START_HEARTS = BUILDER.comment("Hearts a new player starts with (1 heart = 2 HP)").defineInRange("startHearts", 10, 1, 100);
        MAX_HEARTS   = BUILDER.comment("Maximum hearts a player can have").defineInRange("maxHearts", 20, 1, 100);
        MIN_HEARTS   = BUILDER.comment("Minimum hearts before permadeath").defineInRange("minHearts", 0, 0, 99);
        SPEC = BUILDER.build();
    }

    public HeartConfig() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    public int getStartHearts() { return START_HEARTS.get(); }
    public int getMaxHearts()   { return MAX_HEARTS.get(); }
    public int getMinHearts()   { return MIN_HEARTS.get(); }
}
"""

# ForgeConfigSpec config for 1.21.6–1.21.8 (FMLJavaModLoadingContext constructor arg)
_FORGE_CONFIG_FMLJAVAMOD = """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class HeartConfig {
    private final ForgeConfigSpec.IntValue startHearts;
    private final ForgeConfigSpec.IntValue maxHearts;
    private final ForgeConfigSpec.IntValue minHearts;

    public HeartConfig(FMLJavaModLoadingContext context) {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Heart System Configuration");
        startHearts = builder.comment("Hearts a new player starts with (1 heart = 2 HP)").defineInRange("startHearts", 10, 1, 100);
        maxHearts   = builder.comment("Maximum hearts a player can have").defineInRange("maxHearts", 20, 1, 100);
        minHearts   = builder.comment("Minimum hearts before permadeath").defineInRange("minHearts", 0, 0, 99);
        context.registerConfig(ModConfig.Type.SERVER, builder.build());
    }

    public int getStartHearts() { return startHearts.get(); }
    public int getMaxHearts()   { return maxHearts.get(); }
    public int getMinHearts()   { return minHearts.get(); }
}
"""


# ===========================================================================
# FORGE 1.16.5 — StringTextComponent, net.minecraft.server.players ban classes
# broadcastMessage(ITextComponent, ChatType, UUID) — 1.16.5 signature
# player.level is a FIELD (not method) in 1.16.5
# ===========================================================================
SRC_1165_FORGE = {
"HeartSystemMod.java": """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HeartSystemMod.MOD_ID)
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    public HeartSystemMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        config = new HeartConfig();
        MinecraftForge.EVENT_BUS.register(new HeartEventHandler());
    }
}
""",
"HeartEventHandler.java": """\
package asd.itamio.heartsystem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        PlayerEntity player = event.getPlayer();
        if (player.level.isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), HeartSystemMod.config.getStartHearts());
        }
    }

    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        PlayerEntity player = event.getPlayer();
        if (player.level.isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        PlayerEntity newPlayer = event.getPlayer();
        if (newPlayer.level.isClientSide()) return;
        if (!event.isWasDeath()) return;
        UUID uuid = newPlayer.getUUID();
        if (!HeartStorage.get().has(uuid)) return;
        if (newPlayer instanceof ServerPlayerEntity) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData.applyMaxHealth((ServerPlayerEntity) newPlayer, hearts);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level.isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity deadPlayer = (ServerPlayerEntity) event.getEntity();
        UUID deadUUID = deadPlayer.getUUID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                StringTextComponent msg = new StringTextComponent(
                    "\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts).");
                server.getPlayerList().getPlayers().forEach(p -> p.sendMessage(msg, p.getUUID()));
                UserBanList banList = server.getPlayerList().getBans();
                banList.add(new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.connection.disconnect(new StringTextComponent(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(new StringTextComponent(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts), deadPlayer.getUUID());
        }
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (killer instanceof ServerPlayerEntity) {
            ServerPlayerEntity killerPlayer = (ServerPlayerEntity) killer;
            UUID killerUUID = killerPlayer.getUUID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = HeartSystemMod.config.getStartHearts();
            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendMessage(new StringTextComponent(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts), killerPlayer.getUUID());
            } else {
                killerPlayer.sendMessage(new StringTextComponent(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."), killerPlayer.getUUID());
            }
        }
    }
}
""",
"HeartStorage.java": """\
package asd.itamio.heartsystem;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    public static HeartStorage get() { return INSTANCE; }
    private final Map<UUID, Integer> cache = new HashMap<>();
    private HeartStorage() {}

    public int load(String playerUUID, File playerFile) {
        UUID uuid = UUID.fromString(playerUUID);
        if (playerFile.exists()) {
            try {
                CompoundNBT tag = CompressedStreamTools.read(playerFile);
                if (tag != null && tag.contains("hearts")) {
                    int h = tag.getInt("hearts");
                    cache.put(uuid, h);
                    return h;
                }
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("hearts", hearts);
        try {
            CompressedStreamTools.write(tag, playerFile);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
""",
"HeartData.java": """\
package asd.itamio.heartsystem;

import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";

    public static void applyMaxHealth(ServerPlayerEntity player, int hearts) {
        ModifiableAttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        if (attr.getModifier(MODIFIER_UUID) != null) attr.removeModifier(MODIFIER_UUID);
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }
}
""",
"HeartConfig.java": _FORGE_CONFIG_MODLOADINGCTX,
}


# ===========================================================================
# FORGE 1.17.1–1.18.2 — TextComponent, broadcastMessage(Component, boolean)
# player.level is a FIELD in 1.17–1.19.4
# NbtIo.read(File) / NbtIo.write(tag, File) — File overloads exist
# net.minecraft.server.players.UserBanList / UserBanListEntry
# ===========================================================================
_FORGE_1171_HANDLER = """\
package asd.itamio.heartsystem;

import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        Player player = event.getPlayer();
        if (player.level.isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), HeartSystemMod.config.getStartHearts());
        }
    }

    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        Player player = event.getPlayer();
        if (player.level.isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        Player newPlayer = event.getPlayer();
        if (newPlayer.level.isClientSide()) return;
        if (!event.isWasDeath()) return;
        UUID uuid = newPlayer.getUUID();
        if (!HeartStorage.get().has(uuid)) return;
        if (newPlayer instanceof ServerPlayer) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData.applyMaxHealth((ServerPlayer) newPlayer, hearts);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level.isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer deadPlayer = (ServerPlayer) event.getEntity();
        UUID deadUUID = deadPlayer.getUUID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                TextComponent msg = new TextComponent(
                    "\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts).");
                server.getPlayerList().getPlayers().forEach(p -> p.sendMessage(msg, p.getUUID()));
                UserBanList banList = server.getPlayerList().getBans();
                banList.add(new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.connection.disconnect(new TextComponent(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(new TextComponent(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts), deadPlayer.getUUID());
        }
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (killer instanceof ServerPlayer) {
            ServerPlayer killerPlayer = (ServerPlayer) killer;
            UUID killerUUID = killerPlayer.getUUID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = HeartSystemMod.config.getStartHearts();
            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendMessage(new TextComponent(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts), killerPlayer.getUUID());
            } else {
                killerPlayer.sendMessage(new TextComponent(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."), killerPlayer.getUUID());
            }
        }
    }
}
"""

_FORGE_1171_MOD = """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HeartSystemMod.MOD_ID)
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    public HeartSystemMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        config = new HeartConfig();
        MinecraftForge.EVENT_BUS.register(new HeartEventHandler());
    }
}
"""

SRC_1171_FORGE = {
    "HeartSystemMod.java":   _FORGE_1171_MOD,
    "HeartEventHandler.java": _FORGE_1171_HANDLER,
    "HeartStorage.java":     _FORGE_STORAGE_NBTIO_FILE,
    "HeartData.java":        _FORGE_DATA_UUID_ADDITION,
    "HeartConfig.java":      _FORGE_CONFIG_MODLOADINGCTX,
}
SRC_118_FORGE = SRC_1171_FORGE


# ===========================================================================
# FORGE 1.19–1.20.2 — Component.literal, sendSystemMessage, broadcastSystemMessage
# player.level is still a FIELD in 1.19–1.19.4
# player.level() becomes a METHOD in 1.20+
# NbtIo.read(File) / NbtIo.write(tag, File) still work in 1.19–1.20.2
# ===========================================================================
_FORGE_119_HANDLER = """\
package asd.itamio.heartsystem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        Player player = event.getEntity();
        if (player.level.isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), HeartSystemMod.config.getStartHearts());
        }
    }

    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        Player player = event.getEntity();
        if (player.level.isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        Player newPlayer = event.getEntity();
        if (newPlayer.level.isClientSide()) return;
        if (!event.isWasDeath()) return;
        UUID uuid = newPlayer.getUUID();
        if (!HeartStorage.get().has(uuid)) return;
        if (newPlayer instanceof ServerPlayer) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData.applyMaxHealth((ServerPlayer) newPlayer, hearts);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level.isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer deadPlayer = (ServerPlayer) event.getEntity();
        UUID deadUUID = deadPlayer.getUUID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                Component msg = Component.literal("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts).");
                server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(msg));
                UserBanList banList = server.getPlayerList().getBans();
                banList.add(new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.connection.disconnect(Component.literal(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendSystemMessage(Component.literal(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (killer instanceof ServerPlayer) {
            ServerPlayer killerPlayer = (ServerPlayer) killer;
            UUID killerUUID = killerPlayer.getUUID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = HeartSystemMod.config.getStartHearts();
            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendSystemMessage(Component.literal(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.sendSystemMessage(Component.literal(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
"""

# 1.20+ handler: player.level() is a METHOD (not field)
_FORGE_120_HANDLER = _FORGE_119_HANDLER.replace(
    "player.level.isClientSide()",
    "player.level().isClientSide()"
).replace(
    "event.getEntity().level.isClientSide()",
    "event.getEntity().level().isClientSide()"
).replace(
    "newPlayer.level.isClientSide()",
    "newPlayer.level().isClientSide()"
)

SRC_119_FORGE = {
    "HeartSystemMod.java":   _FORGE_1171_MOD,
    "HeartEventHandler.java": _FORGE_119_HANDLER,
    "HeartStorage.java":     _FORGE_STORAGE_NBTIO_FILE,
    "HeartData.java":        _FORGE_DATA_UUID_ADDITION,
    "HeartConfig.java":      _FORGE_CONFIG_MODLOADINGCTX,
}

# 1.20.1–1.20.2: level() method, NbtIo File overload still works
SRC_120_FORGE = {
    "HeartSystemMod.java":   _FORGE_1171_MOD,
    "HeartEventHandler.java": _FORGE_120_HANDLER,
    "HeartStorage.java":     _FORGE_STORAGE_NBTIO_FILE,
    "HeartData.java":        _FORGE_DATA_UUID_ADDITION,
    "HeartConfig.java":      _FORGE_CONFIG_MODLOADINGCTX,
}

# 1.20.3–1.21.5: NbtIo requires Path (not File)
SRC_1203_FORGE = {
    "HeartSystemMod.java":   _FORGE_1171_MOD,
    "HeartEventHandler.java": _FORGE_120_HANDLER,
    "HeartStorage.java":     _FORGE_STORAGE_NBTIO_PATH,
    "HeartData.java":        _FORGE_DATA_UUID_ADDITION,
    "HeartConfig.java":      _FORGE_CONFIG_MODLOADINGCTX,
}


# ===========================================================================
# FORGE 1.21.6–1.21.8 — EventBus 7, FMLJavaModLoadingContext constructor
# AttributeModifier is a record with ResourceLocation id, Operation.ADD_VALUE
# NbtIo requires Path
# ===========================================================================
_FORGE_1216_MOD = """\
package asd.itamio.heartsystem;

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HeartSystemMod.MOD_ID)
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    public HeartSystemMod(FMLJavaModLoadingContext context) {
        config = new HeartConfig(context);
        HeartEventHandler handler = new HeartEventHandler();
        PlayerEvent.LoadFromFile.BUS.addListener(handler::onPlayerLoad);
        PlayerEvent.SaveToFile.BUS.addListener(handler::onPlayerSave);
        PlayerEvent.Clone.BUS.addListener(handler::onPlayerClone);
        LivingDeathEvent.BUS.addListener(handler::onLivingDeath);
    }
}
"""

_FORGE_1216_HANDLER = """\
package asd.itamio.heartsystem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), HeartSystemMod.config.getStartHearts());
        }
    }

    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    public void onPlayerClone(PlayerEvent.Clone event) {
        Player newPlayer = event.getEntity();
        if (newPlayer.level().isClientSide()) return;
        if (!event.isWasDeath()) return;
        UUID uuid = newPlayer.getUUID();
        if (!HeartStorage.get().has(uuid)) return;
        if (newPlayer instanceof ServerPlayer) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData.applyMaxHealth((ServerPlayer) newPlayer, hearts);
        }
    }

    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer deadPlayer = (ServerPlayer) event.getEntity();
        UUID deadUUID = deadPlayer.getUUID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), false);
                UserBanList banList = server.getPlayerList().getBans();
                banList.add(new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.connection.disconnect(Component.literal(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendSystemMessage(Component.literal(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (killer instanceof ServerPlayer) {
            ServerPlayer killerPlayer = (ServerPlayer) killer;
            UUID killerUUID = killerPlayer.getUUID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = HeartSystemMod.config.getStartHearts();
            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendSystemMessage(Component.literal(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.sendSystemMessage(Component.literal(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
"""

SRC_1216_FORGE = {
    "HeartSystemMod.java":   _FORGE_1216_MOD,
    "HeartEventHandler.java": _FORGE_1216_HANDLER,
    "HeartStorage.java":     _FORGE_STORAGE_NBTIO_PATH,
    "HeartData.java":        _FORGE_DATA_RL_ADDVALUE,
    "HeartConfig.java":      _FORGE_CONFIG_FMLJAVAMOD,
}

# 1.21.9–26.1.2: getServer() removed from ServerPlayer — use level().getServer()
# UserBanListEntry takes NameAndId(UUID, String) not GameProfile
_FORGE_1219_HANDLER = """\
package asd.itamio.heartsystem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), HeartSystemMod.config.getStartHearts());
        }
    }

    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    public void onPlayerClone(PlayerEvent.Clone event) {
        Player newPlayer = event.getEntity();
        if (newPlayer.level().isClientSide()) return;
        if (!event.isWasDeath()) return;
        UUID uuid = newPlayer.getUUID();
        if (!HeartStorage.get().has(uuid)) return;
        if (newPlayer instanceof ServerPlayer) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData.applyMaxHealth((ServerPlayer) newPlayer, hearts);
        }
    }

    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer deadPlayer = (ServerPlayer) event.getEntity();
        UUID deadUUID = deadPlayer.getUUID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.level().getServer();
            if (server != null) {
                Component msg = Component.literal("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts).");
                server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(msg));
                UserBanList banList = server.getPlayerList().getBans();
                banList.add(new UserBanListEntry(
                    new NameAndId(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.connection.disconnect(Component.literal(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendSystemMessage(Component.literal(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (killer instanceof ServerPlayer) {
            ServerPlayer killerPlayer = (ServerPlayer) killer;
            UUID killerUUID = killerPlayer.getUUID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = HeartSystemMod.config.getStartHearts();
            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendSystemMessage(Component.literal(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.sendSystemMessage(Component.literal(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
"""

SRC_1219_FORGE = {
    "HeartSystemMod.java":   _FORGE_1216_MOD,
    "HeartEventHandler.java": _FORGE_1219_HANDLER,
    "HeartStorage.java":     _FORGE_STORAGE_NBTIO_PATH_OPT,
    "HeartData.java":        _FORGE_DATA_RL_ADDVALUE_1219,
    "HeartConfig.java":      _FORGE_CONFIG_FMLJAVAMOD,
}


# ===========================================================================
# FABRIC 1.16.5–1.18.2 (yarn mappings)
# ServerLivingEntityEvents does NOT exist in Fabric API for 1.16.5–1.18.x
# Use Mixin on PlayerEntity.onDeath() to intercept player death
# DamageSource: net.minecraft.entity.damage.DamageSource (yarn)
# NbtCompound: net.minecraft.nbt.NbtCompound (yarn)
# NbtIo: net.minecraft.nbt.NbtIo with readCompressed/writeCompressed
# EntityAttributeInstance, EntityAttributeModifier, EntityAttributes (yarn)
# LiteralText for 1.16.5–1.18.x
# ===========================================================================

_FABRIC_165_MOD = """\
package asd.itamio.heartsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HeartSystemMod implements ModInitializer {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    @Override
    public void onInitialize() {
        config = new HeartConfig();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            HeartStorage.get().loadOrInit(player, config.getStartHearts());
            HeartData.applyMaxHealth(player, HeartStorage.get().getHearts(player.getUuid()));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HeartStorage.get().save(handler.player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                HeartStorage.get().copyHearts(oldPlayer.getUuid(), newPlayer.getUuid());
            }
            HeartData.applyMaxHealth(newPlayer, HeartStorage.get().getHearts(newPlayer.getUuid()));
        });
    }
}
"""

_FABRIC_165_MIXIN = """\
package asd.itamio.heartsystem.mixin;

import asd.itamio.heartsystem.HeartConfig;
import asd.itamio.heartsystem.HeartData;
import asd.itamio.heartsystem.HeartStorage;
import asd.itamio.heartsystem.HeartSystemMod;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerEntity.class)
public class PlayerDeathMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onPlayerDeath(DamageSource source, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.world.isClient) return;
        if (!(self instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity deadPlayer = (ServerPlayerEntity) self;
        HeartConfig config = HeartSystemMod.config;
        if (config == null) return;

        UUID deadUUID = deadPlayer.getUuid();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = config.getStartHearts();
        hearts -= 1;
        int min = config.getMinHearts();

        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                server.getPlayerManager().broadcastChatMessage(
                    new LiteralText("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."),
                    net.minecraft.network.MessageType.SYSTEM, net.minecraft.util.Util.NIL_UUID);
                server.getPlayerManager().getUserBanList().add(
                    new BannedPlayerEntry(
                        new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                        null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.networkHandler.disconnect(new LiteralText(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(new LiteralText(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts), false);
        }

        net.minecraft.entity.Entity killer = source.getAttacker();
        if (killer instanceof ServerPlayerEntity) {
            ServerPlayerEntity killerPlayer = (ServerPlayerEntity) killer;
            UUID killerUUID = killerPlayer.getUuid();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = config.getStartHearts();
            int max = config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendMessage(new LiteralText(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts), false);
            } else {
                killerPlayer.sendMessage(new LiteralText(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."), false);
            }
        }
    }
}
"""

_FABRIC_165_STORAGE = """\
package asd.itamio.heartsystem;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    public static HeartStorage get() { return INSTANCE; }
    private final Map<UUID, Integer> cache = new HashMap<>();
    private HeartStorage() {}

    public void loadOrInit(ServerPlayerEntity player, int defaultHearts) {
        UUID uuid = player.getUuid();
        File file = getFile(player);
        if (file.exists()) {
            try {
                NbtCompound tag = NbtIo.readCompressed(file);
                if (tag != null && tag.contains("hearts")) {
                    cache.put(uuid, tag.getInt("hearts"));
                    return;
                }
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        cache.put(uuid, defaultHearts);
    }

    public void save(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int hearts = getHearts(uuid);
        if (hearts < 0) return;
        File file = getFile(player);
        NbtCompound tag = new NbtCompound();
        tag.putInt("hearts", hearts);
        try {
            NbtIo.writeCompressed(tag, file);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    private File getFile(ServerPlayerEntity player) {
        File worldDir = player.getServer().getSavePath(WorldSavePath.PLAYERDATA).toFile();
        return new File(worldDir, player.getUuidAsString() + ".heartsystem.dat");
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void copyHearts(UUID from, UUID to) { Integer h = cache.get(from); if (h != null) cache.put(to, h); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
"""

_FABRIC_165_DATA = """\
package asd.itamio.heartsystem;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";

    public static void applyMaxHealth(ServerPlayerEntity player, int hearts) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        if (attr.getModifier(MODIFIER_UUID) != null) attr.removeModifier(MODIFIER_UUID);
        double delta = (hearts * 2.0) - 20.0;
        EntityAttributeModifier mod = new EntityAttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, EntityAttributeModifier.Operation.ADDITION);
        attr.addModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }
}
"""

_FABRIC_165_CONFIG = """\
package asd.itamio.heartsystem;

public class HeartConfig {
    private final int startHearts = 10;
    private final int maxHearts   = 20;
    private final int minHearts   = 0;
    public int getStartHearts() { return startHearts; }
    public int getMaxHearts()   { return maxHearts; }
    public int getMinHearts()   { return minHearts; }
}
"""

_FABRIC_165_MIXINJSON = """\
{
  "required": true,
  "package": "asd.itamio.heartsystem.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": ["PlayerDeathMixin"],
  "injectors": { "defaultRequire": 1 }
}
"""

SRC_1165_FABRIC = {
    "HeartSystemMod.java":                                    _FABRIC_165_MOD,
    "mixin/PlayerDeathMixin.java":                            _FABRIC_165_MIXIN,
    "HeartStorage.java":                                      _FABRIC_165_STORAGE,
    "HeartData.java":                                         _FABRIC_165_DATA,
    "HeartConfig.java":                                       _FABRIC_165_CONFIG,
    "../resources/heartsystem.mixins.json":                   _FABRIC_165_MIXINJSON,
}

SRC_117_FABRIC  = SRC_1165_FABRIC
SRC_118_FABRIC  = SRC_1165_FABRIC


# ===========================================================================
# FABRIC 1.19–1.20.6 (yarn mappings)
# Text.literal() replaces LiteralText
# broadcastSystemMessage (1.19+)
# BannedPlayerList / BannedPlayerEntry still in net.minecraft.server
# ===========================================================================
_FABRIC_119_MIXIN = """\
package asd.itamio.heartsystem.mixin;

import asd.itamio.heartsystem.HeartConfig;
import asd.itamio.heartsystem.HeartData;
import asd.itamio.heartsystem.HeartStorage;
import asd.itamio.heartsystem.HeartSystemMod;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerEntity.class)
public class PlayerDeathMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onPlayerDeath(DamageSource source, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.world.isClient) return;
        if (!(self instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity deadPlayer = (ServerPlayerEntity) self;
        HeartConfig config = HeartSystemMod.config;
        if (config == null) return;

        UUID deadUUID = deadPlayer.getUuid();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = config.getStartHearts();
        hearts -= 1;
        int min = config.getMinHearts();

        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                server.getPlayerManager().getPlayerList().forEach(p ->
                    p.sendMessage(Text.literal("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), false));
                server.getPlayerManager().getUserBanList().add(
                    new BannedPlayerEntry(
                        new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                        null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.networkHandler.disconnect(Text.literal(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(Text.literal(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts), false);
        }

        net.minecraft.entity.Entity killer = source.getAttacker();
        if (killer instanceof ServerPlayerEntity) {
            ServerPlayerEntity killerPlayer = (ServerPlayerEntity) killer;
            UUID killerUUID = killerPlayer.getUuid();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = config.getStartHearts();
            int max = config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendMessage(Text.literal(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts), false);
            } else {
                killerPlayer.sendMessage(Text.literal(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."), false);
            }
        }
    }
}
"""

_FABRIC_119_MOD = _FABRIC_165_MOD

SRC_119_FABRIC = {
    "HeartSystemMod.java":                  _FABRIC_119_MOD,
    "mixin/PlayerDeathMixin.java":          _FABRIC_119_MIXIN,
    "HeartStorage.java":                    _FABRIC_165_STORAGE,
    "HeartData.java":                       _FABRIC_165_DATA,
    "HeartConfig.java":                     _FABRIC_165_CONFIG,
    "../resources/heartsystem.mixins.json": _FABRIC_165_MIXINJSON,
}


# ===========================================================================
# FABRIC 1.21–26.1.2 (Mojang mappings)
# ServerPlayer (not ServerPlayerEntity), Component (not Text)
# InteractionResult (not ActionResult) — but ALLOW_DEATH returns ActionResult
# Actually ServerLivingEntityEvents.ALLOW_DEATH exists in Fabric API 1.21+
# Use Mixin approach for consistency (avoids ActionResult import issues)
# AttributeModifier is a record with ResourceLocation id, Operation.ADD_VALUE
# removeModifier(ResourceLocation) not UUID
# LevelResource.PLAYER_DATA_DIR (Mojang) instead of WorldSavePath.PLAYERDATA
# NbtIo.read(Path) / NbtIo.write(tag, Path)
# handler.getPlayer() in 1.21+
# ===========================================================================
_FABRIC_121_MOD = """\
package asd.itamio.heartsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HeartSystemMod implements ModInitializer {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    @Override
    public void onInitialize() {
        config = new HeartConfig();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            HeartStorage.get().loadOrInit(player, config.getStartHearts());
            HeartData.applyMaxHealth(player, HeartStorage.get().getHearts(player.getUUID()));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HeartStorage.get().save(handler.getPlayer());
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                HeartStorage.get().copyHearts(oldPlayer.getUUID(), newPlayer.getUUID());
            }
            HeartData.applyMaxHealth(newPlayer, HeartStorage.get().getHearts(newPlayer.getUUID()));
        });
    }
}
"""

_FABRIC_121_MIXIN = """\
package asd.itamio.heartsystem.mixin;

import asd.itamio.heartsystem.HeartConfig;
import asd.itamio.heartsystem.HeartData;
import asd.itamio.heartsystem.HeartStorage;
import asd.itamio.heartsystem.HeartSystemMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Player.class)
public class PlayerDeathMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void onPlayerDeath(DamageSource source, CallbackInfo ci) {
        Player self = (Player)(Object)this;
        if (self.level().isClientSide()) return;
        if (!(self instanceof ServerPlayer)) return;
        ServerPlayer deadPlayer = (ServerPlayer) self;
        HeartConfig config = HeartSystemMod.config;
        if (config == null) return;

        UUID deadUUID = deadPlayer.getUUID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = config.getStartHearts();
        hearts -= 1;
        int min = config.getMinHearts();

        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.level().getServer();
            if (server != null) {
                Component msg = Component.literal("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts).");
                server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(msg));
                UserBanList banList = server.getPlayerList().getBans();
                banList.add(new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.connection.disconnect(Component.literal(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendSystemMessage(Component.literal(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }

        Entity killer = source.getEntity();
        if (killer instanceof ServerPlayer) {
            ServerPlayer killerPlayer = (ServerPlayer) killer;
            UUID killerUUID = killerPlayer.getUUID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = config.getStartHearts();
            int max = config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendSystemMessage(Component.literal(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.sendSystemMessage(Component.literal(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
"""

_FABRIC_121_STORAGE = """\
package asd.itamio.heartsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartStorage {
    private static final HeartStorage INSTANCE = new HeartStorage();
    public static HeartStorage get() { return INSTANCE; }
    private final Map<UUID, Integer> cache = new HashMap<>();
    private HeartStorage() {}

    public void loadOrInit(ServerPlayer player, int defaultHearts) {
        UUID uuid = player.getUUID();
        File file = getFile(player);
        if (file.exists()) {
            try {
                CompoundTag tag = NbtIo.read(file.toPath());
                if (tag != null && tag.contains("hearts")) {
                    cache.put(uuid, tag.getInt("hearts"));
                    return;
                }
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        cache.put(uuid, defaultHearts);
    }

    public void save(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int hearts = getHearts(uuid);
        if (hearts < 0) return;
        File file = getFile(player);
        CompoundTag tag = new CompoundTag();
        tag.putInt("hearts", hearts);
        try {
            NbtIo.write(tag, file.toPath());
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    private File getFile(ServerPlayer player) {
        File worldDir = player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
        return new File(worldDir, player.getStringUUID() + ".heartsystem.dat");
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void copyHearts(UUID from, UUID to) { Integer h = cache.get(from); if (h != null) cache.put(to, h); }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
"""

_FABRIC_121_MIXINJSON = """\
{
  "required": true,
  "package": "asd.itamio.heartsystem.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": ["PlayerDeathMixin"],
  "injectors": { "defaultRequire": 1 }
}
"""

SRC_121_FABRIC = {
    "HeartSystemMod.java":                  _FABRIC_121_MOD,
    "mixin/PlayerDeathMixin.java":          _FABRIC_121_MIXIN,
    "HeartStorage.java":                    _FABRIC_121_STORAGE,
    "HeartData.java":                       _FORGE_DATA_RL_ADDVALUE,
    "HeartConfig.java":                     _FABRIC_165_CONFIG,
    "../resources/heartsystem.mixins.json": _FABRIC_121_MIXINJSON,
}

# 1.21.9+: getInt() returns Optional<Integer>, getServer() removed from ServerPlayer
_FABRIC_121_STORAGE_OPT = _FABRIC_121_STORAGE.replace(
    "CompoundTag tag = NbtIo.read(file.toPath());\n                if (tag != null && tag.contains(\"hearts\")) {\n                    cache.put(uuid, tag.getInt(\"hearts\"));\n                    return;",
    "CompoundTag tag = NbtIo.read(file.toPath());\n                if (tag != null && tag.contains(\"hearts\")) {\n                    int h = tag.getInt(\"hearts\").orElse(-1);\n                    if (h >= 0) { cache.put(uuid, h); return; }"
)

SRC_1219_FABRIC = {
    "HeartSystemMod.java":                  _FABRIC_121_MOD,
    "mixin/PlayerDeathMixin.java":          _FABRIC_121_MIXIN,
    "HeartStorage.java":                    _FABRIC_121_STORAGE_OPT,
    "HeartData.java":                       _FORGE_DATA_RL_ADDVALUE_1219,
    "HeartConfig.java":                     _FABRIC_165_CONFIG,
    "../resources/heartsystem.mixins.json": _FABRIC_121_MIXINJSON,
}


# ===========================================================================
# NEOFORGE 1.20.2–1.20.6 (IEventBus constructor)
# ModConfigSpec (not ForgeConfigSpec)
# NbtIo.read(File) / NbtIo.write(tag, File) — File overloads exist in 1.20.2
# AttributeModifier with UUID, Operation.ADDITION (1.20.x)
# HeartData.applyMaxHealth is STATIC
# ===========================================================================
_NEO_120_MOD = """\
package asd.itamio.heartsystem;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HeartSystemMod.MOD_ID)
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    public HeartSystemMod(IEventBus modBus) {
        config = new HeartConfig(modBus);
        NeoForge.EVENT_BUS.register(new HeartEventHandler());
    }
}
"""

_NEO_120_HANDLER = """\
package asd.itamio.heartsystem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.io.File;
import java.util.UUID;

public class HeartEventHandler {

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), HeartSystemMod.config.getStartHearts());
        }
    }

    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        UUID uuid = UUID.fromString(uuidStr);
        File file = event.getPlayerFile("heartsystem");
        int hearts = HeartStorage.get().getHearts(uuid);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        HeartStorage.get().save(uuidStr, file, hearts);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        Player newPlayer = event.getEntity();
        if (newPlayer.level().isClientSide()) return;
        if (!event.isWasDeath()) return;
        UUID uuid = newPlayer.getUUID();
        if (!HeartStorage.get().has(uuid)) return;
        if (newPlayer instanceof ServerPlayer) {
            int hearts = HeartStorage.get().getHearts(uuid);
            HeartData.applyMaxHealth((ServerPlayer) newPlayer, hearts);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer deadPlayer = (ServerPlayer) event.getEntity();
        UUID deadUUID = deadPlayer.getUUID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = HeartSystemMod.config.getStartHearts();
        hearts -= 1;
        int min = HeartSystemMod.config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                Component msg = Component.literal("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts).");
                server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(msg));
                UserBanList banList = server.getPlayerList().getBans();
                banList.add(new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"));
            }
            deadPlayer.connection.disconnect(Component.literal(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendSystemMessage(Component.literal(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (killer instanceof ServerPlayer) {
            ServerPlayer killerPlayer = (ServerPlayer) killer;
            UUID killerUUID = killerPlayer.getUUID();
            int killerHearts = HeartStorage.get().getHearts(killerUUID);
            if (killerHearts < 0) killerHearts = HeartSystemMod.config.getStartHearts();
            int max = HeartSystemMod.config.getMaxHearts();
            if (killerHearts < max) {
                killerHearts += 1;
                HeartStorage.get().setHearts(killerUUID, killerHearts);
                HeartData.applyMaxHealth(killerPlayer, killerHearts);
                killerPlayer.sendSystemMessage(Component.literal(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.sendSystemMessage(Component.literal(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
"""

_NEO_120_CONFIG = """\
package asd.itamio.heartsystem;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public class HeartConfig {
    private final ModConfigSpec.IntValue startHearts;
    private final ModConfigSpec.IntValue maxHearts;
    private final ModConfigSpec.IntValue minHearts;

    public HeartConfig(IEventBus modBus) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Heart System Configuration");
        startHearts = builder.comment("Hearts a new player starts with (1 heart = 2 HP)").defineInRange("startHearts", 10, 1, 100);
        maxHearts   = builder.comment("Maximum hearts a player can have").defineInRange("maxHearts", 20, 1, 100);
        minHearts   = builder.comment("Minimum hearts before permadeath").defineInRange("minHearts", 0, 0, 99);
        net.neoforged.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, builder.build());
    }

    public int getStartHearts() { return startHearts.get(); }
    public int getMaxHearts()   { return maxHearts.get(); }
    public int getMinHearts()   { return minHearts.get(); }
}
"""

SRC_120_NEO = {
    "HeartSystemMod.java":   _NEO_120_MOD,
    "HeartEventHandler.java": _NEO_120_HANDLER,
    "HeartStorage.java":     _FORGE_STORAGE_NBTIO_FILE,
    "HeartData.java":        _FORGE_DATA_UUID_ADDITION,
    "HeartConfig.java":      _NEO_120_CONFIG,
}

# NeoForge 1.20.5–1.20.6: Operation.ADD_VALUE, NbtIo needs Path
_SRC_1205_NEO = {
    "HeartSystemMod.java":   _NEO_120_MOD,
    "HeartEventHandler.java": _NEO_120_HANDLER,
    "HeartStorage.java":     _FORGE_STORAGE_NBTIO_PATH,
    "HeartData.java":        _FORGE_DATA_UUID_ADDVALUE,
    "HeartConfig.java":      _NEO_120_CONFIG,
}

# NeoForge 1.21–1.21.8: same as 1.20.5 but ResourceLocation AttributeModifier
SRC_121_NEO = {
    "HeartSystemMod.java":   _NEO_120_MOD,
    "HeartEventHandler.java": _NEO_120_HANDLER,
    "HeartStorage.java":     _FORGE_STORAGE_NBTIO_PATH,
    "HeartData.java":        _FORGE_DATA_RL_ADDVALUE,
    "HeartConfig.java":      _NEO_120_CONFIG,
}

# NeoForge 1.21.9–26.1.2: ModContainer required, ResourceLocation AttributeModifier
_NEO_1219_MOD = """\
package asd.itamio.heartsystem;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HeartSystemMod.MOD_ID)
public class HeartSystemMod {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    public HeartSystemMod(IEventBus modBus, ModContainer container) {
        config = new HeartConfig(modBus, container);
        NeoForge.EVENT_BUS.register(new HeartEventHandler());
    }
}
"""

_NEO_1219_CONFIG = """\
package asd.itamio.heartsystem;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public class HeartConfig {
    private final ModConfigSpec.IntValue startHearts;
    private final ModConfigSpec.IntValue maxHearts;
    private final ModConfigSpec.IntValue minHearts;

    public HeartConfig(IEventBus modBus, ModContainer container) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Heart System Configuration");
        startHearts = builder.comment("Hearts a new player starts with (1 heart = 2 HP)").defineInRange("startHearts", 10, 1, 100);
        maxHearts   = builder.comment("Maximum hearts a player can have").defineInRange("maxHearts", 20, 1, 100);
        minHearts   = builder.comment("Minimum hearts before permadeath").defineInRange("minHearts", 0, 0, 99);
        container.registerConfig(ModConfig.Type.SERVER, builder.build());
    }

    public int getStartHearts() { return startHearts.get(); }
    public int getMaxHearts()   { return maxHearts.get(); }
    public int getMinHearts()   { return minHearts.get(); }
}
"""

SRC_1219_NEO = {
    "HeartSystemMod.java":   _NEO_1219_MOD,
    "HeartEventHandler.java": _FORGE_1219_HANDLER,
    "HeartStorage.java":     _FORGE_STORAGE_NBTIO_PATH_OPT,
    "HeartData.java":        _FORGE_DATA_RL_ADDVALUE_1219,
    "HeartConfig.java":      _NEO_1219_CONFIG,
}


# ===========================================================================
# TARGET LIST + BUILD LOGIC
# ===========================================================================

PKG = "asd/itamio/heartsystem"
ALREADY_PUBLISHED = {
    "HeartSystem-1.12-forge",
    "HeartSystem-1.12.2-forge",
    "HeartSystem-1.16.5-fabric",
    "HeartSystem-1.17-fabric",
    "HeartSystem-1.17.1-forge",
    "HeartSystem-1.18-fabric",
    "HeartSystem-1.18-forge",
    "HeartSystem-1.18.1-forge",
    "HeartSystem-1.18.2-forge",
    "HeartSystem-1.19-fabric",
    "HeartSystem-1.19-forge",
    "HeartSystem-1.19.1-fabric",
    "HeartSystem-1.19.1-forge",
    "HeartSystem-1.19.2-fabric",
    "HeartSystem-1.19.2-forge",
    "HeartSystem-1.19.3-fabric",
    "HeartSystem-1.19.3-forge",
    "HeartSystem-1.19.4-fabric",
    "HeartSystem-1.19.4-forge",
    "HeartSystem-1.20.1-fabric",
    "HeartSystem-1.20.1-forge",
    "HeartSystem-1.20.2-fabric",
    "HeartSystem-1.20.2-forge",
    "HeartSystem-1.20.2-neoforge",
    "HeartSystem-1.20.3-fabric",
    "HeartSystem-1.20.3-forge",
    "HeartSystem-1.20.4-fabric",
    "HeartSystem-1.20.4-forge",
    "HeartSystem-1.20.4-neoforge",
    "HeartSystem-1.20.5-neoforge",
    "HeartSystem-1.20.6-forge",
    "HeartSystem-1.20.6-neoforge",
    "HeartSystem-1.21-fabric",
    "HeartSystem-1.21-forge",
    "HeartSystem-1.21.1-forge",
    "HeartSystem-1.21.10-forge",
    "HeartSystem-1.21.10-neoforge",
    "HeartSystem-1.21.11-forge",
    "HeartSystem-1.21.11-neoforge",
    "HeartSystem-1.21.2-fabric",
    "HeartSystem-1.21.3-forge",
    "HeartSystem-1.21.4-forge",
    "HeartSystem-1.21.5-forge",
    "HeartSystem-1.21.6-forge",
    "HeartSystem-1.21.7-forge",
    "HeartSystem-1.21.8-forge",
    "HeartSystem-1.21.9-fabric",
    "HeartSystem-1.21.9-forge",
    "HeartSystem-1.21.9-neoforge",
    "HeartSystem-1.8.9-forge",
    "HeartSystem-26.1-fabric",
    "HeartSystem-26.1-neoforge",
    "HeartSystem-26.1.1-fabric",
    "HeartSystem-26.1.1-neoforge",
    "HeartSystem-26.1.2-fabric",
    "HeartSystem-26.1.2-forge",
    "HeartSystem-26.1.2-neoforge",
}

def make_files(src_dict):
    """Convert {filename: content} to {src/main/java/pkg/filename: content}
       Files starting with ../ go to src/main/resources/ instead."""
    result = {}
    for fname, content in src_dict.items():
        if fname.startswith("../resources/"):
            result[f"src/main/resources/{fname[len('../resources/'):]}" ] = content
        else:
            result[f"src/main/java/{PKG}/{fname}"] = content
    return result

TARGETS = [
    # (folder_name, mc_version, loader, src_dict)
    ("HeartSystem-1.8.9-forge",    "1.8.9",   "forge",    SRC_189_FORGE),
    ("HeartSystem-1.12-forge",     "1.12",    "forge",    SRC_1122_FORGE),
    ("HeartSystem-1.16.5-forge",   "1.16.5",  "forge",    SRC_1165_FORGE),
    ("HeartSystem-1.16.5-fabric",  "1.16.5",  "fabric",   SRC_1165_FABRIC),
    ("HeartSystem-1.17-fabric",    "1.17",    "fabric",   SRC_117_FABRIC),
    ("HeartSystem-1.17.1-fabric",  "1.17.1",  "fabric",   SRC_117_FABRIC),
    ("HeartSystem-1.17.1-forge",   "1.17.1",  "forge",    SRC_1171_FORGE),
    ("HeartSystem-1.18-forge",     "1.18",    "forge",    SRC_118_FORGE),
    ("HeartSystem-1.18-fabric",    "1.18",    "fabric",   SRC_118_FABRIC),
    ("HeartSystem-1.18.1-fabric",  "1.18.1",  "fabric",   SRC_118_FABRIC),
    ("HeartSystem-1.18.1-forge",   "1.18.1",  "forge",    SRC_118_FORGE),
    ("HeartSystem-1.18.2-fabric",  "1.18.2",  "fabric",   SRC_118_FABRIC),
    ("HeartSystem-1.18.2-forge",   "1.18.2",  "forge",    SRC_118_FORGE),
    ("HeartSystem-1.19-forge",     "1.19",    "forge",    SRC_119_FORGE),
    ("HeartSystem-1.19-fabric",    "1.19",    "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.19.1-forge",   "1.19.1",  "forge",    SRC_119_FORGE),
    ("HeartSystem-1.19.1-fabric",  "1.19.1",  "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.19.2-forge",   "1.19.2",  "forge",    SRC_119_FORGE),
    ("HeartSystem-1.19.2-fabric",  "1.19.2",  "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.19.3-forge",   "1.19.3",  "forge",    SRC_119_FORGE),
    ("HeartSystem-1.19.3-fabric",  "1.19.3",  "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.19.4-forge",   "1.19.4",  "forge",    SRC_119_FORGE),
    ("HeartSystem-1.19.4-fabric",  "1.19.4",  "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.20.1-forge",   "1.20.1",  "forge",    SRC_120_FORGE),
    ("HeartSystem-1.20.1-fabric",  "1.20.1",  "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.20.2-forge",   "1.20.2",  "forge",    SRC_120_FORGE),
    ("HeartSystem-1.20.2-fabric",  "1.20.2",  "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.20.2-neoforge","1.20.2",  "neoforge", SRC_120_NEO),
    ("HeartSystem-1.20.3-forge",   "1.20.3",  "forge",    SRC_1203_FORGE),
    ("HeartSystem-1.20.3-fabric",  "1.20.3",  "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.20.4-forge",   "1.20.4",  "forge",    SRC_1203_FORGE),
    ("HeartSystem-1.20.4-fabric",  "1.20.4",  "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.20.4-neoforge","1.20.4",  "neoforge", SRC_120_NEO),
    ("HeartSystem-1.20.5-fabric",  "1.20.5",  "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.20.5-neoforge","1.20.5",  "neoforge", _SRC_1205_NEO),
    ("HeartSystem-1.20.6-forge",   "1.20.6",  "forge",    SRC_1203_FORGE),
    ("HeartSystem-1.20.6-fabric",  "1.20.6",  "fabric",   SRC_119_FABRIC),
    ("HeartSystem-1.20.6-neoforge","1.20.6",  "neoforge", _SRC_1205_NEO),
    ("HeartSystem-1.21-forge",     "1.21",    "forge",    SRC_1203_FORGE),
    ("HeartSystem-1.21-fabric",    "1.21",    "fabric",   SRC_121_FABRIC),
    ("HeartSystem-1.21-neoforge",  "1.21",    "neoforge", SRC_121_NEO),
    ("HeartSystem-1.21.1-forge",   "1.21.1",  "forge",    SRC_1203_FORGE),
    ("HeartSystem-1.21.1-fabric",  "1.21.1",  "fabric",   SRC_121_FABRIC),
    ("HeartSystem-1.21.1-neoforge","1.21.1",  "neoforge", SRC_121_NEO),
    ("HeartSystem-1.21.2-fabric",  "1.21.2",  "fabric",   SRC_121_FABRIC),
    ("HeartSystem-1.21.2-neoforge","1.21.2",  "neoforge", SRC_121_NEO),
    ("HeartSystem-1.21.3-forge",   "1.21.3",  "forge",    SRC_1203_FORGE),
    ("HeartSystem-1.21.3-fabric",  "1.21.3",  "fabric",   SRC_121_FABRIC),
    ("HeartSystem-1.21.3-neoforge","1.21.3",  "neoforge", SRC_121_NEO),
    ("HeartSystem-1.21.4-forge",   "1.21.4",  "forge",    SRC_1203_FORGE),
    ("HeartSystem-1.21.4-fabric",  "1.21.4",  "fabric",   SRC_121_FABRIC),
    ("HeartSystem-1.21.4-neoforge","1.21.4",  "neoforge", SRC_121_NEO),
    ("HeartSystem-1.21.5-forge",   "1.21.5",  "forge",    SRC_1203_FORGE),
    ("HeartSystem-1.21.5-fabric",  "1.21.5",  "fabric",   SRC_121_FABRIC),
    ("HeartSystem-1.21.5-neoforge","1.21.5",  "neoforge", SRC_121_NEO),
    ("HeartSystem-1.21.6-forge",   "1.21.6",  "forge",    SRC_1216_FORGE),
    ("HeartSystem-1.21.6-fabric",  "1.21.6",  "fabric",   SRC_121_FABRIC),
    ("HeartSystem-1.21.6-neoforge","1.21.6",  "neoforge", SRC_121_NEO),
    ("HeartSystem-1.21.7-forge",   "1.21.7",  "forge",    SRC_1216_FORGE),
    ("HeartSystem-1.21.7-fabric",  "1.21.7",  "fabric",   SRC_121_FABRIC),
    ("HeartSystem-1.21.7-neoforge","1.21.7",  "neoforge", SRC_121_NEO),
    ("HeartSystem-1.21.8-forge",   "1.21.8",  "forge",    SRC_1216_FORGE),
    ("HeartSystem-1.21.8-fabric",  "1.21.8",  "fabric",   SRC_121_FABRIC),
    ("HeartSystem-1.21.8-neoforge","1.21.8",  "neoforge", SRC_121_NEO),
    ("HeartSystem-1.21.9-forge",   "1.21.9",  "forge",    SRC_1219_FORGE),
    ("HeartSystem-1.21.9-fabric",  "1.21.9",  "fabric",   SRC_1219_FABRIC),
    ("HeartSystem-1.21.9-neoforge","1.21.9",  "neoforge", SRC_1219_NEO),
    ("HeartSystem-1.21.10-forge",  "1.21.10", "forge",    SRC_1219_FORGE),
    ("HeartSystem-1.21.10-fabric", "1.21.10", "fabric",   SRC_1219_FABRIC),
    ("HeartSystem-1.21.10-neoforge","1.21.10","neoforge", SRC_1219_NEO),
    ("HeartSystem-1.21.11-forge",  "1.21.11", "forge",    SRC_1219_FORGE),
    ("HeartSystem-1.21.11-fabric", "1.21.11", "fabric",   SRC_1219_FABRIC),
    ("HeartSystem-1.21.11-neoforge","1.21.11","neoforge", SRC_1219_NEO),
    ("HeartSystem-26.1-fabric",    "26.1",    "fabric",   SRC_1219_FABRIC),
    ("HeartSystem-26.1-neoforge",  "26.1",    "neoforge", {
        "HeartSystemMod.java":   _NEO_1219_MOD,
        "HeartEventHandler.java": _FORGE_1219_HANDLER,
        "HeartStorage.java":     _FORGE_STORAGE_NBTIO_PATH_OPT,
        "HeartData.java":        _FORGE_DATA_IDENTIFIER_ADDVALUE,
        "HeartConfig.java":      _NEO_1219_CONFIG,
    }),
    ("HeartSystem-26.1.1-fabric",  "26.1.1",  "fabric",   SRC_1219_FABRIC),
    ("HeartSystem-26.1.1-neoforge","26.1.1",  "neoforge", {
        "HeartSystemMod.java":   _NEO_1219_MOD,
        "HeartEventHandler.java": _FORGE_1219_HANDLER,
        "HeartStorage.java":     _FORGE_STORAGE_NBTIO_PATH_OPT,
        "HeartData.java":        _FORGE_DATA_IDENTIFIER_ADDVALUE,
        "HeartConfig.java":      _NEO_1219_CONFIG,
    }),
    ("HeartSystem-26.1.2-forge",   "26.1.2",  "forge",    {
        "HeartSystemMod.java":   _FORGE_1216_MOD,
        "HeartEventHandler.java": _FORGE_1219_HANDLER,
        "HeartStorage.java":     _FORGE_STORAGE_NBTIO_PATH_OPT,
        "HeartData.java":        _FORGE_DATA_IDENTIFIER_ADDVALUE,
        "HeartConfig.java":      _FORGE_CONFIG_FMLJAVAMOD,
    }),
    ("HeartSystem-26.1.2-fabric",  "26.1.2",  "fabric",   SRC_1219_FABRIC),
    ("HeartSystem-26.1.2-neoforge","26.1.2",  "neoforge", {
        "HeartSystemMod.java":   _NEO_1219_MOD,
        "HeartEventHandler.java": _FORGE_1219_HANDLER,
        "HeartStorage.java":     _FORGE_STORAGE_NBTIO_PATH_OPT,
        "HeartData.java":        _FORGE_DATA_IDENTIFIER_ADDVALUE,
        "HeartConfig.java":      _NEO_1219_CONFIG,
    }),
]


def get_failed_targets():
    runs_dir = ROOT / "ModCompileRuns"
    if not runs_dir.exists():
        return set()
    for run in sorted(runs_dir.iterdir(), reverse=True):
        mods_dir = run / "artifacts" / "all-mod-builds" / "mods"
        if mods_dir.exists():
            failed = set()
            for mod_dir in mods_dir.iterdir():
                if not mod_dir.is_dir():
                    continue
                r = mod_dir / "result.json"
                if r.exists():
                    try:
                        d = json.loads(r.read_text())
                        if d.get("status") != "success":
                            # Map slug back to folder name
                            slug = mod_dir.name  # e.g. heartsystem-forge-1-19
                            failed.add(slug)
                    except Exception:
                        pass
            if failed:
                return failed
    return set()


def slug_to_folder(slug):
    """Convert artifact slug like 'heartsystem-forge-1-19' to folder name 'HeartSystem-1.19-forge'."""
    # slug format: heartsystem-<loader>-<version-with-dashes>
    parts = slug.split("-")
    # parts[0] = heartsystem, parts[1] = loader, parts[2:] = version
    if len(parts) < 3:
        return None
    loader = parts[1]
    version = ".".join(parts[2:])
    return f"HeartSystem-{version}-{loader}"


def generate(failed_only: bool = False) -> None:
    if BUNDLE_DIR.exists():
        shutil.rmtree(BUNDLE_DIR)
    BUNDLE_DIR.mkdir(parents=True)

    failed_slugs = get_failed_targets() if failed_only else set()
    failed_folders = {slug_to_folder(s) for s in failed_slugs} if failed_only else set()

    included = []
    skipped  = []

    for (folder, mc, loader, src_dict) in TARGETS:
        if folder in ALREADY_PUBLISHED:
            skipped.append((folder, "already published"))
            continue
        if failed_only and failed_folders and folder not in failed_folders:
            skipped.append((folder, "not failed"))
            continue

        target_dir = BUNDLE_DIR / folder
        files = make_files(src_dict)
        for rel_path, content in files.items():
            write(target_dir / rel_path, content)

        entrypoint = f"{GROUP}.HeartSystemMod"
        write(target_dir / "mod.txt", mod_txt(entrypoint))
        write(target_dir / "version.txt", version_txt(mc, loader))
        included.append(folder)

    if not included:
        print("No targets to build.")
        return

    if ZIP_PATH.exists():
        ZIP_PATH.unlink()
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as zf:
        for folder_name in included:
            folder_path = BUNDLE_DIR / folder_name
            for file_path in sorted(folder_path.rglob("*")):
                if file_path.is_file():
                    arcname = folder_name + "/" + file_path.relative_to(folder_path).as_posix()
                    zf.write(file_path, arcname)

    with zipfile.ZipFile(ZIP_PATH) as zf:
        tops = {n.split("/")[0] for n in zf.namelist()}
        assert "incoming" not in tops, f"Bad zip: 'incoming' at top level!"

    print(f"Generated {len(included)} targets -> {ZIP_PATH}")
    print(f"Skipped   {len(skipped)} targets")
    print(f"Top-level entries sample: {sorted(tops)[:3]}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed-only", action="store_true")
    args = parser.parse_args()
    generate(failed_only=args.failed_only)


if __name__ == "__main__":
    main()
