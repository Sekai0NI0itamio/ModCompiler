package asd.itamio.autotoolswap;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.item.*;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

public class AutoToolSwapHandler {
    
    private final Map<UUID, Integer> previousSlots = new HashMap<>();
    private final Map<UUID, Integer> switchBackTimers = new HashMap<>();
    private static final int SWITCH_BACK_DELAY = 40; // Ticks to wait before switching back (2 seconds)
    
    // Blocks that should use a shovel
    private static final Set<Block> SHOVEL_BLOCKS = new HashSet<>(Arrays.asList(
        Blocks.DIRT, Blocks.GRASS, Blocks.SAND, Blocks.GRAVEL, Blocks.CLAY,
        Blocks.FARMLAND, Blocks.GRASS_PATH, Blocks.MYCELIUM, Blocks.SOUL_SAND,
        Blocks.SNOW, Blocks.SNOW_LAYER, Blocks.CONCRETE_POWDER
    ));
    
    // Blocks that should use an axe
    private static final Set<Block> AXE_BLOCKS = new HashSet<>(Arrays.asList(
        Blocks.LOG, Blocks.LOG2, Blocks.PLANKS, Blocks.WOODEN_SLAB, Blocks.WOODEN_PRESSURE_PLATE,
        Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE_GATE,
        Blocks.BIRCH_FENCE, Blocks.BIRCH_FENCE_GATE, Blocks.JUNGLE_FENCE, Blocks.JUNGLE_FENCE_GATE,
        Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE_GATE, Blocks.ACACIA_FENCE, Blocks.ACACIA_FENCE_GATE,
        Blocks.CRAFTING_TABLE, Blocks.BOOKSHELF, Blocks.CHEST, Blocks.TRAPPED_CHEST,
        Blocks.JUKEBOX, Blocks.NOTEBLOCK, Blocks.WOODEN_BUTTON, Blocks.LADDER,
        Blocks.OAK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.BIRCH_STAIRS, Blocks.JUNGLE_STAIRS,
        Blocks.ACACIA_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.TRAPDOOR,
        Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR, Blocks.JUNGLE_DOOR,
        Blocks.ACACIA_DOOR, Blocks.DARK_OAK_DOOR
    ));
    
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!AutoToolSwapMod.config.enableAutoSwap) {
            return;
        }
        
        EntityPlayer player = event.getEntityPlayer();
        if (player == null) {
            return;
        }
        
        // Only run on client side to ensure visual sync
        if (!player.world.isRemote) {
            return;
        }
        
        BlockPos pos = event.getPos();
        IBlockState state = player.world.getBlockState(pos);
        Block block = state.getBlock();
        
        // Determine which tool type is needed
        ToolType neededTool = determineToolType(block);
        
        // Find best tool of that type
        int bestSlot = findBestToolOfType(player, neededTool, state);
        
        if (bestSlot != -1 && bestSlot != player.inventory.currentItem) {
            // Save previous slot if switchBack is enabled (only if not already saved)
            if (AutoToolSwapMod.config.switchBack && !previousSlots.containsKey(player.getUniqueID())) {
                previousSlots.put(player.getUniqueID(), player.inventory.currentItem);
            }
            
            // Switch to best tool
            player.inventory.currentItem = bestSlot;
            
            // Reset the switch back timer
            if (AutoToolSwapMod.config.switchBack) {
                switchBackTimers.put(player.getUniqueID(), SWITCH_BACK_DELAY);
            }
        } else if (bestSlot == player.inventory.currentItem) {
            // Already holding the right tool, reset timer
            if (AutoToolSwapMod.config.switchBack && switchBackTimers.containsKey(player.getUniqueID())) {
                switchBackTimers.put(player.getUniqueID(), SWITCH_BACK_DELAY);
            }
        }
    }
    
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        if (!AutoToolSwapMod.config.switchBack) {
            return;
        }
        
        EntityPlayer player = event.player;
        if (player == null || !player.world.isRemote) {
            return;
        }
        
        UUID playerId = player.getUniqueID();
        
        // Check if we have a switch back timer
        if (switchBackTimers.containsKey(playerId)) {
            int timer = switchBackTimers.get(playerId);
            timer--;
            
            if (timer <= 0) {
                // Time to switch back
                if (previousSlots.containsKey(playerId)) {
                    int previousSlot = previousSlots.get(playerId);
                    player.inventory.currentItem = previousSlot;
                    previousSlots.remove(playerId);
                }
                switchBackTimers.remove(playerId);
            } else {
                switchBackTimers.put(playerId, timer);
            }
        }
    }
    
    private ToolType determineToolType(Block block) {
        // Check shovel blocks first
        if (SHOVEL_BLOCKS.contains(block)) {
            return ToolType.SHOVEL;
        }
        
        // Check axe blocks second
        if (AXE_BLOCKS.contains(block)) {
            return ToolType.AXE;
        }
        
        // Default to pickaxe for everything else
        return ToolType.PICKAXE;
    }
    
    private int findBestToolOfType(EntityPlayer player, ToolType toolType, IBlockState state) {
        int bestSlot = -1;
        float bestSpeed = 0.0f;
        int bestEnchantmentScore = 0;
        
        // Determine search range
        int maxSlot = AutoToolSwapMod.config.hotbarOnly ? 9 : player.inventory.getSizeInventory();
        
        for (int i = 0; i < maxSlot; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            
            if (stack.isEmpty()) {
                continue;
            }
            
            Item item = stack.getItem();
            
            // Check if this is the correct tool type
            if (!isCorrectToolType(item, toolType)) {
                continue;
            }
            
            // Get mining speed for this tool
            float speed = stack.getDestroySpeed(state);
            
            // Calculate enchantment score
            int enchantmentScore = calculateEnchantmentScore(stack);
            
            // Check if this tool is better
            boolean isBetter = false;
            
            if (speed > bestSpeed) {
                isBetter = true;
            } else if (speed == bestSpeed && enchantmentScore > bestEnchantmentScore) {
                isBetter = true;
            }
            
            if (isBetter) {
                bestSpeed = speed;
                bestSlot = i;
                bestEnchantmentScore = enchantmentScore;
            }
        }
        
        return bestSlot;
    }
    
    private boolean isCorrectToolType(Item item, ToolType toolType) {
        switch (toolType) {
            case PICKAXE:
                return item instanceof ItemPickaxe;
            case AXE:
                return item instanceof ItemAxe;
            case SHOVEL:
                return item instanceof ItemSpade;
            default:
                return false;
        }
    }
    
    private int calculateEnchantmentScore(ItemStack stack) {
        int score = 0;
        
        // Check for Fortune
        if (AutoToolSwapMod.config.preferFortune) {
            int fortuneLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, stack);
            score += fortuneLevel * 100;
        }
        
        // Check for Silk Touch
        if (AutoToolSwapMod.config.preferSilkTouch) {
            int silkTouchLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, stack);
            score += silkTouchLevel * 100;
        }
        
        // Check for Efficiency
        int efficiencyLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, stack);
        score += efficiencyLevel * 10;
        
        // Check for Unbreaking
        int unbreakingLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING, stack);
        score += unbreakingLevel * 5;
        
        return score;
    }
    
    private enum ToolType {
        PICKAXE,
        AXE,
        SHOVEL
    }
}
