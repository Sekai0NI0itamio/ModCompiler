/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.structure;

import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureSetKeys;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.SpreadType;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;

public interface OneTwentyOneStructureSets {
    public static void bootstrap(Registerable<StructureSet> structureSetRegisterable) {
        RegistryEntryLookup<Structure> registryEntryLookup = structureSetRegisterable.getRegistryLookup(RegistryKeys.STRUCTURE);
        structureSetRegisterable.register(StructureSetKeys.TRIAL_CHAMBERS, new StructureSet(registryEntryLookup.getOrThrow(StructureKeys.TRIAL_CHAMBERS), (StructurePlacement)new RandomSpreadStructurePlacement(34, 12, SpreadType.LINEAR, 94251327)));
    }
}

