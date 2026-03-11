package asd.itamio.servercore.service;

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
import net.minecraft.entity.player.EntityPlayerMP;

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
      EntityPlayerMP sender, EntityPlayerMP target, TeleportRequestService.RequestType type
   ) {
      this.pruneExpiredLocked();
      long now = System.currentTimeMillis();
      TeleportRequestService.TeleportRequest request = new TeleportRequestService.TeleportRequest(
         sender.func_110124_au(), sender.func_70005_c_(), target.func_110124_au(), target.func_70005_c_(), type, now, now + 60000L
      );
      this.requestsByPair.put(pairKey(request.getRequesterUuid(), request.getTargetUuid()), request);
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
               if (requesterUuid.equals(request.getRequesterUuid())) {
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
               if (requesterUuid.equals(request.getRequesterUuid()) && request.getTargetName().equalsIgnoreCase(targetNameOrAll)) {
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
            if (targetUuid.equals(request.getTargetUuid()) && request.getRequesterName().equalsIgnoreCase(requesterName.trim())) {
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
            if (targetUuid.equals(request.getTargetUuid())) {
               matches.add(request);
               iterator.remove();
            }
         }

         Collections.sort(matches, new Comparator<TeleportRequestService.TeleportRequest>() {
            public int compare(TeleportRequestService.TeleportRequest left, TeleportRequestService.TeleportRequest right) {
               return Long.compare(left.getCreatedAt(), right.getCreatedAt());
            }
         });
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
         if (request.getExpiresAt() <= now) {
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

   public static class TeleportRequest {
      private final UUID requesterUuid;
      private final String requesterName;
      private final UUID targetUuid;
      private final String targetName;
      private final TeleportRequestService.RequestType type;
      private final long createdAt;
      private final long expiresAt;

      public TeleportRequest(
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
