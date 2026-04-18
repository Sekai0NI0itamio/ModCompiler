#!/usr/bin/env python3
"""
Generates the Set Home Anywhere bundle — ONLY the ghost shell versions.
Source of truth: 1.12.2 Forge 1.0.1
Run: python3 scripts/generate_sethome_bundle.py [--failed-only]
"""
import argparse, json, shutil, subprocess, zipfile
from pathlib import Path

ROOT   = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "incoming" / "set-home-anywhere-all-versions"

MOD_ID      = "sethome"
MOD_NAME    = "Set Home"
MOD_VERSION = "1.0.1"
GROUP       = "net.itamio.sethome"
DESCRIPTION = "Adds /sethome, /home, and /delhome commands to manage personal homes."
AUTHORS     = "Itamio"
LICENSE     = "MIT"
HOMEPAGE    = "https://modrinth.com/mod/set-home-anywhere"
ENTRYPOINT  = f"{GROUP}.SetHomeMod"
PKG         = GROUP.replace('.', '/')
JAVA_MAIN   = f"src/main/java/{PKG}/SetHomeMod.java"

def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.lstrip("\n"), encoding="utf-8")

def mod_txt() -> str:
    return (f"mod_id={MOD_ID}\nname={MOD_NAME}\nmod_version={MOD_VERSION}\n"
            f"group={GROUP}\nentrypoint_class={ENTRYPOINT}\n"
            f"description={DESCRIPTION}\nauthors={AUTHORS}\nlicense={LICENSE}\n"
            f"homepage={HOMEPAGE}\n")

def version_txt(mc: str, loader: str) -> str:
    return f"minecraft_version={mc}\nloader={loader}\n"

# ============================================================
# SHARED NBT SAVE/LOAD LOGIC (used in all versions)
# ============================================================
_NBT_SAVE_LOAD_1122 = """\
        @Override
        public void readFromNBT(NBTTagCompound tag) {
            data.clear();
            NBTTagList players = tag.getTagList("players", 10);
            for (int i = 0; i < players.tagCount(); i++) {
                NBTTagCompound pc = players.getCompoundTagAt(i);
                Map<String, double[]> homes = new HashMap<>();
                NBTTagList hl = pc.getTagList("homes", 10);
                for (int j = 0; j < hl.tagCount(); j++) {
                    NBTTagCompound hc = hl.getCompoundTagAt(j);
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"), hc.getDouble("y"), hc.getDouble("z"),
                        hc.getFloat("yaw"), hc.getFloat("pitch")
                    });
                }
                data.put(pc.getString("uuid"), homes);
            }
        }
        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound tag) {
            NBTTagList players = new NBTTagList();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                NBTTagCompound pc = new NBTTagCompound();
                pc.setString("uuid", pe.getKey());
                NBTTagList hl = new NBTTagList();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    NBTTagCompound hc = new NBTTagCompound();
                    hc.setString("name", he.getKey());
                    double[] v = he.getValue();
                    hc.setDouble("x",v[0]); hc.setDouble("y",v[1]); hc.setDouble("z",v[2]);
                    hc.setFloat("yaw",(float)v[3]); hc.setFloat("pitch",(float)v[4]);
                    hl.appendTag(hc);
                }
                pc.setTag("homes", hl);
                players.appendTag(pc);
            }
            tag.setTag("players", players);
            return tag;
        }
"""

_NBT_SAVE_LOAD_MODERN = """\
        public static HomeData load(CompoundTag tag) {
            HomeData d = new HomeData();
            ListTag players = tag.getList("players", 10);
            for (int i = 0; i < players.size(); i++) {
                CompoundTag pc = players.getCompound(i);
                Map<String, double[]> homes = new HashMap<>();
                ListTag hl = pc.getList("homes", 10);
                for (int j = 0; j < hl.size(); j++) {
                    CompoundTag hc = hl.getCompound(j);
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"), hc.getDouble("y"), hc.getDouble("z"),
                        hc.getFloat("yaw"), hc.getFloat("pitch")
                    });
                }
                d.data.put(pc.getString("uuid"), homes);
            }
            return d;
        }
        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag players = new ListTag();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                CompoundTag pc = new CompoundTag();
                pc.putString("uuid", pe.getKey());
                ListTag hl = new ListTag();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    CompoundTag hc = new CompoundTag();
                    hc.putString("name", he.getKey());
                    double[] v = he.getValue();
                    hc.putDouble("x",v[0]); hc.putDouble("y",v[1]); hc.putDouble("z",v[2]);
                    hc.putFloat("yaw",(float)v[3]); hc.putFloat("pitch",(float)v[4]);
                    hl.add(hc);
                }
                pc.put("homes", hl);
                players.add(pc);
            }
            tag.put("players", players);
            return tag;
        }
"""

# 1.21+ SavedData.save() takes HolderLookup.Provider in newer versions
_NBT_SAVE_LOAD_121 = """\
        public static HomeData load(CompoundTag tag) {
            HomeData d = new HomeData();
            ListTag players = tag.getList("players", 10);
            for (int i = 0; i < players.size(); i++) {
                CompoundTag pc = players.getCompound(i);
                Map<String, double[]> homes = new HashMap<>();
                ListTag hl = pc.getList("homes", 10);
                for (int j = 0; j < hl.size(); j++) {
                    CompoundTag hc = hl.getCompound(j);
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"), hc.getDouble("y"), hc.getDouble("z"),
                        hc.getFloat("yaw"), hc.getFloat("pitch")
                    });
                }
                d.data.put(pc.getString("uuid"), homes);
            }
            return d;
        }
        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            ListTag players = new ListTag();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                CompoundTag pc = new CompoundTag();
                pc.putString("uuid", pe.getKey());
                ListTag hl = new ListTag();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    CompoundTag hc = new CompoundTag();
                    hc.putString("name", he.getKey());
                    double[] v = he.getValue();
                    hc.putDouble("x",v[0]); hc.putDouble("y",v[1]); hc.putDouble("z",v[2]);
                    hc.putFloat("yaw",(float)v[3]); hc.putFloat("pitch",(float)v[4]);
                    hl.add(hc);
                }
                pc.put("homes", hl);
                players.add(pc);
            }
            tag.put("players", players);
            return tag;
        }
"""

