package com.hostilemobs.ai;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Custom AI for zombies that allows them to place blocks to reach their target.
 * This AI recalculates pathfinding to account for block placement possibilities.
 */
public class EntityAIZombieBlockPlace extends EntityAIBase {
    private final EntityZombie zombie;
    private final World world;
    private ZombieBlockInventory inventory;
    private BlockPos lastTargetPos;
    private int pathRecalculationDelay;
    private static final int PATH_RECALC_INTERVAL = 20;
    private static final double BLOCK_PLACE_RANGE = 4.0D;
    private static final Map<EntityZombie, ZombieBlockInventory> ZOMBIE_INVENTORIES = new WeakHashMap<>();

    public EntityAIZombieBlockPlace(EntityZombie zombie) {
        this.zombie = zombie;
        this.world = zombie.world;
        
        // Use shared inventory map to persist across AI resets
        if (!ZOMBIE_INVENTORIES.containsKey(zombie)) {
            ZOMBIE_INVENTORIES.put(zombie, new ZombieBlockInventory());
        }
        this.inventory = ZOMBIE_INVENTORIES.get(zombie);
        
        this.setMutexBits(1);
    }
    
    public static ZombieBlockInventory getInventory(EntityZombie zombie) {
        return ZOMBIE_INVENTORIES.get(zombie);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = zombie.getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            return false;
        }
        return inventory.hasBlocks();
    }

    @Override
    public boolean shouldContinueExecuting() {
        EntityLivingBase target = zombie.getAttackTarget();
        return target != null && target.isEntityAlive() && inventory.hasBlocks();
    }

    @Override
    public void startExecuting() {
        pathRecalculationDelay = 0;
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = zombie.getAttackTarget();
        if (target == null) {
            return;
        }

        pathRecalculationDelay--;
        
        if (pathRecalculationDelay <= 0) {
            pathRecalculationDelay = PATH_RECALC_INTERVAL;
            
            // Check if zombie needs to place a block to continue pathfinding
            if (shouldPlaceBlock(target)) {
                BlockPos placePos = findBlockPlacementPosition(target);
                if (placePos != null && inventory.hasBlocks()) {
                    placeBlock(placePos);
                }
            }
        }
    }

    private boolean shouldPlaceBlock(EntityLivingBase target) {
        // Check if there's a vertical or horizontal obstacle
        Path currentPath = zombie.getNavigator().getPath();
        if (currentPath == null || currentPath.isFinished()) {
            return checkDirectObstacle(target);
        }
        return false;
    }

    private boolean checkDirectObstacle(EntityLivingBase target) {
        BlockPos zombiePos = zombie.getPosition();
        BlockPos targetPos = target.getPosition();
        
        // Check if target is higher and there's a wall
        if (targetPos.getY() > zombiePos.getY()) {
            // Check blocks in front of zombie
            double dx = targetPos.getX() - zombiePos.getX();
            double dz = targetPos.getZ() - zombiePos.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            
            if (distance > 0.1D) {
                dx /= distance;
                dz /= distance;
                
                BlockPos checkPos = zombiePos.add((int) Math.round(dx), 0, (int) Math.round(dz));
                return !world.isAirBlock(checkPos) || !world.isAirBlock(checkPos.up());
            }
        }
        
        return false;
    }

    private BlockPos findBlockPlacementPosition(EntityLivingBase target) {
        BlockPos zombiePos = zombie.getPosition();
        BlockPos targetPos = target.getPosition();
        
        // Calculate direction to target
        double dx = targetPos.getX() - zombiePos.getX();
        double dy = targetPos.getY() - zombiePos.getY();
        double dz = targetPos.getZ() - zombiePos.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        
        if (horizontalDist < 0.1D) {
            return null;
        }
        
        // Normalize horizontal direction
        dx /= horizontalDist;
        dz /= horizontalDist;
        
        // Try to place blocks to create a path upward or forward
        List<BlockPos> candidates = new ArrayList<>();
        
        // Forward positions
        for (int dist = 1; dist <= 3; dist++) {
            BlockPos forward = zombiePos.add((int) Math.round(dx * dist), 0, (int) Math.round(dz * dist));
            
            // Check if we can place a block here (air block with solid block below)
            if (world.isAirBlock(forward) && canPlaceBlockAt(forward)) {
                candidates.add(forward);
            }
            
            // Check one block up (for climbing)
            BlockPos forwardUp = forward.up();
            if (world.isAirBlock(forwardUp) && canPlaceBlockAt(forwardUp)) {
                candidates.add(forwardUp);
            }
        }
        
        // Return the first valid candidate
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private boolean canPlaceBlockAt(BlockPos pos) {
        if (!world.isAirBlock(pos)) {
            return false;
        }
        
        // Check if there's a solid block below or adjacent
        BlockPos below = pos.down();
        if (world.getBlockState(below).isFullBlock()) {
            return true;
        }
        
        // Check adjacent blocks for support
        for (BlockPos adjacent : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (world.getBlockState(adjacent).isFullBlock()) {
                return true;
            }
        }
        
        return false;
    }

    private void placeBlock(BlockPos pos) {
        Block blockToPlace = inventory.consumeBlock();
        if (blockToPlace != null && zombie.getDistance(pos.getX(), pos.getY(), pos.getZ()) < BLOCK_PLACE_RANGE) {
            world.setBlockState(pos, blockToPlace.getDefaultState(), 3);
            
            // Recalculate path after placing block
            EntityLivingBase target = zombie.getAttackTarget();
            if (target != null) {
                zombie.getNavigator().tryMoveToEntityLiving(target, 1.0D);
            }
        }
    }

    /**
     * Internal inventory system for zombie blocks
     */
    public static class ZombieBlockInventory {
        private final List<Block> blocks;
        
        public ZombieBlockInventory() {
            this.blocks = new ArrayList<>();
            initializeBlocks();
        }
        
        private void initializeBlocks() {
            Random random = new Random();
            int totalBlocks = 15 + random.nextInt(6); // 15-20 blocks
            
            Block[] availableBlocks = {
                Blocks.COBBLESTONE,
                Blocks.DIRT,
                Blocks.DIRT, // Coarse dirt (metadata 1)
                Blocks.MOSSY_COBBLESTONE
            };
            
            for (int i = 0; i < totalBlocks; i++) {
                blocks.add(availableBlocks[random.nextInt(availableBlocks.length)]);
            }
        }
        
        public boolean hasBlocks() {
            return !blocks.isEmpty();
        }
        
        public Block consumeBlock() {
            if (blocks.isEmpty()) {
                return null;
            }
            return blocks.remove(0);
        }
        
        public int getBlockCount() {
            return blocks.size();
        }
    }
}
