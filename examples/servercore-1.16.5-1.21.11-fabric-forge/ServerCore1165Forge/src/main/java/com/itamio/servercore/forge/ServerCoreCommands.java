package com.itamio.servercore.forge;

import com.itamio.servercore.forge.TeleportRequestService.RequestType;
import com.itamio.servercore.forge.TeleportRequestService.TeleportRequest;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public final class ServerCoreCommands {
    private ServerCoreCommands() {
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> handleTpa(ctx, EntityArgument.getPlayer(ctx, "player"), RequestType.TPA))));

        dispatcher.register(Commands.literal("tpahere")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> handleTpa(ctx, EntityArgument.getPlayer(ctx, "player"), RequestType.TPAHERE))));

        dispatcher.register(Commands.literal("tpaccept")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .executes(ctx -> handleTpaccept(ctx, null))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleTpaccept(ctx, StringArgumentType.getString(ctx, "player")))));

        dispatcher.register(Commands.literal("tpacceptall")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .executes(ServerCoreCommands::handleTpacceptAll));

        dispatcher.register(Commands.literal("tpadeny")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .executes(ctx -> handleTpadeny(ctx, null))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleTpadeny(ctx, StringArgumentType.getString(ctx, "player")))));

        dispatcher.register(Commands.literal("tpadenyall")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .executes(ServerCoreCommands::handleTpadenyAll));

        dispatcher.register(Commands.literal("tpacancel")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> handleTpacancel(ctx, StringArgumentType.getString(ctx, "player")))));

        dispatcher.register(Commands.literal("sethome")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> handleSetHome(ctx, StringArgumentType.getString(ctx, "name")))));

        dispatcher.register(Commands.literal("home")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(Commands.literal("list")
                        .executes(ServerCoreCommands::handleHomeList))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> handleHomeTeleport(ctx, StringArgumentType.getString(ctx, "name")))));

        dispatcher.register(Commands.literal("delhome")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> handleDelHome(ctx, StringArgumentType.getString(ctx, "name")))));

        dispatcher.register(Commands.literal("rtp")
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                .then(Commands.literal("overworld")
                        .executes(ctx -> handleRtp(ctx, World.OVERWORLD.getLocation().toString())))
                .then(Commands.literal("nether")
                        .executes(ctx -> handleRtp(ctx, World.THE_NETHER.getLocation().toString())))
                .then(Commands.literal("end")
                        .executes(ctx -> handleRtp(ctx, World.THE_END.getLocation().toString()))));
    }

    private static int handleTpa(CommandContext<CommandSource> ctx, ServerPlayerEntity target, RequestType type) {
        ServerPlayerEntity sender = ctx.getSource().asPlayer();
        if (sender.getUniqueID().equals(target.getUniqueID())) {
            MessageUtil.send(sender, "You cannot teleport to yourself.");
            return 0;
        }
        TeleportRequestService.getInstance().upsertRequest(
                sender.getUniqueID(),
                sender.getGameProfile().getName(),
                target.getUniqueID(),
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

    private static int handleTpaccept(CommandContext<CommandSource> ctx, String requesterName) {
        ServerPlayerEntity target = ctx.getSource().asPlayer();
        TeleportRequestService service = TeleportRequestService.getInstance();
        TeleportRequest request;
        if (requesterName == null || requesterName.isEmpty()) {
            request = service.popAllIncoming(target.getUniqueID()).stream().findFirst().orElse(null);
        } else {
            request = service.popIncoming(target.getUniqueID(), requesterName);
        }
        if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
        }
        return completeTeleport(target, request);
    }

    private static int handleTpacceptAll(CommandContext<CommandSource> ctx) {
        ServerPlayerEntity target = ctx.getSource().asPlayer();
        int success = 0;
        for (TeleportRequest request : TeleportRequestService.getInstance().popAllIncoming(target.getUniqueID())) {
            success += completeTeleport(target, request);
        }
        if (success == 0) {
            MessageUtil.send(target, "No pending teleport requests.");
        }
        return success;
    }

    private static int handleTpadeny(CommandContext<CommandSource> ctx, String requesterName) {
        ServerPlayerEntity target = ctx.getSource().asPlayer();
        TeleportRequestService service = TeleportRequestService.getInstance();
        TeleportRequest request;
        if (requesterName == null || requesterName.isEmpty()) {
            request = service.popAllIncoming(target.getUniqueID()).stream().findFirst().orElse(null);
        } else {
            request = service.popIncoming(target.getUniqueID(), requesterName);
        }
        if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
        }
        MessageUtil.send(target, "Teleport request denied.");
        return 1;
    }

    private static int handleTpadenyAll(CommandContext<CommandSource> ctx) {
        ServerPlayerEntity target = ctx.getSource().asPlayer();
        int denied = TeleportRequestService.getInstance().popAllIncoming(target.getUniqueID()).size();
        MessageUtil.send(target, denied == 0 ? "No pending teleport requests." : ("Denied " + denied + " requests."));
        return denied;
    }

    private static int handleTpacancel(CommandContext<CommandSource> ctx, String targetNameOrAll) {
        ServerPlayerEntity requester = ctx.getSource().asPlayer();
        int removed = TeleportRequestService.getInstance().cancelOutgoing(requester.getUniqueID(), targetNameOrAll);
        MessageUtil.send(requester, removed == 0 ? "No pending outgoing requests." : ("Cancelled " + removed + " request(s)."));
        return removed;
    }

    private static int completeTeleport(ServerPlayerEntity target, TeleportRequest request) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            MessageUtil.send(target, "Server unavailable.");
            return 0;
        }
        ServerPlayerEntity requester = server.getPlayerList().getPlayerByUUID(request.getRequesterUuid());
        if (requester == null) {
            MessageUtil.send(target, "Requester is offline.");
            return 0;
        }
        if (request.getType() == RequestType.TPA) {
            TeleportUtil.teleport(requester, target.getServerWorld(), target.getPosX(), target.getPosY(), target.getPosZ(), RotationUtil.getYaw(target), RotationUtil.getPitch(target));
            MessageUtil.send(requester, "Teleporting to " + target.getGameProfile().getName() + ".");
            MessageUtil.send(target, "Accepted teleport request from " + requester.getGameProfile().getName() + ".");
        } else {
            TeleportUtil.teleport(target, requester.getServerWorld(), requester.getPosX(), requester.getPosY(), requester.getPosZ(), RotationUtil.getYaw(requester), RotationUtil.getPitch(requester));
            MessageUtil.send(target, "Teleporting to " + requester.getGameProfile().getName() + ".");
            MessageUtil.send(requester, target.getGameProfile().getName() + " accepted your request.");
        }
        return 1;
    }

    private static int handleSetHome(CommandContext<CommandSource> ctx, String name) {
        ServerPlayerEntity player = ctx.getSource().asPlayer();
        HomeRecord record = HomeService.getInstance().setHome(ctx.getSource().getServer(), player, name);
        if (record == null) {
            MessageUtil.send(player, "Invalid home name.");
            return 0;
        }
        MessageUtil.send(player, "Home set: " + record.getName());
        return 1;
    }

    private static int handleHomeList(CommandContext<CommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().asPlayer();
        var homes = HomeService.getInstance().listHomes(ctx.getSource().getServer(), player.getUniqueID());
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

    private static int handleHomeTeleport(CommandContext<CommandSource> ctx, String name) {
        ServerPlayerEntity player = ctx.getSource().asPlayer();
        HomeRecord record = HomeService.getInstance().getHome(ctx.getSource().getServer(), player.getUniqueID(), name);
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

    private static int handleDelHome(CommandContext<CommandSource> ctx, String name) {
        ServerPlayerEntity player = ctx.getSource().asPlayer();
        boolean removed = HomeService.getInstance().deleteHome(ctx.getSource().getServer(), player.getUniqueID(), name);
        MessageUtil.send(player, removed ? "Home deleted." : "Home not found.");
        return removed ? 1 : 0;
    }

    private static int handleRtp(CommandContext<CommandSource> ctx, String dimensionKey) {
        ServerPlayerEntity player = ctx.getSource().asPlayer();
        RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, dimensionKey);
        MessageUtil.send(player, result.getMessage());
        return result.isSuccess() ? 1 : 0;
    }
}