# ============================================================
# 1.8.9 FORGE
# IChatComponent/ChatComponentText (not TextComponentString)
# MapStorage.loadData / setData (not getOrLoadData)
# CommandBase: getName(), getUsage(), processCommand()
# addChatMessage() on player
# ============================================================
SRC_189 = """\
package net.itamio.sethome;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import java.util.*;
import java.util.Collection;

@Mod(modid=SetHomeMod.MODID,name="Set Home",version="1.0.1",acceptedMinecraftVersions="[1.8.9]")
public class SetHomeMod {
    public static final String MODID = "sethome";
    private static int maxHomes = -1;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        Configuration cfg = new Configuration(e.getSuggestedConfigurationFile());
        cfg.load();
        maxHomes = cfg.getInt("maxHomes","general",-1,-1,Integer.MAX_VALUE,
                "Maximum homes per player. Use -1 for unlimited.");
        if (cfg.hasChanged()) cfg.save();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new SetHomeCmd());
        e.registerServerCommand(new HomeCmd());
        e.registerServerCommand(new DelHomeCmd());
    }

    public static class HomeData extends WorldSavedData {
        private static final String NAME = "sethome_data";
        private final Map<String, Map<String, double[]>> data = new HashMap<String, Map<String, double[]>>();
        public HomeData() { super(NAME); }
        public HomeData(String n) { super(n); }

        public static HomeData get(MinecraftServer srv) {
            MapStorage ms = srv.getEntityWorld().getPerWorldStorage();
            HomeData d = (HomeData) ms.loadData(HomeData.class, NAME);
            if (d == null) { d = new HomeData(); ms.setData(NAME, d); }
            return d;
        }
        private Map<String, double[]> player(String uuid) {
            Map<String, double[]> m = data.get(uuid);
            if (m == null) { m = new HashMap<String, double[]>(); data.put(uuid, m); }
            return m;
        }
        public void setHome(String uuid, String name, double x, double y, double z, float yaw, float pitch) {
            player(uuid).put(name, new double[]{x,y,z,yaw,pitch}); markDirty();
        }
        public double[] getHome(String uuid, String name) { return player(uuid).get(name); }
        public boolean hasHome(String uuid, String name) { return player(uuid).containsKey(name); }
        public boolean removeHome(String uuid, String name) {
            boolean r = player(uuid).remove(name) != null; if (r) markDirty(); return r;
        }
        public Set<String> getHomes(String uuid) { return player(uuid).keySet(); }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            data.clear();
            NBTTagList players = tag.getTagList("players", 10);
            for (int i = 0; i < players.tagCount(); i++) {
                NBTTagCompound pc = players.getCompoundTagAt(i);
                Map<String, double[]> homes = new HashMap<String, double[]>();
                NBTTagList hl = pc.getTagList("homes", 10);
                for (int j = 0; j < hl.tagCount(); j++) {
                    NBTTagCompound hc = hl.getCompoundTagAt(j);
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"),hc.getDouble("y"),hc.getDouble("z"),
                        hc.getFloat("yaw"),hc.getFloat("pitch")});
                }
                data.put(pc.getString("uuid"), homes);
            }
        }
        @Override
        public void writeToNBT(NBTTagCompound tag) {
            NBTTagList players = new NBTTagList();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                NBTTagCompound pc = new NBTTagCompound();
                pc.setString("uuid", pe.getKey());
                NBTTagList hl = new NBTTagList();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    NBTTagCompound hc = new NBTTagCompound();
                    hc.setString("name", he.getKey());
                    double[] v = he.getValue();
                    hc.setDouble("x",v[0]); hc.setDouble("y",v[1]); hc.setDouble("z",v[2]);
                    hc.setFloat("yaw",(float)v[3]); hc.setFloat("pitch",(float)v[4]);
                    hl.appendTag(hc);
                }
                pc.setTag("homes", hl); players.appendTag(pc);
            }
            tag.setTag("players", players);
        }
    }

    static class SetHomeCmd extends CommandBase {
        public String getCommandName() { return "sethome"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/sethome <name>"; }
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /sethome <name>");
            EntityPlayerMP p = (EntityPlayerMP) sender;
            String uuid = p.getUniqueID().toString();
            HomeData d = HomeData.get(MinecraftServer.getServer());
            if (maxHomes > 0 && !d.hasHome(uuid,args[0]) && d.getHomes(uuid).size() >= maxHomes)
                throw new CommandException("You have reached the maximum number of homes ("+maxHomes+").");
            d.setHome(uuid, args[0], p.posX, p.posY, p.posZ, p.rotationYaw, p.rotationPitch);
            p.addChatMessage(new ChatComponentText("Home '"+args[0]+"' set."));
        }
    }
    static class HomeCmd extends CommandBase {
        public String getCommandName() { return "home"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/home <name> or /home list"; }
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /home <name> or /home list");
            EntityPlayerMP p = (EntityPlayerMP) sender;
            String uuid = p.getUniqueID().toString();
            HomeData d = HomeData.get(MinecraftServer.getServer());
            if ("list".equalsIgnoreCase(args[0])) {
                Set<String> homes = d.getHomes(uuid);
                if (homes.isEmpty()) { p.addChatMessage(new ChatComponentText("You have no homes set.")); return; }
                p.addChatMessage(new ChatComponentText("Your homes: "+joinSet(homes)));
                return;
            }
            double[] h = d.getHome(uuid, args[0]);
            if (h == null) throw new CommandException("Home '"+args[0]+"' not found.");
            p.setPositionAndUpdate(h[0], h[1], h[2]);
            p.addChatMessage(new ChatComponentText("Teleported to home '"+args[0]+"'."));
        }
    }
    static class DelHomeCmd extends CommandBase {
        public String getCommandName() { return "delhome"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/delhome <name>"; }
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /delhome <name>");
            EntityPlayerMP p = (EntityPlayerMP) sender;
            if (!HomeData.get(MinecraftServer.getServer()).removeHome(p.getUniqueID().toString(), args[0]))
                throw new CommandException("Home '"+args[0]+"' not found.");
            p.addChatMessage(new ChatComponentText("Home '"+args[0]+"' deleted."));
        }
    }
    private static String joinSet(Collection<String> s) {
        StringBuilder sb = new StringBuilder();
        for (String v : s) { if (sb.length()>0) sb.append(", "); sb.append(v); }
        return sb.toString();
    }
}
"""

