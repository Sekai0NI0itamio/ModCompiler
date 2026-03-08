package net.itamio.tpateleport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = TpaTeleportMod.MODID, name = TpaTeleportMod.NAME, version = TpaTeleportMod.VERSION, acceptableRemoteVersions = "*")
public class TpaTeleportMod {
    public static final String MODID = "tpateleport";
    public static final String NAME = "Tpa Teleport";
    public static final String VERSION = "1.0.0";

    private static final long REQUEST_TTL_MS = 60_000L;
    private static final Map<UUID, Map<UUID, TeleportRequest>> REQUESTS_BY_TARGET = new HashMap<>();

    private int tickCounter = 0;

    public TpaTeleportMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new TpaCommand());
        event.registerServerCommand(new TpaHereCommand());
        event.registerServerCommand(new TpaCancelCommand());
        event.registerServerCommand(new TpaAcceptCommand());
        event.registerServerCommand(new TpaAcceptAllCommand());
        event.registerServerCommand(new TpaDenyCommand());
        event.registerServerCommand(new TpaDenyAllCommand());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            purgeExpired(System.currentTimeMillis());
        }
    }

    private static int sendRequest(EntityPlayerMP sender, MinecraftServer server, String targetName, RequestType type) {
        EntityPlayerMP target = findOnlinePlayer(server, targetName);
        if (target == null) {
            sendMessage(sender, "Player '" + targetName + "' not found.");
            return 0;
        }
        if (target.getUniqueID().equals(sender.getUniqueID())) {
            sendMessage(sender, "You cannot send a request to yourself.");
            return 0;
        }

        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUniqueID(), now);
        Map<UUID, TeleportRequest> requests = getTargetMap(target.getUniqueID());
        TeleportRequest existing = requests.get(sender.getUniqueID());
        if (existing != null && !existing.isExpired(now)) {
            sendMessage(sender, "You already have a pending request to " + target.getName() + ".");
            return 0;
        }

        TeleportRequest request = new TeleportRequest(
            sender.getUniqueID(),
            sender.getName(),
            target.getUniqueID(),
            target.getName(),
            type,
            now + REQUEST_TTL_MS
        );
        requests.put(sender.getUniqueID(), request);

        if (type == RequestType.TO_TARGET) {
            sendMessage(sender, "Teleport request sent to " + target.getName() + ".");
            sendMessage(target, sender.getName() + " wants to teleport to you. Use /tpaccept " + sender.getName() + " or /tpadeny " + sender.getName() + ".");
        } else {
            sendMessage(sender, "Teleport-here request sent to " + target.getName() + ".");
            sendMessage(target, sender.getName() + " wants you to teleport to them. Use /tpaccept " + sender.getName() + " or /tpadeny " + sender.getName() + ".");
        }
        return 1;
    }

    private static int cancelAll(EntityPlayerMP sender, MinecraftServer server) {
        long now = System.currentTimeMillis();
        purgeExpired(now);
        int removed = 0;
        Iterator<Map.Entry<UUID, Map<UUID, TeleportRequest>>> iterator = REQUESTS_BY_TARGET.entrySet().iterator();
        while (iterator.hasNext()) {
            Map<UUID, TeleportRequest> requests = iterator.next().getValue();
            TeleportRequest removedRequest = requests.remove(sender.getUniqueID());
            if (removedRequest != null) {
                removed++;
                notifyTargetOfCancel(server, removedRequest);
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

    private static int cancelRequest(EntityPlayerMP sender, MinecraftServer server, String targetName) {
        long now = System.currentTimeMillis();
        purgeExpired(now);

        EntityPlayerMP target = findOnlinePlayer(server, targetName);
        if (target != null) {
            Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUniqueID());
            if (requests != null) {
                TeleportRequest request = requests.remove(sender.getUniqueID());
                if (request != null) {
                    if (requests.isEmpty()) {
                        REQUESTS_BY_TARGET.remove(target.getUniqueID());
                    }
                    sendMessage(sender, "Canceled request to " + target.getName() + ".");
                    notifyTargetOfCancel(server, request);
                    return 1;
                }
            }
        }

        TeleportRequest request = removeRequestByTargetName(sender.getUniqueID(), targetName);
        if (request == null) {
            sendMessage(sender, "No pending request to " + targetName + ".");
            return 0;
        }

        sendMessage(sender, "Canceled request to " + request.targetName + ".");
        notifyTargetOfCancel(server, request);
        return 1;
    }

    private static int acceptAll(EntityPlayerMP target, MinecraftServer server) {
        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUniqueID(), now);
        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUniqueID());
        if (requests == null || requests.isEmpty()) {
            sendMessage(target, "No pending requests.");
            return 0;
        }

        List<TeleportRequest> snapshot = new ArrayList<>(requests.values());
        requests.clear();
        REQUESTS_BY_TARGET.remove(target.getUniqueID());

        int accepted = 0;
        for (TeleportRequest request : snapshot) {
            if (request.isExpired(now)) {
                continue;
            }
            EntityPlayerMP sender = findOnlinePlayerByUuid(server, request.senderId);
            if (sender != null && executeTeleport(request, sender, target, server)) {
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

    private static int acceptRequest(EntityPlayerMP target, MinecraftServer server, String senderName) {
        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUniqueID(), now);
        EntityPlayerMP sender = findOnlinePlayer(server, senderName);
        if (sender == null) {
            TeleportRequest removed = removeRequestBySenderName(target.getUniqueID(), senderName);
            if (removed != null) {
                sendMessage(target, "Request from " + senderName + " expired or player is offline.");
                return 0;
            }
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUniqueID());
        if (requests == null) {
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        TeleportRequest request = requests.remove(sender.getUniqueID());
        if (request == null || request.isExpired(now)) {
            sendMessage(target, "No pending request from " + senderName + ".");
            if (requests.isEmpty()) {
                REQUESTS_BY_TARGET.remove(target.getUniqueID());
            }
            return 0;
        }

        if (requests.isEmpty()) {
            REQUESTS_BY_TARGET.remove(target.getUniqueID());
        }
        return executeTeleport(request, sender, target, server) ? 1 : 0;
    }

    private static int denyAll(EntityPlayerMP target, MinecraftServer server) {
        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUniqueID(), now);
        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.remove(target.getUniqueID());
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

    private static int denyRequest(EntityPlayerMP target, MinecraftServer server, String senderName) {
        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUniqueID(), now);
        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUniqueID());
        if (requests == null || requests.isEmpty()) {
            sendMessage(target, "No pending request from " + senderName + ".");
            return 0;
        }

        TeleportRequest request = null;
        EntityPlayerMP sender = findOnlinePlayer(server, senderName);
        if (sender != null) {
            request = requests.remove(sender.getUniqueID());
        }
        if (request == null) {
            request = removeRequestBySenderName(target.getUniqueID(), senderName);
        }
        if (request == null || request.isExpired(now)) {
            sendMessage(target, "No pending request from " + senderName + ".");
            if (requests.isEmpty()) {
                REQUESTS_BY_TARGET.remove(target.getUniqueID());
            }
            return 0;
        }

        if (requests.isEmpty()) {
            REQUESTS_BY_TARGET.remove(target.getUniqueID());
        }
        notifySenderOfDeny(server, request);
        sendMessage(target, "Denied request from " + request.senderName + ".");
        return 1;
    }

    private static boolean executeTeleport(TeleportRequest request, EntityPlayerMP sender, EntityPlayerMP target, MinecraftServer server) {
        if (request.type == RequestType.TO_TARGET) {
            if (!teleportPlayer(sender, target, server)) {
                sendMessage(target, "Could not teleport " + sender.getName() + ".");
                sendMessage(sender, "Teleport failed.");
                return false;
            }
            sendMessage(sender, "Teleported to " + target.getName() + ".");
            sendMessage(target, "Accepted request from " + sender.getName() + ".");
            return true;
        }

        if (!teleportPlayer(target, sender, server)) {
            sendMessage(target, "Teleport failed.");
            sendMessage(sender, "Could not teleport " + target.getName() + ".");
            return false;
        }
        sendMessage(target, "Teleported to " + sender.getName() + ".");
        sendMessage(sender, "Accepted request from " + target.getName() + ".");
        return true;
    }

    private static boolean teleportPlayer(EntityPlayerMP player, EntityPlayerMP destination, MinecraftServer server) {
        if (player.dimension != destination.dimension) {
            WorldServer world = server.getWorld(destination.dimension);
            if (world == null) {
                return false;
            }
            server.getPlayerList().transferPlayerToDimension(
                player,
                destination.dimension,
                new FixedPositionTeleporter(world, destination.posX, destination.posY, destination.posZ, destination.rotationYaw, destination.rotationPitch)
            );
        }
        player.connection.setPlayerLocation(destination.posX, destination.posY, destination.posZ, destination.rotationYaw, destination.rotationPitch);
        return true;
    }

    private static Map<UUID, TeleportRequest> getTargetMap(UUID targetId) {
        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(targetId);
        if (requests == null) {
            requests = new HashMap<>();
            REQUESTS_BY_TARGET.put(targetId, requests);
        }
        return requests;
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

    @Nullable
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

    @Nullable
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

    @Nullable
    private static EntityPlayerMP findOnlinePlayer(MinecraftServer server, String playerName) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.getName().equalsIgnoreCase(playerName)) {
                return player;
            }
        }
        return null;
    }

    @Nullable
    private static EntityPlayerMP findOnlinePlayerByUuid(MinecraftServer server, UUID playerId) {
        return server.getPlayerList().getPlayerByUUID(playerId);
    }

    private static List<String> getOnlinePlayerNames(MinecraftServer server) {
        List<String> names = new ArrayList<>();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private static List<String> getPendingSenderNamesForTarget(EntityPlayerMP target) {
        long now = System.currentTimeMillis();
        purgeExpiredForTarget(target.getUniqueID(), now);
        Map<UUID, TeleportRequest> requests = REQUESTS_BY_TARGET.get(target.getUniqueID());
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> names = new ArrayList<>();
        for (TeleportRequest request : requests.values()) {
            names.add(request.senderName);
        }
        return names;
    }

    private static List<String> getPendingTargetNamesForSender(EntityPlayerMP sender) {
        long now = System.currentTimeMillis();
        purgeExpired(now);

        List<String> names = new ArrayList<>();
        for (Map<UUID, TeleportRequest> requests : REQUESTS_BY_TARGET.values()) {
            TeleportRequest request = requests.get(sender.getUniqueID());
            if (request != null && !request.isExpired(now)) {
                names.add(request.targetName);
            }
        }
        return names;
    }

    private static void notifyTargetOfCancel(MinecraftServer server, TeleportRequest request) {
        EntityPlayerMP target = findOnlinePlayerByUuid(server, request.targetId);
        if (target != null) {
            sendMessage(target, request.senderName + " canceled their request.");
        }
    }

    private static void notifySenderOfDeny(MinecraftServer server, TeleportRequest request) {
        EntityPlayerMP sender = findOnlinePlayerByUuid(server, request.senderId);
        if (sender != null) {
            sendMessage(sender, "Your request to " + request.targetName + " was denied.");
        }
    }

    private static void sendMessage(EntityPlayerMP player, String message) {
        player.sendMessage(new TextComponentString(message));
    }

    private static EntityPlayerMP requirePlayer(ICommandSender sender) throws PlayerNotFoundException {
        return CommandBase.getCommandSenderAsPlayer(sender);
    }

    @Nullable
    private static EntityPlayerMP commandSenderAsPlayerOrNull(ICommandSender sender) {
        if (sender.getCommandSenderEntity() instanceof EntityPlayerMP) {
            return (EntityPlayerMP) sender.getCommandSenderEntity();
        }
        return null;
    }

    private static abstract class PlayerCommand extends CommandBase {
        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        protected List<String> completePlayerNames(MinecraftServer server, String[] args) {
            if (args.length != 1) {
                return Collections.emptyList();
            }
            return CommandBase.getListOfStringsMatchingLastWord(args, getOnlinePlayerNames(server));
        }
    }

    private static final class TpaCommand extends PlayerCommand {
        @Override
        public String getName() {
            return "tpa";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/tpa <player>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) {
                throw new WrongUsageException(getUsage(sender), new Object[0]);
            }
            EntityPlayerMP player = requirePlayer(sender);
            sendRequest(player, server, args[0], RequestType.TO_TARGET);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
            return completePlayerNames(server, args);
        }
    }

    private static final class TpaHereCommand extends PlayerCommand {
        @Override
        public String getName() {
            return "tpahere";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/tpahere <player>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) {
                throw new WrongUsageException(getUsage(sender), new Object[0]);
            }
            EntityPlayerMP player = requirePlayer(sender);
            sendRequest(player, server, args[0], RequestType.HERE);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
            return completePlayerNames(server, args);
        }
    }

    private static final class TpaCancelCommand extends PlayerCommand {
        @Override
        public String getName() {
            return "tpacancel";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/tpacancel <player|all>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) {
                throw new WrongUsageException(getUsage(sender), new Object[0]);
            }
            EntityPlayerMP player = requirePlayer(sender);
            if ("all".equalsIgnoreCase(args[0])) {
                cancelAll(player, server);
            } else {
                cancelRequest(player, server, args[0]);
            }
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
            if (args.length != 1) {
                return Collections.emptyList();
            }
            EntityPlayerMP player = commandSenderAsPlayerOrNull(sender);
            if (player == null) {
                return Collections.emptyList();
            }
            List<String> options = new ArrayList<>(getPendingTargetNamesForSender(player));
            options.add("all");
            return CommandBase.getListOfStringsMatchingLastWord(args, options);
        }
    }

    private static final class TpaAcceptCommand extends PlayerCommand {
        @Override
        public String getName() {
            return "tpaccept";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/tpaccept <player>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) {
                throw new WrongUsageException(getUsage(sender), new Object[0]);
            }
            EntityPlayerMP player = requirePlayer(sender);
            acceptRequest(player, server, args[0]);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
            if (args.length != 1) {
                return Collections.emptyList();
            }
            EntityPlayerMP player = commandSenderAsPlayerOrNull(sender);
            if (player == null) {
                return Collections.emptyList();
            }
            return CommandBase.getListOfStringsMatchingLastWord(args, getPendingSenderNamesForTarget(player));
        }
    }

    private static final class TpaAcceptAllCommand extends PlayerCommand {
        @Override
        public String getName() {
            return "tpacceptall";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/tpacceptall";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 0) {
                throw new WrongUsageException(getUsage(sender), new Object[0]);
            }
            acceptAll(requirePlayer(sender), server);
        }
    }

    private static final class TpaDenyCommand extends PlayerCommand {
        @Override
        public String getName() {
            return "tpadeny";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/tpadeny <player>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) {
                throw new WrongUsageException(getUsage(sender), new Object[0]);
            }
            denyRequest(requirePlayer(sender), server, args[0]);
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
            if (args.length != 1) {
                return Collections.emptyList();
            }
            EntityPlayerMP player = commandSenderAsPlayerOrNull(sender);
            if (player == null) {
                return Collections.emptyList();
            }
            return CommandBase.getListOfStringsMatchingLastWord(args, getPendingSenderNamesForTarget(player));
        }
    }

    private static final class TpaDenyAllCommand extends PlayerCommand {
        @Override
        public String getName() {
            return "tpadenyall";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/tpadenyall";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 0) {
                throw new WrongUsageException(getUsage(sender), new Object[0]);
            }
            denyAll(requirePlayer(sender), server);
        }
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

    private static final class FixedPositionTeleporter extends Teleporter {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        private FixedPositionTeleporter(WorldServer world, double x, double y, double z, float yaw, float pitch) {
            super(world);
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public void placeInPortal(Entity entity, float rotationYaw) {
            place(entity);
        }

        @Override
        public boolean placeInExistingPortal(Entity entity, float rotationYaw) {
            place(entity);
            return true;
        }

        @Override
        public boolean makePortal(Entity entity) {
            return true;
        }

        @Override
        public void removeStalePortalLocations(long worldTime) {
        }

        private void place(Entity entity) {
            entity.setLocationAndAngles(x, y, z, yaw, pitch);
            entity.motionX = 0.0D;
            entity.motionY = 0.0D;
            entity.motionZ = 0.0D;
        }
    }
}
