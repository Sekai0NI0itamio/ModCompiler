package com.hostilemobs.ai;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EntityAISlimeBreakBlocks extends EntityAIBase {
   private final EntitySlime slime;
   private final World world;
   private static final Map<EntitySlime, EntityAISlimeBreakBlocks.BlockBreakTracker> BREAK_TRACKERS = new HashMap<>();
   private static final float BREAK_SPEED_MULTIPLIER = 2.0F;
   private int jumpCooldown;

   public EntityAISlimeBreakBlocks(EntitySlime slime) {
      this.slime = slime;
      this.world = slime.field_70170_p;
      if (!BREAK_TRACKERS.containsKey(slime)) {
         BREAK_TRACKERS.put(slime, new EntityAISlimeBreakBlocks.BlockBreakTracker());
      }

      this.func_75248_a(1);
   }

   public boolean func_75250_a() {
      EntityLivingBase target = this.slime.func_70638_az();
      return target != null && target.func_70089_S() ? this.findBlockToBreak() != null : false;
   }

   public boolean func_75253_b() {
      return this.func_75250_a();
   }

   public void func_75246_d() {
      EntityLivingBase target = this.slime.func_70638_az();
      if (target != null) {
         BlockPos blockToBreak = this.findBlockToBreak();
         if (blockToBreak != null) {
            EntityAISlimeBreakBlocks.BlockBreakTracker tracker = BREAK_TRACKERS.get(this.slime);
            if (tracker != null) {
               this.jumpCooldown--;
               if (this.jumpCooldown <= 0 && this.slime.field_70122_E) {
                  this.slime.field_70181_x = 0.42;
                  this.jumpCooldown = 10;
                  IBlockState state = this.world.func_180495_p(blockToBreak);
                  float hardness = state.func_185887_b(this.world, blockToBreak);
                  if (hardness >= 0.0F) {
                     float breakProgress = 1.0F / hardness * 2.0F * 0.1F;
                     tracker.addProgress(blockToBreak, breakProgress);
                     this.world.func_175715_c(this.slime.func_145782_y(), blockToBreak, (int)(tracker.getProgress(blockToBreak) * 10.0F));
                     if (tracker.getProgress(blockToBreak) >= 1.0F) {
                        this.world.func_175655_b(blockToBreak, true);
                        tracker.resetProgress(blockToBreak);
                     }
                  }
               }
            }
         }
      }
   }

   private BlockPos findBlockToBreak() {
      EntityLivingBase target = this.slime.func_70638_az();
      if (target == null) {
         return null;
      } else {
         BlockPos slimePos = this.slime.func_180425_c();
         int slimeSize = this.slime.func_70809_q();
         double dx = target.field_70165_t - this.slime.field_70165_t;
         double dz = target.field_70161_v - this.slime.field_70161_v;
         double distance = Math.sqrt(dx * dx + dz * dz);
         if (distance < 0.1) {
            return null;
         } else {
            dx /= distance;
            dz /= distance;

            for (int dist = 1; dist <= slimeSize; dist++) {
               BlockPos checkPos = slimePos.func_177982_a((int)Math.round(dx * dist), 0, (int)Math.round(dz * dist));

               for (int y = 0; y < slimeSize; y++) {
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
