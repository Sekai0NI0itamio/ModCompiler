package asd.itamio.areadig;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class BlockBreakHandler {
   @SubscribeEvent
   public void onBlockBreak(BreakEvent event) {
      EntityPlayer player = event.getPlayer();
      World world = event.getWorld();
      BlockPos pos = event.getPos();
      if (!world.field_72995_K && player != null) {
         ItemStack heldItem = player.func_184614_ca();
         if (!heldItem.func_190926_b()) {
            int enchantmentLevel = EnchantmentHelper.func_77506_a(AreaDigMod.AREA_DIG_ENCHANTMENT, heldItem);
            if (enchantmentLevel > 0) {
               int radius = enchantmentLevel + 1;
               List<BlockPos> blocksToBreak = this.getBlocksInCube(pos, radius);
               List<ItemStack> allDrops = new ArrayList<>();

               for (BlockPos targetPos : blocksToBreak) {
                  if (!targetPos.equals(pos)) {
                     IBlockState state = world.func_180495_p(targetPos);
                     Block block = state.func_177230_c();
                     if (block != Blocks.field_150350_a && block != Blocks.field_150357_h) {
                        float hardness = state.func_185887_b(world, targetPos);
                        if (!(hardness < 0.0F) && ForgeHooks.canHarvestBlock(block, player, world, targetPos)) {
                           List<ItemStack> drops = this.collectBlockDrops(world, targetPos, player, heldItem, state);
                           allDrops.addAll(drops);
                           world.func_175698_g(targetPos);
                           if (!player.field_71075_bZ.field_75098_d) {
                              heldItem.func_77972_a(1, player);
                              if (heldItem.func_190916_E() == 0) {
                                 break;
                              }
                           }
                        }
                     }
                  }
               }

               this.spawnItemsAtPosition(world, pos, allDrops);
            }
         }
      }
   }

   private List<BlockPos> getBlocksInCube(BlockPos center, int radius) {
      List<BlockPos> blocks = new ArrayList<>();

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               blocks.add(center.func_177982_a(x, y, z));
            }
         }
      }

      return blocks;
   }

   private List<ItemStack> collectBlockDrops(World world, BlockPos pos, EntityPlayer player, ItemStack tool, IBlockState state) {
      List<ItemStack> drops = new ArrayList<>();

      try {
         NonNullList<ItemStack> blockDrops = NonNullList.func_191196_a();
         state.func_177230_c().getDrops(blockDrops, world, pos, state, 0);
         int fortune = EnchantmentHelper.func_77506_a(Enchantments.field_185308_t, tool);
         if (fortune > 0) {
            blockDrops.clear();
            state.func_177230_c().getDrops(blockDrops, world, pos, state, fortune);
         }

         drops.addAll(blockDrops);
      } catch (Exception var9) {
      }

      return drops;
   }

   private void spawnItemsAtPosition(World world, BlockPos pos, List<ItemStack> items) {
      for (ItemStack stack : this.combineItemStacks(items)) {
         if (!stack.func_190926_b()) {
            double x = pos.func_177958_n() + 0.5;
            double y = pos.func_177956_o() + 0.5;
            double z = pos.func_177952_p() + 0.5;
            EntityItem entityItem = new EntityItem(world, x, y, z, stack);
            entityItem.field_70159_w = 0.0;
            entityItem.field_70181_x = 0.0;
            entityItem.field_70179_y = 0.0;
            entityItem.func_174869_p();
            world.func_72838_d(entityItem);
         }
      }
   }

   private List<ItemStack> combineItemStacks(List<ItemStack> items) {
      List<ItemStack> combined = new ArrayList<>();

      for (ItemStack newStack : items) {
         if (!newStack.func_190926_b()) {
            boolean merged = false;

            for (ItemStack existingStack : combined) {
               if (this.canMerge(existingStack, newStack)) {
                  int spaceLeft = existingStack.func_77976_d() - existingStack.func_190916_E();
                  int toAdd = Math.min(spaceLeft, newStack.func_190916_E());
                  existingStack.func_190917_f(toAdd);
                  newStack.func_190918_g(toAdd);
                  if (newStack.func_190926_b()) {
                     merged = true;
                     break;
                  }
               }
            }

            if (!merged && !newStack.func_190926_b()) {
               combined.add(newStack.func_77946_l());
            }
         }
      }

      return combined;
   }

   private boolean canMerge(ItemStack stack1, ItemStack stack2) {
      if (!stack1.func_190926_b() && !stack2.func_190926_b()) {
         if (stack1.func_77973_b() != stack2.func_77973_b()) {
            return false;
         } else if (stack1.func_77960_j() != stack2.func_77960_j()) {
            return false;
         } else {
            return !ItemStack.func_77970_a(stack1, stack2) ? false : stack1.func_190916_E() < stack1.func_77976_d();
         }
      } else {
         return false;
      }
   }
}
