package com.itamio.servercore.forge;

import com.itamio.servercore.forge.TeleportRequestService.RequestType;
import com.itamio.servercore.forge.TeleportRequestService.TeleportRequest;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class ServerCoreCommands {
    private ServerCoreCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> handleTpa(ctx, EntityArgument.getPlayer(ctx, "player"), RequestType.TPA))));

        dispatcher.register(Commands.literal("tpahere")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> handleTpa(ctx, EntityArgument.getPlayer(ctx, "player"), RequestType.TPAHERE))));

        dispatcher.register(Commands.literal("tpaccept")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(ctx -> handleTpaccept(ctx, null))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleTpaccept(ctx, StringArgumentType.getString(ctx, "player")))));

        dispatcher.register(Commands.literal("tpacceptall")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(ServerCoreCommands::handleTpacceptAll));

        dispatcher.register(Commands.literal("tpadeny")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(ctx -> handleTpadeny(ctx, null))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleTpadeny(ctx, StringArgumentType.getString(ctx, "player")))));

        dispatcher.register(Commands.literal("tpadenyall")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(ServerCoreCommands::handleTpadenyAll));

        dispatcher.register(Commands.literal("tpacancel")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleTpacancel(ctx, StringArgumentType.getString(ctx, "player")))));

        dispatcher.register(Commands.literal("sethome")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> handleSetHome(ctx, StringArgumentType.getString(ctx, "name")))));

        dispatcher.register(Commands.literal("home")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("list")
                        .executes(ServerCoreCommands::handleHomeList))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> handleHomeTeleport(ctx, StringArgumentType.getString(ctx, "name")))));

        dispatcher.register(Commands.literal("delhome")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> handleDelHome(ctx, StringArgumentType.getString(ctx, "name")))));

        dispatcher.register(Commands.literal("rtp")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("overworld")
                        .executes(ctx -> handleRtp(ctx, Level.OVERWORLD.location().toString())))
                .then(Commands.literal("nether")
                        .executes(ctx -> handleRtp(ctx, Level.NETHER.location().toString())))
                .then(Commands.literal("end")
                        .executes(ctx -> handleRtp(ctx, Level.END.location().toString()))));
    }

    private static int handleTpa(CommandContext<CommandSourceStack> ctx, ServerPlayer target, RequestType type) {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        if (sender.getUUID().equals(target.getUUID())) {
            MessageUtil.send(sender, "You cannot teleport to yourself.");
            return 0;
        }
        TeleportRequestService.getInstance().upsertRequest(
                sender.getUUID(),
                ServerCoreAccess.getPlayerName(sender),
                target.getUUID(),
                ServerCoreAccess.getPlayerName(target),
                type
        );
        MessageUtil.send(sender, "Teleport request sent to " + ServerCoreAccess.getPlayerName(target) + ".");
        if (type == RequestType.TPA) {
            MessageUtil.send(target, ServerCoreAccess.getPlayerName(sender) + " wants to teleport to you. Use /tpaccept " + ServerCoreAccess.getPlayerName(sender) + " or /tpadeny " + ServerCoreAccess.getPlayerName(sender) + ".");
        } else {
            MessageUtil.send(target, ServerCoreAccess.getPlayerName(sender) + " wants you to teleport to them. Use /tpaccept " + ServerCoreAccess.getPlayerName(sender) + " or /tpadeny " + ServerCoreAccess.getPlayerName(sender) + ".");
        }
        return 1;
    }

    private static int handleTpaccept(CommandContext<CommandSourceStack> ctx, String requesterName) {
        ServerPlayer target = ctx.getSource().getPlayerOrException();
        TeleportRequestService service = TeleportRequestService.getInstance();
        TeleportRequest request;
        if (requesterName == null || requesterName.isEmpty()) {
            request = service.popAllIncoming(target.getUUID()).stream().findFirst().orElse(null);
        } else {
            request = service.popIncoming(target.getUUID(), requesterName);
        }
        if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
        }
        return completeTeleport(target, request);
    }

    private static int handleTpacceptAll(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer target = ctx.getSource().getPlayerOrException();
        int success = 0;
        for (TeleportRequest request : TeleportRequestService.getInstance().popAllIncoming(target.getUUID())) {
            success += completeTeleport(target, request);
        }
        if (success == 0) {
            MessageUtil.send(target, "No pending teleport requests.");
        }
        return success;
    }

    private static int handleTpadeny(CommandContext<CommandSourceStack> ctx, String requesterName) {
        ServerPlayer target = ctx.getSource().getPlayerOrException();
        TeleportRequestService service = TeleportRequestService.getInstance();
        TeleportRequest request;
        if (requesterName == null || requesterName.isEmpty()) {
            request = service.popAllIncoming(target.getUUID()).stream().findFirst().orElse(null);
        } else {
            request = service.popIncoming(target.getUUID(), requesterName);
        }
        if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
        }
        MessageUtil.send(target, "Teleport request denied.");
        return 1;
    }

    private static int handleTpadenyAll(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer target = ctx.getSource().getPlayerOrException();
        int denied = TeleportRequestService.getInstance().popAllIncoming(target.getUUID()).size();
        MessageUtil.send(target, denied == 0 ? "No pending teleport requests." : ("Denied " + denied + " requests."));
        return denied;
    }

    private static int handleTpacancel(CommandContext<CommandSourceStack> ctx, String targetNameOrAll) {
        ServerPlayer requester = ctx.getSource().getPlayerOrException();
        int removed = TeleportRequestService.getInstance().cancelOutgoing(requester.getUUID(), targetNameOrAll);
        MessageUtil.send(requester, removed == 0 ? "No pending outgoing requests." : ("Cancelled " + removed + " request(s)."));
        return removed;
    }

    private static int completeTeleport(ServerPlayer target, TeleportRequest request) {
        MinecraftServer server = ServerCoreAccess.getServer(target);
        if (server == null) {
            MessageUtil.send(target, "Server unavailable.");
            return 0;
        }
        ServerPlayer requester = server.getPlayerList().getPlayer(request.getRequesterUuid());
        if (requester == null) {
            MessageUtil.send(target, "Requester is offline.");
            return 0;
        }
        ServerLevel targetLevel = ServerCoreAccess.getServerLevel(target);
        ServerLevel requesterLevel = ServerCoreAccess.getServerLevel(requester);
        if (targetLevel == null || requesterLevel == null) {
            MessageUtil.send(target, "Target level unavailable.");
            return 0;
        }
        if (request.getType() == RequestType.TPA) {
            TeleportUtil.teleport(requester, targetLevel, target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
            MessageUtil.send(requester, "Teleporting to " + ServerCoreAccess.getPlayerName(target) + ".");
            MessageUtil.send(target, "Accepted teleport request from " + ServerCoreAccess.getPlayerName(requester) + ".");
        } else {
            TeleportUtil.teleport(target, requesterLevel, requester.getX(), requester.getY(), requester.getZ(), requester.getYRot(), requester.getXRot());
            MessageUtil.send(target, "Teleporting to " + ServerCoreAccess.getPlayerName(requester) + ".");
            MessageUtil.send(requester, ServerCoreAccess.getPlayerName(target) + " accepted your request.");
        }
        return 1;
    }

    private static int handleSetHome(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        HomeRecord record = HomeService.getInstance().setHome(ctx.getSource().getServer(), player, name);
        if (record == null) {
            MessageUtil.send(player, "Invalid home name.");
            return 0;
        }
        MessageUtil.send(player, "Home set: " + record.getName());
        return 1;
    }

    private static int handleHomeList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var homes = HomeService.getInstance().listHomes(ctx.getSource().getServer(), player.getUUID());
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

    private static int handleHomeTeleport(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        HomeRecord record = HomeService.getInstance().getHome(ctx.getSource().getServer(), player.getUUID(), name);
        if (record == null) {
            MessageUtil.send(player, "Home not found.");
            return 0;
        }
        ServerLevel level = TeleportUtil.resolveLevel(ctx.getSource().getServer(), record.getDimension());
        if (level == null) {
            MessageUtil.send(player, "Target dimension is not available.");
            return 0;
        }
        TeleportUtil.teleport(player, level, record.getX(), record.getY(), record.getZ(), record.getYaw(), record.getPitch());
        MessageUtil.send(player, "Teleported to home " + record.getName() + ".");
        return 1;
    }

    private static int handleDelHome(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean removed = HomeService.getInstance().deleteHome(ctx.getSource().getServer(), player.getUUID(), name);
        MessageUtil.send(player, removed ? "Home deleted." : "Home not found.");
        return removed ? 1 : 0;
    }

    private static int handleRtp(CommandContext<CommandSourceStack> ctx, String dimensionKey) {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, dimensionKey);
        MessageUtil.send(player, result.getMessage());
        return result.isSuccess() ? 1 : 0;
    }
}
