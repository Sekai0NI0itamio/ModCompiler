package net.mcreator.ashenremains.block;

import net.mcreator.ashenremains.procedures.AdditionalCharredSoundProcedure;
import net.mcreator.ashenremains.procedures.CharredDisintegrateProcedure;
import net.mcreator.ashenremains.procedures.CharredSilkProcedure;
import net.mcreator.ashenremains.procedures.RainDisintegrateProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.ForgeRegistries;

public class CharredRootsBlock extends Block {
   public static final EnumProperty<Axis> AXIS = BlockStateProperties.f_61365_;

   public CharredRootsBlock() {
      super(
         Properties.m_284310_()
            .m_284180_(MapColor.f_283861_)
            .m_60918_(
               new ForgeSoundType(
                  1.0F,
                  1.0F,
                  () -> (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.mangrove_roots.break")),
                  () -> (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step")),
                  () -> (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.mangrove_roots.place")),
                  () -> (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.mangrove_roots.hit")),
                  () -> (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.step"))
               )
            )
            .m_60978_(3.0F)
            .m_60999_()
            .m_60955_()
            .m_60977_()
            .m_60924_((bs, br, bp) -> false)
      );
      this.m_49959_((BlockState)((BlockState)this.f_49792_.m_61090_()).m_61124_(AXIS, Axis.Y));
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

   protected void m_7926_(Builder<Block, BlockState> builder) {
      builder.m_61104_(new Property[]{AXIS});
   }

   public BlockState m_5573_(BlockPlaceContext context) {
      return (BlockState)this.m_49966_().m_61124_(AXIS, context.m_43719_().m_122434_());
   }

   public BlockState m_6843_(BlockState state, Rotation rot) {
      if (rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90) {
         if (state.m_61143_(AXIS) == Axis.X) {
            return (BlockState)state.m_61124_(AXIS, Axis.Z);
         }

         if (state.m_61143_(AXIS) == Axis.Z) {
            return (BlockState)state.m_61124_(AXIS, Axis.X);
         }
      }

      return state;
   }

   public int getFlammability(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
      return 5;
   }

   public void m_6807_(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean moving) {
      super.m_6807_(blockstate, world, pos, oldState, moving);
      AdditionalCharredSoundProcedure.execute(world, pos.m_123341_(), pos.m_123342_(), pos.m_123343_());
   }

   public void m_6861_(BlockState blockstate, Level world, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean moving) {
      super.m_6861_(blockstate, world, pos, neighborBlock, fromPos, moving);
      CharredDisintegrateProcedure.execute(world, pos.m_123341_(), pos.m_123342_(), pos.m_123343_());
   }

   public void m_213897_(BlockState blockstate, ServerLevel world, BlockPos pos, RandomSource random) {
      super.m_213897_(blockstate, world, pos, random);
      int x = pos.m_123341_();
      int y = pos.m_123342_();
      int z = pos.m_123343_();
      RainDisintegrateProcedure.execute(world, x, y, z);
   }

   public boolean onDestroyedByPlayer(BlockState blockstate, Level world, BlockPos pos, Player entity, boolean willHarvest, FluidState fluid) {
      boolean retval = super.onDestroyedByPlayer(blockstate, world, pos, entity, willHarvest, fluid);
      CharredSilkProcedure.execute(world, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), entity);
      return retval;
   }
}
