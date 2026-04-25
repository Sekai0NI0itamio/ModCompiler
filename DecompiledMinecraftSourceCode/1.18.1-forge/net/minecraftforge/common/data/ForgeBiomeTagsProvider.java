/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.data;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.common.Tags;

public final class ForgeBiomeTagsProvider extends BiomeTagsProvider
{

    public ForgeBiomeTagsProvider(DataGenerator arg, ExistingFileHelper existingFileHelper)
    {
        super(arg, "forge", existingFileHelper);
    }

    @Override
    protected void m_6577_()
    {
        tag(Biomes.f_48174_, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48202_, Tags.Biomes.IS_PLAINS, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48203_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_OVERWORLD, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_OVERWORLD, Tags.Biomes.IS_SANDY, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186765_, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48205_, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48206_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_CONIFEROUS, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48207_, Tags.Biomes.IS_WET, Tags.Biomes.IS_WET_OVERWORLD, Tags.Biomes.IS_SWAMP, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48208_, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48209_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_NETHER, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_NETHER);
        tag(Biomes.f_48210_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_END, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_END, Tags.Biomes.IS_END);
        tag(Biomes.f_48211_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48212_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186761_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_WASTELAND, Tags.Biomes.IS_PLAINS, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48215_, Tags.Biomes.IS_MUSHROOM, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48217_, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48222_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_OVERWORLD, Tags.Biomes.IS_WET, Tags.Biomes.IS_WET_OVERWORLD, Tags.Biomes.IS_DENSE, Tags.Biomes.IS_DENSE_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186769_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_OVERWORLD, Tags.Biomes.IS_WET, Tags.Biomes.IS_WET, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48225_, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186760_, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48148_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_BEACH, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48149_, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48151_, Tags.Biomes.IS_SPOOKY, Tags.Biomes.IS_DENSE, Tags.Biomes.IS_DENSE_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48152_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_CONIFEROUS, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186763_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_CONIFEROUS, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186767_, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48157_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_OVERWORLD, Tags.Biomes.IS_SAVANNA, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48158_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_OVERWORLD, Tags.Biomes.IS_SAVANNA, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD, Tags.Biomes.IS_SLOPE, Tags.Biomes.IS_PLATEAU);
        tag(Biomes.f_48159_, Tags.Biomes.IS_SANDY, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186753_, Tags.Biomes.IS_SANDY, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_OVERWORLD, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_OVERWORLD, Tags.Biomes.IS_SLOPE, Tags.Biomes.IS_PLATEAU);
        tag(Biomes.f_186754_, Tags.Biomes.IS_PLAINS, Tags.Biomes.IS_PLATEAU, Tags.Biomes.IS_SLOPE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186755_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_CONIFEROUS, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_SLOPE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186756_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_SLOPE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186758_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_PEAK, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186757_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_PEAK, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186759_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_OVERWORLD, Tags.Biomes.IS_PEAK, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48162_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_END, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_END, Tags.Biomes.IS_END);
        tag(Biomes.f_48163_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_END, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_END, Tags.Biomes.IS_END);
        tag(Biomes.f_48164_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_END, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_END, Tags.Biomes.IS_END);
        tag(Biomes.f_48165_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_END, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_END, Tags.Biomes.IS_END);
        tag(Biomes.f_48166_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48167_, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48168_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48170_, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48171_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48172_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48173_, Tags.Biomes.IS_VOID);
        tag(Biomes.f_48176_, Tags.Biomes.IS_PLAINS, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186766_, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48179_, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48182_, Tags.Biomes.IS_COLD, Tags.Biomes.IS_COLD_OVERWORLD, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186762_, Tags.Biomes.IS_DENSE, Tags.Biomes.IS_DENSE_OVERWORLD, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186764_, Tags.Biomes.IS_DENSE, Tags.Biomes.IS_DENSE_OVERWORLD, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_186768_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_OVERWORLD, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_OVERWORLD, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_SAVANNA, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48194_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_OVERWORLD, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_OVERWORLD, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48197_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_OVERWORLD, Tags.Biomes.IS_WET, Tags.Biomes.IS_WET_OVERWORLD, Tags.Biomes.IS_RARE, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_151785_, Tags.Biomes.IS_UNDERGROUND, Tags.Biomes.IS_LUSH, Tags.Biomes.IS_WET, Tags.Biomes.IS_WET_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_151784_, Tags.Biomes.IS_UNDERGROUND, Tags.Biomes.IS_SPARSE, Tags.Biomes.IS_SPARSE_OVERWORLD, Tags.Biomes.IS_OVERWORLD);
        tag(Biomes.f_48199_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_NETHER, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_NETHER);
        tag(Biomes.f_48200_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_NETHER, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_NETHER);
        tag(Biomes.f_48201_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_NETHER, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_NETHER);
        tag(Biomes.f_48175_, Tags.Biomes.IS_HOT, Tags.Biomes.IS_HOT_NETHER, Tags.Biomes.IS_DRY, Tags.Biomes.IS_DRY_NETHER);

        m_206424_(Tags.Biomes.IS_WATER).m_206428_(BiomeTags.f_207603_).m_206428_(BiomeTags.f_207605_);
        m_206424_(BiomeTags.f_207606_).m_206428_(Tags.Biomes.IS_PEAK).m_206428_(Tags.Biomes.IS_SLOPE);
    }

    @SafeVarargs
    private void tag(ResourceKey<Biome> biome, TagKey<Biome>... tags)
    {
        for(TagKey<Biome> key : tags)
        {
            m_206424_(key).m_211101_(biome);
        }
    }

    @Override
    public String m_6055_() {
        return "Forge Biome Tags";
    }
}
