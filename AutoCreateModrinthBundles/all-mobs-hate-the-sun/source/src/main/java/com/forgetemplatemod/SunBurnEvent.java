package com.forgetemplatemod;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Main.MOD_ID)
public class SunBurnEvent {

    public static final DamageSource SUN_DAMAGE = new DamageSource("sun").setFireDamage().setDamageBypassesArmor();

    @SubscribeEvent
    public static void onLivingUpdate(LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        
        // We only process logic on the server side and for hostile mobs
        if (!entity.world.isRemote && entity instanceof IMob && entity.isEntityAlive()) {
            
            // If it is day time
            if (entity.world.isDaytime()) {
                float f = entity.getBrightness();
                BlockPos pos = new BlockPos(entity.posX, Math.round(entity.posY), entity.posZ);

                // If exposed to the sun correctly (brightness > 0.5 and can see sky)
                if (f > 0.5F && entity.world.canSeeSky(pos) && entity.world.rand.nextFloat() * 30.0F < (f - 0.4F) * 2.0F) {
                    
                    // Note: In vanilla, wearing a helmet protects them and damages the helmet instead.
                    // We'll let the sun burn them regardless, OR we can respect the helmet rule but they still take the hard damage if they catch fire.
                    // We will just force them to catch fire if they aren't immune to it.
                    boolean inWater = entity.isInWater() || entity.isWet();

                    if (!inWater && !entity.isImmuneToFire()) {
                        // Apply normal fire effect so they visually burn
                        entity.setFire(8);
                        
                        // Drain 3 hearts (6.0F damage) per second (every 20 ticks) specifically due to sun
                        if (entity.ticksExisted % 20 == 0) {
                            entity.attackEntityFrom(SUN_DAMAGE, 6.0F); // 3 Full Hearts of damage
                        }
                    }
                }
            }
        }
    }
}