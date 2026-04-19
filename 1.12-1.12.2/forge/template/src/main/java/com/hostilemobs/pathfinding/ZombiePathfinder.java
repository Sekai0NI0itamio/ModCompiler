package com.hostilemobs.pathfinding;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Custom pathfinding engine for zombies that can place blocks.
 * Extends standard pathfinding to consider block placement as a valid path option.
 */
public class ZombiePathfinder {
    private final World world;
    private final EntityLiving entity;
    private final int maxBlocksToUse;
    
    private static final int MAX_SEARCH_NODES = 500;
    private static final double BLOCK_PLACE_COST = 2.0D;
    
    public ZombiePathfinder(World world, EntityLiving entity, int maxBlocksToUse) {
        this.world = world;
        this.entity = entity;
        this.maxBlocksToUse = maxBlocksToUse;
    }
    
    /**
     * Calculate a path that may include block placements
     */
    public ZombiePath findPath(BlockPos start, BlockPos target) {
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Map<BlockPos, PathNode> allNodes = new HashMap<>();
        Set<BlockPos> closedSet = new HashSet<>();
        
        PathNode startNode = new PathNode(start, 0, 0, heuristic(start, target), null, false);
        openSet.add(startNode);
        allNodes.put(start, startNode);
        
        int nodesExplored = 0;
        
        while (!openSet.isEmpty() && nodesExplored < MAX_SEARCH_NODES) {
            PathNode current = openSet.poll();
            nodesExplored++;
            
            if (current.pos.equals(target) || current.pos.distanceSq(target) < 4.0D) {
                return reconstructPath(current);
            }
            
            closedSet.add(current.pos);
            
            // Explore neighbors
            for (PathNode neighbor : getNeighbors(current, target)) {
                if (closedSet.contains(neighbor.pos)) {
                    continue;
                }
                
                if (neighbor.blocksUsed > maxBlocksToUse) {
                    continue;
                }
                
                PathNode existingNode = allNodes.get(neighbor.pos);
                if (existingNode == null || neighbor.gScore < existingNode.gScore) {
                    allNodes.put(neighbor.pos, neighbor);
                    openSet.remove(existingNode);
                    openSet.add(neighbor);
                }
            }
        }
        
        return null; // No path found
    }
    
    private List<PathNode> getNeighbors(PathNode current, BlockPos target) {
        List<PathNode> neighbors = new ArrayList<>();
        BlockPos pos = current.pos;
        
        // Standard movement directions
        BlockPos[] directions = {
            pos.north(), pos.south(), pos.east(), pos.west(),
            pos.north().up(), pos.south().up(), pos.east().up(), pos.west().up(),
            pos.up(), pos.down()
        };
        
        for (BlockPos nextPos : directions) {
            if (canWalkTo(nextPos)) {
                // Normal walkable path
                double moveCost = 1.0D;
                if (nextPos.getY() > pos.getY()) {
                    moveCost = 1.5D; // Climbing is more expensive
                }
                
                neighbors.add(new PathNode(
                    nextPos,
                    current.gScore + moveCost,
                    current.blocksUsed,
                    heuristic(nextPos, target),
                    current,
                    false
                ));
            } else if (canPlaceBlockAt(nextPos) && current.blocksUsed < maxBlocksToUse) {
                // Can place a block here to create a path
                double moveCost = 1.0D + BLOCK_PLACE_COST;
                
                neighbors.add(new PathNode(
                    nextPos,
                    current.gScore + moveCost,
                    current.blocksUsed + 1,
                    heuristic(nextPos, target),
                    current,
                    true
                ));
            }
        }
        
        return neighbors;
    }
    
    private boolean canWalkTo(BlockPos pos) {
        // Check if position is walkable (air with solid ground below)
        if (!world.isAirBlock(pos) || !world.isAirBlock(pos.up())) {
            return false;
        }
        
        BlockPos below = pos.down();
        return world.getBlockState(below).isFullBlock();
    }
    
    private boolean canPlaceBlockAt(BlockPos pos) {
        if (!world.isAirBlock(pos)) {
            return false;
        }
        
        // Must have solid support below or adjacent
        BlockPos below = pos.down();
        if (world.getBlockState(below).isFullBlock()) {
            return true;
        }
        
        // Check adjacent blocks
        for (BlockPos adjacent : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (world.getBlockState(adjacent).isFullBlock()) {
                return true;
            }
        }
        
        return false;
    }
    
    private double heuristic(BlockPos from, BlockPos to) {
        // Manhattan distance with vertical penalty
        int dx = Math.abs(to.getX() - from.getX());
        int dy = Math.abs(to.getY() - from.getY());
        int dz = Math.abs(to.getZ() - from.getZ());
        return dx + dz + (dy * 1.5D);
    }
    
    private ZombiePath reconstructPath(PathNode endNode) {
        List<PathStep> steps = new ArrayList<>();
        PathNode current = endNode;
        
        while (current != null) {
            steps.add(new PathStep(current.pos, current.requiresBlockPlacement));
            current = current.parent;
        }
        
        Collections.reverse(steps);
        return new ZombiePath(steps);
    }
    
    /**
     * Node in the pathfinding graph
     */
    private static class PathNode {
        final BlockPos pos;
        final double gScore;
        final int blocksUsed;
        final double fScore;
        final PathNode parent;
        final boolean requiresBlockPlacement;
        
        PathNode(BlockPos pos, double gScore, int blocksUsed, double hScore, PathNode parent, boolean requiresBlockPlacement) {
            this.pos = pos;
            this.gScore = gScore;
            this.blocksUsed = blocksUsed;
            this.fScore = gScore + hScore;
            this.parent = parent;
            this.requiresBlockPlacement = requiresBlockPlacement;
        }
    }
    
    /**
     * Represents a complete path with block placement information
     */
    public static class ZombiePath {
        private final List<PathStep> steps;
        
        public ZombiePath(List<PathStep> steps) {
            this.steps = steps;
        }
        
        public List<PathStep> getSteps() {
            return steps;
        }
        
        public int getBlocksRequired() {
            return (int) steps.stream().filter(s -> s.requiresBlockPlacement).count();
        }
    }
    
    /**
     * Single step in a zombie path
     */
    public static class PathStep {
        public final BlockPos pos;
        public final boolean requiresBlockPlacement;
        
        public PathStep(BlockPos pos, boolean requiresBlockPlacement) {
            this.pos = pos;
            this.requiresBlockPlacement = requiresBlockPlacement;
        }
    }
}
