package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.mcreator.ashenremains.AshenremainsMod;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class FlamingArrowProcedure {
   @SubscribeEvent
   public static void onEntitySpawned(EntityJoinLevelEvent event) {
      execute(event, event.getLevel(), event.getEntity().m_20185_(), event.getEntity().m_20186_(), event.getEntity().m_20189_(), event.getEntity());
   }

   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
      execute(null, world, x, y, z, entity);
   }

   private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, Entity entity) {
      if (entity != null) {
         double baseRate = 0.0;
         double rateWithAmplifier = 0.0;
         if (entity instanceof Arrow && entity.m_6060_()) {
            AshenremainsMod.queueServerWork(
               Mth.m_216271_(RandomSource.m_216327_(), 180, 300),
               () -> {
                  if (entity.m_6084_()
                     && (
                        world.m_8055_(BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_() - 1.0, entity.m_20189_()))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                           || world.m_8055_(BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_() - 1.0, entity.m_20189_()))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                           || world.m_8055_(BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_() - 1.0, entity.m_20189_()))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))
                     )
                     && world.m_46859_(BlockPos.m_274561_(x, y, z))) {
                     world.m_7731_(
                        BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_()),
                        ((Block)AshenremainsModBlocks.WEIRD_FIRE.get()).m_49966_(),
                        3
                     );
                  } else if (entity.m_6350_() == Direction.NORTH
                     && entity.m_6084_()
                     && (
                        world.m_8055_(BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_() - 1.0))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                           || world.m_8055_(BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_() - 1.0))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                           || world.m_8055_(BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_() - 1.0))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))
                     )
                     && world.m_46859_(BlockPos.m_274561_(x, y, z))) {
                     world.m_7731_(
                        BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_()),
                        ((Block)AshenremainsModBlocks.SOUTH_FIRE.get()).m_49966_(),
                        3
                     );
                  } else if (entity.m_6350_() == Direction.SOUTH
                     && entity.m_6084_()
                     && (
                        world.m_8055_(BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_() + 1.0))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                           || world.m_8055_(BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_() + 1.0))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                           || world.m_8055_(BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_() + 1.0))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))
                     )
                     && world.m_46859_(BlockPos.m_274561_(x, y, z))) {
                     world.m_7731_(
                        BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_()),
                        ((Block)AshenremainsModBlocks.NORTH_FIRE.get()).m_49966_(),
                        3
                     );
                  } else if (entity.m_6350_() == Direction.WEST
                     && entity.m_6084_()
                     && (
                        world.m_8055_(BlockPos.m_274561_(entity.m_20185_() + 1.0, entity.m_20186_(), entity.m_20189_()))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                           || world.m_8055_(BlockPos.m_274561_(entity.m_20185_() + 1.0, entity.m_20186_(), entity.m_20189_()))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                           || world.m_8055_(BlockPos.m_274561_(entity.m_20185_() + 1.0, entity.m_20186_(), entity.m_20189_()))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))
                     )
                     && world.m_46859_(BlockPos.m_274561_(x, y, z))) {
                     world.m_7731_(
                        BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_()),
                        ((Block)AshenremainsModBlocks.WEST_FIRE.get()).m_49966_(),
                        3
                     );
                  } else if (entity.m_6350_() == Direction.EAST
                     && entity.m_6084_()
                     && (
                        world.m_8055_(BlockPos.m_274561_(entity.m_20185_() - 1.0, entity.m_20186_(), entity.m_20189_()))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:semi_flammable")))
                           || world.m_8055_(BlockPos.m_274561_(entity.m_20185_() - 1.0, entity.m_20186_(), entity.m_20189_()))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:flammable")))
                           || world.m_8055_(BlockPos.m_274561_(entity.m_20185_() - 1.0, entity.m_20186_(), entity.m_20189_()))
                              .m_204336_(BlockTags.create(new ResourceLocation("forge:other_flammable")))
                     )
                     && world.m_46859_(BlockPos.m_274561_(x, y, z))) {
                     world.m_7731_(
                        BlockPos.m_274561_(entity.m_20185_(), entity.m_20186_(), entity.m_20189_()),
                        ((Block)AshenremainsModBlocks.EAST_FIRE.get()).m_49966_(),
                        3
                     );
                  }

                  if (!entity.m_9236_().m_5776_()) {
                     entity.m_146870_();
                  }
               }
            );
         }
      }
   }
}
