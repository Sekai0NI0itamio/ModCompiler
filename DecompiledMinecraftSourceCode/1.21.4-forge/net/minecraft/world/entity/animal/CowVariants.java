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

public class CowVariants {
    public static final ResourceKey<CowVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
    public static final ResourceKey<CowVariant> WARM = createKey(TemperatureVariants.WARM);
    public static final ResourceKey<CowVariant> COLD = createKey(TemperatureVariants.COLD);
    public static final ResourceKey<CowVariant> DEFAULT = TEMPERATE;

    private static ResourceKey<CowVariant> createKey(ResourceLocation p_397416_) {
        return ResourceKey.create(Registries.COW_VARIANT, p_397416_);
    }

    public static void bootstrap(BootstrapContext<CowVariant> p_397429_) {
        register(p_397429_, TEMPERATE, CowVariant.ModelType.NORMAL, "temperate_cow", SpawnPrioritySelectors.fallback(0));
        register(p_397429_, WARM, CowVariant.ModelType.WARM, "warm_cow", BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS);
        register(p_397429_, COLD, CowVariant.ModelType.COLD, "cold_cow", BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS);
    }

    private static void register(
        BootstrapContext<CowVariant> p_397751_, ResourceKey<CowVariant> p_394298_, CowVariant.ModelType p_392866_, String p_391818_, TagKey<Biome> p_395560_
    ) {
        HolderSet<Biome> holderset = p_397751_.lookup(Registries.BIOME).getOrThrow(p_395560_);
        register(p_397751_, p_394298_, p_392866_, p_391818_, SpawnPrioritySelectors.single(new BiomeCheck(holderset), 1));
    }

    private static void register(
        BootstrapContext<CowVariant> p_392734_,
        ResourceKey<CowVariant> p_391598_,
        CowVariant.ModelType p_397327_,
        String p_397887_,
        SpawnPrioritySelectors p_397627_
    ) {
        ResourceLocation resourcelocation = ResourceLocation.withDefaultNamespace("entity/cow/" + p_397887_);
        p_392734_.register(p_391598_, new CowVariant(new ModelAndTexture<>(p_397327_, resourcelocation), p_397627_));
    }
}
