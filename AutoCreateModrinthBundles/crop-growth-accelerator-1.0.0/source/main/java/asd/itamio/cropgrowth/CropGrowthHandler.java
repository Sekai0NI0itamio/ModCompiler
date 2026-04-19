package asd.itamio.cropgrowth;

import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Random;

public class CropGrowthHandler {
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20; // Check every second (20 ticks)
    private final Random random = new Random();
    
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Only run on server side and during END phase
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        
        // Check if nearby growth is enabled
        if (!CropGrowthMod.config.enableWhilePlayerNearby) {
            return;
        }
        
        EntityPlayer player = event.player;
        
        // Only check every second to reduce performance impact
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        
        // Accelerate crop growth around player
        accelerateCropsAroundPlayer(player);
    }
    
    @SubscribeEvent
    public void onPlayerWakeUp(PlayerWakeUpEvent event) {
        // Only run on server side
        if (event.getEntityPlayer().world.isRemote) {
            return;
        }
        
        // Check if sleep growth is enabled
        if (!CropGrowthMod.config.enableWhileSleeping) {
            return;
        }
        
        EntityPlayer player = event.getEntityPlayer();
        
        // Apply bonus growth when player wakes up
        applySleepGrowthBonus(player);
    }
    
    private void accelerateCropsAroundPlayer(EntityPlayer player) {
        World world = player.world;
        BlockPos playerPos = player.getPosition();
        int radius = CropGrowthMod.config.radius;
        int multiplier = CropGrowthMod.config.growthSpeedMultiplier;
        
        // Calculate weather-based growth modifier
        int weatherBonus = 0;
        float weatherMultiplier = 1.0f;
        
        if (CropGrowthMod.config.enableWeatherEffects) {
            if (world.isThundering()) {
                weatherBonus = CropGrowthMod.config.thunderGrowthBonus;
            } else if (world.isRaining()) {
                weatherBonus = CropGrowthMod.config.rainGrowthBonus;
            }
            
            // Check if it's snowing in cold biomes
            if (world.isRaining() && world.getBiome(playerPos).getTemperature(playerPos) < 0.15F) {
                weatherMultiplier = CropGrowthMod.config.snowGrowthPenalty;
            }
        }
        
        // Calculate total growth ticks
        int totalGrowthTicks = (int) ((multiplier + weatherBonus) * weatherMultiplier);
        
        // Don't process if growth is disabled by snow
        if (totalGrowthTicks <= 0) {
            return;
        }
        
        // Scan area around player
        int minX = playerPos.getX() - radius;
        int maxX = playerPos.getX() + radius;
        int minY = Math.max(0, playerPos.getY() - radius);
        int maxY = Math.min(255, playerPos.getY() + radius);
        int minZ = playerPos.getZ() - radius;
        int maxZ = playerPos.getZ() + radius;
        
        // Only check a random subset of blocks to reduce lag
        int checksPerTick = 10;
        for (int i = 0; i < checksPerTick; i++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int y = minY + random.nextInt(maxY - minY + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);
            
            BlockPos pos = new BlockPos(x, y, z);
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            
            // Check if it's a growable crop
            if (isCrop(block)) {
                // Apply growth ticks
                for (int j = 0; j < totalGrowthTicks; j++) {
                    if (canGrow(world, pos, state, block)) {
                        block.updateTick(world, pos, state, random);
                    }
                }
            }
        }
    }
    
    private void applySleepGrowthBonus(EntityPlayer player) {
        World world = player.world;
        BlockPos playerPos = player.getPosition();
        int radius = CropGrowthMod.config.radius;
        int bonusTicks = CropGrowthMod.config.sleepGrowthBonus;
        
        // Scan area around player
        for (int x = playerPos.getX() - radius; x <= playerPos.getX() + radius; x++) {
            for (int y = Math.max(0, playerPos.getY() - radius); y <= Math.min(255, playerPos.getY() + radius); y++) {
                for (int z = playerPos.getZ() - radius; z <= playerPos.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();
                    
                    // Check if it's a growable crop
                    if (isCrop(block)) {
                        // Apply bonus growth ticks
                        for (int i = 0; i < bonusTicks; i++) {
                            if (canGrow(world, pos, state, block)) {
                                block.updateTick(world, pos, state, random);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private boolean isCrop(Block block) {
        return block instanceof BlockCrops ||
               block instanceof BlockStem ||
               block instanceof BlockNetherWart ||
               block instanceof BlockCocoa ||
               block instanceof BlockSapling ||
               block instanceof BlockMushroom ||
               block instanceof BlockCactus ||
               block instanceof BlockReed;
    }
    
    private boolean canGrow(World world, BlockPos pos, IBlockState state, Block block) {
        // Check if crop can grow (not fully grown)
        if (block instanceof BlockCrops) {
            BlockCrops crop = (BlockCrops) block;
            return !crop.isMaxAge(state);
        } else if (block instanceof BlockStem) {
            BlockStem stem = (BlockStem) block;
            return state.getValue(BlockStem.AGE) < 7;
        } else if (block instanceof BlockNetherWart) {
            return state.getValue(BlockNetherWart.AGE) < 3;
        } else if (block instanceof BlockCocoa) {
            return state.getValue(BlockCocoa.AGE) < 2;
        } else if (block instanceof BlockSapling || 
                   block instanceof BlockMushroom || 
                   block instanceof BlockCactus || 
                   block instanceof BlockReed) {
            // These can always attempt to grow
            return true;
        }
        
        return false;
    }
}
