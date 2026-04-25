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

package net.minecraftforge.common;

import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

public interface IPlantable
{
    default PlantType getPlantType(BlockGetter world, BlockPos pos) {
        if (this instanceof CropBlock) return PlantType.CROP;
        if (this instanceof SaplingBlock) return PlantType.PLAINS;
        if (this instanceof FlowerBlock) return PlantType.PLAINS;
        if (this == Blocks.f_50036_)      return PlantType.DESERT;
        if (this == Blocks.f_50196_)       return PlantType.WATER;
        if (this == Blocks.f_50073_)   return PlantType.CAVE;
        if (this == Blocks.f_50072_) return PlantType.CAVE;
        if (this == Blocks.f_50200_)    return PlantType.NETHER;
        if (this == Blocks.f_50359_)      return PlantType.PLAINS;
        return net.minecraftforge.common.PlantType.PLAINS;
    }

    BlockState getPlant(BlockGetter world, BlockPos pos);
}
