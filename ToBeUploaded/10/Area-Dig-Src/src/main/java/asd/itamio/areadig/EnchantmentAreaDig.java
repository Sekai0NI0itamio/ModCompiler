package asd.itamio.areadig;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;

public class EnchantmentAreaDig extends Enchantment {
    
    public EnchantmentAreaDig() {
        super(Rarity.UNCOMMON, EnumEnchantmentType.DIGGER, new EntityEquipmentSlot[] {EntityEquipmentSlot.MAINHAND});
        this.setRegistryName("areadig", "area_dig");
        this.setName("area_dig");
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 10 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return this.getMinEnchantability(enchantmentLevel) + 50;
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public boolean canApply(ItemStack stack) {
        return stack.getItem() instanceof ItemPickaxe || 
               stack.getItem() instanceof ItemAxe || 
               stack.getItem() instanceof ItemSpade;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canApply(stack);
    }

    @Override
    public String getName() {
        return "enchantment.areadig.area_dig";
    }
}
