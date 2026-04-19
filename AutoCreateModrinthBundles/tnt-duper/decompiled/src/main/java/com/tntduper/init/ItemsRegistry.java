package com.tntduper.init;

import com.tntduper.items.AluminumAxe;
import com.tntduper.items.AluminumHoe;
import com.tntduper.items.AluminumIngot;
import com.tntduper.items.AluminumPickaxe;
import com.tntduper.items.AluminumShovel;
import com.tntduper.items.AluminumSword;
import com.tntduper.items.IRegisterable;
import java.util.ArrayList;
import net.minecraft.item.Item;
import net.minecraftforge.registries.IForgeRegistry;

public class ItemsRegistry {
   public static final ArrayList<IRegisterable> ITEMS = new ArrayList<>();
   public static final AluminumIngot aluminumIngot = new AluminumIngot("aluminum_ingot");
   public static final AluminumSword aluminumSword = new AluminumSword("aluminum_sword");
   public static final AluminumPickaxe aluminumPickaxe = new AluminumPickaxe("aluminum_pickaxe");
   public static final AluminumAxe aluminumAxe = new AluminumAxe("aluminum_axe");
   public static final AluminumShovel aluminumShovel = new AluminumShovel("aluminum_shovel");
   public static final AluminumHoe aluminumHoe = new AluminumHoe("aluminum_hoe");

   public static void register(IForgeRegistry<Item> registry) {
      registry.registerAll(new Item[]{aluminumIngot, aluminumSword, aluminumPickaxe, aluminumAxe, aluminumShovel, aluminumHoe});
   }

   public static void registerModels() {
      for (IRegisterable item : ITEMS) {
         item.registerItemModel();
      }
   }
}
