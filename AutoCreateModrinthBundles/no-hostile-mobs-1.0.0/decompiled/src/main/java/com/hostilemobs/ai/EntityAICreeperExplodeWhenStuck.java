package com.hostilemobs.ai;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.util.math.BlockPos;

public class EntityAICreeperExplodeWhenStuck extends EntityAIBase {
   private final EntityCreeper creeper;
   private static final Map<EntityCreeper, EntityAICreeperExplodeWhenStuck.StuckTracker> STUCK_TRACKERS = new HashMap<>();
   private static final int STUCK_TIME_THRESHOLD = 100;
   private static final double STUCK_RADIUS = 2.0;
   private static final double INSTANT_EXPLODE_DISTANCE = 36.0;
   private final double moveSpeed;

   public EntityAICreeperExplodeWhenStuck(EntityCreeper creeper) {
      this.creeper = creeper;
      this.moveSpeed = 1.0;
      if (!STUCK_TRACKERS.containsKey(creeper)) {
         STUCK_TRACKERS.put(creeper, new EntityAICreeperExplodeWhenStuck.StuckTracker());
      }

      this.func_75248_a(1);
   }

   public boolean func_75250_a() {
      EntityLivingBase target = this.creeper.func_70638_az();
      return target != null && target.func_70089_S();
   }

   public boolean func_75253_b() {
      EntityLivingBase target = this.creeper.func_70638_az();
      return target != null && target.func_70089_S();
   }

   public void func_75249_e() {
      EntityAICreeperExplodeWhenStuck.StuckTracker tracker = STUCK_TRACKERS.get(this.creeper);
      if (tracker != null) {
         tracker.reset(this.creeper.func_180425_c());
      }
   }

   public void func_75246_d() {
      EntityLivingBase target = this.creeper.func_70638_az();
      if (target != null) {
         double distanceSq = this.creeper.func_70068_e(target);
         if (distanceSq <= 36.0) {
            this.creeper.func_146079_cb();
         } else {
            if (this.creeper.func_70661_as().func_75500_f()) {
               this.creeper.func_70661_as().func_75497_a(target, this.moveSpeed);
            }

            this.creeper.func_70671_ap().func_75651_a(target, 30.0F, 30.0F);
            if (this.creeper.field_70173_aa % 10 == 0) {
               EntityAICreeperExplodeWhenStuck.StuckTracker tracker = STUCK_TRACKERS.get(this.creeper);
               if (tracker != null) {
                  BlockPos currentPos = this.creeper.func_180425_c();
                  if (tracker.isStuckAt(currentPos, 2.0)) {
                     tracker.incrementStuckTime();
                     if (tracker.getStuckTime() >= 100) {
                        this.creeper.func_146079_cb();
                        tracker.reset(currentPos);
                     }
                  } else {
                     tracker.reset(currentPos);
                  }
               }
            }
         }
      }
   }

   private static class StuckTracker {
      private BlockPos lastPos = BlockPos.field_177992_a;
      private int stuckTime = 0;

      public StuckTracker() {
      }

      public void reset(BlockPos pos) {
         this.lastPos = pos;
         this.stuckTime = 0;
      }

      public boolean isStuckAt(BlockPos currentPos, double radius) {
         if (this.lastPos == null) {
            return false;
         } else {
            double distanceSq = currentPos.func_177951_i(this.lastPos);
            return distanceSq <= radius * radius;
         }
      }

      public void incrementStuckTime() {
         this.stuckTime += 10;
      }

      public int getStuckTime() {
         return this.stuckTime;
      }
   }
}
