package com.hostilemobs.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ZombiePathfinder {
   private final World world;
   private final EntityLiving entity;
   private final int maxBlocksToUse;
   private static final int MAX_SEARCH_NODES = 500;
   private static final double BLOCK_PLACE_COST = 2.0;

   public ZombiePathfinder(World world, EntityLiving entity, int maxBlocksToUse) {
      this.world = world;
      this.entity = entity;
      this.maxBlocksToUse = maxBlocksToUse;
   }

   public ZombiePathfinder.ZombiePath findPath(BlockPos start, BlockPos target) {
      PriorityQueue<ZombiePathfinder.PathNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
      Map<BlockPos, ZombiePathfinder.PathNode> allNodes = new HashMap<>();
      Set<BlockPos> closedSet = new HashSet<>();
      ZombiePathfinder.PathNode startNode = new ZombiePathfinder.PathNode(start, 0.0, 0, this.heuristic(start, target), null, false);
      openSet.add(startNode);
      allNodes.put(start, startNode);
      int nodesExplored = 0;

      while (!openSet.isEmpty() && nodesExplored < 500) {
         ZombiePathfinder.PathNode current = openSet.poll();
         nodesExplored++;
         if (current.pos.equals(target) || current.pos.func_177951_i(target) < 4.0) {
            return this.reconstructPath(current);
         }

         closedSet.add(current.pos);

         for (ZombiePathfinder.PathNode neighbor : this.getNeighbors(current, target)) {
            if (!closedSet.contains(neighbor.pos) && neighbor.blocksUsed <= this.maxBlocksToUse) {
               ZombiePathfinder.PathNode existingNode = allNodes.get(neighbor.pos);
               if (existingNode == null || neighbor.gScore < existingNode.gScore) {
                  allNodes.put(neighbor.pos, neighbor);
                  openSet.remove(existingNode);
                  openSet.add(neighbor);
               }
            }
         }
      }

      return null;
   }

   private List<ZombiePathfinder.PathNode> getNeighbors(ZombiePathfinder.PathNode current, BlockPos target) {
      List<ZombiePathfinder.PathNode> neighbors = new ArrayList<>();
      BlockPos pos = current.pos;
      BlockPos[] directions = new BlockPos[]{
         pos.func_177978_c(),
         pos.func_177968_d(),
         pos.func_177974_f(),
         pos.func_177976_e(),
         pos.func_177978_c().func_177984_a(),
         pos.func_177968_d().func_177984_a(),
         pos.func_177974_f().func_177984_a(),
         pos.func_177976_e().func_177984_a(),
         pos.func_177984_a(),
         pos.func_177977_b()
      };

      for (BlockPos nextPos : directions) {
         if (this.canWalkTo(nextPos)) {
            double moveCost = 1.0;
            if (nextPos.func_177956_o() > pos.func_177956_o()) {
               moveCost = 1.5;
            }

            neighbors.add(
               new ZombiePathfinder.PathNode(nextPos, current.gScore + moveCost, current.blocksUsed, this.heuristic(nextPos, target), current, false)
            );
         } else if (this.canPlaceBlockAt(nextPos) && current.blocksUsed < this.maxBlocksToUse) {
            double moveCost = 3.0;
            neighbors.add(
               new ZombiePathfinder.PathNode(nextPos, current.gScore + moveCost, current.blocksUsed + 1, this.heuristic(nextPos, target), current, true)
            );
         }
      }

      return neighbors;
   }

   private boolean canWalkTo(BlockPos pos) {
      if (this.world.func_175623_d(pos) && this.world.func_175623_d(pos.func_177984_a())) {
         BlockPos below = pos.func_177977_b();
         return this.world.func_180495_p(below).func_185913_b();
      } else {
         return false;
      }
   }

   private boolean canPlaceBlockAt(BlockPos pos) {
      if (!this.world.func_175623_d(pos)) {
         return false;
      } else {
         BlockPos below = pos.func_177977_b();
         if (this.world.func_180495_p(below).func_185913_b()) {
            return true;
         } else {
            for (BlockPos adjacent : new BlockPos[]{pos.func_177978_c(), pos.func_177968_d(), pos.func_177974_f(), pos.func_177976_e()}) {
               if (this.world.func_180495_p(adjacent).func_185913_b()) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private double heuristic(BlockPos from, BlockPos to) {
      int dx = Math.abs(to.func_177958_n() - from.func_177958_n());
      int dy = Math.abs(to.func_177956_o() - from.func_177956_o());
      int dz = Math.abs(to.func_177952_p() - from.func_177952_p());
      return dx + dz + dy * 1.5;
   }

   private ZombiePathfinder.ZombiePath reconstructPath(ZombiePathfinder.PathNode endNode) {
      List<ZombiePathfinder.PathStep> steps = new ArrayList<>();

      for (ZombiePathfinder.PathNode current = endNode; current != null; current = current.parent) {
         steps.add(new ZombiePathfinder.PathStep(current.pos, current.requiresBlockPlacement));
      }

      Collections.reverse(steps);
      return new ZombiePathfinder.ZombiePath(steps);
   }

   private static class PathNode {
      final BlockPos pos;
      final double gScore;
      final int blocksUsed;
      final double fScore;
      final ZombiePathfinder.PathNode parent;
      final boolean requiresBlockPlacement;

      PathNode(BlockPos pos, double gScore, int blocksUsed, double hScore, ZombiePathfinder.PathNode parent, boolean requiresBlockPlacement) {
         this.pos = pos;
         this.gScore = gScore;
         this.blocksUsed = blocksUsed;
         this.fScore = gScore + hScore;
         this.parent = parent;
         this.requiresBlockPlacement = requiresBlockPlacement;
      }
   }

   public static class PathStep {
      public final BlockPos pos;
      public final boolean requiresBlockPlacement;

      public PathStep(BlockPos pos, boolean requiresBlockPlacement) {
         this.pos = pos;
         this.requiresBlockPlacement = requiresBlockPlacement;
      }
   }

   public static class ZombiePath {
      private final List<ZombiePathfinder.PathStep> steps;

      public ZombiePath(List<ZombiePathfinder.PathStep> steps) {
         this.steps = steps;
      }

      public List<ZombiePathfinder.PathStep> getSteps() {
         return this.steps;
      }

      public int getBlocksRequired() {
         return (int)this.steps.stream().filter(s -> s.requiresBlockPlacement).count();
      }
   }
}
