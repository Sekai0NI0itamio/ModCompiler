package com.sunhate.proxy;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;

public class ClientProxy extends CommonProxy {
   @Override
   public void registerItemRenderer(Item item, int metadataValue, String itemId) {
      ModelLoader.setCustomModelResourceLocation(item, metadataValue, new ModelResourceLocation("all_most_hate_the_sun:" + itemId, "inventory"));
   }
}
