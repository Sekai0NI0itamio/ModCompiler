/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.world.biome;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.OverworldBiomeCreator;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.feature.PlacedFeature;

public class OneTwentyOneBiomes {
    public static void bootstrap(Registerable<Biome> biomeRegisterable) {
        RegistryEntryLookup<PlacedFeature> registryEntryLookup = biomeRegisterable.getRegistryLookup(RegistryKeys.PLACED_FEATURE);
        RegistryEntryLookup<ConfiguredCarver<?>> registryEntryLookup2 = biomeRegisterable.getRegistryLookup(RegistryKeys.CONFIGURED_CARVER);
        SpawnSettings.SpawnEntry spawnEntry = new SpawnSettings.SpawnEntry(EntityType.BOGGED, 50, 4, 4);
        biomeRegisterable.register(BiomeKeys.MANGROVE_SWAMP, OverworldBiomeCreator.createMangroveSwamp(registryEntryLookup, registryEntryLookup2, builder -> builder.spawn(SpawnGroup.MONSTER, spawnEntry)));
        biomeRegisterable.register(BiomeKeys.SWAMP, OverworldBiomeCreator.createSwamp(registryEntryLookup, registryEntryLookup2, builder -> builder.spawn(SpawnGroup.MONSTER, spawnEntry)));
    }
}