# ============================================================
# 1.12.2 FORGE — source of truth
# CommandBase: getName(), getUsage(), execute()
# TextComponentString, getWorld(0).getPerWorldStorage()
# getOrLoadData / setData
# ============================================================
SRC_1122 = """\
package net.itamio.sethome;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import java.util.*;

@Mod(modid=SetHomeMod.MODID,name="Set Home",version="1.0.1",acceptedMinecraftVersions="[1.12,1.12.2]")
public class SetHomeMod {
    public static final String MODID = "sethome";
    private static int maxHomes = -1;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        Configuration cfg = new Configuration(e.getSuggestedConfigurationFile());
        cfg.load();
        maxHomes = cfg.getInt("maxHomes","general",-1,-1,Integer.MAX_VALUE,
                "Maximum homes per player. Use -1 for unlimited.");
        if (cfg.hasChanged()) cfg.save();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new SetHomeCmd());
        e.registerServerCommand(new HomeCmd());
        e.registerServerCommand(new DelHomeCmd());
    }

    public static class HomeData extends WorldSavedData {
        private static final String NAME = "sethome_data";
        private final Map<String, Map<String, double[]>> data = new HashMap<>();
        public HomeData() { super(NAME); }
        public HomeData(String n) { super(n); }

        public static HomeData get(MinecraftServer srv) {
            MapStorage ms = srv.getWorld(0).getPerWorldStorage();
            HomeData d = (HomeData) ms.getOrLoadData(HomeData.class, NAME);
            if (d == null) { d = new HomeData(); ms.setData(NAME, d); }
            return d;
        }
        private Map<String, double[]> player(String uuid) {
            return data.computeIfAbsent(uuid, k -> new HashMap<>());
        }
        public void setHome(String uuid, String name, double x, double y, double z, float yaw, float pitch) {
            player(uuid).put(name, new double[]{x,y,z,yaw,pitch}); markDirty();
        }
        public double[] getHome(String uuid, String name) { return player(uuid).get(name); }
        public boolean hasHome(String uuid, String name) { return player(uuid).containsKey(name); }
        public boolean removeHome(String uuid, String name) {
            boolean r = player(uuid).remove(name) != null; if (r) markDirty(); return r;
        }
        public Set<String> getHomes(String uuid) { return player(uuid).keySet(); }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            data.clear();
            NBTTagList players = tag.getTagList("players", 10);
            for (int i = 0; i < players.tagCount(); i++) {
                NBTTagCompound pc = players.getCompoundTagAt(i);
                Map<String, double[]> homes = new HashMap<>();
                NBTTagList hl = pc.getTagList("homes", 10);
                for (int j = 0; j < hl.tagCount(); j++) {
                    NBTTagCompound hc = hl.getCompoundTagAt(j);
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"),hc.getDouble("y"),hc.getDouble("z"),
                        hc.getFloat("yaw"),hc.getFloat("pitch")});
                }
                data.put(pc.getString("uuid"), homes);
            }
        }
        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound tag) {
            NBTTagList players = new NBTTagList();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                NBTTagCompound pc = new NBTTagCompound();
                pc.setString("uuid", pe.getKey());
                NBTTagList hl = new NBTTagList();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    NBTTagCompound hc = new NBTTagCompound();
                    hc.setString("name", he.getKey());
                    double[] v = he.getValue();
                    hc.setDouble("x",v[0]); hc.setDouble("y",v[1]); hc.setDouble("z",v[2]);
                    hc.setFloat("yaw",(float)v[3]); hc.setFloat("pitch",(float)v[4]);
                    hl.appendTag(hc);
                }
                pc.setTag("homes", hl); players.appendTag(pc);
            }
            tag.setTag("players", players); return tag;
        }
    }

    static class SetHomeCmd extends CommandBase {
        public String getName() { return "sethome"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getUsage(ICommandSender s) { return "/sethome <name>"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /sethome <name>");
            EntityPlayerMP p = (EntityPlayerMP) sender;
            String uuid = p.getUniqueID().toString();
            HomeData d = HomeData.get(srv);
            if (maxHomes > 0 && !d.hasHome(uuid,args[0]) && d.getHomes(uuid).size() >= maxHomes)
                throw new CommandException("You have reached the maximum number of homes ("+maxHomes+").");
            d.setHome(uuid, args[0], p.posX, p.posY, p.posZ, p.rotationYaw, p.rotationPitch);
            sender.sendMessage(new TextComponentString("Home '"+args[0]+"' set."));
        }
    }
    static class HomeCmd extends CommandBase {
        public String getName() { return "home"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getUsage(ICommandSender s) { return "/home <name> or /home list"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /home <name> or /home list");
            EntityPlayerMP p = (EntityPlayerMP) sender;
            String uuid = p.getUniqueID().toString();
            HomeData d = HomeData.get(srv);
            if ("list".equalsIgnoreCase(args[0])) {
                Set<String> homes = d.getHomes(uuid);
                if (homes.isEmpty()) { sender.sendMessage(new TextComponentString("You have no homes set.")); return; }
                sender.sendMessage(new TextComponentString("Your homes: "+String.join(", ", new ArrayList<>(homes))));
                return;
            }
            double[] h = d.getHome(uuid, args[0]);
            if (h == null) throw new CommandException("Home '"+args[0]+"' not found.");
            p.setPositionAndUpdate(h[0], h[1], h[2]);
            sender.sendMessage(new TextComponentString("Teleported to home '"+args[0]+"'."));
        }
    }
    static class DelHomeCmd extends CommandBase {
        public String getName() { return "delhome"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getUsage(ICommandSender s) { return "/delhome <name>"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /delhome <name>");
            EntityPlayerMP p = (EntityPlayerMP) sender;
            if (!HomeData.get(srv).removeHome(p.getUniqueID().toString(), args[0]))
                throw new CommandException("Home '"+args[0]+"' not found.");
            sender.sendMessage(new TextComponentString("Home '"+args[0]+"' deleted."));
        }
    }
}
"""

