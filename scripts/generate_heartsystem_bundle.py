#!/usr/bin/env python3
"""
Generates the Heart System (Lifesteal Parrot Mod) bundle for all missing MC versions and loaders.
Mod: https://modrinth.com/mod/lifesteal-parrot-mod
Server-side mod: lose a heart on death, gain a heart by killing a player.
Reach 0 hearts and you are permanently banned.

Already published (skip these):
  1.12.2  forge

Run:
    python3 scripts/generate_heartsystem_bundle.py
    python3 scripts/generate_heartsystem_bundle.py --failed-only
"""

import argparse
import json
import shutil
import sys
import zipfile
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
# 1.8.9 FORGE
# Java 6: no underscores in literals, no diamond <>, no lambdas, no removeIf
# TickEvent in net.minecraftforge.fml.common.gameevent (1.8.9 only)
# EntityPlayerMP, EntityPlayer, DamageSource from net.minecraft.entity.player/util
# UserListBans / UserListBansEntry for banning
# NBT: CompressedStreamTools, NBTTagCompound
# SharedMonsterAttributes.maxHealth (field, not method)
# AttributeModifier with operation 0
# event.world is a public field in 1.8.9
# No getServer() on EntityPlayerMP — use MinecraftServer.getServer()
# ===========================================================================
SRC_189_FORGE_MOD = """\
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
"""

SRC_189_FORGE_HANDLER = """\
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
            int start = HeartSystemMod.config.getStartHearts();
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), start);
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
            HeartData data = new HeartData(hearts);
            data.applyMaxHealth((EntityPlayerMP) newPlayer);
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
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                server.getConfigurationManager().sendChatMsg(
                    new ChatComponentText("\\u00a7c[HeartSystem] " + deadPlayer.getCommandSenderName() + " has been permanently banned (0 hearts)."));
                UserListBans banList = server.getConfigurationManager().getBannedPlayers();
                UserListBansEntry banEntry = new UserListBansEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getCommandSenderName()),
                    null, null, null, "Permadeath: ran out of hearts"
                );
                banList.addEntry(banEntry);
            }
            deadPlayer.playerNetServerHandler.kickPlayerFromServer("\\u00a7cYou have been permanently banned.\\nYou ran out of hearts.");
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
                HeartData killerData = new HeartData(killerHearts);
                killerData.applyMaxHealth(killerPlayer);
                killerPlayer.addChatMessage(new ChatComponentText(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.addChatMessage(new ChatComponentText(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
"""

SRC_189_FORGE_STORAGE = """\
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
                HeartData data = HeartData.fromNBT(tag);
                int h = data.getHearts();
                cache.put(uuid, Integer.valueOf(h));
                return h;
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load heart data for {}: {}", playerUUID, e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, Integer.valueOf(hearts));
        HeartData data = new HeartData(hearts);
        try {
            CompressedStreamTools.write(data.toNBT(), playerFile);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save heart data for {}: {}", playerUUID, e.getMessage());
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
"""

SRC_189_FORGE_DATA = """\
package asd.itamio.heartsystem;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";
    private int hearts;

    public HeartData(int hearts) { this.hearts = hearts; }
    public int getHearts() { return hearts; }
    public void setHearts(int hearts) { this.hearts = hearts; }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("hearts", hearts);
        return tag;
    }

    public static HeartData fromNBT(NBTTagCompound tag) {
        int h = tag.hasKey("hearts") ? tag.getInteger("hearts") : -1;
        return new HeartData(h);
    }

    public void applyMaxHealth(EntityPlayerMP player) {
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(attr.getModifier(MODIFIER_UUID));
        }
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, 0);
        attr.applyModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }
}
"""

SRC_189_FORGE_CONFIG = """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class HeartConfig {
    private static final String CAT = Configuration.CATEGORY_GENERAL;
    private final Configuration config;
    private int startHearts;
    private int maxHearts;
    private int minHearts;

    public HeartConfig(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    private void load() {
        config.load();
        startHearts = config.getInt("startHearts", CAT, 10, 1, 100,
            "Number of hearts a new player starts with. (1 heart = 2 HP)");
        maxHearts = config.getInt("maxHearts", CAT, 20, 1, 100,
            "Maximum hearts a player can have.");
        minHearts = config.getInt("minHearts", CAT, 0, 0, 99,
            "Minimum hearts before permadeath triggers.");
        if (config.hasChanged()) config.save();
    }

    public int getStartHearts() { return startHearts; }
    public int getMaxHearts()   { return maxHearts; }
    public int getMinHearts()   { return minHearts; }
}
"""


# ===========================================================================
# 1.12.2 FORGE (already published — included for reference)
# Uses same structure as 1.8.9 but with updated API names
# ===========================================================================
SRC_1122_FORGE_MOD = """\
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
"""

SRC_1122_FORGE_HANDLER = """\
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
            int start = HeartSystemMod.config.getStartHearts();
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), start);
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
            HeartData data = new HeartData(hearts);
            data.applyMaxHealth((EntityPlayerMP) newPlayer);
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
            broadcastMessage(deadPlayer.getServer(),
                "\\u00a7c[HeartSystem] " + deadPlayer.getName() + " has been permanently banned (0 hearts).");
            UserListBans banList = deadPlayer.getServer().getPlayerList().getBannedPlayers();
            UserListBansEntry banEntry = new UserListBansEntry(
                new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName()),
                null, null, null, "Permadeath: ran out of hearts"
            );
            banList.addEntry(banEntry);
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
                HeartData killerData = new HeartData(killerHearts);
                killerData.applyMaxHealth(killerPlayer);
                killerPlayer.sendMessage(new TextComponentString(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.sendMessage(new TextComponentString(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }

    private void broadcastMessage(net.minecraft.server.MinecraftServer server, String msg) {
        if (server == null) return;
        server.getPlayerList().sendMessage(new TextComponentString(msg));
    }
}
"""

