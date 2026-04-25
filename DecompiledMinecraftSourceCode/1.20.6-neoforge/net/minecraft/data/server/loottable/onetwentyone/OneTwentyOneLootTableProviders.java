/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.data.server.loottable.onetwentyone;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.DataOutput;
import net.minecraft.data.server.loottable.LootTableProvider;
import net.minecraft.data.server.loottable.onetwentyone.OneTwentyOneBlockLootTableGenerator;
import net.minecraft.data.server.loottable.onetwentyone.OneTwentyOneChestLootTableGenerator;
import net.minecraft.data.server.loottable.onetwentyone.OneTwentyOneEntityLootTableGenerator;
import net.minecraft.data.server.loottable.onetwentyone.OneTwentyOneEquipmentLootTableGenerator;
import net.minecraft.data.server.loottable.onetwentyone.OneTwentyOneShearingLootTableGenerator;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryWrapper;

public class OneTwentyOneLootTableProviders {
    public static LootTableProvider createOneTwentyOneProvider(DataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookupFuture) {
        return new LootTableProvider(output, Set.of(), List.of(new LootTableProvider.LootTypeGenerator(OneTwentyOneBlockLootTableGenerator::new, LootContextTypes.BLOCK), new LootTableProvider.LootTypeGenerator(OneTwentyOneChestLootTableGenerator::new, LootContextTypes.CHEST), new LootTableProvider.LootTypeGenerator(OneTwentyOneEntityLootTableGenerator::new, LootContextTypes.ENTITY), new LootTableProvider.LootTypeGenerator(OneTwentyOneShearingLootTableGenerator::new, LootContextTypes.SHEARING), new LootTableProvider.LootTypeGenerator(OneTwentyOneEquipmentLootTableGenerator::new, LootContextTypes.EQUIPMENT)), registryLookupFuture);
    }
}

