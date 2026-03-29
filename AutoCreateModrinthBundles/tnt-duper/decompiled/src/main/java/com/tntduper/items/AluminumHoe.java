package com.tntduper.items;

import net.minecraft.item.Item.ToolMaterial;
import net.minecraftforge.common.util.EnumHelper;

public class AluminumHoe extends ItemShovelBase {
   public static final ToolMaterial ALUMINUM_TOOL_MATERIAL = EnumHelper.addToolMaterial("ALUMINUM", 3, 99999, 9999.0F, 9999999.0F, 14);

   public AluminumHoe(String name) {
      super(ALUMINUM_TOOL_MATERIAL, name);
   }
}
