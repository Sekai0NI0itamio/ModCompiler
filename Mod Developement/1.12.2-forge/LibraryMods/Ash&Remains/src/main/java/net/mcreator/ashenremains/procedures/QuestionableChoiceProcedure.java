package net.mcreator.ashenremains.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

public class QuestionableChoiceProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, ItemStack itemstack) {
      if (entity != null) {
         if (Math.random() <= 0.25) {
            entity.m_20254_(Mth.m_216271_(RandomSource.m_216327_(), 4, 13));
         }

         if (world instanceof Level _level) {
            if (!_level.m_5776_()) {
               _level.m_5594_(
                  null,
                  BlockPos.m_274561_(x, y, z),
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F
               );
            } else {
               _level.m_7785_(
                  x,
                  y,
                  z,
                  (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("item.flintandsteel.use")),
                  SoundSource.NEUTRAL,
                  1.0F,
                  1.0F,
                  false
               );
            }
         }

         if (itemstack.m_220157_(1, RandomSource.m_216327_(), null)) {
            itemstack.m_41774_(1);
            itemstack.m_41721_(0);
         }
      }
   }
}
