package com.tntduper.items;

import com.tntduper.Main;
import com.tntduper.init.ItemsRegistry;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.Item.ToolMaterial;

public abstract class ItemAxeBase extends ItemAxe implements IRegisterable {
   protected String name;

   protected ItemAxeBase(ToolMaterial material, String name) {
      super(material, 9999.0F, 9999.0F);
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
