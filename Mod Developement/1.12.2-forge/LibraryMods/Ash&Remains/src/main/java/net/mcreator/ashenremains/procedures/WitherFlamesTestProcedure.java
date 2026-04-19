package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent.Detonate;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class WitherFlamesTestProcedure {
   @SubscribeEvent
   public static void onExplode(Detonate event) {
      execute(
         event,
         event.getLevel(),
         event.getExplosion().getPosition().f_82479_,
         event.getExplosion().getPosition().f_82480_,
         event.getExplosion().getPosition().f_82481_
      );
   }

   public static void execute(LevelAccessor world, double x, double y, double z) {
      execute(null, world, x, y, z);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z) {
      double sx = 0.0;
      double sy = 0.0;
      double sz = 0.0;
      if (!world.m_6443_(WitherSkull.class, AABB.m_165882_(new Vec3(x, y, z), 4.0, 4.0, 4.0), e -> true).isEmpty()) {
         for (int index0 = 0; index0 < 30; index0++) {
            sx = Mth.m_216271_(RandomSource.m_216327_(), -4, 4);
            sy = Mth.m_216271_(RandomSource.m_216327_(), 0, 3);
            sz = Mth.m_216271_(RandomSource.m_216327_(), -4, 4);
            if (world.m_8055_(BlockPos.m_274561_(x + sx, y - sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("forge:ghast_replaceable")))) {
               if (Math.random() < 0.05) {
                  world.m_7731_(BlockPos.m_274561_(x + sx, y - sy, z + sz), ((Block)AshenremainsModBlocks.ACTIVATED_SOUL_SAND.get()).m_49966_(), 3);
               } else if (Math.random() < 0.1) {
                  world.m_7731_(BlockPos.m_274561_(x + sx, y - sy, z + sz), ((Block)AshenremainsModBlocks.ACTIVATED_SOUL_SOIL.get()).m_49966_(), 3);
               } else if (Math.random() < 0.6) {
                  world.m_7731_(BlockPos.m_274561_(x + sx, y - sy, z + sz), Blocks.f_50135_.m_49966_(), 3);
                  if (Math.random() < 0.1 && world.m_46859_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz))) {
                     world.m_7731_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz), Blocks.f_50084_.m_49966_(), 3);
                  } else if (Math.random() < 0.1 && world.m_46859_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz))) {
                     world.m_7731_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
                  }
               } else {
                  world.m_7731_(BlockPos.m_274561_(x + sx, y - sy, z + sz), Blocks.f_50136_.m_49966_(), 3);
                  if (Math.random() < 0.1 && world.m_46859_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz))) {
                     world.m_7731_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz), Blocks.f_50084_.m_49966_(), 3);
                  } else if (Math.random() < 0.1 && world.m_46859_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz))) {
                     world.m_7731_(BlockPos.m_274561_(x + sx, y - sy + 1.0, z + sz), ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(), 3);
                  }
               }
            }
         }
      }
   }
}
