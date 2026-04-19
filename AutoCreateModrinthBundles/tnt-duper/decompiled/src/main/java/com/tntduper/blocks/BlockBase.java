package com.tntduper.blocks;

import com.tntduper.Main;
import com.tntduper.init.BlocksRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;

public class BlockBase extends Block {
   protected String name;

   public BlockBase(Material material, String name) {
      super(material);
      this.func_149663_c(name);
      this.setRegistryName(name);
      BlocksRegistry.BLOCKS.add(this);
   }

   public void registerItemModel(Item itemBlock) {
      Main.proxy.registerItemRenderer(itemBlock, 0, this.name);
   }

   public Item createItemBlock() {
      ItemBlock itemBlock = new ItemBlock(this);
      itemBlock.setRegistryName(this.getRegistryName());
      return itemBlock;
   }

   public BlockBase setCreativeTab(CreativeTabs tab) {
      super.func_149647_a(tab);
      return this;
   }
}
