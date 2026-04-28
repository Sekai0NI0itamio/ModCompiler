#!/usr/bin/env python3
"""
Generates the TPA Teleport bundle for all missing MC versions and loaders.
Source of truth: 1.20.1 Forge (already published on Modrinth).
Run: python3 scripts/generate_tpa_bundle.py [--failed-only]

Already published (skip these):
  1.12-1.12.2  forge
  1.20-1.20.6  forge
  1.21-1.21.1  fabric
  1.21.2-1.21.8 fabric
  1.21.9-1.21.11 fabric
"""
import argparse, json, shutil, zipfile
from pathlib import Path

ROOT   = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / "incoming" / "tpa-teleport-all-versions"

MOD_ID      = "tpateleport"
MOD_NAME    = "Tpa Teleport"
MOD_VERSION = "1.0.0"
GROUP       = "net.itamio.tpateleport"
DESCRIPTION = "Adds /tpa, /tpahere, /tpaccept, /tpadeny, and /tpacancel commands with request timeouts."
AUTHORS     = "Itamio"
LICENSE     = "MIT"
HOMEPAGE    = "https://modrinth.com/mod/tpa-teleport"
ENTRYPOINT  = f"{GROUP}.TpaTeleportMod"
PKG         = GROUP.replace('.', '/')
JAVA_MAIN   = f"src/main/java/{PKG}/TpaTeleportMod.java"

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
# 1.8.9 FORGE
# CommandBase: getCommandName/getCommandUsage/processCommand
# addChatMessage(new ChatComponentText(...))
# ============================================================
SRC_189 = """\
package net.itamio.tpateleport;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import java.util.*;
import java.util.concurrent.*;

@Mod(modid=TpaTeleportMod.MODID, name="Tpa Teleport", version="1.0.0", acceptedMinecraftVersions="[1.8.9]")
public class TpaTeleportMod {
    public static final String MODID = "tpateleport";
    static final long TIMEOUT_MS = 60_000L;
    // key = "requester->target", value = System.currentTimeMillis() when sent
    static final Map<String, Long> pending = new ConcurrentHashMap<>();

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new TpaCmd());
        e.registerServerCommand(new TpaHereCmd());
        e.registerServerCommand(new TpAcceptCmd());
        e.registerServerCommand(new TpAcceptAllCmd());
        e.registerServerCommand(new TpaDenyCmd());
        e.registerServerCommand(new TpaDenyAllCmd());
        e.registerServerCommand(new TpaCancelCmd());
    }

    static String key(String from, String to) { return from + "->" + to; }

    static void cleanExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue() > TIMEOUT_MS);
    }

    static EntityPlayerMP findPlayer(MinecraftServer srv, String name) {
        for (Object o : srv.getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.getCommandSenderName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    static class TpaCmd extends CommandBase {
        public String getCommandName() { return "tpa"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/tpa <player>"; }
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /tpa <player>");
            EntityPlayerMP from = (EntityPlayerMP) sender;
            EntityPlayerMP to = findPlayer(MinecraftServer.getServer(), args[0]);
            if (to == null) throw new CommandException("Player not found: " + args[0]);
            if (to == from) throw new CommandException("You cannot tpa to yourself.");
            cleanExpired();
            pending.put(key(from.getCommandSenderName(), to.getCommandSenderName()), System.currentTimeMillis());
            from.addChatMessage(new ChatComponentText("Teleport request sent to " + to.getCommandSenderName() + ". Expires in 60s."));
            to.addChatMessage(new ChatComponentText(from.getCommandSenderName() + " wants to teleport to you. Use /tpaccept or /tpadeny."));
        }
    }
    static class TpaHereCmd extends CommandBase {
        public String getCommandName() { return "tpahere"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/tpahere <player>"; }
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /tpahere <player>");
            EntityPlayerMP from = (EntityPlayerMP) sender;
            EntityPlayerMP to = findPlayer(MinecraftServer.getServer(), args[0]);
            if (to == null) throw new CommandException("Player not found: " + args[0]);
            if (to == from) throw new CommandException("You cannot tpahere yourself.");
            cleanExpired();
            // key: from->to means "from wants 'to' to come here"
            pending.put(key(from.getCommandSenderName(), to.getCommandSenderName()) + ":here", System.currentTimeMillis());
            from.addChatMessage(new ChatComponentText("Request sent to " + to.getCommandSenderName() + " to come to you. Expires in 60s."));
            to.addChatMessage(new ChatComponentText(from.getCommandSenderName() + " wants you to teleport to them. Use /tpaccept or /tpadeny."));
        }
    }
    static class TpAcceptCmd extends CommandBase {
        public String getCommandName() { return "tpaccept"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/tpaccept <player>"; }
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /tpaccept <player>");
            EntityPlayerMP me = (EntityPlayerMP) sender;
            String requester = args[0];
            cleanExpired();
            String k = key(requester, me.getCommandSenderName());
            String kh = k + ":here";
            if (pending.containsKey(k)) {
                pending.remove(k);
                EntityPlayerMP req = findPlayer(MinecraftServer.getServer(), requester);
                if (req != null) { req.setPositionAndUpdate(me.posX, me.posY, me.posZ); req.addChatMessage(new ChatComponentText("Teleport accepted.")); }
                me.addChatMessage(new ChatComponentText("Accepted teleport from " + requester + "."));
            } else if (pending.containsKey(kh)) {
                pending.remove(kh);
                EntityPlayerMP req = findPlayer(MinecraftServer.getServer(), requester);
                if (req != null) { me.setPositionAndUpdate(req.posX, req.posY, req.posZ); req.addChatMessage(new ChatComponentText("Teleport accepted.")); }
                me.addChatMessage(new ChatComponentText("Accepted teleport to " + requester + "."));
            } else {
                throw new CommandException("No pending request from " + requester + ".");
            }
        }
    }
    static class TpAcceptAllCmd extends CommandBase {
        public String getCommandName() { return "tpacceptall"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/tpacceptall"; }
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            EntityPlayerMP me = (EntityPlayerMP) sender;
            cleanExpired();
            String myName = me.getCommandSenderName();
            int count = 0;
            for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String,Long> e = it.next();
                String k = e.getKey();
                boolean here = k.endsWith(":here");
                String base = here ? k.substring(0, k.length()-5) : k;
                String[] parts = base.split("->");
                if (parts.length != 2) continue;
                String from = parts[0], to = parts[1];
                if (!to.equals(myName)) continue;
                it.remove(); count++;
                EntityPlayerMP req = findPlayer(MinecraftServer.getServer(), from);
                if (req == null) continue;
                if (here) { me.setPositionAndUpdate(req.posX, req.posY, req.posZ); }
                else { req.setPositionAndUpdate(me.posX, me.posY, me.posZ); }
                req.addChatMessage(new ChatComponentText("Teleport accepted."));
            }
            me.addChatMessage(new ChatComponentText("Accepted " + count + " teleport request(s)."));
        }
    }
    static class TpaDenyCmd extends CommandBase {
        public String getCommandName() { return "tpadeny"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/tpadeny <player>"; }
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /tpadeny <player>");
            EntityPlayerMP me = (EntityPlayerMP) sender;
            String requester = args[0];
            cleanExpired();
            String k = key(requester, me.getCommandSenderName());
            boolean removed = pending.remove(k) != null | pending.remove(k + ":here") != null;
            if (!removed) throw new CommandException("No pending request from " + requester + ".");
            me.addChatMessage(new ChatComponentText("Denied teleport from " + requester + "."));
            EntityPlayerMP req = findPlayer(MinecraftServer.getServer(), requester);
            if (req != null) req.addChatMessage(new ChatComponentText(me.getCommandSenderName() + " denied your teleport request."));
        }
    }
    static class TpaDenyAllCmd extends CommandBase {
        public String getCommandName() { return "tpadenyall"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/tpadenyall"; }
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            EntityPlayerMP me = (EntityPlayerMP) sender;
            cleanExpired();
            String myName = me.getCommandSenderName();
            int count = 0;
            for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String,Long> e = it.next();
                String k = e.getKey();
                String base = k.endsWith(":here") ? k.substring(0, k.length()-5) : k;
                String[] parts = base.split("->");
                if (parts.length != 2 || !parts[1].equals(myName)) continue;
                it.remove(); count++;
                EntityPlayerMP req = findPlayer(MinecraftServer.getServer(), parts[0]);
                if (req != null) req.addChatMessage(new ChatComponentText(myName + " denied your teleport request."));
            }
            me.addChatMessage(new ChatComponentText("Denied " + count + " teleport request(s)."));
        }
    }
    static class TpaCancelCmd extends CommandBase {
        public String getCommandName() { return "tpacancel"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getCommandUsage(ICommandSender s) { return "/tpacancel <player|all>"; }
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /tpacancel <player|all>");
            EntityPlayerMP me = (EntityPlayerMP) sender;
            String myName = me.getCommandSenderName();
            cleanExpired();
            if ("all".equalsIgnoreCase(args[0])) {
                int count = 0;
                for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String,Long> e = it.next();
                    String base = e.getKey().endsWith(":here") ? e.getKey().substring(0, e.getKey().length()-5) : e.getKey();
                    if (base.startsWith(myName + "->")) { it.remove(); count++; }
                }
                me.addChatMessage(new ChatComponentText("Cancelled " + count + " outgoing request(s)."));
            } else {
                String target = args[0];
                String k = key(myName, target);
                boolean removed = pending.remove(k) != null | pending.remove(k + ":here") != null;
                if (!removed) throw new CommandException("No pending request to " + target + ".");
                me.addChatMessage(new ChatComponentText("Cancelled request to " + target + "."));
            }
        }
    }
}
"""


