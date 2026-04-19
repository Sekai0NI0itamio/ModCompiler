package net.mcreator.ashenremains.block;

import net.mcreator.ashenremains.procedures.AshGrabbedProcedure;
import net.mcreator.ashenremains.procedures.AshRainDissipateProcedure;
import net.mcreator.ashenremains.procedures.AshSilkTouchProcedure;
import net.mcreator.ashenremains.procedures.RegularAshDamageProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

public class AshBlock extends Block {
   public AshBlock() {
      super(
         Properties.m_284310_()
            .m_284180_(MapColor.f_283947_)
            .m_60918_(SoundType.f_56746_)
            .m_60913_(0.5F, 2.0F)
            .m_60999_()
            .m_60910_()
            .m_60977_()
            .m_278166_(PushReaction.DESTROY)
      );
   }

   public int m_7753_(BlockState state, BlockGetter worldIn, BlockPos pos) {
      return 15;
   }

   public void m_213897_(BlockState blockstate, ServerLevel world, BlockPos pos, RandomSource random) {
      super.m_213897_(blockstate, world, pos, random);
      int x = pos.m_123341_();
      int y = pos.m_123342_();
      int z = pos.m_123343_();
      AshRainDissipateProcedure.execute(world, x, y, z);
   }

   public boolean onDestroyedByPlayer(BlockState blockstate, Level world, BlockPos pos, Player entity, boolean willHarvest, FluidState fluid) {
      boolean retval = super.onDestroyedByPlayer(blockstate, world, pos, entity, willHarvest, fluid);
      AshSilkTouchProcedure.execute(world, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), entity);
      return retval;
   }

   public void m_7892_(BlockState blockstate, Level world, BlockPos pos, Entity entity) {
      super.m_7892_(blockstate, world, pos, entity);
      RegularAshDamageProcedure.execute(world, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), entity);
   }

   public InteractionResult m_6227_(BlockState blockstate, Level world, BlockPos pos, Player entity, InteractionHand hand, BlockHitResult hit) {
      super.m_6227_(blockstate, world, pos, entity, hand, hit);
      int x = pos.m_123341_();
      int y = pos.m_123342_();
      int z = pos.m_123343_();
      double hitX = hit.m_82450_().f_82479_;
      double hitY = hit.m_82450_().f_82480_;
      double hitZ = hit.m_82450_().f_82481_;
      Direction direction = hit.m_82434_();
      AshGrabbedProcedure.execute(world, x, y, z, entity);
      return InteractionResult.SUCCESS;
   }
}
