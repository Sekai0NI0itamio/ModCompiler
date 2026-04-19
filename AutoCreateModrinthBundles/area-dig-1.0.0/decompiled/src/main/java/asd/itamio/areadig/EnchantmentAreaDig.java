package asd.itamio.areadig;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.enchantment.Enchantment.Rarity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;

public class EnchantmentAreaDig extends Enchantment {
   public EnchantmentAreaDig() {
      super(Rarity.UNCOMMON, EnumEnchantmentType.DIGGER, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
      this.setRegistryName("areadig", "area_dig");
      this.func_77322_b("area_dig");
   }

   public int func_77321_a(int enchantmentLevel) {
      return 10 + (enchantmentLevel - 1) * 10;
   }

   public int func_77317_b(int enchantmentLevel) {
      return this.func_77321_a(enchantmentLevel) + 50;
   }

   public int func_77325_b() {
      return 5;
   }

   public boolean func_92089_a(ItemStack stack) {
      return stack.func_77973_b() instanceof ItemPickaxe || stack.func_77973_b() instanceof ItemAxe || stack.func_77973_b() instanceof ItemSpade;
   }

   public boolean canApplyAtEnchantingTable(ItemStack stack) {
      return this.func_92089_a(stack);
   }

   public String func_77320_a() {
      return "enchantment.areadig.area_dig";
   }
}
