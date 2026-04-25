/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.WallSkullBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Access widened by fabric-transitive-access-wideners-v1 to accessible
 */
public class WallWitherSkullBlock
extends WallSkullBlock {
    public WallWitherSkullBlock(AbstractBlock.Settings settings) {
        super(SkullBlock.Type.WITHER_SKELETON, settings);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        Blocks.WITHER_SKELETON_SKULL.onPlaced(world, pos, state, placer, itemStack);
    }
}