# ============================================================
# 1.12.2 FORGE
# CommandBase: getName/getUsage/execute(MinecraftServer, ...)
# TextComponentString, getPlayerList()
# ============================================================
SRC_1122 = """\
package net.itamio.tpateleport;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import java.util.*;
import java.util.concurrent.*;

@Mod(modid=TpaTeleportMod.MODID, name="Tpa Teleport", version="1.0.0", acceptedMinecraftVersions="[1.12,1.12.2]")
public class TpaTeleportMod {
    public static final String MODID = "tpateleport";
    static final long TIMEOUT_MS = 60_000L;
    static final Map<String, Long> pending = new ConcurrentHashMap<>();

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new TpaCmd());
        e.registerServerCommand(new TpaHereCmd());
        e.registerServerCommand(new TpAcceptCmd());
        e.registerServerCommand(new TpAcceptAllCmd());
        e.registerServerCommand(new TpaDenyCmd());
        e.registerServerCommand(new TpaDenyAllCmd());
        e.registerServerCommand(new TpaCancelCmd());
    }

    static String key(String from, String to) { return from + "->" + to; }
    static void cleanExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue() > TIMEOUT_MS);
    }

    static class TpaCmd extends CommandBase {
        public String getName() { return "tpa"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getUsage(ICommandSender s) { return "/tpa <player>"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /tpa <player>");
            EntityPlayerMP from = (EntityPlayerMP) sender;
            EntityPlayerMP to = srv.getPlayerList().getPlayerByUsername(args[0]);
            if (to == null) throw new CommandException("Player not found: " + args[0]);
            if (to == from) throw new CommandException("You cannot tpa to yourself.");
            cleanExpired();
            pending.put(key(from.getName(), to.getName()), System.currentTimeMillis());
            from.sendMessage(new TextComponentString("Teleport request sent to " + to.getName() + ". Expires in 60s."));
            to.sendMessage(new TextComponentString(from.getName() + " wants to teleport to you. Use /tpaccept or /tpadeny."));
        }
    }
    static class TpaHereCmd extends CommandBase {
        public String getName() { return "tpahere"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getUsage(ICommandSender s) { return "/tpahere <player>"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /tpahere <player>");
            EntityPlayerMP from = (EntityPlayerMP) sender;
            EntityPlayerMP to = srv.getPlayerList().getPlayerByUsername(args[0]);
            if (to == null) throw new CommandException("Player not found: " + args[0]);
            if (to == from) throw new CommandException("You cannot tpahere yourself.");
            cleanExpired();
            pending.put(key(from.getName(), to.getName()) + ":here", System.currentTimeMillis());
            from.sendMessage(new TextComponentString("Request sent to " + to.getName() + " to come to you. Expires in 60s."));
            to.sendMessage(new TextComponentString(from.getName() + " wants you to teleport to them. Use /tpaccept or /tpadeny."));
        }
    }
    static class TpAcceptCmd extends CommandBase {
        public String getName() { return "tpaccept"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getUsage(ICommandSender s) { return "/tpaccept <player>"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /tpaccept <player>");
            EntityPlayerMP me = (EntityPlayerMP) sender;
            String requester = args[0];
            cleanExpired();
            String k = key(requester, me.getName());
            String kh = k + ":here";
            if (pending.containsKey(k)) {
                pending.remove(k);
                EntityPlayerMP req = srv.getPlayerList().getPlayerByUsername(requester);
                if (req != null) { req.setPositionAndUpdate(me.posX, me.posY, me.posZ); req.sendMessage(new TextComponentString("Teleport accepted.")); }
                me.sendMessage(new TextComponentString("Accepted teleport from " + requester + "."));
            } else if (pending.containsKey(kh)) {
                pending.remove(kh);
                EntityPlayerMP req = srv.getPlayerList().getPlayerByUsername(requester);
                if (req != null) { me.setPositionAndUpdate(req.posX, req.posY, req.posZ); req.sendMessage(new TextComponentString("Teleport accepted.")); }
                me.sendMessage(new TextComponentString("Accepted teleport to " + requester + "."));
            } else {
                throw new CommandException("No pending request from " + requester + ".");
            }
        }
    }
    static class TpAcceptAllCmd extends CommandBase {
        public String getName() { return "tpacceptall"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getUsage(ICommandSender s) { return "/tpacceptall"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            EntityPlayerMP me = (EntityPlayerMP) sender;
            cleanExpired();
            String myName = me.getName();
            int count = 0;
            for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String,Long> e = it.next();
                String k = e.getKey();
                boolean here = k.endsWith(":here");
                String base = here ? k.substring(0, k.length()-5) : k;
                String[] parts = base.split("->");
                if (parts.length != 2 || !parts[1].equals(myName)) continue;
                it.remove(); count++;
                EntityPlayerMP req = srv.getPlayerList().getPlayerByUsername(parts[0]);
                if (req == null) continue;
                if (here) { me.setPositionAndUpdate(req.posX, req.posY, req.posZ); }
                else { req.setPositionAndUpdate(me.posX, me.posY, me.posZ); }
                req.sendMessage(new TextComponentString("Teleport accepted."));
            }
            me.sendMessage(new TextComponentString("Accepted " + count + " teleport request(s)."));
        }
    }
    static class TpaDenyCmd extends CommandBase {
        public String getName() { return "tpadeny"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getUsage(ICommandSender s) { return "/tpadeny <player>"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /tpadeny <player>");
            EntityPlayerMP me = (EntityPlayerMP) sender;
            String requester = args[0];
            cleanExpired();
            String k = key(requester, me.getName());
            boolean removed = pending.remove(k) != null | pending.remove(k + ":here") != null;
            if (!removed) throw new CommandException("No pending request from " + requester + ".");
            me.sendMessage(new TextComponentString("Denied teleport from " + requester + "."));
            EntityPlayerMP req = srv.getPlayerList().getPlayerByUsername(requester);
            if (req != null) req.sendMessage(new TextComponentString(me.getName() + " denied your teleport request."));
        }
    }
    static class TpaDenyAllCmd extends CommandBase {
        public String getName() { return "tpadenyall"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getUsage(ICommandSender s) { return "/tpadenyall"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            EntityPlayerMP me = (EntityPlayerMP) sender;
            cleanExpired();
            String myName = me.getName();
            int count = 0;
            for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String,Long> e = it.next();
                String k = e.getKey();
                String base = k.endsWith(":here") ? k.substring(0, k.length()-5) : k;
                String[] parts = base.split("->");
                if (parts.length != 2 || !parts[1].equals(myName)) continue;
                it.remove(); count++;
                EntityPlayerMP req = srv.getPlayerList().getPlayerByUsername(parts[0]);
                if (req != null) req.sendMessage(new TextComponentString(myName + " denied your teleport request."));
            }
            me.sendMessage(new TextComponentString("Denied " + count + " teleport request(s)."));
        }
    }
    static class TpaCancelCmd extends CommandBase {
        public String getName() { return "tpacancel"; }
        public int getRequiredPermissionLevel() { return 0; }
        public String getUsage(ICommandSender s) { return "/tpacancel <player|all>"; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Players only.");
            if (args.length < 1) throw new CommandException("Usage: /tpacancel <player|all>");
            EntityPlayerMP me = (EntityPlayerMP) sender;
            String myName = me.getName();
            cleanExpired();
            if ("all".equalsIgnoreCase(args[0])) {
                int count = 0;
                for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String,Long> e = it.next();
                    String base = e.getKey().endsWith(":here") ? e.getKey().substring(0, e.getKey().length()-5) : e.getKey();
                    if (base.startsWith(myName + "->")) { it.remove(); count++; }
                }
                me.sendMessage(new TextComponentString("Cancelled " + count + " outgoing request(s)."));
            } else {
                String target = args[0];
                String k = key(myName, target);
                boolean removed = pending.remove(k) != null | pending.remove(k + ":here") != null;
                if (!removed) throw new CommandException("No pending request to " + target + ".");
                me.sendMessage(new TextComponentString("Cancelled request to " + target + "."));
            }
        }
    }
}
"""


