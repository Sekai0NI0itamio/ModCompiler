package asd.itamio.autotorch;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AutoTorchHandler {
    
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private final Map<UUID, BlockPos> lastTorchPositions = new HashMap<>();
    private final Map<UUID, BlockPos> lastPlayerPositions = new HashMap<>();
    
    @SubscribeEvent
    public void onPlayerUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        
        if (!AutoTorchMod.config.enableAutoTorch) {
            return;
        }
        
        // Only run on server side
        if (player.world.isRemote) {
            return;
        }
        
        // Check dimension
        if (!isDimensionAllowed(player.world)) {
            return;
        }
        
        UUID playerId = player.getUniqueID();
        BlockPos currentPos = player.getPosition();
        
        // Check if player is moving
        boolean isMoving = false;
        if (lastPlayerPositions.containsKey(playerId)) {
            BlockPos lastPos = lastPlayerPositions.get(playerId);
            isMoving = !currentPos.equals(lastPos);
        }
        lastPlayerPositions.put(playerId, currentPos);
        
        // Only check every 2 ticks when moving, or every 10 ticks when stationary
        int checkInterval = isMoving ? 2 : 10;
        
        // Handle cooldown
        if (cooldowns.containsKey(playerId)) {
            int cooldown = cooldowns.get(playerId);
            cooldown--;
            if (cooldown <= 0) {
                cooldowns.remove(playerId);
            } else {
                cooldowns.put(playerId, cooldown);
                return;
            }
        }
        
        // Only check on the interval
        if (player.ticksExisted % checkInterval != 0) {
            return;
        }
        
        // Check average light level around player
        BlockPos playerPos = player.getPosition();
        int averageLight = calculateAverageLightLevel(player.world, playerPos);
        
        if (averageLight >= AutoTorchMod.config.lightThreshold) {
            return;
        }
        
        // Check if we're too close to last placed torch
        if (lastTorchPositions.containsKey(playerId)) {
            BlockPos lastPos = lastTorchPositions.get(playerId);
            double distance = playerPos.getDistance(lastPos.getX(), lastPos.getY(), lastPos.getZ());
            if (distance < AutoTorchMod.config.minTorchDistance) {
                return;
            }
        }
        
        // Find torch in inventory
        ItemStack torchStack = findTorchInInventory(player);
        if (torchStack.isEmpty()) {
            return;
        }
        
        // Try to place torch
        BlockPos placePos = findTorchPlacement(player.world, playerPos);
        if (placePos != null) {
            placeTorch(player, placePos, torchStack);
            cooldowns.put(playerId, AutoTorchMod.config.placementCooldown);
            lastTorchPositions.put(playerId, placePos);
        }
    }
    
    private boolean isDimensionAllowed(World world) {
        int dimension = world.provider.getDimension();
        
        // Overworld is always allowed
        if (dimension == 0) {
            return true;
        }
        
        // Nether
        if (dimension == -1) {
            return AutoTorchMod.config.enableInNether;
        }
        
        // End
        if (dimension == 1) {
            return AutoTorchMod.config.enableInEnd;
        }
        
        // Other dimensions default to true
        return true;
    }
    
    private int calculateAverageLightLevel(World world, BlockPos center) {
        int totalLight = 0;
        int count = 0;
        
        // Check 3x3x3 area around player
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = center.add(x, y, z);
                    totalLight += world.getLight(checkPos);
                    count++;
                }
            }
        }
        
        return count > 0 ? totalLight / count : 0;
    }
    
    private ItemStack findTorchInInventory(EntityPlayer player) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && Block.getBlockFromItem(stack.getItem()) == Blocks.TORCH) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
    
    private BlockPos findTorchPlacement(World world, BlockPos playerPos) {
        String mode = AutoTorchMod.config.placementMode.toLowerCase();
        
        // Try wall placement first (priority)
        if (mode.equals("auto") || mode.equals("wall")) {
            BlockPos wallPos = findWallPlacement(world, playerPos);
            if (wallPos != null) {
                return wallPos;
            }
        }
        
        // Try ground placement second (if allowed)
        if (mode.equals("auto") || mode.equals("ground")) {
            BlockPos groundPos = findGroundPlacement(world, playerPos);
            if (groundPos != null) {
                return groundPos;
            }
        }
        
        return null;
    }
    
    private BlockPos findGroundPlacement(World world, BlockPos playerPos) {
        // Check positions around and below player
        BlockPos[] checkPositions = {
            playerPos.down(),
            playerPos.down().north(),
            playerPos.down().south(),
            playerPos.down().east(),
            playerPos.down().west()
        };
        
        for (BlockPos checkPos : checkPositions) {
            BlockPos torchPos = checkPos.up();
            
            // Check if we can place torch here
            if (canPlaceTorchOnGround(world, checkPos, torchPos)) {
                return torchPos;
            }
        }
        
        return null;
    }
    
    private BlockPos findWallPlacement(World world, BlockPos playerPos) {
        // Check walls around player at player height and one block down
        EnumFacing[] directions = { EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST };
        
        // Try at player height first
        for (EnumFacing facing : directions) {
            BlockPos torchPos = playerPos.offset(facing.getOpposite());
            BlockPos wallPos = playerPos.offset(facing);
            
            if (canPlaceTorchOnWall(world, wallPos, torchPos, facing.getOpposite())) {
                return torchPos;
            }
        }
        
        // Try one block down
        BlockPos lowerPos = playerPos.down();
        for (EnumFacing facing : directions) {
            BlockPos torchPos = lowerPos.offset(facing.getOpposite());
            BlockPos wallPos = lowerPos.offset(facing);
            
            if (canPlaceTorchOnWall(world, wallPos, torchPos, facing.getOpposite())) {
                return torchPos;
            }
        }
        
        return null;
    }
    
    private boolean canPlaceTorchOnGround(World world, BlockPos groundPos, BlockPos torchPos) {
        // Ground must be solid
        Block groundBlock = world.getBlockState(groundPos).getBlock();
        if (!groundBlock.isFullBlock(world.getBlockState(groundPos))) {
            return false;
        }
        
        // Torch position must be air
        if (!world.isAirBlock(torchPos)) {
            return false;
        }
        
        // Check if torch can be placed here
        return Blocks.TORCH.canPlaceBlockAt(world, torchPos);
    }
    
    private boolean canPlaceTorchOnWall(World world, BlockPos wallPos, BlockPos torchPos, EnumFacing facing) {
        // Wall must be solid
        Block wallBlock = world.getBlockState(wallPos).getBlock();
        if (!wallBlock.isFullBlock(world.getBlockState(wallPos))) {
            return false;
        }
        
        // Torch position must be air
        if (!world.isAirBlock(torchPos)) {
            return false;
        }
        
        // Make sure there's space above the torch
        if (!world.isAirBlock(torchPos.up())) {
            return false;
        }
        
        return true;
    }
    
    private void placeTorch(EntityPlayer player, BlockPos pos, ItemStack torchStack) {
        // Place the torch
        player.world.setBlockState(pos, Blocks.TORCH.getDefaultState());
        
        // Consume one torch from inventory
        torchStack.shrink(1);
        if (torchStack.isEmpty()) {
            // Find the slot and clear it
            for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (stack == torchStack) {
                    player.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
        
        // Play sound
        if (AutoTorchMod.config.playSound) {
            player.world.playSound(
                null,
                pos,
                SoundEvents.BLOCK_WOOD_PLACE,
                SoundCategory.BLOCKS,
                0.5F,
                0.8F
            );
        }
        
        // Show message
        if (AutoTorchMod.config.showMessage) {
            player.sendStatusMessage(
                new TextComponentString(TextFormatting.YELLOW + "Torch placed automatically"),
                true
            );
        }
    }
}
