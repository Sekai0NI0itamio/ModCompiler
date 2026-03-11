package com.itamio.servercore.fabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

public final class TeleportRequestService {
    public static final long REQUEST_TTL_MILLIS = 60_000L;
    private static final TeleportRequestService INSTANCE = new TeleportRequestService();

    private final Map<String, TeleportRequest> requestsByPair = new LinkedHashMap<>();

    private TeleportRequestService() {
    }

    public static TeleportRequestService getInstance() {
        return INSTANCE;
    }

    public synchronized TeleportRequest upsertRequest(
            UUID requesterUuid,
            String requesterName,
            UUID targetUuid,
            String targetName,
            RequestType type
    ) {
        pruneExpiredLocked();
        long now = System.currentTimeMillis();
        TeleportRequest request = new TeleportRequest(
                requesterUuid,
                requesterName,
                targetUuid,
                targetName,
                type,
                now,
                now + REQUEST_TTL_MILLIS
        );
        requestsByPair.put(pairKey(requesterUuid, targetUuid), request);
        return request;
    }

    public synchronized int cancelOutgoing(UUID requesterUuid, String targetNameOrAll) {
        pruneExpiredLocked();
        if (requesterUuid == null || targetNameOrAll == null) {
            return 0;
        }
        if ("all".equalsIgnoreCase(targetNameOrAll)) {
            int removed = 0;
            Iterator<Entry<String, TeleportRequest>> iterator = requestsByPair.entrySet().iterator();
            while (iterator.hasNext()) {
                TeleportRequest request = iterator.next().getValue();
                if (requesterUuid.equals(request.requesterUuid)) {
                    iterator.remove();
                    removed++;
                }
            }
            return removed;
        }
        int removed = 0;
        Iterator<Entry<String, TeleportRequest>> iterator = requestsByPair.entrySet().iterator();
        while (iterator.hasNext()) {
            TeleportRequest request = iterator.next().getValue();
            if (requesterUuid.equals(request.requesterUuid)
                    && request.targetName.equalsIgnoreCase(targetNameOrAll)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public synchronized TeleportRequest popIncoming(UUID targetUuid, String requesterName) {
        pruneExpiredLocked();
        if (targetUuid == null || requesterName == null || requesterName.trim().isEmpty()) {
            return null;
        }
        Iterator<Entry<String, TeleportRequest>> iterator = requestsByPair.entrySet().iterator();
        while (iterator.hasNext()) {
            TeleportRequest request = iterator.next().getValue();
            if (targetUuid.equals(request.targetUuid)
                    && request.requesterName.equalsIgnoreCase(requesterName.trim())) {
                iterator.remove();
                return request;
            }
        }
        return null;
    }

    public synchronized List<TeleportRequest> popAllIncoming(UUID targetUuid) {
        pruneExpiredLocked();
        if (targetUuid == null) {
            return Collections.emptyList();
        }
        List<TeleportRequest> matches = new ArrayList<>();
        Iterator<Entry<String, TeleportRequest>> iterator = requestsByPair.entrySet().iterator();
        while (iterator.hasNext()) {
            TeleportRequest request = iterator.next().getValue();
            if (targetUuid.equals(request.targetUuid)) {
                matches.add(request);
                iterator.remove();
            }
        }
        matches.sort(Comparator.comparingLong(left -> left.createdAt));
        return matches;
    }

    public synchronized int pruneExpired() {
        return pruneExpiredLocked();
    }

    private int pruneExpiredLocked() {
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Entry<String, TeleportRequest>> iterator = requestsByPair.entrySet().iterator();
        while (iterator.hasNext()) {
            TeleportRequest request = iterator.next().getValue();
            if (request.expiresAt <= now) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    private static String pairKey(UUID requesterUuid, UUID targetUuid) {
        return requesterUuid.toString().toLowerCase(Locale.ROOT) + ":" + targetUuid.toString().toLowerCase(Locale.ROOT);
    }

    public enum RequestType {
        TPA,
        TPAHERE
    }

    public static final class TeleportRequest {
        private final UUID requesterUuid;
        private final String requesterName;
        private final UUID targetUuid;
        private final String targetName;
        private final RequestType type;
        private final long createdAt;
        private final long expiresAt;

        private TeleportRequest(
                UUID requesterUuid,
                String requesterName,
                UUID targetUuid,
                String targetName,
                RequestType type,
                long createdAt,
                long expiresAt
        ) {
            this.requesterUuid = requesterUuid;
            this.requesterName = requesterName;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.type = type;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        public UUID getRequesterUuid() {
            return requesterUuid;
        }

        public String getRequesterName() {
            return requesterName;
        }

        public UUID getTargetUuid() {
            return targetUuid;
        }

        public String getTargetName() {
            return targetName;
        }

        public RequestType getType() {
            return type;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
