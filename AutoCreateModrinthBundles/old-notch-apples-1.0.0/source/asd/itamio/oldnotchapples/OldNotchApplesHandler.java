package asd.itamio.oldnotchapples;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class OldNotchApplesHandler {
    
    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!OldNotchApplesConfig.enabled) return;
        if (!OldNotchApplesConfig.use189Effects) return;
        
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        ItemStack stack = event.getItem();
        
        // Check if it's an Enchanted Golden Apple (meta 1)
        if (stack.getItem() == Items.GOLDEN_APPLE && stack.getMetadata() == 1) {
            // Remove vanilla effects first
            player.removePotionEffect(MobEffects.REGENERATION);
            player.removePotionEffect(MobEffects.ABSORPTION);
            player.removePotionEffect(MobEffects.RESISTANCE);
            player.removePotionEffect(MobEffects.FIRE_RESISTANCE);
            
            // Apply 1.8.9 effects
            // Regeneration V for 30 seconds (600 ticks)
            player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 600, 4)); // Level 4 = Regeneration V
            
            // Absorption IV for 2 minutes (2400 ticks)
            player.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 2400, 3)); // Level 3 = Absorption IV
            
            // Resistance for 5 minutes (6000 ticks)
            player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 6000, 0)); // Level 0 = Resistance I
            
            // Fire Resistance for 5 minutes (6000 ticks)
            player.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 6000, 0)); // Level 0 = Fire Resistance I
        }
    }
}