SRC_1122_FORGE_STORAGE = """\
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
                HeartData data = HeartData.fromNBT(tag);
                int h = data.getHearts();
                cache.put(uuid, h);
                return h;
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        HeartData data = new HeartData(hearts);
        try {
            CompressedStreamTools.write(data.toNBT(), playerFile);
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

SRC_1122_FORGE_DATA = """\
package asd.itamio.heartsystem;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";
    private int hearts;

    public HeartData(int hearts) { this.hearts = hearts; }
    public int getHearts() { return hearts; }
    public void setHearts(int hearts) { this.hearts = hearts; }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("hearts", hearts);
        return tag;
    }

    public static HeartData fromNBT(NBTTagCompound tag) {
        int h = tag.hasKey("hearts") ? tag.getInteger("hearts") : -1;
        return new HeartData(h);
    }

    public void applyMaxHealth(EntityPlayerMP player) {
        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(attr.getModifier(MODIFIER_UUID));
        }
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, 0);
        attr.applyModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }
}
"""

SRC_1122_FORGE_CONFIG = """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class HeartConfig {
    private static final String CAT = Configuration.CATEGORY_GENERAL;
    private final Configuration config;
    private int startHearts;
    private int maxHearts;
    private int minHearts;

    public HeartConfig(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    private void load() {
        config.load();
        startHearts = config.getInt("startHearts", CAT, 10, 1, 100,
            "Number of hearts a new player starts with. (1 heart = 2 HP)");
        maxHearts = config.getInt("maxHearts", CAT, 20, 1, 100,
            "Maximum hearts a player can have.");
        minHearts = config.getInt("minHearts", CAT, 0, 0, 99,
            "Minimum hearts before permadeath triggers.");
        if (config.hasChanged()) config.save();
    }

    public int getStartHearts() { return startHearts; }
    public int getMaxHearts()   { return maxHearts; }
    public int getMinHearts()   { return minHearts; }
}
"""


# ===========================================================================
# 1.16.5 FORGE (mods.toml era, EventBus 6)
# Constructor-based registration: MinecraftForge.EVENT_BUS.register(this)
# EntityPlayerMP -> ServerPlayerEntity (Mojang: ServerPlayer)
# Actually in 1.16.5 Forge: still EntityPlayerMP? No — 1.16.5 uses Mojang names
# net.minecraft.entity.player.ServerPlayerEntity
# net.minecraft.server.MinecraftServer
# net.minecraft.util.DamageSource
# net.minecraft.util.text.StringTextComponent (1.16.5)
# PlayerEvent.LoadFromFile / SaveToFile still exist
# PlayerEvent.Clone still exists
# LivingDeathEvent still exists
# SharedMonsterAttributes.MAX_HEALTH (Attributes.MAX_HEALTH in 1.16.5)
# net.minecraft.entity.ai.attributes.Attributes
# net.minecraft.entity.ai.attributes.AttributeModifier
# net.minecraft.entity.ai.attributes.ModifiableAttributeInstance
# Ban: server.getPlayerList().getBans() -> UserBanList
# UserBanListEntry
# player.connection.disconnect(ITextComponent)
# ===========================================================================
SRC_1165_FORGE_MOD = """\
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
        logger.info("[HeartSystem] Heart-based permadeath system active.");
    }
}
"""

SRC_1165_FORGE_HANDLER = """\
package asd.itamio.heartsystem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.BanList;
import net.minecraft.server.management.UserBanList;
import net.minecraft.server.management.UserBanListEntry;
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
            int start = HeartSystemMod.config.getStartHearts();
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), start);
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
            HeartData data = new HeartData(hearts);
            data.applyMaxHealth((ServerPlayerEntity) newPlayer);
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
                server.getPlayerList().broadcastMessage(
                    new StringTextComponent("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), net.minecraft.util.text.ChatType.SYSTEM, net.minecraft.util.Util.NIL_UUID);
                UserBanList banList = server.getPlayerList().getBans();
                UserBanListEntry banEntry = new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"
                );
                banList.add(banEntry);
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
                HeartData killerData = new HeartData(killerHearts);
                killerData.applyMaxHealth(killerPlayer);
                killerPlayer.sendMessage(new StringTextComponent(
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts), killerPlayer.getUUID());
            } else {
                killerPlayer.sendMessage(new StringTextComponent(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."), killerPlayer.getUUID());
            }
        }
    }
}
"""

SRC_1165_FORGE_STORAGE = """\
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
                HeartData data = HeartData.fromNBT(tag);
                int h = data.getHearts();
                cache.put(uuid, h);
                return h;
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        HeartData data = new HeartData(hearts);
        try {
            CompressedStreamTools.write(data.toNBT(), playerFile);
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

SRC_1165_FORGE_DATA = """\
package asd.itamio.heartsystem;

import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;

import java.util.UUID;

public class HeartData {
    private static final UUID MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_NAME = "heartsystem.maxhealth";
    private int hearts;

    public HeartData(int hearts) { this.hearts = hearts; }
    public int getHearts() { return hearts; }
    public void setHearts(int hearts) { this.hearts = hearts; }

    public CompoundNBT toNBT() {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("hearts", hearts);
        return tag;
    }

    public static HeartData fromNBT(CompoundNBT tag) {
        int h = tag.contains("hearts") ? tag.getInt("hearts") : -1;
        return new HeartData(h);
    }

    public void applyMaxHealth(ServerPlayerEntity player) {
        ModifiableAttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(MODIFIER_UUID);
        }
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }
}
"""

SRC_1165_FORGE_CONFIG = """\
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


# ===========================================================================
# 1.17.1 FORGE (mods.toml era, EventBus 6)
# net.minecraft.world.entity.player.Player (Mojang mappings)
# net.minecraft.server.level.ServerPlayer
# net.minecraft.network.chat.TextComponent (1.17-1.18)
# net.minecraft.server.players.UserBanList / UserBanListEntry
# net.minecraft.world.entity.ai.attributes.Attributes
# net.minecraft.world.entity.ai.attributes.AttributeInstance
# net.minecraft.world.entity.ai.attributes.AttributeModifier
# net.minecraft.nbt.CompoundTag (1.17+)
# net.minecraft.nbt.NbtIo (1.17+)
# PlayerEvent.LoadFromFile / SaveToFile still exist
# ===========================================================================
SRC_1171_FORGE_MOD = """\
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
        logger.info("[HeartSystem] Heart-based permadeath system active.");
    }
}
"""

SRC_1171_FORGE_HANDLER = """\
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
            int start = HeartSystemMod.config.getStartHearts();
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), start);
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
            HeartData data = new HeartData(hearts);
            data.applyMaxHealth((ServerPlayer) newPlayer);
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
                server.getPlayerList().broadcastMessage(
                    new TextComponent("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), true);
                UserBanList banList = server.getPlayerList().getBans();
                UserBanListEntry banEntry = new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"
                );
                banList.add(banEntry);
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
                HeartData killerData = new HeartData(killerHearts);
                killerData.applyMaxHealth(killerPlayer);
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

SRC_1171_FORGE_STORAGE = """\
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
                if (tag == null) return -1;
                HeartData data = HeartData.fromNBT(tag);
                int h = data.getHearts();
                cache.put(uuid, h);
                return h;
            } catch (IOException e) {
                HeartSystemMod.logger.error("[HeartSystem] Failed to load: {}", e.getMessage());
            }
        }
        return -1;
    }

    public void save(String playerUUID, File playerFile, int hearts) {
        UUID uuid = UUID.fromString(playerUUID);
        cache.put(uuid, hearts);
        HeartData data = new HeartData(hearts);
        try {
            NbtIo.write(data.toNBT(), playerFile);
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

SRC_1171_FORGE_DATA = """\
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
    private int hearts;

    public HeartData(int hearts) { this.hearts = hearts; }
    public int getHearts() { return hearts; }
    public void setHearts(int hearts) { this.hearts = hearts; }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("hearts", hearts);
        return tag;
    }

    public static HeartData fromNBT(CompoundTag tag) {
        int h = tag.contains("hearts") ? tag.getInt("hearts") : -1;
        return new HeartData(h);
    }

    public void applyMaxHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(MODIFIER_UUID);
        }
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }
}
"""

SRC_1171_FORGE_CONFIG = SRC_1165_FORGE_CONFIG


# ===========================================================================
# 1.18-1.18.2 FORGE — same as 1.17.1 (same API era)
# ===========================================================================
SRC_118_FORGE_MOD     = SRC_1171_FORGE_MOD
SRC_118_FORGE_HANDLER = SRC_1171_FORGE_HANDLER
SRC_118_FORGE_STORAGE = SRC_1171_FORGE_STORAGE
SRC_118_FORGE_DATA    = SRC_1171_FORGE_DATA
SRC_118_FORGE_CONFIG  = SRC_1165_FORGE_CONFIG

# ===========================================================================
# 1.19-1.20.6 FORGE (EventBus 6)
# sendMessage(Component, UUID) removed — use sendSystemMessage(Component)
# net.minecraft.network.chat.Component (1.19+)
# DamageSource: event.getSource() still works
# source.getEntity() still works
# ===========================================================================
SRC_119_FORGE_MOD = SRC_1171_FORGE_MOD

SRC_119_FORGE_HANDLER = """\
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
        if (player.level().isClientSide()) return;
        String uuidStr = event.getPlayerUUID();
        File file = event.getPlayerFile("heartsystem");
        int loaded = HeartStorage.get().load(uuidStr, file);
        if (loaded < 0) {
            int start = HeartSystemMod.config.getStartHearts();
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), start);
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
            HeartData data = new HeartData(hearts);
            data.applyMaxHealth((ServerPlayer) newPlayer);
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
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), false);
                UserBanList banList = server.getPlayerList().getBans();
                UserBanListEntry banEntry = new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"
                );
                banList.add(banEntry);
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
                HeartData killerData = new HeartData(killerHearts);
                killerData.applyMaxHealth(killerPlayer);
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

