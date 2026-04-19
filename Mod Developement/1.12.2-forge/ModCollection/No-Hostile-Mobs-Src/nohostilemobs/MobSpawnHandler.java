package com.nohostilemobs;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class MobSpawnHandler {
    private int tickCounter = 0;
    private static final int CONFIG_CHECK_INTERVAL = 100; // Check config every 100 ticks (5 seconds)

    @SubscribeEvent
    public void onMobSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getEntity() instanceof EntityLiving) {
            EntityLiving entity = (EntityLiving) event.getEntity();
            ResourceLocation entityId = EntityList.getKey(entity);
            
            if (entityId != null) {
                String entityIdString = entityId.toString();
                
                if (NoHostileMobsMod.config.isMobBlocked(entityIdString)) {
                    event.setResult(Result.DENY);
                }
            }
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickCounter++;
            if (tickCounter >= CONFIG_CHECK_INTERVAL) {
                tickCounter = 0;
                NoHostileMobsMod.config.checkAndReload();
            }
        }
    }
}
