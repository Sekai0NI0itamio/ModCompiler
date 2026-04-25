package net.minecraft.world.entity.animal;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.variant.BiomeCheck;
import net.minecraft.world.entity.variant.ModelAndTexture;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.level.biome.Biome;

public class PigVariants {
    public static final ResourceKey<PigVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
    public static final ResourceKey<PigVariant> WARM = createKey(TemperatureVariants.WARM);
    public static final ResourceKey<PigVariant> COLD = createKey(TemperatureVariants.COLD);
    public static final ResourceKey<PigVariant> DEFAULT = TEMPERATE;

    private static ResourceKey<PigVariant> createKey(ResourceLocation p_392571_) {
        return ResourceKey.create(Registries.PIG_VARIANT, p_392571_);
    }

    public static void bootstrap(BootstrapContext<PigVariant> p_396775_) {
        register(p_396775_, TEMPERATE, PigVariant.ModelType.NORMAL, "temperate_pig", SpawnPrioritySelectors.fallback(0));
        register(p_396775_, WARM, PigVariant.ModelType.NORMAL, "warm_pig", BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS);
        register(p_396775_, COLD, PigVariant.ModelType.COLD, "cold_pig", BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS);
    }

    private static void register(
        BootstrapContext<PigVariant> p_391626_, ResourceKey<PigVariant> p_392770_, PigVariant.ModelType p_394848_, String p_395374_, TagKey<Biome> p_395744_
    ) {
        HolderSet<Biome> holderset = p_391626_.lookup(Registries.BIOME).getOrThrow(p_395744_);
        register(p_391626_, p_392770_, p_394848_, p_395374_, SpawnPrioritySelectors.single(new BiomeCheck(holderset), 1));
    }

    private static void register(
        BootstrapContext<PigVariant> p_396020_,
        ResourceKey<PigVariant> p_391953_,
        PigVariant.ModelType p_393259_,
        String p_395556_,
        SpawnPrioritySelectors p_396291_
    ) {
        ResourceLocation resourcelocation = ResourceLocation.withDefaultNamespace("entity/pig/" + p_395556_);
        p_396020_.register(p_391953_, new PigVariant(new ModelAndTexture<>(p_393259_, resourcelocation), p_396291_));
    }
}
