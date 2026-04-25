/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.data.server.advancement.onetwentyone;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.DataOutput;
import net.minecraft.data.server.advancement.AdvancementProvider;
import net.minecraft.data.server.advancement.onetwentyone.OneTwentyOneAdventureTabAdvancementGenerator;
import net.minecraft.registry.RegistryWrapper;

public class OneTwentyOneAdvancementProviders {
    public static AdvancementProvider createOneTwentyOneProvider(DataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookupFuture) {
        return new AdvancementProvider(output, registryLookupFuture, List.of(new OneTwentyOneAdventureTabAdvancementGenerator()));
    }
}

