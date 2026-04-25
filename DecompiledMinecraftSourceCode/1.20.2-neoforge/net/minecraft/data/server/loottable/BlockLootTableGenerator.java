/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.data.server.loottable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.datagen.v1.loot.FabricBlockLootTableGenerator;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.CaveVines;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.FlowerbedBlock;
import net.minecraft.block.MultifaceGrowthBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.data.server.loottable.LootTableGenerator;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.BlockStatePropertyLootCondition;
import net.minecraft.loot.condition.LocationCheckLootCondition;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionConsumingBuilder;
import net.minecraft.loot.condition.MatchToolLootCondition;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.condition.SurvivesExplosionLootCondition;
import net.minecraft.loot.condition.TableBonusLootCondition;
import net.minecraft.loot.entry.AlternativeEntry;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LeafEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.loot.function.ApplyBonusLootFunction;
import net.minecraft.loot.function.CopyComponentsLootFunction;
import net.minecraft.loot.function.CopyStateLootFunction;
import net.minecraft.loot.function.ExplosionDecayLootFunction;
import net.minecraft.loot.function.LimitCountLootFunction;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.LootFunctionConsumingBuilder;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.operator.BoundedIntUnaryOperator;
import net.minecraft.loot.provider.number.BinomialLootNumberProvider;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.LootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.predicate.BlockPredicate;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.StatePredicate;
import net.minecraft.predicate.entity.LocationPredicate;
import net.minecraft.predicate.item.EnchantmentPredicate;
import net.minecraft.predicate.item.EnchantmentsPredicate;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.predicate.item.ItemSubPredicateTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.state.property.Property;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public abstract class BlockLootTableGenerator
implements LootTableGenerator,
FabricBlockLootTableGenerator {
    public static final LootCondition.Builder WITH_SILK_TOUCH = MatchToolLootCondition.builder(ItemPredicate.Builder.create().subPredicate(ItemSubPredicateTypes.ENCHANTMENTS, EnchantmentsPredicate.enchantments(List.of(new EnchantmentPredicate(Enchantments.SILK_TOUCH, NumberRange.IntRange.atLeast(1))))));
    public static final LootCondition.Builder WITHOUT_SILK_TOUCH = WITH_SILK_TOUCH.invert();
    public static final LootCondition.Builder WITH_SHEARS = MatchToolLootCondition.builder(ItemPredicate.Builder.create().items(Items.SHEARS));
    public static final LootCondition.Builder WITH_SILK_TOUCH_OR_SHEARS = WITH_SHEARS.or(WITH_SILK_TOUCH);
    public static final LootCondition.Builder WITHOUT_SILK_TOUCH_NOR_SHEARS = WITH_SILK_TOUCH_OR_SHEARS.invert();
    protected final Set<Item> explosionImmuneItems;
    protected final FeatureSet requiredFeatures;
    protected final Map<RegistryKey<LootTable>, LootTable.Builder> lootTables;
    public static final float[] SAPLING_DROP_CHANCE = new float[]{0.05f, 0.0625f, 0.083333336f, 0.1f};
    public static final float[] LEAVES_STICK_DROP_CHANCE = new float[]{0.02f, 0.022222223f, 0.025f, 0.033333335f, 0.1f};

    protected BlockLootTableGenerator(Set<Item> explosionImmuneItems, FeatureSet requiredFeatures) {
        this(explosionImmuneItems, requiredFeatures, new HashMap<RegistryKey<LootTable>, LootTable.Builder>());
    }

    protected BlockLootTableGenerator(Set<Item> explosionImmuneItems, FeatureSet requiredFeatures, Map<RegistryKey<LootTable>, LootTable.Builder> lootTables) {
        this.explosionImmuneItems = explosionImmuneItems;
        this.requiredFeatures = requiredFeatures;
        this.lootTables = lootTables;
    }

    public <T extends LootFunctionConsumingBuilder<T>> T applyExplosionDecay(ItemConvertible drop, LootFunctionConsumingBuilder<T> builder) {
        if (!this.explosionImmuneItems.contains(drop.asItem())) {
            return builder.apply(ExplosionDecayLootFunction.builder());
        }
        return builder.getThisFunctionConsumingBuilder();
    }

    public <T extends LootConditionConsumingBuilder<T>> T addSurvivesExplosionCondition(ItemConvertible drop, LootConditionConsumingBuilder<T> builder) {
        if (!this.explosionImmuneItems.contains(drop.asItem())) {
            return builder.conditionally(SurvivesExplosionLootCondition.builder());
        }
        return builder.getThisConditionConsumingBuilder();
    }

    public LootTable.Builder drops(ItemConvertible drop) {
        return LootTable.builder().pool(this.addSurvivesExplosionCondition(drop, LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with(ItemEntry.builder(drop))));
    }

    public static LootTable.Builder drops(Block drop, LootCondition.Builder conditionBuilder, LootPoolEntry.Builder<?> child) {
        return LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with(((LeafEntry.Builder)ItemEntry.builder(drop).conditionally(conditionBuilder)).alternatively(child)));
    }

    public static LootTable.Builder dropsWithSilkTouch(Block drop, LootPoolEntry.Builder<?> child) {
        return BlockLootTableGenerator.drops(drop, WITH_SILK_TOUCH, child);
    }

    public static LootTable.Builder dropsWithShears(Block drop, LootPoolEntry.Builder<?> child) {
        return BlockLootTableGenerator.drops(drop, WITH_SHEARS, child);
    }

    public static LootTable.Builder dropsWithSilkTouchOrShears(Block drop, LootPoolEntry.Builder<?> child) {
        return BlockLootTableGenerator.drops(drop, WITH_SILK_TOUCH_OR_SHEARS, child);
    }

    public LootTable.Builder drops(Block dropWithSilkTouch, ItemConvertible drop) {
        return BlockLootTableGenerator.dropsWithSilkTouch(dropWithSilkTouch, (LootPoolEntry.Builder)this.addSurvivesExplosionCondition(dropWithSilkTouch, ItemEntry.builder(drop)));
    }

    public LootTable.Builder drops(ItemConvertible drop, LootNumberProvider count) {
        return LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder)this.applyExplosionDecay(drop, ItemEntry.builder(drop).apply(SetCountLootFunction.builder(count)))));
    }

    public LootTable.Builder drops(Block dropWithSilkTouch, ItemConvertible drop, LootNumberProvider count) {
        return BlockLootTableGenerator.dropsWithSilkTouch(dropWithSilkTouch, (LootPoolEntry.Builder)this.applyExplosionDecay(dropWithSilkTouch, ItemEntry.builder(drop).apply(SetCountLootFunction.builder(count))));
    }

    public static LootTable.Builder dropsWithSilkTouch(ItemConvertible drop) {
        return LootTable.builder().pool(LootPool.builder().conditionally(WITH_SILK_TOUCH).rolls(ConstantLootNumberProvider.create(1.0f)).with(ItemEntry.builder(drop)));
    }

    public final LootTable.Builder pottedPlantDrops(ItemConvertible drop) {
        return LootTable.builder().pool(this.addSurvivesExplosionCondition(Blocks.FLOWER_POT, LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with(ItemEntry.builder(Blocks.FLOWER_POT)))).pool(this.addSurvivesExplosionCondition(drop, LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with(ItemEntry.builder(drop))));
    }

    public LootTable.Builder slabDrops(Block drop) {
        return LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder)this.applyExplosionDecay(drop, ItemEntry.builder(drop).apply((LootFunction.Builder)((Object)SetCountLootFunction.builder(ConstantLootNumberProvider.create(2.0f)).conditionally(BlockStatePropertyLootCondition.builder(drop).properties(StatePredicate.Builder.create().exactMatch(SlabBlock.TYPE, SlabType.DOUBLE))))))));
    }

    public <T extends Comparable<T> & StringIdentifiable> LootTable.Builder dropsWithProperty(Block drop, Property<T> property, T value) {
        return LootTable.builder().pool(this.addSurvivesExplosionCondition(drop, LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder<?>)ItemEntry.builder(drop).conditionally(BlockStatePropertyLootCondition.builder(drop).properties(StatePredicate.Builder.create().exactMatch(property, value))))));
    }

    public LootTable.Builder nameableContainerDrops(Block drop) {
        return LootTable.builder().pool(this.addSurvivesExplosionCondition(drop, LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder<?>)((Object)ItemEntry.builder(drop).apply(CopyComponentsLootFunction.builder(CopyComponentsLootFunction.Source.BLOCK_ENTITY).include(DataComponentTypes.CUSTOM_NAME))))));
    }

    public LootTable.Builder shulkerBoxDrops(Block drop) {
        return LootTable.builder().pool(this.addSurvivesExplosionCondition(drop, LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder<?>)((Object)ItemEntry.builder(drop).apply(CopyComponentsLootFunction.builder(CopyComponentsLootFunction.Source.BLOCK_ENTITY).include(DataComponentTypes.CUSTOM_NAME).include(DataComponentTypes.CONTAINER).include(DataComponentTypes.LOCK).include(DataComponentTypes.CONTAINER_LOOT))))));
    }

    public LootTable.Builder copperOreDrops(Block drop) {
        return BlockLootTableGenerator.dropsWithSilkTouch(drop, (LootPoolEntry.Builder)this.applyExplosionDecay(drop, ((LeafEntry.Builder)ItemEntry.builder(Items.RAW_COPPER).apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(2.0f, 5.0f)))).apply(ApplyBonusLootFunction.oreDrops(Enchantments.FORTUNE))));
    }

    public LootTable.Builder lapisOreDrops(Block drop) {
        return BlockLootTableGenerator.dropsWithSilkTouch(drop, (LootPoolEntry.Builder)this.applyExplosionDecay(drop, ((LeafEntry.Builder)ItemEntry.builder(Items.LAPIS_LAZULI).apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(4.0f, 9.0f)))).apply(ApplyBonusLootFunction.oreDrops(Enchantments.FORTUNE))));
    }

    public LootTable.Builder redstoneOreDrops(Block drop) {
        return BlockLootTableGenerator.dropsWithSilkTouch(drop, (LootPoolEntry.Builder)this.applyExplosionDecay(drop, ((LeafEntry.Builder)ItemEntry.builder(Items.REDSTONE).apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(4.0f, 5.0f)))).apply(ApplyBonusLootFunction.uniformBonusCount(Enchantments.FORTUNE))));
    }

    public LootTable.Builder bannerDrops(Block drop) {
        return LootTable.builder().pool(this.addSurvivesExplosionCondition(drop, LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder<?>)((Object)ItemEntry.builder(drop).apply(CopyComponentsLootFunction.builder(CopyComponentsLootFunction.Source.BLOCK_ENTITY).include(DataComponentTypes.CUSTOM_NAME).include(DataComponentTypes.ITEM_NAME).include(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP).include(DataComponentTypes.BANNER_PATTERNS))))));
    }

    public static LootTable.Builder beeNestDrops(Block drop) {
        return LootTable.builder().pool(LootPool.builder().conditionally(WITH_SILK_TOUCH).rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder<?>)((Object)((LeafEntry.Builder)ItemEntry.builder(drop).apply(CopyComponentsLootFunction.builder(CopyComponentsLootFunction.Source.BLOCK_ENTITY).include(DataComponentTypes.BEES))).apply(CopyStateLootFunction.builder(drop).addProperty(BeehiveBlock.HONEY_LEVEL)))));
    }

    public static LootTable.Builder beehiveDrops(Block drop) {
        return LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with(((LootPoolEntry.Builder)((Object)((LeafEntry.Builder)((LeafEntry.Builder)ItemEntry.builder(drop).conditionally(WITH_SILK_TOUCH)).apply(CopyComponentsLootFunction.builder(CopyComponentsLootFunction.Source.BLOCK_ENTITY).include(DataComponentTypes.BEES))).apply(CopyStateLootFunction.builder(drop).addProperty(BeehiveBlock.HONEY_LEVEL)))).alternatively(ItemEntry.builder(drop))));
    }

    public static LootTable.Builder glowBerryDrops(Block drop) {
        return LootTable.builder().pool(LootPool.builder().with(ItemEntry.builder(Items.GLOW_BERRIES)).conditionally(BlockStatePropertyLootCondition.builder(drop).properties(StatePredicate.Builder.create().exactMatch(CaveVines.BERRIES, true))));
    }

    public LootTable.Builder oreDrops(Block dropWithSilkTouch, Item drop) {
        return BlockLootTableGenerator.dropsWithSilkTouch(dropWithSilkTouch, (LootPoolEntry.Builder)this.applyExplosionDecay(dropWithSilkTouch, ItemEntry.builder(drop).apply(ApplyBonusLootFunction.oreDrops(Enchantments.FORTUNE))));
    }

    public LootTable.Builder mushroomBlockDrops(Block dropWithSilkTouch, ItemConvertible drop) {
        return BlockLootTableGenerator.dropsWithSilkTouch(dropWithSilkTouch, (LootPoolEntry.Builder)this.applyExplosionDecay(dropWithSilkTouch, ((LeafEntry.Builder)ItemEntry.builder(drop).apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(-6.0f, 2.0f)))).apply(LimitCountLootFunction.builder(BoundedIntUnaryOperator.createMin(0)))));
    }

    public LootTable.Builder shortPlantDrops(Block dropWithShears) {
        return BlockLootTableGenerator.dropsWithShears(dropWithShears, (LootPoolEntry.Builder)this.applyExplosionDecay(dropWithShears, ((LeafEntry.Builder)ItemEntry.builder(Items.WHEAT_SEEDS).conditionally(RandomChanceLootCondition.builder(0.125f))).apply(ApplyBonusLootFunction.uniformBonusCount(Enchantments.FORTUNE, 2))));
    }

    public LootTable.Builder cropStemDrops(Block stem, Item drop) {
        return LootTable.builder().pool(this.applyExplosionDecay(stem, LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder)ItemEntry.builder(drop).apply(StemBlock.AGE.getValues(), integer -> SetCountLootFunction.builder(BinomialLootNumberProvider.create(3, (float)(integer + 1) / 15.0f)).conditionally(BlockStatePropertyLootCondition.builder(stem).properties(StatePredicate.Builder.create().exactMatch(StemBlock.AGE, integer.intValue())))))));
    }

    public LootTable.Builder attachedCropStemDrops(Block stem, Item drop) {
        return LootTable.builder().pool(this.applyExplosionDecay(stem, LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder<?>)((Object)ItemEntry.builder(drop).apply(SetCountLootFunction.builder(BinomialLootNumberProvider.create(3, 0.53333336f)))))));
    }

    public static LootTable.Builder dropsWithShears(ItemConvertible drop) {
        return LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).conditionally(WITH_SHEARS).with(ItemEntry.builder(drop)));
    }

    public LootTable.Builder multifaceGrowthDrops(Block drop, LootCondition.Builder condition) {
        return LootTable.builder().pool(LootPool.builder().with((LootPoolEntry.Builder)this.applyExplosionDecay(drop, ((LeafEntry.Builder)((LeafEntry.Builder)ItemEntry.builder(drop).conditionally(condition)).apply(Direction.values(), direction -> SetCountLootFunction.builder(ConstantLootNumberProvider.create(1.0f), true).conditionally(BlockStatePropertyLootCondition.builder(drop).properties(StatePredicate.Builder.create().exactMatch(MultifaceGrowthBlock.getProperty(direction), true))))).apply(SetCountLootFunction.builder(ConstantLootNumberProvider.create(-1.0f), true)))));
    }

    public LootTable.Builder leavesDrops(Block leaves, Block drop, float ... chance) {
        return BlockLootTableGenerator.dropsWithSilkTouchOrShears(leaves, ((LeafEntry.Builder)this.addSurvivesExplosionCondition(leaves, ItemEntry.builder(drop))).conditionally(TableBonusLootCondition.builder(Enchantments.FORTUNE, chance))).pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).conditionally(WITHOUT_SILK_TOUCH_NOR_SHEARS).with((LootPoolEntry.Builder<?>)((LeafEntry.Builder)this.applyExplosionDecay(leaves, ItemEntry.builder(Items.STICK).apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 2.0f))))).conditionally(TableBonusLootCondition.builder(Enchantments.FORTUNE, LEAVES_STICK_DROP_CHANCE))));
    }

    public LootTable.Builder oakLeavesDrops(Block leaves, Block drop, float ... chance) {
        return this.leavesDrops(leaves, drop, chance).pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).conditionally(WITHOUT_SILK_TOUCH_NOR_SHEARS).with((LootPoolEntry.Builder<?>)((LeafEntry.Builder)this.addSurvivesExplosionCondition(leaves, ItemEntry.builder(Items.APPLE))).conditionally(TableBonusLootCondition.builder(Enchantments.FORTUNE, 0.005f, 0.0055555557f, 0.00625f, 0.008333334f, 0.025f))));
    }

    public LootTable.Builder mangroveLeavesDrops(Block leaves) {
        return BlockLootTableGenerator.dropsWithSilkTouchOrShears(leaves, ((LeafEntry.Builder)this.applyExplosionDecay(Blocks.MANGROVE_LEAVES, ItemEntry.builder(Items.STICK).apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 2.0f))))).conditionally(TableBonusLootCondition.builder(Enchantments.FORTUNE, LEAVES_STICK_DROP_CHANCE)));
    }

    public LootTable.Builder cropDrops(Block crop, Item product, Item seeds, LootCondition.Builder condition) {
        return this.applyExplosionDecay(crop, LootTable.builder().pool(LootPool.builder().with(((LeafEntry.Builder)ItemEntry.builder(product).conditionally(condition)).alternatively(ItemEntry.builder(seeds)))).pool(LootPool.builder().conditionally(condition).with((LootPoolEntry.Builder<?>)((Object)ItemEntry.builder(seeds).apply(ApplyBonusLootFunction.binomialWithBonusCount(Enchantments.FORTUNE, 0.5714286f, 3))))));
    }

    public static LootTable.Builder seagrassDrops(Block seagrass) {
        return LootTable.builder().pool(LootPool.builder().conditionally(WITH_SHEARS).with((LootPoolEntry.Builder<?>)((Object)ItemEntry.builder(seagrass).apply(SetCountLootFunction.builder(ConstantLootNumberProvider.create(2.0f))))));
    }

    public LootTable.Builder tallPlantDrops(Block tallPlant, Block shortPlant) {
        AlternativeEntry.Builder builder = ((LeafEntry.Builder)((LootPoolEntry.Builder)((Object)ItemEntry.builder(shortPlant).apply(SetCountLootFunction.builder(ConstantLootNumberProvider.create(2.0f))))).conditionally(WITH_SHEARS)).alternatively((LootPoolEntry.Builder<?>)((LeafEntry.Builder)this.addSurvivesExplosionCondition(tallPlant, ItemEntry.builder(Items.WHEAT_SEEDS))).conditionally(RandomChanceLootCondition.builder(0.125f)));
        return LootTable.builder().pool(LootPool.builder().with(builder).conditionally(BlockStatePropertyLootCondition.builder(tallPlant).properties(StatePredicate.Builder.create().exactMatch(TallPlantBlock.HALF, DoubleBlockHalf.LOWER))).conditionally(LocationCheckLootCondition.builder(LocationPredicate.Builder.create().block(BlockPredicate.Builder.create().blocks(tallPlant).state(StatePredicate.Builder.create().exactMatch(TallPlantBlock.HALF, DoubleBlockHalf.UPPER))), new BlockPos(0, 1, 0)))).pool(LootPool.builder().with(builder).conditionally(BlockStatePropertyLootCondition.builder(tallPlant).properties(StatePredicate.Builder.create().exactMatch(TallPlantBlock.HALF, DoubleBlockHalf.UPPER))).conditionally(LocationCheckLootCondition.builder(LocationPredicate.Builder.create().block(BlockPredicate.Builder.create().blocks(tallPlant).state(StatePredicate.Builder.create().exactMatch(TallPlantBlock.HALF, DoubleBlockHalf.LOWER))), new BlockPos(0, -1, 0))));
    }

    public LootTable.Builder candleDrops(Block candle) {
        return LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder)this.applyExplosionDecay(candle, (LootFunctionConsumingBuilder)ItemEntry.builder(candle).apply(List.of(Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4)), candles -> SetCountLootFunction.builder(ConstantLootNumberProvider.create(candles.intValue())).conditionally(BlockStatePropertyLootCondition.builder(candle).properties(StatePredicate.Builder.create().exactMatch(CandleBlock.CANDLES, candles.intValue())))))));
    }

    public LootTable.Builder flowerbedDrops(Block flowerbed) {
        return LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with((LootPoolEntry.Builder)this.applyExplosionDecay(flowerbed, (LootFunctionConsumingBuilder)ItemEntry.builder(flowerbed).apply(IntStream.rangeClosed(1, 4).boxed().toList(), flowerAmount -> SetCountLootFunction.builder(ConstantLootNumberProvider.create(flowerAmount.intValue())).conditionally(BlockStatePropertyLootCondition.builder(flowerbed).properties(StatePredicate.Builder.create().exactMatch(FlowerbedBlock.FLOWER_AMOUNT, flowerAmount.intValue())))))));
    }

    public static LootTable.Builder candleCakeDrops(Block candleCake) {
        return LootTable.builder().pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f)).with(ItemEntry.builder(candleCake)));
    }

    public static LootTable.Builder dropsNothing() {
        return LootTable.builder();
    }

    public abstract void generate();

    @Override
    public void accept(RegistryWrapper.WrapperLookup registryLookup, BiConsumer<RegistryKey<LootTable>, LootTable.Builder> consumer) {
        this.generate();
        HashSet<RegistryKey<LootTable>> set = new HashSet<RegistryKey<LootTable>>();
        for (Block block : Registries.BLOCK) {
            RegistryKey<LootTable> registryKey;
            if (!block.isEnabled(this.requiredFeatures) || (registryKey = block.getLootTableKey()) == LootTables.EMPTY || !set.add(registryKey)) continue;
            LootTable.Builder builder = this.lootTables.remove(registryKey);
            if (builder == null) {
                throw new IllegalStateException(String.format(Locale.ROOT, "Missing loottable '%s' for '%s'", registryKey.getValue(), Registries.BLOCK.getId(block)));
            }
            consumer.accept(registryKey, builder);
        }
        if (!this.lootTables.isEmpty()) {
            throw new IllegalStateException("Created block loot tables for non-blocks: " + String.valueOf(this.lootTables.keySet()));
        }
    }

    public void addVinePlantDrop(Block block, Block drop) {
        LootTable.Builder builder = BlockLootTableGenerator.dropsWithSilkTouchOrShears(block, ItemEntry.builder(block).conditionally(TableBonusLootCondition.builder(Enchantments.FORTUNE, 0.33f, 0.55f, 0.77f, 1.0f)));
        this.addDrop(block, builder);
        this.addDrop(drop, builder);
    }

    public LootTable.Builder doorDrops(Block block) {
        return this.dropsWithProperty(block, DoorBlock.HALF, DoubleBlockHalf.LOWER);
    }

    public void addPottedPlantDrops(Block block) {
        this.addDrop(block, (Block flowerPot) -> this.pottedPlantDrops(((FlowerPotBlock)flowerPot).getContent()));
    }

    public void addDropWithSilkTouch(Block block, Block drop) {
        this.addDrop(block, BlockLootTableGenerator.dropsWithSilkTouch(drop));
    }

    public void addDrop(Block block, ItemConvertible drop) {
        this.addDrop(block, this.drops(drop));
    }

    public void addDropWithSilkTouch(Block block) {
        this.addDropWithSilkTouch(block, block);
    }

    public void addDrop(Block block) {
        this.addDrop(block, block);
    }

    public void addDrop(Block block, Function<Block, LootTable.Builder> lootTableFunction) {
        this.addDrop(block, lootTableFunction.apply(block));
    }

    public void addDrop(Block block, LootTable.Builder lootTable) {
        this.lootTables.put(block.getLootTableKey(), lootTable);
    }
}

