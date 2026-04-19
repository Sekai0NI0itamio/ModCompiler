package net.mcreator.ashenremains.block;

import net.mcreator.ashenremains.procedures.FlamingAshDamageProcedure;
import net.mcreator.ashenremains.procedures.FlamingAshUpdateTickProcedure;
import net.mcreator.ashenremains.procedures.PunchingFlamesProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FlamingAshLayer4Block extends Block {
   public FlamingAshLayer4Block() {
      super(
         Properties.m_284310_()
            .m_284180_(MapColor.f_283947_)
            .m_60918_(SoundType.f_56746_)
            .m_60913_(0.25F, 2.0F)
            .m_60953_(s -> 5)
            .m_60999_()
            .m_60910_()
            .m_60955_()
            .m_60977_()
            .m_278166_(PushReaction.DESTROY)
            .m_60924_((bs, br, bp) -> false)
      );
   }

   public boolean m_7420_(BlockState state, BlockGetter reader, BlockPos pos) {
      return true;
   }

   public int m_7753_(BlockState state, BlockGetter worldIn, BlockPos pos) {
      return 0;
   }

   public VoxelShape m_5909_(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
      return Shapes.m_83040_();
   }

   public VoxelShape m_5940_(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
      return m_49796_(0.0, 0.0, 0.0, 16.0, 4.0, 16.0);
   }

   public void m_213897_(BlockState blockstate, ServerLevel world, BlockPos pos, RandomSource random) {
      super.m_213897_(blockstate, world, pos, random);
      int x = pos.m_123341_();
      int y = pos.m_123342_();
      int z = pos.m_123343_();
      FlamingAshUpdateTickProcedure.execute(world, x, y, z);
   }

   public boolean onDestroyedByPlayer(BlockState blockstate, Level world, BlockPos pos, Player entity, boolean willHarvest, FluidState fluid) {
      boolean retval = super.onDestroyedByPlayer(blockstate, world, pos, entity, willHarvest, fluid);
      PunchingFlamesProcedure.execute(world, entity);
      return retval;
   }

   public void m_7892_(BlockState blockstate, Level world, BlockPos pos, Entity entity) {
      super.m_7892_(blockstate, world, pos, entity);
      FlamingAshDamageProcedure.execute(world, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), entity);
   }
}
