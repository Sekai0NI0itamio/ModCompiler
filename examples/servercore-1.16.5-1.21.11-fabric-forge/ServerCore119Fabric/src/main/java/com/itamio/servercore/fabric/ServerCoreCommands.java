package com.itamio.servercore.fabric;

import com.itamio.servercore.fabric.TeleportRequestService.RequestType;
import com.itamio.servercore.fabric.TeleportRequestService.TeleportRequest;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class ServerCoreCommands {
    private ServerCoreCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("tpa")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> handleTpa(ctx, EntityArgumentType.getPlayer(ctx, "player"), RequestType.TPA))));

        dispatcher.register(CommandManager.literal("tpahere")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> handleTpa(ctx, EntityArgumentType.getPlayer(ctx, "player"), RequestType.TPAHERE))));

        dispatcher.register(CommandManager.literal("tpaccept")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .executes(ctx -> handleTpaccept(ctx, null))
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleTpaccept(ctx, StringArgumentType.getString(ctx, "player")))));

        dispatcher.register(CommandManager.literal("tpacceptall")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .executes(ServerCoreCommands::handleTpacceptAll));

        dispatcher.register(CommandManager.literal("tpadeny")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .executes(ctx -> handleTpadeny(ctx, null))
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleTpadeny(ctx, StringArgumentType.getString(ctx, "player")))));

        dispatcher.register(CommandManager.literal("tpadenyall")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .executes(ServerCoreCommands::handleTpadenyAll));

        dispatcher.register(CommandManager.literal("tpacancel")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleTpacancel(ctx, StringArgumentType.getString(ctx, "player")))));

        dispatcher.register(CommandManager.literal("sethome")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> handleSetHome(ctx, StringArgumentType.getString(ctx, "name")))));

        dispatcher.register(CommandManager.literal("home")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(CommandManager.literal("list")
                        .executes(ServerCoreCommands::handleHomeList))
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> handleHomeTeleport(ctx, StringArgumentType.getString(ctx, "name")))));

        dispatcher.register(CommandManager.literal("delhome")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> handleDelHome(ctx, StringArgumentType.getString(ctx, "name")))));

        dispatcher.register(CommandManager.literal("rtp")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(CommandManager.literal("overworld")
                        .executes(ctx -> handleRtp(ctx, World.OVERWORLD.getValue().toString())))
                .then(CommandManager.literal("nether")
                        .executes(ctx -> handleRtp(ctx, World.NETHER.getValue().toString())))
                .then(CommandManager.literal("end")
                        .executes(ctx -> handleRtp(ctx, World.END.getValue().toString()))));
    }

    private static int handleTpa(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target, RequestType type) {
        ServerPlayerEntity sender = requirePlayer(ctx);
        if (sender == null) {
            return 0;
        }
        if (sender.getUuid().equals(target.getUuid())) {
            MessageUtil.send(sender, "You cannot teleport to yourself.");
            return 0;
        }
        TeleportRequestService.getInstance().upsertRequest(
                sender.getUuid(),
                sender.getGameProfile().getName(),
                target.getUuid(),
                target.getGameProfile().getName(),
                type
        );
        MessageUtil.send(sender, "Teleport request sent to " + target.getGameProfile().getName() + ".");
        if (type == RequestType.TPA) {
            MessageUtil.send(target, sender.getGameProfile().getName() + " wants to teleport to you. Use /tpaccept " + sender.getGameProfile().getName() + " or /tpadeny " + sender.getGameProfile().getName() + ".");
        } else {
            MessageUtil.send(target, sender.getGameProfile().getName() + " wants you to teleport to them. Use /tpaccept " + sender.getGameProfile().getName() + " or /tpadeny " + sender.getGameProfile().getName() + ".");
        }
        return 1;
    }

    private static int handleTpaccept(CommandContext<ServerCommandSource> ctx, String requesterName) {
        ServerPlayerEntity target = requirePlayer(ctx);
        if (target == null) {
            return 0;
        }
        TeleportRequestService service = TeleportRequestService.getInstance();
        TeleportRequest request;
        if (requesterName == null || requesterName.isEmpty()) {
            request = service.popAllIncoming(target.getUuid()).stream().findFirst().orElse(null);
        } else {
            request = service.popIncoming(target.getUuid(), requesterName);
        }
        if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
        }
        return completeTeleport(target, request);
    }

    private static int handleTpacceptAll(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity target = requirePlayer(ctx);
        if (target == null) {
            return 0;
        }
        int success = 0;
        for (TeleportRequest request : TeleportRequestService.getInstance().popAllIncoming(target.getUuid())) {
            success += completeTeleport(target, request);
        }
        if (success == 0) {
            MessageUtil.send(target, "No pending teleport requests.");
        }
        return success;
    }

    private static int handleTpadeny(CommandContext<ServerCommandSource> ctx, String requesterName) {
        ServerPlayerEntity target = requirePlayer(ctx);
        if (target == null) {
            return 0;
        }
        TeleportRequestService service = TeleportRequestService.getInstance();
        TeleportRequest request;
        if (requesterName == null || requesterName.isEmpty()) {
            request = service.popAllIncoming(target.getUuid()).stream().findFirst().orElse(null);
        } else {
            request = service.popIncoming(target.getUuid(), requesterName);
        }
        if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
        }
        MessageUtil.send(target, "Teleport request denied.");
        return 1;
    }

    private static int handleTpadenyAll(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity target = requirePlayer(ctx);
        if (target == null) {
            return 0;
        }
        int denied = TeleportRequestService.getInstance().popAllIncoming(target.getUuid()).size();
        MessageUtil.send(target, denied == 0 ? "No pending teleport requests." : ("Denied " + denied + " requests."));
        return denied;
    }

    private static int handleTpacancel(CommandContext<ServerCommandSource> ctx, String targetNameOrAll) {
        ServerPlayerEntity requester = requirePlayer(ctx);
        if (requester == null) {
            return 0;
        }
        int removed = TeleportRequestService.getInstance().cancelOutgoing(requester.getUuid(), targetNameOrAll);
        MessageUtil.send(requester, removed == 0 ? "No pending outgoing requests." : ("Cancelled " + removed + " request(s)."));
        return removed;
    }

    private static int completeTeleport(ServerPlayerEntity target, TeleportRequest request) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            MessageUtil.send(target, "Server unavailable.");
            return 0;
        }
        ServerPlayerEntity requester = server.getPlayerManager().getPlayer(request.getRequesterUuid());
        if (requester == null) {
            MessageUtil.send(target, "Requester is offline.");
            return 0;
        }
        ServerWorld targetWorld = TeleportUtil.getServerWorld(target);
        ServerWorld requesterWorld = TeleportUtil.getServerWorld(requester);
        if (targetWorld == null || requesterWorld == null) {
            MessageUtil.send(target, "Target world is unavailable.");
            return 0;
        }
        if (request.getType() == RequestType.TPA) {
            TeleportUtil.teleport(requester, targetWorld, target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
            MessageUtil.send(requester, "Teleporting to " + target.getGameProfile().getName() + ".");
            MessageUtil.send(target, "Accepted teleport request from " + requester.getGameProfile().getName() + ".");
        } else {
            TeleportUtil.teleport(target, requesterWorld, requester.getX(), requester.getY(), requester.getZ(), requester.getYaw(), requester.getPitch());
            MessageUtil.send(target, "Teleporting to " + requester.getGameProfile().getName() + ".");
            MessageUtil.send(requester, target.getGameProfile().getName() + " accepted your request.");
        }
        return 1;
    }

    private static int handleSetHome(CommandContext<ServerCommandSource> ctx, String name) {
        ServerPlayerEntity player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        HomeRecord record = HomeService.getInstance().setHome(ctx.getSource().getServer(), player, name);
        if (record == null) {
            MessageUtil.send(player, "Invalid home name.");
            return 0;
        }
        MessageUtil.send(player, "Home set: " + record.getName());
        return 1;
    }

    private static int handleHomeList(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        var homes = HomeService.getInstance().listHomes(ctx.getSource().getServer(), player.getUuid());
        if (homes.isEmpty()) {
            MessageUtil.send(player, "You have no homes.");
            return 0;
        }
        StringBuilder builder = new StringBuilder("Homes: ");
        for (int i = 0; i < homes.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(homes.get(i).getName());
        }
        MessageUtil.send(player, builder.toString());
        return homes.size();
    }

    private static int handleHomeTeleport(CommandContext<ServerCommandSource> ctx, String name) {
        ServerPlayerEntity player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        HomeRecord record = HomeService.getInstance().getHome(ctx.getSource().getServer(), player.getUuid(), name);
        if (record == null) {
            MessageUtil.send(player, "Home not found.");
            return 0;
        }
        ServerWorld world = TeleportUtil.resolveWorld(ctx.getSource().getServer(), record.getDimension());
        if (world == null) {
            MessageUtil.send(player, "Target dimension is not available.");
            return 0;
        }
        TeleportUtil.teleport(player, world, record.getX(), record.getY(), record.getZ(), record.getYaw(), record.getPitch());
        MessageUtil.send(player, "Teleported to home " + record.getName() + ".");
        return 1;
    }

    private static int handleDelHome(CommandContext<ServerCommandSource> ctx, String name) {
        ServerPlayerEntity player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        boolean removed = HomeService.getInstance().deleteHome(ctx.getSource().getServer(), player.getUuid(), name);
        MessageUtil.send(player, removed ? "Home deleted." : "Home not found.");
        return removed ? 1 : 0;
    }

    private static int handleRtp(CommandContext<ServerCommandSource> ctx, String dimensionKey) {
        ServerPlayerEntity player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, dimensionKey);
        MessageUtil.send(player, result.getMessage());
        return result.isSuccess() ? 1 : 0;
    }

    private static ServerPlayerEntity requirePlayer(CommandContext<ServerCommandSource> ctx) {
        try {
            return ctx.getSource().getPlayer();
        } catch (CommandSyntaxException ignored) {
            return null;
        }
    }
}
