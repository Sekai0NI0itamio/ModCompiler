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
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class TpaTeleportMod implements ModInitializer {
    public static final String MOD_ID = "tpateleport";
    private static final long REQUEST_TTL_MS = 60_000L;
    private static final Map<UUID, Map<UUID, TeleportRequest>> REQUESTS_BY_TARGET = new HashMap<>();
    private static int tickCounter = 0;

    private static final SuggestionProvider<ServerCommandSource> ONLINE_PLAYERS = (context, builder) ->
        CommandSource.suggestMatching(context.getSource().getPlayerNames(), builder);

    private static final SuggestionProvider<ServerCommandSource> PENDING_FOR_TARGET = (context, builder) -> {
        ServerPlayerEntity target = context.getSource().getPlayer();
        if (target == null) {
            return builder.buildFuture();
        }

        long now = Util.getMeasuringTimeMs();
        purgeExpiredForTarget(target.getUuid(), now);
        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUuid());
        if (requests == null || requests.isEmpty()) {
            return builder.buildFuture();
        }

        List<String> names = new ArrayList<>();
        for (TeleportRequest request : requests.values()) {
            names.add(request.senderName);
        }
        return CommandSource.suggestMatching(names, builder);
    };

    private static final SuggestionProvider<ServerCommandSource> PENDING_FOR_SENDER = (context, builder) -> {
        ServerPlayerEntity sender = context.getSource().getPlayer();
        if (sender == null) {
            return builder.buildFuture();
        }

        long now = Util.getMeasuringTimeMs();
        purgeExpired(now);
        List<String> names = new ArrayList<>();
        for (Map<UUID, TeleportRequest> requests : REQUESTS_BY_TARGET.values()) {
            TeleportRequest request = requests.get(sender.getUuid());
            if (request != null && !request.isExpired(now)) {
                names.add(request.targetName);
            }
        }
        return CommandSource.suggestMatching(names, builder);
    };

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                purgeExpired(Util.getMeasuringTimeMs());
            }
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("tpa")
                .requires(source -> source.getPlayer() != null)
                .then(
                    CommandManager.argument("player", StringArgumentType.word())
                        .suggests(ONLINE_PLAYERS)
                        .executes(context -> sendRequest(context, StringArgumentType.getString(context, "player"), RequestType.TO_TARGET))
                )
        );

        dispatcher.register(
            CommandManager.literal("tpahere")
                .requires(source -> source.getPlayer() != null)
                .then(
                    CommandManager.argument("player", StringArgumentType.word())
                        .suggests(ONLINE_PLAYERS)
                        .executes(context -> sendRequest(context, StringArgumentType.getString(context, "player"), RequestType.HERE))
                )
        );

        dispatcher.register(
            CommandManager.literal("tpacancel")
                .requires(source -> source.getPlayer() != null)
                .then(CommandManager.literal("all").executes(TpaTeleportMod::cancelAll))
                .then(
                    CommandManager.argument("player", StringArgumentType.word())
                        .suggests(PENDING_FOR_SENDER)
                        .executes(context -> cancelRequest(context, StringArgumentType.getString(context, "player")))
                )
        );

        dispatcher.register(
            CommandManager.literal("tpacceptall")
                .requires(source -> source.getPlayer() != null)
                .executes(TpaTeleportMod::acceptAll)
        );

        dispatcher.register(
            CommandManager.literal("tpaccept")
                .requires(source -> source.getPlayer() != null)
                .then(
                    CommandManager.argument("player", StringArgumentType.word())
                        .suggests(PENDING_FOR_TARGET)
                        .executes(context -> acceptRequest(context, StringArgumentType.getString(context, "player")))
                )
        );

        dispatcher.register(
            CommandManager.literal("tpadeny")
                .requires(source -> source.getPlayer() != null)
                .then(
                    CommandManager.argument("player", StringArgumentType.word())
                        .suggests(PENDING_FOR_TARGET)
                        .executes(context -> denyRequest(context, StringArgumentType.getString(context, "player")))
                )
        );

        dispatcher.register(
            CommandManager.literal("tpadenyall")
                .requires(source -> source.getPlayer() != null)
                .executes(TpaTeleportMod::denyAll)
        );
    }

    private static int sendRequest(CommandContext<ServerCommandSource> context, String targetName, RequestType type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity sender = context.getSource().getPlayerOrThrow();
        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity target = findOnlinePlayer(server, targetName);
        if (target == null) {
            sendMessage(sender, "Player '" + targetName + "' not found.");
            return 0;
        }
        if (target.getUuid().equals(sender.getUuid())) {
            sendMessage(sender, "You cannot send a request to yourself.");
            return 0;
        }

        long now = Util.getMeasuringTimeMs();
        purgeExpiredForTarget(target.getUuid(), now);
        Map<UUID, TeleportRequest> requests = getTargetMap(target.getUuid());
        TeleportRequest existing = requests.get(sender.getUuid());
        if (existing != null && !existing.isExpired(now)) {
            sendMessage(sender, "You already have a pending request to " + target.getNameForScoreboard() + ".");
            return 0;
        }

        TeleportRequest request = new TeleportRequest(
            sender.getUuid(),
            sender.getNameForScoreboard(),
            target.getUuid(),
            target.getNameForScoreboard(),
            type,
            now + REQUEST_TTL_MS
        );
        requests.put(sender.getUuid(), request);

        if (type == RequestType.TO_TARGET) {
            sendMessage(sender, "Teleport request sent to " + target.getNameForScoreboard() + ".");
            sendMessage(target, sender.getNameForScoreboard() + " wants to teleport to you. Use /tpaccept " + sender.getNameForScoreboard() + " or /tpadeny " + sender.getNameForScoreboard() + ".");
        } else {
            sendMessage(sender, "Teleport-here request sent to " + target.getNameForScoreboard() + ".");
            sendMessage(target, sender.getNameForScoreboard() + " wants you to teleport to them. Use /tpaccept " + sender.getNameForScoreboard() + " or /tpadeny " + sender.getNameForScoreboard() + ".");
        }

        return 1;
    }

    private static int cancelAll(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity sender = context.getSource().getPlayerOrThrow();
        long now = Util.getMeasuringTimeMs();
        purgeExpired(now);

        int removed = 0;
        Iterator<Map.Entry<UUID, Map<UUID, TeleportRequest>>> iterator = REQUESTS_BY_TARGET.entrySet().iterator();
        while (iterator.hasNext()) {
            Map<UUID, TeleportRequest> requests = iterator.next().getValue();
            TeleportRequest request = requests.remove(sender.getUuid());
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

    private static int cancelRequest(CommandContext<ServerCommandSource> context, String targetName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity sender = context.getSource().getPlayerOrThrow();
        MinecraftServer server = context.getSource().getServer();
        long now = Util.getMeasuringTimeMs();
        purgeExpired(now);

        ServerPlayerEntity target = findOnlinePlayer(server, targetName);
        if (target != null) {
            Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUuid());
            if (requests != null) {
                TeleportRequest request = requests.remove(sender.getUuid());
                if (request != null) {
                    if (requests.isEmpty()) {
                        REQUESTS_BY_TARGET.remove(target.getUuid());
                    }
                    sendMessage(sender, "Canceled request to " + target.getNameForScoreboard() + ".");
                    notifyTargetOfCancel(server, request);
                    return 1;
                }
            }
        }

        TeleportRequest request = removeRequestByTargetName(sender.getUuid(), targetName);
        if (request == null) {
            sendMessage(sender, "No pending request to " + targetName + ".");
            return 0;
        }

        sendMessage(sender, "Canceled request to " + request.targetName + ".");
        notifyTargetOfCancel(server, request);
        return 1;
    }

    private static int acceptAll(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = context.getSource().getPlayerOrThrow();
        MinecraftServer server = context.getSource().getServer();
        long now = Util.getMeasuringTimeMs();
        purgeExpiredForTarget(target.getUuid(), now);

        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUuid());
        if (requests == null || requests.isEmpty()) {
            sendMessage(target, "No pending requests.");
            return 0;
        }

        List<TeleportRequest> snapshot = new ArrayList<>(requests.values());
        requests.clear();
        REQUESTS_BY_TARGET.remove(target.getUuid());

        int accepted = 0;
        for (TeleportRequest request : snapshot) {
            if (request.isExpired(now)) {
                continue;
            }

            ServerPlayerEntity sender = findOnlinePlayerByUuid(server, request.senderId);
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

    private static int acceptRequest(CommandContext<ServerCommandSource> context, String senderName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = context.getSource().getPlayerOrThrow();
        MinecraftServer server = context.getSource().getServer();
        long now = Util.getMeasuringTimeMs();
        purgeExpiredForTarget(target.getUuid(), now);

        ServerPlayerEntity sender = findOnlinePlayer(server, senderName);
        if (sender == null) {
            TeleportRequest removed = removeRequestBySenderName(target.getUuid(), senderName);
            if (removed != null) {
                sendMessage(target, "Request from " + senderName + " expired or player is offline.");
                return 0;
            }
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUuid());
        if (requests == null) {
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        TeleportRequest request = requests.remove(sender.getUuid());
        if (request == null || request.isExpired(now)) {
            if (requests.isEmpty()) {
                REQUESTS_BY_TARGET.remove(target.getUuid());
            }
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        if (requests.isEmpty()) {
            REQUESTS_BY_TARGET.remove(target.getUuid());
        }

        return executeTeleport(request, sender, target) ? 1 : 0;
    }

    private static int denyAll(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = context.getSource().getPlayerOrThrow();
        MinecraftServer server = context.getSource().getServer();
        long now = Util.getMeasuringTimeMs();
        purgeExpiredForTarget(target.getUuid(), now);

        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.remove(target.getUuid());
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

    private static int denyRequest(CommandContext<ServerCommandSource> context, String senderName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = context.getSource().getPlayerOrThrow();
        MinecraftServer server = context.getSource().getServer();
        long now = Util.getMeasuringTimeMs();
        purgeExpiredForTarget(target.getUuid(), now);

        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUuid());
        if (requests == null || requests.isEmpty()) {
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        TeleportRequest request = null;
        ServerPlayerEntity sender = findOnlinePlayer(server, senderName);
        if (sender != null) {
            request = requests.remove(sender.getUuid());
        }
        if (request == null) {
            request = removeRequestBySenderName(target.getUuid(), senderName);
        }
        if (request == null || request.isExpired(now)) {
            if (requests.isEmpty()) {
                REQUESTS_BY_TARGET.remove(target.getUuid());
            }
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        if (requests.isEmpty()) {
            REQUESTS_BY_TARGET.remove(target.getUuid());
        }

        notifySenderOfDeny(server, request);
        sendMessage(target, "Denied request from " + request.senderName + ".");
        return 1;
    }

    private static boolean executeTeleport(TeleportRequest request, ServerPlayerEntity sender, ServerPlayerEntity target) {
        if (request.type == RequestType.TO_TARGET) {
            if (!teleportPlayer(sender, target)) {
                sendMessage(target, "Could not teleport " + sender.getNameForScoreboard() + ".");
                sendMessage(sender, "Teleport failed.");
                return false;
            }
            sendMessage(sender, "Teleported to " + target.getNameForScoreboard() + ".");
            sendMessage(target, "Accepted request from " + sender.getNameForScoreboard() + ".");
            return true;
        }

        if (!teleportPlayer(target, sender)) {
            sendMessage(target, "Teleport failed.");
            sendMessage(sender, "Could not teleport " + target.getNameForScoreboard() + ".");
            return false;
        }
        sendMessage(target, "Teleported to " + sender.getNameForScoreboard() + ".");
        sendMessage(sender, "Accepted request from " + target.getNameForScoreboard() + ".");
        return true;
    }

    private static boolean teleportPlayer(ServerPlayerEntity player, ServerPlayerEntity destination) {
        ServerWorld destinationWorld = destination.getServerWorld();
        return player.teleport(
            destinationWorld,
            destination.getX(),
            destination.getY(),
            destination.getZ(),
            Set.<PositionFlag>of(),
            destination.getYaw(),
            destination.getPitch(),
            true
        );
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

    private static ServerPlayerEntity findOnlinePlayer(MinecraftServer server, String targetName) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getNameForScoreboard().equalsIgnoreCase(targetName)) {
                return player;
            }
        }
        return null;
    }

    private static ServerPlayerEntity findOnlinePlayerByUuid(MinecraftServer server, UUID playerId) {
        return server.getPlayerManager().getPlayer(playerId);
    }

    private static void notifyTargetOfCancel(MinecraftServer server, TeleportRequest request) {
        ServerPlayerEntity target = findOnlinePlayerByUuid(server, request.targetId);
        if (target != null) {
            sendMessage(target, request.senderName + " canceled their request.");
        }
    }

    private static void notifySenderOfDeny(MinecraftServer server, TeleportRequest request) {
        ServerPlayerEntity sender = findOnlinePlayerByUuid(server, request.senderId);
        if (sender != null) {
            sendMessage(sender, "Your request to " + request.targetName + " was denied.");
        }
    }

    private static void sendMessage(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message));
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
            return now >= this.expiresAt;
        }
    }

    private enum RequestType {
        TO_TARGET,
        HERE
    }
}
