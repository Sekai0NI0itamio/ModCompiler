/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common;

import net.minecraft.block.CropsBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;

public interface IPlantable
{
    default PlantType getPlantType(IBlockReader world, BlockPos pos) {
        if (this instanceof CropsBlock) return PlantType.CROP;
        if (this instanceof SaplingBlock) return PlantType.PLAINS;
        if (this instanceof FlowerBlock) return PlantType.PLAINS;
        if (this == Blocks.field_196555_aI)      return PlantType.DESERT;
        if (this == Blocks.field_196651_dG)       return PlantType.WATER;
        if (this == Blocks.field_150337_Q)   return PlantType.CAVE;
        if (this == Blocks.field_150338_P) return PlantType.CAVE;
        if (this == Blocks.field_150388_bm)    return PlantType.NETHER;
        if (this == Blocks.field_196804_gh)      return PlantType.PLAINS;
        return net.minecraftforge.common.PlantType.PLAINS;
    }

    BlockState getPlant(IBlockReader world, BlockPos pos);
}
