/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.world;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.world.biome.MobSpawnInfo;

public class MobSpawnInfoBuilder extends MobSpawnInfo.Builder
{
    private final Set<EntityClassification> typesView = Collections.unmodifiableSet(this.field_242567_a.keySet());
    private final Set<EntityType<?>> costView = Collections.unmodifiableSet(this.field_242568_b.keySet());

    public MobSpawnInfoBuilder(MobSpawnInfo orig)
    {
        orig.getSpawnerTypes().forEach(k -> {
            field_242567_a.get(k).clear();
            field_242567_a.get(k).addAll(new java.util.ArrayList<>(orig.func_242559_a(k)));
        });
        orig.getEntityTypes().forEach(k -> field_242568_b.put(k, orig.func_242558_a(k)));
        field_242569_c = orig.func_242557_a();
        field_242570_d = orig.func_242562_b();
    }

    public Set<EntityClassification> getSpawnerTypes()
    {
        return this.typesView;
    }

    public List<MobSpawnInfo.Spawners> getSpawner(EntityClassification type)
    {
        return this.field_242567_a.get(type);
    }

    public Set<EntityType<?>> getEntityTypes()
    {
        return this.costView;
    }

    @Nullable
    public MobSpawnInfo.SpawnCosts getCost(EntityType<?> type)
    {
        return this.field_242568_b.get(type);
    }

    public float getProbability()
    {
        return this.field_242569_c;
    }

    public MobSpawnInfoBuilder disablePlayerSpawn()
    {
        this.field_242570_d = false;
        return this;
    }
}