# ============================================================
# BRIGADIER BASE (1.16.5 through 1.20.x Forge)
# Shared template — parameterized by text class and rotation API
# ============================================================
def _brigadier_forge(text_import, text_fn, rot_api, data_get, data_save_method,
                     event_import, event_class, config_import, config_class,
                     mod_loading_ctx, event_bus_register):
    """Build a Brigadier-based Forge source."""
    return f"""\
package net.itamio.sethome;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
{text_import}
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.common.MinecraftForge;
{event_import}
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
{config_import}
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import java.util.*;

@Mod(SetHomeMod.MODID)
public class SetHomeMod {{
    public static final String MODID = "sethome";
    static {config_class}.IntValue MAX_HOMES;
    static {config_class} SPEC;
    static {{
        {config_class}.Builder b = new {config_class}.Builder();
        b.push("general");
        MAX_HOMES = b.comment("Maximum homes per player. Use -1 for unlimited.")
                     .defineInRange("maxHomes", -1, -1, Integer.MAX_VALUE);
        b.pop();
        SPEC = b.build();
    }}

    public SetHomeMod() {{
        {mod_loading_ctx}.registerConfig(ModConfig.Type.COMMON, SPEC);
        {event_bus_register}
    }}

    @SubscribeEvent
    public void onRegisterCommands({event_class} e) {{
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("sethome")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> setHome(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
        d.register(Commands.literal("home")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> home(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
        d.register(Commands.literal("delhome")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> delHome(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
    }}

    private static int setHome(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {{
        ServerPlayer p = src.getPlayerOrException();
        String uuid = p.getUUID().toString();
        HomeData d = HomeData.get(src.getServer());
        int max = MAX_HOMES.get();
        if (max > 0 && !d.hasHome(uuid, name) && d.getHomes(uuid).size() >= max) {{
            src.sendSuccess({text_fn}("You have reached the maximum number of homes (" + max + ")."), false);
            return 0;
        }}
        d.setHome(uuid, name, p.getX(), p.getY(), p.getZ(), {rot_api});
        src.sendSuccess({text_fn}("Home '" + name + "' set."), false);
        return 1;
    }}

    private static int home(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {{
        if ("list".equalsIgnoreCase(name)) {{
            ServerPlayer p = src.getPlayerOrException();
            Set<String> homes = HomeData.get(src.getServer()).getHomes(p.getUUID().toString());
            if (homes.isEmpty()) {{ src.sendSuccess({text_fn}("You have no homes set."), false); return 1; }}
            src.sendSuccess({text_fn}("Your homes: " + String.join(", ", new ArrayList<>(homes))), false);
            return 1;
        }}
        ServerPlayer p = src.getPlayerOrException();
        double[] h = HomeData.get(src.getServer()).getHome(p.getUUID().toString(), name);
        if (h == null) {{ src.sendSuccess({text_fn}("Home '" + name + "' not found."), false); return 0; }}
        p.teleportTo(h[0], h[1], h[2]);
        src.sendSuccess({text_fn}("Teleported to home '" + name + "'."), false);
        return 1;
    }}

    private static int delHome(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {{
        ServerPlayer p = src.getPlayerOrException();
        if (!HomeData.get(src.getServer()).removeHome(p.getUUID().toString(), name)) {{
            src.sendSuccess({text_fn}("Home '" + name + "' not found."), false); return 0;
        }}
        src.sendSuccess({text_fn}("Home '" + name + "' deleted."), false);
        return 1;
    }}

    public static class HomeData extends SavedData {{
        private static final String NAME = "sethome_data";
        private final Map<String, Map<String, double[]>> data = new HashMap<>();
        public HomeData() {{}}

        public static HomeData get(MinecraftServer srv) {{
            DimensionDataStorage storage = srv.overworld().getDataStorage();
            return {data_get};
        }}

        {data_save_method}

        private Map<String, double[]> player(String uuid) {{
            return data.computeIfAbsent(uuid, k -> new HashMap<>());
        }}
        public void setHome(String uuid, String name, double x, double y, double z, float yaw, float pitch) {{
            player(uuid).put(name, new double[]{{x,y,z,yaw,pitch}}); setDirty();
        }}
        public double[] getHome(String uuid, String name) {{ return player(uuid).get(name); }}
        public boolean hasHome(String uuid, String name) {{ return player(uuid).containsKey(name); }}
        public boolean removeHome(String uuid, String name) {{
            boolean r = player(uuid).remove(name) != null; if (r) setDirty(); return r;
        }}
        public Set<String> getHomes(String uuid) {{ return player(uuid).keySet(); }}
    }}
}}
"""

# Shared save/load block for 1.16.5-1.20.x (CompoundTag.save(CompoundTag))
_SAVE_LOAD_MODERN = """\
        public static HomeData load(CompoundTag tag) {
            HomeData d = new HomeData();
            ListTag players = tag.getList("players", 10);
            for (int i = 0; i < players.size(); i++) {
                CompoundTag pc = players.getCompound(i);
                Map<String, double[]> homes = new HashMap<>();
                ListTag hl = pc.getList("homes", 10);
                for (int j = 0; j < hl.size(); j++) {
                    CompoundTag hc = hl.getCompound(j);
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"),hc.getDouble("y"),hc.getDouble("z"),
                        hc.getFloat("yaw"),hc.getFloat("pitch")});
                }
                d.data.put(pc.getString("uuid"), homes);
            }
            return d;
        }
        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag players = new ListTag();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                CompoundTag pc = new CompoundTag();
                pc.putString("uuid", pe.getKey());
                ListTag hl = new ListTag();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    CompoundTag hc = new CompoundTag();
                    hc.putString("name", he.getKey());
                    double[] v = he.getValue();
                    hc.putDouble("x",v[0]); hc.putDouble("y",v[1]); hc.putDouble("z",v[2]);
                    hc.putFloat("yaw",(float)v[3]); hc.putFloat("pitch",(float)v[4]);
                    hl.add(hc);
                }
                pc.put("homes", hl); players.add(pc);
            }
            tag.put("players", players); return tag;
        }"""

# 1.21+ save() takes HolderLookup.Provider
_SAVE_LOAD_121 = _SAVE_LOAD_MODERN.replace(
    "public CompoundTag save(CompoundTag tag) {",
    "public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {"
)

# data_get for 1.16.5-1.20.x
_DATA_GET_MODERN = "storage.computeIfAbsent(HomeData::load, HomeData::new, NAME)"

# data_get for 1.21+ (same API, but save() signature changed)
_DATA_GET_121 = _DATA_GET_MODERN

# ---- Build all Forge variants ----

