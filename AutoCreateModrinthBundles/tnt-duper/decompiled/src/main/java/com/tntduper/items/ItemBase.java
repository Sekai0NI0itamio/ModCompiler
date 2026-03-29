package com.tntduper.items;

import com.tntduper.Main;
import com.tntduper.init.ItemsRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.Item.ToolMaterial;

public class ItemBase extends Item implements IRegisterable, ICreativeTabbable {
   protected String name;

   public ItemBase(String name) {
      this.name = name;
      this.updateRegistryAndLocalizedName(name);
   }

   @Override
   public void registerItemModel() {
      Main.proxy.registerItemRenderer(this, 0, this.name);
   }

   public ItemBase(ToolMaterial material, String name) {
      this.name = name;
   }

   public ItemBase setCreativeTab(CreativeTabs tab) {
      super.func_77637_a(tab);
      return this;
   }

   @Override
   public void updateRegistryAndLocalizedName(String name) {
      this.func_77655_b(name);
      this.setRegistryName(name);
      ItemsRegistry.ITEMS.add(this);
   }
}
