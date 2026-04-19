package com.hostilemobs.ai;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.util.math.MathHelper;

public class EntityAISkeletonAttackWhileMoving extends EntityAIBase {
   private final EntitySkeleton skeleton;
   private final double moveSpeed;
   private int attackCooldown;
   private final int maxAttackCooldown;
   private final float attackRadius;
   private final float maxAttackDistance;
   private int seeTime;

   public EntityAISkeletonAttackWhileMoving(EntitySkeleton skeleton, double moveSpeed, int attackCooldown, float maxAttackDistance) {
      this.skeleton = skeleton;
      this.moveSpeed = moveSpeed;
      this.attackCooldown = -1;
      this.maxAttackCooldown = attackCooldown;
      this.attackRadius = maxAttackDistance;
      this.maxAttackDistance = maxAttackDistance * maxAttackDistance;
      this.func_75248_a(3);
   }

   public boolean func_75250_a() {
      EntityLivingBase target = this.skeleton.func_70638_az();
      return target != null && target.func_70089_S();
   }

   public boolean func_75253_b() {
      return this.func_75250_a() || !this.skeleton.func_70661_as().func_75500_f();
   }

   public void func_75251_c() {
      this.seeTime = 0;
      this.attackCooldown = -1;
   }

   public void func_75246_d() {
      EntityLivingBase target = this.skeleton.func_70638_az();
      if (target != null) {
         double distanceSq = this.skeleton.func_70092_e(target.field_70165_t, target.func_174813_aQ().field_72338_b, target.field_70161_v);
         boolean canSee = this.skeleton.func_70635_at().func_75522_a(target);
         if (canSee) {
            this.seeTime++;
         } else {
            this.seeTime = 0;
         }

         if (this.skeleton.func_70661_as().func_75500_f() || distanceSq < 256.0) {
            this.skeleton.func_70661_as().func_75497_a(target, this.moveSpeed);
         }

         this.skeleton.func_70671_ap().func_75651_a(target, 30.0F, 30.0F);
         if (--this.attackCooldown <= 0) {
            if (!canSee) {
               return;
            }

            float distanceRatio = MathHelper.func_76133_a(distanceSq) / this.attackRadius;
            float clampedRatio = MathHelper.func_76131_a(distanceRatio, 0.1F, 1.0F);
            if (this.skeleton instanceof IRangedAttackMob) {
               this.skeleton.func_82196_d(target, clampedRatio);
            }

            this.attackCooldown = MathHelper.func_76141_d(distanceRatio * (this.maxAttackCooldown - this.maxAttackCooldown / 2) + this.maxAttackCooldown / 2.0F);
         } else if (this.attackCooldown < 0) {
            this.attackCooldown = MathHelper.func_76141_d(MathHelper.func_76133_a(distanceSq) / this.attackRadius * this.maxAttackCooldown);
         }
      }
   }
}
