package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.entity.state.ServerHitboxesRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class EntityRenderDispatcher implements ResourceManagerReloadListener {
    private static final RenderType SHADOW_RENDER_TYPE = RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));
    private static final float MAX_SHADOW_RADIUS = 32.0F;
    private static final float SHADOW_POWER_FALLOFF_Y = 0.5F;
    public Map<EntityType<?>, EntityRenderer<?, ?>> renderers = ImmutableMap.of();
    private Map<PlayerSkin.Model, EntityRenderer<? extends Player, ?>> playerRenderers = Map.of();
    public final TextureManager textureManager;
    private Level level;
    public Camera camera;
    private Quaternionf cameraOrientation;
    public Entity crosshairPickEntity;
    private final ItemModelResolver itemModelResolver;
    private final MapRenderer mapRenderer;
    private final BlockRenderDispatcher blockRenderDispatcher;
    private final ItemInHandRenderer itemInHandRenderer;
    private final Font font;
    public final Options options;
    private final Supplier<EntityModelSet> entityModels;
    private final EquipmentAssetManager equipmentAssets;
    private boolean shouldRenderShadow = true;
    private boolean renderHitBoxes;

    public <E extends Entity> int getPackedLightCoords(E p_114395_, float p_114396_) {
        return this.getRenderer(p_114395_).getPackedLightCoords(p_114395_, p_114396_);
    }

    public EntityRenderDispatcher(
        Minecraft p_234579_,
        TextureManager p_234580_,
        ItemModelResolver p_376277_,
        ItemRenderer p_234581_,
        MapRenderer p_363170_,
        BlockRenderDispatcher p_234582_,
        Font p_234583_,
        Options p_234584_,
        Supplier<EntityModelSet> p_377712_,
        EquipmentAssetManager p_377123_
    ) {
        this.textureManager = p_234580_;
        this.itemModelResolver = p_376277_;
        this.mapRenderer = p_363170_;
        this.itemInHandRenderer = new ItemInHandRenderer(p_234579_, this, p_234581_, p_376277_);
        this.blockRenderDispatcher = p_234582_;
        this.font = p_234583_;
        this.options = p_234584_;
        this.entityModels = p_377712_;
        this.equipmentAssets = p_377123_;
    }

    public <T extends Entity> EntityRenderer<? super T, ?> getRenderer(T p_114383_) {
        if (p_114383_ instanceof AbstractClientPlayer abstractclientplayer) {
            PlayerSkin.Model playerskin$model = abstractclientplayer.getSkin().model();
            EntityRenderer<? extends Player, ?> entityrenderer = this.playerRenderers.get(playerskin$model);
            return (EntityRenderer<? super T, ?>)(entityrenderer != null ? entityrenderer : this.playerRenderers.get(PlayerSkin.Model.WIDE));
        } else {
            return (EntityRenderer<? super T, ?>)this.renderers.get(p_114383_.getType());
        }
    }

    public <S extends EntityRenderState> EntityRenderer<?, ? super S> getRenderer(S p_397828_) {
        if (p_397828_ instanceof PlayerRenderState playerrenderstate) {
            PlayerSkin.Model playerskin$model = playerrenderstate.skin.model();
            EntityRenderer<? extends Player, ?> entityrenderer = this.playerRenderers.get(playerskin$model);
            return (EntityRenderer<?, ? super S>)(entityrenderer != null ? entityrenderer : (EntityRenderer)this.playerRenderers.get(PlayerSkin.Model.WIDE));
        } else {
            return (EntityRenderer<?, ? super S>)this.renderers.get(p_397828_.entityType);
        }
    }

    public void prepare(Level p_114409_, Camera p_114410_, Entity p_114411_) {
        this.level = p_114409_;
        this.camera = p_114410_;
        this.cameraOrientation = p_114410_.rotation();
        this.crosshairPickEntity = p_114411_;
    }

    public void overrideCameraOrientation(Quaternionf p_254264_) {
        this.cameraOrientation = p_254264_;
    }

    public void setRenderShadow(boolean p_114469_) {
        this.shouldRenderShadow = p_114469_;
    }

    public void setRenderHitBoxes(boolean p_114474_) {
        this.renderHitBoxes = p_114474_;
    }

    public boolean shouldRenderHitBoxes() {
        return this.renderHitBoxes;
    }

    public <E extends Entity> boolean shouldRender(E p_114398_, Frustum p_114399_, double p_114400_, double p_114401_, double p_114402_) {
        EntityRenderer<? super E, ?> entityrenderer = this.getRenderer(p_114398_);
        return entityrenderer.shouldRender(p_114398_, p_114399_, p_114400_, p_114401_, p_114402_);
    }

    public <E extends Entity> void render(
        E p_365164_, double p_364927_, double p_368937_, double p_369325_, float p_362312_, PoseStack p_364060_, MultiBufferSource p_362392_, int p_366201_
    ) {
        EntityRenderer<? super E, ?> entityrenderer = this.getRenderer(p_365164_);
        this.render(p_365164_, p_364927_, p_368937_, p_369325_, p_362312_, p_364060_, p_362392_, p_366201_, entityrenderer);
    }

    private <E extends Entity, S extends EntityRenderState> void render(
        E p_114385_,
        double p_114386_,
        double p_114387_,
        double p_114388_,
        float p_114389_,
        PoseStack p_114391_,
        MultiBufferSource p_114392_,
        int p_114393_,
        EntityRenderer<? super E, S> p_367105_
    ) {
        S s;
        try {
            s = p_367105_.createRenderState(p_114385_, p_114389_);
        } catch (Throwable throwable1) {
            CrashReport crashreport = CrashReport.forThrowable(throwable1, "Extracting render state for an entity in world");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being extracted");
            p_114385_.fillCrashReportCategory(crashreportcategory);
            CrashReportCategory crashreportcategory1 = this.fillRendererDetails(p_114386_, p_114387_, p_114388_, p_367105_, crashreport);
            crashreportcategory1.setDetail("Delta", p_114389_);
            throw new ReportedException(crashreport);
        }

        try {
            this.render(s, p_114386_, p_114387_, p_114388_, p_114391_, p_114392_, p_114393_, p_367105_);
        } catch (Throwable throwable) {
            CrashReport crashreport1 = CrashReport.forThrowable(throwable, "Rendering entity in world");
            CrashReportCategory crashreportcategory2 = crashreport1.addCategory("Entity being rendered");
            p_114385_.fillCrashReportCategory(crashreportcategory2);
            throw new ReportedException(crashreport1);
        }
    }

    public <S extends EntityRenderState> void render(
        S p_393895_, double p_391226_, double p_396972_, double p_395277_, PoseStack p_397395_, MultiBufferSource p_394110_, int p_394631_
    ) {
        EntityRenderer<?, ? super S> entityrenderer = this.getRenderer(p_393895_);
        this.render(p_393895_, p_391226_, p_396972_, p_395277_, p_397395_, p_394110_, p_394631_, entityrenderer);
    }

    private <S extends EntityRenderState> void render(
        S p_393838_,
        double p_391549_,
        double p_396344_,
        double p_394445_,
        PoseStack p_392040_,
        MultiBufferSource p_392043_,
        int p_395250_,
        EntityRenderer<?, S> p_395491_
    ) {
        try {
            Vec3 vec3 = p_395491_.getRenderOffset(p_393838_);
            double d3 = p_391549_ + vec3.x();
            double d0 = p_396344_ + vec3.y();
            double d1 = p_394445_ + vec3.z();
            p_392040_.pushPose();
            p_392040_.translate(d3, d0, d1);
            p_395491_.render(p_393838_, p_392040_, p_392043_, p_395250_);
            if (p_393838_.displayFireAnimation) {
                this.renderFlame(p_392040_, p_392043_, p_393838_, Mth.rotationAroundAxis(Mth.Y_AXIS, this.cameraOrientation, new Quaternionf()));
            }

            if (p_393838_ instanceof PlayerRenderState) {
                p_392040_.translate(-vec3.x(), -vec3.y(), -vec3.z());
            }

            if (this.options.entityShadows().get() && this.shouldRenderShadow && !p_393838_.isInvisible) {
                float f = p_395491_.getShadowRadius(p_393838_);
                if (f > 0.0F) {
                    double d2 = p_393838_.distanceToCameraSq;
                    float f1 = (float)((1.0 - d2 / 256.0) * p_395491_.getShadowStrength(p_393838_));
                    if (f1 > 0.0F) {
                        renderShadow(p_392040_, p_392043_, p_393838_, f1, this.level, Math.min(f, 32.0F));
                    }
                }
            }

            if (!(p_393838_ instanceof PlayerRenderState)) {
                p_392040_.translate(-vec3.x(), -vec3.y(), -vec3.z());
            }

            if (p_393838_.hitboxesRenderState != null) {
                this.renderHitboxes(p_392040_, p_393838_, p_393838_.hitboxesRenderState, p_392043_);
            }

            p_392040_.popPose();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Rendering entity in world");
            CrashReportCategory crashreportcategory = crashreport.addCategory("EntityRenderState being rendered");
            p_393838_.fillCrashReportCategory(crashreportcategory);
            this.fillRendererDetails(p_391549_, p_396344_, p_394445_, p_395491_, crashreport);
            throw new ReportedException(crashreport);
        }
    }

    private <S extends EntityRenderState> CrashReportCategory fillRendererDetails(
        double p_391621_, double p_396533_, double p_395641_, EntityRenderer<?, S> p_396247_, CrashReport p_396589_
    ) {
        CrashReportCategory crashreportcategory = p_396589_.addCategory("Renderer details");
        crashreportcategory.setDetail("Assigned renderer", p_396247_);
        crashreportcategory.setDetail("Location", CrashReportCategory.formatLocation(this.level, p_391621_, p_396533_, p_395641_));
        return crashreportcategory;
    }

    private void renderHitboxes(PoseStack p_396163_, EntityRenderState p_397027_, HitboxesRenderState p_395493_, MultiBufferSource p_394663_) {
        VertexConsumer vertexconsumer = p_394663_.getBuffer(RenderType.lines());
        renderHitboxesAndViewVector(p_396163_, p_395493_, vertexconsumer, p_397027_.eyeHeight);
        ServerHitboxesRenderState serverhitboxesrenderstate = p_397027_.serverHitboxesRenderState;
        if (serverhitboxesrenderstate != null) {
            if (serverhitboxesrenderstate.missing()) {
                HitboxRenderState hitboxrenderstate = p_395493_.hitboxes().getFirst();
                DebugRenderer.renderFloatingText(p_396163_, p_394663_, "Missing", p_397027_.x, hitboxrenderstate.y1() + 1.5, p_397027_.z, -65536);
            } else if (serverhitboxesrenderstate.hitboxes() != null) {
                p_396163_.pushPose();
                p_396163_.translate(
                    serverhitboxesrenderstate.serverEntityX() - p_397027_.x,
                    serverhitboxesrenderstate.serverEntityY() - p_397027_.y,
                    serverhitboxesrenderstate.serverEntityZ() - p_397027_.z
                );
                renderHitboxesAndViewVector(p_396163_, serverhitboxesrenderstate.hitboxes(), vertexconsumer, serverhitboxesrenderstate.eyeHeight());
                Vec3 vec3 = new Vec3(serverhitboxesrenderstate.deltaMovementX(), serverhitboxesrenderstate.deltaMovementY(), serverhitboxesrenderstate.deltaMovementZ());
                ShapeRenderer.renderVector(p_396163_, vertexconsumer, new Vector3f(), vec3, -256);
                p_396163_.popPose();
            }
        }
    }

    private static void renderHitboxesAndViewVector(PoseStack p_392904_, HitboxesRenderState p_393611_, VertexConsumer p_391746_, float p_392772_) {
        for (HitboxRenderState hitboxrenderstate : p_393611_.hitboxes()) {
            renderHitbox(p_392904_, p_391746_, hitboxrenderstate);
        }

        Vec3 vec3 = new Vec3(p_393611_.viewX(), p_393611_.viewY(), p_393611_.viewZ());
        ShapeRenderer.renderVector(p_392904_, p_391746_, new Vector3f(0.0F, p_392772_, 0.0F), vec3.scale(2.0), -16776961);
    }

    private static void renderHitbox(PoseStack p_114442_, VertexConsumer p_114443_, HitboxRenderState p_393380_) {
        p_114442_.pushPose();
        p_114442_.translate(p_393380_.offsetX(), p_393380_.offsetY(), p_393380_.offsetZ());
        ShapeRenderer.renderLineBox(
            p_114442_,
            p_114443_,
            p_393380_.x0(),
            p_393380_.y0(),
            p_393380_.z0(),
            p_393380_.x1(),
            p_393380_.y1(),
            p_393380_.z1(),
            p_393380_.red(),
            p_393380_.green(),
            p_393380_.blue(),
            1.0F
        );
        p_114442_.popPose();
    }

    private void renderFlame(PoseStack p_114454_, MultiBufferSource p_114455_, EntityRenderState p_362276_, Quaternionf p_312342_) {
        TextureAtlasSprite textureatlassprite = ModelBakery.FIRE_0.sprite();
        TextureAtlasSprite textureatlassprite1 = ModelBakery.FIRE_1.sprite();
        p_114454_.pushPose();
        float f = p_362276_.boundingBoxWidth * 1.4F;
        p_114454_.scale(f, f, f);
        float f1 = 0.5F;
        float f2 = 0.0F;
        float f3 = p_362276_.boundingBoxHeight / f;
        float f4 = 0.0F;
        p_114454_.mulPose(p_312342_);
        p_114454_.translate(0.0F, 0.0F, 0.3F - (int)f3 * 0.02F);
        float f5 = 0.0F;
        int i = 0;
        VertexConsumer vertexconsumer = p_114455_.getBuffer(Sheets.cutoutBlockSheet());

        for (PoseStack.Pose posestack$pose = p_114454_.last(); f3 > 0.0F; i++) {
            TextureAtlasSprite textureatlassprite2 = i % 2 == 0 ? textureatlassprite : textureatlassprite1;
            float f6 = textureatlassprite2.getU0();
            float f7 = textureatlassprite2.getV0();
            float f8 = textureatlassprite2.getU1();
            float f9 = textureatlassprite2.getV1();
            if (i / 2 % 2 == 0) {
                float f10 = f8;
                f8 = f6;
                f6 = f10;
            }

            fireVertex(posestack$pose, vertexconsumer, -f1 - 0.0F, 0.0F - f4, f5, f8, f9);
            fireVertex(posestack$pose, vertexconsumer, f1 - 0.0F, 0.0F - f4, f5, f6, f9);
            fireVertex(posestack$pose, vertexconsumer, f1 - 0.0F, 1.4F - f4, f5, f6, f7);
            fireVertex(posestack$pose, vertexconsumer, -f1 - 0.0F, 1.4F - f4, f5, f8, f7);
            f3 -= 0.45F;
            f4 -= 0.45F;
            f1 *= 0.9F;
            f5 -= 0.03F;
        }

        p_114454_.popPose();
    }

    private static void fireVertex(
        PoseStack.Pose p_114415_, VertexConsumer p_114416_, float p_114417_, float p_114418_, float p_114419_, float p_114420_, float p_114421_
    ) {
        p_114416_.addVertex(p_114415_, p_114417_, p_114418_, p_114419_)
            .setColor(-1)
            .setUv(p_114420_, p_114421_)
            .setUv1(0, 10)
            .setLight(240)
            .setNormal(p_114415_, 0.0F, 1.0F, 0.0F);
    }

    private static void renderShadow(
        PoseStack p_114458_, MultiBufferSource p_114459_, EntityRenderState p_365724_, float p_114461_, LevelReader p_114463_, float p_114462_
    ) {
        float f = Math.min(p_114461_ / 0.5F, p_114462_);
        int i = Mth.floor(p_365724_.x - p_114462_);
        int j = Mth.floor(p_365724_.x + p_114462_);
        int k = Mth.floor(p_365724_.y - f);
        int l = Mth.floor(p_365724_.y);
        int i1 = Mth.floor(p_365724_.z - p_114462_);
        int j1 = Mth.floor(p_365724_.z + p_114462_);
        PoseStack.Pose posestack$pose = p_114458_.last();
        VertexConsumer vertexconsumer = p_114459_.getBuffer(SHADOW_RENDER_TYPE);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int k1 = i1; k1 <= j1; k1++) {
            for (int l1 = i; l1 <= j; l1++) {
                blockpos$mutableblockpos.set(l1, 0, k1);
                ChunkAccess chunkaccess = p_114463_.getChunk(blockpos$mutableblockpos);

                for (int i2 = k; i2 <= l; i2++) {
                    blockpos$mutableblockpos.setY(i2);
                    float f1 = p_114461_ - (float)(p_365724_.y - blockpos$mutableblockpos.getY()) * 0.5F;
                    renderBlockShadow(
                        posestack$pose,
                        vertexconsumer,
                        chunkaccess,
                        p_114463_,
                        blockpos$mutableblockpos,
                        p_365724_.x,
                        p_365724_.y,
                        p_365724_.z,
                        p_114462_,
                        f1
                    );
                }
            }
        }
    }

    private static void renderBlockShadow(
        PoseStack.Pose p_277956_,
        VertexConsumer p_277533_,
        ChunkAccess p_277501_,
        LevelReader p_277622_,
        BlockPos p_277911_,
        double p_277682_,
        double p_278099_,
        double p_277806_,
        float p_277844_,
        float p_277496_
    ) {
        BlockPos blockpos = p_277911_.below();
        BlockState blockstate = p_277501_.getBlockState(blockpos);
        if (blockstate.getRenderShape() != RenderShape.INVISIBLE && p_277622_.getMaxLocalRawBrightness(p_277911_) > 3) {
            if (blockstate.isCollisionShapeFullBlock(p_277501_, blockpos)) {
                VoxelShape voxelshape = blockstate.getShape(p_277501_, blockpos);
                if (!voxelshape.isEmpty()) {
                    float f = LightTexture.getBrightness(p_277622_.dimensionType(), p_277622_.getMaxLocalRawBrightness(p_277911_));
                    float f1 = p_277496_ * 0.5F * f;
                    if (f1 >= 0.0F) {
                        if (f1 > 1.0F) {
                            f1 = 1.0F;
                        }

                        int i = ARGB.color(Mth.floor(f1 * 255.0F), 255, 255, 255);
                        AABB aabb = voxelshape.bounds();
                        double d0 = p_277911_.getX() + aabb.minX;
                        double d1 = p_277911_.getX() + aabb.maxX;
                        double d2 = p_277911_.getY() + aabb.minY;
                        double d3 = p_277911_.getZ() + aabb.minZ;
                        double d4 = p_277911_.getZ() + aabb.maxZ;
                        float f2 = (float)(d0 - p_277682_);
                        float f3 = (float)(d1 - p_277682_);
                        float f4 = (float)(d2 - p_278099_);
                        float f5 = (float)(d3 - p_277806_);
                        float f6 = (float)(d4 - p_277806_);
                        float f7 = -f2 / 2.0F / p_277844_ + 0.5F;
                        float f8 = -f3 / 2.0F / p_277844_ + 0.5F;
                        float f9 = -f5 / 2.0F / p_277844_ + 0.5F;
                        float f10 = -f6 / 2.0F / p_277844_ + 0.5F;
                        shadowVertex(p_277956_, p_277533_, i, f2, f4, f5, f7, f9);
                        shadowVertex(p_277956_, p_277533_, i, f2, f4, f6, f7, f10);
                        shadowVertex(p_277956_, p_277533_, i, f3, f4, f6, f8, f10);
                        shadowVertex(p_277956_, p_277533_, i, f3, f4, f5, f8, f9);
                    }
                }
            }
        }
    }

    private static void shadowVertex(
        PoseStack.Pose p_114423_, VertexConsumer p_114424_, int p_343218_, float p_114425_, float p_114426_, float p_114427_, float p_114428_, float p_114429_
    ) {
        Vector3f vector3f = p_114423_.pose().transformPosition(p_114425_, p_114426_, p_114427_, new Vector3f());
        p_114424_.addVertex(vector3f.x(), vector3f.y(), vector3f.z(), p_343218_, p_114428_, p_114429_, OverlayTexture.NO_OVERLAY, 15728880, 0.0F, 1.0F, 0.0F);
    }

    public void setLevel(@Nullable Level p_114407_) {
        this.level = p_114407_;
        if (p_114407_ == null) {
            this.camera = null;
        }
    }

    public double distanceToSqr(Entity p_114472_) {
        return this.camera.getPosition().distanceToSqr(p_114472_.position());
    }

    public double distanceToSqr(double p_114379_, double p_114380_, double p_114381_) {
        return this.camera.getPosition().distanceToSqr(p_114379_, p_114380_, p_114381_);
    }

    public Quaternionf cameraOrientation() {
        return this.cameraOrientation;
    }

    public ItemInHandRenderer getItemInHandRenderer() {
        return this.itemInHandRenderer;
    }

    public Map<PlayerSkin.Model, EntityRenderer<? extends Player, ?>> getSkinMap() {
        return java.util.Collections.unmodifiableMap(playerRenderers);
    }

    @Override
    public void onResourceManagerReload(ResourceManager p_174004_) {
        EntityRendererProvider.Context entityrendererprovider$context = new EntityRendererProvider.Context(
            this, this.itemModelResolver, this.mapRenderer, this.blockRenderDispatcher, p_174004_, this.entityModels.get(), this.equipmentAssets, this.font
        );
        this.renderers = EntityRenderers.createEntityRenderers(entityrendererprovider$context);
        this.playerRenderers = EntityRenderers.createPlayerRenderers(entityrendererprovider$context);
        net.minecraftforge.client.event.ForgeEventFactoryClient.onGatherLayers(renderers, playerRenderers, entityrendererprovider$context);
    }
}
