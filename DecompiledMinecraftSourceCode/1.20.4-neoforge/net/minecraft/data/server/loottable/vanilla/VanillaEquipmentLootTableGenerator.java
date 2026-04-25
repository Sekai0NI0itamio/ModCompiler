/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.data.server.loottable.vanilla;

import java.util.function.BiConsumer;
import net.minecraft.data.server.loottable.LootTableGenerator;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;

public class VanillaEquipmentLootTableGenerator
implements LootTableGenerator {
    @Override
    public void accept(RegistryWrapper.WrapperLookup registryLookup, BiConsumer<RegistryKey<LootTable>, LootTable.Builder> consumer) {
        consumer.accept(LootTables.TRIAL_CHAMBER_EQUIPMENT, LootTable.builder());
        consumer.accept(LootTables.TRIAL_CHAMBER_MELEE_EQUIPMENT, LootTable.builder());
        consumer.accept(LootTables.TRIAL_CHAMBER_RANGED_EQUIPMENT, LootTable.builder());
    }
}

