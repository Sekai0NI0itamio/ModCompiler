package net.mcreator.ashenremains.procedures;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent.Detonate;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class CreeperMossProcedure {
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
      if (world.m_6106_().m_5470_().m_46207_(GameRules.f_46132_)
         && !world.m_6443_(Creeper.class, AABB.m_165882_(new Vec3(x, y, z), 4.0, 4.0, 4.0), e -> true).isEmpty()) {
         if ((
               world.m_8055_(BlockPos.m_274561_(x, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:dirt")))
                  || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:base_stone_overworld")))
            )
            && (
               world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:dirt")))
                  || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z - 1.0))
                     .m_204336_(BlockTags.create(new ResourceLocation("minecraft:base_stone_overworld")))
            )
            && (
               world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:dirt")))
                  || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z + 1.0))
                     .m_204336_(BlockTags.create(new ResourceLocation("minecraft:base_stone_overworld")))
            )
            && (
               world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z - 1.0)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:dirt")))
                  || world.m_8055_(BlockPos.m_274561_(x + 1.0, y - 1.0, z - 1.0))
                     .m_204336_(BlockTags.create(new ResourceLocation("minecraft:base_stone_overworld")))
            )
            && (
               world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z + 1.0)).m_204336_(BlockTags.create(new ResourceLocation("minecraft:dirt")))
                  || world.m_8055_(BlockPos.m_274561_(x - 1.0, y - 1.0, z + 1.0))
                     .m_204336_(BlockTags.create(new ResourceLocation("minecraft:base_stone_overworld")))
            )) {
            world.m_7731_(BlockPos.m_274561_(x - 1.0, y - 1.0, z - 1.0), Blocks.f_152544_.m_49966_(), 3);
            world.m_7731_(BlockPos.m_274561_(x + 1.0, y - 1.0, z + 1.0), Blocks.f_152544_.m_49966_(), 3);
            world.m_7731_(BlockPos.m_274561_(x - 1.0, y - 1.0, z + 1.0), Blocks.f_152544_.m_49966_(), 3);
            world.m_7731_(BlockPos.m_274561_(x + 1.0, y - 1.0, z - 1.0), Blocks.f_152544_.m_49966_(), 3);
            if (world instanceof Level _level) {
               BlockPos _bp = BlockPos.m_274561_(x - 1.0, y - 1.0, z - 1.0);
               if ((
                     BoneMealItem.m_40627_(new ItemStack(Items.f_42499_), _level, _bp)
                        || BoneMealItem.m_40631_(new ItemStack(Items.f_42499_), _level, _bp, null)
                  )
                  && !_level.m_5776_()) {
                  _level.m_46796_(2005, _bp, 0);
               }
            }

            if (world instanceof Level _levelx) {
               BlockPos _bp = BlockPos.m_274561_(x + 1.0, y - 1.0, z + 1.0);
               if ((
                     BoneMealItem.m_40627_(new ItemStack(Items.f_42499_), _levelx, _bp)
                        || BoneMealItem.m_40631_(new ItemStack(Items.f_42499_), _levelx, _bp, null)
                  )
                  && !_levelx.m_5776_()) {
                  _levelx.m_46796_(2005, _bp, 0);
               }
            }

            if (world instanceof Level _levelxx) {
               BlockPos _bp = BlockPos.m_274561_(x - 1.0, y - 1.0, z + 1.0);
               if ((
                     BoneMealItem.m_40627_(new ItemStack(Items.f_42499_), _levelxx, _bp)
                        || BoneMealItem.m_40631_(new ItemStack(Items.f_42499_), _levelxx, _bp, null)
                  )
                  && !_levelxx.m_5776_()) {
                  _levelxx.m_46796_(2005, _bp, 0);
               }
            }

            if (world instanceof Level _levelxxx) {
               BlockPos _bp = BlockPos.m_274561_(x + 1.0, y - 1.0, z - 1.0);
               if ((
                     BoneMealItem.m_40627_(new ItemStack(Items.f_42499_), _levelxxx, _bp)
                        || BoneMealItem.m_40631_(new ItemStack(Items.f_42499_), _levelxxx, _bp, null)
                  )
                  && !_levelxxx.m_5776_()) {
                  _levelxxx.m_46796_(2005, _bp, 0);
               }
            }
         }

         TheMossIsRealProcedure.execute(world, x, y, z);
      }
   }
}
