package asd.itamio.instantxp;

import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

public class InstantXPHandler {
    
    // Instant XP absorption - give XP immediately without delay
    @SubscribeEvent
    public void onXPPickup(PlayerPickupXpEvent event) {
        if (!InstantXPConfig.enabled) return;
        if (!InstantXPConfig.instantAbsorption && !InstantXPConfig.simplifiedLeveling) return;
        
        EntityPlayer player = event.getEntityPlayer();
        EntityXPOrb orb = event.getOrb();
        
        if (player != null && orb != null) {
            int xpValue = orb.xpValue;
            
            if (InstantXPConfig.simplifiedLeveling) {
                // Simplified: 1 XP orb = 1 level
                player.addExperienceLevel(xpValue);
            } else if (InstantXPConfig.instantAbsorption) {
                // Instant absorption with vanilla leveling
                player.addExperience(xpValue);
            }
            
            // Remove the orb
            orb.setDead();
            
            // Cancel the event to prevent vanilla XP handling
            event.setCanceled(true);
        }
    }
    
    // Clump nearby XP orbs together to reduce lag
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!InstantXPConfig.enabled) return;
        if (!InstantXPConfig.clumpOrbs) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return; // Only run on server
        
        // Only check every 20 ticks (1 second) to reduce performance impact
        if (event.world.getTotalWorldTime() % 20 != 0) return;
        
        List<EntityXPOrb> orbs = event.world.getEntities(EntityXPOrb.class, orb -> orb != null && !orb.isDead);
        
        for (int i = 0; i < orbs.size(); i++) {
            EntityXPOrb mainOrb = orbs.get(i);
            if (mainOrb.isDead) continue;
            
            // Find nearby orbs within clump radius
            AxisAlignedBB searchBox = new AxisAlignedBB(
                mainOrb.posX - InstantXPConfig.clumpRadius,
                mainOrb.posY - InstantXPConfig.clumpRadius,
                mainOrb.posZ - InstantXPConfig.clumpRadius,
                mainOrb.posX + InstantXPConfig.clumpRadius,
                mainOrb.posY + InstantXPConfig.clumpRadius,
                mainOrb.posZ + InstantXPConfig.clumpRadius
            );
            
            List<EntityXPOrb> nearbyOrbs = event.world.getEntitiesWithinAABB(
                EntityXPOrb.class,
                searchBox,
                orb -> orb != null && !orb.isDead && orb != mainOrb
            );
            
            // Merge nearby orbs into the main orb
            for (EntityXPOrb nearbyOrb : nearbyOrbs) {
                if (nearbyOrb.isDead) continue;
                
                // Add the XP value to the main orb
                mainOrb.xpValue += nearbyOrb.xpValue;
                
                // Remove the nearby orb
                nearbyOrb.setDead();
            }
        }
    }
}