# 1.16.5 — StringTextComponent, yRot/xRot private → getYRot()/getXRot()
# DimensionType.OVERWORLD for overworld level
SRC_1165 = _brigadier_forge(
    text_import="import net.minecraft.util.text.StringTextComponent;",
    text_fn="new StringTextComponent",
    rot_api="p.yRot, p.xRot",
    data_get=_DATA_GET_MODERN,
    data_save_method=_SAVE_LOAD_MODERN,
    event_import="import net.minecraftforge.event.RegisterCommandsEvent;",
    event_class="RegisterCommandsEvent",
    config_import="import net.minecraftforge.common.ForgeConfigSpec;",
    config_class="ForgeConfigSpec",
    mod_loading_ctx="net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC); MinecraftForge.EVENT_BUS.register(this); return; } public SetHomeMod() { net.minecraftforge.fml.ModLoadingContext.get()",
    event_bus_register="MinecraftForge.EVENT_BUS.register(this);"
)
# That approach is getting messy — build it directly
SRC_1165 = """\
package net.itamio.sethome;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import java.util.*;

@Mod(SetHomeMod.MODID)
public class SetHomeMod {
    public static final String MODID = "sethome";
    static ForgeConfigSpec.IntValue MAX_HOMES;
    static ForgeConfigSpec SPEC;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("general");
        MAX_HOMES = b.comment("Maximum homes per player. Use -1 for unlimited.")
                     .defineInRange("maxHomes", -1, -1, Integer.MAX_VALUE);
        b.pop(); SPEC = b.build();
    }
    public SetHomeMod() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSource> d = e.getDispatcher();
        d.register(Commands.literal("sethome")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> setHome(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
        d.register(Commands.literal("home")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> home(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
        d.register(Commands.literal("delhome")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> delHome(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
    }

    private static int setHome(CommandSource src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(src.getEntity() instanceof ServerPlayerEntity)) { src.sendSuccess(new StringTextComponent("Players only."), false); return 0; }
        ServerPlayerEntity p = (ServerPlayerEntity) src.getEntity();
        String uuid = p.getUUID().toString();
        HomeData d = HomeData.get(src.getServer());
        int max = MAX_HOMES.get();
        if (max > 0 && !d.hasHome(uuid, name) && d.getHomes(uuid).size() >= max) {
            src.sendSuccess(new StringTextComponent("You have reached the maximum number of homes (" + max + ")."), false); return 0;
        }
        d.setHome(uuid, name, p.getX(), p.getY(), p.getZ(), p.yRot, p.xRot);
        src.sendSuccess(new StringTextComponent("Home '" + name + "' set."), false); return 1;
    }
    private static int home(CommandSource src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if ("list".equalsIgnoreCase(name)) {
            if (!(src.getEntity() instanceof ServerPlayerEntity)) { src.sendSuccess(new StringTextComponent("Players only."), false); return 0; }
            ServerPlayerEntity p = (ServerPlayerEntity) src.getEntity();
            Set<String> homes = HomeData.get(src.getServer()).getHomes(p.getUUID().toString());
            if (homes.isEmpty()) { src.sendSuccess(new StringTextComponent("You have no homes set."), false); return 1; }
            src.sendSuccess(new StringTextComponent("Your homes: " + String.join(", ", new ArrayList<>(homes))), false); return 1;
        }
        if (!(src.getEntity() instanceof ServerPlayerEntity)) { src.sendSuccess(new StringTextComponent("Players only."), false); return 0; }
        ServerPlayerEntity p = (ServerPlayerEntity) src.getEntity();
        double[] h = HomeData.get(src.getServer()).getHome(p.getUUID().toString(), name);
        if (h == null) { src.sendSuccess(new StringTextComponent("Home '" + name + "' not found."), false); return 0; }
        p.teleportTo(h[0], h[1], h[2]);
        src.sendSuccess(new StringTextComponent("Teleported to home '" + name + "'."), false); return 1;
    }
    private static int delHome(CommandSource src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(src.getEntity() instanceof ServerPlayerEntity)) { src.sendSuccess(new StringTextComponent("Players only."), false); return 0; }
        ServerPlayerEntity p = (ServerPlayerEntity) src.getEntity();
        if (!HomeData.get(src.getServer()).removeHome(p.getUUID().toString(), name)) {
            src.sendSuccess(new StringTextComponent("Home '" + name + "' not found."), false); return 0;
        }
        src.sendSuccess(new StringTextComponent("Home '" + name + "' deleted."), false); return 1;
    }

    public static class HomeData extends WorldSavedData {
        private static final String NAME = "sethome_data";
        private final Map<String, Map<String, double[]>> data = new HashMap<>();
        public HomeData() { super(NAME); }
        public HomeData(String n) { super(n); }

        public static HomeData get(MinecraftServer srv) {
            net.minecraft.world.storage.DimensionSavedDataManager mgr = srv.overworld().getDataStorage();
            HomeData d = mgr.get(HomeData::new, NAME);
            if (d == null) { d = new HomeData(); mgr.set(d); }
            return d;
        }
        private Map<String, double[]> player(String uuid) { return data.computeIfAbsent(uuid, k -> new HashMap<>()); }
        public void setHome(String uuid, String name, double x, double y, double z, float yaw, float pitch) {
            player(uuid).put(name, new double[]{x,y,z,yaw,pitch}); setDirty();
        }
        public double[] getHome(String uuid, String name) { return player(uuid).get(name); }
        public boolean hasHome(String uuid, String name) { return player(uuid).containsKey(name); }
        public boolean removeHome(String uuid, String name) {
            boolean r = player(uuid).remove(name) != null; if (r) setDirty(); return r;
        }
        public Set<String> getHomes(String uuid) { return player(uuid).keySet(); }

        @Override
        public void load(CompoundNBT tag) {
            data.clear();
            ListNBT players = tag.getList("players", 10);
            for (int i = 0; i < players.size(); i++) {
                CompoundNBT pc = players.getCompound(i);
                Map<String, double[]> homes = new HashMap<>();
                ListNBT hl = pc.getList("homes", 10);
                for (int j = 0; j < hl.size(); j++) {
                    CompoundNBT hc = hl.getCompound(j);
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"),hc.getDouble("y"),hc.getDouble("z"),
                        hc.getFloat("yaw"),hc.getFloat("pitch")});
                }
                data.put(pc.getString("uuid"), homes);
            }
        }
        @Override
        public CompoundNBT save(CompoundNBT tag) {
            ListNBT players = new ListNBT();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                CompoundNBT pc = new CompoundNBT();
                pc.putString("uuid", pe.getKey());
                ListNBT hl = new ListNBT();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    CompoundNBT hc = new CompoundNBT();
                    hc.putString("name", he.getKey());
                    double[] v = he.getValue();
                    hc.putDouble("x",v[0]); hc.putDouble("y",v[1]); hc.putDouble("z",v[2]);
                    hc.putFloat("yaw",(float)v[3]); hc.putFloat("pitch",(float)v[4]);
                    hl.add(hc);
                }
                pc.put("homes", hl); players.add(pc);
            }
            tag.put("players", players); return tag;
        }
    }
}
"""

