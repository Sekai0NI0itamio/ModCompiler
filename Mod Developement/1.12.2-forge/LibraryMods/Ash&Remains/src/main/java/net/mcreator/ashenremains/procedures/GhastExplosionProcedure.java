package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent.Detonate;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class GhastExplosionProcedure {
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
      if (!world.m_6443_(LargeFireball.class, AABB.m_165882_(new Vec3(x, y, z), 4.0, 4.0, 4.0), e -> true).isEmpty()) {
         for (int index0 = 0; index0 < 24; index0++) {
            sx = Mth.m_216271_(RandomSource.m_216327_(), -3, 3);
            sy = Mth.m_216271_(RandomSource.m_216327_(), 1, 3);
            sz = Mth.m_216271_(RandomSource.m_216327_(), -3, 3);
            if (world.m_8055_(BlockPos.m_274561_(x + sx, y - sy, z + sz)).m_204336_(BlockTags.create(new ResourceLocation("forge:ghast_replaceable")))) {
               if (Math.random() < 0.6) {
                  world.m_7731_(BlockPos.m_274561_(x + sx, y - sy, z + sz), Blocks.f_50135_.m_49966_(), 3);
               } else {
                  world.m_7731_(BlockPos.m_274561_(x + sx, y - sy, z + sz), Blocks.f_50136_.m_49966_(), 3);
               }
            }
         }
      }
   }
}
