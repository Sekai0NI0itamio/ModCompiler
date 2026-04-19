package asd.itamio.veinminer;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

public class VeinMinerHandler {
    
    private Map<UUID, Long> cooldowns = new HashMap<>();
    
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!VeinMinerMod.config.enableVeinMiner) {
            return;
        }

        EntityPlayer player = event.getPlayer();
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState state = event.getState();
        
        // Server-side only
        if (world.isRemote) {
            return;
        }
        
        // Check if player is in creative mode
        if (player.isCreative()) {
            return;
        }
        
        // Check client-side toggle (synced via packet or assume enabled)
        // For simplicity, we'll check sneak requirement
        if (VeinMinerMod.config.requireSneak && !player.isSneaking()) {
            return;
        }
        
        // Check cooldown
        if (VeinMinerMod.config.cooldownTicks > 0) {
            long currentTime = world.getTotalWorldTime();
            Long lastUse = cooldowns.get(player.getUniqueID());
            
            if (lastUse != null && (currentTime - lastUse) < VeinMinerMod.config.cooldownTicks) {
                return;
            }
            
            cooldowns.put(player.getUniqueID(), currentTime);
        }
        
        // Check if block is vein mineable
        if (!isVeinMineable(state.getBlock())) {
            return;
        }
        
        // Check if player has correct tool
        if (VeinMinerMod.config.limitToCorrectTool) {
            ItemStack heldItem = player.getHeldItemMainhand();
            if (!isCorrectTool(state.getBlock(), heldItem)) {
                return;
            }
        }
        
        // Find connected blocks
        Set<BlockPos> vein = findVein(world, pos, state.getBlock(), VeinMinerMod.config.maxBlocks);
        
        if (vein.size() <= 1) {
            return; // Only one block, no need for vein mining
        }
        
        // Mine the vein
        mineVein(world, player, vein, state, pos);
    }
    
    private boolean isVeinMineable(Block block) {
        // Ores
        if (VeinMinerMod.config.mineOres) {
            if (block == Blocks.COAL_ORE || block == Blocks.IRON_ORE || 
                block == Blocks.GOLD_ORE || block == Blocks.DIAMOND_ORE ||
                block == Blocks.EMERALD_ORE || block == Blocks.LAPIS_ORE ||
                block == Blocks.REDSTONE_ORE || block == Blocks.LIT_REDSTONE_ORE ||
                block == Blocks.QUARTZ_ORE) {
                return true;
            }
        }
        
        // Logs
        if (VeinMinerMod.config.mineLogs) {
            if (block == Blocks.LOG || block == Blocks.LOG2) {
                return true;
            }
        }
        
        // Stone
        if (VeinMinerMod.config.mineStone) {
            if (block == Blocks.STONE || block == Blocks.COBBLESTONE) {
                return true;
            }
        }
        
        // Dirt
        if (VeinMinerMod.config.mineDirt) {
            if (block == Blocks.DIRT || block == Blocks.GRASS) {
                return true;
            }
        }
        
        // Gravel
        if (VeinMinerMod.config.mineGravel && block == Blocks.GRAVEL) {
            return true;
        }
        
        // Sand
        if (VeinMinerMod.config.mineSand && block == Blocks.SAND) {
            return true;
        }
        
        // Clay
        if (VeinMinerMod.config.mineClay && block == Blocks.CLAY) {
            return true;
        }
        
        // Netherrack
        if (VeinMinerMod.config.mineNetherrack && block == Blocks.NETHERRACK) {
            return true;
        }
        
        // End Stone
        if (VeinMinerMod.config.mineEndStone && block == Blocks.END_STONE) {
            return true;
        }
        
        // Glowstone
        if (VeinMinerMod.config.mineGlowstone && block == Blocks.GLOWSTONE) {
            return true;
        }
        
        return false;
    }
    
    private boolean isCorrectTool(Block block, ItemStack tool) {
        if (tool.isEmpty()) {
            return false;
        }
        
        String toolClass = tool.getItem().getToolClasses(tool).isEmpty() ? "" : 
                          tool.getItem().getToolClasses(tool).iterator().next();
        
        // Ores, stone, netherrack, end stone require pickaxe
        if (block == Blocks.COAL_ORE || block == Blocks.IRON_ORE || 
            block == Blocks.GOLD_ORE || block == Blocks.DIAMOND_ORE ||
            block == Blocks.EMERALD_ORE || block == Blocks.LAPIS_ORE ||
            block == Blocks.REDSTONE_ORE || block == Blocks.LIT_REDSTONE_ORE ||
            block == Blocks.QUARTZ_ORE || block == Blocks.STONE || 
            block == Blocks.COBBLESTONE || block == Blocks.NETHERRACK ||
            block == Blocks.END_STONE || block == Blocks.GLOWSTONE) {
            return toolClass.equals("pickaxe");
        }
        
        // Logs require axe
        if (block == Blocks.LOG || block == Blocks.LOG2) {
            return toolClass.equals("axe");
        }
        
        // Dirt, gravel, sand, clay require shovel
        if (block == Blocks.DIRT || block == Blocks.GRASS || 
            block == Blocks.GRAVEL || block == Blocks.SAND || block == Blocks.CLAY) {
            return toolClass.equals("shovel");
        }
        
        return true;
    }
    
    private Set<BlockPos> findVein(World world, BlockPos start, Block targetBlock, int maxBlocks) {
        Set<BlockPos> vein = new HashSet<>();
        Queue<BlockPos> toCheck = new LinkedList<>();
        
        toCheck.add(start);
        vein.add(start);
        
        // Breadth-first search for connected blocks
        while (!toCheck.isEmpty() && vein.size() < maxBlocks) {
            BlockPos current = toCheck.poll();
            
            // Check all 26 surrounding blocks (including diagonals for better vein detection)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        
                        BlockPos neighbor = current.add(dx, dy, dz);
                        
                        if (vein.contains(neighbor)) continue;
                        if (vein.size() >= maxBlocks) break;
                        
                        IBlockState neighborState = world.getBlockState(neighbor);
                        
                        // Check if same block type (including metadata for logs)
                        if (neighborState.getBlock() == targetBlock) {
                            // For logs, also check metadata to ensure same wood type
                            if (targetBlock == Blocks.LOG || targetBlock == Blocks.LOG2) {
                                IBlockState startState = world.getBlockState(start);
                                if (neighborState.getBlock().getMetaFromState(neighborState) != 
                                    startState.getBlock().getMetaFromState(startState)) {
                                    continue;
                                }
                            }
                            
                            vein.add(neighbor);
                            toCheck.add(neighbor);
                        }
                    }
                }
            }
        }
        
        return vein;
    }
    
    private void mineVein(World world, EntityPlayer player, Set<BlockPos> vein, IBlockState originalState, BlockPos originalPos) {
        ItemStack tool = player.getHeldItemMainhand();
        List<ItemStack> allDrops = new ArrayList<>();
        int blocksMined = 0;
        
        // Mine all blocks in vein
        for (BlockPos pos : vein) {
            if (pos.equals(originalPos)) {
                continue; // Skip the original block (already being broken)
            }
            
            IBlockState state = world.getBlockState(pos);
            
            // Get drops
            NonNullList<ItemStack> drops = NonNullList.create();
            state.getBlock().getDrops(drops, world, pos, state, player.experienceLevel);
            
            // Add drops to collection
            for (ItemStack drop : drops) {
                allDrops.add(drop.copy());
            }
            
            // Remove block (no particles, no sound)
            world.setBlockToAir(pos);
            
            blocksMined++;
            
            // Consume durability
            if (VeinMinerMod.config.consumeDurability && !tool.isEmpty()) {
                tool.damageItem(1, player);
                if (tool.getItemDamage() >= tool.getMaxDamage()) {
                    tool.shrink(1);
                    break; // Tool broke
                }
            }
        }
        
        // Play single break sound at original position
        if (!VeinMinerMod.config.disableSound) {
            world.playSound(null, originalPos, originalState.getBlock().getSoundType(originalState, world, originalPos, player).getBreakSound(),
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        
        // Drop all items at one location
        if (VeinMinerMod.config.dropAtOneLocation) {
            // Combine stacks
            Map<String, ItemStack> combinedStacks = new HashMap<>();
            
            for (ItemStack drop : allDrops) {
                String key = drop.getItem().getRegistryName().toString() + ":" + drop.getMetadata();
                
                if (combinedStacks.containsKey(key)) {
                    ItemStack existing = combinedStacks.get(key);
                    int newCount = existing.getCount() + drop.getCount();
                    existing.setCount(Math.min(newCount, existing.getMaxStackSize()));
                    
                    // If overflow, create new stack
                    if (newCount > existing.getMaxStackSize()) {
                        ItemStack overflow = drop.copy();
                        overflow.setCount(newCount - existing.getMaxStackSize());
                        String overflowKey = key + "_" + combinedStacks.size();
                        combinedStacks.put(overflowKey, overflow);
                    }
                } else {
                    combinedStacks.put(key, drop.copy());
                }
            }
            
            // Spawn combined items at original position
            for (ItemStack stack : combinedStacks.values()) {
                if (!stack.isEmpty()) {
                    EntityItem entityItem = new EntityItem(world, 
                            originalPos.getX() + 0.5, 
                            originalPos.getY() + 0.5, 
                            originalPos.getZ() + 0.5, 
                            stack);
                    entityItem.setDefaultPickupDelay();
                    world.spawnEntity(entityItem);
                }
            }
        }
        
        // Consume hunger
        if (VeinMinerMod.config.consumeHunger) {
            float exhaustion = 0.005F * blocksMined * VeinMinerMod.config.hungerMultiplier;
            player.addExhaustion(exhaustion);
        }
    }
}