# ============================================================
# 1.17.1 - 1.18.x FORGE
# TextComponent, getYRot()/getXRot(), SavedData, overworld()
# ============================================================
SRC_1171_118 = """\
package net.itamio.sethome;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import java.util.*;

@Mod(SetHomeMod.MODID)
public class SetHomeMod {
    public static final String MODID = "sethome";
    static ForgeConfigSpec.IntValue MAX_HOMES;
    static ForgeConfigSpec SPEC;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("general");
        MAX_HOMES = b.comment("Maximum homes per player. Use -1 for unlimited.")
                     .defineInRange("maxHomes", -1, -1, Integer.MAX_VALUE);
        b.pop(); SPEC = b.build();
    }
    public SetHomeMod() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("sethome")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> setHome(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
        d.register(Commands.literal("home")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> home(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
        d.register(Commands.literal("delhome")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> delHome(ctx.getSource(), StringArgumentType.getString(ctx,"name")))));
    }

    private static int setHome(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        String uuid = p.getUUID().toString();
        HomeData d = HomeData.get(src.getServer());
        int max = MAX_HOMES.get();
        if (max > 0 && !d.hasHome(uuid, name) && d.getHomes(uuid).size() >= max) {
            src.sendSuccess(new TextComponent("You have reached the maximum number of homes (" + max + ")."), false); return 0;
        }
        d.setHome(uuid, name, p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        src.sendSuccess(new TextComponent("Home '" + name + "' set."), false); return 1;
    }
    private static int home(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if ("list".equalsIgnoreCase(name)) {
            ServerPlayer p = src.getPlayerOrException();
            Set<String> homes = HomeData.get(src.getServer()).getHomes(p.getUUID().toString());
            if (homes.isEmpty()) { src.sendSuccess(new TextComponent("You have no homes set."), false); return 1; }
            src.sendSuccess(new TextComponent("Your homes: " + String.join(", ", new ArrayList<>(homes))), false); return 1;
        }
        ServerPlayer p = src.getPlayerOrException();
        double[] h = HomeData.get(src.getServer()).getHome(p.getUUID().toString(), name);
        if (h == null) { src.sendSuccess(new TextComponent("Home '" + name + "' not found."), false); return 0; }
        p.teleportTo(h[0], h[1], h[2]);
        src.sendSuccess(new TextComponent("Teleported to home '" + name + "'."), false); return 1;
    }
    private static int delHome(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!HomeData.get(src.getServer()).removeHome(p.getUUID().toString(), name)) {
            src.sendSuccess(new TextComponent("Home '" + name + "' not found."), false); return 0;
        }
        src.sendSuccess(new TextComponent("Home '" + name + "' deleted."), false); return 1;
    }

    public static class HomeData extends SavedData {
        private static final String NAME = "sethome_data";
        private final Map<String, Map<String, double[]>> data = new HashMap<>();
        public HomeData() {}

        public static HomeData get(MinecraftServer srv) {
            DimensionDataStorage storage = srv.overworld().getDataStorage();
            return storage.computeIfAbsent(new SavedData.Factory<HomeData>(HomeData::new, (tag, provider) -> HomeData.load(tag), null), NAME);
        }
        public static HomeData load(CompoundTag tag) {
            HomeData d = new HomeData();
            ListTag players = tag.getList("players", 10);
            for (int i = 0; i < players.size(); i++) {
                CompoundTag pc = players.getCompound(i);
                Map<String, double[]> homes = new HashMap<>();
                ListTag hl = pc.getList("homes", 10);
                for (int j = 0; j < hl.size(); j++) {
                    CompoundTag hc = hl.getCompound(j);
                    homes.put(hc.getString("name"), new double[]{
                        hc.getDouble("x"),hc.getDouble("y"),hc.getDouble("z"),
                        hc.getFloat("yaw"),hc.getFloat("pitch")});
                }
                d.data.put(pc.getString("uuid"), homes);
            }
            return d;
        }
        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag players = new ListTag();
            for (Map.Entry<String, Map<String, double[]>> pe : data.entrySet()) {
                CompoundTag pc = new CompoundTag();
                pc.putString("uuid", pe.getKey());
                ListTag hl = new ListTag();
                for (Map.Entry<String, double[]> he : pe.getValue().entrySet()) {
                    CompoundTag hc = new CompoundTag();
                    hc.putString("name", he.getKey());
                    double[] v = he.getValue();
                    hc.putDouble("x",v[0]); hc.putDouble("y",v[1]); hc.putDouble("z",v[2]);
                    hc.putFloat("yaw",(float)v[3]); hc.putFloat("pitch",(float)v[4]);
                    hl.add(hc);
                }
                pc.put("homes", hl); players.add(pc);
            }
            tag.put("players", players); return tag;
        }
        private Map<String, double[]> player(String uuid) { return data.computeIfAbsent(uuid, k -> new HashMap<>()); }
        public void setHome(String uuid, String name, double x, double y, double z, float yaw, float pitch) {
            player(uuid).put(name, new double[]{x,y,z,yaw,pitch}); setDirty();
        }
        public double[] getHome(String uuid, String name) { return player(uuid).get(name); }
        public boolean hasHome(String uuid, String name) { return player(uuid).containsKey(name); }
        public boolean removeHome(String uuid, String name) {
            boolean r = player(uuid).remove(name) != null; if (r) setDirty(); return r;
        }
        public Set<String> getHomes(String uuid) { return player(uuid).keySet(); }
    }
}
"""

# 1.19.x — Component.literal replaces TextComponent
SRC_119 = SRC_1171_118.replace(
    "import net.minecraft.network.chat.TextComponent;",
    "import net.minecraft.network.chat.Component;"
).replace("new TextComponent(", "Component.literal(")

# 1.19.4 — same as 1.19 (Component.literal already)
SRC_1194 = SRC_119

