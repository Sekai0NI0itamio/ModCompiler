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
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = MobVisionMod.MOD_ID, name = MobVisionMod.NAME, version = MobVisionMod.VERSION)
@Mod.EventBusSubscriber(modid = MobVisionMod.MOD_ID)
public final class MobVisionMod {
    public static final String MOD_ID = "mobvision";
    public static final String NAME = "Mob Vision";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    private static final long UPDATE_INTERVAL_TICKS = 20L;
    private static MobVisionConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new MobVisionConfig(new File(event.getModConfigurationDirectory(), "mobvision.txt"));
        config.load();
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityLiving) || !(event.getEntity() instanceof IMob) || config == null) {
            return;
        }

        config.reloadIfChanged();
        applyMobVision((EntityLiving) event.getEntity());
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world == null || event.world.isRemote || config == null) {
            return;
        }

        config.reloadIfChanged();
        if ((event.world.getTotalWorldTime() % UPDATE_INTERVAL_TICKS) != 0L) {
            return;
        }

        for (Entity entity : event.world.loadedEntityList) {
            if (entity instanceof EntityLiving && entity instanceof IMob) {
                applyMobVision((EntityLiving) entity);
            }
        }
    }

    private static void applyMobVision(EntityLiving mob) {
        String mobId = getMobId(mob);
        double configuredRange = config.getRangeForMob(mobId);
        IAttributeInstance followRange = mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE);
        if (followRange != null && Math.abs(followRange.getBaseValue() - configuredRange) > 0.001D) {
            followRange.setBaseValue(configuredRange);
        }

        if (configuredRange <= 0.0D) {
            mob.setAttackTarget(null);
            return;
        }

        EntityPlayer nearestPlayer = findNearestPlayer(mob.world, mob, configuredRange);
        mob.setAttackTarget(nearestPlayer);
    }

    private static EntityPlayer findNearestPlayer(World world, EntityLiving mob, double range) {
        EntityPlayer nearest = null;
        double bestDistanceSq = Double.MAX_VALUE;
        double maxDistanceSq = range * range;

        for (EntityPlayer player : world.playerEntities) {
            if (player == null || !player.isEntityAlive() || player.isSpectator() || player.capabilities.disableDamage) {
                continue;
            }

            double distanceSq = mob.getDistanceSq(player);
            if (distanceSq > maxDistanceSq || distanceSq >= bestDistanceSq) {
                continue;
            }

            bestDistanceSq = distanceSq;
            nearest = player;
        }

        return nearest;
    }

    private static String getMobId(EntityLiving mob) {
        ResourceLocation key = EntityListBridge.getEntityKey(mob);
        return key == null ? mob.getClass().getName() : key.toString();
    }
}