SRC_119_FORGE_STORAGE = SRC_1171_FORGE_STORAGE
SRC_119_FORGE_DATA    = SRC_1171_FORGE_DATA
SRC_119_FORGE_CONFIG  = SRC_1165_FORGE_CONFIG

# ===========================================================================
# 1.21-1.21.5 FORGE (EventBus 6) — same as 1.19 era
# ===========================================================================
SRC_121_FORGE_MOD     = SRC_119_FORGE_MOD
SRC_121_FORGE_HANDLER = SRC_119_FORGE_HANDLER
SRC_121_FORGE_STORAGE = SRC_1171_FORGE_STORAGE
SRC_121_FORGE_DATA    = SRC_1171_FORGE_DATA
SRC_121_FORGE_CONFIG  = SRC_1165_FORGE_CONFIG

# ===========================================================================
# 1.21.6-1.21.8 FORGE (EventBus 7)
# Constructor takes FMLJavaModLoadingContext
# LivingDeathEvent.BUS.addListener() / PlayerEvent.LoadFromFile.BUS.addListener()
# ===========================================================================
SRC_1216_FORGE_MOD = """\
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
        logger.info("[HeartSystem] Heart-based permadeath system active.");
    }
}
"""

SRC_1216_FORGE_HANDLER = """\
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
            int start = HeartSystemMod.config.getStartHearts();
            HeartStorage.get().setHearts(UUID.fromString(uuidStr), start);
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
            HeartData data = new HeartData(hearts);
            data.applyMaxHealth((ServerPlayer) newPlayer);
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
                UserBanListEntry banEntry = new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"
                );
                banList.add(banEntry);
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
                HeartData killerData = new HeartData(killerHearts);
                killerData.applyMaxHealth(killerPlayer);
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

SRC_1216_FORGE_STORAGE = SRC_1171_FORGE_STORAGE
SRC_1216_FORGE_DATA    = SRC_1171_FORGE_DATA

SRC_1216_FORGE_CONFIG = """\
package asd.itamio.heartsystem;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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
# 1.21.9-26.1.2 FORGE (EventBus 7, same as 1.21.6 era)
# ===========================================================================
SRC_1219_FORGE_MOD     = SRC_1216_FORGE_MOD
SRC_1219_FORGE_HANDLER = SRC_1216_FORGE_HANDLER
SRC_1219_FORGE_STORAGE = SRC_1171_FORGE_STORAGE
SRC_1219_FORGE_DATA    = SRC_1171_FORGE_DATA
SRC_1219_FORGE_CONFIG  = SRC_1216_FORGE_CONFIG


# ===========================================================================
# FABRIC 1.16.5 (presplit, yarn mappings)
# net.minecraft.entity.player.PlayerEntity (yarn)
# net.minecraft.server.network.ServerPlayerEntity (yarn)
# net.minecraft.text.LiteralText (1.16.5-1.18)
# net.minecraft.server.BannedPlayerList / BannedPlayerEntry
# net.minecraft.entity.attribute.EntityAttributes (yarn)
# net.minecraft.entity.attribute.EntityAttributeInstance (yarn)
# net.minecraft.entity.attribute.EntityAttributeModifier (yarn)
# net.minecraft.nbt.NbtCompound (1.16.5 uses CompoundTag? No — yarn uses NbtCompound)
# Actually 1.16.5 yarn: net.minecraft.nbt.CompoundTag
# ServerLifecycleEvents for init, ServerPlayConnectionEvents for player join
# net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
# PlayerEvent doesn't exist in Fabric — use Mixin or ServerPlayConnectionEvents
# For player death: use ServerLivingEntityMixin or ServerPlayerEvents
# Fabric API: net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
# ServerPlayerEvents.AFTER_RESPAWN for clone
# For death: net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH
# For player data load/save: use ServerPlayConnectionEvents.JOIN + custom NBT
# ===========================================================================
SRC_1165_FABRIC_MOD = """\
package asd.itamio.heartsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HeartSystemMod implements ModInitializer {
    public static final String MOD_ID = "heartsystem";
    public static final Logger logger = LogManager.getLogger();
    public static HeartConfig config;

    @Override
    public void onInitialize() {
        config = new HeartConfig();

        // Load hearts when player joins
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            HeartStorage.get().loadOrInit(player, config.getStartHearts());
            HeartData.applyMaxHealth(player, HeartStorage.get().getHearts(player.getUuid()));
        });

        // Save hearts when player disconnects
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            HeartStorage.get().save(player);
        });

        // Copy hearts on respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                HeartStorage.get().copyHearts(oldPlayer.getUuid(), newPlayer.getUuid());
            }
            HeartData.applyMaxHealth(newPlayer, HeartStorage.get().getHearts(newPlayer.getUuid()));
        });

        // Handle death
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof ServerPlayerEntity)) return ActionResult.PASS;
            ServerPlayerEntity deadPlayer = (ServerPlayerEntity) entity;
            HeartEventHandler.handleDeath(deadPlayer, damageSource, config);
            return ActionResult.PASS;
        });
    }
}
"""

