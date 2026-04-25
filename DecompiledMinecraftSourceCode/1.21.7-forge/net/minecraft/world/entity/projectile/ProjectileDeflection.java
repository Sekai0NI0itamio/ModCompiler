package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface ProjectileDeflection {
    ProjectileDeflection NONE = (p_335766_, p_335741_, p_334113_) -> {};
    ProjectileDeflection REVERSE = (p_405562_, p_405563_, p_405564_) -> {
        float f = 170.0F + p_405564_.nextFloat() * 20.0F;
        p_405562_.setDeltaMovement(p_405562_.getDeltaMovement().scale(-0.5));
        p_405562_.setYRot(p_405562_.getYRot() + f);
        p_405562_.yRotO += f;
        p_405562_.hasImpulse = true;
    };
    ProjectileDeflection AIM_DEFLECT = (p_375177_, p_375178_, p_375179_) -> {
        if (p_375178_ != null) {
            Vec3 vec3 = p_375178_.getLookAngle().normalize();
            p_375177_.setDeltaMovement(vec3);
            p_375177_.hasImpulse = true;
        }
    };
    ProjectileDeflection MOMENTUM_DEFLECT = (p_375174_, p_375175_, p_375176_) -> {
        if (p_375175_ != null) {
            Vec3 vec3 = p_375175_.getDeltaMovement().normalize();
            p_375174_.setDeltaMovement(vec3);
            p_375174_.hasImpulse = true;
        }
    };

    void deflect(Projectile p_332034_, @Nullable Entity p_330319_, RandomSource p_333938_);
}
