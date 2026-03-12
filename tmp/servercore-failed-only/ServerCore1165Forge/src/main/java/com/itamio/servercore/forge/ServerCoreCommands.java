package com.itamio.servercore.forge;

import com.itamio.servercore.forge.TeleportRequestService.RequestType;
import com.itamio.servercore.forge.TeleportRequestService.TeleportRequest;
import java.lang.reflect.Method;
import java.util.List;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
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
                        .executes(ctx -> handleRtp(ctx, "minecraft:overworld")))
                .then(Commands.literal("nether")
                        .executes(ctx -> handleRtp(ctx, "minecraft:the_nether")))
                .then(Commands.literal("end")
                        .executes(ctx -> handleRtp(ctx, "minecraft:the_end"))));
    }

    private static int handleTpa(CommandContext<CommandSource> ctx, ServerPlayerEntity target, RequestType type) {
        ServerPlayerEntity sender = requirePlayer(ctx);
        if (sender == null) {
            return 0;
        }
        if (sender == target) {
            MessageUtil.send(sender, "You cannot teleport to yourself.");
            return 0;
        }
        java.util.UUID senderUuid = PlayerUtil.getUuid(sender);
        java.util.UUID targetUuid = PlayerUtil.getUuid(target);
        if (senderUuid == null || targetUuid == null) {
            MessageUtil.send(sender, "Player data unavailable.");
            return 0;
        }
        if (senderUuid.equals(targetUuid)) {
            MessageUtil.send(sender, "You cannot teleport to yourself.");
            return 0;
        }
        TeleportRequestService.getInstance().upsertRequest(
                senderUuid,
                sender.getGameProfile().getName(),
                targetUuid,
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
        ServerPlayerEntity target = requirePlayer(ctx);
        if (target == null) {
            return 0;
        }
        TeleportRequestService service = TeleportRequestService.getInstance();
        TeleportRequest request;
        if (requesterName == null || requesterName.isEmpty()) {
            request = service.popAllIncoming(PlayerUtil.getUuid(target)).stream().findFirst().orElse(null);
        } else {
            request = service.popIncoming(PlayerUtil.getUuid(target), requesterName);
        }
        if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
        }
        return completeTeleport(target, request);
    }

    private static int handleTpacceptAll(CommandContext<CommandSource> ctx) {
        ServerPlayerEntity target = requirePlayer(ctx);
        if (target == null) {
            return 0;
        }
        int success = 0;
        for (TeleportRequest request : TeleportRequestService.getInstance().popAllIncoming(PlayerUtil.getUuid(target))) {
            success += completeTeleport(target, request);
        }
        if (success == 0) {
            MessageUtil.send(target, "No pending teleport requests.");
        }
        return success;
    }

    private static int handleTpadeny(CommandContext<CommandSource> ctx, String requesterName) {
        ServerPlayerEntity target = requirePlayer(ctx);
        if (target == null) {
            return 0;
        }
        TeleportRequestService service = TeleportRequestService.getInstance();
        TeleportRequest request;
        if (requesterName == null || requesterName.isEmpty()) {
            request = service.popAllIncoming(PlayerUtil.getUuid(target)).stream().findFirst().orElse(null);
        } else {
            request = service.popIncoming(PlayerUtil.getUuid(target), requesterName);
        }
        if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
        }
        MessageUtil.send(target, "Teleport request denied.");
        return 1;
    }

    private static int handleTpadenyAll(CommandContext<CommandSource> ctx) {
        ServerPlayerEntity target = requirePlayer(ctx);
        if (target == null) {
            return 0;
        }
        int denied = TeleportRequestService.getInstance().popAllIncoming(PlayerUtil.getUuid(target)).size();
        MessageUtil.send(target, denied == 0 ? "No pending teleport requests." : ("Denied " + denied + " requests."));
        return denied;
    }

    private static int handleTpacancel(CommandContext<CommandSource> ctx, String targetNameOrAll) {
        ServerPlayerEntity requester = requirePlayer(ctx);
        if (requester == null) {
            return 0;
        }
        int removed = TeleportRequestService.getInstance().cancelOutgoing(PlayerUtil.getUuid(requester), targetNameOrAll);
        MessageUtil.send(requester, removed == 0 ? "No pending outgoing requests." : ("Cancelled " + removed + " request(s)."));
        return removed;
    }

    private static int completeTeleport(ServerPlayerEntity target, TeleportRequest request) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            MessageUtil.send(target, "Server unavailable.");
            return 0;
        }
        ServerPlayerEntity requester = PlayerUtil.getPlayerByUuid(server, request.getRequesterUuid());
        if (requester == null) {
            MessageUtil.send(target, "Requester is offline.");
            return 0;
        }
        if (request.getType() == RequestType.TPA) {
            TeleportUtil.teleport(requester, PlayerUtil.getServerWorld(target), PlayerUtil.getX(target), PlayerUtil.getY(target), PlayerUtil.getZ(target), RotationUtil.getYaw(target), RotationUtil.getPitch(target));
            MessageUtil.send(requester, "Teleporting to " + target.getGameProfile().getName() + ".");
            MessageUtil.send(target, "Accepted teleport request from " + requester.getGameProfile().getName() + ".");
        } else {
            TeleportUtil.teleport(target, PlayerUtil.getServerWorld(requester), PlayerUtil.getX(requester), PlayerUtil.getY(requester), PlayerUtil.getZ(requester), RotationUtil.getYaw(requester), RotationUtil.getPitch(requester));
            MessageUtil.send(target, "Teleporting to " + requester.getGameProfile().getName() + ".");
            MessageUtil.send(requester, target.getGameProfile().getName() + " accepted your request.");
        }
        return 1;
    }

    private static int handleSetHome(CommandContext<CommandSource> ctx, String name) {
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

    private static int handleHomeList(CommandContext<CommandSource> ctx) {
        ServerPlayerEntity player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        List<HomeRecord> homes = HomeService.getInstance().listHomes(ctx.getSource().getServer(), PlayerUtil.getUuid(player));
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
        ServerPlayerEntity player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        HomeRecord record = HomeService.getInstance().getHome(ctx.getSource().getServer(), PlayerUtil.getUuid(player), name);
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
        ServerPlayerEntity player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        boolean removed = HomeService.getInstance().deleteHome(ctx.getSource().getServer(), PlayerUtil.getUuid(player), name);
        MessageUtil.send(player, removed ? "Home deleted." : "Home not found.");
        return removed ? 1 : 0;
    }

    private static int handleRtp(CommandContext<CommandSource> ctx, String dimensionKey) {
        ServerPlayerEntity player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, dimensionKey);
        MessageUtil.send(player, result.getMessage());
        return result.isSuccess() ? 1 : 0;
    }

    private static ServerPlayerEntity requirePlayer(CommandContext<CommandSource> ctx) {
        if (ctx == null || ctx.getSource() == null) {
            return null;
        }
        CommandSource source = ctx.getSource();
        if (source.getEntity() instanceof ServerPlayerEntity) {
            return (ServerPlayerEntity) source.getEntity();
        }
        ServerPlayerEntity player = invokePlayer(source, "getPlayerOrException");
        if (player != null) {
            return player;
        }
        return invokePlayer(source, "asPlayer");
    }

    private static ServerPlayerEntity invokePlayer(CommandSource source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            Object result = method.invoke(source);
            return result instanceof ServerPlayerEntity ? (ServerPlayerEntity) result : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
