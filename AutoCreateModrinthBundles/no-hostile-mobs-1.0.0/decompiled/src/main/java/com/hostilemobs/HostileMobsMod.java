package com.hostilemobs;

import com.hostilemobs.ai.EntityAICreeperExplodeWhenStuck;
import com.hostilemobs.ai.EntityAISkeletonAttackWhileMoving;
import com.hostilemobs.ai.EntityAISlimeBreakBlocks;
import com.hostilemobs.ai.EntityAIZombieBlockPlace;
import com.hostilemobs.ai.EntityAIZombieBreakBlocks;
import com.hostilemobs.config.HostileMobsConfig;
import com.hostilemobs.pathfinding.AsyncPathfindingManager;
import com.hostilemobs.pathfinding.GlobalPathManager;
import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
   modid = "hostilemobs",
   name = "Hostile Mobs",
   version = "1.0.0",
   acceptedMinecraftVersions = "[1.12.2]"
)
@EventBusSubscriber(
   modid = "hostilemobs"
)
public final class HostileMobsMod {
   public static final String MOD_ID = "hostilemobs";
   public static final String NAME = "Hostile Mobs";
   public static final String VERSION = "1.0.0";
   public static final Logger LOGGER = LogManager.getLogger("Hostile Mobs");
   private static final long UPDATE_INTERVAL_TICKS = 20L;
   private static final double INFINITE_FOLLOW_RANGE = 2048.0;
   private static HostileMobsConfig config;
   private static int updateOffset = 0;
   private static final int STAGGER_INTERVAL = 4;
   private static final Set<EntityZombie> BUILDER_ZOMBIES = ConcurrentHashMap.newKeySet();
   private static final int MAX_BUILDERS = 10;
   private static boolean DEBUG_MODE = true;
   private static boolean ZOMBIES_ONLY_MODE = true;

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      config = new HostileMobsConfig(new File(event.getModConfigurationDirectory(), "hostilemobs.txt"));
      config.load();
   }

   @EventHandler
   public void serverStopping(FMLServerStoppingEvent event) {
      AsyncPathfindingManager.shutdown();
      GlobalPathManager.cleanup();
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (!event.player.field_70170_p.field_72995_K) {
         if (DEBUG_MODE) {
            LOGGER.info("[DEBUG] ========================================");
            LOGGER.info("[DEBUG] Player {} joined - activating hostile mobs", event.player.func_70005_c_());
            LOGGER.info("[DEBUG] Debug mode: ENABLED");
            LOGGER.info("[DEBUG] Zombies only mode: {}", ZOMBIES_ONLY_MODE ? "ENABLED" : "DISABLED");
            LOGGER.info("[DEBUG] ========================================");
         }

         activateAllHostileMobs(event.player.field_70170_p, event.player);
      }
   }

   @SubscribeEvent
   public static void onEntityJoin(EntityJoinWorldEvent event) {
      if (!event.getWorld().field_72995_K && event.getEntity() instanceof EntityLiving && event.getEntity() instanceof IMob) {
         EntityLiving mob = (EntityLiving)event.getEntity();
         if (ZOMBIES_ONLY_MODE && !(mob instanceof EntityZombie)) {
            if (DEBUG_MODE) {
               LOGGER.info("[DEBUG] Removing non-zombie mob: {}", mob.getClass().getSimpleName());
            }

            event.setCanceled(true);
         } else {
            if (DEBUG_MODE) {
               LOGGER.info("[DEBUG] Hostile mob joined: {} at {}", mob.getClass().getSimpleName(), mob.func_180425_c());
            }

            setInfiniteFollowRange(mob);
            if (mob instanceof EntityZombie) {
               EntityZombie zombie = (EntityZombie)mob;
               if (BUILDER_ZOMBIES.size() < 10) {
                  BUILDER_ZOMBIES.add(zombie);
                  setupZombieWithBlocks(zombie);
                  if (DEBUG_MODE) {
                     LOGGER.info("[DEBUG] Zombie {} assigned as BUILDER (total builders: {})", zombie.func_145782_y(), BUILDER_ZOMBIES.size());
                  }
               } else if (DEBUG_MODE) {
                  LOGGER.info("[DEBUG] Zombie {} assigned as BREAKER (max builders reached)", zombie.func_145782_y());
               }

               setupZombieBreaking(zombie);
            }

            if (mob instanceof EntitySkeleton) {
               setupSkeletonAI((EntitySkeleton)mob);
            }

            if (mob instanceof EntityCreeper) {
               setupCreeperAI((EntityCreeper)mob);
            }

            if (mob instanceof EntitySlime) {
               setupSlimeAI((EntitySlime)mob);
            }

            EntityPlayer nearestPlayer = findNearestPlayer(mob.field_70170_p, mob);
            if (nearestPlayer != null) {
               mob.func_70624_b(nearestPlayer);
               if (DEBUG_MODE) {
                  LOGGER.info(
                     "[DEBUG] Mob {} targeting player {} at distance {}",
                     mob.func_145782_y(),
                     nearestPlayer.func_70005_c_(),
                     Math.sqrt(mob.func_70068_e(nearestPlayer))
                  );
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onWorldTick(WorldTickEvent event) {
      if (event.phase == Phase.END && event.world != null && !event.world.field_72995_K) {
         AsyncPathfindingManager.applyComputedPaths();
         BUILDER_ZOMBIES.removeIf(z -> !z.func_70089_S());
         updateOffset = (updateOffset + 1) % 4;
         int mobIndex = 0;
         int updatedThisTick = 0;

         for (Entity entity : event.world.field_72996_f) {
            if (entity instanceof EntityLiving && entity instanceof IMob) {
               if (mobIndex % 4 != updateOffset) {
                  mobIndex++;
               } else {
                  mobIndex++;
                  updatedThisTick++;
                  EntityLiving mob = (EntityLiving)entity;
                  if (event.world.func_82737_E() % 20L == 0L) {
                     setInfiniteFollowRange(mob);
                  }

                  EntityPlayer nearestPlayer = findNearestPlayer(mob.field_70170_p, mob);
                  if (nearestPlayer != null) {
                     if (mob.func_70638_az() != nearestPlayer) {
                        mob.func_70624_b(nearestPlayer);
                        if (DEBUG_MODE && event.world.func_82737_E() % 100L == 0L) {
                           LOGGER.info("[DEBUG] Mob {} retargeted to player {}", mob.func_145782_y(), nearestPlayer.func_70005_c_());
                        }
                     }

                     if (mob instanceof EntityZombie) {
                        EntityZombie zombie = (EntityZombie)mob;
                        if (BUILDER_ZOMBIES.contains(zombie)) {
                           EntityAIZombieBlockPlace.ZombieBlockInventory inv = EntityAIZombieBlockPlace.getInventory(zombie);
                           if (inv != null && !inv.hasBlocks()) {
                              BUILDER_ZOMBIES.remove(zombie);
                              if (DEBUG_MODE) {
                                 LOGGER.info(
                                    "[DEBUG] Zombie {} ran out of blocks, removed from builders (total: {})", zombie.func_145782_y(), BUILDER_ZOMBIES.size()
                                 );
                              }

                              promoteNearbyZombieToBuilder(event.world, zombie.func_180425_c());
                           }
                        }
                     }

                     if (mob.func_70661_as().func_75500_f()) {
                        double distanceSq = mob.func_70068_e(nearestPlayer);
                        if (DEBUG_MODE && event.world.func_82737_E() % 100L == 0L) {
                           LOGGER.info("[DEBUG] Mob {} has no path, distance: {}", mob.func_145782_y(), Math.sqrt(distanceSq));
                        }

                        if (distanceSq < 256.0) {
                           mob.func_70661_as().func_75497_a(nearestPlayer, 1.0);
                        } else if (distanceSq < 1024.0 && event.world.func_82737_E() % 10L == 0L) {
                           AsyncPathfindingManager.requestPathfinding(mob, nearestPlayer);
                        } else if (event.world.func_82737_E() % 20L == 0L) {
                           AsyncPathfindingManager.requestPathfinding(mob, nearestPlayer);
                        }
                     }
                  }
               }
            }
         }

         if (DEBUG_MODE && event.world.func_82737_E() % 100L == 0L) {
            LOGGER.info("[DEBUG] Tick update: {} mobs processed, {} builders active", updatedThisTick, BUILDER_ZOMBIES.size());
         }
      }
   }

   private static void promoteNearbyZombieToBuilder(World world, BlockPos pos) {
      if (BUILDER_ZOMBIES.size() < 10) {
         EntityZombie nearestZombie = null;
         double nearestDistSq = Double.MAX_VALUE;

         for (Entity entity : world.field_72996_f) {
            if (entity instanceof EntityZombie) {
               EntityZombie zombie = (EntityZombie)entity;
               if (!BUILDER_ZOMBIES.contains(zombie)) {
                  double distSq = zombie.func_174818_b(pos);
                  if (distSq < nearestDistSq && distSq < 1024.0) {
                     nearestDistSq = distSq;
                     nearestZombie = zombie;
                  }
               }
            }
         }

         if (nearestZombie != null) {
            BUILDER_ZOMBIES.add(nearestZombie);
            setupZombieWithBlocks(nearestZombie);
            if (DEBUG_MODE) {
               LOGGER.info(
                  "[DEBUG] Promoted zombie {} to builder at distance {} (total: {})",
                  nearestZombie.func_145782_y(),
                  Math.sqrt(nearestDistSq),
                  BUILDER_ZOMBIES.size()
               );
            }
         }
      }
   }

   private static void activateAllHostileMobs(World world, EntityPlayer player) {
      int mobCount = 0;
      int zombieCount = 0;

      for (Entity entity : world.field_72996_f) {
         if (entity instanceof EntityLiving && entity instanceof IMob) {
            EntityLiving mob = (EntityLiving)entity;
            setInfiniteFollowRange(mob);
            mob.func_70624_b(player);
            if (mob instanceof EntityZombie) {
               zombieCount++;
               EntityZombie zombie = (EntityZombie)mob;
               if (BUILDER_ZOMBIES.size() < 10 && !BUILDER_ZOMBIES.contains(zombie)) {
                  BUILDER_ZOMBIES.add(zombie);
                  setupZombieWithBlocks(zombie);
                  if (DEBUG_MODE) {
                     LOGGER.info("[DEBUG] Existing zombie {} promoted to BUILDER", zombie.func_145782_y());
                  }
               }

               if (!hasBlockPlacingAI(zombie)) {
                  setupZombieBreaking(zombie);
               }
            }

            if (mob instanceof EntitySkeleton && !hasSkeletonAI((EntitySkeleton)mob)) {
               setupSkeletonAI((EntitySkeleton)mob);
            }

            if (mob instanceof EntityCreeper && !hasCreeperAI((EntityCreeper)mob)) {
               setupCreeperAI((EntityCreeper)mob);
            }

            if (mob instanceof EntitySlime && !hasSlimeAI((EntitySlime)mob)) {
               setupSlimeAI((EntitySlime)mob);
            }

            mobCount++;
         }
      }

      if (DEBUG_MODE) {
         LOGGER.info(
            "[DEBUG] Activated {} hostile mobs for player {} ({} zombies, {} builders)", mobCount, player.func_70005_c_(), zombieCount, BUILDER_ZOMBIES.size()
         );
      }
   }

   private static void setInfiniteFollowRange(EntityLiving mob) {
      IAttributeInstance followRange = mob.func_110148_a(SharedMonsterAttributes.field_111265_b);
      if (followRange != null && Math.abs(followRange.func_111125_b() - 2048.0) > 0.001) {
         followRange.func_111128_a(2048.0);
      }
   }

   private static void setupZombieWithBlocks(EntityZombie zombie) {
      if (hasBlockPlacingAI(zombie)) {
         if (DEBUG_MODE) {
            LOGGER.info("[DEBUG] Zombie {} already has block-placing AI", zombie.func_145782_y());
         }
      } else {
         zombie.field_70714_bg.func_75776_a(1, new EntityAIZombieBlockPlace(zombie));
         EntityAIZombieBlockPlace.ZombieBlockInventory inv = EntityAIZombieBlockPlace.getInventory(zombie);
         int blockCount = inv != null ? inv.getBlockCount() : 0;
         if (DEBUG_MODE) {
            LOGGER.info("[DEBUG] Zombie {} equipped with block-placing AI and {} blocks", zombie.func_145782_y(), blockCount);
         }
      }
   }

   private static void setupZombieBreaking(EntityZombie zombie) {
      zombie.field_70714_bg.func_75776_a(2, new EntityAIZombieBreakBlocks(zombie));
      if (DEBUG_MODE) {
         LOGGER.info("[DEBUG] Zombie {} equipped with block-breaking AI", zombie.func_145782_y());
      }
   }

   private static boolean hasBlockPlacingAI(EntityZombie zombie) {
      return zombie.field_70714_bg.field_75782_a.stream().anyMatch(entry -> entry.field_75733_a instanceof EntityAIZombieBlockPlace);
   }

   private static void setupSlimeAI(EntitySlime slime) {
      if (!hasSlimeAI(slime)) {
         slime.field_70714_bg.func_75776_a(1, new EntityAISlimeBreakBlocks(slime));
         LOGGER.info("Slime equipped with block-breaking AI");
      }
   }

   private static boolean hasSlimeAI(EntitySlime slime) {
      return slime.field_70714_bg.field_75782_a.stream().anyMatch(entry -> entry.field_75733_a instanceof EntityAISlimeBreakBlocks);
   }

   private static void setupSkeletonAI(EntitySkeleton skeleton) {
      if (!hasSkeletonAI(skeleton)) {
         skeleton.field_70714_bg.func_75776_a(1, new EntityAISkeletonAttackWhileMoving(skeleton, 1.0, 20, 15.0F));
         LOGGER.info("Skeleton equipped with shoot-while-moving AI");
      }
   }

   private static boolean hasSkeletonAI(EntitySkeleton skeleton) {
      return skeleton.field_70714_bg.field_75782_a.stream().anyMatch(entry -> entry.field_75733_a instanceof EntityAISkeletonAttackWhileMoving);
   }

   private static void setupCreeperAI(EntityCreeper creeper) {
      if (!hasCreeperAI(creeper)) {
         creeper.field_70714_bg.func_75776_a(1, new EntityAICreeperExplodeWhenStuck(creeper));
         creeper.field_70714_bg.func_75776_a(2, new EntityAIAttackMelee(creeper, 1.0, false));
         LOGGER.info("Creeper equipped with stuck-explosion AI");
      }
   }

   private static boolean hasCreeperAI(EntityCreeper creeper) {
      return creeper.field_70714_bg.field_75782_a.stream().anyMatch(entry -> entry.field_75733_a instanceof EntityAICreeperExplodeWhenStuck);
   }

   private static EntityPlayer findNearestPlayer(World world, EntityLiving mob) {
      EntityPlayer nearest = null;
      double bestDistanceSq = Double.MAX_VALUE;

      for (EntityPlayer player : world.field_73010_i) {
         if (player != null && player.func_70089_S() && !player.func_175149_v() && !player.field_71075_bZ.field_75102_a) {
            double distanceSq = mob.func_70068_e(player);
            if (!(distanceSq >= bestDistanceSq)) {
               bestDistanceSq = distanceSq;
               nearest = player;
            }
         }
      }

      return nearest;
   }
}