SRC_1165_FABRIC_HANDLER = """\
package asd.itamio.heartsystem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.DamageSource;

import java.util.UUID;

public class HeartEventHandler {

    public static void handleDeath(ServerPlayerEntity deadPlayer, DamageSource source, HeartConfig config) {
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
                    new net.minecraft.server.BannedPlayerEntry(
                        new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                        null, null, null, "Permadeath: ran out of hearts"
                    )
                );
            }
            deadPlayer.networkHandler.disconnect(new LiteralText(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(new LiteralText(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts), deadPlayer.getUuid());
        }
        Entity killer = source.getAttacker();
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
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts), killerPlayer.getUuid());
            } else {
                killerPlayer.sendMessage(new LiteralText(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."), killerPlayer.getUuid());
            }
        }
    }
}
"""

SRC_1165_FABRIC_STORAGE = """\
package asd.itamio.heartsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.network.ServerPlayerEntity;

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
                CompoundTag tag = NbtIo.read(file);
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
        CompoundTag tag = new CompoundTag();
        tag.putInt("hearts", hearts);
        try {
            NbtIo.write(tag, file);
        } catch (IOException e) {
            HeartSystemMod.logger.error("[HeartSystem] Failed to save: {}", e.getMessage());
        }
    }

    private File getFile(ServerPlayerEntity player) {
        File worldDir = player.getServer().getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA).toFile();
        return new File(worldDir, player.getUuidAsString() + ".heartsystem.dat");
    }

    public boolean has(UUID uuid) { return cache.containsKey(uuid); }
    public int getHearts(UUID uuid) { Integer h = cache.get(uuid); return h != null ? h : -1; }
    public void setHearts(UUID uuid, int hearts) { cache.put(uuid, hearts); }
    public void copyHearts(UUID from, UUID to) {
        Integer h = cache.get(from);
        if (h != null) cache.put(to, h);
    }
    public void remove(UUID uuid) { cache.remove(uuid); }
}
"""

SRC_1165_FABRIC_DATA = """\
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
        if (attr.getModifier(MODIFIER_UUID) != null) {
            attr.removeModifier(MODIFIER_UUID);
        }
        double delta = (hearts * 2.0) - 20.0;
        EntityAttributeModifier mod = new EntityAttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, EntityAttributeModifier.Operation.ADDITION);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }
}
"""

SRC_1165_FABRIC_CONFIG = """\
package asd.itamio.heartsystem;

public class HeartConfig {
    private final int startHearts;
    private final int maxHearts;
    private final int minHearts;

    public HeartConfig() {
        // Simple hardcoded defaults — config file support can be added later
        this.startHearts = 10;
        this.maxHearts   = 20;
        this.minHearts   = 0;
    }

    public int getStartHearts() { return startHearts; }
    public int getMaxHearts()   { return maxHearts; }
    public int getMinHearts()   { return minHearts; }
}
"""


# ===========================================================================
# FABRIC 1.17-1.18.2 (presplit, yarn mappings)
# Same API as 1.16.5 — LiteralText still exists, same event hooks
# ===========================================================================
SRC_117_FABRIC_MOD     = SRC_1165_FABRIC_MOD
SRC_117_FABRIC_HANDLER = SRC_1165_FABRIC_HANDLER
SRC_117_FABRIC_STORAGE = SRC_1165_FABRIC_STORAGE
SRC_117_FABRIC_DATA    = SRC_1165_FABRIC_DATA
SRC_117_FABRIC_CONFIG  = SRC_1165_FABRIC_CONFIG

# ===========================================================================
# FABRIC 1.19-1.20.6 (yarn mappings)
# LiteralText removed — use Text.literal()
# broadcastChatMessage -> broadcastSystemMessage (1.19+)
# source.getAttacker() still works
# ===========================================================================
SRC_119_FABRIC_MOD = """\
package asd.itamio.heartsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
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

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof ServerPlayerEntity)) return ActionResult.PASS;
            HeartEventHandler.handleDeath((ServerPlayerEntity) entity, damageSource, config);
            return ActionResult.PASS;
        });
    }
}
"""

SRC_119_FABRIC_HANDLER = """\
package asd.itamio.heartsystem;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.DamageSource;

import java.util.UUID;

public class HeartEventHandler {

    public static void handleDeath(ServerPlayerEntity deadPlayer, DamageSource source, HeartConfig config) {
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
                server.getPlayerManager().broadcastSystemMessage(
                    Text.literal("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), false);
                server.getPlayerManager().getUserBanList().add(
                    new net.minecraft.server.BannedPlayerEntry(
                        new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                        null, null, null, "Permadeath: ran out of hearts"
                    )
                );
            }
            deadPlayer.networkHandler.disconnect(Text.literal(
                "\\u00a7cYou have been permanently banned.\\nYou ran out of hearts."));
        } else {
            HeartStorage.get().setHearts(deadUUID, hearts);
            deadPlayer.sendMessage(Text.literal(
                "\\u00a7c[HeartSystem] You lost a heart! Hearts remaining: " + hearts));
        }
        Entity killer = source.getAttacker();
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
                    "\\u00a7a[HeartSystem] You gained a heart! Hearts: " + killerHearts));
            } else {
                killerPlayer.sendMessage(Text.literal(
                    "\\u00a7e[HeartSystem] You killed a player but are already at max hearts (" + max + ")."));
            }
        }
    }
}
"""

SRC_119_FABRIC_STORAGE = SRC_1165_FABRIC_STORAGE
SRC_119_FABRIC_DATA    = SRC_1165_FABRIC_DATA
SRC_119_FABRIC_CONFIG  = SRC_1165_FABRIC_CONFIG


# ===========================================================================
# FABRIC 1.21-1.21.8 (Mojang mappings)
# net.minecraft.server.level.ServerPlayer (Mojang)
# net.minecraft.network.chat.Component (Mojang)
# net.minecraft.world.damagesource.DamageSource (Mojang)
# net.minecraft.world.entity.ai.attributes.Attributes (Mojang)
# net.minecraft.world.entity.ai.attributes.AttributeInstance (Mojang)
# net.minecraft.world.entity.ai.attributes.AttributeModifier (Mojang)
# server.getPlayerList().getBans() -> BanList
# player.getServer() works
# ServerPlayConnectionEvents still in fabric API
# ServerLivingEntityEvents.ALLOW_DEATH still works
# ServerPlayerEvents.AFTER_RESPAWN still works
# WorldSavePath -> LevelResource in Mojang mappings
# ===========================================================================
SRC_121_FABRIC_MOD = """\
package asd.itamio.heartsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ActionResult;
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

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof ServerPlayer)) return ActionResult.PASS;
            HeartEventHandler.handleDeath((ServerPlayer) entity, damageSource, config);
            return ActionResult.PASS;
        });
    }
}
"""

