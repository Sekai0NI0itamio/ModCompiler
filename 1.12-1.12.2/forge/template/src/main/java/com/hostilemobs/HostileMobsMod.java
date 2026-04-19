package com.hostilemobs;

import com.hostilemobs.ai.EntityAIZombieBlockPlace;
import com.hostilemobs.config.HostileMobsConfig;
import java.io.File;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = HostileMobsMod.MOD_ID, name = HostileMobsMod.NAME, version = HostileMobsMod.VERSION)
@Mod.EventBusSubscriber(modid = HostileMobsMod.MOD_ID)
public final class HostileMobsMod {
    public static final String MOD_ID = "hostilemobs";
    public static final String NAME = "Hostile Mobs";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    private static final long UPDATE_INTERVAL_TICKS = 20L;
    private static HostileMobsConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new HostileMobsConfig(new File(event.getModConfigurationDirectory(), "hostilemobs.txt"));
        config.load();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player.world.isRemote) {
            return;
        }

        LOGGER.info("Player {} joined - activating all hostile mobs", event.player.getName());
        activateAllHostileMobs(event.player.world, event.player);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityLiving) || !(event.getEntity() instanceof IMob)) {
            return;
        }

        EntityLiving mob = (EntityLiving) event.getEntity();
        
        // Give zombies block-placing abilities
        if (mob instanceof EntityZombie) {
            setupZombieWithBlocks((EntityZombie) mob);
        }
        
        // Set nearest player as target
        EntityPlayer nearestPlayer = findNearestPlayer(mob.world, mob);
        if (nearestPlayer != null) {
            mob.setAttackTarget(nearestPlayer);
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world == null || event.world.isRemote) {
            return;
        }

        if ((event.world.getTotalWorldTime() % UPDATE_INTERVAL_TICKS) != 0L) {
            return;
        }

        // Periodically update hostile mob targets
        for (Entity entity : event.world.loadedEntityList) {
            if (entity instanceof EntityLiving && entity instanceof IMob) {
                EntityLiving mob = (EntityLiving) entity;
                EntityPlayer nearestPlayer = findNearestPlayer(mob.world, mob);
                if (nearestPlayer != null) {
                    mob.setAttackTarget(nearestPlayer);
                }
            }
        }
    }

    private static void activateAllHostileMobs(World world, EntityPlayer player) {
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityLiving && entity instanceof IMob) {
                EntityLiving mob = (EntityLiving) entity;
                mob.setAttackTarget(player);
                
                if (mob instanceof EntityZombie && !hasBlockPlacingAI((EntityZombie) mob)) {
                    setupZombieWithBlocks((EntityZombie) mob);
                }
            }
        }
    }

    private static void setupZombieWithBlocks(EntityZombie zombie) {
        // Check if already has block-placing AI
        if (hasBlockPlacingAI(zombie)) {
            return;
        }
        
        // Add block-placing AI task (high priority)
        zombie.tasks.addTask(1, new EntityAIZombieBlockPlace(zombie));
        
        LOGGER.info("Zombie equipped with block-placing AI and {} blocks", 
            EntityAIZombieBlockPlace.getInventory(zombie).getBlockCount());
    }

    private static boolean hasBlockPlacingAI(EntityZombie zombie) {
        return zombie.tasks.taskEntries.stream()
            .anyMatch(entry -> entry.action instanceof EntityAIZombieBlockPlace);
    }

    private static EntityPlayer findNearestPlayer(World world, EntityLiving mob) {
        EntityPlayer nearest = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (EntityPlayer player : world.playerEntities) {
            if (player == null || !player.isEntityAlive() || player.isSpectator() || player.capabilities.disableDamage) {
                continue;
            }

            double distanceSq = mob.getDistanceSq(player);
            if (distanceSq >= bestDistanceSq) {
                continue;
            }

            bestDistanceSq = distanceSq;
            nearest = player;
        }

        return nearest;
    }
}
