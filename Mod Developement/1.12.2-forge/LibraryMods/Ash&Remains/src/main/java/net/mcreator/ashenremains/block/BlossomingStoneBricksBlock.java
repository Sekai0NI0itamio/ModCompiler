package net.mcreator.ashenremains.block;

import net.mcreator.ashenremains.procedures.MossificationInitiationMarkIIProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public class BlossomingStoneBricksBlock extends Block {
   public BlossomingStoneBricksBlock() {
      super(
         Properties.m_284310_()
            .m_280658_(NoteBlockInstrument.BASEDRUM)
            .m_284180_(MapColor.f_283947_)
            .m_60918_(SoundType.f_56742_)
            .m_60913_(1.5F, 6.0F)
            .m_60999_()
            .m_60977_()
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
      MossificationInitiationMarkIIProcedure.execute(world, x, y, z);
   }
}