# ============================================================
# BRIGADIER BASE — shared helper for 1.16.5+ Forge/NeoForge
# Parameterized by text class, sendSuccess signature, event imports
# ============================================================

# 1.16.5 Forge: CommandSource, StringTextComponent, sendSuccess(comp, bool)
SRC_1165_FORGE = """\
package net.itamio.tpateleport;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import java.util.concurrent.*;

@Mod(TpaTeleportMod.MODID)
public class TpaTeleportMod {
    public static final String MODID = "tpateleport";
    static final long TIMEOUT_MS = 60_000L;
    static final Map<String, Long> pending = new ConcurrentHashMap<>();

    public TpaTeleportMod() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSource> d = e.getDispatcher();
        d.register(Commands.literal("tpa").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpa(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpahere").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpahere(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpaccept").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpaccept(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpacceptall").executes(ctx -> tpacceptall(ctx.getSource())));
        d.register(Commands.literal("tpadeny").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpadeny(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpadenyall").executes(ctx -> tpadenyall(ctx.getSource())));
        d.register(Commands.literal("tpacancel").then(Commands.argument("target", StringArgumentType.word()).executes(ctx -> tpacancel(ctx.getSource(), StringArgumentType.getString(ctx,"target")))));
    }

    static String key(String f, String t) { return f + "->" + t; }
    static void cleanExpired() { long now=System.currentTimeMillis(); pending.entrySet().removeIf(e->now-e.getValue()>TIMEOUT_MS); }

    static int tpa(CommandSource src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity from = src.asPlayer();
        MinecraftServer srv = src.getServer();
        ServerPlayerEntity to = srv.getPlayerList().getPlayerByName(target);
        if (to == null) { src.sendSuccess(new StringTextComponent("Player not found: "+target), false); return 0; }
        if (to == from) { src.sendSuccess(new StringTextComponent("You cannot tpa to yourself."), false); return 0; }
        cleanExpired();
        pending.put(key(from.getName().getString(), to.getName().getString()), System.currentTimeMillis());
        src.sendSuccess(new StringTextComponent("Teleport request sent to "+to.getName().getString()+". Expires in 60s."), false);
        to.sendMessage(new StringTextComponent(from.getName().getString()+" wants to teleport to you. Use /tpaccept or /tpadeny."), to.getUUID());
        return 1;
    }
    static int tpahere(CommandSource src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity from = src.asPlayer();
        MinecraftServer srv = src.getServer();
        ServerPlayerEntity to = srv.getPlayerList().getPlayerByName(target);
        if (to == null) { src.sendSuccess(new StringTextComponent("Player not found: "+target), false); return 0; }
        if (to == from) { src.sendSuccess(new StringTextComponent("You cannot tpahere yourself."), false); return 0; }
        cleanExpired();
        pending.put(key(from.getName().getString(), to.getName().getString())+":here", System.currentTimeMillis());
        src.sendSuccess(new StringTextComponent("Request sent to "+to.getName().getString()+" to come to you. Expires in 60s."), false);
        to.sendMessage(new StringTextComponent(from.getName().getString()+" wants you to teleport to them. Use /tpaccept or /tpadeny."), to.getUUID());
        return 1;
    }
    static int tpaccept(CommandSource src, String requester) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me = src.asPlayer();
        cleanExpired();
        String k = key(requester, me.getName().getString());
        String kh = k+":here";
        MinecraftServer srv = src.getServer();
        if (pending.containsKey(k)) {
            pending.remove(k);
            ServerPlayerEntity req = srv.getPlayerList().getPlayerByName(requester);
            if (req != null) { req.teleportTo(me.getX(),me.getY(),me.getZ()); req.sendMessage(new StringTextComponent("Teleport accepted."), req.getUUID()); }
            src.sendSuccess(new StringTextComponent("Accepted teleport from "+requester+"."), false);
        } else if (pending.containsKey(kh)) {
            pending.remove(kh);
            ServerPlayerEntity req = srv.getPlayerList().getPlayerByName(requester);
            if (req != null) { me.teleportTo(req.getX(),req.getY(),req.getZ()); req.sendMessage(new StringTextComponent("Teleport accepted."), req.getUUID()); }
            src.sendSuccess(new StringTextComponent("Accepted teleport to "+requester+"."), false);
        } else { src.sendSuccess(new StringTextComponent("No pending request from "+requester+"."), false); return 0; }
        return 1;
    }
    static int tpacceptall(CommandSource src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me = src.asPlayer();
        cleanExpired();
        String myName = me.getName().getString();
        int count = 0;
        MinecraftServer srv = src.getServer();
        for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,Long> e = it.next();
            String k = e.getKey(); boolean here = k.endsWith(":here");
            String base = here ? k.substring(0,k.length()-5) : k;
            String[] parts = base.split("->"); if (parts.length!=2||!parts[1].equals(myName)) continue;
            it.remove(); count++;
            ServerPlayerEntity req = srv.getPlayerList().getPlayerByName(parts[0]); if (req==null) continue;
            if (here) { me.teleportTo(req.getX(),req.getY(),req.getZ()); } else { req.teleportTo(me.getX(),me.getY(),me.getZ()); }
            req.sendMessage(new StringTextComponent("Teleport accepted."), req.getUUID());
        }
        src.sendSuccess(new StringTextComponent("Accepted "+count+" teleport request(s)."), false); return 1;
    }
    static int tpadeny(CommandSource src, String requester) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me = src.asPlayer();
        cleanExpired();
        String k = key(requester, me.getName().getString());
        boolean removed = pending.remove(k)!=null | pending.remove(k+":here")!=null;
        if (!removed) { src.sendSuccess(new StringTextComponent("No pending request from "+requester+"."), false); return 0; }
        src.sendSuccess(new StringTextComponent("Denied teleport from "+requester+"."), false);
        ServerPlayerEntity req = src.getServer().getPlayerList().getPlayerByName(requester);
        if (req!=null) req.sendMessage(new StringTextComponent(me.getName().getString()+" denied your teleport request."), req.getUUID());
        return 1;
    }
    static int tpadenyall(CommandSource src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me = src.asPlayer();
        cleanExpired();
        String myName = me.getName().getString(); int count=0;
        for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,Long> e = it.next();
            String k = e.getKey(); String base = k.endsWith(":here")?k.substring(0,k.length()-5):k;
            String[] parts = base.split("->"); if (parts.length!=2||!parts[1].equals(myName)) continue;
            it.remove(); count++;
            ServerPlayerEntity req = src.getServer().getPlayerList().getPlayerByName(parts[0]);
            if (req!=null) req.sendMessage(new StringTextComponent(myName+" denied your teleport request."), req.getUUID());
        }
        src.sendSuccess(new StringTextComponent("Denied "+count+" teleport request(s)."), false); return 1;
    }
    static int tpacancel(CommandSource src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me = src.asPlayer();
        String myName = me.getName().getString(); cleanExpired();
        if ("all".equalsIgnoreCase(target)) {
            int count=0;
            for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String,Long> e = it.next();
                String base = e.getKey().endsWith(":here")?e.getKey().substring(0,e.getKey().length()-5):e.getKey();
                if (base.startsWith(myName+"->")) { it.remove(); count++; }
            }
            src.sendSuccess(new StringTextComponent("Cancelled "+count+" outgoing request(s)."), false);
        } else {
            String k = key(myName, target);
            boolean removed = pending.remove(k)!=null | pending.remove(k+":here")!=null;
            if (!removed) { src.sendSuccess(new StringTextComponent("No pending request to "+target+"."), false); return 0; }
            src.sendSuccess(new StringTextComponent("Cancelled request to "+target+"."), false);
        }
        return 1;
    }
}
"""


