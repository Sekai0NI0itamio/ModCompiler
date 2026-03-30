package com.sunhate;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@EventBusSubscriber(
   modid = "all_most_hate_the_sun"
)
public class SunBurnEvent {
   public static final DamageSource SUN_DAMAGE = new DamageSource("sun").func_76361_j().func_76348_h();

   @SubscribeEvent
   public static void onLivingUpdate(LivingUpdateEvent event) {
      EntityLivingBase entity = event.getEntityLiving();
      if (!entity.field_70170_p.field_72995_K && entity instanceof IMob && entity.func_70089_S() && entity.field_70170_p.func_72935_r()) {
         float f = entity.func_70013_c();
         BlockPos pos = new BlockPos(entity.field_70165_t, Math.round(entity.field_70163_u), entity.field_70161_v);
         if (f > 0.5F && entity.field_70170_p.func_175678_i(pos) && entity.field_70170_p.field_73012_v.nextFloat() * 30.0F < (f - 0.4F) * 2.0F) {
            boolean inWater = entity.func_70090_H() || entity.func_70026_G();
            if (!inWater && !entity.func_70045_F()) {
               entity.func_70015_d(8);
               if (entity.field_70173_aa % 20 == 0) {
                  entity.func_70097_a(SUN_DAMAGE, 6.0F);
               }
            }
         }
      }
   }
}
