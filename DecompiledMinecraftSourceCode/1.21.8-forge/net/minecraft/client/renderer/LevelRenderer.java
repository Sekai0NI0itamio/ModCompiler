package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionBuffers;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class LevelRenderer implements ResourceManagerReloadListener, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TRANSPARENCY_POST_CHAIN_ID = ResourceLocation.withDefaultNamespace("transparency");
    private static final ResourceLocation ENTITY_OUTLINE_POST_CHAIN_ID = ResourceLocation.withDefaultNamespace("entity_outline");
    public static final int SECTION_SIZE = 16;
    public static final int HALF_SECTION_SIZE = 8;
    public static final int NEARBY_SECTION_DISTANCE_IN_BLOCKS = 32;
    private static final int MINIMUM_TRANSPARENT_SORT_COUNT = 15;
    private static final Comparator<Entity> ENTITY_COMPARATOR = Comparator.comparing(p_404929_ -> p_404929_.getType().hashCode());
    private final Minecraft minecraft;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
    private final RenderBuffers renderBuffers;
    private final SkyRenderer skyRenderer = new SkyRenderer();
    private final CloudRenderer cloudRenderer = new CloudRenderer();
    private final WorldBorderRenderer worldBorderRenderer = new WorldBorderRenderer();
    private WeatherEffectRenderer weatherEffectRenderer = new WeatherEffectRenderer();
    @Nullable
    private ClientLevel level;
    private final SectionOcclusionGraph sectionOcclusionGraph = new SectionOcclusionGraph();
    private final ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections = new ObjectArrayList<>(10000);
    private final ObjectArrayList<SectionRenderDispatcher.RenderSection> nearbyVisibleSections = new ObjectArrayList<>(50);
    @Nullable
    private ViewArea viewArea;
    private int ticks;
    private final Int2ObjectMap<BlockDestructionProgress> destroyingBlocks = new Int2ObjectOpenHashMap<>();
    private final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress = new Long2ObjectOpenHashMap<>();
    @Nullable
    private RenderTarget entityOutlineTarget;
    private final LevelTargetBundle targets = new LevelTargetBundle();
    private int lastCameraSectionX = Integer.MIN_VALUE;
    private int lastCameraSectionY = Integer.MIN_VALUE;
    private int lastCameraSectionZ = Integer.MIN_VALUE;
    private double prevCamX = Double.MIN_VALUE;
    private double prevCamY = Double.MIN_VALUE;
    private double prevCamZ = Double.MIN_VALUE;
    private double prevCamRotX = Double.MIN_VALUE;
    private double prevCamRotY = Double.MIN_VALUE;
    @Nullable
    private SectionRenderDispatcher sectionRenderDispatcher;
    private int lastViewDistance = -1;
    private final List<Entity> visibleEntities = new ArrayList<>();
    private int visibleEntityCount;
    private Frustum cullingFrustum;
    private boolean captureFrustum;
    @Nullable
    private Frustum capturedFrustum;
    @Nullable
    private BlockPos lastTranslucentSortBlockPos;
    private int translucencyResortIterationIndex;

    public LevelRenderer(Minecraft p_234245_, EntityRenderDispatcher p_234246_, BlockEntityRenderDispatcher p_234247_, RenderBuffers p_234248_) {
        this.minecraft = p_234245_;
        this.entityRenderDispatcher = p_234246_;
        this.blockEntityRenderDispatcher = p_234247_;
        this.renderBuffers = p_234248_;
        net.minecraftforge.client.event.ForgeEventFactoryClient.onInitLevelRenderer();
    }

    public void tickParticles(Camera p_369538_) {
        this.weatherEffectRenderer.tickRainParticles(this.minecraft.level, p_369538_, this.ticks, this.minecraft.options.particles().get());
    }

    @Override
    public void close() {
        if (this.entityOutlineTarget != null) {
            this.entityOutlineTarget.destroyBuffers();
        }

        this.skyRenderer.close();
        this.cloudRenderer.close();
    }

    @Override
    public void onResourceManagerReload(ResourceManager p_109513_) {
        this.initOutline();
    }

    public void initOutline() {
        if (this.entityOutlineTarget != null) {
            this.entityOutlineTarget.destroyBuffers();
        }

        this.entityOutlineTarget = new TextureTarget("Entity Outline", this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(), true);
    }

    @Nullable
    private PostChain getTransparencyChain() {
        if (!Minecraft.useShaderTransparency()) {
            return null;
        } else {
            PostChain postchain = this.minecraft.getShaderManager().getPostChain(TRANSPARENCY_POST_CHAIN_ID, LevelTargetBundle.SORTING_TARGETS);
            if (postchain == null) {
                this.minecraft.options.graphicsMode().set(GraphicsStatus.FANCY);
                this.minecraft.options.save();
            }

            return postchain;
        }
    }

    public void doEntityOutline() {
        if (this.shouldShowEntityOutlines()) {
            this.entityOutlineTarget.blitAndBlendToTexture(this.minecraft.getMainRenderTarget().getColorTextureView());
        }
    }

    public boolean shouldShowEntityOutlines() {
        return !this.minecraft.gameRenderer.isPanoramicMode() && this.entityOutlineTarget != null && this.minecraft.player != null;
    }

    public void setLevel(@Nullable ClientLevel p_109702_) {
        this.lastCameraSectionX = Integer.MIN_VALUE;
        this.lastCameraSectionY = Integer.MIN_VALUE;
        this.lastCameraSectionZ = Integer.MIN_VALUE;
        this.entityRenderDispatcher.setLevel(p_109702_);
        this.level = p_109702_;
        if (p_109702_ != null) {
            this.allChanged();
        } else {
            if (this.viewArea != null) {
                this.viewArea.releaseAllBuffers();
                this.viewArea = null;
            }

            if (this.sectionRenderDispatcher != null) {
                this.sectionRenderDispatcher.dispose();
            }

            this.sectionRenderDispatcher = null;
            this.sectionOcclusionGraph.waitAndReset(null);
            this.clearVisibleSections();
        }
    }

    private void clearVisibleSections() {
        this.visibleSections.clear();
        this.nearbyVisibleSections.clear();
    }

    public void allChanged() {
        if (this.level != null) {
            this.level.clearTintCaches();
            if (this.sectionRenderDispatcher == null) {
                this.sectionRenderDispatcher = new SectionRenderDispatcher(
                    this.level, this, Util.backgroundExecutor(), this.renderBuffers, this.minecraft.getBlockRenderer(), this.minecraft.getBlockEntityRenderDispatcher()
                );
            } else {
                this.sectionRenderDispatcher.setLevel(this.level);
            }

            this.cloudRenderer.markForRebuild();
            ItemBlockRenderTypes.setFancy(Minecraft.useFancyGraphics());
            this.lastViewDistance = this.minecraft.options.getEffectiveRenderDistance();
            if (this.viewArea != null) {
                this.viewArea.releaseAllBuffers();
            }

            this.sectionRenderDispatcher.clearCompileQueue();
            this.viewArea = new ViewArea(this.sectionRenderDispatcher, this.level, this.minecraft.options.getEffectiveRenderDistance(), this);
            this.sectionOcclusionGraph.waitAndReset(this.viewArea);
            this.clearVisibleSections();
            Camera camera = this.minecraft.gameRenderer.getMainCamera();
            this.viewArea.repositionCamera(SectionPos.of(camera.getPosition()));
        }
    }

    public void resize(int p_109488_, int p_109489_) {
        this.needsUpdate();
        if (this.entityOutlineTarget != null) {
            this.entityOutlineTarget.resize(p_109488_, p_109489_);
        }
    }

    public String getSectionStatistics() {
        int i = this.viewArea.sections.length;
        int j = this.countRenderedSections();
        return String.format(
            Locale.ROOT,
            "C: %d/%d %sD: %d, %s",
            j,
            i,
            this.minecraft.smartCull ? "(s) " : "",
            this.lastViewDistance,
            this.sectionRenderDispatcher == null ? "null" : this.sectionRenderDispatcher.getStats()
        );
    }

    public SectionRenderDispatcher getSectionRenderDispatcher() {
        return this.sectionRenderDispatcher;
    }

    public double getTotalSections() {
        return this.viewArea.sections.length;
    }

    public double getLastViewDistance() {
        return this.lastViewDistance;
    }

    public int countRenderedSections() {
        int i = 0;

        for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection : this.visibleSections) {
            if (sectionrenderdispatcher$rendersection.getSectionMesh().hasRenderableLayers()) {
                i++;
            }
        }

        return i;
    }

    public String getEntityStatistics() {
        return "E: " + this.visibleEntityCount + "/" + this.level.getEntityCount() + ", SD: " + this.level.getServerSimulationDistance();
    }

    private void setupRender(Camera p_194339_, Frustum p_194340_, boolean p_194341_, boolean p_194342_) {
        Vec3 vec3 = p_194339_.getPosition();
        if (this.minecraft.options.getEffectiveRenderDistance() != this.lastViewDistance) {
            this.allChanged();
        }

        ProfilerFiller profilerfiller = Profiler.get();
        profilerfiller.push("camera");
        int i = SectionPos.posToSectionCoord(vec3.x());
        int j = SectionPos.posToSectionCoord(vec3.y());
        int k = SectionPos.posToSectionCoord(vec3.z());
        if (this.lastCameraSectionX != i || this.lastCameraSectionY != j || this.lastCameraSectionZ != k) {
            this.lastCameraSectionX = i;
            this.lastCameraSectionY = j;
            this.lastCameraSectionZ = k;
            this.viewArea.repositionCamera(SectionPos.of(vec3));
            this.worldBorderRenderer.invalidate();
        }

        this.sectionRenderDispatcher.setCameraPosition(vec3);
        profilerfiller.popPush("cull");
        double d0 = Math.floor(vec3.x / 8.0);
        double d1 = Math.floor(vec3.y / 8.0);
        double d2 = Math.floor(vec3.z / 8.0);
        if (d0 != this.prevCamX || d1 != this.prevCamY || d2 != this.prevCamZ) {
            this.sectionOcclusionGraph.invalidate();
        }

        this.prevCamX = d0;
        this.prevCamY = d1;
        this.prevCamZ = d2;
        profilerfiller.popPush("update");
        if (!p_194341_) {
            boolean flag = this.minecraft.smartCull;
            if (p_194342_ && this.level.getBlockState(p_194339_.getBlockPosition()).isSolidRender()) {
                flag = false;
            }

            profilerfiller.push("section_occlusion_graph");
            this.sectionOcclusionGraph.update(flag, p_194339_, p_194340_, this.visibleSections, this.level.getChunkSource().getLoadedEmptySections());
            profilerfiller.pop();
            double d3 = Math.floor(p_194339_.getXRot() / 2.0F);
            double d4 = Math.floor(p_194339_.getYRot() / 2.0F);
            if (this.sectionOcclusionGraph.consumeFrustumUpdate() || d3 != this.prevCamRotX || d4 != this.prevCamRotY) {
                this.applyFrustum(offsetFrustum(p_194340_));
                this.prevCamRotX = d3;
                this.prevCamRotY = d4;
            }
        }

        profilerfiller.pop();
    }

    public static Frustum offsetFrustum(Frustum p_298803_) {
        return new Frustum(p_298803_).offsetToFullyIncludeCameraCube(8);
    }

    private void applyFrustum(Frustum p_194355_) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("applyFrustum called from wrong thread: " + Thread.currentThread().getName());
        } else {
            Profiler.get().push("apply_frustum");
            this.clearVisibleSections();
            this.sectionOcclusionGraph.addSectionsInFrustum(p_194355_, this.visibleSections, this.nearbyVisibleSections);
            Profiler.get().pop();
        }
    }

    public void addRecentlyCompiledSection(SectionRenderDispatcher.RenderSection p_301248_) {
        this.sectionOcclusionGraph.schedulePropagationFrom(p_301248_);
    }

    public void prepareCullFrustum(Vec3 p_253766_, Matrix4f p_254341_, Matrix4f p_332544_) {
        this.cullingFrustum = new Frustum(p_254341_, p_332544_);
        this.cullingFrustum.prepare(p_253766_.x(), p_253766_.y(), p_253766_.z());
    }

    public void renderLevel(
        GraphicsResourceAllocator p_367325_,
        DeltaTracker p_342180_,
        boolean p_109603_,
        Camera p_109604_,
        Matrix4f p_254120_,
        Matrix4f p_330527_,
        GpuBufferSlice p_407881_,
        Vector4f p_410175_,
        boolean p_407316_
    ) {
        float f = p_342180_.getGameTimeDeltaPartialTick(false);
        this.blockEntityRenderDispatcher.prepare(this.level, p_109604_, this.minecraft.hitResult);
        this.entityRenderDispatcher.prepare(this.level, p_109604_, this.minecraft.crosshairPickEntity);
        final ProfilerFiller profilerfiller = Profiler.get();
        profilerfiller.push("light_update_queue");
        this.level.pollLightUpdates();
        profilerfiller.popPush("light_updates");
        this.level.getChunkSource().getLightEngine().runLightUpdates();
        Vec3 vec3 = p_109604_.getPosition();
        double d0 = vec3.x();
        double d1 = vec3.y();
        double d2 = vec3.z();
        profilerfiller.popPush("culling");
        boolean flag = this.capturedFrustum != null;
        Frustum frustum = flag ? this.capturedFrustum : this.cullingFrustum;
        profilerfiller.popPush("captureFrustum");
        if (this.captureFrustum) {
            this.capturedFrustum = flag ? new Frustum(p_254120_, p_330527_) : frustum;
            this.capturedFrustum.prepare(d0, d1, d2);
            this.captureFrustum = false;
        }

        profilerfiller.popPush("cullEntities");
        boolean flag1 = this.collectVisibleEntities(p_109604_, frustum, this.visibleEntities);
        this.visibleEntityCount = this.visibleEntities.size();
        profilerfiller.popPush("terrain_setup");
        this.setupRender(p_109604_, frustum, flag, this.minecraft.player.isSpectator());
        profilerfiller.popPush("compile_sections");
        this.compileSections(p_109604_);
        Matrix4fStack matrix4fstack = RenderSystem.getModelViewStack();
        matrix4fstack.pushMatrix();
        matrix4fstack.mul(p_254120_);
        FrameGraphBuilder framegraphbuilder = new FrameGraphBuilder();
        this.targets.main = framegraphbuilder.importExternal("main", this.minecraft.getMainRenderTarget());
        int i = this.minecraft.getMainRenderTarget().width;
        int j = this.minecraft.getMainRenderTarget().height;
        RenderTargetDescriptor rendertargetdescriptor = new RenderTargetDescriptor(i, j, true, 0);
        PostChain postchain = this.getTransparencyChain();
        if (postchain != null) {
            this.targets.translucent = framegraphbuilder.createInternal("translucent", rendertargetdescriptor);
            this.targets.itemEntity = framegraphbuilder.createInternal("item_entity", rendertargetdescriptor);
            this.targets.particles = framegraphbuilder.createInternal("particles", rendertargetdescriptor);
            this.targets.weather = framegraphbuilder.createInternal("weather", rendertargetdescriptor);
            this.targets.clouds = framegraphbuilder.createInternal("clouds", rendertargetdescriptor);
        }

        if (this.entityOutlineTarget != null) {
            this.targets.entityOutline = framegraphbuilder.importExternal("entity_outline", this.entityOutlineTarget);
        }

        FramePass framepass = framegraphbuilder.addPass("clear");
        this.targets.main = framepass.readsAndWrites(this.targets.main);
        framepass.executes(
            () -> {
                RenderTarget rendertarget = this.minecraft.getMainRenderTarget();
                RenderSystem.getDevice()
                    .createCommandEncoder()
                    .clearColorAndDepthTextures(
                        rendertarget.getColorTexture(), ARGB.colorFromFloat(0.0F, p_410175_.x, p_410175_.y, p_410175_.z), rendertarget.getDepthTexture(), 1.0
                    );
            }
        );
        if (p_407316_) {
            this.addSkyPass(framegraphbuilder, p_109604_, f, p_407881_);
        }

        this.addMainPass(framegraphbuilder, frustum, p_109604_, p_254120_, p_407881_, p_109603_, flag1, p_342180_, profilerfiller);
        PostChain postchain1 = this.minecraft.getShaderManager().getPostChain(ENTITY_OUTLINE_POST_CHAIN_ID, LevelTargetBundle.OUTLINE_TARGETS);
        if (flag1 && postchain1 != null) {
            postchain1.addToFrame(framegraphbuilder, i, j, this.targets);
        }

        this.addParticlesPass(framegraphbuilder, p_109604_, f, p_407881_, frustum);
        CloudStatus cloudstatus = this.minecraft.options.getCloudsType();
        if (cloudstatus != CloudStatus.OFF) {
            Optional<Integer> optional = this.level.dimensionType().cloudHeight();
            if (optional.isPresent()) {
                float f1 = this.ticks + f;
                int k = this.level.getCloudColor(f);
                this.addCloudsPass(framegraphbuilder, cloudstatus, p_109604_.getPosition(), f1, k, optional.get().intValue() + 0.33F);
            }
        }

        this.addWeatherPass(framegraphbuilder, p_109604_.getPosition(), f, p_407881_);
        if (postchain != null) {
            postchain.addToFrame(framegraphbuilder, i, j, this.targets);
        }

        this.addLateDebugPass(framegraphbuilder, vec3, p_407881_);
        net.minecraftforge.client.FramePassManager.insertForgePasses(framegraphbuilder, targets); // Forge: Modded passes are inserted here.
        profilerfiller.popPush("framegraph");
        framegraphbuilder.execute(p_367325_, new FrameGraphBuilder.Inspector() {
            @Override
            public void beforeExecutePass(String p_367748_) {
                profilerfiller.push(p_367748_);
            }

            @Override
            public void afterExecutePass(String p_367757_) {
                profilerfiller.pop();
            }
        });
        this.visibleEntities.clear();
        this.targets.clear();
        matrix4fstack.popMatrix();
        profilerfiller.pop();
    }

    private void addMainPass(
        FrameGraphBuilder p_365119_,
        Frustum p_363733_,
        Camera p_364769_,
        Matrix4f p_361439_,
        GpuBufferSlice p_407574_,
        boolean p_362593_,
        boolean p_368830_,
        DeltaTracker p_365046_,
        ProfilerFiller p_369478_
    ) {
        FramePass framepass = p_365119_.addPass("main");
        this.targets.main = framepass.readsAndWrites(this.targets.main);
        if (this.targets.translucent != null) {
            this.targets.translucent = framepass.readsAndWrites(this.targets.translucent);
        }

        if (this.targets.itemEntity != null) {
            this.targets.itemEntity = framepass.readsAndWrites(this.targets.itemEntity);
        }

        if (this.targets.weather != null) {
            this.targets.weather = framepass.readsAndWrites(this.targets.weather);
        }

        if (p_368830_ && this.targets.entityOutline != null) {
            this.targets.entityOutline = framepass.readsAndWrites(this.targets.entityOutline);
        }

        ResourceHandle<RenderTarget> resourcehandle = this.targets.main;
        ResourceHandle<RenderTarget> resourcehandle1 = this.targets.translucent;
        ResourceHandle<RenderTarget> resourcehandle2 = this.targets.itemEntity;
        ResourceHandle<RenderTarget> resourcehandle3 = this.targets.entityOutline;
        framepass.executes(() -> {
            RenderSystem.setShaderFog(p_407574_);
            float f = p_365046_.getGameTimeDeltaPartialTick(false);
            Vec3 vec3 = p_364769_.getPosition();
            double d0 = vec3.x();
            double d1 = vec3.y();
            double d2 = vec3.z();
            p_369478_.push("terrain");
            ChunkSectionsToRender chunksectionstorender = this.prepareChunkRenders(p_361439_, d0, d1, d2);
            chunksectionstorender.renderGroup(ChunkSectionLayerGroup.OPAQUE);
            this.minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
            if (resourcehandle2 != null) {
                resourcehandle2.get().copyDepthFrom(this.minecraft.getMainRenderTarget());
            }

            if (this.shouldShowEntityOutlines() && resourcehandle3 != null) {
                RenderTarget rendertarget = resourcehandle3.get();
                RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(rendertarget.getColorTexture(), 0, rendertarget.getDepthTexture(), 1.0);
            }

            PoseStack posestack = new PoseStack();
            MultiBufferSource.BufferSource multibuffersource$buffersource = this.renderBuffers.bufferSource();
            MultiBufferSource.BufferSource multibuffersource$buffersource1 = this.renderBuffers.crumblingBufferSource();
            p_369478_.popPush("entities");
            this.visibleEntities.sort(ENTITY_COMPARATOR);
            this.renderEntities(posestack, multibuffersource$buffersource, p_364769_, p_365046_, this.visibleEntities);
            multibuffersource$buffersource.endLastBatch();
            this.checkPoseStack(posestack);
            p_369478_.popPush("blockentities");
            var renderOutline = this.renderBlockEntities(posestack, multibuffersource$buffersource, multibuffersource$buffersource1, p_364769_, f, p_363733_) || p_362593_;
            multibuffersource$buffersource.endLastBatch();
            this.checkPoseStack(posestack);
            multibuffersource$buffersource.endBatch(RenderType.solid());
            multibuffersource$buffersource.endBatch(RenderType.endPortal());
            multibuffersource$buffersource.endBatch(RenderType.endGateway());
            multibuffersource$buffersource.endBatch(Sheets.solidBlockSheet());
            multibuffersource$buffersource.endBatch(Sheets.cutoutBlockSheet());
            multibuffersource$buffersource.endBatch(Sheets.bedSheet());
            multibuffersource$buffersource.endBatch(Sheets.shulkerBoxSheet());
            multibuffersource$buffersource.endBatch(Sheets.signSheet());
            multibuffersource$buffersource.endBatch(Sheets.hangingSignSheet());
            multibuffersource$buffersource.endBatch(Sheets.chestSheet());
            this.renderBuffers.outlineBufferSource().endOutlineBatch();
            if (renderOutline) {
                this.renderBlockOutline(p_364769_, multibuffersource$buffersource, posestack, false, f);
            }

            p_369478_.popPush("debug");
            this.minecraft.debugRenderer.render(posestack, p_363733_, multibuffersource$buffersource, d0, d1, d2);
            multibuffersource$buffersource.endLastBatch();
            this.checkPoseStack(posestack);
            multibuffersource$buffersource.endBatch(Sheets.translucentItemSheet());
            multibuffersource$buffersource.endBatch(Sheets.bannerSheet());
            multibuffersource$buffersource.endBatch(Sheets.shieldSheet());
            multibuffersource$buffersource.endBatch(RenderType.armorEntityGlint());
            multibuffersource$buffersource.endBatch(RenderType.glint());
            multibuffersource$buffersource.endBatch(RenderType.glintTranslucent());
            multibuffersource$buffersource.endBatch(RenderType.entityGlint());
            p_369478_.popPush("destroyProgress");
            this.renderBlockDestroyAnimation(posestack, p_364769_, multibuffersource$buffersource1);
            multibuffersource$buffersource1.endBatch();
            this.checkPoseStack(posestack);
            multibuffersource$buffersource.endBatch(RenderType.waterMask());
            multibuffersource$buffersource.endBatch();
            if (resourcehandle1 != null) {
                resourcehandle1.get().copyDepthFrom(resourcehandle.get());
            }

            p_369478_.popPush("translucent");
            chunksectionstorender.renderGroup(ChunkSectionLayerGroup.TRANSLUCENT);
            p_369478_.popPush("string");
            chunksectionstorender.renderGroup(ChunkSectionLayerGroup.TRIPWIRE);
            if (renderOutline) {
                this.renderBlockOutline(p_364769_, multibuffersource$buffersource, posestack, true, f);
            }

            multibuffersource$buffersource.endBatch();
            p_369478_.pop();
        });
    }

    private void addParticlesPass(FrameGraphBuilder p_366471_, Camera p_363128_, float p_365755_, GpuBufferSlice p_405857_, Frustum frustum) {
        FramePass framepass = p_366471_.addPass("particles");
        if (this.targets.particles != null) {
            this.targets.particles = framepass.readsAndWrites(this.targets.particles);
            framepass.reads(this.targets.main);
        } else {
            this.targets.main = framepass.readsAndWrites(this.targets.main);
        }

        ResourceHandle<RenderTarget> resourcehandle = this.targets.main;
        ResourceHandle<RenderTarget> resourcehandle1 = this.targets.particles;
        framepass.executes(() -> {
            RenderSystem.setShaderFog(p_405857_);
            if (resourcehandle1 != null) {
                resourcehandle1.get().copyDepthFrom(resourcehandle.get());
            }

            this.minecraft.particleEngine.render(p_363128_, p_365755_, this.renderBuffers.bufferSource(), frustum);
        });
    }

    private void addCloudsPass(FrameGraphBuilder p_364518_, CloudStatus p_368512_, Vec3 p_364075_, float p_369524_, int p_369495_, float p_366207_) {
        FramePass framepass = p_364518_.addPass("clouds");
        if (this.targets.clouds != null) {
            this.targets.clouds = framepass.readsAndWrites(this.targets.clouds);
        } else {
            this.targets.main = framepass.readsAndWrites(this.targets.main);
        }

        framepass.executes(() -> this.cloudRenderer.render(p_369495_, p_368512_, p_366207_, p_364075_, p_369524_));
    }

    private void addWeatherPass(FrameGraphBuilder p_362650_, Vec3 p_368413_, float p_367747_, GpuBufferSlice p_408677_) {
        int i = this.minecraft.options.getEffectiveRenderDistance() * 16;
        float f = this.minecraft.gameRenderer.getDepthFar();
        FramePass framepass = p_362650_.addPass("weather");
        if (this.targets.weather != null) {
            this.targets.weather = framepass.readsAndWrites(this.targets.weather);
        } else {
            this.targets.main = framepass.readsAndWrites(this.targets.main);
        }

        framepass.executes(() -> {
            RenderSystem.setShaderFog(p_408677_);
            MultiBufferSource.BufferSource multibuffersource$buffersource = this.renderBuffers.bufferSource();
            this.weatherEffectRenderer.render(this.minecraft.level, multibuffersource$buffersource, this.ticks, p_367747_, p_368413_);
            this.worldBorderRenderer.render(this.level.getWorldBorder(), p_368413_, i, f);
            multibuffersource$buffersource.endBatch();
        });
    }

    private void addLateDebugPass(FrameGraphBuilder p_369572_, Vec3 p_365929_, GpuBufferSlice p_408435_) {
        FramePass framepass = p_369572_.addPass("late_debug");
        this.targets.main = framepass.readsAndWrites(this.targets.main);
        if (this.targets.itemEntity != null) {
            this.targets.itemEntity = framepass.readsAndWrites(this.targets.itemEntity);
        }

        ResourceHandle<RenderTarget> resourcehandle = this.targets.main;
        framepass.executes(() -> {
            RenderSystem.setShaderFog(p_408435_);
            PoseStack posestack = new PoseStack();
            MultiBufferSource.BufferSource multibuffersource$buffersource = this.renderBuffers.bufferSource();
            this.minecraft.debugRenderer.renderAfterTranslucents(posestack, multibuffersource$buffersource, p_365929_.x, p_365929_.y, p_365929_.z);
            multibuffersource$buffersource.endLastBatch();
            this.checkPoseStack(posestack);
        });
    }

    private boolean collectVisibleEntities(Camera p_365712_, Frustum p_365717_, List<Entity> p_368622_) {
        Vec3 vec3 = p_365712_.getPosition();
        double d0 = vec3.x();
        double d1 = vec3.y();
        double d2 = vec3.z();
        boolean flag = false;
        boolean flag1 = this.shouldShowEntityOutlines();
        Entity.setViewScale(Mth.clamp(this.minecraft.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5) * this.minecraft.options.entityDistanceScaling().get());

        for (Entity entity : this.level.entitiesForRendering()) {
            if (this.entityRenderDispatcher.shouldRender(entity, p_365717_, d0, d1, d2) || entity.hasIndirectPassenger(this.minecraft.player)) {
                BlockPos blockpos = entity.blockPosition();
                if ((this.level.isOutsideBuildHeight(blockpos.getY()) || this.isSectionCompiled(blockpos))
                    && (
                        entity != p_365712_.getEntity()
                            || p_365712_.isDetached()
                            || p_365712_.getEntity() instanceof LivingEntity && ((LivingEntity)p_365712_.getEntity()).isSleeping()
                    )
                    && (!(entity instanceof LocalPlayer) || p_365712_.getEntity() == entity || (entity == minecraft.player && !minecraft.player.isSpectator()))) { //FORGE: render local player entity when it is not the renderViewEntity
                    p_368622_.add(entity);
                    if (flag1 && (this.minecraft.shouldEntityAppearGlowing(entity) || entity.hasCustomOutlineRendering(this.minecraft.player))) {
                        flag = true;
                    }
                }
            }
        }

        return flag;
    }

    private void renderEntities(PoseStack p_369689_, MultiBufferSource.BufferSource p_367493_, Camera p_368044_, DeltaTracker p_369396_, List<Entity> p_364182_) {
        Vec3 vec3 = p_368044_.getPosition();
        double d0 = vec3.x();
        double d1 = vec3.y();
        double d2 = vec3.z();
        TickRateManager tickratemanager = this.minecraft.level.tickRateManager();
        boolean flag = this.shouldShowEntityOutlines();

        for (Entity entity : p_364182_) {
            if (entity.tickCount == 0) {
                entity.xOld = entity.getX();
                entity.yOld = entity.getY();
                entity.zOld = entity.getZ();
            }

            MultiBufferSource multibuffersource;
            if (flag && this.minecraft.shouldEntityAppearGlowing(entity)) {
                OutlineBufferSource outlinebuffersource = this.renderBuffers.outlineBufferSource();
                multibuffersource = outlinebuffersource;
                int i = entity.getTeamColor();
                outlinebuffersource.setColor(ARGB.red(i), ARGB.green(i), ARGB.blue(i), 255);
            } else {
                multibuffersource = p_367493_;
            }

            float f = p_369396_.getGameTimeDeltaPartialTick(!tickratemanager.isEntityFrozen(entity));
            this.renderEntity(entity, d0, d1, d2, f, p_369689_, multibuffersource);
        }
    }

    private boolean renderBlockEntities(
        PoseStack p_366168_, MultiBufferSource.BufferSource p_362022_, MultiBufferSource.BufferSource p_369016_, Camera p_369847_, float p_367074_, Frustum frustum
    ) {
        boolean customOutline = false;
        Vec3 vec3 = p_369847_.getPosition();
        double d0 = vec3.x();
        double d1 = vec3.y();
        double d2 = vec3.z();

        for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection : this.visibleSections) {
            List<BlockEntity> list = sectionrenderdispatcher$rendersection.getSectionMesh().getRenderableBlockEntities();
            if (!list.isEmpty()) {
                for (BlockEntity blockentity : list) {
                    if (!frustum.isVisible(blockentity.getRenderBoundingBox())) continue;
                    BlockPos blockpos = blockentity.getBlockPos();
                    MultiBufferSource multibuffersource = p_362022_;
                    p_366168_.pushPose();
                    p_366168_.translate(blockpos.getX() - d0, blockpos.getY() - d1, blockpos.getZ() - d2);
                    SortedSet<BlockDestructionProgress> sortedset = this.destructionProgress.get(blockpos.asLong());
                    if (sortedset != null && !sortedset.isEmpty()) {
                        int i = sortedset.last().getProgress();
                        if (i >= 0) {
                            PoseStack.Pose posestack$pose = p_366168_.last();
                            VertexConsumer vertexconsumer = new SheetedDecalTextureGenerator(
                                p_369016_.getBuffer(ModelBakery.DESTROY_TYPES.get(i)), posestack$pose, 1.0F
                            );
                            multibuffersource = p_234298_ -> {
                                VertexConsumer vertexconsumer1 = p_362022_.getBuffer(p_234298_);
                                return p_234298_.affectsCrumbling() ? VertexMultiConsumer.create(vertexconsumer, vertexconsumer1) : vertexconsumer1;
                            };
                        }
                    }

                    if (!customOutline && this.shouldShowEntityOutlines() && blockentity.hasCustomOutlineRendering(this.minecraft.player))
                        customOutline = true;

                    this.blockEntityRenderDispatcher.render(blockentity, p_367074_, p_366168_, multibuffersource);
                    p_366168_.popPose();
                }
            }
        }

        Iterator<BlockEntity> iterator = this.level.getGloballyRenderedBlockEntities().iterator();

        while (iterator.hasNext()) {
            BlockEntity blockentity1 = iterator.next();
            if (blockentity1.isRemoved()) {
                iterator.remove();
            } else {
                if (!frustum.isVisible(blockentity1.getRenderBoundingBox())) continue;
                BlockPos blockpos1 = blockentity1.getBlockPos();
                p_366168_.pushPose();
                p_366168_.translate(blockpos1.getX() - d0, blockpos1.getY() - d1, blockpos1.getZ() - d2);
                if (!customOutline && this.shouldShowEntityOutlines() && blockentity1.hasCustomOutlineRendering(this.minecraft.player))
                    customOutline = true;
                this.blockEntityRenderDispatcher.render(blockentity1, p_367074_, p_366168_, p_362022_);
                p_366168_.popPose();
            }
        }

        return customOutline;
    }

    private void renderBlockDestroyAnimation(PoseStack p_366956_, Camera p_369324_, MultiBufferSource.BufferSource p_365998_) {
        Vec3 vec3 = p_369324_.getPosition();
        double d0 = vec3.x();
        double d1 = vec3.y();
        double d2 = vec3.z();

        for (Entry<SortedSet<BlockDestructionProgress>> entry : this.destructionProgress.long2ObjectEntrySet()) {
            BlockPos blockpos = BlockPos.of(entry.getLongKey());
            if (!(blockpos.distToCenterSqr(d0, d1, d2) > 1024.0)) {
                SortedSet<BlockDestructionProgress> sortedset = entry.getValue();
                if (sortedset != null && !sortedset.isEmpty()) {
                    int i = sortedset.last().getProgress();
                    p_366956_.pushPose();
                    p_366956_.translate(blockpos.getX() - d0, blockpos.getY() - d1, blockpos.getZ() - d2);
                    PoseStack.Pose posestack$pose = p_366956_.last();
                    VertexConsumer vertexconsumer = new SheetedDecalTextureGenerator(p_365998_.getBuffer(ModelBakery.DESTROY_TYPES.get(i)), posestack$pose, 1.0F);
                    this.minecraft.getBlockRenderer().renderBreakingTexture(this.level.getBlockState(blockpos), blockpos, this.level, p_366956_, vertexconsumer, level.getModelDataManager().getAtOrEmpty(blockpos));
                    p_366956_.popPose();
                }
            }
        }
    }

    private void renderBlockOutline(Camera p_367935_, MultiBufferSource.BufferSource p_367206_, PoseStack p_365062_, boolean p_368189_, float partialTicks) {
        if (this.minecraft.hitResult instanceof BlockHitResult blockhitresult) {
            if (blockhitresult.getType() != HitResult.Type.MISS) {
                BlockPos blockpos = blockhitresult.getBlockPos();
                BlockState blockstate = this.level.getBlockState(blockpos);
                if (!blockstate.isAir() && this.level.getWorldBorder().isWithinBounds(blockpos)) {
                    boolean flag = ItemBlockRenderTypes.getChunkRenderType(blockstate).sortOnUpload();
                    if (flag != p_368189_) {
                        return;
                    }

                    if (net.minecraftforge.client.ForgeHooksClient.onDrawHighlight(this, p_367935_, blockhitresult, partialTicks, p_365062_, p_367206_))
                        return;

                    Vec3 vec3 = p_367935_.getPosition();
                    Boolean obool = this.minecraft.options.highContrastBlockOutline().get();
                    if (obool) {
                        VertexConsumer vertexconsumer = p_367206_.getBuffer(RenderType.secondaryBlockOutline());
                        this.renderHitOutline(
                            p_365062_, vertexconsumer, p_367935_.getEntity(), vec3.x, vec3.y, vec3.z, blockpos, blockstate, -16777216
                        );
                    }

                    VertexConsumer vertexconsumer1 = p_367206_.getBuffer(RenderType.lines());
                    int i = obool ? -11010079 : ARGB.color(102, -16777216);
                    this.renderHitOutline(p_365062_, vertexconsumer1, p_367935_.getEntity(), vec3.x, vec3.y, vec3.z, blockpos, blockstate, i);
                    p_367206_.endLastBatch();
                }
            }
        } else if (this.minecraft.hitResult instanceof net.minecraft.world.phys.EntityHitResult entity) {
            net.minecraftforge.client.ForgeHooksClient.onDrawHighlight(this, p_367935_, entity, partialTicks, p_365062_, p_367206_);
        }
    }

    private void checkPoseStack(PoseStack p_109589_) {
        if (!p_109589_.isEmpty()) {
            throw new IllegalStateException("Pose stack not empty");
        }
    }

    private void renderEntity(
        Entity p_109518_, double p_109519_, double p_109520_, double p_109521_, float p_109522_, PoseStack p_109523_, MultiBufferSource p_109524_
    ) {
        double d0 = Mth.lerp(p_109522_, p_109518_.xOld, p_109518_.getX());
        double d1 = Mth.lerp(p_109522_, p_109518_.yOld, p_109518_.getY());
        double d2 = Mth.lerp(p_109522_, p_109518_.zOld, p_109518_.getZ());
        this.entityRenderDispatcher
            .render(
                p_109518_, d0 - p_109519_, d1 - p_109520_, d2 - p_109521_, p_109522_, p_109523_, p_109524_, this.entityRenderDispatcher.getPackedLightCoords(p_109518_, p_109522_)
            );
    }

    private void scheduleTranslucentSectionResort(Vec3 p_362155_) {
        if (!this.visibleSections.isEmpty()) {
            BlockPos blockpos = BlockPos.containing(p_362155_);
            boolean flag = !blockpos.equals(this.lastTranslucentSortBlockPos);
            Profiler.get().push("translucent_sort");
            TranslucencyPointOfView translucencypointofview = new TranslucencyPointOfView();

            for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection : this.nearbyVisibleSections) {
                this.scheduleResort(sectionrenderdispatcher$rendersection, translucencypointofview, p_362155_, flag, true);
            }

            this.translucencyResortIterationIndex = this.translucencyResortIterationIndex % this.visibleSections.size();
            int i = Math.max(this.visibleSections.size() / 8, 15);

            while (i-- > 0) {
                int j = this.translucencyResortIterationIndex++ % this.visibleSections.size();
                this.scheduleResort(this.visibleSections.get(j), translucencypointofview, p_362155_, flag, false);
            }

            this.lastTranslucentSortBlockPos = blockpos;
            Profiler.get().pop();
        }
    }

    private void scheduleResort(
        SectionRenderDispatcher.RenderSection p_363545_, TranslucencyPointOfView p_409581_, Vec3 p_364217_, boolean p_363419_, boolean p_368916_
    ) {
        p_409581_.set(p_364217_, p_363545_.getSectionNode());
        boolean flag = p_363545_.getSectionMesh().isDifferentPointOfView(p_409581_);
        boolean flag1 = p_363419_ && (p_409581_.isAxisAligned() || p_368916_);
        if ((flag1 || flag) && !p_363545_.transparencyResortingScheduled() && p_363545_.hasTranslucentGeometry()) {
            p_363545_.resortTransparency(this.sectionRenderDispatcher);
        }
    }

    private ChunkSectionsToRender prepareChunkRenders(Matrix4fc p_407733_, double p_409433_, double p_409487_, double p_408168_) {
        ObjectListIterator<SectionRenderDispatcher.RenderSection> objectlistiterator = this.visibleSections.listIterator(0);
        EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> enummap = new EnumMap<>(ChunkSectionLayer.class);
        int i = 0;

        for (ChunkSectionLayer chunksectionlayer : ChunkSectionLayer.values()) {
            enummap.put(chunksectionlayer, new ArrayList<>());
        }

        List<DynamicUniforms.Transform> list = new ArrayList<>();
        Vector4f vector4f = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
        Matrix4f matrix4f = new Matrix4f();

        while (objectlistiterator.hasNext()) {
            SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection = objectlistiterator.next();
            SectionMesh sectionmesh = sectionrenderdispatcher$rendersection.getSectionMesh();

            for (ChunkSectionLayer chunksectionlayer1 : ChunkSectionLayer.values()) {
                SectionBuffers sectionbuffers = sectionmesh.getBuffers(chunksectionlayer1);
                if (sectionbuffers != null) {
                    GpuBuffer gpubuffer;
                    VertexFormat.IndexType vertexformat$indextype;
                    if (sectionbuffers.getIndexBuffer() == null) {
                        if (sectionbuffers.getIndexCount() > i) {
                            i = sectionbuffers.getIndexCount();
                        }

                        gpubuffer = null;
                        vertexformat$indextype = null;
                    } else {
                        gpubuffer = sectionbuffers.getIndexBuffer();
                        vertexformat$indextype = sectionbuffers.getIndexType();
                    }

                    BlockPos blockpos = sectionrenderdispatcher$rendersection.getRenderOrigin();
                    int j = list.size();
                    list.add(
                        new DynamicUniforms.Transform(
                            p_407733_,
                            vector4f,
                            new Vector3f(
                                (float)(blockpos.getX() - p_409433_), (float)(blockpos.getY() - p_409487_), (float)(blockpos.getZ() - p_408168_)
                            ),
                            matrix4f,
                            1.0F
                        )
                    );
                    enummap.get(chunksectionlayer1)
                        .add(
                            new RenderPass.Draw<>(
                                0,
                                sectionbuffers.getVertexBuffer(),
                                gpubuffer,
                                vertexformat$indextype,
                                0,
                                sectionbuffers.getIndexCount(),
                                (p_404906_, p_404907_) -> p_404907_.upload("DynamicTransforms", p_404906_[j])
                            )
                        );
                }
            }
        }

        GpuBufferSlice[] agpubufferslice = RenderSystem.getDynamicUniforms().writeTransforms(list.toArray(new DynamicUniforms.Transform[0]));
        return new ChunkSectionsToRender(enummap, i, agpubufferslice);
    }

    public void endFrame() {
        this.cloudRenderer.endFrame();
    }

    public void captureFrustum() {
        this.captureFrustum = true;
    }

    public void killFrustum() {
        this.capturedFrustum = null;
    }

    public void tick() {
        if (this.level.tickRateManager().runsNormally()) {
            this.ticks++;
        }

        if (this.ticks % 20 == 0) {
            Iterator<BlockDestructionProgress> iterator = this.destroyingBlocks.values().iterator();

            while (iterator.hasNext()) {
                BlockDestructionProgress blockdestructionprogress = iterator.next();
                int i = blockdestructionprogress.getUpdatedRenderTick();
                if (this.ticks - i > 400) {
                    iterator.remove();
                    this.removeProgress(blockdestructionprogress);
                }
            }
        }
    }

    private void removeProgress(BlockDestructionProgress p_109766_) {
        long i = p_109766_.getPos().asLong();
        Set<BlockDestructionProgress> set = this.destructionProgress.get(i);
        set.remove(p_109766_);
        if (set.isEmpty()) {
            this.destructionProgress.remove(i);
        }
    }

    private void addSkyPass(FrameGraphBuilder p_362462_, Camera p_369183_, float p_368085_, GpuBufferSlice p_408470_) {
        FogType fogtype = p_369183_.getFluidInCamera();
        if (fogtype != FogType.POWDER_SNOW && fogtype != FogType.LAVA && !this.doesMobEffectBlockSky(p_369183_)) {
            DimensionSpecialEffects dimensionspecialeffects = this.level.effects();
            DimensionSpecialEffects.SkyType dimensionspecialeffects$skytype = dimensionspecialeffects.skyType();
            if (dimensionspecialeffects$skytype != DimensionSpecialEffects.SkyType.NONE) {
                FramePass framepass = p_362462_.addPass("sky");
                this.targets.main = framepass.readsAndWrites(this.targets.main);
                framepass.executes(() -> {
                    RenderSystem.setShaderFog(p_408470_);
                    if (dimensionspecialeffects$skytype == DimensionSpecialEffects.SkyType.END) {
                        this.skyRenderer.renderEndSky();
                    } else {
                        PoseStack posestack = new PoseStack();
                        float f = this.level.getSunAngle(p_368085_);
                        float f1 = this.level.getTimeOfDay(p_368085_);
                        float f2 = 1.0F - this.level.getRainLevel(p_368085_);
                        float f3 = this.level.getStarBrightness(p_368085_) * f2;
                        int i = dimensionspecialeffects.getSunriseOrSunsetColor(f1);
                        int j = this.level.getMoonPhase();
                        int k = this.level.getSkyColor(this.minecraft.gameRenderer.getMainCamera().getPosition(), p_368085_);
                        float f4 = ARGB.redFloat(k);
                        float f5 = ARGB.greenFloat(k);
                        float f6 = ARGB.blueFloat(k);
                        this.skyRenderer.renderSkyDisc(f4, f5, f6);
                        MultiBufferSource.BufferSource multibuffersource$buffersource = this.renderBuffers.bufferSource();
                        if (dimensionspecialeffects.isSunriseOrSunset(f1)) {
                            this.skyRenderer.renderSunriseAndSunset(posestack, multibuffersource$buffersource, f, i);
                        }

                        this.skyRenderer.renderSunMoonAndStars(posestack, multibuffersource$buffersource, f1, j, f2, f3);
                        multibuffersource$buffersource.endBatch();
                        if (this.shouldRenderDarkDisc(p_368085_)) {
                            this.skyRenderer.renderDarkDisc();
                        }
                    }
                });
            }
        }
    }

    private boolean shouldRenderDarkDisc(float p_365771_) {
        return this.minecraft.player.getEyePosition(p_365771_).y - this.level.getLevelData().getHorizonHeight(this.level) < 0.0;
    }

    private boolean doesMobEffectBlockSky(Camera p_234311_) {
        return !(p_234311_.getEntity() instanceof LivingEntity livingentity)
            ? false
            : livingentity.hasEffect(MobEffects.BLINDNESS) || livingentity.hasEffect(MobEffects.DARKNESS);
    }

    private void compileSections(Camera p_194371_) {
        ProfilerFiller profilerfiller = Profiler.get();
        profilerfiller.push("populate_sections_to_compile");
        RenderRegionCache renderregioncache = new RenderRegionCache();
        BlockPos blockpos = p_194371_.getBlockPosition();
        List<SectionRenderDispatcher.RenderSection> list = Lists.newArrayList();

        for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection : this.visibleSections) {
            if (sectionrenderdispatcher$rendersection.isDirty()
                && (sectionrenderdispatcher$rendersection.getSectionMesh() != CompiledSectionMesh.UNCOMPILED || sectionrenderdispatcher$rendersection.hasAllNeighbors())) {
                boolean flag = false;
                if (this.minecraft.options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.NEARBY) {
                    BlockPos blockpos1 = SectionPos.of(sectionrenderdispatcher$rendersection.getSectionNode()).center();
                    flag = blockpos1.distSqr(blockpos) < 768.0 || sectionrenderdispatcher$rendersection.isDirtyFromPlayer();
                } else if (this.minecraft.options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.PLAYER_AFFECTED) {
                    flag = sectionrenderdispatcher$rendersection.isDirtyFromPlayer();
                }

                if (flag) {
                    profilerfiller.push("build_near_sync");
                    this.sectionRenderDispatcher.rebuildSectionSync(sectionrenderdispatcher$rendersection, renderregioncache);
                    sectionrenderdispatcher$rendersection.setNotDirty();
                    profilerfiller.pop();
                } else {
                    list.add(sectionrenderdispatcher$rendersection);
                }
            }
        }

        profilerfiller.popPush("upload");
        this.sectionRenderDispatcher.uploadAllPendingUploads();
        profilerfiller.popPush("schedule_async_compile");

        for (SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection1 : list) {
            sectionrenderdispatcher$rendersection1.rebuildSectionAsync(renderregioncache);
            sectionrenderdispatcher$rendersection1.setNotDirty();
        }

        profilerfiller.pop();
        this.scheduleTranslucentSectionResort(p_194371_.getPosition());
    }

    private void renderHitOutline(
        PoseStack p_109638_,
        VertexConsumer p_109639_,
        Entity p_109640_,
        double p_109641_,
        double p_109642_,
        double p_109643_,
        BlockPos p_109644_,
        BlockState p_109645_,
        int p_362600_
    ) {
        ShapeRenderer.renderShape(
            p_109638_,
            p_109639_,
            p_109645_.getShape(this.level, p_109644_, CollisionContext.of(p_109640_)),
            p_109644_.getX() - p_109641_,
            p_109644_.getY() - p_109642_,
            p_109644_.getZ() - p_109643_,
            p_362600_
        );
    }

    public void blockChanged(BlockGetter p_109545_, BlockPos p_109546_, BlockState p_109547_, BlockState p_109548_, int p_109549_) {
        this.setBlockDirty(p_109546_, (p_109549_ & 8) != 0);
    }

    private void setBlockDirty(BlockPos p_109733_, boolean p_109734_) {
        for (int i = p_109733_.getZ() - 1; i <= p_109733_.getZ() + 1; i++) {
            for (int j = p_109733_.getX() - 1; j <= p_109733_.getX() + 1; j++) {
                for (int k = p_109733_.getY() - 1; k <= p_109733_.getY() + 1; k++) {
                    this.setSectionDirty(SectionPos.blockToSectionCoord(j), SectionPos.blockToSectionCoord(k), SectionPos.blockToSectionCoord(i), p_109734_);
                }
            }
        }
    }

    public void setBlocksDirty(int p_109495_, int p_109496_, int p_109497_, int p_109498_, int p_109499_, int p_109500_) {
        for (int i = p_109497_ - 1; i <= p_109500_ + 1; i++) {
            for (int j = p_109495_ - 1; j <= p_109498_ + 1; j++) {
                for (int k = p_109496_ - 1; k <= p_109499_ + 1; k++) {
                    this.setSectionDirty(SectionPos.blockToSectionCoord(j), SectionPos.blockToSectionCoord(k), SectionPos.blockToSectionCoord(i));
                }
            }
        }
    }

    public void setBlockDirty(BlockPos p_109722_, BlockState p_109723_, BlockState p_109724_) {
        if (this.minecraft.getModelManager().requiresRender(p_109723_, p_109724_)) {
            this.setBlocksDirty(
                p_109722_.getX(), p_109722_.getY(), p_109722_.getZ(), p_109722_.getX(), p_109722_.getY(), p_109722_.getZ()
            );
        }
    }

    public void setSectionDirtyWithNeighbors(int p_109491_, int p_109492_, int p_109493_) {
        this.setSectionRangeDirty(p_109491_ - 1, p_109492_ - 1, p_109493_ - 1, p_109491_ + 1, p_109492_ + 1, p_109493_ + 1);
    }

    public void setSectionRangeDirty(int p_368495_, int p_365381_, int p_365979_, int p_367380_, int p_368841_, int p_363880_) {
        for (int i = p_365979_; i <= p_363880_; i++) {
            for (int j = p_368495_; j <= p_367380_; j++) {
                for (int k = p_365381_; k <= p_368841_; k++) {
                    this.setSectionDirty(j, k, i);
                }
            }
        }
    }

    public void setSectionDirty(int p_109771_, int p_109772_, int p_109773_) {
        this.setSectionDirty(p_109771_, p_109772_, p_109773_, false);
    }

    private void setSectionDirty(int p_109502_, int p_109503_, int p_109504_, boolean p_109505_) {
        this.viewArea.setDirty(p_109502_, p_109503_, p_109504_, p_109505_);
    }

    public void onSectionBecomingNonEmpty(long p_366966_) {
        SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection = this.viewArea.getRenderSection(p_366966_);
        if (sectionrenderdispatcher$rendersection != null) {
            this.sectionOcclusionGraph.schedulePropagationFrom(sectionrenderdispatcher$rendersection);
        }
    }

    public void addParticle(
        ParticleOptions p_109744_,
        boolean p_109745_,
        double p_109746_,
        double p_109747_,
        double p_109748_,
        double p_109749_,
        double p_109750_,
        double p_109751_
    ) {
        this.addParticle(p_109744_, p_109745_, false, p_109746_, p_109747_, p_109748_, p_109749_, p_109750_, p_109751_);
    }

    public void addParticle(
        ParticleOptions p_109753_,
        boolean p_109754_,
        boolean p_109755_,
        double p_109756_,
        double p_109757_,
        double p_109758_,
        double p_109759_,
        double p_109760_,
        double p_109761_
    ) {
        try {
            this.addParticleInternal(p_109753_, p_109754_, p_109755_, p_109756_, p_109757_, p_109758_, p_109759_, p_109760_, p_109761_);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception while adding particle");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Particle being added");
            crashreportcategory.setDetail("ID", BuiltInRegistries.PARTICLE_TYPE.getKey(p_109753_.getType()));
            crashreportcategory.setDetail(
                "Parameters", () -> ParticleTypes.CODEC.encodeStart(this.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), p_109753_).toString()
            );
            crashreportcategory.setDetail("Position", () -> CrashReportCategory.formatLocation(this.level, p_109756_, p_109757_, p_109758_));
            throw new ReportedException(crashreport);
        }
    }

    public <T extends ParticleOptions> void addParticle(
        T p_109736_, double p_109737_, double p_109738_, double p_109739_, double p_109740_, double p_109741_, double p_109742_
    ) {
        this.addParticle(p_109736_, p_109736_.getType().getOverrideLimiter(), p_109737_, p_109738_, p_109739_, p_109740_, p_109741_, p_109742_);
    }

    @Nullable
    Particle addParticleInternal(
        ParticleOptions p_109796_,
        boolean p_109797_,
        double p_109798_,
        double p_109799_,
        double p_109800_,
        double p_109801_,
        double p_109802_,
        double p_109803_
    ) {
        return this.addParticleInternal(p_109796_, p_109797_, false, p_109798_, p_109799_, p_109800_, p_109801_, p_109802_, p_109803_);
    }

    @Nullable
    private Particle addParticleInternal(
        ParticleOptions p_109805_,
        boolean p_109806_,
        boolean p_109807_,
        double p_109808_,
        double p_109809_,
        double p_109810_,
        double p_109811_,
        double p_109812_,
        double p_109813_
    ) {
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        ParticleStatus particlestatus = this.calculateParticleLevel(p_109807_);
        if (p_109806_) {
            return this.minecraft.particleEngine.createParticle(p_109805_, p_109808_, p_109809_, p_109810_, p_109811_, p_109812_, p_109813_);
        } else if (camera.getPosition().distanceToSqr(p_109808_, p_109809_, p_109810_) > 1024.0) {
            return null;
        } else {
            return particlestatus == ParticleStatus.MINIMAL
                ? null
                : this.minecraft.particleEngine.createParticle(p_109805_, p_109808_, p_109809_, p_109810_, p_109811_, p_109812_, p_109813_);
        }
    }

    private ParticleStatus calculateParticleLevel(boolean p_109768_) {
        ParticleStatus particlestatus = this.minecraft.options.particles().get();
        if (p_109768_ && particlestatus == ParticleStatus.MINIMAL && this.level.random.nextInt(10) == 0) {
            particlestatus = ParticleStatus.DECREASED;
        }

        if (particlestatus == ParticleStatus.DECREASED && this.level.random.nextInt(3) == 0) {
            particlestatus = ParticleStatus.MINIMAL;
        }

        return particlestatus;
    }

    public void destroyBlockProgress(int p_109775_, BlockPos p_109776_, int p_109777_) {
        if (p_109777_ >= 0 && p_109777_ < 10) {
            BlockDestructionProgress blockdestructionprogress1 = this.destroyingBlocks.get(p_109775_);
            if (blockdestructionprogress1 != null) {
                this.removeProgress(blockdestructionprogress1);
            }

            if (blockdestructionprogress1 == null
                || blockdestructionprogress1.getPos().getX() != p_109776_.getX()
                || blockdestructionprogress1.getPos().getY() != p_109776_.getY()
                || blockdestructionprogress1.getPos().getZ() != p_109776_.getZ()) {
                blockdestructionprogress1 = new BlockDestructionProgress(p_109775_, p_109776_);
                this.destroyingBlocks.put(p_109775_, blockdestructionprogress1);
            }

            blockdestructionprogress1.setProgress(p_109777_);
            blockdestructionprogress1.updateTick(this.ticks);
            this.destructionProgress.computeIfAbsent(blockdestructionprogress1.getPos().asLong(), p_234254_ -> Sets.newTreeSet()).add(blockdestructionprogress1);
        } else {
            BlockDestructionProgress blockdestructionprogress = this.destroyingBlocks.remove(p_109775_);
            if (blockdestructionprogress != null) {
                this.removeProgress(blockdestructionprogress);
            }
        }
    }

    public boolean hasRenderedAllSections() {
        return this.sectionRenderDispatcher.isQueueEmpty();
    }

    public void onChunkReadyToRender(ChunkPos p_376082_) {
        this.sectionOcclusionGraph.onChunkReadyToRender(p_376082_);
    }

    public void needsUpdate() {
        this.sectionOcclusionGraph.invalidate();
        this.cloudRenderer.markForRebuild();
    }

    public static int getLightColor(BlockAndTintGetter p_109542_, BlockPos p_109543_) {
        return getLightColor(LevelRenderer.BrightnessGetter.DEFAULT, p_109542_, p_109542_.getBlockState(p_109543_), p_109543_);
    }

    public static int getLightColor(LevelRenderer.BrightnessGetter p_398213_, BlockAndTintGetter p_109538_, BlockState p_109539_, BlockPos p_109540_) {
        if (p_109539_.emissiveRendering(p_109538_, p_109540_)) {
            return 15728880;
        } else {
            int i = p_398213_.packedBrightness(p_109538_, p_109540_);
            int j = LightTexture.block(i);
            int k = p_109539_.getLightEmission(p_109538_, p_109540_);
            if (j < k) {
                int l = LightTexture.sky(i);
                return LightTexture.pack(k, l);
            } else {
                return i;
            }
        }
    }

    public boolean isSectionCompiled(BlockPos p_300380_) {
        SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection = this.viewArea.getRenderSectionAt(p_300380_);
        return sectionrenderdispatcher$rendersection != null && sectionrenderdispatcher$rendersection.sectionMesh.get() != CompiledSectionMesh.UNCOMPILED;
    }

    @Nullable
    public RenderTarget entityOutlineTarget() {
        return this.targets.entityOutline != null ? this.targets.entityOutline.get() : null;
    }

    @Nullable
    public RenderTarget getTranslucentTarget() {
        return this.targets.translucent != null ? this.targets.translucent.get() : null;
    }

    @Nullable
    public RenderTarget getItemEntityTarget() {
        return this.targets.itemEntity != null ? this.targets.itemEntity.get() : null;
    }

    @Nullable
    public RenderTarget getParticlesTarget() {
        return this.targets.particles != null ? this.targets.particles.get() : null;
    }

    @Nullable
    public RenderTarget getWeatherTarget() {
        return this.targets.weather != null ? this.targets.weather.get() : null;
    }

    @Nullable
    public RenderTarget getCloudsTarget() {
        return this.targets.clouds != null ? this.targets.clouds.get() : null;
    }

    @VisibleForDebug
    public ObjectArrayList<SectionRenderDispatcher.RenderSection> getVisibleSections() {
        return this.visibleSections;
    }

    @VisibleForDebug
    public SectionOcclusionGraph getSectionOcclusionGraph() {
        return this.sectionOcclusionGraph;
    }

    @Nullable
    public Frustum getCapturedFrustum() {
        return this.capturedFrustum;
    }

    public CloudRenderer getCloudRenderer() {
        return this.cloudRenderer;
    }

    public Frustum getFrustum() {
        return this.capturedFrustum != null ? this.capturedFrustum : this.cullingFrustum;
    }

    public int getTicks() {
        return this.ticks;
    }

    public WeatherEffectRenderer getWeatherEffects() {
        return this.weatherEffectRenderer;
    }

    public void setWeatherEffects(WeatherEffectRenderer value) {
        this.weatherEffectRenderer = value;
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    public interface BrightnessGetter {
        LevelRenderer.BrightnessGetter DEFAULT = (p_398214_, p_398219_) -> {
            int i = p_398214_.getBrightness(LightLayer.SKY, p_398219_);
            int j = p_398214_.getBrightness(LightLayer.BLOCK, p_398219_);
            return Brightness.pack(j, i);
        };

        int packedBrightness(BlockAndTintGetter p_398222_, BlockPos p_398220_);
    }
}