# 1.20.x Forge — same as 1.19.4 but sendSuccess takes Supplier<Component>
SRC_120_FORGE = SRC_1194.replace(
    "src.sendSuccess(Component.literal(",
    "src.sendSuccess(() -> Component.literal("
).replace(
    "src.sendSuccess(() -> Component.literal(",
    "src.sendSuccess(() -> Component.literal("
).replace(
    "), false); return 0;\n        }\n        d.setHome",
    "), false); return 0;\n        }\n        d.setHome"
)
# Simpler: just replace all sendSuccess patterns
SRC_120_FORGE = SRC_1194.replace(
    "src.sendSuccess(Component.literal(",
    "src.sendSuccess(() -> Component.literal("
)
for old, new in [
    ('src.sendSuccess(() -> Component.literal("You have reached', 'src.sendSuccess(() -> Component.literal("You have reached'),
    ('src.sendSuccess(() -> Component.literal("Home \'" + name + "\' set.")', 'src.sendSuccess(() -> Component.literal("Home \'" + name + "\' set.")'),
    ('src.sendSuccess(() -> Component.literal("You have no homes set.")', 'src.sendSuccess(() -> Component.literal("You have no homes set.")'),
    ('src.sendSuccess(() -> Component.literal("Your homes: "', 'src.sendSuccess(() -> Component.literal("Your homes: "'),
    ('src.sendSuccess(() -> Component.literal("Home \'" + name + "\' not found.")', 'src.sendSuccess(() -> Component.literal("Home \'" + name + "\' not found.")'),
    ('src.sendSuccess(() -> Component.literal("Teleported to home"', 'src.sendSuccess(() -> Component.literal("Teleported to home"'),
    ('src.sendSuccess(() -> Component.literal("Home \'" + name + "\' deleted.")', 'src.sendSuccess(() -> Component.literal("Home \'" + name + "\' deleted.")'),
]:
    SRC_120_FORGE = SRC_120_FORGE.replace(old, new)

# ============================================================
# 1.21+ FORGE — save() takes HolderLookup.Provider
# ============================================================
SRC_121_FORGE = SRC_120_FORGE.replace(
    "public CompoundTag save(CompoundTag tag) {",
    "public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {"
)

# 1.21.5+ Forge — getList(String, int) removed, getCompound(int) returns Optional
SRC_1215_FORGE = (SRC_121_FORGE
    .replace(
        'ListTag players = tag.getList("players", 10);',
        'ListTag players = tag.getList("players").orElse(new ListTag());'
    )
    .replace(
        "CompoundTag pc = players.getCompound(i);",
        "CompoundTag pc = players.getCompound(i).orElse(new CompoundTag());"
    )
    .replace(
        'ListTag hl = pc.getList("homes", 10);',
        'ListTag hl = pc.getList("homes").orElse(new ListTag());'
    )
    .replace(
        "CompoundTag hc = hl.getCompound(j);",
        "CompoundTag hc = hl.getCompound(j).orElse(new CompoundTag());"
    )
)

# 1.21.11 Forge — addListener pattern (no @SubscribeEvent import)
SRC_12111_FORGE = (SRC_1215_FORGE
    .replace("import net.minecraftforge.eventbus.api.SubscribeEvent;\n", "")
    .replace(
        "    public SetHomeMod() {\n        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);\n        MinecraftForge.EVENT_BUS.register(this);\n    }\n\n    @SubscribeEvent\n    public void onRegisterCommands(RegisterCommandsEvent e) {",
        "    public SetHomeMod() {\n        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);\n        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);\n    }\n\n    public void onRegisterCommands(RegisterCommandsEvent e) {"
    )
)

# ============================================================
# NEOFORGE variants
# ModConfigSpec replaces ForgeConfigSpec
# NeoForge.EVENT_BUS replaces MinecraftForge.EVENT_BUS
# ModLoadingContext replaced by IEventBus injection in constructor
# ============================================================
def to_neoforge_sethome(src: str) -> str:
    return (src
        .replace("import net.minecraftforge.common.MinecraftForge;",
                 "import net.neoforged.neoforge.common.NeoForge;")
        .replace("import net.minecraftforge.event.RegisterCommandsEvent;",
                 "import net.neoforged.neoforge.event.RegisterCommandsEvent;")
        .replace("import net.minecraftforge.eventbus.api.SubscribeEvent;",
                 "import net.neoforged.bus.api.SubscribeEvent;")
        .replace("import net.minecraftforge.fml.common.Mod;",
                 "import net.neoforged.fml.common.Mod;")
        .replace("import net.minecraftforge.fml.config.ModConfig;",
                 "import net.neoforged.fml.config.ModConfig;")
        .replace("import net.minecraftforge.common.ForgeConfigSpec;",
                 "import net.neoforged.neoforge.common.ModConfigSpec;")
        .replace("ForgeConfigSpec.IntValue", "ModConfigSpec.IntValue")
        .replace("ForgeConfigSpec.Builder", "ModConfigSpec.Builder")
        .replace("ForgeConfigSpec SPEC;", "ModConfigSpec SPEC;")
        .replace("MinecraftForge.EVENT_BUS.register(this);",
                 "NeoForge.EVENT_BUS.register(this);")
        .replace("net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);",
                 "// config uses defaults")
    )

SRC_120_NEOFORGE = to_neoforge_sethome(SRC_120_FORGE)
# NeoForge 1.20.5+ — save() takes HolderLookup.Provider
SRC_1205_NEOFORGE = to_neoforge_sethome(SRC_120_FORGE.replace(
    "public CompoundTag save(CompoundTag tag) {",
    "public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {"
))
SRC_121_NEOFORGE = to_neoforge_sethome(SRC_121_FORGE)  # 1.21.1-1.21.4
SRC_1215_NEOFORGE = to_neoforge_sethome(SRC_1215_FORGE)  # 1.21.5+

