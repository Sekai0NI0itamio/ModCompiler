package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class SkeletonBonemealProcedure {
   @SubscribeEvent
   public static void onEntityDeath(LivingDeathEvent event) {
      if (event != null && event.getEntity() != null) {
         execute(
            event,
            event.getEntity().m_9236_(),
            event.getEntity().m_20185_(),
            event.getEntity().m_20186_(),
            event.getEntity().m_20189_(),
            event.getSource(),
            event.getEntity()
         );
      }
   }

   public static void execute(LevelAccessor world, double x, double y, double z, DamageSource damagesource, Entity entity) {
      execute(null, world, x, y, z, damagesource, entity);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, DamageSource damagesource, Entity entity) {
      if (damagesource != null && entity != null) {
         if (world.m_6106_().m_5470_().m_46207_(GameRules.f_46132_)
            && !damagesource.m_276093_(DamageTypes.f_268468_)
            && entity.m_6095_().m_204039_(TagKey.m_203882_(Registries.f_256939_, new ResourceLocation("minecraft:skeletons")))
            && Math.random() < 0.08
            && world instanceof Level _level) {
            BlockPos _bp = BlockPos.m_274561_(x, y - 1.0, z);
            if ((BoneMealItem.m_40627_(new ItemStack(Items.f_42499_), _level, _bp) || BoneMealItem.m_40631_(new ItemStack(Items.f_42499_), _level, _bp, null))
               && !_level.m_5776_()) {
               _level.m_46796_(2005, _bp, 0);
            }
         }
      }
   }
}