# ============================================================
# 1.17-1.18.x FORGE: CommandSourceStack, TextComponent, sendSuccess(comp,bool)
# ============================================================
SRC_117_118_FORGE = """\
package net.itamio.tpateleport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import java.util.concurrent.*;

@Mod(TpaTeleportMod.MODID)
public class TpaTeleportMod {
    public static final String MODID = "tpateleport";
    static final long TIMEOUT_MS = 60_000L;
    static final Map<String, Long> pending = new ConcurrentHashMap<>();

    public TpaTeleportMod() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("tpa").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpa(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpahere").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpahere(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpaccept").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpaccept(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpacceptall").executes(ctx -> tpacceptall(ctx.getSource())));
        d.register(Commands.literal("tpadeny").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpadeny(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpadenyall").executes(ctx -> tpadenyall(ctx.getSource())));
        d.register(Commands.literal("tpacancel").then(Commands.argument("target", StringArgumentType.word()).executes(ctx -> tpacancel(ctx.getSource(), StringArgumentType.getString(ctx,"target")))));
    }

    static String key(String f, String t) { return f+"->"+t; }
    static void cleanExpired() { long now=System.currentTimeMillis(); pending.entrySet().removeIf(e->now-e.getValue()>TIMEOUT_MS); }

    static int tpa(CommandSourceStack src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer from = src.getPlayerOrException();
        ServerPlayer to = src.getServer().getPlayerList().getPlayerByName(target);
        if (to==null) { src.sendSuccess(new TextComponent("Player not found: "+target), false); return 0; }
        if (to==from) { src.sendSuccess(new TextComponent("You cannot tpa to yourself."), false); return 0; }
        cleanExpired();
        pending.put(key(from.getName().getString(), to.getName().getString()), System.currentTimeMillis());
        src.sendSuccess(new TextComponent("Teleport request sent to "+to.getName().getString()+". Expires in 60s."), false);
        to.sendMessage(new TextComponent(from.getName().getString()+" wants to teleport to you. Use /tpaccept or /tpadeny."), to.getUUID());
        return 1;
    }
    static int tpahere(CommandSourceStack src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer from = src.getPlayerOrException();
        ServerPlayer to = src.getServer().getPlayerList().getPlayerByName(target);
        if (to==null) { src.sendSuccess(new TextComponent("Player not found: "+target), false); return 0; }
        if (to==from) { src.sendSuccess(new TextComponent("You cannot tpahere yourself."), false); return 0; }
        cleanExpired();
        pending.put(key(from.getName().getString(), to.getName().getString())+":here", System.currentTimeMillis());
        src.sendSuccess(new TextComponent("Request sent to "+to.getName().getString()+" to come to you. Expires in 60s."), false);
        to.sendMessage(new TextComponent(from.getName().getString()+" wants you to teleport to them. Use /tpaccept or /tpadeny."), to.getUUID());
        return 1;
    }
    static int tpaccept(CommandSourceStack src, String requester) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me = src.getPlayerOrException();
        cleanExpired();
        String k = key(requester, me.getName().getString()); String kh = k+":here";
        MinecraftServer srv = src.getServer();
        if (pending.containsKey(k)) {
            pending.remove(k);
            ServerPlayer req = srv.getPlayerList().getPlayerByName(requester);
            if (req!=null) { req.teleportTo(me.getX(),me.getY(),me.getZ()); req.sendMessage(new TextComponent("Teleport accepted."), req.getUUID()); }
            src.sendSuccess(new TextComponent("Accepted teleport from "+requester+"."), false);
        } else if (pending.containsKey(kh)) {
            pending.remove(kh);
            ServerPlayer req = srv.getPlayerList().getPlayerByName(requester);
            if (req!=null) { me.teleportTo(req.getX(),req.getY(),req.getZ()); req.sendMessage(new TextComponent("Teleport accepted."), req.getUUID()); }
            src.sendSuccess(new TextComponent("Accepted teleport to "+requester+"."), false);
        } else { src.sendSuccess(new TextComponent("No pending request from "+requester+"."), false); return 0; }
        return 1;
    }
    static int tpacceptall(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me = src.getPlayerOrException(); cleanExpired();
        String myName = me.getName().getString(); int count=0;
        MinecraftServer srv = src.getServer();
        for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,Long> e = it.next(); String k=e.getKey(); boolean here=k.endsWith(":here");
            String base=here?k.substring(0,k.length()-5):k; String[] parts=base.split("->");
            if (parts.length!=2||!parts[1].equals(myName)) continue;
            it.remove(); count++;
            ServerPlayer req=srv.getPlayerList().getPlayerByName(parts[0]); if (req==null) continue;
            if (here) { me.teleportTo(req.getX(),req.getY(),req.getZ()); } else { req.teleportTo(me.getX(),me.getY(),me.getZ()); }
            req.sendMessage(new TextComponent("Teleport accepted."), req.getUUID());
        }
        src.sendSuccess(new TextComponent("Accepted "+count+" teleport request(s)."), false); return 1;
    }
    static int tpadeny(CommandSourceStack src, String requester) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me = src.getPlayerOrException(); cleanExpired();
        String k = key(requester, me.getName().getString());
        boolean removed = pending.remove(k)!=null | pending.remove(k+":here")!=null;
        if (!removed) { src.sendSuccess(new TextComponent("No pending request from "+requester+"."), false); return 0; }
        src.sendSuccess(new TextComponent("Denied teleport from "+requester+"."), false);
        ServerPlayer req = src.getServer().getPlayerList().getPlayerByName(requester);
        if (req!=null) req.sendMessage(new TextComponent(me.getName().getString()+" denied your teleport request."), req.getUUID());
        return 1;
    }
    static int tpadenyall(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me = src.getPlayerOrException(); cleanExpired();
        String myName=me.getName().getString(); int count=0;
        for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,Long> e=it.next(); String k=e.getKey();
            String base=k.endsWith(":here")?k.substring(0,k.length()-5):k; String[] parts=base.split("->");
            if (parts.length!=2||!parts[1].equals(myName)) continue; it.remove(); count++;
            ServerPlayer req=src.getServer().getPlayerList().getPlayerByName(parts[0]);
            if (req!=null) req.sendMessage(new TextComponent(myName+" denied your teleport request."), req.getUUID());
        }
        src.sendSuccess(new TextComponent("Denied "+count+" teleport request(s)."), false); return 1;
    }
    static int tpacancel(CommandSourceStack src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me = src.getPlayerOrException(); String myName=me.getName().getString(); cleanExpired();
        if ("all".equalsIgnoreCase(target)) {
            int count=0;
            for (Iterator<Map.Entry<String,Long>> it = pending.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String,Long> e=it.next();
                String base=e.getKey().endsWith(":here")?e.getKey().substring(0,e.getKey().length()-5):e.getKey();
                if (base.startsWith(myName+"->")) { it.remove(); count++; }
            }
            src.sendSuccess(new TextComponent("Cancelled "+count+" outgoing request(s)."), false);
        } else {
            String k=key(myName,target);
            boolean removed=pending.remove(k)!=null|pending.remove(k+":here")!=null;
            if (!removed) { src.sendSuccess(new TextComponent("No pending request to "+target+"."), false); return 0; }
            src.sendSuccess(new TextComponent("Cancelled request to "+target+"."), false);
        }
        return 1;
    }
}
"""