SRC_121_FABRIC_HANDLER = """\
package asd.itamio.heartsystem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public class HeartEventHandler {

    public static void handleDeath(ServerPlayer deadPlayer, DamageSource source, HeartConfig config) {
        UUID deadUUID = deadPlayer.getUUID();
        int hearts = HeartStorage.get().getHearts(deadUUID);
        if (hearts < 0) hearts = config.getStartHearts();
        hearts -= 1;
        int min = config.getMinHearts();
        if (hearts <= min) {
            hearts = min;
            HeartStorage.get().setHearts(deadUUID, hearts);
            MinecraftServer server = deadPlayer.getServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), false);
                UserBanList banList = server.getPlayerList().getBans();
                UserBanListEntry banEntry = new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"
                );
                banList.add(banEntry);
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

SRC_121_FABRIC_STORAGE = """\
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

SRC_121_FABRIC_DATA = """\
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
        attr.removeModifier(MODIFIER_UUID);
        double delta = (hearts * 2.0) - 20.0;
        AttributeModifier mod = new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, delta, AttributeModifier.Operation.ADDITION);
        attr.addPermanentModifier(mod);
        float newMax = (float)(hearts * 2);
        if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        }
    }
}
"""

SRC_121_FABRIC_CONFIG  = SRC_1165_FABRIC_CONFIG

# ===========================================================================
# FABRIC 1.21.9-26.1.2 (Mojang mappings) — same as 1.21 era
# handler.getPlayer() still works in 1.21.9+
# ===========================================================================
SRC_1219_FABRIC_MOD     = SRC_121_FABRIC_MOD
SRC_1219_FABRIC_HANDLER = SRC_121_FABRIC_HANDLER
SRC_1219_FABRIC_STORAGE = SRC_121_FABRIC_STORAGE
SRC_1219_FABRIC_DATA    = SRC_121_FABRIC_DATA
SRC_1219_FABRIC_CONFIG  = SRC_1165_FABRIC_CONFIG


# ===========================================================================
# NEOFORGE 1.20.2-1.20.6 (IEventBus constructor)
# net.neoforged.fml.common.Mod
# net.neoforged.bus.api.IEventBus
# net.neoforged.neoforge.common.NeoForge
# net.neoforged.neoforge.event.entity.living.LivingDeathEvent
# net.neoforged.neoforge.event.entity.player.PlayerEvent
# net.neoforged.bus.api.SubscribeEvent
# ServerStartingEvent for init
# net.minecraft.server.level.ServerPlayer
# net.minecraft.network.chat.Component
# net.minecraft.world.damagesource.DamageSource
# net.minecraft.world.entity.ai.attributes.Attributes / AttributeInstance / AttributeModifier
# net.minecraft.nbt.CompoundTag / NbtIo
# ===========================================================================
SRC_120_NEO_MOD = """\
package asd.itamio.heartsystem;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
        logger.info("[HeartSystem] Heart-based permadeath system active.");
    }
}
"""

SRC_120_NEO_HANDLER = """\
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
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("\\u00a7c[HeartSystem] " + deadPlayer.getName().getString() + " has been permanently banned (0 hearts)."), false);
                UserBanList banList = server.getPlayerList().getBans();
                UserBanListEntry banEntry = new UserBanListEntry(
                    new com.mojang.authlib.GameProfile(deadUUID, deadPlayer.getName().getString()),
                    null, null, null, "Permadeath: ran out of hearts"
                );
                banList.add(banEntry);
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

SRC_120_NEO_STORAGE = SRC_1171_FORGE_STORAGE
SRC_120_NEO_DATA    = SRC_1171_FORGE_DATA

SRC_120_NEO_CONFIG = """\
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

# ===========================================================================
# NEOFORGE 1.21-1.21.8 (IEventBus constructor) — same as 1.20 NeoForge
# ===========================================================================
SRC_121_NEO_MOD     = SRC_120_NEO_MOD
SRC_121_NEO_HANDLER = SRC_120_NEO_HANDLER
SRC_121_NEO_STORAGE = SRC_1171_FORGE_STORAGE
SRC_121_NEO_DATA    = SRC_1171_FORGE_DATA
SRC_121_NEO_CONFIG  = SRC_120_NEO_CONFIG

# ===========================================================================
# NEOFORGE 1.21.9-26.1.2 (ModContainer required in @Mod constructor)
# ===========================================================================
SRC_1219_NEO_MOD = """\
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
        logger.info("[HeartSystem] Heart-based permadeath system active.");
    }
}
"""

SRC_1219_NEO_HANDLER = SRC_120_NEO_HANDLER
SRC_1219_NEO_STORAGE = SRC_1171_FORGE_STORAGE
SRC_1219_NEO_DATA    = SRC_1171_FORGE_DATA

