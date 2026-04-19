package com.forgetemplatemod;

import net.minecraft.block.BlockFire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

public class BlockWildFire extends BlockFire {
    
    public BlockWildFire() {
        super();
        this.setRegistryName("minecraft", "fire");
        this.setTranslationKey("fire");
    }

    @Override
    public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {
        if (!worldIn.getGameRules().getBoolean("doFireTick")) {
            return;
        }

        BlockPos posBelow = pos.down();
        IBlockState stateBelow = worldIn.getBlockState(posBelow);

        // Grass block becomes coarse dirt, chance of shoveled dirt
        if (stateBelow.getBlock() == Blocks.GRASS) {
            // Give it roughly a 10% chance per tick evaluation to alter the block, stretching it to loosely match "10 seconds" before naturally vanishing
            if (rand.nextInt(3) == 0) { 
                if (rand.nextBoolean()) {
                    worldIn.setBlockState(posBelow, Blocks.GRASS_PATH.getDefaultState());
                } else {
                    worldIn.setBlockState(posBelow, Blocks.DIRT.getStateFromMeta(1)); // Coarse Dirt
                }
            }
        }
        
        super.updateTick(worldIn, pos, state, rand);
        
        // We force it to re-tick sooner every 3-4 seconds (60-80 ticks) to enable rampant spreading
        worldIn.scheduleUpdate(pos, this, 60 + rand.nextInt(20));
    }
}
