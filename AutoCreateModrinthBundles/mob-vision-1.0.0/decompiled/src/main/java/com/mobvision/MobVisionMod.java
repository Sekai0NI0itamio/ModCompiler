package com.mobvision;

import com.mobvision.config.MobVisionConfig;
import java.io.File;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "mobvision",
   name = "Mob Vision",
   version = "1.0.0",
   acceptedMinecraftVersions = "[1.12.2]"
)
@EventBusSubscriber(
   modid = "mobvision"
)
public final class MobVisionMod {
   public static final String MOD_ID = "mobvision";
   public static final String NAME = "Mob Vision";
   public static final String VERSION = "1.0.0";
   public static final Logger LOGGER = LogManager.getLogger("Mob Vision");
   private static final long UPDATE_INTERVAL_TICKS = 20L;
   private static MobVisionConfig config;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      config = new MobVisionConfig(new File(event.getModConfigurationDirectory(), "mobvision.txt"));
      config.load();
   }

   @SubscribeEvent
   public static void onEntityJoin(EntityJoinWorldEvent event) {
      if (!event.getWorld().field_72995_K && event.getEntity() instanceof EntityLiving && event.getEntity() instanceof IMob && config != null) {
         config.reloadIfChanged();
         applyMobVision((EntityLiving)event.getEntity());
      }
   }

   @SubscribeEvent
   public static void onWorldTick(WorldTickEvent event) {
      if (event.phase == Phase.END && event.world != null && !event.world.field_72995_K && config != null) {
         config.reloadIfChanged();
         if (event.world.func_82737_E() % 20L == 0L) {
            for (Entity entity : event.world.field_72996_f) {
               if (entity instanceof EntityLiving && entity instanceof IMob) {
                  applyMobVision((EntityLiving)entity);
               }
            }
         }
      }
   }

   private static void applyMobVision(EntityLiving mob) {
      String mobId = getMobId(mob);
      double configuredRange = config.getRangeForMob(mobId);
      IAttributeInstance followRange = mob.func_110148_a(SharedMonsterAttributes.field_111265_b);
      if (followRange != null && Math.abs(followRange.func_111125_b() - configuredRange) > 0.001) {
         followRange.func_111128_a(configuredRange);
      }

      if (configuredRange <= 0.0) {
         mob.func_70624_b(null);
      } else {
         EntityPlayer nearestPlayer = findNearestPlayer(mob.field_70170_p, mob, configuredRange);
         mob.func_70624_b(nearestPlayer);
      }
   }

   private static EntityPlayer findNearestPlayer(World world, EntityLiving mob, double range) {
      EntityPlayer nearest = null;
      double bestDistanceSq = Double.MAX_VALUE;
      double maxDistanceSq = range * range;

      for (EntityPlayer player : world.field_73010_i) {
         if (player != null && player.func_70089_S() && !player.func_175149_v() && !player.field_71075_bZ.field_75102_a) {
            double distanceSq = mob.func_70068_e(player);
            if (!(distanceSq > maxDistanceSq) && !(distanceSq >= bestDistanceSq)) {
               bestDistanceSq = distanceSq;
               nearest = player;
            }
         }
      }

      return nearest;
   }

   private static String getMobId(EntityLiving mob) {
      ResourceLocation key = EntityListBridge.getEntityKey(mob);
      return key == null ? mob.getClass().getName() : key.toString();
   }
}
