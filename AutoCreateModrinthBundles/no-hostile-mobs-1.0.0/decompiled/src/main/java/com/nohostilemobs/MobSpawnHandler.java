package com.nohostilemobs;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;

public class MobSpawnHandler {
   private int tickCounter = 0;
   private static final int CONFIG_CHECK_INTERVAL = 100;

   @SubscribeEvent
   public void onMobSpawn(CheckSpawn event) {
      if (event.getEntity() instanceof EntityLiving) {
         EntityLiving entity = (EntityLiving)event.getEntity();
         ResourceLocation entityId = EntityList.func_191301_a(entity);
         if (entityId != null) {
            String entityIdString = entityId.toString();
            if (NoHostileMobsMod.config.isMobBlocked(entityIdString)) {
               event.setResult(Result.DENY);
            }
         }
      }
   }

   @SubscribeEvent
   public void onWorldTick(WorldTickEvent event) {
      if (event.phase == Phase.END) {
         this.tickCounter++;
         if (this.tickCounter >= 100) {
            this.tickCounter = 0;
            NoHostileMobsMod.config.checkAndReload();
         }
      }
   }
}