# 1.19-1.19.4 Forge: Component.literal replaces TextComponent
SRC_119_FORGE = SRC_117_118_FORGE.replace(
    "import net.minecraft.network.chat.TextComponent;",
    "import net.minecraft.network.chat.Component;"
).replace("new TextComponent(", "Component.literal(")


# 1.20-1.20.6 Forge: sendSuccess takes Supplier<Component>
SRC_120_FORGE = SRC_119_FORGE.replace(
    "src.sendSuccess(Component.literal(",
    "src.sendSuccess(() -> Component.literal("
)

# 1.21-1.21.x Forge: same as 1.20 (sendSuccess supplier already)
SRC_121_FORGE = SRC_120_FORGE

# 1.21.6-1.21.8 Forge: EventBus 7 — RegisterCommandsEvent.BUS.addListener
SRC_1216_FORGE = """\
package net.itamio.tpateleport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import java.util.concurrent.*;

@Mod(TpaTeleportMod.MODID)
public class TpaTeleportMod {
    public static final String MODID = "tpateleport";
    static final long TIMEOUT_MS = 60_000L;
    static final Map<String, Long> pending = new ConcurrentHashMap<>();

    public TpaTeleportMod(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        RegisterCommandsEvent.BUS.addListener(TpaTeleportMod::onRegisterCommands);
    }

    static void onRegisterCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("tpa").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpa(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpahere").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpahere(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpaccept").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpaccept(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpacceptall").executes(ctx -> tpacceptall(ctx.getSource())));
        d.register(Commands.literal("tpadeny").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> tpadeny(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(Commands.literal("tpadenyall").executes(ctx -> tpadenyall(ctx.getSource())));
        d.register(Commands.literal("tpacancel").then(Commands.argument("target", StringArgumentType.word()).executes(ctx -> tpacancel(ctx.getSource(), StringArgumentType.getString(ctx,"target")))));
    }

    static String key(String f, String t) { return f+"->"+t; }
    static void cleanExpired() { long now=System.currentTimeMillis(); pending.entrySet().removeIf(e->now-e.getValue()>TIMEOUT_MS); }

    static int tpa(CommandSourceStack src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer from = src.getPlayerOrException();
        ServerPlayer to = src.getServer().getPlayerList().getPlayerByName(target);
        if (to==null) { src.sendSuccess(()->Component.literal("Player not found: "+target), false); return 0; }
        if (to==from) { src.sendSuccess(()->Component.literal("You cannot tpa to yourself."), false); return 0; }
        cleanExpired();
        pending.put(key(from.getName().getString(), to.getName().getString()), System.currentTimeMillis());
        src.sendSuccess(()->Component.literal("Teleport request sent to "+to.getName().getString()+". Expires in 60s."), false);
        to.sendSystemMessage(Component.literal(from.getName().getString()+" wants to teleport to you. Use /tpaccept or /tpadeny."));
        return 1;
    }
    static int tpahere(CommandSourceStack src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer from = src.getPlayerOrException();
        ServerPlayer to = src.getServer().getPlayerList().getPlayerByName(target);
        if (to==null) { src.sendSuccess(()->Component.literal("Player not found: "+target), false); return 0; }
        if (to==from) { src.sendSuccess(()->Component.literal("You cannot tpahere yourself."), false); return 0; }
        cleanExpired();
        pending.put(key(from.getName().getString(), to.getName().getString())+":here", System.currentTimeMillis());
        src.sendSuccess(()->Component.literal("Request sent to "+to.getName().getString()+" to come to you. Expires in 60s."), false);
        to.sendSystemMessage(Component.literal(from.getName().getString()+" wants you to teleport to them. Use /tpaccept or /tpadeny."));
        return 1;
    }
    static int tpaccept(CommandSourceStack src, String requester) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me = src.getPlayerOrException(); cleanExpired();
        String k=key(requester,me.getName().getString()); String kh=k+":here";
        MinecraftServer srv=src.getServer();
        if (pending.containsKey(k)) {
            pending.remove(k);
            ServerPlayer req=srv.getPlayerList().getPlayerByName(requester);
            if (req!=null) { req.teleportTo(me.getX(),me.getY(),me.getZ()); req.sendSystemMessage(Component.literal("Teleport accepted.")); }
            src.sendSuccess(()->Component.literal("Accepted teleport from "+requester+"."), false);
        } else if (pending.containsKey(kh)) {
            pending.remove(kh);
            ServerPlayer req=srv.getPlayerList().getPlayerByName(requester);
            if (req!=null) { me.teleportTo(req.getX(),req.getY(),req.getZ()); req.sendSystemMessage(Component.literal("Teleport accepted.")); }
            src.sendSuccess(()->Component.literal("Accepted teleport to "+requester+"."), false);
        } else { src.sendSuccess(()->Component.literal("No pending request from "+requester+"."), false); return 0; }
        return 1;
    }
    static int tpacceptall(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me=src.getPlayerOrException(); cleanExpired();
        String myName=me.getName().getString(); int count=0;
        MinecraftServer srv=src.getServer();
        for (Iterator<Map.Entry<String,Long>> it=pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,Long> e=it.next(); String k=e.getKey(); boolean here=k.endsWith(":here");
            String base=here?k.substring(0,k.length()-5):k; String[] parts=base.split("->");
            if (parts.length!=2||!parts[1].equals(myName)) continue; it.remove(); count++;
            ServerPlayer req=srv.getPlayerList().getPlayerByName(parts[0]); if (req==null) continue;
            if (here) { me.teleportTo(req.getX(),req.getY(),req.getZ()); } else { req.teleportTo(me.getX(),me.getY(),me.getZ()); }
            req.sendSystemMessage(Component.literal("Teleport accepted."));
        }
        src.sendSuccess(()->Component.literal("Accepted "+count+" teleport request(s)."), false); return 1;
    }
    static int tpadeny(CommandSourceStack src, String requester) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me=src.getPlayerOrException(); cleanExpired();
        String k=key(requester,me.getName().getString());
        boolean removed=pending.remove(k)!=null|pending.remove(k+":here")!=null;
        if (!removed) { src.sendSuccess(()->Component.literal("No pending request from "+requester+"."), false); return 0; }
        src.sendSuccess(()->Component.literal("Denied teleport from "+requester+"."), false);
        ServerPlayer req=src.getServer().getPlayerList().getPlayerByName(requester);
        if (req!=null) req.sendSystemMessage(Component.literal(me.getName().getString()+" denied your teleport request."));
        return 1;
    }
    static int tpadenyall(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me=src.getPlayerOrException(); cleanExpired();
        String myName=me.getName().getString(); int count=0;
        for (Iterator<Map.Entry<String,Long>> it=pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,Long> e=it.next(); String k=e.getKey();
            String base=k.endsWith(":here")?k.substring(0,k.length()-5):k; String[] parts=base.split("->");
            if (parts.length!=2||!parts[1].equals(myName)) continue; it.remove(); count++;
            ServerPlayer req=src.getServer().getPlayerList().getPlayerByName(parts[0]);
            if (req!=null) req.sendSystemMessage(Component.literal(myName+" denied your teleport request."));
        }
        src.sendSuccess(()->Component.literal("Denied "+count+" teleport request(s)."), false); return 1;
    }
    static int tpacancel(CommandSourceStack src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer me=src.getPlayerOrException(); String myName=me.getName().getString(); cleanExpired();
        if ("all".equalsIgnoreCase(target)) {
            int count=0;
            for (Iterator<Map.Entry<String,Long>> it=pending.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String,Long> e=it.next();
                String base=e.getKey().endsWith(":here")?e.getKey().substring(0,e.getKey().length()-5):e.getKey();
                if (base.startsWith(myName+"->")) { it.remove(); count++; }
            }
            src.sendSuccess(()->Component.literal("Cancelled "+count+" outgoing request(s)."), false);
        } else {
            String k=key(myName,target);
            boolean removed=pending.remove(k)!=null|pending.remove(k+":here")!=null;
            if (!removed) { src.sendSuccess(()->Component.literal("No pending request to "+target+"."), false); return 0; }
            src.sendSuccess(()->Component.literal("Cancelled request to "+target+"."), false);
        }
        return 1;
    }
}
"""