# ============================================================
# TARGETS — only ghost shell versions
# ============================================================
SRC_1215_NEOFORGE = to_neoforge_sethome(SRC_1215_FORGE)  # 1.21.5+
targets = [
    # (folder, src, loader, mc_ver)
    ("SetHome189Forge",       SRC_189,         "forge",    "1.8.9"),
    ("SetHome1122Forge",      SRC_1122,        "forge",    "1.12.2"),
    ("SetHome1165Forge",      SRC_1165,        "forge",    "1.16.5"),
    ("SetHome1171Forge",      SRC_1171_118,    "forge",    "1.17.1"),
    ("SetHome118Forge",       SRC_1171_118,    "forge",    "1.18"),
    ("SetHome1181Forge",      SRC_1171_118,    "forge",    "1.18.1"),
    ("SetHome1182Forge",      SRC_1171_118,    "forge",    "1.18.2"),
    ("SetHome119Forge",       SRC_119,         "forge",    "1.19"),
    ("SetHome1191Forge",      SRC_119,         "forge",    "1.19.1"),
    ("SetHome1192Forge",      SRC_119,         "forge",    "1.19.2"),
    ("SetHome1193Forge",      SRC_119,         "forge",    "1.19.3"),
    ("SetHome1194Forge",      SRC_1194,        "forge",    "1.19.4"),
    ("SetHome121Forge",       SRC_121_FORGE,   "forge",    "1.21"),
    ("SetHome1211Forge",      SRC_121_FORGE,   "forge",    "1.21.1"),
    ("SetHome1213Forge",      SRC_121_FORGE,   "forge",    "1.21.3"),
    ("SetHome1214Forge",      SRC_121_FORGE,   "forge",    "1.21.4"),
    ("SetHome1215Forge",      SRC_1215_FORGE,   "forge",    "1.21.5"),
    ("SetHome1216Forge",      SRC_1215_FORGE,   "forge",    "1.21.6"),
    ("SetHome1217Forge",      SRC_1215_FORGE,   "forge",    "1.21.7"),
    ("SetHome1218Forge",      SRC_1215_FORGE,   "forge",    "1.21.8"),
    ("SetHome1219Forge",      SRC_1215_FORGE,   "forge",    "1.21.9"),
    ("SetHome12110Forge",     SRC_1215_FORGE,  "forge",    "1.21.10"),
    ("SetHome12111Forge",     SRC_12111_FORGE, "forge",    "1.21.11"),
    ("SetHome1202NeoForge",   SRC_120_NEOFORGE,"neoforge", "1.20.2"),
    ("SetHome1204NeoForge",   SRC_120_NEOFORGE,"neoforge", "1.20.4"),
    ("SetHome1205NeoForge",   SRC_1205_NEOFORGE,"neoforge", "1.20.5"),
    ("SetHome1206NeoForge",   SRC_1205_NEOFORGE,"neoforge", "1.20.6"),
    ("SetHome121NeoForge",    SRC_121_NEOFORGE,"neoforge", "1.21"),
    ("SetHome1211NeoForge",   SRC_121_NEOFORGE,"neoforge", "1.21.1"),
    ("SetHome1212NeoForge",   SRC_121_NEOFORGE,"neoforge", "1.21.2"),
    ("SetHome1213NeoForge",   SRC_121_NEOFORGE,"neoforge", "1.21.3"),
    ("SetHome1214NeoForge",   SRC_121_NEOFORGE,"neoforge", "1.21.4"),
    ("SetHome1215NeoForge",   SRC_1215_NEOFORGE,"neoforge", "1.21.5"),
    ("SetHome1216NeoForge",   SRC_1215_NEOFORGE,"neoforge", "1.21.6"),
    ("SetHome1217NeoForge",   SRC_1215_NEOFORGE,"neoforge", "1.21.7"),
    ("SetHome1218NeoForge",   SRC_1215_NEOFORGE,"neoforge", "1.21.8"),
    ("SetHome1219NeoForge",   SRC_1215_NEOFORGE,"neoforge", "1.21.9"),
    ("SetHome12110NeoForge",  SRC_1215_NEOFORGE,"neoforge", "1.21.10"),
    ("SetHome12111NeoForge",  SRC_121_NEOFORGE,"neoforge", "1.21.11"),
]

# ============================================================
# FAILED-ONLY MODE
# ============================================================
import re as _re
_ap = argparse.ArgumentParser()
_ap.add_argument("--failed-only", action="store_true")
_ap.add_argument("--run-dir", default="")
_parsed = _ap.parse_args()

active_targets = targets
if _parsed.failed_only:
    runs_root = ROOT / "ModCompileRuns"
    run_dir = Path(_parsed.run_dir) if _parsed.run_dir else (
        sorted(runs_root.iterdir())[-1] if runs_root.exists() and any(runs_root.iterdir()) else None
    )
    if run_dir is None:
        print("WARNING: no run dir found, using all targets.")
    else:
        art = run_dir / "artifacts" / "all-mod-builds" / "mods"
        if not art.exists():
            print(f"WARNING: no artifact at {art}, using all targets.")
        else:
            failed_slugs = set()
            for mod_dir in art.iterdir():
                if not mod_dir.is_dir(): continue
                rf = mod_dir / "result.json"
                if rf.exists():
                    try:
                        if json.loads(rf.read_text()).get("status") != "success":
                            failed_slugs.add(mod_dir.name)
                    except: failed_slugs.add(mod_dir.name)
                else:
                    failed_slugs.add(mod_dir.name)

            def matches(folder, slug):
                m = _re.search(r'(\d+)(Forge|NeoForge)$', folder, _re.IGNORECASE)
                if not m: return False
                digits, loader = m.group(1), m.group(2).lower()
                if len(digits)==3:   ver=f"1-{digits[1]}-{digits[2]}"
                elif len(digits)==4: ver=f"1-{digits[1]}{digits[2]}-{digits[3]}"
                elif len(digits)==5: ver=f"1-{digits[1]}{digits[2]}-{digits[3]}{digits[4]}"
                elif len(digits)==2: ver=f"1-{digits[1]}"
                else: ver=digits
                return loader in slug and ver in slug

            failed_folders = {t[0] for t in targets if any(matches(t[0],s) for s in failed_slugs)}
            if failed_folders:
                active_targets = [t for t in targets if t[0] in failed_folders]
                print(f"Failed-only: {len(active_targets)} targets (skipping {len(targets)-len(active_targets)} green)")
                for t in active_targets: print(f"  -> {t[0]}")
            else:
                print("No failed targets — all green!")
                active_targets = []

# ============================================================
# GENERATE
# ============================================================
if BUNDLE.exists():
    shutil.rmtree(BUNDLE)

for (folder, src, loader, mc_ver) in active_targets:
    base = BUNDLE / folder
    write(base / "mod.txt", mod_txt())
    write(base / "version.txt", version_txt(mc_ver, loader))
    write(base / JAVA_MAIN, src)

print(f"Generated {len(active_targets)} targets")

zip_path = ROOT / "incoming" / "set-home-anywhere-all-versions.zip"
if active_targets:
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(BUNDLE.rglob("*")):
            if not path.is_file(): continue
            rel = path.relative_to(BUNDLE)
            if len(rel.parts) < 2: continue
            zf.write(path, rel)
    print(f"Zip: {zip_path}")

    r = subprocess.run(
        ["python3", "build_mods.py", "prepare",
         "--zip-path", str(zip_path),
         "--manifest", "version-manifest.json",
         "--output-dir", "/tmp/prepare-sethome"],
        capture_output=True, text=True, cwd=str(ROOT)
    )
    if r.returncode == 0:
        matrix = json.loads(r.stdout)
        print(f"Prepare OK — {len(matrix.get('include',[]))} build targets")
    else:
        print(f"Prepare FAILED:\n{r.stderr[:500]}")
else:
    print("Nothing to build.")
