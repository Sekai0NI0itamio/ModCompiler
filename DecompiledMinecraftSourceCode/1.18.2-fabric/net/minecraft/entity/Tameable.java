/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.entity;

import java.util.UUID;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

public interface Tameable {
    @Nullable
    public UUID getOwnerUuid();

    @Nullable
    public Entity getOwner();
}

