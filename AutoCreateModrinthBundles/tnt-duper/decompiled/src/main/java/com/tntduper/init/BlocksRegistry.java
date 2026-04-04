package com.tntduper.init;

import com.tntduper.blocks.BlockBase;
import com.tntduper.blocks.BlockCompressedCobblestone;
import java.util.ArrayList;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.registries.IForgeRegistry;

public class BlocksRegistry {
   public static final ArrayList<BlockBase> BLOCKS = new ArrayList<>();
   public static final BlockCompressedCobblestone compressedCobblestone = new BlockCompressedCobblestone("compressed_cobblestone");

   public static void register(IForgeRegistry<Block> registry) {
      for (BlockBase block : BLOCKS) {
         registry.register(block);
      }
   }

   public static void registerItemBlocks(IForgeRegistry<Item> registry) {
      for (BlockBase block : BLOCKS) {
         registry.register(block.createItemBlock());
      }
   }

   public static void registerModels() {
      for (BlockBase block : BLOCKS) {
         block.registerItemModel(Item.func_150898_a(block));
      }
   }
}
