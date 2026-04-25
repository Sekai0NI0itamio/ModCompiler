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

import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.surfacebuilders.ConfiguredSurfaceBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class BiomeGenerationSettingsBuilder extends BiomeGenerationSettings.Builder
{
    public BiomeGenerationSettingsBuilder(BiomeGenerationSettings orig)
    {
        f_47826_ = Optional.of(orig.m_47821_());
        orig.getCarvingStages().forEach(k -> f_47827_.put(k, new ArrayList<>(orig.m_47799_(k))));
        orig.m_47818_().forEach(l -> f_47828_.add(new ArrayList<>(l)));
        f_47829_.addAll(orig.m_47796_());
    }

    public List<Supplier<ConfiguredFeature<?, ?>>> getFeatures(GenerationStep.Decoration stage) {
        m_47832_(stage.ordinal());
        return f_47828_.get(stage.ordinal());
    }

    public Optional<Supplier<ConfiguredSurfaceBuilder<?>>> getSurfaceBuilder() {
        return f_47826_;
    }

    public List<Supplier<ConfiguredWorldCarver<?>>> getCarvers(GenerationStep.Carving stage) {
        return f_47827_.computeIfAbsent(stage, key -> new ArrayList<>());
    }

    public List<Supplier<ConfiguredStructureFeature<?, ?>>> getStructures() {
        return f_47829_;
    }
}