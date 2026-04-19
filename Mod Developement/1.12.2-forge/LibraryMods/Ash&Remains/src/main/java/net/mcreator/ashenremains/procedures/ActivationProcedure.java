package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

@EventBusSubscriber
public class ActivationProcedure {
   @SubscribeEvent
   public static void onBlockPlace(EntityPlaceEvent event) {
      execute(event, event.getLevel(), event.getPos().m_123341_(), event.getPos().m_123342_(), event.getPos().m_123343_());
   }

   public static void execute(LevelAccessor world, double x, double y, double z) {
      execute(null, world, x, y, z);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z) {
      if (world.m_8055_(BlockPos.m_274561_(x, y, z)).m_60734_() == Blocks.f_50312_) {
         if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50135_) {
            world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), ((Block)AshenremainsModBlocks.ACTIVATED_SOUL_SAND.get()).m_49966_(), 3);
            if (world instanceof Level _level) {
               if (!_level.m_5776_()) {
                  _level.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("ambient.soul_sand_valley.mood")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     2.0F
                  );
               } else {
                  _level.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("ambient.soul_sand_valley.mood")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     2.0F,
                     false
                  );
               }
            }
         } else if (world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_60734_() == Blocks.f_50136_) {
            world.m_7731_(BlockPos.m_274561_(x, y - 1.0, z), ((Block)AshenremainsModBlocks.ACTIVATED_SOUL_SOIL.get()).m_49966_(), 3);
            if (world instanceof Level _levelx) {
               if (!_levelx.m_5776_()) {
                  _levelx.m_5594_(
                     null,
                     BlockPos.m_274561_(x, y, z),
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("ambient.soul_sand_valley.mood")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     2.0F
                  );
               } else {
                  _levelx.m_7785_(
                     x,
                     y,
                     z,
                     (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("ambient.soul_sand_valley.mood")),
                     SoundSource.NEUTRAL,
                     1.0F,
                     2.0F,
                     false
                  );
               }
            }
         }
      }
   }
}
