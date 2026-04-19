package net.mcreator.ashenremains.block;

import net.mcreator.ashenremains.procedures.ActivatedAmbienceProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public class ActivatedSoulSandBlock extends Block {
   public ActivatedSoulSandBlock() {
      super(Properties.m_284310_().m_280658_(NoteBlockInstrument.SNARE).m_60918_(SoundType.f_56716_).m_60913_(1.0F, 10.0F).m_60953_(s -> 5).m_60956_(0.5F));
   }

   public int m_7753_(BlockState state, BlockGetter worldIn, BlockPos pos) {
      return 15;
   }

   public void m_6807_(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean moving) {
      super.m_6807_(blockstate, world, pos, oldState, moving);
      world.m_186460_(pos, this, 20);
   }

   public void m_213897_(BlockState blockstate, ServerLevel world, BlockPos pos, RandomSource random) {
      super.m_213897_(blockstate, world, pos, random);
      int x = pos.m_123341_();
      int y = pos.m_123342_();
      int z = pos.m_123343_();
      ActivatedAmbienceProcedure.execute(world, x, y, z);
      world.m_186460_(pos, this, 20);
   }
}
