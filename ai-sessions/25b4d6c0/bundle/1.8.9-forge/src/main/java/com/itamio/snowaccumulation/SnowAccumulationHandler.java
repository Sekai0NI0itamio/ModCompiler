package com.itamio.snowaccumulation;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.BlockEvent;

public class SnowAccumulationHandler {
    private Random random = new Random();
    private int tickCounter = 0;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickCounter++;
            if (tickCounter >= ConfigManager.getAccumulationSpeed()) {
                tickCounter = 0;
                // Process snow accumulation logic
            }
        }
    }

    public void processSnowAccumulation(World world, BlockPos pos) {
        // Check if it's snowing and the position is valid for snow accumulation
        if (world.isRainingAt(pos) && world.canBlockSeeSky(pos)) {
            // Get the block at the position
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            
            // If it's a snow layer block, increase the layer
            if (block == Blocks.snow_layer) {
                int currentLayers = ((Integer) state.getValue(net.minecraft.block.BlockSnow.LAYERS));
                if (currentLayers