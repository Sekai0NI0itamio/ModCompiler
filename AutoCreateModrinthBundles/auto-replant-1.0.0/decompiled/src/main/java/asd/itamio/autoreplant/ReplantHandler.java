package asd.itamio.autoreplant;

import java.util.Timer;
import java.util.TimerTask;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBeetroot;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ReplantHandler {
   @SubscribeEvent
   public void onBlockBreak(HarvestDropsEvent event) {
      World world = event.getWorld();
      BlockPos pos = event.getPos();
      IBlockState state = event.getState();
      EntityPlayer player = event.getHarvester();
      if (!world.field_72995_K && player != null) {
         Block block = state.func_177230_c();
         if (block instanceof BlockCrops) {
            this.handleCropReplant(world, pos, state, block, player);
         } else if (block instanceof BlockCocoa) {
            this.handleCocoaReplant(world, pos, state, player);
         } else if (block == Blocks.field_150364_r || block == Blocks.field_150363_s) {
            this.handleTreeReplant(world, pos, player);
         }
      }
   }

   private void handleCropReplant(final World world, final BlockPos pos, IBlockState state, Block block, EntityPlayer player) {
      BlockCrops crop = (BlockCrops)block;
      int age;
      int maxAge;
      if (block == Blocks.field_185773_cZ) {
         age = (Integer)state.func_177229_b(BlockBeetroot.field_185531_a);
         maxAge = 3;
      } else {
         age = (Integer)state.func_177229_b(BlockCrops.field_176488_a);
         maxAge = 7;
      }

      if (age >= maxAge) {
         Item seedItem = null;
         Block blockToPlant = null;
         if (block == Blocks.field_150464_aj) {
            seedItem = Items.field_151014_N;
            blockToPlant = Blocks.field_150464_aj;
         } else if (block == Blocks.field_150459_bM) {
            seedItem = Items.field_151172_bF;
            blockToPlant = Blocks.field_150459_bM;
         } else if (block == Blocks.field_150469_bN) {
            seedItem = Items.field_151174_bG;
            blockToPlant = Blocks.field_150469_bN;
         } else if (block == Blocks.field_185773_cZ) {
            seedItem = Items.field_185163_cU;
            blockToPlant = Blocks.field_185773_cZ;
         }

         if (seedItem != null && blockToPlant != null && this.consumeItemFromInventory(player, seedItem)) {
            final Block finalBlock = blockToPlant;
            new Timer().schedule(new TimerTask() {
               @Override
               public void run() {
                  if (world.func_175623_d(pos)) {
                     world.func_180501_a(pos, finalBlock.func_176223_P(), 3);
                  }
               }
            }, 50L);
         }
      }
   }

   private void handleCocoaReplant(final World world, final BlockPos pos, IBlockState state, EntityPlayer player) {
      int age = (Integer)state.func_177229_b(BlockCocoa.field_176501_a);
      if (age >= 2 && this.consumeItemFromInventory(player, Items.field_151100_aR, 3)) {
         final EnumFacing facing = (EnumFacing)state.func_177229_b(BlockCocoa.field_185512_D);
         new Timer()
            .schedule(
               new TimerTask() {
                  @Override
                  public void run() {
                     if (world.func_175623_d(pos)) {
                        IBlockState newState = Blocks.field_150375_by
                           .func_176223_P()
                           .func_177226_a(BlockCocoa.field_185512_D, facing)
                           .func_177226_a(BlockCocoa.field_176501_a, 0);
                        world.func_180501_a(pos, newState, 3);
                     }
                  }
               },
               50L
            );
      }
   }

   private void handleTreeReplant(World world, BlockPos pos, EntityPlayer player) {
      BlockPos groundPos = pos;

      while (
         world.func_180495_p(groundPos.func_177977_b()).func_177230_c() == Blocks.field_150364_r
            || world.func_180495_p(groundPos.func_177977_b()).func_177230_c() == Blocks.field_150363_s
      ) {
         groundPos = groundPos.func_177977_b();
      }

      Block belowBlock = world.func_180495_p(groundPos.func_177977_b()).func_177230_c();
      if ((belowBlock == Blocks.field_150346_d || belowBlock == Blocks.field_150349_c)
         && this.consumeItemFromInventory(player, Item.func_150898_a(Blocks.field_150345_g))) {
         world.func_175656_a(groundPos, Blocks.field_150345_g.func_176223_P());
      }
   }

   private boolean consumeItemFromInventory(EntityPlayer player, Item item) {
      return this.consumeItemFromInventory(player, item, -1);
   }

   private boolean consumeItemFromInventory(EntityPlayer player, Item item, int metadata) {
      if (player.field_71075_bZ.field_75098_d) {
         return true;
      } else {
         for (int i = 0; i < player.field_71071_by.func_70302_i_(); i++) {
            ItemStack stack = player.field_71071_by.func_70301_a(i);
            if (!stack.func_190926_b() && stack.func_77973_b() == item && (metadata < 0 || stack.func_77960_j() == metadata)) {
               stack.func_190918_g(1);
               if (stack.func_190926_b()) {
                  player.field_71071_by.func_70299_a(i, ItemStack.field_190927_a);
               }

               return true;
            }
         }

         return false;
      }
   }
}
