/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.entity;

import net.minecraft.entity.Entity;
import net.minecraft.network.IPacket;

public abstract class PartEntity<T extends Entity> extends Entity {
    private final T parent;

    public PartEntity(T parent) {
        super(parent.func_200600_R(), parent.field_70170_p);
        this.parent = parent;
    }

    public T getParent() {
        return parent;
    }

    @Override
    public IPacket<?> func_213297_N() {
        throw new UnsupportedOperationException();
    }
}
