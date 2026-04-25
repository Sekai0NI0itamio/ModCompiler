/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.world;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BiomeGenerationSettingsBuilder extends BiomeGenerationSettings.Builder
{
    public BiomeGenerationSettingsBuilder(BiomeGenerationSettings orig)
    {
        orig.getCarvingStages().forEach(k -> {
            f_47827_.put(k, new ArrayList<>());
            orig.m_204187_(k).forEach(v -> f_47827_.get(k).add(v));
        });
        orig.m_47818_().forEach(l -> {
            final ArrayList<Holder<PlacedFeature>> featureList = new ArrayList<>();
            l.forEach(featureList::add);
            f_47828_.add(featureList);
        });
    }

    public List<Holder<PlacedFeature>> getFeatures(GenerationStep.Decoration stage) {
        m_47832_(stage.ordinal());
        return f_47828_.get(stage.ordinal());
    }

    public List<Holder<ConfiguredWorldCarver<?>>> getCarvers(GenerationStep.Carving stage) {
        return f_47827_.computeIfAbsent(stage, key -> new ArrayList<>());
    }
}