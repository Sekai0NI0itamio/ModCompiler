package net.minecraft.client.resources.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SpecialBlockModelRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ModelManager implements PreparableReloadListener, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models");
    private static final Map<ResourceLocation, ResourceLocation> VANILLA_ATLASES = Map.of(
        Sheets.BANNER_SHEET,
        AtlasIds.BANNER_PATTERNS,
        Sheets.BED_SHEET,
        AtlasIds.BEDS,
        Sheets.CHEST_SHEET,
        AtlasIds.CHESTS,
        Sheets.SHIELD_SHEET,
        AtlasIds.SHIELD_PATTERNS,
        Sheets.SIGN_SHEET,
        AtlasIds.SIGNS,
        Sheets.SHULKER_SHEET,
        AtlasIds.SHULKER_BOXES,
        Sheets.ARMOR_TRIMS_SHEET,
        AtlasIds.ARMOR_TRIMS,
        Sheets.DECORATED_POT_SHEET,
        AtlasIds.DECORATED_POT,
        TextureAtlas.LOCATION_BLOCKS,
        AtlasIds.BLOCKS
    );
    private Map<ResourceLocation, ItemModel> bakedItemStackModels = Map.of();
    private Map<ResourceLocation, ItemModel> bakedItemStackModelsView = Map.of();
    private Map<ResourceLocation, ClientItem.Properties> itemProperties = Map.of();
    private final AtlasSet atlases;
    private final BlockModelShaper blockModelShaper;
    private final BlockColors blockColors;
    private EntityModelSet entityModelSet = EntityModelSet.EMPTY;
    private SpecialBlockModelRenderer specialBlockModelRenderer = SpecialBlockModelRenderer.EMPTY;
    private int maxMipmapLevels;
    private ModelBakery.MissingModels missingModels;
    private Object2IntMap<BlockState> modelGroups = Object2IntMaps.emptyMap();
    private ModelBakery modelBakery;

    public ModelManager(TextureManager p_119406_, BlockColors p_119407_, int p_119408_) {
        this.blockColors = p_119407_;
        this.maxMipmapLevels = p_119408_;
        this.blockModelShaper = new BlockModelShaper(this);
        this.atlases = new AtlasSet(VANILLA_ATLASES, p_119406_);
    }

    public BlockStateModel getMissingBlockStateModel() {
        return this.missingModels.block();
    }

    public ItemModel getItemModel(ResourceLocation p_376816_) {
        return this.bakedItemStackModels.getOrDefault(p_376816_, this.missingModels.item());
    }

    public Map<ResourceLocation, ItemModel> getItemModels() {
        return this.bakedItemStackModelsView;
    }

    public ClientItem.Properties getItemProperties(ResourceLocation p_378319_) {
        return this.itemProperties.getOrDefault(p_378319_, ClientItem.Properties.DEFAULT);
    }

    public BlockModelShaper getBlockModelShaper() {
        return this.blockModelShaper;
    }

    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier p_249079_, ResourceManager p_251134_, Executor p_250550_, Executor p_249221_
    ) {
        net.minecraftforge.client.model.geometry.GeometryLoaderManager.init();
        CompletableFuture<EntityModelSet> completablefuture = CompletableFuture.supplyAsync(EntityModelSet::vanilla, p_250550_);
        CompletableFuture<SpecialBlockModelRenderer> completablefuture1 = completablefuture.thenApplyAsync(SpecialBlockModelRenderer::vanilla, p_250550_);
        CompletableFuture<Map<ResourceLocation, UnbakedModel>> completablefuture2 = loadBlockModels(p_251134_, p_250550_);
        CompletableFuture<BlockStateModelLoader.LoadedModels> completablefuture3 = BlockStateModelLoader.loadBlockStates(p_251134_, p_250550_);
        CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> completablefuture4 = ClientItemInfoLoader.scheduleLoad(p_251134_, p_250550_);
        CompletableFuture<ModelManager.ResolvedModels> completablefuture5 = CompletableFuture.allOf(completablefuture2, completablefuture3, completablefuture4)
            .thenApplyAsync(p_389625_ -> discoverModelDependencies(completablefuture2.join(), completablefuture3.join(), completablefuture4.join()), p_250550_);
        CompletableFuture<Object2IntMap<BlockState>> completablefuture6 = completablefuture3.thenApplyAsync(
            p_358038_ -> buildModelGroups(this.blockColors, p_358038_), p_250550_
        );
        Map<ResourceLocation, CompletableFuture<AtlasSet.StitchResult>> map = this.atlases.scheduleLoad(p_251134_, this.maxMipmapLevels, p_250550_);
        return CompletableFuture.allOf(
                Stream.concat(
                        map.values().stream(),
                        Stream.of(
                            completablefuture5,
                            completablefuture6,
                            completablefuture3,
                            completablefuture4,
                            completablefuture,
                            completablefuture1,
                            completablefuture2
                        )
                    )
                    .toArray(CompletableFuture[]::new)
            )
            .thenComposeAsync(
                p_389621_ -> {
                    Map<ResourceLocation, AtlasSet.StitchResult> map1 = Util.mapValues(map, CompletableFuture::join);
                    ModelManager.ResolvedModels modelmanager$resolvedmodels = completablefuture5.join();
                    Object2IntMap<BlockState> object2intmap = completablefuture6.join();
                    Set<ResourceLocation> set = Sets.difference(completablefuture2.join().keySet(), modelmanager$resolvedmodels.models.keySet());
                    if (!set.isEmpty()) {
                        LOGGER.debug(
                            "Unreferenced models: \n{}", set.stream().sorted().map(p_374723_ -> "\t" + p_374723_ + "\n").collect(Collectors.joining())
                        );
                    }

                    ModelBakery modelbakery = new ModelBakery(
                        completablefuture.join(),
                        completablefuture3.join().models(),
                        completablefuture4.join().contents(),
                        modelmanager$resolvedmodels.models(),
                        modelmanager$resolvedmodels.missing()
                    );
                    return loadModels(map1, modelbakery, object2intmap, completablefuture.join(), completablefuture1.join(), p_250550_);
                },
                p_250550_
            )
            .thenCompose(p_252255_ -> p_252255_.readyForUpload.thenApply(p_251581_ -> (ModelManager.ReloadState)p_252255_))
            .thenCompose(p_249079_::wait)
            .thenAcceptAsync(p_358039_ -> this.apply(p_358039_, Profiler.get()), p_249221_);
    }

    private static CompletableFuture<Map<ResourceLocation, UnbakedModel>> loadBlockModels(ResourceManager p_251361_, Executor p_252189_) {
        return CompletableFuture.<Map<ResourceLocation, Resource>>supplyAsync(() -> MODEL_LISTER.listMatchingResources(p_251361_), p_252189_)
            .thenCompose(
                p_250597_ -> {
                    List<CompletableFuture<Pair<ResourceLocation, BlockModel>>> list = new ArrayList<>(p_250597_.size());

                    for (Entry<ResourceLocation, Resource> entry : p_250597_.entrySet()) {
                        list.add(CompletableFuture.supplyAsync(() -> {
                            ResourceLocation resourcelocation = MODEL_LISTER.fileToId(entry.getKey());

                            try {
                                Pair pair;
                                try (Reader reader = entry.getValue().openAsReader()) {
                                    pair = Pair.of(resourcelocation, BlockModel.fromStream(reader));
                                }

                                return pair;
                            } catch (Exception exception) {
                                LOGGER.error("Failed to load model {}", entry.getKey(), exception);
                                return null;
                            }
                        }, p_252189_));
                    }

                    return Util.sequence(list)
                        .thenApply(
                            p_250813_ -> p_250813_.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond))
                        );
                }
            );
    }

    private static ModelManager.ResolvedModels discoverModelDependencies(
        Map<ResourceLocation, UnbakedModel> p_360749_, BlockStateModelLoader.LoadedModels p_366446_, ClientItemInfoLoader.LoadedClientInfos p_378505_
    ) {
        ModelManager.ResolvedModels modelmanager$resolvedmodels;
        try (Zone zone = Profiler.get().zone("dependencies")) {
            ModelDiscovery modeldiscovery = new ModelDiscovery(p_360749_, MissingBlockModel.missingModel());
            modeldiscovery.addSpecialModel(ItemModelGenerator.GENERATED_ITEM_MODEL_ID, new ItemModelGenerator());
            p_366446_.models().values().forEach(modeldiscovery::addRoot);
            p_378505_.contents().values().forEach(p_374734_ -> modeldiscovery.addRoot(p_374734_.model()));
            modelmanager$resolvedmodels = new ModelManager.ResolvedModels(modeldiscovery.missingModel(), modeldiscovery.resolve());
        }

        return modelmanager$resolvedmodels;
    }

    private static CompletableFuture<ModelManager.ReloadState> loadModels(
        final Map<ResourceLocation, AtlasSet.StitchResult> p_250646_,
        ModelBakery p_248945_,
        Object2IntMap<BlockState> p_361513_,
        EntityModelSet p_378097_,
        SpecialBlockModelRenderer p_377275_,
        Executor p_394729_
    ) {
        CompletableFuture<Void> completablefuture = CompletableFuture.allOf(
            p_250646_.values().stream().map(AtlasSet.StitchResult::readyForUpload).toArray(CompletableFuture[]::new)
        );
        final Multimap<String, Material> multimap = Multimaps.synchronizedMultimap(HashMultimap.create());
        final Multimap<String, String> multimap1 = Multimaps.synchronizedMultimap(HashMultimap.create());
        return p_248945_.bakeModels(new SpriteGetter() {
                private final TextureAtlasSprite missingSprite = p_250646_.get(TextureAtlas.LOCATION_BLOCKS).missing();

                @Override
                public TextureAtlasSprite get(Material p_375858_, ModelDebugName p_375833_) {
                    AtlasSet.StitchResult atlasset$stitchresult = p_250646_.get(p_375858_.atlasLocation());
                    TextureAtlasSprite textureatlassprite = atlasset$stitchresult.getSprite(p_375858_.texture());
                    if (textureatlassprite != null) {
                        return textureatlassprite;
                    } else {
                        multimap.put(p_375833_.debugName(), p_375858_);
                        return atlasset$stitchresult.missing();
                    }
                }

                @Override
                public TextureAtlasSprite reportMissingReference(String p_378821_, ModelDebugName p_377684_) {
                    multimap1.put(p_377684_.debugName(), p_378821_);
                    return this.missingSprite;
                }
            }, p_394729_)
            .thenApply(
                p_389636_ -> {
                    net.minecraftforge.client.ForgeHooksClient.onModifyBakingResult(p_248945_, p_389636_);
                    multimap.asMap()
                        .forEach(
                            (p_376688_, p_252017_) -> LOGGER.warn(
                                "Missing textures in model {}:\n{}",
                                p_376688_,
                                p_252017_.stream()
                                    .sorted(Material.COMPARATOR)
                                    .map(p_325574_ -> "    " + p_325574_.atlasLocation() + ":" + p_325574_.texture())
                                    .collect(Collectors.joining("\n"))
                            )
                        );
                    multimap1.asMap()
                        .forEach(
                            (p_374739_, p_374740_) -> LOGGER.warn(
                                "Missing texture references in model {}:\n{}",
                                p_374739_,
                                p_374740_.stream().sorted().map(p_374742_ -> "    " + p_374742_).collect(Collectors.joining("\n"))
                            )
                        );
                    Map<BlockState, BlockStateModel> map = createBlockStateToModelDispatch(p_389636_.blockStateModels(), p_389636_.missingModels().block());
                    return new ModelManager.ReloadState(p_389636_, p_361513_, map, p_250646_, p_378097_, p_377275_, completablefuture, p_248945_);
                }
            );
    }

    private static Map<BlockState, BlockStateModel> createBlockStateToModelDispatch(Map<BlockState, BlockStateModel> p_377857_, BlockStateModel p_396223_) {
        Object object;
        try (Zone zone = Profiler.get().zone("block state dispatch")) {
            Map<BlockState, BlockStateModel> map = new IdentityHashMap<>(p_377857_);

            for (Block block : BuiltInRegistries.BLOCK) {
                block.getStateDefinition().getPossibleStates().forEach(p_389628_ -> {
                    if (p_377857_.putIfAbsent(p_389628_, p_396223_) == null) {
                        LOGGER.warn("Missing model for variant: '{}'", p_389628_);
                    }
                });
            }

            object = map;
        }

        return (Map<BlockState, BlockStateModel>)object;
    }

    private static Object2IntMap<BlockState> buildModelGroups(BlockColors p_369941_, BlockStateModelLoader.LoadedModels p_360724_) {
        Object2IntMap object2intmap;
        try (Zone zone = Profiler.get().zone("block groups")) {
            object2intmap = ModelGroupCollector.build(p_369941_, p_360724_);
        }

        return object2intmap;
    }

    private void apply(ModelManager.ReloadState p_248996_, ProfilerFiller p_251960_) {
        p_251960_.push("upload");
        p_248996_.atlasPreparations.values().forEach(AtlasSet.StitchResult::upload);
        ModelBakery.BakingResult modelbakery$bakingresult = p_248996_.bakedModels;
        // TODO [BlockState Models] fix
        //this.bakedBlockStateModelsView = java.util.Collections.unmodifiableMap(this.bakedBlockStateModels);
        this.bakedItemStackModels = modelbakery$bakingresult.itemStackModels();
        this.bakedItemStackModelsView = java.util.Collections.unmodifiableMap(this.bakedItemStackModels);
        this.itemProperties = modelbakery$bakingresult.itemProperties();
        this.modelGroups = p_248996_.modelGroups;
        this.missingModels = modelbakery$bakingresult.missingModels();
        this.modelBakery = p_248996_.modelBakery();
        net.minecraftforge.client.ForgeHooksClient.onModelBake(this, this.modelBakery);
        p_251960_.popPush("cache");
        this.blockModelShaper.replaceCache(p_248996_.modelCache);
        this.specialBlockModelRenderer = p_248996_.specialBlockModelRenderer;
        this.entityModelSet = p_248996_.entityModelSet;
        p_251960_.pop();
    }

    public boolean requiresRender(BlockState p_119416_, BlockState p_119417_) {
        if (p_119416_ == p_119417_) {
            return false;
        } else {
            int i = this.modelGroups.getInt(p_119416_);
            if (i != -1) {
                int j = this.modelGroups.getInt(p_119417_);
                if (i == j) {
                    FluidState fluidstate = p_119416_.getFluidState();
                    FluidState fluidstate1 = p_119417_.getFluidState();
                    return fluidstate != fluidstate1;
                }
            }

            return true;
        }
    }

    public TextureAtlas getAtlas(ResourceLocation p_119429_) {
        if (this.atlases == null) throw new RuntimeException("getAtlasTexture called too early!");
        return this.atlases.getAtlas(p_119429_);
    }

    @Override
    public void close() {
        this.atlases.close();
    }

    public void updateMaxMipLevel(int p_119411_) {
        this.maxMipmapLevels = p_119411_;
    }

    public ModelBakery getModelBakery() {
        return com.google.common.base.Preconditions.checkNotNull(modelBakery, "Attempted to query model bakery before it has been initialized.");
    }

    public Supplier<SpecialBlockModelRenderer> specialBlockModelRenderer() {
        return () -> this.specialBlockModelRenderer;
    }

    public Supplier<EntityModelSet> entityModels() {
        return () -> this.entityModelSet;
    }

    @OnlyIn(Dist.CLIENT)
    record ReloadState(
        ModelBakery.BakingResult bakedModels,
        Object2IntMap<BlockState> modelGroups,
        Map<BlockState, BlockStateModel> modelCache,
        Map<ResourceLocation, AtlasSet.StitchResult> atlasPreparations,
        EntityModelSet entityModelSet,
        SpecialBlockModelRenderer specialBlockModelRenderer,
        CompletableFuture<Void> readyForUpload,
        ModelBakery modelBakery
    ) {
    }

    @OnlyIn(Dist.CLIENT)
    record ResolvedModels(ResolvedModel missing, Map<ResourceLocation, ResolvedModel> models) {
    }
}
