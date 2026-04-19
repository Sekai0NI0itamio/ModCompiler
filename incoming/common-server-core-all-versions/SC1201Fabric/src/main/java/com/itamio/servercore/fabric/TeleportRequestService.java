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
   public static final long REQUEST_TTL_MILLIS = 60000L;
   private static final TeleportRequestService INSTANCE = new TeleportRequestService();
   private final Map<String, TeleportRequestService.TeleportRequest> requestsByPair = new LinkedHashMap<>();

   private TeleportRequestService() {
   }

   public static TeleportRequestService getInstance() {
      return INSTANCE;
   }

   public synchronized TeleportRequestService.TeleportRequest upsertRequest(
      UUID requesterUuid, String requesterName, UUID targetUuid, String targetName, TeleportRequestService.RequestType type
   ) {
      this.pruneExpiredLocked();
      long now = System.currentTimeMillis();
      TeleportRequestService.TeleportRequest request = new TeleportRequestService.TeleportRequest(
         requesterUuid, requesterName, targetUuid, targetName, type, now, now + 60000L
      );
      this.requestsByPair.put(pairKey(requesterUuid, targetUuid), request);
      return request;
   }

   public synchronized int cancelOutgoing(UUID requesterUuid, String targetNameOrAll) {
      this.pruneExpiredLocked();
      if (requesterUuid != null && targetNameOrAll != null) {
         if ("all".equalsIgnoreCase(targetNameOrAll)) {
            int removed = 0;
            Iterator<Entry<String, TeleportRequestService.TeleportRequest>> iterator = this.requestsByPair.entrySet().iterator();

            while (iterator.hasNext()) {
               TeleportRequestService.TeleportRequest request = iterator.next().getValue();
               if (requesterUuid.equals(request.requesterUuid)) {
                  iterator.remove();
                  removed++;
               }
            }

            return removed;
         } else {
            int removed = 0;
            Iterator<Entry<String, TeleportRequestService.TeleportRequest>> iterator = this.requestsByPair.entrySet().iterator();

            while (iterator.hasNext()) {
               TeleportRequestService.TeleportRequest request = iterator.next().getValue();
               if (requesterUuid.equals(request.requesterUuid) && request.targetName.equalsIgnoreCase(targetNameOrAll)) {
                  iterator.remove();
                  removed++;
               }
            }

            return removed;
         }
      } else {
         return 0;
      }
   }

   public synchronized TeleportRequestService.TeleportRequest popIncoming(UUID targetUuid, String requesterName) {
      this.pruneExpiredLocked();
      if (targetUuid != null && requesterName != null && !requesterName.trim().isEmpty()) {
         Iterator<Entry<String, TeleportRequestService.TeleportRequest>> iterator = this.requestsByPair.entrySet().iterator();

         while (iterator.hasNext()) {
            TeleportRequestService.TeleportRequest request = iterator.next().getValue();
            if (targetUuid.equals(request.targetUuid) && request.requesterName.equalsIgnoreCase(requesterName.trim())) {
               iterator.remove();
               return request;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public synchronized List<TeleportRequestService.TeleportRequest> popAllIncoming(UUID targetUuid) {
      this.pruneExpiredLocked();
      if (targetUuid == null) {
         return Collections.emptyList();
      } else {
         List<TeleportRequestService.TeleportRequest> matches = new ArrayList<>();
         Iterator<Entry<String, TeleportRequestService.TeleportRequest>> iterator = this.requestsByPair.entrySet().iterator();

         while (iterator.hasNext()) {
            TeleportRequestService.TeleportRequest request = iterator.next().getValue();
            if (targetUuid.equals(request.targetUuid)) {
               matches.add(request);
               iterator.remove();
            }
         }

         matches.sort(Comparator.comparingLong(left -> left.createdAt));
         return matches;
      }
   }

   public synchronized int pruneExpired() {
      return this.pruneExpiredLocked();
   }

   private int pruneExpiredLocked() {
      long now = System.currentTimeMillis();
      int removed = 0;
      Iterator<Entry<String, TeleportRequestService.TeleportRequest>> iterator = this.requestsByPair.entrySet().iterator();

      while (iterator.hasNext()) {
         TeleportRequestService.TeleportRequest request = iterator.next().getValue();
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

   public static enum RequestType {
      TPA,
      TPAHERE;
   }

   public static final class TeleportRequest {
      private final UUID requesterUuid;
      private final String requesterName;
      private final UUID targetUuid;
      private final String targetName;
      private final TeleportRequestService.RequestType type;
      private final long createdAt;
      private final long expiresAt;

      private TeleportRequest(
         UUID requesterUuid, String requesterName, UUID targetUuid, String targetName, TeleportRequestService.RequestType type, long createdAt, long expiresAt
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
         return this.requesterUuid;
      }

      public String getRequesterName() {
         return this.requesterName;
      }

      public UUID getTargetUuid() {
         return this.targetUuid;
      }

      public String getTargetName() {
         return this.targetName;
      }

      public TeleportRequestService.RequestType getType() {
         return this.type;
      }

      public long getCreatedAt() {
         return this.createdAt;
      }

      public long getExpiresAt() {
         return this.expiresAt;
      }
   }
}
