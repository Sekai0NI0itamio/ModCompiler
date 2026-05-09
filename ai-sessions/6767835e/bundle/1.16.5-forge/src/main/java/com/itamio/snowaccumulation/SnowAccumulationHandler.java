package com.itamio.snowaccumulation;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Random;

public class SnowAccumulationHandler {
    private Random random = new Random();
    private int tickCounter = 0;
    
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.world.provider.getDimensionType().getId() == 0) {
            tickCounter++;
            if (tickCounter >= ConfigManager.getAccumulationSpeed()) {
                tickCounter = 0;
                checkForSnowAccumulation(event.world);
            }
        }
    }
    
    private void checkForSnowAccumulation(World world) {
        if (world.playerEntities.isEmpty()) return;
        
        // Get a random player
        EntityPlayer player = (EntityPlayer) world.playerEntities.get(random.nextInt(world.playerEntities.size()));
        if (player == null) return;
        
        // Get player position
        BlockPos playerPos = player.getPosition();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        
        // Get a random position in the area around the player
        int radius = ConfigManager.getChunkRadius();
        int randomX = (playerChunkX << 4) + (random.nextInt(16) * (radius * 2) - radius) + 8;
        int randomZ = (playerChunkZ << 4) + (random.nextInt(16) * (radius * 2) - radius) + 8;
        
        // Check if the chunk is loaded
        if (!world.isChunkLoaded(randomX >> 4, randomZ >> 4)) {
            return;
        }
        
        // Get the surface block position
        int height = world.getHeight(randomX, randomZ);
        BlockPos surfacePos = new BlockPos(randomX, height, randomZ);
        
        // Check if it's snowing
        Biome biome = world.getBiome(surfacePos);
        String precipitation = world.isRaining() ? "snowing" : "clear";
        
        if (world.isRaining() && world.canBlockSeeSky(surfacePos.up())) {
            processSnowAccumulation(world, randomX, randomZ);
        }
    }
    
    private void processSnowAccumulation(World world, int x, int z) {
        BlockPos pos = new BlockPos(x, world.getHeight(x, z), z);
        BlockPos belowPos = pos.down();
        
        IBlockState belowState = world.getBlockState(belowPos);
        Block belowBlock = belowState.getBlock();
        
        boolean canPlace = belowBlock.canPlaceBlockAt(world, belowPos);
        boolean isSnowing = world.isRainingAt(pos);
        
        if (isSnowing && canPlace) {
            accumulateSnow(world, pos);
        }
    }
    
    private BlockPos findSnowPosition(World world, BlockPos pos) {
        int maxY = Math.min(pos.getY(), 255);
        for (int y = maxY; y >= 0; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            IBlockState state = world.getBlockState(checkPos);
            Block block = state.getBlock();
            
            if (block == Blocks.SNOW_LAYER) {
                return checkPos;
            }
            
            if (block == Blocks.AIR) {
                BlockPos abovePos = checkPos.up();
                if (world.canSnowAt(abovePos, true)) {
                    return abovePos;
                }
            }
        }
        return null;
    }
    
    private void accumulateSnow(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        
        if (block == Blocks.SNOW_LAYER) {
            int layers = state.getValue(BlockSnow.LAYERS);
            if (layers < 8) {
                world.setBlockState(pos, state.withProperty(BlockSnow.LAYERS, layers + 1), 3);
            } else {
                stackSnowAbove(world, pos);
            }
        } else if (block == Blocks.AIR) {
            world.setBlockState(pos, Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, 1), 3);
        }
    }
    
    private void stackSnowAbove(World world, BlockPos pos) {
        int maxHeight = ConfigManager.getMaxSnowHeight();
        int currentHeight = 0;
        
        BlockPos currentPos = pos;
        while (currentPos.getY() > 0) {
            IBlockState state = world.getBlockState(currentPos);
            if (state.getBlock() == Blocks.SNOW_LAYER) {
                currentHeight++;
            } else {
                break;
            }
            currentPos = currentPos.down();
        }
        
        currentPos = pos;
        while (currentPos.getY() < 256) {
            IBlockState state = world.getBlockState(currentPos);
            if (state.getBlock() == Blocks.SNOW_LAYER) {
                currentPos = currentPos.up();
            } else {
                break;
            }
        }
        
        if (currentHeight < maxHeight) {
            IBlockState state = world.getBlockState(currentPos);
            if (state.getBlock() == Blocks.AIR) {
                world.setBlockState(currentPos, Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, 1), 3);
            }
        }
    }
}