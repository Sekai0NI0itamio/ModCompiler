package asd.itamio.keepinventory;

import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class KeepInventoryHandler {
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20; // Check every second (20 ticks)
    
    // When a world loads, immediately set keepInventory to true
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!KeepInventoryConfig.enabled) return;
        
        World world = event.getWorld();
        if (world != null && !world.isRemote) {
            GameRules rules = world.getGameRules();
            if (rules != null) {
                rules.setOrCreateGameRule("keepInventory", "true");
                KeepInventoryMod.logger.info("Set keepInventory to true for world: " + world.provider.getDimensionType().getName());
            }
        }
    }
    
    // Periodically check and enforce keepInventory = true
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!KeepInventoryConfig.enabled) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return; // Only run on server side
        
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            
            World world = event.world;
            GameRules rules = world.getGameRules();
            if (rules != null) {
                // Check if keepInventory is false and set it back to true
                if (!rules.getBoolean("keepInventory")) {
                    rules.setOrCreateGameRule("keepInventory", "true");
                    KeepInventoryMod.logger.info("Enforced keepInventory=true (was changed to false)");
                }
            }
        }
    }
}
