package com.hostilemobs.ai;

import com.hostilemobs.HostileMobsMod;
import com.hostilemobs.pathfinding.GlobalPathManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EntityAIZombieBlockPlace extends EntityAIBase {
   private final EntityZombie zombie;
   private final World world;
   private EntityAIZombieBlockPlace.ZombieBlockInventory inventory;
   private BlockPos lastTargetPos;
   private int pathRecalculationDelay;
   private static final int PATH_RECALC_INTERVAL = 20;
   private static final double BLOCK_PLACE_RANGE = 4.0;
   private static final Map<EntityZombie, EntityAIZombieBlockPlace.ZombieBlockInventory> ZOMBIE_INVENTORIES = new WeakHashMap<>();

   public EntityAIZombieBlockPlace(EntityZombie zombie) {
      this.zombie = zombie;
      this.world = zombie.field_70170_p;
      if (!ZOMBIE_INVENTORIES.containsKey(zombie)) {
         ZOMBIE_INVENTORIES.put(zombie, new EntityAIZombieBlockPlace.ZombieBlockInventory());
      }

      this.inventory = ZOMBIE_INVENTORIES.get(zombie);
      this.func_75248_a(1);
   }

   public static EntityAIZombieBlockPlace.ZombieBlockInventory getInventory(EntityZombie zombie) {
      return ZOMBIE_INVENTORIES.get(zombie);
   }

   public boolean func_75250_a() {
      EntityLivingBase target = this.zombie.func_70638_az();
      if (target != null && target.func_70089_S()) {
         boolean hasBlocks = this.inventory.hasBlocks();
         if (this.zombie.field_70173_aa % 100 == 0) {
            HostileMobsMod.LOGGER
               .info(
                  "[DEBUG] Zombie {} shouldExecute={}: target={}, hasBlocks={}, blockCount={}",
                  this.zombie.func_145782_y(),
                  hasBlocks,
                  target.func_70005_c_(),
                  hasBlocks,
                  this.inventory.getBlockCount()
               );
         }

         return hasBlocks;
      } else {
         if (this.zombie.field_70173_aa % 100 == 0) {
            HostileMobsMod.LOGGER.info("[DEBUG] Zombie {} shouldExecute=FALSE: no target", this.zombie.func_145782_y());
         }

         return false;
      }
   }

   public boolean func_75253_b() {
      EntityLivingBase target = this.zombie.func_70638_az();
      boolean result = target != null && target.func_70089_S() && this.inventory.hasBlocks();
      if (!result && this.zombie.field_70173_aa % 100 == 0) {
         HostileMobsMod.LOGGER
            .info(
               "[DEBUG] Zombie {} shouldContinueExecuting=FALSE: target={}, hasBlocks={}",
               this.zombie.func_145782_y(),
               target != null,
               this.inventory.hasBlocks()
            );
      }

      return result;
   }

   public void func_75249_e() {
      this.pathRecalculationDelay = 0;
      HostileMobsMod.LOGGER.info("[DEBUG] Zombie {} STARTED EXECUTING block placement AI", this.zombie.func_145782_y());
   }

   public void func_75246_d() {
      EntityLivingBase target = this.zombie.func_70638_az();
      if (target != null) {
         BlockPos zombiePos = this.zombie.func_180425_c();
         BlockPos targetPos = target.func_180425_c();
         double verticalDist = targetPos.func_177956_o() - zombiePos.func_177956_o();
         double horizontalDist = Math.sqrt(this.zombie.func_70092_e(targetPos.func_177958_n(), zombiePos.func_177956_o(), targetPos.func_177952_p()));
         if (this.zombie.func_70661_as().func_75500_f()) {
            this.zombie.func_70661_as().func_75497_a(target, 1.0);
            if (this.zombie.field_70173_aa % 100 == 0) {
               HostileMobsMod.LOGGER
                  .info("[DEBUG] Zombie {} pathfinding to target at distance {}", this.zombie.func_145782_y(), Math.sqrt(this.zombie.func_70068_e(target)));
            }
         }

         this.pathRecalculationDelay--;
         if (this.pathRecalculationDelay <= 0) {
            this.pathRecalculationDelay = 20;
            BlockPos placePos = this.findBlockPlacementPosition(target);
            if (this.zombie.field_70173_aa % 20 == 0) {
               HostileMobsMod.LOGGER
                  .info(
                     "[DEBUG] Zombie {} checking placement: zombieY={}, targetY={}, vertDist={}, horizDist={}, hasPath={}, placePos={}, hasBlocks={}",
                     this.zombie.func_145782_y(),
                     zombiePos.func_177956_o(),
                     targetPos.func_177956_o(),
                     verticalDist,
                     horizontalDist,
                     !this.zombie.func_70661_as().func_75500_f(),
                     placePos,
                     this.inventory.hasBlocks()
                  );
            }

            if (placePos != null && this.inventory.hasBlocks()) {
               HostileMobsMod.LOGGER
                  .info("[DEBUG] Zombie {} PLACING BLOCK at {} (blocks left: {})", this.zombie.func_145782_y(), placePos, this.inventory.getBlockCount() - 1);
               this.placeBlock(placePos);
            }
         }
      }
   }

   private boolean shouldPlaceBlock(EntityLivingBase target) {
      Path currentPath = this.zombie.func_70661_as().func_75505_d();
      return currentPath != null && !currentPath.func_75879_b() ? false : this.checkDirectObstacle(target);
   }

   private boolean checkDirectObstacle(EntityLivingBase target) {
      BlockPos zombiePos = this.zombie.func_180425_c();
      BlockPos targetPos = target.func_180425_c();
      if (targetPos.func_177956_o() > zombiePos.func_177956_o()) {
         double dx = targetPos.func_177958_n() - zombiePos.func_177958_n();
         double dz = targetPos.func_177952_p() - zombiePos.func_177952_p();
         double distance = Math.sqrt(dx * dx + dz * dz);
         if (distance > 0.1) {
            dx /= distance;
            dz /= distance;
            BlockPos checkPos = zombiePos.func_177982_a((int)Math.round(dx), 0, (int)Math.round(dz));
            return !this.world.func_175623_d(checkPos) || !this.world.func_175623_d(checkPos.func_177984_a());
         }
      }

      return false;
   }

   private BlockPos findBlockPlacementPosition(EntityLivingBase target) {
      BlockPos zombiePos = this.zombie.func_180425_c();
      BlockPos targetPos = target.func_180425_c();
      double dx = targetPos.func_177958_n() - zombiePos.func_177958_n();
      double dy = targetPos.func_177956_o() - zombiePos.func_177956_o();
      double dz = targetPos.func_177952_p() - zombiePos.func_177952_p();
      double horizontalDist = Math.sqrt(dx * dx + dz * dz);
      if (horizontalDist > 0.1) {
         dx /= horizontalDist;
         dz /= horizontalDist;
      } else {
         double angle = this.zombie.func_145782_y() * 0.5;
         dx = Math.cos(angle);
         dz = Math.sin(angle);
      }

      double effectiveHorizontalThreshold = 10.0;
      if (dy > 10.0) {
         effectiveHorizontalThreshold = Math.max(5.0, 10.0 - dy / 5.0);
         HostileMobsMod.LOGGER
            .info("[DEBUG] Zombie {} adjusted threshold to {} due to large Y gap (dy: {})", this.zombie.func_145782_y(), effectiveHorizontalThreshold, dy);
      }

      if (horizontalDist > effectiveHorizontalThreshold && dy < 5.0) {
         HostileMobsMod.LOGGER
            .info("[DEBUG] Zombie {} TOO FAR - pathfinding closer first (horizontal dist: {}, dy: {})", this.zombie.func_145782_y(), horizontalDist, dy);
         return null;
      } else if (horizontalDist <= 10.0 && dy > 1.0 && this.zombie.func_70661_as().func_75500_f()) {
         HostileMobsMod.LOGGER
            .info("[DEBUG] Zombie {} NO PATH - building staircase (dy: {}, horizontal dist: {})", this.zombie.func_145782_y(), dy, horizontalDist);
         return this.findStaircasePosition(zombiePos, dx, dz, targetPos);
      } else if (horizontalDist < 5.0 && dy > 3.0) {
         HostileMobsMod.LOGGER
            .info("[DEBUG] Zombie {} AT BASE - building staircase (dy: {}, horizontal dist: {})", this.zombie.func_145782_y(), dy, horizontalDist);
         return this.findStaircasePosition(zombiePos, dx, dz, targetPos);
      } else {
         return null;
      }
   }

   private BlockPos findStaircasePosition(BlockPos zombiePos, double dx, double dz, BlockPos targetPos) {
      for (int step = 1; step <= 3; step++) {
         int forwardX = (int)Math.round(dx * step);
         int forwardZ = (int)Math.round(dz * step);
         if (forwardX != 0 || forwardZ != 0) {
            BlockPos stairPos = zombiePos.func_177982_a(forwardX, step, forwardZ);
            if (!this.isAtZombiePosition(stairPos) && this.world.func_175623_d(stairPos) && this.canPlaceBlockAt(stairPos)) {
               HostileMobsMod.LOGGER.info("[DEBUG] Staircase: placing diagonal step at {} (forward: {}, up: {})", stairPos, step, step);
               return stairPos;
            }
         }
      }

      for (int dist = 1; dist <= 2; dist++) {
         BlockPos forward = zombiePos.func_177982_a((int)Math.round(dx * dist), 0, (int)Math.round(dz * dist));
         if (!this.isAtZombiePosition(forward) && this.world.func_175623_d(forward) && this.canPlaceBlockAt(forward)) {
            HostileMobsMod.LOGGER.info("[DEBUG] Staircase: placing base block at {}", forward);
            return forward;
         }
      }

      return null;
   }

   private BlockPos findStraightUpPosition(BlockPos zombiePos, double dx, double dz) {
      for (int height = 2; height <= 4; height++) {
         BlockPos upPos = zombiePos.func_177981_b(height);
         if (this.world.func_175623_d(upPos) && this.canPlaceBlockAt(upPos)) {
            HostileMobsMod.LOGGER.info("[DEBUG] Straight up: placing at height {}", height);
            return upPos;
         }
      }

      return null;
   }

   private BlockPos findHorizontalPosition(BlockPos zombiePos, double dx, double dz) {
      for (int dist = 1; dist <= 3; dist++) {
         BlockPos forward = zombiePos.func_177982_a((int)Math.round(dx * dist), 0, (int)Math.round(dz * dist));
         if (!this.isAtZombiePosition(forward) && this.world.func_175623_d(forward) && this.canPlaceBlockAt(forward)) {
            HostileMobsMod.LOGGER.info("[DEBUG] Horizontal: placing bridge block at {}", forward);
            return forward;
         }
      }

      return null;
   }

   private boolean isAtZombiePosition(BlockPos pos) {
      BlockPos zombiePos = this.zombie.func_180425_c();
      return pos.func_177958_n() == zombiePos.func_177958_n() && pos.func_177952_p() == zombiePos.func_177952_p()
         ? pos.func_177956_o() < zombiePos.func_177956_o() + 2
         : false;
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

   private void placeBlock(BlockPos pos) {
      Block blockToPlace = this.inventory.consumeBlock();
      if (blockToPlace != null && this.zombie.func_70011_f(pos.func_177958_n(), pos.func_177956_o(), pos.func_177952_p()) < 4.0) {
         this.world.func_180501_a(pos, blockToPlace.func_176223_P(), 3);
         HostileMobsMod.LOGGER
            .info(
               "[DEBUG] Zombie {} SUCCESSFULLY PLACED {} at {} (blocks remaining: {})",
               this.zombie.func_145782_y(),
               blockToPlace.func_149732_F(),
               pos,
               this.inventory.getBlockCount()
            );
         EntityLivingBase target = this.zombie.func_70638_az();
         if (target instanceof EntityPlayer) {
            GlobalPathManager.markBlockPlaced((EntityPlayer)target, pos);
         }

         if (target != null) {
            this.zombie.func_70661_as().func_75497_a(target, 1.0);
         }
      } else if (this.zombie.field_70173_aa % 20 == 0) {
         HostileMobsMod.LOGGER
            .info(
               "[DEBUG] Zombie {} FAILED to place block: blockToPlace={}, distance={}, range={}",
               this.zombie.func_145782_y(),
               blockToPlace != null,
               this.zombie.func_70011_f(pos.func_177958_n(), pos.func_177956_o(), pos.func_177952_p()),
               4.0
            );
      }
   }

   public static class ZombieBlockInventory {
      private final List<Block> blocks = new ArrayList<>();

      public ZombieBlockInventory() {
         this.initializeBlocks();
      }

      private void initializeBlocks() {
         Random random = new Random();
         int totalBlocks = 15 + random.nextInt(6);
         Block[] availableBlocks = new Block[]{Blocks.field_150347_e, Blocks.field_150346_d, Blocks.field_150346_d, Blocks.field_150341_Y};

         for (int i = 0; i < totalBlocks; i++) {
            this.blocks.add(availableBlocks[random.nextInt(availableBlocks.length)]);
         }
      }

      public boolean hasBlocks() {
         return !this.blocks.isEmpty();
      }

      public Block consumeBlock() {
         return this.blocks.isEmpty() ? null : this.blocks.remove(0);
      }

      public int getBlockCount() {
         return this.blocks.size();
      }
   }
}
