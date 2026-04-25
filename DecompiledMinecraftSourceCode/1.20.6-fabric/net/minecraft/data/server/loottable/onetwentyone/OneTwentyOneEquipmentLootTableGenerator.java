/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.data.server.loottable.onetwentyone;

import java.util.function.BiConsumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.data.server.loottable.LootTableGenerator;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.item.trim.ArmorTrimMaterial;
import net.minecraft.item.trim.ArmorTrimMaterials;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.item.trim.ArmorTrimPatterns;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LeafEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.loot.entry.LootTableEntry;
import net.minecraft.loot.function.SetComponentsLootFunction;
import net.minecraft.loot.function.SetEnchantmentsLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;

public class OneTwentyOneEquipmentLootTableGenerator
implements LootTableGenerator {
    @Override
    public void accept(RegistryWrapper.WrapperLookup registryLookup, BiConsumer<RegistryKey<LootTable>, LootTable.Builder> consumer) {
        RegistryWrapper.Impl<ArmorTrimPattern> impl = registryLookup.getOptionalWrapper(RegistryKeys.TRIM_PATTERN).orElseThrow();
        RegistryWrapper.Impl<ArmorTrimMaterial> impl2 = registryLookup.getOptionalWrapper(RegistryKeys.TRIM_MATERIAL).orElseThrow();
        ArmorTrim armorTrim = new ArmorTrim((RegistryEntry<ArmorTrimMaterial>)impl2.getOptional(ArmorTrimMaterials.COPPER).orElseThrow(), (RegistryEntry<ArmorTrimPattern>)impl.getOptional(ArmorTrimPatterns.FLOW).orElseThrow());
        ArmorTrim armorTrim2 = new ArmorTrim((RegistryEntry<ArmorTrimMaterial>)impl2.getOptional(ArmorTrimMaterials.COPPER).orElseThrow(), (RegistryEntry<ArmorTrimPattern>)impl.getOptional(ArmorTrimPatterns.BOLT).orElseThrow());
        consumer.accept(LootTables.TRIAL_CHAMBER_EQUIPMENT, LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder<?>)LootTableEntry.builder(OneTwentyOneEquipmentLootTableGenerator.builder(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, armorTrim2).build()).weight(4)).with((LootPoolEntry.Builder<?>)LootTableEntry.builder(OneTwentyOneEquipmentLootTableGenerator.builder(Items.IRON_HELMET, Items.IRON_CHESTPLATE, armorTrim).build()).weight(2)).with((LootPoolEntry.Builder<?>)LootTableEntry.builder(OneTwentyOneEquipmentLootTableGenerator.builder(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, armorTrim).build()).weight(1))));
        consumer.accept(LootTables.TRIAL_CHAMBER_MELEE_EQUIPMENT, LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with(LootTableEntry.builder(LootTables.TRIAL_CHAMBER_EQUIPMENT))).pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder<?>)ItemEntry.builder(Items.IRON_SWORD).weight(4)).with((LootPoolEntry.Builder<?>)((Object)ItemEntry.builder(Items.IRON_SWORD).apply(new SetEnchantmentsLootFunction.Builder().enchantment(Enchantments.SHARPNESS, ConstantLootNumberProvider.create(1.0f))))).with((LootPoolEntry.Builder<?>)((Object)ItemEntry.builder(Items.IRON_SWORD).apply(new SetEnchantmentsLootFunction.Builder().enchantment(Enchantments.KNOCKBACK, ConstantLootNumberProvider.create(1.0f))))).with(ItemEntry.builder(Items.DIAMOND_SWORD))));
        consumer.accept(LootTables.TRIAL_CHAMBER_RANGED_EQUIPMENT, LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with(LootTableEntry.builder(LootTables.TRIAL_CHAMBER_EQUIPMENT))).pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder<?>)ItemEntry.builder(Items.BOW).weight(2)).with((LootPoolEntry.Builder<?>)((Object)ItemEntry.builder(Items.BOW).apply(new SetEnchantmentsLootFunction.Builder().enchantment(Enchantments.POWER, ConstantLootNumberProvider.create(1.0f))))).with((LootPoolEntry.Builder<?>)((Object)ItemEntry.builder(Items.BOW).apply(new SetEnchantmentsLootFunction.Builder().enchantment(Enchantments.PUNCH, ConstantLootNumberProvider.create(1.0f)))))));
    }

    public static LootTable.Builder builder(Item helmet, Item chestplate, ArmorTrim trim) {
        return LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).conditionally(RandomChanceLootCondition.builder(0.5f)).with((LootPoolEntry.Builder<?>)((Object)((LeafEntry.Builder)ItemEntry.builder(helmet).apply(SetComponentsLootFunction.builder(DataComponentTypes.TRIM, trim))).apply(new SetEnchantmentsLootFunction.Builder().enchantment(Enchantments.PROTECTION, ConstantLootNumberProvider.create(4.0f)).enchantment(Enchantments.PROJECTILE_PROTECTION, ConstantLootNumberProvider.create(4.0f)).enchantment(Enchantments.FIRE_PROTECTION, ConstantLootNumberProvider.create(4.0f)))))).pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).conditionally(RandomChanceLootCondition.builder(0.5f)).with((LootPoolEntry.Builder<?>)((Object)((LeafEntry.Builder)ItemEntry.builder(chestplate).apply(SetComponentsLootFunction.builder(DataComponentTypes.TRIM, trim))).apply(new SetEnchantmentsLootFunction.Builder().enchantment(Enchantments.PROTECTION, ConstantLootNumberProvider.create(4.0f)).enchantment(Enchantments.PROJECTILE_PROTECTION, ConstantLootNumberProvider.create(4.0f)).enchantment(Enchantments.FIRE_PROTECTION, ConstantLootNumberProvider.create(4.0f))))));
    }
}

