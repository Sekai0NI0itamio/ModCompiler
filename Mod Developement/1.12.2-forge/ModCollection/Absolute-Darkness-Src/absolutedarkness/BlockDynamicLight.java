package com.absolutedarkness;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class BlockDynamicLight extends Block {

    public static final Material INVISIBLE = new Material(MapColor.AIR);

    public BlockDynamicLight() {
        super(INVISIBLE);
        setRegistryName("dynamic_light");
        setUnlocalizedName("dynamic_light");
        setLightLevel(1.0F); // Maximum light emit
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }
    
    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean isReplaceable(IBlockAccess worldIn, BlockPos pos) { return true; }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE; // Do not render
    }
}