# 1.21.9-1.21.11 Forge: same EventBus7 pattern (BUS.addListener)
SRC_1219_FORGE = SRC_1216_FORGE


# ============================================================
# 26.1-26.x FORGE: EventBus7, no obfuscation, Java 25
# Same as 1.21.6+ pattern
# ============================================================
SRC_261_FORGE = SRC_1216_FORGE

# ============================================================
# FABRIC SOURCES
# TPA is server-side only — uses ServerModInitializer + ServerLifecycleEvents
# ============================================================

def fabric_mod_json(mod_id, name, description, entrypoint, homepage):
    return f"""\
{{
  "schemaVersion": 1,
  "id": "{mod_id}",
  "version": "1.0.0",
  "name": "{name}",
  "description": "{description}",
  "authors": ["Itamio"],
  "contact": {{
    "homepage": "{homepage}"
  }},
  "license": "MIT",
  "environment": "server",
  "entrypoints": {{
    "main": ["{entrypoint}"]
  }},
  "depends": {{
    "fabricloader": ">=0.12.0",
    "fabric-api": "*",
    "minecraft": "*"
  }}
}}
"""

FABRIC_MOD_JSON = fabric_mod_json(
    MOD_ID, MOD_NAME, DESCRIPTION, ENTRYPOINT, HOMEPAGE
)

