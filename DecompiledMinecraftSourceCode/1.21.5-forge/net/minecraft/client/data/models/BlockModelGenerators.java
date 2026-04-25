package net.minecraft.client.data.models;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Quadrant;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.client.color.item.GrassColorSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.block.model.VariantMutator;
import net.minecraft.client.renderer.block.model.multipart.CombinedCondition;
import net.minecraft.client.renderer.block.model.multipart.Condition;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.special.BannerSpecialRenderer;
import net.minecraft.client.renderer.special.BedSpecialRenderer;
import net.minecraft.client.renderer.special.ChestSpecialRenderer;
import net.minecraft.client.renderer.special.ConduitSpecialRenderer;
import net.minecraft.client.renderer.special.DecoratedPotSpecialRenderer;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.client.renderer.special.SkullSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.DriedGhastBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SnifferEggBlock;
import net.minecraft.world.level.block.TestBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.block.state.properties.TestBlockMode;
import net.minecraft.world.level.block.state.properties.Tilt;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockModelGenerators {
    protected final Consumer<BlockModelDefinitionGenerator> blockStateOutput;
    protected final ItemModelOutput itemModelOutput;
    protected final BiConsumer<ResourceLocation, ModelInstance> modelOutput;
    static final List<Block> NON_ORIENTABLE_TRAPDOOR = List.of(Blocks.OAK_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.IRON_TRAPDOOR);
    public static final VariantMutator NOP = p_395768_ -> p_395768_;
    public static final VariantMutator UV_LOCK = VariantMutator.UV_LOCK.withValue(true);
    public static final VariantMutator X_ROT_90 = VariantMutator.X_ROT.withValue(Quadrant.R90);
    public static final VariantMutator X_ROT_180 = VariantMutator.X_ROT.withValue(Quadrant.R180);
    public static final VariantMutator X_ROT_270 = VariantMutator.X_ROT.withValue(Quadrant.R270);
    public static final VariantMutator Y_ROT_90 = VariantMutator.Y_ROT.withValue(Quadrant.R90);
    public static final VariantMutator Y_ROT_180 = VariantMutator.Y_ROT.withValue(Quadrant.R180);
    public static final VariantMutator Y_ROT_270 = VariantMutator.Y_ROT.withValue(Quadrant.R270);
    private static final Function<ConditionBuilder, ConditionBuilder> FLOWER_BED_MODEL_1_SEGMENT_CONDITION = p_394122_ -> p_394122_;
    private static final Function<ConditionBuilder, ConditionBuilder> FLOWER_BED_MODEL_2_SEGMENT_CONDITION = p_389202_ -> p_389202_.term(BlockStateProperties.FLOWER_AMOUNT, 2, 3, 4);
    private static final Function<ConditionBuilder, ConditionBuilder> FLOWER_BED_MODEL_3_SEGMENT_CONDITION = p_389201_ -> p_389201_.term(BlockStateProperties.FLOWER_AMOUNT, 3, 4);
    private static final Function<ConditionBuilder, ConditionBuilder> FLOWER_BED_MODEL_4_SEGMENT_CONDITION = p_389228_ -> p_389228_.term(BlockStateProperties.FLOWER_AMOUNT, 4);
    private static final Function<ConditionBuilder, ConditionBuilder> LEAF_LITTER_MODEL_1_SEGMENT_CONDITION = p_389259_ -> p_389259_.term(BlockStateProperties.SEGMENT_AMOUNT, 1);
    private static final Function<ConditionBuilder, ConditionBuilder> LEAF_LITTER_MODEL_2_SEGMENT_CONDITION = p_389263_ -> p_389263_.term(BlockStateProperties.SEGMENT_AMOUNT, 2, 3);
    private static final Function<ConditionBuilder, ConditionBuilder> LEAF_LITTER_MODEL_3_SEGMENT_CONDITION = p_389252_ -> p_389252_.term(BlockStateProperties.SEGMENT_AMOUNT, 3);
    private static final Function<ConditionBuilder, ConditionBuilder> LEAF_LITTER_MODEL_4_SEGMENT_CONDITION = p_389246_ -> p_389246_.term(BlockStateProperties.SEGMENT_AMOUNT, 4);
    static final Map<Block, BlockModelGenerators.BlockStateGeneratorSupplier> FULL_BLOCK_MODEL_CUSTOM_GENERATORS = Map.of(
        Blocks.STONE, BlockModelGenerators::createMirroredCubeGenerator, Blocks.DEEPSLATE, BlockModelGenerators::createMirroredColumnGenerator, Blocks.MUD_BRICKS, BlockModelGenerators::createNorthWestMirroredCubeGenerator
    );
    private static final PropertyDispatch<VariantMutator> ROTATION_FACING = PropertyDispatch.modify(BlockStateProperties.FACING)
        .select(Direction.DOWN, X_ROT_90)
        .select(Direction.UP, X_ROT_270)
        .select(Direction.NORTH, NOP)
        .select(Direction.SOUTH, Y_ROT_180)
        .select(Direction.WEST, Y_ROT_270)
        .select(Direction.EAST, Y_ROT_90);
    private static final PropertyDispatch<VariantMutator> ROTATIONS_COLUMN_WITH_FACING = PropertyDispatch.modify(BlockStateProperties.FACING)
        .select(Direction.DOWN, X_ROT_180)
        .select(Direction.UP, NOP)
        .select(Direction.NORTH, X_ROT_90)
        .select(Direction.SOUTH, X_ROT_90.then(Y_ROT_180))
        .select(Direction.WEST, X_ROT_90.then(Y_ROT_270))
        .select(Direction.EAST, X_ROT_90.then(Y_ROT_90));
    private static final PropertyDispatch<VariantMutator> ROTATION_TORCH = PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
        .select(Direction.EAST, NOP)
        .select(Direction.SOUTH, Y_ROT_90)
        .select(Direction.WEST, Y_ROT_180)
        .select(Direction.NORTH, Y_ROT_270);
    private static final PropertyDispatch<VariantMutator> ROTATION_HORIZONTAL_FACING_ALT = PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
        .select(Direction.SOUTH, NOP)
        .select(Direction.WEST, Y_ROT_90)
        .select(Direction.NORTH, Y_ROT_180)
        .select(Direction.EAST, Y_ROT_270);
    private static final PropertyDispatch<VariantMutator> ROTATION_HORIZONTAL_FACING = PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
        .select(Direction.EAST, Y_ROT_90)
        .select(Direction.SOUTH, Y_ROT_180)
        .select(Direction.WEST, Y_ROT_270)
        .select(Direction.NORTH, NOP);
    static final Map<Block, TexturedModel> TEXTURED_MODELS = ImmutableMap.<Block, TexturedModel>builder()
        .put(Blocks.SANDSTONE, TexturedModel.TOP_BOTTOM_WITH_WALL.get(Blocks.SANDSTONE))
        .put(Blocks.RED_SANDSTONE, TexturedModel.TOP_BOTTOM_WITH_WALL.get(Blocks.RED_SANDSTONE))
        .put(Blocks.SMOOTH_SANDSTONE, TexturedModel.createAllSame(TextureMapping.getBlockTexture(Blocks.SANDSTONE, "_top")))
        .put(Blocks.SMOOTH_RED_SANDSTONE, TexturedModel.createAllSame(TextureMapping.getBlockTexture(Blocks.RED_SANDSTONE, "_top")))
        .put(
            Blocks.CUT_SANDSTONE,
            TexturedModel.COLUMN
                .get(Blocks.SANDSTONE)
                .updateTextures(p_376753_ -> p_376753_.put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.CUT_SANDSTONE)))
        )
        .put(
            Blocks.CUT_RED_SANDSTONE,
            TexturedModel.COLUMN
                .get(Blocks.RED_SANDSTONE)
                .updateTextures(p_376502_ -> p_376502_.put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.CUT_RED_SANDSTONE)))
        )
        .put(Blocks.QUARTZ_BLOCK, TexturedModel.COLUMN.get(Blocks.QUARTZ_BLOCK))
        .put(Blocks.SMOOTH_QUARTZ, TexturedModel.createAllSame(TextureMapping.getBlockTexture(Blocks.QUARTZ_BLOCK, "_bottom")))
        .put(Blocks.BLACKSTONE, TexturedModel.COLUMN_WITH_WALL.get(Blocks.BLACKSTONE))
        .put(Blocks.DEEPSLATE, TexturedModel.COLUMN_WITH_WALL.get(Blocks.DEEPSLATE))
        .put(
            Blocks.CHISELED_QUARTZ_BLOCK,
            TexturedModel.COLUMN
                .get(Blocks.CHISELED_QUARTZ_BLOCK)
                .updateTextures(p_377216_ -> p_377216_.put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.CHISELED_QUARTZ_BLOCK)))
        )
        .put(Blocks.CHISELED_SANDSTONE, TexturedModel.COLUMN.get(Blocks.CHISELED_SANDSTONE).updateTextures(p_376762_ -> {
            p_376762_.put(TextureSlot.END, TextureMapping.getBlockTexture(Blocks.SANDSTONE, "_top"));
            p_376762_.put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.CHISELED_SANDSTONE));
        }))
        .put(Blocks.CHISELED_RED_SANDSTONE, TexturedModel.COLUMN.get(Blocks.CHISELED_RED_SANDSTONE).updateTextures(p_377408_ -> {
            p_377408_.put(TextureSlot.END, TextureMapping.getBlockTexture(Blocks.RED_SANDSTONE, "_top"));
            p_377408_.put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.CHISELED_RED_SANDSTONE));
        }))
        .put(Blocks.CHISELED_TUFF_BRICKS, TexturedModel.COLUMN_WITH_WALL.get(Blocks.CHISELED_TUFF_BRICKS))
        .put(Blocks.CHISELED_TUFF, TexturedModel.COLUMN_WITH_WALL.get(Blocks.CHISELED_TUFF))
        .build();
    static final Map<BlockFamily.Variant, BiConsumer<BlockModelGenerators.BlockFamilyProvider, Block>> SHAPE_CONSUMERS = ImmutableMap.<BlockFamily.Variant, BiConsumer<BlockModelGenerators.BlockFamilyProvider, Block>>builder()
        .put(BlockFamily.Variant.BUTTON, BlockModelGenerators.BlockFamilyProvider::button)
        .put(BlockFamily.Variant.DOOR, BlockModelGenerators.BlockFamilyProvider::door)
        .put(BlockFamily.Variant.CHISELED, BlockModelGenerators.BlockFamilyProvider::fullBlockVariant)
        .put(BlockFamily.Variant.CRACKED, BlockModelGenerators.BlockFamilyProvider::fullBlockVariant)
        .put(BlockFamily.Variant.CUSTOM_FENCE, BlockModelGenerators.BlockFamilyProvider::customFence)
        .put(BlockFamily.Variant.FENCE, BlockModelGenerators.BlockFamilyProvider::fence)
        .put(BlockFamily.Variant.CUSTOM_FENCE_GATE, BlockModelGenerators.BlockFamilyProvider::customFenceGate)
        .put(BlockFamily.Variant.FENCE_GATE, BlockModelGenerators.BlockFamilyProvider::fenceGate)
        .put(BlockFamily.Variant.SIGN, BlockModelGenerators.BlockFamilyProvider::sign)
        .put(BlockFamily.Variant.SLAB, BlockModelGenerators.BlockFamilyProvider::slab)
        .put(BlockFamily.Variant.STAIRS, BlockModelGenerators.BlockFamilyProvider::stairs)
        .put(BlockFamily.Variant.PRESSURE_PLATE, BlockModelGenerators.BlockFamilyProvider::pressurePlate)
        .put(BlockFamily.Variant.TRAPDOOR, BlockModelGenerators.BlockFamilyProvider::trapdoor)
        .put(BlockFamily.Variant.WALL, BlockModelGenerators.BlockFamilyProvider::wall)
        .build();
    private static final Map<Direction, VariantMutator> MULTIFACE_GENERATOR = ImmutableMap.of(
        Direction.NORTH,
        NOP,
        Direction.EAST,
        Y_ROT_90.then(UV_LOCK),
        Direction.SOUTH,
        Y_ROT_180.then(UV_LOCK),
        Direction.WEST,
        Y_ROT_270.then(UV_LOCK),
        Direction.UP,
        X_ROT_270.then(UV_LOCK),
        Direction.DOWN,
        X_ROT_90.then(UV_LOCK)
    );
    private static final Map<BlockModelGenerators.BookSlotModelCacheKey, ResourceLocation> CHISELED_BOOKSHELF_SLOT_MODEL_CACHE = new HashMap<>();

    protected static Variant plainModel(ResourceLocation p_393672_) {
        return new Variant(p_393672_);
    }

    protected static MultiVariant variant(Variant p_396618_) {
        return new MultiVariant(WeightedList.of(p_396618_));
    }

    protected static MultiVariant variants(Variant... p_393301_) {
        return new MultiVariant(WeightedList.of(Arrays.stream(p_393301_).map(p_389196_ -> new Weighted<>(p_389196_, 1)).toList()));
    }

    protected static MultiVariant plainVariant(ResourceLocation p_391485_) {
        return variant(plainModel(p_391485_));
    }

    protected static ConditionBuilder condition() {
        return new ConditionBuilder();
    }

    protected static Condition or(ConditionBuilder... p_397725_) {
        return new CombinedCondition(CombinedCondition.Operation.OR, Stream.of(p_397725_).map(ConditionBuilder::build).toList());
    }

    protected static BlockModelDefinitionGenerator createMirroredCubeGenerator(
        Block p_377256_, Variant p_392810_, TextureMapping p_376649_, BiConsumer<ResourceLocation, ModelInstance> p_378385_
    ) {
        Variant variant = plainModel(ModelTemplates.CUBE_MIRRORED_ALL.create(p_377256_, p_376649_, p_378385_));
        return MultiVariantGenerator.dispatch(p_377256_, createRotatedVariants(p_392810_, variant));
    }

    protected static BlockModelDefinitionGenerator createNorthWestMirroredCubeGenerator(
        Block p_376344_, Variant p_395096_, TextureMapping p_375768_, BiConsumer<ResourceLocation, ModelInstance> p_378658_
    ) {
        MultiVariant multivariant = plainVariant(ModelTemplates.CUBE_NORTH_WEST_MIRRORED_ALL.create(p_376344_, p_375768_, p_378658_));
        return createSimpleBlock(p_376344_, multivariant);
    }

    protected static BlockModelDefinitionGenerator createMirroredColumnGenerator(
        Block p_376474_, Variant p_393810_, TextureMapping p_376415_, BiConsumer<ResourceLocation, ModelInstance> p_377524_
    ) {
        Variant variant = plainModel(ModelTemplates.CUBE_COLUMN_MIRRORED.create(p_376474_, p_376415_, p_377524_));
        return MultiVariantGenerator.dispatch(p_376474_, createRotatedVariants(p_393810_, variant)).with(createRotatedPillar());
    }

    public BlockModelGenerators(
        Consumer<BlockModelDefinitionGenerator> p_378137_, ItemModelOutput p_378502_, BiConsumer<ResourceLocation, ModelInstance> p_378240_
    ) {
        this.blockStateOutput = p_378137_;
        this.itemModelOutput = p_378502_;
        this.modelOutput = p_378240_;
    }

    protected void registerSimpleItemModel(Item p_375488_, ResourceLocation p_377760_) {
        this.itemModelOutput.accept(p_375488_, ItemModelUtils.plainModel(p_377760_));
    }

    protected void registerSimpleItemModel(Block p_376656_, ResourceLocation p_375635_) {
        this.itemModelOutput.accept(p_376656_.asItem(), ItemModelUtils.plainModel(p_375635_));
    }

    protected void registerSimpleTintedItemModel(Block p_375646_, ResourceLocation p_376671_, ItemTintSource p_378262_) {
        this.itemModelOutput.accept(p_375646_.asItem(), ItemModelUtils.tintedModel(p_376671_, p_378262_));
    }

    protected ResourceLocation createFlatItemModel(Item p_378261_) {
        return ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(p_378261_), TextureMapping.layer0(p_378261_), this.modelOutput);
    }

    protected ResourceLocation createFlatItemModelWithBlockTexture(Item p_376351_, Block p_377327_) {
        return ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(p_376351_), TextureMapping.layer0(p_377327_), this.modelOutput);
    }

    protected ResourceLocation createFlatItemModelWithBlockTexture(Item p_375454_, Block p_376580_, String p_376399_) {
        return ModelTemplates.FLAT_ITEM
            .create(ModelLocationUtils.getModelLocation(p_375454_), TextureMapping.layer0(TextureMapping.getBlockTexture(p_376580_, p_376399_)), this.modelOutput);
    }

    protected ResourceLocation createFlatItemModelWithBlockTextureAndOverlay(Item p_377621_, Block p_376897_, String p_375836_) {
        ResourceLocation resourcelocation = TextureMapping.getBlockTexture(p_376897_);
        ResourceLocation resourcelocation1 = TextureMapping.getBlockTexture(p_376897_, p_375836_);
        return ModelTemplates.TWO_LAYERED_ITEM
            .create(ModelLocationUtils.getModelLocation(p_377621_), TextureMapping.layered(resourcelocation, resourcelocation1), this.modelOutput);
    }

    protected void registerSimpleFlatItemModel(Item p_378703_) {
        this.registerSimpleItemModel(p_378703_, this.createFlatItemModel(p_378703_));
    }

    protected void registerSimpleFlatItemModel(Block p_378454_) {
        Item item = p_378454_.asItem();
        if (item != Items.AIR) {
            this.registerSimpleItemModel(item, this.createFlatItemModelWithBlockTexture(item, p_378454_));
        }
    }

    protected void registerSimpleFlatItemModel(Block p_376201_, String p_377421_) {
        Item item = p_376201_.asItem();
        if (item != Items.AIR) {
            this.registerSimpleItemModel(item, this.createFlatItemModelWithBlockTexture(item, p_376201_, p_377421_));
        }
    }

    protected void registerTwoLayerFlatItemModel(Block p_377903_, String p_378009_) {
        Item item = p_377903_.asItem();
        if (item != Items.AIR) {
            ResourceLocation resourcelocation = this.createFlatItemModelWithBlockTextureAndOverlay(item, p_377903_, p_378009_);
            this.registerSimpleItemModel(item, resourcelocation);
        }
    }

    protected static MultiVariant createRotatedVariants(Variant p_397110_) {
        return variants(p_397110_, p_397110_.with(Y_ROT_90), p_397110_.with(Y_ROT_180), p_397110_.with(Y_ROT_270));
    }

    protected static MultiVariant createRotatedVariants(Variant p_397271_, Variant p_393824_) {
        return variants(p_397271_, p_393824_, p_397271_.with(Y_ROT_180), p_393824_.with(Y_ROT_180));
    }

    protected static PropertyDispatch<MultiVariant> createBooleanModelDispatch(BooleanProperty p_378433_, MultiVariant p_397632_, MultiVariant p_393124_) {
        return PropertyDispatch.initial(p_378433_).select(true, p_397632_).select(false, p_393124_);
    }

    protected void createRotatedMirroredVariantBlock(Block p_375955_) {
        Variant variant = plainModel(TexturedModel.CUBE.create(p_375955_, this.modelOutput));
        Variant variant1 = plainModel(TexturedModel.CUBE_MIRRORED.create(p_375955_, this.modelOutput));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_375955_, createRotatedVariants(variant, variant1)));
    }

    protected void createRotatedVariantBlock(Block p_376360_) {
        Variant variant = plainModel(TexturedModel.CUBE.create(p_376360_, this.modelOutput));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_376360_, createRotatedVariants(variant)));
    }

    protected void createBrushableBlock(Block p_377090_) {
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_377090_)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.DUSTED)
                            .generate(
                                p_389192_ -> {
                                    String s = "_" + p_389192_;
                                    ResourceLocation resourcelocation = TextureMapping.getBlockTexture(p_377090_, s);
                                    ResourceLocation resourcelocation1 = ModelTemplates.CUBE_ALL
                                        .createWithSuffix(p_377090_, s, new TextureMapping().put(TextureSlot.ALL, resourcelocation), this.modelOutput);
                                    return plainVariant(resourcelocation1);
                                }
                            )
                    )
            );
        this.registerSimpleItemModel(p_377090_, ModelLocationUtils.getModelLocation(p_377090_, "_0"));
    }

    protected static BlockModelDefinitionGenerator createButton(Block p_396089_, MultiVariant p_395909_, MultiVariant p_394744_) {
        return MultiVariantGenerator.dispatch(p_396089_)
            .with(PropertyDispatch.initial(BlockStateProperties.POWERED).select(false, p_395909_).select(true, p_394744_))
            .with(
                PropertyDispatch.modify(BlockStateProperties.ATTACH_FACE, BlockStateProperties.HORIZONTAL_FACING)
                    .select(AttachFace.FLOOR, Direction.EAST, Y_ROT_90)
                    .select(AttachFace.FLOOR, Direction.WEST, Y_ROT_270)
                    .select(AttachFace.FLOOR, Direction.SOUTH, Y_ROT_180)
                    .select(AttachFace.FLOOR, Direction.NORTH, NOP)
                    .select(AttachFace.WALL, Direction.EAST, Y_ROT_90.then(X_ROT_90).then(UV_LOCK))
                    .select(AttachFace.WALL, Direction.WEST, Y_ROT_270.then(X_ROT_90).then(UV_LOCK))
                    .select(AttachFace.WALL, Direction.SOUTH, Y_ROT_180.then(X_ROT_90).then(UV_LOCK))
                    .select(AttachFace.WALL, Direction.NORTH, X_ROT_90.then(UV_LOCK))
                    .select(AttachFace.CEILING, Direction.EAST, Y_ROT_270.then(X_ROT_180))
                    .select(AttachFace.CEILING, Direction.WEST, Y_ROT_90.then(X_ROT_180))
                    .select(AttachFace.CEILING, Direction.SOUTH, X_ROT_180)
                    .select(AttachFace.CEILING, Direction.NORTH, Y_ROT_180.then(X_ROT_180))
            );
    }

    protected static BlockModelDefinitionGenerator createDoor(
        Block p_378473_,
        MultiVariant p_396274_,
        MultiVariant p_394223_,
        MultiVariant p_394131_,
        MultiVariant p_392301_,
        MultiVariant p_395830_,
        MultiVariant p_395019_,
        MultiVariant p_397543_,
        MultiVariant p_394737_
    ) {
        return MultiVariantGenerator.dispatch(p_378473_)
            .with(
                PropertyDispatch.initial(
                        BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.DOUBLE_BLOCK_HALF, BlockStateProperties.DOOR_HINGE, BlockStateProperties.OPEN
                    )
                    .select(Direction.EAST, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, false, p_396274_)
                    .select(Direction.SOUTH, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, false, p_396274_.with(Y_ROT_90))
                    .select(Direction.WEST, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, false, p_396274_.with(Y_ROT_180))
                    .select(Direction.NORTH, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, false, p_396274_.with(Y_ROT_270))
                    .select(Direction.EAST, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, false, p_394131_)
                    .select(Direction.SOUTH, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, false, p_394131_.with(Y_ROT_90))
                    .select(Direction.WEST, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, false, p_394131_.with(Y_ROT_180))
                    .select(Direction.NORTH, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, false, p_394131_.with(Y_ROT_270))
                    .select(Direction.EAST, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, true, p_394223_.with(Y_ROT_90))
                    .select(Direction.SOUTH, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, true, p_394223_.with(Y_ROT_180))
                    .select(Direction.WEST, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, true, p_394223_.with(Y_ROT_270))
                    .select(Direction.NORTH, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, true, p_394223_)
                    .select(Direction.EAST, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, true, p_392301_.with(Y_ROT_270))
                    .select(Direction.SOUTH, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, true, p_392301_)
                    .select(Direction.WEST, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, true, p_392301_.with(Y_ROT_90))
                    .select(Direction.NORTH, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, true, p_392301_.with(Y_ROT_180))
                    .select(Direction.EAST, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, false, p_395830_)
                    .select(Direction.SOUTH, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, false, p_395830_.with(Y_ROT_90))
                    .select(Direction.WEST, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, false, p_395830_.with(Y_ROT_180))
                    .select(Direction.NORTH, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, false, p_395830_.with(Y_ROT_270))
                    .select(Direction.EAST, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, false, p_397543_)
                    .select(Direction.SOUTH, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, false, p_397543_.with(Y_ROT_90))
                    .select(Direction.WEST, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, false, p_397543_.with(Y_ROT_180))
                    .select(Direction.NORTH, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, false, p_397543_.with(Y_ROT_270))
                    .select(Direction.EAST, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, true, p_395019_.with(Y_ROT_90))
                    .select(Direction.SOUTH, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, true, p_395019_.with(Y_ROT_180))
                    .select(Direction.WEST, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, true, p_395019_.with(Y_ROT_270))
                    .select(Direction.NORTH, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, true, p_395019_)
                    .select(Direction.EAST, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, true, p_394737_.with(Y_ROT_270))
                    .select(Direction.SOUTH, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, true, p_394737_)
                    .select(Direction.WEST, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, true, p_394737_.with(Y_ROT_90))
                    .select(Direction.NORTH, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, true, p_394737_.with(Y_ROT_180))
            );
    }

    protected static BlockModelDefinitionGenerator createCustomFence(
        Block p_376702_, MultiVariant p_395325_, MultiVariant p_396907_, MultiVariant p_396248_, MultiVariant p_394310_, MultiVariant p_393780_
    ) {
        return MultiPartGenerator.multiPart(p_376702_)
            .with(p_395325_)
            .with(condition().term(BlockStateProperties.NORTH, true), p_396907_)
            .with(condition().term(BlockStateProperties.EAST, true), p_396248_)
            .with(condition().term(BlockStateProperties.SOUTH, true), p_394310_)
            .with(condition().term(BlockStateProperties.WEST, true), p_393780_);
    }

    protected static BlockModelDefinitionGenerator createFence(Block p_378690_, MultiVariant p_393823_, MultiVariant p_397610_) {
        return MultiPartGenerator.multiPart(p_378690_)
            .with(p_393823_)
            .with(condition().term(BlockStateProperties.NORTH, true), p_397610_.with(UV_LOCK))
            .with(condition().term(BlockStateProperties.EAST, true), p_397610_.with(Y_ROT_90).with(UV_LOCK))
            .with(condition().term(BlockStateProperties.SOUTH, true), p_397610_.with(Y_ROT_180).with(UV_LOCK))
            .with(condition().term(BlockStateProperties.WEST, true), p_397610_.with(Y_ROT_270).with(UV_LOCK));
    }

    protected static BlockModelDefinitionGenerator createWall(Block p_376287_, MultiVariant p_395745_, MultiVariant p_394575_, MultiVariant p_398002_) {
        return MultiPartGenerator.multiPart(p_376287_)
            .with(condition().term(BlockStateProperties.UP, true), p_395745_)
            .with(condition().term(BlockStateProperties.NORTH_WALL, WallSide.LOW), p_394575_.with(UV_LOCK))
            .with(condition().term(BlockStateProperties.EAST_WALL, WallSide.LOW), p_394575_.with(Y_ROT_90).with(UV_LOCK))
            .with(condition().term(BlockStateProperties.SOUTH_WALL, WallSide.LOW), p_394575_.with(Y_ROT_180).with(UV_LOCK))
            .with(condition().term(BlockStateProperties.WEST_WALL, WallSide.LOW), p_394575_.with(Y_ROT_270).with(UV_LOCK))
            .with(condition().term(BlockStateProperties.NORTH_WALL, WallSide.TALL), p_398002_.with(UV_LOCK))
            .with(condition().term(BlockStateProperties.EAST_WALL, WallSide.TALL), p_398002_.with(Y_ROT_90).with(UV_LOCK))
            .with(condition().term(BlockStateProperties.SOUTH_WALL, WallSide.TALL), p_398002_.with(Y_ROT_180).with(UV_LOCK))
            .with(condition().term(BlockStateProperties.WEST_WALL, WallSide.TALL), p_398002_.with(Y_ROT_270).with(UV_LOCK));
    }

    protected static BlockModelDefinitionGenerator createFenceGate(
        Block p_375545_, MultiVariant p_395774_, MultiVariant p_397014_, MultiVariant p_392017_, MultiVariant p_395785_, boolean p_378283_
    ) {
        return MultiVariantGenerator.dispatch(p_375545_)
            .with(
                PropertyDispatch.initial(BlockStateProperties.IN_WALL, BlockStateProperties.OPEN)
                    .select(false, false, p_397014_)
                    .select(true, false, p_395785_)
                    .select(false, true, p_395774_)
                    .select(true, true, p_392017_)
            )
            .with(p_378283_ ? UV_LOCK : NOP)
            .with(ROTATION_HORIZONTAL_FACING_ALT);
    }

    protected static BlockModelDefinitionGenerator createStairs(Block p_377012_, MultiVariant p_397742_, MultiVariant p_397607_, MultiVariant p_394566_) {
        return MultiVariantGenerator.dispatch(p_377012_)
            .with(
                PropertyDispatch.initial(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.HALF, BlockStateProperties.STAIRS_SHAPE)
                    .select(Direction.EAST, Half.BOTTOM, StairsShape.STRAIGHT, p_397607_)
                    .select(Direction.WEST, Half.BOTTOM, StairsShape.STRAIGHT, p_397607_.with(Y_ROT_180).with(UV_LOCK))
                    .select(Direction.SOUTH, Half.BOTTOM, StairsShape.STRAIGHT, p_397607_.with(Y_ROT_90).with(UV_LOCK))
                    .select(Direction.NORTH, Half.BOTTOM, StairsShape.STRAIGHT, p_397607_.with(Y_ROT_270).with(UV_LOCK))
                    .select(Direction.EAST, Half.BOTTOM, StairsShape.OUTER_RIGHT, p_394566_)
                    .select(Direction.WEST, Half.BOTTOM, StairsShape.OUTER_RIGHT, p_394566_.with(Y_ROT_180).with(UV_LOCK))
                    .select(Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_RIGHT, p_394566_.with(Y_ROT_90).with(UV_LOCK))
                    .select(Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_RIGHT, p_394566_.with(Y_ROT_270).with(UV_LOCK))
                    .select(Direction.EAST, Half.BOTTOM, StairsShape.OUTER_LEFT, p_394566_.with(Y_ROT_270).with(UV_LOCK))
                    .select(Direction.WEST, Half.BOTTOM, StairsShape.OUTER_LEFT, p_394566_.with(Y_ROT_90).with(UV_LOCK))
                    .select(Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_LEFT, p_394566_)
                    .select(Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_LEFT, p_394566_.with(Y_ROT_180).with(UV_LOCK))
                    .select(Direction.EAST, Half.BOTTOM, StairsShape.INNER_RIGHT, p_397742_)
                    .select(Direction.WEST, Half.BOTTOM, StairsShape.INNER_RIGHT, p_397742_.with(Y_ROT_180).with(UV_LOCK))
                    .select(Direction.SOUTH, Half.BOTTOM, StairsShape.INNER_RIGHT, p_397742_.with(Y_ROT_90).with(UV_LOCK))
                    .select(Direction.NORTH, Half.BOTTOM, StairsShape.INNER_RIGHT, p_397742_.with(Y_ROT_270).with(UV_LOCK))
                    .select(Direction.EAST, Half.BOTTOM, StairsShape.INNER_LEFT, p_397742_.with(Y_ROT_270).with(UV_LOCK))
                    .select(Direction.WEST, Half.BOTTOM, StairsShape.INNER_LEFT, p_397742_.with(Y_ROT_90).with(UV_LOCK))
                    .select(Direction.SOUTH, Half.BOTTOM, StairsShape.INNER_LEFT, p_397742_)
                    .select(Direction.NORTH, Half.BOTTOM, StairsShape.INNER_LEFT, p_397742_.with(Y_ROT_180).with(UV_LOCK))
                    .select(Direction.EAST, Half.TOP, StairsShape.STRAIGHT, p_397607_.with(X_ROT_180).with(UV_LOCK))
                    .select(Direction.WEST, Half.TOP, StairsShape.STRAIGHT, p_397607_.with(X_ROT_180).with(Y_ROT_180).with(UV_LOCK))
                    .select(Direction.SOUTH, Half.TOP, StairsShape.STRAIGHT, p_397607_.with(X_ROT_180).with(Y_ROT_90).with(UV_LOCK))
                    .select(Direction.NORTH, Half.TOP, StairsShape.STRAIGHT, p_397607_.with(X_ROT_180).with(Y_ROT_270).with(UV_LOCK))
                    .select(Direction.EAST, Half.TOP, StairsShape.OUTER_RIGHT, p_394566_.with(X_ROT_180).with(Y_ROT_90).with(UV_LOCK))
                    .select(Direction.WEST, Half.TOP, StairsShape.OUTER_RIGHT, p_394566_.with(X_ROT_180).with(Y_ROT_270).with(UV_LOCK))
                    .select(Direction.SOUTH, Half.TOP, StairsShape.OUTER_RIGHT, p_394566_.with(X_ROT_180).with(Y_ROT_180).with(UV_LOCK))
                    .select(Direction.NORTH, Half.TOP, StairsShape.OUTER_RIGHT, p_394566_.with(X_ROT_180).with(UV_LOCK))
                    .select(Direction.EAST, Half.TOP, StairsShape.OUTER_LEFT, p_394566_.with(X_ROT_180).with(UV_LOCK))
                    .select(Direction.WEST, Half.TOP, StairsShape.OUTER_LEFT, p_394566_.with(X_ROT_180).with(Y_ROT_180).with(UV_LOCK))
                    .select(Direction.SOUTH, Half.TOP, StairsShape.OUTER_LEFT, p_394566_.with(X_ROT_180).with(Y_ROT_90).with(UV_LOCK))
                    .select(Direction.NORTH, Half.TOP, StairsShape.OUTER_LEFT, p_394566_.with(X_ROT_180).with(Y_ROT_270).with(UV_LOCK))
                    .select(Direction.EAST, Half.TOP, StairsShape.INNER_RIGHT, p_397742_.with(X_ROT_180).with(Y_ROT_90).with(UV_LOCK))
                    .select(Direction.WEST, Half.TOP, StairsShape.INNER_RIGHT, p_397742_.with(X_ROT_180).with(Y_ROT_270).with(UV_LOCK))
                    .select(Direction.SOUTH, Half.TOP, StairsShape.INNER_RIGHT, p_397742_.with(X_ROT_180).with(Y_ROT_180).with(UV_LOCK))
                    .select(Direction.NORTH, Half.TOP, StairsShape.INNER_RIGHT, p_397742_.with(X_ROT_180).with(UV_LOCK))
                    .select(Direction.EAST, Half.TOP, StairsShape.INNER_LEFT, p_397742_.with(X_ROT_180).with(UV_LOCK))
                    .select(Direction.WEST, Half.TOP, StairsShape.INNER_LEFT, p_397742_.with(X_ROT_180).with(Y_ROT_180).with(UV_LOCK))
                    .select(Direction.SOUTH, Half.TOP, StairsShape.INNER_LEFT, p_397742_.with(X_ROT_180).with(Y_ROT_90).with(UV_LOCK))
                    .select(Direction.NORTH, Half.TOP, StairsShape.INNER_LEFT, p_397742_.with(X_ROT_180).with(Y_ROT_270).with(UV_LOCK))
            );
    }

    protected static BlockModelDefinitionGenerator createOrientableTrapdoor(Block p_378448_, MultiVariant p_393714_, MultiVariant p_395963_, MultiVariant p_392454_) {
        return MultiVariantGenerator.dispatch(p_378448_)
            .with(
                PropertyDispatch.initial(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.HALF, BlockStateProperties.OPEN)
                    .select(Direction.NORTH, Half.BOTTOM, false, p_395963_)
                    .select(Direction.SOUTH, Half.BOTTOM, false, p_395963_.with(Y_ROT_180))
                    .select(Direction.EAST, Half.BOTTOM, false, p_395963_.with(Y_ROT_90))
                    .select(Direction.WEST, Half.BOTTOM, false, p_395963_.with(Y_ROT_270))
                    .select(Direction.NORTH, Half.TOP, false, p_393714_)
                    .select(Direction.SOUTH, Half.TOP, false, p_393714_.with(Y_ROT_180))
                    .select(Direction.EAST, Half.TOP, false, p_393714_.with(Y_ROT_90))
                    .select(Direction.WEST, Half.TOP, false, p_393714_.with(Y_ROT_270))
                    .select(Direction.NORTH, Half.BOTTOM, true, p_392454_)
                    .select(Direction.SOUTH, Half.BOTTOM, true, p_392454_.with(Y_ROT_180))
                    .select(Direction.EAST, Half.BOTTOM, true, p_392454_.with(Y_ROT_90))
                    .select(Direction.WEST, Half.BOTTOM, true, p_392454_.with(Y_ROT_270))
                    .select(Direction.NORTH, Half.TOP, true, p_392454_.with(X_ROT_180).with(Y_ROT_180))
                    .select(Direction.SOUTH, Half.TOP, true, p_392454_.with(X_ROT_180))
                    .select(Direction.EAST, Half.TOP, true, p_392454_.with(X_ROT_180).with(Y_ROT_270))
                    .select(Direction.WEST, Half.TOP, true, p_392454_.with(X_ROT_180).with(Y_ROT_90))
            );
    }

    protected static BlockModelDefinitionGenerator createTrapdoor(Block p_376522_, MultiVariant p_392028_, MultiVariant p_394140_, MultiVariant p_394870_) {
        return MultiVariantGenerator.dispatch(p_376522_)
            .with(
                PropertyDispatch.initial(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.HALF, BlockStateProperties.OPEN)
                    .select(Direction.NORTH, Half.BOTTOM, false, p_394140_)
                    .select(Direction.SOUTH, Half.BOTTOM, false, p_394140_)
                    .select(Direction.EAST, Half.BOTTOM, false, p_394140_)
                    .select(Direction.WEST, Half.BOTTOM, false, p_394140_)
                    .select(Direction.NORTH, Half.TOP, false, p_392028_)
                    .select(Direction.SOUTH, Half.TOP, false, p_392028_)
                    .select(Direction.EAST, Half.TOP, false, p_392028_)
                    .select(Direction.WEST, Half.TOP, false, p_392028_)
                    .select(Direction.NORTH, Half.BOTTOM, true, p_394870_)
                    .select(Direction.SOUTH, Half.BOTTOM, true, p_394870_.with(Y_ROT_180))
                    .select(Direction.EAST, Half.BOTTOM, true, p_394870_.with(Y_ROT_90))
                    .select(Direction.WEST, Half.BOTTOM, true, p_394870_.with(Y_ROT_270))
                    .select(Direction.NORTH, Half.TOP, true, p_394870_)
                    .select(Direction.SOUTH, Half.TOP, true, p_394870_.with(Y_ROT_180))
                    .select(Direction.EAST, Half.TOP, true, p_394870_.with(Y_ROT_90))
                    .select(Direction.WEST, Half.TOP, true, p_394870_.with(Y_ROT_270))
            );
    }

    protected static MultiVariantGenerator createSimpleBlock(Block p_377037_, MultiVariant p_392859_) {
        return MultiVariantGenerator.dispatch(p_377037_, p_392859_);
    }

    protected static PropertyDispatch<VariantMutator> createRotatedPillar() {
        return PropertyDispatch.modify(BlockStateProperties.AXIS)
            .select(Direction.Axis.Y, NOP)
            .select(Direction.Axis.Z, X_ROT_90)
            .select(Direction.Axis.X, X_ROT_90.then(Y_ROT_90));
    }

    protected static BlockModelDefinitionGenerator createPillarBlockUVLocked(Block p_375885_, TextureMapping p_376939_, BiConsumer<ResourceLocation, ModelInstance> p_378526_) {
        MultiVariant multivariant = plainVariant(ModelTemplates.CUBE_COLUMN_UV_LOCKED_X.create(p_375885_, p_376939_, p_378526_));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.CUBE_COLUMN_UV_LOCKED_Y.create(p_375885_, p_376939_, p_378526_));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.CUBE_COLUMN_UV_LOCKED_Z.create(p_375885_, p_376939_, p_378526_));
        return MultiVariantGenerator.dispatch(p_375885_)
            .with(
                PropertyDispatch.initial(BlockStateProperties.AXIS)
                    .select(Direction.Axis.X, multivariant)
                    .select(Direction.Axis.Y, multivariant1)
                    .select(Direction.Axis.Z, multivariant2)
            );
    }

    protected static BlockModelDefinitionGenerator createAxisAlignedPillarBlock(Block p_375933_, MultiVariant p_395777_) {
        return MultiVariantGenerator.dispatch(p_375933_, p_395777_).with(createRotatedPillar());
    }

    protected void createAxisAlignedPillarBlockCustomModel(Block p_375527_, MultiVariant p_393501_) {
        this.blockStateOutput.accept(createAxisAlignedPillarBlock(p_375527_, p_393501_));
    }

    public void createAxisAlignedPillarBlock(Block p_376383_, TexturedModel.Provider p_376075_) {
        MultiVariant multivariant = plainVariant(p_376075_.create(p_376383_, this.modelOutput));
        this.blockStateOutput.accept(createAxisAlignedPillarBlock(p_376383_, multivariant));
    }

    protected void createHorizontallyRotatedBlock(Block p_376196_, TexturedModel.Provider p_378733_) {
        MultiVariant multivariant = plainVariant(p_378733_.create(p_376196_, this.modelOutput));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_376196_, multivariant).with(ROTATION_HORIZONTAL_FACING));
    }

    protected static BlockModelDefinitionGenerator createRotatedPillarWithHorizontalVariant(Block p_376711_, MultiVariant p_394595_, MultiVariant p_393773_) {
        return MultiVariantGenerator.dispatch(p_376711_)
            .with(
                PropertyDispatch.initial(BlockStateProperties.AXIS)
                    .select(Direction.Axis.Y, p_394595_)
                    .select(Direction.Axis.Z, p_393773_.with(X_ROT_90))
                    .select(Direction.Axis.X, p_393773_.with(X_ROT_90).with(Y_ROT_90))
            );
    }

    protected void createRotatedPillarWithHorizontalVariant(Block p_377355_, TexturedModel.Provider p_375936_, TexturedModel.Provider p_375397_) {
        MultiVariant multivariant = plainVariant(p_375936_.create(p_377355_, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(p_375397_.create(p_377355_, this.modelOutput));
        this.blockStateOutput.accept(createRotatedPillarWithHorizontalVariant(p_377355_, multivariant, multivariant1));
    }

    protected void createCreakingHeart(Block p_376905_) {
        MultiVariant multivariant = plainVariant(TexturedModel.COLUMN_ALT.create(p_376905_, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(TexturedModel.COLUMN_HORIZONTAL_ALT.create(p_376905_, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(this.createCreakingHeartModel(TexturedModel.COLUMN_ALT, p_376905_, "_awake"));
        MultiVariant multivariant3 = plainVariant(this.createCreakingHeartModel(TexturedModel.COLUMN_HORIZONTAL_ALT, p_376905_, "_awake"));
        MultiVariant multivariant4 = plainVariant(this.createCreakingHeartModel(TexturedModel.COLUMN_ALT, p_376905_, "_dormant"));
        MultiVariant multivariant5 = plainVariant(this.createCreakingHeartModel(TexturedModel.COLUMN_HORIZONTAL_ALT, p_376905_, "_dormant"));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_376905_)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.AXIS, CreakingHeartBlock.STATE)
                            .select(Direction.Axis.Y, CreakingHeartState.UPROOTED, multivariant)
                            .select(Direction.Axis.Z, CreakingHeartState.UPROOTED, multivariant1.with(X_ROT_90))
                            .select(Direction.Axis.X, CreakingHeartState.UPROOTED, multivariant1.with(X_ROT_90).with(Y_ROT_90))
                            .select(Direction.Axis.Y, CreakingHeartState.DORMANT, multivariant4)
                            .select(Direction.Axis.Z, CreakingHeartState.DORMANT, multivariant5.with(X_ROT_90))
                            .select(Direction.Axis.X, CreakingHeartState.DORMANT, multivariant5.with(X_ROT_90).with(Y_ROT_90))
                            .select(Direction.Axis.Y, CreakingHeartState.AWAKE, multivariant2)
                            .select(Direction.Axis.Z, CreakingHeartState.AWAKE, multivariant3.with(X_ROT_90))
                            .select(Direction.Axis.X, CreakingHeartState.AWAKE, multivariant3.with(X_ROT_90).with(Y_ROT_90))
                    )
            );
    }

    protected ResourceLocation createCreakingHeartModel(TexturedModel.Provider p_394714_, Block p_397280_, String p_397717_) {
        return p_394714_.updateTexture(
                p_389175_ -> p_389175_.put(TextureSlot.SIDE, TextureMapping.getBlockTexture(p_397280_, p_397717_))
                    .put(TextureSlot.END, TextureMapping.getBlockTexture(p_397280_, "_top" + p_397717_))
            )
            .createWithSuffix(p_397280_, p_397717_, this.modelOutput);
    }

    protected ResourceLocation createSuffixedVariant(Block p_375634_, String p_376378_, ModelTemplate p_376381_, Function<ResourceLocation, TextureMapping> p_378653_) {
        return p_376381_.createWithSuffix(p_375634_, p_376378_, p_378653_.apply(TextureMapping.getBlockTexture(p_375634_, p_376378_)), this.modelOutput);
    }

    protected static BlockModelDefinitionGenerator createPressurePlate(Block p_378199_, MultiVariant p_394849_, MultiVariant p_391966_) {
        return MultiVariantGenerator.dispatch(p_378199_).with(createBooleanModelDispatch(BlockStateProperties.POWERED, p_391966_, p_394849_));
    }

    protected static BlockModelDefinitionGenerator createSlab(Block p_377807_, MultiVariant p_392597_, MultiVariant p_392947_, MultiVariant p_394971_) {
        return MultiVariantGenerator.dispatch(p_377807_)
            .with(
                PropertyDispatch.initial(BlockStateProperties.SLAB_TYPE)
                    .select(SlabType.BOTTOM, p_392597_)
                    .select(SlabType.TOP, p_392947_)
                    .select(SlabType.DOUBLE, p_394971_)
            );
    }

    public void createTrivialCube(Block p_376957_) {
        this.createTrivialBlock(p_376957_, TexturedModel.CUBE);
    }

    public void createTrivialBlock(Block p_375823_, TexturedModel.Provider p_376542_) {
        this.blockStateOutput.accept(createSimpleBlock(p_375823_, plainVariant(p_376542_.create(p_375823_, this.modelOutput))));
    }

    public void createTintedLeaves(Block p_375590_, TexturedModel.Provider p_376506_, int p_375511_) {
        ResourceLocation resourcelocation = p_376506_.create(p_375590_, this.modelOutput);
        this.blockStateOutput.accept(createSimpleBlock(p_375590_, plainVariant(resourcelocation)));
        this.registerSimpleTintedItemModel(p_375590_, resourcelocation, ItemModelUtils.constantTint(p_375511_));
    }

    protected void createVine() {
        this.createMultifaceBlockStates(Blocks.VINE);
        ResourceLocation resourcelocation = this.createFlatItemModelWithBlockTexture(Items.VINE, Blocks.VINE);
        this.registerSimpleTintedItemModel(Blocks.VINE, resourcelocation, ItemModelUtils.constantTint(-12012264));
    }

    protected void createItemWithGrassTint(Block p_377529_) {
        ResourceLocation resourcelocation = this.createFlatItemModelWithBlockTexture(p_377529_.asItem(), p_377529_);
        this.registerSimpleTintedItemModel(p_377529_, resourcelocation, new GrassColorSource());
    }

    protected BlockModelGenerators.BlockFamilyProvider family(Block p_378128_) {
        TexturedModel texturedmodel = TEXTURED_MODELS.getOrDefault(p_378128_, TexturedModel.CUBE.get(p_378128_));
        return new BlockModelGenerators.BlockFamilyProvider(texturedmodel.getMapping()).fullBlock(p_378128_, texturedmodel.getTemplate());
    }

    public void createHangingSign(Block p_376300_, Block p_376812_, Block p_376678_) {
        MultiVariant multivariant = this.createParticleOnlyBlockModel(p_376812_, p_376300_);
        this.blockStateOutput.accept(createSimpleBlock(p_376812_, multivariant));
        this.blockStateOutput.accept(createSimpleBlock(p_376678_, multivariant));
        this.registerSimpleFlatItemModel(p_376812_.asItem());
    }

    protected void createDoor(Block p_377944_) {
        TextureMapping texturemapping = TextureMapping.door(p_377944_);
        MultiVariant multivariant = plainVariant(ModelTemplates.DOOR_BOTTOM_LEFT.create(p_377944_, texturemapping, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.DOOR_BOTTOM_LEFT_OPEN.create(p_377944_, texturemapping, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.DOOR_BOTTOM_RIGHT.create(p_377944_, texturemapping, this.modelOutput));
        MultiVariant multivariant3 = plainVariant(ModelTemplates.DOOR_BOTTOM_RIGHT_OPEN.create(p_377944_, texturemapping, this.modelOutput));
        MultiVariant multivariant4 = plainVariant(ModelTemplates.DOOR_TOP_LEFT.create(p_377944_, texturemapping, this.modelOutput));
        MultiVariant multivariant5 = plainVariant(ModelTemplates.DOOR_TOP_LEFT_OPEN.create(p_377944_, texturemapping, this.modelOutput));
        MultiVariant multivariant6 = plainVariant(ModelTemplates.DOOR_TOP_RIGHT.create(p_377944_, texturemapping, this.modelOutput));
        MultiVariant multivariant7 = plainVariant(ModelTemplates.DOOR_TOP_RIGHT_OPEN.create(p_377944_, texturemapping, this.modelOutput));
        this.registerSimpleFlatItemModel(p_377944_.asItem());
        this.blockStateOutput
            .accept(createDoor(p_377944_, multivariant, multivariant1, multivariant2, multivariant3, multivariant4, multivariant5, multivariant6, multivariant7));
    }

    protected void copyDoorModel(Block p_377434_, Block p_376354_) {
        MultiVariant multivariant = plainVariant(ModelTemplates.DOOR_BOTTOM_LEFT.getDefaultModelLocation(p_377434_));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.DOOR_BOTTOM_LEFT_OPEN.getDefaultModelLocation(p_377434_));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.DOOR_BOTTOM_RIGHT.getDefaultModelLocation(p_377434_));
        MultiVariant multivariant3 = plainVariant(ModelTemplates.DOOR_BOTTOM_RIGHT_OPEN.getDefaultModelLocation(p_377434_));
        MultiVariant multivariant4 = plainVariant(ModelTemplates.DOOR_TOP_LEFT.getDefaultModelLocation(p_377434_));
        MultiVariant multivariant5 = plainVariant(ModelTemplates.DOOR_TOP_LEFT_OPEN.getDefaultModelLocation(p_377434_));
        MultiVariant multivariant6 = plainVariant(ModelTemplates.DOOR_TOP_RIGHT.getDefaultModelLocation(p_377434_));
        MultiVariant multivariant7 = plainVariant(ModelTemplates.DOOR_TOP_RIGHT_OPEN.getDefaultModelLocation(p_377434_));
        this.itemModelOutput.copy(p_377434_.asItem(), p_376354_.asItem());
        this.blockStateOutput
            .accept(createDoor(p_376354_, multivariant, multivariant1, multivariant2, multivariant3, multivariant4, multivariant5, multivariant6, multivariant7));
    }

    protected void createOrientableTrapdoor(Block p_378524_) {
        TextureMapping texturemapping = TextureMapping.defaultTexture(p_378524_);
        MultiVariant multivariant = plainVariant(ModelTemplates.ORIENTABLE_TRAPDOOR_TOP.create(p_378524_, texturemapping, this.modelOutput));
        ResourceLocation resourcelocation = ModelTemplates.ORIENTABLE_TRAPDOOR_BOTTOM.create(p_378524_, texturemapping, this.modelOutput);
        MultiVariant multivariant1 = plainVariant(ModelTemplates.ORIENTABLE_TRAPDOOR_OPEN.create(p_378524_, texturemapping, this.modelOutput));
        this.blockStateOutput.accept(createOrientableTrapdoor(p_378524_, multivariant, plainVariant(resourcelocation), multivariant1));
        this.registerSimpleItemModel(p_378524_, resourcelocation);
    }

    protected void createTrapdoor(Block p_376752_) {
        TextureMapping texturemapping = TextureMapping.defaultTexture(p_376752_);
        MultiVariant multivariant = plainVariant(ModelTemplates.TRAPDOOR_TOP.create(p_376752_, texturemapping, this.modelOutput));
        ResourceLocation resourcelocation = ModelTemplates.TRAPDOOR_BOTTOM.create(p_376752_, texturemapping, this.modelOutput);
        MultiVariant multivariant1 = plainVariant(ModelTemplates.TRAPDOOR_OPEN.create(p_376752_, texturemapping, this.modelOutput));
        this.blockStateOutput.accept(createTrapdoor(p_376752_, multivariant, plainVariant(resourcelocation), multivariant1));
        this.registerSimpleItemModel(p_376752_, resourcelocation);
    }

    protected void copyTrapdoorModel(Block p_376748_, Block p_376418_) {
        MultiVariant multivariant = plainVariant(ModelTemplates.TRAPDOOR_TOP.getDefaultModelLocation(p_376748_));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.TRAPDOOR_BOTTOM.getDefaultModelLocation(p_376748_));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.TRAPDOOR_OPEN.getDefaultModelLocation(p_376748_));
        this.itemModelOutput.copy(p_376748_.asItem(), p_376418_.asItem());
        this.blockStateOutput.accept(createTrapdoor(p_376418_, multivariant, multivariant1, multivariant2));
    }

    protected void createBigDripLeafBlock() {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.BIG_DRIPLEAF));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.BIG_DRIPLEAF, "_partial_tilt"));
        MultiVariant multivariant2 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.BIG_DRIPLEAF, "_full_tilt"));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.BIG_DRIPLEAF)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.TILT)
                            .select(Tilt.NONE, multivariant)
                            .select(Tilt.UNSTABLE, multivariant)
                            .select(Tilt.PARTIAL, multivariant1)
                            .select(Tilt.FULL, multivariant2)
                    )
                    .with(ROTATION_HORIZONTAL_FACING)
            );
    }

    protected BlockModelGenerators.WoodProvider woodProvider(Block p_376901_) {
        return new BlockModelGenerators.WoodProvider(TextureMapping.logColumn(p_376901_));
    }

    protected void createNonTemplateModelBlock(Block p_377331_) {
        this.createNonTemplateModelBlock(p_377331_, p_377331_);
    }

    protected void createNonTemplateModelBlock(Block p_376889_, Block p_375872_) {
        this.blockStateOutput.accept(createSimpleBlock(p_376889_, plainVariant(ModelLocationUtils.getModelLocation(p_375872_))));
    }

    protected void createCrossBlockWithDefaultItem(Block p_377523_, BlockModelGenerators.PlantType p_376483_) {
        this.registerSimpleItemModel(p_377523_.asItem(), p_376483_.createItemModel(this, p_377523_));
        this.createCrossBlock(p_377523_, p_376483_);
    }

    protected void createCrossBlockWithDefaultItem(Block p_376375_, BlockModelGenerators.PlantType p_375442_, TextureMapping p_375740_) {
        this.registerSimpleFlatItemModel(p_376375_);
        this.createCrossBlock(p_376375_, p_375442_, p_375740_);
    }

    protected void createCrossBlock(Block p_378311_, BlockModelGenerators.PlantType p_377491_) {
        TextureMapping texturemapping = p_377491_.getTextureMapping(p_378311_);
        this.createCrossBlock(p_378311_, p_377491_, texturemapping);
    }

    protected void createCrossBlock(Block p_378299_, BlockModelGenerators.PlantType p_378744_, TextureMapping p_377447_) {
        MultiVariant multivariant = plainVariant(p_378744_.getCross().create(p_378299_, p_377447_, this.modelOutput));
        this.blockStateOutput.accept(createSimpleBlock(p_378299_, multivariant));
    }

    protected void createCrossBlock(Block p_376503_, BlockModelGenerators.PlantType p_377030_, Property<Integer> p_377779_, int... p_376808_) {
        if (p_377779_.getPossibleValues().size() != p_376808_.length) {
            throw new IllegalArgumentException("missing values for property: " + p_377779_);
        } else {
            this.registerSimpleFlatItemModel(p_376503_.asItem());
            this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_376503_).with(PropertyDispatch.initial(p_377779_).generate(p_389185_ -> {
                String s = "_stage" + p_376808_[p_389185_];
                TextureMapping texturemapping = TextureMapping.cross(TextureMapping.getBlockTexture(p_376503_, s));
                return plainVariant(p_377030_.getCross().createWithSuffix(p_376503_, s, texturemapping, this.modelOutput));
            })));
        }
    }

    protected void createPlantWithDefaultItem(Block p_375477_, Block p_376166_, BlockModelGenerators.PlantType p_377517_) {
        this.registerSimpleItemModel(p_375477_.asItem(), p_377517_.createItemModel(this, p_375477_));
        this.createPlant(p_375477_, p_376166_, p_377517_);
    }

    protected void createPlant(Block p_376623_, Block p_378539_, BlockModelGenerators.PlantType p_378054_) {
        this.createCrossBlock(p_376623_, p_378054_);
        TextureMapping texturemapping = p_378054_.getPlantTextureMapping(p_376623_);
        MultiVariant multivariant = plainVariant(p_378054_.getCrossPot().create(p_378539_, texturemapping, this.modelOutput));
        this.blockStateOutput.accept(createSimpleBlock(p_378539_, multivariant));
    }

    protected void createCoralFans(Block p_378025_, Block p_377013_) {
        TexturedModel texturedmodel = TexturedModel.CORAL_FAN.get(p_378025_);
        MultiVariant multivariant = plainVariant(texturedmodel.create(p_378025_, this.modelOutput));
        this.blockStateOutput.accept(createSimpleBlock(p_378025_, multivariant));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.CORAL_WALL_FAN.create(p_377013_, texturedmodel.getMapping(), this.modelOutput));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_377013_, multivariant1).with(ROTATION_HORIZONTAL_FACING));
        this.registerSimpleFlatItemModel(p_378025_);
    }

    protected void createStems(Block p_376872_, Block p_376213_) {
        this.registerSimpleFlatItemModel(p_376872_.asItem());
        TextureMapping texturemapping = TextureMapping.stem(p_376872_);
        TextureMapping texturemapping1 = TextureMapping.attachedStem(p_376872_, p_376213_);
        MultiVariant multivariant = plainVariant(ModelTemplates.ATTACHED_STEM.create(p_376213_, texturemapping1, this.modelOutput));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_376213_, multivariant)
                    .with(
                        PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
                            .select(Direction.WEST, NOP)
                            .select(Direction.SOUTH, Y_ROT_270)
                            .select(Direction.NORTH, Y_ROT_90)
                            .select(Direction.EAST, Y_ROT_180)
                    )
            );
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_376872_)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.AGE_7)
                            .generate(p_389238_ -> plainVariant(ModelTemplates.STEMS[p_389238_].create(p_376872_, texturemapping, this.modelOutput)))
                    )
            );
    }

    protected void createPitcherPlant() {
        Block block = Blocks.PITCHER_PLANT;
        this.registerSimpleFlatItemModel(block.asItem());
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(block, "_top"));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(block, "_bottom"));
        this.createDoubleBlock(block, multivariant, multivariant1);
    }

    protected void createPitcherCrop() {
        Block block = Blocks.PITCHER_CROP;
        this.registerSimpleFlatItemModel(block.asItem());
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(block)
                    .with(PropertyDispatch.initial(PitcherCropBlock.AGE, BlockStateProperties.DOUBLE_BLOCK_HALF).generate((p_389240_, p_389241_) -> {
                        return switch (p_389241_) {
                            case UPPER -> plainVariant(ModelLocationUtils.getModelLocation(block, "_top_stage_" + p_389240_));
                            case LOWER -> plainVariant(ModelLocationUtils.getModelLocation(block, "_bottom_stage_" + p_389240_));
                        };
                    }))
            );
    }

    protected void createCoral(
        Block p_378121_, Block p_378514_, Block p_378059_, Block p_376641_, Block p_375482_, Block p_378293_, Block p_375643_, Block p_375706_
    ) {
        this.createCrossBlockWithDefaultItem(p_378121_, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createCrossBlockWithDefaultItem(p_378514_, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createTrivialCube(p_378059_);
        this.createTrivialCube(p_376641_);
        this.createCoralFans(p_375482_, p_375643_);
        this.createCoralFans(p_378293_, p_375706_);
    }

    protected void createDoublePlant(Block p_377839_, BlockModelGenerators.PlantType p_377454_) {
        MultiVariant multivariant = plainVariant(this.createSuffixedVariant(p_377839_, "_top", p_377454_.getCross(), TextureMapping::cross));
        MultiVariant multivariant1 = plainVariant(this.createSuffixedVariant(p_377839_, "_bottom", p_377454_.getCross(), TextureMapping::cross));
        this.createDoubleBlock(p_377839_, multivariant, multivariant1);
    }

    protected void createDoublePlantWithDefaultItem(Block p_376248_, BlockModelGenerators.PlantType p_377473_) {
        this.registerSimpleFlatItemModel(p_376248_, "_top");
        this.createDoublePlant(p_376248_, p_377473_);
    }

    protected void createTintedDoublePlant(Block p_377988_) {
        ResourceLocation resourcelocation = this.createFlatItemModelWithBlockTexture(p_377988_.asItem(), p_377988_, "_top");
        this.registerSimpleTintedItemModel(p_377988_, resourcelocation, new GrassColorSource());
        this.createDoublePlant(p_377988_, BlockModelGenerators.PlantType.TINTED);
    }

    protected void createSunflower() {
        this.registerSimpleFlatItemModel(Blocks.SUNFLOWER, "_front");
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.SUNFLOWER, "_top"));
        MultiVariant multivariant1 = plainVariant(
            this.createSuffixedVariant(Blocks.SUNFLOWER, "_bottom", BlockModelGenerators.PlantType.NOT_TINTED.getCross(), TextureMapping::cross)
        );
        this.createDoubleBlock(Blocks.SUNFLOWER, multivariant, multivariant1);
    }

    protected void createTallSeagrass() {
        MultiVariant multivariant = plainVariant(this.createSuffixedVariant(Blocks.TALL_SEAGRASS, "_top", ModelTemplates.SEAGRASS, TextureMapping::defaultTexture));
        MultiVariant multivariant1 = plainVariant(this.createSuffixedVariant(Blocks.TALL_SEAGRASS, "_bottom", ModelTemplates.SEAGRASS, TextureMapping::defaultTexture));
        this.createDoubleBlock(Blocks.TALL_SEAGRASS, multivariant, multivariant1);
    }

    protected void createSmallDripleaf() {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.SMALL_DRIPLEAF, "_top"));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.SMALL_DRIPLEAF, "_bottom"));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.SMALL_DRIPLEAF)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.DOUBLE_BLOCK_HALF)
                            .select(DoubleBlockHalf.LOWER, multivariant1)
                            .select(DoubleBlockHalf.UPPER, multivariant)
                    )
                    .with(ROTATION_HORIZONTAL_FACING)
            );
    }

    protected void createDoubleBlock(Block p_376427_, MultiVariant p_391163_, MultiVariant p_392459_) {
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_376427_)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.DOUBLE_BLOCK_HALF)
                            .select(DoubleBlockHalf.LOWER, p_392459_)
                            .select(DoubleBlockHalf.UPPER, p_391163_)
                    )
            );
    }

    protected void createPassiveRail(Block p_376794_) {
        TextureMapping texturemapping = TextureMapping.rail(p_376794_);
        TextureMapping texturemapping1 = TextureMapping.rail(TextureMapping.getBlockTexture(p_376794_, "_corner"));
        MultiVariant multivariant = plainVariant(ModelTemplates.RAIL_FLAT.create(p_376794_, texturemapping, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.RAIL_CURVED.create(p_376794_, texturemapping1, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.RAIL_RAISED_NE.create(p_376794_, texturemapping, this.modelOutput));
        MultiVariant multivariant3 = plainVariant(ModelTemplates.RAIL_RAISED_SW.create(p_376794_, texturemapping, this.modelOutput));
        this.registerSimpleFlatItemModel(p_376794_);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_376794_)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.RAIL_SHAPE)
                            .select(RailShape.NORTH_SOUTH, multivariant)
                            .select(RailShape.EAST_WEST, multivariant.with(Y_ROT_90))
                            .select(RailShape.ASCENDING_EAST, multivariant2.with(Y_ROT_90))
                            .select(RailShape.ASCENDING_WEST, multivariant3.with(Y_ROT_90))
                            .select(RailShape.ASCENDING_NORTH, multivariant2)
                            .select(RailShape.ASCENDING_SOUTH, multivariant3)
                            .select(RailShape.SOUTH_EAST, multivariant1)
                            .select(RailShape.SOUTH_WEST, multivariant1.with(Y_ROT_90))
                            .select(RailShape.NORTH_WEST, multivariant1.with(Y_ROT_180))
                            .select(RailShape.NORTH_EAST, multivariant1.with(Y_ROT_270))
                    )
            );
    }

    protected void createActiveRail(Block p_378443_) {
        MultiVariant multivariant = plainVariant(this.createSuffixedVariant(p_378443_, "", ModelTemplates.RAIL_FLAT, TextureMapping::rail));
        MultiVariant multivariant1 = plainVariant(this.createSuffixedVariant(p_378443_, "", ModelTemplates.RAIL_RAISED_NE, TextureMapping::rail));
        MultiVariant multivariant2 = plainVariant(this.createSuffixedVariant(p_378443_, "", ModelTemplates.RAIL_RAISED_SW, TextureMapping::rail));
        MultiVariant multivariant3 = plainVariant(this.createSuffixedVariant(p_378443_, "_on", ModelTemplates.RAIL_FLAT, TextureMapping::rail));
        MultiVariant multivariant4 = plainVariant(this.createSuffixedVariant(p_378443_, "_on", ModelTemplates.RAIL_RAISED_NE, TextureMapping::rail));
        MultiVariant multivariant5 = plainVariant(this.createSuffixedVariant(p_378443_, "_on", ModelTemplates.RAIL_RAISED_SW, TextureMapping::rail));
        this.registerSimpleFlatItemModel(p_378443_);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_378443_)
                    .with(PropertyDispatch.initial(BlockStateProperties.POWERED, BlockStateProperties.RAIL_SHAPE_STRAIGHT).generate((p_389209_, p_389210_) -> {
                        return switch (p_389210_) {
                            case NORTH_SOUTH -> p_389209_ ? multivariant3 : multivariant;
                            case EAST_WEST -> (p_389209_ ? multivariant3 : multivariant).with(Y_ROT_90);
                            case ASCENDING_EAST -> (p_389209_ ? multivariant4 : multivariant1).with(Y_ROT_90);
                            case ASCENDING_WEST -> (p_389209_ ? multivariant5 : multivariant2).with(Y_ROT_90);
                            case ASCENDING_NORTH -> p_389209_ ? multivariant4 : multivariant1;
                            case ASCENDING_SOUTH -> p_389209_ ? multivariant5 : multivariant2;
                            default -> throw new UnsupportedOperationException("Fix you generator!");
                        };
                    }))
            );
    }

    protected void createAirLikeBlock(Block p_375555_, Item p_377535_) {
        MultiVariant multivariant = plainVariant(ModelTemplates.PARTICLE_ONLY.create(p_375555_, TextureMapping.particleFromItem(p_377535_), this.modelOutput));
        this.blockStateOutput.accept(createSimpleBlock(p_375555_, multivariant));
    }

    protected void createAirLikeBlock(Block p_377174_, ResourceLocation p_376318_) {
        MultiVariant multivariant = plainVariant(ModelTemplates.PARTICLE_ONLY.create(p_377174_, TextureMapping.particle(p_376318_), this.modelOutput));
        this.blockStateOutput.accept(createSimpleBlock(p_377174_, multivariant));
    }

    protected MultiVariant createParticleOnlyBlockModel(Block p_376767_, Block p_377465_) {
        return plainVariant(ModelTemplates.PARTICLE_ONLY.create(p_376767_, TextureMapping.particle(p_377465_), this.modelOutput));
    }

    public void createParticleOnlyBlock(Block p_377029_, Block p_376512_) {
        this.blockStateOutput.accept(createSimpleBlock(p_377029_, this.createParticleOnlyBlockModel(p_377029_, p_376512_)));
    }

    protected void createParticleOnlyBlock(Block p_378546_) {
        this.createParticleOnlyBlock(p_378546_, p_378546_);
    }

    protected void createFullAndCarpetBlocks(Block p_376946_, Block p_377725_) {
        this.createTrivialCube(p_376946_);
        MultiVariant multivariant = plainVariant(TexturedModel.CARPET.get(p_376946_).create(p_377725_, this.modelOutput));
        this.blockStateOutput.accept(createSimpleBlock(p_377725_, multivariant));
    }

    protected void createLeafLitter(Block p_398007_) {
        MultiVariant multivariant = plainVariant(TexturedModel.LEAF_LITTER_1.create(p_398007_, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(TexturedModel.LEAF_LITTER_2.create(p_398007_, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(TexturedModel.LEAF_LITTER_3.create(p_398007_, this.modelOutput));
        MultiVariant multivariant3 = plainVariant(TexturedModel.LEAF_LITTER_4.create(p_398007_, this.modelOutput));
        this.registerSimpleFlatItemModel(p_398007_.asItem());
        this.createSegmentedBlock(p_398007_, multivariant, LEAF_LITTER_MODEL_1_SEGMENT_CONDITION, multivariant1, LEAF_LITTER_MODEL_2_SEGMENT_CONDITION, multivariant2, LEAF_LITTER_MODEL_3_SEGMENT_CONDITION, multivariant3, LEAF_LITTER_MODEL_4_SEGMENT_CONDITION);
    }

    protected void createFlowerBed(Block p_376829_) {
        MultiVariant multivariant = plainVariant(TexturedModel.FLOWERBED_1.create(p_376829_, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(TexturedModel.FLOWERBED_2.create(p_376829_, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(TexturedModel.FLOWERBED_3.create(p_376829_, this.modelOutput));
        MultiVariant multivariant3 = plainVariant(TexturedModel.FLOWERBED_4.create(p_376829_, this.modelOutput));
        this.registerSimpleFlatItemModel(p_376829_.asItem());
        this.createSegmentedBlock(p_376829_, multivariant, FLOWER_BED_MODEL_1_SEGMENT_CONDITION, multivariant1, FLOWER_BED_MODEL_2_SEGMENT_CONDITION, multivariant2, FLOWER_BED_MODEL_3_SEGMENT_CONDITION, multivariant3, FLOWER_BED_MODEL_4_SEGMENT_CONDITION);
    }

    protected void createSegmentedBlock(
        Block p_392535_,
        MultiVariant p_392471_,
        Function<ConditionBuilder, ConditionBuilder> p_393854_,
        MultiVariant p_397890_,
        Function<ConditionBuilder, ConditionBuilder> p_397179_,
        MultiVariant p_397693_,
        Function<ConditionBuilder, ConditionBuilder> p_394418_,
        MultiVariant p_393924_,
        Function<ConditionBuilder, ConditionBuilder> p_392283_
    ) {
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(p_392535_)
                    .with(p_393854_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)), p_392471_)
                    .with(p_393854_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)), p_392471_.with(Y_ROT_90))
                    .with(p_393854_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)), p_392471_.with(Y_ROT_180))
                    .with(p_393854_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)), p_392471_.with(Y_ROT_270))
                    .with(p_397179_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)), p_397890_)
                    .with(p_397179_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)), p_397890_.with(Y_ROT_90))
                    .with(p_397179_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)), p_397890_.with(Y_ROT_180))
                    .with(p_397179_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)), p_397890_.with(Y_ROT_270))
                    .with(p_394418_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)), p_397693_)
                    .with(p_394418_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)), p_397693_.with(Y_ROT_90))
                    .with(p_394418_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)), p_397693_.with(Y_ROT_180))
                    .with(p_394418_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)), p_397693_.with(Y_ROT_270))
                    .with(p_392283_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)), p_393924_)
                    .with(p_392283_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)), p_393924_.with(Y_ROT_90))
                    .with(p_392283_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)), p_393924_.with(Y_ROT_180))
                    .with(p_392283_.apply(condition().term(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)), p_393924_.with(Y_ROT_270))
            );
    }

    protected void createColoredBlockWithRandomRotations(TexturedModel.Provider p_378645_, Block... p_378778_) {
        for (Block block : p_378778_) {
            Variant variant = plainModel(p_378645_.create(block, this.modelOutput));
            this.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, createRotatedVariants(variant)));
        }
    }

    protected void createColoredBlockWithStateRotations(TexturedModel.Provider p_377875_, Block... p_378422_) {
        for (Block block : p_378422_) {
            MultiVariant multivariant = plainVariant(p_377875_.create(block, this.modelOutput));
            this.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, multivariant).with(ROTATION_HORIZONTAL_FACING_ALT));
        }
    }

    protected void createGlassBlocks(Block p_376058_, Block p_376366_) {
        this.createTrivialCube(p_376058_);
        TextureMapping texturemapping = TextureMapping.pane(p_376058_, p_376366_);
        MultiVariant multivariant = plainVariant(ModelTemplates.STAINED_GLASS_PANE_POST.create(p_376366_, texturemapping, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.STAINED_GLASS_PANE_SIDE.create(p_376366_, texturemapping, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.STAINED_GLASS_PANE_SIDE_ALT.create(p_376366_, texturemapping, this.modelOutput));
        MultiVariant multivariant3 = plainVariant(ModelTemplates.STAINED_GLASS_PANE_NOSIDE.create(p_376366_, texturemapping, this.modelOutput));
        MultiVariant multivariant4 = plainVariant(ModelTemplates.STAINED_GLASS_PANE_NOSIDE_ALT.create(p_376366_, texturemapping, this.modelOutput));
        Item item = p_376366_.asItem();
        this.registerSimpleItemModel(item, this.createFlatItemModelWithBlockTexture(item, p_376058_));
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(p_376366_)
                    .with(multivariant)
                    .with(condition().term(BlockStateProperties.NORTH, true), multivariant1)
                    .with(condition().term(BlockStateProperties.EAST, true), multivariant1.with(Y_ROT_90))
                    .with(condition().term(BlockStateProperties.SOUTH, true), multivariant2)
                    .with(condition().term(BlockStateProperties.WEST, true), multivariant2.with(Y_ROT_90))
                    .with(condition().term(BlockStateProperties.NORTH, false), multivariant3)
                    .with(condition().term(BlockStateProperties.EAST, false), multivariant4)
                    .with(condition().term(BlockStateProperties.SOUTH, false), multivariant4.with(Y_ROT_90))
                    .with(condition().term(BlockStateProperties.WEST, false), multivariant3.with(Y_ROT_270))
            );
    }

    protected void createCommandBlock(Block p_377693_) {
        TextureMapping texturemapping = TextureMapping.commandBlock(p_377693_);
        MultiVariant multivariant = plainVariant(ModelTemplates.COMMAND_BLOCK.create(p_377693_, texturemapping, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(
            this.createSuffixedVariant(p_377693_, "_conditional", ModelTemplates.COMMAND_BLOCK, p_378831_ -> texturemapping.copyAndUpdate(TextureSlot.SIDE, p_378831_))
        );
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_377693_)
                    .with(createBooleanModelDispatch(BlockStateProperties.CONDITIONAL, multivariant1, multivariant))
                    .with(ROTATION_FACING)
            );
    }

    protected void createAnvil(Block p_377887_) {
        MultiVariant multivariant = plainVariant(TexturedModel.ANVIL.create(p_377887_, this.modelOutput));
        this.blockStateOutput.accept(createSimpleBlock(p_377887_, multivariant).with(ROTATION_HORIZONTAL_FACING_ALT));
    }

    protected static MultiVariant createBambooModels(int p_375570_) {
        String s = "_age" + p_375570_;
        return new MultiVariant(
            WeightedList.of(
                IntStream.range(1, 5)
                    .mapToObj(p_389158_ -> new Weighted<>(plainModel(ModelLocationUtils.getModelLocation(Blocks.BAMBOO, p_389158_ + s)), 1))
                    .collect(Collectors.toList())
            )
        );
    }

    protected void createBamboo() {
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(Blocks.BAMBOO)
                    .with(condition().term(BlockStateProperties.AGE_1, 0), createBambooModels(0))
                    .with(condition().term(BlockStateProperties.AGE_1, 1), createBambooModels(1))
                    .with(
                        condition().term(BlockStateProperties.BAMBOO_LEAVES, BambooLeaves.SMALL),
                        plainVariant(ModelLocationUtils.getModelLocation(Blocks.BAMBOO, "_small_leaves"))
                    )
                    .with(
                        condition().term(BlockStateProperties.BAMBOO_LEAVES, BambooLeaves.LARGE),
                        plainVariant(ModelLocationUtils.getModelLocation(Blocks.BAMBOO, "_large_leaves"))
                    )
            );
    }

    protected void createBarrel() {
        ResourceLocation resourcelocation = TextureMapping.getBlockTexture(Blocks.BARREL, "_top_open");
        MultiVariant multivariant = plainVariant(TexturedModel.CUBE_TOP_BOTTOM.create(Blocks.BARREL, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(
            TexturedModel.CUBE_TOP_BOTTOM
                .get(Blocks.BARREL)
                .updateTextures(p_377357_ -> p_377357_.put(TextureSlot.TOP, resourcelocation))
                .createWithSuffix(Blocks.BARREL, "_open", this.modelOutput)
        );
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.BARREL)
                    .with(PropertyDispatch.initial(BlockStateProperties.OPEN).select(false, multivariant).select(true, multivariant1))
                    .with(ROTATIONS_COLUMN_WITH_FACING)
            );
    }

    protected static <T extends Comparable<T>> PropertyDispatch<MultiVariant> createEmptyOrFullDispatch(
        Property<T> p_377696_, T p_375681_, MultiVariant p_393994_, MultiVariant p_392146_
    ) {
        return PropertyDispatch.initial(p_377696_).generate(p_375444_ -> {
            boolean flag = p_375444_.compareTo(p_375681_) >= 0;
            return flag ? p_393994_ : p_392146_;
        });
    }

    protected void createBeeNest(Block p_377006_, Function<Block, TextureMapping> p_375956_) {
        TextureMapping texturemapping = p_375956_.apply(p_377006_).copyForced(TextureSlot.SIDE, TextureSlot.PARTICLE);
        TextureMapping texturemapping1 = texturemapping.copyAndUpdate(TextureSlot.FRONT, TextureMapping.getBlockTexture(p_377006_, "_front_honey"));
        ResourceLocation resourcelocation = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.createWithSuffix(p_377006_, "_empty", texturemapping, this.modelOutput);
        ResourceLocation resourcelocation1 = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.createWithSuffix(p_377006_, "_honey", texturemapping1, this.modelOutput);
        this.itemModelOutput
            .accept(
                p_377006_.asItem(),
                ItemModelUtils.selectBlockItemProperty(
                    BeehiveBlock.HONEY_LEVEL, ItemModelUtils.plainModel(resourcelocation), Map.of(5, ItemModelUtils.plainModel(resourcelocation1))
                )
            );
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_377006_)
                    .with(createEmptyOrFullDispatch(BeehiveBlock.HONEY_LEVEL, 5, plainVariant(resourcelocation1), plainVariant(resourcelocation)))
                    .with(ROTATION_HORIZONTAL_FACING)
            );
    }

    protected void createCropBlock(Block p_378549_, Property<Integer> p_377514_, int... p_378260_) {
        this.registerSimpleFlatItemModel(p_378549_.asItem());
        if (p_377514_.getPossibleValues().size() != p_378260_.length) {
            throw new IllegalArgumentException();
        } else {
            Int2ObjectMap<ResourceLocation> int2objectmap = new Int2ObjectOpenHashMap<>();
            this.blockStateOutput
                .accept(
                    MultiVariantGenerator.dispatch(p_378549_)
                        .with(
                            PropertyDispatch.initial(p_377514_)
                                .generate(
                                    p_389245_ -> {
                                        int i = p_378260_[p_389245_];
                                        return plainVariant(
                                            int2objectmap.computeIfAbsent(
                                                i,
                                                p_377909_ -> this.createSuffixedVariant(
                                                    p_378549_, "_stage" + p_377909_, ModelTemplates.CROP, TextureMapping::crop
                                                )
                                            )
                                        );
                                    }
                                )
                        )
                );
        }
    }

    protected void createBell() {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.BELL, "_floor"));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.BELL, "_ceiling"));
        MultiVariant multivariant2 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.BELL, "_wall"));
        MultiVariant multivariant3 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.BELL, "_between_walls"));
        this.registerSimpleFlatItemModel(Items.BELL);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.BELL)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.BELL_ATTACHMENT)
                            .select(Direction.NORTH, BellAttachType.FLOOR, multivariant)
                            .select(Direction.SOUTH, BellAttachType.FLOOR, multivariant.with(Y_ROT_180))
                            .select(Direction.EAST, BellAttachType.FLOOR, multivariant.with(Y_ROT_90))
                            .select(Direction.WEST, BellAttachType.FLOOR, multivariant.with(Y_ROT_270))
                            .select(Direction.NORTH, BellAttachType.CEILING, multivariant1)
                            .select(Direction.SOUTH, BellAttachType.CEILING, multivariant1.with(Y_ROT_180))
                            .select(Direction.EAST, BellAttachType.CEILING, multivariant1.with(Y_ROT_90))
                            .select(Direction.WEST, BellAttachType.CEILING, multivariant1.with(Y_ROT_270))
                            .select(Direction.NORTH, BellAttachType.SINGLE_WALL, multivariant2.with(Y_ROT_270))
                            .select(Direction.SOUTH, BellAttachType.SINGLE_WALL, multivariant2.with(Y_ROT_90))
                            .select(Direction.EAST, BellAttachType.SINGLE_WALL, multivariant2)
                            .select(Direction.WEST, BellAttachType.SINGLE_WALL, multivariant2.with(Y_ROT_180))
                            .select(Direction.SOUTH, BellAttachType.DOUBLE_WALL, multivariant3.with(Y_ROT_90))
                            .select(Direction.NORTH, BellAttachType.DOUBLE_WALL, multivariant3.with(Y_ROT_270))
                            .select(Direction.EAST, BellAttachType.DOUBLE_WALL, multivariant3)
                            .select(Direction.WEST, BellAttachType.DOUBLE_WALL, multivariant3.with(Y_ROT_180))
                    )
            );
    }

    protected void createGrindstone() {
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.GRINDSTONE, plainVariant(ModelLocationUtils.getModelLocation(Blocks.GRINDSTONE)))
                    .with(
                        PropertyDispatch.modify(BlockStateProperties.ATTACH_FACE, BlockStateProperties.HORIZONTAL_FACING)
                            .select(AttachFace.FLOOR, Direction.NORTH, NOP)
                            .select(AttachFace.FLOOR, Direction.EAST, Y_ROT_90)
                            .select(AttachFace.FLOOR, Direction.SOUTH, Y_ROT_180)
                            .select(AttachFace.FLOOR, Direction.WEST, Y_ROT_270)
                            .select(AttachFace.WALL, Direction.NORTH, X_ROT_90)
                            .select(AttachFace.WALL, Direction.EAST, X_ROT_90.then(Y_ROT_90))
                            .select(AttachFace.WALL, Direction.SOUTH, X_ROT_90.then(Y_ROT_180))
                            .select(AttachFace.WALL, Direction.WEST, X_ROT_90.then(Y_ROT_270))
                            .select(AttachFace.CEILING, Direction.SOUTH, X_ROT_180)
                            .select(AttachFace.CEILING, Direction.WEST, X_ROT_180.then(Y_ROT_90))
                            .select(AttachFace.CEILING, Direction.NORTH, X_ROT_180.then(Y_ROT_180))
                            .select(AttachFace.CEILING, Direction.EAST, X_ROT_180.then(Y_ROT_270))
                    )
            );
    }

    protected void createFurnace(Block p_376661_, TexturedModel.Provider p_378251_) {
        MultiVariant multivariant = plainVariant(p_378251_.create(p_376661_, this.modelOutput));
        ResourceLocation resourcelocation = TextureMapping.getBlockTexture(p_376661_, "_front_on");
        MultiVariant multivariant1 = plainVariant(
            p_378251_.get(p_376661_)
                .updateTextures(p_377718_ -> p_377718_.put(TextureSlot.FRONT, resourcelocation))
                .createWithSuffix(p_376661_, "_on", this.modelOutput)
        );
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_376661_)
                    .with(createBooleanModelDispatch(BlockStateProperties.LIT, multivariant1, multivariant))
                    .with(ROTATION_HORIZONTAL_FACING)
            );
    }

    protected void createCampfires(Block... p_376654_) {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.decorateBlockModelLocation("campfire_off"));

        for (Block block : p_376654_) {
            MultiVariant multivariant1 = plainVariant(ModelTemplates.CAMPFIRE.create(block, TextureMapping.campfire(block), this.modelOutput));
            this.registerSimpleFlatItemModel(block.asItem());
            this.blockStateOutput
                .accept(
                    MultiVariantGenerator.dispatch(block)
                        .with(createBooleanModelDispatch(BlockStateProperties.LIT, multivariant1, multivariant))
                        .with(ROTATION_HORIZONTAL_FACING_ALT)
                );
        }
    }

    protected void createAzalea(Block p_378737_) {
        MultiVariant multivariant = plainVariant(ModelTemplates.AZALEA.create(p_378737_, TextureMapping.cubeTop(p_378737_), this.modelOutput));
        this.blockStateOutput.accept(createSimpleBlock(p_378737_, multivariant));
    }

    protected void createPottedAzalea(Block p_377155_) {
        MultiVariant multivariant;
        if (p_377155_ == Blocks.POTTED_FLOWERING_AZALEA) {
            multivariant = plainVariant(ModelTemplates.POTTED_FLOWERING_AZALEA.create(p_377155_, TextureMapping.pottedAzalea(p_377155_), this.modelOutput));
        } else {
            multivariant = plainVariant(ModelTemplates.POTTED_AZALEA.create(p_377155_, TextureMapping.pottedAzalea(p_377155_), this.modelOutput));
        }

        this.blockStateOutput.accept(createSimpleBlock(p_377155_, multivariant));
    }

    protected void createBookshelf() {
        TextureMapping texturemapping = TextureMapping.column(TextureMapping.getBlockTexture(Blocks.BOOKSHELF), TextureMapping.getBlockTexture(Blocks.OAK_PLANKS));
        MultiVariant multivariant = plainVariant(ModelTemplates.CUBE_COLUMN.create(Blocks.BOOKSHELF, texturemapping, this.modelOutput));
        this.blockStateOutput.accept(createSimpleBlock(Blocks.BOOKSHELF, multivariant));
    }

    protected void createRedstoneWire() {
        this.registerSimpleFlatItemModel(Items.REDSTONE);
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(Blocks.REDSTONE_WIRE)
                    .with(
                        or(
                            condition()
                                .term(BlockStateProperties.NORTH_REDSTONE, RedstoneSide.NONE)
                                .term(BlockStateProperties.EAST_REDSTONE, RedstoneSide.NONE)
                                .term(BlockStateProperties.SOUTH_REDSTONE, RedstoneSide.NONE)
                                .term(BlockStateProperties.WEST_REDSTONE, RedstoneSide.NONE),
                            condition()
                                .term(BlockStateProperties.NORTH_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP)
                                .term(BlockStateProperties.EAST_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP),
                            condition()
                                .term(BlockStateProperties.EAST_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP)
                                .term(BlockStateProperties.SOUTH_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP),
                            condition()
                                .term(BlockStateProperties.SOUTH_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP)
                                .term(BlockStateProperties.WEST_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP),
                            condition()
                                .term(BlockStateProperties.WEST_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP)
                                .term(BlockStateProperties.NORTH_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP)
                        ),
                        plainVariant(ModelLocationUtils.decorateBlockModelLocation("redstone_dust_dot"))
                    )
                    .with(
                        condition().term(BlockStateProperties.NORTH_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP),
                        plainVariant(ModelLocationUtils.decorateBlockModelLocation("redstone_dust_side0"))
                    )
                    .with(
                        condition().term(BlockStateProperties.SOUTH_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP),
                        plainVariant(ModelLocationUtils.decorateBlockModelLocation("redstone_dust_side_alt0"))
                    )
                    .with(
                        condition().term(BlockStateProperties.EAST_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP),
                        plainVariant(ModelLocationUtils.decorateBlockModelLocation("redstone_dust_side_alt1")).with(Y_ROT_270)
                    )
                    .with(
                        condition().term(BlockStateProperties.WEST_REDSTONE, RedstoneSide.SIDE, RedstoneSide.UP),
                        plainVariant(ModelLocationUtils.decorateBlockModelLocation("redstone_dust_side1")).with(Y_ROT_270)
                    )
                    .with(
                        condition().term(BlockStateProperties.NORTH_REDSTONE, RedstoneSide.UP), plainVariant(ModelLocationUtils.decorateBlockModelLocation("redstone_dust_up"))
                    )
                    .with(
                        condition().term(BlockStateProperties.EAST_REDSTONE, RedstoneSide.UP),
                        plainVariant(ModelLocationUtils.decorateBlockModelLocation("redstone_dust_up")).with(Y_ROT_90)
                    )
                    .with(
                        condition().term(BlockStateProperties.SOUTH_REDSTONE, RedstoneSide.UP),
                        plainVariant(ModelLocationUtils.decorateBlockModelLocation("redstone_dust_up")).with(Y_ROT_180)
                    )
                    .with(
                        condition().term(BlockStateProperties.WEST_REDSTONE, RedstoneSide.UP),
                        plainVariant(ModelLocationUtils.decorateBlockModelLocation("redstone_dust_up")).with(Y_ROT_270)
                    )
            );
    }

    protected void createComparator() {
        this.registerSimpleFlatItemModel(Items.COMPARATOR);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.COMPARATOR)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.MODE_COMPARATOR, BlockStateProperties.POWERED)
                            .select(ComparatorMode.COMPARE, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.COMPARATOR)))
                            .select(ComparatorMode.COMPARE, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.COMPARATOR, "_on")))
                            .select(ComparatorMode.SUBTRACT, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.COMPARATOR, "_subtract")))
                            .select(ComparatorMode.SUBTRACT, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.COMPARATOR, "_on_subtract")))
                    )
                    .with(ROTATION_HORIZONTAL_FACING_ALT)
            );
    }

    protected void createSmoothStoneSlab() {
        TextureMapping texturemapping = TextureMapping.cube(Blocks.SMOOTH_STONE);
        TextureMapping texturemapping1 = TextureMapping.column(
            TextureMapping.getBlockTexture(Blocks.SMOOTH_STONE_SLAB, "_side"), texturemapping.get(TextureSlot.TOP)
        );
        MultiVariant multivariant = plainVariant(ModelTemplates.SLAB_BOTTOM.create(Blocks.SMOOTH_STONE_SLAB, texturemapping1, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.SLAB_TOP.create(Blocks.SMOOTH_STONE_SLAB, texturemapping1, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.CUBE_COLUMN.createWithOverride(Blocks.SMOOTH_STONE_SLAB, "_double", texturemapping1, this.modelOutput));
        this.blockStateOutput.accept(createSlab(Blocks.SMOOTH_STONE_SLAB, multivariant, multivariant1, multivariant2));
        this.blockStateOutput.accept(createSimpleBlock(Blocks.SMOOTH_STONE, plainVariant(ModelTemplates.CUBE_ALL.create(Blocks.SMOOTH_STONE, texturemapping, this.modelOutput))));
    }

    protected void createBrewingStand() {
        this.registerSimpleFlatItemModel(Items.BREWING_STAND);
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(Blocks.BREWING_STAND)
                    .with(plainVariant(TextureMapping.getBlockTexture(Blocks.BREWING_STAND)))
                    .with(condition().term(BlockStateProperties.HAS_BOTTLE_0, true), plainVariant(TextureMapping.getBlockTexture(Blocks.BREWING_STAND, "_bottle0")))
                    .with(condition().term(BlockStateProperties.HAS_BOTTLE_1, true), plainVariant(TextureMapping.getBlockTexture(Blocks.BREWING_STAND, "_bottle1")))
                    .with(condition().term(BlockStateProperties.HAS_BOTTLE_2, true), plainVariant(TextureMapping.getBlockTexture(Blocks.BREWING_STAND, "_bottle2")))
                    .with(condition().term(BlockStateProperties.HAS_BOTTLE_0, false), plainVariant(TextureMapping.getBlockTexture(Blocks.BREWING_STAND, "_empty0")))
                    .with(condition().term(BlockStateProperties.HAS_BOTTLE_1, false), plainVariant(TextureMapping.getBlockTexture(Blocks.BREWING_STAND, "_empty1")))
                    .with(condition().term(BlockStateProperties.HAS_BOTTLE_2, false), plainVariant(TextureMapping.getBlockTexture(Blocks.BREWING_STAND, "_empty2")))
            );
    }

    protected void createMushroomBlock(Block p_377576_) {
        MultiVariant multivariant = plainVariant(ModelTemplates.SINGLE_FACE.create(p_377576_, TextureMapping.defaultTexture(p_377576_), this.modelOutput));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.decorateBlockModelLocation("mushroom_block_inside"));
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(p_377576_)
                    .with(condition().term(BlockStateProperties.NORTH, true), multivariant)
                    .with(condition().term(BlockStateProperties.EAST, true), multivariant.with(Y_ROT_90).with(UV_LOCK))
                    .with(condition().term(BlockStateProperties.SOUTH, true), multivariant.with(Y_ROT_180).with(UV_LOCK))
                    .with(condition().term(BlockStateProperties.WEST, true), multivariant.with(Y_ROT_270).with(UV_LOCK))
                    .with(condition().term(BlockStateProperties.UP, true), multivariant.with(X_ROT_270).with(UV_LOCK))
                    .with(condition().term(BlockStateProperties.DOWN, true), multivariant.with(X_ROT_90).with(UV_LOCK))
                    .with(condition().term(BlockStateProperties.NORTH, false), multivariant1)
                    .with(condition().term(BlockStateProperties.EAST, false), multivariant1.with(Y_ROT_90))
                    .with(condition().term(BlockStateProperties.SOUTH, false), multivariant1.with(Y_ROT_180))
                    .with(condition().term(BlockStateProperties.WEST, false), multivariant1.with(Y_ROT_270))
                    .with(condition().term(BlockStateProperties.UP, false), multivariant1.with(X_ROT_270))
                    .with(condition().term(BlockStateProperties.DOWN, false), multivariant1.with(X_ROT_90))
            );
        this.registerSimpleItemModel(p_377576_, TexturedModel.CUBE.createWithSuffix(p_377576_, "_inventory", this.modelOutput));
    }

    protected void createCakeBlock() {
        this.registerSimpleFlatItemModel(Items.CAKE);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.CAKE)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.BITES)
                            .select(0, plainVariant(ModelLocationUtils.getModelLocation(Blocks.CAKE)))
                            .select(1, plainVariant(ModelLocationUtils.getModelLocation(Blocks.CAKE, "_slice1")))
                            .select(2, plainVariant(ModelLocationUtils.getModelLocation(Blocks.CAKE, "_slice2")))
                            .select(3, plainVariant(ModelLocationUtils.getModelLocation(Blocks.CAKE, "_slice3")))
                            .select(4, plainVariant(ModelLocationUtils.getModelLocation(Blocks.CAKE, "_slice4")))
                            .select(5, plainVariant(ModelLocationUtils.getModelLocation(Blocks.CAKE, "_slice5")))
                            .select(6, plainVariant(ModelLocationUtils.getModelLocation(Blocks.CAKE, "_slice6")))
                    )
            );
    }

    protected void createCartographyTable() {
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(Blocks.CARTOGRAPHY_TABLE, "_side3"))
            .put(TextureSlot.DOWN, TextureMapping.getBlockTexture(Blocks.DARK_OAK_PLANKS))
            .put(TextureSlot.UP, TextureMapping.getBlockTexture(Blocks.CARTOGRAPHY_TABLE, "_top"))
            .put(TextureSlot.NORTH, TextureMapping.getBlockTexture(Blocks.CARTOGRAPHY_TABLE, "_side3"))
            .put(TextureSlot.EAST, TextureMapping.getBlockTexture(Blocks.CARTOGRAPHY_TABLE, "_side3"))
            .put(TextureSlot.SOUTH, TextureMapping.getBlockTexture(Blocks.CARTOGRAPHY_TABLE, "_side1"))
            .put(TextureSlot.WEST, TextureMapping.getBlockTexture(Blocks.CARTOGRAPHY_TABLE, "_side2"));
        this.blockStateOutput.accept(createSimpleBlock(Blocks.CARTOGRAPHY_TABLE, plainVariant(ModelTemplates.CUBE.create(Blocks.CARTOGRAPHY_TABLE, texturemapping, this.modelOutput))));
    }

    protected void createSmithingTable() {
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(Blocks.SMITHING_TABLE, "_front"))
            .put(TextureSlot.DOWN, TextureMapping.getBlockTexture(Blocks.SMITHING_TABLE, "_bottom"))
            .put(TextureSlot.UP, TextureMapping.getBlockTexture(Blocks.SMITHING_TABLE, "_top"))
            .put(TextureSlot.NORTH, TextureMapping.getBlockTexture(Blocks.SMITHING_TABLE, "_front"))
            .put(TextureSlot.SOUTH, TextureMapping.getBlockTexture(Blocks.SMITHING_TABLE, "_front"))
            .put(TextureSlot.EAST, TextureMapping.getBlockTexture(Blocks.SMITHING_TABLE, "_side"))
            .put(TextureSlot.WEST, TextureMapping.getBlockTexture(Blocks.SMITHING_TABLE, "_side"));
        this.blockStateOutput.accept(createSimpleBlock(Blocks.SMITHING_TABLE, plainVariant(ModelTemplates.CUBE.create(Blocks.SMITHING_TABLE, texturemapping, this.modelOutput))));
    }

    protected void createCraftingTableLike(Block p_377210_, Block p_375763_, BiFunction<Block, Block, TextureMapping> p_378456_) {
        TextureMapping texturemapping = p_378456_.apply(p_377210_, p_375763_);
        this.blockStateOutput.accept(createSimpleBlock(p_377210_, plainVariant(ModelTemplates.CUBE.create(p_377210_, texturemapping, this.modelOutput))));
    }

    public void createGenericCube(Block p_378403_) {
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(p_378403_, "_particle"))
            .put(TextureSlot.DOWN, TextureMapping.getBlockTexture(p_378403_, "_down"))
            .put(TextureSlot.UP, TextureMapping.getBlockTexture(p_378403_, "_up"))
            .put(TextureSlot.NORTH, TextureMapping.getBlockTexture(p_378403_, "_north"))
            .put(TextureSlot.SOUTH, TextureMapping.getBlockTexture(p_378403_, "_south"))
            .put(TextureSlot.EAST, TextureMapping.getBlockTexture(p_378403_, "_east"))
            .put(TextureSlot.WEST, TextureMapping.getBlockTexture(p_378403_, "_west"));
        this.blockStateOutput.accept(createSimpleBlock(p_378403_, plainVariant(ModelTemplates.CUBE.create(p_378403_, texturemapping, this.modelOutput))));
    }

    protected void createPumpkins() {
        TextureMapping texturemapping = TextureMapping.column(Blocks.PUMPKIN);
        this.blockStateOutput.accept(createSimpleBlock(Blocks.PUMPKIN, plainVariant(ModelLocationUtils.getModelLocation(Blocks.PUMPKIN))));
        this.createPumpkinVariant(Blocks.CARVED_PUMPKIN, texturemapping);
        this.createPumpkinVariant(Blocks.JACK_O_LANTERN, texturemapping);
    }

    protected void createPumpkinVariant(Block p_376185_, TextureMapping p_377021_) {
        MultiVariant multivariant = plainVariant(
            ModelTemplates.CUBE_ORIENTABLE.create(p_376185_, p_377021_.copyAndUpdate(TextureSlot.FRONT, TextureMapping.getBlockTexture(p_376185_)), this.modelOutput)
        );
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_376185_, multivariant).with(ROTATION_HORIZONTAL_FACING));
    }

    protected void createCauldrons() {
        this.registerSimpleFlatItemModel(Items.CAULDRON);
        this.createNonTemplateModelBlock(Blocks.CAULDRON);
        this.blockStateOutput
            .accept(
                createSimpleBlock(
                    Blocks.LAVA_CAULDRON,
                    plainVariant(
                        ModelTemplates.CAULDRON_FULL
                            .create(Blocks.LAVA_CAULDRON, TextureMapping.cauldron(TextureMapping.getBlockTexture(Blocks.LAVA, "_still")), this.modelOutput)
                    )
                )
            );
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.WATER_CAULDRON)
                    .with(
                        PropertyDispatch.initial(LayeredCauldronBlock.LEVEL)
                            .select(
                                1,
                                plainVariant(
                                    ModelTemplates.CAULDRON_LEVEL1
                                        .createWithSuffix(
                                            Blocks.WATER_CAULDRON,
                                            "_level1",
                                            TextureMapping.cauldron(TextureMapping.getBlockTexture(Blocks.WATER, "_still")),
                                            this.modelOutput
                                        )
                                )
                            )
                            .select(
                                2,
                                plainVariant(
                                    ModelTemplates.CAULDRON_LEVEL2
                                        .createWithSuffix(
                                            Blocks.WATER_CAULDRON,
                                            "_level2",
                                            TextureMapping.cauldron(TextureMapping.getBlockTexture(Blocks.WATER, "_still")),
                                            this.modelOutput
                                        )
                                )
                            )
                            .select(
                                3,
                                plainVariant(
                                    ModelTemplates.CAULDRON_FULL
                                        .createWithSuffix(
                                            Blocks.WATER_CAULDRON,
                                            "_full",
                                            TextureMapping.cauldron(TextureMapping.getBlockTexture(Blocks.WATER, "_still")),
                                            this.modelOutput
                                        )
                                )
                            )
                    )
            );
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.POWDER_SNOW_CAULDRON)
                    .with(
                        PropertyDispatch.initial(LayeredCauldronBlock.LEVEL)
                            .select(
                                1,
                                plainVariant(
                                    ModelTemplates.CAULDRON_LEVEL1
                                        .createWithSuffix(
                                            Blocks.POWDER_SNOW_CAULDRON, "_level1", TextureMapping.cauldron(TextureMapping.getBlockTexture(Blocks.POWDER_SNOW)), this.modelOutput
                                        )
                                )
                            )
                            .select(
                                2,
                                plainVariant(
                                    ModelTemplates.CAULDRON_LEVEL2
                                        .createWithSuffix(
                                            Blocks.POWDER_SNOW_CAULDRON, "_level2", TextureMapping.cauldron(TextureMapping.getBlockTexture(Blocks.POWDER_SNOW)), this.modelOutput
                                        )
                                )
                            )
                            .select(
                                3,
                                plainVariant(
                                    ModelTemplates.CAULDRON_FULL
                                        .createWithSuffix(
                                            Blocks.POWDER_SNOW_CAULDRON, "_full", TextureMapping.cauldron(TextureMapping.getBlockTexture(Blocks.POWDER_SNOW)), this.modelOutput
                                        )
                                )
                            )
                    )
            );
    }

    protected void createChorusFlower() {
        TextureMapping texturemapping = TextureMapping.defaultTexture(Blocks.CHORUS_FLOWER);
        MultiVariant multivariant = plainVariant(ModelTemplates.CHORUS_FLOWER.create(Blocks.CHORUS_FLOWER, texturemapping, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(
            this.createSuffixedVariant(Blocks.CHORUS_FLOWER, "_dead", ModelTemplates.CHORUS_FLOWER, p_377838_ -> texturemapping.copyAndUpdate(TextureSlot.TEXTURE, p_377838_))
        );
        this.blockStateOutput
            .accept(MultiVariantGenerator.dispatch(Blocks.CHORUS_FLOWER).with(createEmptyOrFullDispatch(BlockStateProperties.AGE_5, 5, multivariant1, multivariant)));
    }

    protected void createCrafterBlock() {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.CRAFTER));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.CRAFTER, "_triggered"));
        MultiVariant multivariant2 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.CRAFTER, "_crafting"));
        MultiVariant multivariant3 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.CRAFTER, "_crafting_triggered"));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.CRAFTER)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.TRIGGERED, CrafterBlock.CRAFTING)
                            .select(false, false, multivariant)
                            .select(true, true, multivariant3)
                            .select(true, false, multivariant1)
                            .select(false, true, multivariant2)
                    )
                    .with(PropertyDispatch.modify(BlockStateProperties.ORIENTATION).generate(BlockModelGenerators::applyRotation))
            );
    }

    protected void createDispenserBlock(Block p_376199_) {
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.FURNACE, "_top"))
            .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.FURNACE, "_side"))
            .put(TextureSlot.FRONT, TextureMapping.getBlockTexture(p_376199_, "_front"));
        TextureMapping texturemapping1 = new TextureMapping()
            .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.FURNACE, "_top"))
            .put(TextureSlot.FRONT, TextureMapping.getBlockTexture(p_376199_, "_front_vertical"));
        MultiVariant multivariant = plainVariant(ModelTemplates.CUBE_ORIENTABLE.create(p_376199_, texturemapping, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.CUBE_ORIENTABLE_VERTICAL.create(p_376199_, texturemapping1, this.modelOutput));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_376199_)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.FACING)
                            .select(Direction.DOWN, multivariant1.with(X_ROT_180))
                            .select(Direction.UP, multivariant1)
                            .select(Direction.NORTH, multivariant)
                            .select(Direction.EAST, multivariant.with(Y_ROT_90))
                            .select(Direction.SOUTH, multivariant.with(Y_ROT_180))
                            .select(Direction.WEST, multivariant.with(Y_ROT_270))
                    )
            );
    }

    protected void createEndPortalFrame() {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.END_PORTAL_FRAME));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.END_PORTAL_FRAME, "_filled"));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.END_PORTAL_FRAME)
                    .with(PropertyDispatch.initial(BlockStateProperties.EYE).select(false, multivariant).select(true, multivariant1))
                    .with(ROTATION_HORIZONTAL_FACING_ALT)
            );
    }

    protected void createChorusPlant() {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.CHORUS_PLANT, "_side"));
        Variant variant = plainModel(ModelLocationUtils.getModelLocation(Blocks.CHORUS_PLANT, "_noside"));
        Variant variant1 = plainModel(ModelLocationUtils.getModelLocation(Blocks.CHORUS_PLANT, "_noside1"));
        Variant variant2 = plainModel(ModelLocationUtils.getModelLocation(Blocks.CHORUS_PLANT, "_noside2"));
        Variant variant3 = plainModel(ModelLocationUtils.getModelLocation(Blocks.CHORUS_PLANT, "_noside3"));
        Variant variant4 = variant.with(UV_LOCK);
        Variant variant5 = variant1.with(UV_LOCK);
        Variant variant6 = variant2.with(UV_LOCK);
        Variant variant7 = variant3.with(UV_LOCK);
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(Blocks.CHORUS_PLANT)
                    .with(condition().term(BlockStateProperties.NORTH, true), multivariant)
                    .with(condition().term(BlockStateProperties.EAST, true), multivariant.with(Y_ROT_90).with(UV_LOCK))
                    .with(condition().term(BlockStateProperties.SOUTH, true), multivariant.with(Y_ROT_180).with(UV_LOCK))
                    .with(condition().term(BlockStateProperties.WEST, true), multivariant.with(Y_ROT_270).with(UV_LOCK))
                    .with(condition().term(BlockStateProperties.UP, true), multivariant.with(X_ROT_270).with(UV_LOCK))
                    .with(condition().term(BlockStateProperties.DOWN, true), multivariant.with(X_ROT_90).with(UV_LOCK))
                    .with(
                        condition().term(BlockStateProperties.NORTH, false),
                        new MultiVariant(
                            WeightedList.of(
                                new Weighted<>(variant, 2), new Weighted<>(variant1, 1), new Weighted<>(variant2, 1), new Weighted<>(variant3, 1)
                            )
                        )
                    )
                    .with(
                        condition().term(BlockStateProperties.EAST, false),
                        new MultiVariant(
                            WeightedList.of(
                                new Weighted<>(variant5.with(Y_ROT_90), 1),
                                new Weighted<>(variant6.with(Y_ROT_90), 1),
                                new Weighted<>(variant7.with(Y_ROT_90), 1),
                                new Weighted<>(variant4.with(Y_ROT_90), 2)
                            )
                        )
                    )
                    .with(
                        condition().term(BlockStateProperties.SOUTH, false),
                        new MultiVariant(
                            WeightedList.of(
                                new Weighted<>(variant6.with(Y_ROT_180), 1),
                                new Weighted<>(variant7.with(Y_ROT_180), 1),
                                new Weighted<>(variant4.with(Y_ROT_180), 2),
                                new Weighted<>(variant5.with(Y_ROT_180), 1)
                            )
                        )
                    )
                    .with(
                        condition().term(BlockStateProperties.WEST, false),
                        new MultiVariant(
                            WeightedList.of(
                                new Weighted<>(variant7.with(Y_ROT_270), 1),
                                new Weighted<>(variant4.with(Y_ROT_270), 2),
                                new Weighted<>(variant5.with(Y_ROT_270), 1),
                                new Weighted<>(variant6.with(Y_ROT_270), 1)
                            )
                        )
                    )
                    .with(
                        condition().term(BlockStateProperties.UP, false),
                        new MultiVariant(
                            WeightedList.of(
                                new Weighted<>(variant4.with(X_ROT_270), 2),
                                new Weighted<>(variant7.with(X_ROT_270), 1),
                                new Weighted<>(variant5.with(X_ROT_270), 1),
                                new Weighted<>(variant6.with(X_ROT_270), 1)
                            )
                        )
                    )
                    .with(
                        condition().term(BlockStateProperties.DOWN, false),
                        new MultiVariant(
                            WeightedList.of(
                                new Weighted<>(variant7.with(X_ROT_90), 1),
                                new Weighted<>(variant6.with(X_ROT_90), 1),
                                new Weighted<>(variant5.with(X_ROT_90), 1),
                                new Weighted<>(variant4.with(X_ROT_90), 2)
                            )
                        )
                    )
            );
    }

    protected void createComposter() {
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(Blocks.COMPOSTER)
                    .with(plainVariant(TextureMapping.getBlockTexture(Blocks.COMPOSTER)))
                    .with(condition().term(BlockStateProperties.LEVEL_COMPOSTER, 1), plainVariant(TextureMapping.getBlockTexture(Blocks.COMPOSTER, "_contents1")))
                    .with(condition().term(BlockStateProperties.LEVEL_COMPOSTER, 2), plainVariant(TextureMapping.getBlockTexture(Blocks.COMPOSTER, "_contents2")))
                    .with(condition().term(BlockStateProperties.LEVEL_COMPOSTER, 3), plainVariant(TextureMapping.getBlockTexture(Blocks.COMPOSTER, "_contents3")))
                    .with(condition().term(BlockStateProperties.LEVEL_COMPOSTER, 4), plainVariant(TextureMapping.getBlockTexture(Blocks.COMPOSTER, "_contents4")))
                    .with(condition().term(BlockStateProperties.LEVEL_COMPOSTER, 5), plainVariant(TextureMapping.getBlockTexture(Blocks.COMPOSTER, "_contents5")))
                    .with(condition().term(BlockStateProperties.LEVEL_COMPOSTER, 6), plainVariant(TextureMapping.getBlockTexture(Blocks.COMPOSTER, "_contents6")))
                    .with(condition().term(BlockStateProperties.LEVEL_COMPOSTER, 7), plainVariant(TextureMapping.getBlockTexture(Blocks.COMPOSTER, "_contents7")))
                    .with(condition().term(BlockStateProperties.LEVEL_COMPOSTER, 8), plainVariant(TextureMapping.getBlockTexture(Blocks.COMPOSTER, "_contents_ready")))
            );
    }

    protected void createCopperBulb(Block p_377771_) {
        MultiVariant multivariant = plainVariant(ModelTemplates.CUBE_ALL.create(p_377771_, TextureMapping.cube(p_377771_), this.modelOutput));
        MultiVariant multivariant1 = plainVariant(this.createSuffixedVariant(p_377771_, "_powered", ModelTemplates.CUBE_ALL, TextureMapping::cube));
        MultiVariant multivariant2 = plainVariant(this.createSuffixedVariant(p_377771_, "_lit", ModelTemplates.CUBE_ALL, TextureMapping::cube));
        MultiVariant multivariant3 = plainVariant(this.createSuffixedVariant(p_377771_, "_lit_powered", ModelTemplates.CUBE_ALL, TextureMapping::cube));
        this.blockStateOutput.accept(createCopperBulb(p_377771_, multivariant, multivariant2, multivariant1, multivariant3));
    }

    protected static BlockModelDefinitionGenerator createCopperBulb(
        Block p_376664_, MultiVariant p_391523_, MultiVariant p_393528_, MultiVariant p_392854_, MultiVariant p_392882_
    ) {
        return MultiVariantGenerator.dispatch(p_376664_)
            .with(PropertyDispatch.initial(BlockStateProperties.LIT, BlockStateProperties.POWERED).generate((p_389268_, p_389269_) -> {
                if (p_389268_) {
                    return p_389269_ ? p_392882_ : p_393528_;
                } else {
                    return p_389269_ ? p_392854_ : p_391523_;
                }
            }));
    }

    protected void copyCopperBulbModel(Block p_377765_, Block p_378253_) {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(p_377765_));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(p_377765_, "_powered"));
        MultiVariant multivariant2 = plainVariant(ModelLocationUtils.getModelLocation(p_377765_, "_lit"));
        MultiVariant multivariant3 = plainVariant(ModelLocationUtils.getModelLocation(p_377765_, "_lit_powered"));
        this.itemModelOutput.copy(p_377765_.asItem(), p_378253_.asItem());
        this.blockStateOutput.accept(createCopperBulb(p_378253_, multivariant, multivariant2, multivariant1, multivariant3));
    }

    protected void createAmethystCluster(Block p_376834_) {
        MultiVariant multivariant = plainVariant(ModelTemplates.CROSS.create(p_376834_, TextureMapping.cross(p_376834_), this.modelOutput));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_376834_, multivariant).with(ROTATIONS_COLUMN_WITH_FACING));
    }

    protected void createAmethystClusters() {
        this.createAmethystCluster(Blocks.SMALL_AMETHYST_BUD);
        this.createAmethystCluster(Blocks.MEDIUM_AMETHYST_BUD);
        this.createAmethystCluster(Blocks.LARGE_AMETHYST_BUD);
        this.createAmethystCluster(Blocks.AMETHYST_CLUSTER);
    }

    protected void createPointedDripstone() {
        PropertyDispatch.C2<MultiVariant, Direction, DripstoneThickness> c2 = PropertyDispatch.initial(
            BlockStateProperties.VERTICAL_DIRECTION, BlockStateProperties.DRIPSTONE_THICKNESS
        );

        for (DripstoneThickness dripstonethickness : DripstoneThickness.values()) {
            c2.select(Direction.UP, dripstonethickness, this.createPointedDripstoneVariant(Direction.UP, dripstonethickness));
        }

        for (DripstoneThickness dripstonethickness1 : DripstoneThickness.values()) {
            c2.select(Direction.DOWN, dripstonethickness1, this.createPointedDripstoneVariant(Direction.DOWN, dripstonethickness1));
        }

        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(Blocks.POINTED_DRIPSTONE).with(c2));
    }

    protected MultiVariant createPointedDripstoneVariant(Direction p_377266_, DripstoneThickness p_377993_) {
        String s = "_" + p_377266_.getSerializedName() + "_" + p_377993_.getSerializedName();
        TextureMapping texturemapping = TextureMapping.cross(TextureMapping.getBlockTexture(Blocks.POINTED_DRIPSTONE, s));
        return plainVariant(ModelTemplates.POINTED_DRIPSTONE.createWithSuffix(Blocks.POINTED_DRIPSTONE, s, texturemapping, this.modelOutput));
    }

    protected void createNyliumBlock(Block p_375546_) {
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(Blocks.NETHERRACK))
            .put(TextureSlot.TOP, TextureMapping.getBlockTexture(p_375546_))
            .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(p_375546_, "_side"));
        this.blockStateOutput.accept(createSimpleBlock(p_375546_, plainVariant(ModelTemplates.CUBE_BOTTOM_TOP.create(p_375546_, texturemapping, this.modelOutput))));
    }

    protected void createDaylightDetector() {
        ResourceLocation resourcelocation = TextureMapping.getBlockTexture(Blocks.DAYLIGHT_DETECTOR, "_side");
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.DAYLIGHT_DETECTOR, "_top"))
            .put(TextureSlot.SIDE, resourcelocation);
        TextureMapping texturemapping1 = new TextureMapping()
            .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.DAYLIGHT_DETECTOR, "_inverted_top"))
            .put(TextureSlot.SIDE, resourcelocation);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.DAYLIGHT_DETECTOR)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.INVERTED)
                            .select(false, plainVariant(ModelTemplates.DAYLIGHT_DETECTOR.create(Blocks.DAYLIGHT_DETECTOR, texturemapping, this.modelOutput)))
                            .select(
                                true,
                                plainVariant(
                                    ModelTemplates.DAYLIGHT_DETECTOR
                                        .create(ModelLocationUtils.getModelLocation(Blocks.DAYLIGHT_DETECTOR, "_inverted"), texturemapping1, this.modelOutput)
                                )
                            )
                    )
            );
    }

    protected void createRotatableColumn(Block p_378792_) {
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_378792_, plainVariant(ModelLocationUtils.getModelLocation(p_378792_))).with(ROTATIONS_COLUMN_WITH_FACING));
    }

    protected void createLightningRod() {
        Block block = Blocks.LIGHTNING_ROD;
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(block, "_on"));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(block));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(block).with(createBooleanModelDispatch(BlockStateProperties.POWERED, multivariant, multivariant1)).with(ROTATIONS_COLUMN_WITH_FACING)
            );
    }

    protected void createFarmland() {
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.DIRT, TextureMapping.getBlockTexture(Blocks.DIRT))
            .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.FARMLAND));
        TextureMapping texturemapping1 = new TextureMapping()
            .put(TextureSlot.DIRT, TextureMapping.getBlockTexture(Blocks.DIRT))
            .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.FARMLAND, "_moist"));
        MultiVariant multivariant = plainVariant(ModelTemplates.FARMLAND.create(Blocks.FARMLAND, texturemapping, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(
            ModelTemplates.FARMLAND.create(TextureMapping.getBlockTexture(Blocks.FARMLAND, "_moist"), texturemapping1, this.modelOutput)
        );
        this.blockStateOutput
            .accept(MultiVariantGenerator.dispatch(Blocks.FARMLAND).with(createEmptyOrFullDispatch(BlockStateProperties.MOISTURE, 7, multivariant1, multivariant)));
    }

    protected MultiVariant createFloorFireModels(Block p_378241_) {
        return variants(
            plainModel(
                ModelTemplates.FIRE_FLOOR.create(ModelLocationUtils.getModelLocation(p_378241_, "_floor0"), TextureMapping.fire0(p_378241_), this.modelOutput)
            ),
            plainModel(
                ModelTemplates.FIRE_FLOOR.create(ModelLocationUtils.getModelLocation(p_378241_, "_floor1"), TextureMapping.fire1(p_378241_), this.modelOutput)
            )
        );
    }

    protected MultiVariant createSideFireModels(Block p_376173_) {
        return variants(
            plainModel(
                ModelTemplates.FIRE_SIDE.create(ModelLocationUtils.getModelLocation(p_376173_, "_side0"), TextureMapping.fire0(p_376173_), this.modelOutput)
            ),
            plainModel(
                ModelTemplates.FIRE_SIDE.create(ModelLocationUtils.getModelLocation(p_376173_, "_side1"), TextureMapping.fire1(p_376173_), this.modelOutput)
            ),
            plainModel(
                ModelTemplates.FIRE_SIDE_ALT.create(ModelLocationUtils.getModelLocation(p_376173_, "_side_alt0"), TextureMapping.fire0(p_376173_), this.modelOutput)
            ),
            plainModel(
                ModelTemplates.FIRE_SIDE_ALT.create(ModelLocationUtils.getModelLocation(p_376173_, "_side_alt1"), TextureMapping.fire1(p_376173_), this.modelOutput)
            )
        );
    }

    protected MultiVariant createTopFireModels(Block p_375647_) {
        return variants(
            plainModel(ModelTemplates.FIRE_UP.create(ModelLocationUtils.getModelLocation(p_375647_, "_up0"), TextureMapping.fire0(p_375647_), this.modelOutput)),
            plainModel(ModelTemplates.FIRE_UP.create(ModelLocationUtils.getModelLocation(p_375647_, "_up1"), TextureMapping.fire1(p_375647_), this.modelOutput)),
            plainModel(
                ModelTemplates.FIRE_UP_ALT.create(ModelLocationUtils.getModelLocation(p_375647_, "_up_alt0"), TextureMapping.fire0(p_375647_), this.modelOutput)
            ),
            plainModel(
                ModelTemplates.FIRE_UP_ALT.create(ModelLocationUtils.getModelLocation(p_375647_, "_up_alt1"), TextureMapping.fire1(p_375647_), this.modelOutput)
            )
        );
    }

    protected void createFire() {
        ConditionBuilder conditionbuilder = condition()
            .term(BlockStateProperties.NORTH, false)
            .term(BlockStateProperties.EAST, false)
            .term(BlockStateProperties.SOUTH, false)
            .term(BlockStateProperties.WEST, false)
            .term(BlockStateProperties.UP, false);
        MultiVariant multivariant = this.createFloorFireModels(Blocks.FIRE);
        MultiVariant multivariant1 = this.createSideFireModels(Blocks.FIRE);
        MultiVariant multivariant2 = this.createTopFireModels(Blocks.FIRE);
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(Blocks.FIRE)
                    .with(conditionbuilder, multivariant)
                    .with(or(condition().term(BlockStateProperties.NORTH, true), conditionbuilder), multivariant1)
                    .with(or(condition().term(BlockStateProperties.EAST, true), conditionbuilder), multivariant1.with(Y_ROT_90))
                    .with(or(condition().term(BlockStateProperties.SOUTH, true), conditionbuilder), multivariant1.with(Y_ROT_180))
                    .with(or(condition().term(BlockStateProperties.WEST, true), conditionbuilder), multivariant1.with(Y_ROT_270))
                    .with(condition().term(BlockStateProperties.UP, true), multivariant2)
            );
    }

    protected void createSoulFire() {
        MultiVariant multivariant = this.createFloorFireModels(Blocks.SOUL_FIRE);
        MultiVariant multivariant1 = this.createSideFireModels(Blocks.SOUL_FIRE);
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(Blocks.SOUL_FIRE)
                    .with(multivariant)
                    .with(multivariant1)
                    .with(multivariant1.with(Y_ROT_90))
                    .with(multivariant1.with(Y_ROT_180))
                    .with(multivariant1.with(Y_ROT_270))
            );
    }

    protected void createLantern(Block p_376956_) {
        MultiVariant multivariant = plainVariant(TexturedModel.LANTERN.create(p_376956_, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(TexturedModel.HANGING_LANTERN.create(p_376956_, this.modelOutput));
        this.registerSimpleFlatItemModel(p_376956_.asItem());
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_376956_).with(createBooleanModelDispatch(BlockStateProperties.HANGING, multivariant1, multivariant)));
    }

    protected void createMuddyMangroveRoots() {
        TextureMapping texturemapping = TextureMapping.column(
            TextureMapping.getBlockTexture(Blocks.MUDDY_MANGROVE_ROOTS, "_side"), TextureMapping.getBlockTexture(Blocks.MUDDY_MANGROVE_ROOTS, "_top")
        );
        MultiVariant multivariant = plainVariant(ModelTemplates.CUBE_COLUMN.create(Blocks.MUDDY_MANGROVE_ROOTS, texturemapping, this.modelOutput));
        this.blockStateOutput.accept(createAxisAlignedPillarBlock(Blocks.MUDDY_MANGROVE_ROOTS, multivariant));
    }

    protected void createMangrovePropagule() {
        this.registerSimpleFlatItemModel(Items.MANGROVE_PROPAGULE);
        Block block = Blocks.MANGROVE_PROPAGULE;
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(block));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.MANGROVE_PROPAGULE)
                    .with(
                        PropertyDispatch.initial(MangrovePropaguleBlock.HANGING, MangrovePropaguleBlock.AGE)
                            .generate(
                                (p_389215_, p_389216_) -> p_389215_ ? plainVariant(ModelLocationUtils.getModelLocation(block, "_hanging_" + p_389216_)) : multivariant
                            )
                    )
            );
    }

    protected void createFrostedIce() {
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.FROSTED_ICE)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.AGE_3)
                            .select(0, plainVariant(this.createSuffixedVariant(Blocks.FROSTED_ICE, "_0", ModelTemplates.CUBE_ALL, TextureMapping::cube)))
                            .select(1, plainVariant(this.createSuffixedVariant(Blocks.FROSTED_ICE, "_1", ModelTemplates.CUBE_ALL, TextureMapping::cube)))
                            .select(2, plainVariant(this.createSuffixedVariant(Blocks.FROSTED_ICE, "_2", ModelTemplates.CUBE_ALL, TextureMapping::cube)))
                            .select(3, plainVariant(this.createSuffixedVariant(Blocks.FROSTED_ICE, "_3", ModelTemplates.CUBE_ALL, TextureMapping::cube)))
                    )
            );
    }

    protected void createGrassBlocks() {
        ResourceLocation resourcelocation = TextureMapping.getBlockTexture(Blocks.DIRT);
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.BOTTOM, resourcelocation)
            .copyForced(TextureSlot.BOTTOM, TextureSlot.PARTICLE)
            .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.GRASS_BLOCK, "_top"))
            .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.GRASS_BLOCK, "_snow"));
        MultiVariant multivariant = plainVariant(ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(Blocks.GRASS_BLOCK, "_snow", texturemapping, this.modelOutput));
        ResourceLocation resourcelocation1 = ModelLocationUtils.getModelLocation(Blocks.GRASS_BLOCK);
        this.createGrassLikeBlock(Blocks.GRASS_BLOCK, createRotatedVariants(plainModel(resourcelocation1)), multivariant);
        this.registerSimpleTintedItemModel(Blocks.GRASS_BLOCK, resourcelocation1, new GrassColorSource());
        MultiVariant multivariant1 = createRotatedVariants(
            plainModel(
                TexturedModel.CUBE_TOP_BOTTOM
                    .get(Blocks.MYCELIUM)
                    .updateTextures(p_376559_ -> p_376559_.put(TextureSlot.BOTTOM, resourcelocation))
                    .create(Blocks.MYCELIUM, this.modelOutput)
            )
        );
        this.createGrassLikeBlock(Blocks.MYCELIUM, multivariant1, multivariant);
        MultiVariant multivariant2 = createRotatedVariants(
            plainModel(
                TexturedModel.CUBE_TOP_BOTTOM
                    .get(Blocks.PODZOL)
                    .updateTextures(p_378353_ -> p_378353_.put(TextureSlot.BOTTOM, resourcelocation))
                    .create(Blocks.PODZOL, this.modelOutput)
            )
        );
        this.createGrassLikeBlock(Blocks.PODZOL, multivariant2, multivariant);
    }

    protected void createGrassLikeBlock(Block p_378702_, MultiVariant p_392736_, MultiVariant p_391351_) {
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_378702_)
                    .with(PropertyDispatch.initial(BlockStateProperties.SNOWY).select(true, p_391351_).select(false, p_392736_))
            );
    }

    protected void createCocoa() {
        this.registerSimpleFlatItemModel(Items.COCOA_BEANS);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.COCOA)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.AGE_2)
                            .select(0, plainVariant(ModelLocationUtils.getModelLocation(Blocks.COCOA, "_stage0")))
                            .select(1, plainVariant(ModelLocationUtils.getModelLocation(Blocks.COCOA, "_stage1")))
                            .select(2, plainVariant(ModelLocationUtils.getModelLocation(Blocks.COCOA, "_stage2")))
                    )
                    .with(ROTATION_HORIZONTAL_FACING_ALT)
            );
    }

    protected void createDirtPath() {
        Variant variant = plainModel(ModelLocationUtils.getModelLocation(Blocks.DIRT_PATH));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(Blocks.DIRT_PATH, createRotatedVariants(variant)));
    }

    protected void createWeightedPressurePlate(Block p_376071_, Block p_375591_) {
        TextureMapping texturemapping = TextureMapping.defaultTexture(p_375591_);
        MultiVariant multivariant = plainVariant(ModelTemplates.PRESSURE_PLATE_UP.create(p_376071_, texturemapping, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.PRESSURE_PLATE_DOWN.create(p_376071_, texturemapping, this.modelOutput));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_376071_).with(createEmptyOrFullDispatch(BlockStateProperties.POWER, 1, multivariant1, multivariant)));
    }

    protected void createHopper() {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.HOPPER));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.HOPPER, "_side"));
        this.registerSimpleFlatItemModel(Items.HOPPER);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.HOPPER)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.FACING_HOPPER)
                            .select(Direction.DOWN, multivariant)
                            .select(Direction.NORTH, multivariant1)
                            .select(Direction.EAST, multivariant1.with(Y_ROT_90))
                            .select(Direction.SOUTH, multivariant1.with(Y_ROT_180))
                            .select(Direction.WEST, multivariant1.with(Y_ROT_270))
                    )
            );
    }

    protected void copyModel(Block p_376488_, Block p_376633_) {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(p_376488_));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_376633_, multivariant));
        this.itemModelOutput.copy(p_376488_.asItem(), p_376633_.asItem());
    }

    protected void createIronBars() {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.IRON_BARS, "_post_ends"));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.IRON_BARS, "_post"));
        MultiVariant multivariant2 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.IRON_BARS, "_cap"));
        MultiVariant multivariant3 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.IRON_BARS, "_cap_alt"));
        MultiVariant multivariant4 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.IRON_BARS, "_side"));
        MultiVariant multivariant5 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.IRON_BARS, "_side_alt"));
        this.blockStateOutput
            .accept(
                MultiPartGenerator.multiPart(Blocks.IRON_BARS)
                    .with(multivariant)
                    .with(
                        condition()
                            .term(BlockStateProperties.NORTH, false)
                            .term(BlockStateProperties.EAST, false)
                            .term(BlockStateProperties.SOUTH, false)
                            .term(BlockStateProperties.WEST, false),
                        multivariant1
                    )
                    .with(
                        condition()
                            .term(BlockStateProperties.NORTH, true)
                            .term(BlockStateProperties.EAST, false)
                            .term(BlockStateProperties.SOUTH, false)
                            .term(BlockStateProperties.WEST, false),
                        multivariant2
                    )
                    .with(
                        condition()
                            .term(BlockStateProperties.NORTH, false)
                            .term(BlockStateProperties.EAST, true)
                            .term(BlockStateProperties.SOUTH, false)
                            .term(BlockStateProperties.WEST, false),
                        multivariant2.with(Y_ROT_90)
                    )
                    .with(
                        condition()
                            .term(BlockStateProperties.NORTH, false)
                            .term(BlockStateProperties.EAST, false)
                            .term(BlockStateProperties.SOUTH, true)
                            .term(BlockStateProperties.WEST, false),
                        multivariant3
                    )
                    .with(
                        condition()
                            .term(BlockStateProperties.NORTH, false)
                            .term(BlockStateProperties.EAST, false)
                            .term(BlockStateProperties.SOUTH, false)
                            .term(BlockStateProperties.WEST, true),
                        multivariant3.with(Y_ROT_90)
                    )
                    .with(condition().term(BlockStateProperties.NORTH, true), multivariant4)
                    .with(condition().term(BlockStateProperties.EAST, true), multivariant4.with(Y_ROT_90))
                    .with(condition().term(BlockStateProperties.SOUTH, true), multivariant5)
                    .with(condition().term(BlockStateProperties.WEST, true), multivariant5.with(Y_ROT_90))
            );
        this.registerSimpleFlatItemModel(Blocks.IRON_BARS);
    }

    protected void createNonTemplateHorizontalBlock(Block p_375983_) {
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_375983_, plainVariant(ModelLocationUtils.getModelLocation(p_375983_))).with(ROTATION_HORIZONTAL_FACING));
    }

    protected void createLever() {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.LEVER));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.LEVER, "_on"));
        this.registerSimpleFlatItemModel(Blocks.LEVER);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.LEVER)
                    .with(createBooleanModelDispatch(BlockStateProperties.POWERED, multivariant, multivariant1))
                    .with(
                        PropertyDispatch.modify(BlockStateProperties.ATTACH_FACE, BlockStateProperties.HORIZONTAL_FACING)
                            .select(AttachFace.CEILING, Direction.NORTH, X_ROT_180.then(Y_ROT_180))
                            .select(AttachFace.CEILING, Direction.EAST, X_ROT_180.then(Y_ROT_270))
                            .select(AttachFace.CEILING, Direction.SOUTH, X_ROT_180)
                            .select(AttachFace.CEILING, Direction.WEST, X_ROT_180.then(Y_ROT_90))
                            .select(AttachFace.FLOOR, Direction.NORTH, NOP)
                            .select(AttachFace.FLOOR, Direction.EAST, Y_ROT_90)
                            .select(AttachFace.FLOOR, Direction.SOUTH, Y_ROT_180)
                            .select(AttachFace.FLOOR, Direction.WEST, Y_ROT_270)
                            .select(AttachFace.WALL, Direction.NORTH, X_ROT_90)
                            .select(AttachFace.WALL, Direction.EAST, X_ROT_90.then(Y_ROT_90))
                            .select(AttachFace.WALL, Direction.SOUTH, X_ROT_90.then(Y_ROT_180))
                            .select(AttachFace.WALL, Direction.WEST, X_ROT_90.then(Y_ROT_270))
                    )
            );
    }

    protected void createLilyPad() {
        ResourceLocation resourcelocation = this.createFlatItemModelWithBlockTexture(Items.LILY_PAD, Blocks.LILY_PAD);
        this.registerSimpleTintedItemModel(Blocks.LILY_PAD, resourcelocation, ItemModelUtils.constantTint(-9321636));
        Variant variant = plainModel(ModelLocationUtils.getModelLocation(Blocks.LILY_PAD));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(Blocks.LILY_PAD, createRotatedVariants(variant)));
    }

    protected void createFrogspawnBlock() {
        this.registerSimpleFlatItemModel(Blocks.FROGSPAWN);
        this.blockStateOutput.accept(createSimpleBlock(Blocks.FROGSPAWN, plainVariant(ModelLocationUtils.getModelLocation(Blocks.FROGSPAWN))));
    }

    protected void createNetherPortalBlock() {
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.NETHER_PORTAL)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.HORIZONTAL_AXIS)
                            .select(Direction.Axis.X, plainVariant(ModelLocationUtils.getModelLocation(Blocks.NETHER_PORTAL, "_ns")))
                            .select(Direction.Axis.Z, plainVariant(ModelLocationUtils.getModelLocation(Blocks.NETHER_PORTAL, "_ew")))
                    )
            );
    }

    protected void createNetherrack() {
        Variant variant = plainModel(TexturedModel.CUBE.create(Blocks.NETHERRACK, this.modelOutput));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(
                    Blocks.NETHERRACK,
                    variants(
                        variant,
                        variant.with(X_ROT_90),
                        variant.with(X_ROT_180),
                        variant.with(X_ROT_270),
                        variant.with(Y_ROT_90),
                        variant.with(Y_ROT_90.then(X_ROT_90)),
                        variant.with(Y_ROT_90.then(X_ROT_180)),
                        variant.with(Y_ROT_90.then(X_ROT_270)),
                        variant.with(Y_ROT_180),
                        variant.with(Y_ROT_180.then(X_ROT_90)),
                        variant.with(Y_ROT_180.then(X_ROT_180)),
                        variant.with(Y_ROT_180.then(X_ROT_270)),
                        variant.with(Y_ROT_270),
                        variant.with(Y_ROT_270.then(X_ROT_90)),
                        variant.with(Y_ROT_270.then(X_ROT_180)),
                        variant.with(Y_ROT_270.then(X_ROT_270))
                    )
                )
            );
    }

    protected void createObserver() {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.OBSERVER));
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.OBSERVER, "_on"));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.OBSERVER)
                    .with(createBooleanModelDispatch(BlockStateProperties.POWERED, multivariant1, multivariant))
                    .with(ROTATION_FACING)
            );
    }

    protected void createPistons() {
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(Blocks.PISTON, "_bottom"))
            .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.PISTON, "_side"));
        ResourceLocation resourcelocation = TextureMapping.getBlockTexture(Blocks.PISTON, "_top_sticky");
        ResourceLocation resourcelocation1 = TextureMapping.getBlockTexture(Blocks.PISTON, "_top");
        TextureMapping texturemapping1 = texturemapping.copyAndUpdate(TextureSlot.PLATFORM, resourcelocation);
        TextureMapping texturemapping2 = texturemapping.copyAndUpdate(TextureSlot.PLATFORM, resourcelocation1);
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(Blocks.PISTON, "_base"));
        this.createPistonVariant(Blocks.PISTON, multivariant, texturemapping2);
        this.createPistonVariant(Blocks.STICKY_PISTON, multivariant, texturemapping1);
        ResourceLocation resourcelocation2 = ModelTemplates.CUBE_BOTTOM_TOP
            .createWithSuffix(Blocks.PISTON, "_inventory", texturemapping.copyAndUpdate(TextureSlot.TOP, resourcelocation1), this.modelOutput);
        ResourceLocation resourcelocation3 = ModelTemplates.CUBE_BOTTOM_TOP
            .createWithSuffix(Blocks.STICKY_PISTON, "_inventory", texturemapping.copyAndUpdate(TextureSlot.TOP, resourcelocation), this.modelOutput);
        this.registerSimpleItemModel(Blocks.PISTON, resourcelocation2);
        this.registerSimpleItemModel(Blocks.STICKY_PISTON, resourcelocation3);
    }

    protected void createPistonVariant(Block p_377085_, MultiVariant p_393911_, TextureMapping p_377851_) {
        MultiVariant multivariant = plainVariant(ModelTemplates.PISTON.create(p_377085_, p_377851_, this.modelOutput));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_377085_).with(createBooleanModelDispatch(BlockStateProperties.EXTENDED, p_393911_, multivariant)).with(ROTATION_FACING)
            );
    }

    protected void createPistonHeads() {
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.UNSTICKY, TextureMapping.getBlockTexture(Blocks.PISTON, "_top"))
            .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.PISTON, "_side"));
        TextureMapping texturemapping1 = texturemapping.copyAndUpdate(TextureSlot.PLATFORM, TextureMapping.getBlockTexture(Blocks.PISTON, "_top_sticky"));
        TextureMapping texturemapping2 = texturemapping.copyAndUpdate(TextureSlot.PLATFORM, TextureMapping.getBlockTexture(Blocks.PISTON, "_top"));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.PISTON_HEAD)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.SHORT, BlockStateProperties.PISTON_TYPE)
                            .select(
                                false,
                                PistonType.DEFAULT,
                                plainVariant(ModelTemplates.PISTON_HEAD.createWithSuffix(Blocks.PISTON, "_head", texturemapping2, this.modelOutput))
                            )
                            .select(
                                false,
                                PistonType.STICKY,
                                plainVariant(ModelTemplates.PISTON_HEAD.createWithSuffix(Blocks.PISTON, "_head_sticky", texturemapping1, this.modelOutput))
                            )
                            .select(
                                true,
                                PistonType.DEFAULT,
                                plainVariant(ModelTemplates.PISTON_HEAD_SHORT.createWithSuffix(Blocks.PISTON, "_head_short", texturemapping2, this.modelOutput))
                            )
                            .select(
                                true,
                                PistonType.STICKY,
                                plainVariant(ModelTemplates.PISTON_HEAD_SHORT.createWithSuffix(Blocks.PISTON, "_head_short_sticky", texturemapping1, this.modelOutput))
                            )
                    )
                    .with(ROTATION_FACING)
            );
    }

    protected void createTrialSpawner() {
        Block block = Blocks.TRIAL_SPAWNER;
        TextureMapping texturemapping = TextureMapping.trialSpawner(block, "_side_inactive", "_top_inactive");
        TextureMapping texturemapping1 = TextureMapping.trialSpawner(block, "_side_active", "_top_active");
        TextureMapping texturemapping2 = TextureMapping.trialSpawner(block, "_side_active", "_top_ejecting_reward");
        TextureMapping texturemapping3 = TextureMapping.trialSpawner(block, "_side_inactive_ominous", "_top_inactive_ominous");
        TextureMapping texturemapping4 = TextureMapping.trialSpawner(block, "_side_active_ominous", "_top_active_ominous");
        TextureMapping texturemapping5 = TextureMapping.trialSpawner(block, "_side_active_ominous", "_top_ejecting_reward_ominous");
        ResourceLocation resourcelocation = ModelTemplates.CUBE_BOTTOM_TOP_INNER_FACES.create(block, texturemapping, this.modelOutput);
        MultiVariant multivariant = plainVariant(resourcelocation);
        MultiVariant multivariant1 = plainVariant(ModelTemplates.CUBE_BOTTOM_TOP_INNER_FACES.createWithSuffix(block, "_active", texturemapping1, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.CUBE_BOTTOM_TOP_INNER_FACES.createWithSuffix(block, "_ejecting_reward", texturemapping2, this.modelOutput));
        MultiVariant multivariant3 = plainVariant(ModelTemplates.CUBE_BOTTOM_TOP_INNER_FACES.createWithSuffix(block, "_inactive_ominous", texturemapping3, this.modelOutput));
        MultiVariant multivariant4 = plainVariant(ModelTemplates.CUBE_BOTTOM_TOP_INNER_FACES.createWithSuffix(block, "_active_ominous", texturemapping4, this.modelOutput));
        MultiVariant multivariant5 = plainVariant(ModelTemplates.CUBE_BOTTOM_TOP_INNER_FACES.createWithSuffix(block, "_ejecting_reward_ominous", texturemapping5, this.modelOutput));
        this.registerSimpleItemModel(block, resourcelocation);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(block)
                    .with(PropertyDispatch.initial(BlockStateProperties.TRIAL_SPAWNER_STATE, BlockStateProperties.OMINOUS).generate((p_389276_, p_389277_) -> {
                        return switch (p_389276_) {
                            case INACTIVE, COOLDOWN -> p_389277_ ? multivariant3 : multivariant;
                            case WAITING_FOR_PLAYERS, ACTIVE, WAITING_FOR_REWARD_EJECTION -> p_389277_ ? multivariant4 : multivariant1;
                            case EJECTING_REWARD -> p_389277_ ? multivariant5 : multivariant2;
                        };
                    }))
            );
    }

    protected void createVault() {
        Block block = Blocks.VAULT;
        TextureMapping texturemapping = TextureMapping.vault(block, "_front_off", "_side_off", "_top", "_bottom");
        TextureMapping texturemapping1 = TextureMapping.vault(block, "_front_on", "_side_on", "_top", "_bottom");
        TextureMapping texturemapping2 = TextureMapping.vault(block, "_front_ejecting", "_side_on", "_top", "_bottom");
        TextureMapping texturemapping3 = TextureMapping.vault(block, "_front_ejecting", "_side_on", "_top_ejecting", "_bottom");
        ResourceLocation resourcelocation = ModelTemplates.VAULT.create(block, texturemapping, this.modelOutput);
        MultiVariant multivariant = plainVariant(resourcelocation);
        MultiVariant multivariant1 = plainVariant(ModelTemplates.VAULT.createWithSuffix(block, "_active", texturemapping1, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.VAULT.createWithSuffix(block, "_unlocking", texturemapping2, this.modelOutput));
        MultiVariant multivariant3 = plainVariant(ModelTemplates.VAULT.createWithSuffix(block, "_ejecting_reward", texturemapping3, this.modelOutput));
        TextureMapping texturemapping4 = TextureMapping.vault(block, "_front_off_ominous", "_side_off_ominous", "_top_ominous", "_bottom_ominous");
        TextureMapping texturemapping5 = TextureMapping.vault(block, "_front_on_ominous", "_side_on_ominous", "_top_ominous", "_bottom_ominous");
        TextureMapping texturemapping6 = TextureMapping.vault(block, "_front_ejecting_ominous", "_side_on_ominous", "_top_ominous", "_bottom_ominous");
        TextureMapping texturemapping7 = TextureMapping.vault(
            block, "_front_ejecting_ominous", "_side_on_ominous", "_top_ejecting_ominous", "_bottom_ominous"
        );
        MultiVariant multivariant4 = plainVariant(ModelTemplates.VAULT.createWithSuffix(block, "_ominous", texturemapping4, this.modelOutput));
        MultiVariant multivariant5 = plainVariant(ModelTemplates.VAULT.createWithSuffix(block, "_active_ominous", texturemapping5, this.modelOutput));
        MultiVariant multivariant6 = plainVariant(ModelTemplates.VAULT.createWithSuffix(block, "_unlocking_ominous", texturemapping6, this.modelOutput));
        MultiVariant multivariant7 = plainVariant(ModelTemplates.VAULT.createWithSuffix(block, "_ejecting_reward_ominous", texturemapping7, this.modelOutput));
        this.registerSimpleItemModel(block, resourcelocation);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(block)
                    .with(PropertyDispatch.initial(VaultBlock.STATE, VaultBlock.OMINOUS).generate((p_389168_, p_389169_) -> {
                        return switch (p_389168_) {
                            case INACTIVE -> p_389169_ ? multivariant4 : multivariant;
                            case ACTIVE -> p_389169_ ? multivariant5 : multivariant1;
                            case UNLOCKING -> p_389169_ ? multivariant6 : multivariant2;
                            case EJECTING -> p_389169_ ? multivariant7 : multivariant3;
                        };
                    }))
                    .with(ROTATION_HORIZONTAL_FACING)
            );
    }

    protected void createSculkSensor() {
        ResourceLocation resourcelocation = ModelLocationUtils.getModelLocation(Blocks.SCULK_SENSOR, "_inactive");
        MultiVariant multivariant = plainVariant(resourcelocation);
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.SCULK_SENSOR, "_active"));
        this.registerSimpleItemModel(Blocks.SCULK_SENSOR, resourcelocation);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.SCULK_SENSOR)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.SCULK_SENSOR_PHASE)
                            .generate(
                                p_389181_ -> p_389181_ != SculkSensorPhase.ACTIVE && p_389181_ != SculkSensorPhase.COOLDOWN ? multivariant : multivariant1
                            )
                    )
            );
    }

    protected void createCalibratedSculkSensor() {
        ResourceLocation resourcelocation = ModelLocationUtils.getModelLocation(Blocks.CALIBRATED_SCULK_SENSOR, "_inactive");
        MultiVariant multivariant = plainVariant(resourcelocation);
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.CALIBRATED_SCULK_SENSOR, "_active"));
        this.registerSimpleItemModel(Blocks.CALIBRATED_SCULK_SENSOR, resourcelocation);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.CALIBRATED_SCULK_SENSOR)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.SCULK_SENSOR_PHASE)
                            .generate(
                                p_389235_ -> p_389235_ != SculkSensorPhase.ACTIVE && p_389235_ != SculkSensorPhase.COOLDOWN ? multivariant : multivariant1
                            )
                    )
                    .with(ROTATION_HORIZONTAL_FACING)
            );
    }

    protected void createSculkShrieker() {
        ResourceLocation resourcelocation = ModelTemplates.SCULK_SHRIEKER.create(Blocks.SCULK_SHRIEKER, TextureMapping.sculkShrieker(false), this.modelOutput);
        MultiVariant multivariant = plainVariant(resourcelocation);
        MultiVariant multivariant1 = plainVariant(
            ModelTemplates.SCULK_SHRIEKER.createWithSuffix(Blocks.SCULK_SHRIEKER, "_can_summon", TextureMapping.sculkShrieker(true), this.modelOutput)
        );
        this.registerSimpleItemModel(Blocks.SCULK_SHRIEKER, resourcelocation);
        this.blockStateOutput
            .accept(MultiVariantGenerator.dispatch(Blocks.SCULK_SHRIEKER).with(createBooleanModelDispatch(BlockStateProperties.CAN_SUMMON, multivariant1, multivariant)));
    }

    protected void createScaffolding() {
        ResourceLocation resourcelocation = ModelLocationUtils.getModelLocation(Blocks.SCAFFOLDING, "_stable");
        MultiVariant multivariant = plainVariant(resourcelocation);
        MultiVariant multivariant1 = plainVariant(ModelLocationUtils.getModelLocation(Blocks.SCAFFOLDING, "_unstable"));
        this.registerSimpleItemModel(Blocks.SCAFFOLDING, resourcelocation);
        this.blockStateOutput
            .accept(MultiVariantGenerator.dispatch(Blocks.SCAFFOLDING).with(createBooleanModelDispatch(BlockStateProperties.BOTTOM, multivariant1, multivariant)));
    }

    protected void createCaveVines() {
        MultiVariant multivariant = plainVariant(this.createSuffixedVariant(Blocks.CAVE_VINES, "", ModelTemplates.CROSS, TextureMapping::cross));
        MultiVariant multivariant1 = plainVariant(this.createSuffixedVariant(Blocks.CAVE_VINES, "_lit", ModelTemplates.CROSS, TextureMapping::cross));
        this.blockStateOutput
            .accept(MultiVariantGenerator.dispatch(Blocks.CAVE_VINES).with(createBooleanModelDispatch(BlockStateProperties.BERRIES, multivariant1, multivariant)));
        MultiVariant multivariant2 = plainVariant(this.createSuffixedVariant(Blocks.CAVE_VINES_PLANT, "", ModelTemplates.CROSS, TextureMapping::cross));
        MultiVariant multivariant3 = plainVariant(this.createSuffixedVariant(Blocks.CAVE_VINES_PLANT, "_lit", ModelTemplates.CROSS, TextureMapping::cross));
        this.blockStateOutput
            .accept(MultiVariantGenerator.dispatch(Blocks.CAVE_VINES_PLANT).with(createBooleanModelDispatch(BlockStateProperties.BERRIES, multivariant3, multivariant2)));
    }

    protected void createRedstoneLamp() {
        MultiVariant multivariant = plainVariant(TexturedModel.CUBE.create(Blocks.REDSTONE_LAMP, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(this.createSuffixedVariant(Blocks.REDSTONE_LAMP, "_on", ModelTemplates.CUBE_ALL, TextureMapping::cube));
        this.blockStateOutput
            .accept(MultiVariantGenerator.dispatch(Blocks.REDSTONE_LAMP).with(createBooleanModelDispatch(BlockStateProperties.LIT, multivariant1, multivariant)));
    }

    protected void createNormalTorch(Block p_377444_, Block p_377353_) {
        TextureMapping texturemapping = TextureMapping.torch(p_377444_);
        this.blockStateOutput.accept(createSimpleBlock(p_377444_, plainVariant(ModelTemplates.TORCH.create(p_377444_, texturemapping, this.modelOutput))));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_377353_, plainVariant(ModelTemplates.WALL_TORCH.create(p_377353_, texturemapping, this.modelOutput)))
                    .with(ROTATION_TORCH)
            );
        this.registerSimpleFlatItemModel(p_377444_);
    }

    protected void createRedstoneTorch() {
        TextureMapping texturemapping = TextureMapping.torch(Blocks.REDSTONE_TORCH);
        TextureMapping texturemapping1 = TextureMapping.torch(TextureMapping.getBlockTexture(Blocks.REDSTONE_TORCH, "_off"));
        MultiVariant multivariant = plainVariant(ModelTemplates.REDSTONE_TORCH.create(Blocks.REDSTONE_TORCH, texturemapping, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.TORCH_UNLIT.createWithSuffix(Blocks.REDSTONE_TORCH, "_off", texturemapping1, this.modelOutput));
        this.blockStateOutput
            .accept(MultiVariantGenerator.dispatch(Blocks.REDSTONE_TORCH).with(createBooleanModelDispatch(BlockStateProperties.LIT, multivariant, multivariant1)));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.REDSTONE_WALL_TORCH.create(Blocks.REDSTONE_WALL_TORCH, texturemapping, this.modelOutput));
        MultiVariant multivariant3 = plainVariant(ModelTemplates.WALL_TORCH_UNLIT.createWithSuffix(Blocks.REDSTONE_WALL_TORCH, "_off", texturemapping1, this.modelOutput));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.REDSTONE_WALL_TORCH)
                    .with(createBooleanModelDispatch(BlockStateProperties.LIT, multivariant2, multivariant3))
                    .with(ROTATION_TORCH)
            );
        this.registerSimpleFlatItemModel(Blocks.REDSTONE_TORCH);
    }

    protected void createRepeater() {
        this.registerSimpleFlatItemModel(Items.REPEATER);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.REPEATER)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.DELAY, BlockStateProperties.LOCKED, BlockStateProperties.POWERED)
                            .generate((p_389193_, p_389194_, p_389195_) -> {
                                StringBuilder stringbuilder = new StringBuilder();
                                stringbuilder.append('_').append(p_389193_).append("tick");
                                if (p_389195_) {
                                    stringbuilder.append("_on");
                                }

                                if (p_389194_) {
                                    stringbuilder.append("_locked");
                                }

                                return plainVariant(TextureMapping.getBlockTexture(Blocks.REPEATER, stringbuilder.toString()));
                            })
                    )
                    .with(ROTATION_HORIZONTAL_FACING_ALT)
            );
    }

    protected void createSeaPickle() {
        this.registerSimpleFlatItemModel(Items.SEA_PICKLE);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.SEA_PICKLE)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.PICKLES, BlockStateProperties.WATERLOGGED)
                            .select(1, false, createRotatedVariants(plainModel(ModelLocationUtils.decorateBlockModelLocation("dead_sea_pickle"))))
                            .select(2, false, createRotatedVariants(plainModel(ModelLocationUtils.decorateBlockModelLocation("two_dead_sea_pickles"))))
                            .select(3, false, createRotatedVariants(plainModel(ModelLocationUtils.decorateBlockModelLocation("three_dead_sea_pickles"))))
                            .select(4, false, createRotatedVariants(plainModel(ModelLocationUtils.decorateBlockModelLocation("four_dead_sea_pickles"))))
                            .select(1, true, createRotatedVariants(plainModel(ModelLocationUtils.decorateBlockModelLocation("sea_pickle"))))
                            .select(2, true, createRotatedVariants(plainModel(ModelLocationUtils.decorateBlockModelLocation("two_sea_pickles"))))
                            .select(3, true, createRotatedVariants(plainModel(ModelLocationUtils.decorateBlockModelLocation("three_sea_pickles"))))
                            .select(4, true, createRotatedVariants(plainModel(ModelLocationUtils.decorateBlockModelLocation("four_sea_pickles"))))
                    )
            );
    }

    protected void createSnowBlocks() {
        TextureMapping texturemapping = TextureMapping.cube(Blocks.SNOW);
        MultiVariant multivariant = plainVariant(ModelTemplates.CUBE_ALL.create(Blocks.SNOW_BLOCK, texturemapping, this.modelOutput));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.SNOW)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.LAYERS)
                            .generate(
                                p_389198_ -> p_389198_ < 8 ? plainVariant(ModelLocationUtils.getModelLocation(Blocks.SNOW, "_height" + p_389198_ * 2)) : multivariant
                            )
                    )
            );
        this.registerSimpleItemModel(Blocks.SNOW, ModelLocationUtils.getModelLocation(Blocks.SNOW, "_height2"));
        this.blockStateOutput.accept(createSimpleBlock(Blocks.SNOW_BLOCK, multivariant));
    }

    protected void createStonecutter() {
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(Blocks.STONECUTTER, plainVariant(ModelLocationUtils.getModelLocation(Blocks.STONECUTTER))).with(ROTATION_HORIZONTAL_FACING));
    }

    protected void createStructureBlock() {
        ResourceLocation resourcelocation = TexturedModel.CUBE.create(Blocks.STRUCTURE_BLOCK, this.modelOutput);
        this.registerSimpleItemModel(Blocks.STRUCTURE_BLOCK, resourcelocation);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.STRUCTURE_BLOCK)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.STRUCTUREBLOCK_MODE)
                            .generate(
                                p_389255_ -> plainVariant(
                                    this.createSuffixedVariant(Blocks.STRUCTURE_BLOCK, "_" + p_389255_.getSerializedName(), ModelTemplates.CUBE_ALL, TextureMapping::cube)
                                )
                            )
                    )
            );
    }

    protected void createTestBlock() {
        Map<TestBlockMode, ResourceLocation> map = new HashMap<>();

        for (TestBlockMode testblockmode : TestBlockMode.values()) {
            map.put(testblockmode, this.createSuffixedVariant(Blocks.TEST_BLOCK, "_" + testblockmode.getSerializedName(), ModelTemplates.CUBE_ALL, TextureMapping::cube));
        }

        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.TEST_BLOCK)
                    .with(PropertyDispatch.initial(BlockStateProperties.TEST_BLOCK_MODE).generate(p_389258_ -> plainVariant(map.get(p_389258_))))
            );
        this.itemModelOutput
            .accept(
                Items.TEST_BLOCK,
                ItemModelUtils.selectBlockItemProperty(
                    TestBlock.MODE,
                    ItemModelUtils.plainModel(map.get(TestBlockMode.START)),
                    Map.of(
                        TestBlockMode.FAIL,
                        ItemModelUtils.plainModel(map.get(TestBlockMode.FAIL)),
                        TestBlockMode.LOG,
                        ItemModelUtils.plainModel(map.get(TestBlockMode.LOG)),
                        TestBlockMode.ACCEPT,
                        ItemModelUtils.plainModel(map.get(TestBlockMode.ACCEPT))
                    )
                )
            );
    }

    protected void createSweetBerryBush() {
        this.registerSimpleFlatItemModel(Items.SWEET_BERRIES);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.SWEET_BERRY_BUSH)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.AGE_3)
                            .generate(
                                p_389159_ -> plainVariant(
                                    this.createSuffixedVariant(Blocks.SWEET_BERRY_BUSH, "_stage" + p_389159_, ModelTemplates.CROSS, TextureMapping::cross)
                                )
                            )
                    )
            );
    }

    protected void createTripwire() {
        this.registerSimpleFlatItemModel(Items.STRING);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.TRIPWIRE)
                    .with(
                        PropertyDispatch.initial(
                                BlockStateProperties.ATTACHED,
                                BlockStateProperties.EAST,
                                BlockStateProperties.NORTH,
                                BlockStateProperties.SOUTH,
                                BlockStateProperties.WEST
                            )
                            .select(false, false, false, false, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_ns")))
                            .select(false, true, false, false, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_n")).with(Y_ROT_90))
                            .select(false, false, true, false, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_n")))
                            .select(false, false, false, true, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_n")).with(Y_ROT_180))
                            .select(false, false, false, false, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_n")).with(Y_ROT_270))
                            .select(false, true, true, false, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_ne")))
                            .select(false, true, false, true, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_ne")).with(Y_ROT_90))
                            .select(false, false, false, true, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_ne")).with(Y_ROT_180))
                            .select(false, false, true, false, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_ne")).with(Y_ROT_270))
                            .select(false, false, true, true, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_ns")))
                            .select(false, true, false, false, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_ns")).with(Y_ROT_90))
                            .select(false, true, true, true, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_nse")))
                            .select(false, true, false, true, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_nse")).with(Y_ROT_90))
                            .select(false, false, true, true, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_nse")).with(Y_ROT_180))
                            .select(false, true, true, false, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_nse")).with(Y_ROT_270))
                            .select(false, true, true, true, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_nsew")))
                            .select(true, false, false, false, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_ns")))
                            .select(true, false, true, false, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_n")))
                            .select(
                                true, false, false, true, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_n")).with(Y_ROT_180)
                            )
                            .select(
                                true, true, false, false, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_n")).with(Y_ROT_90)
                            )
                            .select(
                                true, false, false, false, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_n")).with(Y_ROT_270)
                            )
                            .select(true, true, true, false, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_ne")))
                            .select(
                                true, true, false, true, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_ne")).with(Y_ROT_90)
                            )
                            .select(
                                true, false, false, true, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_ne")).with(Y_ROT_180)
                            )
                            .select(
                                true, false, true, false, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_ne")).with(Y_ROT_270)
                            )
                            .select(true, false, true, true, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_ns")))
                            .select(
                                true, true, false, false, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_ns")).with(Y_ROT_90)
                            )
                            .select(true, true, true, true, false, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_nse")))
                            .select(
                                true, true, false, true, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_nse")).with(Y_ROT_90)
                            )
                            .select(
                                true, false, true, true, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_nse")).with(Y_ROT_180)
                            )
                            .select(
                                true, true, true, false, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_nse")).with(Y_ROT_270)
                            )
                            .select(true, true, true, true, true, plainVariant(ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE, "_attached_nsew")))
                    )
            );
    }

    protected void createTripwireHook() {
        this.registerSimpleFlatItemModel(Blocks.TRIPWIRE_HOOK);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.TRIPWIRE_HOOK)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.ATTACHED, BlockStateProperties.POWERED)
                            .generate(
                                (p_389253_, p_389254_) -> plainVariant(
                                    ModelLocationUtils.getModelLocation(Blocks.TRIPWIRE_HOOK, (p_389253_ ? "_attached" : "") + (p_389254_ ? "_on" : ""))
                                )
                            )
                    )
                    .with(ROTATION_HORIZONTAL_FACING)
            );
    }

    protected Variant createTurtleEggModel(int p_375779_, String p_376950_, TextureMapping p_378641_) {
        return switch (p_375779_) {
            case 1 -> plainModel(ModelTemplates.TURTLE_EGG.create(ModelLocationUtils.decorateBlockModelLocation(p_376950_ + "turtle_egg"), p_378641_, this.modelOutput));
            case 2 -> plainModel(ModelTemplates.TWO_TURTLE_EGGS.create(ModelLocationUtils.decorateBlockModelLocation("two_" + p_376950_ + "turtle_eggs"), p_378641_, this.modelOutput));
            case 3 -> plainModel(
                ModelTemplates.THREE_TURTLE_EGGS.create(ModelLocationUtils.decorateBlockModelLocation("three_" + p_376950_ + "turtle_eggs"), p_378641_, this.modelOutput)
            );
            case 4 -> plainModel(
                ModelTemplates.FOUR_TURTLE_EGGS.create(ModelLocationUtils.decorateBlockModelLocation("four_" + p_376950_ + "turtle_eggs"), p_378641_, this.modelOutput)
            );
            default -> throw new UnsupportedOperationException();
        };
    }

    protected Variant createTurtleEggModel(int p_393968_, int p_392933_) {
        return switch (p_392933_) {
            case 0 -> this.createTurtleEggModel(p_393968_, "", TextureMapping.cube(TextureMapping.getBlockTexture(Blocks.TURTLE_EGG)));
            case 1 -> this.createTurtleEggModel(p_393968_, "slightly_cracked_", TextureMapping.cube(TextureMapping.getBlockTexture(Blocks.TURTLE_EGG, "_slightly_cracked")));
            case 2 -> this.createTurtleEggModel(p_393968_, "very_cracked_", TextureMapping.cube(TextureMapping.getBlockTexture(Blocks.TURTLE_EGG, "_very_cracked")));
            default -> throw new UnsupportedOperationException();
        };
    }

    protected void createTurtleEgg() {
        this.registerSimpleFlatItemModel(Items.TURTLE_EGG);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.TURTLE_EGG)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.EGGS, BlockStateProperties.HATCH)
                            .generate((p_389220_, p_389221_) -> createRotatedVariants(this.createTurtleEggModel(p_389220_, p_389221_)))
                    )
            );
    }

    protected void createDriedGhastBlock() {
        ResourceLocation resourcelocation = ModelLocationUtils.getModelLocation(Blocks.DRIED_GHAST, "_hydration_0");
        this.registerSimpleItemModel(Blocks.DRIED_GHAST, resourcelocation);
        Function<Integer, ResourceLocation> function = p_404808_ -> {
            String s = switch (p_404808_) {
                case 1 -> "_hydration_1";
                case 2 -> "_hydration_2";
                case 3 -> "_hydration_3";
                default -> "_hydration_0";
            };
            TextureMapping texturemapping = TextureMapping.driedGhast(s);
            return ModelTemplates.DRIED_GHAST.createWithSuffix(Blocks.DRIED_GHAST, s, texturemapping, this.modelOutput);
        };
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.DRIED_GHAST)
                    .with(PropertyDispatch.initial(DriedGhastBlock.HYDRATION_LEVEL).generate(p_404810_ -> plainVariant(function.apply(p_404810_))))
                    .with(ROTATION_HORIZONTAL_FACING)
            );
    }

    protected void createSnifferEgg() {
        this.registerSimpleFlatItemModel(Items.SNIFFER_EGG);
        this.blockStateOutput
            .accept(MultiVariantGenerator.dispatch(Blocks.SNIFFER_EGG).with(PropertyDispatch.initial(SnifferEggBlock.HATCH).generate(p_389256_ -> {
                String s = switch (p_389256_) {
                    case 1 -> "_slightly_cracked";
                    case 2 -> "_very_cracked";
                    default -> "_not_cracked";
                };
                TextureMapping texturemapping = TextureMapping.snifferEgg(s);
                return plainVariant(ModelTemplates.SNIFFER_EGG.createWithSuffix(Blocks.SNIFFER_EGG, s, texturemapping, this.modelOutput));
            })));
    }

    protected void createMultiface(Block p_377870_) {
        this.registerSimpleFlatItemModel(p_377870_);
        this.createMultifaceBlockStates(p_377870_);
    }

    protected void createMultiface(Block p_377112_, Item p_377596_) {
        this.registerSimpleFlatItemModel(p_377596_);
        this.createMultifaceBlockStates(p_377112_);
    }

    protected static <T extends Property<?>> Map<T, VariantMutator> selectMultifaceProperties(StateHolder<?, ?> p_394855_, Function<Direction, T> p_391470_) {
        Builder<T, VariantMutator> builder = ImmutableMap.builderWithExpectedSize(MULTIFACE_GENERATOR.size());
        MULTIFACE_GENERATOR.forEach((p_389189_, p_389190_) -> {
            T t = p_391470_.apply(p_389189_);
            if (p_394855_.hasProperty(t)) {
                builder.put(t, p_389190_);
            }
        });
        return builder.build();
    }

    protected void createMultifaceBlockStates(Block p_375972_) {
        Map<Property<Boolean>, VariantMutator> map = selectMultifaceProperties(p_375972_.defaultBlockState(), MultifaceBlock::getFaceProperty);
        ConditionBuilder conditionbuilder = condition();
        map.forEach((p_389177_, p_389178_) -> conditionbuilder.term((Property<Boolean>)p_389177_, false));
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(p_375972_));
        MultiPartGenerator multipartgenerator = MultiPartGenerator.multiPart(p_375972_);
        map.forEach((p_389250_, p_389251_) -> {
            multipartgenerator.with(condition().term((Property<Boolean>)p_389250_, true), multivariant.with(p_389251_));
            multipartgenerator.with(conditionbuilder, multivariant.with(p_389251_));
        });
        this.blockStateOutput.accept(multipartgenerator);
    }

    protected void createMossyCarpet(Block p_376848_) {
        Map<Property<WallSide>, VariantMutator> map = selectMultifaceProperties(p_376848_.defaultBlockState(), MossyCarpetBlock::getPropertyForFace);
        ConditionBuilder conditionbuilder = condition().term(MossyCarpetBlock.BASE, false);
        map.forEach((p_389218_, p_389219_) -> conditionbuilder.term((Property<WallSide>)p_389218_, WallSide.NONE));
        MultiVariant multivariant = plainVariant(TexturedModel.CARPET.create(p_376848_, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(
            TexturedModel.MOSSY_CARPET_SIDE
                .get(p_376848_)
                .updateTextures(p_375592_ -> p_375592_.put(TextureSlot.SIDE, TextureMapping.getBlockTexture(p_376848_, "_side_tall")))
                .createWithSuffix(p_376848_, "_side_tall", this.modelOutput)
        );
        MultiVariant multivariant2 = plainVariant(
            TexturedModel.MOSSY_CARPET_SIDE
                .get(p_376848_)
                .updateTextures(p_377296_ -> p_377296_.put(TextureSlot.SIDE, TextureMapping.getBlockTexture(p_376848_, "_side_small")))
                .createWithSuffix(p_376848_, "_side_small", this.modelOutput)
        );
        MultiPartGenerator multipartgenerator = MultiPartGenerator.multiPart(p_376848_);
        multipartgenerator.with(condition().term(MossyCarpetBlock.BASE, true), multivariant);
        multipartgenerator.with(conditionbuilder, multivariant);
        map.forEach((p_389226_, p_389227_) -> {
            multipartgenerator.with(condition().term((Property<WallSide>)p_389226_, WallSide.TALL), multivariant1.with(p_389227_));
            multipartgenerator.with(condition().term((Property<WallSide>)p_389226_, WallSide.LOW), multivariant2.with(p_389227_));
            multipartgenerator.with(conditionbuilder, multivariant1.with(p_389227_));
        });
        this.blockStateOutput.accept(multipartgenerator);
    }

    protected void createHangingMoss(Block p_378635_) {
        this.registerSimpleFlatItemModel(p_378635_);
        this.blockStateOutput
            .accept(MultiVariantGenerator.dispatch(p_378635_).with(PropertyDispatch.initial(HangingMossBlock.TIP).generate(p_389200_ -> {
                String s = p_389200_ ? "_tip" : "";
                TextureMapping texturemapping = TextureMapping.cross(TextureMapping.getBlockTexture(p_378635_, s));
                return plainVariant(BlockModelGenerators.PlantType.NOT_TINTED.getCross().createWithSuffix(p_378635_, s, texturemapping, this.modelOutput));
            })));
    }

    protected void createSculkCatalyst() {
        ResourceLocation resourcelocation = TextureMapping.getBlockTexture(Blocks.SCULK_CATALYST, "_bottom");
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.BOTTOM, resourcelocation)
            .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.SCULK_CATALYST, "_top"))
            .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.SCULK_CATALYST, "_side"));
        TextureMapping texturemapping1 = new TextureMapping()
            .put(TextureSlot.BOTTOM, resourcelocation)
            .put(TextureSlot.TOP, TextureMapping.getBlockTexture(Blocks.SCULK_CATALYST, "_top_bloom"))
            .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.SCULK_CATALYST, "_side_bloom"));
        ResourceLocation resourcelocation1 = ModelTemplates.CUBE_BOTTOM_TOP.create(Blocks.SCULK_CATALYST, texturemapping, this.modelOutput);
        MultiVariant multivariant = plainVariant(resourcelocation1);
        MultiVariant multivariant1 = plainVariant(ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(Blocks.SCULK_CATALYST, "_bloom", texturemapping1, this.modelOutput));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.SCULK_CATALYST)
                    .with(PropertyDispatch.initial(BlockStateProperties.BLOOM).generate(p_389262_ -> p_389262_ ? multivariant1 : multivariant))
            );
        this.registerSimpleItemModel(Blocks.SCULK_CATALYST, resourcelocation1);
    }

    protected void createChiseledBookshelf() {
        Block block = Blocks.CHISELED_BOOKSHELF;
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(block));
        MultiPartGenerator multipartgenerator = MultiPartGenerator.multiPart(block);
        List.of(
                Pair.of(Direction.NORTH, NOP),
                Pair.of(Direction.EAST, Y_ROT_90),
                Pair.of(Direction.SOUTH, Y_ROT_180),
                Pair.of(Direction.WEST, Y_ROT_270)
            )
            .forEach(p_389172_ -> {
                Direction direction = p_389172_.getFirst();
                VariantMutator variantmutator = p_389172_.getSecond();
                Condition condition = condition().term(BlockStateProperties.HORIZONTAL_FACING, direction).build();
                multipartgenerator.with(condition, multivariant.with(variantmutator).with(UV_LOCK));
                this.addSlotStateAndRotationVariants(multipartgenerator, condition, variantmutator);
            });
        this.blockStateOutput.accept(multipartgenerator);
        this.registerSimpleItemModel(block, ModelLocationUtils.getModelLocation(block, "_inventory"));
        CHISELED_BOOKSHELF_SLOT_MODEL_CACHE.clear();
    }

    protected void addSlotStateAndRotationVariants(MultiPartGenerator p_375471_, Condition p_394899_, VariantMutator p_394911_) {
        List.of(
                Pair.of(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED, ModelTemplates.CHISELED_BOOKSHELF_SLOT_TOP_LEFT),
                Pair.of(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_1_OCCUPIED, ModelTemplates.CHISELED_BOOKSHELF_SLOT_TOP_MID),
                Pair.of(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED, ModelTemplates.CHISELED_BOOKSHELF_SLOT_TOP_RIGHT),
                Pair.of(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_3_OCCUPIED, ModelTemplates.CHISELED_BOOKSHELF_SLOT_BOTTOM_LEFT),
                Pair.of(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_4_OCCUPIED, ModelTemplates.CHISELED_BOOKSHELF_SLOT_BOTTOM_MID),
                Pair.of(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_5_OCCUPIED, ModelTemplates.CHISELED_BOOKSHELF_SLOT_BOTTOM_RIGHT)
            )
            .forEach(p_389232_ -> {
                BooleanProperty booleanproperty = p_389232_.getFirst();
                ModelTemplate modeltemplate = p_389232_.getSecond();
                this.addBookSlotModel(p_375471_, p_394899_, p_394911_, booleanproperty, modeltemplate, true);
                this.addBookSlotModel(p_375471_, p_394899_, p_394911_, booleanproperty, modeltemplate, false);
            });
    }

    protected void addBookSlotModel(
        MultiPartGenerator p_378566_, Condition p_394648_, VariantMutator p_397061_, BooleanProperty p_376329_, ModelTemplate p_376560_, boolean p_376040_
    ) {
        String s = p_376040_ ? "_occupied" : "_empty";
        TextureMapping texturemapping = new TextureMapping().put(TextureSlot.TEXTURE, TextureMapping.getBlockTexture(Blocks.CHISELED_BOOKSHELF, s));
        BlockModelGenerators.BookSlotModelCacheKey blockmodelgenerators$bookslotmodelcachekey = new BlockModelGenerators.BookSlotModelCacheKey(p_376560_, s);
        MultiVariant multivariant = plainVariant(
            CHISELED_BOOKSHELF_SLOT_MODEL_CACHE.computeIfAbsent(
                blockmodelgenerators$bookslotmodelcachekey, p_377610_ -> p_376560_.createWithSuffix(Blocks.CHISELED_BOOKSHELF, s, texturemapping, this.modelOutput)
            )
        );
        p_378566_.with(
            new CombinedCondition(CombinedCondition.Operation.AND, List.of(p_394648_, condition().term(p_376329_, p_376040_).build())),
            multivariant.with(p_397061_)
        );
    }

    protected void createMagmaBlock() {
        MultiVariant multivariant = plainVariant(
            ModelTemplates.CUBE_ALL.create(Blocks.MAGMA_BLOCK, TextureMapping.cube(ModelLocationUtils.decorateBlockModelLocation("magma")), this.modelOutput)
        );
        this.blockStateOutput.accept(createSimpleBlock(Blocks.MAGMA_BLOCK, multivariant));
    }

    protected void createShulkerBox(Block p_376780_, @Nullable DyeColor p_378224_) {
        this.createParticleOnlyBlock(p_376780_);
        Item item = p_376780_.asItem();
        ResourceLocation resourcelocation = ModelTemplates.SHULKER_BOX_INVENTORY.create(item, TextureMapping.particle(p_376780_), this.modelOutput);
        ItemModel.Unbaked itemmodel$unbaked = p_378224_ != null
            ? ItemModelUtils.specialModel(resourcelocation, new ShulkerBoxSpecialRenderer.Unbaked(p_378224_))
            : ItemModelUtils.specialModel(resourcelocation, new ShulkerBoxSpecialRenderer.Unbaked());
        this.itemModelOutput.accept(item, itemmodel$unbaked);
    }

    protected void createGrowingPlant(Block p_376039_, Block p_377260_, BlockModelGenerators.PlantType p_375604_) {
        this.createCrossBlock(p_376039_, p_375604_);
        this.createCrossBlock(p_377260_, p_375604_);
    }

    protected void createInfestedStone() {
        ResourceLocation resourcelocation = ModelLocationUtils.getModelLocation(Blocks.STONE);
        Variant variant = plainModel(resourcelocation);
        Variant variant1 = plainModel(ModelLocationUtils.getModelLocation(Blocks.STONE, "_mirrored"));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(Blocks.INFESTED_STONE, createRotatedVariants(variant, variant1)));
        this.registerSimpleItemModel(Blocks.INFESTED_STONE, resourcelocation);
    }

    protected void createInfestedDeepslate() {
        ResourceLocation resourcelocation = ModelLocationUtils.getModelLocation(Blocks.DEEPSLATE);
        Variant variant = plainModel(resourcelocation);
        Variant variant1 = plainModel(ModelLocationUtils.getModelLocation(Blocks.DEEPSLATE, "_mirrored"));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(Blocks.INFESTED_DEEPSLATE, createRotatedVariants(variant, variant1)).with(createRotatedPillar()));
        this.registerSimpleItemModel(Blocks.INFESTED_DEEPSLATE, resourcelocation);
    }

    protected void createNetherRoots(Block p_378807_, Block p_378001_) {
        this.createCrossBlockWithDefaultItem(p_378807_, BlockModelGenerators.PlantType.NOT_TINTED);
        TextureMapping texturemapping = TextureMapping.plant(TextureMapping.getBlockTexture(p_378807_, "_pot"));
        MultiVariant multivariant = plainVariant(BlockModelGenerators.PlantType.NOT_TINTED.getCrossPot().create(p_378001_, texturemapping, this.modelOutput));
        this.blockStateOutput.accept(createSimpleBlock(p_378001_, multivariant));
    }

    protected void createRespawnAnchor() {
        ResourceLocation resourcelocation = TextureMapping.getBlockTexture(Blocks.RESPAWN_ANCHOR, "_bottom");
        ResourceLocation resourcelocation1 = TextureMapping.getBlockTexture(Blocks.RESPAWN_ANCHOR, "_top_off");
        ResourceLocation resourcelocation2 = TextureMapping.getBlockTexture(Blocks.RESPAWN_ANCHOR, "_top");
        ResourceLocation[] aresourcelocation = new ResourceLocation[5];

        for (int i = 0; i < 5; i++) {
            TextureMapping texturemapping = new TextureMapping()
                .put(TextureSlot.BOTTOM, resourcelocation)
                .put(TextureSlot.TOP, i == 0 ? resourcelocation1 : resourcelocation2)
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(Blocks.RESPAWN_ANCHOR, "_side" + i));
            aresourcelocation[i] = ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(Blocks.RESPAWN_ANCHOR, "_" + i, texturemapping, this.modelOutput);
        }

        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.RESPAWN_ANCHOR)
                    .with(PropertyDispatch.initial(BlockStateProperties.RESPAWN_ANCHOR_CHARGES).generate(p_389212_ -> plainVariant(aresourcelocation[p_389212_])))
            );
        this.registerSimpleItemModel(Blocks.RESPAWN_ANCHOR, aresourcelocation[0]);
    }

    protected static VariantMutator applyRotation(FrontAndTop p_377309_) {
        return switch (p_377309_) {
            case DOWN_NORTH -> X_ROT_90;
            case DOWN_SOUTH -> X_ROT_90.then(Y_ROT_180);
            case DOWN_WEST -> X_ROT_90.then(Y_ROT_270);
            case DOWN_EAST -> X_ROT_90.then(Y_ROT_90);
            case UP_NORTH -> X_ROT_270.then(Y_ROT_180);
            case UP_SOUTH -> X_ROT_270;
            case UP_WEST -> X_ROT_270.then(Y_ROT_90);
            case UP_EAST -> X_ROT_270.then(Y_ROT_270);
            case NORTH_UP -> NOP;
            case SOUTH_UP -> Y_ROT_180;
            case WEST_UP -> Y_ROT_270;
            case EAST_UP -> Y_ROT_90;
        };
    }

    protected void createJigsaw() {
        ResourceLocation resourcelocation = TextureMapping.getBlockTexture(Blocks.JIGSAW, "_top");
        ResourceLocation resourcelocation1 = TextureMapping.getBlockTexture(Blocks.JIGSAW, "_bottom");
        ResourceLocation resourcelocation2 = TextureMapping.getBlockTexture(Blocks.JIGSAW, "_side");
        ResourceLocation resourcelocation3 = TextureMapping.getBlockTexture(Blocks.JIGSAW, "_lock");
        TextureMapping texturemapping = new TextureMapping()
            .put(TextureSlot.DOWN, resourcelocation2)
            .put(TextureSlot.WEST, resourcelocation2)
            .put(TextureSlot.EAST, resourcelocation2)
            .put(TextureSlot.PARTICLE, resourcelocation)
            .put(TextureSlot.NORTH, resourcelocation)
            .put(TextureSlot.SOUTH, resourcelocation1)
            .put(TextureSlot.UP, resourcelocation3);
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(Blocks.JIGSAW, plainVariant(ModelTemplates.CUBE_DIRECTIONAL.create(Blocks.JIGSAW, texturemapping, this.modelOutput)))
                    .with(PropertyDispatch.modify(BlockStateProperties.ORIENTATION).generate(BlockModelGenerators::applyRotation))
            );
    }

    protected void createPetrifiedOakSlab() {
        Block block = Blocks.OAK_PLANKS;
        MultiVariant multivariant = plainVariant(ModelLocationUtils.getModelLocation(block));
        TextureMapping texturemapping = TextureMapping.cube(block);
        Block block1 = Blocks.PETRIFIED_OAK_SLAB;
        MultiVariant multivariant1 = plainVariant(ModelTemplates.SLAB_BOTTOM.create(block1, texturemapping, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.SLAB_TOP.create(block1, texturemapping, this.modelOutput));
        this.blockStateOutput.accept(createSlab(block1, multivariant1, multivariant2, multivariant));
    }

    protected void createHead(Block p_376902_, Block p_378202_, SkullBlock.Type p_375856_, ResourceLocation p_377201_) {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.decorateBlockModelLocation("skull"));
        this.blockStateOutput.accept(createSimpleBlock(p_376902_, multivariant));
        this.blockStateOutput.accept(createSimpleBlock(p_378202_, multivariant));
        if (p_375856_ == SkullBlock.Types.PLAYER) {
            this.itemModelOutput.accept(p_376902_.asItem(), ItemModelUtils.specialModel(p_377201_, new PlayerHeadSpecialRenderer.Unbaked()));
        } else {
            this.itemModelOutput.accept(p_376902_.asItem(), ItemModelUtils.specialModel(p_377201_, new SkullSpecialRenderer.Unbaked(p_375856_)));
        }
    }

    protected void createHeads() {
        ResourceLocation resourcelocation = ModelLocationUtils.decorateItemModelLocation("template_skull");
        this.createHead(Blocks.CREEPER_HEAD, Blocks.CREEPER_WALL_HEAD, SkullBlock.Types.CREEPER, resourcelocation);
        this.createHead(Blocks.PLAYER_HEAD, Blocks.PLAYER_WALL_HEAD, SkullBlock.Types.PLAYER, resourcelocation);
        this.createHead(Blocks.ZOMBIE_HEAD, Blocks.ZOMBIE_WALL_HEAD, SkullBlock.Types.ZOMBIE, resourcelocation);
        this.createHead(Blocks.SKELETON_SKULL, Blocks.SKELETON_WALL_SKULL, SkullBlock.Types.SKELETON, resourcelocation);
        this.createHead(Blocks.WITHER_SKELETON_SKULL, Blocks.WITHER_SKELETON_WALL_SKULL, SkullBlock.Types.WITHER_SKELETON, resourcelocation);
        this.createHead(Blocks.PIGLIN_HEAD, Blocks.PIGLIN_WALL_HEAD, SkullBlock.Types.PIGLIN, resourcelocation);
        this.createHead(Blocks.DRAGON_HEAD, Blocks.DRAGON_WALL_HEAD, SkullBlock.Types.DRAGON, ModelLocationUtils.getModelLocation(Items.DRAGON_HEAD));
    }

    protected void createBanner(Block p_378301_, Block p_378343_, DyeColor p_377534_) {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.decorateBlockModelLocation("banner"));
        ResourceLocation resourcelocation = ModelLocationUtils.decorateItemModelLocation("template_banner");
        this.blockStateOutput.accept(createSimpleBlock(p_378301_, multivariant));
        this.blockStateOutput.accept(createSimpleBlock(p_378343_, multivariant));
        Item item = p_378301_.asItem();
        this.itemModelOutput.accept(item, ItemModelUtils.specialModel(resourcelocation, new BannerSpecialRenderer.Unbaked(p_377534_)));
    }

    protected void createBanners() {
        this.createBanner(Blocks.WHITE_BANNER, Blocks.WHITE_WALL_BANNER, DyeColor.WHITE);
        this.createBanner(Blocks.ORANGE_BANNER, Blocks.ORANGE_WALL_BANNER, DyeColor.ORANGE);
        this.createBanner(Blocks.MAGENTA_BANNER, Blocks.MAGENTA_WALL_BANNER, DyeColor.MAGENTA);
        this.createBanner(Blocks.LIGHT_BLUE_BANNER, Blocks.LIGHT_BLUE_WALL_BANNER, DyeColor.LIGHT_BLUE);
        this.createBanner(Blocks.YELLOW_BANNER, Blocks.YELLOW_WALL_BANNER, DyeColor.YELLOW);
        this.createBanner(Blocks.LIME_BANNER, Blocks.LIME_WALL_BANNER, DyeColor.LIME);
        this.createBanner(Blocks.PINK_BANNER, Blocks.PINK_WALL_BANNER, DyeColor.PINK);
        this.createBanner(Blocks.GRAY_BANNER, Blocks.GRAY_WALL_BANNER, DyeColor.GRAY);
        this.createBanner(Blocks.LIGHT_GRAY_BANNER, Blocks.LIGHT_GRAY_WALL_BANNER, DyeColor.LIGHT_GRAY);
        this.createBanner(Blocks.CYAN_BANNER, Blocks.CYAN_WALL_BANNER, DyeColor.CYAN);
        this.createBanner(Blocks.PURPLE_BANNER, Blocks.PURPLE_WALL_BANNER, DyeColor.PURPLE);
        this.createBanner(Blocks.BLUE_BANNER, Blocks.BLUE_WALL_BANNER, DyeColor.BLUE);
        this.createBanner(Blocks.BROWN_BANNER, Blocks.BROWN_WALL_BANNER, DyeColor.BROWN);
        this.createBanner(Blocks.GREEN_BANNER, Blocks.GREEN_WALL_BANNER, DyeColor.GREEN);
        this.createBanner(Blocks.RED_BANNER, Blocks.RED_WALL_BANNER, DyeColor.RED);
        this.createBanner(Blocks.BLACK_BANNER, Blocks.BLACK_WALL_BANNER, DyeColor.BLACK);
    }

    protected void createChest(Block p_377801_, Block p_377106_, ResourceLocation p_378349_, boolean p_376992_) {
        this.createParticleOnlyBlock(p_377801_, p_377106_);
        Item item = p_377801_.asItem();
        ResourceLocation resourcelocation = ModelTemplates.CHEST_INVENTORY.create(item, TextureMapping.particle(p_377106_), this.modelOutput);
        ItemModel.Unbaked itemmodel$unbaked = ItemModelUtils.specialModel(resourcelocation, new ChestSpecialRenderer.Unbaked(p_378349_));
        if (p_376992_) {
            ItemModel.Unbaked itemmodel$unbaked1 = ItemModelUtils.specialModel(resourcelocation, new ChestSpecialRenderer.Unbaked(ChestSpecialRenderer.GIFT_CHEST_TEXTURE));
            this.itemModelOutput.accept(item, ItemModelUtils.isXmas(itemmodel$unbaked1, itemmodel$unbaked));
        } else {
            this.itemModelOutput.accept(item, itemmodel$unbaked);
        }
    }

    protected void createChests() {
        this.createChest(Blocks.CHEST, Blocks.OAK_PLANKS, ChestSpecialRenderer.NORMAL_CHEST_TEXTURE, true);
        this.createChest(Blocks.TRAPPED_CHEST, Blocks.OAK_PLANKS, ChestSpecialRenderer.TRAPPED_CHEST_TEXTURE, true);
        this.createChest(Blocks.ENDER_CHEST, Blocks.OBSIDIAN, ChestSpecialRenderer.ENDER_CHEST_TEXTURE, false);
    }

    protected void createBed(Block p_378031_, Block p_378477_, DyeColor p_376996_) {
        MultiVariant multivariant = plainVariant(ModelLocationUtils.decorateBlockModelLocation("bed"));
        this.blockStateOutput.accept(createSimpleBlock(p_378031_, multivariant));
        Item item = p_378031_.asItem();
        ResourceLocation resourcelocation = ModelTemplates.BED_INVENTORY
            .create(ModelLocationUtils.getModelLocation(item), TextureMapping.particle(p_378477_), this.modelOutput);
        this.itemModelOutput.accept(item, ItemModelUtils.specialModel(resourcelocation, new BedSpecialRenderer.Unbaked(p_376996_)));
    }

    protected void createBeds() {
        this.createBed(Blocks.WHITE_BED, Blocks.WHITE_WOOL, DyeColor.WHITE);
        this.createBed(Blocks.ORANGE_BED, Blocks.ORANGE_WOOL, DyeColor.ORANGE);
        this.createBed(Blocks.MAGENTA_BED, Blocks.MAGENTA_WOOL, DyeColor.MAGENTA);
        this.createBed(Blocks.LIGHT_BLUE_BED, Blocks.LIGHT_BLUE_WOOL, DyeColor.LIGHT_BLUE);
        this.createBed(Blocks.YELLOW_BED, Blocks.YELLOW_WOOL, DyeColor.YELLOW);
        this.createBed(Blocks.LIME_BED, Blocks.LIME_WOOL, DyeColor.LIME);
        this.createBed(Blocks.PINK_BED, Blocks.PINK_WOOL, DyeColor.PINK);
        this.createBed(Blocks.GRAY_BED, Blocks.GRAY_WOOL, DyeColor.GRAY);
        this.createBed(Blocks.LIGHT_GRAY_BED, Blocks.LIGHT_GRAY_WOOL, DyeColor.LIGHT_GRAY);
        this.createBed(Blocks.CYAN_BED, Blocks.CYAN_WOOL, DyeColor.CYAN);
        this.createBed(Blocks.PURPLE_BED, Blocks.PURPLE_WOOL, DyeColor.PURPLE);
        this.createBed(Blocks.BLUE_BED, Blocks.BLUE_WOOL, DyeColor.BLUE);
        this.createBed(Blocks.BROWN_BED, Blocks.BROWN_WOOL, DyeColor.BROWN);
        this.createBed(Blocks.GREEN_BED, Blocks.GREEN_WOOL, DyeColor.GREEN);
        this.createBed(Blocks.RED_BED, Blocks.RED_WOOL, DyeColor.RED);
        this.createBed(Blocks.BLACK_BED, Blocks.BLACK_WOOL, DyeColor.BLACK);
    }

    protected void generateSimpleSpecialItemModel(Block p_376478_, SpecialModelRenderer.Unbaked p_375868_) {
        Item item = p_376478_.asItem();
        ResourceLocation resourcelocation = ModelLocationUtils.getModelLocation(item);
        this.itemModelOutput.accept(item, ItemModelUtils.specialModel(resourcelocation, p_375868_));
    }

    public void run() {
        BlockFamilies.getAllFamilies().filter(BlockFamily::shouldGenerateModel).forEach(p_375984_ -> this.family(p_375984_.getBaseBlock()).generateFor(p_375984_));
        this.family(Blocks.CUT_COPPER)
            .generateFor(BlockFamilies.CUT_COPPER)
            .donateModelTo(Blocks.CUT_COPPER, Blocks.WAXED_CUT_COPPER)
            .donateModelTo(Blocks.CHISELED_COPPER, Blocks.WAXED_CHISELED_COPPER)
            .generateFor(BlockFamilies.WAXED_CUT_COPPER);
        this.family(Blocks.EXPOSED_CUT_COPPER)
            .generateFor(BlockFamilies.EXPOSED_CUT_COPPER)
            .donateModelTo(Blocks.EXPOSED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER)
            .donateModelTo(Blocks.EXPOSED_CHISELED_COPPER, Blocks.WAXED_EXPOSED_CHISELED_COPPER)
            .generateFor(BlockFamilies.WAXED_EXPOSED_CUT_COPPER);
        this.family(Blocks.WEATHERED_CUT_COPPER)
            .generateFor(BlockFamilies.WEATHERED_CUT_COPPER)
            .donateModelTo(Blocks.WEATHERED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER)
            .donateModelTo(Blocks.WEATHERED_CHISELED_COPPER, Blocks.WAXED_WEATHERED_CHISELED_COPPER)
            .generateFor(BlockFamilies.WAXED_WEATHERED_CUT_COPPER);
        this.family(Blocks.OXIDIZED_CUT_COPPER)
            .generateFor(BlockFamilies.OXIDIZED_CUT_COPPER)
            .donateModelTo(Blocks.OXIDIZED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER)
            .donateModelTo(Blocks.OXIDIZED_CHISELED_COPPER, Blocks.WAXED_OXIDIZED_CHISELED_COPPER)
            .generateFor(BlockFamilies.WAXED_OXIDIZED_CUT_COPPER);
        this.createCopperBulb(Blocks.COPPER_BULB);
        this.createCopperBulb(Blocks.EXPOSED_COPPER_BULB);
        this.createCopperBulb(Blocks.WEATHERED_COPPER_BULB);
        this.createCopperBulb(Blocks.OXIDIZED_COPPER_BULB);
        this.copyCopperBulbModel(Blocks.COPPER_BULB, Blocks.WAXED_COPPER_BULB);
        this.copyCopperBulbModel(Blocks.EXPOSED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB);
        this.copyCopperBulbModel(Blocks.WEATHERED_COPPER_BULB, Blocks.WAXED_WEATHERED_COPPER_BULB);
        this.copyCopperBulbModel(Blocks.OXIDIZED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB);
        this.createNonTemplateModelBlock(Blocks.AIR);
        this.createNonTemplateModelBlock(Blocks.CAVE_AIR, Blocks.AIR);
        this.createNonTemplateModelBlock(Blocks.VOID_AIR, Blocks.AIR);
        this.createNonTemplateModelBlock(Blocks.BEACON);
        this.createNonTemplateModelBlock(Blocks.CACTUS);
        this.createNonTemplateModelBlock(Blocks.BUBBLE_COLUMN, Blocks.WATER);
        this.createNonTemplateModelBlock(Blocks.DRAGON_EGG);
        this.createNonTemplateModelBlock(Blocks.DRIED_KELP_BLOCK);
        this.createNonTemplateModelBlock(Blocks.ENCHANTING_TABLE);
        this.createNonTemplateModelBlock(Blocks.FLOWER_POT);
        this.registerSimpleFlatItemModel(Items.FLOWER_POT);
        this.createNonTemplateModelBlock(Blocks.HONEY_BLOCK);
        this.createNonTemplateModelBlock(Blocks.WATER);
        this.createNonTemplateModelBlock(Blocks.LAVA);
        this.createNonTemplateModelBlock(Blocks.SLIME_BLOCK);
        this.registerSimpleFlatItemModel(Items.CHAIN);
        this.createCandleAndCandleCake(Blocks.WHITE_CANDLE, Blocks.WHITE_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.ORANGE_CANDLE, Blocks.ORANGE_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.MAGENTA_CANDLE, Blocks.MAGENTA_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.LIGHT_BLUE_CANDLE, Blocks.LIGHT_BLUE_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.YELLOW_CANDLE, Blocks.YELLOW_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.LIME_CANDLE, Blocks.LIME_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.PINK_CANDLE, Blocks.PINK_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.GRAY_CANDLE, Blocks.GRAY_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.LIGHT_GRAY_CANDLE, Blocks.LIGHT_GRAY_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.CYAN_CANDLE, Blocks.CYAN_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.PURPLE_CANDLE, Blocks.PURPLE_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.BLUE_CANDLE, Blocks.BLUE_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.BROWN_CANDLE, Blocks.BROWN_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.GREEN_CANDLE, Blocks.GREEN_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.RED_CANDLE, Blocks.RED_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.BLACK_CANDLE, Blocks.BLACK_CANDLE_CAKE);
        this.createCandleAndCandleCake(Blocks.CANDLE, Blocks.CANDLE_CAKE);
        this.createNonTemplateModelBlock(Blocks.POTTED_BAMBOO);
        this.createNonTemplateModelBlock(Blocks.POTTED_CACTUS);
        this.createNonTemplateModelBlock(Blocks.POWDER_SNOW);
        this.createNonTemplateModelBlock(Blocks.SPORE_BLOSSOM);
        this.createAzalea(Blocks.AZALEA);
        this.createAzalea(Blocks.FLOWERING_AZALEA);
        this.createPottedAzalea(Blocks.POTTED_AZALEA);
        this.createPottedAzalea(Blocks.POTTED_FLOWERING_AZALEA);
        this.createCaveVines();
        this.createFullAndCarpetBlocks(Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET);
        this.createMossyCarpet(Blocks.PALE_MOSS_CARPET);
        this.createHangingMoss(Blocks.PALE_HANGING_MOSS);
        this.createTrivialCube(Blocks.PALE_MOSS_BLOCK);
        this.createFlowerBed(Blocks.PINK_PETALS);
        this.createFlowerBed(Blocks.WILDFLOWERS);
        this.createLeafLitter(Blocks.LEAF_LITTER);
        this.createCrossBlock(Blocks.FIREFLY_BUSH, BlockModelGenerators.PlantType.EMISSIVE_NOT_TINTED);
        this.registerSimpleFlatItemModel(Items.FIREFLY_BUSH);
        this.createAirLikeBlock(Blocks.BARRIER, Items.BARRIER);
        this.registerSimpleFlatItemModel(Items.BARRIER);
        this.createLightBlock();
        this.createAirLikeBlock(Blocks.STRUCTURE_VOID, Items.STRUCTURE_VOID);
        this.registerSimpleFlatItemModel(Items.STRUCTURE_VOID);
        this.createAirLikeBlock(Blocks.MOVING_PISTON, TextureMapping.getBlockTexture(Blocks.PISTON, "_side"));
        this.createTrivialCube(Blocks.COAL_ORE);
        this.createTrivialCube(Blocks.DEEPSLATE_COAL_ORE);
        this.createTrivialCube(Blocks.COAL_BLOCK);
        this.createTrivialCube(Blocks.DIAMOND_ORE);
        this.createTrivialCube(Blocks.DEEPSLATE_DIAMOND_ORE);
        this.createTrivialCube(Blocks.DIAMOND_BLOCK);
        this.createTrivialCube(Blocks.EMERALD_ORE);
        this.createTrivialCube(Blocks.DEEPSLATE_EMERALD_ORE);
        this.createTrivialCube(Blocks.EMERALD_BLOCK);
        this.createTrivialCube(Blocks.GOLD_ORE);
        this.createTrivialCube(Blocks.NETHER_GOLD_ORE);
        this.createTrivialCube(Blocks.DEEPSLATE_GOLD_ORE);
        this.createTrivialCube(Blocks.GOLD_BLOCK);
        this.createTrivialCube(Blocks.IRON_ORE);
        this.createTrivialCube(Blocks.DEEPSLATE_IRON_ORE);
        this.createTrivialCube(Blocks.IRON_BLOCK);
        this.createTrivialBlock(Blocks.ANCIENT_DEBRIS, TexturedModel.COLUMN);
        this.createTrivialCube(Blocks.NETHERITE_BLOCK);
        this.createTrivialCube(Blocks.LAPIS_ORE);
        this.createTrivialCube(Blocks.DEEPSLATE_LAPIS_ORE);
        this.createTrivialCube(Blocks.LAPIS_BLOCK);
        this.createTrivialCube(Blocks.RESIN_BLOCK);
        this.createTrivialCube(Blocks.NETHER_QUARTZ_ORE);
        this.createTrivialCube(Blocks.REDSTONE_ORE);
        this.createTrivialCube(Blocks.DEEPSLATE_REDSTONE_ORE);
        this.createTrivialCube(Blocks.REDSTONE_BLOCK);
        this.createTrivialCube(Blocks.GILDED_BLACKSTONE);
        this.createTrivialCube(Blocks.BLUE_ICE);
        this.createTrivialCube(Blocks.CLAY);
        this.createTrivialCube(Blocks.COARSE_DIRT);
        this.createTrivialCube(Blocks.CRYING_OBSIDIAN);
        this.createTrivialCube(Blocks.END_STONE);
        this.createTrivialCube(Blocks.GLOWSTONE);
        this.createTrivialCube(Blocks.GRAVEL);
        this.createTrivialCube(Blocks.HONEYCOMB_BLOCK);
        this.createTrivialCube(Blocks.ICE);
        this.createTrivialBlock(Blocks.JUKEBOX, TexturedModel.CUBE_TOP);
        this.createTrivialBlock(Blocks.LODESTONE, TexturedModel.COLUMN);
        this.createTrivialBlock(Blocks.MELON, TexturedModel.COLUMN);
        this.createNonTemplateModelBlock(Blocks.MANGROVE_ROOTS);
        this.createNonTemplateModelBlock(Blocks.POTTED_MANGROVE_PROPAGULE);
        this.createTrivialCube(Blocks.NETHER_WART_BLOCK);
        this.createTrivialCube(Blocks.NOTE_BLOCK);
        this.createTrivialCube(Blocks.PACKED_ICE);
        this.createTrivialCube(Blocks.OBSIDIAN);
        this.createTrivialCube(Blocks.QUARTZ_BRICKS);
        this.createTrivialCube(Blocks.SEA_LANTERN);
        this.createTrivialCube(Blocks.SHROOMLIGHT);
        this.createTrivialCube(Blocks.SOUL_SAND);
        this.createTrivialCube(Blocks.SOUL_SOIL);
        this.createTrivialBlock(Blocks.SPAWNER, TexturedModel.CUBE_INNER_FACES);
        this.createCreakingHeart(Blocks.CREAKING_HEART);
        this.createTrivialCube(Blocks.SPONGE);
        this.createTrivialBlock(Blocks.SEAGRASS, TexturedModel.SEAGRASS);
        this.registerSimpleFlatItemModel(Items.SEAGRASS);
        this.createTrivialBlock(Blocks.TNT, TexturedModel.CUBE_TOP_BOTTOM);
        this.createTrivialBlock(Blocks.TARGET, TexturedModel.COLUMN);
        this.createTrivialCube(Blocks.WARPED_WART_BLOCK);
        this.createTrivialCube(Blocks.WET_SPONGE);
        this.createTrivialCube(Blocks.AMETHYST_BLOCK);
        this.createTrivialCube(Blocks.BUDDING_AMETHYST);
        this.createTrivialCube(Blocks.CALCITE);
        this.createTrivialCube(Blocks.DRIPSTONE_BLOCK);
        this.createTrivialCube(Blocks.RAW_IRON_BLOCK);
        this.createTrivialCube(Blocks.RAW_COPPER_BLOCK);
        this.createTrivialCube(Blocks.RAW_GOLD_BLOCK);
        this.createRotatedMirroredVariantBlock(Blocks.SCULK);
        this.createNonTemplateModelBlock(Blocks.HEAVY_CORE);
        this.createPetrifiedOakSlab();
        this.createTrivialCube(Blocks.COPPER_ORE);
        this.createTrivialCube(Blocks.DEEPSLATE_COPPER_ORE);
        this.createTrivialCube(Blocks.COPPER_BLOCK);
        this.createTrivialCube(Blocks.EXPOSED_COPPER);
        this.createTrivialCube(Blocks.WEATHERED_COPPER);
        this.createTrivialCube(Blocks.OXIDIZED_COPPER);
        this.copyModel(Blocks.COPPER_BLOCK, Blocks.WAXED_COPPER_BLOCK);
        this.copyModel(Blocks.EXPOSED_COPPER, Blocks.WAXED_EXPOSED_COPPER);
        this.copyModel(Blocks.WEATHERED_COPPER, Blocks.WAXED_WEATHERED_COPPER);
        this.copyModel(Blocks.OXIDIZED_COPPER, Blocks.WAXED_OXIDIZED_COPPER);
        this.createDoor(Blocks.COPPER_DOOR);
        this.createDoor(Blocks.EXPOSED_COPPER_DOOR);
        this.createDoor(Blocks.WEATHERED_COPPER_DOOR);
        this.createDoor(Blocks.OXIDIZED_COPPER_DOOR);
        this.copyDoorModel(Blocks.COPPER_DOOR, Blocks.WAXED_COPPER_DOOR);
        this.copyDoorModel(Blocks.EXPOSED_COPPER_DOOR, Blocks.WAXED_EXPOSED_COPPER_DOOR);
        this.copyDoorModel(Blocks.WEATHERED_COPPER_DOOR, Blocks.WAXED_WEATHERED_COPPER_DOOR);
        this.copyDoorModel(Blocks.OXIDIZED_COPPER_DOOR, Blocks.WAXED_OXIDIZED_COPPER_DOOR);
        this.createTrapdoor(Blocks.COPPER_TRAPDOOR);
        this.createTrapdoor(Blocks.EXPOSED_COPPER_TRAPDOOR);
        this.createTrapdoor(Blocks.WEATHERED_COPPER_TRAPDOOR);
        this.createTrapdoor(Blocks.OXIDIZED_COPPER_TRAPDOOR);
        this.copyTrapdoorModel(Blocks.COPPER_TRAPDOOR, Blocks.WAXED_COPPER_TRAPDOOR);
        this.copyTrapdoorModel(Blocks.EXPOSED_COPPER_TRAPDOOR, Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR);
        this.copyTrapdoorModel(Blocks.WEATHERED_COPPER_TRAPDOOR, Blocks.WAXED_WEATHERED_COPPER_TRAPDOOR);
        this.copyTrapdoorModel(Blocks.OXIDIZED_COPPER_TRAPDOOR, Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR);
        this.createTrivialCube(Blocks.COPPER_GRATE);
        this.createTrivialCube(Blocks.EXPOSED_COPPER_GRATE);
        this.createTrivialCube(Blocks.WEATHERED_COPPER_GRATE);
        this.createTrivialCube(Blocks.OXIDIZED_COPPER_GRATE);
        this.copyModel(Blocks.COPPER_GRATE, Blocks.WAXED_COPPER_GRATE);
        this.copyModel(Blocks.EXPOSED_COPPER_GRATE, Blocks.WAXED_EXPOSED_COPPER_GRATE);
        this.copyModel(Blocks.WEATHERED_COPPER_GRATE, Blocks.WAXED_WEATHERED_COPPER_GRATE);
        this.copyModel(Blocks.OXIDIZED_COPPER_GRATE, Blocks.WAXED_OXIDIZED_COPPER_GRATE);
        this.createWeightedPressurePlate(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, Blocks.GOLD_BLOCK);
        this.createWeightedPressurePlate(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, Blocks.IRON_BLOCK);
        this.createAmethystClusters();
        this.createBookshelf();
        this.createChiseledBookshelf();
        this.createBrewingStand();
        this.createCakeBlock();
        this.createCampfires(Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE);
        this.createCartographyTable();
        this.createCauldrons();
        this.createChorusFlower();
        this.createChorusPlant();
        this.createComposter();
        this.createDaylightDetector();
        this.createEndPortalFrame();
        this.createRotatableColumn(Blocks.END_ROD);
        this.createLightningRod();
        this.createFarmland();
        this.createFire();
        this.createSoulFire();
        this.createFrostedIce();
        this.createGrassBlocks();
        this.createCocoa();
        this.createDirtPath();
        this.createGrindstone();
        this.createHopper();
        this.createIronBars();
        this.createLever();
        this.createLilyPad();
        this.createNetherPortalBlock();
        this.createNetherrack();
        this.createObserver();
        this.createPistons();
        this.createPistonHeads();
        this.createScaffolding();
        this.createRedstoneTorch();
        this.createRedstoneLamp();
        this.createRepeater();
        this.createSeaPickle();
        this.createSmithingTable();
        this.createSnowBlocks();
        this.createStonecutter();
        this.createStructureBlock();
        this.createSweetBerryBush();
        this.createTestBlock();
        this.createTrivialCube(Blocks.TEST_INSTANCE_BLOCK);
        this.createTripwire();
        this.createTripwireHook();
        this.createTurtleEgg();
        this.createSnifferEgg();
        this.createDriedGhastBlock();
        this.createVine();
        this.createMultiface(Blocks.GLOW_LICHEN);
        this.createMultiface(Blocks.SCULK_VEIN);
        this.createMultiface(Blocks.RESIN_CLUMP, Items.RESIN_CLUMP);
        this.createMagmaBlock();
        this.createJigsaw();
        this.createSculkSensor();
        this.createCalibratedSculkSensor();
        this.createSculkShrieker();
        this.createFrogspawnBlock();
        this.createMangrovePropagule();
        this.createMuddyMangroveRoots();
        this.createTrialSpawner();
        this.createVault();
        this.createNonTemplateHorizontalBlock(Blocks.LADDER);
        this.registerSimpleFlatItemModel(Blocks.LADDER);
        this.createNonTemplateHorizontalBlock(Blocks.LECTERN);
        this.createBigDripLeafBlock();
        this.createNonTemplateHorizontalBlock(Blocks.BIG_DRIPLEAF_STEM);
        this.createNormalTorch(Blocks.TORCH, Blocks.WALL_TORCH);
        this.createNormalTorch(Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH);
        this.createCraftingTableLike(Blocks.CRAFTING_TABLE, Blocks.OAK_PLANKS, TextureMapping::craftingTable);
        this.createCraftingTableLike(Blocks.FLETCHING_TABLE, Blocks.BIRCH_PLANKS, TextureMapping::fletchingTable);
        this.createNyliumBlock(Blocks.CRIMSON_NYLIUM);
        this.createNyliumBlock(Blocks.WARPED_NYLIUM);
        this.createDispenserBlock(Blocks.DISPENSER);
        this.createDispenserBlock(Blocks.DROPPER);
        this.createCrafterBlock();
        this.createLantern(Blocks.LANTERN);
        this.createLantern(Blocks.SOUL_LANTERN);
        this.createAxisAlignedPillarBlockCustomModel(Blocks.CHAIN, plainVariant(ModelLocationUtils.getModelLocation(Blocks.CHAIN)));
        this.createAxisAlignedPillarBlock(Blocks.BASALT, TexturedModel.COLUMN);
        this.createAxisAlignedPillarBlock(Blocks.POLISHED_BASALT, TexturedModel.COLUMN);
        this.createTrivialCube(Blocks.SMOOTH_BASALT);
        this.createAxisAlignedPillarBlock(Blocks.BONE_BLOCK, TexturedModel.COLUMN);
        this.createRotatedVariantBlock(Blocks.DIRT);
        this.createRotatedVariantBlock(Blocks.ROOTED_DIRT);
        this.createRotatedVariantBlock(Blocks.SAND);
        this.createBrushableBlock(Blocks.SUSPICIOUS_SAND);
        this.createBrushableBlock(Blocks.SUSPICIOUS_GRAVEL);
        this.createRotatedVariantBlock(Blocks.RED_SAND);
        this.createRotatedMirroredVariantBlock(Blocks.BEDROCK);
        this.createTrivialBlock(Blocks.REINFORCED_DEEPSLATE, TexturedModel.CUBE_TOP_BOTTOM);
        this.createRotatedPillarWithHorizontalVariant(Blocks.HAY_BLOCK, TexturedModel.COLUMN, TexturedModel.COLUMN_HORIZONTAL);
        this.createRotatedPillarWithHorizontalVariant(Blocks.PURPUR_PILLAR, TexturedModel.COLUMN_ALT, TexturedModel.COLUMN_HORIZONTAL_ALT);
        this.createRotatedPillarWithHorizontalVariant(Blocks.QUARTZ_PILLAR, TexturedModel.COLUMN_ALT, TexturedModel.COLUMN_HORIZONTAL_ALT);
        this.createRotatedPillarWithHorizontalVariant(Blocks.OCHRE_FROGLIGHT, TexturedModel.COLUMN, TexturedModel.COLUMN_HORIZONTAL);
        this.createRotatedPillarWithHorizontalVariant(Blocks.VERDANT_FROGLIGHT, TexturedModel.COLUMN, TexturedModel.COLUMN_HORIZONTAL);
        this.createRotatedPillarWithHorizontalVariant(Blocks.PEARLESCENT_FROGLIGHT, TexturedModel.COLUMN, TexturedModel.COLUMN_HORIZONTAL);
        this.createHorizontallyRotatedBlock(Blocks.LOOM, TexturedModel.ORIENTABLE);
        this.createPumpkins();
        this.createBeeNest(Blocks.BEE_NEST, TextureMapping::orientableCube);
        this.createBeeNest(Blocks.BEEHIVE, TextureMapping::orientableCubeSameEnds);
        this.createCropBlock(Blocks.BEETROOTS, BlockStateProperties.AGE_3, 0, 1, 2, 3);
        this.createCropBlock(Blocks.CARROTS, BlockStateProperties.AGE_7, 0, 0, 1, 1, 2, 2, 2, 3);
        this.createCropBlock(Blocks.NETHER_WART, BlockStateProperties.AGE_3, 0, 1, 1, 2);
        this.createCropBlock(Blocks.POTATOES, BlockStateProperties.AGE_7, 0, 0, 1, 1, 2, 2, 2, 3);
        this.createCropBlock(Blocks.WHEAT, BlockStateProperties.AGE_7, 0, 1, 2, 3, 4, 5, 6, 7);
        this.createCrossBlock(Blocks.TORCHFLOWER_CROP, BlockModelGenerators.PlantType.NOT_TINTED, BlockStateProperties.AGE_1, 0, 1);
        this.createPitcherCrop();
        this.createPitcherPlant();
        this.createBanners();
        this.createBeds();
        this.createHeads();
        this.createChests();
        this.createShulkerBox(Blocks.SHULKER_BOX, null);
        this.createShulkerBox(Blocks.WHITE_SHULKER_BOX, DyeColor.WHITE);
        this.createShulkerBox(Blocks.ORANGE_SHULKER_BOX, DyeColor.ORANGE);
        this.createShulkerBox(Blocks.MAGENTA_SHULKER_BOX, DyeColor.MAGENTA);
        this.createShulkerBox(Blocks.LIGHT_BLUE_SHULKER_BOX, DyeColor.LIGHT_BLUE);
        this.createShulkerBox(Blocks.YELLOW_SHULKER_BOX, DyeColor.YELLOW);
        this.createShulkerBox(Blocks.LIME_SHULKER_BOX, DyeColor.LIME);
        this.createShulkerBox(Blocks.PINK_SHULKER_BOX, DyeColor.PINK);
        this.createShulkerBox(Blocks.GRAY_SHULKER_BOX, DyeColor.GRAY);
        this.createShulkerBox(Blocks.LIGHT_GRAY_SHULKER_BOX, DyeColor.LIGHT_GRAY);
        this.createShulkerBox(Blocks.CYAN_SHULKER_BOX, DyeColor.CYAN);
        this.createShulkerBox(Blocks.PURPLE_SHULKER_BOX, DyeColor.PURPLE);
        this.createShulkerBox(Blocks.BLUE_SHULKER_BOX, DyeColor.BLUE);
        this.createShulkerBox(Blocks.BROWN_SHULKER_BOX, DyeColor.BROWN);
        this.createShulkerBox(Blocks.GREEN_SHULKER_BOX, DyeColor.GREEN);
        this.createShulkerBox(Blocks.RED_SHULKER_BOX, DyeColor.RED);
        this.createShulkerBox(Blocks.BLACK_SHULKER_BOX, DyeColor.BLACK);
        this.createParticleOnlyBlock(Blocks.CONDUIT);
        this.generateSimpleSpecialItemModel(Blocks.CONDUIT, new ConduitSpecialRenderer.Unbaked());
        this.createParticleOnlyBlock(Blocks.DECORATED_POT, Blocks.TERRACOTTA);
        this.generateSimpleSpecialItemModel(Blocks.DECORATED_POT, new DecoratedPotSpecialRenderer.Unbaked());
        this.createParticleOnlyBlock(Blocks.END_PORTAL, Blocks.OBSIDIAN);
        this.createParticleOnlyBlock(Blocks.END_GATEWAY, Blocks.OBSIDIAN);
        this.createTrivialCube(Blocks.AZALEA_LEAVES);
        this.createTrivialCube(Blocks.FLOWERING_AZALEA_LEAVES);
        this.createTrivialCube(Blocks.WHITE_CONCRETE);
        this.createTrivialCube(Blocks.ORANGE_CONCRETE);
        this.createTrivialCube(Blocks.MAGENTA_CONCRETE);
        this.createTrivialCube(Blocks.LIGHT_BLUE_CONCRETE);
        this.createTrivialCube(Blocks.YELLOW_CONCRETE);
        this.createTrivialCube(Blocks.LIME_CONCRETE);
        this.createTrivialCube(Blocks.PINK_CONCRETE);
        this.createTrivialCube(Blocks.GRAY_CONCRETE);
        this.createTrivialCube(Blocks.LIGHT_GRAY_CONCRETE);
        this.createTrivialCube(Blocks.CYAN_CONCRETE);
        this.createTrivialCube(Blocks.PURPLE_CONCRETE);
        this.createTrivialCube(Blocks.BLUE_CONCRETE);
        this.createTrivialCube(Blocks.BROWN_CONCRETE);
        this.createTrivialCube(Blocks.GREEN_CONCRETE);
        this.createTrivialCube(Blocks.RED_CONCRETE);
        this.createTrivialCube(Blocks.BLACK_CONCRETE);
        this.createColoredBlockWithRandomRotations(
            TexturedModel.CUBE,
            Blocks.WHITE_CONCRETE_POWDER,
            Blocks.ORANGE_CONCRETE_POWDER,
            Blocks.MAGENTA_CONCRETE_POWDER,
            Blocks.LIGHT_BLUE_CONCRETE_POWDER,
            Blocks.YELLOW_CONCRETE_POWDER,
            Blocks.LIME_CONCRETE_POWDER,
            Blocks.PINK_CONCRETE_POWDER,
            Blocks.GRAY_CONCRETE_POWDER,
            Blocks.LIGHT_GRAY_CONCRETE_POWDER,
            Blocks.CYAN_CONCRETE_POWDER,
            Blocks.PURPLE_CONCRETE_POWDER,
            Blocks.BLUE_CONCRETE_POWDER,
            Blocks.BROWN_CONCRETE_POWDER,
            Blocks.GREEN_CONCRETE_POWDER,
            Blocks.RED_CONCRETE_POWDER,
            Blocks.BLACK_CONCRETE_POWDER
        );
        this.createTrivialCube(Blocks.TERRACOTTA);
        this.createTrivialCube(Blocks.WHITE_TERRACOTTA);
        this.createTrivialCube(Blocks.ORANGE_TERRACOTTA);
        this.createTrivialCube(Blocks.MAGENTA_TERRACOTTA);
        this.createTrivialCube(Blocks.LIGHT_BLUE_TERRACOTTA);
        this.createTrivialCube(Blocks.YELLOW_TERRACOTTA);
        this.createTrivialCube(Blocks.LIME_TERRACOTTA);
        this.createTrivialCube(Blocks.PINK_TERRACOTTA);
        this.createTrivialCube(Blocks.GRAY_TERRACOTTA);
        this.createTrivialCube(Blocks.LIGHT_GRAY_TERRACOTTA);
        this.createTrivialCube(Blocks.CYAN_TERRACOTTA);
        this.createTrivialCube(Blocks.PURPLE_TERRACOTTA);
        this.createTrivialCube(Blocks.BLUE_TERRACOTTA);
        this.createTrivialCube(Blocks.BROWN_TERRACOTTA);
        this.createTrivialCube(Blocks.GREEN_TERRACOTTA);
        this.createTrivialCube(Blocks.RED_TERRACOTTA);
        this.createTrivialCube(Blocks.BLACK_TERRACOTTA);
        this.createTrivialCube(Blocks.TINTED_GLASS);
        this.createGlassBlocks(Blocks.GLASS, Blocks.GLASS_PANE);
        this.createGlassBlocks(Blocks.WHITE_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.ORANGE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.MAGENTA_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.YELLOW_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.LIME_STAINED_GLASS, Blocks.LIME_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.PINK_STAINED_GLASS, Blocks.PINK_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.GRAY_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.LIGHT_GRAY_STAINED_GLASS, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.CYAN_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.PURPLE_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.BLUE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.BROWN_STAINED_GLASS, Blocks.BROWN_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.GREEN_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.RED_STAINED_GLASS, Blocks.RED_STAINED_GLASS_PANE);
        this.createGlassBlocks(Blocks.BLACK_STAINED_GLASS, Blocks.BLACK_STAINED_GLASS_PANE);
        this.createColoredBlockWithStateRotations(
            TexturedModel.GLAZED_TERRACOTTA,
            Blocks.WHITE_GLAZED_TERRACOTTA,
            Blocks.ORANGE_GLAZED_TERRACOTTA,
            Blocks.MAGENTA_GLAZED_TERRACOTTA,
            Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA,
            Blocks.YELLOW_GLAZED_TERRACOTTA,
            Blocks.LIME_GLAZED_TERRACOTTA,
            Blocks.PINK_GLAZED_TERRACOTTA,
            Blocks.GRAY_GLAZED_TERRACOTTA,
            Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA,
            Blocks.CYAN_GLAZED_TERRACOTTA,
            Blocks.PURPLE_GLAZED_TERRACOTTA,
            Blocks.BLUE_GLAZED_TERRACOTTA,
            Blocks.BROWN_GLAZED_TERRACOTTA,
            Blocks.GREEN_GLAZED_TERRACOTTA,
            Blocks.RED_GLAZED_TERRACOTTA,
            Blocks.BLACK_GLAZED_TERRACOTTA
        );
        this.createFullAndCarpetBlocks(Blocks.WHITE_WOOL, Blocks.WHITE_CARPET);
        this.createFullAndCarpetBlocks(Blocks.ORANGE_WOOL, Blocks.ORANGE_CARPET);
        this.createFullAndCarpetBlocks(Blocks.MAGENTA_WOOL, Blocks.MAGENTA_CARPET);
        this.createFullAndCarpetBlocks(Blocks.LIGHT_BLUE_WOOL, Blocks.LIGHT_BLUE_CARPET);
        this.createFullAndCarpetBlocks(Blocks.YELLOW_WOOL, Blocks.YELLOW_CARPET);
        this.createFullAndCarpetBlocks(Blocks.LIME_WOOL, Blocks.LIME_CARPET);
        this.createFullAndCarpetBlocks(Blocks.PINK_WOOL, Blocks.PINK_CARPET);
        this.createFullAndCarpetBlocks(Blocks.GRAY_WOOL, Blocks.GRAY_CARPET);
        this.createFullAndCarpetBlocks(Blocks.LIGHT_GRAY_WOOL, Blocks.LIGHT_GRAY_CARPET);
        this.createFullAndCarpetBlocks(Blocks.CYAN_WOOL, Blocks.CYAN_CARPET);
        this.createFullAndCarpetBlocks(Blocks.PURPLE_WOOL, Blocks.PURPLE_CARPET);
        this.createFullAndCarpetBlocks(Blocks.BLUE_WOOL, Blocks.BLUE_CARPET);
        this.createFullAndCarpetBlocks(Blocks.BROWN_WOOL, Blocks.BROWN_CARPET);
        this.createFullAndCarpetBlocks(Blocks.GREEN_WOOL, Blocks.GREEN_CARPET);
        this.createFullAndCarpetBlocks(Blocks.RED_WOOL, Blocks.RED_CARPET);
        this.createFullAndCarpetBlocks(Blocks.BLACK_WOOL, Blocks.BLACK_CARPET);
        this.createTrivialCube(Blocks.MUD);
        this.createTrivialCube(Blocks.PACKED_MUD);
        this.createPlant(Blocks.FERN, Blocks.POTTED_FERN, BlockModelGenerators.PlantType.TINTED);
        this.createItemWithGrassTint(Blocks.FERN);
        this.createPlantWithDefaultItem(Blocks.DANDELION, Blocks.POTTED_DANDELION, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.POPPY, Blocks.POTTED_POPPY, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.OPEN_EYEBLOSSOM, Blocks.POTTED_OPEN_EYEBLOSSOM, BlockModelGenerators.PlantType.EMISSIVE_NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.CLOSED_EYEBLOSSOM, Blocks.POTTED_CLOSED_EYEBLOSSOM, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.BLUE_ORCHID, Blocks.POTTED_BLUE_ORCHID, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.ALLIUM, Blocks.POTTED_ALLIUM, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.AZURE_BLUET, Blocks.POTTED_AZURE_BLUET, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.RED_TULIP, Blocks.POTTED_RED_TULIP, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.ORANGE_TULIP, Blocks.POTTED_ORANGE_TULIP, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.WHITE_TULIP, Blocks.POTTED_WHITE_TULIP, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.PINK_TULIP, Blocks.POTTED_PINK_TULIP, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.OXEYE_DAISY, Blocks.POTTED_OXEYE_DAISY, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.CORNFLOWER, Blocks.POTTED_CORNFLOWER, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.LILY_OF_THE_VALLEY, Blocks.POTTED_LILY_OF_THE_VALLEY, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.WITHER_ROSE, Blocks.POTTED_WITHER_ROSE, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.RED_MUSHROOM, Blocks.POTTED_RED_MUSHROOM, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.BROWN_MUSHROOM, Blocks.POTTED_BROWN_MUSHROOM, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.DEAD_BUSH, Blocks.POTTED_DEAD_BUSH, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPlantWithDefaultItem(Blocks.TORCHFLOWER, Blocks.POTTED_TORCHFLOWER, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createPointedDripstone();
        this.createMushroomBlock(Blocks.BROWN_MUSHROOM_BLOCK);
        this.createMushroomBlock(Blocks.RED_MUSHROOM_BLOCK);
        this.createMushroomBlock(Blocks.MUSHROOM_STEM);
        this.createCrossBlock(Blocks.SHORT_GRASS, BlockModelGenerators.PlantType.TINTED);
        this.createItemWithGrassTint(Blocks.SHORT_GRASS);
        this.createCrossBlockWithDefaultItem(Blocks.SHORT_DRY_GRASS, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createCrossBlockWithDefaultItem(Blocks.TALL_DRY_GRASS, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createCrossBlock(Blocks.BUSH, BlockModelGenerators.PlantType.TINTED);
        this.createItemWithGrassTint(Blocks.BUSH);
        this.createCrossBlock(Blocks.SUGAR_CANE, BlockModelGenerators.PlantType.TINTED);
        this.registerSimpleFlatItemModel(Items.SUGAR_CANE);
        this.createGrowingPlant(Blocks.KELP, Blocks.KELP_PLANT, BlockModelGenerators.PlantType.NOT_TINTED);
        this.registerSimpleFlatItemModel(Items.KELP);
        this.createCrossBlock(Blocks.HANGING_ROOTS, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createGrowingPlant(Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createGrowingPlant(Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, BlockModelGenerators.PlantType.NOT_TINTED);
        this.registerSimpleFlatItemModel(Blocks.WEEPING_VINES, "_plant");
        this.registerSimpleFlatItemModel(Blocks.TWISTING_VINES, "_plant");
        this.createCrossBlockWithDefaultItem(Blocks.BAMBOO_SAPLING, BlockModelGenerators.PlantType.TINTED, TextureMapping.cross(TextureMapping.getBlockTexture(Blocks.BAMBOO, "_stage0")));
        this.createBamboo();
        this.createCrossBlockWithDefaultItem(Blocks.CACTUS_FLOWER, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createCrossBlockWithDefaultItem(Blocks.COBWEB, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createDoublePlantWithDefaultItem(Blocks.LILAC, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createDoublePlantWithDefaultItem(Blocks.ROSE_BUSH, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createDoublePlantWithDefaultItem(Blocks.PEONY, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createTintedDoublePlant(Blocks.TALL_GRASS);
        this.createTintedDoublePlant(Blocks.LARGE_FERN);
        this.createSunflower();
        this.createTallSeagrass();
        this.createSmallDripleaf();
        this.createCoral(Blocks.TUBE_CORAL, Blocks.DEAD_TUBE_CORAL, Blocks.TUBE_CORAL_BLOCK, Blocks.DEAD_TUBE_CORAL_BLOCK, Blocks.TUBE_CORAL_FAN, Blocks.DEAD_TUBE_CORAL_FAN, Blocks.TUBE_CORAL_WALL_FAN, Blocks.DEAD_TUBE_CORAL_WALL_FAN);
        this.createCoral(Blocks.BRAIN_CORAL, Blocks.DEAD_BRAIN_CORAL, Blocks.BRAIN_CORAL_BLOCK, Blocks.DEAD_BRAIN_CORAL_BLOCK, Blocks.BRAIN_CORAL_FAN, Blocks.DEAD_BRAIN_CORAL_FAN, Blocks.BRAIN_CORAL_WALL_FAN, Blocks.DEAD_BRAIN_CORAL_WALL_FAN);
        this.createCoral(Blocks.BUBBLE_CORAL, Blocks.DEAD_BUBBLE_CORAL, Blocks.BUBBLE_CORAL_BLOCK, Blocks.DEAD_BUBBLE_CORAL_BLOCK, Blocks.BUBBLE_CORAL_FAN, Blocks.DEAD_BUBBLE_CORAL_FAN, Blocks.BUBBLE_CORAL_WALL_FAN, Blocks.DEAD_BUBBLE_CORAL_WALL_FAN);
        this.createCoral(Blocks.FIRE_CORAL, Blocks.DEAD_FIRE_CORAL, Blocks.FIRE_CORAL_BLOCK, Blocks.DEAD_FIRE_CORAL_BLOCK, Blocks.FIRE_CORAL_FAN, Blocks.DEAD_FIRE_CORAL_FAN, Blocks.FIRE_CORAL_WALL_FAN, Blocks.DEAD_FIRE_CORAL_WALL_FAN);
        this.createCoral(Blocks.HORN_CORAL, Blocks.DEAD_HORN_CORAL, Blocks.HORN_CORAL_BLOCK, Blocks.DEAD_HORN_CORAL_BLOCK, Blocks.HORN_CORAL_FAN, Blocks.DEAD_HORN_CORAL_FAN, Blocks.HORN_CORAL_WALL_FAN, Blocks.DEAD_HORN_CORAL_WALL_FAN);
        this.createStems(Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM);
        this.createStems(Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM);
        this.woodProvider(Blocks.MANGROVE_LOG).logWithHorizontal(Blocks.MANGROVE_LOG).wood(Blocks.MANGROVE_WOOD);
        this.woodProvider(Blocks.STRIPPED_MANGROVE_LOG).logWithHorizontal(Blocks.STRIPPED_MANGROVE_LOG).wood(Blocks.STRIPPED_MANGROVE_WOOD);
        this.createHangingSign(Blocks.STRIPPED_MANGROVE_LOG, Blocks.MANGROVE_HANGING_SIGN, Blocks.MANGROVE_WALL_HANGING_SIGN);
        this.createTintedLeaves(Blocks.MANGROVE_LEAVES, TexturedModel.LEAVES, -7158200);
        this.woodProvider(Blocks.ACACIA_LOG).logWithHorizontal(Blocks.ACACIA_LOG).wood(Blocks.ACACIA_WOOD);
        this.woodProvider(Blocks.STRIPPED_ACACIA_LOG).logWithHorizontal(Blocks.STRIPPED_ACACIA_LOG).wood(Blocks.STRIPPED_ACACIA_WOOD);
        this.createHangingSign(Blocks.STRIPPED_ACACIA_LOG, Blocks.ACACIA_HANGING_SIGN, Blocks.ACACIA_WALL_HANGING_SIGN);
        this.createPlantWithDefaultItem(Blocks.ACACIA_SAPLING, Blocks.POTTED_ACACIA_SAPLING, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createTintedLeaves(Blocks.ACACIA_LEAVES, TexturedModel.LEAVES, -12012264);
        this.woodProvider(Blocks.CHERRY_LOG).logUVLocked(Blocks.CHERRY_LOG).wood(Blocks.CHERRY_WOOD);
        this.woodProvider(Blocks.STRIPPED_CHERRY_LOG).logUVLocked(Blocks.STRIPPED_CHERRY_LOG).wood(Blocks.STRIPPED_CHERRY_WOOD);
        this.createHangingSign(Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_HANGING_SIGN, Blocks.CHERRY_WALL_HANGING_SIGN);
        this.createPlantWithDefaultItem(Blocks.CHERRY_SAPLING, Blocks.POTTED_CHERRY_SAPLING, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createTrivialBlock(Blocks.CHERRY_LEAVES, TexturedModel.LEAVES);
        this.woodProvider(Blocks.BIRCH_LOG).logWithHorizontal(Blocks.BIRCH_LOG).wood(Blocks.BIRCH_WOOD);
        this.woodProvider(Blocks.STRIPPED_BIRCH_LOG).logWithHorizontal(Blocks.STRIPPED_BIRCH_LOG).wood(Blocks.STRIPPED_BIRCH_WOOD);
        this.createHangingSign(Blocks.STRIPPED_BIRCH_LOG, Blocks.BIRCH_HANGING_SIGN, Blocks.BIRCH_WALL_HANGING_SIGN);
        this.createPlantWithDefaultItem(Blocks.BIRCH_SAPLING, Blocks.POTTED_BIRCH_SAPLING, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createTintedLeaves(Blocks.BIRCH_LEAVES, TexturedModel.LEAVES, -8345771);
        this.woodProvider(Blocks.OAK_LOG).logWithHorizontal(Blocks.OAK_LOG).wood(Blocks.OAK_WOOD);
        this.woodProvider(Blocks.STRIPPED_OAK_LOG).logWithHorizontal(Blocks.STRIPPED_OAK_LOG).wood(Blocks.STRIPPED_OAK_WOOD);
        this.createHangingSign(Blocks.STRIPPED_OAK_LOG, Blocks.OAK_HANGING_SIGN, Blocks.OAK_WALL_HANGING_SIGN);
        this.createPlantWithDefaultItem(Blocks.OAK_SAPLING, Blocks.POTTED_OAK_SAPLING, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createTintedLeaves(Blocks.OAK_LEAVES, TexturedModel.LEAVES, -12012264);
        this.woodProvider(Blocks.SPRUCE_LOG).logWithHorizontal(Blocks.SPRUCE_LOG).wood(Blocks.SPRUCE_WOOD);
        this.woodProvider(Blocks.STRIPPED_SPRUCE_LOG).logWithHorizontal(Blocks.STRIPPED_SPRUCE_LOG).wood(Blocks.STRIPPED_SPRUCE_WOOD);
        this.createHangingSign(Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_HANGING_SIGN, Blocks.SPRUCE_WALL_HANGING_SIGN);
        this.createPlantWithDefaultItem(Blocks.SPRUCE_SAPLING, Blocks.POTTED_SPRUCE_SAPLING, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createTintedLeaves(Blocks.SPRUCE_LEAVES, TexturedModel.LEAVES, -10380959);
        this.woodProvider(Blocks.DARK_OAK_LOG).logWithHorizontal(Blocks.DARK_OAK_LOG).wood(Blocks.DARK_OAK_WOOD);
        this.woodProvider(Blocks.STRIPPED_DARK_OAK_LOG).logWithHorizontal(Blocks.STRIPPED_DARK_OAK_LOG).wood(Blocks.STRIPPED_DARK_OAK_WOOD);
        this.createHangingSign(Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_HANGING_SIGN, Blocks.DARK_OAK_WALL_HANGING_SIGN);
        this.createPlantWithDefaultItem(Blocks.DARK_OAK_SAPLING, Blocks.POTTED_DARK_OAK_SAPLING, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createTintedLeaves(Blocks.DARK_OAK_LEAVES, TexturedModel.LEAVES, -12012264);
        this.woodProvider(Blocks.PALE_OAK_LOG).logWithHorizontal(Blocks.PALE_OAK_LOG).wood(Blocks.PALE_OAK_WOOD);
        this.woodProvider(Blocks.STRIPPED_PALE_OAK_LOG).logWithHorizontal(Blocks.STRIPPED_PALE_OAK_LOG).wood(Blocks.STRIPPED_PALE_OAK_WOOD);
        this.createHangingSign(Blocks.STRIPPED_PALE_OAK_LOG, Blocks.PALE_OAK_HANGING_SIGN, Blocks.PALE_OAK_WALL_HANGING_SIGN);
        this.createPlantWithDefaultItem(Blocks.PALE_OAK_SAPLING, Blocks.POTTED_PALE_OAK_SAPLING, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createTrivialBlock(Blocks.PALE_OAK_LEAVES, TexturedModel.LEAVES);
        this.woodProvider(Blocks.JUNGLE_LOG).logWithHorizontal(Blocks.JUNGLE_LOG).wood(Blocks.JUNGLE_WOOD);
        this.woodProvider(Blocks.STRIPPED_JUNGLE_LOG).logWithHorizontal(Blocks.STRIPPED_JUNGLE_LOG).wood(Blocks.STRIPPED_JUNGLE_WOOD);
        this.createHangingSign(Blocks.STRIPPED_JUNGLE_LOG, Blocks.JUNGLE_HANGING_SIGN, Blocks.JUNGLE_WALL_HANGING_SIGN);
        this.createPlantWithDefaultItem(Blocks.JUNGLE_SAPLING, Blocks.POTTED_JUNGLE_SAPLING, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createTintedLeaves(Blocks.JUNGLE_LEAVES, TexturedModel.LEAVES, -12012264);
        this.woodProvider(Blocks.CRIMSON_STEM).log(Blocks.CRIMSON_STEM).wood(Blocks.CRIMSON_HYPHAE);
        this.woodProvider(Blocks.STRIPPED_CRIMSON_STEM).log(Blocks.STRIPPED_CRIMSON_STEM).wood(Blocks.STRIPPED_CRIMSON_HYPHAE);
        this.createHangingSign(Blocks.STRIPPED_CRIMSON_STEM, Blocks.CRIMSON_HANGING_SIGN, Blocks.CRIMSON_WALL_HANGING_SIGN);
        this.createPlantWithDefaultItem(Blocks.CRIMSON_FUNGUS, Blocks.POTTED_CRIMSON_FUNGUS, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createNetherRoots(Blocks.CRIMSON_ROOTS, Blocks.POTTED_CRIMSON_ROOTS);
        this.woodProvider(Blocks.WARPED_STEM).log(Blocks.WARPED_STEM).wood(Blocks.WARPED_HYPHAE);
        this.woodProvider(Blocks.STRIPPED_WARPED_STEM).log(Blocks.STRIPPED_WARPED_STEM).wood(Blocks.STRIPPED_WARPED_HYPHAE);
        this.createHangingSign(Blocks.STRIPPED_WARPED_STEM, Blocks.WARPED_HANGING_SIGN, Blocks.WARPED_WALL_HANGING_SIGN);
        this.createPlantWithDefaultItem(Blocks.WARPED_FUNGUS, Blocks.POTTED_WARPED_FUNGUS, BlockModelGenerators.PlantType.NOT_TINTED);
        this.createNetherRoots(Blocks.WARPED_ROOTS, Blocks.POTTED_WARPED_ROOTS);
        this.woodProvider(Blocks.BAMBOO_BLOCK).logUVLocked(Blocks.BAMBOO_BLOCK);
        this.woodProvider(Blocks.STRIPPED_BAMBOO_BLOCK).logUVLocked(Blocks.STRIPPED_BAMBOO_BLOCK);
        this.createHangingSign(Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_HANGING_SIGN, Blocks.BAMBOO_WALL_HANGING_SIGN);
        this.createCrossBlock(Blocks.NETHER_SPROUTS, BlockModelGenerators.PlantType.NOT_TINTED);
        this.registerSimpleFlatItemModel(Items.NETHER_SPROUTS);
        this.createDoor(Blocks.IRON_DOOR);
        this.createTrapdoor(Blocks.IRON_TRAPDOOR);
        this.createSmoothStoneSlab();
        this.createPassiveRail(Blocks.RAIL);
        this.createActiveRail(Blocks.POWERED_RAIL);
        this.createActiveRail(Blocks.DETECTOR_RAIL);
        this.createActiveRail(Blocks.ACTIVATOR_RAIL);
        this.createComparator();
        this.createCommandBlock(Blocks.COMMAND_BLOCK);
        this.createCommandBlock(Blocks.REPEATING_COMMAND_BLOCK);
        this.createCommandBlock(Blocks.CHAIN_COMMAND_BLOCK);
        this.createAnvil(Blocks.ANVIL);
        this.createAnvil(Blocks.CHIPPED_ANVIL);
        this.createAnvil(Blocks.DAMAGED_ANVIL);
        this.createBarrel();
        this.createBell();
        this.createFurnace(Blocks.FURNACE, TexturedModel.ORIENTABLE_ONLY_TOP);
        this.createFurnace(Blocks.BLAST_FURNACE, TexturedModel.ORIENTABLE_ONLY_TOP);
        this.createFurnace(Blocks.SMOKER, TexturedModel.ORIENTABLE);
        this.createRedstoneWire();
        this.createRespawnAnchor();
        this.createSculkCatalyst();
        this.copyModel(Blocks.CHISELED_STONE_BRICKS, Blocks.INFESTED_CHISELED_STONE_BRICKS);
        this.copyModel(Blocks.COBBLESTONE, Blocks.INFESTED_COBBLESTONE);
        this.copyModel(Blocks.CRACKED_STONE_BRICKS, Blocks.INFESTED_CRACKED_STONE_BRICKS);
        this.copyModel(Blocks.MOSSY_STONE_BRICKS, Blocks.INFESTED_MOSSY_STONE_BRICKS);
        this.createInfestedStone();
        this.copyModel(Blocks.STONE_BRICKS, Blocks.INFESTED_STONE_BRICKS);
        this.createInfestedDeepslate();
    }

    protected void createLightBlock() {
        ItemModel.Unbaked itemmodel$unbaked = ItemModelUtils.plainModel(this.createFlatItemModel(Items.LIGHT));
        Map<Integer, ItemModel.Unbaked> map = new HashMap<>(16);
        PropertyDispatch.C1<MultiVariant, Integer> c1 = PropertyDispatch.initial(BlockStateProperties.LEVEL);

        for (int i = 0; i <= 15; i++) {
            String s = String.format(Locale.ROOT, "_%02d", i);
            ResourceLocation resourcelocation = TextureMapping.getItemTexture(Items.LIGHT, s);
            c1.select(i, plainVariant(ModelTemplates.PARTICLE_ONLY.createWithSuffix(Blocks.LIGHT, s, TextureMapping.particle(resourcelocation), this.modelOutput)));
            ItemModel.Unbaked itemmodel$unbaked1 = ItemModelUtils.plainModel(
                ModelTemplates.FLAT_ITEM
                    .create(ModelLocationUtils.getModelLocation(Items.LIGHT, s), TextureMapping.layer0(resourcelocation), this.modelOutput)
            );
            map.put(i, itemmodel$unbaked1);
        }

        this.itemModelOutput.accept(Items.LIGHT, ItemModelUtils.selectBlockItemProperty(LightBlock.LEVEL, itemmodel$unbaked, map));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(Blocks.LIGHT).with(c1));
    }

    protected void createCandleAndCandleCake(Block p_376372_, Block p_378320_) {
        this.registerSimpleFlatItemModel(p_376372_.asItem());
        TextureMapping texturemapping = TextureMapping.cube(TextureMapping.getBlockTexture(p_376372_));
        TextureMapping texturemapping1 = TextureMapping.cube(TextureMapping.getBlockTexture(p_376372_, "_lit"));
        MultiVariant multivariant = plainVariant(ModelTemplates.CANDLE.createWithSuffix(p_376372_, "_one_candle", texturemapping, this.modelOutput));
        MultiVariant multivariant1 = plainVariant(ModelTemplates.TWO_CANDLES.createWithSuffix(p_376372_, "_two_candles", texturemapping, this.modelOutput));
        MultiVariant multivariant2 = plainVariant(ModelTemplates.THREE_CANDLES.createWithSuffix(p_376372_, "_three_candles", texturemapping, this.modelOutput));
        MultiVariant multivariant3 = plainVariant(ModelTemplates.FOUR_CANDLES.createWithSuffix(p_376372_, "_four_candles", texturemapping, this.modelOutput));
        MultiVariant multivariant4 = plainVariant(ModelTemplates.CANDLE.createWithSuffix(p_376372_, "_one_candle_lit", texturemapping1, this.modelOutput));
        MultiVariant multivariant5 = plainVariant(ModelTemplates.TWO_CANDLES.createWithSuffix(p_376372_, "_two_candles_lit", texturemapping1, this.modelOutput));
        MultiVariant multivariant6 = plainVariant(ModelTemplates.THREE_CANDLES.createWithSuffix(p_376372_, "_three_candles_lit", texturemapping1, this.modelOutput));
        MultiVariant multivariant7 = plainVariant(ModelTemplates.FOUR_CANDLES.createWithSuffix(p_376372_, "_four_candles_lit", texturemapping1, this.modelOutput));
        this.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(p_376372_)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.CANDLES, BlockStateProperties.LIT)
                            .select(1, false, multivariant)
                            .select(2, false, multivariant1)
                            .select(3, false, multivariant2)
                            .select(4, false, multivariant3)
                            .select(1, true, multivariant4)
                            .select(2, true, multivariant5)
                            .select(3, true, multivariant6)
                            .select(4, true, multivariant7)
                    )
            );
        MultiVariant multivariant8 = plainVariant(ModelTemplates.CANDLE_CAKE.create(p_378320_, TextureMapping.candleCake(p_376372_, false), this.modelOutput));
        MultiVariant multivariant9 = plainVariant(ModelTemplates.CANDLE_CAKE.createWithSuffix(p_378320_, "_lit", TextureMapping.candleCake(p_376372_, true), this.modelOutput));
        this.blockStateOutput.accept(MultiVariantGenerator.dispatch(p_378320_).with(createBooleanModelDispatch(BlockStateProperties.LIT, multivariant9, multivariant8)));
    }

    @OnlyIn(Dist.CLIENT)
    public class BlockFamilyProvider {
        private final TextureMapping mapping;
        private final Map<ModelTemplate, ResourceLocation> models = new HashMap<>();
        @Nullable
        private BlockFamily family;
        @Nullable
        private Variant fullBlock;
        private final Set<Block> skipGeneratingModelsFor = new HashSet<>();

        public BlockFamilyProvider(final TextureMapping p_375997_) {
            this.mapping = p_375997_;
        }

        public BlockModelGenerators.BlockFamilyProvider fullBlock(Block p_378517_, ModelTemplate p_376200_) {
            this.fullBlock = BlockModelGenerators.plainModel(p_376200_.create(p_378517_, this.mapping, BlockModelGenerators.this.modelOutput));
            if (BlockModelGenerators.FULL_BLOCK_MODEL_CUSTOM_GENERATORS.containsKey(p_378517_)) {
                BlockModelGenerators.this.blockStateOutput
                    .accept(
                        BlockModelGenerators.FULL_BLOCK_MODEL_CUSTOM_GENERATORS.get(p_378517_).create(p_378517_, this.fullBlock, this.mapping, BlockModelGenerators.this.modelOutput)
                    );
            } else {
                BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(p_378517_, BlockModelGenerators.variant(this.fullBlock)));
            }

            return this;
        }

        public BlockModelGenerators.BlockFamilyProvider donateModelTo(Block p_375392_, Block p_375457_) {
            ResourceLocation resourcelocation = ModelLocationUtils.getModelLocation(p_375392_);
            BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(p_375457_, BlockModelGenerators.plainVariant(resourcelocation)));
            BlockModelGenerators.this.itemModelOutput.copy(p_375392_.asItem(), p_375457_.asItem());
            this.skipGeneratingModelsFor.add(p_375457_);
            return this;
        }

        public BlockModelGenerators.BlockFamilyProvider button(Block p_377478_) {
            MultiVariant multivariant = BlockModelGenerators.plainVariant(
                ModelTemplates.BUTTON.create(p_377478_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant1 = BlockModelGenerators.plainVariant(
                ModelTemplates.BUTTON_PRESSED.create(p_377478_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createButton(p_377478_, multivariant, multivariant1));
            ResourceLocation resourcelocation = ModelTemplates.BUTTON_INVENTORY.create(p_377478_, this.mapping, BlockModelGenerators.this.modelOutput);
            BlockModelGenerators.this.registerSimpleItemModel(p_377478_, resourcelocation);
            return this;
        }

        public BlockModelGenerators.BlockFamilyProvider wall(Block p_377084_) {
            MultiVariant multivariant = BlockModelGenerators.plainVariant(
                ModelTemplates.WALL_POST.create(p_377084_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant1 = BlockModelGenerators.plainVariant(
                ModelTemplates.WALL_LOW_SIDE.create(p_377084_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant2 = BlockModelGenerators.plainVariant(
                ModelTemplates.WALL_TALL_SIDE.create(p_377084_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createWall(p_377084_, multivariant, multivariant1, multivariant2));
            ResourceLocation resourcelocation = ModelTemplates.WALL_INVENTORY.create(p_377084_, this.mapping, BlockModelGenerators.this.modelOutput);
            BlockModelGenerators.this.registerSimpleItemModel(p_377084_, resourcelocation);
            return this;
        }

        public BlockModelGenerators.BlockFamilyProvider customFence(Block p_377251_) {
            TextureMapping texturemapping = TextureMapping.customParticle(p_377251_);
            MultiVariant multivariant = BlockModelGenerators.plainVariant(
                ModelTemplates.CUSTOM_FENCE_POST.create(p_377251_, texturemapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant1 = BlockModelGenerators.plainVariant(
                ModelTemplates.CUSTOM_FENCE_SIDE_NORTH.create(p_377251_, texturemapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant2 = BlockModelGenerators.plainVariant(
                ModelTemplates.CUSTOM_FENCE_SIDE_EAST.create(p_377251_, texturemapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant3 = BlockModelGenerators.plainVariant(
                ModelTemplates.CUSTOM_FENCE_SIDE_SOUTH.create(p_377251_, texturemapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant4 = BlockModelGenerators.plainVariant(
                ModelTemplates.CUSTOM_FENCE_SIDE_WEST.create(p_377251_, texturemapping, BlockModelGenerators.this.modelOutput)
            );
            BlockModelGenerators.this.blockStateOutput
                .accept(BlockModelGenerators.createCustomFence(p_377251_, multivariant, multivariant1, multivariant2, multivariant3, multivariant4));
            ResourceLocation resourcelocation = ModelTemplates.CUSTOM_FENCE_INVENTORY.create(p_377251_, texturemapping, BlockModelGenerators.this.modelOutput);
            BlockModelGenerators.this.registerSimpleItemModel(p_377251_, resourcelocation);
            return this;
        }

        public BlockModelGenerators.BlockFamilyProvider fence(Block p_378548_) {
            MultiVariant multivariant = BlockModelGenerators.plainVariant(
                ModelTemplates.FENCE_POST.create(p_378548_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant1 = BlockModelGenerators.plainVariant(
                ModelTemplates.FENCE_SIDE.create(p_378548_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createFence(p_378548_, multivariant, multivariant1));
            ResourceLocation resourcelocation = ModelTemplates.FENCE_INVENTORY.create(p_378548_, this.mapping, BlockModelGenerators.this.modelOutput);
            BlockModelGenerators.this.registerSimpleItemModel(p_378548_, resourcelocation);
            return this;
        }

        public BlockModelGenerators.BlockFamilyProvider customFenceGate(Block p_378765_) {
            TextureMapping texturemapping = TextureMapping.customParticle(p_378765_);
            MultiVariant multivariant = BlockModelGenerators.plainVariant(
                ModelTemplates.CUSTOM_FENCE_GATE_OPEN.create(p_378765_, texturemapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant1 = BlockModelGenerators.plainVariant(
                ModelTemplates.CUSTOM_FENCE_GATE_CLOSED.create(p_378765_, texturemapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant2 = BlockModelGenerators.plainVariant(
                ModelTemplates.CUSTOM_FENCE_GATE_WALL_OPEN.create(p_378765_, texturemapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant3 = BlockModelGenerators.plainVariant(
                ModelTemplates.CUSTOM_FENCE_GATE_WALL_CLOSED.create(p_378765_, texturemapping, BlockModelGenerators.this.modelOutput)
            );
            BlockModelGenerators.this.blockStateOutput
                .accept(BlockModelGenerators.createFenceGate(p_378765_, multivariant, multivariant1, multivariant2, multivariant3, false));
            return this;
        }

        public BlockModelGenerators.BlockFamilyProvider fenceGate(Block p_378252_) {
            MultiVariant multivariant = BlockModelGenerators.plainVariant(
                ModelTemplates.FENCE_GATE_OPEN.create(p_378252_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant1 = BlockModelGenerators.plainVariant(
                ModelTemplates.FENCE_GATE_CLOSED.create(p_378252_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant2 = BlockModelGenerators.plainVariant(
                ModelTemplates.FENCE_GATE_WALL_OPEN.create(p_378252_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant3 = BlockModelGenerators.plainVariant(
                ModelTemplates.FENCE_GATE_WALL_CLOSED.create(p_378252_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            BlockModelGenerators.this.blockStateOutput
                .accept(BlockModelGenerators.createFenceGate(p_378252_, multivariant, multivariant1, multivariant2, multivariant3, true));
            return this;
        }

        public BlockModelGenerators.BlockFamilyProvider pressurePlate(Block p_377761_) {
            MultiVariant multivariant = BlockModelGenerators.plainVariant(
                ModelTemplates.PRESSURE_PLATE_UP.create(p_377761_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            MultiVariant multivariant1 = BlockModelGenerators.plainVariant(
                ModelTemplates.PRESSURE_PLATE_DOWN.create(p_377761_, this.mapping, BlockModelGenerators.this.modelOutput)
            );
            BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createPressurePlate(p_377761_, multivariant, multivariant1));
            return this;
        }

        public BlockModelGenerators.BlockFamilyProvider sign(Block p_377458_) {
            if (this.family == null) {
                throw new IllegalStateException("Family not defined");
            } else {
                Block block = this.family.getVariants().get(BlockFamily.Variant.WALL_SIGN);
                MultiVariant multivariant = BlockModelGenerators.plainVariant(
                    ModelTemplates.PARTICLE_ONLY.create(p_377458_, this.mapping, BlockModelGenerators.this.modelOutput)
                );
                BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(p_377458_, multivariant));
                BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, multivariant));
                BlockModelGenerators.this.registerSimpleFlatItemModel(p_377458_.asItem());
                return this;
            }
        }

        public BlockModelGenerators.BlockFamilyProvider slab(Block p_377334_) {
            if (this.fullBlock == null) {
                throw new IllegalStateException("Full block not generated yet");
            } else {
                ResourceLocation resourcelocation = this.getOrCreateModel(ModelTemplates.SLAB_BOTTOM, p_377334_);
                MultiVariant multivariant = BlockModelGenerators.plainVariant(this.getOrCreateModel(ModelTemplates.SLAB_TOP, p_377334_));
                BlockModelGenerators.this.blockStateOutput
                    .accept(
                        BlockModelGenerators.createSlab(
                            p_377334_, BlockModelGenerators.plainVariant(resourcelocation), multivariant, BlockModelGenerators.variant(this.fullBlock)
                        )
                    );
                BlockModelGenerators.this.registerSimpleItemModel(p_377334_, resourcelocation);
                return this;
            }
        }

        public BlockModelGenerators.BlockFamilyProvider stairs(Block p_376299_) {
            MultiVariant multivariant = BlockModelGenerators.plainVariant(this.getOrCreateModel(ModelTemplates.STAIRS_INNER, p_376299_));
            ResourceLocation resourcelocation = this.getOrCreateModel(ModelTemplates.STAIRS_STRAIGHT, p_376299_);
            MultiVariant multivariant1 = BlockModelGenerators.plainVariant(this.getOrCreateModel(ModelTemplates.STAIRS_OUTER, p_376299_));
            BlockModelGenerators.this.blockStateOutput
                .accept(BlockModelGenerators.createStairs(p_376299_, multivariant, BlockModelGenerators.plainVariant(resourcelocation), multivariant1));
            BlockModelGenerators.this.registerSimpleItemModel(p_376299_, resourcelocation);
            return this;
        }

        protected BlockModelGenerators.BlockFamilyProvider fullBlockVariant(Block p_376700_) {
            TexturedModel texturedmodel = BlockModelGenerators.TEXTURED_MODELS.getOrDefault(p_376700_, TexturedModel.CUBE.get(p_376700_));
            MultiVariant multivariant = BlockModelGenerators.plainVariant(texturedmodel.create(p_376700_, BlockModelGenerators.this.modelOutput));
            BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(p_376700_, multivariant));
            return this;
        }

        protected BlockModelGenerators.BlockFamilyProvider door(Block p_378741_) {
            BlockModelGenerators.this.createDoor(p_378741_);
            return this;
        }

        protected void trapdoor(Block p_378286_) {
            if (BlockModelGenerators.NON_ORIENTABLE_TRAPDOOR.contains(p_378286_)) {
                BlockModelGenerators.this.createTrapdoor(p_378286_);
            } else {
                BlockModelGenerators.this.createOrientableTrapdoor(p_378286_);
            }
        }

        protected ResourceLocation getOrCreateModel(ModelTemplate p_375991_, Block p_376743_) {
            return this.models.computeIfAbsent(p_375991_, p_377362_ -> p_377362_.create(p_376743_, this.mapping, BlockModelGenerators.this.modelOutput));
        }

        public BlockModelGenerators.BlockFamilyProvider generateFor(BlockFamily p_376238_) {
            this.family = p_376238_;
            p_376238_.getVariants().forEach((p_375413_, p_375795_) -> {
                if (!this.skipGeneratingModelsFor.contains(p_375795_)) {
                    BiConsumer<BlockModelGenerators.BlockFamilyProvider, Block> biconsumer = BlockModelGenerators.SHAPE_CONSUMERS.get(p_375413_);
                    if (biconsumer != null) {
                        biconsumer.accept(this, p_375795_);
                    }
                }
            });
            return this;
        }
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    interface BlockStateGeneratorSupplier {
        BlockModelDefinitionGenerator create(
            Block p_375423_, Variant p_396561_, TextureMapping p_378118_, BiConsumer<ResourceLocation, ModelInstance> p_377645_
        );
    }

    @OnlyIn(Dist.CLIENT)
    record BookSlotModelCacheKey(ModelTemplate template, String modelSuffix) {
    }

    @OnlyIn(Dist.CLIENT)
    public static enum PlantType {
        TINTED(ModelTemplates.TINTED_CROSS, ModelTemplates.TINTED_FLOWER_POT_CROSS, false),
        NOT_TINTED(ModelTemplates.CROSS, ModelTemplates.FLOWER_POT_CROSS, false),
        EMISSIVE_NOT_TINTED(ModelTemplates.CROSS_EMISSIVE, ModelTemplates.FLOWER_POT_CROSS_EMISSIVE, true);

        private final ModelTemplate blockTemplate;
        private final ModelTemplate flowerPotTemplate;
        private final boolean isEmissive;

        private PlantType(final ModelTemplate p_376693_, final ModelTemplate p_377578_, final boolean p_378590_) {
            this.blockTemplate = p_376693_;
            this.flowerPotTemplate = p_377578_;
            this.isEmissive = p_378590_;
        }

        public ModelTemplate getCross() {
            return this.blockTemplate;
        }

        public ModelTemplate getCrossPot() {
            return this.flowerPotTemplate;
        }

        public ResourceLocation createItemModel(BlockModelGenerators p_378438_, Block p_377000_) {
            Item item = p_377000_.asItem();
            return this.isEmissive ? p_378438_.createFlatItemModelWithBlockTextureAndOverlay(item, p_377000_, "_emissive") : p_378438_.createFlatItemModelWithBlockTexture(item, p_377000_);
        }

        public TextureMapping getTextureMapping(Block p_377046_) {
            return this.isEmissive ? TextureMapping.crossEmissive(p_377046_) : TextureMapping.cross(p_377046_);
        }

        public TextureMapping getPlantTextureMapping(Block p_378688_) {
            return this.isEmissive ? TextureMapping.plantEmissive(p_378688_) : TextureMapping.plant(p_378688_);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public class WoodProvider {
        private final TextureMapping logMapping;

        public WoodProvider(final TextureMapping p_378405_) {
            this.logMapping = p_378405_;
        }

        public BlockModelGenerators.WoodProvider wood(Block p_376713_) {
            TextureMapping texturemapping = this.logMapping.copyAndUpdate(TextureSlot.END, this.logMapping.get(TextureSlot.SIDE));
            ResourceLocation resourcelocation = ModelTemplates.CUBE_COLUMN.create(p_376713_, texturemapping, BlockModelGenerators.this.modelOutput);
            BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createAxisAlignedPillarBlock(p_376713_, BlockModelGenerators.plainVariant(resourcelocation)));
            BlockModelGenerators.this.registerSimpleItemModel(p_376713_, resourcelocation);
            return this;
        }

        public BlockModelGenerators.WoodProvider log(Block p_378573_) {
            ResourceLocation resourcelocation = ModelTemplates.CUBE_COLUMN.create(p_378573_, this.logMapping, BlockModelGenerators.this.modelOutput);
            BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createAxisAlignedPillarBlock(p_378573_, BlockModelGenerators.plainVariant(resourcelocation)));
            BlockModelGenerators.this.registerSimpleItemModel(p_378573_, resourcelocation);
            return this;
        }

        public BlockModelGenerators.WoodProvider logWithHorizontal(Block p_376308_) {
            ResourceLocation resourcelocation = ModelTemplates.CUBE_COLUMN.create(p_376308_, this.logMapping, BlockModelGenerators.this.modelOutput);
            MultiVariant multivariant = BlockModelGenerators.plainVariant(
                ModelTemplates.CUBE_COLUMN_HORIZONTAL.create(p_376308_, this.logMapping, BlockModelGenerators.this.modelOutput)
            );
            BlockModelGenerators.this.blockStateOutput
                .accept(BlockModelGenerators.createRotatedPillarWithHorizontalVariant(p_376308_, BlockModelGenerators.plainVariant(resourcelocation), multivariant));
            BlockModelGenerators.this.registerSimpleItemModel(p_376308_, resourcelocation);
            return this;
        }

        public BlockModelGenerators.WoodProvider logUVLocked(Block p_376867_) {
            BlockModelGenerators.this.blockStateOutput.accept(BlockModelGenerators.createPillarBlockUVLocked(p_376867_, this.logMapping, BlockModelGenerators.this.modelOutput));
            BlockModelGenerators.this.registerSimpleItemModel(p_376867_, ModelTemplates.CUBE_COLUMN.create(p_376867_, this.logMapping, BlockModelGenerators.this.modelOutput));
            return this;
        }
    }
}
