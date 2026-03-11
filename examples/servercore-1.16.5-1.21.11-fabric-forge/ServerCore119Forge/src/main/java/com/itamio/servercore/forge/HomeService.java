package com.itamio.servercore.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class HomeService {
    private static final Pattern HOME_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    private static final HomeService INSTANCE = new HomeService();

    private HomeService() {
    }

    public static HomeService getInstance() {
        return INSTANCE;
    }

    public synchronized HomeRecord setHome(MinecraftServer server, ServerPlayer player, String rawName) {
        String homeName = sanitizeHomeName(rawName);
        if (server == null || player == null || homeName == null) {
            return null;
        }
        String key = normalizeKey(homeName);
        ServerLevel level = (ServerLevel) player.getLevel();
        String dimension = TeleportUtil.dimensionKey(level);
        HomeRecord record = new HomeRecord(
                key,
                homeName,
                dimension,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
        ServerCoreData data = ServerCoreData.get(server);
        data.putHome(player.getUUID(), record);
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
