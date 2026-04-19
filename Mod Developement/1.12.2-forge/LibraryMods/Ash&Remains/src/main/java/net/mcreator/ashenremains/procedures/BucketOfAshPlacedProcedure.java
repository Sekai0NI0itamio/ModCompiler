package net.mcreator.ashenremains.procedures;

import net.mcreator.ashenremains.AshenremainsMod;
import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

public class BucketOfAshPlacedProcedure {
   public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, ItemStack itemstack) {
      if (entity != null) {
         double raytrace_y = 0.0;
         double raytrace_x = 0.0;
         double raytrace_z = 0.0;
         if (entity.m_9236_()
                  .m_45547_(
                     new ClipContext(
                        entity.m_20299_(1.0F), entity.m_20299_(1.0F).m_82549_(entity.m_20252_(1.0F).m_82490_(5.0)), Block.OUTLINE, Fluid.NONE, entity
                     )
                  )
                  .m_6662_()
               == Type.BLOCK
            && !(new Object() {
                  public boolean checkGamemode(Entity _ent) {
                     if (_ent instanceof ServerPlayer _serverPlayer) {
                        return _serverPlayer.f_8941_.m_9290_() == GameType.ADVENTURE;
                     } else {
                        return _ent.m_9236_().m_5776_() && _ent instanceof Player _player
                           ? Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()) != null
                              && Minecraft.m_91087_().m_91403_().m_104949_(_player.m_36316_().getId()).m_105325_() == GameType.ADVENTURE
                           : false;
                     }
                  }
               })
               .checkGamemode(entity)) {
            raytrace_x = entity.m_9236_()
               .m_45547_(
                  new ClipContext(entity.m_20299_(1.0F), entity.m_20299_(1.0F).m_82549_(entity.m_20252_(1.0F).m_82490_(5.0)), Block.OUTLINE, Fluid.NONE, entity)
               )
               .m_82425_()
               .m_123341_();
            raytrace_y = entity.m_9236_()
               .m_45547_(
                  new ClipContext(entity.m_20299_(1.0F), entity.m_20299_(1.0F).m_82549_(entity.m_20252_(1.0F).m_82490_(5.0)), Block.OUTLINE, Fluid.NONE, entity)
               )
               .m_82425_()
               .m_123342_();
            raytrace_z = entity.m_9236_()
               .m_45547_(
                  new ClipContext(entity.m_20299_(1.0F), entity.m_20299_(1.0F).m_82549_(entity.m_20252_(1.0F).m_82490_(5.0)), Block.OUTLINE, Fluid.NONE, entity)
               )
               .m_82425_()
               .m_123343_();
            if (world.m_8055_(BlockPos.m_274561_(raytrace_x, raytrace_y, raytrace_z)).m_60815_()
               && world.m_46859_(BlockPos.m_274561_(raytrace_x, raytrace_y + 1.0, raytrace_z))) {
               world.m_7731_(
                  BlockPos.m_274561_(raytrace_x, raytrace_y + 1.0, raytrace_z),
                  ((net.minecraft.world.level.block.Block)AshenremainsModBlocks.ASH.get()).m_49966_(),
                  3
               );
               if (!(entity instanceof Player _plr && _plr.m_150110_().f_35937_)) {
                  itemstack.m_41774_(1);
                  AshenremainsMod.queueServerWork(1, () -> {
                     if (entity instanceof Player _player) {
                        ItemStack _setstack = new ItemStack(Items.f_42446_).m_41777_();
                        _setstack.m_41764_(1);
                        ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
                     }
                  });
               }

               if (world.m_8055_(BlockPos.m_274561_(raytrace_x, raytrace_y, raytrace_z)).m_60734_() == Blocks.f_50440_
                  || world.m_8055_(BlockPos.m_274561_(raytrace_x, raytrace_y, raytrace_z)).m_60734_() == Blocks.f_152481_
                  || world.m_8055_(BlockPos.m_274561_(raytrace_x, raytrace_y, raytrace_z)).m_60734_() == Blocks.f_50599_
                  || world.m_8055_(BlockPos.m_274561_(raytrace_x, raytrace_y, raytrace_z)).m_60734_() == AshenremainsModBlocks.FLOWERING_GRASS.get()) {
                  world.m_7731_(
                     BlockPos.m_274561_(raytrace_x, raytrace_y, raytrace_z),
                     ((net.minecraft.world.level.block.Block)AshenremainsModBlocks.ASHY_GRASS.get()).m_49966_(),
                     3
                  );
               }

               if (entity instanceof LivingEntity _entity) {
                  _entity.m_21011_(InteractionHand.MAIN_HAND, true);
               }

               if (world instanceof Level _level) {
                  if (!_level.m_5776_()) {
                     _level.m_5594_(
                        null,
                        BlockPos.m_274561_(x, y, z),
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F
                     );
                  } else {
                     _level.m_7785_(
                        x,
                        y,
                        z,
                        (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F,
                        false
                     );
                  }
               }
            }
         }
      }
   }
}
