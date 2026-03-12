package com.itamio.servercore.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

public final class HomeService {
    private static final Pattern HOME_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    private static final HomeService INSTANCE = new HomeService();

    private HomeService() {
    }

    public static HomeService getInstance() {
        return INSTANCE;
    }

    public synchronized HomeRecord setHome(MinecraftServer server, ServerPlayerEntity player, String rawName) {
        String homeName = sanitizeHomeName(rawName);
        if (server == null || player == null || homeName == null) {
            return null;
        }
        String key = normalizeKey(homeName);
        String dimension = TeleportUtil.dimensionKey(PlayerUtil.getServerWorld(player));
        HomeRecord record = new HomeRecord(
                key,
                homeName,
                dimension,
                PlayerUtil.getX(player),
                PlayerUtil.getY(player),
                PlayerUtil.getZ(player),
                RotationUtil.getYaw(player),
                RotationUtil.getPitch(player)
        );
        java.util.UUID uuid = PlayerUtil.getUuid(player);
        if (uuid == null) {
            return null;
        }
        ServerCoreData data = ServerCoreData.get(server);
        data.putHome(uuid, record);
        return record;
    }

    public synchronized List<HomeRecord> listHomes(MinecraftServer server, UUID playerUuid) {
        if (server == null || playerUuid == null) {
            return Collections.emptyList();
        }
        List<HomeRecord> homes = new ArrayList<>(ServerCoreData.get(server).listHomes(playerUuid));
        homes.sort(Comparator.comparing(HomeRecord::getName, String.CASE_INSENSITIVE_ORDER));
        return homes;
    }

    public synchronized HomeRecord getHome(MinecraftServer server, UUID playerUuid, String rawName) {
        if (server == null || playerUuid == null) {
            return null;
        }
        String key = normalizeKey(rawName);
        if (key == null) {
            return null;
        }
        return ServerCoreData.get(server).getHome(playerUuid, key);
    }

    public synchronized boolean deleteHome(MinecraftServer server, UUID playerUuid, String rawName) {
        if (server == null || playerUuid == null) {
            return false;
        }
        String key = normalizeKey(rawName);
        if (key == null) {
            return false;
        }
        HomeRecord removed = ServerCoreData.get(server).removeHome(playerUuid, key);
        return removed != null;
    }

    public static String sanitizeHomeName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        return HOME_NAME_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    private static String normalizeKey(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
