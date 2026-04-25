/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.entity.mob;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public abstract class AmbientEntity
extends MobEntity {
    protected AmbientEntity(EntityType<? extends AmbientEntity> entityType, World world) {
        super((EntityType<? extends MobEntity>)entityType, world);
    }

    @Override
    public boolean canBeLeashedBy(PlayerEntity player) {
        return false;
    }
}

