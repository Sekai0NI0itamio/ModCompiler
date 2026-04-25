package net.minecraft.world.entity.npc;

import com.google.common.collect.ImmutableSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public record VillagerProfession(
    Component name,
    Predicate<Holder<PoiType>> heldJobSite,
    Predicate<Holder<PoiType>> acquirableJobSite,
    ImmutableSet<Item> requestedItems,
    ImmutableSet<Block> secondaryPoi,
    @Nullable SoundEvent workSound
) {
    public static final Predicate<Holder<PoiType>> ALL_ACQUIRABLE_JOBS = p_238239_ -> p_238239_.is(PoiTypeTags.ACQUIRABLE_JOB_SITE);
    public static final ResourceKey<VillagerProfession> NONE = createKey("none");
    public static final ResourceKey<VillagerProfession> ARMORER = createKey("armorer");
    public static final ResourceKey<VillagerProfession> BUTCHER = createKey("butcher");
    public static final ResourceKey<VillagerProfession> CARTOGRAPHER = createKey("cartographer");
    public static final ResourceKey<VillagerProfession> CLERIC = createKey("cleric");
    public static final ResourceKey<VillagerProfession> FARMER = createKey("farmer");
    public static final ResourceKey<VillagerProfession> FISHERMAN = createKey("fisherman");
    public static final ResourceKey<VillagerProfession> FLETCHER = createKey("fletcher");
    public static final ResourceKey<VillagerProfession> LEATHERWORKER = createKey("leatherworker");
    public static final ResourceKey<VillagerProfession> LIBRARIAN = createKey("librarian");
    public static final ResourceKey<VillagerProfession> MASON = createKey("mason");
    public static final ResourceKey<VillagerProfession> NITWIT = createKey("nitwit");
    public static final ResourceKey<VillagerProfession> SHEPHERD = createKey("shepherd");
    public static final ResourceKey<VillagerProfession> TOOLSMITH = createKey("toolsmith");
    public static final ResourceKey<VillagerProfession> WEAPONSMITH = createKey("weaponsmith");

    private static ResourceKey<VillagerProfession> createKey(String p_392057_) {
        return ResourceKey.create(Registries.VILLAGER_PROFESSION, ResourceLocation.withDefaultNamespace(p_392057_));
    }

    private static VillagerProfession register(
        Registry<VillagerProfession> p_393915_, ResourceKey<VillagerProfession> p_219645_, ResourceKey<PoiType> p_392275_, @Nullable SoundEvent p_219646_
    ) {
        return register(p_393915_, p_219645_, p_219668_ -> p_219668_.is(p_392275_), p_219640_ -> p_219640_.is(p_392275_), p_219646_);
    }

    private static VillagerProfession register(
        Registry<VillagerProfession> p_394693_,
        ResourceKey<VillagerProfession> p_219649_,
        Predicate<Holder<PoiType>> p_393436_,
        Predicate<Holder<PoiType>> p_397870_,
        @Nullable SoundEvent p_219652_
    ) {
        return register(p_394693_, p_219649_, p_393436_, p_397870_, ImmutableSet.of(), ImmutableSet.of(), p_219652_);
    }

    private static VillagerProfession register(
        Registry<VillagerProfession> p_395015_,
        ResourceKey<VillagerProfession> p_394072_,
        ResourceKey<PoiType> p_395537_,
        ImmutableSet<Item> p_219662_,
        ImmutableSet<Block> p_219663_,
        @Nullable SoundEvent p_219664_
    ) {
        return register(
            p_395015_, p_394072_, p_238234_ -> p_238234_.is(p_395537_), p_238237_ -> p_238237_.is(p_395537_), p_219662_, p_219663_, p_219664_
        );
    }

    private static VillagerProfession register(
        Registry<VillagerProfession> p_395735_,
        ResourceKey<VillagerProfession> p_397170_,
        Predicate<Holder<PoiType>> p_219655_,
        Predicate<Holder<PoiType>> p_219656_,
        ImmutableSet<Item> p_396051_,
        ImmutableSet<Block> p_394349_,
        @Nullable SoundEvent p_219657_
    ) {
        return Registry.register(
            p_395735_,
            p_397170_,
            new VillagerProfession(
                Component.translatable("entity." + p_397170_.location().getNamespace() + ".villager." + p_397170_.location().getPath()),
                p_219655_,
                p_219656_,
                p_396051_,
                p_394349_,
                p_219657_
            )
        );
    }

    public static VillagerProfession bootstrap(Registry<VillagerProfession> p_393047_) {
        register(p_393047_, NONE, PoiType.NONE, ALL_ACQUIRABLE_JOBS, null);
        register(p_393047_, ARMORER, PoiTypes.ARMORER, SoundEvents.VILLAGER_WORK_ARMORER);
        register(p_393047_, BUTCHER, PoiTypes.BUTCHER, SoundEvents.VILLAGER_WORK_BUTCHER);
        register(p_393047_, CARTOGRAPHER, PoiTypes.CARTOGRAPHER, SoundEvents.VILLAGER_WORK_CARTOGRAPHER);
        register(p_393047_, CLERIC, PoiTypes.CLERIC, SoundEvents.VILLAGER_WORK_CLERIC);
        register(
            p_393047_,
            FARMER,
            PoiTypes.FARMER,
            ImmutableSet.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.BONE_MEAL),
            ImmutableSet.of(Blocks.FARMLAND),
            SoundEvents.VILLAGER_WORK_FARMER
        );
        register(p_393047_, FISHERMAN, PoiTypes.FISHERMAN, SoundEvents.VILLAGER_WORK_FISHERMAN);
        register(p_393047_, FLETCHER, PoiTypes.FLETCHER, SoundEvents.VILLAGER_WORK_FLETCHER);
        register(p_393047_, LEATHERWORKER, PoiTypes.LEATHERWORKER, SoundEvents.VILLAGER_WORK_LEATHERWORKER);
        register(p_393047_, LIBRARIAN, PoiTypes.LIBRARIAN, SoundEvents.VILLAGER_WORK_LIBRARIAN);
        register(p_393047_, MASON, PoiTypes.MASON, SoundEvents.VILLAGER_WORK_MASON);
        register(p_393047_, NITWIT, PoiType.NONE, PoiType.NONE, null);
        register(p_393047_, SHEPHERD, PoiTypes.SHEPHERD, SoundEvents.VILLAGER_WORK_SHEPHERD);
        register(p_393047_, TOOLSMITH, PoiTypes.TOOLSMITH, SoundEvents.VILLAGER_WORK_TOOLSMITH);
        return register(p_393047_, WEAPONSMITH, PoiTypes.WEAPONSMITH, SoundEvents.VILLAGER_WORK_WEAPONSMITH);
    }
}
