package net.mcreator.ashenremains.block;

import net.mcreator.ashenremains.init.AshenremainsModBlocks;
import net.mcreator.ashenremains.procedures.ExpansionProcedureProcedure;
import net.mcreator.ashenremains.procedures.FloweringGrassBonemealProcedure;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterColorHandlersEvent.Item;
import net.minecraftforge.common.IPlantable;

public class FloweringGrassBlock extends Block {
   public FloweringGrassBlock() {
      super(
         Properties.m_284310_()
            .m_284180_(MapColor.f_283824_)
            .m_60918_(SoundType.f_56739_)
            .m_60913_(1.0F, 10.0F)
            .m_60955_()
            .m_60977_()
            .m_60924_((bs, br, bp) -> false)
      );
   }

   public boolean shouldDisplayFluidOverlay(BlockState state, BlockAndTintGetter world, BlockPos pos, FluidState fluidstate) {
      return true;
   }

   public int m_7753_(BlockState state, BlockGetter worldIn, BlockPos pos) {
      return 15;
   }

   public VoxelShape m_5909_(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
      return Shapes.m_83040_();
   }

   public boolean canSustainPlant(BlockState state, BlockGetter world, BlockPos pos, Direction direction, IPlantable plantable) {
      return true;
   }

   public void m_213897_(BlockState blockstate, ServerLevel world, BlockPos pos, RandomSource random) {
      super.m_213897_(blockstate, world, pos, random);
      int x = pos.m_123341_();
      int y = pos.m_123342_();
      int z = pos.m_123343_();
      ExpansionProcedureProcedure.execute(world, x, y, z);
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
      FloweringGrassBonemealProcedure.execute(world, x, y, z, entity);
      return InteractionResult.SUCCESS;
   }

   @OnlyIn(Dist.CLIENT)
   public static void blockColorLoad(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
      event.getBlockColors()
         .m_92589_(
            (bs, world, pos, index) -> world != null && pos != null ? BiomeColors.m_108793_(world, pos) : GrassColor.m_46415_(0.5, 1.0),
            new Block[]{(Block)AshenremainsModBlocks.FLOWERING_GRASS.get()}
         );
   }

   @OnlyIn(Dist.CLIENT)
   public static void itemColorLoad(Item event) {
      event.getItemColors().m_92689_((stack, index) -> GrassColor.m_46415_(0.5, 1.0), new ItemLike[]{(ItemLike)AshenremainsModBlocks.FLOWERING_GRASS.get()});
   }
}
