package com.hostilemobs.pathfinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class GlobalPathManager {
   private static final Map<EntityPlayer, GlobalPathManager.GlobalPath> PLAYER_PATHS = new ConcurrentHashMap<>();
   private static final int PATH_RECALC_INTERVAL = 100;
   private static final int MAX_PATH_LENGTH = 200;

   public static GlobalPathManager.GlobalPath getPathToPlayer(World world, EntityPlayer player, BlockPos startPos) {
      GlobalPathManager.GlobalPath path = PLAYER_PATHS.get(player);
      if (path == null || path.needsRecalculation(world.func_82737_E())) {
         path = new GlobalPathManager.GlobalPath(player, world.func_82737_E());
         PLAYER_PATHS.put(player, path);
      }

      return path;
   }

   public static void markBlockForPlacement(EntityPlayer player, BlockPos pos) {
      GlobalPathManager.GlobalPath path = PLAYER_PATHS.get(player);
      if (path != null) {
         path.addPlannedBlock(pos);
      }
   }

   public static void markBlockPlaced(EntityPlayer player, BlockPos pos) {
      GlobalPathManager.GlobalPath path = PLAYER_PATHS.get(player);
      if (path != null) {
         path.markBlockPlaced(pos);
      }
   }

   public static boolean needsBreaking(EntityPlayer player, BlockPos pos) {
      GlobalPathManager.GlobalPath path = PLAYER_PATHS.get(player);
      return path != null && path.needsBreaking(pos);
   }

   public static void cleanup() {
      PLAYER_PATHS.entrySet().removeIf(entry -> !entry.getKey().func_70089_S());
   }

   public static class GlobalPath {
      private final EntityPlayer target;
      private final long createdTime;
      private final Set<BlockPos> plannedBlocks = ConcurrentHashMap.newKeySet();
      private final Set<BlockPos> placedBlocks = ConcurrentHashMap.newKeySet();
      private final Set<BlockPos> blocksToBreak = ConcurrentHashMap.newKeySet();
      private final List<GlobalPathManager.PathSegment> segments = new ArrayList<>();
      private boolean isComplete = false;

      public GlobalPath(EntityPlayer target, long createdTime) {
         this.target = target;
         this.createdTime = createdTime;
      }

      public boolean needsRecalculation(long currentTime) {
         return currentTime - this.createdTime > 100L;
      }

      public void addPlannedBlock(BlockPos pos) {
         this.plannedBlocks.add(pos);
      }

      public void markBlockPlaced(BlockPos pos) {
         this.placedBlocks.add(pos);
         this.plannedBlocks.remove(pos);
         if (this.plannedBlocks.isEmpty()) {
            this.isComplete = true;
         }
      }

      public boolean needsBreaking(BlockPos pos) {
         return this.blocksToBreak.contains(pos);
      }

      public void addBlockToBreak(BlockPos pos) {
         this.blocksToBreak.add(pos);
      }

      public boolean isComplete() {
         return this.isComplete;
      }

      public Set<BlockPos> getPlannedBlocks() {
         return new HashSet<>(this.plannedBlocks);
      }

      public Set<BlockPos> getPlacedBlocks() {
         return new HashSet<>(this.placedBlocks);
      }

      public EntityPlayer getTarget() {
         return this.target;
      }

      public void addSegment(GlobalPathManager.PathSegment segment) {
         this.segments.add(segment);
      }

      public List<GlobalPathManager.PathSegment> getSegments() {
         return new ArrayList<>(this.segments);
      }
   }

   public static class PathSegment {
      private final BlockPos start;
      private final BlockPos end;
      private final GlobalPathManager.PathType type;

      public PathSegment(BlockPos start, BlockPos end, GlobalPathManager.PathType type) {
         this.start = start;
         this.end = end;
         this.type = type;
      }

      public BlockPos getStart() {
         return this.start;
      }

      public BlockPos getEnd() {
         return this.end;
      }

      public GlobalPathManager.PathType getType() {
         return this.type;
      }
   }

   public static enum PathType {
      STAIRCASE,
      TUNNEL,
      BRIDGE,
      DIRECT;
   }
}
