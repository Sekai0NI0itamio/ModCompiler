package net.itamio.tpateleport;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class TpaTeleportMod implements ModInitializer {
    public static final String MOD_ID = "tpateleport";
    private static final long REQUEST_TTL_MS = 60_000L;
    private static final Map<UUID, Map<UUID, TeleportRequest>> REQUESTS_BY_TARGET = new HashMap<>();
    private static int tickCounter = 0;

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (context, builder) ->
        SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> PENDING_FOR_TARGET = (context, builder) -> {
        ServerPlayer target = context.getSource().getPlayer();
        if (target == null) {
            return builder.buildFuture();
        }

        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUUID(), now);
        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUUID());
        if (requests == null || requests.isEmpty()) {
            return builder.buildFuture();
        }

        List<String> names = new ArrayList<>();
        for (TeleportRequest request : requests.values()) {
            names.add(request.senderName);
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> PENDING_FOR_SENDER = (context, builder) -> {
        ServerPlayer sender = context.getSource().getPlayer();
        if (sender == null) {
            return builder.buildFuture();
        }

        long now = System.currentTimeMillis();
        purgeExpired(now);
        List<String> names = new ArrayList<>();
        for (Map<UUID, TeleportRequest> requests : REQUESTS_BY_TARGET.values()) {
            TeleportRequest request = requests.get(sender.getUUID());
            if (request != null && !request.isExpired(now)) {
                names.add(request.targetName);
            }
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                purgeExpired(System.currentTimeMillis());
            }
        });
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("tpa")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(
                    Commands.argument("player", StringArgumentType.word())
                        .suggests(ONLINE_PLAYERS)
                        .executes(context -> sendRequest(context, StringArgumentType.getString(context, "player"), RequestType.TO_TARGET))
                )
        );

        dispatcher.register(
            Commands.literal("tpahere")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(
                    Commands.argument("player", StringArgumentType.word())
                        .suggests(ONLINE_PLAYERS)
                        .executes(context -> sendRequest(context, StringArgumentType.getString(context, "player"), RequestType.HERE))
                )
        );

        dispatcher.register(
            Commands.literal("tpacancel")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("all").executes(TpaTeleportMod::cancelAll))
                .then(
                    Commands.argument("player", StringArgumentType.word())
                        .suggests(PENDING_FOR_SENDER)
                        .executes(context -> cancelRequest(context, StringArgumentType.getString(context, "player")))
                )
        );

        dispatcher.register(
            Commands.literal("tpacceptall")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(TpaTeleportMod::acceptAll)
        );

        dispatcher.register(
            Commands.literal("tpaccept")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(
                    Commands.argument("player", StringArgumentType.word())
                        .suggests(PENDING_FOR_TARGET)
                        .executes(context -> acceptRequest(context, StringArgumentType.getString(context, "player")))
                )
        );

        dispatcher.register(
            Commands.literal("tpadeny")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(
                    Commands.argument("player", StringArgumentType.word())
                        .suggests(PENDING_FOR_TARGET)
                        .executes(context -> denyRequest(context, StringArgumentType.getString(context, "player")))
                )
        );

        dispatcher.register(
            Commands.literal("tpadenyall")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(TpaTeleportMod::denyAll)
        );
    }

    private static int sendRequest(CommandContext<CommandSourceStack> context, String targetName, RequestType type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        ServerPlayer target = findOnlinePlayer(server, targetName);
        if (target == null) {
            sendMessage(sender, "Player '" + targetName + "' not found.");
            return 0;
        }
        if (target.getUUID().equals(sender.getUUID())) {
            sendMessage(sender, "You cannot send a request to yourself.");
            return 0;
        }

        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUUID(), now);
        Map<UUID, TeleportRequest> requests = getTargetMap(target.getUUID());
        TeleportRequest existing = requests.get(sender.getUUID());
        if (existing != null && !existing.isExpired(now)) {
            sendMessage(sender, "You already have a pending request to " + playerName(target) + ".");
            return 0;
        }

        TeleportRequest request = new TeleportRequest(
            sender.getUUID(),
            playerName(sender),
            target.getUUID(),
            playerName(target),
            type,
            now + REQUEST_TTL_MS
        );
        requests.put(sender.getUUID(), request);

        if (type == RequestType.TO_TARGET) {
            sendMessage(sender, "Teleport request sent to " + playerName(target) + ".");
            sendMessage(target, playerName(sender) + " wants to teleport to you. Use /tpaccept " + playerName(sender) + " or /tpadeny " + playerName(sender) + ".");
        } else {
            sendMessage(sender, "Teleport-here request sent to " + playerName(target) + ".");
            sendMessage(target, playerName(sender) + " wants you to teleport to them. Use /tpaccept " + playerName(sender) + " or /tpadeny " + playerName(sender) + ".");
        }

        return 1;
    }

    private static int cancelAll(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        long now = System.currentTimeMillis();
        purgeExpired(now);

        int removed = 0;
        Iterator<Map.Entry<UUID, Map<UUID, TeleportRequest>>> iterator = REQUESTS_BY_TARGET.entrySet().iterator();
        while (iterator.hasNext()) {
            Map<UUID, TeleportRequest> requests = iterator.next().getValue();
            TeleportRequest request = requests.remove(sender.getUUID());
            if (request != null) {
                removed++;
                notifyTargetOfCancel(context.getSource().getServer(), request);
            }
            if (requests.isEmpty()) {
                iterator.remove();
            }
        }

        if (removed == 0) {
            sendMessage(sender, "You have no pending requests to cancel.");
            return 0;
        }

        sendMessage(sender, "Canceled " + removed + " request(s).");
        return removed;
    }

    private static int cancelRequest(CommandContext<CommandSourceStack> context, String targetName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        long now = System.currentTimeMillis();
        purgeExpired(now);

        ServerPlayer target = findOnlinePlayer(server, targetName);
        if (target != null) {
            Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUUID());
            if (requests != null) {
                TeleportRequest request = requests.remove(sender.getUUID());
                if (request != null) {
                    if (requests.isEmpty()) {
                        REQUESTS_BY_TARGET.remove(target.getUUID());
                    }
                    sendMessage(sender, "Canceled request to " + playerName(target) + ".");
                    notifyTargetOfCancel(server, request);
                    return 1;
                }
            }
        }

        TeleportRequest request = removeRequestByTargetName(sender.getUUID(), targetName);
        if (request == null) {
            sendMessage(sender, "No pending request to " + targetName + ".");
            return 0;
        }

        sendMessage(sender, "Canceled request to " + request.targetName + ".");
        notifyTargetOfCancel(server, request);
        return 1;
    }

    private static int acceptAll(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUUID(), now);

        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUUID());
        if (requests == null || requests.isEmpty()) {
            sendMessage(target, "No pending requests.");
            return 0;
        }

        List<TeleportRequest> snapshot = new ArrayList<>(requests.values());
        requests.clear();
        REQUESTS_BY_TARGET.remove(target.getUUID());

        int accepted = 0;
        for (TeleportRequest request : snapshot) {
            if (request.isExpired(now)) {
                continue;
            }

            ServerPlayer sender = findOnlinePlayerByUuid(server, request.senderId);
            if (sender != null && executeTeleport(request, sender, target)) {
                accepted++;
            }
        }

        if (accepted == 0) {
            sendMessage(target, "No valid requests to accept.");
            return 0;
        }

        sendMessage(target, "Accepted " + accepted + " request(s).");
        return accepted;
    }

    private static int acceptRequest(CommandContext<CommandSourceStack> context, String senderName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUUID(), now);

        ServerPlayer sender = findOnlinePlayer(server, senderName);
        if (sender == null) {
            TeleportRequest removed = removeRequestBySenderName(target.getUUID(), senderName);
            if (removed != null) {
                sendMessage(target, "Request from " + senderName + " expired or player is offline.");
                return 0;
            }
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUUID());
        if (requests == null) {
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        TeleportRequest request = requests.remove(sender.getUUID());
        if (request == null || request.isExpired(now)) {
            if (requests.isEmpty()) {
                REQUESTS_BY_TARGET.remove(target.getUUID());
            }
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        if (requests.isEmpty()) {
            REQUESTS_BY_TARGET.remove(target.getUUID());
        }

        return executeTeleport(request, sender, target) ? 1 : 0;
    }

    private static int denyAll(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUUID(), now);

        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.remove(target.getUUID());
        if (requests == null || requests.isEmpty()) {
            sendMessage(target, "No pending requests.");
            return 0;
        }

        for (TeleportRequest request : requests.values()) {
            notifySenderOfDeny(server, request);
        }
        sendMessage(target, "Denied " + requests.size() + " request(s).");
        return requests.size();
    }

    private static int denyRequest(CommandContext<CommandSourceStack> context, String senderName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUUID(), now);

        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUUID());
        if (requests == null || requests.isEmpty()) {
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        TeleportRequest request = null;
        ServerPlayer sender = findOnlinePlayer(server, senderName);
        if (sender != null) {
            request = requests.remove(sender.getUUID());
        }
        if (request == null) {
            request = removeRequestBySenderName(target.getUUID(), senderName);
        }
        if (request == null || request.isExpired(now)) {
            if (requests.isEmpty()) {
                REQUESTS_BY_TARGET.remove(target.getUUID());
            }
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        if (requests.isEmpty()) {
            REQUESTS_BY_TARGET.remove(target.getUUID());
        }

        notifySenderOfDeny(server, request);
        sendMessage(target, "Denied request from " + request.senderName + ".");
        return 1;
    }

    private static boolean executeTeleport(TeleportRequest request, ServerPlayer sender, ServerPlayer target) {
        if (request.type == RequestType.TO_TARGET) {
            if (!teleportPlayer(sender, target)) {
                sendMessage(target, "Could not teleport " + playerName(sender) + ".");
                sendMessage(sender, "Teleport failed.");
                return false;
            }
            sendMessage(sender, "Teleported to " + playerName(target) + ".");
            sendMessage(target, "Accepted request from " + playerName(sender) + ".");
            return true;
        }

        if (!teleportPlayer(target, sender)) {
            sendMessage(target, "Teleport failed.");
            sendMessage(sender, "Could not teleport " + playerName(target) + ".");
            return false;
        }
        sendMessage(target, "Teleported to " + playerName(sender) + ".");
        sendMessage(sender, "Accepted request from " + playerName(target) + ".");
        return true;
    }

    private static boolean teleportPlayer(ServerPlayer player, ServerPlayer destination) {
        player.teleportTo(
            (net.minecraft.server.level.ServerLevel) destination.level(),
            destination.getX(),
            destination.getY(),
            destination.getZ(),
            java.util.Set.of(),
            destination.getYRot(),
            destination.getXRot(),
            false
        );
        return true;
    }

    private static Map<UUID, TeleportRequest> getTargetMap(UUID targetId) {
        return REQUESTS_BY_TARGET.computeIfAbsent(targetId, ignored -> new HashMap<>());
    }

    private static void purgeExpired(long now) {
        Iterator<Map.Entry<UUID, Map<UUID, TeleportRequest>>> iterator = REQUESTS_BY_TARGET.entrySet().iterator();
        while (iterator.hasNext()) {
            Map<UUID, TeleportRequest> requests = iterator.next().getValue();
            requests.values().removeIf(request -> request.isExpired(now));
            if (requests.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private static void purgeExpiredForTarget(UUID targetId, long now) {
        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(targetId);
        if (requests == null) {
            return;
        }
        requests.values().removeIf(request -> request.isExpired(now));
        if (requests.isEmpty()) {
            REQUESTS_BY_TARGET.remove(targetId);
        }
    }

    private static TeleportRequest removeRequestByTargetName(UUID senderId, String targetName) {
        Iterator<Map.Entry<UUID, Map<UUID, TeleportRequest>>> iterator = REQUESTS_BY_TARGET.entrySet().iterator();
        while (iterator.hasNext()) {
            Map<UUID, TeleportRequest> requests = iterator.next().getValue();
            TeleportRequest request = requests.get(senderId);
            if (request != null && request.targetName.equalsIgnoreCase(targetName)) {
                requests.remove(senderId);
                if (requests.isEmpty()) {
                    iterator.remove();
                }
                return request;
            }
        }
        return null;
    }

    private static TeleportRequest removeRequestBySenderName(UUID targetId, String senderName) {
        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(targetId);
        if (requests == null) {
            return null;
        }

        Iterator<Map.Entry<UUID, TeleportRequest>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            TeleportRequest request = iterator.next().getValue();
            if (request.senderName.equalsIgnoreCase(senderName)) {
                iterator.remove();
                if (requests.isEmpty()) {
                    REQUESTS_BY_TARGET.remove(targetId);
                }
                return request;
            }
        }

        return null;
    }

    private static ServerPlayer findOnlinePlayer(MinecraftServer server, String targetName) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (playerName(player).equalsIgnoreCase(targetName)) {
                return player;
            }
        }
        return null;
    }

    private static ServerPlayer findOnlinePlayerByUuid(MinecraftServer server, UUID playerId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(playerId)) {
                return player;
            }
        }
        return null;
    }

    private static void notifyTargetOfCancel(MinecraftServer server, TeleportRequest request) {
        ServerPlayer target = findOnlinePlayerByUuid(server, request.targetId);
        if (target != null) {
            sendMessage(target, request.senderName + " canceled their request.");
        }
    }

    private static void notifySenderOfDeny(MinecraftServer server, TeleportRequest request) {
        ServerPlayer sender = findOnlinePlayerByUuid(server, request.senderId);
        if (sender != null) {
            sendMessage(sender, "Your request to " + request.targetName + " was denied.");
        }
    }

    private static void sendMessage(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    private static String playerName(ServerPlayer player) {
        return player.getName().getString();
    }

    private static final class TeleportRequest {
        private final UUID senderId;
        private final String senderName;
        private final UUID targetId;
        private final String targetName;
        private final RequestType type;
        private final long expiresAt;

        private TeleportRequest(UUID senderId, String senderName, UUID targetId, String targetName, RequestType type, long expiresAt) {
            this.senderId = senderId;
            this.senderName = senderName;
            this.targetId = targetId;
            this.targetName = targetName;
            this.type = type;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private enum RequestType {
        TO_TARGET,
        HERE
    }
}