SRC_1219_NEO_CONFIG = """\
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


# ===========================================================================
# TARGET DEFINITIONS
# Each entry: (folder_name, mc_version, loader, files_dict)
# files_dict maps relative path -> source string
# ===========================================================================

def forge_files(mod, handler, storage, data, config):
    pkg = "asd/itamio/heartsystem"
    return {
        f"src/main/java/{pkg}/HeartSystemMod.java":   mod,
        f"src/main/java/{pkg}/HeartEventHandler.java": handler,
        f"src/main/java/{pkg}/HeartStorage.java":      storage,
        f"src/main/java/{pkg}/HeartData.java":         data,
        f"src/main/java/{pkg}/HeartConfig.java":       config,
    }

def fabric_files(mod, handler, storage, data, config):
    pkg = "asd/itamio/heartsystem"
    return {
        f"src/main/java/{pkg}/HeartSystemMod.java":   mod,
        f"src/main/java/{pkg}/HeartEventHandler.java": handler,
        f"src/main/java/{pkg}/HeartStorage.java":      storage,
        f"src/main/java/{pkg}/HeartData.java":         data,
        f"src/main/java/{pkg}/HeartConfig.java":       config,
    }

TARGETS = [
    # (folder_name, mc_version, loader, files)
    # ---- 1.8.9 ----
    ("HeartSystem-1.8.9-forge",   "1.8.9",   "forge",
     forge_files(SRC_189_FORGE_MOD, SRC_189_FORGE_HANDLER, SRC_189_FORGE_STORAGE, SRC_189_FORGE_DATA, SRC_189_FORGE_CONFIG)),

    # ---- 1.12 ----
    ("HeartSystem-1.12-forge",    "1.12",    "forge",
     forge_files(SRC_1122_FORGE_MOD, SRC_1122_FORGE_HANDLER, SRC_1122_FORGE_STORAGE, SRC_1122_FORGE_DATA, SRC_1122_FORGE_CONFIG)),

    # ---- 1.16.5 ----
    ("HeartSystem-1.16.5-forge",  "1.16.5",  "forge",
     forge_files(SRC_1165_FORGE_MOD, SRC_1165_FORGE_HANDLER, SRC_1165_FORGE_STORAGE, SRC_1165_FORGE_DATA, SRC_1165_FORGE_CONFIG)),
    ("HeartSystem-1.16.5-fabric", "1.16.5",  "fabric",
     fabric_files(SRC_1165_FABRIC_MOD, SRC_1165_FABRIC_HANDLER, SRC_1165_FABRIC_STORAGE, SRC_1165_FABRIC_DATA, SRC_1165_FABRIC_CONFIG)),

    # ---- 1.17 ----
    ("HeartSystem-1.17-fabric",   "1.17",    "fabric",
     fabric_files(SRC_117_FABRIC_MOD, SRC_117_FABRIC_HANDLER, SRC_117_FABRIC_STORAGE, SRC_117_FABRIC_DATA, SRC_117_FABRIC_CONFIG)),
    ("HeartSystem-1.17.1-forge",  "1.17.1",  "forge",
     forge_files(SRC_1171_FORGE_MOD, SRC_1171_FORGE_HANDLER, SRC_1171_FORGE_STORAGE, SRC_1171_FORGE_DATA, SRC_1171_FORGE_CONFIG)),

    # ---- 1.18 ----
    ("HeartSystem-1.18-forge",    "1.18",    "forge",
     forge_files(SRC_118_FORGE_MOD, SRC_118_FORGE_HANDLER, SRC_118_FORGE_STORAGE, SRC_118_FORGE_DATA, SRC_118_FORGE_CONFIG)),
    ("HeartSystem-1.18-fabric",   "1.18",    "fabric",
     fabric_files(SRC_117_FABRIC_MOD, SRC_117_FABRIC_HANDLER, SRC_117_FABRIC_STORAGE, SRC_117_FABRIC_DATA, SRC_117_FABRIC_CONFIG)),
    ("HeartSystem-1.18.1-forge",  "1.18.1",  "forge",
     forge_files(SRC_118_FORGE_MOD, SRC_118_FORGE_HANDLER, SRC_118_FORGE_STORAGE, SRC_118_FORGE_DATA, SRC_118_FORGE_CONFIG)),
    ("HeartSystem-1.18.2-forge",  "1.18.2",  "forge",
     forge_files(SRC_118_FORGE_MOD, SRC_118_FORGE_HANDLER, SRC_118_FORGE_STORAGE, SRC_118_FORGE_DATA, SRC_118_FORGE_CONFIG)),

    # ---- 1.19 ----
    ("HeartSystem-1.19-forge",    "1.19",    "forge",
     forge_files(SRC_119_FORGE_MOD, SRC_119_FORGE_HANDLER, SRC_119_FORGE_STORAGE, SRC_119_FORGE_DATA, SRC_119_FORGE_CONFIG)),
    ("HeartSystem-1.19-fabric",   "1.19",    "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),
    ("HeartSystem-1.19.1-forge",  "1.19.1",  "forge",
     forge_files(SRC_119_FORGE_MOD, SRC_119_FORGE_HANDLER, SRC_119_FORGE_STORAGE, SRC_119_FORGE_DATA, SRC_119_FORGE_CONFIG)),
    ("HeartSystem-1.19.1-fabric", "1.19.1",  "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),
    ("HeartSystem-1.19.2-forge",  "1.19.2",  "forge",
     forge_files(SRC_119_FORGE_MOD, SRC_119_FORGE_HANDLER, SRC_119_FORGE_STORAGE, SRC_119_FORGE_DATA, SRC_119_FORGE_CONFIG)),
    ("HeartSystem-1.19.2-fabric", "1.19.2",  "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),
    ("HeartSystem-1.19.3-forge",  "1.19.3",  "forge",
     forge_files(SRC_119_FORGE_MOD, SRC_119_FORGE_HANDLER, SRC_119_FORGE_STORAGE, SRC_119_FORGE_DATA, SRC_119_FORGE_CONFIG)),
    ("HeartSystem-1.19.3-fabric", "1.19.3",  "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),
    ("HeartSystem-1.19.4-forge",  "1.19.4",  "forge",
     forge_files(SRC_119_FORGE_MOD, SRC_119_FORGE_HANDLER, SRC_119_FORGE_STORAGE, SRC_119_FORGE_DATA, SRC_119_FORGE_CONFIG)),
    ("HeartSystem-1.19.4-fabric", "1.19.4",  "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),

    # ---- 1.20 ----
    ("HeartSystem-1.20.1-forge",    "1.20.1",  "forge",
     forge_files(SRC_119_FORGE_MOD, SRC_119_FORGE_HANDLER, SRC_119_FORGE_STORAGE, SRC_119_FORGE_DATA, SRC_119_FORGE_CONFIG)),
    ("HeartSystem-1.20.1-fabric",   "1.20.1",  "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),
    ("HeartSystem-1.20.2-forge",    "1.20.2",  "forge",
     forge_files(SRC_119_FORGE_MOD, SRC_119_FORGE_HANDLER, SRC_119_FORGE_STORAGE, SRC_119_FORGE_DATA, SRC_119_FORGE_CONFIG)),
    ("HeartSystem-1.20.2-fabric",   "1.20.2",  "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),
    ("HeartSystem-1.20.2-neoforge", "1.20.2",  "neoforge",
     forge_files(SRC_120_NEO_MOD, SRC_120_NEO_HANDLER, SRC_120_NEO_STORAGE, SRC_120_NEO_DATA, SRC_120_NEO_CONFIG)),
    ("HeartSystem-1.20.3-forge",    "1.20.3",  "forge",
     forge_files(SRC_119_FORGE_MOD, SRC_119_FORGE_HANDLER, SRC_119_FORGE_STORAGE, SRC_119_FORGE_DATA, SRC_119_FORGE_CONFIG)),
    ("HeartSystem-1.20.3-fabric",   "1.20.3",  "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),
    ("HeartSystem-1.20.4-forge",    "1.20.4",  "forge",
     forge_files(SRC_119_FORGE_MOD, SRC_119_FORGE_HANDLER, SRC_119_FORGE_STORAGE, SRC_119_FORGE_DATA, SRC_119_FORGE_CONFIG)),
    ("HeartSystem-1.20.4-fabric",   "1.20.4",  "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),
    ("HeartSystem-1.20.4-neoforge", "1.20.4",  "neoforge",
     forge_files(SRC_120_NEO_MOD, SRC_120_NEO_HANDLER, SRC_120_NEO_STORAGE, SRC_120_NEO_DATA, SRC_120_NEO_CONFIG)),
    ("HeartSystem-1.20.5-fabric",   "1.20.5",  "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),
    ("HeartSystem-1.20.5-neoforge", "1.20.5",  "neoforge",
     forge_files(SRC_120_NEO_MOD, SRC_120_NEO_HANDLER, SRC_120_NEO_STORAGE, SRC_120_NEO_DATA, SRC_120_NEO_CONFIG)),
    ("HeartSystem-1.20.6-forge",    "1.20.6",  "forge",
     forge_files(SRC_119_FORGE_MOD, SRC_119_FORGE_HANDLER, SRC_119_FORGE_STORAGE, SRC_119_FORGE_DATA, SRC_119_FORGE_CONFIG)),
    ("HeartSystem-1.20.6-fabric",   "1.20.6",  "fabric",
     fabric_files(SRC_119_FABRIC_MOD, SRC_119_FABRIC_HANDLER, SRC_119_FABRIC_STORAGE, SRC_119_FABRIC_DATA, SRC_119_FABRIC_CONFIG)),
    ("HeartSystem-1.20.6-neoforge", "1.20.6",  "neoforge",
     forge_files(SRC_120_NEO_MOD, SRC_120_NEO_HANDLER, SRC_120_NEO_STORAGE, SRC_120_NEO_DATA, SRC_120_NEO_CONFIG)),

    # ---- 1.21 ----
    ("HeartSystem-1.21-forge",      "1.21",    "forge",
     forge_files(SRC_121_FORGE_MOD, SRC_121_FORGE_HANDLER, SRC_121_FORGE_STORAGE, SRC_121_FORGE_DATA, SRC_121_FORGE_CONFIG)),
    ("HeartSystem-1.21-fabric",     "1.21",    "fabric",
     fabric_files(SRC_121_FABRIC_MOD, SRC_121_FABRIC_HANDLER, SRC_121_FABRIC_STORAGE, SRC_121_FABRIC_DATA, SRC_121_FABRIC_CONFIG)),
    ("HeartSystem-1.21-neoforge",   "1.21",    "neoforge",
     forge_files(SRC_121_NEO_MOD, SRC_121_NEO_HANDLER, SRC_121_NEO_STORAGE, SRC_121_NEO_DATA, SRC_121_NEO_CONFIG)),
    ("HeartSystem-1.21.1-forge",    "1.21.1",  "forge",
     forge_files(SRC_121_FORGE_MOD, SRC_121_FORGE_HANDLER, SRC_121_FORGE_STORAGE, SRC_121_FORGE_DATA, SRC_121_FORGE_CONFIG)),
    ("HeartSystem-1.21.1-neoforge", "1.21.1",  "neoforge",
     forge_files(SRC_121_NEO_MOD, SRC_121_NEO_HANDLER, SRC_121_NEO_STORAGE, SRC_121_NEO_DATA, SRC_121_NEO_CONFIG)),

    # ---- 1.21.2-1.21.8 ----
    ("HeartSystem-1.21.2-fabric",   "1.21.2",  "fabric",
     fabric_files(SRC_121_FABRIC_MOD, SRC_121_FABRIC_HANDLER, SRC_121_FABRIC_STORAGE, SRC_121_FABRIC_DATA, SRC_121_FABRIC_CONFIG)),
    ("HeartSystem-1.21.2-neoforge", "1.21.2",  "neoforge",
     forge_files(SRC_121_NEO_MOD, SRC_121_NEO_HANDLER, SRC_121_NEO_STORAGE, SRC_121_NEO_DATA, SRC_121_NEO_CONFIG)),
    ("HeartSystem-1.21.3-forge",    "1.21.3",  "forge",
     forge_files(SRC_121_FORGE_MOD, SRC_121_FORGE_HANDLER, SRC_121_FORGE_STORAGE, SRC_121_FORGE_DATA, SRC_121_FORGE_CONFIG)),
    ("HeartSystem-1.21.3-neoforge", "1.21.3",  "neoforge",
     forge_files(SRC_121_NEO_MOD, SRC_121_NEO_HANDLER, SRC_121_NEO_STORAGE, SRC_121_NEO_DATA, SRC_121_NEO_CONFIG)),
    ("HeartSystem-1.21.4-forge",    "1.21.4",  "forge",
     forge_files(SRC_121_FORGE_MOD, SRC_121_FORGE_HANDLER, SRC_121_FORGE_STORAGE, SRC_121_FORGE_DATA, SRC_121_FORGE_CONFIG)),
    ("HeartSystem-1.21.4-neoforge", "1.21.4",  "neoforge",
     forge_files(SRC_121_NEO_MOD, SRC_121_NEO_HANDLER, SRC_121_NEO_STORAGE, SRC_121_NEO_DATA, SRC_121_NEO_CONFIG)),
    ("HeartSystem-1.21.5-forge",    "1.21.5",  "forge",
     forge_files(SRC_121_FORGE_MOD, SRC_121_FORGE_HANDLER, SRC_121_FORGE_STORAGE, SRC_121_FORGE_DATA, SRC_121_FORGE_CONFIG)),
    ("HeartSystem-1.21.5-neoforge", "1.21.5",  "neoforge",
     forge_files(SRC_121_NEO_MOD, SRC_121_NEO_HANDLER, SRC_121_NEO_STORAGE, SRC_121_NEO_DATA, SRC_121_NEO_CONFIG)),
    ("HeartSystem-1.21.6-forge",    "1.21.6",  "forge",
     forge_files(SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, SRC_1216_FORGE_STORAGE, SRC_1216_FORGE_DATA, SRC_1216_FORGE_CONFIG)),
    ("HeartSystem-1.21.6-neoforge", "1.21.6",  "neoforge",
     forge_files(SRC_121_NEO_MOD, SRC_121_NEO_HANDLER, SRC_121_NEO_STORAGE, SRC_121_NEO_DATA, SRC_121_NEO_CONFIG)),
    ("HeartSystem-1.21.7-forge",    "1.21.7",  "forge",
     forge_files(SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, SRC_1216_FORGE_STORAGE, SRC_1216_FORGE_DATA, SRC_1216_FORGE_CONFIG)),
    ("HeartSystem-1.21.7-neoforge", "1.21.7",  "neoforge",
     forge_files(SRC_121_NEO_MOD, SRC_121_NEO_HANDLER, SRC_121_NEO_STORAGE, SRC_121_NEO_DATA, SRC_121_NEO_CONFIG)),
    ("HeartSystem-1.21.8-forge",    "1.21.8",  "forge",
     forge_files(SRC_1216_FORGE_MOD, SRC_1216_FORGE_HANDLER, SRC_1216_FORGE_STORAGE, SRC_1216_FORGE_DATA, SRC_1216_FORGE_CONFIG)),
    ("HeartSystem-1.21.8-neoforge", "1.21.8",  "neoforge",
     forge_files(SRC_121_NEO_MOD, SRC_121_NEO_HANDLER, SRC_121_NEO_STORAGE, SRC_121_NEO_DATA, SRC_121_NEO_CONFIG)),

    # ---- 1.21.9-1.21.11 ----
    ("HeartSystem-1.21.9-forge",    "1.21.9",  "forge",
     forge_files(SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, SRC_1219_FORGE_STORAGE, SRC_1219_FORGE_DATA, SRC_1219_FORGE_CONFIG)),
    ("HeartSystem-1.21.9-fabric",   "1.21.9",  "fabric",
     fabric_files(SRC_1219_FABRIC_MOD, SRC_1219_FABRIC_HANDLER, SRC_1219_FABRIC_STORAGE, SRC_1219_FABRIC_DATA, SRC_1219_FABRIC_CONFIG)),
    ("HeartSystem-1.21.9-neoforge", "1.21.9",  "neoforge",
     forge_files(SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, SRC_1219_NEO_STORAGE, SRC_1219_NEO_DATA, SRC_1219_NEO_CONFIG)),
    ("HeartSystem-1.21.10-forge",   "1.21.10", "forge",
     forge_files(SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, SRC_1219_FORGE_STORAGE, SRC_1219_FORGE_DATA, SRC_1219_FORGE_CONFIG)),
    ("HeartSystem-1.21.10-neoforge","1.21.10", "neoforge",
     forge_files(SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, SRC_1219_NEO_STORAGE, SRC_1219_NEO_DATA, SRC_1219_NEO_CONFIG)),
    ("HeartSystem-1.21.11-forge",   "1.21.11", "forge",
     forge_files(SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, SRC_1219_FORGE_STORAGE, SRC_1219_FORGE_DATA, SRC_1219_FORGE_CONFIG)),
    ("HeartSystem-1.21.11-neoforge","1.21.11", "neoforge",
     forge_files(SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, SRC_1219_NEO_STORAGE, SRC_1219_NEO_DATA, SRC_1219_NEO_CONFIG)),

    # ---- 26.1 ----
    ("HeartSystem-26.1-fabric",     "26.1",    "fabric",
     fabric_files(SRC_1219_FABRIC_MOD, SRC_1219_FABRIC_HANDLER, SRC_1219_FABRIC_STORAGE, SRC_1219_FABRIC_DATA, SRC_1219_FABRIC_CONFIG)),
    ("HeartSystem-26.1-neoforge",   "26.1",    "neoforge",
     forge_files(SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, SRC_1219_NEO_STORAGE, SRC_1219_NEO_DATA, SRC_1219_NEO_CONFIG)),
    ("HeartSystem-26.1.1-fabric",   "26.1.1",  "fabric",
     fabric_files(SRC_1219_FABRIC_MOD, SRC_1219_FABRIC_HANDLER, SRC_1219_FABRIC_STORAGE, SRC_1219_FABRIC_DATA, SRC_1219_FABRIC_CONFIG)),
    ("HeartSystem-26.1.1-neoforge", "26.1.1",  "neoforge",
     forge_files(SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, SRC_1219_NEO_STORAGE, SRC_1219_NEO_DATA, SRC_1219_NEO_CONFIG)),
    ("HeartSystem-26.1.2-forge",    "26.1.2",  "forge",
     forge_files(SRC_1219_FORGE_MOD, SRC_1219_FORGE_HANDLER, SRC_1219_FORGE_STORAGE, SRC_1219_FORGE_DATA, SRC_1219_FORGE_CONFIG)),
    ("HeartSystem-26.1.2-fabric",   "26.1.2",  "fabric",
     fabric_files(SRC_1219_FABRIC_MOD, SRC_1219_FABRIC_HANDLER, SRC_1219_FABRIC_STORAGE, SRC_1219_FABRIC_DATA, SRC_1219_FABRIC_CONFIG)),
    ("HeartSystem-26.1.2-neoforge", "26.1.2",  "neoforge",
     forge_files(SRC_1219_NEO_MOD, SRC_1219_NEO_HANDLER, SRC_1219_NEO_STORAGE, SRC_1219_NEO_DATA, SRC_1219_NEO_CONFIG)),
]


# ===========================================================================
# BUILD LOGIC
# ===========================================================================

ALREADY_PUBLISHED = {"HeartSystem-1.12.2-forge"}  # skip this one


def get_failed_targets():
    """Read the most recent ModCompileRuns/ run and return failed folder names."""
    runs_dir = ROOT / "ModCompileRuns"
    if not runs_dir.exists():
        return set()
    runs = sorted(runs_dir.iterdir(), reverse=True)
    for run in runs:
        result_file = run / "result.json"
        if result_file.exists():
            try:
                data = json.loads(result_file.read_text())
                failed = set()
                for mod_id, info in data.get("mods", {}).items():
                    if info.get("status") != "success":
                        failed.add(mod_id)
                return failed
            except Exception:
                pass
        # Also check artifacts subfolder
        artifacts = run / "artifacts" / "all-mod-builds"
        if artifacts.exists():
            failed = set()
            for mod_dir in artifacts.iterdir():
                if not mod_dir.is_dir():
                    continue
                r = mod_dir / "result.json"
                if r.exists():
                    try:
                        d = json.loads(r.read_text())
                        if d.get("status") != "success":
                            failed.add(mod_dir.name)
                    except Exception:
                        pass
            if failed:
                return failed
    return set()


def generate(failed_only: bool = False) -> None:
    if BUNDLE_DIR.exists():
        shutil.rmtree(BUNDLE_DIR)
    BUNDLE_DIR.mkdir(parents=True)

    failed = get_failed_targets() if failed_only else set()

    included = []
    skipped  = []

    for (folder, mc, loader, files) in TARGETS:
        # Skip already-published
        if folder in ALREADY_PUBLISHED:
            skipped.append((folder, "already published"))
            continue
        # In failed-only mode, skip targets not in the failed set
        if failed_only and failed and folder not in failed:
            skipped.append((folder, "not failed"))
            continue

        target_dir = BUNDLE_DIR / folder
        # Write source files
        for rel_path, content in files.items():
            write(target_dir / rel_path, content)
        # Write mod.txt
        entrypoint = f"{GROUP}.HeartSystemMod"
        write(target_dir / "mod.txt", mod_txt(entrypoint))
        # Write version.txt
        write(target_dir / "version.txt", version_txt(mc, loader))
        included.append(folder)

    if not included:
        print("No targets to build.")
        if failed_only:
            print("  (--failed-only: no failed targets found in last run)")
        return

    # Create zip — run from inside BUNDLE_DIR so top-level entries are mod folders
    if ZIP_PATH.exists():
        ZIP_PATH.unlink()
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as zf:
        for folder_name in included:
            folder_path = BUNDLE_DIR / folder_name
            for file_path in sorted(folder_path.rglob("*")):
                if file_path.is_file():
                    arcname = folder_name + "/" + file_path.relative_to(folder_path).as_posix()
                    zf.write(file_path, arcname)

    # Verify zip structure
    with zipfile.ZipFile(ZIP_PATH) as zf:
        tops = {n.split("/")[0] for n in zf.namelist()}
        assert "incoming" not in tops, f"Bad zip: 'incoming' at top level! tops={tops}"

    print(f"Generated {len(included)} targets -> {ZIP_PATH}")
    print(f"Skipped {len(skipped)} targets")
    for f, reason in skipped[:5]:
        print(f"  skip: {f} ({reason})")
    if len(skipped) > 5:
        print(f"  ... and {len(skipped)-5} more")
    print()
    print("Top-level zip entries:", sorted(tops)[:5], "...")
    print()
    print("Next steps:")
    print(f"  git add incoming/heartsystem-all-versions.zip incoming/heartsystem-all-versions/")
    print(f"  git commit -m 'Add Heart System all-versions bundle'")
    print(f"  git push")
    print(f"  python3 scripts/run_build.py incoming/heartsystem-all-versions.zip --modrinth https://modrinth.com/mod/lifesteal-parrot-mod")


def main():
    parser = argparse.ArgumentParser(description="Generate Heart System all-versions bundle")
    parser.add_argument("--failed-only", action="store_true",
                        help="Only include targets that failed in the last run")
    args = parser.parse_args()
    generate(failed_only=args.failed_only)


if __name__ == "__main__":
    main()