# ============================================================
# Fabric 1.16.5 — presplit, yarn mappings
# ServerCommandSource, LiteralText, sendFeedback(text, bool)
# ServerLifecycleEvents.SERVER_STARTING
# ============================================================
SRC_1165_FABRIC = """\
package net.itamio.tpateleport;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import java.util.*;
import java.util.concurrent.*;

public class TpaTeleportMod implements ModInitializer {
    static final long TIMEOUT_MS = 60_000L;
    static final Map<String, Long> pending = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> register(dispatcher));
    }

    static String key(String f, String t) { return f+"->"+t; }
    static void cleanExpired() { long now=System.currentTimeMillis(); pending.entrySet().removeIf(e->now-e.getValue()>TIMEOUT_MS); }

    static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("tpa").then(CommandManager.argument("player", StringArgumentType.word()).executes(ctx -> tpa(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(CommandManager.literal("tpahere").then(CommandManager.argument("player", StringArgumentType.word()).executes(ctx -> tpahere(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(CommandManager.literal("tpaccept").then(CommandManager.argument("player", StringArgumentType.word()).executes(ctx -> tpaccept(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(CommandManager.literal("tpacceptall").executes(ctx -> tpacceptall(ctx.getSource())));
        d.register(CommandManager.literal("tpadeny").then(CommandManager.argument("player", StringArgumentType.word()).executes(ctx -> tpadeny(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        d.register(CommandManager.literal("tpadenyall").executes(ctx -> tpadenyall(ctx.getSource())));
        d.register(CommandManager.literal("tpacancel").then(CommandManager.argument("target", StringArgumentType.word()).executes(ctx -> tpacancel(ctx.getSource(), StringArgumentType.getString(ctx,"target")))));
    }

    static int tpa(ServerCommandSource src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity from = src.getPlayer();
        ServerPlayerEntity to = src.getMinecraftServer().getPlayerManager().getPlayer(target);
        if (to==null) { src.sendFeedback(new LiteralText("Player not found: "+target), false); return 0; }
        if (to==from) { src.sendFeedback(new LiteralText("You cannot tpa to yourself."), false); return 0; }
        cleanExpired();
        pending.put(key(from.getName().getString(), to.getName().getString()), System.currentTimeMillis());
        src.sendFeedback(new LiteralText("Teleport request sent to "+to.getName().getString()+". Expires in 60s."), false);
        to.sendMessage(new LiteralText(from.getName().getString()+" wants to teleport to you. Use /tpaccept or /tpadeny."), to.getUuid());
        return 1;
    }
    static int tpahere(ServerCommandSource src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity from = src.getPlayer();
        ServerPlayerEntity to = src.getMinecraftServer().getPlayerManager().getPlayer(target);
        if (to==null) { src.sendFeedback(new LiteralText("Player not found: "+target), false); return 0; }
        if (to==from) { src.sendFeedback(new LiteralText("You cannot tpahere yourself."), false); return 0; }
        cleanExpired();
        pending.put(key(from.getName().getString(), to.getName().getString())+":here", System.currentTimeMillis());
        src.sendFeedback(new LiteralText("Request sent to "+to.getName().getString()+" to come to you. Expires in 60s."), false);
        to.sendMessage(new LiteralText(from.getName().getString()+" wants you to teleport to them. Use /tpaccept or /tpadeny."), to.getUuid());
        return 1;
    }
    static int tpaccept(ServerCommandSource src, String requester) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me = src.getPlayer(); cleanExpired();
        String k=key(requester,me.getName().getString()); String kh=k+":here";
        if (pending.containsKey(k)) {
            pending.remove(k);
            ServerPlayerEntity req=src.getMinecraftServer().getPlayerManager().getPlayer(requester);
            if (req!=null) { req.teleport(me.getX(),me.getY(),me.getZ()); req.sendMessage(new LiteralText("Teleport accepted."), req.getUuid()); }
            src.sendFeedback(new LiteralText("Accepted teleport from "+requester+"."), false);
        } else if (pending.containsKey(kh)) {
            pending.remove(kh);
            ServerPlayerEntity req=src.getMinecraftServer().getPlayerManager().getPlayer(requester);
            if (req!=null) { me.teleport(req.getX(),req.getY(),req.getZ()); req.sendMessage(new LiteralText("Teleport accepted."), req.getUuid()); }
            src.sendFeedback(new LiteralText("Accepted teleport to "+requester+"."), false);
        } else { src.sendFeedback(new LiteralText("No pending request from "+requester+"."), false); return 0; }
        return 1;
    }
    static int tpacceptall(ServerCommandSource src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me=src.getPlayer(); cleanExpired();
        String myName=me.getName().getString(); int count=0;
        for (Iterator<Map.Entry<String,Long>> it=pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,Long> e=it.next(); String k=e.getKey(); boolean here=k.endsWith(":here");
            String base=here?k.substring(0,k.length()-5):k; String[] parts=base.split("->");
            if (parts.length!=2||!parts[1].equals(myName)) continue; it.remove(); count++;
            ServerPlayerEntity req=src.getMinecraftServer().getPlayerManager().getPlayer(parts[0]); if (req==null) continue;
            if (here) { me.teleport(req.getX(),req.getY(),req.getZ()); } else { req.teleport(me.getX(),me.getY(),me.getZ()); }
            req.sendMessage(new LiteralText("Teleport accepted."), req.getUuid());
        }
        src.sendFeedback(new LiteralText("Accepted "+count+" teleport request(s)."), false); return 1;
    }
    static int tpadeny(ServerCommandSource src, String requester) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me=src.getPlayer(); cleanExpired();
        String k=key(requester,me.getName().getString());
        boolean removed=pending.remove(k)!=null|pending.remove(k+":here")!=null;
        if (!removed) { src.sendFeedback(new LiteralText("No pending request from "+requester+"."), false); return 0; }
        src.sendFeedback(new LiteralText("Denied teleport from "+requester+"."), false);
        ServerPlayerEntity req=src.getMinecraftServer().getPlayerManager().getPlayer(requester);
        if (req!=null) req.sendMessage(new LiteralText(me.getName().getString()+" denied your teleport request."), req.getUuid());
        return 1;
    }
    static int tpadenyall(ServerCommandSource src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me=src.getPlayer(); cleanExpired();
        String myName=me.getName().getString(); int count=0;
        for (Iterator<Map.Entry<String,Long>> it=pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,Long> e=it.next(); String k=e.getKey();
            String base=k.endsWith(":here")?k.substring(0,k.length()-5):k; String[] parts=base.split("->");
            if (parts.length!=2||!parts[1].equals(myName)) continue; it.remove(); count++;
            ServerPlayerEntity req=src.getMinecraftServer().getPlayerManager().getPlayer(parts[0]);
            if (req!=null) req.sendMessage(new LiteralText(myName+" denied your teleport request."), req.getUuid());
        }
        src.sendFeedback(new LiteralText("Denied "+count+" teleport request(s)."), false); return 1;
    }
    static int tpacancel(ServerCommandSource src, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me=src.getPlayer(); String myName=me.getName().getString(); cleanExpired();
        if ("all".equalsIgnoreCase(target)) {
            int count=0;
            for (Iterator<Map.Entry<String,Long>> it=pending.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String,Long> e=it.next();
                String base=e.getKey().endsWith(":here")?e.getKey().substring(0,e.getKey().length()-5):e.getKey();
                if (base.startsWith(myName+"->")) { it.remove(); count++; }
            }
            src.sendFeedback(new LiteralText("Cancelled "+count+" outgoing request(s)."), false);
        } else {
            String k=key(myName,target);
            boolean removed=pending.remove(k)!=null|pending.remove(k+":here")!=null;
            if (!removed) { src.sendFeedback(new LiteralText("No pending request to "+target+"."), false); return 0; }
            src.sendFeedback(new LiteralText("Cancelled request to "+target+"."), false);
        }
        return 1;
    }
}
"""


# ============================================================
# Fabric 1.17-1.18.x — presplit, yarn, Text.of / sendMessage(text, uuid)
# CommandRegistrationCallback.EVENT (fabric-api)
# ============================================================
SRC_117_118_FABRIC = SRC_1165_FABRIC.replace(
    "import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;",
    "import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;"
).replace(
    "CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> register(dispatcher));",
    "CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, dedicated) -> register(dispatcher));"
)

# Fabric 1.19-1.19.4 — Text.literal replaces LiteralText, sendFeedback -> sendFeedback(supplier, bool) in 1.20+
# For 1.19 sendFeedback still takes Text directly
SRC_119_FABRIC = SRC_117_118_FABRIC.replace(
    "import net.minecraft.text.LiteralText;",
    "import net.minecraft.text.Text;"
).replace("new LiteralText(", "Text.literal(")

# Fabric 1.20-1.20.6 — sendFeedback takes Supplier<Text>
SRC_120_FABRIC = SRC_119_FABRIC.replace(
    "src.sendFeedback(Text.literal(",
    "src.sendFeedback(() -> Text.literal("
)

# Fabric 1.21+ — same as 1.20 (Mojang mappings, but yarn class names same for commands)
SRC_121_FABRIC = SRC_120_FABRIC

# Fabric 1.21.9-1.21.11 — same
SRC_1219_FABRIC = SRC_121_FABRIC

