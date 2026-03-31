package com.mobvision;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;

final class EntityListBridge {

    private EntityListBridge() {
    }

    static ResourceLocation getEntityKey(Entity entity) {
        return EntityList.getKey(entity);
    }
}
