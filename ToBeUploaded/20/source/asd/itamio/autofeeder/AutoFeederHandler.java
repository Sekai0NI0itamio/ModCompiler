package asd.itamio.autofeeder;

import net.minecraft.entity.passive.*;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

public class AutoFeederHandler {
    
    private final Map<BlockPos, Long> lastFeedTime = new HashMap<>();
    private final Set<BlockPos> chestsToCheck = new HashSet<>();
    
    @SubscribeEvent
    public void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        // When player interacts with a chest, mark it for checking
        if (event.getWorld().isRemote) {
            return;
        }
        
        TileEntity te = event.getWorld().getTileEntity(event.getPos());
        if (te instanceof TileEntityChest) {
            chestsToCheck.add(event.getPos());
        }
    }
    
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        if (!AutoFeederMod.config.enableAutoFeeder) {
            return;
        }
        
        // Only run on server side
        if (event.world.isRemote) {
            return;
        }
        
        long currentTime = event.world.getTotalWorldTime();
        
        // Check marked chests
        Iterator<BlockPos> iterator = chestsToCheck.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            
            // Check if enough time has passed since last feed
            if (lastFeedTime.containsKey(pos)) {
                long timeSinceLastFeed = currentTime - lastFeedTime.get(pos);
                if (timeSinceLastFeed < AutoFeederMod.config.feedingInterval) {
                    continue;
                }
            }
            
            // Try to feed animals
            if (tryFeedAnimals(event.world, pos)) {
                lastFeedTime.put(pos, currentTime);
            }
            
            // Remove from check list after attempting
            iterator.remove();
        }
        
        // Periodically check all known chests (every feeding interval)
        if (currentTime % AutoFeederMod.config.feedingInterval == 0) {
            for (TileEntity te : new ArrayList<>(event.world.loadedTileEntityList)) {
                if (te instanceof TileEntityChest) {
                    BlockPos pos = te.getPos();
                    
                    // Check if enough time has passed
                    if (lastFeedTime.containsKey(pos)) {
                        long timeSinceLastFeed = currentTime - lastFeedTime.get(pos);
                        if (timeSinceLastFeed < AutoFeederMod.config.feedingInterval) {
                            continue;
                        }
                    }
                    
                    // Try to feed animals
                    if (tryFeedAnimals(event.world, pos)) {
                        lastFeedTime.put(pos, currentTime);
                    }
                }
            }
        }
    }
    
    private boolean tryFeedAnimals(World world, BlockPos containerPos) {
        TileEntity te = world.getTileEntity(containerPos);
        if (!(te instanceof IInventory)) {
            return false;
        }
        
        IInventory inventory = (IInventory) te;
        
        // Find animals in range
        int range = AutoFeederMod.config.searchRange;
        AxisAlignedBB searchBox = new AxisAlignedBB(
            containerPos.getX() - range, containerPos.getY() - range, containerPos.getZ() - range,
            containerPos.getX() + range, containerPos.getY() + range, containerPos.getZ() + range
        );
        
        List<EntityAnimal> animals = world.getEntitiesWithinAABB(EntityAnimal.class, searchBox);
        
        // Group animals by type
        Map<Class<? extends EntityAnimal>, List<EntityAnimal>> animalsByType = new HashMap<>();
        
        for (EntityAnimal animal : animals) {
            // Skip if animal is not enabled in config
            if (!isAnimalEnabled(animal)) {
                continue;
            }
            
            // Skip if animal is baby or already in love
            if (animal.isChild() || animal.isInLove()) {
                continue;
            }
            
            // Group by class
            Class<? extends EntityAnimal> animalClass = animal.getClass();
            if (!animalsByType.containsKey(animalClass)) {
                animalsByType.put(animalClass, new ArrayList<>());
            }
            animalsByType.get(animalClass).add(animal);
        }
        
        boolean fedAny = false;
        
        // Feed animals in pairs for each type
        for (Map.Entry<Class<? extends EntityAnimal>, List<EntityAnimal>> entry : animalsByType.entrySet()) {
            List<EntityAnimal> typeAnimals = entry.getValue();
            
            // Only feed even numbers (pairs)
            int animalsToFeed = (typeAnimals.size() / 2) * 2;
            
            for (int i = 0; i < animalsToFeed; i++) {
                EntityAnimal animal = typeAnimals.get(i);
                
                // Find food for this animal
                ItemStack food = findFoodForAnimal(inventory, animal);
                if (food.isEmpty()) {
                    // If we run out of food, stop feeding this type
                    break;
                }
                
                // Feed the animal
                if (feedAnimal(world, animal, food, inventory)) {
                    fedAny = true;
                }
            }
        }
        
        return fedAny;
    }
    
    private boolean isAnimalEnabled(EntityAnimal animal) {
        if (animal instanceof EntityCow && !AutoFeederMod.config.feedCows) return false;
        if (animal instanceof EntityPig && !AutoFeederMod.config.feedPigs) return false;
        if (animal instanceof EntitySheep && !AutoFeederMod.config.feedSheep) return false;
        if (animal instanceof EntityChicken && !AutoFeederMod.config.feedChickens) return false;
        if (animal instanceof EntityHorse && !AutoFeederMod.config.feedHorses) return false;
        if (animal instanceof EntityRabbit && !AutoFeederMod.config.feedRabbits) return false;
        return true;
    }
    
    private ItemStack findFoodForAnimal(IInventory inventory, EntityAnimal animal) {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            
            if (isValidFood(animal, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
    
    private boolean isValidFood(EntityAnimal animal, ItemStack stack) {
        Item item = stack.getItem();
        
        if (animal instanceof EntityCow || animal instanceof EntitySheep) {
            return item == Items.WHEAT;
        }
        
        if (animal instanceof EntityPig) {
            return item == Items.CARROT || item == Items.POTATO || item == Items.BEETROOT;
        }
        
        if (animal instanceof EntityChicken) {
            return item == Items.WHEAT_SEEDS || item == Items.MELON_SEEDS || 
                   item == Items.PUMPKIN_SEEDS || item == Items.BEETROOT_SEEDS;
        }
        
        if (animal instanceof EntityHorse) {
            return item == Items.GOLDEN_APPLE || item == Items.GOLDEN_CARROT;
        }
        
        if (animal instanceof EntityRabbit) {
            return item == Items.CARROT || item == Items.GOLDEN_CARROT;
        }
        
        return false;
    }
    
    private boolean feedAnimal(World world, EntityAnimal animal, ItemStack food, IInventory inventory) {
        // Consume one item from inventory
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            
            if (isValidFood(animal, stack)) {
                // Create a copy of the food item to feed
                ItemStack foodCopy = stack.copy();
                foodCopy.setCount(1);
                
                // Use the animal's isBreedingItem method to properly feed it
                if (animal.isBreedingItem(foodCopy)) {
                    // Consume one item from inventory
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        inventory.setInventorySlotContents(i, ItemStack.EMPTY);
                    }
                    
                    // Set animal in love (this is what happens when a player feeds them)
                    animal.setInLove(null);
                    
                    // Play the eating sound
                    animal.playLivingSound();
                    
                    // Show particles
                    if (world instanceof WorldServer) {
                        ((WorldServer) world).spawnParticle(
                            EnumParticleTypes.HEART,
                            animal.posX,
                            animal.posY + animal.height,
                            animal.posZ,
                            7,
                            0.5D,
                            0.5D,
                            0.5D,
                            0.0D
                        );
                    }
                    
                    return true;
                }
            }
        }
        
        return false;
    }
}
