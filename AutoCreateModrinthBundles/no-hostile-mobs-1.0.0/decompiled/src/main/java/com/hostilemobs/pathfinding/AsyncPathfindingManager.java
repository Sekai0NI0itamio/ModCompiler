package com.hostilemobs.pathfinding;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

public class AsyncPathfindingManager {
   private static final ExecutorService PATHFINDING_EXECUTOR = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), r -> {
      Thread t = new Thread(r, "HostileMobs-Pathfinding");
      t.setDaemon(true);
      t.setPriority(1);
      return t;
   });
   private static final Map<EntityLiving, AsyncPathfindingManager.PathfindingTask> PENDING_TASKS = new ConcurrentHashMap<>();
   private static final Map<EntityLiving, AsyncPathfindingManager.CachedPath> PATH_CACHE = new ConcurrentHashMap<>();
   private static final int CACHE_DURATION_TICKS = 40;
   private static final double SIGNIFICANT_MOVEMENT = 4.0;

   public static void requestPathfinding(EntityLiving mob, EntityPlayer target) {
      if (mob != null && target != null && !mob.field_70170_p.field_72995_K) {
         AsyncPathfindingManager.CachedPath cached = PATH_CACHE.get(mob);
         if (cached == null || !cached.isValid(target.func_180425_c())) {
            if (!PENDING_TASKS.containsKey(mob)) {
               AsyncPathfindingManager.PathfindingTask task = new AsyncPathfindingManager.PathfindingTask(mob, target);
               PENDING_TASKS.put(mob, task);
               PATHFINDING_EXECUTOR.submit(() -> {
                  try {
                     task.compute();
                  } catch (Exception var6) {
                  } finally {
                     PENDING_TASKS.remove(mob);
                  }
               });
            }
         }
      }
   }

   public static void applyComputedPaths() {
      for (Entry<EntityLiving, AsyncPathfindingManager.PathfindingTask> entry : PENDING_TASKS.entrySet()) {
         AsyncPathfindingManager.PathfindingTask task = entry.getValue();
         if (task.isComplete()) {
            EntityLiving mob = entry.getKey();
            EntityPlayer target = task.getTarget();
            if (mob.func_70089_S() && target != null && target.func_70089_S()) {
               mob.func_70661_as().func_75497_a(target, 1.0);
               PATH_CACHE.put(mob, new AsyncPathfindingManager.CachedPath(target.func_180425_c(), mob.field_70170_p.func_82737_E()));
            }

            PENDING_TASKS.remove(mob);
         }
      }

      PATH_CACHE.entrySet()
         .removeIf(
            entryx -> !((EntityLiving)entryx.getKey()).func_70089_S()
               || ((AsyncPathfindingManager.CachedPath)entryx.getValue()).isExpired(((EntityLiving)entryx.getKey()).field_70170_p.func_82737_E())
         );
   }

   public static void shutdown() {
      PATHFINDING_EXECUTOR.shutdownNow();
      PENDING_TASKS.clear();
      PATH_CACHE.clear();
   }

   private static class CachedPath {
      private final BlockPos targetPos;
      private final long computedTime;

      CachedPath(BlockPos targetPos, long computedTime) {
         this.targetPos = targetPos;
         this.computedTime = computedTime;
      }

      boolean isValid(BlockPos currentTargetPos) {
         return this.targetPos.func_177951_i(currentTargetPos) < 16.0;
      }

      boolean isExpired(long currentTime) {
         return currentTime - this.computedTime > 40L;
      }
   }

   private static class PathfindingTask {
      private final EntityLiving mob;
      private final EntityPlayer target;
      private final BlockPos mobPos;
      private final BlockPos targetPos;
      private volatile boolean complete = false;

      PathfindingTask(EntityLiving mob, EntityPlayer target) {
         this.mob = mob;
         this.target = target;
         this.mobPos = mob.func_180425_c();
         this.targetPos = target.func_180425_c();
      }

      void compute() {
         try {
            Thread.sleep(1L);
         } catch (InterruptedException var2) {
            Thread.currentThread().interrupt();
         }

         this.complete = true;
      }

      boolean isComplete() {
         return this.complete;
      }

      EntityPlayer getTarget() {
         return this.target;
      }
   }
}