# Fabric 26.1.x — same pattern (no obfuscation, Java 25)
SRC_261_FABRIC = SRC_1219_FABRIC

# ============================================================
# NEOFORGE SOURCES
# ============================================================

# NeoForge 1.20.2-1.20.4: net.neoforged, SubscribeEvent, NeoForge.EVENT_BUS
SRC_120_NEOFORGE = SRC_120_FORGE.replace(
    "import net.minecraftforge.common.MinecraftForge;",
    "import net.neoforged.neoforge.common.NeoForge;"
).replace(
    "import net.minecraftforge.event.RegisterCommandsEvent;",
    "import net.neoforged.neoforge.event.RegisterCommandsEvent;"
).replace(
    "import net.minecraftforge.eventbus.api.SubscribeEvent;",
    "import net.neoforged.bus.api.SubscribeEvent;"
).replace(
    "import net.minecraftforge.fml.common.Mod;",
    "import net.neoforged.fml.common.Mod;"
).replace(
    "MinecraftForge.EVENT_BUS.register(this);",
    "NeoForge.EVENT_BUS.register(this);"
).replace(
    "@SubscribeEvent",
    "@net.neoforged.bus.api.SubscribeEvent"
)

# NeoForge 1.20.5-1.21.x: same as 1.20.2 (sendSuccess supplier already in SRC_120_FORGE base)
SRC_1205_NEOFORGE = SRC_120_NEOFORGE

# NeoForge 1.21.2-1.21.8: same
SRC_1212_NEOFORGE = SRC_1205_NEOFORGE

# NeoForge 1.21.9-1.21.11: same
SRC_1219_NEOFORGE = SRC_1205_NEOFORGE

# NeoForge 26.1.x: same
SRC_261_NEOFORGE = SRC_1205_NEOFORGE


# ============================================================
# TARGETS — only missing versions (already published excluded)
#
# Already published:
#   1.12-1.12.2  forge   -> skip
#   1.20-1.20.6  forge   -> skip
#   1.21-1.21.1  fabric  -> skip
#   1.21.2-1.21.8 fabric -> skip
#   1.21.9-1.21.11 fabric -> skip
# ============================================================
targets = [
    # (folder_name, src, loader, mc_ver, extra_files)
    # --- 1.8.9 ---
    ("TpaTeleport-1.8.9-forge",         SRC_189,          "forge",    "1.8.9",         {}),
    # --- 1.16.5 ---
    ("TpaTeleport-1.16.5-forge",        SRC_1165_FORGE,   "forge",    "1.16.5",        {}),
    ("TpaTeleport-1.16.5-fabric",       SRC_1165_FABRIC,  "fabric",   "1.16.5",        {"src/main/resources/fabric.mod.json": FABRIC_MOD_JSON}),
    # --- 1.17-1.17.1 ---
    ("TpaTeleport-1.17-1.17.1-forge",   SRC_117_118_FORGE,"forge",    "1.17-1.17.1",   {}),
    ("TpaTeleport-1.17-1.17.1-fabric",  SRC_117_118_FABRIC,"fabric",  "1.17-1.17.1",   {"src/main/resources/fabric.mod.json": FABRIC_MOD_JSON}),
    # --- 1.18-1.18.2 ---
    ("TpaTeleport-1.18-1.18.2-forge",   SRC_117_118_FORGE,"forge",    "1.18-1.18.2",   {}),
    ("TpaTeleport-1.18-1.18.2-fabric",  SRC_117_118_FABRIC,"fabric",  "1.18-1.18.2",   {"src/main/resources/fabric.mod.json": FABRIC_MOD_JSON}),
    # --- 1.19-1.19.4 ---
    ("TpaTeleport-1.19-1.19.4-forge",   SRC_119_FORGE,    "forge",    "1.19-1.19.4",   {}),
    ("TpaTeleport-1.19-1.19.4-fabric",  SRC_119_FABRIC,   "fabric",   "1.19-1.19.4",   {"src/main/resources/fabric.mod.json": FABRIC_MOD_JSON}),
    # --- 1.20-1.20.6 fabric + neoforge (forge already published) ---
    ("TpaTeleport-1.20-1.20.6-fabric",  SRC_120_FABRIC,   "fabric",   "1.20-1.20.6",   {"src/main/resources/fabric.mod.json": FABRIC_MOD_JSON}),
    ("TpaTeleport-1.20-1.20.6-neoforge",SRC_120_NEOFORGE, "neoforge", "1.20-1.20.6",   {}),
    # --- 1.21-1.21.1 forge + neoforge (fabric already published) ---
    ("TpaTeleport-1.21-1.21.1-forge",   SRC_121_FORGE,    "forge",    "1.21-1.21.1",   {}),
    ("TpaTeleport-1.21-1.21.1-neoforge",SRC_1205_NEOFORGE,"neoforge", "1.21-1.21.1",   {}),
    # --- 1.21.2-1.21.8 forge + neoforge (fabric already published) ---
    ("TpaTeleport-1.21.2-1.21.8-forge", SRC_121_FORGE,    "forge",    "1.21.2-1.21.8", {}),
    ("TpaTeleport-1.21.2-1.21.8-neoforge",SRC_1212_NEOFORGE,"neoforge","1.21.2-1.21.8",{}),
    # --- 1.21.9-1.21.11 forge + neoforge (fabric already published) ---
    ("TpaTeleport-1.21.9-1.21.11-forge",SRC_1219_FORGE,   "forge",    "1.21.9-1.21.11",{}),
    ("TpaTeleport-1.21.9-1.21.11-neoforge",SRC_1219_NEOFORGE,"neoforge","1.21.9-1.21.11",{}),
    # --- 26.1-26.x ---
    ("TpaTeleport-26.1-26.x-forge",     SRC_261_FORGE,    "forge",    "26.1-26.x",     {}),
    ("TpaTeleport-26.1-26.x-fabric",    SRC_261_FABRIC,   "fabric",   "26.1-26.x",     {"src/main/resources/fabric.mod.json": FABRIC_MOD_JSON}),
    ("TpaTeleport-26.1-26.x-neoforge",  SRC_261_NEOFORGE, "neoforge", "26.1-26.x",     {}),
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

            failed_folders = {t[0] for t in targets if any(t[0].lower() in s.lower() or s.lower() in t[0].lower() for s in failed_slugs)}
            if failed_folders:
                active_targets = [t for t in targets if t[0] in failed_folders]
                print(f"Failed-only: {len(active_targets)} targets")
                for t in active_targets: print(f"  -> {t[0]}")
            else:
                print("No failed targets — all green!")
                active_targets = []

# ============================================================
# GENERATE
# ============================================================
if BUNDLE.exists():
    shutil.rmtree(BUNDLE)

for (folder, src, loader, mc_ver, extra_files) in active_targets:
    base = BUNDLE / folder
    write(base / "mod.txt", mod_txt())
    write(base / "version.txt", version_txt(mc_ver, loader))
    write(base / JAVA_MAIN, src)
    for rel_path, content in extra_files.items():
        write(base / rel_path, content)

print(f"Generated {len(active_targets)} targets in {BUNDLE}")

zip_path = ROOT / "incoming" / "tpa-teleport-all-versions.zip"
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for f in sorted(BUNDLE.rglob("*")):
        if f.is_file():
            zf.write(f, f.relative_to(BUNDLE))
print(f"Created {zip_path} ({zip_path.stat().st_size // 1024} KB)")

