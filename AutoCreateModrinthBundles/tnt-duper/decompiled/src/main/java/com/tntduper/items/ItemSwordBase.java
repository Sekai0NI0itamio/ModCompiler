package com.tntduper.items;

import com.tntduper.Main;
import com.tntduper.init.ItemsRegistry;
import net.minecraft.item.ItemSword;
import net.minecraft.item.Item.ToolMaterial;

public abstract class ItemSwordBase extends ItemSword implements IRegisterable {
   protected String name;

   public ItemSwordBase(ToolMaterial material, String name) {
      super(material);
      this.name = name;
      this.updateRegistryAndLocalizedName(name);
   }

   @Override
   public void registerItemModel() {
      Main.proxy.registerItemRenderer(this, 0, this.name);
   }

   @Override
   public void updateRegistryAndLocalizedName(String name) {
      this.setRegistryName(name);
      this.func_77655_b(name);
      ItemsRegistry.ITEMS.add(this);
   }
}
