package com.hostilemobs.ai;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EntityAIZombieBreakBlocks extends EntityAIBase {
   private final EntityZombie zombie;
   private final World world;
   private static final Map<EntityZombie, EntityAIZombieBreakBlocks.BlockBreakTracker> BREAK_TRACKERS = new HashMap<>();
   private static final float BREAK_SPEED_MULTIPLIER = 1.0F;
   private BlockPos currentBreakingBlock;
   private int breakTicks;

   public EntityAIZombieBreakBlocks(EntityZombie zombie) {
      this.zombie = zombie;
      this.world = zombie.field_70170_p;
      if (!BREAK_TRACKERS.containsKey(zombie)) {
         BREAK_TRACKERS.put(zombie, new EntityAIZombieBreakBlocks.BlockBreakTracker());
      }

      this.func_75248_a(3);
   }

   public boolean func_75250_a() {
      EntityLivingBase target = this.zombie.func_70638_az();
      return target != null && target.func_70089_S() ? this.findBlockToBreak() != null : false;
   }

   public boolean func_75253_b() {
      return this.func_75250_a() && this.currentBreakingBlock != null;
   }

   public void func_75249_e() {
      this.currentBreakingBlock = this.findBlockToBreak();
      this.breakTicks = 0;
   }

   public void func_75251_c() {
      if (this.currentBreakingBlock != null) {
         this.world.func_175715_c(this.zombie.func_145782_y(), this.currentBreakingBlock, -1);
         this.currentBreakingBlock = null;
      }

      this.breakTicks = 0;
   }

   public void func_75246_d() {
      if (this.currentBreakingBlock == null) {
         this.currentBreakingBlock = this.findBlockToBreak();
         if (this.currentBreakingBlock == null) {
            return;
         }
      }

      this.zombie
         .func_70671_ap()
         .func_75650_a(
            this.currentBreakingBlock.func_177958_n() + 0.5,
            this.currentBreakingBlock.func_177956_o() + 0.5,
            this.currentBreakingBlock.func_177952_p() + 0.5,
            10.0F,
            this.zombie.func_70646_bf()
         );
      this.breakTicks++;
      EntityAIZombieBreakBlocks.BlockBreakTracker tracker = BREAK_TRACKERS.get(this.zombie);
      if (tracker != null) {
         IBlockState state = this.world.func_180495_p(this.currentBreakingBlock);
         float hardness = state.func_185887_b(this.world, this.currentBreakingBlock);
         if (!(hardness < 0.0F) && state.func_177230_c() != Blocks.field_150357_h) {
            float breakProgress = 1.0F / Math.max(hardness, 0.5F) * 1.0F * 0.05F;
            tracker.addProgress(this.currentBreakingBlock, breakProgress);
            int progressLevel = (int)(tracker.getProgress(this.currentBreakingBlock) * 10.0F);
            this.world.func_175715_c(this.zombie.func_145782_y(), this.currentBreakingBlock, progressLevel);
            if (tracker.getProgress(this.currentBreakingBlock) >= 1.0F) {
               this.world.func_175655_b(this.currentBreakingBlock, true);
               tracker.resetProgress(this.currentBreakingBlock);
               this.currentBreakingBlock = null;
            }
         } else {
            this.func_75251_c();
         }
      }
   }

   private BlockPos findBlockToBreak() {
      EntityLivingBase target = this.zombie.func_70638_az();
      if (target == null) {
         return null;
      } else {
         BlockPos zombiePos = this.zombie.func_180425_c();
         double dx = target.field_70165_t - this.zombie.field_70165_t;
         double dz = target.field_70161_v - this.zombie.field_70161_v;
         double distance = Math.sqrt(dx * dx + dz * dz);
         if (distance < 0.1) {
            return null;
         } else {
            dx /= distance;
            dz /= distance;

            for (int dist = 1; dist <= 2; dist++) {
               BlockPos checkPos = zombiePos.func_177982_a((int)Math.round(dx * dist), 0, (int)Math.round(dz * dist));

               for (int y = 0; y <= 1; y++) {
                  BlockPos blockPos = checkPos.func_177981_b(y);
                  IBlockState state = this.world.func_180495_p(blockPos);
                  if (!this.world.func_175623_d(blockPos)
                     && state.func_177230_c() != Blocks.field_150357_h
                     && state.func_185887_b(this.world, blockPos) >= 0.0F) {
                     return blockPos;
                  }
               }
            }

            return null;
         }
      }
   }

   private static class BlockBreakTracker {
      private final Map<BlockPos, Float> breakProgress = new HashMap<>();

      private BlockBreakTracker() {
      }

      public void addProgress(BlockPos pos, float amount) {
         float current = this.breakProgress.getOrDefault(pos, 0.0F);
         this.breakProgress.put(pos, Math.min(1.0F, current + amount));
      }

      public float getProgress(BlockPos pos) {
         return this.breakProgress.getOrDefault(pos, 0.0F);
      }

      public void resetProgress(BlockPos pos) {
         this.breakProgress.remove(pos);
      }
   }
}
