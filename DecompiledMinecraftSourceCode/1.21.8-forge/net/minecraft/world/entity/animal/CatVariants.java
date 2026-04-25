package net.minecraft.world.entity.animal;

import java.util.List;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.variant.MoonBrightnessCheck;
import net.minecraft.world.entity.variant.PriorityProvider;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.entity.variant.StructureCheck;
import net.minecraft.world.level.levelgen.structure.Structure;

public interface CatVariants {
    ResourceKey<CatVariant> TABBY = createKey("tabby");
    ResourceKey<CatVariant> BLACK = createKey("black");
    ResourceKey<CatVariant> RED = createKey("red");
    ResourceKey<CatVariant> SIAMESE = createKey("siamese");
    ResourceKey<CatVariant> BRITISH_SHORTHAIR = createKey("british_shorthair");
    ResourceKey<CatVariant> CALICO = createKey("calico");
    ResourceKey<CatVariant> PERSIAN = createKey("persian");
    ResourceKey<CatVariant> RAGDOLL = createKey("ragdoll");
    ResourceKey<CatVariant> WHITE = createKey("white");
    ResourceKey<CatVariant> JELLIE = createKey("jellie");
    ResourceKey<CatVariant> ALL_BLACK = createKey("all_black");

    private static ResourceKey<CatVariant> createKey(String p_392922_) {
        return ResourceKey.create(Registries.CAT_VARIANT, ResourceLocation.withDefaultNamespace(p_392922_));
    }

    static void bootstrap(BootstrapContext<CatVariant> p_394778_) {
        HolderGetter<Structure> holdergetter = p_394778_.lookup(Registries.STRUCTURE);
        registerForAnyConditions(p_394778_, TABBY, "entity/cat/tabby");
        registerForAnyConditions(p_394778_, BLACK, "entity/cat/black");
        registerForAnyConditions(p_394778_, RED, "entity/cat/red");
        registerForAnyConditions(p_394778_, SIAMESE, "entity/cat/siamese");
        registerForAnyConditions(p_394778_, BRITISH_SHORTHAIR, "entity/cat/british_shorthair");
        registerForAnyConditions(p_394778_, CALICO, "entity/cat/calico");
        registerForAnyConditions(p_394778_, PERSIAN, "entity/cat/persian");
        registerForAnyConditions(p_394778_, RAGDOLL, "entity/cat/ragdoll");
        registerForAnyConditions(p_394778_, WHITE, "entity/cat/white");
        registerForAnyConditions(p_394778_, JELLIE, "entity/cat/jellie");
        register(
            p_394778_,
            ALL_BLACK,
            "entity/cat/all_black",
            new SpawnPrioritySelectors(
                List.of(
                    new PriorityProvider.Selector<>(new StructureCheck(holdergetter.getOrThrow(StructureTags.CATS_SPAWN_AS_BLACK)), 1),
                    new PriorityProvider.Selector<>(new MoonBrightnessCheck(MinMaxBounds.Doubles.atLeast(0.9)), 0)
                )
            )
        );
    }

    private static void registerForAnyConditions(BootstrapContext<CatVariant> p_393449_, ResourceKey<CatVariant> p_393102_, String p_395640_) {
        register(p_393449_, p_393102_, p_395640_, SpawnPrioritySelectors.fallback(0));
    }

    private static void register(BootstrapContext<CatVariant> p_394846_, ResourceKey<CatVariant> p_394887_, String p_392213_, SpawnPrioritySelectors p_396826_) {
        p_394846_.register(p_394887_, new CatVariant(new ClientAsset(ResourceLocation.withDefaultNamespace(p_392213_)), p_396826_));
    }
}
