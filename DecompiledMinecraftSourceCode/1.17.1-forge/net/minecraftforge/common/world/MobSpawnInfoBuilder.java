/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.common.world;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;

public class MobSpawnInfoBuilder extends MobSpawnSettings.Builder
{
    private final Set<MobCategory> typesView = Collections.unmodifiableSet(this.f_48362_.keySet());
    private final Set<EntityType<?>> costView = Collections.unmodifiableSet(this.f_48363_.keySet());

    public MobSpawnInfoBuilder(MobSpawnSettings orig)
    {
        orig.getSpawnerTypes().forEach(k -> {
            f_48362_.get(k).clear();
            f_48362_.get(k).addAll(orig.m_151798_(k).m_146338_());
        });
        orig.getEntityTypes().forEach(k -> f_48363_.put(k, orig.m_48345_(k)));
        f_48364_ = orig.m_48344_();
        f_48365_ = orig.m_48353_();
    }

    public Set<MobCategory> getSpawnerTypes()
    {
        return this.typesView;
    }

    public List<MobSpawnSettings.SpawnerData> getSpawner(MobCategory type)
    {
        return this.f_48362_.get(type);
    }

    public Set<EntityType<?>> getEntityTypes()
    {
        return this.costView;
    }

    @Nullable
    public MobSpawnSettings.MobSpawnCost getCost(EntityType<?> type)
    {
        return this.f_48363_.get(type);
    }

    public float getProbability()
    {
        return this.f_48364_;
    }

    public MobSpawnInfoBuilder disablePlayerSpawn()
    {
        this.f_48365_ = false;
        return this;
    }
}