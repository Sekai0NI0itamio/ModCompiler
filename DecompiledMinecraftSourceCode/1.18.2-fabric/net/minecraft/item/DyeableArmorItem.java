/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.item;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.Item;

public class DyeableArmorItem
extends ArmorItem
implements DyeableItem {
    public DyeableArmorItem(ArmorMaterial armorMaterial, EquipmentSlot equipmentSlot, Item.Settings settings) {
        super(armorMaterial, equipmentSlot, settings);
    }
}

