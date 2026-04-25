/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.structure;

import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureManager;
import net.minecraft.util.registry.DynamicRegistryManager;

public record StructureContext(ResourceManager resourceManager, DynamicRegistryManager registryManager, StructureManager structureManager) {
    public static StructureContext from(ServerWorld world) {
        MinecraftServer minecraftServer = world.getServer();
        return new StructureContext(minecraftServer.getResourceManager(), minecraftServer.getRegistryManager(), minecraftServer.getStructureManager());
    }
}

